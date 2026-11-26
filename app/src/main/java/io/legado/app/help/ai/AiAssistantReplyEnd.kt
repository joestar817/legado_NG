package io.legado.app.help.ai

internal enum class AiAssistantReplyEndReason {
    TRUNCATED,
    INTERRUPTED,
    REFUSED,
}

internal class AiAssistantReplyEndException(
    val reason: AiAssistantReplyEndReason,
    partialContent: String = "",
    partialReasoning: String? = null,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause) {
    val partialContent = if (reason == AiAssistantReplyEndReason.REFUSED) "" else partialContent.trim()
    val partialReasoning = partialReasoning?.takeIf {
        reason != AiAssistantReplyEndReason.REFUSED && it.isNotBlank()
    }
}

internal fun assistantUnfinishedReply(
    finishReason: String?,
    streamEnded: Boolean,
    refused: Boolean,
    content: String,
    reasoning: String?,
): AiAssistantReplyEndException? {
    val endReason = assistantReplyEndReason(finishReason, streamEnded, refused) ?: return null
    return AiAssistantReplyEndException(endReason, AiChatToolStepContent.streamingProjection(content), reasoning)
}

internal fun assistantReplyEndReason(
    finishReason: String?,
    streamEnded: Boolean,
    refused: Boolean,
): AiAssistantReplyEndReason? {
    if (refused) return AiAssistantReplyEndReason.REFUSED
    return when (finishReason?.trim()?.lowercase()) {
        "content_filter", "refusal" -> AiAssistantReplyEndReason.REFUSED
        "length", "max_tokens", "max_output_tokens" -> AiAssistantReplyEndReason.TRUNCATED
        "aborted", "cancelled", "canceled", "error", "incomplete" -> AiAssistantReplyEndReason.INTERRUPTED
        else -> if (streamEnded) null else AiAssistantReplyEndReason.INTERRUPTED
    }
}
