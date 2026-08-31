package io.legado.app.ui.config

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgTheme

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
