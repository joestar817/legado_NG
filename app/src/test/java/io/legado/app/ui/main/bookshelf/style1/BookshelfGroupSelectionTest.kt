package io.legado.app.ui.main.bookshelf.style1

import io.legado.app.data.entities.BookGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfGroupSelectionTest {
    private fun group(id: Long, name: String = "group-$id") = BookGroup(groupId = id, groupName = name)

    @Test
    fun removingAGroupBeforeTheSelectionKeepsItsIdentity() {
        val afterRefresh = listOf(group(-1), group(-7), group(1), group(2))
        assertEquals(2, resolveUpdatedBookshelfGroupIndex(afterRefresh, selectedGroupId = 1, savedPosition = 3))
    }

    @Test
    fun insertingOrReorderingGroupsKeepsTheSelectedIdentity() {
        val afterInsert = listOf(group(-1), group(-7), group(-3), group(1), group(2))
        assertEquals(3, resolveUpdatedBookshelfGroupIndex(afterInsert, selectedGroupId = 1, savedPosition = 2))
        val reordered = listOf(group(2), group(-1), group(1, "renamed"))
        assertEquals(2, resolveUpdatedBookshelfGroupIndex(reordered, selectedGroupId = 1, savedPosition = 0))
    }

    @Test
    fun initialLoadAndRemovedSelectionKeepExistingIndexFallback() {
        val groups = listOf(group(-1), group(1), group(2))
        assertEquals(2, resolveUpdatedBookshelfGroupIndex(groups, selectedGroupId = null, savedPosition = 2))
        assertEquals(1, resolveUpdatedBookshelfGroupIndex(groups, selectedGroupId = 99, savedPosition = 1))
        assertEquals(2, resolveUpdatedBookshelfGroupIndex(groups, selectedGroupId = 99, savedPosition = 8))
        assertEquals(0, resolveUpdatedBookshelfGroupIndex(groups, selectedGroupId = null, savedPosition = -1))
        assertEquals(0, resolveUpdatedBookshelfGroupIndex(emptyList(), selectedGroupId = 1, savedPosition = 3))
    }

    @Test
    fun zeroAndLowestLongRemainValidGroupIdentities() {
        val groups = listOf(group(-1), group(0), group(Long.MIN_VALUE), group(2))
        assertEquals(1, resolveUpdatedBookshelfGroupIndex(groups, selectedGroupId = 0, savedPosition = 0))
        assertEquals(2, resolveUpdatedBookshelfGroupIndex(groups, selectedGroupId = Long.MIN_VALUE, savedPosition = 0))
    }
}
