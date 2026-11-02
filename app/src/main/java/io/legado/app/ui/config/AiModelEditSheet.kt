package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormInlineTextRow
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormPanelSectionTitle
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader

internal data class AiModelEditDraft(
    val displayName: String,
    val type: AiModelType,
    val inputModalities: Set<AiModelModality>,
    val outputModalities: Set<AiModelModality>,
    val abilities: Set<AiModelAbility>,
)

@Composable
internal fun AiModelEditSheet(
    model: AiModel,
    onSave: (AiModelEditDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by remember(model) { mutableStateOf(model.displayName()) }
    var selectedType by remember(model) { mutableStateOf(model.safeType()) }
    var inputModalities by remember(model) {
        mutableStateOf(model.safeInputModalities().toSet())
    }
    var outputModalities by remember(model) {
        mutableStateOf(model.safeOutputModalities().toSet())
    }
    var abilities by remember(model) { mutableStateOf(model.safeAbilities().toSet()) }
    val modelTypes = listOf(
        AiModelType.CHAT,
        AiModelType.IMAGE,
        AiModelType.EMBEDDING,
        AiModelType.ASR,
        AiModelType.TTS,
        AiModelType.VIDEO,
    )
    val modalities = listOf(
        AiModelModality.TEXT,
        AiModelModality.IMAGE,
        AiModelModality.VIDEO,
    )
    val editableAbilities = listOf(AiModelAbility.TOOL, AiModelAbility.REASONING)
    val showModalities = selectedType == AiModelType.CHAT || selectedType == AiModelType.VIDEO
    val showAbilities = selectedType == AiModelType.CHAT
    val save = {
        onSave(
            AiModelEditDraft(
                displayName = displayName,
                type = selectedType,
                inputModalities = inputModalities,
                outputModalities = outputModalities,
                abilities = abilities,
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
    ) {
        NgLongDrawerHeader(
            title = stringResource(R.string.ai_edit_model),
            actionIconRes = R.drawable.ic_save,
            actionContentDescription = stringResource(R.string.save),
            actionActive = true,
            onActionClick = save,
            centerTitle = true,
        )
        NgFormPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                NgFormPanelSectionTitle(
                    title = stringResource(R.string.ai_model_information)
                )
                NgFormInlineTextRow(
                    title = stringResource(R.string.ai_model_id),
                    value = model.safeId(),
                    onValueChange = {},
                    readOnly = true,
                    valueMuted = true,
                )
                NgFormGroupDivider()
                NgFormInlineTextRow(
                    title = stringResource(R.string.ai_model_display_name),
                    value = displayName,
                    onValueChange = { displayName = it },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                NgFormGroupDivider()
                NgFormPanelSectionTitle(
                    title = stringResource(R.string.ai_model_type)
                )
                AiModelSelectionRail(
                    items = modelTypes.take(3),
                    selected = setOf(selectedType),
                    label = { modelTypeLabel(it) },
                    onItemClick = { selectedType = it },
                )
                NgFormGroupDivider()
                AiModelSelectionRail(
                    items = modelTypes.drop(3),
                    selected = setOf(selectedType),
                    label = { modelTypeLabel(it) },
                    onItemClick = { selectedType = it },
                )
                if (showModalities) {
                    NgFormGroupDivider()
                    NgFormPanelSectionTitle(
                        title = stringResource(R.string.ai_model_input_modalities)
                    )
                    AiModelSelectionRail(
                        items = modalities,
                        selected = inputModalities,
                        label = { modalityLabel(it) },
                        onItemClick = { modality ->
                            inputModalities = inputModalities.toggle(modality)
                        },
                    )
                    NgFormGroupDivider()
                    NgFormPanelSectionTitle(
                        title = stringResource(R.string.ai_model_output_modalities)
                    )
                    AiModelSelectionRail(
                        items = modalities,
                        selected = outputModalities,
                        label = { modalityLabel(it) },
                        onItemClick = { modality ->
                            outputModalities = outputModalities.toggle(modality)
                        },
                    )
                }
                if (showAbilities) {
                    NgFormGroupDivider()
                    NgFormPanelSectionTitle(
                        title = stringResource(R.string.ai_model_abilities)
                    )
                    AiModelSelectionRail(
                        items = editableAbilities,
                        selected = abilities,
                        label = { abilityLabel(it) },
                        onItemClick = { ability -> abilities = abilities.toggle(ability) },
                    )
                }
                NgFormGroupDivider()
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun <T> AiModelSelectionRail(
    items: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onItemClick: (T) -> Unit,
) {
    NgFlatActionRail(
        items = items.map { item ->
            NgFlatActionRailItem(
                label = label(item),
                emphasized = item in selected,
            )
        },
        onItemClick = { index -> items.getOrNull(index)?.let(onItemClick) },
        variant = NgFlatActionRailVariant.FORM_TEXT_PICKER,
    )
}

@Composable
private fun modelTypeLabel(type: AiModelType): String = stringResource(
    when (type) {
        AiModelType.CHAT -> R.string.ai_model_type_chat
        AiModelType.IMAGE -> R.string.ai_model_type_image
        AiModelType.EMBEDDING -> R.string.ai_model_type_embedding
        AiModelType.ASR -> R.string.ai_model_type_asr
        AiModelType.TTS -> R.string.ai_model_type_tts
        AiModelType.VIDEO -> R.string.ai_model_type_video
    }
)

@Composable
private fun modalityLabel(modality: AiModelModality): String = stringResource(
    when (modality) {
        AiModelModality.TEXT -> R.string.ai_model_modality_text
        AiModelModality.IMAGE -> R.string.ai_model_modality_image
        AiModelModality.VIDEO -> R.string.ai_model_modality_video
        AiModelModality.AUDIO -> R.string.audio
    }
)

@Composable
private fun abilityLabel(ability: AiModelAbility): String = stringResource(
    when (ability) {
        AiModelAbility.TOOL -> R.string.ai_model_ability_tool
        AiModelAbility.REASONING -> R.string.ai_model_ability_reasoning
        AiModelAbility.ASR -> R.string.ai_model_type_asr
        AiModelAbility.TTS -> R.string.ai_model_type_tts
    }
)

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (value in this) this - value else this + value
}
