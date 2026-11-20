package io.legado.app.help.ai

internal data class AiModelTokenLimits(val contextWindow: Int?, val maxOutputTokens: Int?)

internal fun providerModelTokenLimits(providerId: String?, modelId: String): AiModelTokenLimits? =
    when (providerId) {
        "siliconflow" -> siliconFlowLimits
        "aliyun_bailian" -> bailianLimits
        else -> null
    }?.get(modelId.lowercase()) ?: documentedOfficialModelLimits(providerId, modelId)

internal fun MutableMap<String, AiModelTokenLimits>.putLimits(
    contextWindow: Int?,
    maxOutputTokens: Int?,
    vararg modelIds: String,
) {
    val limits = AiModelTokenLimits(contextWindow, maxOutputTokens)
    modelIds.forEach { put(it.lowercase(), limits) }
}

// China deployment specs from https://www.siliconflow.cn/models (2026-11-20).
// DeepSeek-V4-Flash and Hy4-preview publish output references in their descriptions.
private val siliconFlowLimits = buildMap {
    // Use Qwen's published 8,192 generation length. The LoRA IDs use their base model's default window.
    // https://huggingface.co/Qwen/Qwen2.5-32B-Instruct
    putLimits(
        32_768, 8_192,
        "Pro/Qwen/Qwen2.5-7B-Instruct",
        "Qwen/Qwen2.5-14B-Instruct",
        "Qwen/Qwen2.5-32B-Instruct",
        "Qwen/Qwen2.5-72B-Instruct",
        "Qwen/Qwen2.5-7B-Instruct",
        "LoRA/Qwen/Qwen2.5-14B-Instruct",
        "LoRA/Qwen/Qwen2.5-32B-Instruct",
        "LoRA/Qwen/Qwen2.5-72B-Instruct",
        "LoRA/Qwen/Qwen2.5-7B-Instruct",
    )
    putLimits(
        32_768, null,
        "tencent/Hunyuan-MT-7B",
        "THUDM/GLM-4-32B-0414",
        "THUDM/GLM-4-9B-0414",
    )
    putLimits(
        65_536, null,
        "Qwen/Qwen3-Omni-30B-A3B-Captioner",
        "Qwen/Qwen3-Omni-30B-A3B-Instruct",
        "Qwen/Qwen3-Omni-30B-A3B-Thinking",
    )
    putLimits(
        131_072, null,
        "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B",
        "inclusionAI/Ling-flash-2.0",
        "THUDM/GLM-Z1-9B-0414",
        "zai-org/GLM-4.5-Air",
    )
    putLimits(
        131_072, 8_192,
        "Qwen/Qwen2.5-72B-Instruct-128K",
    )
    // Qwen's recommended output lengths provide references when the provider omits the field.
    putLimits(
        131_072, 32_768,
        "Qwen/Qwen3-14B",
        "Qwen/Qwen3-32B",
        "Qwen/Qwen3-8B",
    )
    putLimits(
        163_840, null,
        "deepseek-ai/DeepSeek-R1",
        "deepseek-ai/DeepSeek-V3",
        "deepseek-ai/DeepSeek-V3.1-Terminus",
        "deepseek-ai/DeepSeek-V3.2",
        "Pro/deepseek-ai/DeepSeek-R1",
        "Pro/deepseek-ai/DeepSeek-V3",
        "Pro/deepseek-ai/DeepSeek-V3.1-Terminus",
        "Pro/deepseek-ai/DeepSeek-V3.2",
    )
    putLimits(
        262_144, null,
        "ByteDance-Seed/Seed-OSS-36B-Instruct",
        "moonshotai/Kimi-K2.7-Code",
        "Pro/moonshotai/Kimi-K2.6",
        "Qwen/Qwen3-VL-30B-A3B-Instruct",
        "Qwen/Qwen3-VL-32B-Instruct",
        "Qwen/Qwen3-VL-32B-Thinking",
        "Qwen/Qwen3-VL-8B-Instruct",
        "Qwen/Qwen3-VL-8B-Thinking",
        "XingChenAGI/Xing4.0-29B",
    )
    putLimits(
        262_144, 16_384,
        "Qwen/Qwen3-30B-A3B-Instruct-2507",
    )
    putLimits(
        262_144, 32_768,
        "Qwen/Qwen3.5-27B",
        "Qwen/Qwen3.5-35B-A3B",
        "Qwen/Qwen3.5-4B",
        "Qwen/Qwen3.5-9B",
        "Qwen/Qwen3.6-27B",
        "Qwen/Qwen3.6-35B-A3B",
    )
    putLimits(
        262_144, 65_536,
        "Qwen/Qwen3-Coder-30B-A3B-Instruct",
    )
    putLimits(
        262_144, 131_072,
        "Qwen/Qwen3.8-27B",
    )
    putLimits(
        1_048_576, null,
        "deepseek-ai/DeepSeek-V4-Pro",
        "deepseek-ai/DeepSeek-V4.1-Flash",
        "meituan-longcat/LongCat-2.0",
        "zai-org/GLM-5.2",
        "zai-org/GLM-5.3",
        "zai-org/GLM-5.3-Flash",
    )
    putLimits(
        1_048_576, 65_536,
        "tencent/Hy4-preview",
    )
    putLimits(
        1_048_576, 393_216,
        "deepseek-ai/DeepSeek-V4-Flash",
    )
}

