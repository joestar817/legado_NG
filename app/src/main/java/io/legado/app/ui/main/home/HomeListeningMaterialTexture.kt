package io.legado.app.ui.main.home

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val MATERIAL_TILE_PIXELS = 256
private const val MATERIAL_PIXELS_PER_DP = 2f

/** Both transparent tiles are shared; callers remember the density-specific paint. */
internal fun listeningStructuredMaterialPaint(styleId: String, density: Float): Paint {
    require(density.isFinite() && density > 0f)
    val bitmap = when (styleId) {
        "storybook" -> storyMaterialTile
        "night" -> nightMaterialTile
        else -> error("No structured listening material for $styleId")
    }
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    shader.setLocalMatrix(Matrix().apply {
        val scale = density / MATERIAL_PIXELS_PER_DP
        setScale(scale, scale)
    })
    return Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = shader
    }
}

// Two ARGB_8888 tiles occupy 2 * 256 * 256 * 4 = 524288 bytes (512 KiB).
// Lazy generation owns all random draws, loops and paths; rendering repeats a single shader.
private val storyMaterialTile: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(MATERIAL_TILE_PIXELS, MATERIAL_TILE_PIXELS, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val random = Random(0x534741)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val path = Path()
    val bounds = RectF()
    repeat(1800) {
        val x = random.nextFloat() * MATERIAL_TILE_PIXELS
        val y = random.nextFloat() * MATERIAL_TILE_PIXELS
        val angle = random.nextFloat() * 6.2831853f
        val length = (0.8f + random.nextFloat() * 4.2f) * MATERIAL_PIXELS_PER_DP
        val dx = cos(angle) * length
        val dy = sin(angle) * length
        val bend = (random.nextFloat() - 0.5f) * 0.7f
        paint.color = if (random.nextBoolean()) Color.rgb(146, 137, 111) else Color.rgb(250, 245, 228)
        paint.alpha = random.nextInt(3, 11)
        paint.strokeWidth = (0.15f + random.nextFloat() * 0.15f) * MATERIAL_PIXELS_PER_DP
        path.reset()
        path.moveTo(x, y)
        path.quadTo(x + dx * 0.5f - dy * bend, y + dy * 0.5f + dx * bend, x + dx, y + dy)
        path.computeBounds(bounds, true)
        bounds.inset(-paint.strokeWidth, -paint.strokeWidth)
        drawWrapped(canvas, bounds) { canvas.drawPath(path, paint) }
    }
    // Sparse pinpricks supplement the fibres; they do not form the material's main structure.
    paint.style = Paint.Style.FILL
    repeat(480) {
        val x = random.nextFloat() * MATERIAL_TILE_PIXELS
        val y = random.nextFloat() * MATERIAL_TILE_PIXELS
        val radius = 0.15f + random.nextFloat() * 0.25f
        paint.color = Color.rgb(156, 145, 119)
        paint.alpha = random.nextInt(3, 8)
        bounds.set(x - radius, y - radius, x + radius, y + radius)
        drawWrapped(canvas, bounds) { canvas.drawCircle(x, y, radius, paint) }
    }
    bitmap
}

private val nightMaterialTile: Bitmap by lazy {
    val bitmap = Bitmap.createBitmap(MATERIAL_TILE_PIXELS, MATERIAL_TILE_PIXELS, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val random = Random(0x4E4754)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 0.38f
    }
    val bounds = RectF()
    // 160 threads fit the 256px period exactly: 1.6px / 2 = 0.8dp spacing.
    val step = MATERIAL_TILE_PIXELS / 160f
    repeat(160) { thread ->
        val coordinate = thread * step
        for (vertical in listOf(false, true)) {
            var along = random.nextFloat() * step
            while (along < MATERIAL_TILE_PIXELS) {
                val end = along + (4 + random.nextInt(8)) * step
                paint.color = if (vertical) Color.rgb(7, 15, 30) else Color.rgb(113, 142, 172)
                paint.alpha = random.nextInt(3, 9)
                val x1 = if (vertical) coordinate else along
                val y1 = if (vertical) along else coordinate
                val x2 = if (vertical) coordinate else end
                val y2 = if (vertical) end else coordinate
                bounds.set(minOf(x1, x2) - 1f, minOf(y1, y2) - 1f, maxOf(x1, x2) + 1f, maxOf(y1, y2) + 1f)
                drawWrapped(canvas, bounds) { canvas.drawLine(x1, y1, x2, y2, paint) }
                // Broken, unequal runs avoid broad stripes or a visible checkerboard.
                along = end + (1 + random.nextInt(3)) * step
            }
        }
    }
    bitmap
}

/** Repeat each crossing mark on the opposite edge, including diagonal corner crossings. */
private inline fun drawWrapped(canvas: Canvas, bounds: RectF, draw: () -> Unit) {
    val tile = MATERIAL_TILE_PIXELS.toFloat()
    for (offsetY in -1..1) for (offsetX in -1..1) {
        val dx = offsetX * tile
        val dy = offsetY * tile
        if (bounds.right + dx < 0f || bounds.left + dx > tile ||
            bounds.bottom + dy < 0f || bounds.top + dy > tile) continue
        val saved = canvas.save()
        canvas.translate(dx, dy)
        draw()
        canvas.restoreToCount(saved)
    }
}
