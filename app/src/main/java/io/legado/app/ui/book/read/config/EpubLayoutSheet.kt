package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.Book
import io.legado.app.help.config.EpubLayoutPreferences
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDefaults
import io.legado.app.ui.design.theme.NgTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubLayoutSheet(
    book: Book,
    onStyleChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxHeight = minOf(READ_MORE_CONFIG_WINDOW_HEIGHT_DP.dp, LocalConfiguration.current.screenHeightDp.dp)
    val appearance = NgDrawerDefaults.currentAppearance().copy(horizontalMarginDp = 0, cornerRadiusDp = 20)
    var choices by remember(book.bookUrl) { mutableStateOf(EpubLayoutPreferences.read(book.bookUrl)) }
    fun change(key: String, value: Boolean) {
        EpubLayoutPreferences.set(book.bookUrl, key, value)
        choices = EpubLayoutPreferences.read(book.bookUrl)
        onStyleChanged()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dp.Unspecified,
        containerColor = Color.Transparent, shape = RectangleShape, dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(top = 8.dp),
            appearance = appearance,
        ) {
            val contentColor = Color(NgTheme.colors.onSurface)
            Column(
                Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(top = 10.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "EPUB 排版",
                    modifier = Modifier.height(42.dp).padding(horizontal = 16.dp),
                    color = contentColor,
                    fontSize = 20.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Medium,
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SwitchSettingRow(
                        title = "原书排版优先",
                        checked = choices.getValue("publisher"),
                        onCheckedChange = { change("publisher", it) },
                    )
                    ReadMoreDivider(contentColor)
                    EpubLayoutPreferences.features.entries.forEach { (key, title) ->
                        SwitchSettingRow(
                            title = title,
                            checked = choices.getValue(key),
                            onCheckedChange = { change(key, it) },
                        )
                        ReadMoreDivider(contentColor)
                    }
                    ActionSettingRow(title = "恢复默认", onClick = {
                        EpubLayoutPreferences.reset(book.bookUrl)
                        choices = EpubLayoutPreferences.read(book.bookUrl)
                        onStyleChanged()
                    })
                }
            }
        }
    }
}
