package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetNamesTest {

    @Test
    fun blankNameIsRejected() {
        assertNull(PresetNames.allocate("   ", emptyList()))
        assertNull(PresetNames.allocate("", listOf("秋山书意")))
    }

    @Test
    fun uniqueNameKeepsTheTypedText() {
        assertEquals("秋山书意", PresetNames.allocate("  秋山书意  ", listOf("微信读书")))
        assertEquals(
            "一二三四五六七八九",
            PresetNames.allocate("一二三四五六七八九", listOf("微信读书")),
        )
    }

    @Test
    fun duplicateGetsTheNextSuffix() {
        assertEquals(
            "秋山书意-1",
            PresetNames.allocate("秋山书意", listOf("秋山书意")),
        )
        assertEquals(
            "秋山书意-2",
            PresetNames.allocate("秋山书意", listOf("秋山书意", "秋山书意-1")),
        )
    }

    @Test
    fun ownNameIsNotADuplicate() {
        assertEquals(
            "秋山书意",
            PresetNames.allocate("秋山书意", listOf("秋山书意", "微信读书"), current = "秋山书意"),
        )
    }

    @Test
    fun suffixStaysWithinTheNameField() {
        val base = "一二三四五六七八"
        val saved = PresetNames.allocate(base, listOf(base))
        assertEquals("一二三四五六-1", saved)
        assertEquals(PresetNames.MAX_CODE_POINTS, saved!!.codePointCount(0, saved.length))
    }

    @Test
    fun longerSuffixShortensTheStem() {
        val taken = mutableListOf("一二三四五六七八")
        taken += (1..9).map { "一二三四五六-$it" }
        val saved = PresetNames.allocate("一二三四五六七八", taken)
        assertEquals("一二三四五-10", saved)
        assertEquals(PresetNames.MAX_CODE_POINTS, saved!!.codePointCount(0, saved.length))
    }

    @Test
    fun truncatedCandidateSkipsAnExistingName() {
        val base = "一二三四五六七八"
        assertEquals(
            "一二三四五六-2",
            PresetNames.allocate(base, listOf(base, "一二三四五六-1")),
        )
    }

    @Test
    fun suffixDoesNotSplitACodePoint() {
        val base = "😀".repeat(8)
        val saved = PresetNames.allocate(base, listOf(base))
        assertEquals("😀".repeat(6) + "-1", saved)
        assertEquals(PresetNames.MAX_CODE_POINTS, saved!!.codePointCount(0, saved.length))
    }
}
