package io.legado.app.ui.main.bookshelf

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgTheme

internal fun bookshelfMenuItems(
    includeBrowseHistory: Boolean = false
): List<NgExpandableActionMenuItem> = buildList {
    if (includeBrowseHistory) {
        add(
            NgExpandableActionMenuItem(
                itemId = R.id.menu_read_record,
                titleRes = R.string.read_record,
                iconRes = R.drawable.ic_history
            )
        )
    }
    add(
        NgExpandableActionMenuItem(
            itemId = R.id.menu_ai_assistant,
            titleRes = R.string.ai_bookshelf_assistant,
            iconRes = R.drawable.ic_ai
        )
    )
    add(
        NgExpandableActionMenuItem(
            itemId = R.id.menu_add_books,
            titleRes = R.string.add_books,
            iconRes = R.drawable.ic_add,
            dividerBefore = true,
            children = listOf(
                NgExpandableActionMenuItem(
                    R.id.menu_add_local,
                    R.string.book_local,
                    R.drawable.ic_add
                ),
                NgExpandableActionMenuItem(
                    R.id.menu_remote,
                    R.string.add_remote_book,
                    R.drawable.ic_add
                ),
                NgExpandableActionMenuItem(
                    R.id.menu_add_url,
                    R.string.add_url,
                    R.drawable.ic_add_online
                )
            )
        )
    )
    add(
        NgExpandableActionMenuItem(
            R.id.menu_group_manage,
            R.string.group_manage,
            R.drawable.ic_groups,
            dividerBefore = true
        )
    )
    add(
        NgExpandableActionMenuItem(
            R.id.menu_bookshelf_layout,
            R.string.bookshelf_layout,
            R.drawable.ic_view_quilt
        )
    )
    add(
        NgExpandableActionMenuItem(
            itemId = R.id.menu_bookshelf_backup,
            titleRes = R.string.bookshelf_backup,
            iconRes = R.drawable.ic_backup,
            dividerBefore = true,
            children = listOf(
                NgExpandableActionMenuItem(
                    R.id.menu_export_bookshelf,
                    R.string.export_bookshelf,
                    R.drawable.ic_export
                ),
                NgExpandableActionMenuItem(
                    R.id.menu_import_bookshelf,
                    R.string.import_bookshelf,
                    R.drawable.ic_import
                )
            )
        )
    )
    add(
        NgExpandableActionMenuItem(
            itemId = R.id.menu_diagnostics,
            titleRes = R.string.diagnostics,
            iconRes = R.drawable.ic_bug_report,
            dividerBefore = true,
            children = listOf(
                NgExpandableActionMenuItem(
                    R.id.menu_log,
                    R.string.log,
                    R.drawable.ic_cfg_about
                ),
                NgExpandableActionMenuItem(
                    R.id.menu_network_log,
                    R.string.network_request_log,
                    R.drawable.ic_network_check
                )
            )
        )
    )
}

@Composable
internal fun BookshelfMenuHost(
    includeBrowseHistory: Boolean,
    onMenuItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    menuOffset: DpOffset = DpOffset.Zero,
    anchor: @Composable BoxScope.((() -> Unit)) -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    val items = remember(includeBrowseHistory) {
        bookshelfMenuItems(includeBrowseHistory)
    }
    Box(modifier = modifier) {
        anchor { menuState.onAnchorClick() }
        NgExpandableActionMenu(
            expanded = menuState.expanded,
            onDismissRequest = menuState::onDismissRequest,
            items = items,
            offset = menuOffset,
            onItemClick = { item ->
                menuState.close()
                onMenuItemClick(item.itemId)
            }
        )
    }
}

@Composable
internal fun BookshelfToolbarMenuButton(
    onMenuItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuDescription = stringResource(R.string.menu)
    val contentColor = colorResource(R.color.ng_search_icon)
    BookshelfMenuHost(
        includeBrowseHistory = false,
        onMenuItemClick = onMenuItemClick,
        modifier = modifier
    ) { openMenu ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = openMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_grid_menu),
                contentDescription = menuDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun BookshelfContentToolbarMenuButton(
    onMenuItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BookshelfMenuHost(
        includeBrowseHistory = true,
        onMenuItemClick = onMenuItemClick,
        modifier = modifier,
        menuOffset = DpOffset(0.dp, (-6).dp)
    ) { openMenu ->
        BookshelfContentToolbarActionContent(
            iconRes = R.drawable.ic_bookshelf_dock_more,
            labelRes = R.string.more,
            onClick = openMenu,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun BookshelfContentToolbarActionButton(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BookshelfContentToolbarActionContent(
        iconRes = iconRes,
        labelRes = labelRes,
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        horizontalPadding = 6.dp
    )
}

@Composable
private fun BookshelfContentToolbarActionContent(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp
) {
    val contentColor = bookshelfContentToolbarActionColor()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 90 else 140),
        label = "bookshelfToolbarPress"
    )
    val backgroundColor = colorResource(
        if (isPressed) R.color.ng_bookshelf_action_pressed
        else R.color.ng_bookshelf_action_surface
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f - (0.03f * pressProgress)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (0.08f * pressProgress)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
        )
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = stringResource(labelRes),
                color = contentColor,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun bookshelfContentToolbarActionColor(): Color {
    val snapshot = NgTheme.snapshot
    return if (snapshot.isDark) {
        Color(snapshot.colors.onSurface)
    } else {
        Color(snapshot.colors.onSurfaceVariant).copy(alpha = 184f / 255f)
    }
}
