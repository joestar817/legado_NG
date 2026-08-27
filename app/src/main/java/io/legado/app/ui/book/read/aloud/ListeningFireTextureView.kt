package io.legado.app.ui.book.read.aloud

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Choreographer
import android.view.TextureView
import io.legado.app.help.config.ListeningFireStyle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** A translucent GLES 3 TextureView that composes inside the normal Compose/View hierarchy. */
internal class ListeningFireTextureView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener {

    private val appContext = context.applicationContext
    private val sessionLock = Any()

    @Volatile
    private var renderConfig = FireConfig()

    @Volatile
    private var session: RenderSession? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun update(
        style: ListeningFireStyle,
        intensity: Int,
        color: Int,
        accentFollowsMain: Boolean,
    ) {
        val config = FireConfig(
            style = style,
            intensity = intensity.coerceIn(0, 100),
            color = color,
            accentFollowsMain = accentFollowsMain,
        )
        val currentSession = synchronized(sessionLock) {
            renderConfig = config
            session
        }
        currentSession?.update(config)
    }

    fun release() {
        synchronized(sessionLock) { session }?.requestStop()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        release()
        val nextSession = synchronized(sessionLock) {
            RenderSession(surface, width, height, renderConfig).also {
                session = it
            }
        }
        postOnAnimation {
            postOnAnimation {
                val shouldStart = synchronized(sessionLock) { session === nextSession }
                if (shouldStart && isAvailable && surfaceTexture === surface) {
                    nextSession.start()
                }
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        session?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val oldSession = synchronized(sessionLock) {
            session?.takeIf { it.owns(surface) }?.also { session = null }
        } ?: return true
        return oldSession.stopAndAwait()
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private inner class RenderSession(
        private val surfaceTexture: SurfaceTexture,
        private val initialWidth: Int,
        private val initialHeight: Int,
        initialConfig: FireConfig,
    ) {
        private val motionRenderer = ReadAloudFlipbookFireRenderer(appContext).apply {
            update(
                initialConfig.style,
                initialConfig.intensity,
                initialConfig.color,
                initialConfig.accentFollowsMain,
            )
        }
        private val thread = HandlerThread("ListeningFireGL", Process.THREAD_PRIORITY_BACKGROUND)
        private val decoderThread = HandlerThread(
            "ListeningFireDecode",
            Process.THREAD_PRIORITY_BACKGROUND,
        )
        private lateinit var handler: Handler
        private lateinit var decoderHandler: Handler

        @Volatile
        private var active = true
        private var eglWindow: EglWindow? = null
        private var choreographer: Choreographer? = null
        private var nextFrameTimeNanos = 0L
        private var framesStarted = false
        @Volatile
        private var latestWidth = initialWidth
        @Volatile
        private var latestHeight = initialHeight
        private val frameCallback = Choreographer.FrameCallback(::renderFrame)
        private val stopRequested = AtomicBoolean(false)
        private val finished = AtomicBoolean(false)
        private val surfaceReleased = AtomicBoolean(false)
        private val releaseCompleted = CountDownLatch(1)
        @Volatile
        private var releaseSurfaceWhenFinished = false

        fun owns(surface: SurfaceTexture): Boolean = surfaceTexture === surface

        fun update(config: FireConfig) {
            motionRenderer.update(
                config.style,
                config.intensity,
                config.color,
                config.accentFollowsMain,
            )
        }

        fun start() {
            if (!active) return
            thread.start()
            handler = Handler(thread.looper)
            handler.post {
                if (!active) return@post
                runCatching {
                    choreographer = Choreographer.getInstance()
                    val egl = EglWindow(surfaceTexture)
                    eglWindow = egl
                    GLES30.glClearColor(0f, 0f, 0f, 0f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    check(egl.swapBuffers()) { "Unable to swap initial transparent fire buffer" }
                    motionRenderer.onSurfaceCreated()
                    motionRenderer.onSurfaceChanged(latestWidth, latestHeight)
                    startAtlasDecode()
                }.onFailure { error ->
                    active = false
                    Log.e(TAG, "Unable to start fire TextureView", error)
                    finishSession()
                }
            }
        }

        fun resize(width: Int, height: Int) {
            latestWidth = width
            latestHeight = height
            if (!active || !::handler.isInitialized) return
            handler.post {
                if (active) motionRenderer.onSurfaceChanged(latestWidth, latestHeight)
            }
        }

        fun requestStop() {
            active = false
            if (!stopRequested.compareAndSet(false, true)) return
            if (!::handler.isInitialized) {
                finished.set(true)
                releaseCompleted.countDown()
                return
            }
            if (decoderThread.isAlive) decoderThread.quitSafely()
            if (!handler.post(::finishSession)) finishSessionWithoutGl()
        }

        fun stopAndAwait(): Boolean {
            requestStop()
            if (Thread.currentThread() === thread) return finished.get()
            val completed = releaseCompleted.await(RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!completed) {
                releaseSurfaceWhenFinished = true
                if (finished.get()) releaseOwnedSurfaceTexture()
            }
            return completed
        }

        private fun renderFrame(frameTimeNanos: Long) {
            if (!active) return
            if (nextFrameTimeNanos == 0L) nextFrameTimeNanos = frameTimeNanos
            if (frameTimeNanos + FRAME_TOLERANCE_NANOS < nextFrameTimeNanos) {
                choreographer?.postFrameCallback(frameCallback)
                return
            }
            runCatching {
                motionRenderer.onDrawFrame(frameTimeNanos)
                check(eglWindow?.swapBuffers() == true) { "Unable to swap fire EGL buffers" }
            }.onFailure { error ->
                active = false
                Log.e(TAG, "Fire TextureView frame failed", error)
                finishSession()
                return
            }
            do {
                nextFrameTimeNanos += FRAME_PERIOD_NANOS
            } while (nextFrameTimeNanos <= frameTimeNanos)
            if (active) choreographer?.postFrameCallback(frameCallback)
        }

        private fun startAtlasDecode() {
            if (!active) return
            decoderThread.start()
            decoderHandler = Handler(decoderThread.looper)
            decoderHandler.post { decodeAtlasPage(0) }
        }

        private fun decodeAtlasPage(index: Int) {
            if (!active || index !in 0 until FIRE_ATLAS_PAGE_COUNT) return
            val pixels = runCatching { motionRenderer.decodeAtlasPage(index) }
                .getOrElse { error ->
                    handler.post {
                        if (index <= 1 && active) {
                            active = false
                            Log.e(TAG, "Unable to decode initial fire atlas $index", error)
                            finishSession()
                        } else {
                            Log.w(TAG, "Unable to decode fire atlas $index", error)
                        }
                    }
                    if (index > 1) scheduleNextAtlasDecode(index)
                    return
                }
            if (!active) return
            handler.post {
                if (!active) return@post
                runCatching { motionRenderer.uploadAtlasPage(index, pixels) }
                    .onFailure { error ->
                        if (index <= 1) {
                            active = false
                            Log.e(TAG, "Unable to upload initial fire atlas $index", error)
                            finishSession()
                        } else {
                            Log.w(TAG, "Unable to upload fire atlas $index", error)
                        }
                    }
                if (index == 1 && active && !framesStarted) {
                    framesStarted = true
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                    choreographer?.postFrameCallback(frameCallback)
                }
            }
            scheduleNextAtlasDecode(index)
        }

        private fun scheduleNextAtlasDecode(index: Int) {
            val nextIndex = index + 1
            if (!active || nextIndex >= FIRE_ATLAS_PAGE_COUNT || !::decoderHandler.isInitialized) {
                return
            }
            val delay = if (nextIndex == 3) FIRE_LAST_ATLAS_DELAY_MILLIS else 0L
            decoderHandler.postDelayed({ decodeAtlasPage(nextIndex) }, delay)
        }

        private fun releaseOnRenderThread() {
            choreographer?.removeFrameCallback(frameCallback)
            choreographer = null
            runCatching { motionRenderer.release() }
                .onFailure { Log.w(TAG, "Unable to release fire renderer", it) }
            runCatching { eglWindow?.release() }
                .onFailure { Log.w(TAG, "Unable to release fire EGL window", it) }
            eglWindow = null
        }

        private fun finishSession() {
            if (!finished.compareAndSet(false, true)) return
            try {
                releaseOnRenderThread()
            } finally {
                if (releaseSurfaceWhenFinished) releaseOwnedSurfaceTexture()
                releaseCompleted.countDown()
                if (decoderThread.isAlive) decoderThread.quitSafely()
                thread.quitSafely()
            }
        }

        private fun finishSessionWithoutGl() {
            if (!finished.compareAndSet(false, true)) return
            if (releaseSurfaceWhenFinished) releaseOwnedSurfaceTexture()
            releaseCompleted.countDown()
            if (decoderThread.isAlive) decoderThread.quitSafely()
            if (thread.isAlive) thread.quitSafely()
        }

        private fun releaseOwnedSurfaceTexture() {
            if (surfaceReleased.compareAndSet(false, true)) {
                runCatching { surfaceTexture.release() }
                    .onFailure { Log.w(TAG, "Unable to release owned fire SurfaceTexture", it) }
            }
        }
    }

    private data class FireConfig(
        val style: ListeningFireStyle = ListeningFireStyle.GODFIRE,
        val intensity: Int = 40,
        val color: Int = DEFAULT_FIRE_COLOR,
        val accentFollowsMain: Boolean = false,
    )

    private class EglWindow(surfaceTexture: SurfaceTexture) {
        private val display: EGLDisplay
        private val context: EGLContext
        private val surface: EGLSurface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "Unable to acquire EGL display" }
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
                "Unable to initialize EGL"
            }
            val configAttributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    display,
                    configAttributes,
                    0,
                    configs,
                    0,
                    configs.size,
                    configCount,
                    0,
                ) && configCount[0] > 0
            ) { "Unable to choose translucent GLES 3 config" }
            val config = checkNotNull(configs[0])
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                0,
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create GLES 3 context" }
            surface = EGL14.eglCreateWindowSurface(
                display,
                config,
                surfaceTexture,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                "Unable to make fire EGL context current"
            }
            EGL14.eglSwapInterval(display, 1)
        }

        fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(display, surface)

        fun release() {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    }

    private companion object {
        const val TAG = "ListeningFireTextureView"
        const val EGL_OPENGL_ES3_BIT_KHR = 0x00000040
        const val FRAME_PERIOD_NANOS = 1_000_000_000L / 60L
        const val FRAME_TOLERANCE_NANOS = 1_000_000L
        const val FIRE_ATLAS_PAGE_COUNT = 4
        const val FIRE_LAST_ATLAS_DELAY_MILLIS = 1_500L
        const val RELEASE_TIMEOUT_MILLIS = 250L
        const val DEFAULT_FIRE_COLOR = -0x002EC5D9
    }
}
