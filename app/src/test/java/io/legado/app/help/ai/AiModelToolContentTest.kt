package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelToolContentTest {

    @Test
    fun prefersStructuredContentInsteadOfDuplicatedMcpEnvelope() {
        val structured = JsonObject().apply {
            addProperty("ok", true)
            addProperty("normalized_data", "正文")
        }
        val result = JsonObject().apply {
            add("content", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", structured.toString())
                })
            })
            add("structuredContent", structured)
            addProperty("isError", false)
        }

        val formatted = AiModelToolContent.format(result, "test", 10_000)

        assertEquals(structured.toString(), formatted)
        assertFalse(formatted.contains("structuredContent"))
        assertFalse(AiModelToolContent.isTruncatedByApp(formatted))
    }

    @Test
    fun marksOversizedToolContentAsAppTruncated() {
        val structured = JsonObject().apply {
            addProperty("ok", true)
            addProperty("normalized_data", "正文".repeat(100))
        }
        val result = JsonObject().apply {
            add("structuredContent", structured)
        }

        val formatted = AiModelToolContent.format(result, "bookshelf_text_window_get", 20)

        assertTrue(formatted.contains("\"truncated_by_app\":true"))
        assertTrue(AiModelToolContent.isTruncatedByApp(formatted))
    }

    @Test
    fun toolResultBudgetShrinksNearContextBoundaryAndKeepsResponseReserve() {
        val roomy = AiToolResultBudget.resolveChars(
            contextWindowTokens = 200_000,
            thresholdTokens = 180_000,
            estimatedTokens = 20_000
        )
        val nearBoundary = AiToolResultBudget.resolveChars(
            contextWindowTokens = 200_000,
            thresholdTokens = 180_000,
            estimatedTokens = 178_000
        )

        assertEquals(120_000, roomy)
        assertEquals(1_024, nearBoundary)
        assertTrue(nearBoundary < roomy)
    }
}
