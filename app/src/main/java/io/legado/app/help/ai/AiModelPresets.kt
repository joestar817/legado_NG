package io.legado.app.help.ai

internal data class AiModelPreset(
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val efforts: List<String>? = null,
    val reasoningProtocol: AiReasoningProtocol? = null,
    val canDisableThinking: Boolean? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
    val toolCalling: Boolean? = null,
    val effortLimit: List<String>? = null,
    val reasoningBudgetRange: IntRange? = null,
    val defaultThinkingEnabled: Boolean? = null,
    val reasoning: Boolean? = null,
)

/** These defaults describe the named provider's deployment, not models with a similar ID. */
private val deepSeekV4 = AiModelPreset(
    efforts = listOf("low", "high", "max"),
    reasoningProtocol = AiReasoningProtocol.THINKING, canDisableThinking = true,
)
private val deepSeekModels = listOf(
    "deepseek-flash", "deepseek-v4.1-flash", "deepseek-v4.1-flash-1017", "deepseek-v4-flash", "deepseek-v4-pro",
).associateWith { deepSeekV4 }
private val deepSeekFlashCatalogPreset = deepSeekV4.copy(
    inputModalities = listOf("text", "image"), outputModalities = listOf("text"), toolCalling = true,
)
private val deepSeekOfficialPresets = deepSeekModels + mapOf(
    "deepseek-flash" to deepSeekFlashCatalogPreset,
    "deepseek-v4-flash" to deepSeekFlashCatalogPreset,
    "deepseek-v4-flash-vision-exp" to deepSeekFlashCatalogPreset,
    "deepseek-v4-pro" to deepSeekV4.copy(
        inputModalities = listOf("text"), outputModalities = listOf("text"), toolCalling = true,
    ),
)
private val siliconFlowEffort = AiModelPreset(
    reasoningProtocol = AiReasoningProtocol.ENABLE_THINKING, canDisableThinking = true,
    effortLimit = listOf("high", "max"),
)
private val siliconFlowThinking = AiModelPreset(
    efforts = emptyList(), reasoningProtocol = AiReasoningProtocol.ENABLE_THINKING, canDisableThinking = true,
)
private val bailianVisionReasoning = AiModelPreset(
    efforts = listOf("low", "high", "xhigh"),
    reasoningProtocol = AiReasoningProtocol.ENABLE_THINKING, canDisableThinking = true,
    inputModalities = listOf("text", "image", "video"), outputModalities = listOf("text"), toolCalling = true,
)
private val bailianSharedPresets = mapOf(
    "qwen4-max" to bailianVisionReasoning,
    "qwen3.8-flash" to bailianVisionReasoning.copy(efforts = listOf("low", "medium", "xhigh")),
)

private val bailianThinkingSwitch = AiModelPreset(
    efforts = emptyList(), reasoningProtocol = AiReasoningProtocol.ENABLE_THINKING, canDisableThinking = true,
)
private val bailianMandatoryThinking = AiModelPreset(
    efforts = emptyList(), reasoningProtocol = AiReasoningProtocol.EFFORT, canDisableThinking = false,
)

