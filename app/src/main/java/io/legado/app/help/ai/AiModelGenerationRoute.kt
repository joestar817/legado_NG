package io.legado.app.help.ai

import io.legado.app.help.ai.AiModelGenerationTestProtocol.CHAT_COMPLETIONS
import io.legado.app.help.ai.AiModelGenerationTestProtocol.RESPONSES
import okhttp3.HttpUrl.Companion.toHttpUrl

internal enum class AiModelGenerationIssue {
    UNSUPPORTED_PROTOCOL,
    STREAMING_REQUIRED,
}

internal data class AiModelGenerationRoute(
    val protocol: AiModelGenerationTestProtocol,
    val issue: AiModelGenerationIssue?,
    val requiresStreaming: Boolean = false,
)

internal fun AiProviderSetting.resolveModelGenerationRoute(
    model: AiModel,
    protocolOverride: AiModelGenerationTestProtocol? = null,
    stream: Boolean = streamResponseEnabled,
): AiModelGenerationRoute {
    if (isOpenAiAccountProvider()) {
        val issue = when {
            protocolOverride != null && protocolOverride != RESPONSES -> AiModelGenerationIssue.UNSUPPORTED_PROTOCOL
            !stream -> AiModelGenerationIssue.STREAMING_REQUIRED
            else -> null
        }
        return AiModelGenerationRoute(RESPONSES, issue, requiresStreaming = true)
    }
    val defaultProtocol = modelGenerationTestProtocol()
    if (type != AiProviderType.OPENAI) {
        val issue = AiModelGenerationIssue.UNSUPPORTED_PROTOCOL.takeIf {
            protocolOverride != null && protocolOverride != defaultProtocol
        }
        return AiModelGenerationRoute(defaultProtocol, issue)
    }

    val protocol = protocolOverride ?: model.interfaceProtocol(this) ?: defaultProtocol
    val officialOpenAiResponsesOnly = model.id in openAiResponsesOnlyModels &&
        isOfficialOpenAiGenerationPath(CHAT_COMPLETIONS, "/v1/chat/completions")

    val requiresStreaming = protocol == CHAT_COMPLETIONS && model.id in bailianStreamingOnlyModels &&
        isOfficialBailianGenerationEndpoint() &&
        generationUrl(protocol)?.encodedPath?.trimEnd('/') == "/compatible-mode/v1/chat/completions"
    val issue = when {
        generationApiPath(protocol) == null || (officialOpenAiResponsesOnly && protocol == CHAT_COMPLETIONS) ->
            AiModelGenerationIssue.UNSUPPORTED_PROTOCOL
        requiresStreaming && !stream -> AiModelGenerationIssue.STREAMING_REQUIRED
        else -> null
    }
    return AiModelGenerationRoute(protocol, issue, requiresStreaming)
}

private fun AiModel.interfaceProtocol(provider: AiProviderSetting): AiModelGenerationTestProtocol? {
    if (modelSettings?.interfacePreference == null) return null
    return when (modelSettingsFor(provider.modelConfigurationKey())?.interfacePreference) {
        AiModelInterfacePreference.CHAT_COMPLETIONS -> CHAT_COMPLETIONS
        AiModelInterfacePreference.RESPONSES -> RESPONSES
        AiModelInterfacePreference.PROVIDER_DEFAULT, null -> null
    }
}

private fun AiProviderSetting.isOfficialOpenAiGenerationPath(
    protocol: AiModelGenerationTestProtocol,
    expectedPath: String,
): Boolean {
    val url = generationUrl(protocol) ?: return false
    return url.isHttps && url.host == "api.openai.com" && url.port == 443 &&
        url.encodedPath.trimEnd('/') == expectedPath
}

internal fun AiProviderSetting.generationUrl(protocol: AiModelGenerationTestProtocol) =
    generationApiPath(protocol)?.let { path ->
        runCatching { buildAiApiEndpoint(baseUrl, path).toHttpUrl() }.getOrNull()
    }

// OpenAI documents these Pro models as Responses-only in their individual model pages.
private val openAiResponsesOnlyModels = setOf(
    "o3-pro", "o3-pro-2025-06-10",
    "gpt-5-pro", "gpt-5-pro-2025-10-06",
    "gpt-5.2-pro", "gpt-5.2-pro-2025-12-11",
    "gpt-5.4-pro", "gpt-5.4-pro-2026-03-05",
)

// Bailian documents these Qwen3 open models and QwQ/QVQ families as streaming-only for Chat.
// https://help.aliyun.com/zh/model-studio/stream
private val bailianStreamingOnlyModels = setOf(
    "qwen3-235b-a22b", "qwen3-30b-a3b", "qwen3-32b", "qwen3-14b", "qwen3-8b", "qwen3-4b",
    "qwen3-1.7b", "qwen3-0.6b", "qwen3-235b-a22b-thinking-2507", "qwen3-235b-a22b-instruct-2507",
    "qwen3-30b-a3b-thinking-2507", "qwen3-30b-a3b-instruct-2507",
    "qwen3-4b-thinking-2507", "qwen3-4b-instruct-2507",
    "qwq-32b", "qwq-32b-preview", "qwq-plus", "qwq-plus-2025-03-05", "qwq-plus-latest",
    "qvq-72b-preview", "qvq-max", "qvq-max-2025-03-25", "qvq-max-2025-05-15", "qvq-max-latest",
    "qvq-plus", "qvq-plus-2025-05-15", "qvq-plus-latest",
)
