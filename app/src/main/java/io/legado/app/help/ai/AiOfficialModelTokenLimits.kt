package io.legado.app.help.ai

internal fun documentedOfficialModelLimits(providerId: String?, modelId: String): AiModelTokenLimits? =
    when (providerId) {
        "openai" -> openAiTokenLimits
        "deepseek" -> deepSeekTokenLimits
        "volcengine" -> arkTokenLimits
        "zhipu" -> bigModelTokenLimits
        "moonshot" -> kimiTokenLimits
        else -> null
    }?.get(modelId.lowercase())

internal fun documentedOpenAiModelIds(): Set<String> = openAiTokenLimits.keys

// https://developers.openai.com/api/docs/models/all and the linked model pages.
private val openAiTokenLimits = buildMap {
    putLimits(1_050_000, 128_000,
        "gpt-6.2-astra", "gpt-6.2-sol", "gpt-6.1-terra", "gpt-6.1-sol",
        "gpt-6-luna", "gpt-6-astra", "gpt-6-sol",
        "gpt-5.6", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna",
        "gpt-5.5", "gpt-5.5-2026-04-23", "gpt-5.5-pro", "gpt-5.5-pro-2026-04-23",
        "gpt-5.4", "gpt-5.4-2026-03-05", "gpt-5.4-pro", "gpt-5.4-pro-2026-03-05",
    )
    putLimits(400_000, 128_000,
        "gpt-5.4-mini", "gpt-5.4-mini-2026-03-17", "gpt-5.4-nano", "gpt-5.4-nano-2026-03-17",
        "gpt-5.3-codex", "gpt-5.2", "gpt-5.2-2025-12-11", "gpt-5.2-pro", "gpt-5.2-pro-2025-12-11",
        "gpt-5.2-codex", "gpt-5.1", "gpt-5.1-2025-11-13", "gpt-5", "gpt-5-2025-08-07",
        "gpt-5-mini", "gpt-5-mini-2025-08-07", "gpt-5-nano", "gpt-5-nano-2025-08-07",
        "gpt-5.1-codex", "gpt-5.1-codex-max", "gpt-5.1-codex-mini", "gpt-5-codex", "chat-latest",
    )
    putLimits(400_000, 272_000, "gpt-5-pro", "gpt-5-pro-2025-10-06")
    putLimits(1_047_576, 32_768,
        "gpt-4.1", "gpt-4.1-2025-04-14", "gpt-4.1-mini", "gpt-4.1-mini-2025-04-14",
        "gpt-4.1-nano", "gpt-4.1-nano-2025-04-14",
    )
    putLimits(128_000, 16_384,
        "gpt-4o", "gpt-4o-mini", "gpt-4o-mini-2024-07-18", "gpt-5.3-chat-latest",
        "gpt-5.2-chat-latest", "gpt-5.1-chat-latest", "gpt-5-chat-latest", "chatgpt-4o-latest",
    )
    putLimits(200_000, 100_000,
        "o3", "o3-2025-04-16", "o3-pro", "o3-pro-2025-06-10", "o4-mini", "o4-mini-2025-04-16",
        "o1", "o1-2024-12-17", "o1-pro", "o1-pro-2025-03-19", "o3-mini", "o3-mini-2025-01-31",
        "o3-deep-research", "o3-deep-research-2025-06-26",
        "o4-mini-deep-research", "o4-mini-deep-research-2025-06-26", "codex-mini-latest",
    )
}

// https://api-docs.deepseek.com/zh-cn/api/list-models/ and /zh-cn/quick_start/pricing/
private val deepSeekTokenLimits = buildMap {
    putLimits(1_048_576, 393_216,
        "deepseek-flash", "deepseek-v4-pro", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp",
    )
}

// https://console.volcengine.com/ark/region:cn-beijing/docs/model-list
// The official Models API example gives the exact token conversion for this table's 1024K and 256K.
private val arkTokenLimits = buildMap {
    putLimits(1_048_576, 262_144, "doubao-seed-evolving", "doubao-seed-2-1-pro-260915",
        "doubao-seed-2-1-lite-260915")
    putLimits(262_144, 262_144, "doubao-seed-2-1-turbo-260628", "doubao-seed-2-1-pro-260628")
    putLimits(262_144, 131_072,
        "doubao-seed-2-0-lite-260428", "doubao-seed-2-0-mini-260428", "doubao-seed-2-0-pro-260215",
        "doubao-seed-2-0-lite-260215", "doubao-seed-2-0-mini-260215", "doubao-seed-2-0-code-preview-260215",
    )
    putLimits(131_072, 32_768, "doubao-seed-character-260628", "doubao-seed-character-251128")
    putLimits(4_096, 3_072, "doubao-seed-translation-250915")
    putLimits(1_048_576, 131_072, "glm-5-3-flash-260828", "glm-5-2-260617")
    putLimits(1_048_576, 1_048_576, "kimi-k3-1-pro-261024")
    putLimits(1_048_576, 393_216,
        "deepseek-v4-1-flash-261017", "deepseek-v4-1-flash-260910", "deepseek-v4-1-flash-economy-261017",
        "deepseek-v4-pro-ga-260813", "deepseek-v4-flash-ga-260731", "deepseek-v4-pro-260425",
    )
}

// https://docs.bigmodel.cn/cn/guide/start/concept-param gives integer max_tokens values.
private val bigModelTokenLimits = buildMap {
    putLimits(null, 131_072,
        "glm-5.3", "glm-5.3-flash", "glm-5.3-flashx", "glm-5.2", "glm-5.1",
        "glm-5v-turbo", "glm-5", "glm-5-turbo", "glm-4.7", "glm-4.6",
    )
    putLimits(null, 32_768, "glm-4.6v", "glm-4.6v-flash", "glm-4.6v-flashx")
    putLimits(null, 98_304, "glm-4.5", "glm-4.5-air", "glm-4.5-flash", "glm-4.5-x")
    putLimits(null, 16_384, "glm-4.5v", "glm-4.1v-thinking-flashx", "glm-4-air-250414")
    putLimits(null, 4_095, "glm-4-plus", "glm-4-air", "glm-4-airx", "glm-4-flash", "glm-4-flashx")
    putLimits(null, 8_192, "glm-4v-plus-0111")
    putLimits(null, 1_024, "glm-4v-flash")
}

// https://platform.kimi.com/docs/guide/kimi-k3-quickstart and /docs/guide/kimi-k3-1-pro-quickstart
private val kimiTokenLimits = buildMap {
    putLimits(null, 1_048_576, "kimi-k3", "kimi-k3.1-pro")
}
