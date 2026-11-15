package io.legado.app.ui.design.theme

import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class NgDockSelectionPaletteTest {
    private fun colors(seed: Int, dark: Boolean = false) =
        NgDockSelectionPalette.resolve(seed, 0xFFF4F6F8.toInt(), dark)

    @Test
    fun opaqueWhiteContentPairsAcrossRgbCube() {
        val channels = listOf(0, 51, 102, 153, 204, 255)
        for (r in channels) for (g in channels) for (b in channels) {
            val seed = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            for (dark in listOf(false, true)) {
                val pair = colors(seed, dark)
                assertEquals(255, pair.container ushr 24)
                assertEquals(0xFFFFFFFF.toInt(), pair.content)
                assertTrue("seed=$seed dark=$dark", NgColorMath.contrastRatio(pair.container, pair.content) >= 4.5)
            }
        }
    }

    @Test
    fun coloredSeedsKeepHueAndMoreChromaThanLegacyTint() {
        for (seed in listOf(0xFF008F78.toInt(), 0xFF1A73E8.toInt(), 0xFFD16A29.toInt())) {
            val source = Hct.fromInt(seed)
            val selected = Hct.fromInt(colors(seed).container)
            val distance = abs(source.hue - selected.hue)
            assertTrue(min(distance, 360.0 - distance) < 4.0)
            val legacy = Hct.fromInt(NgColorMath.blend(0xFFF4F6F8.toInt(), seed, 0.16f))
            assertTrue(selected.chroma > legacy.chroma + 4.0)
        }
    }

    @Test
    fun neutralSeedsStayNeutralAndDepthFollowsMode() {
        for (seed in listOf(0xFF000000.toInt(), 0xFF777777.toInt(), 0xFFFFFFFF.toInt())) {
            val day = Hct.fromInt(colors(seed).container)
            val night = Hct.fromInt(colors(seed, true).container)
            assertTrue(day.chroma < 8.0 && night.chroma < 8.0)
            assertEquals(48.0, day.tone, 1.0)
            assertEquals(32.0, night.tone, 1.0)
        }
    }

    @Test
    fun inputTransparencyDoesNotChangeGeneratedPair() {
        assertEquals(colors(0xFF008F78.toInt()), colors(0x22008F78))
    }

    @Test
    fun preservedThemeSurfacesRemainByteExact() {
        for (surface in listOf(0xF2FFFFFF.toInt(), 0xFFFFFFFF.toInt())) {
            assertEquals(
                NgDockSelectionColors(surface, 0xFF008F78.toInt()),
                NgDockSelectionPalette.resolve(0xFF008F78.toInt(), surface, false, preserveExisting = true),
            )
        }
    }
}
