package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 0 契约测试：字符级脚本分类与中性字符边界（§3.7-2）。
 */
class ReadScriptClassifierTest {

    private val classifier: ReadScriptClassifier = ReadScriptClassifierContract

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
