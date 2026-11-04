package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.view.SurfaceControl
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** One immutable buffer per capture. Producers retire before their pixels reach the frame window. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object EpubHardwareBufferCapture {
    private val main = Handler(Looper.getMainLooper())
    private val executor = Dispatchers.IO.asExecutor()

    fun eligible(view: View): Boolean {
        val host = view as? FrameLayout ?: return false
        if (!host.isAttachedToWindow || host.width <= 0 || host.height <= 0 || host.isLayoutRequested ||
            host.childCount != 1 || host.foreground != null || host.scrollX != 0 || host.scrollY != 0 ||
            host.paddingLeft != 0 || host.paddingTop != 0 || host.paddingRight != 0 || host.paddingBottom != 0) return false
        val child = host.getChildAt(0) as? EpubLayoutSurface ?: return false
        val ready = child.readyState ?: return false
        if (child.left != 0 || child.top != 0 || child.width != host.width || child.height != host.height ||
            child.isLayoutRequested || child.alpha != 1f || !child.matrix.isIdentity || child.clipBounds != null ||
            child.background != null || child.foreground != null || child.clipToOutline ||
            child.elevation != 0f || child.translationZ != 0f || child.webView.alpha != 1f ||
            ready.optString("status") != "ready" || ready.optString("mode") != "HORIZONTAL" || !ready.optBoolean("canvas") ||
            ready.optBoolean("scrolled") || ready.optBoolean("cover") || ready.optBoolean("busy") ||
            ready.optJSONArray("media")?.length() != 0 || ready.optJSONArray("galleryIndexes")?.length() != 0) return false
        return host.background?.bounds?.let { it == Rect(0, 0, host.width, host.height) } != false
    }

    /** Require exactly one direction to match the original backend on this host. Symmetric pages cannot qualify it. */
    fun qualify(view: View, timing: EpubStartupTiming, isCurrent: () -> Boolean,
                callback: (Boolean?, Result<EpubCapturePixels>) -> Unit) {
        EpubRenderNodeCaptureAsync.capture(view, timing) reference@ { result ->
            val reference = result.getOrNull() ?: run { callback(null, result); return@reference }
            executor.execute {
                val copied = runCatching { checkNotNull(reference.bitmap.copy(Bitmap.Config.ARGB_8888, false)) }
                main.post copied@ {
                    val baseline = copied.getOrNull()
                    if (baseline == null || !isCurrent()) {
                        baseline?.recycle()
                        callback(null, Result.success(reference))
                        return@copied
                    }
                    fun finish(flipY: Boolean?) {
                        baseline.recycle()
                        callback(flipY.takeIf { isCurrent() }, Result.success(reference))
                    }
                    fun compare(flipY: Boolean, done: (Boolean?) -> Unit) {
                        if (!isCurrent()) { done(null); return }
                        capture(view, flipY, timing) candidate@ { captured ->
                            val pixels = captured.getOrNull() ?: run { done(null); return@candidate }
                            executor.execute {
                                val matches = runCatching {
                                    val software = checkNotNull(pixels.bitmap.copy(Bitmap.Config.ARGB_8888, false))
                                    try { baseline.sameAs(software) } finally { software.recycle() }
                                }.getOrNull()
                                pixels.close()
                                main.post { done(matches) }
                            }
                        }
                    }
                    compare(false) normal@ { normal ->
                        if (normal == null || !isCurrent()) { finish(null); return@normal }
                        compare(true) { mirrored ->
                            finish(mirrored?.takeIf { it != normal })
                        }
                    }
                }
            }
        }
    }

    fun capture(view: View, flipY: Boolean, timing: EpubStartupTiming, callback: (Result<EpubCapturePixels>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        var buffer: HardwareBuffer? = null
        var node: RenderNode? = null
        var renderer: HardwareBufferRenderer? = null
        val rendered = CompletableFuture<HardwareBufferRenderer.RenderResult>()
        val submission = runCatching {
            check(eligible(view))
            val output = HardwareBuffer.create(view.width, view.height, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            buffer = output
            timing.mark("buffer-created")
            val drawing = RenderNode("EPUB immutable hardware buffer")
            node = drawing
            drawing.setPosition(0, 0, view.width, view.height)
            val target = HardwareBufferRenderer(output)
            renderer = target
            target.setContentRoot(drawing)
            val canvas = drawing.beginRecording(view.width, view.height)
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                if (flipY) {
                    val host = view as FrameLayout
                    host.background?.draw(canvas)
                    val saved = canvas.save()
                    try {
                        // Some WebView functors invert Y on a direct buffer. The native
                        // background retains its original coordinates; qualify() proves the choice.
                        canvas.translate(0f, view.height.toFloat())
                        canvas.scale(1f, -1f)
                        host.getChildAt(0).draw(canvas)
                    } finally { canvas.restoreToCount(saved) }
                } else view.draw(canvas)
            } finally { drawing.endRecording() }
            timing.mark("buffer-recorded")
            target.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                .setBufferTransform(SurfaceControl.BUFFER_TRANSFORM_IDENTITY).draw(EpubRenderCallbacks.executor) { result ->
                    if (!rendered.complete(result)) result.fence?.close()
                }
            timing.mark("buffer-submitted")
        }
        executor.execute {
            var consumed = false
            var result = runCatching {
                submission.getOrThrow()
                val produced = rendered.get(2, TimeUnit.SECONDS)
                consumed = true
                produced.fence.use { fence ->
                    check(produced.status == HardwareBufferRenderer.RenderResult.SUCCESS) { "EPUB buffer render failed" }
                    check(!fence.isValid || fence.await(Duration.ofSeconds(2))) { "EPUB buffer fence timed out" }
                }
                timing.mark("buffer-gpu-ready")
                checkNotNull(Bitmap.wrapHardwareBuffer(checkNotNull(buffer), ColorSpace.get(ColorSpace.Named.SRGB)))
            }
            // Cancellation races with native callback delivery. Retire an unconsumed fence too.
            rendered.cancel(false)
            if (!consumed && rendered.isDone && !rendered.isCompletedExceptionally && !rendered.isCancelled)
                runCatching { rendered.getNow(null)?.fence?.close() }
            val retired = runCatching {
                try { renderer?.close() } finally { buffer?.close() }
            }
            if (retired.isFailure) {
                result.getOrNull()?.recycle()
                result = Result.failure(retired.exceptionOrNull()!!)
            }
            timing.mark("buffer-retired")
            main.post {
                val discarded = runCatching { node?.discardDisplayList() }
                if (discarded.isFailure) {
                    result.getOrNull()?.recycle()
                    callback(Result.failure(discarded.exceptionOrNull()!!))
                } else {
                    timing.mark("buffer-frame-ready")
                    callback(result.map { EpubCapturePixels(it) })
                }
            }
        }
    }
}
