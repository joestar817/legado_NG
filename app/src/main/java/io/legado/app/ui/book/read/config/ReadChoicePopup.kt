package io.legado.app.ui.book.read.config

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.theme.NgTheme

internal enum class ReadChoicePopupVariant { STANDARD, ADAPTIVE, COMPACT }

@Composable
internal fun ReadChoicePopup(
    expanded: Boolean,
    options: List<ReadMoreConfigOption>,
    selectedValue: String?,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
    preferPopupBelow: Boolean = false,
    style: NgGlassStyle = readFloatingGlassStyle(),
    role: NgMaterialRole = NgMaterialRole.SOFT_SURFACE,
    scrollable: Boolean = false,
    variant: ReadChoicePopupVariant = ReadChoicePopupVariant.STANDARD,
    menuWidth: Dp = 156.dp,
    centerOnAnchor: Boolean = false,
    useVisibleWindowBounds: Boolean = false,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val density = LocalDensity.current
    val anchorView = LocalView.current
    val gapPx = with(density) { 6.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val popupPositionProvider = remember(gapPx, marginPx, preferPopupBelow, scrollable, centerOnAnchor, useVisibleWindowBounds, anchorView) {
        EndAnchoredPopupPositionProvider(
            gapPx = gapPx,
            marginPx = marginPx,
            preferBelow = preferPopupBelow,
            constrainToWindow = scrollable,
            centerOnAnchor = centerOnAnchor,
            visibleBoundsProvider = if (useVisibleWindowBounds) {
                { popupVisibleBoundsInWindow(anchorView) }
            } else null,
        )
    }
    if (expanded) {
        Popup(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            if (scrollable) {
                BoxWithConstraints {
                    ReadChoicePopupContent(
                        options = options,
                        selectedValue = selectedValue,
                        contentColor = contentColor,
                        onDismissRequest = onDismissRequest,
                        onSelected = onSelected,
                        style = style,
                        role = role,
                        variant = variant,
                        menuWidth = menuWidth,
                        scrollMaxHeight = (maxHeight - 16.dp).coerceAtLeast(0.dp),
                    )
                }
            } else {
                ReadChoicePopupContent(
                    options = options,
                    selectedValue = selectedValue,
                    contentColor = contentColor,
                    onDismissRequest = onDismissRequest,
                    onSelected = onSelected,
                    style = style,
                    role = role,
                    variant = variant,
                    menuWidth = menuWidth,
                )
            }
        }
    }
}

@Composable
private fun ReadChoicePopupContent(
    options: List<ReadMoreConfigOption>,
    selectedValue: String?,
    contentColor: Color,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
    style: NgGlassStyle,
    role: NgMaterialRole,
    variant: ReadChoicePopupVariant,
    menuWidth: Dp,
    scrollMaxHeight: Dp? = null,
) {
    val items: @Composable ColumnScope.() -> Unit = {
        options.forEach { option ->
            val isSelected = selectedValue != null && option.value == selectedValue
            val label: @Composable () -> Unit = {
                Text(
                    text = option.label,
                    color = if (isSelected) Color(NgTheme.colors.secondary) else contentColor,
                    fontSize = 14.sp,
                    lineHeight = if (variant == ReadChoicePopupVariant.STANDARD) TextUnit.Unspecified else 20.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val selectedModifier = if (selectedValue != null) Modifier.semantics { selected = isSelected } else Modifier
            val onClick = {
                onDismissRequest()
                onSelected(option.value)
            }
            if (variant == ReadChoicePopupVariant.COMPACT) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).then(selectedModifier)
                        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { label() }
            } else {
                DropdownMenuItem(
                    modifier = Modifier
                        .then(if (variant == ReadChoicePopupVariant.ADAPTIVE) Modifier.heightIn(min = 42.dp) else Modifier.height(42.dp))
                        .then(selectedModifier),
                    text = label,
                    onClick = onClick,
                )
            }
        }
    }
    NgGlassSurface(
        modifier = Modifier.width(menuWidth),
        shape = RoundedCornerShape(12.dp),
        style = style,
        role = role,
    ) {
        if (scrollMaxHeight != null) {
            Column(
                modifier = Modifier.heightIn(max = scrollMaxHeight)
                    .verticalScroll(rememberScrollState()),
                content = items,
            )
        } else {
            items()
        }
    }
}

internal class EndAnchoredPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val preferBelow: Boolean,
    private val constrainToWindow: Boolean,
    private val centerOnAnchor: Boolean = false,
    private val visibleBoundsProvider: (() -> IntRect?)? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bounds = visibleBoundsProvider?.invoke()?.takeIf { it.width > 0 && it.height > 0 }
            ?: IntRect(0, 0, windowSize.width, windowSize.height)
        val desiredX = when {
            centerOnAnchor -> anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
            layoutDirection == LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
            else -> anchorBounds.left
        }
        val minX = bounds.left + marginPx
        val maxX = (bounds.right - popupContentSize.width - marginPx).coerceAtLeast(minX)
        val x = desiredX.coerceIn(minX, maxX)
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val belowY = anchorBounds.bottom + gapPx
        val minY = bounds.top + marginPx
        val maxY = (bounds.bottom - popupContentSize.height - marginPx).coerceAtLeast(minY)
        val y = if (preferBelow) {
            if (belowY <= maxY) belowY else aboveY.coerceAtLeast(minY)
        } else {
            if (aboveY >= minY) aboveY else belowY.coerceAtMost(maxY)
        }
        return IntOffset(x, if (constrainToWindow) y.coerceIn(minY, maxY) else y)
    }
}

/** Popup anchors are window-local, whereas the visible frame is reported in screen coordinates. */
internal fun popupVisibleFrameInWindow(
    screenFrame: IntRect,
    locationOnScreen: IntOffset,
    locationInWindow: IntOffset,
): IntRect {
    val originX = locationOnScreen.x - locationInWindow.x
    val originY = locationOnScreen.y - locationInWindow.y
    return IntRect(
        screenFrame.left - originX, screenFrame.top - originY,
        screenFrame.right - originX, screenFrame.bottom - originY,
    )
}

internal fun popupVisibleBoundsInWindow(view: View): IntRect? {
    if (!view.isAttachedToWindow) return null
    val frame = Rect()
    view.getWindowVisibleDisplayFrame(frame)
    if (frame.width() <= 0 || frame.height() <= 0) return null
    val onScreen = IntArray(2)
    val inWindow = IntArray(2)
    view.getLocationOnScreen(onScreen)
    view.getLocationInWindow(inWindow)
    return popupVisibleFrameInWindow(
        screenFrame = IntRect(frame.left, frame.top, frame.right, frame.bottom),
        locationOnScreen = IntOffset(onScreen[0], onScreen[1]),
        locationInWindow = IntOffset(inWindow[0], inWindow[1]),
    )
}