// Beijing deployment specs from the native Model Studio catalog (2026-11-20).
// https://help.aliyun.com/zh/model-studio/list-models
// Use the larger advertised output when the provider lists separate thinking and non-thinking limits.
private val bailianLimits = buildMap {
    // Historical specs for IDs still present in the compatibility catalog. Retired 2025-05-08.
    // https://web.archive.org/web/20250302065423/https://help.aliyun.com/zh/model-studio/getting-started/models
    // https://www.aliyun.com/notice/117139
    putLimits(
        7_500, 1_500,
        "qwen-7b-chat",
    )
    putLimits(
        8_000, 2_000,
        "qwen-1.8b-chat",
        "qwen-14b-chat",
    )
    putLimits(
        32_000, 2_000,
        "qwen-1.8b-longcontext-chat",
        "qwen-72b-chat",
    )
    // These exact open-weight IDs are absent from the native catalog. Use the author configs.
    putLimits(
        4_096, null,
        "qwen2.5-math-1.5b-instruct",
    )
    putLimits(
        32_768, null,
        "qwen1.5-0.5b-chat",
        "qwen1.5-1.8b-chat",
        "qwen1.5-7b-chat",
        "qwen1.5-14b-chat",
        "qwen1.5-32b-chat",
        "qwen1.5-72b-chat",
        "qwen1.5-110b-chat",
        "qwen2-0.5b-instruct",
        "qwen2-1.5b-instruct",
        "qwen2-7b-instruct",
        "qwen2-57b-a14b-instruct",
    )
    putLimits(
        8_192, 4_096,
        "qwen-flash-character",
        "qwen-flash-character-2026-02-26",
        "tongyi-intent-detect-v3",
    )
    putLimits(
        8_192, 8_192,
        "vanchin/deepseek-ocr",
    )
    putLimits(
        16_384, 8_192,
        "qwen-mt-flash",
        "qwen-mt-lite",
        "qwen-mt-plus",
    )
    putLimits(
        32_768, 4_096,
        "qwen-plus-character",
        "tongyi-xiaomi-analysis-flash",
        "tongyi-xiaomi-analysis-pro",
    )
    putLimits(
        32_768, 8_192,
        "qwen-max",
        "qwen2.5-0.5b-instruct",
        "qwen2.5-1.5b-instruct",
    )
    putLimits(
        32_768, 16_384,
        "deepseek-r1-distill-llama-70b",
        "deepseek-r1-distill-qwen-1.5b",
    )
    putLimits(
        32_768, null,
        "qwen3.7-text-rerank",
    )
    putLimits(
        38_192, 8_192,
        "qwen-vl-ocr",
        "qwen-vl-ocr-2025-11-20",
    )
    // The 3.0 model pages list the realtime session window as 40,960.
    // https://help.aliyun.com/zh/model-studio/qwen-audio-3-0-realtime-plus
    putLimits(
        40_960, 8_192,
        "qwen-audio-3.0-realtime-flash",
        "qwen-audio-3.0-realtime-plus",
    )
    putLimits(
        53_248, 4_096,
        "qwen3-livetranslate-flash",
        "qwen3-livetranslate-flash-2025-12-01",
        "qwen3.5-livetranslate-flash-realtime",
        "qwen3.5-livetranslate-flash-realtime-2026-05-19",
        "qwen3.8-livetranslate-flash-realtime",
    )
    putLimits(
        65_536, 16_384,
        "qwen3-omni-flash",
        "qwen3-omni-flash-realtime-2025-09-15",
        "qwen3.5-ocr",
    )
    putLimits(
        131_072, null,
        "qwen3.7-text-embedding",
        "qwen3.7-text-embedding-flash",
    )
    putLimits(
        131_072, 8_192,
        "qwen-plus-2025-01-25",
    )
    putLimits(
        131_072, 16_384,
        "qwen-plus-2025-04-28",
        "qwen-plus-2025-07-14",
        "vanchin/deepseek-v3",
    )
    putLimits(
        131_072, 32_768,
        "vanchin/deepseek-r1",
    )
    putLimits(
        131_072, 65_536,
        "vanchin/deepseek-v3.1-terminus",
        "vanchin/deepseek-v3.2-think",
    )
    putLimits(
        163_840, 32_768,
        "siliconflow/deepseek-r1-0528",
    )
    putLimits(
        163_840, 65_536,
        "siliconflow/deepseek-v3.1-terminus",
        "siliconflow/deepseek-v3.2",
        "unisound/unisound-u2",
    )
    putLimits(
        163_840, 163_840,
        "siliconflow/deepseek-v3-0324",
    )
    putLimits(
        202_745, 131_072,
        "glm-5.1",
    )
    putLimits(
        202_752, 16_384,
        "glm-5",
    )
    putLimits(
        204_800, 32_768,
        "MiniMax-M2.5",
    )
    putLimits(
        204_800, 131_072,
        "MiniMax/MiniMax-M2.1",
        "MiniMax/MiniMax-M2.5",
        "MiniMax/MiniMax-M2.7",
        "ZHIPU/GLM-5",
        "ZHIPU/GLM-5.1",
    )
    putLimits(
        256_000, 32_768,
        "gui-plus",
        "gui-plus-2026-02-26",
    )
    putLimits(
        262_144, 8_192,
        "qwen-doc-turbo",
    )
    putLimits(
        262_144, 16_384,
        "kimi-k2.5",
        "kimi-k2.6",
        "kimi-k2.7-code",
        "qwen-audio-3.1-realtime-flash",
        "qwen-audio-3.1-realtime-plus",
    )
    putLimits(
        262_144, 32_768,
        "qwen3-vl-plus",
    )
    putLimits(
        262_144, 65_536,
        "qwen3.5-122b-a10b",
        "qwen3.5-27b",
        "qwen3.5-35b-a3b",
        "qwen3.5-397b-a17b",
        "qwen3.5-omni-flash",
        "qwen3.5-omni-flash-2026-03-15",
        "qwen3.5-omni-flash-realtime",
        "qwen3.5-omni-flash-realtime-2026-03-15",
        "qwen3.5-omni-plus",
        "qwen3.5-omni-plus-2026-03-15",
        "qwen3.5-omni-plus-realtime",
        "qwen3.5-omni-plus-realtime-2026-03-15",
        "qwen3.6-27b",
        "qwen3.6-35b-a3b",
    )
    putLimits(
        262_144, 262_144,
        "kimi/kimi-k2.6",
        "kimi/kimi-k2.7-code",
        "kimi/kimi-k2.7-code-highspeed",
        "stepfun/step-3.7-flash",
    )
    putLimits(
        1_000_000, null,
        "MiniMax/MiniMax-M3",
    )
    putLimits(
        1_000_000, 32_768,
        "qwen-deep-research",
        "qwen-deep-research-2025-12-15",
        "qwen-flash",
        "qwen-flash-2025-07-28",
        "qwen-plus",
        "qwen-plus-2025-12-01",
        "qwen-plus-latest",
    )
    putLimits(
        1_000_000, 65_536,
        "qwen3-coder-flash",
        "qwen3-coder-flash-2025-07-28",
        "qwen3.5-flash",
        "qwen3.5-flash-2026-02-23",
        "qwen3.5-plus",
        "qwen3.5-plus-2026-02-15",
        "qwen3.5-plus-2026-04-20",
        "qwen3.6-flash",
        "qwen3.6-flash-2026-04-16",
        "qwen3.6-plus",
        "qwen3.6-plus-2026-04-02",
    )
    putLimits(
        1_000_000, 131_072,
        "glm-5.2",
        "glm-5.2-fast-preview",
        "glm-5.3",
        "glm-5.3-prime",
        "qwen3.7-flash",
        "qwen3.7-flash-2026-07-15",
        "qwen3.7-max",
        "qwen3.7-max-2026-05-17",
        "qwen3.7-max-2026-05-20",
        "qwen3.7-max-2026-06-08",
        "qwen3.7-max-preview",
        "qwen3.7-plus",
        "qwen3.7-plus-2026-05-26",
        "qwen3.8-2.4t-a95b",
        "qwen3.8-27b",
        "qwen3.8-flash",
        "qwen3.8-max",
        "qwen3.8-max-0902",
        "qwen3.8-omni-flash",
        "ZHIPU/GLM-5.2",
        "ZHIPU/GLM-5.3",
        "ZHIPU/GLM-5.3-Flash",
    )
    putLimits(
        1_000_000, 262_144,
        "qwen4-max",
    )
    putLimits(
        1_000_000, 800_000,
        "qwen-mt-uni",
    )
    putLimits(
        1_000_000, 393_216,
        "deepseek-v4-flash-0731",
        "deepseek-v4-pro",
        "deepseek-v4-pro-0813",
        "deepseek-v4.1-flash",
        "deepseek-v4.1-flash-1017",
        "vanchin/deepseek-v4-pro",
        "vanchin/deepseek-v4-pro-0813",
    )
    putLimits(
        1_000_000, 1_048_576,
        "kimi-k3",
        "kimi-k3.1-pro",
        "kimi/kimi-k3",
    )
    putLimits(
        1_048_576, null,
        "MiniMax/MiniMax-M3.1-Flash",
    )
    putLimits(
        1_048_576, 131_072,
        "xiaomi/mimo-v2.6-pro-ultraspeed",
        "ZHIPU/GLM-5.3-FlashX",
    )
    putLimits(
        1_048_576, 262_144,
        "antdigital/Ling-3.1-flash",
    )
    putLimits(
        1_048_576, 393_216,
        "vanchin/deepseek-v4.1-flash",
        "vanchin/deepseek-v4.1-flash-1017",
    )
    putLimits(
        1_048_576, 1_048_576,
        "kimi/kimi-k2.8-preview",
        "kimi/kimi-k3.1-pro",
        "kimi/kimi-k3.1-pro-highspeed",
        "stepfun/step-5-preview",
    )
    putLimits(
        10_000_000, 8_192,
        "qwen-long",
    )
}
