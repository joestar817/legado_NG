package example.pets.orb

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import io.legado.pet.api.PetAssets
import io.legado.pet.api.PetPlugin
import io.legado.pet.api.PetSession
import java.nio.ByteBuffer

/** A complete plugin with no images, character parts or predefined poses. */
class OrbPlugin : PetPlugin {
    override val id = "orb"
    override fun createSession(assets: PetAssets): PetSession = OrbSession()
}

private class OrbSession : PetSession {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val radius = 18f
    private var width = 0f
    private var height = 0f
    private var fractionX = .8f
    private var fractionY = .65f
    private var offsetX = 0f
    private var offsetY = 0f
    private var pressed = false
    private var dragged = false

    private val x get() = width * fractionX
    private val y get() = height * fractionY

    override val hitBounds: RectF
        get() = bounds.apply { set(x - radius, y - radius, x + radius, y + radius) }

    override fun resize(widthDp: Float, heightDp: Float) {
        width = widthDp.coerceAtLeast(0f)
        height = heightDp.coerceAtLeast(0f)
    }

    override fun draw(canvas: Canvas) {
        paint.color = if (pressed) Color.rgb(57, 150, 216) else Color.rgb(81, 183, 238)
        canvas.drawCircle(x, y, radius, paint)
        paint.color = Color.rgb(206, 242, 255)
        canvas.drawCircle(x - 5f, y - 6f, 5f, paint)
    }

    override fun hitTest(x: Float, y: Float): Boolean {
        val dx = x - this.x
        val dy = y - this.y
        return dx * dx + dy * dy <= radius * radius
    }

    override fun press(x: Float, y: Float, eventTimeMs: Long) {
        pressed = true
        dragged = false
        offsetX = this.x - x
        offsetY = this.y - y
    }

    override fun move(x: Float, y: Float, eventTimeMs: Long, dragging: Boolean) {
        if (!pressed || !dragging || width <= 0f || height <= 0f) return
        dragged = true
        fractionX = ((x + offsetX) / width).coerceIn(0f, 1f)
        fractionY = ((y + offsetY) / height).coerceIn(0f, 1f)
    }

    override fun release(x: Float, y: Float): Boolean {
        val activate = pressed && !dragged && hitTest(x, y)
        cancel()
        return activate
    }

    override fun cancel() {
        pressed = false
        dragged = false
    }

    override fun advance(deltaMs: Long) {}
    override fun nextFrameDelayMs(): Long? = null

    override fun saveState(): ByteArray = ByteBuffer.allocate(9)
        .put(1.toByte()).putFloat(fractionX).putFloat(fractionY).array()

    override fun restoreState(state: ByteArray) {
        if (state.size != 9) return
        val saved = ByteBuffer.wrap(state)
        if (saved.get().toInt() != 1) return
        val oldX = saved.float
        val oldY = saved.float
        if (oldX.isFinite() && oldY.isFinite()) {
            fractionX = oldX.coerceIn(0f, 1f)
            fractionY = oldY.coerceIn(0f, 1f)
        }
    }

    override fun setVisible(visible: Boolean) { if (!visible) cancel() }
    override fun close() { cancel() }
}
