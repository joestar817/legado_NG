package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.caverock.androidsvg.SVG
import io.legado.app.constant.AppLog
import io.legado.app.help.config.ParagraphCommentIconConfig
import io.legado.app.help.config.ParagraphCommentIconSettings
import io.legado.app.help.config.ensureParagraphIconViewBox
import kotlin.math.roundToInt

data class ParagraphCommentIcon(
    val text: String,
    val config: ParagraphCommentIconConfig,
)

object ParagraphCommentIconRenderer {
    private data class CacheKey(val template: String, val text: String, val color: Int, val textColor: Int, val width: Int, val height: Int)

    private val cache = object : LruCache<CacheKey, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount
    }

    @JvmOverloads
    fun bitmap(
        config: ParagraphCommentIconConfig,
        text: String,
        color: Int,
        width: Int,
        height: Int,
        textColor: Int = color,
    ): Bitmap? {
        val safeWidth = width.coerceIn(1, 512)
        val safeHeight = height.coerceIn(1, 512)
        val template = config.template()
        val key = CacheKey(template, text, color, textColor, safeWidth, safeHeight)
        cache.get(key)?.let { return it }
        return runCatching {
            val svgText = ParagraphCommentIconSettings.resolveTemplate(template, text, color, textColor)
            val svg = SVG.getFromString(svgText)
            svg.ensureParagraphIconViewBox()
            svg.setDocumentWidth("100%")
            svg.setDocumentHeight("100%")
            Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val save = canvas.saveLayerAlpha(null, Color.alpha(color))
                svg.renderToCanvas(canvas)
                canvas.restoreToCount(save)
                cache.put(key, bitmap)
            }
        }.onFailure { AppLog.put("段评图标渲染失败", it) }.getOrNull()
    }

    internal fun heightInLine(width: Float, lineHeight: Float): Float = (width * 39f / 54f).coerceAtMost(lineHeight)

    fun draw(
        canvas: Canvas,
        icon: ParagraphCommentIcon,
        start: Float,
        end: Float,
        lineHeight: Float,
        color: Int,
        paint: Paint,
        textColor: Int = color,
    ): Boolean {
        val width = (end - start).coerceAtLeast(1f)
        val height = heightInLine(width, lineHeight)
        val image = bitmap(icon.config, icon.text, color, width.roundToInt(), height.roundToInt(), textColor) ?: return false
        canvas.drawBitmap(image, null, RectF(start, (lineHeight - height) / 2, end, (lineHeight + height) / 2), paint)
        return true
    }
}
