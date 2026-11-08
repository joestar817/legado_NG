package io.legado.plugins.pets.layered

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.legado.plugins.pets.layered.core.BlueFishCharacter
import io.legado.plugins.pets.layered.core.BlueFishScooterVisual
import io.legado.plugins.pets.layered.core.BlueFishVisual
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Draws one solid direction pose at a time, with every tyre contact on the same ground. */
class BlueFishScooterRenderer(private val bitmaps: Map<String, Bitmap>) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(31, 47, 72, 87) }
    private val sourceRect = RectF(0f, 0f, BlueFishCharacter.SOURCE_SIZE, BlueFishCharacter.SOURCE_SIZE)
    private val wheelClip = Path()
    private val spoke = Path().apply {
        moveTo(-7f, 0f); lineTo(-11f, -51f); lineTo(10f, -51f); lineTo(7f, 0f); close()
    }

    /** Canvas is already in the standard 1254 source space used by the native pet renderer. */
    fun draw(canvas: Canvas, visual: BlueFishVisual) {
        val motion = visual.scooter ?: return
        if (motion.hidden) return
        val rootX = BlueFishCharacter.ROOT_X
        val rootY = BlueFishCharacter.ROOT_Y
        val shadowWidth = 1200f * (.34f - if (motion.turning) .08f * sin(motion.yaw * PI).toFloat() else 0f)
        val shadowY = rootY + 2f / BlueFishCharacter.definition.scale
        canvas.drawOval(rootX - shadowWidth, shadowY - 21.6f, rootX + shadowWidth, shadowY + 21.6f, shadowPaint)
        canvas.save()
        // Native root = (640,1230), while the scooter's ground = 1205. Preserve
        // the accepted 128dp ride height without moving the engine's anchor.
        canvas.translate(rootX, rootY)
        canvas.scale(1200f / 1150f, 1200f / 1150f)
        canvas.translate(-640f, -1205f)
        if (motion.turning) drawTurn(canvas, motion.yaw) else drawRide(canvas, motion)
        canvas.restore()
    }

    private fun drawRide(canvas: Canvas, motion: BlueFishScooterVisual) {
        val file = if (motion.rightward) RIGHT else if (motion.push) PUSH else LEFT
        drawFull(canvas, file)
        if (motion.rightward) {
            drawWheel(canvas, 354f, 1130f, motion.wheelAngle)
            drawWheel(canvas, 1005f, 1128f, motion.wheelAngle, RIGHT)
        } else {
            drawWheel(canvas, 309f, 1127f, motion.wheelAngle, LEFT)
            drawWheel(canvas, 925f, 1129f, motion.wheelAngle)
        }
    }

    private fun drawTurn(canvas: Canvas, yaw: Float) {
        val progress = yaw.coerceIn(0f, 1f)
        val angle = progress * 180f - 90f
        var pose = poses[0]
        for (index in 1 until poses.size) {
            if (abs(poses[index].angle - angle) < abs(pose.angle - angle)) pose = poses[index]
        }
        val scale = 1150f / (pose.ground - pose.top)
        canvas.save()
        canvas.translate(617f + 63f * progress, 1205f)
        canvas.scale(scale, scale)
        canvas.translate(-pose.pivot, -pose.ground)
        // Only the three isolated bottom-row portraits of the near-front sheet
        // are valid. Do not draw its overlapping upper-row source experiments.
        pose.crop?.let { canvas.clipRect(it) }
        drawFull(canvas, pose.file)
        canvas.restore()
    }

    private fun drawWheel(canvas: Canvas, x: Float, y: Float, angle: Float, fork: String? = null) {
        canvas.save()
        canvas.translate(x, y)
        wheelClip.rewind()
        wheelClip.addCircle(0f, 0f, 47f, Path.Direction.CW)
        canvas.clipPath(wheelClip)
        wheelPaint.color = Color.rgb(231, 209, 178)
        canvas.drawRect(-50f, -50f, 50f, 50f, wheelPaint)
        canvas.rotate((angle * 180f / PI).toFloat())
        wheelPaint.color = Color.rgb(48, 78, 139)
        repeat(3) {
            canvas.rotate(120f)
            canvas.drawPath(spoke, wheelPaint)
        }
        canvas.drawCircle(0f, 0f, 14f, wheelPaint)
        wheelPaint.color = Color.rgb(120, 145, 194)
        canvas.drawCircle(0f, 0f, 6f, wheelPaint)
        canvas.restore()
        if (fork != null) {
            canvas.save()
            canvas.clipRect(x - 17f, y - 64f, x + 18f, y - 16f)
            drawFull(canvas, fork)
            canvas.restore()
        }
    }

    private fun drawFull(canvas: Canvas, file: String) =
        canvas.drawBitmap(bitmaps.getValue(file), null, sourceRect, bitmapPaint)

    private data class TurnPose(
        val file: String, val angle: Float, val ground: Float, val top: Float, val pivot: Float,
        val crop: RectF? = null,
    )

    private companion object {
        const val LEFT = "scooter-ride-left.webp"
        const val RIGHT = "scooter-ride-right.webp"
        const val PUSH = "scooter-push-left.webp"
        val poses = arrayOf(
            TurnPose(LEFT, -90f, 1206f, 54f, 617f),
            TurnPose("scooter-turn-left-front.webp", -52f, 1223f, 31f, 617f),
            TurnPose("scooter-turn-left-near.webp", -30f, 1250f, 31f, 572f),
            TurnPose("scooter-turn-left-almost-front.webp", -15f, 1218f, 54f, 643f),
            TurnPose("scooter-turn-front.webp", 0f, 1226f, 34f, 607f),
            TurnPose("scooter-turn-near-front.webp", 10f, 1244f, 658f, 205f, RectF(0f, 658f, 431f, 1254f)),
            TurnPose("scooter-turn-near-front.webp", 20f, 1244f, 659f, 637f, RectF(431f, 658f, 843f, 1254f)),
            TurnPose("scooter-turn-near-front.webp", 30f, 1244f, 660f, 1092f, RectF(843f, 658f, 1254f, 1254f)),
            TurnPose("scooter-turn-right-front.webp", 52f, 1232f, 47f, 622f),
            TurnPose(RIGHT, 90f, 1211f, 59f, 680f),
        )
    }
}
