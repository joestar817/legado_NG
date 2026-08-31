package io.legado.app.ui.config

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField

@Composable
internal fun AiSkillLinkImportDialogContent(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val normalizedUrl = url.trim()
    val confirm = {
        if (normalizedUrl.isNotBlank()) {
            onConfirm(normalizedUrl)
        }
    }

    NgDialog(
        title = stringResource(R.string.import_on_line),
        variant = NgDialogVariant.STANDARD,
        titleFontWeight = FontWeight.Normal,
        actions = {
            NgDialogTextActionButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
            NgDialogTextActionButton(
                text = stringResource(R.string.ok),
                onClick = confirm,
            )
        },
    ) {
        NgFormField(
            label = "",
            value = url,
            onValueChange = { url = it },
            placeholder = "url",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            autoFocus = true,
            variant = NgFormFieldVariant.DIALOG_UNDERLINE,
        )
    }
}
