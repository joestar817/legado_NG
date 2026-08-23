package io.legado.app.ui.design.components.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

@Immutable
data class NgExpandableActionMenuItem(
    @IdRes val itemId: Int,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val dividerBefore: Boolean = false,
    val children: List<NgExpandableActionMenuItem> = emptyList(),
    val title: String? = null,
    val checked: Boolean = false,
    val danger: Boolean = false,
    val themedIconKind: NgThemedActionIconKind? = null,
    val enabled: Boolean = true,
)

enum class NgExpandableActionMenuVariant {
    DROPDOWN,
    SIDE_SLIDE,
    DRILL_IN
}

enum class NgExpandableActionMenuWidthVariant(val width: Dp?) {
    CONTENT(null),
    STANDARD(152.dp),
    GROUPED_LABELS(160.dp),
}

@Composable
private fun rememberNgExpandableMenuContentWidth(
    items: List<NgExpandableActionMenuItem>,
): Dp {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium
    val minWidth = 96.dp
    val maxWidth = (configuration.screenWidthDp.dp - 16.dp)
        .coerceAtMost(240.dp)
        .coerceAtLeast(minWidth)

    return remember(items, configuration, density, textStyle, maxWidth) {
        fun itemTitle(item: NgExpandableActionMenuItem): String {
            return item.title ?: item.titleRes
                .takeIf { it != 0 }
                ?.let { titleRes -> context.getString(titleRes) }
                .orEmpty()
        }

        fun groupWidthPx(group: List<NgExpandableActionMenuItem>): Float {
            if (group.isEmpty()) return 0f
            val reserveIconSpace = group.any {
                it.iconRes != 0 || it.themedIconKind != null
            }
            return group.maxOf { item ->
                val textWidthPx = textMeasurer.measure(
                    text = AnnotatedString(itemTitle(item)),
                    style = textStyle,
                    maxLines = 1,
                ).size.width.toFloat()
                var fixedWidthDp = 24.dp
                if (reserveIconSpace) fixedWidthDp += 30.dp
                if (item.checked) fixedWidthDp += 30.dp
                if (item.children.isNotEmpty()) fixedWidthDp += 30.dp
                val rowWidthPx = textWidthPx + with(density) { fixedWidthDp.toPx() }
                maxOf(rowWidthPx, groupWidthPx(item.children))
            }
        }

        with(density) { groupWidthPx(items).toDp() }
            .coerceIn(minWidth, maxWidth)
    }
}

/**
 * Reading NG 可原位展开的轻量操作菜单。
 *
 * 子项与一级项共用同一水平栅格，展开只改变内容高度，不创建二级窗口。
 */
@Composable
fun NgExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    width: Dp? = null,
    rowMinHeight: Dp = 44.dp,
    bottomPointerHeight: Dp = 0.dp,
    bottomPointerWidth: Dp = 18.dp,
    bottomPointerEndOffset: Dp = 26.dp,
    menuContainerColor: Color? = null,
    defaultExpandedItemIds: Set<Int> = emptySet(),
    variant: NgExpandableActionMenuVariant = NgExpandableActionMenuVariant.DROPDOWN,
    properties: PopupProperties = PopupProperties(),
    widthVariant: NgExpandableActionMenuWidthVariant =
        NgExpandableActionMenuWidthVariant.CONTENT,
) {
    val contentWidth = rememberNgExpandableMenuContentWidth(items)
    val resolvedWidth = width ?: widthVariant.width ?: contentWidth
    var expandedItemIds by remember(items, defaultExpandedItemIds) {
        mutableStateOf(defaultExpandedItemIds)
    }

    if (variant == NgExpandableActionMenuVariant.SIDE_SLIDE) {
        NgSideSlideExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items,
            expandedItemIds = expandedItemIds,
            onToggle = { itemId ->
                expandedItemIds = if (itemId in expandedItemIds) {
                    expandedItemIds - itemId
                } else {
                    expandedItemIds + itemId
                }
            },
            onItemClick = onItemClick,
            width = resolvedWidth,
            rowMinHeight = rowMinHeight,
            menuContainerColor = menuContainerColor,
            properties = properties,
            onFullyHidden = { expandedItemIds = defaultExpandedItemIds }
        )
        return
    }

    if (variant == NgExpandableActionMenuVariant.DRILL_IN) {
        NgDrillInExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            items = items,
            onItemClick = onItemClick,
            modifier = modifier,
            offset = offset,
            width = resolvedWidth,
            rowMinHeight = rowMinHeight,
            menuContainerColor = menuContainerColor,
            properties = properties
        )
        return
    }

    LaunchedEffect(expanded) {
        if (!expanded) expandedItemIds = defaultExpandedItemIds
    }

    val cornerRadius = NgTheme.shapes.largeDp.dp
    val shape = if (bottomPointerHeight > 0.dp) {
        NgBottomPointerShape(
            cornerRadius = cornerRadius,
            pointerHeight = bottomPointerHeight,
            pointerWidth = bottomPointerWidth,
            pointerEndOffset = bottomPointerEndOffset
        )
    } else {
        RoundedCornerShape(cornerRadius)
    }
    val containerColor = menuContainerColor ?: colorResource(R.color.ng_surface_card)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        modifier = modifier.width(resolvedWidth),
        shape = shape,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        properties = properties
    ) {
        NgExpandableActionMenuRows(
            items = items,
            expandedItemIds = expandedItemIds,
            onToggle = { itemId ->
                expandedItemIds = if (itemId in expandedItemIds) {
                    expandedItemIds - itemId
                } else {
                    expandedItemIds + itemId
                }
            },
            onItemClick = onItemClick,
            rowMinHeight = rowMinHeight
        )
        if (bottomPointerHeight > 0.dp) {
            Spacer(Modifier.heightIn(min = bottomPointerHeight))
        }
    }
}

