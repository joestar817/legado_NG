package io.legado.app.web.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTextQueryTest {

    @Test
    fun literalCountIsCaseInsensitiveByDefault() {
        val query = McpTextQuery.compile("lin", regex = false, ignoreCase = true)
        assertEquals(2, query.count("Lin and LIN"))
    }

    @Test
    fun literalCountSupportsChineseAliases() {
        val query = McpTextQuery.compile("林动", regex = false, ignoreCase = true)
        assertEquals(3, query.count("林动抬头，林动笑了，小炎看着林动"))
    }

    @Test
    fun regexCountMatchesAllOccurrences() {
        val query = McpTextQuery.compile("第[一二三四五六七八九十]+章", regex = true, ignoreCase = false)
        assertEquals(2, query.count("第一章开始，第二章结束"))
    }

    @Test
    fun snippetsKeepContextAndAddEllipsis() {
        val query = McpTextQuery.compile("林动", regex = false, ignoreCase = true)
        val content = "前文很多字".repeat(10) + "林动出现" + "后文很多字".repeat(10)
        val snippets = query.snippets(content, limit = 1, contextChars = 4)
        assertEquals(1, snippets.size)
        assertTrue(snippets[0].startsWith("…"))
        assertTrue(snippets[0].endsWith("…"))
        assertTrue(snippets[0].contains("林动"))
    }

    @Test
    fun snippetsRespectPerChapterLimit() {
        val query = McpTextQuery.compile("林动", regex = false, ignoreCase = true)
        val snippets = query.snippets("林动林动林动", limit = 2, contextChars = 0)
        assertEquals(2, snippets.size)
        assertTrue(snippets[0].startsWith("林动"))
        assertTrue(snippets[1].contains("林动"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankQueryIsRejected() {
        McpTextQuery.compile("   ", regex = false, ignoreCase = true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRegexIsRejected() {
        McpTextQuery.compile("(", regex = true, ignoreCase = true)
    }
}
