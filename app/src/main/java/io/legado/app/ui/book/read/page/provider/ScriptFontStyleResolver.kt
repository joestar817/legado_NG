package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadScriptClassifierContract
import io.legado.app.help.config.ReadValueScope

/**
 * Phase 3 TXT 生产化：脚本字体样式叠加。
 *
 * 纯函数，字体来源通过 [fontProvider] 注入（生产环境 = ReadBookConfig.scriptFontPath），
 * JVM 测试直接注入 lambda。规则与已验收的 PoC 一致：
 * - 已有高亮字体样式（fontPath / 字重 / 斜体非默认）的字符保持原样式；
 * - CJK / Latin / Other 命中且字体与 effective default 不同 → 写 ReadCharStyle(fontPath)；
 * - 中性字符继承前一个强脚本；DEFAULT 不叠加；Other 命中即换字体但不更新强脚本；
 * - provider 全部返回 null 时原样返回输入数组（零分配）。
 */
object ScriptFontStyleResolver {

    fun overlay(
        text: String,
        styles: Array<ReadCharStyle?>?,
        fontProvider: (ReadValueScope) -> String?,
    ): Array<ReadCharStyle?>? {
        val cjk = fontProvider(ReadValueScope.CJK)
        val latin = fontProvider(ReadValueScope.LATIN)
        val other = fontProvider(ReadValueScope.OTHER)
        if (cjk == null && latin == null && other == null) return styles
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
                        cjk
                    }

                    ReadValueScope.LATIN -> {
                        previousStrong = ReadValueScope.LATIN
                        latin
                    }

                    // OTHER 是真实脚本桶：命中即换字体，但不更新 previousStrong
                    //（Other 不算强脚本，后续中性字符仍继承最近的 CJK/Latin）。
                    ReadValueScope.OTHER -> other

                    ReadValueScope.DEFAULT -> null
                }
                if (font != null) {
                    for (unit in index until (index + charCount).coerceAtMost(result.size)) {
                        result[unit] = ReadCharStyle(fontPath = font)
                    }
                }
            } else {
                previousStrong = scope.takeIf {
                    it == ReadValueScope.CJK || it == ReadValueScope.LATIN
                } ?: previousStrong
            }
            index += charCount
        }
        return result
    }
}
