package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatinOpticalScaleTest {
    @Test
    fun nonCjkBooksStayAtOne() {
        listOf(null, "", "latin", "other").forEach { script ->
            assertEquals(1f, LatinOpticalScale.effective(script, 1.1f, "cjk.ttf", "latin.ttf") { _, _ -> 1.5f })
        }
    }

    @Test
    fun manualOverridesSameFaceAndClamps() {
        assertEquals(1.1f, LatinOpticalScale.effective("cjk", 1.1f, "same.ttf", "same.ttf") { _, _ -> 9f })
        assertEquals(LatinOpticalScale.MAX, LatinOpticalScale.effective("cjk", 2f, "a.ttf", "b.ttf") { _, _ -> 1f })
        assertEquals(LatinOpticalScale.MIN, LatinOpticalScale.effective("cjk", 0.2f, "a.ttf", "b.ttf") { _, _ -> 1f })
        assertEquals(1f, LatinOpticalScale.clamp(Float.NaN))
    }

    @Test
    fun autoUsesOneWhenFacesMatch() {
        var called = false
        val scale = LatinOpticalScale.effective("cjk", null, "same.ttf", "same.ttf") { _, _ ->
            called = true
            1.2f
        }
        assertEquals(1f, scale)
        assertEquals(false, called)
        assertEquals(1f, LatinOpticalScale.effective("cjk", null, null, "latin.ttf") { _, _ -> 1.2f })
        assertEquals(1f, LatinOpticalScale.effective("cjk", null, "", "") { _, _ -> 1.2f })
    }

    @Test
    fun autoEstimatesWhenFacesDiffer() {
        val scale = LatinOpticalScale.effective("cjk", null, "cjk.ttf", "latin.ttf") { cjk, latin ->
            assertEquals("cjk.ttf", cjk)
            assertEquals("latin.ttf", latin)
            1.08f
        }
        assertEquals(1.08f, scale)
    }

    @Test
    fun estimateTargetsAProportionOfCjkInk() {
        // Typical em-relative ink: matching heights would clamp to 1.20 and lose the font pair.
        val typical = LatinOpticalScale.estimate(cjkBody = 0.86f, latinCap = 0.69f, latinX = 0.48f)
        assertEquals(0.97f, typical, 0.01f)
        assertTrue(typical < LatinOpticalScale.MAX)
        assertTrue(typical < 0.86f / (0.75f * 0.69f + 0.25f * 0.48f))

        // Raw ratio is k (0.72) when the two optical heights already match, then the floor raises it.
        val matchedOptical = LatinOpticalScale.estimate(cjkBody = 65f, latinCap = 70f, latinX = 50f)
        assertEquals(LatinOpticalScale.MIN, matchedOptical)

        val chased = LatinOpticalScale.estimate(cjkBody = 80f, latinCap = 72f, latinX = 45f)
        assertTrue(chased <= LatinOpticalScale.MAX)
        assertTrue(chased < 80f / 45f)
        assertEquals(LatinOpticalScale.MAX, LatinOpticalScale.estimate(200f, 70f, 40f))
        assertEquals(LatinOpticalScale.MIN, LatinOpticalScale.estimate(20f, 80f, 50f))
        assertEquals(1f, LatinOpticalScale.estimate(0f, 70f, 40f))
    }

    @Test
    fun epubSizeAdjustRequiresADistinctLatinFace() {
        assertNull(LatinOpticalScale.epubSizeAdjust("cjk", 1.1f, "same.ttf", "same.ttf", null) { _, _ -> 1f })
        assertNull(LatinOpticalScale.epubSizeAdjust("latin", 1.1f, "a.ttf", "b.ttf", "b.ttf") { _, _ -> 1f })
        assertEquals(
            1.1f,
            LatinOpticalScale.epubSizeAdjust("cjk", 1.1f, "a.ttf", "b.ttf", "b.ttf") { _, _ -> 1f },
        )
        assertNull(LatinOpticalScale.epubSizeAdjust("cjk", null, "same.ttf", "same.ttf", "ignored.ttf") { _, _ -> 1.2f })
    }
}
