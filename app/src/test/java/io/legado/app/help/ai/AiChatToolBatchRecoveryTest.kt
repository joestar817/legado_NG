package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatToolBatchRecoveryTest {

    @Test
    fun removesOnlyIncompleteAssistantToolBatchAndKeepsLaterUserMessage() {
        val messages = mutableListOf(
            message("user", "开始"),
            assistantCalls("call-1", "call-2"),
            toolResult("call-1"),
            message("user", "继续")
        )

        assertTrue(AiChatToolBatchRecovery.discardLatestIncompleteBatch(messages))
        assertEquals(listOf("user", "user"), messages.map { it.get("role").asString })
        assertEquals("继续", messages.last().get("content").asString)
    }

    @Test
    fun keepsCompleteToolBatch() {
        val messages = mutableListOf(
            message("user", "开始"),
            assistantCalls("call-1", "call-2"),
            toolResult("call-1"),
            toolResult("call-2")
        )

        assertFalse(AiChatToolBatchRecovery.discardLatestIncompleteBatch(messages))
        assertEquals(4, messages.size)
    }

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    private fun assistantCalls(vararg ids: String) = message("assistant", "").apply {
        add("tool_calls", JsonArray().apply {
            ids.forEach { id ->
                add(JsonObject().apply { addProperty("id", id) })
            }
        })
    }

    private fun toolResult(callId: String) = message("tool", "{}").apply {
        addProperty("tool_call_id", callId)
    }
}
