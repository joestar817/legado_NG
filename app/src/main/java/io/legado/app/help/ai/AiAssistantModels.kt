package io.legado.app.help.ai

internal data class AiAssistantModelChoice(val model: AiModel, val textTrial: Boolean = false)

internal fun AiProviderSetting.assistantModelChoices(): List<AiAssistantModelChoice> {
    if (!canUseModels()) return emptyList()
    val source = modelConfigurationKey()
    val enabledIds = availableModelIds.toSet()
    return models.mapNotNull { model ->
        if (!modelIsCurrent(model, source) || (availableModelSelectionInitialized && model.id !in enabledIds)) null
        else assistantChoice(model)
    }
}

internal fun AiProviderSetting.assistantModelChoice(modelId: String): AiAssistantModelChoice? {
    return availableCatalogModel(modelId)?.let { assistantChoice(it) }
}

internal fun AiProviderSetting.isMissingCatalogModel(modelId: String): Boolean =
    modelId.isNotBlank() && canUseModels() && !catalogRefreshRequired && catalogLoaded && models.none { it.id == modelId }

internal fun AiProviderSetting.missingCatalogModelName(modelId: String): String? {
    if (!isMissingCatalogModel(modelId)) return null
    val source = modelConfigurationKey()
    return unlistedModelEdits?.firstOrNull { it.id == modelId && it.modelSettingsFor(source) != null }
        ?.displayName?.takeIf { it.isNotBlank() } ?: modelId
}

private fun AiProviderSetting.canUseModels(): Boolean =
    enabled && hasRequestCredentials()

private fun AiProviderSetting.modelIsCurrent(model: AiModel, source: String): Boolean =
    model.manualModelEndpoint?.let { it == source } ?: !catalogRefreshRequired

internal fun AiProviderSetting.availableCatalogModel(modelId: String): AiModel? {
    if (!canUseModels() || (availableModelSelectionInitialized && modelId !in availableModelIds)) return null
    val source = modelConfigurationKey()
    return models.firstOrNull { it.id == modelId && modelIsCurrent(it, source) }
}

internal fun AiModel.isTextChatModel(): Boolean =
    safeType() == AiModelType.CHAT && AiModelModality.TEXT in safeInputModalities() &&
        AiModelModality.TEXT in safeOutputModalities()

internal fun AiProviderSetting.assistantChoice(model: AiModel): AiAssistantModelChoice? {
    if (model.id.isBlank() || AiModelRegistry.shouldClearDeclaredCapabilities(model.id)) return null
    val effective = effectiveModelCapabilities(model)
    if (effective.safeType() != AiModelType.CHAT) return null
    val input = effective.safeInputModalities()
    val output = effective.safeOutputModalities()
    val text = AiModelModality.TEXT
    if (effective.isTextChatModel()) return AiAssistantModelChoice(effective)

    val info = effectiveModelInfo(model)
    val settings = model.modelSettingsFor(info.endpoint)
    val inputDeclared = input.isNotEmpty() || info.inputModalities != null ||
        settings?.overridesCapability(AiModelCapabilityField.INPUT_MODALITIES) == true
    val outputDeclared = output.isNotEmpty() || info.outputModalities != null ||
        settings?.overridesCapability(AiModelCapabilityField.OUTPUT_MODALITIES) == true
    if (builtIn || (text !in input && inputDeclared) || (text !in output && outputDeclared)) return null
    return AiAssistantModelChoice(
        effective.copy(
            inputModalities = input.ifEmpty { listOf(text) },
            outputModalities = output.ifEmpty { listOf(text) },
        ),
        textTrial = true,
    )
}
