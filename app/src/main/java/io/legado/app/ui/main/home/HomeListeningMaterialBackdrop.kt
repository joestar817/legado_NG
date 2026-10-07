package io.legado.app.ui.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

/** Listening's established native material; fixed slots retain the common frame and title geometry. */
@Composable
internal fun HomeListeningMaterialBackdrop(
    styleId: String,
    modifier: Modifier = Modifier,
    headerHeight: Dp = HomeWidgetSkinHeaderHeight,
    headerOffset: Dp = 0.dp,
    bottomInset: Dp = 16.dp,
    small: Boolean = false,
) {
    if (styleId != "storybook" && styleId != "night") return
    val night = styleId == "night"
    val density = LocalDensity.current.density
    val grain = remember(styleId, density) { listeningStructuredMaterialPaint(styleId, density) }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val headerStart = headerOffset.toPx().coerceIn(0f, height)
            val headerEnd = (headerStart + headerHeight.toPx()).coerceIn(headerStart, height)
            val base = if (night) listOf(Color(0xFF12243E), Color(0xFF0B192F), Color(0xFF0C1C32))
            else listOf(Color(0xFFFFFBE9), Color(0xFFFDF9EA), Color(0xFFF8F7E7))
            drawRect(Brush.linearGradient(base, Offset.Zero, Offset(width, height)))
            if (night) {
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFFE6B86D).copy(alpha = 0.11f), Color.Transparent),
                    center = Offset(0f, headerStart), radius = width * 0.40f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFFF3BC61).copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(width * 0.95f, height - bottomInset.toPx() - 20.dp.toPx()),
                    radius = width * 0.34f,
                ))
            } else {
                val tint = Color(0xFFE2EDC6)
                if (headerEnd > headerStart) drawRect(
                    brush = Brush.verticalGradient(listOf(tint.copy(alpha = 0.60f),
                        tint.copy(alpha = 0.25f)), startY = headerStart, endY = headerEnd),
                    topLeft = Offset(0f, headerStart), size = Size(width, headerEnd - headerStart),
                )
                if (height > headerEnd) drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFFFEF0).copy(alpha = 0.55f),
                        Color(0xFFFFFBE9).copy(alpha = 0.10f)), startY = headerEnd, endY = height),
                    topLeft = Offset(0f, headerEnd), size = Size(width, height - headerEnd),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                )
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFFF0F5CD).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(width * 0.02f, headerEnd + 48.dp.toPx()), radius = width * 0.65f,
                ))
                drawLine(Color(0xFF7B856D).copy(alpha = 0.08f), Offset(0f, headerEnd),
                    Offset(width, headerEnd), strokeWidth = 0.4.dp.toPx())
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(width * 0.26f, headerEnd + 24.dp.toPx()), radius = width * 0.8f,
                ))
            }
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, width, height, grain) }
        }
        if (night && bottomInset > 0.dp) Image(
            painter = homeWidgetArtworkPainter(R.drawable.ng_home_listening_large_wood), contentDescription = null,
            contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, alpha = 0.60f,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomInset),
        )
        if (night) {
            val leaves = if (night) R.drawable.ng_home_listening_large_night_leaves
                else R.drawable.ng_home_listening_large_paper_bottom
            Image(
                painter = homeWidgetArtworkPainter(leaves), contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = if (night) Alignment.TopEnd else Alignment.BottomStart,
                alpha = if (night) { if (small) 0.45f else 0.55f } else { if (small) 0.55f else 0.70f },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = headerOffset)
                    .size(if (small) { if (night) 65.dp else 54.dp } else { if (night) 107.dp else 100.dp },
                        if (small) { if (night) 57.dp else 67.dp } else { if (night) 63.dp else 68.dp })
                    .then(if (night) Modifier else Modifier.rotate(180f)),
            )
            Image(
                painter = homeWidgetArtworkPainter(leaves), contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = if (night) Alignment.TopEnd else Alignment.BottomStart,
                alpha = if (night) { if (small) 0.32f else 0.60f } else 0.75f,
                modifier = Modifier.align(Alignment.BottomStart)
                    .size(if (night) { if (small) 84.dp else 102.dp } else 78.dp,
                        if (night) { if (small) 54.dp else 53.dp } else 49.dp)
                    .then(if (night) Modifier.rotate(180f) else Modifier),
            )
        } else {
            HomeStorybookLeafBackdrop(Modifier.matchParentSize(), headerOffset, small)
        }
        // Inner contour is drawn after ornaments, as in the refined pre-unification material.
        Canvas(Modifier.matchParentSize()) {
            if (night) drawRect(Brush.radialGradient(
                listOf(Color(0xFFFFD784).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.94f,
                    size.height - bottomInset.toPx() - 20.dp.toPx()), radius = size.width * 0.34f,
            ))
            val radius = HomeWidgetSkinCornerRadius.toPx()
            fun rim(inset: Float, stroke: Float, brush: Brush) {
                drawRoundRect(brush, topLeft = Offset(inset, inset),
                    size = Size((size.width - inset * 2).coerceAtLeast(0f),
                        (size.height - inset * 2).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                    style = Stroke(stroke))
            }
            val soft = if (night) Color(0xFFFFD588) else Color.White
            rim(3.25.dp.toPx(), 6.dp.toPx(), Brush.linearGradient(listOf(
                soft.copy(alpha = if (night) 0.08f else 0.10f), Color.Transparent,
                soft.copy(alpha = if (night) 0.12f else 0.06f))))
            rim(1.75.dp.toPx(), 3.dp.toPx(), Brush.linearGradient(listOf(
                soft.copy(alpha = 0.12f), Color.Transparent, soft.copy(alpha = 0.10f))))
            val edge = if (night) listOf(Color(0xFFF6CC85).copy(alpha = 0.94f),
                Color(0xFFB5C1DB).copy(alpha = 0.48f), Color(0xFFF3BC61).copy(alpha = 0.92f))
            else listOf(Color.White.copy(alpha = 0.96f), Color(0xFFFFFFF2).copy(alpha = 0.75f),
                Color.White.copy(alpha = 0.90f))
            rim(0.85.dp.toPx(), 1.1.dp.toPx(), Brush.linearGradient(edge, Offset.Zero,
                Offset(size.width, size.height)))
        }
    }
}
