package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

internal enum class ReadChoicePopupVariant { STANDARD, ADAPTIVE }

@Composable
internal fun ReadChoicePopup(
    expanded: Boolean,
    options: List<ReadMoreConfigOption>,
    selectedValue: String,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
    preferPopupBelow: Boolean = false,
    style: NgGlassStyle = readFloatingGlassStyle(),
    role: NgMaterialRole = NgMaterialRole.SOFT_SURFACE,
    scrollable: Boolean = false,
    variant: ReadChoicePopupVariant = ReadChoicePopupVariant.STANDARD,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val density = LocalDensity.current
    val gapPx = with(density) { 6.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val popupPositionProvider = remember(gapPx, marginPx, preferPopupBelow, scrollable) {
        EndAnchoredPopupPositionProvider(
            gapPx = gapPx,
            marginPx = marginPx,
            preferBelow = preferPopupBelow,
            constrainToWindow = scrollable,
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
                )
            }
        }
    }
}

@Composable
private fun ReadChoicePopupContent(
    options: List<ReadMoreConfigOption>,
    selectedValue: String,
    contentColor: Color,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
    style: NgGlassStyle,
    role: NgMaterialRole,
    variant: ReadChoicePopupVariant,
    scrollMaxHeight: Dp? = null,
) {
    val items: @Composable ColumnScope.() -> Unit = {
        options.forEach { option ->
            DropdownMenuItem(
                modifier = Modifier
                    .then(
                        if (variant == ReadChoicePopupVariant.ADAPTIVE) Modifier.heightIn(min = 42.dp)
                        else Modifier.height(42.dp)
                    )
                    .semantics {
                        selected = option.value == selectedValue
                    },
                text = {
                    Text(
                        text = option.label,
                        color = if (option.value == selectedValue) {
                            Color(NgTheme.colors.secondary)
                        } else {
                            contentColor
                        },
                        fontSize = 14.sp,
                        lineHeight = if (variant == ReadChoicePopupVariant.ADAPTIVE) 20.sp
                            else TextUnit.Unspecified,
                        fontWeight = if (option.value == selectedValue) {
                            FontWeight.Medium
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onSelected(option.value)
                },
            )
        }
    }
    NgGlassSurface(
        modifier = Modifier.width(156.dp),
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

private class EndAnchoredPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val preferBelow: Boolean,
    private val constrainToWindow: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val desiredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val maxX = (windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        val x = desiredX.coerceIn(marginPx, maxX)
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val belowY = anchorBounds.bottom + gapPx
        val maxY = (windowSize.height - popupContentSize.height - marginPx)
            .coerceAtLeast(marginPx)
        val y = if (preferBelow) {
            if (belowY <= maxY) belowY else aboveY.coerceAtLeast(marginPx)
        } else {
            if (aboveY >= marginPx) aboveY else belowY.coerceAtMost(maxY)
        }
        return IntOffset(x, if (constrainToWindow) y.coerceIn(marginPx, maxY) else y)
    }
}
