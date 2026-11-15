package io.legado.plugins.pets.layered

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.legado.plugins.pets.layered.core.BlueFishStage
import io.legado.plugins.pets.layered.core.BlueFishSuspendedMotion
import io.legado.plugins.pets.layered.core.BlueFishSuspendedMotion.Companion.smooth
import io.legado.plugins.pets.layered.core.BlueFishVisual
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** v12 pickup and relief, composed in the renderer's normal 1254-pixel coordinates. */
class BlueFishSuspendedRenderer(private val bitmaps: Map<String, Bitmap>) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val motion = BlueFishSuspendedMotion()
    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val tailMatrix = FloatArray(6)
    private val sourceRect = RectF(0f, 0f, 1254f, 1254f)
    // Both limb sheets are actually 1448 x 1086; their coordinates must not
    // inherit the standing artwork's square dimensions after downsampling.
    private val atlasRect = RectF(0f, 0f, 1448f, 1086f)
    private val layerBounds = RectF(-64f, -64f, 1318f, 1318f)
    private val tailSource = RectF(505f, 662f, 872f, 1017f)
    private val coreSource = RectF(8f, 8f, 582f, 650f)
    private val armSources = arrayOf(RectF(646f, 190f, 880f, 446f), RectF(1134f, 190f, 1368f, 446f))
    private val calfSources = arrayOf(RectF(66f, 775f, 188f, 848f), RectF(306f, 775f, 428f, 848f))
    private val shoeSources = arrayOf(RectF(66f, 838f, 188f, 946f), RectF(306f, 838f, 428f, 946f))
    private val sleeveClips = Array(2) { index -> armClip(index, false) }
    private val handClips = Array(2) { index -> armClip(index, true) }
    private val featureRegions = arrayOf(
        RectF(414f, 371f, 730f, 439f),
        RectF(390f, 441f, 784f, 572f),
        RectF(535f, 572f, 638f, 624f),
    )
    private val faceKeys = arrayOf("landing-aftershock.webp", "landing-exhale.webp", "landing-settled.webp", "standing-body.webp")
    // One sampled-size surface is reused. Facial tracks are composited at full
    // opacity before the whole portrait receives the landing blend's alpha.
    private val portrait = bitmaps.getValue("standing-body.webp").let {
        Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
    }
    private val portraitCanvas = Canvas(portrait).apply {
        scale(portrait.width / 1254f, portrait.height / 1254f)
    }

    fun draw(canvas: Canvas, visual: BlueFishVisual) {
        val seconds = visual.actionElapsedMs.coerceAtLeast(0L) / 1000.0
        when (visual.stage) {
            BlueFishStage.SUSPENDED -> drawSuspended(canvas, visual.struggleElapsedMs, 1f, 1f)
            BlueFishStage.LANDING -> {
                val p = visual.landingProgress.coerceIn(0f, 1f).toDouble()
                val blend = smooth((p - .65) / .35).toFloat()
                drawSuspended(canvas, visual.struggleElapsedMs, (1.0 - smooth(seconds / .32)).toFloat(), 1f - blend)
                val bounce = if (p > .7) sin((p - .7) / .3 * PI).toFloat() * .04f else 0f
                drawStanding(canvas, -1.0, blend, bounce, visual.tailAngle, visual.landingOffsetY)
            }
            BlueFishStage.RELIEF -> {
                val breath = .006 * smooth(seconds / .18) * (1.0 - smooth((seconds - .28) / .8))
                drawStanding(canvas, seconds, 1f, -breath.toFloat(), visual.tailAngle, 0f)
            }
            else -> Unit
        }
    }

    private fun drawSuspended(canvas: Canvas, elapsedMs: Long, settling: Float, alpha: Float) {
        if (alpha <= 0f) return
        motion.update(elapsedMs, settling)
        val group = saveGroup(canvas, alpha)
        canvas.translate(640f, 1230f)
        canvas.scale(1200f / 700f, 1200f / 700f)
        canvas.translate(-290f, -718f)
        BlueFishSuspendedMotion.rigid(tailMatrix, 553.0, 942.0, 392.0, 584.0, motion.tailAngle.toDouble(), .58)
        drawPart(canvas, "suspended-atlas.webp", tailMatrix, tailSource)
        for (index in 0..1) {
            drawPart(canvas, "suspended-atlas.webp", motion.calfMatrices[index], calfSources[index])
            drawPart(canvas, "suspended-atlas.webp", motion.shoeMatrices[index], shoeSources[index])
        }
        canvas.save()
        canvas.clipRect(coreSource)
        drawAtlas(canvas, "suspended-atlas.webp")
        canvas.restore()
        for (index in 0..1) {
            drawPart(canvas, "forward-fist-arms.webp", motion.sleeveMatrices[index], armSources[index], sleeveClips[index])
            drawPart(canvas, "forward-fist-arms.webp", motion.handMatrices[index], armSources[index], handClips[index])
        }
        canvas.restoreToCount(group)
    }

    private fun drawStanding(canvas: Canvas, seconds: Double, alpha: Float, bounce: Float, tailAngle: Float, offsetY: Float) {
        if (alpha <= 0f) return
        composePortrait(seconds)
        canvas.save()
        // During contact the standing image is already planted on the final
        // ground; the remaining suspended image still follows its falling root.
        canvas.translate(0f, offsetY)
        canvas.translate(640f, 1230f)
        canvas.scale(1f + bounce * .5f, 1f - bounce)
        canvas.translate(-640f, -1230f)
        val group = saveGroup(canvas, alpha)
        canvas.save()
        canvas.rotate(tailAngle * DEGREES, 775f, 1080f)
        drawFull(canvas, "standing-tail.webp")
        canvas.restore()
        canvas.drawBitmap(portrait, null, sourceRect, paint)
        canvas.restoreToCount(group)
        canvas.restore()
    }

    private fun composePortrait(seconds: Double) {
        portrait.eraseColor(Color.TRANSPARENT)
        drawFull(portraitCanvas, "standing-body.webp")
        for (index in featureRegions.indices) {
            val track = FACE_TRACKS[index]
            var from = 0
            var to = 0
            var mix = 0f
            for (key in track.indices step 3) {
                if (seconds < track[key]) break
                to = track[key + 2].toInt()
                if (seconds < track[key + 1]) {
                    mix = smooth((seconds - track[key]) / (track[key + 1] - track[key])).toFloat()
                    break
                }
                from = to
            }
            portraitCanvas.save()
            portraitCanvas.clipRect(featureRegions[index])
            drawFull(portraitCanvas, faceKeys[from])
            if (mix > 0f) {
                paint.alpha = (255f * mix).roundToInt()
                drawFull(portraitCanvas, faceKeys[to])
                paint.alpha = 255
            }
            portraitCanvas.restore()
        }
    }

    private fun drawPart(canvas: Canvas, key: String, transform: FloatArray, source: RectF, clip: Path? = null) {
        matrixValues[0] = transform[0]; matrixValues[1] = transform[2]; matrixValues[2] = transform[4]
        matrixValues[3] = transform[1]; matrixValues[4] = transform[3]; matrixValues[5] = transform[5]
        matrixValues[6] = 0f; matrixValues[7] = 0f; matrixValues[8] = 1f
        matrix.setValues(matrixValues)
        canvas.save()
        canvas.concat(matrix)
        canvas.clipRect(source)
        if (clip != null) canvas.clipPath(clip)
        drawAtlas(canvas, key)
        canvas.restore()
    }

    private fun drawAtlas(canvas: Canvas, key: String) = canvas.drawBitmap(bitmaps.getValue(key), null, atlasRect, paint)

    private fun drawFull(canvas: Canvas, key: String) = canvas.drawBitmap(bitmaps.getValue(key), null, sourceRect, paint)

    private fun saveGroup(canvas: Canvas, alpha: Float): Int = if (alpha >= 1f) canvas.save()
        else canvas.saveLayerAlpha(layerBounds, (255f * alpha).roundToInt().coerceIn(0, 255))

    private fun armClip(index: Int, hand: Boolean): Path {
        val g = BlueFishSuspendedMotion.ARMS[index]
        val dx = g.wristX - g.shoulderX
        val dy = g.wristY - g.shoulderY
        val length = hypot(dx, dy)
        val ux = dx / length
        val uy = dy / length
        val from = if (hand) length - 5.0 else -200.0
        val to = if (hand) 600.0 else length + 5.0
        return Path().apply {
            moveTo((g.shoulderX + from * ux + 400.0 * uy).toFloat(), (g.shoulderY + from * uy - 400.0 * ux).toFloat())
            lineTo((g.shoulderX + to * ux + 400.0 * uy).toFloat(), (g.shoulderY + to * uy - 400.0 * ux).toFloat())
            lineTo((g.shoulderX + to * ux - 400.0 * uy).toFloat(), (g.shoulderY + to * uy + 400.0 * ux).toFloat())
            lineTo((g.shoulderX + from * ux - 400.0 * uy).toFloat(), (g.shoulderY + from * uy + 400.0 * ux).toFloat())
            close()
        }
    }

    companion object {
        private const val DEGREES = 57.2957795f
        // start, end, expression index: aftershock=0, exhale=1, settled=2, idle=3.
        private val FACE_TRACKS = arrayOf(
            doubleArrayOf(.18, .48, 2.0, 1.40, 1.65, 3.0),
            doubleArrayOf(.24, .36, 1.0, .76, .90, 2.0, 1.35, 1.50, 3.0),
            doubleArrayOf(.18, .40, 1.0, .84, 1.05, 2.0, 1.40, 1.65, 3.0),
        )
    }
}
