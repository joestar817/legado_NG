package io.legado.app.help.ai

import com.google.gson.annotations.SerializedName

enum class AiModelCapabilityField {
    @SerializedName("type") TYPE,
    @SerializedName("inputModalities") INPUT_MODALITIES,
    @SerializedName("outputModalities") OUTPUT_MODALITIES,
    @SerializedName("toolCalling") TOOL_CALLING,
    @SerializedName("reasoning") REASONING,
}

enum class AiModelInterfacePreference {
    @SerializedName("auto") PROVIDER_DEFAULT,
    @SerializedName("chatCompletions") CHAT_COMPLETIONS,
    @SerializedName("responses") RESPONSES,
}

data class AiModelSettings(
    @SerializedName("catalogEndpoint") val catalogEndpoint: String,
    @SerializedName("capabilitiesEdited") val capabilitiesEdited: Boolean = false,
    @SerializedName("followModelContext") val followModelContext: Boolean = false,
    @SerializedName("contextWindowTokens") val contextWindowTokens: Int? = null,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = null,
    @SerializedName("reasoningEffort") val reasoningEffort: String? = null,
    @SerializedName("referenceContextWindowTokens") val referenceContextWindowTokens: Int? = null,
    @SerializedName("referenceMaxOutputTokens") val referenceMaxOutputTokens: Int? = null,
    @SerializedName("enabledReasoningEfforts") val enabledReasoningEfforts: List<String>? = null,
    @SerializedName("capabilityOverrides") val capabilityOverrides: Set<AiModelCapabilityField>? = null,
    @SerializedName("interfacePreference") val interfacePreference: AiModelInterfacePreference? = null,
    @SerializedName("nativeWebSearch") val nativeWebSearch: Boolean? = null,
    @SerializedName("nativeReadLinks") val nativeReadLinks: Boolean? = null,
) {
    internal fun overriddenCapabilities(): Set<AiModelCapabilityField> =
        capabilityOverrides ?: if (capabilitiesEdited) AiModelCapabilityField.entries.toSet() else emptySet()

    internal fun overridesCapability(field: AiModelCapabilityField): Boolean =
        capabilityOverrides?.contains(field) ?: capabilitiesEdited

    internal fun withCapabilityOverrides(fields: Set<AiModelCapabilityField>): AiModelSettings =
        copy(capabilitiesEdited = fields.isNotEmpty(), capabilityOverrides = fields.takeUnless { it.isEmpty() })

    internal fun referenceContextWindow(info: AiModelInfo?): Int? =
        referenceContextWindowTokens?.takeIf { it > 0 }
            ?: info?.takeIf { it.endpoint == catalogEndpoint }?.contextWindow?.value

    internal fun referenceMaxOutput(info: AiModelInfo?): Int? =
        referenceMaxOutputTokens?.takeIf { it > 0 }
            ?: info?.takeIf { it.endpoint == catalogEndpoint }?.maxOutputTokens?.value

    internal fun applyOutputLimit(
        params: AiTextParams,
        info: AiModelInfo?,
        contextWindow: Int,
    ): AiTextParams {
        val outputLimit = maxOutputTokens?.takeIf { it > 0 }?.let { custom ->
            minOf(custom, info?.maxOutputTokens?.value ?: custom, (contextWindow - 1).coerceAtLeast(1))
        }
        return params.copy(maxTokens = outputLimit ?: params.maxTokens)
    }
}

internal const val DEFAULT_REASONING_CHOICE = "auto"
internal const val DISABLE_REASONING_CHOICE = "off"
internal const val ENABLE_REASONING_CHOICE = "on"
private const val REASONING_BUDGET_PREFIX = "budget:"

internal val modelEffortChoices = listOf("low", "medium", "high", "xhigh", "max", "ultra")
private val effortOrder = listOf("minimal") + modelEffortChoices
private val reservedEfforts = setOf(DEFAULT_REASONING_CHOICE, DISABLE_REASONING_CHOICE, ENABLE_REASONING_CHOICE, "none")
private val validEffortName = Regex("[a-z][a-z0-9_-]{0,31}")