/**
 * 二级项进入同一锚点下的独立菜单页，保持一级菜单宽度与位置稳定。
 * 关闭二级页后重新打开会回到一级菜单。
 */
@Composable
private fun NgDrillInExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    modifier: Modifier,
    offset: DpOffset,
    width: Dp,
    rowMinHeight: Dp,
    menuContainerColor: Color?,
    properties: PopupProperties
) {
    var activeParentId by remember(items) { mutableStateOf<Int?>(null) }
    LaunchedEffect(expanded) {
        if (!expanded) activeParentId = null
    }
    val activeParent = activeParentId?.let { parentId ->
        items.firstOrNull {
            it.itemId == parentId && it.enabled && it.children.isNotEmpty()
        }
    }
    val visibleItems = activeParent?.children ?: items
    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            activeParentId = null
            onDismissRequest()
        },
        offset = offset,
        modifier = modifier.width(width),
        shape = shape,
        containerColor = menuContainerColor ?: colorResource(R.color.ng_surface_card),
        tonalElevation = 0.dp,
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        properties = properties
    ) {
        activeParent?.let { parent ->
            Text(
                text = parent.title ?: stringResource(parent.titleRes),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NgExpandableActionMenuRows(
            items = visibleItems,
            expandedItemIds = emptySet(),
            onToggle = { activeParentId = it },
            onItemClick = onItemClick,
            rowMinHeight = rowMinHeight
        )
    }
}

@Composable
private fun NgSideSlideExpandableActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<NgExpandableActionMenuItem>,
    expandedItemIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    width: Dp,
    rowMinHeight: Dp,
    menuContainerColor: Color?,
    properties: PopupProperties,
    onFullyHidden: () -> Unit
) {
    val slideFraction = remember { Animatable(1f) }
    var popupVisible by remember { mutableStateOf(expanded) }
    val motion = NgTheme.snapshot.motion
    val durationMs = if (motion.enabled) motion.mediumDurationMs else 0
    val density = LocalDensity.current
    val endMarginPx = with(density) { 8.dp.roundToPx() }
    val anchorBottomOffsetPx = with(density) { 16.dp.roundToPx() }
    val positionProvider = remember(
        endMarginPx,
        anchorBottomOffsetPx,
        slideFraction.value
    ) {
        NgWindowEndBelowAnchorPopupPositionProvider(
            marginPx = endMarginPx,
            anchorBottomOffsetPx = anchorBottomOffsetPx,
            horizontalSlideFraction = slideFraction.value
        )
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 220.dp)
        .coerceAtLeast(rowMinHeight)

    LaunchedEffect(expanded) {
        if (expanded) {
            popupVisible = true
            slideFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = LinearOutSlowInEasing
                )
            )
        } else if (popupVisible) {
            slideFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = FastOutLinearInEasing
                )
            )
            popupVisible = false
            onFullyHidden()
        }
    }

    if (expanded || popupVisible) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties
        ) {
            val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
            Surface(
                modifier = Modifier.width(width),
                shape = shape,
                color = menuContainerColor ?: colorResource(R.color.ng_surface_card),
                contentColor = Color(NgTheme.colors.onSurface),
                border = BorderStroke(
                    width = if (NgTheme.snapshot.isEInk) 1.dp else 0.5.dp,
                    color = Color(NgTheme.colors.outlineVariant).copy(
                        alpha = if (NgTheme.snapshot.isEInk) 1f else 0.45f
                    )
                ),
                tonalElevation = 0.dp,
                shadowElevation = NgTheme.effects.overlayElevationDp.dp
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    NgExpandableActionMenuRows(
                        items = items,
                        expandedItemIds = expandedItemIds,
                        onToggle = onToggle,
                        onItemClick = onItemClick,
                        rowMinHeight = rowMinHeight
                    )
                }
            }
        }
    }
}