// Exact model IDs and canonical effort values from the Model Studio Chat Completions docs.
// https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions
private val bailianChannelPresets = buildMap {
    putAll(bailianSharedPresets)
    val qwen38 = AiModelPreset(
        efforts = listOf("low", "medium", "xhigh"),
        reasoningProtocol = AiReasoningProtocol.EFFORT, canDisableThinking = true,
    )
    putReasoning(qwen38, "qwen3.8-max", "qwen3.8-max-0902", "qwen3.8-27b", "qwen3.8-omni-flash")
    put("qwen3.8-2.4t-a95b", qwen38.copy(canDisableThinking = false))

    putReasoning(
        bailianThinkingSwitch,
        "qwen3.7-max", "qwen3.7-max-2026-05-20", "qwen3.7-max-2026-06-08",
        "qwen3.7-plus", "qwen3.7-plus-2026-05-26", "qwen3.7-flash", "qwen3.7-flash-2026-07-15",
        "qwen3.6-plus", "qwen3.6-plus-2026-04-02", "qwen3.6-flash", "qwen3.6-flash-2026-04-16",
        "qwen3.6-35b-a3b", "qwen3.5-plus", "qwen3.5-plus-2026-02-15", "qwen3.5-plus-2026-04-20",
        "qwen3.5-flash", "qwen3.5-flash-2026-02-23", "qwen3.5-397b-a17b", "qwen3.5-122b-a10b",
        "qwen3.5-27b", "qwen3.5-35b-a3b", "qwen3-vl-plus", "qwen3-omni-flash",
        "antdigital/ling-3.1-flash", "kimi/kimi-k2.6", "zhipu/glm-5.1", "zhipu/glm-5",
    )
    putReasoning(
        bailianThinkingSwitch.copy(defaultThinkingEnabled = false),
        "qwen-plus", "qwen-plus-latest", "qwen-plus-2025-04-28",
        "qwen-plus-2025-07-14", "qwen-plus-2025-11-05", "qwen-plus-2025-12-01", "qwen-flash",
        "qwen-flash-2025-07-28", "kimi-k2.6", "kimi-k2.5",
        "siliconflow/deepseek-v3.2", "siliconflow/deepseek-v3.1-terminus", "vanchin/deepseek-v3.2-think",
        "vanchin/deepseek-v3.1-terminus",
    )
    putReasoning(
        bailianMandatoryThinking,
        "qwen3.7-max-preview", "qwen3.7-max-2026-05-17", "siliconflow/deepseek-r1-0528",
        "vanchin/deepseek-r1", "kimi-k2.7-code", "kimi/kimi-k2.7-code", "kimi/kimi-k2.7-code-highspeed",
        "minimax-m2.5", "minimax/minimax-m2.1", "minimax/minimax-m2.5", "minimax/minimax-m2.7",
        "deepseek-r1-distill-llama-70b", "deepseek-r1-distill-qwen-1.5b",
    )
    putReasoning(
        bailianThinkingSwitch.copy(efforts = listOf("high", "max")),
        "deepseek-v4-pro", "vanchin/deepseek-v4-pro",
    )
    putReasoning(
        bailianThinkingSwitch.copy(efforts = listOf("low", "high", "max")),
        "deepseek-v4.1-flash", "deepseek-v4-pro-0813", "deepseek-v4-flash-0731",
    )
    putReasoning(
        bailianMandatoryThinking.copy(efforts = listOf("low", "high", "max")),
        "glm-5.3", "glm-5.3-prime", "zhipu/glm-5.3", "zhipu/glm-5.3-flash", "zhipu/glm-5.3-flashx",
    )
    put("kimi-k3.1-pro", bailianThinkingSwitch.copy(efforts = listOf("low", "medium", "high", "max")))
    putReasoning(
        bailianMandatoryThinking.copy(efforts = listOf("max")),
        "kimi/kimi-k3", "kimi/kimi-k3.1-pro", "kimi/kimi-k3.1-pro-highspeed",
    )
    put(
        "minimax/minimax-m3.1-flash",
        bailianMandatoryThinking.copy(efforts = listOf("low", "medium", "high", "xhigh", "max")),
    )
    putReasoning(
        bailianMandatoryThinking.copy(efforts = listOf("low", "medium", "high")),
        "stepfun/step-3.7-flash", "stepfun/step-5-preview",
    )

    // Exact Beijing catalog inference_metadata, scoped to the current official main endpoint.
    // https://help.aliyun.com/zh/model-studio/list-models
    putModalities(
        listOf("text", "image"), listOf("text"),
        "deepseek-v4.1-flash", "deepseek-v4.1-flash-1017", "gui-plus", "gui-plus-2026-02-26",
        "kimi-k3", "kimi-k3.1-pro", "vanchin/deepseek-v4.1-flash", "vanchin/deepseek-v4.1-flash-1017",
    )
    putModalities(
        listOf("text", "image", "video"), listOf("text"),
        "kimi-k2.5", "kimi-k2.6", "kimi-k2.7-code", "kimi/kimi-k2.6", "kimi/kimi-k2.7-code",
        "kimi/kimi-k2.7-code-highspeed", "kimi/kimi-k2.8-preview", "kimi/kimi-k3", "kimi/kimi-k3.1-pro",
        "kimi/kimi-k3.1-pro-highspeed", "minimax/minimax-m3", "minimax/minimax-m3.1-flash", "qwen3-vl-plus",
        "qwen3.5-122b-a10b", "qwen3.5-27b", "qwen3.5-35b-a3b", "qwen3.5-397b-a17b",
        "qwen3.5-flash", "qwen3.5-flash-2026-02-23", "qwen3.5-plus", "qwen3.5-plus-2026-02-15",
        "qwen3.5-plus-2026-04-20", "qwen3.6-27b", "qwen3.6-35b-a3b", "qwen3.6-flash",
        "qwen3.6-flash-2026-04-16", "qwen3.6-plus", "qwen3.6-plus-2026-04-02", "qwen3.7-flash",
        "qwen3.7-flash-2026-07-15", "qwen3.7-max-2026-06-08", "qwen3.7-plus", "qwen3.7-plus-2026-05-26",
        "qwen3.8-27b", "qwen3.8-max", "qwen3.8-max-0902", "stepfun/step-3.7-flash", "stepfun/step-5-preview",
        "zhipu/glm-5.3-flash", "zhipu/glm-5.3-flashx",
    )
    putModalities(
        listOf("text", "image", "video", "audio"), listOf("text"),
        "qwen3.8-omni-flash", "xiaomi/mimo-v2.6-pro-ultraspeed",
    )
    putModalities(
        listOf("text", "image", "video", "audio"), listOf("text", "audio"),
        "qwen3-omni-flash", "qwen3.5-omni-flash", "qwen3.5-omni-flash-2026-03-15",
        "qwen3.5-omni-plus", "qwen3.5-omni-plus-2026-03-15",
    )
    // Function Calling declarations in the provider's model and direct-supply API documents.
    // https://help.aliyun.com/zh/model-studio/text-generation-model
    // https://help.aliyun.com/zh/model-studio/minimax-api-by-minimax
    updateModels(
        "deepseek-v4.1-flash-1017", "glm-5.3", "kimi-k3", "kimi-k3.1-pro", "minimax/minimax-m3",
        "minimax/minimax-m3.1-flash", "minimax/minimax-m2.5", "minimax/minimax-m2.7",
        "qwen3-coder-flash", "qwen3.5-122b-a10b", "qwen3.5-27b", "qwen3.5-35b-a3b", "qwen3.5-397b-a17b",
        "qwen3.5-flash", "qwen3.5-plus", "qwen3.6-35b-a3b", "qwen3.6-flash", "qwen3.6-plus",
        "qwen3.7-flash", "qwen3.7-max", "qwen3.7-plus", "qwen3.8-max", "qwen3.8-omni-flash",
        "qwen-max", "qwen-plus", "qwen-flash", "stepfun/step-5-preview", "xiaomi/mimo-v2.6-pro-ultraspeed",
    ) { it.copy(toolCalling = true) }
    updateModels(
        "deepseek-r1-distill-llama-70b", "deepseek-r1-distill-qwen-1.5b",
        "qwen-flash-character", "qwen-long", "qwen-mt-flash", "qwen-mt-lite", "qwen-mt-plus", "qwen-plus-character",
    ) { it.copy(toolCalling = false) }
    updateModels(
        "deepseek-v4.1-flash-1017", "glm-5", "glm-5.1", "glm-5.2", "kimi-k3", "kimi/kimi-k2.8-preview",
        "qwen3.6-27b", "vanchin/deepseek-v4.1-flash", "vanchin/deepseek-v4.1-flash-1017", "zhipu/glm-5.2",
    ) { it.copy(reasoning = true) }
    // M3's provider API supports adaptive thinking but no named reasoning_effort levels.
    updateModels("minimax/minimax-m3") { it.copy(reasoning = true, effortLimit = emptyList()) }
}

