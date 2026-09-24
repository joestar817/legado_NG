package io.legado.app.ui.book.read.epub

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One bounded preparation display. WebView is drawn by Android's compositor, never by Canvas. */
internal class EpubHardwareCapture(
    private val context: Context,
    private val actualWidth: Int,
    private val actualHeight: Int,
    private val viewport: Rect,
    private val onHost: (FrameLayout) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("epub-frame").apply { start() }
    private val gl = Handler(thread.looper)
    @Volatile private var closed = false
    private var presentation: Presentation? = null
    private var display: VirtualDisplay? = null
    private var frame: CaptureFrame? = null
    private var marker = 0
    private data class CaptureRequest(val marker: Int, val callback: (Bitmap) -> Unit, var timestamp: Long? = null)
    private var request: CaptureRequest? = null // GL thread only.
    private var texture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglInitialized = false
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var pixelBuffer: ByteBuffer? = null
    private var pixelColors: IntArray? = null
    private val width get() = viewport.width()
    private val height get() = viewport.height()

    init {
        require(width > 0 && height > 0 && actualWidth.toLong() * actualHeight <= 16_000_000)
        require(Rect(0, 0, actualWidth, actualHeight).contains(viewport))
        gl.post {
            runCatching { initialize() }.onFailure { error -> main.post { if (!closed) onError(error) } }
        }
    }

    private fun initialize() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        // Older Android EGL bindings require non-null version outputs. A display
        // handle alone does not mean we acquired an initialization reference.
        val version = IntArray(2)
        eglInitialized = EGL14.eglInitialize(eglDisplay, version, 0, version, 1)
        check(eglInitialized)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE,
        ), 0, configs, 0, 1, count, 0) && count[0] > 0)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], intArrayOf(
            EGL14.EGL_WIDTH, actualWidth, EGL14.EGL_HEIGHT, actualHeight, EGL14.EGL_NONE,
        ), 0)
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        program = GLES20.glCreateProgram()
        val vertex = shader(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 p; attribute vec2 uv; uniform mat4 m; varying vec2 t; void main(){gl_Position=vec4(p,0.,1.);t=(m*vec4(uv,0.,1.)).xy;}")
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES s; varying vec2 t; void main(){gl_FragColor=texture2D(s,t);}")
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val source = SurfaceTexture(textureId).apply { setDefaultBufferSize(actualWidth, actualHeight) }
        texture = source
        source.setOnFrameAvailableListener({
            if (!closed) runCatching { receiveFrame() }.onFailure { error ->
                main.post { if (!closed) onError(error) }
            }
        }, gl)
        val output = Surface(source)
        surface = output
        main.post {
            if (closed) return@post
            runCatching {
                val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val virtual = manager.createVirtualDisplay("EPUB page preparation", actualWidth, actualHeight,
                    context.resources.displayMetrics.densityDpi, output,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
                    ?: error("无法创建 EPUB 硬件绘制目标")
                display = virtual
                val window = Presentation(context, virtual.display)
                presentation = window
                window.window?.apply {
                    addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                    setBackgroundDrawableResource(android.R.color.transparent)
                    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                }
                val root = CaptureFrame(window.context)
                frame = root
                val content = FrameLayout(window.context)
                root.addView(content, FrameLayout.LayoutParams(width, height).apply {
                    leftMargin = viewport.left; topMargin = viewport.top
                })
                window.setContentView(root)
                window.show()
                onHost(content)
            }.onFailure(onError)
        }
    }

    /** Call after the runtime and WebView visual-state callback agree on the requested revision. */
    fun capture(callback: (Bitmap) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return
        val color = Color.rgb(++marker and 255, marker.shr(8) and 255, 113)
        gl.post {
            request = CaptureRequest(color, callback)
            main.post {
                if (!closed) {
                    frame?.marker = color
                    frame?.markerVisible = true
                    frame?.invalidate()
                }
            }
        }
    }

    private fun receiveFrame() {
        val source = texture ?: return
        source.updateTexImage()
        val pending = request ?: return
        val matrix = FloatArray(16)
        source.getTransformMatrix(matrix)
        GLES20.glViewport(0, 0, actualWidth, actualHeight)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "s"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "m"), 1, false, matrix, 0)
        val vertices = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        val coordinates = floats(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
        val p = GLES20.glGetAttribLocation(program, "p")
        val uv = GLES20.glGetAttribLocation(program, "uv")
        GLES20.glEnableVertexAttribArray(p)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, coordinates)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (pending.timestamp == null) {
            val pixel = ByteBuffer.allocateDirect(4)
            GLES20.glReadPixels(0, actualHeight - 1, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel)
            val stamp = Color.argb(pixel.get(3).toInt() and 255, pixel.get(0).toInt() and 255,
                pixel.get(1).toInt() and 255, pixel.get(2).toInt() and 255)
            if (stamp != pending.marker) return
            pending.timestamp = source.timestamp
            main.post {
                if (!closed) { frame?.markerVisible = false; frame?.invalidate() }
            }
            return
        }
        if (source.timestamp <= pending.timestamp!!) return
        val pixels = pixelBuffer ?: ByteBuffer.allocateDirect(width * height * 4).also { pixelBuffer = it }
        pixels.clear()
        GLES20.glReadPixels(viewport.left, actualHeight - viewport.bottom, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "EPUB 硬件画面读取失败" }
        // Capture the clean frame following the marker. The marker never appears in the bitmap.
        val colors = pixelColors ?: IntArray(width * height).also { pixelColors = it }
        readEpubFramePixels(pixels, width, height, colors)
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        request = null
        main.post { if (closed) bitmap.recycle() else pending.callback(bitmap) }
    }

    private fun shader(type: Int, code: String): Int {
        val value = GLES20.glCreateShader(type)
        GLES20.glShaderSource(value, code)
        GLES20.glCompileShader(value)
        val status = IntArray(1)
        GLES20.glGetShaderiv(value, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { GLES20.glGetShaderInfoLog(value) }
        return value
    }

    private fun floats(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

    override fun close() {
        if (closed) return
        closed = true
        presentation?.dismiss()
        presentation = null
        frame = null
        display?.release()
        display = null
        gl.post {
            request = null
            pixelBuffer = null
            pixelColors = null
            surface?.release()
            surface = null
            texture?.setOnFrameAvailableListener(null)
            texture?.release()
            texture = null
            if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            if (program != 0) GLES20.glDeleteProgram(program)
            if (eglInitialized) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
                eglInitialized = false
            }
            EGL14.eglReleaseThread()
            thread.quitSafely()
        }
    }

    private class CaptureFrame(context: Context) : FrameLayout(context) {
        var marker: Int = Color.BLACK
        var markerVisible = false
        private val markerPaint = Paint()
        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (markerVisible) {
                markerPaint.color = marker
                canvas.drawRect(0f, 0f, 1f, 1f, markerPaint)
            }
        }
    }
}
