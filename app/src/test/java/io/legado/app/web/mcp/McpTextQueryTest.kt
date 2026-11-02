package io.legado.app.web.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTextQueryTest {

    @Test
    fun literalCountIsCaseInsensitiveByDefault() {
        val query = McpTextQuery.compile("lin", ignoreCase = true)
        assertEquals(2, query.count("Lin and LIN"))
    }

    @Test
    fun literalCountSupportsChineseAliases() {
        val query = McpTextQuery.compile("林动", ignoreCase = true)
        assertEquals(3, query.count("林动抬头，林动笑了，小炎看着林动"))
    }

    @Test
    fun snippetsKeepContextAndAddEllipsis() {
        val query = McpTextQuery.compile("林动", ignoreCase = true)
        val content = "前文很多字".repeat(10) + "林动出现" + "后文很多字".repeat(10)
        val snippets = query.snippets(content, limit = 1, contextChars = 4)
        assertEquals(1, snippets.size)
        assertTrue(snippets[0].startsWith("…"))
        assertTrue(snippets[0].endsWith("…"))
        assertTrue(snippets[0].contains("林动"))
    }

    @Test
    fun snippetsRespectPerChapterLimit() {
        val query = McpTextQuery.compile("林动", ignoreCase = true)
        val snippets = query.snippets("林动林动林动", limit = 2, contextChars = 0)
        assertEquals(2, snippets.size)
        assertTrue(snippets[0].startsWith("林动"))
        assertTrue(snippets[1].contains("林动"))
    }

    @Test
    fun snippetLengthStaysWithinHardCapRegardlessOfContext() {
        val query = McpTextQuery.compile("林动", ignoreCase = true)
        val content = "甲".repeat(5_000) + "林动" + "乙".repeat(5_000)
        val snippet = query.snippets(content, limit = 1, contextChars = 4_000).single()
        assertTrue(
            "snippet length was ${snippet.length}",
            snippet.length <= McpTextQuery.MAX_SNIPPET_CHARS
        )
        assertTrue(snippet.contains("林动"))
    }

    @Test
    fun snippetLengthStaysWithinHardCapForLongQuery() {
        val keyword = "a".repeat(McpTextQuery.MAX_QUERY_LENGTH)
        val query = McpTextQuery.compile(keyword, ignoreCase = false)
        val content = "b".repeat(300) + keyword + "c".repeat(300)
        val snippet = query.snippets(content, limit = 1, contextChars = 200).single()
        assertTrue(
            "snippet length was ${snippet.length}",
            snippet.length <= McpTextQuery.MAX_SNIPPET_CHARS
        )
        assertTrue(snippet.contains(keyword))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankQueryIsRejected() {
        McpTextQuery.compile("   ", ignoreCase = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlongQueryIsRejected() {
        McpTextQuery.compile("a".repeat(McpTextQuery.MAX_QUERY_LENGTH + 1), ignoreCase = true)
    }
}