private fun MutableMap<String, AiModelPreset>.putReasoning(preset: AiModelPreset, vararg modelIds: String) {
    modelIds.forEach { put(it, preset) }
}

private fun MutableMap<String, AiModelPreset>.putModalities(
    input: List<String>,
    output: List<String>,
    vararg modelIds: String,
) {
    updateModels(*modelIds) { it.copy(inputModalities = input, outputModalities = output) }
}

private fun MutableMap<String, AiModelPreset>.updateModels(
    vararg modelIds: String,
    update: (AiModelPreset) -> AiModelPreset,
) {
    modelIds.forEach { id -> put(id, update(this[id] ?: AiModelPreset())) }
}

private val siliconFlowPresets = buildMap {
    putReasoning(siliconFlowEffort, "pro/deepseek-ai/deepseek-v4", "pro/zai-org/glm-5.2", "deepseek-ai/deepseek-v4-flash")
    put("pro/deepseek-ai/deepseek-v3.2", siliconFlowThinking.copy(reasoningBudgetRange = 128..32_768))
    put("deepseek-ai/deepseek-v4.1-flash", siliconFlowThinking)

    // functionCallSupport and visual input from the official China model center's exact entries.
    // https://www.siliconflow.cn/models
    updateModels(
        "bytedance-seed/seed-oss-36b-instruct", "deepseek-ai/deepseek-r1", "deepseek-ai/deepseek-v3",
        "deepseek-ai/deepseek-v3.1-terminus", "deepseek-ai/deepseek-v3.2", "deepseek-ai/deepseek-v4-flash",
        "deepseek-ai/deepseek-v4-pro", "deepseek-ai/deepseek-v4.1-flash", "inclusionai/ling-flash-2.0",
        "meituan-longcat/longcat-2.0", "moonshotai/kimi-k2.7-code", "pro/deepseek-ai/deepseek-r1",
        "pro/deepseek-ai/deepseek-v3", "pro/deepseek-ai/deepseek-v3.1-terminus", "pro/deepseek-ai/deepseek-v3.2",
        "pro/moonshotai/kimi-k2.6", "pro/qwen/qwen2.5-7b-instruct", "qwen/qwen2.5-14b-instruct",
        "qwen/qwen2.5-32b-instruct", "qwen/qwen2.5-72b-instruct", "qwen/qwen2.5-72b-instruct-128k",
        "qwen/qwen2.5-7b-instruct", "qwen/qwen3-14b", "qwen/qwen3-30b-a3b-instruct-2507", "qwen/qwen3-32b",
        "qwen/qwen3-8b", "qwen/qwen3-coder-30b-a3b-instruct", "qwen/qwen3-omni-30b-a3b-captioner",
        "qwen/qwen3-omni-30b-a3b-instruct", "qwen/qwen3-omni-30b-a3b-thinking",
        "qwen/qwen3-vl-30b-a3b-instruct", "qwen/qwen3-vl-32b-instruct", "qwen/qwen3-vl-32b-thinking",
        "qwen/qwen3-vl-8b-instruct", "qwen/qwen3-vl-8b-thinking",
        "qwen/qwen3.5-27b", "qwen/qwen3.5-35b-a3b", "qwen/qwen3.5-4b", "qwen/qwen3.5-9b",
        "qwen/qwen3.6-27b", "qwen/qwen3.6-35b-a3b", "qwen/qwen3.8-27b", "tencent/hy4-preview",
        "thudm/glm-4-32b-0414", "thudm/glm-4-9b-0414", "thudm/glm-z1-9b-0414",
        "xingchenagi/xing4.0-29b", "zai-org/glm-4.5-air", "zai-org/glm-5.2",
        "zai-org/glm-5.3", "zai-org/glm-5.3-flash",
    ) { it.copy(toolCalling = true) }
    updateModels("deepseek-ai/deepseek-r1-0528-qwen3-8b", "tencent/hunyuan-mt-7b") { it.copy(toolCalling = false) }
    putModalities(listOf("text"), listOf("text"), "deepseek-ai/deepseek-v4-flash", "deepseek-ai/deepseek-v4-pro")
    putModalities(
        listOf("text", "image"), listOf("text"),
        "deepseek-ai/deepseek-v4.1-flash", "zai-org/glm-5.3-flash",
        "qwen/qwen3-vl-30b-a3b-instruct", "qwen/qwen3-vl-32b-instruct", "qwen/qwen3-vl-32b-thinking",
        "qwen/qwen3-vl-8b-instruct", "qwen/qwen3-vl-8b-thinking", "qwen/qwen3.6-35b-a3b",
    )
    // Video input is listed on the provider's model pages for these exact deployments.
    putModalities(
        listOf("text", "image", "video"), listOf("text"),
        "moonshotai/kimi-k2.7-code", "pro/moonshotai/kimi-k2.6",
        "qwen/qwen3.5-27b", "qwen/qwen3.5-35b-a3b", "qwen/qwen3.5-4b", "qwen/qwen3.5-9b",
        "qwen/qwen3.6-27b", "qwen/qwen3.8-27b",
    )
    updateModels(
        "deepseek-ai/deepseek-r1", "deepseek-ai/deepseek-r1-0528-qwen3-8b", "deepseek-ai/deepseek-v3.2",
        "deepseek-ai/deepseek-v4-pro", "meituan-longcat/longcat-2.0", "moonshotai/kimi-k2.7-code",
        "pro/deepseek-ai/deepseek-r1", "pro/moonshotai/kimi-k2.6",
        "qwen/qwen3-8b", "qwen/qwen3-omni-30b-a3b-thinking", "qwen/qwen3-vl-32b-thinking", "qwen/qwen3-vl-8b-thinking",
        "qwen/qwen3.5-27b", "qwen/qwen3.5-35b-a3b", "qwen/qwen3.5-4b", "qwen/qwen3.5-9b",
        "qwen/qwen3.6-27b", "qwen/qwen3.6-35b-a3b", "qwen/qwen3.8-27b", "tencent/hy4-preview",
        "thudm/glm-z1-9b-0414", "xingchenagi/xing4.0-29b", "zai-org/glm-4.5-air", "zai-org/glm-5.2",
        "zai-org/glm-5.3", "zai-org/glm-5.3-flash",
    ) { it.copy(reasoning = true) }
}

