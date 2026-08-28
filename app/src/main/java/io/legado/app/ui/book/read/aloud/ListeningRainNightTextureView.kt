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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Draws the opaque Rain Night scene inside the normal Compose/View layer order. */
internal class ListeningRainNightTextureView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener,
    ListeningCartoonTextureHost {

    private val appContext = context.applicationContext
    private val sessionLock = Any()

    @Volatile
    private var renderConfig = RainNightConfig()

    @Volatile
    private var session: RenderSession? = null

    init {
        // Remain transparent until the background has decoded and the first complete scene is
        // ready, leaving the normal player background as the failure-safe fallback.
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun update(
        intensity: Int,
        animationAllowed: Boolean,
        timelineOriginNanos: Long?,
    ) {
        val config = RainNightConfig(
            intensity = intensity.coerceIn(0, 100),
            animationAllowed = animationAllowed,
            timelineOriginNanos = timelineOriginNanos,
        )
        val currentSession = synchronized(sessionLock) {
            renderConfig = config
            session
        }
        currentSession?.update(config)
    }

    override fun release(onReleased: (() -> Unit)?) {
        val currentSession = synchronized(sessionLock) { session }
        if (currentSession == null) {
            onReleased?.invoke()
        } else {
            currentSession.requestStop(onReleased)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        release()
        alpha = 1f
        val nextSession = synchronized(sessionLock) {
            RenderSession(surface, width, height, renderConfig).also { session = it }
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
        oldSession.stopAndReleaseSurface()
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private inner class RenderSession(
        private val surfaceTexture: SurfaceTexture,
        initialWidth: Int,
        initialHeight: Int,
        initialConfig: RainNightConfig,
    ) {
        private val motionRenderer = ReadAloudRainNightRenderer(appContext).apply {
            update(
                initialConfig.intensity,
                initialConfig.animationAllowed,
                initialConfig.timelineOriginNanos,
            )
        }
        private val thread = HandlerThread(
            "ListeningRainNightGL",
            Process.THREAD_PRIORITY_BACKGROUND,
        )
        private val decoderThread = HandlerThread(
            "ListeningRainNightDecode",
            Process.THREAD_PRIORITY_BACKGROUND,
        )
        private lateinit var handler: Handler
        private lateinit var decoderHandler: Handler

        @Volatile
        private var active = true
        private var eglWindow: EglWindow? = null
        private var choreographer: Choreographer? = null
        private var nextFrameTimeNanos = 0L
        private var frameScheduled = false
        private var framesReady = false
        @Volatile
        private var latestWidth = initialWidth
        @Volatile
        private var latestHeight = initialHeight
        private val frameCallback = Choreographer.FrameCallback(::renderFrame)
        private val stopRequested = AtomicBoolean(false)
        private val teardownStarted = AtomicBoolean(false)
        private val surfaceReleased = AtomicBoolean(false)
        private val releaseCompleted = CountDownLatch(1)
        private val surfaceHandoffLock = Any()
        private var releaseSurfaceWhenFinished = false
        private val releaseCallbackLock = Any()
        private val releaseCallbacks = mutableListOf<() -> Unit>()

        fun owns(surface: SurfaceTexture): Boolean = surfaceTexture === surface

        fun update(config: RainNightConfig) {
            motionRenderer.update(
                config.intensity,
                config.animationAllowed,
                config.timelineOriginNanos,
            )
            if (!active || !::handler.isInitialized) return
            handler.post {
                if (active && framesReady) {
                    nextFrameTimeNanos = 0L
                    scheduleFrame()
                }
            }
        }

        fun start() {
            if (!active) return
            thread.start()
            handler = Handler(thread.looper)
            handler.post {
                if (!active) return@post
                runCatching {
                    choreographer = Choreographer.getInstance()
                    val egl = EglWindow.create(surfaceTexture)
                    eglWindow = egl
                    GLES30.glClearColor(0f, 0f, 0f, 0f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    check(egl.swapBuffers()) { "Unable to swap initial Rain Night buffer" }
                    val maxTextureSize = motionRenderer.onSurfaceCreated()
                    motionRenderer.onSurfaceChanged(latestWidth, latestHeight)
                    startAssetDecode(maxTextureSize)
                }.onFailure { error ->
                    active = false
                    Log.e(TAG, "Unable to start Rain Night TextureView", error)
                    revealFallback()
                    finishSession()
                }
            }
        }

        fun resize(width: Int, height: Int) {
            latestWidth = width
            latestHeight = height
            if (!active || !::handler.isInitialized) return
            handler.post {
                if (active) {
                    motionRenderer.onSurfaceChanged(latestWidth, latestHeight)
                    if (framesReady) scheduleFrame()
                }
            }
        }

        fun requestStop(onReleased: (() -> Unit)? = null) {
            onReleased?.let(::registerReleaseCallback)
            active = false
            if (!stopRequested.compareAndSet(false, true)) return
            if (!::handler.isInitialized) {
                finishSessionWithoutGl()
                return
            }
            if (decoderThread.isAlive) decoderThread.quitSafely()
            if (!handler.post(::finishSession)) finishSessionWithoutGl()
        }

        fun stopAndReleaseSurface() {
            requestSurfaceHandoff()
            requestStop()
        }

        private fun startAssetDecode(maxTextureSize: Int) {
            if (!active) return
            decoderThread.start()
            decoderHandler = Handler(decoderThread.looper)
            decoderHandler.post decode@{
                val textureData = runCatching {
                    motionRenderer.decodeAssets(maxTextureSize)
                }.getOrElse { error ->
                    handler.post {
                        if (active) {
                            active = false
                            Log.e(TAG, "Unable to decode Rain Night assets", error)
                            revealFallback()
                            finishSession()
                        }
                    }
                    if (decoderThread.isAlive) decoderThread.quitSafely()
                    return@decode
                }
                if (!active) {
                    textureData.recycle()
                    if (decoderThread.isAlive) decoderThread.quitSafely()
                    return@decode
                }
                val accepted = handler.post upload@{
                    if (!active) {
                        textureData.recycle()
                        return@upload
                    }
                    runCatching { motionRenderer.uploadAssets(textureData) }
                        .onSuccess {
                            framesReady = true
                            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                            scheduleFrame()
                        }
                        .onFailure { error ->
                            active = false
                            Log.e(TAG, "Unable to upload Rain Night assets", error)
                            revealFallback()
                            finishSession()
                        }
                }
                if (!accepted) textureData.recycle()
                if (decoderThread.isAlive) decoderThread.quitSafely()
            }
        }

        private fun scheduleFrame() {
            if (!active || !framesReady || frameScheduled) return
            frameScheduled = true
            choreographer?.postFrameCallback(frameCallback)
        }

        private fun renderFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!active || !framesReady) return
            if (nextFrameTimeNanos == 0L) nextFrameTimeNanos = frameTimeNanos
            if (
                motionRenderer.shouldAnimate() &&
                frameTimeNanos + FRAME_TOLERANCE_NANOS < nextFrameTimeNanos
            ) {
                scheduleFrame()
                return
            }
            runCatching {
                motionRenderer.onDrawFrame(frameTimeNanos)
                check(eglWindow?.swapBuffers() == true) {
                    "Unable to swap Rain Night EGL buffers"
                }
            }.onFailure { error ->
                active = false
                Log.e(TAG, "Rain Night TextureView frame failed", error)
                revealFallback()
                finishSession()
                return
            }
            if (motionRenderer.shouldAnimate()) {
                do {
                    nextFrameTimeNanos += FRAME_PERIOD_NANOS
                } while (nextFrameTimeNanos <= frameTimeNanos)
                scheduleFrame()
            } else {
                nextFrameTimeNanos = 0L
            }
        }

        private fun releaseOnRenderThread() {
            choreographer?.removeFrameCallback(frameCallback)
            choreographer = null
            runCatching { motionRenderer.release() }
                .onFailure { Log.w(TAG, "Unable to release Rain Night renderer", it) }
            runCatching { eglWindow?.release() }
                .onFailure { Log.w(TAG, "Unable to release Rain Night EGL window", it) }
            eglWindow = null
        }

        private fun finishSession() {
            if (!teardownStarted.compareAndSet(false, true)) return
            try {
                releaseOnRenderThread()
            } finally {
                completeSurfaceHandoff()
                if (decoderThread.isAlive) decoderThread.quitSafely()
                thread.quitSafely()
            }
        }

        private fun finishSessionWithoutGl() {
            if (!teardownStarted.compareAndSet(false, true)) return
            completeSurfaceHandoff()
            if (decoderThread.isAlive) decoderThread.quitSafely()
            if (thread.isAlive) thread.quitSafely()
        }

        private fun requestSurfaceHandoff() {
            synchronized(surfaceHandoffLock) {
                releaseSurfaceWhenFinished = true
                if (releaseCompleted.count == 0L) releaseOwnedSurfaceTexture()
            }
        }

        private fun completeSurfaceHandoff() {
            synchronized(surfaceHandoffLock) {
                if (releaseSurfaceWhenFinished) releaseOwnedSurfaceTexture()
                releaseCompleted.countDown()
            }
            val callbacks = synchronized(releaseCallbackLock) {
                releaseCallbacks.toList().also { releaseCallbacks.clear() }
            }
            callbacks.forEach { callback -> runCatching(callback) }
        }

        private fun registerReleaseCallback(callback: () -> Unit) {
            val invokeImmediately = synchronized(releaseCallbackLock) {
                if (releaseCompleted.count == 0L) {
                    true
                } else {
                    releaseCallbacks.add(callback)
                    false
                }
            }
            if (invokeImmediately) callback()
        }

        private fun releaseOwnedSurfaceTexture() {
            if (surfaceReleased.compareAndSet(false, true)) {
                runCatching { surfaceTexture.release() }
                    .onFailure {
                        Log.w(TAG, "Unable to release owned Rain Night SurfaceTexture", it)
                    }
            }
        }

        private fun revealFallback() {
            val failedSession = this
            this@ListeningRainNightTextureView.post {
                val stillCurrent = synchronized(sessionLock) { session === failedSession }
                if (stillCurrent) this@ListeningRainNightTextureView.alpha = 0f
            }
        }
    }

    private data class RainNightConfig(
        val intensity: Int = 100,
        val animationAllowed: Boolean = true,
        val timelineOriginNanos: Long? = null,
    )

    private class EglWindow private constructor(
        private val display: EGLDisplay,
        private val context: EGLContext,
        private val surface: EGLSurface,
    ) {

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

        companion object {
            fun create(surfaceTexture: SurfaceTexture): EglWindow {
                var display = EGL14.EGL_NO_DISPLAY
                var context = EGL14.EGL_NO_CONTEXT
                var surface = EGL14.EGL_NO_SURFACE
                var initialized = false
                try {
                    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    check(display != EGL14.EGL_NO_DISPLAY) { "Unable to acquire EGL display" }
                    val versions = IntArray(2)
                    check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
                        "Unable to initialize EGL"
                    }
                    initialized = true
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
                    check(context != EGL14.EGL_NO_CONTEXT) {
                        "Unable to create GLES 3 context"
                    }
                    surface = EGL14.eglCreateWindowSurface(
                        display,
                        config,
                        surfaceTexture,
                        intArrayOf(EGL14.EGL_NONE),
                        0,
                    )
                    check(surface != EGL14.EGL_NO_SURFACE) {
                        "Unable to create EGL window surface"
                    }
                    check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                        "Unable to make Rain Night EGL context current"
                    }
                    EGL14.eglSwapInterval(display, 1)
                    return EglWindow(display, context, surface)
                } catch (error: Throwable) {
                    if (display != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(
                            display,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_CONTEXT,
                        )
                        if (surface != EGL14.EGL_NO_SURFACE) {
                            EGL14.eglDestroySurface(display, surface)
                        }
                        if (context != EGL14.EGL_NO_CONTEXT) {
                            EGL14.eglDestroyContext(display, context)
                        }
                        EGL14.eglReleaseThread()
                        if (initialized) EGL14.eglTerminate(display)
                    }
                    throw error
                }
            }
        }
    }

    private companion object {
        const val TAG = "ListeningRainNightTextureView"
        const val EGL_OPENGL_ES3_BIT_KHR = 0x00000040
        const val FRAME_PERIOD_NANOS = 1_000_000_000L / 30L
        const val FRAME_TOLERANCE_NANOS = 1_000_000L
    }
}
