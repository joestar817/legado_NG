package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 0 契约测试：字符级脚本分类与中性字符边界（§3.7-2）。
 */
class ReadScriptClassifierTest {

    private val classifier: ReadScriptClassifier = ReferenceScriptClassifier

    @Test
    fun `han is cjk`() {
        assertEquals(ReadValueScope.CJK, classifier.classify('中'.code, previousStrong = null))
        assertEquals(ReadValueScope.CJK, classifier.classify('文'.code, previousStrong = null))
    }

    @Test
    fun `kana and hangul are cjk`() {
        assertEquals(ReadValueScope.CJK, classifier.classify('あ'.code, previousStrong = null)) // Hiragana
        assertEquals(ReadValueScope.CJK, classifier.classify('ア'.code, previousStrong = null)) // Katakana
        assertEquals(ReadValueScope.CJK, classifier.classify('한'.code, previousStrong = null)) // Hangul
    }

    @Test
    fun `latin is latin`() {
        assertEquals(ReadValueScope.LATIN, classifier.classify('a'.code, previousStrong = null))
        assertEquals(ReadValueScope.LATIN, classifier.classify('A'.code, previousStrong = null))
    }

    @Test
    fun `fullwidth punctuation is cjk per plan`() {
        assertEquals(ReadValueScope.CJK, classifier.classify('，'.code, previousStrong = null)) // U+FF0C
        assertEquals(ReadValueScope.CJK, classifier.classify('、'.code, previousStrong = null)) // U+3001
    }

    @Test
    fun `neutral inherits previous strong script`() {
        // 中文，with ASCII 数字与标点
        assertEquals(ReadValueScope.CJK, classifier.classify('中'.code, previousStrong = null))
        val cjk = ReadValueScope.CJK
        assertEquals(ReadValueScope.CJK, classifier.classify(','.code, previousStrong = cjk))
        assertEquals(ReadValueScope.CJK, classifier.classify('1'.code, previousStrong = cjk))
        assertEquals(ReadValueScope.CJK, classifier.classify(' '.code, previousStrong = cjk))

        val latin = ReadValueScope.LATIN
        assertEquals(ReadValueScope.LATIN, classifier.classify(' '.code, previousStrong = latin))
        assertEquals(ReadValueScope.LATIN, classifier.classify('2'.code, previousStrong = latin))
    }

    @Test
    fun `neutral at paragraph start falls to default`() {
        assertEquals(ReadValueScope.DEFAULT, classifier.classify(' '.code, previousStrong = null))
        assertEquals(ReadValueScope.DEFAULT, classifier.classify('1'.code, previousStrong = null))
    }

    @Test
    fun `non latin non cjk scripts are other`() {
        assertEquals(ReadValueScope.OTHER, classifier.classify('α'.code, previousStrong = null)) // Greek
        assertEquals(ReadValueScope.OTHER, classifier.classify('Ж'.code, previousStrong = null)) // Cyrillic
        assertEquals(ReadValueScope.OTHER, classifier.classify('ก'.code, previousStrong = null)) // Thai
    }
}

/**
 * 契约参考实现：按 §3.7-2 的规则机械实现。
 * Phase 3 生产实现（含性能优化）必须通过同一组测试。
 */
private object ReferenceScriptClassifier : ReadScriptClassifier {
    override fun classify(codePoint: Int, previousStrong: ReadValueScope?): ReadValueScope {
        // 全角形式先归 CJK（按计划：U+3000–303F、U+FF00–FFEF）
        if (codePoint in 0x3000..0x303F || codePoint in 0xFF00..0xFFEF) {
            return ReadValueScope.CJK
        }
        val script = Character.UnicodeScript.of(codePoint)
        return when (script) {
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