internal data class AiModelEffortOptions(
    val detected: List<String>?,
    val candidates: List<String>,
    val checked: List<String>,
    val editable: List<String> = candidates,
) {
    val enabled: List<String> get() = checked.ifEmpty { candidates }
}

internal data class AiModelReasoning(
    val supported: Boolean,
    val efforts: AiModelEffortOptions,
    val isTrial: Boolean = false,
    val canDisable: Boolean = false,
    val canEnable: Boolean = false,
    val protocol: AiReasoningProtocol? = null,
    val budgetRange: IntRange? = null,
    val budgetOutputLimit: Int? = null,
    val defaultThinkingEnabled: Boolean? = null,
)

internal fun AiProviderSetting.modelReasoning(model: AiModel): AiModelReasoning {
    val source = modelConfigurationKey()
    val info = reasoningCatalogInfo(model)
    val settings = model.modelSettingsFor(source)
    val vendor = modelPresetProviderId()
    val preset = modelPreset(vendor, model.id)
    val resolved = effectiveModelCapabilities(model, source, vendor)
    val manualAbility = settings?.takeIf { it.overridesCapability(AiModelCapabilityField.REASONING) }?.let {
        AiModelAbility.REASONING in model.safeAbilities()
    }
    val unavailable = resolved.safeType() != AiModelType.CHAT ||
        AiModelRegistry.shouldClearDeclaredCapabilities(model.id) ||
        (vendor == "openrouter" && model.id.lowercase() in setOf("openrouter/auto", "openrouter/free")) ||
        info?.reasoning?.value == false || manualAbility == false
    if (unavailable) return AiModelReasoning(false, AiModelEffortOptions(emptyList(), emptyList(), emptyList()))

    if (type != AiProviderType.OPENAI) {
        val native = nativeModelReasoning(model.id, info)
        val efforts = native?.efforts.orEmpty()
        val ordered = effortOrder.filter(efforts::contains)
        val checked = settings?.enabledReasoningEfforts ?: ordered
        val claudeOutputLimit = minOf(CLAUDE_DEFAULT_MAX_TOKENS, info?.maxOutputTokens?.value ?: Int.MAX_VALUE)
        val budgetRange = native?.budgetRange?.let {
            if (type == AiProviderType.CLAUDE) {
                (it.first until minOf(it.last + 1, claudeOutputLimit)).takeUnless(IntRange::isEmpty)
            } else it
        }
        return AiModelReasoning(
            supported = native != null || AiModelAbility.REASONING in resolved.safeAbilities(),
            efforts = AiModelEffortOptions(native?.efforts, ordered, ordered.filter(checked::contains)),
            canDisable = native?.canDisable == true,
            canEnable = native?.defaultEnabled == false && ordered.isEmpty() &&
                (native.adaptive || (type == AiProviderType.GOOGLE && budgetRange != null)),
            protocol = AiReasoningProtocol.NATIVE.takeIf { native != null },
            budgetRange = budgetRange,
            budgetOutputLimit = claudeOutputLimit.takeIf { type == AiProviderType.CLAUDE && budgetRange != null },
            defaultThinkingEnabled = native?.defaultEnabled,
        )
    }

    val configured = assistantReasoningFields(model.id)
    val hasConfiguredFields = thinkingParam.isNotBlank() || effortParam.isNotBlank()
    val protocol = when {
        hasConfiguredFields -> AiReasoningProtocol.CONFIGURED
        vendor == "openrouter" -> AiReasoningProtocol.OPENROUTER
        preset?.reasoningProtocol != null -> preset.reasoningProtocol
        configured.thinkingParam.isNotBlank() || configured.effortParam.isNotBlank() ->
            AiReasoningProtocol.CONFIGURED
        else -> AiReasoningProtocol.EFFORT
    }
    val effortLimit = when {
        protocol == AiReasoningProtocol.CONFIGURED && configured.effortParam.isBlank() -> emptyList()
        else -> preset?.effortLimit
    }
    val declared = info?.effortLevels?.value
    val wireEfforts = openRouterEfforts.takeIf { protocol == AiReasoningProtocol.OPENROUTER }
    val channelEfforts = when {
        declared != null && effortLimit != null -> declared.filter(effortLimit::contains)
        declared != null -> declared
        effortLimit != null -> effortLimit
        else -> preset?.efforts
    }?.selectableEfforts()?.filter { wireEfforts?.contains(it) != false }
    val detected = channelEfforts ?: sharedModelEfforts(model.id)

    val knownSupport = manualAbility == true || AiModelAbility.REASONING in resolved.safeAbilities() ||
        info?.canDisableThinking != null || detected?.isNotEmpty() == true ||
        (protocol == AiReasoningProtocol.CONFIGURED && hasConfiguredFields)
    val canTry = assistantChoice(model) != null
    val isTrial = detected == null && canTry
    val available = detected ?: modelEffortChoices.takeIf { isTrial }.orEmpty()
    val ordered = (effortOrder + available).distinct()
        .filter { it in available && wireEfforts?.contains(it) != false }
    val editable = if (channelEfforts == null && canTry) (modelEffortChoices + ordered).distinct()
        .filter { wireEfforts?.contains(it) != false } else ordered
    val options = AiModelEffortOptions(
        detected = detected,
        candidates = ordered,
        checked = editable.filter((settings?.enabledReasoningEfforts ?: detected).orEmpty()::contains),
        editable = editable,
    )

    val hasSwitch = when (protocol) {
        AiReasoningProtocol.THINKING, AiReasoningProtocol.ENABLE_THINKING, AiReasoningProtocol.OPENROUTER -> true
        AiReasoningProtocol.CONFIGURED -> configured.thinkingParam.isNotBlank()
        else -> false
    }
    val hasDisableValue = when (protocol) {
        AiReasoningProtocol.CONFIGURED -> configured.effortParam.isNotBlank() &&
            configured.disableEffortValue.isNotBlank()
        AiReasoningProtocol.EFFORT -> info?.canDisableThinking?.value == true ||
            preset?.canDisableThinking == true || "none" in declared.orEmpty()
        else -> false
    }
    val mandatory = (info?.canDisableThinking?.value ?: preset?.canDisableThinking) == false
    val anthropic = model.id.removePrefix("~").startsWith("anthropic/", ignoreCase = true)
    val outputLimit = info?.maxOutputTokens?.value?.takeIf { it > 0 }
    val budgetRange = when {
        protocol == AiReasoningProtocol.OPENROUTER && info?.supportsReasoningBudget?.value == true &&
            (!anthropic || outputLimit != null) -> {
            val minimum = if (anthropic) 1_024 else 1
            val maximum = minOf((outputLimit ?: Int.MAX_VALUE) - 1, if (anthropic) 128_000 else Int.MAX_VALUE)
            (minimum..maximum).takeUnless { it.isEmpty() }
        }
        protocol == AiReasoningProtocol.ENABLE_THINKING -> preset?.reasoningBudgetRange
        else -> null
    }?.takeIf {
        val generationProtocol = resolveModelGenerationRoute(model).protocol
        generationProtocol != AiModelGenerationTestProtocol.RESPONSES ||
            generationUrl(generationProtocol).isOpenRouterApi("responses")
    }
    return AiModelReasoning(
        supported = knownSupport || isTrial,
        efforts = options,
        isTrial = isTrial,
        canDisable = knownSupport && !mandatory && (hasSwitch || hasDisableValue),
        canEnable = knownSupport && !mandatory && hasSwitch && ordered.isEmpty(),
        protocol = protocol,
        budgetRange = budgetRange,
        budgetOutputLimit = outputLimit.takeIf { protocol == AiReasoningProtocol.OPENROUTER && anthropic && budgetRange != null },
        defaultThinkingEnabled = if (mandatory) true
            else info?.defaultReasoningEnabled?.value ?: preset?.defaultThinkingEnabled,
    )
}

