package io.legado.app.ui.book.toc

import io.legado.app.data.entities.CatalogOutlineEntry
import io.legado.app.ui.book.read.visibleCatalogRows

internal data class TocCatalogProjection(
    val visibleRows: List<CatalogOutlineEntry>,
    val parentVolumeByKey: Map<String, String>,
)

internal fun projectTocCatalogRows(
    rows: List<CatalogOutlineEntry>,
    collapsedVolumes: Set<String>,
    reversed: Boolean,
    searching: Boolean,
): TocCatalogProjection {
    // Search results are sparse: a preceding volume may not be among the matches.
    if (searching) return TocCatalogProjection(rows, emptyMap())

    // ReverseToc changes the stored order and indices. Membership follows source order,
    // while row identity and indices must remain those supplied by the caller.
    val sourceOrder = if (reversed) rows.asReversed() else rows
    val parents = mutableMapOf<String, String>()
    var volumeKey: String? = null
    sourceOrder.forEach { row ->
        if (row.isVolume) {
            volumeKey = row.url
        } else {
            volumeKey?.let { parents[row.url] = it }
        }
    }
    return TocCatalogProjection(
        visibleRows = visibleCatalogRows(sourceOrder, collapsedVolumes, reversed),
        parentVolumeByKey = parents,
    )
}

internal fun tocCatalogAnchorPosition(
    projection: TocCatalogProjection,
    anchorKey: String?,
): Int {
    if (anchorKey == null) return 0
    val directPosition = projection.visibleRows.indexOfFirst { it.url == anchorKey }
    if (directPosition >= 0) return directPosition
    val parentKey = projection.parentVolumeByKey[anchorKey] ?: return 0
    return projection.visibleRows.indexOfFirst { it.url == parentKey }.coerceAtLeast(0)
}