private val miMoV26 = AiModelPreset(
    1_048_576, 131_072, reasoningProtocol = AiReasoningProtocol.THINKING, canDisableThinking = true,
    inputModalities = listOf("text", "image", "audio", "video"), outputModalities = listOf("text"),
    toolCalling = true, effortLimit = emptyList(),
)

// https://platform.kimi.com/docs/guide/kimi-k3-quickstart and /docs/guide/kimi-k3-1-pro-quickstart
private val kimiK3Preset = AiModelPreset(
    efforts = listOf("low", "high", "max"), reasoningProtocol = AiReasoningProtocol.EFFORT,
    canDisableThinking = false, inputModalities = listOf("text", "image", "video"),
    outputModalities = listOf("text"), toolCalling = true,
)

// https://docs.bigmodel.cn/cn/guide/start/concept-param
private val glm53Preset = AiModelPreset(
    efforts = listOf("low", "high", "max"), reasoningProtocol = AiReasoningProtocol.THINKING,
    canDisableThinking = false,
)
private val glm53VisionPreset = glm53Preset.copy(
    inputModalities = listOf("text", "image", "video", "file"),
    outputModalities = listOf("text"), toolCalling = true,
)

// https://aistudio.google.com/docs/models lists the normal synchronous output limit.
private val geminiSynchronousPresets = listOf(
    "gemini-4-argon", "gemini-4-carbon", "gemini-4-hydrogen", "gemini-4-neon",
    "gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.1-pro-preview",
    "gemini-3.1-flash-lite", "gemini-3-flash-preview", "gemini-3-pro-preview",
    "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite",
).associateWith {
    AiModelPreset(1_048_576, 65_536, outputModalities = listOf("text"), toolCalling = true)
}

