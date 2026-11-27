package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme

enum class NgLongDrawerHeaderVariant {
    COMPACT,
    ROUND_ACTIONS,
    ROUND_ACTIONS_TRAILING_GROUP,
}

/**
 * NG 长列表抽屉的紧凑标题区。
 *
 * 提示线、标题和尾部操作的几何由公共组件统一；业务页面只提供标题、图标和事件。
 */
@Composable
fun NgLongDrawerHeader(
    title: String,
    statusText: String? = null,
    @DrawableRes statusIconRes: Int? = null,
    @DrawableRes navigationIconRes: Int? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    @DrawableRes actionIconRes: Int? = null,
    actionContentDescription: String? = null,
    actionActive: Boolean = false,
    onActionClick: (() -> Unit)? = null,
    trailingActionText: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    secondaryTrailingActionText: String? = null,
    onSecondaryTrailingActionClick: (() -> Unit)? = null,
    @DrawableRes secondaryActionIconRes: Int? = null,
    secondaryActionContentDescription: String? = null,
    secondaryActionActive: Boolean = false,
    onSecondaryActionClick: (() -> Unit)? = null,
    @DrawableRes tertiaryActionIconRes: Int? = null,
    tertiaryActionContentDescription: String? = null,
    tertiaryActionLoading: Boolean = false,
    onTertiaryActionClick: (() -> Unit)? = null,
    @DrawableRes quaternaryActionIconRes: Int? = null,
    quaternaryActionContentDescription: String? = null,
    quaternaryActionEnabled: Boolean = true,
    onQuaternaryActionClick: (() -> Unit)? = null,
    centerTitle: Boolean = false,
    modifier: Modifier = Modifier,
    navigationActive: Boolean = false,
    titleFontWeight: FontWeight = FontWeight.Medium,
    variant: NgLongDrawerHeaderVariant = NgLongDrawerHeaderVariant.COMPACT,
    navigationContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
    centeredTrailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = NgTheme.colors
    val roundActions = variant != NgLongDrawerHeaderVariant.COMPACT
    val actionSize = if (roundActions) 44.dp else 40.dp
    val hasSecondaryAction = secondaryActionIconRes != null && onSecondaryActionClick != null
    val rowModifier = Modifier.fillMaxWidth().height(if (roundActions) 48.dp else 42.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
        if (centerTitle && centeredTrailingContent != null) {
            DrawerHeaderWithCenteredTitle(
                modifier = rowModifier,
                navigation = {
                    if (navigationContent != null) {
                        Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
                            navigationContent()
                        }
                    } else {
                        DrawerHeaderIconSlot(
                            iconRes = navigationIconRes,
                            contentDescription = navigationContentDescription,
                            active = navigationActive,
                            onClick = onNavigationClick,
                            size = actionSize,
                        )
                    }
                },
                title = { DrawerHeaderTitle(title, centerTitle, titleFontWeight) },
                trailing = centeredTrailingContent,
                fitTitleBetweenActions = variant == NgLongDrawerHeaderVariant.ROUND_ACTIONS_TRAILING_GROUP,
            )
            return@Column
        }
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationContent != null) {
                Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
                    navigationContent()
                }
                if (centerTitle && hasSecondaryAction) Spacer(Modifier.width(actionSize))
            } else if (centerTitle) {
                repeat(if (hasSecondaryAction) 2 else 1) { index ->
                    DrawerHeaderIconSlot(
                        iconRes = navigationIconRes.takeIf { index == 0 },
                        contentDescription = navigationContentDescription.takeIf { index == 0 },
                        active = navigationActive,
                        onClick = onNavigationClick.takeIf { index == 0 },
                        size = actionSize,
                    )
                }
            } else if (navigationIconRes != null && onNavigationClick != null) {
                DrawerHeaderIconSlot(
                    iconRes = navigationIconRes,
                    contentDescription = navigationContentDescription,
                    active = navigationActive,
                    onClick = onNavigationClick,
                    size = actionSize,
                )
            }
            DrawerHeaderTitle(title, centerTitle, titleFontWeight, Modifier.weight(1f))
            if (!statusText.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusIconRes != null) {
                        Icon(
                            painter = painterResource(statusIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(colors.primary),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = statusText,
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                    )
                }
            }
            if (tertiaryActionIconRes != null && onTertiaryActionClick != null) {
                Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
                    if (tertiaryActionLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(colors.primary),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        DrawerHeaderIconSlot(
                            iconRes = tertiaryActionIconRes,
                            contentDescription = tertiaryActionContentDescription,
                            active = false,
                            onClick = onTertiaryActionClick,
                            size = actionSize,
                        )
                    }
                }
            }
            if (quaternaryActionIconRes != null && onQuaternaryActionClick != null) {
                DrawerHeaderIconSlot(
                    iconRes = quaternaryActionIconRes,
                    contentDescription = quaternaryActionContentDescription,
                    active = false,
                    enabled = quaternaryActionEnabled,
                    onClick = onQuaternaryActionClick,
                    size = actionSize,
                )
            }
            if (hasSecondaryAction) {
                DrawerHeaderIconSlot(
                    iconRes = secondaryActionIconRes,
                    contentDescription = secondaryActionContentDescription,
                    active = secondaryActionActive,
                    onClick = onSecondaryActionClick,
                    size = actionSize,
                )
            }
            if (centerTitle) {
                if (actionContent != null) {
                    Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) { actionContent() }
                } else {
                    DrawerHeaderIconSlot(
                        iconRes = actionIconRes,
                        contentDescription = actionContentDescription,
                        active = actionActive,
                        onClick = onActionClick,
                        size = actionSize,
                    )
                }
            } else if (actionIconRes != null && onActionClick != null) {
                DrawerHeaderIconSlot(
                    iconRes = actionIconRes,
                    contentDescription = actionContentDescription,
                    active = actionActive,
                    onClick = onActionClick,
                    size = actionSize,
                )
            }
            DrawerHeaderTextAction(
                text = secondaryTrailingActionText,
                onClick = onSecondaryTrailingActionClick,
            )
            DrawerHeaderTextAction(
                text = trailingActionText,
                onClick = onTrailingActionClick,
            )
        }
    }
}

