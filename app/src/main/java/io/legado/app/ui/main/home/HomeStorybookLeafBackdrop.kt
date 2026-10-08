package io.legado.app.ui.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

/** Match the theme's richer leaf greens without recoloring the card or flattening leaf veins. */
internal val HomeStorybookLeafColorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    1.10f, 0f, 0f, 0f, -68f,
    0f, 1.10f, 0f, 0f, -42f,
    0f, 0f, 1.10f, 0f, -78f,
    0f, 0f, 0f, 1f, 0f,
)))

/** Fit the original branch itself, so rotation never moves padding into the corner. */
@Composable
internal fun HomeStorybookLeafBackdrop(
    modifier: Modifier = Modifier,
    headerOffset: Dp = 0.dp,
    small: Boolean = false,
) {
    val leaves = homeWidgetArtworkPainter(R.drawable.ng_home_listening_large_paper_bottom)
    Canvas(modifier) {
        val headerStart = headerOffset.toPx().coerceIn(0f, size.height)
        val availableHeight = size.height - headerStart
        val aspect = 306f / 384f
        val topWidth = (if (small) 80.dp else 112.dp).toPx()
        val bottomWidth = (if (small) 64.dp else 96.dp).toPx()
        val factor = minOf(1f, size.width / topWidth,
            availableHeight / ((topWidth + bottomWidth) * aspect + 12.dp.toPx()))
        fun fittedSize(width: Dp): Size {
            val desiredWidth = width.toPx()
            return Size(desiredWidth * factor, desiredWidth * aspect * factor)
        }
        if (size.width > 0f && availableHeight > 0f) clipRect(top = headerStart) {
            val top = fittedSize(if (small) 80.dp else 112.dp)
            translate(size.width - top.width + 4.dp.toPx() * factor,
                headerStart - 6.dp.toPx() * factor) {
                rotate(180f, pivot = Offset(top.width / 2f, top.height / 2f)) {
                    with(leaves) { draw(top, colorFilter = HomeStorybookLeafColorFilter) }
                }
            }
            val bottom = fittedSize(if (small) 64.dp else 96.dp)
            translate(-4.dp.toPx() * factor,
                size.height - bottom.height + 6.dp.toPx() * factor) {
                with(leaves) { draw(bottom, colorFilter = HomeStorybookLeafColorFilter) }
            }
        }
    }
}
