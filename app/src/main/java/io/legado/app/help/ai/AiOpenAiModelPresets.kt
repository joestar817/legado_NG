package io.legado.app.help.ai

// The model pages linked from https://developers.openai.com/api/docs/models/all
// declare modalities and function calling. Conditional capability depends on the request API.
private val textOnlyOpenAiModels = setOf("o3-mini", "o3-mini-2025-01-31")
private val openAiToolCallingDependsOnRoute = setOf(
    "gpt-6.2-astra", "gpt-6.2-sol", "gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna", "gpt-6-sol",
    "chatgpt-4o-latest", "o3-deep-research", "o3-deep-research-2025-06-26",
    "o4-mini-deep-research", "o4-mini-deep-research-2025-06-26",
)

internal fun documentedOpenAiModelPresets(): Map<String, AiModelPreset> = buildMap {
    for (id in documentedOpenAiModelIds()) {
        put(id, AiModelPreset(
            inputModalities = if (id in textOnlyOpenAiModels) listOf("text") else listOf("text", "image"),
            outputModalities = listOf("text"),
            toolCalling = true.takeUnless { id in openAiToolCallingDependsOnRoute },
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
