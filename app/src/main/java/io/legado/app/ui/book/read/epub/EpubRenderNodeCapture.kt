package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/** Draw the attached, visually confirmed viewport into one immutable hardware output. */
@RequiresApi(Build.VERSION_CODES.Q)
internal object EpubRenderNodeCapture {
    fun capture(view: View, timing: EpubStartupTiming): EpubCapturePixels {
        var reader: ImageReader? = null
        var node: RenderNode? = null
        var renderer: HardwareRenderer? = null
        var image: Image? = null
        var buffer: HardwareBuffer? = null
        var bitmap: Bitmap? = null
        var handedOff = false
        try {
            try {
                val width = view.width
                val height = view.height
                check(width > 0 && height > 0 && view.isAttachedToWindow)
                val output = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
                reader = output
                val drawing = RenderNode("EPUB prepared viewport")
                node = drawing
                drawing.setPosition(0, 0, width, height)
                val target = HardwareRenderer()
                renderer = target
                target.setSurface(output.surface)
                target.setContentRoot(drawing)
                target.setOpaque(false)
                val canvas = drawing.beginRecording(width, height)
                try { view.draw(canvas) } finally { drawing.endRecording() }
                timing.mark("node-recorded")
                val status = target.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                check(status == HardwareRenderer.SYNC_OK || status == HardwareRenderer.SYNC_REDRAW_REQUESTED) {
                    "EPUB hardware frame was not produced: $status"
                }
                timing.mark("node-rendered")
                val acquired = checkNotNull(output.acquireNextImage())
                image = acquired
                val nativeBuffer = checkNotNull(acquired.hardwareBuffer)
                buffer = nativeBuffer
                bitmap = checkNotNull(Bitmap.wrapHardwareBuffer(nativeBuffer, ColorSpace.get(ColorSpace.Named.SRGB)))
            } finally {
                // Retire the sole producer before closing its acquired buffer. The Bitmap
                // retains its own native reference, with no writable producer left behind.
                try { renderer?.destroy() } finally {
                    try { node?.discardDisplayList() } finally {
                        try { buffer?.close() } finally {
                            try { image?.close() } finally { reader?.close() }
                        }
                    }
                }
            }
            val pixels = EpubCapturePixels(checkNotNull(bitmap))
            handedOff = true
            return pixels
        } finally {
            if (!handedOff) bitmap?.recycle()
        }
    }
}
