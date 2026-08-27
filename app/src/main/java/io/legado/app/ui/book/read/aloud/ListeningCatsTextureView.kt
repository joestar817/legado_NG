package io.legado.app.ui.book.read.aloud

import android.app.ActivityManager
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

/** Owns one GLES 3 session for the fixed CATV0001 Curious Cats scene. */
internal class ListeningCatsTextureView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener,
    ListeningCartoonTextureHost {

    private val appContext = context.applicationContext
    private val sessionLock = Any()

    @Volatile
    private var renderConfig = CatsConfig()

    @Volatile
    private var session: RenderSession? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun update(intensity: Int, animationAllowed: Boolean) {
        val config = CatsConfig(
            intensity = intensity.coerceIn(0, 100),
            animationAllowed = animationAllowed,
        )
        val currentSession = synchronized(sessionLock) {
            renderConfig = config
            session
        }
        currentSession?.update(config)
    }

    override fun release() {
        synchronized(sessionLock) { session }?.requestStop()
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
                if (shouldStart && isAvailable && surfaceTexture === surface) nextSession.start()
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
        initialConfig: CatsConfig,
    ) {
        private val motionRenderer = ReadAloudCatsRenderer(appContext).apply {
            update(initialConfig.intensity, initialConfig.animationAllowed)
        }
        private val thread = HandlerThread("ListeningCatsGL", Process.THREAD_PRIORITY_BACKGROUND)
        private val decoderThread = HandlerThread(
            "ListeningCatsDecode",
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

        fun owns(surface: SurfaceTexture): Boolean = surfaceTexture === surface

        fun update(config: CatsConfig) {
            motionRenderer.update(config.intensity, config.animationAllowed)
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
                    check(egl.swapBuffers()) { "Unable to swap initial Cats buffer" }
                    val maximumTextureSize = motionRenderer.onSurfaceCreated()
                    motionRenderer.onSurfaceChanged(latestWidth, latestHeight)
                    val activityManager =
                        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val textureSampleSize = motionRenderer.selectTextureSampleSize(
                        maximumTextureSize = maximumTextureSize,
                        surfaceWidth = latestWidth,
                        surfaceHeight = latestHeight,
                        memoryClassMb = activityManager?.memoryClass,
                    )
                    startAssetDecode(textureSampleSize)
                }.onFailure(::fail)
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

        fun requestStop() {
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

        private fun startAssetDecode(textureSampleSize: Int) {
            if (!active) return
            decoderThread.start()
            decoderHandler = Handler(decoderThread.looper)
            decoderHandler.post decodeScene@{
                val scene = runCatching { motionRenderer.decodeScene() }.getOrElse { error ->
                    postFailure(error)
                    return@decodeScene
                }
                if (!active) return@decodeScene
                val accepted = handler.post uploadScene@{
                    if (!active) return@uploadScene
                    runCatching { motionRenderer.uploadScene(scene) }
                        .onSuccess { decodeTexture(0, textureSampleSize) }
                        .onFailure(::fail)
                }
                if (!accepted) postFailure(IllegalStateException("Cats render thread stopped"))
            }
        }

        private fun decodeTexture(textureIndex: Int, textureSampleSize: Int) {
            if (!active || !decoderThread.isAlive) return
            decoderHandler.post decodeTexture@{
                val texture = runCatching {
                    motionRenderer.decodeTexture(textureIndex, textureSampleSize)
                }.getOrElse { error ->
                    postFailure(error)
                    return@decodeTexture
                }
                if (!active) {
                    texture.bitmap.recycle()
                    return@decodeTexture
                }
                val accepted = handler.post uploadTexture@{
                    if (!active) {
                        texture.bitmap.recycle()
                        return@uploadTexture
                    }
                    val uploadResult = runCatching {
                        motionRenderer.uploadTexture(texture)
                        val next = textureIndex + 1
                        if (next < CatsMotionAssets.TEXTURES.size) {
                            decodeTexture(next, textureSampleSize)
                        } else {
                            motionRenderer.finishUpload()
                            framesReady = true
                            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                            if (decoderThread.isAlive) decoderThread.quitSafely()
                            scheduleFrame()
                        }
                    }
                    texture.bitmap.recycle()
                    uploadResult.onFailure(::fail)
                }
                if (!accepted) {
                    texture.bitmap.recycle()
                    postFailure(IllegalStateException("Cats texture upload rejected"))
                }
            }
        }

        private fun postFailure(error: Throwable) {
            if (::handler.isInitialized && handler.post { fail(error) }) return
            active = false
            Log.e(TAG, "Cats decode pipeline failed", error)
            revealFallback()
            finishSessionWithoutGl()
        }

        private fun fail(error: Throwable) {
            active = false
            Log.e(TAG, "Cats TextureView session failed", error)
            revealFallback()
            finishSession()
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
                check(eglWindow?.swapBuffers() == true) { "Unable to swap Cats EGL buffers" }
            }.onFailure {
                fail(it)
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
                .onFailure { Log.w(TAG, "Unable to release Cats renderer", it) }
            runCatching { eglWindow?.release() }
                .onFailure { Log.w(TAG, "Unable to release Cats EGL window", it) }
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
        }

        private fun releaseOwnedSurfaceTexture() {
            if (surfaceReleased.compareAndSet(false, true)) {
                runCatching { surfaceTexture.release() }
                    .onFailure { Log.w(TAG, "Unable to release owned Cats SurfaceTexture", it) }
            }
        }

        private fun revealFallback() {
            val failedSession = this
            this@ListeningCatsTextureView.post {
                val stillCurrent = synchronized(sessionLock) { session === failedSession }
                if (stillCurrent) this@ListeningCatsTextureView.alpha = 0f
            }
        }
    }

    private data class CatsConfig(
        val intensity: Int = 100,
        val animationAllowed: Boolean = true,
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
                    check(display != EGL14.EGL_NO_DISPLAY)
                    val versions = IntArray(2)
                    check(EGL14.eglInitialize(display, versions, 0, versions, 1))
                    initialized = true
                    val attributes = intArrayOf(
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                        EGL14.EGL_NONE,
                    )
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val count = IntArray(1)
                    check(
                        EGL14.eglChooseConfig(
                            display,
                            attributes,
                            0,
                            configs,
                            0,
                            configs.size,
                            count,
                            0,
                        ) && count[0] > 0
                    )
                    val config = checkNotNull(configs[0])
                    context = EGL14.eglCreateContext(
                        display,
                        config,
                        EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
                        0,
                    )
                    check(context != EGL14.EGL_NO_CONTEXT)
                    surface = EGL14.eglCreateWindowSurface(
                        display,
                        config,
                        surfaceTexture,
                        intArrayOf(EGL14.EGL_NONE),
                        0,
                    )
                    check(surface != EGL14.EGL_NO_SURFACE)
                    check(EGL14.eglMakeCurrent(display, surface, surface, context))
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
                        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                        EGL14.eglReleaseThread()
                        if (initialized) EGL14.eglTerminate(display)
                    }
                    throw error
                }
            }
        }
    }

    private companion object {
        const val TAG = "ListeningCatsTextureView"
        const val EGL_OPENGL_ES3_BIT_KHR = 0x00000040
        const val FRAME_PERIOD_NANOS = 1_000_000_000L / 30L
        const val FRAME_TOLERANCE_NANOS = 1_000_000L
    }
}
