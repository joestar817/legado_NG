package io.legado.app.web.mcp

/**
 * Pure literal keyword matcher used by the MCP chapter-text search tool.
 *
 * Only literal, case-(in)sensitive matching is supported. Regular expressions are
 * deliberately excluded: an unconstrained regex can backtrack for an unbounded
 * amount of time inside a single chapter, where the tool's time budget cannot
 * interrupt it. Literal matching keeps every chapter scan bounded and cancellable
 * at chapter granularity.
 *
 * Kept free of Android APIs so the matching and snippet behaviour can be covered
 * by plain JVM tests. Offsets are UTF-16 indices into the supplied text.
 */
internal class McpTextQuery private constructor(
    val text: String,
    private val ignoreCase: Boolean
) {

    companion object {
        const val MAX_QUERY_LENGTH = 200

        /** Hard cap for one rendered snippet, including the ellipsis markers. */
        const val MAX_SNIPPET_CHARS = 600

        private const val MAX_COUNT_PER_CHAPTER = 100_000

        fun compile(text: String, ignoreCase: Boolean): McpTextQuery {
            val normalized = text.trim()
            require(normalized.isNotEmpty()) { "query must not be blank" }
            require(normalized.length <= MAX_QUERY_LENGTH) {
                "query is too long, max $MAX_QUERY_LENGTH chars"
            }
            return McpTextQuery(normalized, ignoreCase)
        }
    }

    fun count(content: String): Int {
        if (content.isEmpty()) return 0
        var count = 0
        var index = content.indexOf(text, 0, ignoreCase)
        while (index >= 0) {
            count++
            if (count >= MAX_COUNT_PER_CHAPTER) break
            index = content.indexOf(text, index + text.length, ignoreCase)
        }
        return count
    }

    fun snippets(content: String, limit: Int, contextChars: Int): List<String> {
        if (limit <= 0 || content.isEmpty()) return emptyList()
        val context = contextChars.coerceAtLeast(0)
        val result = ArrayList<String>(limit)
        var index = content.indexOf(text, 0, ignoreCase)
        while (index >= 0 && result.size < limit) {
            result.add(buildSnippet(content, index, text.length, context))
            index = content.indexOf(text, index + text.length, ignoreCase)
        }
        return result
    }

    private fun buildSnippet(
        content: String,
        matchStart: Int,
        matchLength: Int,
        contextChars: Int
    ): String {
        val matchEnd = matchStart + matchLength
        var start = (matchStart - contextChars).coerceAtLeast(0)
        var end = (matchEnd + contextChars).coerceAtMost(content.length)
        val contentBudget = MAX_SNIPPET_CHARS - 2 // reserve room for both ellipsis markers
        if (end - start > contentBudget) {
            // Keep the whole hit when it fits, then spend the remaining budget on
            // symmetric context. This prevents a single snippet from growing with
            // the match length or with a caller-supplied context size.
            val hitBudget = minOf(matchLength, contentBudget)
            val contextBudget = (contentBudget - hitBudget) / 2
            start = (matchStart - contextBudget).coerceAtLeast(0)
            end = (matchEnd + contextBudget).coerceAtMost(content.length)
            if (end - start > contentBudget) {
                end = (start + contentBudget).coerceAtMost(content.length)
            }
        }
        if (start >= end) return ""
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < content.length) "…" else ""
        return prefix + content.substring(start, end) + suffix
    }
}
