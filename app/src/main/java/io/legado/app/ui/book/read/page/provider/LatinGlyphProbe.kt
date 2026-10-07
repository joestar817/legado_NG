package io.legado.app.ui.book.read.page.provider

import android.graphics.Rect
import android.text.TextPaint
import io.legado.app.help.config.LatinOpticalScale
import java.util.concurrent.ConcurrentHashMap

/**
 * 用同一 em 下的字形墨高估算光学比例：目标是 0.72×汉字墨高，不是墨高相等。结果按字体路径缓存。
 * 缺字回退不探测：绑不上字体时退回 1。
 */
object LatinGlyphProbe {
    private val cache = ConcurrentHashMap<String, Float>()

    fun estimate(cjkFace: String, latinFace: String): Float {
        val key = "$cjkFace\n$latinFace"
        cache[key]?.let { return it }
        val value = measure(cjkFace, latinFace)
        cache[key] = value
        return value
    }

    private fun measure(cjkFace: String, latinFace: String): Float {
        val cjk = paint(cjkFace) ?: return 1f
        val latin = paint(latinFace) ?: return 1f
        return LatinOpticalScale.estimate(
            averageInk(cjk, "永国中"),
            averageInk(latin, "HNE"),
            averageInk(latin, "xno"),
        )
    }

    private fun paint(path: String): TextPaint? {
        val typeface = ChapterProvider.loadOptionalTypeface(path) ?: return null
        return TextPaint().apply {
            this.typeface = typeface
            textSize = 100f
            isAntiAlias = true
        }
    }

    private fun averageInk(paint: TextPaint, sample: String): Float {
        val bounds = Rect()
        var sum = 0f
        var count = 0
        sample.forEach { ch ->
            val text = ch.toString()
            paint.getTextBounds(text, 0, text.length, bounds)
            val height = bounds.height()
            if (height > 0) {
                sum += height
                count++
            }
        }
        return if (count == 0) 0f else sum / count
    }
}
