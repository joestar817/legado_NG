package io.legado.app.ui.design.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SwatchMatrixTest {

    @Test
    fun spectrumHasClassicGridShape() {
        val cells = SwatchMatrix.cells(SwatchMatrix.SPECTRUM_ID)
        assertEquals(SwatchMatrix.ROWS * SwatchMatrix.COLS, cells.size)
        cells.forEach { color ->
            assertEquals(255, color ushr 24 and 0xFF)
        }
        val first = cells[0]
        val lastHue = cells[SwatchMatrix.HUE_COLUMNS - 1]
        assertTrue(first != lastHue)
        val grayTop = cells[SwatchMatrix.HUE_COLUMNS]
        val r = grayTop ushr 16 and 0xFF
        val g = grayTop ushr 8 and 0xFF
        val b = grayTop and 0xFF
        assertEquals(r, g)
        assertEquals(g, b)
    }

    @Test
    fun paperFamilyStaysNearWarmHue() {
        val cells = SwatchMatrix.cells("paper")
        assertEquals(104, cells.size)
        val sample = cells[3 * SwatchMatrix.COLS + 6]
        val hue = accentHueOf(sample)
        val delta = abs(hue - 65.0).let { if (it > 180) 360 - it else it }
        assertTrue("paper cell hue $hue", delta < 50.0)
        assertTrue(NgColorMath.relativeLuminance(cells[6]) > 0.4)
        assertTrue(NgColorMath.relativeLuminance(cells[7 * SwatchMatrix.COLS + 6]) < 0.4)
    }

    @Test
    fun forestFamilyIsNotSpectrumRainbow() {
        val spectrum = SwatchMatrix.cells(SwatchMatrix.SPECTRUM_ID)
        val forest = SwatchMatrix.cells("forest")
        assertTrue(spectrum[6] != forest[6])
        val hue = accentHueOf(forest[4 * SwatchMatrix.COLS + 6])
        assertTrue("forest hue $hue", hue in 90.0..180.0)
    }
}
