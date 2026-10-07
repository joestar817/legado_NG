package io.legado.app.ui.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Named optical treatment for the three reference presets; default NG controls are untouched. */
@Composable
internal fun HomeListeningReferenceDisc(
    reference: HomeListeningReference,
    primary: Boolean,
    editing: Boolean,
    modifier: Modifier,
) {
    HomeWidgetControlDisc(reference.styleId, primary, editing, modifier)
}

/** Material only: shared functional controls keep their measured touch and caption geometry. */
@Composable
internal fun HomeWidgetControlDisc(styleId: String, primary: Boolean, editing: Boolean, modifier: Modifier) {
    val night = styleId == "night"
    val story = styleId == "storybook"
    val refined = night || story
    val alpha = if (editing) 0.4f else 1f
    val colors = when {
        primary && night -> listOf(Color(0xFFFFD780), Color(0xFFF5BE5C), Color(0xFFEBA648))
        primary && story -> listOf(Color(0xFF337758), Color(0xFF27664A), Color(0xFF1B5238))
        primary -> listOf(Color(0xFF0D8D78), Color(0xFF087F6B), Color(0xFF056D5B))
        night -> listOf(Color(0xFF405071), Color(0xFF2E3954), Color(0xFF222B40))
        story -> listOf(Color(0xFFFFFFF8), Color(0xFFFCF9EB), Color(0xFFF1EED8))
        else -> listOf(Color(0xFFFFFEF8), Color(0xFFFAF9EF), Color(0xFFF0EFD9))
    }.map { it.copy(alpha = alpha) }
    Canvas(modifier.shadow(
        if (editing) 1.dp else if (primary) 2.dp else 3.dp,
        CircleShape, clip = false,
        ambientColor = (if (night) Color.Black else Color(0xFF4B6242)).copy(alpha = 0.12f * alpha),
        spotColor = (if (night) Color.Black else Color(0xFF71815C)).copy(alpha = 0.18f * alpha),
    )) {
        if (refined && !editing) {
            val shadowCenter = center + Offset(0f, 2.5.dp.toPx())
            val shadowRadius = size.minDimension / 2 + 5.dp.toPx()
            val shade = if (night) Color.Black else Color(0xFF6B7451)
            drawCircle(Brush.radialGradient(
                0f to shade.copy(alpha = 0.16f), 0.70f to shade.copy(alpha = 0.12f),
                0.86f to shade.copy(alpha = 0.05f), 1f to Color.Transparent,
                center = shadowCenter, radius = shadowRadius,
            ), radius = shadowRadius, center = shadowCenter)
        }
        val rim = when {
            primary && night -> Color(0xFFFFE5A8)
            night -> Color(0xFF8296C6).copy(alpha = 0.6f)
            primary -> Color.White.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.9f)
        }.let { it.copy(alpha = it.alpha * alpha) }
        drawCircle(Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height)))
        if (refined) {
            drawCircle(Brush.radialGradient(
                listOf(Color.White.copy(alpha = (if (primary) 0.065f else 0.11f) * alpha), Color.Transparent),
                center = Offset(size.width * 0.25f, size.height * 0.12f), radius = size.width * 0.8f,
            ))
        }
        drawCircle(rim, radius = size.minDimension / 2 - 0.35.dp.toPx(), style = Stroke(0.7.dp.toPx()))
    }
}

@Composable
internal fun HomeListeningReferencePlayGlyph(playing: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        if (playing) {
            drawRoundRect(color, Offset(6.5f * unit, 5f * unit), Size(3.5f * unit, 14f * unit),
                androidx.compose.ui.geometry.CornerRadius(0.6f * unit))
            drawRoundRect(color, Offset(14f * unit, 5f * unit), Size(3.5f * unit, 14f * unit),
                androidx.compose.ui.geometry.CornerRadius(0.6f * unit))
        } else {
            val path = Path().apply {
                moveTo(7.2f * unit, 5.3f * unit)
                quadraticTo(7.2f * unit, 4.3f * unit, 8.2f * unit, 4.9f * unit)
                lineTo(20.1f * unit, 11.2f * unit)
                quadraticTo(21.2f * unit, 12f * unit, 20.1f * unit, 12.8f * unit)
                lineTo(8.2f * unit, 19.1f * unit)
                quadraticTo(7.2f * unit, 19.7f * unit, 7.2f * unit, 18.7f * unit)
                close()
            }
            drawPath(path, color)
        }
    }
}
