package io.legado.app.help.ai

internal const val CLAUDE_DEFAULT_MAX_TOKENS = 8_192
internal const val CLAUDE_MIN_THINKING_TOKENS = 1_024

internal data class AiNativeReasoning(
    val efforts: List<String> = emptyList(),
    val budgetRange: IntRange? = null,
    val canDisable: Boolean = false,
    val defaultEnabled: Boolean? = null,
    val adaptive: Boolean = false,
)

internal fun AiProviderSetting.reasoningCatalogInfo(model: AiModel): AiModelInfo? =
    model.modelInfo?.takeIf { !catalogRefreshRequired && it.endpoint == modelConfigurationKey() }

internal fun AiProviderSetting.nativeModelReasoning(modelId: String, info: AiModelInfo?): AiNativeReasoning? =
    when (type) {
        AiProviderType.CLAUDE -> claudeNativeReasoning(modelId, info)
        AiProviderType.GOOGLE -> geminiNativeReasoning(modelId, info)
        AiProviderType.OPENAI -> null
    }

// https://platform.claude.com/docs/en/build-with-claude/effort
private val claudeBaseEfforts = listOf("low", "medium", "high")
private val claudeMaxEfforts = claudeBaseEfforts + "max"
private val claudeXHighEfforts = claudeBaseEfforts + listOf("xhigh", "max")
private val claudeModernEffort = Regex("claude-(?:(?:fable|mythos)-5(?:-[12])?|opus-5(?:-5)?|sonnet-5(?:-5)?|haiku-5-5)")
private val claudeLegacyBudget = Regex("claude-(?:3-7-sonnet|(?:opus|sonnet)-4(?:-[015])?|haiku-4-5)")
private val datedModelSuffix = Regex("-\\d{8}$")

internal fun claudeNativeReasoning(modelId: String, info: AiModelInfo?): AiNativeReasoning? {
    if (info?.reasoning?.value == false) return null
    val id = modelId.lowercase().removeSuffix("-latest").replace(datedModelSuffix, "")
    val modern = claudeModernEffort.matches(id)
    val knownEfforts = when {
        modern || id in listOf("claude-opus-4-7", "claude-opus-4-8") -> claudeXHighEfforts
        id in listOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-mythos-preview") -> claudeMaxEfforts
        id == "claude-opus-4-5" -> claudeBaseEfforts
        else -> emptyList()
    }
    val adaptive = info?.supportsAdaptiveThinking?.value ?: (knownEfforts.isNotEmpty() && id != "claude-opus-4-5")
    val budget = !adaptive && (info?.supportsReasoningBudget?.value ?: claudeLegacyBudget.matches(id))
    if (!adaptive && !budget) return null
    val canDisable = info?.canDisableThinking?.value ?: when {
        id == "claude-haiku-5-5" || id == "claude-opus-5" -> true
        modern || id == "claude-mythos-preview" -> false
        else -> knownEfforts.isNotEmpty() || claudeLegacyBudget.matches(id)
    }
    return AiNativeReasoning(
        efforts = info?.effortLevels?.value?.let { info.selectableEffortLevels().filter(claudeXHighEfforts::contains) }
            ?: knownEfforts,
        budgetRange = if (budget) CLAUDE_MIN_THINKING_TOKENS until CLAUDE_DEFAULT_MAX_TOKENS else null,
        canDisable = canDisable,
        defaultEnabled = info?.defaultReasoningEnabled?.value ?: (modern || id == "claude-mythos-preview"),
        adaptive = adaptive,
    )
}

// https://aistudio.google.com/docs/thinking?generate-content=true
// https://firebase.google.com/docs/ai-logic/thinking
internal fun geminiNativeReasoning(modelId: String, info: AiModelInfo?): AiNativeReasoning? {
    if (info?.reasoning?.value == false) return null
    val id = modelId.lowercase().removePrefix("models/")
    val standard = listOf("low", "medium", "high")
    val minimal = listOf("minimal") + standard
    val declared = info?.effortLevels?.value
    val preset = when {
        id.isGeminiVariant("gemini-2.5-pro") -> AiNativeReasoning(budgetRange = 128..32_768, defaultEnabled = true)
        id.isGeminiVariant("gemini-2.5-flash-lite") ->
            AiNativeReasoning(budgetRange = 512..24_576, canDisable = true, defaultEnabled = false)
        id.isGeminiVariant("gemini-2.5-flash") ->
            AiNativeReasoning(budgetRange = 1..24_576, canDisable = true, defaultEnabled = true)
        id.isGeminiVariant("gemini-3.1-flash-lite-image") -> AiNativeReasoning(listOf("minimal", "high"))
        id.isGeminiVariant("gemini-3-pro") -> AiNativeReasoning(listOf("low", "high"))
        id.isGeminiVariant("gemini-3-flash") || id.isGeminiVariant("gemini-3.1-flash-lite") ||
            id.isGeminiVariant("gemini-3.5-flash-lite") || id.isGeminiVariant("gemini-3.6-flash") ->
            AiNativeReasoning(minimal)
        id.isGeminiVariant("gemini-4-neon") || id.isGeminiVariant("gemini-4-hydrogen") ->
            AiNativeReasoning(standard + "xhigh")
        id.isGeminiVariant("gemini-4-argon") || id.isGeminiVariant("gemini-4-carbon") ||
            id.isGeminiVariant("gemini-3.1-pro") || id.isGeminiVariant("gemini-3.5-flash") ||
            id.isGeminiVariant("gemini-3.8-flash") -> AiNativeReasoning(standard)
        else -> (minimal + "xhigh").filter { declared?.contains(it) == true }
            .takeIf(List<String>::isNotEmpty)?.let { AiNativeReasoning(it) } ?: return null
    }
    return preset.copy(
        efforts = declared?.let { preset.efforts.filter(it::contains) } ?: preset.efforts,
        canDisable = preset.canDisable && info?.canDisableThinking?.value != false,
        defaultEnabled = info?.defaultReasoningEnabled?.value ?: preset.defaultEnabled ?: true,
    )
}

private fun String.isGeminiVariant(model: String): Boolean =
    equals(model) || startsWith("$model-")
