package io.legado.app.ui.book.read.config

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.book.character.readerCharacterHeaderAccentColor
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.PI
import kotlin.math.cos

@Composable
internal fun AiPurifyOrb() {
    val theme = NgTheme.snapshot
    val accent = readerCharacterHeaderAccentColor()
    val card = ngDrawerContentCardColor()
    val highlight = lerp(Color.White, accent, if (theme.isDark) 0.60f else 0.06f)
    val animated = !theme.isEInk &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())
    val cycle = if (animated) {
        rememberInfiniteTransition(label = "purify orb").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_600, easing = LinearEasing)),
            label = "purify orb cycle",
        )
    } else null

    Box(Modifier.size(144.dp).drawWithCache {
        val middle = Offset(size.width / 2f, size.height / 2f)
        val orbRadius = 53.dp.toPx()
        val ringRadius = 71.dp.toPx()
        val ringStroke = Stroke(1.dp.toPx())
        val orbBounds = Rect(
            middle - Offset(orbRadius, orbRadius), Size(orbRadius * 2, orbRadius * 2),
        )
        val circle = Path().apply { addOval(orbBounds) }
        val glass = Brush.radialGradient(
            0f to lerp(card, Color.White, if (theme.isDark) 0.27f else 0.72f),
            0.49f to lerp(card, accent, 0.10f),
            1f to lerp(card, accent, if (theme.isDark) 0.29f else 0.24f),
            center = Offset(middle.x - 17.dp.toPx(), middle.y - 28.dp.toPx()),
            radius = 86.dp.toPx(),
        )
        val halo = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
            center = middle,
            radius = ringRadius,
        )
        val beamHeight = 24.dp.toPx()
        val beam = Brush.verticalGradient(
            0f to Color.Transparent,
            0.47f to accent.copy(alpha = 0.13f),
            0.52f to highlight,
            0.56f to accent.copy(alpha = 0.28f),
            1f to Color.Transparent,
            startY = middle.y - beamHeight / 2,
            endY = middle.y + beamHeight / 2,
        )
        val lineHeight = 2.dp.toPx()
        val lineCorner = CornerRadius(lineHeight / 2)
        val lineWidths = floatArrayOf(26.dp.toPx(), 42.dp.toPx(), 33.dp.toPx())
        val left = middle.x - 21.dp.toPx()
        val markSize = Size(13.dp.toPx(), 11.dp.toPx())
        val markPosition = Offset(left + 22.dp.toPx(), middle.y - markSize.height / 2)
        val markCorner = CornerRadius(3.dp.toPx())
        val rimInset = 6.dp.toPx()

        onDrawBehind {
            val phase = cycle?.value ?: 0f
            val lift = if (animated) ((1f - cos(phase * 2 * PI).toFloat()) / 2f) else 0f
            if (!theme.isEInk) drawCircle(halo, ringRadius, middle)
            drawCircle(accent.copy(alpha = 0.20f), ringRadius, middle, style = ringStroke)
            drawCircle(accent.copy(alpha = 0.14f), ringRadius - 10.dp.toPx(), middle, style = ringStroke)
            drawArc(
                color = accent.copy(alpha = 0.70f),
                startAngle = phase * 360f - 112f,
                sweepAngle = 66f,
                useCenter = false,
                topLeft = middle - Offset(ringRadius, ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round),
            )
            translate(top = if (animated) (1f - lift * 4f).dp.toPx() else 0f) {
                scale(if (animated) 0.995f + lift * 0.028f else 1f, middle) {
                    if (theme.isEInk) drawCircle(card, orbRadius, middle)
                    else drawCircle(glass, orbRadius, middle)
                    drawCircle(accent.copy(alpha = 0.27f), orbRadius, middle, style = ringStroke)
                    if (!theme.isEInk) {
                        drawArc(
                            color = highlight.copy(alpha = 0.72f),
                            startAngle = -155f,
                            sweepAngle = 88f,
                            useCenter = false,
                            topLeft = orbBounds.topLeft + Offset(rimInset, rimInset),
                            size = Size((orbRadius - rimInset) * 2, (orbRadius - rimInset) * 2),
                            style = Stroke(1.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    val beamAlpha = if (animated) {
                        minOf((phase - 0.08f) / 0.12f, (0.63f - phase) / 0.14f).coerceIn(0f, 1f) * 0.92f
                    } else 0.42f
                    if (!theme.isEInk && beamAlpha > 0f) {
                        val offset = if (animated) {
                            (-57f + 112f * ((phase - 0.08f) / 0.55f).coerceIn(0f, 1f)).dp.toPx()
                        } else 0f
                        clipPath(circle) {
                            translate(top = offset) {
                                drawRect(
                                    beam,
                                    topLeft = Offset(orbBounds.left, middle.y - beamHeight / 2),
                                    size = Size(orbRadius * 2, beamHeight),
                                    alpha = beamAlpha,
                                )
                            }
                        }
                    }
                    lineWidths.forEachIndexed { index, width ->
                        drawRoundRect(
                            accent,
                            topLeft = Offset(left, middle.y + (index - 1) * 9.dp.toPx() - lineHeight / 2),
                            size = Size(width, lineHeight),
                            cornerRadius = lineCorner,
                        )
                    }
                    val markAlpha = 1f - minOf((phase - 0.34f) / 0.13f, (1f - phase) / 0.12f)
                        .coerceIn(0f, 1f)
                    if (markAlpha > 0f) {
                        drawRoundRect(accent.copy(alpha = 0.15f * markAlpha), markPosition, markSize, markCorner)
                        drawRoundRect(
                            accent.copy(alpha = 0.68f * markAlpha),
                            markPosition, markSize, markCorner, style = ringStroke,
                        )
                    }
                }
            }
        }
    })
}
