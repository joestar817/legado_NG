package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NgDialogThemeTest {
    private fun theme(dark: Boolean): NgThemeSnapshot = NgThemeResolver.resolve(
        NgLegacyThemeInput(
            primaryColor = 0xFFAA6655.toInt(),
            accentColor = 0xFFCC7766.toInt(),
            backgroundColor = if (dark) 0xFF202020.toInt() else 0xFFFFFFFF.toInt(),
            bottomBackground = if (dark) 0xFF202020.toInt() else 0xFFFFFFFF.toInt(),
            errorColor = 0xFFFF4433.toInt(),
            isDark = dark,
            isEInk = false,
        )
    )

    @Test
    fun matchingModeKeepsExistingPaletteAndGeometry() {
        for (dark in listOf(false, true)) {
            val source = theme(dark)
            assertSame(source, resolveNgDialogTheme(source, dark, source.colors.surface))
        }
    }

    @Test
    fun lightDrawerOnNightDialogGetsReadableContentWithoutChangingAccent() {
        val source = theme(false)
        val background = 0xFF1F1F1F.toInt()
        val result = resolveNgDialogTheme(source, true, background)
        assertTrue(result.isDark)
        assertEquals(source.colors.primary, result.colors.primary)
        assertEquals(source.colors.error, result.colors.error)
        assertEquals(source.shapes, result.shapes)
        assertEquals(source.spacing, result.spacing)
        assertTrue(NgColorMath.contrastRatio(background, result.colors.onSurface) >= 4.5)
        assertTrue(NgColorMath.contrastRatio(background, result.colors.onSurfaceVariant) >= 4.5)
        assertTrue(NgColorMath.contrastRatio(result.colors.inputContainer, result.colors.onSurface) >= 4.5)
    }

    @Test
    fun darkLocalThemeOnDayDialogGetsReadableContent() {
        val result = resolveNgDialogTheme(theme(true), false, 0xFFFFFFFF.toInt())
        assertEquals(false, result.isDark)
        assertTrue(NgColorMath.contrastRatio(0xFFFFFFFF.toInt(), result.colors.onSurface) >= 4.5)
        assertTrue(NgColorMath.contrastRatio(0xFFFFFFFF.toInt(), result.colors.onSurfaceVariant) >= 4.5)
    }

    @Test
    fun einkPaletteIsPreserved() {
        val source = theme(false).copy(isEInk = true)
        assertSame(source, resolveNgDialogTheme(source, true, 0xFF1F1F1F.toInt()))
    }
}
