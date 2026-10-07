package io.legado.app.web.mcp

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RssContentMcpToolsTest {
    private fun value(extra: String = "") = JsonParser.parseString(
        """{"origin":"source","link":"link"$extra}"""
    ).asJsonObject

    @Test fun articleRejectsInvalidTypesAndNullRequiredFields() {
        listOf("\"type\":99", "\"durPos\":-1", "\"read\":\"false\"",
            "\"title\":null", "\"order\":1.5", "\"typo\":true").forEach {
            assertTrue(it, runCatching { RssContentMcpTools.decodeArticle(value(",$it"), "source") }.isFailure)
        }
    }

    @Test fun articleRejectsIdentityMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            RssContentMcpTools.decodeArticle(value(), "other")
        }
    }

    @Test fun articleKeepsPayloadAndDefaultsWithoutPersistence() {
        val article = RssContentMcpTools.decodeArticle(value(",\"description\":\"中文😀\",\"variable\":\"{}\""), "source")
        assertEquals("中文😀", article.description)
        assertEquals("{}", article.variable)
        assertFalse(article.read)
        assertEquals(0, article.durPos)
    }
}
