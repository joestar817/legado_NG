package io.legado.app.help.config

/**
 * Phase 0 契约：字符级脚本分类（§3.7-2）。
 *
 * - CJK：Han / Hiragana / Katakana / Hangul + 全角形式（U+3000–303F、U+FF00–FFEF）
 * - Latin：Latin 系
 * - Neutral（Common / Inherited：半角标点、数字、空白）：继承前一个强脚本字符；段首中性字符用 DEFAULT
 * - Other：其余
 *
 * myreader 的整书语言检测（BookLanguageDetector）不参与新模型，仅用于迁移。
 */
fun interface ReadScriptClassifier {
    /**
     * @param codePoint Unicode code point
     * @param previousStrong 前一个强脚本字符的 scope；段首为 null
     * @return 字符所属脚本 scope；中性字符返回 previousStrong ?: DEFAULT
     */
    fun classify(codePoint: Int, previousStrong: ReadValueScope?): ReadValueScope
}
