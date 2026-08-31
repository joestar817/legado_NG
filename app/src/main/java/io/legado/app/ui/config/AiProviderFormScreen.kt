package io.legado.app.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormActionGroup
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSwitchGroup
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRowVariant
import io.legado.app.ui.design.components.compose.NgPasswordField

data class AiProviderFormScreenState(
    val providerId: String = "",
    val builtIn: Boolean = true,
    val providerType: String = "",
    val name: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val chatPath: String = "",
    val timeoutSeconds: String = "",
    val enabled: Boolean = true,
    val streamResponseEnabled: Boolean = false,
    val openAiCompatible: Boolean = true,
    val useCustomBalanceUrl: Boolean = false,
    val balanceUrl: String = "",
    val balanceJsonPath: String = "",
    val useCustomModelsUrl: Boolean = false,
    val modelsUrl: String = ""
)

enum class AiProviderFormField {
    NAME,
    API_KEY,
    BASE_URL,
    CHAT_PATH,
    TIMEOUT_SECONDS,
    BALANCE_URL,
    BALANCE_JSON_PATH,
    MODELS_URL
}

enum class AiProviderFormToggle {
    ENABLED,
    STREAM_RESPONSE,
    CUSTOM_BALANCE_URL,
    CUSTOM_MODELS_URL
}

sealed interface AiProviderFormScreenAction {
    data class FieldChanged(
        val field: AiProviderFormField,
        val value: String
    ) : AiProviderFormScreenAction

    data class ToggleChanged(
        val toggle: AiProviderFormToggle,
        val checked: Boolean
    ) : AiProviderFormScreenAction

    data object TestConnectionRequested : AiProviderFormScreenAction
    data object QueryBalanceRequested : AiProviderFormScreenAction
    data object DeleteRequested : AiProviderFormScreenAction
}

internal fun AiProviderFormScreenState.withField(
    field: AiProviderFormField,
    value: String
): AiProviderFormScreenState {
    return when (field) {
        AiProviderFormField.NAME -> copy(name = value)
        AiProviderFormField.API_KEY -> copy(apiKey = value)
        AiProviderFormField.BASE_URL -> copy(baseUrl = value)
        AiProviderFormField.CHAT_PATH -> copy(chatPath = value)
        AiProviderFormField.TIMEOUT_SECONDS -> copy(timeoutSeconds = value)
        AiProviderFormField.BALANCE_URL -> copy(balanceUrl = value)
        AiProviderFormField.BALANCE_JSON_PATH -> copy(balanceJsonPath = value)
        AiProviderFormField.MODELS_URL -> copy(modelsUrl = value)
    }
}

internal fun AiProviderFormScreenState.withToggle(
    toggle: AiProviderFormToggle,
    checked: Boolean
): AiProviderFormScreenState {
    return when (toggle) {
        AiProviderFormToggle.ENABLED -> copy(enabled = checked)
        AiProviderFormToggle.STREAM_RESPONSE -> copy(streamResponseEnabled = checked)
        AiProviderFormToggle.CUSTOM_BALANCE_URL -> copy(useCustomBalanceUrl = checked)
        AiProviderFormToggle.CUSTOM_MODELS_URL -> copy(useCustomModelsUrl = checked)
    }
}

