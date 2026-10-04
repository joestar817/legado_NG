package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadValueScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * TXT 生产化叠加契约：脚本字体 ReadCharStyle 生成（fontProvider 注入）。
 */
class ScriptFontStyleResolverTest {

    private fun provider(
        cjk: String? = "/fonts/cjk.ttf",
        latin: String? = "/fonts/latin.ttf",
    ): (ReadValueScope) -> String? = { scope ->
        when (scope) {
            ReadValueScope.CJK -> cjk
            ReadValueScope.LATIN -> latin
            else -> null
        }
    }

    @Test
    fun `empty provider returns original array unchanged`() {
        val styles = arrayOfNulls<ReadCharStyle?>(3)
        val none: (ReadValueScope) -> String? = { null }
        assertSame(styles, ScriptFontStyleResolver.overlay("abc", styles, none))
    }

    @Test
    fun `cjk chars get cjk font`() {
        val result = ScriptFontStyleResolver.overlay("你好", null, provider())!!
        assertEquals("/fonts/cjk.ttf", result[0]?.fontPath)
        assertEquals("/fonts/cjk.ttf", result[1]?.fontPath)
    }

    @Test
    fun `latin chars get latin font`() {
        val result = ScriptFontStyleResolver.overlay("Hello", null, provider())!!
        assertEquals("/fonts/latin.ttf", result[0]?.fontPath)
        assertEquals("/fonts/latin.ttf", result[4]?.fontPath)
    }

    @Test
    fun `mixed text switches per script`() {
        val result = ScriptFontStyleResolver.overlay("A你B", null, provider())!!
        assertEquals("/fonts/latin.ttf", result[0]?.fontPath)
        assertEquals("/fonts/cjk.ttf", result[1]?.fontPath)
        assertEquals("/fonts/latin.ttf", result[2]?.fontPath)
    }

    @Test
    fun `neutral inherits previous strong script`() {
        val result = ScriptFontStyleResolver.overlay("中，1", null, provider())!!
        assertEquals("/fonts/cjk.ttf", result[0]?.fontPath)
        assertEquals("/fonts/cjk.ttf", result[1]?.fontPath)
        assertEquals("/fonts/cjk.ttf", result[2]?.fontPath)
    }

    @Test
    fun `neutral at start falls back to body font`() {
        val result = ScriptFontStyleResolver.overlay("1A", null, provider())!!
        assertNull(result[0])
        assertEquals("/fonts/latin.ttf", result[1]?.fontPath)
    }

    @Test
    fun `highlight font style wins over script overlay`() {
        val styles = arrayOfNulls<ReadCharStyle?>(3)
        styles[1] = ReadCharStyle(fontPath = "/fonts/highlight.ttf")
        val result = ScriptFontStyleResolver.overlay("你A你", styles, provider())!!
        assertEquals("/fonts/cjk.ttf", result[0]?.fontPath)
        assertEquals("/fonts/highlight.ttf", result[1]?.fontPath)
        assertEquals("/fonts/cjk.ttf", result[2]?.fontPath)
    }

    @Test
    fun `other script chars are left without overlay`() {
        val result = ScriptFontStyleResolver.overlay("αβ", null, provider())!!
        assertNull(result[0])
        assertNull(result[1])
    }

    @Test
    fun `single script configured only overlays that script`() {
        val result = ScriptFontStyleResolver.overlay("A你", null, provider(cjk = null))!!
        assertEquals("/fonts/latin.ttf", result[0]?.fontPath)
        assertNull(result[1])
    }
}
