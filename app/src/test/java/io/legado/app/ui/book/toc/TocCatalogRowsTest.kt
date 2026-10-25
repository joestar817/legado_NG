package io.legado.app.ui.book.toc

import io.legado.app.data.entities.CatalogOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TocCatalogRowsTest {
    private val rows = listOf(
        row("cover", 0), row("intro", 1), row("v1", 2, true),
        row("c1", 3), row("c2", 4), row("empty", 5, true),
        row("last", 6, true), row("c3", 7), row("c4", 8),
    )

    @Test
    fun ordinaryPrefaceRowsStayVisibleAndHaveNoParent() {
        val projection = project(rows, setOf("v1", "last"))
        assertEquals(listOf("cover", "intro", "v1", "empty", "last"), keys(projection))
        assertFalse(projection.parentVolumeByKey.containsKey("cover"))
        assertFalse(projection.parentVolumeByKey.containsKey("intro"))
        assertFalse(projection.parentVolumeByKey.containsKey("v1"))
        assertEquals(mapOf("c1" to "v1", "c2" to "v1", "c3" to "last", "c4" to "last"),
            projection.parentVolumeByKey)
    }

    @Test
    fun emptyVolumeAndUnknownCollapseKeysDoNotHideOtherVolumes() {
        assertEquals(rows, project(rows, setOf("empty", "missing")).visibleRows)
        assertEquals(listOf("cover", "intro", "v1", "c1", "c2", "empty", "last"),
            keys(project(rows, setOf("last"))))
    }

    @Test
    fun realReversedAndRenumberedRowsUseSourceMembershipWithoutChangingIndices() {
        val reversedRows = rows.asReversed().mapIndexed { index, row -> row.copy(index = index) }
        val projection = project(reversedRows, setOf("v1"), reversed = true)
        assertEquals(listOf("c4", "c3", "last", "empty", "v1", "intro", "cover"), keys(projection))
        assertEquals(listOf(0, 1, 2, 3, 6, 7, 8), projection.visibleRows.map { it.index })
        assertEquals("v1", projection.parentVolumeByKey["c1"])
        assertEquals("last", projection.parentVolumeByKey["c4"])
        assertEquals(reversedRows.filterNot { it.url == "c1" || it.url == "c2" },
            projection.visibleRows)
    }

    @Test
    fun sparseSearchMatchesBypassCollapseAndMembershipInBothDirections() {
        val matches = listOf(rows[0], rows[2], rows[4], rows[8])
        listOf(false, true).forEach { reversed ->
            val input = if (reversed) matches.asReversed() else matches
            val projection = projectTocCatalogRows(input, setOf("v1", "last"), reversed, true)
            assertEquals(input, projection.visibleRows)
            assertEquals(emptyMap<String, String>(), projection.parentVolumeByKey)
        }
    }

    @Test
    fun hiddenAnchorUsesItsOwnVolumeInEitherDirection() {
        val ascending = project(rows, setOf("v1", "last"))
        assertEquals(2, tocCatalogAnchorPosition(ascending, "c2"))
        assertEquals(4, tocCatalogAnchorPosition(ascending, "c4"))
        assertEquals(1, tocCatalogAnchorPosition(ascending, "intro"))
        val descending = project(rows.asReversed(), setOf("v1", "last"), reversed = true)
        assertEquals(2, tocCatalogAnchorPosition(descending, "c2"))
        assertEquals(0, tocCatalogAnchorPosition(descending, "c4"))
        assertEquals(3, tocCatalogAnchorPosition(descending, "intro"))
    }

    @Test
    fun unknownNullAndEmptyAnchorsStartAtZero() {
        val projection = project(rows)
        assertEquals(0, tocCatalogAnchorPosition(projection, "missing"))
        assertEquals(0, tocCatalogAnchorPosition(projection, ""))
        assertEquals(0, tocCatalogAnchorPosition(projection, null))
        assertEquals(0, tocCatalogAnchorPosition(project(emptyList()), "c1"))
        assertEquals(0, tocCatalogAnchorPosition(project(emptyList()), null))
    }

    private fun project(
        input: List<CatalogOutlineEntry>,
        collapsed: Set<String> = emptySet(),
        reversed: Boolean = false,
    ) = projectTocCatalogRows(input, collapsed, reversed, searching = false)

    private fun keys(projection: TocCatalogProjection) = projection.visibleRows.map { it.url }

    private fun row(key: String, index: Int, volume: Boolean = false) =
        CatalogOutlineEntry(key, index, volume)
}
