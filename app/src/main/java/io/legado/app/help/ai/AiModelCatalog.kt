package io.legado.app.help.ai

import com.google.gson.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class AiModelCatalogFormat { COMPATIBLE, OPENROUTER, SENSENOVA, ARK, OPENCODE_GO, MOONSHOT, CLAUDE, GEMINI }

internal fun AiProviderSetting.openAiCatalogFormat(resolvedUrl: HttpUrl? = null): AiModelCatalogFormat {
    val (format, catalogPath) = when (officialModelProviderId()) {
        "openrouter" -> AiModelCatalogFormat.OPENROUTER to "/api/v1/models"
        "sensenova" -> AiModelCatalogFormat.SENSENOVA to "/v1/models"
        "volcengine" -> AiModelCatalogFormat.ARK to "/api/v3/models"
        AiDefaultProviders.OPENCODE_ID -> AiModelCatalogFormat.OPENCODE_GO to "/inference/go/v1/models"
        "moonshot" -> AiModelCatalogFormat.MOONSHOT to "/v1/models"
        else -> return AiModelCatalogFormat.COMPATIBLE
    }
    if (resolvedUrl == null) return format
    val base = baseUrl.toHttpUrlOrNull() ?: return AiModelCatalogFormat.COMPATIBLE
    return format.takeIf { resolvedUrl.sameOriginAs(base) && resolvedUrl.encodedPath.trimEnd('/') == catalogPath }
        ?: AiModelCatalogFormat.COMPATIBLE
}

internal fun readModelInfo(model: JsonObject, endpoint: String, format: AiModelCatalogFormat): AiModelInfo {
    val info = readCommonModelInfo(model, endpoint)
    val features = model.catalogStrings("supported_features")
    val common = info.copy(
        contextWindow = info.contextWindow ?: model.positiveInt("context_length").catalogValue(),
        maxOutputTokens = info.maxOutputTokens ?: model.positiveInt("max_completion_tokens").catalogValue(),
        toolCalling = features?.any { it in toolFeatureNames }.catalogValue(),
        reasoning = (model.strictBoolean("reasoning") ?: true.takeIf { !info.effortLevels?.value.isNullOrEmpty() }
            ?: features?.contains("reasoning")).catalogValue(),
        canDisableThinking = model.strictBoolean("can_disable_thinking").catalogValue(),
    )
    return when (format) {
        AiModelCatalogFormat.COMPATIBLE -> common
        AiModelCatalogFormat.SENSENOVA -> common.copy(
            maxOutputTokens = common.maxOutputTokens ?: model.positiveInt("max_output_length").catalogValue(),
        )
        AiModelCatalogFormat.ARK -> common.withArkCatalog(model)
        AiModelCatalogFormat.OPENCODE_GO -> common.withOpenCodeCatalog(model)
        AiModelCatalogFormat.MOONSHOT -> common.copy(
            inputModalities = common.inputModalities ?: catalogTextInputs(
                model.strictBoolean("supports_image_in"), model.strictBoolean("supports_video_in"), "video",
            ),
            reasoning = model.strictBoolean("supports_reasoning").catalogValue() ?: common.reasoning,
        )
        AiModelCatalogFormat.OPENROUTER -> {
            val architecture = model.objectOrNull("architecture")
            val route = model.objectOrNull("top_provider")
            val parameters = model.catalogStrings("supported_parameters")
            val reasoning = model.objectOrNull("reasoning")
            val efforts = reasoning?.let {
                when {
                    !it.has("supported_efforts") -> emptyList()
                    it.get("supported_efforts").isJsonNull -> openRouterEfforts
                    else -> it.catalogStrings("supported_efforts")
                }
            }
            common.copy(
                contextWindow = common.contextWindow ?: route?.positiveInt("context_length").catalogValue(),
                maxOutputTokens = route?.positiveInt("max_completion_tokens").catalogValue() ?: common.maxOutputTokens,
                inputModalities = architecture?.catalogStrings("input_modalities").catalogValue() ?: common.inputModalities,
                outputModalities = architecture?.catalogStrings("output_modalities").catalogValue() ?: common.outputModalities,
                toolCalling = parameters?.contains("tools").catalogValue() ?: common.toolCalling,
                reasoning = (if (reasoning != null) true else model.strictBoolean("reasoning")
                    ?: parameters?.any { it in reasoningParameterNames }).catalogValue() ?: common.reasoning,
                effortLevels = efforts.catalogValue() ?: common.effortLevels,
                defaultEffort = reasoning?.stringOrNull("default_effort").catalogValue() ?: common.defaultEffort,
                canDisableThinking = reasoning?.let { it.strictBoolean("mandatory") != true }.catalogValue()
                    ?: common.canDisableThinking,
                supportsReasoningBudget =
                    reasoning?.let { it.strictBoolean("supports_max_tokens") == true }.catalogValue(),
                defaultReasoningEnabled = (reasoning?.strictBoolean("default_enabled")
                    ?: false.takeIf { reasoning?.get("default_effort")?.takeIf { it.isJsonPrimitive }?.asString == "none" })
                    .catalogValue(),
            )
        }
        AiModelCatalogFormat.CLAUDE -> {
            val capabilities = model.objectOrNull("capabilities")
            val effort = capabilities?.objectOrNull("effort")
            val thinking = capabilities?.objectOrNull("thinking")
            val thinkingTypes = thinking?.objectOrNull("types")
            val levels = effort?.entrySet()?.mapNotNull { (key, value) ->
                key.takeIf { value.isJsonObject && value.asJsonObject.strictBoolean("supported") == true }
            }
            common.copy(
                contextWindow = model.positiveInt("max_input_tokens").catalogValue() ?: common.contextWindow,
                maxOutputTokens = model.positiveInt("max_tokens").catalogValue() ?: common.maxOutputTokens,
                inputModalities = common.inputModalities ?: catalogTextInputs(
                    capabilities?.objectOrNull("image_input")?.strictBoolean("supported"),
                    capabilities?.objectOrNull("pdf_input")?.strictBoolean("supported"),
                    "file",
                ),
                reasoning = (thinking?.strictBoolean("supported") ?: effort?.strictBoolean("supported"))
                    .catalogValue() ?: common.reasoning,
                effortLevels = levels.catalogValue() ?: common.effortLevels,
                canDisableThinking = thinkingTypes?.objectOrNull("disabled")
                    ?.strictBoolean("supported").catalogValue() ?: common.canDisableThinking,
                supportsAdaptiveThinking = thinkingTypes?.objectOrNull("adaptive")
                    ?.strictBoolean("supported").catalogValue(),
                supportsReasoningBudget = thinkingTypes?.objectOrNull("enabled")
                    ?.strictBoolean("supported").catalogValue(),
            )
        }
        AiModelCatalogFormat.GEMINI -> common.copy(
            contextWindow = model.positiveInt("inputTokenLimit").catalogValue() ?: common.contextWindow,
            maxOutputTokens = model.positiveInt("outputTokenLimit").catalogValue() ?: common.maxOutputTokens,
            reasoning = model.strictBoolean("thinking").catalogValue() ?: common.reasoning,
        )
    }
}

