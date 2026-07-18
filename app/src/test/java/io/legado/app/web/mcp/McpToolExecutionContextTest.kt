package io.legado.app.web.mcp

import io.legado.app.utils.GSON
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolExecutionContextTest {

    @Test
    fun emptyPolicyKeepsGeneralAgentMemoryUnrestricted() {
        context().requireMemoryAccess(
            scopeType = "conversation",
            scopeKey = "conversation-1",
            domain = "general_notes"
        )
    }

    @Test
    fun multipleRangesAllowExactGlobalProfileAndConcreteBookMemory() {
        val context = context(
            McpMemoryAccessRange(scopeType = "book", domain = "book_scan"),
            McpMemoryAccessRange(
                scopeType = "global",
                scopeKey = "default",
                domain = "reader_preference"
            )
        )

        context.requireMemoryAccess("book", "book-work-key", "book_scan")
        context.requireMemoryAccess("global", "default", "reader_preference")
    }

    @Test
    fun activePolicyRejectsBroadSearchAndOutOfRangeAccess() {
        val context = context(
            McpMemoryAccessRange(scopeType = "book", domain = "book_scan"),
            McpMemoryAccessRange(
                scopeType = "global",
                scopeKey = "default",
                domain = "reader_preference"
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            context.requireMemoryAccess("book", "", "book_scan")
        }
        assertThrows(IllegalArgumentException::class.java) {
            context.requireMemoryAccess("global", "other", "reader_preference")
        }
        assertThrows(IllegalArgumentException::class.java) {
            context.requireMemoryAccess("global", "default", "book_scan")
        }
    }

    @Test
    fun readOnlyRangeAllowsModelReadsButRejectsModelWrites() {
        val context = context(
            McpMemoryAccessRange(
                scopeType = "global",
                scopeKey = "default",
                domain = "reader_preference",
                access = McpMemoryAccess.READ_ONLY
            )
        )

        context.requireMemoryAccess(
            "global",
            "default",
            "reader_preference",
            McpMemoryOperation.READ
        )
        assertThrows(IllegalArgumentException::class.java) {
            context.requireMemoryAccess(
                "global",
                "default",
                "reader_preference",
                McpMemoryOperation.WRITE
            )
        }
    }

    @Test
    fun protocolUsesStableSerializedFieldNames() {
        val json = GSON.toJsonTree(
            context(
                McpMemoryAccessRange(
                    scopeType = "global",
                    scopeKey = "default",
                    domain = "reader_preference",
                    access = McpMemoryAccess.READ_ONLY
                )
            )
        ).asJsonObject
        val range = json.getAsJsonArray("allowed_memory_ranges").single().asJsonObject

        assertTrue(json.has("allowed_memory_ranges"))
        assertTrue(range.get("scope_type").asString == "global")
        assertTrue(range.get("scope_key").asString == "default")
        assertTrue(range.get("domain").asString == "reader_preference")
        assertTrue(range.get("access").asString == "read_only")
    }

    private fun context(vararg ranges: McpMemoryAccessRange): McpToolExecutionContext {
        return McpToolExecutionContext(
            conversationId = "conversation-1",
            turnId = "turn-1",
            skillRevision = "skill@1",
            skillContentHash = "hash",
            allowedMemoryRanges = ranges.toList()
        )
    }
}
