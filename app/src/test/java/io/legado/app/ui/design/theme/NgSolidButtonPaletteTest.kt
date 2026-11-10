package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgSolidButtonPaletteTest {
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun lightNightButtonsKeepReadableWhiteContentAcrossColors() {
        val backgrounds = listOf(
            0xFFDFFFF6.toInt(), 0xFFEB4333.toInt(), 0xFF5CCBFF.toInt(),
            0xFFFFFF00.toInt(), 0xFFFFFFFF.toInt(), 0xFF888888.toInt(),
        )
        for (background in backgrounds) {
            for (content in listOf(white, 0xFFF8F5F8.toInt())) {
                val resolved = NgSolidButtonPalette.resolveContainer(background, content, isDark = true)
                assertTrue(
                    "background=$background content=$content",
                    NgColorMath.contrastRatio(resolved, content) >= 4.5,
                )
            }
        }
    }

    @Test
    fun dayDarkTextAndAlreadyReadableButtonsStayUnchanged() {
        val mint = 0xFFDFFFF6.toInt()
        val dark = 0xFF003D35.toInt()
        assertEquals(mint, NgSolidButtonPalette.resolveContainer(mint, white, isDark = false))
        assertEquals(mint, NgSolidButtonPalette.resolveContainer(mint, 0xFF1D1B20.toInt(), isDark = true))
        assertEquals(dark, NgSolidButtonPalette.resolveContainer(dark, white, isDark = true))
    }

    @Test
    fun readingNightConfirmUsesPaleAccentAndReadableDarkSurface() {
        val mint = 0xFFE5FFF8.toInt()
        val navy = 0xFF12314D.toInt()
        val colors = NgSolidButtonPalette.resolveLightTonalAction(mint, navy)
        assertEquals(mint, colors.container)
        assertEquals(navy, colors.content)
        assertTrue(NgColorMath.contrastRatio(colors.container, colors.content) >= 4.5)
    }

    @Test
    fun readingNightConfirmProducesLightReadableTonalSurfaceForOtherAccents() {
        val navy = 0xFF12314D.toInt()
        for (accent in listOf(0xFF005849.toInt(), 0xFFFFD000.toInt(), 0xFF888888.toInt())) {
            val colors = NgSolidButtonPalette.resolveLightTonalAction(accent, navy)
            assertTrue(NgColorMath.isLight(colors.container))
            assertTrue(NgColorMath.contrastRatio(colors.container, colors.content) >= 4.5)
        }
    }

    @Test
    fun lowContrastNightPrimaryUsesLightAccentAndDarkContentTogether() {
        val mint = 0xFFE5FFF8.toInt()
        val navy = 0xFF12314D.toInt()
        val colors = NgSolidButtonPalette.resolvePrimary(mint, white, navy, isDark = true)
        assertEquals(mint, colors.container)
        assertEquals(navy, colors.content)
        assertTrue(NgColorMath.contrastRatio(colors.container, colors.content) >= 4.5)
    }

    @Test
    fun lowContrastNightPrimaryRemainsReadableForMultipleAccents() {
        val navy = 0xFF12314D.toInt()
        for (accent in listOf(0xFF5CCBFF.toInt(), 0xFFFFFF00.toInt(), 0xFF888888.toInt())) {
            val colors = NgSolidButtonPalette.resolvePrimary(accent, white, navy, isDark = true)
            assertTrue(NgColorMath.isLight(colors.container))
            assertTrue(NgColorMath.contrastRatio(colors.container, colors.content) >= 4.5)
        }
    }

    @Test
    fun primaryKeepsDayDarkContentAndAlreadyReadablePairs() {
        val mint = 0xFFE5FFF8.toInt()
        val navy = 0xFF12314D.toInt()
        val darkGreen = 0xFF005849.toInt()
        assertEquals(NgLightActionColors(mint, white),
            NgSolidButtonPalette.resolvePrimary(mint, white, navy, isDark = false))
        assertEquals(NgLightActionColors(mint, navy),
            NgSolidButtonPalette.resolvePrimary(mint, navy, navy, isDark = true))
        assertEquals(NgLightActionColors(darkGreen, white),
            NgSolidButtonPalette.resolvePrimary(darkGreen, white, navy, isDark = true))
    }
}
