package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadScriptClassifierContract
import io.legado.app.help.config.ReadValueScope

/**
 * Phase 3 TXT PoC：验证 TXT 渲染层按脚本选字体的 ReadCharStyle 链路。
 *
 * 不建模型、不加持久化、不改 UI：ENABLED 时在 [TextChapterLayout] 的 highlight 样式
 * 数组上叠加脚本字体 ReadCharStyle，走既有 `remeasureHighlightFonts` / `TextColumn.draw`
 * 链路（正好是生产实现要接的挂接点）。
 *
 * PoC 后删除本文件与 hook。
 */
object TxtScriptFontPoc {
    const val ENABLED = false
    const val LATIN_FONT = "assets://poc-fonts/latin.ttf"
    const val CJK_FONT = "assets://poc-fonts/cjk.ttf"

    /**
     * 叠加脚本字体样式。
     * - 已有高亮字体样式（fontPath 非空 / 非 400 字重 / 斜体）的字符保持原样式；
     * - CJK → [CJK_FONT]，Latin → [LATIN_FONT]；
     * - 中性字符继承前一个强脚本；DEFAULT/OTHER 不叠加（回落正文字体）。
     */
    fun overlayStyles(
        text: String,
        styles: Array<ReadCharStyle?>?,
        enabled: Boolean = ENABLED,
    ): Array<ReadCharStyle?>? {
        if (!enabled) return styles
        val result = styles?.copyOf() ?: arrayOfNulls(text.length)
        var previousStrong: ReadValueScope? = null
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val scope = ReadScriptClassifierContract.classify(codePoint, previousStrong)
            val existing = result.getOrNull(index)
            val hasHighlightFont = existing != null && (
                existing.fontPath.isNotBlank() || existing.fontWeight != 400 || existing.isItalic
            )
            if (!hasHighlightFont) {
                val font = when (scope) {
                    ReadValueScope.CJK -> {
                        previousStrong = ReadValueScope.CJK
                        CJK_FONT
                    }

                    ReadValueScope.LATIN -> {
                        previousStrong = ReadValueScope.LATIN
                        LATIN_FONT
                    }

                    ReadValueScope.DEFAULT,
                    ReadValueScope.OTHER,
                    -> null
                }
                if (font != null) {
                    for (unit in index until (index + charCount).coerceAtMost(result.size)) {
                        result[unit] = ReadCharStyle(fontPath = font)
                    }
                }
            } else {
                previousStrong = scope.takeIf { it == ReadValueScope.CJK || it == ReadValueScope.LATIN }
                    ?: previousStrong
            }
            index += charCount
        }
        return result
    }
}
