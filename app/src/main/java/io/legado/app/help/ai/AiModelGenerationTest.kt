package io.legado.app.help.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

internal enum class AiModelGenerationTestProtocol {
    CHAT_COMPLETIONS,
    RESPONSES,
    CLAUDE_MESSAGES,
    GEMINI_GENERATE_CONTENT,
}

internal fun AiProviderSetting.modelGenerationTestProtocol(): AiModelGenerationTestProtocol =
    when (type) {
        AiProviderType.CLAUDE -> AiModelGenerationTestProtocol.CLAUDE_MESSAGES
        AiProviderType.GOOGLE -> AiModelGenerationTestProtocol.GEMINI_GENERATE_CONTENT
        AiProviderType.OPENAI ->
            if (useResponsesApi ?: (legacyResponsesPath() != null)) AiModelGenerationTestProtocol.RESPONSES
            else AiModelGenerationTestProtocol.CHAT_COMPLETIONS
    }

internal fun AiProviderSetting.modelGenerationTestProtocols(): List<AiModelGenerationTestProtocol> {
    val current = modelGenerationTestProtocol()
    if (type != AiProviderType.OPENAI || isOpenAiAccountProvider()) return listOf(current)
    return if (current == AiModelGenerationTestProtocol.RESPONSES ||
        generationApiPath(AiModelGenerationTestProtocol.RESPONSES) != null
    ) {
        listOf(AiModelGenerationTestProtocol.CHAT_COMPLETIONS, AiModelGenerationTestProtocol.RESPONSES)
    } else {
        listOf(AiModelGenerationTestProtocol.CHAT_COMPLETIONS)
    }
}

private val generationPresets by lazy { AiDefaultProviders.all().associateBy { it.id } }

internal fun AiProviderSetting.generationApiPath(protocol: AiModelGenerationTestProtocol): String? {
    if (isOpenAiAccountProvider()) return when (protocol) {
        AiModelGenerationTestProtocol.RESPONSES -> if (isCodexSubscription()) "$CODEX_API/responses" else "$CHATGPT_API/responses"
        else -> null
    }
    if (type != AiProviderType.OPENAI) return null
    return when (protocol) {
        AiModelGenerationTestProtocol.CHAT_COMPLETIONS ->
            if (legacyResponsesPath() != null) {
                generationPresets[id]?.chatCompletionsPath.takeIf { builtIn } ?: "/chat/completions"
            } else {
                chatCompletionsPath.trim().takeIf(String::isNotEmpty)
            }
        AiModelGenerationTestProtocol.RESPONSES -> {
            val savedResponsesPath = legacyResponsesPath()
            if (builtIn) {
                val officialId = if (id == "aliyun_bailian") {
                    id.takeIf { isOfficialBailianGenerationEndpoint() }
                } else {
                    officialModelProviderId()
                }
                savedResponsesPath ?: officialId?.let { generationPresets[it]?.responsesApiPath }
            } else {
                responsesApiPath?.trim()?.takeIf(String::isNotEmpty) ?: savedResponsesPath ?: "/responses"
            }
        }
        else -> null
    }
}

private fun AiProviderSetting.legacyResponsesPath(): String? =
    chatCompletionsPath.trim().takeIf { path ->
        path.isNotEmpty() && runCatching {
            buildAiApiEndpoint(baseUrl, path).usesResponsesProtocol()
        }.getOrDefault(false)
    }

internal fun String.usesResponsesProtocol(): Boolean =
    toHttpUrl().encodedPath.trimEnd('/').endsWith("/responses")

internal sealed interface AiModelGenerationTestResult {
    data class Success(val reply: String) : AiModelGenerationTestResult

    data class Failure(
        val reason: AiModelGenerationTestFailure,
        val reply: String = "",
        val httpStatus: Int? = null,
    ) : AiModelGenerationTestResult
}

internal enum class AiModelGenerationTestFailure {
    MISSING_API_KEY,
    INVALID_ENDPOINT,
    UNSUPPORTED_PROTOCOL,
    STREAMING_REQUIRED,
    HTTP,
    NETWORK,
    TIMEOUT,
    INTERRUPTED,
    TRUNCATED,
    REFUSED,
    TOOL_CALL,
    EMPTY_REPLY,
    INVALID_RESPONSE,
}

internal fun JsonObject.hasChatGenerationRefusal(): Boolean =
    get("refusal")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.isNotBlank() == true ||
        arrayOrNull("content")?.any { part ->
            part.isJsonObject && part.asJsonObject.stringOrNull("type") == "refusal"
        } == true

internal fun JsonElement?.chatGenerationVisibleText(): String {
    val content = this ?: return ""
    if (content.isJsonPrimitive && content.asJsonPrimitive.isString) return content.asString
    if (!content.isJsonArray) return ""
    return content.asJsonArray.asSequence()
        .filter { it.isJsonObject }
        .map { it.asJsonObject }
        .filter { part ->
            when (part.stringOrNull("type")) {
                null, "text", "output_text" -> true
                else -> false
            }
        }
        .mapNotNull { it.get("text")?.takeIf { text -> text.isJsonPrimitive && text.asJsonPrimitive.isString } }
        .joinToString("") { it.asString }
}

internal fun evaluateModelGenerationTestReply(
    content: String,
    finishReason: String?,
    streamEnded: Boolean,
    toolCallReceived: Boolean,
    refused: Boolean,
): AiModelGenerationTestResult {
    val reply = content.trim()
    val failure = when {
        refused || finishReason in setOf("content_filter", "refusal") -> AiModelGenerationTestFailure.REFUSED
        finishReason == "length" -> AiModelGenerationTestFailure.TRUNCATED
        toolCallReceived || finishReason in setOf("tool_calls", "function_call") ->
            AiModelGenerationTestFailure.TOOL_CALL
        !streamEnded || (finishReason != null && finishReason != "stop") ->
            AiModelGenerationTestFailure.INTERRUPTED
        reply.isBlank() -> AiModelGenerationTestFailure.EMPTY_REPLY
        else -> null
    }
    return if (failure == null) AiModelGenerationTestResult.Success(reply)
    else AiModelGenerationTestResult.Failure(failure, reply)
}
