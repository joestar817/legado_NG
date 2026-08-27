package io.legado.app.ui.main.bookshelf

import android.graphics.Rect
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal data class BookshelfDockGroup(
    val groupId: Long,
    val name: String,
)

private const val COMPACT_GROUP_ITEM_ID_BASE = 0x55000000

@Composable
internal fun BookshelfFloatingDock(
    groups: List<BookshelfDockGroup>,
    selectedIndex: Int,
    onSearchClick: () -> Unit,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
    topDistancePx: Int,
    contentTopInsetPx: Int,
    transparencyPercent: Int,
    searchPosition: BookshelfFloatingDockSearchPosition,
    backdropSource: View?,
    modifier: Modifier = Modifier
) {
    val snapshot = NgTheme.snapshot
    val shape = RoundedCornerShape(12.dp)
    val surfaceColor = colorResource(R.color.ng_floating_dock_surface).copy(
        alpha = BookshelfFloatingDockConfig.surfaceAlpha(transparencyPercent)
    )
    val dockTopSpacerHeight = with(LocalDensity.current) {
        (topDistancePx - contentTopInsetPx).coerceAtLeast(0).toDp()
    }
    val dockBorderColor = when {
        snapshot.isEInk -> Color.Black
        snapshot.isDark -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val materialStyle = rememberBookshelfDockStyle(surfaceColor, dockBorderColor)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dockTopSpacerHeight)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            NgVisualSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                role = NgMaterialRole.TOP_NAVIGATION,
                cornerRadius = 12.dp,
                shape = shape,
                style = materialStyle,
                viewBackdropSource = backdropSource,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searchPosition == BookshelfFloatingDockSearchPosition.LEFT) {
                        DockAction(
                            iconRes = R.drawable.ic_bookshelf_dock_search,
                            label = stringResource(R.string.search),
                            onClick = onSearchClick
                        )
                    }
                    GroupTrack(
                        groups = groups,
                        selectedIndex = selectedIndex,
                        onGroupClick = onGroupClick,
                        onGroupLongClick = onGroupLongClick,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchPosition == BookshelfFloatingDockSearchPosition.RIGHT) {
                        DockAction(
                            iconRes = R.drawable.ic_bookshelf_dock_search,
                            label = stringResource(R.string.search),
                            onClick = onSearchClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val contentColor = floatingDockInactiveContentColor()
    DockItemContent(
        label = label,
        contentColor = contentColor,
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            }
    ) {
        DockVectorIcon(iconRes = iconRes, tint = contentColor)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTrack(
    groups: List<BookshelfDockGroup>,
    selectedIndex: Int,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        if (groups.isEmpty()) return@BoxWithConstraints
        val visibleItemCount = (maxWidth.value / MIN_GROUP_ITEM_WIDTH.value)
            .toInt()
            .coerceAtLeast(1)
        val scrollable = groups.size > visibleItemCount
        val itemWidth = if (scrollable) {
            maxWidth / visibleItemCount.toFloat()
        } else {
            minOf(MIN_GROUP_ITEM_WIDTH, maxWidth)
        }
        val listState = rememberLazyListState()
        val snapFlingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start)
        LaunchedEffect(selectedIndex, groups.size, itemWidth, maxWidth) {
            if (scrollable && selectedIndex in groups.indices) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.Start,
                flingBehavior = snapFlingBehavior,
                userScrollEnabled = scrollable,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = groups,
                    key = { _, item -> item.groupId }
                ) { index, group ->
                    GroupItem(
                        group = group,
                        selected = index == selectedIndex,
                        width = itemWidth,
                        onClick = { onGroupClick(index) },
                        onLongClick = { onGroupLongClick(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupItem(
    group: BookshelfDockGroup,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val contentColor = if (selected) {
        floatingDockActiveContentColor()
    } else {
        floatingDockInactiveContentColor()
    }
    DockItemContent(
        label = group.name,
        contentColor = contentColor,
        labelWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.Tab
                contentDescription = group.name
            }
    ) {
        GroupIcon(group = group, selected = selected)
    }
}

@Composable
private fun DockItemContent(
    label: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    labelWeight: FontWeight = FontWeight.Normal,
    iconContent: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            iconContent()
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = labelWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        )
    }
}

@Composable
private fun DockVectorIcon(@DrawableRes iconRes: Int, tint: Color) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun GroupIcon(group: BookshelfDockGroup, selected: Boolean) {
    val iconTint = if (selected) {
        floatingDockActiveContentColor()
    } else {
        floatingDockInactiveContentColor()
    }
    val builtInIconRes = group.builtInIconRes()
    if (builtInIconRes != null) {
        DockVectorIcon(
            iconRes = builtInIconRes,
            tint = iconTint
        )
    } else {
        Text(
            text = group.name.firstOrNull()?.toString().orEmpty(),
            color = iconTint,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}

@Composable
internal fun BookshelfCompactToolbar(
    groups: List<BookshelfDockGroup>,
    selectedIndex: Int,
    groupGridMode: Boolean,
    onSearchClick: () -> Unit,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
    onManageClick: () -> Unit,
    onSortClick: (View, Rect) -> Unit,
    onMenuItemClick: (Int) -> Unit,
    topDistancePx: Int,
    contentTopInsetPx: Int,
    transparencyPercent: Int,
    searchPosition: BookshelfFloatingDockSearchPosition,
    backdropSource: View?,
    modifier: Modifier = Modifier,
) {
    val snapshot = NgTheme.snapshot
    val rootView = LocalView.current
    val shape = RoundedCornerShape(12.dp)
    val surfaceColor = colorResource(R.color.ng_floating_dock_surface).copy(
        alpha = BookshelfFloatingDockConfig.surfaceAlpha(transparencyPercent),
    )
    val contentColor = if (snapshot.isDark) {
        Color(snapshot.colors.onSurface)
    } else {
        Color(snapshot.colors.onSurfaceVariant).copy(alpha = 184f / 255f)
    }
    val dividerColor = Color(snapshot.colors.outlineVariant).copy(
        alpha = if (snapshot.isDark) 0.34f else 0.24f,
    )
    val dockBorderColor = when {
        snapshot.isEInk -> Color.Black
        snapshot.isDark -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val materialStyle = rememberBookshelfDockStyle(surfaceColor, dockBorderColor)
    val dockTopSpacerHeight = with(LocalDensity.current) {
        (topDistancePx - contentTopInsetPx).coerceAtLeast(0).toDp()
    }
    var sortAnchorBounds by remember { mutableStateOf(Rect()) }
    val searchLabel = stringResource(R.string.search)
    val manageLabel = stringResource(R.string.manage)
    val sortLabel = stringResource(R.string.sort)
    val moreLabel = stringResource(R.string.more)

    val actions: @Composable () -> Unit = {
        CompactToolbarAction(
            iconRes = R.drawable.ic_settings,
            label = manageLabel,
            contentColor = contentColor,
            onClick = onManageClick,
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
        )
        CompactToolbarDivider(color = dividerColor)
        CompactToolbarAction(
            iconRes = R.drawable.ic_swap_vert,
            label = sortLabel,
            contentColor = contentColor,
            onClick = {
                if (!sortAnchorBounds.isEmpty) {
                    onSortClick(rootView, Rect(sortAnchorBounds))
                }
            },
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    sortAnchorBounds = Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    )
                },
        )
        CompactToolbarDivider(color = dividerColor)
        BookshelfMenuHost(
            includeBrowseHistory = true,
            onMenuItemClick = onMenuItemClick,
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            menuOffset = DpOffset(0.dp, (-6).dp),
        ) { openMenu ->
            CompactToolbarAction(
                iconRes = R.drawable.ic_bookshelf_dock_more,
                label = moreLabel,
                contentColor = contentColor,
                onClick = openMenu,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(dockTopSpacerHeight),
        )
        NgVisualSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            role = NgMaterialRole.TOP_NAVIGATION,
            cornerRadius = 12.dp,
            shape = shape,
            style = materialStyle,
            viewBackdropSource = backdropSource,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (searchPosition == BookshelfFloatingDockSearchPosition.LEFT) {
                    CompactToolbarSearchAction(
                        label = searchLabel,
                        contentColor = contentColor,
                        onClick = onSearchClick,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 96.dp),
                    )
                    CompactToolbarDivider(color = dividerColor)
                    if (!groupGridMode && groups.isNotEmpty()) {
                        CompactToolbarGroupAction(
                            groups = groups,
                            selectedIndex = selectedIndex,
                            contentColor = contentColor,
                            onGroupClick = onGroupClick,
                            onGroupLongClick = onGroupLongClick,
                        )
                        CompactToolbarDivider(color = dividerColor)
                    }
                    actions()
                } else {
                    actions()
                    CompactToolbarDivider(color = dividerColor)
                    if (!groupGridMode && groups.isNotEmpty()) {
                        CompactToolbarGroupAction(
                            groups = groups,
                            selectedIndex = selectedIndex,
                            contentColor = contentColor,
                            onGroupClick = onGroupClick,
                            onGroupLongClick = onGroupLongClick,
                        )
                        CompactToolbarDivider(color = dividerColor)
                    }
                    CompactToolbarSearchAction(
                        label = searchLabel,
                        contentColor = contentColor,
                        onClick = onSearchClick,
                        alignEnd = true,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 96.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberBookshelfDockStyle(
    surfaceColor: Color,
    borderColor: Color,
): NgGlassStyle {
    val base = NgGlassDefaults.flatNeutralStyle(containerAlpha = surfaceColor.alpha)
    return remember(base, surfaceColor, borderColor) {
        base.copy(
            containerTop = surfaceColor,
            containerBottom = surfaceColor,
            accentGlow = Color.Transparent,
            borderColor = borderColor,
            edgeHighlight = Color.White.copy(alpha = 0.58f),
            surfaceGloss = Color.Transparent,
            depthEdge = Color.Transparent,
            blurRadius = 0.dp,
            shadowElevation = 0.dp,
            borderWidth = 0.6.dp,
            highlightWidth = 0.dp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactToolbarGroupAction(
    groups: List<BookshelfDockGroup>,
    selectedIndex: Int,
    contentColor: Color,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
) {
    val selectedGroup = groups.getOrNull(selectedIndex) ?: return
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var expanded by remember { mutableStateOf(false) }
    val groupLabel = stringResource(R.string.group)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val menuTextStyle = MaterialTheme.typography.bodyMedium
    val menuWidth = remember(
        groups,
        configuration.screenWidthDp,
        density.density,
        density.fontScale,
        menuTextStyle,
    ) {
        val longestTextWidth = groups.maxOfOrNull { group ->
            textMeasurer.measure(
                text = group.name,
                style = menuTextStyle,
                maxLines = 1,
            ).size.width
        } ?: 0
        val measuredWidth = with(density) { longestTextWidth.toDp() } + 84.dp
        val maxWidth = (configuration.screenWidthDp.dp - 32.dp)
            .coerceAtMost(196.dp)
            .coerceAtLeast(120.dp)
        measuredWidth.coerceIn(120.dp, maxWidth)
    }
    val menuItems = remember(groups, selectedIndex) {
        groups.mapIndexed { index, group ->
            NgExpandableActionMenuItem(
                itemId = COMPACT_GROUP_ITEM_ID_BASE + index,
                titleRes = 0,
                iconRes = group.builtInIconRes() ?: R.drawable.ic_groups,
                title = group.name,
                checked = index == selectedIndex,
            )
        }
    }

    Box(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isPressed) colorResource(R.color.ng_bookshelf_action_pressed)
                    else Color.Transparent,
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = true },
                    onLongClick = { onGroupLongClick(selectedIndex) },
                )
                .padding(horizontal = 3.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$groupLabel: ${selectedGroup.name}"
                    role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedGroup.name,
                color = contentColor,
                fontSize = NgTheme.typography.compactItemTitleSp.sp,
                lineHeight = (NgTheme.typography.compactItemTitleSp + 3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        }
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = menuItems,
            width = menuWidth,
            offset = DpOffset(0.dp, (-4).dp),
            onItemClick = { item ->
                expanded = false
                val index = item.itemId - COMPACT_GROUP_ITEM_ID_BASE
                if (index in groups.indices) onGroupClick(index)
            },
        )
    }
}

@Composable
private fun CompactToolbarSearchAction(
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPressed) colorResource(R.color.ng_bookshelf_action_pressed)
                else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Button
            },
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookshelf_dock_search),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = NgTheme.typography.compactItemTitleSp.sp,
            lineHeight = (NgTheme.typography.compactItemTitleSp + 3).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactToolbarAction(
    @DrawableRes iconRes: Int,
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isPressed) colorResource(R.color.ng_bookshelf_action_pressed)
                else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CompactToolbarDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(color),
    )
}

@Composable
private fun floatingDockActiveContentColor(): Color {
    return Color(NgTheme.colors.primary)
}

@Composable
private fun floatingDockInactiveContentColor(): Color {
    val snapshot = NgTheme.snapshot
    return if (snapshot.isDark) {
        Color(snapshot.colors.onSurface)
    } else {
        Color(snapshot.colors.onSurfaceVariant).copy(alpha = 0.66f)
    }
}

@DrawableRes
private fun BookshelfDockGroup.builtInIconRes(): Int? {
    return when (groupId) {
        BookGroup.IdAll -> R.drawable.ic_bookshelf_dock_all
        BookGroup.IdNoGroup -> R.drawable.ic_groups
        BookGroup.IdLocal -> R.drawable.ic_bookshelf_dock_local
        BookGroup.IdAudio -> R.drawable.ic_bookshelf_dock_audio
        BookGroup.IdVideo -> R.drawable.ic_bookshelf_dock_video
        else -> null
    }
}

private val MIN_GROUP_ITEM_WIDTH = 64.dp
