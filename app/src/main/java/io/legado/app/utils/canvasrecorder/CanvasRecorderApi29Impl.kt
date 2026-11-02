package io.legado.app.utils.canvasrecorder

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Picture
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import io.legado.app.utils.objectpool.synchronized
import io.legado.app.utils.canvasrecorder.pools.PicturePool
import io.legado.app.utils.canvasrecorder.pools.RenderNodePool

@RequiresApi(Build.VERSION_CODES.Q)
class CanvasRecorderApi29Impl : BaseCanvasRecorder() {

    private var renderNode: RenderNode? = null
    private var picture: Picture? = null

    override val width get() = renderNode?.width ?: -1
    override val height get() = renderNode?.height ?: -1

    private fun init() {
        if (renderNode == null) {
            renderNode = renderNodePool.obtain()
        }
        if (picture == null) {
            picture = picturePool.obtain()
        }
    }

    private fun flushRenderNode() {
        val rc = renderNode!!.beginRecording()
        rc.drawPicture(picture!!)
        renderNode!!.endRecording()
    }

    override fun beginRecording(width: Int, height: Int): Canvas {
        init()
        renderNode!!.setPosition(0, 0, width, height)
        return picture!!.beginRecording(width, height)
    }

    override fun endRecording() {
        picture!!.endRecording()
        flushRenderNode()
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        if (renderNode == null || picture == null) {
            return
        }
        if (canvas.isHardwareAccelerated) {
            if (!renderNode!!.hasDisplayList()) {
                flushRenderNode()
            }
            canvas.drawRenderNode(renderNode!!)
        } else {
            canvas.drawPicture(picture!!)
        }
    }

    /** Explicit software consumers may request pixels from a picture containing GPU images. */
    fun snapshotBitmap(): Bitmap {
        val bitmap = renderBitmap()
        if (bitmap.config != Bitmap.Config.HARDWARE) return bitmap
        try { return checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) }
        finally { bitmap.recycle() }
    }

    /** Preserve the hardware result when the picture requires GPU images. */
    fun renderBitmap(): Bitmap = Bitmap.createBitmap(checkNotNull(picture))

    /** An independent completed display list; the receiver may hand it to one worker. */
    fun snapshotPicture(): Picture = Picture(checkNotNull(picture))

    override fun recycle() {
        super.recycle()
        if (renderNode == null || picture == null) return
        renderNodePool.recycle(renderNode!!)
        renderNode = null
        // Pooling the Picture must not retain old bitmap/native-buffer commands after
        // the owner's source leases have been released.
        picture!!.beginRecording(1, 1)
        picture!!.endRecording()
        picturePool.recycle(picture!!)
        picture = null
    }

    companion object {
        private val picturePool = PicturePool().synchronized()
        private val renderNodePool = RenderNodePool().synchronized()
    }

}
