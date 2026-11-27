package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelToolExecutionTest {

    @Test
    fun missingRequiredItemsReturnsTheToolErrorToTheModel() {
        val failure = executeInternalToolForModel {
            throw IllegalArgumentException("items is required")
        }
        val reply = AiModelToolContent.format(failure, "agent_memory_batch_upsert", 4096)
        val result = JsonParser.parseString(reply).asJsonObject

        assertFalse(result.get("ok").asBoolean)
        assertEquals("items is required", result.get("error").asString)
    }

    @Test
    fun wrongToolNameIsReportedWithTheRequestedName() {
        val name = "mcp__legado__bookshelf_text_snippets_get"
        val result = unavailableModelToolResult(name)

        assertFalse(result.get("ok").asBoolean)
        assertTrue(result.get("error").asString.contains(name))
    }

    @Test
    fun successfulToolResultIsReturnedUnmodified() {
        val success = JsonObject().apply { addProperty("ok", true) }

        assertSame(success, executeInternalToolForModel { success })
    }

    @Test
    fun cancellationPropagatesToTheTurn() {
        val canceled = CancellationException("stop")

        assertSame(canceled, assertThrows(CancellationException::class.java) {
            executeInternalToolForModel { throw canceled }
        })
    }
}
