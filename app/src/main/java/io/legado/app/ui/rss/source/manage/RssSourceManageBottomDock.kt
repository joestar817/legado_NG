package io.legado.app.ui.rss.source.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun RssSourceManageBottomDock(
    selectedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onAction: (RssSourceManageAction) -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    NgGlassSurface(
        modifier = modifier.fillMaxWidth(),
        role = NgMaterialRole.CONTROL,
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_control_surface)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.rss_source_selected_count,
                    selectedCount
                ),
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.width(220.dp)) {
                NgFlatActionRail(
                    items = listOf(
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_select_all,
                            label = stringResource(R.string.select_all),
                            enabled = totalCount > 0,
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_refresh_black_24dp,
                            label = stringResource(R.string.revert_selection),
                            enabled = totalCount > 0,
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_more_horiz,
                            label = stringResource(R.string.more),
                            enabled = selectedCount > 0,
                            emphasized = menuState.expanded,
                        ),
                    ),
                    onItemClick = { index ->
                        when (index) {
                            0 -> onAction(RssSourceManageAction.SelectAll)
                            1 -> onAction(RssSourceManageAction.InvertSelection)
                            else -> menuState.onAnchorClick()
                        }
                    },
                    variant = NgFlatActionRailVariant.INLINE_DIVIDED,
                    trailingOverlay = {
                        NgExpandableActionMenu(
                            expanded = menuState.expanded,
                            onDismissRequest = menuState::onDismissRequest,
                            items = rssSourceDockMoreItems(),
                            onItemClick = { item ->
                                menuState.close()
                                onAction(item.toManageAction())
                            },
                            width = 174.dp,
                            rowMinHeight = 36.dp,
                            bottomPointerHeight = 8.dp,
                            bottomPointerWidth = 18.dp,
                            bottomPointerEndOffset = 65.dp,
                            menuContainerColor = colorResource(R.color.ng_surface_card),
                            offset = DpOffset(x = (-89).dp, y = (-12).dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun rssSourceDockMoreItems(): List<NgExpandableActionMenuItem> = listOf(
    NgExpandableActionMenuItem(
        itemId = R.id.menu_enable_selection,
        titleRes = R.string.enable,
        iconRes = R.drawable.ic_check_circle_outline,
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_disable_selection,
        titleRes = R.string.disable,
        iconRes = R.drawable.ic_block_outline,
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_add_group,
        titleRes = R.string.group,
        iconRes = R.drawable.ic_bookshelf_action_folder,
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_remove_group,
        titleRes = R.string.remove_group,
        iconRes = R.drawable.ic_folder_open
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_top_sel,
        titleRes = R.string.selection_to_top,
        iconRes = R.drawable.ic_arrow_drop_up
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_bottom_sel,
        titleRes = R.string.selection_to_bottom,
        iconRes = R.drawable.ic_arrow_down
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_export_selection,
        titleRes = R.string.export_selection,
        iconRes = R.drawable.ic_export
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_share_source,
        titleRes = R.string.share_selected_source,
        iconRes = R.drawable.ic_share
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_check_selected_interval,
        titleRes = R.string.check_selected_interval,
        iconRes = R.drawable.ic_network_check
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_del_selection,
        titleRes = R.string.delete,
        iconRes = R.drawable.ic_book_info_delete,
        dividerBefore = true,
        danger = true
    )
)

private fun NgExpandableActionMenuItem.toManageAction(): RssSourceManageAction {
    return when (itemId) {
        R.id.menu_enable_selection -> RssSourceManageAction.EnableSelection
        R.id.menu_disable_selection -> RssSourceManageAction.DisableSelection
        R.id.menu_add_group -> RssSourceManageAction.AddSelectionToGroup
        R.id.menu_remove_group -> RssSourceManageAction.RemoveSelectionFromGroup
        R.id.menu_top_sel -> RssSourceManageAction.TopSelection
        R.id.menu_bottom_sel -> RssSourceManageAction.BottomSelection
        R.id.menu_export_selection -> RssSourceManageAction.ExportSelection
        R.id.menu_share_source -> RssSourceManageAction.ShareSelection
        R.id.menu_check_selected_interval -> {
            RssSourceManageAction.CompleteSelectionInterval
        }
        else -> RssSourceManageAction.DeleteSelection
    }
}
