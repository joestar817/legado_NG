package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** Rasterize native page commands and captured bitmap textures, never a WebView functor. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object EpubPictureRenderer {
    // The caller owns an independent Picture and stays on its worker until GPU completion.
    fun render(picture: Picture): Bitmap {
        val width = picture.width
        val height = picture.height
        val buffer = HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        var renderer: HardwareBufferRenderer? = null
        var node: RenderNode? = null
        try {
            val drawing = RenderNode("EPUB frozen native page")
            node = drawing
            drawing.setPosition(0, 0, width, height)
            val canvas = drawing.beginRecording(width, height)
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawPicture(picture)
            } finally { drawing.endRecording() }
            val target = HardwareBufferRenderer(buffer)
            renderer = target
            target.setContentRoot(drawing)
            val completion = CompletableFuture<HardwareBufferRenderer.RenderResult>()
            target.obtainRenderRequest().draw(EpubRenderCallbacks.executor) { completion.complete(it) }
            // Cancellation may discard the result, but cannot retire its producer early.
            val result = completion.get()
            result.fence.use { fence ->
                check(result.status == HardwareBufferRenderer.RenderResult.SUCCESS)
                check(!fence.isValid || fence.await(Duration.ofSeconds(2))) { "EPUB page fence timed out" }
            }
            return checkNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)))
        } finally {
            // Only this worker records/discards the node. The immutable Bitmap keeps its
            // own native reference after the sole writable producer has been retired.
            try { renderer?.close() } finally {
                try { node?.discardDisplayList() } finally { buffer.close() }
            }
        }
    }
}
