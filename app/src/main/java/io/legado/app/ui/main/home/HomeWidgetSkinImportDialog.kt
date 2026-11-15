package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.help.home.HomeWidgetSkinImport
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun HomeWidgetSkinImportDialog(draft: HomeWidgetSkinImport, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val replacing = draft.previous != null
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(title = if (replacing) "替换皮肤" else "导入皮肤", actions = {
            NgDialogTextActionButton("取消", onClick = onDismiss)
            NgDialogTextActionButton(if (replacing) "替换" else "导入", onClick = onConfirm)
        }) {
            Text(
                draft.prepared.record.name,
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            draft.preview?.let { bitmap ->
                Image(
                    remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = null,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(172.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
