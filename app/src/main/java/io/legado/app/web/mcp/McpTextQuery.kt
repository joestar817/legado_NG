package io.legado.app.web.mcp

/**
 * Pure keyword/regex matcher used by the MCP chapter-text search tool.
 *
 * Kept free of Android APIs so the matching and snippet behaviour can be covered
 * by plain JVM tests. Offsets are UTF-16 indices into the supplied text.
 */
internal class McpTextQuery private constructor(
    val text: String,
    private val regex: Regex?,
    private val ignoreCase: Boolean
) {

    companion object {
        const val MAX_QUERY_LENGTH = 200
        private const val MAX_COUNT_PER_CHAPTER = 100_000
        private const val MAX_EMPTY_REGEX_MATCHES = 1_000

        fun compile(text: String, regex: Boolean, ignoreCase: Boolean): McpTextQuery {
            val normalized = text.trim()
            require(normalized.isNotEmpty()) { "query must not be blank" }
            require(normalized.length <= MAX_QUERY_LENGTH) {
                "query is too long, max $MAX_QUERY_LENGTH chars"
            }
            val compiled = if (regex) {
                runCatching {
                    Regex(
                        normalized,
                        if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    )
                }.getOrElse {
                    throw IllegalArgumentException("Invalid regex: ${it.localizedMessage}")
                }
            } else {
                null
            }
            return McpTextQuery(normalized, compiled, ignoreCase)
        }
    }

    fun count(content: String): Int {
        if (content.isEmpty()) return 0
        if (regex != null) {
            var count = 0
            for (match in regex.findAll(content)) {
                count++
                if (match.value.isEmpty() && count >= MAX_EMPTY_REGEX_MATCHES) break
                if (count >= MAX_COUNT_PER_CHAPTER) break
            }
            return count
        }
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
        if (regex != null) {
            for (match in regex.findAll(content)) {
                result.add(buildSnippet(content, match.range.first, match.value.length, context))
                if (result.size >= limit) break
            }
        } else {
            var index = content.indexOf(text, 0, ignoreCase)
            while (index >= 0 && result.size < limit) {
                result.add(buildSnippet(content, index, text.length, context))
                index = content.indexOf(text, index + text.length, ignoreCase)
            }
        }
        return result
    }

    private fun buildSnippet(
        content: String,
        matchStart: Int,
        matchLength: Int,
        contextChars: Int
    ): String {
        val start = (matchStart - contextChars).coerceAtLeast(0)
        val end = (matchStart + matchLength + contextChars).coerceAtMost(content.length)
        if (start >= end) return ""
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < content.length) "…" else ""
        return prefix + content.substring(start, end) + suffix
    }
}