private val modelPresets = mapOf(
    "deepseek" to deepSeekOfficialPresets,
    "openai" to documentedOpenAiModelPresets(),
    // These model pages document function calling on Responses, the route used by ChatGPT plans.
    // https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
    AiDefaultProviders.CHATGPT_ID to listOf(
        "gpt-6.2-sol", "gpt-6.2-astra",
        "gpt-6.1-terra", "gpt-6.1-sol",
        "gpt-6-astra", "gpt-6-sol", "gpt-6-luna",
        "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna",
    ).associateWith { AiModelPreset(toolCalling = true) },
    "gemini" to geminiSynchronousPresets,
    "aliyun_bailian" to bailianChannelPresets,
    "xiaomi_mimo" to listOf(
        "mimo-v2.6-flash", "mimo-v2.6-pro", "mimo-v2.6-pro-ultraspeed",
    ).associateWith { miMoV26 },
    "siliconflow" to siliconFlowPresets,
    "moonshot" to mapOf(
        "kimi-k3" to kimiK3Preset,
        "kimi-k3.1-pro" to kimiK3Preset,
    ),
    "zhipu" to mapOf(
        "glm-5.3" to glm53Preset,
        "glm-5.3-flash" to glm53VisionPreset,
        "glm-5.3-flashx" to glm53VisionPreset,
        "glm-5.2" to AiModelPreset(
            efforts = listOf("high", "max"), reasoningProtocol = AiReasoningProtocol.THINKING,
            canDisableThinking = true,
        ),
    ),
)

