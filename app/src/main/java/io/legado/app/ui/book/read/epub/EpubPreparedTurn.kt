package io.legado.app.ui.book.read.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import io.legado.app.model.epub.EpubPublicationSession
import io.legado.app.model.epub.EpubResourceLink
import org.json.JSONObject
import java.io.Closeable

internal data class EpubCapturedFrame(val bitmap: Bitmap, val clip: Rect?) : Closeable {
    override fun close() = bitmap.recycle()
}

/** Captures both frames from the same hardware viewport before the visible document is changed. */
internal class EpubPreparedTurn(
    context: Context,
    publication: EpubPublicationSession,
    displayWidth: Int,
    displayHeight: Int,
    private val viewport: Rect,
    onError: (Throwable) -> Unit,
    private val setContent: (EpubLayoutSurface, EpubResourceLink, JSONObject) -> Unit,
) : Closeable {
    private var closed = false
    private var surface: EpubLayoutSurface? = null
    private var captureHost: android.widget.FrameLayout? = null
    private val background = Bitmap.createBitmap(viewport.width(), viewport.height(), Bitmap.Config.ARGB_8888)
    private var before: EpubCapturedFrame? = null
    private var phase = 0
    private var job: Job? = null
    private data class Job(
        val beforeLocation: EpubResourceLink, val beforeOptions: JSONObject,
        val afterLocation: EpubResourceLink, val afterOptions: JSONObject,
        val callback: ((EpubCapturedFrame, EpubCapturedFrame) -> Unit)?,
        val single: ((EpubCapturedFrame) -> Unit)? = null,
        val bounds: ((JSONObject?) -> Unit)? = null,
    )
    private val capture = EpubHardwareCapture(context, displayWidth, displayHeight, viewport,
        onHost = { host ->
            if (!closed) {
                captureHost = host
                host.background = BitmapDrawable(host.resources, background)
                val view = EpubLayoutSurface(host.context, publication,
                    onReady = { captureReady() },
                    onError = { onError(IllegalStateException(it)) },
                )
                surface = view
                host.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
                job?.let { show(it.beforeLocation, it.beforeOptions) }
            }
        },
        onError = onError,
    )

    fun prepare(
        nativeFrame: Bitmap,
        beforeLocation: EpubResourceLink, beforeOptions: JSONObject,
        afterLocation: EpubResourceLink, afterOptions: JSONObject,
        callback: (EpubCapturedFrame, EpubCapturedFrame) -> Unit,
    ) {
        check(!closed && job == null)
        // Own one mutable background, never an aliased crop of a frame later recycled by NG.
        Canvas(background).drawBitmap(nativeFrame, -viewport.left.toFloat(), -viewport.top.toFloat(), null)
        captureHost?.invalidate()
        phase = 0
        job = Job(beforeLocation, beforeOptions, afterLocation, afterOptions, callback)
        show(beforeLocation, beforeOptions)
    }

    fun capturePage(nativeFrame: Bitmap, location: EpubResourceLink, options: JSONObject,
                    callback: (EpubCapturedFrame) -> Unit) {
        check(!closed && job == null)
        Canvas(background).drawBitmap(nativeFrame, -viewport.left.toFloat(), -viewport.top.toFloat(), null)
        captureHost?.invalidate()
        phase = 0
        job = Job(location, options, location, options, null, callback)
        show(location, options)
    }

    private fun show(location: EpubResourceLink, options: JSONObject) {
        val view = surface ?: return
        setContent(view, location, options)
        if (!view.movePrepared(location, options)) view.open(location, options)
    }

    fun measurePageBounds(nativeFrame: Bitmap, location: EpubResourceLink, options: JSONObject,
                          callback: (JSONObject?) -> Unit) {
        check(!closed && job == null)
        Canvas(background).drawBitmap(nativeFrame, -viewport.left.toFloat(), -viewport.top.toFloat(), null)
        job = Job(location, options, location, options, null, bounds = callback)
        show(location, options)
    }

    private fun captureReady() {
        if (closed) return
        val current = job ?: return
        current.bounds?.let { callback ->
            surface?.interact(JSONObject().put("action", "pageBounds").put("index", current.beforeOptions.optInt("index"))) {
                if (!closed && job === current) { job = null; callback(it) }
            }
            return
        }
        val clip = surface?.clipBounds?.let(::Rect)
        capture.capture { bitmap ->
            if (closed) { bitmap.recycle(); return@capture }
            val frame = EpubCapturedFrame(bitmap, clip)
            if (current.single != null) {
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
                job = null
                if (first == null) frame.close() else current.callback?.invoke(first, frame)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        job = null
        surface?.close()
        surface = null
        captureHost?.background = null
        captureHost = null
        before?.close()
        before = null
        capture.close()
        background.recycle()
    }
}
