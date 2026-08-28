package io.legado.app.ui.book.manage

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
import io.legado.app.ui.design.components.compose.NgThemedActionIconKind
import io.legado.app.ui.design.theme.NgTheme

internal enum class BookshelfManageDockAction {
    CACHE,
    EXPORT_CONTENT,
    GROUP,
    EXPORT_SOURCE,
    CHANGE_SOURCE,
    ENABLE_UPDATE,
    DISABLE_UPDATE,
    REMOVE_GROUP,
    CLEAR_CACHE,
    DELETE
}

@Composable
internal fun BookshelfManageBottomDock(
    selectedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAction: (BookshelfManageDockAction) -> Unit
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
                        R.string.bookshelf_manage_selected_count,
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
                            if (index == 0) {
                                onSelectAll()
                            } else {
                                onInvertSelection()
                            }
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
                            iconRes = R.drawable.ic_bookshelf_action_download,
                            label = stringResource(R.string.book_cache),
                            enabled = enabled
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_bookshelf_action_upload,
                            label = stringResource(R.string.export),
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
                            0 -> onAction(BookshelfManageDockAction.CACHE)
                            1 -> onAction(BookshelfManageDockAction.EXPORT_CONTENT)
                            2 -> onAction(BookshelfManageDockAction.GROUP)
                            else -> menuExpanded = true
                        }
                    },
                    variant = NgFlatActionRailVariant.SPACED_COMPACT,
                    trailingOverlay = {
                        NgExpandableActionMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            items = dockMoreItems(),
                            onItemClick = { item ->
                                menuExpanded = false
                                onAction(item.toDockAction())
                            },
                            width = 136.dp,
                            rowMinHeight = 36.dp,
                            bottomPointerHeight = 8.dp,
                            bottomPointerWidth = 18.dp,
                            bottomPointerEndOffset = 26.dp,
                            menuContainerColor = colorResource(R.color.ng_surface_card),
                            offset = androidx.compose.ui.unit.DpOffset(
                                x = (-90).dp,
                                y = (-45).dp
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun dockMoreItems(): List<NgExpandableActionMenuItem> = listOf(
    NgExpandableActionMenuItem(
        itemId = R.id.menu_export_selection,
        titleRes = R.string.export_book_source,
        iconRes = R.drawable.ic_export
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_change_source,
        titleRes = R.string.change_source_batch,
        iconRes = R.drawable.ic_swap_horiz
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_update_enable,
        titleRes = R.string.allow_update,
        iconRes = R.drawable.ic_check_circle_outline
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_update_disable,
        titleRes = R.string.disable_update,
        iconRes = R.drawable.ic_block_outline
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_remove_to_group,
        titleRes = R.string.set_ungrouped,
        iconRes = R.drawable.ic_folder_open
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_clear_cache,
        titleRes = R.string.clear_cache,
        iconRes = R.drawable.ic_clear_all,
        themedIconKind = NgThemedActionIconKind.CLEAR_CACHE
    ),
    NgExpandableActionMenuItem(
        itemId = R.id.menu_del_selection,
        titleRes = R.string.delete,
        iconRes = R.drawable.ic_book_info_delete,
        dividerBefore = true,
        danger = true
    )
)

private fun NgExpandableActionMenuItem.toDockAction(): BookshelfManageDockAction {
    return when (itemId) {
        R.id.menu_export_selection -> BookshelfManageDockAction.EXPORT_SOURCE
        R.id.menu_change_source -> BookshelfManageDockAction.CHANGE_SOURCE
        R.id.menu_update_enable -> BookshelfManageDockAction.ENABLE_UPDATE
        R.id.menu_update_disable -> BookshelfManageDockAction.DISABLE_UPDATE
        R.id.menu_remove_to_group -> BookshelfManageDockAction.REMOVE_GROUP
        R.id.menu_clear_cache -> BookshelfManageDockAction.CLEAR_CACHE
        else -> BookshelfManageDockAction.DELETE
    }
}
