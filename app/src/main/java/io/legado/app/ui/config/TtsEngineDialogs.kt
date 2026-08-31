package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun TtsImportUrlDialogContent(
    initialValue: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    NgDialog(
        title = stringResource(R.string.import_on_line),
        variant = NgDialogVariant.CLASSIC_CONFIRMATION,
        titleFontWeight = FontWeight.Normal,
        actions = {
            NgDialogTextActionButton(
                text = stringResource(android.R.string.cancel),
                onClick = onCancel,
            )
            NgDialogTextActionButton(
                text = stringResource(android.R.string.ok),
                onClick = { onConfirm(value) },
            )
        },
    ) {
        NgFormField(
            label = "",
            value = value,
            onValueChange = { value = it },
            placeholder = stringResource(R.string.tts_engine_url_hint),
            variant = NgFormFieldVariant.DIALOG_UNDERLINE,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
        )
    }
}

@Composable
internal fun TtsImportConflictDialogContent(
    message: String,
    canOverwrite: Boolean,
    onKeepBoth: () -> Unit,
    onOverwrite: () -> Unit,
) {
    NgDialog(
        title = stringResource(R.string.tts_engine_import_conflict_title),
        variant = NgDialogVariant.STANDARD,
        actions = {
            NgFormActionButton(
                text = stringResource(R.string.tts_engine_import_keep_both),
                onClick = onKeepBoth,
                appearance = NgFormActionButtonAppearance.DIALOG,
                buttonHeight = 42.dp,
                minimumWidth = 88.dp,
                textSize = 15.sp,
                textLineHeight = 19.sp,
            )
            if (canOverwrite) {
                NgFormActionButton(
                    text = stringResource(R.string.tts_engine_import_overwrite),
                    onClick = onOverwrite,
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                    buttonHeight = 42.dp,
                    minimumWidth = 88.dp,
                    textSize = 15.sp,
                    textLineHeight = 19.sp,
                )
            }
        },
    ) {
        Text(
            text = message,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
internal fun TtsConfirmDialogContent(
    title: String,
    message: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    NgDialog(
        title = title,
        variant = NgDialogVariant.CLASSIC_CONFIRMATION,
        titleFontWeight = FontWeight.Normal,
        actions = {
            NgDialogTextActionButton(
                text = stringResource(android.R.string.cancel),
                onClick = onCancel,
            )
            NgDialogTextActionButton(
                text = stringResource(android.R.string.ok),
                onClick = onConfirm,
            )
        },
    ) {
        Text(
            text = message,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    }
}

@Composable
internal fun TtsPreviewStyleDialogContent(
    items: List<String>,
    onSelect: (Int) -> Unit,
) {
    NgDialog(
        title = "试听风格",
        variant = NgDialogVariant.LONG_CONTENT,
        actions = {},
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            itemsIndexed(items, key = { index, item -> "$index:$item" }) { index, item ->
                Column {
                    Text(
                        text = item,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 4.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.26f),
                        )
                    }
                }
            }
        }
    }
}
