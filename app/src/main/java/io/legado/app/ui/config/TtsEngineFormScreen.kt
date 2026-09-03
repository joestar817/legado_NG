package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.tts.DEFAULT_TTS_RANDOM_NUMBER_DIGITS
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgFormFieldGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSelectField
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSwitchGroup
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRowVariant
import io.legado.app.ui.design.components.compose.NgPasswordField
import io.legado.app.ui.design.theme.NgTheme

data class TtsEngineFormScreenState(
    val engineId: String = "",
    val engineEnabled: Boolean = true,
    val formEnabled: Boolean = true,
    val loading: Boolean = false,
    val fields: List<TtsEngineFormFieldState> = emptyList()
)

data class TtsEngineFormFieldState(
    val key: String,
    val label: String,
    val value: String,
    val type: TtsEngineFormFieldType,
    val options: List<TtsEngineFormOption> = emptyList(),
    val randomNumberDigits: Int = DEFAULT_TTS_RANDOM_NUMBER_DIGITS,
    val randomNumberAllowsLeadingZero: Boolean = false
)

data class TtsEngineFormOption(
    val label: String,
    val value: String
)

enum class TtsEngineFormFieldType {
    TEXT,
    PASSWORD,
    NUMBER,
    SELECT,
    BOOLEAN,
    RANDOM_NUMBER
}

sealed interface TtsEngineFormScreenAction {
    data class FieldChanged(
        val engineId: String,
        val key: String,
        val value: String
    ) : TtsEngineFormScreenAction

    data class FieldEditFinished(
        val engineId: String,
        val key: String
    ) : TtsEngineFormScreenAction

    data class RandomNumberRegenerateRequested(
        val engineId: String,
        val key: String
    ) : TtsEngineFormScreenAction

    data class EngineEnabledChanged(
        val engineId: String,
        val checked: Boolean
    ) : TtsEngineFormScreenAction
}

internal fun TtsEngineFormScreenState.withFieldValue(
    key: String,
    value: String
): TtsEngineFormScreenState {
    return copy(
        fields = fields.map { field ->
            if (field.key == key) field.copy(value = value) else field
        }
    )
}

internal fun String?.toTtsBooleanOption(): Boolean {
    return when (this?.trim()?.lowercase()) {
        "true", "1", "yes", "y", "on", "enable", "enabled", "启用", "是" -> true
        else -> false
    }
}

@Composable
fun TtsEngineFormScreen(
    state: TtsEngineFormScreenState,
    onAction: (TtsEngineFormScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val regularFields = state.fields.filterNot {
        it.type == TtsEngineFormFieldType.BOOLEAN
    }
    val switchFields = state.fields.filter {
        it.type == TtsEngineFormFieldType.BOOLEAN
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!state.loading) {
            if (regularFields.isNotEmpty()) {
                NgFormFieldGroup {
                    regularFields.forEach { field ->
                        key(field.key) {
                            TtsEngineFormField(
                                engineId = state.engineId,
                                field = field,
                                enabled = state.formEnabled,
                                focusManager = focusManager,
                                onAction = onAction
                            )
                        }
                    }
                }
            }
            NgFormSwitchGroup(modifier = Modifier.padding(top = 6.dp)) {
                switchFields.forEach { field ->
                    key(field.key) {
                        NgFormSwitchRow(
                            title = field.label,
                            checked = field.value.toTtsBooleanOption(),
                            onCheckedChange = {
                                onAction(
                                    TtsEngineFormScreenAction.FieldChanged(
                                        state.engineId,
                                        field.key,
                                        it.toString()
                                    )
                                )
                            },
                            enabled = state.formEnabled,
                            variant = NgFormSwitchRowVariant.GROUPED,
                        )
                        NgFormGroupDivider(horizontalPadding = 0.dp)
                    }
                }
                NgFormSwitchRow(
                    title = stringResource(R.string.enabled),
                    checked = state.engineEnabled,
                    onCheckedChange = {
                        focusManager.clearFocus()
                        onAction(
                            TtsEngineFormScreenAction.EngineEnabledChanged(
                                state.engineId,
                                it
                            )
                        )
                    },
                    variant = NgFormSwitchRowVariant.GROUPED,
                )
            }
        }
    }
}