/**
 * 水平方向按整扇窗口贴右，纵向则跟随顶栏菜单按钮的底边。
 * 展开内容过长时由窗口边界反向收口，避免越出屏幕。
 */
private class NgWindowEndBelowAnchorPopupPositionProvider(
    private val marginPx: Int,
    private val anchorBottomOffsetPx: Int,
    private val horizontalSlideFraction: Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        val slideOffsetPx = (
            (popupContentSize.width + marginPx) * horizontalSlideFraction.coerceIn(0f, 1f)
            ).roundToInt()
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> maxX + slideOffsetPx
            LayoutDirection.Rtl -> marginPx - slideOffsetPx
        }
        val maxY = (windowSize.height - popupContentSize.height - marginPx)
            .coerceAtLeast(marginPx)
        val y = (anchorBounds.bottom + anchorBottomOffsetPx)
            .coerceIn(marginPx, maxY)
        return IntOffset(x, y)
    }
}

/**
 * 带底部指向角的菜单外形，用于让浮层与触发按钮形成明确的气泡锚定关系。
 */
private class NgBottomPointerShape(
    private val cornerRadius: Dp,
    private val pointerHeight: Dp,
    private val pointerWidth: Dp,
    private val pointerEndOffset: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = with(density) { cornerRadius.toPx() }
        val pointerHeightPx = with(density) { pointerHeight.toPx() }
        val pointerWidthPx = with(density) { pointerWidth.toPx() }
        val pointerEndOffsetPx = with(density) { pointerEndOffset.toPx() }
        val bodyBottom = (size.height - pointerHeightPx).coerceAtLeast(0f)
        val pointerCenterX = when (layoutDirection) {
            LayoutDirection.Ltr -> size.width - pointerEndOffsetPx
            LayoutDirection.Rtl -> pointerEndOffsetPx
        }.coerceIn(pointerWidthPx / 2f, size.width - pointerWidthPx / 2f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, bodyBottom),
                    cornerRadius = CornerRadius(radiusPx),
                )
            )
            moveTo(pointerCenterX - pointerWidthPx / 2f, bodyBottom - 1f)
            lineTo(pointerCenterX, size.height)
            lineTo(pointerCenterX + pointerWidthPx / 2f, bodyBottom - 1f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun NgExpandableActionMenuRows(
    items: List<NgExpandableActionMenuItem>,
    expandedItemIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
    rowMinHeight: Dp
) {
    val reserveIconSpace = items.any { it.iconRes != 0 || it.themedIconKind != null }
    items.forEach { item ->
        key(item.itemId) {
            if (item.dividerBefore) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    color = Color(NgTheme.colors.outlineVariant).copy(
                        alpha = if (NgTheme.snapshot.isEInk) 1f else 0.35f
                    )
                )
            }
            val isExpanded = item.enabled && item.itemId in expandedItemIds
            NgExpandableActionMenuRow(
                item = item,
                isExpanded = isExpanded,
                reserveIconSpace = reserveIconSpace,
                rowMinHeight = rowMinHeight,
                onClick = {
                    if (item.children.isEmpty()) {
                        onItemClick(item)
                    } else {
                        onToggle(item.itemId)
                    }
                }
            )
            if (isExpanded) {
                NgExpandableActionMenuRows(
                    items = item.children,
                    expandedItemIds = expandedItemIds,
                    onToggle = onToggle,
                    onItemClick = onItemClick,
                    rowMinHeight = rowMinHeight
                )
            }
        }
    }
}

@Composable
private fun NgExpandableActionMenuRow(
    item: NgExpandableActionMenuItem,
    isExpanded: Boolean,
    reserveIconSpace: Boolean,
    rowMinHeight: Dp,
    onClick: () -> Unit
) {
    val contentColor = Color(
        if (item.danger) NgTheme.colors.error else NgTheme.colors.onSurface
    ).copy(alpha = if (item.enabled) 1f else 0.38f)
    val themedIconKind = item.themedIconKind
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowMinHeight)
            .clickable(enabled = item.enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reserveIconSpace) {
            if (themedIconKind == null) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.iconRes != 0) {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                NgThemedActionIcon(
                    kind = themedIconKind,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = item.title ?: stringResource(item.titleRes),
            color = contentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (item.checked) {
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ng_ic_popup_selected),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        if (item.children.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
                    ),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
