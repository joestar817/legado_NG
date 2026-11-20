package io.legado.app.ui.config

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelRegistry
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiModelSettings

internal data class AiModelEditDraft(
    val displayName: String,
    val type: AiModelType,
    val inputModalities: Set<AiModelModality>,
    val outputModalities: Set<AiModelModality>,
    val abilities: Set<AiModelAbility>,
    val modelSettings: AiModelSettings? = null,
)

internal data class AiModelCapabilityDefaults(
    val type: AiModelType,
    val inputModalities: Set<AiModelModality>,
    val outputModalities: Set<AiModelModality>,
    val abilities: Set<AiModelAbility>,
)

/**
 * 按模型 id 从内置注册表推导能力。注册表没有匹配规则时返回 null，
 * 避免把未知模型静默重置成聊天模型。
 */
internal fun AiModel.registryCapabilityDefaults(): AiModelCapabilityDefaults? {
    val modelId = safeId()
    if (modelId.isBlank() || AiModelRegistry.shouldClearDeclaredCapabilities(modelId)) {
        return null
    }
    val capabilities = AiModelRegistry.capabilities(modelId)
    val hasRegistryRule = capabilities.type != null ||
        capabilities.inputModalities.isNotEmpty() ||
        capabilities.outputModalities.isNotEmpty() ||
        capabilities.abilities.isNotEmpty()
    if (!hasRegistryRule) {
        return null
    }
    return AiModelCapabilityDefaults(
        type = capabilities.type ?: AiModelType.CHAT,
        inputModalities = capabilities.inputModalities.toSet(),
        outputModalities = capabilities.outputModalities.toSet(),
        abilities = capabilities.abilities.toSet(),
    )
}

@Composable
internal fun modelTypeLabel(type: AiModelType): String = stringResource(modelTypeLabelRes(type))

@StringRes
internal fun modelTypeLabelRes(type: AiModelType): Int = when (type) {
    AiModelType.CHAT -> R.string.ai_model_type_chat
    AiModelType.IMAGE -> R.string.ai_model_type_image
    AiModelType.EMBEDDING -> R.string.ai_model_type_embedding
    AiModelType.ASR -> R.string.ai_model_type_asr
    AiModelType.TTS -> R.string.ai_model_type_tts
    AiModelType.VIDEO -> R.string.ai_model_type_video
    AiModelType.OCR -> R.string.ai_model_type_ocr
}

@Composable
internal fun modalityLabel(modality: AiModelModality): String = stringResource(
    when (modality) {
        AiModelModality.TEXT -> R.string.ai_model_modality_text
        AiModelModality.IMAGE -> R.string.ai_model_modality_image
        AiModelModality.VIDEO -> R.string.ai_model_modality_video
        AiModelModality.AUDIO -> R.string.audio
        AiModelModality.FILE -> R.string.ai_model_modality_file
    }
)

@Composable
internal fun abilityLabel(ability: AiModelAbility): String = stringResource(
    when (ability) {
        AiModelAbility.TOOL -> R.string.ai_model_ability_tool
        AiModelAbility.REASONING -> R.string.ai_model_ability_reasoning
        AiModelAbility.ASR -> R.string.ai_model_type_asr
        AiModelAbility.TTS -> R.string.ai_model_type_tts
    }
)

internal fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (value in this) this - value else this + value
}
