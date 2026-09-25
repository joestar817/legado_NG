package io.legado.app.ui.book.explore

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.compose.NgGlassGeometry
import io.legado.app.ui.design.components.compose.NgSegmentedGlassShape
import kotlin.math.ceil

/** One outline for the panel and pointer, so glass and its edge have no seam. */
internal class ExploreCategoryBubbleShape(
    private val cornerRadius: Dp,
    private val anchorX: Float?
) : NgSegmentedGlassShape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val (top, radius, halfPointer, center) = geometry(size, density)
        return Outline.Generic(Path().apply {
            moveTo(radius, top)
            lineTo(center - halfPointer, top)
            lineTo(center, 0f)
            lineTo(center + halfPointer, top)
            lineTo(size.width - radius, top)
            quadraticTo(size.width, top, size.width, top + radius)
            lineTo(size.width, size.height - radius)
            quadraticTo(size.width, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, top + radius)
            quadraticTo(0f, top, radius, top)
            close()
        })
    }

    override fun createContentOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val (t, r) = geometry(size, density)
        return Outline.Generic(Path().apply {
            moveTo(r, t); lineTo(size.width - r, t)
            quadraticTo(size.width, t, size.width, t + r)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(r, size.height)
            quadraticTo(0f, size.height, 0f, size.height - r)
            lineTo(0f, t + r); quadraticTo(0f, t, r, t)
            close()
        })
    }

    private fun geometry(size: Size, density: Density): FloatArray {
        val top = with(density) { 4.dp.toPx() }.coerceAtMost(size.height)
        val radius = with(density) { cornerRadius.toPx() }
            .coerceAtMost(size.width / 2f).coerceAtMost((size.height - top) / 2f)
        val halfPointer = with(density) { 6.dp.toPx() }
            .coerceAtMost((size.width / 2f - radius).coerceAtLeast(0f))
        val center = (anchorX ?: (size.width - with(density) { 60.dp.toPx() }))
            .coerceIn(radius + halfPointer, size.width - radius - halfPointer)
        return floatArrayOf(top, radius, halfPointer, center)
    }

    override fun createGlassGeometry(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): NgGlassGeometry {
        val (t, r, half, center) = geometry(size, density)
        val w = size.width
        val h = size.height
        // Integral internal boundaries avoid antialias seams between adjacent fills.
        val q = ceil(r).coerceAtMost(w / 2f).coerceAtMost((h - t) / 2f)
        fun path(block: Path.() -> Unit) = Path().apply(block)
        val corners = listOf(
            path { moveTo(0f, t + q); lineTo(0f, t + r); quadraticTo(0f, t, r, t)
                lineTo(q, t); lineTo(q, t + q); close() },
            path { moveTo(w - q, t); lineTo(w - r, t); quadraticTo(w, t, w, t + r)
                lineTo(w, t + q); lineTo(w - q, t + q); close() },
            path { moveTo(w, h - q); lineTo(w, h - r); quadraticTo(w, h, w - r, h)
                lineTo(w - q, h); lineTo(w - q, h - q); close() },
            path { moveTo(q, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r)
                lineTo(0f, h - q); lineTo(q, h - q); close() }
        )
        val pointer = path {
            moveTo(center - half, t); lineTo(center, 0f); lineTo(center + half, t); close()
        }
        val edges = listOf(
            path { moveTo(r, t); lineTo(center - half, t); lineTo(center, 0f)
                lineTo(center + half, t); lineTo(w - r, t) },
            path { moveTo(w - r, t); quadraticTo(w, t, w, t + r) },
            path { moveTo(w, t + r); lineTo(w, h - r) },
            path { moveTo(w, h - r); quadraticTo(w, h, w - r, h) },
            path { moveTo(w - r, h); lineTo(r, h) },
            path { moveTo(r, h); quadraticTo(0f, h, 0f, h - r) },
            path { moveTo(0f, h - r); lineTo(0f, t + r) },
            path { moveTo(0f, t + r); quadraticTo(0f, t, r, t) }
        )
        return NgGlassGeometry(
            fillRects = listOf(
                Rect(q, t, w - q, h),
                Rect(0f, t + q, q, h - q),
                Rect(w - q, t + q, w, h - q)
            ),
            fillPaths = corners + pointer,
            strokePaths = edges
        )
    }
}
