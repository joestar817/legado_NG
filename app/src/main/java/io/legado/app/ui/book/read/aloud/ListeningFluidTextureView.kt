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
import io.legado.app.help.config.ListeningFluidType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Translucent 60 fps GLES 3 host for the stable-fluid layer. */
internal class ListeningFluidTextureView(context: Context) :
    TextureView(context),
    TextureView.SurfaceTextureListener {

    private val sessionLock = Any()

    @Volatile
    private var renderConfig = FluidConfig()

    @Volatile
    private var session: RenderSession? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun update(type: ListeningFluidType, intensity: Int) {
        val config = FluidConfig(type, intensity.coerceIn(0, 100))
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
        initialConfig: FluidConfig,
    ) {
        private val motionRenderer = ReadAloudStableFluidRenderer().apply {
            update(initialConfig.type, initialConfig.intensity)
        }
        private val thread = HandlerThread("ListeningFluidGL", Process.THREAD_PRIORITY_BACKGROUND)
        private lateinit var handler: Handler

        @Volatile
        private var active = true
        private var eglWindow: EglWindow? = null
        private var choreographer: Choreographer? = null
        private var nextFrameTimeNanos = 0L
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

        fun update(config: FluidConfig) {
            motionRenderer.update(config.type, config.intensity)
        }

        fun start() {
            if (!active) return
            thread.start()
            handler = Handler(thread.looper)
            handler.post {
                if (!active) return@post
                runCatching {
                    choreographer = Choreographer.getInstance()
                    eglWindow = EglWindow(surfaceTexture)
                    GLES30.glClearColor(0f, 0f, 0f, 0f)
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                    check(eglWindow?.swapBuffers() == true) {
                        "Unable to swap initial transparent fluid buffer"
                    }
                    motionRenderer.onSurfaceCreated()
                    motionRenderer.onSurfaceChanged(latestWidth, latestHeight)
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                    choreographer?.postFrameCallback(frameCallback)
                }.onFailure { error ->
                    active = false
                    Log.w(TAG, "Stable fluid disabled for this surface", error)
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
                check(eglWindow?.swapBuffers() == true) { "Unable to swap fluid EGL buffers" }
            }.onFailure { error ->
                active = false
                Log.w(TAG, "Stable fluid frame failed; effect disabled", error)
                finishSession()
                return
            }
            do {
                nextFrameTimeNanos += FRAME_PERIOD_NANOS
            } while (nextFrameTimeNanos <= frameTimeNanos)
            if (active) choreographer?.postFrameCallback(frameCallback)
        }

        private fun releaseOnRenderThread() {
            choreographer?.removeFrameCallback(frameCallback)
            choreographer = null
            runCatching { motionRenderer.release() }
                .onFailure { Log.w(TAG, "Unable to release fluid renderer", it) }
            runCatching { eglWindow?.release() }
                .onFailure { Log.w(TAG, "Unable to release fluid EGL window", it) }
            eglWindow = null
        }

        private fun finishSession() {
            if (!finished.compareAndSet(false, true)) return
            try {
                releaseOnRenderThread()
            } finally {
                if (releaseSurfaceWhenFinished) releaseOwnedSurfaceTexture()
                releaseCompleted.countDown()
                thread.quitSafely()
            }
        }

        private fun finishSessionWithoutGl() {
            if (!finished.compareAndSet(false, true)) return
            if (releaseSurfaceWhenFinished) releaseOwnedSurfaceTexture()
            releaseCompleted.countDown()
            if (thread.isAlive) thread.quitSafely()
        }

        private fun releaseOwnedSurfaceTexture() {
            if (surfaceReleased.compareAndSet(false, true)) {
                runCatching { surfaceTexture.release() }
                    .onFailure { Log.w(TAG, "Unable to release owned fluid SurfaceTexture", it) }
            }
        }
    }

    private data class FluidConfig(
        val type: ListeningFluidType = ListeningFluidType.SMOKE,
        val intensity: Int = 100,
    )

    private class EglWindow(surfaceTexture: SurfaceTexture) {
        private val display: EGLDisplay
        private val context: EGLContext
        private val surface: EGLSurface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "Unable to acquire EGL display" }
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) { "Unable to initialize EGL" }
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "Unable to choose translucent GLES 3 config"
            }
            val config = checkNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "Unable to create GLES 3 context" }
            surface = EGL14.eglCreateWindowSurface(display, config, surfaceTexture,
                intArrayOf(EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                "Unable to make fluid EGL context current"
            }
            EGL14.eglSwapInterval(display, 1)
        }

        fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(display, surface)

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    }

    private companion object {
        const val TAG = "ListeningFluidTextureView"
        const val EGL_OPENGL_ES3_BIT_KHR = 0x00000040
        const val FRAME_PERIOD_NANOS = 1_000_000_000L / 60L
        const val FRAME_TOLERANCE_NANOS = 1_000_000L
        const val RELEASE_TIMEOUT_MILLIS = 250L
    }
}
