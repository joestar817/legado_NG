package io.legado.app.ui.book.read.aloud

import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

internal const val LISTENING_PANE_HORIZONTAL_PADDING_DP = 28
internal const val LISTENING_PANE_GAP_DP = 24
internal const val LISTENING_CONTROLS_PANE_WEIGHT = 0.4f

/** Window coordinates shared by the player and its pane-local drawers. */
internal data class ListeningPaneLayout(
    val controls: IntRect,
    val music: IntRect,
)

internal fun calculateListeningPaneLayout(
    windowLeftPx: Int,
    windowTopPx: Int,
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ListeningPaneLayout {
    val horizontalPadding = (LISTENING_PANE_HORIZONTAL_PADDING_DP * density).roundToInt()
    val gap = (LISTENING_PANE_GAP_DP * density).roundToInt()
    val contentWidth = (widthPx - horizontalPadding * 2 - gap).coerceAtLeast(0)
    val controlsWidth = (contentWidth * LISTENING_CONTROLS_PANE_WEIGHT).roundToInt()
    // Match Row's pixel rounding, with the shared edge at the middle of its gap.
    val divider = windowLeftPx + (horizontalPadding + controlsWidth + gap / 2f)
        .roundToInt().coerceIn(0, widthPx)
    val bottom = windowTopPx + heightPx
    return ListeningPaneLayout(
        controls = IntRect(windowLeftPx, windowTopPx, divider, bottom),
        music = IntRect(divider, windowTopPx, windowLeftPx + widthPx, bottom),
    )
}

/** Display capability is independent of the current app window, which may be half a tablet. */
internal data class ListeningWindowEnvironment(
    val largeDisplay: Boolean = false,
    val multiWindow: Boolean = false,
)

internal fun isLargeListeningDisplay(widthPx: Int, heightPx: Int, densityDpi: Int): Boolean =
    minOf(widthPx, heightPx).toFloat() * 160f / densityDpi.coerceAtLeast(1) >= 600f

internal fun shouldLockListeningPortrait(environment: ListeningWindowEnvironment): Boolean =
    !environment.largeDisplay && !environment.multiWindow

internal fun shouldUseListeningTwoPane(
    environment: ListeningWindowEnvironment,
    widthDp: Float,
    heightDp: Float,
): Boolean = environment.largeDisplay && !environment.multiWindow &&
    widthDp >= 840f && heightDp >= 480f && widthDp > heightDp
