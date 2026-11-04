package io.legado.app.ui.book.read.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.webkit.WebViewFeature
import io.legado.app.model.epub.EpubPublicationSession
import io.legado.app.model.epub.EpubResourceLink
import org.json.JSONObject
import java.io.Closeable

internal data class EpubCapturedFrame(
    private val pixels: EpubCapturePixels,
    val clip: Rect?,
    val canvas: Boolean = false,
    val hideHeader: Boolean = false,
    val hideFooter: Boolean = false,
    val pageIndex: Int = -1,
    val staticPage: Boolean = false,
) : Closeable {
    val bitmap: Bitmap get() = pixels.bitmap
    override fun close() = pixels.close()
}

/** Captures both frames from the same hardware viewport before the visible document is changed. */
internal class EpubPreparedTurn(
    context: Context,
    publication: EpubPublicationSession,
    private val displayWidth: Int,
    private val displayHeight: Int,
    private val viewport: Rect,
    private val hardwareFrames: Boolean,
    private val drawBackground: (Canvas) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val setContent: (EpubLayoutSurface, EpubResourceLink, JSONObject) -> Unit,
) : Closeable {
    private var closed = false
    private val hasVisualStateFence = WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)
    private val main = Handler(Looper.getMainLooper())
    private val watchdog = EpubRenderWatchdog({ task, delay -> main.postDelayed(task, delay); Unit }, main::removeCallbacks)
    private var surface: EpubLayoutSurface? = null
    private var captureHost: android.widget.FrameLayout? = null
    private val background = if (hardwareFrames) null else Bitmap.createBitmap(viewport.width(), viewport.height(), Bitmap.Config.ARGB_8888)
    private var before: EpubCapturedFrame? = null
    private var phase = 0
    private var job: Job? = null
    val hasPendingJob: Boolean get() = job != null
    private var timing: EpubStartupTiming? = null
    private var capturing = false
    val waitingFor: String get() = if (capturing) capture.waitingFor else "等待准备页排版"
    private data class Job(
        val beforeLocation: EpubResourceLink, val beforeOptions: JSONObject,
        val afterLocation: EpubResourceLink, val afterOptions: JSONObject,
        val callback: ((EpubCapturedFrame, EpubCapturedFrame) -> Unit)?,
        val single: ((EpubCapturedFrame) -> Unit)? = null,
        val bounds: ((JSONObject?) -> Unit)? = null,
        val allowBufferQualification: () -> Boolean = { false },
    )
    private val capture = EpubHardwareCapture(context, displayWidth, displayHeight, viewport, hardwareFrames,
        onHost = { host ->
            if (!closed) {
                captureHost = host
                host.background = background?.let { BitmapDrawable(host.resources, it) } ?: object : Drawable() {
                    override fun draw(canvas: Canvas) {
                        val saved = canvas.save()
                        canvas.translate(-viewport.left.toFloat(), -viewport.top.toFloat())
                        drawBackground(canvas)
                        canvas.restoreToCount(saved)
                    }
                    override fun setAlpha(alpha: Int) = Unit
                    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
                    @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
                }
                val view = EpubLayoutSurface(host.context, publication,
                    onReady = { captureReady() },
                    onError = { fail(IllegalStateException(it)) },
                    captureOnly = true,
                )
                surface = view
                host.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
                job?.let { show(it.beforeLocation, it.beforeOptions) }
            }
        },
        onError = ::fail,
    )

    fun prepare(
        nativeFrame: Bitmap?,
        beforeLocation: EpubResourceLink, beforeOptions: JSONObject,
        afterLocation: EpubResourceLink, afterOptions: JSONObject,
        callback: (EpubCapturedFrame, EpubCapturedFrame) -> Unit,
    ) {
        check(!closed && job == null)
        // Own one mutable background, never an aliased crop of a frame later recycled by NG.
        updateBackground(nativeFrame)
        phase = 0
        job = Job(beforeLocation, beforeOptions, afterLocation, afterOptions, callback)
        watchJob()
        show(beforeLocation, beforeOptions)
    }

    fun capturePage(nativeFrame: Bitmap?, location: EpubResourceLink, options: JSONObject,
                    allowBufferQualification: () -> Boolean = { false },
                    callback: (EpubCapturedFrame) -> Unit) {
        check(!closed && job == null)
        updateBackground(nativeFrame)
        phase = 0
        job = Job(location, options, location, options, null, callback,
            allowBufferQualification = allowBufferQualification)
        watchJob()
        show(location, options)
    }

    private fun show(location: EpubResourceLink, options: JSONObject) {
        capturing = false
        val view = surface ?: return
        setContent(view, location, options)
        if (view.movePrepared(location, options)) timing?.mark("seek-reused")
        else {
            // New content and style changes retain the normal window-rendering lifecycle.
            captureHost?.alpha = 1f
            timing?.mark("configure-required")
            view.open(location, options)
        }
    }

    private fun updateBackground(nativeFrame: Bitmap?) {
        background?.let { Canvas(it).drawBitmap(checkNotNull(nativeFrame), -viewport.left.toFloat(), -viewport.top.toFloat(), null) }
        captureHost?.invalidate()
    }

    fun preloadDocument(documents: List<EpubPreparedContent.Document>, index: Int, generation: String,
                        configuration: () -> JSONObject) {
        if (closed || job != null || !hardwareFrames || captureHost?.alpha != 0f ||
            !capture.canSuppressWindowDraw) return
        surface?.preloadPreparedDocument(documents, index, generation, configuration)
    }

    fun measurePageBounds(nativeFrame: Bitmap, location: EpubResourceLink, options: JSONObject,
                          callback: (JSONObject?) -> Unit) {
        check(!closed && job == null)
        updateBackground(nativeFrame)
        job = Job(location, options, location, options, null, bounds = callback)
        watchJob()
        show(location, options)
    }

    private fun watchJob() {
        timing = EpubStartupTiming("frame-prepare")
        watchdog.arm { fail(IllegalStateException("EPUB 翻页画面准备超时（$waitingFor）")) }
    }

    private fun fail(error: Throwable) {
        if (closed) return
        close()
        onError(error)
    }

    private fun captureReady() {
        if (closed) return
        val current = job ?: return
        timing?.mark("layout-ready")
        current.bounds?.let { callback ->
            surface?.interact(JSONObject().put("action", "pageBounds").put("index", current.beforeOptions.optInt("index"))) {
                if (!closed && job === current) { watchdog.cancel(); job = null; callback(it) }
            }
            return
        }
        val view = surface ?: return
        val ready = view.readyState ?: return
        val clip = view.clipBounds?.let(::Rect)
        // These belong to this captured document, never the visible source chapter.
        val canvas = ready.optBoolean("canvas")
        val hideHeader = ready.optBoolean("hideHeader")
        val hideFooter = ready.optBoolean("hideFooter")
        capturing = true
        view.invalidatePreparedFrame()
        capture.capture(current.allowBufferQualification()) { pixels ->
            if (closed || job !== current) { pixels.close(); return@capture }
            val staticPage = ready.optString("mode") == "HORIZONTAL" && !ready.optBoolean("scrolled") &&
                !ready.optBoolean("cover") && (ready.optJSONArray("media")?.length() ?: 0) == 0 &&
                (ready.optJSONArray("galleryIndexes")?.length() ?: 0) == 0
            // Native capture draws this host directly. Its own alpha only controls the
            // redundant Presentation draw; the attached WebView and visual fence stay active.
            captureHost?.alpha = if (staticPage && hasVisualStateFence && capture.canSuppressWindowDraw) 0f else 1f
            val frame = EpubCapturedFrame(pixels, clip, canvas, hideHeader, hideFooter,
                ready.optInt("pageIndex", -1), staticPage)
            timing?.mark("frame-ready")
            if (current.single != null) {
                watchdog.cancel()
                job = null
                current.single.invoke(frame)
                return@capture
            }
            if (phase == 0) {
                before = frame
                phase = 1
                show(current.afterLocation, current.afterOptions)
            } else {
                val first = before
                before = null
                phase = 2
                watchdog.cancel()
                job = null
                if (first == null) frame.close() else current.callback?.invoke(first, frame)
            }
        }
    }

    fun matches(width: Int, height: Int, bounds: Rect, hardware: Boolean): Boolean =
        !closed && displayWidth == width && displayHeight == height && viewport == bounds && hardwareFrames == hardware

    /** All callers also invalidate their own request epoch before dropping callbacks. */
    fun cancel() {
        if (closed) return
        watchdog.cancel()
        job = null
        timing = null
        before?.close()
        before = null
        phase = 0
        capturing = false
        capture.cancel()
        surface?.cancelPreparation(keepDocument = true)
    }

    override fun close() {
        if (closed) return
        closed = true
        watchdog.cancel()
        job = null
        surface?.close()
        surface = null
        captureHost?.background = null
        captureHost = null
        before?.close()
        before = null
        capture.close()
        background?.recycle()
    }
}
