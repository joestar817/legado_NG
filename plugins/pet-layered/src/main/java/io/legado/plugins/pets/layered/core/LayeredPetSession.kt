package io.legado.plugins.pets.layered.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import io.legado.pet.api.PetAssets
import io.legado.pet.api.PetSession
import java.nio.ByteBuffer

/** Author-side support shared by the two layered pets. Compiled into their own packages. */
class LayeredPetSession private constructor(
    private val controller: PetController,
    private val renderer: PetRenderer,
) : PetSession {
    override fun resize(widthDp: Float, heightDp: Float) = controller.resize(widthDp, heightDp)
    override fun setVisible(visible: Boolean) { if (!visible) controller.cancel() }
    override fun draw(canvas: Canvas) =
        renderer.draw(canvas, controller.frame(), controller.widthDp, controller.heightDp)
    override val hitBounds: RectF get() = renderer.hitBounds
    override fun hitTest(x: Float, y: Float) = renderer.hitTest(PetPoint(x, y))
    override fun press(x: Float, y: Float, eventTimeMs: Long) { controller.press(x, y, eventTimeMs) }
    override fun move(x: Float, y: Float, eventTimeMs: Long, dragging: Boolean) =
        controller.move(x, y, eventTimeMs, dragging)
    override fun release(x: Float, y: Float) = controller.release(x, y)
    override fun cancel() = controller.cancel()
    override fun advance(deltaMs: Long) = controller.advance(deltaMs)
    override fun nextFrameDelayMs() = controller.nextFrameDelayMs()

    override fun saveState(): ByteArray {
        val placement = controller.placement()
        return ByteBuffer.allocate(10).apply {
            put(1.toByte())
            put((if (placement.pose == PetPose.PEEK) 1 else 0).toByte())
            putFloat(placement.xFraction)
            putFloat(placement.yFraction)
        }.array()
    }

    override fun restoreState(state: ByteArray) {
        if (state.size != 10) return
        val data = ByteBuffer.wrap(state)
        if (data.get().toInt() != 1) return
        val pose = when (data.get().toInt()) {
            0 -> PetPose.FULL
            1 -> PetPose.PEEK
            else -> return
        }
        val x = data.float
        val y = data.float
        if (x.isFinite() && y.isFinite()) controller.restorePlacement(PetPlacement(pose, x, y))
    }

    override fun close() {
        controller.cancel()
        // Hardware-accelerated Canvas may still hold a reference to the last rendered bitmap.
        // Let Android release published artwork when both the renderer and display list drop it.
    }

    companion object {
        fun create(
            assets: PetAssets,
            controller: PetController,
            createRenderer: (Map<String, Bitmap>) -> PetRenderer,
        ): PetSession {
            val definition = controller.definition
            val artwork = mutableMapOf<String, Bitmap>()
            try {
                for (file in definition.assetFiles) {
                    artwork[file] = assets.open(definition.assetDirectory + "/" + file).use { stream ->
                        requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                            inScaled = false
                            inSampleSize = definition.bitmapSampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        })) { "Invalid pet artwork: " + file }
                    }
                }
                return LayeredPetSession(controller, createRenderer(artwork))
            } catch (failure: Throwable) {
                // No renderer has been published yet.
                artwork.values.forEach(Bitmap::recycle)
                throw failure
            }
        }
    }
}
