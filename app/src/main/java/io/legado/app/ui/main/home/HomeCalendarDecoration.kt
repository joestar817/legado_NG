package io.legado.app.ui.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draw beneath the dates without contributing size, semantics or pointer handling. */
@Composable
internal fun HomeCalendarGridArtwork(gridWidth: Dp, weekdayHeight: Dp, modifier: Modifier, styleId: String?) {
    val artwork = when (styleId) {
        "storybook" -> homeWidgetArtworkPainter(styleId, "calendar")
        "blue_fish" -> homeWidgetArtworkPainter(styleId, "calendar")
        "night" -> homeWidgetArtworkPainter(styleId, "lamp")
        else -> return
    } ?: return
    Canvas(modifier.clearAndSetSemantics {}) {
        val edge = minOf(gridWidth.toPx() * 0.55f, (size.height - weekdayHeight.toPx()).coerceAtLeast(0f))
        if (edge > 0f) {
            // Extend into the card's existing 14dp padding, leaving about 7dp at its edge.
            translate(left = size.width - edge + 7.dp.toPx(), top = size.height - edge) {
                val alpha = when (styleId) {
                    "night" -> 0.22f
                    else -> 1f
                }
                with(artwork) { draw(Size(edge, edge), alpha = alpha) }
            }
        }
    }
}
