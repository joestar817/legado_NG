package io.legado.app.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.ai.AiModelType
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun AiAddModelDialog(
    existingModelIds: Set<String>,
    onDismissRequest: () -> Unit,
    onAdd: (String, AiModelType) -> Unit,
    onViewExisting: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var modelType by rememberSaveable { mutableStateOf(AiModelType.CHAT) }
    val modelId = input.trim()
    val duplicate = modelId.isNotEmpty() && modelId in existingModelIds
    val modelIdLabel = stringResource(R.string.ai_model_id)

    NgPaneDialog(onDismissRequest = onDismissRequest) {
        NgDialog(
            title = stringResource(R.string.ai_model_add),
            variant = NgDialogVariant.COMPACT_CONFIRMATION,
            titleFontSize = 17.sp,
            titleFontWeight = FontWeight.Medium,
            actions = {
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    appearance = NgFormActionButtonAppearance.DIALOG,
                    buttonHeight = 40.dp,
                )
                NgFormActionButton(
                    text = stringResource(R.string.add),
                    onClick = { onAdd(modelId, modelType) },
                    modifier = Modifier.weight(1f),
                    enabled = modelId.isNotEmpty() && !duplicate,
                    variant = NgButtonVariant.PRIMARY,
                    buttonHeight = 40.dp,
                )
            },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text(
                    text = modelIdLabel,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                NgFormField(
                    label = modelIdLabel,
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.semantics { contentDescription = modelIdLabel },
                    isError = duplicate,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                )
                if (duplicate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ai_model_add_duplicate),
                            modifier = Modifier.weight(1f),
                            color = Color(NgTheme.colors.error),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                        NgFormActionButton(
                            text = stringResource(R.string.ai_model_view_existing),
                            onClick = { onViewExisting(modelId) },
                            appearance = NgFormActionButtonAppearance.TEXT,
                        )
                    }
                }
            }
            NgFormGroupDivider()
            NgFormSelectRow(
                title = stringResource(R.string.ai_model_type),
                selectedValue = modelType.name,
                options = AiModelType.entries.map { type ->
                    NgFormSelectOption(label = modelTypeLabel(type), value = type.name)
                },
                onValueChange = { modelType = AiModelType.valueOf(it) },
                arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
            )
        }
    }
}
