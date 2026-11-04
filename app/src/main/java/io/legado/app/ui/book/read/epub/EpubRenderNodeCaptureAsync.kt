package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Keep View recording on UI, and consume/retire the one-shot producer off UI. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object EpubRenderNodeCaptureAsync {
    private val main = Handler(Looper.getMainLooper())

    fun capture(view: View, timing: EpubStartupTiming, callback: (Result<EpubCapturePixels>) -> Unit) {
        check(view.isAttachedToWindow)
        render(view.width, view.height, timing, view::draw) { result -> callback(result.map { EpubCapturePixels(it) }) }
    }

    fun render(width: Int, height: Int, timing: EpubStartupTiming, draw: (Canvas) -> Unit,
               callback: (Result<Bitmap>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        var reader: ImageReader? = null
        var node: RenderNode? = null
        var renderer: HardwareRenderer? = null
        val executor = Dispatchers.IO.asExecutor()
        val committed = CompletableFuture<Unit>()
        val submission = runCatching {
            check(width > 0 && height > 0)
            val output = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            reader = output
            timing.mark("async-node-reader-created")
            val drawing = RenderNode("EPUB asynchronous frozen frame")
            node = drawing
            drawing.setPosition(0, 0, width, height)
            val target = HardwareRenderer()
            renderer = target
            timing.mark("async-node-owner-created")
            target.setSurface(output.surface)
            target.setContentRoot(drawing)
            target.setOpaque(false)
            timing.mark("async-node-surface-bound")
            val canvas = drawing.beginRecording(width, height)
            try { draw(canvas) } finally { drawing.endRecording() }
            timing.mark("async-node-recorded")
            val status = target.createRenderRequest()
                .setFrameCommitCallback(EpubRenderCallbacks.executor) { committed.complete(Unit) }
                .setWaitForPresent(false).syncAndDraw()
            check(status == HardwareRenderer.SYNC_OK || status == HardwareRenderer.SYNC_REDRAW_REQUESTED) {
                "EPUB hardware frame was not produced: $status"
            }
            timing.mark("async-node-submitted")
        }
        // After syncAndDraw returns, this worker exclusively owns the renderer and
        // output. A commit means the buffer has entered the swap chain; its fence
        // still guards pixels. Main need not drain ImageReader or wait in destroy().
        executor.execute {
            var image: Image? = null
            var buffer: HardwareBuffer? = null
            var result = runCatching {
                submission.getOrThrow()
                committed.get(2, TimeUnit.SECONDS)
                val acquired = checkNotNull(checkNotNull(reader).acquireNextImage())
                image = acquired
                val nativeBuffer = checkNotNull(acquired.hardwareBuffer)
                buffer = nativeBuffer
                timing.mark("async-node-frame-acquired")
                acquired.fence.use { fence ->
                    check(!fence.isValid || fence.await(Duration.ofSeconds(2))) { "EPUB hardware frame fence timed out" }
                }
                timing.mark("async-node-gpu-ready")
                checkNotNull(Bitmap.wrapHardwareBuffer(nativeBuffer, ColorSpace.get(ColorSpace.Named.SRGB)))
            }
            val retired = runCatching {
                // Retire the only writable producer before releasing its image. No
                // buffers are pooled or reused while a frozen page can still read them.
                try { renderer?.let { it.destroy(); timing.mark("async-node-owner-destroyed") } } finally {
                    try { buffer?.close() } finally {
                        try { image?.close() } finally { reader?.close() }
                    }
                }
            }
            if (retired.isFailure) {
                result.getOrNull()?.recycle()
                result = Result.failure(retired.exceptionOrNull()!!)
            }
            timing.mark("async-node-retired")
            main.post {
                // RenderNode is recorded/discarded only on its original UI thread.
                val discarded = runCatching { node?.discardDisplayList() }
                if (discarded.isFailure) {
                    result.getOrNull()?.recycle()
                    callback(Result.failure(discarded.exceptionOrNull()!!))
                } else {
                    timing.mark("async-node-frame-ready")
                    callback(result)
                }
            }
        }
    }
}
