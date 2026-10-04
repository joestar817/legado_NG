package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * TXT PoC 叠加语义契约：脚本字体 ReadCharStyle 的生成与高亮样式优先级。
 */
class TxtScriptFontPocTest {

    @Test
    fun `disabled returns original array unchanged`() {
        val styles = arrayOfNulls<ReadCharStyle?>(3)
        assertSame(styles, TxtScriptFontPoc.overlayStyles("abc", styles, enabled = false))
    }

    @Test
    fun `cjk chars get cjk font`() {
        val result = TxtScriptFontPoc.overlayStyles("你好", null, enabled = true)!!
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[0]?.fontPath)
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[1]?.fontPath)
    }

    @Test
    fun `latin chars get latin font`() {
        val result = TxtScriptFontPoc.overlayStyles("Hello", null, enabled = true)!!
        assertEquals(TxtScriptFontPoc.LATIN_FONT, result[0]?.fontPath)
        assertEquals(TxtScriptFontPoc.LATIN_FONT, result[4]?.fontPath)
    }

    @Test
    fun `mixed text switches per script`() {
        val result = TxtScriptFontPoc.overlayStyles("A你B", null, enabled = true)!!
        assertEquals(TxtScriptFontPoc.LATIN_FONT, result[0]?.fontPath)
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[1]?.fontPath)
        assertEquals(TxtScriptFontPoc.LATIN_FONT, result[2]?.fontPath)
    }

    @Test
    fun `neutral inherits previous strong script`() {
        // "中，1"：逗号与数字继承前面的 CJK
        val result = TxtScriptFontPoc.overlayStyles("中，1", null, enabled = true)!!
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[0]?.fontPath)
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[1]?.fontPath)
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[2]?.fontPath)
    }

    @Test
    fun `neutral at start falls back to body font`() {
        val result = TxtScriptFontPoc.overlayStyles("1A", null, enabled = true)!!
        assertNull(result[0])
        assertEquals(TxtScriptFontPoc.LATIN_FONT, result[1]?.fontPath)
    }

    @Test
    fun `highlight font style wins over script overlay`() {
        val styles = arrayOfNulls<ReadCharStyle?>(3)
        styles[1] = ReadCharStyle(fontPath = "/fonts/highlight.ttf")
        val result = TxtScriptFontPoc.overlayStyles("你A你", styles, enabled = true)!!
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[0]?.fontPath)
        assertEquals("/fonts/highlight.ttf", result[1]?.fontPath)
        assertEquals(TxtScriptFontPoc.CJK_FONT, result[2]?.fontPath)
    }

    @Test
    fun `other script chars are left without overlay`() {
        val result = TxtScriptFontPoc.overlayStyles("αβ", null, enabled = true)!!
        assertNull(result[0])
        assertNull(result[1])
    }
}
