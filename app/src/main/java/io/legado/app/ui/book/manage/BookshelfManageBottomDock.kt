package io.legado.app.ui.book.manage

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
import io.legado.app.ui.design.components.compose.NgThemedActionIconKind
import io.legado.app.ui.design.theme.NgTheme

private const val CACHE_ACTION_ID = 0x56200001
private const val EXPORT_CONTENT_ACTION_ID = 0x56200002
private const val GROUP_ACTION_ID = 0x56200003

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
                    R.string.bookshelf_manage_selected_count,
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
                            0 -> onSelectAll()
                            1 -> onInvertSelection()
                            else -> menuState.onAnchorClick()
                        }
                    },
                    variant = NgFlatActionRailVariant.INLINE_DIVIDED,
                    trailingOverlay = {
                        NgExpandableActionMenu(
                            expanded = menuState.expanded,
                            onDismissRequest = menuState::onDismissRequest,
                            items = dockMoreItems(),
                            onItemClick = { item ->
                                menuState.close()
                                onAction(item.toDockAction())
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
private fun dockMoreItems(): List<NgExpandableActionMenuItem> = listOf(
    NgExpandableActionMenuItem(
        itemId = CACHE_ACTION_ID,
        titleRes = R.string.book_cache,
        iconRes = R.drawable.ic_bookshelf_action_download,
    ),
    NgExpandableActionMenuItem(
        itemId = EXPORT_CONTENT_ACTION_ID,
        titleRes = R.string.export,
        iconRes = R.drawable.ic_bookshelf_action_upload,
    ),
    NgExpandableActionMenuItem(
        itemId = GROUP_ACTION_ID,
        titleRes = R.string.group,
        iconRes = R.drawable.ic_bookshelf_action_folder,
    ),
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
        CACHE_ACTION_ID -> BookshelfManageDockAction.CACHE
        EXPORT_CONTENT_ACTION_ID -> BookshelfManageDockAction.EXPORT_CONTENT
        GROUP_ACTION_ID -> BookshelfManageDockAction.GROUP
        R.id.menu_export_selection -> BookshelfManageDockAction.EXPORT_SOURCE
        R.id.menu_change_source -> BookshelfManageDockAction.CHANGE_SOURCE
        R.id.menu_update_enable -> BookshelfManageDockAction.ENABLE_UPDATE
        R.id.menu_update_disable -> BookshelfManageDockAction.DISABLE_UPDATE
        R.id.menu_remove_to_group -> BookshelfManageDockAction.REMOVE_GROUP
        R.id.menu_clear_cache -> BookshelfManageDockAction.CLEAR_CACHE
        R.id.menu_del_selection -> BookshelfManageDockAction.DELETE
        else -> error("Unknown bookshelf dock action: $itemId")
    }
}
