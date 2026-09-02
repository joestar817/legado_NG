package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgTheme

internal sealed interface BackupConfigBusinessDialog {
    data class Ignore(val selected: List<Boolean>) : BackupConfigBusinessDialog
    data class RestoreFiles(val names: List<String>) : BackupConfigBusinessDialog
    data class RestoreError(val message: String) : BackupConfigBusinessDialog
}

@Composable
internal fun BackupConfigBusinessDialogHost(
    dialog: BackupConfigBusinessDialog?,
    ignoreTitles: List<String>,
    cancelText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onIgnoreChanged: (Int, Boolean) -> Unit,
    onRestoreFileSelected: (String) -> Unit,
    onRestoreFromLocal: () -> Unit,
) {
    when (dialog) {
        is BackupConfigBusinessDialog.Ignore -> BackupIgnoreDialog(
            titles = ignoreTitles,
            selected = dialog.selected,
            onDismiss = onDismiss,
            onChanged = onIgnoreChanged,
        )

        is BackupConfigBusinessDialog.RestoreFiles -> ConfigChoiceDialog(
            title = stringResource(R.string.select_restore_file),
            options = dialog.names.map { ConfigChoiceOption(it, it) },
            onDismissRequest = onDismiss,
            onSelected = onRestoreFileSelected,
        )

        is BackupConfigBusinessDialog.RestoreError -> ConfigConfirmationDialog(
            title = stringResource(R.string.restore),
            message = dialog.message,
            cancelText = cancelText,
            confirmText = confirmText,
            onDismissRequest = onDismiss,
            onConfirm = onRestoreFromLocal,
        )

        null -> Unit
    }
}

@Composable
private fun BackupIgnoreDialog(
    titles: List<String>,
    selected: List<Boolean>,
    onDismiss: () -> Unit,
    onChanged: (Int, Boolean) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.restore_ignore),
            modifier = Modifier.padding(horizontal = 18.dp),
            variant = NgDialogVariant.STANDARD,
            titleFontWeight = FontWeight.Normal,
            actions = {},
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                itemsIndexed(
                    items = titles,
                    key = { index, _ -> index },
                ) { index, title ->
                    val checked = selected.getOrElse(index) { false }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onChanged(index, !checked) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onChanged(index, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(NgTheme.colors.primary),
                                checkmarkColor = Color.White,
                                uncheckedColor = Color(NgTheme.colors.onSurfaceVariant),
                            ),
                        )
                        Text(
                            text = title,
                            modifier = Modifier.padding(start = 8.dp),
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    if (index < titles.lastIndex) NgDialogDivider()
                }
            }
        }
    }
}

@Composable
internal fun BackupTextInputDialogContent(
    title: String,
    initialValue: String,
    placeholder: String,
    cancelText: String,
    confirmText: String,
    password: Boolean,
    message: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val confirm = { onConfirm(value) }
    NgDialog(
        title = title,
        variant = NgDialogVariant.STANDARD,
        titleFontWeight = FontWeight.Normal,
        actions = {
            NgDialogTextActionButton(
                text = cancelText,
                onClick = onCancel,
            )
            NgDialogTextActionButton(
                text = confirmText,
                onClick = confirm,
            )
        },
    ) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        NgFormField(
            label = "",
            value = value,
            onValueChange = { value = it },
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            variant = NgFormFieldVariant.DIALOG_UNDERLINE,
            autoFocus = true,
        )
    }
}
