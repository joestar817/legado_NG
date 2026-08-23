package io.legado.app.ui.book.character

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun BookStoryboardConfirmationDialog(
    title: String,
    message: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = title,
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(84.dp)
                        .height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.no), fontSize = 15.sp)
                }
                NgButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(84.dp)
                        .height(42.dp),
                    variant = if (destructive) {
                        NgButtonVariant.DANGER
                    } else {
                        NgButtonVariant.PRIMARY
                    },
                ) {
                    Text(stringResource(R.string.yes), fontSize = 15.sp)
                }
            },
        ) {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}