private fun AiModelInfo.withArkCatalog(model: JsonObject): AiModelInfo {
    val limits = model.objectOrNull("token_limits")
    val modalities = model.objectOrNull("modalities")
    val features = model.objectOrNull("features")
    val thinking = when (features?.objectOrNull("thinking")?.stringOrNull("type")) {
        "enabled" -> true
        "disabled" -> false
        else -> null
    }
    return copy(
        contextWindow = limits?.positiveInt("context_window").catalogValue() ?: contextWindow,
        maxOutputTokens = limits?.positiveInt("max_output_token_length").catalogValue() ?: maxOutputTokens,
        inputModalities = modalities?.catalogStrings("input_modalities").catalogValue() ?: inputModalities,
        outputModalities = modalities?.catalogStrings("output_modalities").catalogValue() ?: outputModalities,
        toolCalling = features?.objectOrNull("tools")?.strictBoolean("function_calling").catalogValue() ?: toolCalling,
        reasoning = thinking.catalogValue() ?: reasoning,
    )
}

private fun AiModelInfo.withOpenCodeCatalog(model: JsonObject): AiModelInfo {
    val limits = model.objectOrNull("limit")
    val modalities = model.objectOrNull("modalities")
    val capabilities = model.objectOrNull("capabilities")
    val thinking = capabilities?.objectOrNull("reasoning")
    val effort = thinking?.catalogStrings("effort")
    val reasoningSupported = thinking?.strictBoolean("supported") ?: true.takeIf { !effort.isNullOrEmpty() }
    return copy(
        contextWindow = limits?.positiveInt("context").catalogValue() ?: contextWindow,
        maxOutputTokens = limits?.positiveInt("output").catalogValue() ?: maxOutputTokens,
        inputModalities = modalities?.openCodeModalities("input").catalogValue() ?: inputModalities,
        outputModalities = modalities?.openCodeModalities("output").catalogValue() ?: outputModalities,
        toolCalling = capabilities?.objectOrNull("tools")?.strictBoolean("supported").catalogValue() ?: toolCalling,
        reasoning = reasoningSupported.catalogValue() ?: reasoning,
        effortLevels = effort.catalogValue() ?: effortLevels,
    )
}

