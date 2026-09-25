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
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDefaults
import io.legado.app.ui.design.components.compose.NgCompactSettingsDivider
import io.legado.app.ui.design.components.compose.NgCompactSettingsGroup
import io.legado.app.ui.design.components.compose.NgCompactSettingsItem
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
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("EPUB 排版", color = Color(NgTheme.colors.onSurface), fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp, bottom = 16.dp))
                NgCompactSettingsGroup {
                    NgCompactSettingsItem(title = "原书排版优先",
                        trailing = NgSettingsTrailing.SWITCH, checked = choices.getValue("publisher"),
                        onCheckedChange = { change("publisher", it) }, onClick = { change("publisher", !choices.getValue("publisher")) })
                }
                Text("保留原书特性", color = Color(NgTheme.colors.onSurface), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp))
                NgCompactSettingsGroup {
                    EpubLayoutPreferences.features.entries.forEachIndexed { index, (key, title) ->
                        NgCompactSettingsItem(title = title, trailing = NgSettingsTrailing.SWITCH,
                            checked = choices.getValue(key), onCheckedChange = { change(key, it) },
                            onClick = { change(key, !choices.getValue(key)) })
                        if (index < EpubLayoutPreferences.features.size - 1) NgCompactSettingsDivider()
                    }
                }
                Spacer(Modifier.height(12.dp))
                NgCompactSettingsGroup {
                    NgCompactSettingsItem(title = "恢复默认", onClick = {
                        EpubLayoutPreferences.reset(book.bookUrl)
                        choices = EpubLayoutPreferences.read(book.bookUrl)
                        onStyleChanged()
                    })
                }
            }
        }
    }
}
