package io.legado.app.ui.rss.source.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
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
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun RssSourceManageBottomDock(
    selectedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onAction: (RssSourceManageAction) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val enabled = selectedCount > 0
    val dockShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    NgGlassSurface(
        modifier = modifier.fillMaxWidth(),
        role = NgMaterialRole.CONTROL,
        shape = dockShape,
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_control_surface)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.rss_source_selected_count,
                        selectedCount
                    ),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.width(132.dp)) {
                    NgFlatActionRail(
                        items = listOf(
                            NgFlatActionRailItem(
                                iconRes = R.drawable.ic_select_all,
                                label = stringResource(R.string.select_all),
                                enabled = totalCount > 0
                            ),
                            NgFlatActionRailItem(
                                iconRes = R.drawable.ic_refresh_black_24dp,
                                label = stringResource(R.string.revert_selection),
                                enabled = totalCount > 0
                            )
                        ),
                        onItemClick = { index ->
                            onAction(
                                if (index == 0) {
                                    RssSourceManageAction.SelectAll
                                } else {
                                    RssSourceManageAction.InvertSelection
                                }
                            )
                        },
                        variant = NgFlatActionRailVariant.COMPACT_SEGMENTED
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = Color(NgTheme.colors.outlineVariant).copy(
                    alpha = if (NgTheme.snapshot.isEInk) 1f else 0.24f
                )
            )
            Spacer(Modifier.height(3.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                NgFlatActionRail(
                    items = listOf(
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_check_circle_outline,
                            label = stringResource(R.string.enable),
                            enabled = enabled
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_block_outline,
                            label = stringResource(R.string.disable),
                            enabled = enabled
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_bookshelf_action_folder,
                            label = stringResource(R.string.group),
                            enabled = enabled
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_more_horiz,
                            label = stringResource(R.string.more),
                            enabled = enabled
                        )
                    ),
                    onItemClick = { index ->
                        when (index) {
                            0 -> onAction(RssSourceManageAction.EnableSelection)
                            1 -> onAction(RssSourceManageAction.DisableSelection)
                            2 -> onAction(RssSourceManageAction.AddSelectionToGroup)
                            else -> menuExpanded = true
                        }
                    },
                    variant = NgFlatActionRailVariant.SPACED_COMPACT,
                    trailingOverlay = {
                        NgExpandableActionMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            items = rssSourceDockMoreItems(),
                            onItemClick = { item ->
                                menuExpanded = false
                                onAction(item.toManageAction())
                            },
                            width = 152.dp,
                            rowMinHeight = 36.dp,
                            bottomPointerHeight = 8.dp,
                            bottomPointerWidth = 18.dp,
                            bottomPointerEndOffset = 26.dp,
                            menuContainerColor = colorResource(R.color.ng_surface_card),
                            offset = DpOffset(x = (-106).dp, y = (-45).dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun rssSourceDockMoreItems(): List<NgExpandableActionMenuItem> = listOf(
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