private fun JsonObject.openCodeModalities(key: String): List<String>? =
    catalogStrings(key)?.map { modality ->
        when (val mediaType = modality.substringBefore('/').lowercase()) {
            "text", "image", "video", "audio" -> mediaType
            "application" -> if (modality.equals("application/pdf", ignoreCase = true)) "file" else modality
            else -> modality
        }
    }?.distinct()

private fun catalogTextInputs(
    imageSupported: Boolean?,
    otherSupported: Boolean?,
    other: String,
): AiModelValue<List<String>>? {
    if (imageSupported == null || otherSupported == null) return null
    return AiModelValue(buildList {
        add("text")
        if (imageSupported) add("image")
        if (otherSupported) add(other)
    })
}

internal fun AiProviderSetting.effectiveModelCapabilities(
    model: AiModel,
    source: String = modelConfigurationKey(),
    vendor: String? = modelPresetProviderId(),
): AiModel = model.withCatalogCapabilities(model.effectiveModelInfo(source, vendor, !catalogRefreshRequired))

internal fun AiModelValue<List<String>>.knownModalities(): Set<AiModelModality>? =
    value.mapNotNull { token -> AiModelModality.entries.firstOrNull { it.name.equals(token, ignoreCase = true) } }
        .toSet().takeIf { it.isNotEmpty() || value.isEmpty() }

internal fun AiModel.withCatalogCapabilities(info: AiModelInfo?): AiModel {
    if (info == null || AiModelRegistry.shouldClearDeclaredCapabilities(id)) return this
    val input = info.inputModalities?.knownModalities()?.toList()
    val output = info.outputModalities?.knownModalities()?.toList()
    if (input == null && output == null && info.toolCalling == null && info.reasoning == null) return this
    val modelType = if (safeType() == AiModelType.CHAT) {
        when (output?.singleOrNull()) {
            AiModelModality.IMAGE -> AiModelType.IMAGE
            AiModelModality.VIDEO -> AiModelType.VIDEO
            else -> AiModelType.CHAT
        }
    } else {
        safeType()
    }
    val resolved = safeAbilities().toMutableSet()
    info.toolCalling?.value?.let { if (it) resolved.add(AiModelAbility.TOOL) else resolved.remove(AiModelAbility.TOOL) }
    info.reasoning?.value?.let { if (it) resolved.add(AiModelAbility.REASONING) else resolved.remove(AiModelAbility.REASONING) }
    if (modelType != AiModelType.CHAT) resolved.removeAll(setOf(AiModelAbility.TOOL, AiModelAbility.REASONING))
    val detected = copy(
        type = modelType,
        inputModalities = input ?: safeInputModalities(),
        outputModalities = output ?: safeOutputModalities(),
        abilities = resolved.toList(),
    )
    val settings = modelSettingsFor(info.endpoint) ?: return detected
    return detected.withCapabilityOverrides(this, settings)
}

internal fun AiModel.withCapabilityOverrides(saved: AiModel, settings: AiModelSettings): AiModel {
    val overrides = settings.overriddenCapabilities()
    if (overrides.isEmpty()) return this
    val typeOverridden = AiModelCapabilityField.TYPE in overrides
    val preferredType = if (typeOverridden) saved.safeType() else safeType()
    val abilities = if (typeOverridden && preferredType != AiModelType.CHAT) {
        saved.safeAbilities().filterNot { it == AiModelAbility.TOOL || it == AiModelAbility.REASONING }
    } else buildList {
        val detected = safeAbilities()
        val manual = saved.safeAbilities()
        addAll(detected.filterNot { ability ->
            ability == AiModelAbility.TOOL || ability == AiModelAbility.REASONING ||
                (typeOverridden && (ability == AiModelAbility.ASR || ability == AiModelAbility.TTS))
        })
        if (preferredType == AiModelType.CHAT) {
            val toolSource = if (AiModelCapabilityField.TOOL_CALLING in overrides) manual else detected
            val reasoningSource = if (AiModelCapabilityField.REASONING in overrides) manual else detected
            if (AiModelAbility.TOOL in toolSource) add(AiModelAbility.TOOL)
            if (AiModelAbility.REASONING in reasoningSource) add(AiModelAbility.REASONING)
        }
    }
    return copy(
        type = preferredType,
        inputModalities = if (AiModelCapabilityField.INPUT_MODALITIES in overrides)
            saved.safeInputModalities() else safeInputModalities(),
        outputModalities = if (AiModelCapabilityField.OUTPUT_MODALITIES in overrides)
            saved.safeOutputModalities() else safeOutputModalities(),
        abilities = abilities,
    )
}

private val toolFeatureNames = setOf("tools", "tool", "function_calling")
private val reasoningParameterNames = setOf("reasoning", "reasoning_effort", "include_reasoning")
internal val openRouterEfforts = listOf("minimal", "low", "medium", "high", "xhigh", "max", "none")

private fun JsonObject.strictBoolean(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
