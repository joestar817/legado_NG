package io.legado.app.ui.book.read.page.provider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import io.legado.app.help.PaintPool
import splitties.init.appCtx
import java.io.File

/** 正文与规则预览共用的高亮背景图绘制，目标矩形由各自文字布局提供。 */
internal object ReadHighlightImageRenderer {
    const val CONTENT_INSET_DP = 1
    private val highlightBitmapCache = LruCache<String, Bitmap>(16)

    fun scale(style: ReadCharStyle): Float = style.bgImageScale.coerceIn(0.1f, 5f)

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        style: ReadCharStyle,
    ) {
        val paint = PaintPool.obtain().apply {
            isAntiAlias = true
            isFilterBitmap = true
            this.style = Paint.Style.FILL
        }
        val scale = scale(style)
        when (style.bgImageFit) {
            1 -> {
                val width = destination.width() * scale
                val drawHeight = destination.height() * scale
                val target = RectF(
                    destination.centerX() - width / 2,
                    destination.centerY() - drawHeight / 2,
                    destination.centerX() + width / 2,
                    destination.centerY() + drawHeight / 2,
                )
                canvas.save()
                canvas.clipRect(destination)
                canvas.drawBitmap(bitmap, null, target, paint)
                canvas.restore()
            }
            2 -> {
                val cover = maxOf(destination.width() / bitmap.width, destination.height() / bitmap.height) * scale
                val width = bitmap.width * cover
                val drawHeight = bitmap.height * cover
                val target = RectF(
                    destination.centerX() - width / 2,
                    destination.centerY() - drawHeight / 2,
                    destination.centerX() + width / 2,
                    destination.centerY() + drawHeight / 2,
                )
                canvas.save()
                canvas.clipRect(destination)
                canvas.drawBitmap(bitmap, null, target, paint)
                canvas.restore()
            }
            3 -> drawNineSliceCenter(canvas, bitmap, destination, style, paint)
            else -> {
                val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                shader.setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(destination.left, destination.top)
                })
                paint.shader = shader
                canvas.drawRect(destination, paint)
            }
        }
        PaintPool.recycle(paint)
    }

    private fun drawNineSliceCenter(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        style: ReadCharStyle,
        paint: Paint,
    ) {
        val cuts = ReadNineSliceGeometry.from(bitmap.width, bitmap.height, style)
        if (cuts.left < cuts.right && cuts.top < cuts.bottom) {
            canvas.drawBitmap(bitmap, Rect(cuts.left, cuts.top, cuts.right, cuts.bottom), destination, paint)
        }
    }

    /** 单独画在真实画布上，避免行缓存裁掉外扩区域。 */
    fun drawNineSliceFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        style: ReadCharStyle,
        lineSpacing: Float,
    ) {
        val cuts = ReadNineSliceGeometry.from(bitmap.width, bitmap.height, style)
            .forLine(destination.height(), lineSpacing)
        val (top, bottom) = cuts.verticalInsets(destination.height(), lineSpacing)
        val sourceX = intArrayOf(0, cuts.left, cuts.right, bitmap.width)
        val sourceY = intArrayOf(0, cuts.top, cuts.bottom, bitmap.height)
        val targetX = floatArrayOf(destination.left - cuts.leftWidth, destination.left,
            destination.right, destination.right + cuts.rightWidth)
        val targetY = floatArrayOf(destination.top - top, destination.top,
            destination.bottom, destination.bottom + bottom)
        val paint = PaintPool.obtain().apply {
            isAntiAlias = true
            isFilterBitmap = true
            this.style = Paint.Style.FILL
        }
        for (row in 0..2) for (column in 0..2) {
            if (row == 1 && column == 1) continue
            if (sourceX[column] >= sourceX[column + 1] || sourceY[row] >= sourceY[row + 1] ||
                targetX[column] >= targetX[column + 1] || targetY[row] >= targetY[row + 1]) continue
            canvas.drawBitmap(bitmap,
                Rect(sourceX[column], sourceY[row], sourceX[column + 1], sourceY[row + 1]),
                RectF(targetX[column], targetY[row], targetX[column + 1], targetY[row + 1]), paint)
        }
        PaintPool.recycle(paint)
    }

    fun loadBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        highlightBitmapCache.get(path)?.let { return it }
        val bitmap = runCatching {
            when {
                path.startsWith("assets://") -> appCtx.assets.open(path.removePrefix("assets://"))
                    .use(BitmapFactory::decodeStream)
                path.startsWith("content://") -> appCtx.contentResolver.openInputStream(
                    android.net.Uri.parse(path)
                )?.use(BitmapFactory::decodeStream)
                else -> File(path).takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
        }.getOrNull() ?: return null
        highlightBitmapCache.put(path, bitmap)
        return bitmap
    }
}
