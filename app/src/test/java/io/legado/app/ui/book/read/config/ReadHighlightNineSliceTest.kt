package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadHighlightNineSliceTest {
    @Test
    fun wideBubbleEstimateProtectsCornersAndKeepsStretchArea() {
        assertEquals(listOf(0.07f, 0.07f, 0.25f, 0.25f), estimateNineSliceCuts(1024, 270))
        assertEquals(listOf(0.25f, 0.25f, 0.07f, 0.07f), estimateNineSliceCuts(270, 1024))
    }

    @Test
    fun wideImageUsesItsOwnBoundsRatherThanPreviewLetterbox() {
        val bounds = nineSliceImageBounds(1000, 100, 300f, 250f)
        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(110f, bounds.top, 0.001f)
        assertEquals(300f, bounds.width, 0.001f)
        assertEquals(30f, bounds.height, 0.001f)
        assertEquals(118.1f, bounds.top + bounds.height * 0.27f, 0.001f)
    }

    @Test
    fun tallImageIsCenteredHorizontally() {
        val bounds = nineSliceImageBounds(100, 1000, 300f, 250f)
        assertEquals(137.5f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(25f, bounds.width, 0.001f)
        assertEquals(250f, bounds.height, 0.001f)
    }

    @Test
    fun stepperUsesOnePercentAndClampsAtBothEnds() {
        assertEquals(0.04f, stepNineSliceCut(0.03f, 1), 0.00001f)
        assertEquals(0.02f, stepNineSliceCut(0.03f, -1), 0.00001f)
        assertEquals(0f, stepNineSliceCut(0f, -1), 0f)
        assertEquals(0.5f, stepNineSliceCut(0.5f, 1), 0f)
    }
}
