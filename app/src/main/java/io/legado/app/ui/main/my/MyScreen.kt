package io.legado.app.ui.main.my

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.theme.NgTheme

internal enum class MyMenuAction {
    BOOK_SOURCE,
    RULE,
    AI,
    READ_ALOUD,
    SERVICE,
    BACKUP,
    SETTINGS,
    BOOKMARK,
    READ_RECORD,
    FILE_MANAGE,
    ABOUT,
}

private data class MyMenuItem(
    val action: MyMenuAction,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int? = null,
    @param:DrawableRes val iconRes: Int,
)

private val primaryItems = listOf(
    MyMenuItem(
        MyMenuAction.BOOK_SOURCE,
        R.string.book_source_manage,
        R.string.book_source_manage_desc,
        R.drawable.ic_cfg_source,
    ),
    MyMenuItem(
        MyMenuAction.RULE,
        R.string.rule_management,
        R.string.rule_management_summary,
        R.drawable.ic_toc,
    ),
    MyMenuItem(
        MyMenuAction.AI,
        R.string.ai_setting,
        R.string.ai_setting_summary,
        R.drawable.ic_ai_setting,
    ),
    MyMenuItem(
        MyMenuAction.READ_ALOUD,
        R.string.read_aloud_settings,
        R.string.read_aloud_settings_summary,
        R.drawable.ic_ai_capability_tts,
    ),
    MyMenuItem(
        MyMenuAction.SERVICE,
        R.string.service_manage,
        R.string.service_manage_desc,
        R.drawable.ic_cfg_web,
    ),
)

private val settingsItems = listOf(
    MyMenuItem(
        MyMenuAction.BACKUP,
        R.string.backup_restore,
        R.string.web_dav_set_import_old,
        R.drawable.ic_cfg_backup,
    ),
    MyMenuItem(
        MyMenuAction.SETTINGS,
        R.string.setting,
        R.string.settings_menu_summary,
        R.drawable.ic_settings,
    ),
)

private val otherItems = listOf(
    MyMenuItem(
        MyMenuAction.BOOKMARK,
        R.string.bookmark,
        R.string.all_bookmark,
        R.drawable.ic_bookmark,
    ),
    MyMenuItem(
        MyMenuAction.READ_RECORD,
        R.string.read_record,
        R.string.read_record_summary,
        R.drawable.ic_history,
    ),
    MyMenuItem(
        MyMenuAction.FILE_MANAGE,
        R.string.file_manage,
        R.string.file_manage_summary,
        R.drawable.ic_folder_outline,
    ),
    MyMenuItem(
        MyMenuAction.ABOUT,
        R.string.about,
        iconRes = R.drawable.ic_cfg_about,
    ),
)

@Composable
internal fun MyScreen(
    bottomInsetPx: Int,
    transparentTopBar: Boolean,
    onAction: (MyMenuAction) -> Unit,
) {
    val bottomInset = with(LocalDensity.current) { bottomInsetPx.toDp() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        MyTopBar(transparent = transparentTopBar)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = bottomInset),
        ) {
            myMenuItems(primaryItems, onAction)
            item(key = "settings_section") { MySectionLabel(R.string.setting) }
            myMenuItems(settingsItems, onAction)
            item(key = "other_section") { MySectionLabel(R.string.other) }
            myMenuItems(otherItems, onAction)
        }
    }
}

@Composable
private fun MyTopBar(transparent: Boolean) {
    val isEInk = NgTheme.snapshot.isEInk
    val background = when {
        isEInk -> Color(NgTheme.colors.surface)
        transparent -> Color.Transparent
        else -> Color(NgTheme.colors.topBarContainer)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!transparent && !isEInk) Modifier.shadow(4.dp) else Modifier)
            .background(background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.my),
                color = Color(NgTheme.colors.onTopBar),
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (isEInk) {
            HorizontalDivider(
                color = Color(NgTheme.colors.outline),
                thickness = 1.dp,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.myMenuItems(
    entries: List<MyMenuItem>,
    onAction: (MyMenuAction) -> Unit,
) {
    items(entries, key = { it.action.name }) { entry ->
        NgSettingsItem(
            title = stringResource(entry.titleRes),
            summary = entry.summaryRes?.let { stringResource(it) },
            summaryMaxLines = 2,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            onClick = { onAction(entry.action) },
            leading = {
                NgSettingsIcon(
                    painter = painterResource(entry.iconRes),
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
private fun MySectionLabel(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
        color = Color(NgTheme.colors.primary),
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
