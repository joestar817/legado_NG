package io.legado.app.help.ai

internal enum class AiManualModelAddResult { ADDED, DUPLICATE, UNAVAILABLE }

internal fun AiProviderSetting.isManualModel(modelId: String): Boolean {
    val source = modelConfigurationKey()
    return models.any { it.id == modelId && it.manualModelEndpoint == source }
}

internal fun AiProviderSetting.withManualModel(id: String, type: AiModelType): AiProviderSetting {
    val endpoint = modelConfigurationKey()
    val previous = models.firstOrNull { it.id == id }
    val previousSettings = previous?.modelSettingsFor(endpoint)
    val settings = (previousSettings ?: AiModelSettings(endpoint)).let {
        it.withCapabilityOverrides(it.overriddenCapabilities() + AiModelCapabilityField.TYPE)
    }
    val manual = createManualModel(id, type, settings).let { added ->
        if (previous == null || previousSettings == null) added else added.withCapabilityOverrides(previous, settings)
            .copy(type = type, displayName = previous.displayName.ifBlank { id })
    }
    val unlisted = unlistedModelEdits.orEmpty() + listOfNotNull(previous?.takeIf {
        it.modelSettings?.catalogEndpoint?.let { source -> source != endpoint } == true
    }?.copy(modelInfo = null))
    return copy(
        models = models.filterNot { it.id == id } + manual,
        availableModelIds = availableModelIds + id,
        unlistedModelEdits = unlisted.distinctBy { it.id to it.modelSettings?.catalogEndpoint }
            .takeIf { it.isNotEmpty() },
    )
}

private fun createManualModel(id: String, type: AiModelType, settings: AiModelSettings): AiModel {
    val detected = AiModelRegistry.enrich(AiModel(id = id, type = type)).takeIf { it.type == type }
    val (input, output) = when (type) {
        AiModelType.CHAT, AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT) to listOf(AiModelModality.TEXT)
        AiModelType.IMAGE -> listOf(AiModelModality.TEXT) to listOf(AiModelModality.IMAGE)
        AiModelType.VIDEO -> listOf(AiModelModality.TEXT) to listOf(AiModelModality.VIDEO)
        AiModelType.ASR -> listOf(AiModelModality.AUDIO) to listOf(AiModelModality.TEXT)
        AiModelType.TTS -> listOf(AiModelModality.TEXT) to listOf(AiModelModality.AUDIO)
        AiModelType.OCR -> listOf(AiModelModality.TEXT, AiModelModality.IMAGE) to listOf(AiModelModality.TEXT)
        AiModelType.OTHER -> emptyList<AiModelModality>() to emptyList()
    }
    return AiModel(
        id = id,
        displayName = id,
        type = type,
        inputModalities = detected?.inputModalities.orEmpty().ifEmpty { input },
        outputModalities = detected?.outputModalities.orEmpty().ifEmpty { output },
        abilities = when (type) {
            AiModelType.ASR -> listOf(AiModelAbility.ASR)
            AiModelType.TTS -> listOf(AiModelAbility.TTS)
            else -> detected?.abilities.orEmpty()
        },
        modelSettings = settings,
        manualModelEndpoint = settings.catalogEndpoint,
    )
}

/** Keep previous configurations available for a later switch back without exposing them on this one. */
internal fun AiProviderSetting.reconcileManualModels(): AiProviderSetting {
    val source = modelConfigurationKey()
    val activeIds = models.filter { it.manualModelEndpoint == source }.mapTo(hashSetOf()) { it.id }
    val (returning, archived) = unlistedModelEdits.orEmpty().partition { it.manualModelEndpoint == source }
    val restored = returning.filterNot { it.id in activeIds }.distinctBy { it.id }
    val restoredIds = restored.mapTo(hashSetOf()) { it.id }
    val (old, current) = models.partition { model ->
        model.manualModelEndpoint?.let { it != source } == true || model.id in restoredIds
    }
    if (old.isEmpty() && returning.isEmpty()) return this
    return copy(
        models = current + restored,
        unlistedModelEdits = (old.filter { it.modelSettings != null }.map { it.copy(modelInfo = null) } + archived)
            .distinctBy { it.id to it.modelSettings?.catalogEndpoint }.takeIf { it.isNotEmpty() },
    )
}
