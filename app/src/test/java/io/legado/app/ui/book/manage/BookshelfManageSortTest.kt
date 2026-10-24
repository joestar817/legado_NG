package io.legado.app.ui.book.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfManageSortTest {
    private data class Item(val name: String, val read: Long, val update: Long, val order: Int, val bytes: Long?)

    private val books = listOf(
        Item("Beta", 10, 30, 3, null),
        Item("Alpha", 20, 20, 2, 0),
        Item("Gamma", 20, 10, 1, 90),
        Item("Delta", 0, 40, 4, 90),
    )

    private fun List<Item>.sort(sort: BookshelfManageSort, reverseNames: Boolean = false) = sortedForBookshelfManage(
        sort = sort,
        readTime = Item::read,
        updateTime = Item::update,
        compareNames = { first, second -> if (reverseNames) second.name.compareTo(first.name) else first.name.compareTo(second.name) },
        manualOrder = Item::order,
        cacheBytes = Item::bytes,
    )

    private fun names(sort: BookshelfManageSort) = books.sort(sort).map(Item::name)

    @Test
    fun followingShelfKeepsItsOrderAndAllModesLeaveTheInputUntouched() {
        val original = books.toList()
        assertSame(books, books.sort(BookshelfManageSort.FOLLOW_BOOKSHELF))
        BookshelfManageSort.entries.forEach { books.sort(it) }
        assertEquals(original, books)
    }

    @Test
    fun recentReadingIsDescendingAndTiesKeepTheirShelfOrder() {
        assertEquals(listOf("Alpha", "Gamma", "Beta", "Delta"), names(BookshelfManageSort.RECENT_READ))
    }

    @Test
    fun latestUpdateIsIndependentFromReadingTime() {
        assertEquals(listOf("Delta", "Beta", "Alpha", "Gamma"), names(BookshelfManageSort.RECENT_UPDATE))
    }

    @Test
    fun titleOrderUsesTheCallersCollation() {
        assertEquals(listOf("Alpha", "Beta", "Delta", "Gamma"), names(BookshelfManageSort.NAME))
        assertEquals(listOf("Gamma", "Delta", "Beta", "Alpha"), books.sort(BookshelfManageSort.NAME, true).map(Item::name))
    }

    @Test
    fun cacheOrderRetainsZeroAndUnknownEntriesWithStableTies() {
        assertEquals(listOf("Gamma", "Delta", "Alpha", "Beta"), names(BookshelfManageSort.CACHE_SIZE))
    }

    @Test
    fun manualOrderUsesSavedOrderAndOnlyManualOrFollowModesAllowDragging() {
        assertEquals(listOf("Gamma", "Alpha", "Beta", "Delta"), names(BookshelfManageSort.MANUAL))
        assertTrue(BookshelfManageSort.MANUAL.allowsDragging)
        assertTrue(BookshelfManageSort.FOLLOW_BOOKSHELF.allowsDragging)
        BookshelfManageSort.entries.filterNot { it == BookshelfManageSort.MANUAL || it == BookshelfManageSort.FOLLOW_BOOKSHELF }
            .forEach { assertFalse(it.allowsDragging) }
    }

    @Test
    fun sortingSearchResultsDoesNotBringHiddenBooksBack() {
        val matching = books.filter { it.name == "Alpha" || it.name == "Beta" }
        assertEquals(listOf("Alpha", "Beta"), matching.sort(BookshelfManageSort.CACHE_SIZE).map(Item::name))
    }
}
