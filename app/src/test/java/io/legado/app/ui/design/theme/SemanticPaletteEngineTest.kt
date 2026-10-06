package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SemanticPaletteEngineTest {

    @Test
    fun naturalLightStaysInVdtComfortBand() {
        val seed = ReadingPaletteCatalog.seed("natural")!!
        val tokens = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.LIGHT)
        val ratio = NgColorMath.displayedContrast(tokens.text, tokens.background)
        assertTrue(ratio >= PaperSenseGenerator.AA_FLOOR)
        assertTrue(ratio >= 8.0)
        assertTrue(NgColorMath.relativeLuminance(tokens.background) > 0.4)
        assertTrue(tokens.background != 0xFFFFFFFF.toInt())
        assertTrue(tokens.text != 0xFF000000.toInt())
    }

    @Test
    fun naturalDarkAvoidsPaperWhiteAndPureBlack() {
        val seed = ReadingPaletteCatalog.seed("natural")!!
        val tokens = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.DARK)
        val ratio = NgColorMath.displayedContrast(tokens.text, tokens.background)
        assertTrue(ratio >= PaperSenseGenerator.AA_FLOOR)
        assertTrue(NgColorMath.relativeLuminance(tokens.background) < 0.4)
        assertTrue(NgColorMath.relativeLuminance(tokens.text) < 0.85)
    }

    @Test
    fun sameSeedKeepsFamilyAcrossDayAndNight() {
        val seed = ReadingPaletteCatalog.seed("paper")!!
        val day = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.LIGHT)
        val night = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.DARK)
        assertTrue(NgColorMath.relativeLuminance(day.background) > 0.4)
        assertTrue(NgColorMath.relativeLuminance(night.background) < 0.4)
        val dayHue = accentHueOf(day.background)
        val nightHue = accentHueOf(night.background)
        val hueDelta = abs(dayHue - nightHue).let { if (it > 180) 360 - it else it }
        assertTrue("paper hue drift $hueDelta", hueDelta < 40.0)
    }

    @Test
    fun warmPaperAccentIsNotComplementary() {
        val seed = ReadingPaletteCatalog.seed("natural")!!
        val tokens = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.LIGHT)
        val accentHue = accentHueOf(tokens.accent)
        val hct = com.materialkolor.hct.Hct.fromInt(tokens.accent)
        assertTrue("accent chroma ${hct.chroma} too high", hct.chroma < 48.0)
        assertTrue("accent $accentHue not restrained blue", accentHue in 200.0..280.0)
    }

    @Test
    fun forestAccentStaysGreenNotComplementaryRed() {
        val seed = ReadingPaletteCatalog.seed("forest")!!
        val tokens = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.LIGHT)
        val hue = accentHueOf(tokens.accent)
        assertTrue("forest accent $hue", hue in 100.0..180.0)
    }

    @Test
    fun einkStaysPositivePolarityAndNeutral() {
        ReadingPaletteCatalog.seeds.forEach { seed ->
            val tokens = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.EINK)
            assertTrue(NgColorMath.relativeLuminance(tokens.background) > 0.4)
            assertTrue(NgColorMath.relativeLuminance(tokens.text) < 0.4)
        }
    }

    @Test
    fun generatorUsesNamedSeedsInsteadOfRandomHsl() {
        val looks = PaperSenseGenerator.generate(
            isNight = false,
            isEink = false,
            count = 3,
            random = Random(4),
            seedIds = listOf("natural", "paper", "ink"),
        )
        assertEquals(3, looks.size)
        val ids = looks.map { ReadingPaletteCatalog.baseId(it.labelKey) }.toSet()
        assertTrue(ids.contains("natural"))
        assertTrue(ids.contains("paper"))
        looks.forEach { look ->
            assertTrue(look.contrastRatio >= PaperSenseGenerator.AA_FLOOR)
        }
    }

    @Test
    fun lookForKeepsSeedSoNightAndEinkCanBeDerived() {
        val seed = ReadingPaletteCatalog.seed("paper")!!
        val look = SemanticPaletteEngine.lookFor(seed, isNight = false, isEink = false)
        val attached = look?.seed
        assertEquals("paper", attached?.id)
        requireNotNull(attached)
        val night = SemanticPaletteEngine.deriveTokens(attached, ReadingDisplayMode.DARK)
        val eink = SemanticPaletteEngine.deriveTokens(attached, ReadingDisplayMode.EINK)
        assertTrue(NgColorMath.relativeLuminance(night.background) < 0.4)
        assertTrue(NgColorMath.relativeLuminance(eink.background) > 0.4)
    }

    @Test
    fun parseAiSeedDeltaDoesNotNeedRawHex() {
        val looks = parseAiPaperLooks(
            raw = """[{"id":"mist","warmth":-0.4,"contrast":0.4}]""",
            isNight = false,
            isEink = false,
        )
        assertEquals(1, looks.size)
        assertEquals("mist", looks[0].labelKey)
        assertTrue(looks[0].contrastRatio >= PaperSenseGenerator.AA_FLOOR)
    }
}
