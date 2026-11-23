package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatSearchQueryTest {
    @Test
    fun treatsPunctuationLiterallyAndIgnoresCase() {
        val query = AiChatSearchQuery("Legado [MCP]+")
        val text = "legado [mcp]+ / LEGADO [MCP]+"

        assertEquals(listOf("legado [mcp]+", "LEGADO [MCP]+"), query.findAllIn(text).map { it.value }.toList())
        assertNull(query.findIn("Legado MCPP"))
    }

    @Test
    fun findsWhitespaceAcrossLinesAndInTheExcerpt() {
        val query = AiChatSearchQuery("商安  斗罗")
        val text = "主角商安\n　斗罗大陆"
        val hit = requireNotNull(query.findIn(text))

        assertEquals("商安\n　斗罗", hit.value)
        val excerpt = query.excerpt(text, hit.range)
        assertEquals("主角商安 斗罗大陆", excerpt)
        assertNotNull(query.findIn(excerpt))
    }

    @Test
    fun keepsTheHitVisibleNearTheEndOfALongReply() {
        val query = AiChatSearchQuery("商安")
        val text = "前面的正文".repeat(100) + "商安" + "后文".repeat(40)
        val excerpt = query.excerpt(text, requireNotNull(query.findIn(text)).range)

        assertTrue(excerpt.startsWith("…"))
        assertTrue(excerpt.endsWith("…"))
        assertEquals(11, query.findIn(excerpt)?.range?.first)
    }

    @Test
    fun preservesSurrogatePairsAtBothExcerptEdges() {
        val query = AiChatSearchQuery("商安")
        val left = "旧".repeat(6) + "🍃" + "前".repeat(9) + "商安"
        val right = "商安" + "字".repeat(51) + "🍃" + "下一段"

        assertTrue(query.excerpt(left, requireNotNull(query.findIn(left)).range).startsWith("…🍃"))
        assertTrue(query.excerpt(right, requireNotNull(query.findIn(right)).range).endsWith("🍃…"))
    }
}