internal data class AiAssistantReasoning(
    val options: List<String>,
    val selected: String,
    val supported: Boolean,
    val isTrial: Boolean,
    val protocol: AiReasoningProtocol?,
    val budgetRange: IntRange? = null,
    val budgetTokens: Int? = null,
    val budgetOutputLimit: Int? = null,
    val autoThinkingEnabled: Boolean = !isTrial,
) {
    val hasControls: Boolean get() = options.size > 1 || budgetRange != null

    val active: Boolean get() = isActive(selected)

    fun isActive(choice: String): Boolean =
        supported && choice != DISABLE_REASONING_CHOICE && (choice != DEFAULT_REASONING_CHOICE || autoThinkingEnabled)

    fun applyTo(params: AiTextParams): AiTextParams = params.copy(
        maxTokens = if (budgetTokens != null && budgetOutputLimit != null) {
            minOf(params.maxTokens ?: budgetOutputLimit, budgetOutputLimit)
        } else params.maxTokens,
        enableThinking = selected != DEFAULT_REASONING_CHOICE && selected != DISABLE_REASONING_CHOICE,
        disableThinking = selected == DISABLE_REASONING_CHOICE,
        reasoningEffort = selected.takeUnless { budgetTokens != null || it in reservedEfforts },
        assistantReasoningProtocol = protocol,
        assistantReasoningMaxTokens = budgetTokens,
    )
}

