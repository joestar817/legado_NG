package io.legado.app.ui.book.read.epub

internal data class EpubWarmTarget(val key: Int, val document: Int, val page: Int, val last: Boolean = false)

/** Negative keys reserve the adjacent document edges inside the same bounded frame window. */
internal fun epubWarmTargets(document: Int, center: Int, count: Int, documentCount: Int,
    direction: Int, capacity: Int, boundaryOnly: Boolean = false): List<EpubWarmTarget> {
    if (count <= 0 || document !in 0 until documentCount || capacity <= 0) return emptyList()
    // A zero-animation turn can move the live document directly. Freeze pixels only
    // near a real chapter boundary, where they bridge the replacement document.
    if (boundaryOnly && !(document > 0 && center <= 1 || document + 1 < documentCount && center >= count - 2))
        return emptyList()
    val step = if (direction < 0) -1 else 1
    val lookahead = when {
        // A direction change one page before the edge must not have to rebuild
        // the adjacent document while handling the next pair of taps.
        count > 2 && center == 1 && step > 0 && document > 0 -> -1
        count > 2 && center == count - 2 && step < 0 && document + 1 < documentCount -> count
        else -> center + 2 * step
    }
    val opposite = center - step
    // A cross-document lookbehind requires a full private reflow. Finish the current
    // direction's lookahead first; an already retained boundary edge needs no work.
    val pages = if (opposite !in 0 until count) listOf(center, center + step, lookahead, opposite)
        else listOf(center, center + step, opposite, lookahead)
    return pages.mapNotNull { page ->
        when {
            page in 0 until count -> EpubWarmTarget(page, document, page)
            page >= count && document + 1 < documentCount ->
                EpubWarmTarget(-1 - 2 * (page - count), document + 1, page - count)
            page < 0 && document > 0 -> EpubWarmTarget(2 * page, document - 1, page, last = true)
            else -> null
        }
    }.distinctBy { it.key }.take(capacity)
}
