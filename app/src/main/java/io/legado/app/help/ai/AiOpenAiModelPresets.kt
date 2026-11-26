package io.legado.app.help.ai

// The model pages linked from https://developers.openai.com/api/docs/models/all
// declare modalities and function calling. Conditional capability depends on the request API.
private val textOnlyOpenAiModels = setOf("o3-mini", "o3-mini-2025-01-31")
private val openAiResponsesToolModels = setOf(
    "gpt-6.2-astra", "gpt-6.2-sol", "gpt-6.1-sol", "gpt-6.1-terra", "gpt-6-astra", "gpt-6-luna", "gpt-6-sol",
)
private val openAiModelsWithoutFunctionCalling = setOf(
    "chatgpt-4o-latest", "o3-deep-research", "o3-deep-research-2025-06-26",
    "o4-mini-deep-research", "o4-mini-deep-research-2025-06-26",
)
private val openAiAudioChatModels = setOf(
    "gpt-audio", "gpt-audio-2025-08-28", "gpt-audio-mini", "gpt-audio-mini-2025-12-15", "gpt-audio-1.5",
)
private val openAiSearchChatModels = setOf("gpt-5-search-api", "gpt-5-search-api-2025-10-14")

internal fun isOpenAiChatCompletionsOnlyModel(modelId: String): Boolean =
    modelId.lowercase().let { it in openAiAudioChatModels || it in openAiSearchChatModels }

internal fun openAiSpecializedModelPreset(modelId: String): AiModelPreset? {
    val id = modelId.lowercase()
    return when {
        id.startsWith("gpt-image-") || id == "chatgpt-image-latest" -> AiModelPreset(
            inputModalities = listOf("text", "image"), outputModalities = listOf("image"),
            toolCalling = false, type = AiModelType.IMAGE,
        )
        id == "whisper-1" || id == "gpt-realtime-whisper" || id == "gpt-live-transcribe" ||
            id == "gpt-transcribe" || id.startsWith("gpt-transcribe-") ||
            id.startsWith("gpt-4o-transcribe") || id.startsWith("gpt-4o-mini-transcribe") -> AiModelPreset(
                inputModalities = listOf("audio"), outputModalities = listOf("text"),
                toolCalling = false, type = AiModelType.ASR,
            )
        id.startsWith("gpt-4o-mini-tts") -> AiModelPreset(
            inputModalities = listOf("text"), outputModalities = listOf("audio"),
            toolCalling = false, type = AiModelType.TTS,
        )
        id.startsWith("omni-moderation-") -> AiModelPreset(
            inputModalities = listOf("text", "image"), outputModalities = emptyList(),
            toolCalling = false, type = AiModelType.OTHER,
        )
        id == "gpt-realtime-translate" -> AiModelPreset(
            inputModalities = listOf("audio"), outputModalities = listOf("audio", "text"),
            toolCalling = false, type = AiModelType.OTHER,
        )
        id == "gpt-realtime" || id.startsWith("gpt-realtime-") || id == "gpt-live-1" || id == "gpt-live-1.1" ->
            AiModelPreset(
                inputModalities = listOf("text", "audio"), outputModalities = listOf("text", "audio"),
                toolCalling = false, type = AiModelType.OTHER,
            )
        id in openAiAudioChatModels -> AiModelPreset(
            inputModalities = listOf("text", "audio"), outputModalities = listOf("text", "audio"), type = AiModelType.CHAT,
        )
        id in openAiSearchChatModels -> AiModelPreset(toolCalling = false, type = AiModelType.CHAT)
        else -> null
    }
}

internal fun documentedOpenAiModelPresets(): Map<String, AiModelPreset> = buildMap {
    for (id in documentedOpenAiModelIds()) {
        put(id, AiModelPreset(
            inputModalities = if (id in textOnlyOpenAiModels) listOf("text") else listOf("text", "image"),
            outputModalities = listOf("text"),
            toolCalling = when (id) {
                in openAiResponsesToolModels -> null
                in openAiModelsWithoutFunctionCalling -> false
                else -> true
            },
        ))
    }

    fun addEfforts(efforts: List<String>, canDisable: Boolean?, vararg modelIds: String) {
        for (id in modelIds) {
            put(id, getValue(id).copy(
                efforts = efforts,
                reasoningProtocol = AiReasoningProtocol.EFFORT,
                canDisableThinking = canDisable,
            ))
        }
    }

    addEfforts(listOf("low", "medium", "high", "xhigh", "max"), true,
        "gpt-6.1-terra", "gpt-6-luna", "gpt-6-sol",
        "gpt-5.6", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna",
    )
    addEfforts(listOf("low", "medium", "high", "xhigh", "max"), null, "gpt-6.1-sol", "gpt-6-astra")
    for (id in listOf("gpt-6.2-astra", "gpt-6.2-sol")) {
        put(id, getValue(id).copy(reasoning = true, canDisableThinking = false))
    }
    addEfforts(listOf("low", "medium", "high", "xhigh"), true,
        "gpt-5.5", "gpt-5.5-2026-04-23", "gpt-5.4", "gpt-5.4-2026-03-05",
        "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17", "gpt-5.4-nano", "gpt-5.4-nano-2026-03-17",
        "gpt-5.2", "gpt-5.2-2025-12-11",
    )
    addEfforts(listOf("low", "medium", "high", "xhigh"), null, "gpt-5.3-codex", "gpt-5.2-codex")
    addEfforts(listOf("medium", "high", "xhigh"), false,
        "gpt-5.5-pro", "gpt-5.5-pro-2026-04-23", "gpt-5.4-pro", "gpt-5.4-pro-2026-03-05",
        "gpt-5.2-pro", "gpt-5.2-pro-2025-12-11",
    )
    addEfforts(listOf("low", "medium", "high"), true, "gpt-5.1", "gpt-5.1-2025-11-13")
    addEfforts(listOf("low", "medium", "high"), null, "gpt-5", "gpt-5-2025-08-07")
    addEfforts(listOf("high"), false, "gpt-5-pro", "gpt-5-pro-2025-10-06")
}

/** OpenAI's GPT-6 guide documents Responses, with Chat Completions function calling for Sol/Luna only when reasoning is disabled. */
internal fun AiProviderSetting.officialOpenAiToolCallingOnRoute(model: AiModel): Boolean? {
    val id = model.id.lowercase()
    val audioChat = id in openAiAudioChatModels
    if (!audioChat && id !in openAiResponsesToolModels) return null
    val route = resolveModelGenerationRoute(model).protocol
    return when {
        isOfficialOpenAiGenerationPath(route, "/v1/responses") -> !audioChat
        // The ordinary Chat Completions route may leave reasoning enabled for Sol/Luna.
        isOfficialOpenAiGenerationPath(route, "/v1/chat/completions") -> audioChat
        else -> null
    }
}
