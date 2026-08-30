package io.legado.app.ui.association

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme

/** 规则管理共用的书源管理同款紧凑删除确认。 */
@Composable
fun RuleDeleteConfirmDialog(
    itemName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message = stringResource(R.string.sure_del)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.draw),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .widthIn(max = 520.dp)
                .heightIn(min = 156.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.no),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.yes),
                    onClick = onConfirm,
                )
            },
        ) {
            Text(
                text = buildString {
                    append(message)
                    itemName?.let {
                        append('\n')
                        append(it)
                    }
                },
                color = Color(NgTheme.colors.onSurface),
                fontSize = 18.sp,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
