package io.legado.app.ui.book.read

import io.legado.app.data.entities.CatalogOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogVolumeRowsTest {
    private val outline = listOf(
        row(0), row(2, true), row(3), row(6), row(7, true), row(8, true), row(10), row(11),
    )

    @Test
    fun defaultExpandedKeepsPrefaceAndEmptyVolumes() {
        assertEquals(outline, visibleCatalogRows(outline, emptySet(), false))
    }

    @Test
    fun collapseStopsAtNextVolumeAndLastVolumeIncludesRemainingChapters() {
        assertEquals(listOf(0, 2, 7, 8, 10, 11), indices(setOf("2")))
        assertEquals(listOf(0, 2, 7, 8), indices(setOf("2", "8")))
        assertEquals(outline, visibleCatalogRows(outline, setOf("7", "missing"), false))
    }

    @Test
    fun descendingUsesSameMembershipAndDoesNotRenumberChapters() {
        assertEquals(listOf(11, 10, 8, 7, 2, 0),
            visibleCatalogRows(outline, setOf("2"), true).map { it.index })
    }

    @Test
    fun sparseChapterIndicesStayCorrectAcrossPageBoundaries() {
        val visible = visibleCatalogRows(outline, setOf("2"), false)
        assertEquals(listOf(7, 8), catalogPageIndices(visible, 2, 2))
        assertEquals(listOf(11), catalogPageIndices(visible, 5, 2))
        assertEquals(emptyList<Int>(), catalogPageIndices(visible, 8, 2))
    }

    @Test
    fun hiddenCurrentChapterAnchorsToVolumeInEitherSortDirection() {
        val visible = visibleCatalogRows(outline, setOf("2"), false)
        assertEquals(1, catalogVisiblePosition(visible, 6))
        assertEquals(4, catalogVisiblePosition(visible.asReversed(), 6))
        assertEquals(0, catalogVisiblePosition(emptyList(), 6))
    }

    @Test
    fun persistedReversalKeepsVolumeMembershipAndLocatesTheCorrectParent() {
        val reversed = outline.asReversed().mapIndexed { index, entry -> entry.copy(index = index) }
        val visible = visibleCatalogRows(reversed, setOf("2"), descending = false, reversed = true)
        assertEquals(listOf("11", "10", "8", "7", "2", "0"), visible.map { it.url })
        val currentChapter = reversed.first { it.url == "6" }
        assertEquals("2", catalogVolumeForChapter(reversed, currentChapter.index, reversed = true))
        assertEquals(4, catalogVisiblePosition(visible, currentChapter.index, reversed = true))
        assertEquals(visible.asReversed(), visibleCatalogRows(reversed, setOf("2"), descending = true, reversed = true))
    }

    private fun indices(collapsed: Set<String>) =
        visibleCatalogRows(outline, collapsed, false).map { it.index }

    private fun row(index: Int, volume: Boolean = false) =
        CatalogOutlineEntry(index.toString(), index, volume)
}
