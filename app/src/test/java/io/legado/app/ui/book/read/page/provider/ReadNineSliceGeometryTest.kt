package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadNineSliceGeometryTest {
    private val bubble = ReadCharStyle(
        bgImage = "bubble.png", bgImageFit = 3,
        npLeft = 0.03f, npRight = 0.03f, npTop = 0.11f, npBottom = 0.12f,
    )
    private fun geometry(style: ReadCharStyle) = ReadNineSliceGeometry.from(1024, 270, style)

    @Test
    fun suppliedBubbleUsesLineGapForOuterFrameEvenOnShortLines() {
        val cuts = geometry(bubble)
        assertEquals(29, cuts.top)
        assertEquals(237, cuts.bottom)
        val (top, bottom) = cuts.verticalInsets(40f, 1.5f)
        assertEquals(29f * 40f / 208f, top, 0.001f)
        assertEquals(33f * 40f / 208f, bottom, 0.001f)
        // No gap collapses only the outside frame, not the center's source rectangle.
        assertEquals(0f to 0f, cuts.verticalInsets(40f, 1f))
        assertEquals(208, cuts.bottom - cuts.top)
    }

    @Test
    fun largeLineGapKeepsOriginalFrameSize() {
        assertEquals(29f to 33f, geometry(bubble).verticalInsets(300f, 2f))
    }

    @Test
    fun allFourCornersUseOneScaleAndLayoutUsesTheSameWidths() {
        for (style in listOf(bubble, bubble.copy(npLeft = .07f, npRight = .07f, npTop = .25f, npBottom = .25f))) {
            val source = geometry(style)
            for (height in listOf(32f, 40f, 60f)) for (spacing in listOf(1f, 1.2f, 1.5f, 2f)) {
                val frame = source.forLine(height, spacing)
                val (top, bottom) = source.verticalInsets(height, spacing)
                val scale = top / source.top
                assertEquals(scale, bottom / (source.height - source.bottom), .00001f)
                assertEquals(scale, frame.leftWidth / source.left, .00001f)
                assertEquals(scale, frame.rightWidth / (source.width - source.right), .00001f)
                val expected = frame.leftWidth + frame.rightWidth + 12f
                val budget = ReadNineSliceWidthBudget(arrayOf(style, style), { frame }, 6f)
                val insets = nineSliceLineInsets(listOf("甲", "乙"), arrayOf(style, style), 0, 6f) { frame }
                assertEquals(expected, budget.width(0, 2), .0001f)
                assertEquals(expected, insets.before.sum() + insets.after, .0001f)
            }
        }
    }

    @Test
    fun continuationLineReservesBothSidesWithoutDependingOnPreviousLine() {
        val result = nineSliceLineInsets(listOf("乙", "丙"), arrayOf(bubble, bubble, bubble), 1, geometry = ::geometry)
        assertArrayEquals(floatArrayOf(30f, 0f), result.before, 0f)
        assertEquals(31f, result.after, 0f)
    }

    @Test
    fun adjacentDifferentCutsCloseAndReopenTheFrame() {
        val other = bubble.copy(npLeft = 0.1f)
        val result = nineSliceLineInsets(listOf("甲", "乙"), arrayOf(bubble, other), 0, geometry = ::geometry)
        assertArrayEquals(floatArrayOf(30f, 31f + 102f), result.before, 0f)
        assertEquals(31f, result.after, 0f)
    }

    @Test
    fun supplementaryCharacterDoesNotShiftFollowingStyleOffsets() {
        val result = nineSliceLineInsets(listOf("😀", "甲", "乙"), arrayOf(null, null, bubble, null), 0, geometry = ::geometry)
        assertArrayEquals(floatArrayOf(0f, 30f, 31f), result.before, 0f)
        assertEquals(0f, result.after, 0f)
    }

    @Test
    fun widthBudgetIncludesContinuationFramesAndTextPadding() {
        val budget = ReadNineSliceWidthBudget(arrayOf(bubble, bubble, bubble, bubble), ::geometry, 6f)
        assertEquals(73f, budget.width(0, 2), 0f)
        assertEquals(73f, budget.width(2, 4), 0f)
        val lines = nineSliceLineStarts(listOf("甲", "乙", "丙", "丁"),
            listOf(20f, 20f, 20f, 20f), 120f, budget::width) { true }
        org.junit.Assert.assertArrayEquals(intArrayOf(0, 2, 4), lines)
    }

    @Test
    fun mixedRulesBudgetAgreesWithPositioningInsets() {
        val styles = arrayOf(null, bubble, bubble.copy(npLeft = 0.1f), null)
        val budget = ReadNineSliceWidthBudget(styles, ::geometry, 6f)
        val insets = nineSliceLineInsets(listOf("甲", "乙", "丙", "丁"), styles, 0, 6f, ::geometry)
        assertEquals(insets.before.sum() + insets.after, budget.width(0, 4), 0f)
        assertEquals(0f, budget.width(0, 1), 0f)
    }

    @Test
    fun longWordAndSupplementaryClustersAlwaysProgressWithoutSplittingClusters() {
        val lines = nineSliceLineStarts(listOf("😀", "甲", "乙", "丙"),
            listOf(20f, 20f, 20f, 20f), 45f, { _, _ -> 0f }) { false }
        org.junit.Assert.assertArrayEquals(intArrayOf(0, 3, 5), lines)
    }
}
