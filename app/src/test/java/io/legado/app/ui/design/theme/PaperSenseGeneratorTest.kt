package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PaperSenseGeneratorTest {

    @Test
    fun flattenCompositesSourceOverThenMeasuresContrast() {
        val bg = 0xFFFAF9F5.toInt()
        val translucentFg = 0x80333333.toInt()
        val flat = NgColorMath.flatten(translucentFg, bg)
        assertEquals(255, flat ushr 24 and 0xFF)
        val stacked = NgColorMath.displayedContrast(translucentFg, bg)
        val opaque = NgColorMath.contrastRatio(flat, NgColorMath.opaque(bg))
        assertEquals(opaque, stacked, 0.001)
    }

    @Test
    fun wcagLabelNamesTheStandard() {
        assertEquals("WCAG 15.3:1  AAA", NgColorMath.wcagContrastLabel(15.3))
        assertEquals("WCAG 4.5:1  AA", NgColorMath.wcagContrastLabel(4.5))
        assertEquals("WCAG 3.0:1  —", NgColorMath.wcagContrastLabel(3.0))
    }

    @Test
    fun dayLooksMeetAaAndStayOffPureBlackWhite() {
        val looks = PaperSenseGenerator.generate(
            isNight = false,
            isEink = false,
            random = Random(7),
        )
        assertTrue(looks.size >= 3)
        looks.forEach { look ->
            assertTrue(look.contrastRatio >= PaperSenseGenerator.AA_FLOOR)
            assertTrue(NgColorMath.relativeLuminance(look.background) > 0.4)
            assertTrue(look.foreground != 0xFF000000.toInt())
            assertTrue(look.background != 0xFFFFFFFF.toInt())
        }
    }

    @Test
    fun nightLooksStayAboveAaAndAvoidPaperWhiteText() {
        val looks = PaperSenseGenerator.generate(
            isNight = true,
            isEink = false,
            random = Random(11),
        )
        assertTrue(looks.isNotEmpty())
        looks.forEach { look ->
            assertTrue(look.contrastRatio >= PaperSenseGenerator.AA_FLOOR)
            assertTrue(NgColorMath.relativeLuminance(look.background) < 0.4)
            assertTrue(NgColorMath.relativeLuminance(look.foreground) < 0.85)
        }
    }

    @Test
    fun einkLooksStayPositivePolarity() {
        val looks = PaperSenseGenerator.generate(
            isNight = true,
            isEink = true,
            random = Random(3),
        )
        assertTrue(looks.isNotEmpty())
        looks.forEach { look ->
            assertTrue(NgColorMath.relativeLuminance(look.background) > 0.4)
            assertTrue(NgColorMath.relativeLuminance(look.foreground) < 0.4)
        }
    }

    @Test
    fun lowContrastNightTextIsRepairedInsteadOfShown() {
        val bg = 0xFF1A1A1A.toInt()
        val tooDark = 0xFF3A3A3A.toInt()
        val fixed = NgColorMath.fixForegroundContrast(
            foreground = tooDark,
            background = bg,
            minContrast = PaperSenseGenerator.NIGHT_MIN_CONTRAST,
            maxContrast = PaperSenseGenerator.NIGHT_MAX_CONTRAST,
            isNight = true,
        )
        val ratio = NgColorMath.displayedContrast(fixed, bg)
        assertTrue(
            "repaired contrast $ratio for #${Integer.toHexString(fixed)}",
            ratio >= PaperSenseGenerator.AA_FLOOR,
        )
    }
}
