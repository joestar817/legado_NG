package io.legado.app.ui.config

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant

@Composable
internal fun AiAssistantReasoningControls(
    model: AiAssistantConfigUi.AssistantModelOption,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember(model) { mutableIntStateOf(0) }
    val picker = remember(model, revision) { model.reasoningPicker() }
    var editingBudget by remember(model) { mutableStateOf(false) }
    val budgetRange = picker.budgetRange
    AiDiscreteScaleDialogContent(
        title = stringResource(R.string.ai_assistant_reasoning_title),
        description = picker.description(context),
        iconRes = R.drawable.ic_ai_capability_reasoning,
        labels = picker.labels(context),
        currentLabels = picker.currentLabels(context),
        initialSelectedIndex = picker.selectedIndex,
        tintIcon = picker::tintIcon,
        fitLabels = true,
        footer = if (budgetRange == null) null else {
            {
                NgDialogTextActionButton(
                    text = stringResource(R.string.ai_reasoning_budget),
                    onClick = { editingBudget = true },
                )
            }
        },
        onSelectedIndexChanged = { index ->
            picker.select(index)
            revision++
            onChanged()
        },
    )
    if (editingBudget && budgetRange != null) {
        var input by remember { mutableStateOf(picker.budgetTokens?.toString().orEmpty()) }
        val tokens = input.trim().toIntOrNull()?.takeIf { it in budgetRange }
        Dialog(onDismissRequest = { editingBudget = false }) {
            NgDialog(
                title = stringResource(R.string.ai_reasoning_budget),
                variant = NgDialogVariant.EDITOR,
                actions = {
                    NgDialogTextActionButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = { editingBudget = false },
                    )
                    NgDialogTextActionButton(
                        text = stringResource(R.string.save),
                        enabled = tokens != null,
                        onClick = {
                            if (tokens != null) {
                                picker.selectBudget(tokens)
                                editingBudget = false
                                revision++
                                onChanged()
                            }
                        },
                    )
                },
            ) {
                NgFormField(
                    label = stringResource(R.string.ai_reasoning_budget_token_label),
                    value = input,
                    onValueChange = { input = it },
                    isError = input.isNotBlank() && tokens == null,
                    supportingText = stringResource(
                        R.string.ai_reasoning_budget_range, budgetRange.first, budgetRange.last,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                )
            }
        }
    }
}
