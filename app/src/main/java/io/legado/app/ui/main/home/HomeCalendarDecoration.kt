package io.legado.app.ui.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

/** Draw beneath the dates without contributing size, semantics or pointer handling. */
@Composable
internal fun HomeCalendarGridArtwork(gridWidth: Dp, weekdayHeight: Dp, modifier: Modifier, styleId: String?) {
    val artwork = when (styleId) {
        "storybook" -> R.drawable.ng_home_calendar_story_cat
        "night" -> R.drawable.ng_home_calendar_night_lamp
        else -> return
    }
    val painter = homeWidgetArtworkPainter(artwork)
    Canvas(modifier.clearAndSetSemantics {}) {
        val edge = minOf(gridWidth.toPx() * 0.55f, (size.height - weekdayHeight.toPx()).coerceAtLeast(0f))
        if (edge > 0f) {
            // Extend into the card's existing 14dp padding, leaving about 7dp at its edge.
            translate(left = size.width - edge + 7.dp.toPx(), top = size.height - edge) {
                if (styleId == "night") {
                    with(painter) { draw(Size(edge, edge), alpha = 0.22f) }
                } else {
                    with(painter) { draw(Size(edge, edge)) }
                }
            }
        }
    }
}
