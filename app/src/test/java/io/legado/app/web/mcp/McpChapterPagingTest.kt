package io.legado.app.web.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpChapterPagingTest {

    private val bookIndexes = (0 until 1_200).toList()

    @Test
    fun omittedEndPagesThroughWholeBook() {
        val first = McpChapterPaging.page(bookIndexes, start = 0, end = null, limit = 500)
        assertEquals(500, first.indexes.size)
        assertTrue(first.hasMore)
        assertEquals(500, first.nextStart)
        assertEquals(700, first.remaining)
        assertEquals(1_200, first.rangeEnd)

        val second = McpChapterPaging.page(bookIndexes, start = first.nextStart!!, end = null, limit = 500)
        assertEquals(500, second.indexes.size)
        assertTrue(second.hasMore)
        assertEquals(1_000, second.nextStart)

        val third = McpChapterPaging.page(bookIndexes, start = second.nextStart!!, end = null, limit = 500)
        assertEquals(200, third.indexes.size)
        assertFalse(third.hasMore)
        assertNull(third.nextStart)
        assertEquals(0, third.remaining)
    }

    @Test
    fun explicitEndStopsAtRangeEnd() {
        val page = McpChapterPaging.page(bookIndexes, start = 0, end = 300, limit = 500)
        assertEquals(300, page.indexes.size)
        assertFalse(page.hasMore)
        assertNull(page.nextStart)
        assertEquals(300, page.rangeEnd)
        assertEquals(0, page.remaining)
    }

    @Test
    fun explicitRangeLargerThanPageReturnsCursor() {
        val page = McpChapterPaging.page(bookIndexes, start = 100, end = 900, limit = 500)
        assertEquals(500, page.indexes.size)
        assertEquals(100, page.indexes.first())
        assertEquals(599, page.indexes.last())
        assertTrue(page.hasMore)
        assertEquals(600, page.nextStart)
        assertEquals(300, page.remaining)
    }

    @Test
    fun endBeyondLastChapterIsClamped() {
        val page = McpChapterPaging.page(bookIndexes, start = 1_100, end = 99_999, limit = 500)
        assertEquals(100, page.indexes.size)
        assertEquals(1_200, page.rangeEnd)
        assertFalse(page.hasMore)
    }

    @Test
    fun sparseIndexesUseCollectionOrderForContinuation() {
        val sparse = listOf(0, 2, 5, 9)
        val page = McpChapterPaging.page(sparse, start = 1, end = null, limit = 2)
        assertEquals(listOf(2, 5), page.indexes)
        assertEquals(6, page.nextStart)
        assertTrue(page.hasMore)
        assertEquals(1, page.remaining)
    }

    @Test
    fun emptyBookReturnsEmptyPage() {
        val page = McpChapterPaging.page(emptyList(), start = 0, end = null, limit = 500)
        assertTrue(page.indexes.isEmpty())
        assertFalse(page.hasMore)
        assertNull(page.nextStart)
        assertEquals(0, page.remaining)
    }
}
