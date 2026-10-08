package io.legado.app.ui.main.chatentry

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import io.legado.app.ui.main.chatentry.core.BlueFishArmMotion
import io.legado.app.ui.main.chatentry.core.BlueFishArmRig
import io.legado.app.ui.main.chatentry.core.BlueFishCharacter
import io.legado.app.ui.main.chatentry.core.BlueFishStage
import io.legado.app.ui.main.chatentry.core.BlueFishVisual
import io.legado.app.ui.main.chatentry.core.PetFrame
import io.legado.app.ui.main.chatentry.core.PetPoint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Native rendering of the accepted v3 layered artwork, with final-pixel touch hit testing. */
class BlueFishPetRenderer(private val bitmaps: Map<String, Bitmap>) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val sourceRect = RectF(0f, 0f, SOURCE_SIZE, SOURCE_SIZE)
    private val surfaceRect = RectF(-PADDING, -PADDING, SOURCE_SIZE + PADDING, SOURCE_SIZE + PADDING)
    private val rectangle = RectF()
    private val sourceHitBounds = RectF()
    private val rootMatrix = Matrix()
    private val inverseRootMatrix = Matrix()
    private val partMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val pointValues = FloatArray(2)
    private val arm = BlueFishArmRig()

    // One bounded, reusable surface also supplies the exact alpha mask. No GPU readback is needed.
    private val surface = Bitmap.createBitmap(SURFACE_SIZE, SURFACE_SIZE, Bitmap.Config.ARGB_8888)
    private val surfaceCanvas = Canvas(surface).apply {
        scale(SURFACE_SCALE, SURFACE_SCALE)
        translate(PADDING, PADDING)
    }
    private val pixels = IntArray(SURFACE_SIZE * SURFACE_SIZE)
    private val thinkingBody = expressionBody("think.webp")
    private val ahaBody = expressionBody("aha.webp")
    private val sleeveTexture = crop("eat-arm.webp", BlueFishArmRig.SLEEVE_X, BlueFishArmRig.SLEEVE_Y, BlueFishArmRig.SLEEVE_WIDTH, BlueFishArmRig.SLEEVE_HEIGHT)
    private val sleevePaint = Paint(paint).apply { shader = BitmapShader(sleeveTexture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val sleeveTextureCoordinates = FloatArray(arm.meshVertices.size).apply {
        for (row in 0..BlueFishArmRig.ROWS) for (column in 0..BlueFishArmRig.COLUMNS) {
            val index = (row * (BlueFishArmRig.COLUMNS + 1) + column) * 2
            this[index] = sleeveTexture.width.toFloat() * column / BlueFishArmRig.COLUMNS
            this[index + 1] = sleeveTexture.height.toFloat() * row / BlueFishArmRig.ROWS
        }
    }
    private val sleeveIndices = ShortArray(BlueFishArmRig.COLUMNS * BlueFishArmRig.ROWS * 6).apply {
        var index = 0
        for (row in 0 until BlueFishArmRig.ROWS) for (column in 0 until BlueFishArmRig.COLUMNS) {
            val a = row * (BlueFishArmRig.COLUMNS + 1) + column
            val b = a + 1
            val c = a + BlueFishArmRig.COLUMNS + 1
            val d = c + 1
            this[index++] = a.toShort(); this[index++] = b.toShort(); this[index++] = d.toShort()
            this[index++] = a.toShort(); this[index++] = d.toShort(); this[index++] = c.toShort()
        }
    }
    private val handTexture = crop("eat-arm.webp", 185f, 560f, 350f, 230f)
    private val riceTexture = crop("rice-grains.webp", 172f, 766f, 894f, 302f)
    private val biteTexture = crop("rice-bite.webp", 250f, 367f, 757f, 526f)
    private val handRect = RectF(185f, 560f, 535f, 790f)
    private val biteRect = RectF(250f, 367f, 1007f, 893f)
    private val mouthPath = Path().apply { addOval(508f, 557f, 632f, 671f, Path.Direction.CW) }
    private val peekBlinkPath = polygon(396f, 356f, 786f, 356f, 786f, 564f, 566f, 564f, 566f, 609f, 396f, 609f)
    private val riceRimPath = Path().apply {
        moveTo(145f, 0f); lineTo(967f, 0f); lineTo(967f, 823f)
        cubicTo(755f, 910f, 345f, 910f, 145f, 823f); close()
    }
    private val sleeveVisiblePath = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        addRect(0f, 0f, SOURCE_SIZE, SOURCE_SIZE, Path.Direction.CW)
        moveTo(145f, 823f); cubicTo(345f, 754f, 755f, 754f, 967f, 823f)
        lineTo(967f, SOURCE_SIZE); lineTo(145f, SOURCE_SIZE); close()
    }
    private val bowlFrontPath = polygon(
        145f, 824f, 245f, 856f, 355f, 878f, 465f, 891f, 577f, 895f,
        694f, 891f, 802f, 878f, 899f, 856f, 970f, 824f, 1030f, 1000f,
        945f, 1234f, 250f, 1234f, 130f, 1030f,
    )
    private var renderedVisual: BlueFishVisual? = null
    private var inverseReady = false
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    val hitBounds = RectF()

    fun draw(canvas: Canvas, frame: PetFrame, viewportWidth: Float, viewportHeight: Float) {
        val visual = frame.blueFish ?: return
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        if (!sameComposition(visual, renderedVisual)) {
            compose(visual)
            renderedVisual = visual
        }
        val scale = BlueFishCharacter.definition.scale
        val c = cos(visual.angle)
        val s = sin(visual.angle)
        val a = scale * c
        val b = scale * s
        val skewX = -scale * visual.scaleY * s
        val d = scale * visual.scaleY * c
        setMatrix(rootMatrix, a, b, skewX, d,
            frame.anchor.x - a * BlueFishCharacter.ROOT_X - skewX * BlueFishCharacter.ROOT_Y,
            frame.anchor.y - b * BlueFishCharacter.ROOT_X - d * BlueFishCharacter.ROOT_Y)
        inverseReady = rootMatrix.invert(inverseRootMatrix)
        hitBounds.set(sourceHitBounds)
        rootMatrix.mapRect(hitBounds)
        if (!hitBounds.intersect(0f, 0f, viewportWidth, viewportHeight)) hitBounds.setEmpty()

        canvas.save()
        canvas.concat(rootMatrix)
        canvas.drawBitmap(surface, null, surfaceRect, paint)
        canvas.restore()
        if (visual.stage == BlueFishStage.THINKING || visual.stage == BlueFishStage.AHA) {
            drawBubble(canvas, frame, visual)
        }
    }

    fun hitTest(point: PetPoint): Boolean {
        if (!inverseReady || point.x < 0f || point.x >= viewportWidth || point.y < 0f || point.y >= viewportHeight || !hitBounds.contains(point.x, point.y)) return false
        pointValues[0] = point.x; pointValues[1] = point.y
        inverseRootMatrix.mapPoints(pointValues)
        val x = floor((pointValues[0] + PADDING) * SURFACE_SCALE).toInt()
        val y = floor((pointValues[1] + PADDING) * SURFACE_SCALE).toInt()
        return x in 0 until SURFACE_SIZE && y in 0 until SURFACE_SIZE && pixels[y * SURFACE_SIZE + x] ushr 24 > 32
    }

    private fun compose(visual: BlueFishVisual) {
        surface.eraseColor(Color.TRANSPARENT)
        val canvas = surfaceCanvas
        val eating = visual.stage == BlueFishStage.EATING
        if (visual.stage != BlueFishStage.PEEK) {
            val pivotX = if (eating) 920f else 775f
            val pivotY = if (eating) 1155f else 1080f
            canvas.save()
            canvas.rotate(visual.tailAngle * DEGREES, pivotX, pivotY)
            drawFull(canvas, if (eating) "eat-tail.webp" else "standing-tail.webp")
            canvas.restore()
        }
        val body = when (visual.stage) {
            BlueFishStage.PEEK -> bitmaps.getValue("peek.webp")
            BlueFishStage.THINKING -> thinkingBody
            BlueFishStage.AHA -> ahaBody
            BlueFishStage.SURPRISED -> bitmaps.getValue("drag-surprised.webp")
            BlueFishStage.SHY -> bitmaps.getValue("release-shy.webp")
            BlueFishStage.EATING -> bitmaps.getValue("eat-body.webp")
            else -> bitmaps.getValue("standing-body.webp")
        }
        canvas.drawBitmap(body, null, sourceRect, paint)
        if (eating) {
            drawRice(canvas, visual.riceLevel)
            if (!visual.chew) {
                canvas.save(); canvas.clipPath(mouthPath)
                drawFull(canvas, "mouth-open.webp")
                canvas.restore()
            }
            visual.arm?.let { drawArm(canvas, it) }
        } else if (visual.blink) {
            canvas.save()
            when (visual.stage) {
                BlueFishStage.PEEK -> {
                    canvas.clipPath(peekBlinkPath)
                    drawFull(canvas, "peek-blink.webp")
                }
                BlueFishStage.IDLE, BlueFishStage.THINKING, BlueFishStage.SETTLE -> {
                    canvas.clipRect(375f, 375f, 795f, 580f)
                    drawFull(canvas, "idle-blink.webp")
                }
                else -> Unit
            }
            canvas.restore()
        }
        updateAlphaBounds()
    }

    private fun sameComposition(current: BlueFishVisual, previous: BlueFishVisual?): Boolean = previous != null &&
        current.stage == previous.stage && current.tailAngle == previous.tailAngle &&
        current.blink == previous.blink && current.riceLevel == previous.riceLevel &&
        current.chew == previous.chew && current.arm == previous.arm

    private fun drawRice(canvas: Canvas, level: Float) {
        if (level <= 0f) return
        val amount = level.coerceIn(0f, 1f)
        val width = 790f * sqrt(amount)
        val height = 242f * amount
        rectangle.set(555f - width / 2f, 892f - height, 555f + width / 2f, 892f)
        canvas.save(); canvas.clipPath(riceRimPath)
        canvas.drawBitmap(riceTexture, null, rectangle, paint)
        canvas.restore()
    }

    private fun drawArm(canvas: Canvas, motion: BlueFishArmMotion) {
        arm.update(motion)
        canvas.save()
        // The bowl's rear edge hides only the sleeve, never the hand or chopsticks.
        canvas.clipPath(sleeveVisiblePath)
        // Explicit indices preserve the preview's triangle diagonals in one native draw call.
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES, arm.meshVertices.size, arm.meshVertices, 0,
            sleeveTextureCoordinates, 0, null, 0, sleeveIndices, 0, sleeveIndices.size, sleevePaint)
        canvas.restore()
        val m = arm.handMatrix
        setMatrix(partMatrix, m[0], m[1], m[2], m[3], m[4], m[5])
        canvas.save(); canvas.concat(partMatrix)
        canvas.drawBitmap(handTexture, null, handRect, paint)
        canvas.restore()
        if (motion.riceBallScale > 0f) {
            val scale = motion.riceBallScale * .095f
            canvas.save(); canvas.translate(arm.tipX, arm.tipY)
            canvas.rotate(motion.handAngle * DEGREES); canvas.scale(scale, scale)
            canvas.translate(-360f, -670f)
            canvas.drawBitmap(biteTexture, null, biteRect, paint)
            canvas.restore()
        }
        canvas.save(); canvas.clipRect(130f, 814f, 1030f, 1234f); canvas.clipPath(bowlFrontPath)
        drawFull(canvas, "eat-body.webp")
        canvas.restore()
    }

    private fun drawBubble(canvas: Canvas, frame: PetFrame, visual: BlueFishVisual) {
        val k = BlueFishCharacter.definition.scale / (220f / 1200f)
        val width = 112f * k
        val height = 40f * k
        val rawCx = frame.anchor.x - 9f * k
        val rawCy = frame.anchor.y - (BlueFishCharacter.ROOT_Y - 25f) * BlueFishCharacter.definition.scale - height * .7f
        val cx = if (visual.bubbleFollowsAnchor) rawCx else clampCenter(rawCx, width / 2f + 14f, viewportWidth - width / 2f - 14f)
        val cy = if (visual.bubbleFollowsAnchor) rawCy else clampCenter(rawCy, height / 2f + 18f, viewportHeight - height / 2f - 18f)
        rectangle.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
        bubblePaint.style = Paint.Style.FILL
        bubblePaint.color = Color.rgb(255, 253, 245)
        canvas.drawOval(rectangle, bubblePaint)
        bubblePaint.style = Paint.Style.STROKE
        bubblePaint.color = Color.rgb(49, 82, 121)
        bubblePaint.strokeWidth = 1.15f * k
        canvas.drawOval(rectangle, bubblePaint)
        val exclamation = visual.stage == BlueFishStage.AHA
        textPaint.color = Color.rgb(49, 82, 121)
        textPaint.textSize = (if (exclamation) 24f else 15f) * k
        textPaint.isFakeBoldText = exclamation
        val text = if (exclamation) "！" else visual.thoughtText
        textPaint.textScaleX = 1f
        val measuredWidth = textPaint.measureText(text)
        val maxWidth = width - 14f * k
        if (measuredWidth > maxWidth) textPaint.textScaleX = maxWidth / measuredWidth
        canvas.drawText(text, cx, cy + .5f * k - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        if (exclamation) {
            bubblePaint.color = Color.rgb(201, 166, 102)
            bubblePaint.strokeWidth = 2f * k
            canvas.drawLine(cx - width / 2f - 7f * k, cy - 5f * k, cx - width / 2f - 15f * k, cy - 9f * k, bubblePaint)
            canvas.drawLine(cx + width / 2f + 7f * k, cy - 5f * k, cx + width / 2f + 15f * k, cy - 9f * k, bubblePaint)
        }
    }

    private fun drawFull(canvas: Canvas, key: String) = canvas.drawBitmap(bitmaps.getValue(key), null, sourceRect, paint)

    private fun expressionBody(key: String): Bitmap {
        val base = bitmaps.getValue("standing-body.webp")
        val result = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.scale(base.width / SOURCE_SIZE, base.height / SOURCE_SIZE)
        drawFull(canvas, "standing-body.webp")
        canvas.save(); canvas.clipRect(260f, 360f, 870f, 1020f)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawFull(canvas, key)
        canvas.restore()
        return result
    }

    private fun crop(key: String, x: Float, y: Float, width: Float, height: Float): Bitmap {
        val source = bitmaps.getValue(key)
        val result = Bitmap.createBitmap(ceil(width * source.width / SOURCE_SIZE).toInt(), ceil(height * source.height / SOURCE_SIZE).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.scale(result.width / width, result.height / height)
        canvas.translate(-x, -y)
        drawFull(canvas, key)
        return result
    }

    private fun updateAlphaBounds() {
        surface.getPixels(pixels, 0, SURFACE_SIZE, 0, 0, SURFACE_SIZE, SURFACE_SIZE)
        var left = SURFACE_SIZE
        var right = -1
        var top = SURFACE_SIZE
        var bottom = -1
        for (y in 0 until SURFACE_SIZE) for (x in 0 until SURFACE_SIZE) {
            if (pixels[y * SURFACE_SIZE + x] ushr 24 <= 32) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (right < left) sourceHitBounds.setEmpty() else sourceHitBounds.set(
            left / SURFACE_SCALE - PADDING, top / SURFACE_SCALE - PADDING,
            (right + 1) / SURFACE_SCALE - PADDING, (bottom + 1) / SURFACE_SCALE - PADDING,
        )
    }

    private fun setMatrix(matrix: Matrix, a: Float, b: Float, c: Float, d: Float, tx: Float, ty: Float) {
        matrixValues[0] = a; matrixValues[1] = c; matrixValues[2] = tx
        matrixValues[3] = b; matrixValues[4] = d; matrixValues[5] = ty
        matrixValues[6] = 0f; matrixValues[7] = 0f; matrixValues[8] = 1f
        matrix.setValues(matrixValues)
    }

    private fun clampCenter(value: Float, min: Float, max: Float) = if (min <= max) value.coerceIn(min, max) else (min + max) / 2f

    private fun polygon(vararg coordinates: Float) = Path().apply {
        moveTo(coordinates[0], coordinates[1])
        for (index in 2 until coordinates.size step 2) lineTo(coordinates[index], coordinates[index + 1])
        close()
    }

    companion object {
        private const val SOURCE_SIZE = BlueFishCharacter.SOURCE_SIZE
        private const val PADDING = 64f
        private const val SURFACE_SIZE = 448
        private const val SURFACE_SCALE = SURFACE_SIZE / (SOURCE_SIZE + PADDING * 2f)
        private const val DEGREES = 57.2957795f
    }
}
