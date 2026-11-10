package io.legado.app.ui.font

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.help.font.FontFileEntry
import io.legado.app.help.font.FontUsageReference
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.book.read.ReadFloatingPalette
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme

internal data class FontDeletionRequest(val file: FontFileEntry, val usages: List<FontUsageReference>)

internal enum class FontDeletionConfirmStyle { INTERFACE, READER }

@Composable
internal fun FontDeletionConfirmDialog(
    usages: List<FontUsageReference>,
    busy: Boolean,
    style: FontDeletionConfirmStyle = FontDeletionConfirmStyle.INTERFACE,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val inUse = usages.isNotEmpty()
    Dialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val title = stringResource(R.string.font_delete_confirmation_title)
        val snapshot = NgTheme.snapshot
        val dangerColor = if (style == FontDeletionConfirmStyle.READER && snapshot.isDark) {
            val strength = ReadFloatingAppearanceState.primaryStrengthPercent
            remember(snapshot, strength) {
                Color(ReadFloatingPalette.resolveDangerTextColor(snapshot, strength))
            }
        } else null
        val actions: @Composable RowScope.() -> Unit = {
            NgDialogTextActionButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                secondary = true,
                enabled = !busy,
            )
            NgDialogTextActionButton(
                text = stringResource(if (inUse) R.string.font_delete_and_switch else R.string.delete),
                onClick = onConfirm,
                danger = true,
                enabled = !busy,
                contentColorOverride = dangerColor,
            )
        }
        val message: @Composable ColumnScope.() -> Unit = {
            if (inUse) {
                val separator = stringResource(R.string.font_delete_usage_separator)
                val locations = remember(usages, separator) {
                    usages.joinToString(separator) { it.displayLocation }
                }
                Text(
                    text = stringResource(R.string.font_delete_switch_summary, locations),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        when (style) {
            FontDeletionConfirmStyle.INTERFACE -> NgDialog(
                title = title,
                modifier = Modifier.padding(horizontal = 9.dp).widthIn(max = 520.dp),
                variant = NgDialogVariant.CLASSIC_CONFIRMATION,
                titleFontWeight = FontWeight.Normal,
                actions = actions,
                content = message,
            )

            FontDeletionConfirmStyle.READER -> ReadConfigDialogSurface(
                modifier = Modifier.padding(horizontal = READER_FONT_DIALOG_MARGIN_DP.dp),
                contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 10.dp),
            ) {
                Text(
                    text = title,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Normal,
                )
                if (inUse) Spacer(Modifier.height(8.dp))
                message()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}
