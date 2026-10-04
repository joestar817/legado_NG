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

/**
 * 生产分类器（Phase 3 TXT PoC 落地）：机械实现 §3.7-2。
 * 契约测试 ReadScriptClassifierTest 直接消费本对象。
 */
object ReadScriptClassifierContract : ReadScriptClassifier {
    override fun classify(codePoint: Int, previousStrong: ReadValueScope?): ReadValueScope {
        // 全角形式先归 CJK（按计划：U+3000–303F、U+FF00–FFEF）
        if (codePoint in 0x3000..0x303F || codePoint in 0xFF00..0xFFEF) {
            return ReadValueScope.CJK
        }
        return when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            -> ReadValueScope.CJK

            Character.UnicodeScript.LATIN -> ReadValueScope.LATIN
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED,
            -> previousStrong ?: ReadValueScope.DEFAULT

            else -> ReadValueScope.OTHER
        }
    }
}