@Composable
fun TtsEngineFormActions(
    sourceMode: Boolean,
    onToggleSourceMode: () -> Unit,
    onMeasureLatency: () -> Unit,
    onSaveSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NgFormActionRow(modifier = modifier.padding(vertical = 4.dp)) {
        NgFormActionButton(
            text = stringResource(
                if (sourceMode) R.string.tts_form_mode else R.string.tts_source_mode
            ),
            onClick = onToggleSourceMode,
            modifier = Modifier.weight(1f),
            appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
        )
        NgFormActionButton(
            text = "测速",
            onClick = onMeasureLatency,
            modifier = Modifier.weight(1f),
            appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
        )
        if (sourceMode) {
            NgFormActionButton(
                text = stringResource(R.string.save),
                onClick = onSaveSource,
                modifier = Modifier.weight(1f),
                appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
            )
        }
    }
}

@Composable
private fun TtsEngineFormField(
    engineId: String,
    field: TtsEngineFormFieldState,
    enabled: Boolean,
    focusManager: FocusManager,
    onAction: (TtsEngineFormScreenAction) -> Unit
) {
    val fieldChanged: (String) -> Unit = {
        onAction(TtsEngineFormScreenAction.FieldChanged(engineId, field.key, it))
    }
    val editFinished = {
        onAction(TtsEngineFormScreenAction.FieldEditFinished(engineId, field.key))
    }
    val doneActions = KeyboardActions(onDone = {
        focusManager.clearFocus()
        editFinished()
    })
    when (field.type) {
        TtsEngineFormFieldType.TEXT -> NgFormField(
            label = field.label,
            value = field.value,
            onValueChange = fieldChanged,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = doneActions,
            onFocusLost = editFinished
        )

        TtsEngineFormFieldType.PASSWORD -> NgPasswordField(
            label = field.label,
            value = field.value,
            onValueChange = fieldChanged,
            hiddenIcon = painterResource(R.drawable.ic_visibility_off),
            visibleIcon = painterResource(R.drawable.ic_visibility),
            showPasswordDescription = stringResource(R.string.tts_show_password),
            hidePasswordDescription = stringResource(R.string.tts_hide_password),
            visibilityResetKey = "$engineId:${field.key}",
            enabled = enabled,
            keyboardActions = doneActions,
            onFocusLost = editFinished
        )

        TtsEngineFormFieldType.NUMBER -> NgFormField(
            label = field.label,
            value = field.value,
            onValueChange = fieldChanged,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                // Compose 没有同时表达 decimal + signed 的数字键盘类型；使用文本键盘
                // 保留旧 View 表单输入负数和编辑中间态（如 "-"、"-."）的能力。
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = doneActions,
            onFocusLost = editFinished
        )

        TtsEngineFormFieldType.SELECT -> NgFormSelectField(
            label = field.label,
            selectedValue = field.value,
            options = field.options.map { option ->
                NgFormSelectOption(option.label, option.value)
            },
            onValueChange = fieldChanged,
            arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
            enabled = enabled
        )

        TtsEngineFormFieldType.BOOLEAN -> NgFormSwitchRow(
            title = field.label,
            checked = field.value.toTtsBooleanOption(),
            onCheckedChange = { fieldChanged(it.toString()) },
            enabled = enabled
        )

        TtsEngineFormFieldType.RANDOM_NUMBER -> NgFormField(
            label = field.label,
            value = field.value,
            onValueChange = {},
            enabled = enabled,
            readOnly = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingContent = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(enabled = enabled) {
                            focusManager.clearFocus()
                            onAction(
                                TtsEngineFormScreenAction.RandomNumberRegenerateRequested(
                                    engineId,
                                    field.key
                                )
                            )
                        }
                        .padding(7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh_black_24dp),
                        contentDescription = stringResource(R.string.tts_regenerate_random_number),
                        tint = Color(NgTheme.colors.onSurfaceVariant)
                    )
                }
            }
        )
    }
}
