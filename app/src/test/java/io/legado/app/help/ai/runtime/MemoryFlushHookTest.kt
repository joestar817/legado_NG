package io.legado.app.help.ai.runtime

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryFlushHookTest {

    @Test
    fun readReceiptBlocksNextReadAndFinalizeUntilMemoryAcknowledgesIt() {
        val runtime = AgentHookRegistry.createRuntime(listOf(binding()))

        val notice = runtime.dispatch(resultEvent(readReceipt("read-1"))).single().result
        assertTrue(notice is AgentHookResult.InjectNotice)
        assertEquals(listOf("read-1"), runtime.snapshot().single().pendingReceiptIds)

        assertBlocked(
            runtime.dispatch(proposedEvent("bookshelf_text_window_get")).single().result,
            "memory_flush_pending_limit"
        )
        assertBlocked(
            runtime.dispatch(boundaryEvent("before-memory", AgentHookPhase.BEFORE_TURN_FINALIZE)).single().result,
            "memory_flush_pending"
        )

        runtime.dispatch(resultEvent(memoryReceipt(listOf("read-1"))))

        assertEquals(
            AgentHookResult.Continue,
            runtime.dispatch(boundaryEvent("after-memory", AgentHookPhase.BEFORE_TURN_FINALIZE)).single().result
        )
        assertTrue(runtime.snapshot().single().pendingReceiptIds.isEmpty())
    }

    @Test
    fun failedOrTruncatedReadDoesNotBecomePending() {
        val runtime = AgentHookRegistry.createRuntime(listOf(binding()))

        runtime.dispatch(resultEvent(readReceipt("failed", ToolExecutionStatus.FAILED)))
        runtime.dispatch(resultEvent(readReceipt("truncated", resultTruncated = true)))

        assertTrue(runtime.snapshot().single().pendingReceiptIds.isEmpty())
    }

    @Test
    fun resumeRestoresOnlyConfiguredReadReceipts() {
        val runtime = AgentHookRegistry.createRuntime(listOf(binding()))
        runtime.dispatch(
            AgentHookEvent(
                eventId = "resume",
                phase = AgentHookPhase.TURN_RESUMED,
                conversationId = CONVERSATION_ID,
                turnId = TURN_ID,
                pendingReceiptIds = listOf("read-1")
            )
        )

        assertEquals(setOf("bookshelf_text_window_get"), runtime.pendingReceiptToolNames())
        assertBlocked(
            runtime.dispatch(boundaryEvent("after-resume", AgentHookPhase.BEFORE_COMPACTION)).single().result,
            "memory_flush_pending"
        )
    }

    private fun binding() = AgentHookBinding(
        id = "core.memory_flush",
        version = 1,
        config = JsonParser.parseString(
            """
            {
              "watch_tools": ["bookshelf_text_window_get"],
              "flush_tools": ["agent_memory_upsert", "agent_memory_batch_upsert"],
              "max_pending": 1,
              "block_before": ["compaction", "turn_finalize"]
            }
            """.trimIndent()
        ).asJsonObject
    )

    private fun readReceipt(
        id: String,
        status: ToolExecutionStatus = ToolExecutionStatus.SUCCEEDED,
        resultTruncated: Boolean = false
    ) = receipt(
        id = id,
        toolName = "bookshelf_text_window_get",
        status = status,
        resultTruncated = resultTruncated
    )

    private fun memoryReceipt(sourceIds: List<String>) = receipt(
        id = "memory-1",
        toolName = "agent_memory_batch_upsert",
        acknowledgedReceiptIds = sourceIds
    )

    private fun receipt(
        id: String,
        toolName: String,
        status: ToolExecutionStatus = ToolExecutionStatus.SUCCEEDED,
        resultTruncated: Boolean = false,
        acknowledgedReceiptIds: List<String> = emptyList()
    ) = ToolExecutionReceipt(
        receiptId = id,
        conversationId = CONVERSATION_ID,
        turnId = TURN_ID,
        stepId = "step-$id",
        toolCallId = "call-$id",
        toolName = toolName,
        argumentsHash = "arguments-hash",
        resultHash = "result-hash",
        status = status,
        resultTruncated = resultTruncated,
        acknowledgedReceiptIds = acknowledgedReceiptIds
    )

    private fun resultEvent(receipt: ToolExecutionReceipt) = AgentHookEvent(
        eventId = "event-${receipt.receiptId}",
        phase = AgentHookPhase.TOOL_RESULT_COMMITTED,
        conversationId = CONVERSATION_ID,
        turnId = TURN_ID,
        stepId = receipt.stepId,
        receipt = receipt
    )

    private fun proposedEvent(toolName: String) = AgentHookEvent(
        eventId = "proposed-$toolName",
        phase = AgentHookPhase.TOOL_CALL_PROPOSED,
        conversationId = CONVERSATION_ID,
        turnId = TURN_ID,
        proposedToolName = toolName
    )

    private fun boundaryEvent(id: String, phase: AgentHookPhase) = AgentHookEvent(
        eventId = "boundary-$id-$phase",
        phase = phase,
        conversationId = CONVERSATION_ID,
        turnId = TURN_ID
    )

    private fun assertBlocked(result: AgentHookResult, reasonCode: String) {
        assertTrue(result is AgentHookResult.BlockBoundary)
        assertEquals(reasonCode, (result as AgentHookResult.BlockBoundary).reasonCode)
        assertEquals(listOf("read-1"), result.pendingReceiptIds)
    }

    private companion object {
        const val CONVERSATION_ID = "conversation-1"
        const val TURN_ID = "turn-1"
    }
}