internal fun modelPreset(officialProvider: String?, modelId: String): AiModelPreset? =
    officialProvider?.let(modelPresets::get)?.get(modelId.lowercase())

// Share model strengths only. Capabilities such as request fields and limits belong to a channel.
private val sharedEffortsByModel = (deepSeekModels + modelPresets.getValue("openai") +
    bailianSharedPresets + modelPresets.getValue("moonshot")).mapValues { it.value.efforts }

private val sharedModelAliases = mapOf(
    "deepseek-ai/deepseek-v4-flash" to "deepseek-v4-flash",
    "deepseek-ai/deepseek-v4.1-flash" to "deepseek-v4.1-flash",
    "deepseek/deepseek-v4-pro" to "deepseek-v4-pro",
    "deepseek/deepseek-v4-flash" to "deepseek-v4-flash",
)

internal fun sharedModelEfforts(modelId: String): List<String>? {
    val id = modelId.lowercase()
    return sharedEffortsByModel[sharedModelAliases[id] ?: id]
}

internal fun AiProviderSetting.modelPresetProviderId(): String? {
    val official = officialModelProviderId() ?: return null
    if (official == "deepseek") return official.takeIf { officialDeepSeekModelConfigEndpoint() != null }
    if (type == AiProviderType.OPENAI && chatCompletionsPath.trimEnd('/') != "/chat/completions") return null
    return official
}

internal fun AiModel.effectiveModelInfo(
    source: String,
    officialProvider: String?,
    catalogCurrent: Boolean = true,
): AiModelInfo {
    val catalog = modelInfo?.takeIf { catalogCurrent && it.endpoint == source } ?: AiModelInfo(source)
    val preset = modelPreset(officialProvider, id)
    val limits = providerModelTokenLimits(officialProvider, id)
    if (preset == null && limits == null) return catalog
    return catalog.copy(
        contextWindow = catalog.contextWindow ?: (limits?.contextWindow ?: preset?.contextWindow).catalogValue(),
        maxOutputTokens = catalog.maxOutputTokens ?: (limits?.maxOutputTokens ?: preset?.maxOutputTokens).catalogValue(),
        inputModalities = catalog.inputModalities ?: preset?.inputModalities.catalogValue(),
        outputModalities = catalog.outputModalities ?: preset?.outputModalities.catalogValue(),
        effortLevels = catalog.effortLevels ?: (preset?.effortLimit ?: preset?.efforts).catalogValue(),
        toolCalling = catalog.toolCalling ?: preset?.toolCalling.catalogValue(),
        reasoning = catalog.reasoning ?:
            (preset?.reasoning ?: true.takeIf { preset?.reasoningProtocol != null }).catalogValue(),
        canDisableThinking = catalog.canDisableThinking ?: preset?.canDisableThinking.catalogValue(),
    )
}

internal fun AiProviderSetting.effectiveModelInfo(model: AiModel): AiModelInfo =
    model.effectiveModelInfo(modelConfigurationKey(), modelPresetProviderId(), !catalogRefreshRequired)
