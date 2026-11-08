package io.legado.app.ui.resource

import io.legado.app.help.resource.ResourceCategory
import io.legado.app.help.resource.ResourceEntry
import io.legado.app.help.resource.ResourceItem
import org.junit.Assert.*
import org.junit.Test

class ResourceDisplayRowTest {
    private fun entry(id: String, category: String) = ResourceEntry(
        category = ResourceCategory(id = category, name = category),
        item = ResourceItem(id = id),
    )

    @Test fun groupedListHasFullWidthHeadersAndKeepsResourceIdentity() {
        val entries = listOf(entry("a", "fonts"), entry("b", "fonts"), entry("c", "themes"))
        val rows = resourceDisplayRows(entries, true)
        assertEquals(listOf("group:fonts", "resource:a", "resource:b", "group:themes", "resource:c"), rows.map { it.key })
        assertEquals(listOf(2, 1), rows.filter { it.entry == null }.map { it.count })
        assertEquals(entries, rows.mapNotNull { it.entry })
    }

    @Test fun switchingPresentationLocatesTheSameResourceDespiteGroupHeaders() {
        val entries = listOf(entry("a", "fonts"), entry("b", "themes"))
        val listRows = resourceDisplayRows(entries, true)
        val gridRows = resourceDisplayRows(entries, false)
        assertEquals(3, resourceAnchorIndex(listRows, "resource:b"))
        assertEquals(1, resourceAnchorIndex(gridRows, "resource:b"))
        assertEquals(0, resourceAnchorIndex(gridRows, "removed"))
    }

    @Test fun gridAndSingleCategoryDoNotAddRepeatedHeadings() {
        val entries = listOf(entry("a", "fonts"), entry("b", "fonts"))
        assertEquals(listOf("resource:a", "resource:b"), resourceDisplayRows(entries, true).map { it.key })
        assertEquals(resourceDisplayRows(entries, false), resourceDisplayRows(entries, true))
        assertTrue(resourceDisplayRows(emptyList(), true).isEmpty())
    }
}

