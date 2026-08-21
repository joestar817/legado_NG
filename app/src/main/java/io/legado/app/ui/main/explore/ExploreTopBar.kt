package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.source.BookSourceGroupIcon
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant

private const val EXPLORE_GROUP_ITEM_ID_BASE = 0x51000000
private const val EXPLORE_LAYOUT_LIST_ITEM_ID = 0x52000001
private const val EXPLORE_LAYOUT_GRID_ITEM_ID = 0x52000002
private const val EXPLORE_LAYOUT_GROUP_GRID_ITEM_ID = 0x52000003

@Composable
internal fun ExploreTopBar(
    query: String,
    groups: List<String>,
    selectedGroup: String?,
    layoutMode: ExploreLayoutMode,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onLayoutModeChange: (ExploreLayoutMode) -> Unit,
    onManageSources: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val actionContainerColor = androidx.compose.ui.res.colorResource(R.color.ng_search_surface)
    val actionContentColor = androidx.compose.ui.res.colorResource(R.color.ng_search_icon)
    val menuItems = remember(groups, selectedGroup, layoutMode) {
        buildList {
            add(
                NgExpandableActionMenuItem(
                    itemId = EXPLORE_LAYOUT_LIST_ITEM_ID,
                    titleRes = R.string.replace_view_list,
                    iconRes = R.drawable.ic_chapter_list,
                    checked = layoutMode == ExploreLayoutMode.LIST
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = EXPLORE_LAYOUT_GRID_ITEM_ID,
                    titleRes = R.string.explore_view_grid,
                    iconRes = R.drawable.ic_grid_menu,
                    checked = layoutMode == ExploreLayoutMode.GRID
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = EXPLORE_LAYOUT_GROUP_GRID_ITEM_ID,
                    titleRes = R.string.explore_view_group_grid,
                    iconRes = R.drawable.ic_folder_outline,
                    checked = layoutMode == ExploreLayoutMode.GROUP_GRID
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_1,
                    titleRes = R.string.all_source,
                    iconRes = R.drawable.ic_check_source,
                    dividerBefore = true,
                    checked = selectedGroup == null
                )
            )
            if (layoutMode != ExploreLayoutMode.GROUP_GRID) {
                groups.forEachIndexed { index, group ->
                    add(
                        NgExpandableActionMenuItem(
                            itemId = EXPLORE_GROUP_ITEM_ID_BASE + index,
                            titleRes = 0,
                            iconRes = BookSourceGroupIcon.resolve(group),
                            title = group,
                            checked = group == selectedGroup
                        )
                    )
                }
            }
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_source_manage,
                    titleRes = R.string.book_source_manage,
                    iconRes = R.drawable.ic_cfg_source,
                    dividerBefore = true,
                )
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NgSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.screen_find),
            modifier = Modifier.weight(1f),
            variant = NgSearchBarVariant.TOOLBAR,
            hideHintOnFocus = true
        )
        Spacer(Modifier.width(12.dp))
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(actionContainerColor)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_grid_menu),
                    contentDescription = stringResource(R.string.group),
                    tint = actionContentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            NgExpandableActionMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = menuItems,
                onItemClick = { item ->
                    menuExpanded = false
                    when (item.itemId) {
                        EXPLORE_LAYOUT_LIST_ITEM_ID -> {
                            onLayoutModeChange(ExploreLayoutMode.LIST)
                        }

                        EXPLORE_LAYOUT_GRID_ITEM_ID -> {
                            onLayoutModeChange(ExploreLayoutMode.GRID)
                        }

                        EXPLORE_LAYOUT_GROUP_GRID_ITEM_ID -> {
                            onLayoutModeChange(ExploreLayoutMode.GROUP_GRID)
                        }

                        R.id.menu_1 -> onGroupSelected(null)
                        R.id.menu_source_manage -> onManageSources()
                        else -> {
                            val index = item.itemId - EXPLORE_GROUP_ITEM_ID_BASE
                            onGroupSelected(groups.getOrNull(index))
                        }
                    }
                }
            )
        }
    }
}
