package io.legado.app.ui.book.changesource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRowVariant
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDrawerThemeProvider
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ChangeSourceSettingsContent(
    settings: ChangeChapterSourceSettingsUi,
    blockSourceDialogs: Boolean,
    onBack: () -> Unit,
    onToggleSameTypeOnly: () -> Unit,
    onToggleBlockSourceDialogs: () -> Unit,
    onToggleCheckAuthor: () -> Unit,
    onToggleLoadWordCount: () -> Unit,
    onToggleLoadInfo: () -> Unit,
    onToggleLoadToc: () -> Unit,
) {
    NgDrawerThemeProvider(
        snapshot = NgTheme.snapshot,
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        hasThemeProfile = false,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(24.dp),
                        tint = Color(NgTheme.colors.onSurface),
                    )
                }
                Text(
                    text = stringResource(R.string.change_source_settings),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "search_behavior") {
                    NgFormGroup(title = stringResource(R.string.change_source_search_behavior)) {
                        ChangeSourceSwitchRow(
                            titleRes = R.string.change_source_same_type_only,
                            summaryRes = R.string.change_source_same_type_only_summary,
                            checked = settings.sameTypeOnly,
                            onToggle = onToggleSameTypeOnly,
                        )
                        NgFormGroupDivider()
                        ChangeSourceSwitchRow(
                            titleRes = R.string.block_source_dialogs,
                            summaryRes = R.string.change_source_block_dialogs_summary,
                            checked = blockSourceDialogs,
                            onToggle = onToggleBlockSourceDialogs,
                        )
                        NgFormGroupDivider()
                        ChangeSourceSwitchRow(
                            titleRes = R.string.checkAuthor,
                            summaryRes = R.string.change_source_check_author_summary,
                            checked = settings.checkAuthor,
                            onToggle = onToggleCheckAuthor,
                        )
                    }
                }
                item(key = "result_info") {
                    NgFormGroup(title = stringResource(R.string.change_source_result_info)) {
                        ChangeSourceSwitchRow(
                            titleRes = R.string.load_word_count,
                            summaryRes = R.string.change_source_word_count_summary,
                            checked = settings.loadWordCount,
                            onToggle = onToggleLoadWordCount,
                        )
                        NgFormGroupDivider()
                        ChangeSourceSwitchRow(
                            titleRes = R.string.load_info,
                            summaryRes = R.string.change_source_info_summary,
                            checked = settings.loadInfo,
                            onToggle = onToggleLoadInfo,
                        )
                        NgFormGroupDivider()
                        ChangeSourceSwitchRow(
                            titleRes = R.string.load_toc,
                            summaryRes = R.string.change_source_toc_summary,
                            checked = settings.loadToc,
                            onToggle = onToggleLoadToc,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeSourceSwitchRow(
    titleRes: Int,
    summaryRes: Int,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    NgFormSwitchSettingRow(
        title = stringResource(titleRes),
        summary = stringResource(summaryRes),
        checked = checked,
        onCheckedChange = { onToggle() },
        variant = NgFormSwitchSettingRowVariant.CHANGE_SOURCE,
    )
}

@Composable
internal fun ChangeSourceMoreMenu(
    settings: ChangeChapterSourceSettingsUi,
    onOpenSourceManage: () -> Unit,
    onOpenSettings: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onClose: () -> Unit,
    onRefreshList: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val groupTitle = if (settings.selectedGroup.isBlank()) {
        stringResource(R.string.group)
    } else {
        "${stringResource(R.string.group)}(${settings.selectedGroup})"
    }
    val allGroupsTitle = stringResource(R.string.change_source_all_groups)
    val menuItems = buildList {
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_SOURCE_MANAGE,
                titleRes = R.string.book_source_manage,
                iconRes = R.drawable.ic_cfg_source,
            )
        )
        if (onRefreshList != null) {
            add(
                NgExpandableActionMenuItem(
                    itemId = MENU_REFRESH_LIST,
                    titleRes = R.string.refresh_list,
                    iconRes = R.drawable.ic_refresh_black_24dp,
                )
            )
        }
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_SETTINGS,
                titleRes = R.string.change_source_settings,
                iconRes = R.drawable.ic_settings,
                navigation = true,
            )
        )
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_GROUP,
                titleRes = 0,
                title = groupTitle,
                iconRes = R.drawable.ic_groups,
                children = buildList {
                    add(
                        NgExpandableActionMenuItem(
                            itemId = MENU_GROUP_ALL,
                            titleRes = 0,
                            title = allGroupsTitle,
                            iconRes = 0,
                            checked = settings.selectedGroup.isBlank(),
                        )
                    )
                    settings.groups.forEachIndexed { index, group ->
                        add(
                            NgExpandableActionMenuItem(
                                itemId = MENU_GROUP_BASE + index,
                                titleRes = 0,
                                title = group,
                                iconRes = 0,
                                checked = settings.selectedGroup == group,
                            )
                        )
                    }
                },
            )
        )
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_CLOSE,
                titleRes = R.string.close,
                iconRes = R.drawable.ic_baseline_close,
            )
        )
    }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(24.dp),
            )
        }
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = menuItems,
            onItemClick = { item ->
                expanded = false
                when (item.itemId) {
                    MENU_SOURCE_MANAGE -> onOpenSourceManage()
                    MENU_REFRESH_LIST -> onRefreshList?.invoke()
                    MENU_SETTINGS -> onOpenSettings()
                    MENU_GROUP_ALL -> onGroupSelected("")
                    MENU_CLOSE -> onClose()
                    else -> {
                        val index = item.itemId - MENU_GROUP_BASE
                        settings.groups.getOrNull(index)?.let(onGroupSelected)
                    }
                }
            },
            width = 152.dp,
            rowMinHeight = 44.dp,
            menuContainerColor = Color(NgTheme.colors.cardContainer).copy(alpha = 1f),
            variant = NgExpandableActionMenuVariant.DRILL_IN,
            properties = PopupProperties(focusable = true, clippingEnabled = false),
        )
    }
}

private const val MENU_SOURCE_MANAGE = 0x62000001
private const val MENU_REFRESH_LIST = 0x62000002
private const val MENU_SETTINGS = 0x62000003
private const val MENU_GROUP = 0x62000007
private const val MENU_GROUP_ALL = 0x62000008
private const val MENU_CLOSE = 0x62000009
private const val MENU_GROUP_BASE = 0x62000100
