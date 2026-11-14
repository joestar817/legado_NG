package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadTypographyMetricTest {
    @Test
    fun bodyIndentAndWeightRejectInvalidValuesAndRespectDiscreteSteps() {
        val indent = ReadTypographyMetric.PARAGRAPH_INDENT
        assertEquals(0, indent.parseTicks("0", clamp = false))
        assertEquals(4, indent.parseTicks("4", clamp = false))
        assertNull(indent.parseTicks("5", clamp = false))
        assertEquals(4, indent.parseTicks("5", clamp = true))
        assertFalse(indent.acceptsDraft("-1"))
        assertFalse(indent.acceptsDraft("1.5"))
        assertNull(indent.parseTicks("", clamp = true))

        val weight = ReadTypographyMetric.BODY_WEIGHT
        assertEquals(100, weight.parseTicks("100", clamp = false))
        assertEquals(500, weight.parseTicks("450", clamp = false))
        assertEquals(900, weight.parseTicks("9999999999", clamp = true))
        assertNull(weight.parseTicks("901", clamp = false))
        assertEquals(100, weight.parseTicks("0", clamp = true))
        assertFalse(weight.acceptsDraft("-100"))
        assertFalse(weight.acceptsDraft("400.5"))
    }

    @Test
    fun titleLineSpacingUsesTenthsAndTheOriginalSliderBounds() {
        val metric = ReadTypographyMetric.TITLE_LINE_SPACING
        assertEquals("0.0", metric.format(0))
        assertEquals("1.2", metric.format(12))
        assertEquals("10.0", metric.format(100))
        assertEquals(13, metric.parseTicks("1.25", clamp = false))
        assertEquals(100, metric.parseTicks("10.1", clamp = true))
        assertNull(metric.parseTicks("10.1", clamp = false))
        assertFalse(metric.acceptsDraft("-0.1"))
    }

    @Test
    fun titleMetricsKeepTheirRangesAndWeightSteps() {
        assertEquals("+2", ReadTypographyMetric.TITLE_SIZE.format(2))
        assertEquals(2, ReadTypographyMetric.TITLE_SIZE.parseTicks("+2", clamp = false))
        assertNull(ReadTypographyMetric.TITLE_SIZE.parseTicks("21", clamp = false))
        assertEquals(500, ReadTypographyMetric.TITLE_WEIGHT.parseTicks("460", clamp = false))
        assertEquals(900, ReadTypographyMetric.TITLE_WEIGHT.parseTicks("999", clamp = true))
        assertEquals(100, ReadTypographyMetric.TITLE_SPACING.parseTicks("101", clamp = true))
    }

    @Test
    fun valuesUseTheExistingRangesAndPrecision() {
        assertEquals(18, ReadTypographyMetric.SIZE.parseTicks("18", clamp = false))
        assertEquals(-5, ReadTypographyMetric.LETTER_SPACING.parseTicks("-0.05", clamp = false))
        assertEquals(25, ReadTypographyMetric.LETTER_SPACING.parseTicks("0,25", clamp = false))
        assertEquals(-6, ReadTypographyMetric.LINE_SPACING.parseTicks("-0.6", clamp = false))
        assertEquals(8, ReadTypographyMetric.PARAGRAPH_SPACING.parseTicks("0.8", clamp = false))
        assertEquals("0.05", ReadTypographyMetric.LETTER_SPACING.format(5))
        assertEquals("-0.6", ReadTypographyMetric.LINE_SPACING.format(-6))
        assertEquals(13, ReadTypographyMetric.LETTER_SPACING.parseTicks("0.125", clamp = false))
    }

    @Test
    fun incompleteAndOutOfRangeDraftsDoNotApplyUntilCommit() {
        val letter = ReadTypographyMetric.LETTER_SPACING
        assertTrue(letter.acceptsDraft("-"))
        assertTrue(letter.acceptsDraft("-."))
        assertNull(letter.parseTicks("-", clamp = false))
        assertNull(letter.parseTicks("-.", clamp = true))
        assertNull(letter.parseTicks("0.6", clamp = false))
        assertEquals(50, letter.parseTicks("0.6", clamp = true))
        assertEquals(-50, letter.parseTicks("-0.6", clamp = true))
        assertNull(ReadTypographyMetric.SIZE.parseTicks("4", clamp = false))
        assertEquals(5, ReadTypographyMetric.SIZE.parseTicks("4", clamp = true))
        assertEquals(50, ReadTypographyMetric.SIZE.parseTicks("500", clamp = true))
        assertFalse(ReadTypographyMetric.SIZE.acceptsDraft("1.5"))
        assertFalse(letter.acceptsDraft("1e2"))
        assertFalse(letter.acceptsDraft("12345678901"))
    }
}
