package io.legado.app.web.mcp

/**
 * Pure pagination helper shared by MCP chapter-range tools.
 *
 * A caller-supplied `end` describes the end of the logical query range, while
 * [limit] only caps one response page. Omitting `end` must therefore query the
 * whole book and still return a continuation cursor when the page is truncated.
 */
internal data class McpChapterPage(
    val indexes: List<Int>,
    /** Exclusive end of the logical query range, echoed back to the caller. */
    val rangeEnd: Int,
    val hasMore: Boolean,
    val nextStart: Int?,
    /** Number of chapters inside the query range that were not returned. */
    val remaining: Int
)

internal object McpChapterPaging {

    fun page(
        allIndexes: List<Int>,
        start: Int,
        end: Int?,
        limit: Int
    ): McpChapterPage {
        require(limit > 0) { "limit must be positive" }
        val rangeStart = start.coerceAtLeast(0)
        val bookEnd = allIndexes.lastOrNull()?.plus(1) ?: 0
        val rangeEnd = (end ?: bookEnd).coerceIn(rangeStart, maxOf(rangeStart, bookEnd))
        if (rangeEnd <= rangeStart) {
            return McpChapterPage(
                indexes = emptyList(),
                rangeEnd = rangeEnd,
                hasMore = false,
                nextStart = null,
                remaining = 0
            )
        }
        val candidates = allIndexes.filter { it >= rangeStart && it < rangeEnd }
        val returned = candidates.take(limit)
        val hasMore = candidates.size > returned.size
        return McpChapterPage(
            indexes = returned,
            rangeEnd = rangeEnd,
            hasMore = hasMore,
            nextStart = if (hasMore) returned.lastOrNull()?.plus(1) else null,
            remaining = candidates.size - returned.size
        )
    }
}
