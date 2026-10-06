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
    fun parseAiHexPalettesUsesNameAndTextNotCatalogSeed() {
        val looks = parseAiPaperLooks(
            raw = """
                [
                  {
                    "name": "Warm Paper",
                    "bg": "#F4EDE0",
                    "text": "#3A3228",
                    "secondaryText": "#6A5E50",
                    "highlight": "#E6C97A",
                    "accent": "#5B6E4E"
                  },
                  {
                    "name": "Cool Mist",
                    "bg": "#E8EEF2",
                    "text": "#2C3338",
                    "secondaryText": "#5C6A72",
                    "highlight": "#D4C48A",
                    "accent": "#3D6B8A"
                  }
                ]
            """.trimIndent(),
            isNight = false,
            isEink = false,
        )
        assertEquals(2, looks.size)
        assertEquals("Warm Paper", looks[0].labelKey)
        assertEquals("Cool Mist", looks[1].labelKey)
        assertEquals(0xFFF4EDE0.toInt(), looks[0].background)
        assertEquals(0xFF3A3228.toInt(), looks[0].foreground)
        assertEquals(0xFFE6C97A.toInt(), looks[0].highlight)
        assertEquals(0xFF5B6E4E.toInt(), looks[0].accent)
        assertEquals(null, looks[0].seed)
        assertTrue(looks[0].contrastRatio >= PaperSenseGenerator.AA_FLOOR)
    }

    @Test
    fun parseAiHexPalettesAcceptsFgFallbackAndMarkdownFence() {
        val looks = parseAiPaperLooks(
            raw = """
                ```json
                [{"name":"Ivory Page","bg":"#F7F3EA","fg":"#2F2A24","highlight":"#E2C56A","accent":"#4A6678"}]
                ```
            """.trimIndent(),
            isNight = false,
            isEink = false,
        )
        assertEquals(1, looks.size)
        assertEquals("Ivory Page", looks[0].labelKey)
        assertEquals(0xFF2F2A24.toInt(), looks[0].foreground)
    }

    @Test
    fun buildAiThemeUserPromptNamesCurrentModeAndUiLanguage() {
        assertTrue(
            buildAiThemeUserPrompt("warm ivory", isNight = false, isEink = false, uiLanguageTag = "en")
                .contains("CURRENT_MODE: day"),
        )
        assertTrue(
            buildAiThemeUserPrompt("dim", isNight = true, isEink = false, uiLanguageTag = "en")
                .contains("CURRENT_MODE: night"),
        )
        assertTrue(
            buildAiThemeUserPrompt("eink", isNight = true, isEink = true, uiLanguageTag = "en")
                .contains("CURRENT_MODE: eink"),
        )
        assertTrue(
            buildAiThemeUserPrompt("  less blue  ", isNight = false, isEink = false, uiLanguageTag = "en")
                .contains("less blue"),
        )
        assertTrue(
            buildAiThemeUserPrompt("暖纸", isNight = false, isEink = false, uiLanguageTag = "zh-CN")
                .startsWith("UI_LANGUAGE: zh-CN"),
        )
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
