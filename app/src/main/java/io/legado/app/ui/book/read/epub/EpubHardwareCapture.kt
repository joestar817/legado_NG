package io.legado.app.ui.book.read.epub

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.Build
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Immutable capture pixels shared only through the bounded frame window's leases. */
internal class EpubCapturePixels(val bitmap: Bitmap, private val release: () -> Unit = { bitmap.recycle() }) : Closeable {
    private val closed = AtomicBoolean()
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

/** One bounded preparation display. WebView is drawn by Android's compositor, never by Canvas. */
internal class EpubHardwareCapture(
    private val context: Context,
    private val actualWidth: Int,
    private val actualHeight: Int,
    private val viewport: Rect,
    private val hardwareFrames: Boolean,
    private val onHost: (FrameLayout) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Closeable {
    private val marker = EpubCaptureMarker(actualWidth, actualHeight)
    private val markerPixels = ByteBuffer.allocateDirect(marker.byteCount)
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("epub-frame").apply { start() }
    private val gl = Handler(thread.looper)
    @Volatile private var closed = false
    private var presentation: Presentation? = null
    private var display: VirtualDisplay? = null
    private var frame: CaptureFrame? = null
    private var pulseToken: Long? = null // Main thread only.
    private val capturePulse = object : Runnable {
        override fun run() {
            val token = pulseToken ?: return
            if (closed || !epoch.isCurrent(token)) return
            frame?.let { it.pulse = !it.pulse; it.invalidate() }
            main.postDelayed(this, 100)
        }
    }
    @Volatile var waitingFor = "初始化画面捕获"
        private set
    private val epoch = EpubRequestEpoch()
    private val startup = EpubStartupTiming("capture-host")
    private data class CaptureRequest(val token: Long, val callback: (EpubCapturePixels) -> Unit,
                                      val timing: EpubStartupTiming, var timestamp: Long? = null,
                                      var copying: Boolean = false)
    private var request: CaptureRequest? = null // GL thread only.
    private var texture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglInitialized = false
    private var eglConfig: EGLConfig? = null
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var pixelBuffer: ByteBuffer? = null
    private var pixelColors: IntArray? = null
    private var pixelCopyAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    private var pixelCopyInFlight = false // Includes cancelled requests until their callback.
    private var hardwareOutputAvailable = hardwareFrames && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private var nodeCaptureAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q // Main thread only.
    private var asyncNodeCaptureAvailable = hardwareFrames && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // Qualified per preparation host; no driver-specific direction or persisted preference.
    private var bufferCaptureMode = 0 // 0 unknown, 1 normal, 2 WebView Y correction, -1 unavailable.
    val canSuppressWindowDraw: Boolean
        get() = !closed && hardwareFrames && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            nodeCaptureAvailable && asyncNodeCaptureAvailable
    private var outputReader: ImageReader? = null
    private var outputSurface = EGL14.EGL_NO_SURFACE
    private var outputRequest: CaptureRequest? = null
    private var glCloseRequested = false
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
        fun choose(surfaceType: Int) = EGL14.eglChooseConfig(eglDisplay, intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, surfaceType, EGL14.EGL_NONE,
        ), 0, configs, 0, 1, count, 0) && count[0] > 0
        if (hardwareOutputAvailable && !choose(EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT))
            hardwareOutputAvailable = false
        if (!hardwareOutputAvailable) check(choose(EGL14.EGL_PBUFFER_BIT))
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], intArrayOf(
            EGL14.EGL_WIDTH, actualWidth, EGL14.EGL_HEIGHT, actualHeight, EGL14.EGL_NONE,
        ), 0)
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
        program = GLES20.glCreateProgram()
        val precision = IntArray(1)
        GLES20.glGetShaderPrecisionFormat(GLES20.GL_FRAGMENT_SHADER, GLES20.GL_HIGH_FLOAT, IntArray(2), 0, precision, 0)
        val coordinatePrecision = if (precision[0] > 0) "highp" else "mediump"
        val vertex = shader(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 p; attribute vec2 uv; uniform mat4 m; varying $coordinatePrecision vec2 t; void main(){gl_Position=vec4(p,0.,1.);t=(m*vec4(uv,0.,1.)).xy;}")
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\nprecision $coordinatePrecision float; uniform samplerExternalOES s; varying $coordinatePrecision vec2 t; void main(){gl_FragColor=texture2D(s,t);}")
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
        startup.mark("gl-ready")
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
                val root = CaptureFrame(window.context, marker)
                frame = root
                val content = FrameLayout(window.context)
                root.addView(content, FrameLayout.LayoutParams(width, height).apply {
                    leftMargin = viewport.left; topMargin = viewport.top
                })
                window.setContentView(root)
                window.show()
                startup.mark("host-ready")
                onHost(content)
            }.onFailure(onError)
        }
    }

    /** Call after the runtime and WebView visual-state callback agree on the requested revision. */
    fun capture(allowBufferQualification: Boolean = false, callback: (EpubCapturePixels) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return
        val token = epoch.next()
        val timing = EpubStartupTiming("capture")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && nodeCaptureAvailable) {
            waitingFor = "提交硬件画面"
            // Surface.publishState completes its alpha/clipping update after onReady returns.
            // Draw on the next main task, while this private job still owns the same viewport.
            main.post {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || closed || !epoch.isCurrent(token)) return@post
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && asyncNodeCaptureAvailable) {
                    val host = checkNotNull(frame?.getChildAt(0))
                    val captured: (Result<EpubCapturePixels>) -> Unit = { result ->
                        val pixels = result.getOrNull()
                        if (closed || !epoch.isCurrent(token)) pixels?.close()
                        else if (pixels != null) { stopPulse(); callback(pixels) }
                        else {
                            asyncNodeCaptureAvailable = false
                            timing.mark("async-node-capture-unavailable")
                            // Preserve the existing synchronous/legacy backends on unsupported drivers.
                            capture(allowBufferQualification, callback)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bufferCaptureMode >= 0 &&
                        (bufferCaptureMode != 0 || allowBufferQualification) && EpubHardwareBufferCapture.eligible(host)) {
                        if (bufferCaptureMode == 0) {
                            EpubHardwareBufferCapture.qualify(host, timing, { !closed && epoch.isCurrent(token) }) { flipY, result ->
                                if (!closed && epoch.isCurrent(token)) {
                                    bufferCaptureMode = when (flipY) { false -> 1; true -> 2; null -> -1 }
                                    timing.mark("buffer-qualified-$bufferCaptureMode")
                                }
                                captured(result)
                            }
                        } else EpubHardwareBufferCapture.capture(host, bufferCaptureMode == 2, timing) { result ->
                            if (result.isFailure && !closed && epoch.isCurrent(token)) {
                                bufferCaptureMode = -1
                                timing.mark("buffer-capture-unavailable")
                                EpubRenderNodeCaptureAsync.capture(host, timing, captured)
                            } else captured(result)
                        }
                    } else EpubRenderNodeCaptureAsync.capture(host, timing, captured)
                    return@post
                }
                val pixels = try {
                    EpubRenderNodeCapture.capture(checkNotNull(frame?.getChildAt(0)), timing)
                } catch (_: RuntimeException) {
                    // Once a host needs the legacy producer, never overlap it with a node
                    // producer, including an old PixelCopy callback still in flight.
                    nodeCaptureAvailable = false
                    timing.mark("node-capture-unavailable")
                    captureMarked(token, timing, callback)
                    return@post
                }
                if (closed || !epoch.isCurrent(token)) pixels.close()
                else if (hardwareFrames) {
                    stopPulse()
                    timing.mark("node-frame-ready")
                    callback(pixels)
                } else gl.post copy@ {
                    if (closed || !epoch.isCurrent(token)) { pixels.close(); return@copy }
                    // The producer has already retired. Software curl consumers receive
                    // one immutable readback on this worker, never a hardware Bitmap.
                    val software = try {
                        EpubCapturePixels(checkNotNull(pixels.bitmap.copy(Bitmap.Config.ARGB_8888, false)))
                    } catch (_: RuntimeException) {
                        main.post {
                            if (!closed && epoch.isCurrent(token)) {
                                nodeCaptureAvailable = false
                                timing.mark("node-readback-unavailable")
                                captureMarked(token, timing, callback)
                            }
                        }
                        return@copy
                    } finally { pixels.close() }
                    main.post {
                        if (closed || !epoch.isCurrent(token)) software.close()
                        else {
                            stopPulse()
                            timing.mark("node-software-frame-ready")
                            callback(software)
                        }
                    }
                }
            }
            return
        }
        captureMarked(token, timing, callback)
    }

    private fun captureMarked(token: Long, timing: EpubStartupTiming, callback: (EpubCapturePixels) -> Unit) {
        if (closed || !epoch.isCurrent(token)) return
        // A legacy marker must certify the visible body, not only its parent overlay.
        frame?.getChildAt(0)?.alpha = 1f
        waitingFor = "等待捕获标记"
        gl.post {
            if (closed || !epoch.isCurrent(token)) return@post
            request = CaptureRequest(token, callback, timing)
            main.post {
                if (!closed && epoch.isCurrent(token)) {
                    frame?.markerToken = token
                    frame?.markerVisible = true
                    frame?.invalidate()
                    pulseToken = token
                    main.removeCallbacks(capturePulse)
                    main.postDelayed(capturePulse, 100)
                }
            }
        }
    }

    /** Keep the GL/display host, but invalidate queued marker work and pixel delivery. */
    fun cancel() {
        check(Looper.myLooper() == Looper.getMainLooper())
        epoch.next()
        waitingFor = "捕获已取消"
        stopPulse()
        frame?.let { it.markerVisible = false; it.invalidate() }
        gl.post { request = null }
    }

    private fun receiveFrame() {
        val source = texture ?: return
        source.updateTexImage()
        val pending = request ?: return
        if (!epoch.isCurrent(pending.token)) { request = null; return }
        if (pending.copying || pixelCopyInFlight || outputRequest != null) return
        val matrix = FloatArray(16)
        source.getTransformMatrix(matrix)
        val markerOnly = hardwareOutputAvailable || pixelCopyAvailable
        drawSource(matrix, markerOnly = markerOnly)
        markerPixels.clear()
        GLES20.glReadPixels(marker.left, actualHeight - marker.top - marker.height, marker.width, marker.height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, markerPixels)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "EPUB 捕获标记读取失败" }
        if (pending.timestamp == null) {
            if (!marker.matches(markerPixels, pending.token)) return
            pending.timestamp = source.timestamp
            waitingFor = "等待完整画面"
            pending.timing.mark("marker-ready")
            main.post {
                if (!closed && epoch.isCurrent(pending.token)) { frame?.markerVisible = false; frame?.invalidate() }
            }
            return
        }
        if (!marker.isCleanAfter(markerPixels, pending.timestamp!!, source.timestamp)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hardwareOutputAvailable && copyHardwareFrame(pending, matrix)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && pixelCopyAvailable && copySurfaceFrame(pending)) return
        if (markerOnly) drawSource(matrix)
        waitingFor = "读取画面像素"
        val pixels = pixelBuffer ?: ByteBuffer.allocateDirect(width * height * 4).also { pixelBuffer = it }
        pixels.clear()
        pending.timing.mark("read-pixels-start")
        GLES20.glReadPixels(viewport.left, actualHeight - viewport.bottom, width, height,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "EPUB 硬件画面读取失败" }
        pending.timing.mark("read-pixels-ready")
        // Capture the clean frame following the marker. The marker never appears in the bitmap.
        val colors = pixelColors ?: IntArray(width * height).also { pixelColors = it }
        readEpubFramePixels(pixels, width, height, colors)
        pending.timing.mark("pixels-converted")
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        deliverFrame(pending, bitmap)
    }

    private fun drawSource(matrix: FloatArray, outputWidth: Int = actualWidth, outputHeight: Int = actualHeight,
        textureCoordinates: FloatArray = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f), markerOnly: Boolean = false) {
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "s"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "m"), 1, false, matrix, 0)
        val vertices = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        val coordinates = floats(textureCoordinates)
        val p = GLES20.glGetAttribLocation(program, "p")
        val uv = GLES20.glGetAttribLocation(program, "uv")
        GLES20.glEnableVertexAttribArray(p)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 0, coordinates)
        if (markerOnly) {
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(marker.left, actualHeight - marker.top - marker.height, marker.width, marker.height)
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (markerOnly) GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun copyHardwareFrame(pending: CaptureRequest, matrix: FloatArray): Boolean {
        pending.timing.mark("hardware-frame-start")
        try {
            // One output buffer per request. Retire its producer before publishing the
            // Bitmap: retaining a wrapper alone would not prevent ImageReader reuse.
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            outputReader = reader
            outputSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, reader.surface,
                intArrayOf(EGL14.EGL_NONE), 0)
            check(outputSurface != EGL14.EGL_NO_SURFACE)
            reader.setOnImageAvailableListener({ receiveHardwareFrame(reader) }, gl)
            check(EGL14.eglMakeCurrent(eglDisplay, outputSurface, outputSurface, eglContext))
            val left = viewport.left.toFloat() / actualWidth
            val right = viewport.right.toFloat() / actualWidth
            val top = 1f - viewport.top.toFloat() / actualHeight
            val bottom = 1f - viewport.bottom.toFloat() / actualHeight
            // EGL window buffers already use the Bitmap's top-first row convention.
            // The CPU glReadPixels fallback flips rows separately; repeating that flip
            // here mirrors the captured page vertically during the native animation.
            drawSource(matrix, width, height,
                floatArrayOf(left, bottom, right, bottom, left, top, right, top))
            GLES20.glFinish()
            check(GLES20.glGetError() == GLES20.GL_NO_ERROR)
            pending.timing.mark("hardware-render-ready")
            outputRequest = pending
            pending.copying = true
            check(EGL14.eglSwapBuffers(eglDisplay, outputSurface))
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
            return true
        } catch (_: RuntimeException) {
            outputRequest = null
            pending.copying = false
            hardwareOutputAvailable = false
            releaseHardwareOutput()
            pending.timing.mark("hardware-frame-unavailable")
            return false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun receiveHardwareFrame(reader: ImageReader) {
        if (reader !== outputReader) return
        val pending = outputRequest ?: return
        var pixels: EpubCapturePixels? = null
        var acquired = false
        try {
            val image = reader.acquireNextImage() ?: return
            acquired = true
            image.use {
                if (!closed && epoch.isCurrent(pending.token) && request === pending) {
                    val buffer = checkNotNull(image.hardwareBuffer)
                    buffer.use {
                        pixels = EpubCapturePixels(checkNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))))
                    }
                }
                // No producer may ever overwrite this buffer again. The wrapped Bitmap
                // owns its own native reference after Image/reader teardown below.
                check(retireHardwareProducer())
            }
        } catch (_: RuntimeException) {
            acquired = true
            pixels?.close()
            pixels = null
            hardwareOutputAvailable = false
            pending.timing.mark("hardware-frame-fallback")
        } finally {
            if (acquired) {
                releaseHardwareOutput()
                outputRequest = null
            }
        }
        if (!acquired) return
        val captured = pixels
        when {
            glCloseRequested -> { captured?.close(); releaseGlResources() }
            closed || !epoch.isCurrent(pending.token) || request !== pending -> {
                captured?.close()
                if (!closed && request != null) resumeCapture()
            }
            captured != null -> {
                pending.timing.mark("hardware-frame-ready")
                deliverFrame(pending, captured)
            }
            else -> { pending.copying = false; resumeCapture() }
        }
    }

    private fun retireHardwareProducer(): Boolean {
        var retired = true
        if (eglInitialized) {
            retired = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            if (outputSurface != EGL14.EGL_NO_SURFACE)
                retired = EGL14.eglDestroySurface(eglDisplay, outputSurface) && retired
        }
        outputSurface = EGL14.EGL_NO_SURFACE
        return retired
    }

    private fun releaseHardwareOutput() {
        retireHardwareProducer()
        outputReader?.setOnImageAvailableListener(null, null)
        outputReader?.close()
        outputReader = null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun copySurfaceFrame(pending: CaptureRequest): Boolean {
        val source = surface ?: return false
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        waitingFor = "复制硬件画面"
        pending.copying = true
        pixelCopyInFlight = true
        pending.timing.mark("pixel-copy-start")
        try {
            // The clean marker frame is already latched. This private document cannot move
            // until delivery; later queued buffers have the same revision and no marker.
            PixelCopy.request(source, viewport, bitmap, { result ->
                pixelCopyInFlight = false
                if (closed || !epoch.isCurrent(pending.token) || request !== pending) {
                    bitmap.recycle()
                    if (glCloseRequested) releaseGlResources()
                    else if (!closed && request != null) resumeCapture()
                } else if (result == PixelCopy.SUCCESS) {
                    pending.timing.mark("pixel-copy-ready")
                    deliverFrame(pending, bitmap)
                } else {
                    bitmap.recycle()
                    pending.copying = false
                    pixelCopyAvailable = false
                    pending.timing.mark("pixel-copy-fallback-$result")
                    resumeCapture()
                }
            }, gl)
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            pending.copying = false
            pixelCopyInFlight = false
            pixelCopyAvailable = false
            pending.timing.mark("pixel-copy-unavailable")
            return false
        }
        return true
    }

    private fun resumeCapture() {
        runCatching { receiveFrame() }.onFailure { error -> main.post { if (!closed) onError(error) } }
    }

    private fun deliverFrame(pending: CaptureRequest, bitmap: Bitmap) = deliverFrame(pending, EpubCapturePixels(bitmap))

    private fun deliverFrame(pending: CaptureRequest, pixels: EpubCapturePixels) {
        if (closed || !epoch.isCurrent(pending.token) || request !== pending) { pixels.close(); return }
        pending.timing.mark("pixels-ready")
        request = null
        waitingFor = "交付捕获画面"
        main.post {
            if (closed || !epoch.isCurrent(pending.token)) pixels.close()
            else { stopPulse(); pending.timing.mark("delivered"); pending.callback(pixels) }
        }
    }

    private fun stopPulse() {
        pulseToken = null
        main.removeCallbacks(capturePulse)
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
        epoch.next()
        stopPulse()
        presentation?.dismiss()
        presentation = null
        frame = null
        display?.release()
        display = null
        gl.post {
            request = null
            glCloseRequested = true
            if (!pixelCopyInFlight && outputRequest == null) releaseGlResources()
        }
    }

    private fun releaseGlResources() {
        releaseHardwareOutput()
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

    private class CaptureFrame(context: Context, private val marker: EpubCaptureMarker) : FrameLayout(context) {
        var markerToken = 0L
        var markerVisible = false
        var pulse = false
        private val markerPaint = Paint().apply { isAntiAlias = false; isDither = false }
        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (markerVisible) {
                for (cell in 0 until EpubCaptureMarker.CELLS) {
                    val x = marker.left + cell % EpubCaptureMarker.COLUMNS * marker.cellSize
                    val y = marker.top + cell / EpubCaptureMarker.COLUMNS * marker.cellSize
                    markerPaint.color = if (marker.white(markerToken, cell)) Color.WHITE else Color.BLACK
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + marker.cellSize).toFloat(), (y + marker.cellSize).toFloat(), markerPaint)
                }
                // Change only a cell border, outside every sampled center. This keeps a stalled
                // private display producing frames without changing the request identity.
                if (marker.cellSize >= 4) {
                    markerPaint.color = if (pulse) Color.WHITE else Color.BLACK
                    canvas.drawRect(marker.left.toFloat(), marker.top.toFloat(),
                        (marker.left + 1).toFloat(), (marker.top + 1).toFloat(), markerPaint)
                }
            }
        }
    }
}
