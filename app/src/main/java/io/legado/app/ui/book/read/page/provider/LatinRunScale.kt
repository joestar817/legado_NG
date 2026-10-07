package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadScriptClassifierContract
import io.legado.app.help.config.ReadValueScope

/** 把西文比例写到 Latin run 上。比例为 1 时原样返回，不改行高。 */
object LatinRunScale {
    fun apply(text: String, styles: Array<ReadCharStyle?>?, scale: Float): Array<ReadCharStyle?>? {
        if (scale == 1f || !scale.isFinite()) return styles
        val result = styles?.copyOf() ?: arrayOfNulls(text.length)
        var previousStrong: ReadValueScope? = null
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val scope = ReadScriptClassifierContract.classify(codePoint, previousStrong)
            if (scope == ReadValueScope.CJK || scope == ReadValueScope.LATIN || scope == ReadValueScope.OTHER) {
                previousStrong = scope
            }
            if (scope == ReadValueScope.LATIN) {
                val end = (index + charCount).coerceAtMost(result.size)
                for (unit in index until end) {
                    val existing = result.getOrNull(unit) ?: ReadCharStyle()
                    result[unit] = existing.copy(latinScale = scale)
                }
            }
            index += charCount
        }
        return result
    }
}