@Composable
private fun DrawerHeaderWithCenteredTitle(
    modifier: Modifier,
    navigation: @Composable () -> Unit,
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    fitTitleBetweenActions: Boolean,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { navigation() }
            Box { title() }
            Box { trailing() }
        },
    ) { (navigationMeasurable, titleMeasurable, trailingMeasurable), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val sideConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = width / 2)
        val start = navigationMeasurable.measure(sideConstraints)
        val end = trailingMeasurable.measure(sideConstraints)
        val sideWidth = maxOf(start.width, end.width)
        val gap = minOf(6.dp.roundToPx(), (width - start.width - end.width) / 2)
        val titleStart = if (fitTitleBetweenActions) start.width + gap else sideWidth
        val titleEnd = if (fitTitleBetweenActions) width - end.width - gap else width - sideWidth
        val center = titleMeasurable.measure(sideConstraints.copy(maxWidth = titleEnd - titleStart))
        val titleX = ((width - center.width) / 2).coerceIn(titleStart, titleEnd - center.width)
        layout(width, height) {
            start.placeRelative(0, (height - start.height) / 2)
            center.placeRelative(titleX, (height - center.height) / 2)
            end.placeRelative(width - end.width, (height - end.height) / 2)
        }
    }
}

@Composable
private fun DrawerHeaderTitle(
    title: String,
    centered: Boolean,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        color = Color(NgTheme.colors.onSurface),
        fontSize = 17.sp,
        lineHeight = 21.sp,
        fontWeight = fontWeight,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DrawerHeaderTextAction(
    text: String?,
    onClick: (() -> Unit)?,
) {
    if (text.isNullOrBlank() || onClick == null) return
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawerHeaderIconSlot(
    @DrawableRes iconRes: Int?,
    contentDescription: String?,
    active: Boolean,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    size: Dp,
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null && onClick != null) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(size),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(22.dp),
                    tint = (if (active) {
                        Color(NgTheme.colors.primary)
                    } else {
                        Color(NgTheme.colors.onSurfaceVariant)
                    }).copy(alpha = if (enabled) 1f else 0.35f),
                )
            }
        }
    }
}
