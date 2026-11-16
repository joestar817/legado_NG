package io.legado.app.ui.book.read

import io.legado.app.data.entities.CatalogOutlineEntry

internal fun catalogSourceOrder(outline: List<CatalogOutlineEntry>, reversed: Boolean): List<CatalogOutlineEntry> =
    if (reversed) outline.asReversed() else outline

internal fun visibleCatalogRows(
    outline: List<CatalogOutlineEntry>,
    collapsedVolumes: Set<String>,
    descending: Boolean,
    reversed: Boolean = false,
): List<CatalogOutlineEntry> {
    var hidden = false
    val visible = catalogSourceOrder(outline, reversed).filter { entry ->
        if (entry.isVolume) {
            hidden = entry.url in collapsedVolumes
            true
        } else {
            !hidden
        }
    }
    return if (descending != reversed) visible.asReversed() else visible
}

internal fun catalogVolumeForChapter(
    outline: List<CatalogOutlineEntry>,
    chapterIndex: Int,
    reversed: Boolean,
): String? {
    var volumeUrl: String? = null
    for (entry in catalogSourceOrder(outline, reversed)) {
        if (entry.index == chapterIndex) return if (entry.isVolume) null else volumeUrl
        if (entry.isVolume) volumeUrl = entry.url
    }
    return null
}

internal fun catalogVisiblePosition(rows: List<CatalogOutlineEntry>, chapterIndex: Int, reversed: Boolean = false): Int {
    val target = rows.firstOrNull { it.index == chapterIndex }
        ?: if (reversed) rows.filter { it.index > chapterIndex }.minByOrNull { it.index }
        else rows.filter { it.index < chapterIndex }.maxByOrNull { it.index }
    return rows.indexOf(target).coerceAtLeast(0)
}

internal fun catalogPageIndices(rows: List<CatalogOutlineEntry>, offset: Int, limit: Int): List<Int> {
    val start = offset.coerceIn(0, rows.size)
    val end = (start + limit.coerceAtLeast(0)).coerceAtMost(rows.size)
    return rows.subList(start, end).map { it.index }
}
