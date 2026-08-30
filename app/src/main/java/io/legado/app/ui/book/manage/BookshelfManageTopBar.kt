package io.legado.app.ui.book.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgTheme

private const val ALL_GROUP_ITEM_ID = 0x53FFFFFF
private const val UNGROUPED_GROUP_ITEM_ID = 0x53FFFFFE
private const val GROUP_ITEM_ID_BASE = 0x54000000

@Composable
internal fun BookshelfManageTopBar(
    query: String,
    groups: List<BookGroup>,
    selectedGroupId: Long,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onGroupSelected: (groupId: Long) -> Unit,
    onGroupManage: () -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    val actionContentColor = colorResource(R.color.ng_search_icon)
    val headerShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val headerStyle = NgGlassDefaults.bookDetailStyle(
        containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface)
    )
    val groupedEntries = remember(groups) {
        groups.filterNot { it.groupId == BookGroup.IdAll }
    }
    val defaultExpandedItemIds = remember(groupedEntries.size) {
        if (groupedEntries.size <= 10) {
            setOf(R.id.menu_book_group)
        } else {
            emptySet()
        }
    }
    val menuItems = remember(groupedEntries, selectedGroupId) {
        listOf(
            NgExpandableActionMenuItem(
                itemId = R.id.menu_group_manage,
                titleRes = R.string.group_manage,
                iconRes = R.drawable.ic_settings
            ),
            NgExpandableActionMenuItem(
                itemId = ALL_GROUP_ITEM_ID,
                titleRes = R.string.all,
                iconRes = R.drawable.ic_groups,
                checked = selectedGroupId == BookGroup.IdAll
            ),
            NgExpandableActionMenuItem(
                itemId = UNGROUPED_GROUP_ITEM_ID,
                titleRes = R.string.no_group,
                iconRes = R.drawable.ic_groups,
                checked = selectedGroupId == BookGroup.IdNoGroup
            ),
            NgExpandableActionMenuItem(
                itemId = R.id.menu_book_group,
                titleRes = R.string.group,
                iconRes = R.drawable.ic_groups,
                children = groupedEntries.mapIndexed { index, group ->
                    NgExpandableActionMenuItem(
                        itemId = GROUP_ITEM_ID_BASE + index,
                        titleRes = 0,
                        iconRes = R.drawable.ic_groups,
                        title = group.groupName,
                        checked = group.groupId == selectedGroupId
                    )
                }
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = headerShape,
            style = headerStyle
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NgFloatingToolbarBackButton(onClick = onBack)
                    NgSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        hint = stringResource(R.string.search_book_key),
                        modifier = Modifier.weight(1f),
                        variant = NgSearchBarVariant.TOOLBAR,
                        containerColor = Color.Transparent,
                        hideHintOnFocus = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { menuState.onAnchorClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_grid_menu),
                                contentDescription = stringResource(R.string.menu),
                                tint = actionContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        NgExpandableActionMenu(
                            expanded = menuState.expanded,
                            onDismissRequest = menuState::onDismissRequest,
                            items = menuItems,
                            defaultExpandedItemIds = defaultExpandedItemIds,
                            variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                            width = 156.dp,
                            menuContainerColor = colorResource(R.color.ng_surface_card),
                            properties = PopupProperties(
                                focusable = true,
                                clippingEnabled = false
                            ),
                            onItemClick = { item ->
                                menuState.close()
                                when (item.itemId) {
                                    R.id.menu_group_manage -> onGroupManage()
                                    ALL_GROUP_ITEM_ID -> onGroupSelected(BookGroup.IdAll)
                                    UNGROUPED_GROUP_ITEM_ID ->
                                        onGroupSelected(BookGroup.IdNoGroup)

                                    else -> {
                                        val index = item.itemId - GROUP_ITEM_ID_BASE
                                        groupedEntries.getOrNull(index)?.let { group ->
                                            onGroupSelected(group.groupId)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
