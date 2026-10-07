package io.legado.app.web.mcp

import com.google.gson.JsonParser
import io.legado.app.web.mcp.McpModuleSupport.identifiers
import io.legado.app.web.mcp.McpModuleSupport.int
import org.junit.Assert.*
import org.junit.Test

class McpModuleSupportTest {
    @Test fun contentWindowsRoundTripSupplementaryCharacters() {
        val body = "AB😀CD𠮷末"
        for (limit in 1..5) {
            var offset = 0
            val parts = StringBuilder()
            do {
                val window = mcpTextWindow(body, offset, limit)
                val text = window["content"] as String
                assertEquals(text, text.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
                parts.append(text)
                val next = window["next_offset"] as? Int
                if (next == null) break
                assertTrue(next > offset)
                offset = next
            } while (true)
            assertEquals(body, parts.toString())
        }
    }

    @Test fun contentOffsetInsideSurrogatePairReportsAlignedStart() {
        val window = mcpTextWindow("AB😀CD", 3, 1)
        assertEquals(2, window["offset"])
        assertEquals("😀", window["content"])
        assertEquals(4, window["next_offset"])
    }

    @Test fun contentWindowPastEndIsEmpty() {
        assertEquals("", mcpTextWindow("text", Int.MAX_VALUE, 2)["content"])
        assertNull(mcpTextWindow("text", Int.MAX_VALUE, 2)["next_offset"])
    }

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    @Test fun identifiersPreserveWhitespaceAndRejectDuplicates() {
        assertEquals(listOf(" https://a "), json("""{"urls":[" https://a "]}""").identifiers())
        assertThrows(IllegalArgumentException::class.java) { json("""{"urls":["a","a"]}""").identifiers() }
        assertThrows(IllegalArgumentException::class.java) { json("""{"urls":[]}""").identifiers() }
    }

    @Test fun integersRejectFractionOverflowAndStringCoercion() {
        listOf("1.5", "2147483648", "\"2\"", "null").forEach { value ->
            assertTrue(runCatching { json("""{"page":$value}""").int("page", 1, 1) }.isFailure)
        }
        assertEquals(2, json("""{"page":2}""").int("page", 1, 1))
    }

    @Test fun pagePastEndHasNoContinuationAndDoesNotOverflow() {
        val result = McpModuleSupport.page(listOf("a", "b"), json("""{"offset":2147483647}"""), "items")
        assertEquals(emptyList<String>(), result["items"])
        assertNull(result["next_offset"])
    }

    @Test fun localPageContinuationDoesNotSkipResults() {
        val result = McpModuleSupport.page(listOf("a", "b", "c"), json("""{"offset":1,"limit":1}"""), "items")
        assertEquals(listOf("b"), result["items"])
        assertEquals(2, result["next_offset"])
        assertEquals(3, result["total"])
    }

    @Test fun invalidArgumentsAreRejectedBeforeExecution() {
        val definition = listOf(McpModuleSupport.tool("test", "test", McpModuleSupport.paging +
            mapOf("enabled" to McpModuleSupport.field("boolean", "required")), "enabled"))
        listOf("{}", """{"enabled":"false"}""", """{"enabled":false,"limit":0}""",
            """{"enabled":false,"extra":1}""").forEach {
            assertTrue(runCatching { McpModuleSupport.validate("test", json(it), definition) }.isFailure)
        }
        McpModuleSupport.validate("test", json("""{"enabled":false,"limit":1}"""), definition)
    }

    @Test fun newModulesSeparateReadWriteAndDestructiveOperations() {
        listOf("rss_source_import", "rss_source_save", "rss_star_save", "rss_read_record_save",
            "rss_rule_subscription_save", "rss_rule_subscription_refresh", "explore_source_set_enabled").forEach {
            assertEquals(McpToolSideEffect.APP_WRITE, McpInternalToolCatalog.sideEffectOf(it))
        }
        listOf("rss_source_delete", "rss_star_delete", "rss_read_record_delete", "rss_rule_subscription_delete").forEach {
            assertEquals(McpToolSideEffect.DESTRUCTIVE, McpInternalToolCatalog.sideEffectOf(it))
        }
        val read = McpInternalToolCatalog.resolveToolNames(listOf("rss.sources", "rss.read", "rss.library", "explore.query"))
        assertTrue(read.isNotEmpty())
        assertTrue(read.all { !McpInternalToolCatalog.requiresUserConfirmation(it) })
        assertFalse("rss_source_import" in read)
    }
}
