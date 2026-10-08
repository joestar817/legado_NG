package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NativeHighlightPositionMapTest {
    @Test
    fun `changing indentation preserves the selected canonical text`() {
        val canonical = "章节\n甲乙丙\n丁戊己\n"
        for (indent in listOf("", "　", "　　", ">> ")) {
            val map = NativeHighlightPositionMap.Builder(canonical)
                .append("章节\n", 0, 3)
                .append(indent, 3, 3)
                .append("甲乙丙\n", 3, 7)
                .append(indent, 7, 7)
                .append("丁戊己\n", 7, 11)
                .snapshot()
            val firstStart = 3 + indent.length
            val secondStart = 7 + indent.length * 2
            assertEquals(range(firstStart, firstStart + 3), map.toDisplayRange(3, 6))
            assertEquals(range(secondStart, secondStart + 3), map.toDisplayRange(7, 10))
            assertEquals(3, map.toCanonical(firstStart))
            assertEquals(6, map.toCanonical(firstStart + 3, end = true))
            assertEquals(7, map.toCanonical(secondStart))
            assertEquals(10, map.toCanonical(secondStart + 3, end = true))
        }
    }

    @Test
    fun `hidden title does not shift canonical body positions`() {
        val map = NativeHighlightPositionMap.Builder("第一章 标题\n正文\n")
            .append("　　", 7, 7)
            .append("正文\n", 7, 10)
            .snapshot()
        assertNull(map.toDisplayRange(0, 7))
        assertEquals(range(2, 4), map.toDisplayRange(7, 9))
        assertEquals(range(2, 5), map.toDisplayRange(0, Int.MAX_VALUE))
        assertEquals(7, map.toCanonical(2))
    }

    @Test
    fun `segmented title uses generated breaks without changing source offsets`() {
        val map = NativeHighlightPositionMap.Builder("第十章 标题\n正文\n")
            .append("第十章", 0, 3)
            .append("\n", 3, 3)
            .append("标题", 4, 6)
            .append("\n", 6, 7)
            .append("正文\n", 7, 10)
            .snapshot()
        assertEquals("第十章\n标题\n正文\n", map.displayText)
        assertNull(map.toDisplayRange(3, 4))
        assertEquals(range(4, 6), map.toDisplayRange(4, 6))
        assertEquals(range(0, 6), map.toDisplayRange(0, 6))
        assertEquals(4, map.toCanonical(4))
        assertEquals(3, map.toCanonical(4, end = true))
    }

    @Test
    fun `snapshot only exposes published pages and stays immutable while appending`() {
        val builder = NativeHighlightPositionMap.Builder("甲乙丙丁戊己")
        val empty = builder.snapshot()
        assertNull(empty.toCanonical(0))
        assertNull(empty.toCanonical(-1))
        assertNull(empty.toDisplayRange(0, 6))
        builder.append("甲乙", 0, 2)
        val firstPage = builder.snapshot()
        assertSame(firstPage, builder.snapshot())
        assertNull(firstPage.toDisplayRange(2, 6))
        assertEquals(range(1, 2), firstPage.toDisplayRange(1, 6))
        builder.append("丙丁", 2, 4)
        builder.append("戊己", 4, 6)
        val allPages = builder.snapshot()
        assertNotSame(firstPage, allPages)
        assertEquals("", empty.displayText)
        assertEquals("甲乙", firstPage.displayText)
        assertEquals("甲乙丙丁戊己", allPages.displayText)
        assertEquals(range(1, 5), allPages.toDisplayRange(1, 5))
        assertEquals(2, firstPage.toCanonical(2))
        assertNull(firstPage.toCanonical(3))
    }

    @Test
    fun `UTF16 supplementary characters keep two code units`() {
        val map = NativeHighlightPositionMap.Builder("甲😀乙\n")
            .append("　", 0, 0)
            .append("甲😀乙\n", 0, 5)
            .snapshot()
        assertEquals(range(2, 4), map.toDisplayRange(1, 3))
        assertEquals(1, map.toCanonical(2))
        assertEquals(2, map.toCanonical(3))
        assertEquals(3, map.toCanonical(4, end = true))
    }

    @Test
    fun `repeated sentences map by the supplied positions`() {
        val map = NativeHighlightPositionMap.Builder("相同。\n相同。\n")
            .append("相同。\n", 0, 4)
            .append("　", 4, 4)
            .append("相同。\n", 4, 8)
            .snapshot()
        assertEquals(range(5, 8), map.toDisplayRange(4, 7))
        assertEquals(range(0, 3), map.toDisplayRange(0, 3))
        assertEquals(4, map.toCanonical(5))
    }

    @Test
    fun `canonical position lookup ignores generated runs and uses endpoint affinity`() {
        val map = NativeHighlightPositionMap.Builder("甲乙")
            .append("　", 0, 0)
            .append("甲", 0, 1)
            .append("\n　", 1, 1)
            .append("乙", 1, 2)
            .append("\n", 2, 2)
            .snapshot()
        assertEquals(1, map.toDisplayPosition(0))
        assertEquals(1, map.toDisplayPosition(0, end = true))
        assertEquals(4, map.toDisplayPosition(1))
        assertEquals(2, map.toDisplayPosition(1, end = true))
        assertEquals(5, map.toDisplayPosition(2))
        assertEquals(5, map.toDisplayPosition(2, end = true))
    }

    @Test
    fun `canonical position lookup rejects hidden or unpublished content`() {
        val map = NativeHighlightPositionMap.Builder("隐藏甲乙后文")
            .append("　", 2, 2)
            .append("甲乙", 2, 4)
            .snapshot()
        assertNull(map.toDisplayPosition(-1))
        assertNull(map.toDisplayPosition(0))
        assertNull(map.toDisplayPosition(1, end = true))
        assertEquals(1, map.toDisplayPosition(2))
        assertEquals(2, map.toDisplayPosition(3))
        assertEquals(3, map.toDisplayPosition(4, end = true))
        assertNull(map.toDisplayPosition(4))
        assertNull(map.toDisplayPosition(2, end = true))
        assertNull(map.toDisplayPosition(5))
        assertNull(map.toDisplayPosition(7))
        assertNull(NativeHighlightPositionMap.Builder("甲").snapshot().toDisplayPosition(0))
    }

    @Test
    fun `full image before retained indentation and failed image keep body offsets`() {
        val canonical = "\uFFFC\uFFFC正文\n"
        val loaded = NativeHighlightPositionMap.Builder(canonical)
            .append(" ", 0, 1)
            .append("　　", 0, 0)
            .append("꧁", 1, 2)
            .append("正文\n", 2, 5)
            .snapshot()
        assertEquals(range(0, 1), loaded.toDisplayRange(0, 1))
        assertEquals(range(3, 4), loaded.toDisplayRange(1, 2))
        assertEquals(range(4, 6), loaded.toDisplayRange(2, 4))
        assertEquals(0, loaded.toCanonical(1))
        assertEquals(1, loaded.toCanonical(1, end = true))
        val failed = NativeHighlightPositionMap.Builder(canonical)
            .append("　　", 0, 0)
            .append("꧁", 1, 2)
            .append("正文\n", 2, 5)
            .snapshot()
        assertNull(failed.toDisplayRange(0, 1))
        assertEquals(range(2, 3), failed.toDisplayRange(1, 2))
        assertEquals(range(3, 5), failed.toDisplayRange(2, 4))
    }

    @Test
    fun `failed standalone image paragraph does not move the next published page`() {
        val builder = NativeHighlightPositionMap.Builder("甲\n\uFFFC\n乙\n")
            .append("甲\n", 0, 2)
        val firstPage = builder.snapshot()
        // The native paginator starts the next page after the last actual line. A failed
        // image paragraph has canonical content but must not add a speculative display newline.
        builder.append("　", 4, 4).append("乙\n", 4, 6)
        val map = builder.snapshot()
        assertEquals("甲\n", firstPage.displayText)
        assertEquals("甲\n　乙\n", map.displayText)
        assertNull(map.toDisplayRange(2, 4))
        assertEquals(range(3, 4), map.toDisplayRange(4, 5))
        assertEquals(4, map.toCanonical(3))
    }

    @Test
    fun `adjacent runs use the requested endpoint affinity across hidden text`() {
        val map = NativeHighlightPositionMap.Builder("甲隐藏乙")
            .append("甲", 0, 1)
            .append("乙", 3, 4)
            .snapshot()
        assertEquals(3, map.toCanonical(1))
        assertEquals(1, map.toCanonical(1, end = true))
        assertEquals(0, map.toCanonical(0, end = true))
        assertEquals(4, map.toCanonical(2))
        assertNull(map.toDisplayRange(1, 3))
        assertEquals(range(1, 2), map.toDisplayRange(1, 4))
        assertEquals(range(0, 1), map.toDisplayRange(0, 3))
    }

    @Test
    fun `generated outer whitespace never expands display range`() {
        val map = NativeHighlightPositionMap.Builder("正文")
            .append("　　", 0, 0)
            .append("正文", 0, 2)
            .append("\n", 2, 2)
            .snapshot()
        assertEquals(range(2, 4), map.toDisplayRange(0, 2))
        assertEquals(0, map.toCanonical(1))
        assertEquals(0, map.toCanonical(1, end = true))
        assertEquals(2, map.toCanonical(5, end = true))
    }

    @Test
    fun `nonlinear replacement uses owning endpoints without proportional guesses`() {
        val map = NativeHighlightPositionMap.Builder("甲乙丙丁戊")
            .append("天地人", 0, 2)
            .append("物", 2, 5)
            .snapshot()
        assertEquals(0, map.toCanonical(1))
        assertEquals(2, map.toCanonical(1, end = true))
        assertEquals(2, map.toCanonical(3))
        assertEquals(5, map.toCanonical(4, end = true))
        assertEquals(range(0, 3), map.toDisplayRange(1, 2))
        assertEquals(range(3, 4), map.toDisplayRange(3, 4))
    }

    @Test
    fun `nonmonotonic positive runs contribute the outermost visible interval`() {
        val map = NativeHighlightPositionMap.Builder("甲乙丙丁")
            .append("丙丁", 2, 4)
            .append("　", 0, 0)
            .append("甲乙", 0, 2)
            .snapshot()
        assertEquals(range(0, 5), map.toDisplayRange(1, 3))
        assertEquals(range(3, 5), map.toDisplayRange(0, 2))
        assertEquals(range(0, 2), map.toDisplayRange(2, 4))
    }

    @Test
    fun `lookups reject invalid positions and allow an open ended canonical range`() {
        val builder = NativeHighlightPositionMap.Builder("正文").append("正文", 0, 2)
        val map = builder.snapshot()
        builder.append("", 0, 2)
        assertSame(map, builder.snapshot())
        assertEquals(range(0, 2), map.toDisplayRange(0, Int.MAX_VALUE))
        assertNull(map.toDisplayRange(-1, 2))
        assertNull(map.toDisplayRange(2, 1))
        assertNull(map.toDisplayRange(1, 1))
        assertNull(map.toDisplayRange(2, Int.MAX_VALUE))
        assertNull(map.toDisplayRange(3, Int.MAX_VALUE))
        assertNull(map.toCanonical(-1))
        assertNull(map.toCanonical(3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects negative canonical start`() {
        NativeHighlightPositionMap.Builder("正文").append("", -1, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects a reversed canonical range`() {
        NativeHighlightPositionMap.Builder("正文").append("正文", 2, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `builder rejects canonical end beyond the text`() {
        NativeHighlightPositionMap.Builder("正文").append("正文", 0, 3)
    }

    private fun range(start: Int, end: Int) = NativeHighlightPositionMap.Range(start, end)
}