@Composable
fun AiProviderFormScreen(
    state: AiProviderFormScreenState,
    onAction: (AiProviderFormScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val doneActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProviderTextField(
            label = stringResource(R.string.ai_provider_name),
            value = state.name,
            field = AiProviderFormField.NAME,
            onAction = onAction,
            focusManager = focusManager
        )
        if (!state.builtIn) {
            NgFormField(
                label = stringResource(R.string.ai_provider_type),
                value = state.providerType,
                onValueChange = {},
                readOnly = true
            )
        }
        NgPasswordField(
            label = stringResource(R.string.ai_api_key),
            value = state.apiKey,
            onValueChange = {
                onAction(
                    AiProviderFormScreenAction.FieldChanged(
                        AiProviderFormField.API_KEY,
                        it
                    )
                )
            },
            hiddenIcon = painterResource(R.drawable.ic_visibility_off),
            visibleIcon = painterResource(R.drawable.ic_visibility),
            showPasswordDescription = stringResource(R.string.ai_api_key_toggle_visibility),
            hidePasswordDescription = stringResource(R.string.ai_api_key_toggle_visibility),
            visibilityResetKey = state.providerId,
            keyboardActions = doneActions
        )
        ProviderTextField(
            label = stringResource(R.string.ai_base_url),
            value = state.baseUrl,
            field = AiProviderFormField.BASE_URL,
            onAction = onAction,
            focusManager = focusManager,
            keyboardType = KeyboardType.Uri
        )
        if (state.openAiCompatible) {
            ProviderTextField(
                label = stringResource(R.string.ai_chat_completions_path),
                value = state.chatPath,
                field = AiProviderFormField.CHAT_PATH,
                onAction = onAction,
                focusManager = focusManager
            )
        }
        ProviderTextField(
            label = stringResource(R.string.ai_timeout_seconds),
            value = state.timeoutSeconds,
            field = AiProviderFormField.TIMEOUT_SECONDS,
            onAction = onAction,
            focusManager = focusManager,
            keyboardType = KeyboardType.Number
        )
        NgFormSwitchGroup(modifier = Modifier.padding(top = 6.dp)) {
            NgFormSwitchRow(
                title = stringResource(R.string.ai_enabled),
                checked = state.enabled,
                onCheckedChange = {
                    onAction(
                        AiProviderFormScreenAction.ToggleChanged(
                            AiProviderFormToggle.ENABLED,
                            it
                        )
                    )
                },
                variant = NgFormSwitchRowVariant.GROUPED,
            )
            NgFormGroupDivider(horizontalPadding = 0.dp)
            if (state.openAiCompatible) {
                NgFormSwitchRow(
                    title = stringResource(R.string.ai_stream_response),
                    checked = state.streamResponseEnabled,
                    onCheckedChange = {
                        onAction(
                            AiProviderFormScreenAction.ToggleChanged(
                                AiProviderFormToggle.STREAM_RESPONSE,
                                it
                            )
                        )
                    },
                    variant = NgFormSwitchRowVariant.GROUPED,
                )
                NgFormGroupDivider(horizontalPadding = 0.dp)
            }
            NgFormSwitchRow(
                title = stringResource(R.string.ai_custom_balance_url),
                checked = state.useCustomBalanceUrl,
                onCheckedChange = {
                    onAction(
                        AiProviderFormScreenAction.ToggleChanged(
                            AiProviderFormToggle.CUSTOM_BALANCE_URL,
                            it
                        )
                    )
                },
                variant = NgFormSwitchRowVariant.GROUPED,
            )
            if (state.useCustomBalanceUrl) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProviderTextField(
                        label = stringResource(R.string.ai_balance_url),
                        value = state.balanceUrl,
                        field = AiProviderFormField.BALANCE_URL,
                        onAction = onAction,
                        focusManager = focusManager,
                        keyboardType = KeyboardType.Uri,
                        placeholder = stringResource(R.string.ai_balance_url_summary)
                    )
                    ProviderTextField(
                        label = stringResource(R.string.ai_balance_json_path),
                        value = state.balanceJsonPath,
                        field = AiProviderFormField.BALANCE_JSON_PATH,
                        onAction = onAction,
                        focusManager = focusManager,
                        placeholder = stringResource(R.string.ai_balance_json_path_summary)
                    )
                }
            }
            NgFormGroupDivider(horizontalPadding = 0.dp)
            NgFormSwitchRow(
                title = stringResource(R.string.ai_custom_models_url),
                checked = state.useCustomModelsUrl,
                onCheckedChange = {
                    onAction(
                        AiProviderFormScreenAction.ToggleChanged(
                            AiProviderFormToggle.CUSTOM_MODELS_URL,
                            it
                        )
                    )
                },
                variant = NgFormSwitchRowVariant.GROUPED,
            )
            if (state.useCustomModelsUrl) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    ProviderTextField(
                        label = stringResource(R.string.ai_models_url),
                        value = state.modelsUrl,
                        field = AiProviderFormField.MODELS_URL,
                        onAction = onAction,
                        focusManager = focusManager,
                        keyboardType = KeyboardType.Uri,
                        placeholder = stringResource(R.string.ai_models_url_summary)
                    )
                }
            }
        }
        NgFormActionGroup {
            NgFormActionRow {
                NgFormActionButton(
                    text = stringResource(R.string.ai_test_connection),
                    onClick = {
                        focusManager.clearFocus()
                        onAction(AiProviderFormScreenAction.TestConnectionRequested)
                    },
                    modifier = Modifier.weight(1f),
                    appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
                )
                NgFormActionButton(
                    text = stringResource(R.string.ai_query_balance),
                    onClick = {
                        focusManager.clearFocus()
                        onAction(AiProviderFormScreenAction.QueryBalanceRequested)
                    },
                    modifier = Modifier.weight(1f),
                    appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
                )
            }
            if (!state.builtIn) {
                NgFormActionButton(
                    text = stringResource(R.string.ai_delete_provider),
                    onClick = {
                        focusManager.clearFocus()
                        onAction(AiProviderFormScreenAction.DeleteRequested)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
                )
            }
        }
    }
}

@Composable
private fun ProviderTextField(
    label: String,
    value: String,
    field: AiProviderFormField,
    onAction: (AiProviderFormScreenAction) -> Unit,
    focusManager: FocusManager,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null
) {
    NgFormField(
        label = label,
        value = value,
        onValueChange = {
            onAction(AiProviderFormScreenAction.FieldChanged(field, it))
        },
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
    )
}
