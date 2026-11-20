package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantReplyEndTest {
    @Test
    fun outputLimitProducesAnUnfinishedReplyForTheNextRequest() {
        val failure = requireNotNull(assistantUnfinishedReply(
            finishReason = "length",
            streamEnded = true,
            refused = false,
            content = "Partial reply",
            reasoning = "Unfinished reasoning",
        ))
        assertEquals(AiAssistantReplyEndReason.TRUNCATED, failure.reason)
        assertEquals("Partial reply", failure.partialContent)
        assertEquals("Unfinished reasoning", failure.partialReasoning)

        val draft = requireNotNull(AiChatContextManager.interruptedReplyContent("Partial reply", failure))
        val history = listOf(AiChatContextManager.interruptedReplyMessage("message-id", draft))
        val outgoing = AiChatContextManager.providerMessages(history)
        assertEquals(draft, outgoing.single().asJsonObject.stringOrNull("content"))
        assertTrue(draft.contains("输出达到上限"))
    }

    @Test
    fun explicitRefusalDoesNotCarryPartialTextOrReasoning() {
        val failure = requireNotNull(assistantUnfinishedReply(
            finishReason = "stop",
            streamEnded = true,
            refused = true,
            content = "Blocked preview",
            reasoning = "Blocked reasoning",
        ))
        assertEquals(AiAssistantReplyEndReason.REFUSED, failure.reason)
        assertTrue(failure.partialContent.isNullOrBlank())
        assertTrue(failure.partialReasoning.isNullOrBlank())
        assertNull(AiChatContextManager.interruptedReplyContent("Blocked preview", failure))
    }

    @Test
    fun socketEofWithoutAnEndSignalIsInterrupted() {
        val failure = requireNotNull(assistantUnfinishedReply(
            finishReason = null,
            streamEnded = false,
            refused = false,
            content = "Partial reply",
            reasoning = null,
        ))
        assertEquals(AiAssistantReplyEndReason.INTERRUPTED, failure.reason)
        assertEquals("Partial reply", failure.partialContent)
        assertNull(assistantUnfinishedReply("stop", true, false, "Complete reply", null))
        assertNull(assistantUnfinishedReply(null, true, false, "Complete reply", null))
    }
}
