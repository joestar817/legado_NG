package io.legado.app.ui.book.read

import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Shared with the actual rail so the capacity follows its measured geometry.
internal val FloatingToolButtonHeight = 48.dp
internal val FloatingToolRailVerticalPadding = 6.dp
internal val FloatingToolDividerVerticalPadding = 4.dp
internal val FloatingToolDividerThickness = 1.dp
internal const val FloatingToolMaxScreenFraction = 0.60f

internal fun floatingToolCapacity(availableHeightPx: Int, screenHeightDp: Int, density: Float): Int {
    val buttonPx = (FloatingToolButtonHeight.value * density).roundToInt()
    val railPaddingPx = (FloatingToolRailVerticalPadding.value * density).roundToInt() * 2
    val dividerPx = (FloatingToolDividerVerticalPadding.value * density).roundToInt() * 2 +
        (FloatingToolDividerThickness.value * density).roundToInt()
    val columnCapPx = (screenHeightDp * FloatingToolMaxScreenFraction * density).roundToInt()
    val usableColumnPx = minOf(availableHeightPx - railPaddingPx, columnCapPx)
    // One button is reserved for switching sides; the divider remains when a tool is present.
    return ((usableColumnPx - buttonPx - dividerPx) / buttonPx).coerceAtLeast(1)
}

internal enum class FloatingToolToggleLimit { MINIMUM, MAXIMUM }

internal fun floatingToolToggleLimit(
    isEnabled: Boolean,
    enabledCount: Int,
    maxEnabled: Int,
): FloatingToolToggleLimit? = when {
    isEnabled && enabledCount <= 1 -> FloatingToolToggleLimit.MINIMUM
    !isEnabled && enabledCount >= maxEnabled -> FloatingToolToggleLimit.MAXIMUM
    else -> null
}
