package io.legado.app.ui.book.bgm

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

internal data class BgmSelectionAction(
    val title: String,
    @param:DrawableRes val icon: Int,
    val action: () -> Unit,
    val enabled: Boolean = true,
    val danger: Boolean = false,
)

private const val BGM_SORT_MENU_ID = 1_000_000

/** Toolbar actions and sorting share one popup; sort options expand in place. */
@Composable
internal fun BgmToolbarMenu(
    actions: List<BgmSelectionAction>,
    sortOptions: List<String>?,
    selectedSortIndex: Int,
    onSort: (Int) -> Unit,
    enabled: Boolean = true,
    sortEnabled: Boolean = true,
    loadedOnly: Boolean = false,
) {
    val listening = LocalBgmListeningMode.current
    val menu = remember { NgPopupToggleState() }
    LaunchedEffect(enabled) { if (!enabled) menu.close() }
    val items = buildList {
        actions.forEachIndexed { index, action ->
            add(NgExpandableActionMenuItem(index + 1, 0, action.icon, title = action.title,
                enabled = enabled && action.enabled, danger = action.danger))
        }
        if (!sortOptions.isNullOrEmpty()) add(NgExpandableActionMenuItem(
            BGM_SORT_MENU_ID, 0, R.drawable.ic_swap_vert, title = "排序",
            enabled = enabled && sortEnabled, dividerBefore = actions.isNotEmpty(),
            children = buildList {
                if (loadedOnly) add(NgExpandableActionMenuItem(BGM_SORT_MENU_ID - 1, 0, 0,
                    title = "仅排序已加载歌曲", enabled = false))
                sortOptions.forEachIndexed { index, title ->
                    add(NgExpandableActionMenuItem(BGM_SORT_MENU_ID + index + 1, 0, 0,
                        title = title, checked = selectedSortIndex == index))
                }
            },
        ))
    }
    Box {
        if (listening) BgmListeningToolbarAction(R.drawable.ic_bgm_grid_menu, "更多", menu::onAnchorClick, enabled)
        else NgFloatingToolbarActionButton(R.drawable.ic_bgm_grid_menu, "更多", menu::onAnchorClick, enabled = enabled)
        NgExpandableActionMenu(menu.expanded, menu::onDismissRequest, items,
            variant = if (listening) NgExpandableActionMenuVariant.DROPDOWN
                else NgExpandableActionMenuVariant.SIDE_SLIDE,
            menuContainerColor = if (listening) Color(NgTheme.colors.surfaceContainerHigh) else null,
            properties = PopupProperties(focusable = true, clippingEnabled = !listening),
            onItemClick = { item ->
                menu.close()
                val sortIndex = item.itemId - BGM_SORT_MENU_ID - 1
                if (enabled && sortEnabled && sortOptions != null && sortIndex in sortOptions.indices) {
                    onSort(sortIndex)
                } else if (enabled) {
                    actions.getOrNull(item.itemId - 1)?.takeIf { it.enabled }?.action?.invoke()
                }
            },
        )
    }
}

/** The source manager's compact selection layout, retaining the music page's white surface. */
@Composable
internal fun BgmSelectionDock(
    selectedCount: Int,
    selectableCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    actions: List<BgmSelectionAction>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusText: String? = null,
) {
    val listening = LocalBgmListeningMode.current
    val menu = remember { NgPopupToggleState() }
    val canOpen = enabled && actions.any { it.enabled }
    LaunchedEffect(canOpen) { if (!canOpen) menu.close() }
    BgmContentPanel(modifier) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(statusText ?: "已选 $selectedCount 首", Modifier.weight(1f), color = Color(NgTheme.colors.onSurface),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.width(220.dp)) {
                NgFlatActionRail(
                    items = listOf(
                        NgFlatActionRailItem(R.drawable.ic_select_all, "全选", enabled = enabled && selectableCount > 0),
                        NgFlatActionRailItem(R.drawable.ic_refresh_black_24dp, "反选", enabled = enabled && selectableCount > 0),
                        NgFlatActionRailItem(R.drawable.ic_more_horiz, "更多", enabled = canOpen, emphasized = menu.expanded),
                    ),
                    onItemClick = { when (it) {
                        0 -> onSelectAll()
                        1 -> onInvertSelection()
                        else -> menu.onAnchorClick()
                    } },
                    variant = NgFlatActionRailVariant.INLINE_DIVIDED,
                    trailingOverlay = {
                        NgExpandableActionMenu(menu.expanded, menu::onDismissRequest,
                            actions.mapIndexed { index, action -> NgExpandableActionMenuItem(
                                index + 1, 0, action.icon, title = action.title,
                                enabled = enabled && action.enabled, danger = action.danger) },
                            onItemClick = { item ->
                                menu.close()
                                actions.getOrNull(item.itemId - 1)?.takeIf { enabled && it.enabled }?.action?.invoke()
                            },
                            width = 174.dp, rowMinHeight = 36.dp,
                            bottomPointerHeight = 8.dp, bottomPointerWidth = 18.dp, bottomPointerEndOffset = 65.dp,
                            menuContainerColor = if (listening) Color(NgTheme.colors.surfaceContainerHigh)
                                else colorResource(R.color.ng_surface_card),
                            offset = DpOffset((-89).dp, (-12).dp),
                            properties = PopupProperties(focusable = true),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun BgmSortMenu(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    loadedOnly: Boolean = false,
) {
    val menu = remember { NgPopupToggleState() }
    LaunchedEffect(enabled) { if (!enabled) menu.close() }
    Box {
        NgFloatingToolbarActionButton(R.drawable.ic_swap_vert, "排序", menu::onAnchorClick, enabled = enabled)
        NgExpandableActionMenu(menu.expanded, menu::onDismissRequest,
            buildList {
                if (loadedOnly) add(NgExpandableActionMenuItem(-1, 0, 0, title = "仅排序已加载歌曲", enabled = false))
                options.forEachIndexed { index, title ->
                    add(NgExpandableActionMenuItem(index + 1, 0, 0, title = title,
                        checked = selectedIndex == index, dividerBefore = loadedOnly && index == 0))
                }
            },
            onItemClick = { item ->
                menu.close()
                if (enabled && item.itemId in 1..options.size) onSelect(item.itemId - 1)
            },
            properties = PopupProperties(focusable = true),
        )
    }
}
