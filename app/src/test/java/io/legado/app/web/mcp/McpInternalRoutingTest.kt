package io.legado.app.web.mcp

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class McpInternalRoutingTest {
    @Test fun everyCatalogToolHasARealDefinition() {
        val definitions = McpServer.listInternalTools(McpInternalToolCatalog.allCapabilityIds)
        assertEquals(McpInternalToolCatalog.allToolNames, definitions.map { it["name"] }.toSet())
        assertEquals(definitions.size, definitions.map { it["name"] }.distinct().size)
    }

    @Test fun readCapabilityCannotDispatchWriteOrDelete() {
        listOf("rss_source_save", "rss_source_import", "rss_source_delete", "rss_rule_subscription_refresh").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                McpServer.callInternalTool(name, JsonObject(), listOf("rss.read", "rss.sources"))
            }
        }
        assertTrue(McpServer.listInternalTools(listOf("unknown")).isEmpty())
    }

    @Test fun selectedCapabilitiesOnlyExposeTheirOwnDefinitions() {
        val selected = listOf("rss.read", "explore.query")
        assertEquals(McpInternalToolCatalog.resolveToolNames(selected),
            McpServer.listInternalTools(selected).map { it["name"] }.toSet())
    }
}
