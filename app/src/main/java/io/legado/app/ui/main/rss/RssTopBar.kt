package io.legado.app.ui.main.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarActionButton
import io.legado.app.ui.design.components.compose.NgSearchBarVariant

private const val RSS_FAVORITE_ITEM_ID = 0x53000001
private const val RSS_GROUP_ITEM_ID = 0x53000002
private const val RSS_ALL_GROUP_ITEM_ID = 0x53000003
private const val RSS_SOURCE_MANAGE_ITEM_ID = 0x53000004
private const val RSS_GROUP_CHILD_ITEM_ID_BASE = 0x53100000

@Composable
internal fun RssTopBar(
    query: String,
    groups: List<String>,
    selectedGroup: String?,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSourceManage: () -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember(groups, selectedGroup) {
        listOf(
            NgExpandableActionMenuItem(
                itemId = RSS_FAVORITE_ITEM_ID,
                titleRes = R.string.favorite,
                iconRes = R.drawable.ic_star
            ),
            NgExpandableActionMenuItem(
                itemId = RSS_GROUP_ITEM_ID,
                titleRes = R.string.group,
                iconRes = R.drawable.ic_groups,
                checked = selectedGroup != null,
                children = buildList {
                    add(
                        NgExpandableActionMenuItem(
                            itemId = RSS_ALL_GROUP_ITEM_ID,
                            titleRes = R.string.all,
                            iconRes = R.drawable.ic_check_source,
                            checked = selectedGroup == null
                        )
                    )
                    groups.forEachIndexed { index, group ->
                        add(
                            NgExpandableActionMenuItem(
                                itemId = RSS_GROUP_CHILD_ITEM_ID_BASE + index,
                                titleRes = 0,
                                iconRes = R.drawable.ic_groups,
                                title = group,
                                checked = group == selectedGroup
                            )
                        )
                    }
                }
            ),
            NgExpandableActionMenuItem(
                itemId = RSS_SOURCE_MANAGE_ITEM_ID,
                titleRes = R.string.rss_source_manage,
                iconRes = R.drawable.ic_settings,
                dividerBefore = true
            )
        )
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
            hint = stringResource(R.string.search_rss_source),
            modifier = Modifier.weight(1f),
            variant = NgSearchBarVariant.TOOLBAR,
            hideHintOnFocus = true
        )
        Spacer(Modifier.width(12.dp))
        Box {
            NgSearchBarActionButton(
                onClick = menuState::onAnchorClick,
                contentDescription = stringResource(R.string.group),
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        RSS_FAVORITE_ITEM_ID -> onOpenFavorites()
                        RSS_ALL_GROUP_ITEM_ID -> onGroupSelected(null)
                        RSS_SOURCE_MANAGE_ITEM_ID -> onOpenSourceManage()
                        else -> {
                            val index = item.itemId - RSS_GROUP_CHILD_ITEM_ID_BASE
                            onGroupSelected(groups.getOrNull(index))
                        }
                    }
                }
            )
        }
    }
}
