package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelCapabilityField
import io.legado.app.help.ai.AiModelRegistry
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.defineAiModel
import io.legado.app.help.ai.isTextChatModel
import io.legado.app.help.ai.modelSettingsFor

internal enum class AiProviderModelFilter(private val type: AiModelType? = null) {
    ALL,
    CHAT(AiModelType.CHAT),
    IMAGE(AiModelType.IMAGE),
    EMBEDDING(AiModelType.EMBEDDING),
    VIDEO(AiModelType.VIDEO),
    ASR(AiModelType.ASR),
    TTS(AiModelType.TTS),
    OCR(AiModelType.OCR),
    OTHER;

    @get:StringRes
    val labelRes: Int
        get() = type?.let(::modelTypeLabelRes) ?: if (this == ALL) R.string.all else R.string.other

    @get:DrawableRes
    val iconRes: Int
        get() = when (this) {
            ALL -> R.drawable.ic_select_all
            CHAT -> R.drawable.ic_ai_chat_suggestion
            IMAGE -> R.drawable.ic_ai_model_image
            EMBEDDING -> R.drawable.ic_bubble_chart
            VIDEO -> R.drawable.ic_ai_capability_video
            ASR -> R.drawable.ic_ai_capability_asr
            TTS -> R.drawable.ic_ai_capability_tts
            OCR -> R.drawable.ic_scan
            OTHER -> R.drawable.ic_ai_model_other
        }

    fun matches(modelType: AiModelType?): Boolean = this == ALL || type == modelType

    fun orAllFor(models: List<AiProviderModelItemUiModel>): AiProviderModelFilter =
        if (models.any { matches(it.type) }) this else ALL
}

internal fun List<AiProviderModelItemUiModel>.availableModelFilters(): List<AiProviderModelFilter> {
    if (isEmpty()) return emptyList()
    val types = mapTo(hashSetOf()) { it.type }
    return AiProviderModelFilter.entries.filter { filter -> types.any(filter::matches) }
}

private val otherModelIds = defineAiModel {
    tokens("rerank|reranker")
}

internal fun AiModel.providerListType(configurationKey: String?): AiModelType? {
    val type = safeType()
    if (modelSettingsFor(configurationKey)?.overridesCapability(AiModelCapabilityField.TYPE) == true) {
        return type
    }
    val modelId = safeId()
    if (AiModelRegistry.shouldClearDeclaredCapabilities(modelId) ||
        otherModelIds.match(modelId.substringAfterLast('/'))
    ) {
        return null
    }
    // CHAT is also the storage default for ID-only catalog entries.
    return type.takeIf { it != AiModelType.CHAT || isTextChatModel() }
}

internal fun List<AiProviderModelItemUiModel>.filterProviderModels(
    query: String,
    typeFilter: AiProviderModelFilter,
): List<AiProviderModelItemUiModel> {
    val hasQuery = query.isNotBlank()
    if (!hasQuery && typeFilter == AiProviderModelFilter.ALL) return this
    return filter { model ->
        typeFilter.matches(model.type) &&
            (!hasQuery || model.id.contains(query, ignoreCase = true) ||
                model.name.contains(query, ignoreCase = true) ||
                model.searchAliases.any { it.contains(query, ignoreCase = true) })
    }
}

internal fun List<AiProviderModelItemUiModel>.bulkModelEnableTarget(): Boolean? =
    if (isEmpty()) null else any { !it.selected }

internal fun AiProviderSetting.withModelSelection(
    modelIds: Set<String>,
    enabled: Boolean,
): AiProviderSetting {
    val affectedIds = displayModels().mapNotNullTo(linkedSetOf()) { it.safeId().takeIf(modelIds::contains) }
    if (affectedIds.isEmpty()) return this
    val selected = (if (availableModelSelectionInitialized) {
        availableModelIds
    } else {
        effectiveAvailableModelIds()
    }).toMutableSet()
    val changed = if (enabled) selected.addAll(affectedIds) else selected.removeAll(affectedIds)
    return if (changed) {
        copy(
            availableModelIds = selected.toList(),
            availableModelSelectionInitialized = true,
        )
    } else {
        this
    }
}
