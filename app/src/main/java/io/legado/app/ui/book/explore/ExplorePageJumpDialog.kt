package io.legado.app.ui.book.explore

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant

@Composable
internal fun ExplorePageJumpDialog(
    currentPage: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    // Empty initially so typing a distant page never appends to the current page.
    var input by rememberSaveable(currentPage) { mutableStateOf("") }
    val page = input.takeIf { text -> text.all { it in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..999 }
    var submitted by rememberSaveable(currentPage) { mutableStateOf(false) }
    val submit: () -> Unit = {
        if (page != null && !submitted) {
            submitted = true
            onJump(page)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        NgDialog(
            title = stringResource(R.string.explore_jump_page),
            modifier = Modifier.padding(horizontal = 24.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontSize = 20.sp,
            titleFontWeight = FontWeight.Medium,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    secondary = true
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.explore_jump),
                    onClick = submit,
                    enabled = page != null
                )
            }
        ) {
            NgFormField(
                label = stringResource(R.string.explore_jump_page),
                value = input,
                onValueChange = { input = it },
                placeholder = stringResource(R.string.explore_page_input_hint, currentPage),
                isError = input.isNotEmpty() && page == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                autoFocus = true
            )
        }
    }
}