internal fun AiProviderSetting.modelAssistantReasoning(
    model: AiModel,
    assistantEffort: String?,
    inheritedLevel: AiReasoningLevel?,
    modelEffort: String? = null,
): AiAssistantReasoning {
    val capability = modelReasoning(model)
    val settings = model.modelSettingsFor(modelConfigurationKey())
    // Keep old provider settings working. Experimental levels otherwise need a choice for this model.
    val useLegacyChoice = type == AiProviderType.OPENAI && (!capability.isTrial ||
        (AiModelAbility.REASONING in model.safeAbilities() && assistantReasoningFields(model.id).effortParam.isNotBlank()))
    val inheritedChoice = inheritedLevel?.let { level ->
        if (capability.canEnable && level.reasoningEffort != null) ENABLE_REASONING_CHOICE
        else level.reasoningEffort ?: level.prefValue
    }
    val preferred = modelEffort ?: assistantEffort.takeIf { useLegacyChoice } ?: settings?.reasoningEffort
        ?: inheritedChoice.takeIf { useLegacyChoice }
    val budget = preferred?.takeIf { it.startsWith(REASONING_BUDGET_PREFIX) }
        ?.removePrefix(REASONING_BUDGET_PREFIX)?.toIntOrNull()
        ?.takeIf { capability.budgetRange?.contains(it) == true }
    val options = buildList {
        if (capability.canDisable) add(DISABLE_REASONING_CHOICE)
        add(DEFAULT_REASONING_CHOICE)
        if (capability.canEnable) add(ENABLE_REASONING_CHOICE)
        if (capability.protocol != null) addAll(capability.efforts.enabled)
        if (budget != null) add(reasoningBudgetChoice(budget))
    }
    return AiAssistantReasoning(
        options = options,
        selected = budget?.let(::reasoningBudgetChoice) ?: preferred?.takeIf(options::contains) ?: DEFAULT_REASONING_CHOICE,
        supported = capability.supported,
        isTrial = capability.isTrial,
        protocol = capability.protocol,
        budgetRange = capability.budgetRange,
        budgetTokens = budget,
        budgetOutputLimit = capability.budgetOutputLimit,
        autoThinkingEnabled = capability.defaultThinkingEnabled ?: !capability.isTrial,
    )
}

internal fun AiProviderSetting.compactionTextParams(model: AiModel): AiTextParams {
    val params = AiTextParams(temperature = 0f)
    if (type != AiProviderType.OPENAI) return params
    return modelAssistantReasoning(model, DISABLE_REASONING_CHOICE, null, modelEffort = DISABLE_REASONING_CHOICE)
        .applyTo(params)
}

internal fun reasoningBudgetChoice(tokens: Int): String = "$REASONING_BUDGET_PREFIX$tokens"

internal fun AiModelInfo.selectableEffortLevels(): List<String> = effortLevels?.value.orEmpty().selectableEfforts()

private fun List<String>.selectableEfforts(): List<String> =
    filter(validEffortName::matches).filterNot(reservedEfforts::contains).distinct()

internal fun AiModel.modelSettingsFor(endpoint: String?): AiModelSettings? =
    endpoint?.let { modelSettings?.takeIf { settings -> settings.catalogEndpoint == it } }

internal fun AiModel.catalogInfo(settings: AiModelSettings): AiModelInfo? =
    modelInfo?.takeIf { it.endpoint == settings.catalogEndpoint }
