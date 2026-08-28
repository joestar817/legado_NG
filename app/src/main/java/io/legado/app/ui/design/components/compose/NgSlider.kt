package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

enum class NgSliderVariant {
    CONTINUOUS,
    DISCRETE,
    COMPACT
}

internal fun ngSliderStepValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    stepDelta: Int = 0,
): Float {
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange must have a positive length"
    }
    require(steps > 0) { "steps must be positive" }
    val intervals = steps + 1
    val rangeLength = valueRange.endInclusive - valueRange.start
    val currentIndex = (((value.coerceIn(valueRange) - valueRange.start) / rangeLength) * intervals)
        .roundToInt()
    val targetIndex = (currentIndex + stepDelta).coerceIn(0, intervals)
    return valueRange.start + rangeLength * targetIndex / intervals
}

/** 无常驻底色的滑轨微调按钮；图标保持轻量，整行高度承担触控区域。 */
@Composable
fun NgSliderStepButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(NgTheme.colors.onSurface),
) {
    Box(
        modifier = modifier
            .width(36.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.width(18.dp).height(18.dp),
            tint = tint.copy(alpha = tint.alpha * if (enabled) 0.74f else 0.28f),
        )
    }
}

/**
 * Reading NG 的通用滑动轨道。
 *
 * 连续型沿用听书进度条的低矮胶囊轨道；离散型使用更清晰的粗轨道并增加刻度和档位吸附；
 * 紧凑型使用阅读底部进度条的细轨道和小滑块，适合窄高受限的浮动工具。
 * [steps] 与 Compose Slider 一致，表示最小值和最大值之间的中间档位数。
 */
@Composable
fun NgSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    variant: NgSliderVariant = NgSliderVariant.CONTINUOUS,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange must have a positive length"
    }
    require(steps >= 0) { "steps must be non-negative" }

    val colors = NgTheme.colors
    val currentValue = value.coerceIn(valueRange)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
    val compact = variant == NgSliderVariant.COMPACT
    fun snapToStep(rawValue: Float): Float {
        if (steps == 0) return rawValue.coerceIn(valueRange)
        return ngSliderStepValue(rawValue, valueRange, steps)
    }

    fun valueForPosition(x: Float, width: Float, thumbRadius: Float): Float {
        val usableWidth = (width - thumbRadius * 2f).coerceAtLeast(1f)
        val fraction = ((x - thumbRadius) / usableWidth).coerceIn(0f, 1f)
        val rawValue = valueRange.start +
            (valueRange.endInclusive - valueRange.start) * fraction
        return snapToStep(rawValue)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 36.dp else 48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = currentValue,
                    range = valueRange,
                    steps = steps
                )
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled) {
                        false
                    } else {
                        currentOnValueChange.value(snapToStep(target))
                        currentOnValueChangeFinished.value?.invoke()
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange, steps, compact) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val thumbRadius = if (compact) 6.dp.toPx() else 12.dp.toPx()
                    currentOnValueChange.value(
                        valueForPosition(down.position.x, size.width.toFloat(), thumbRadius)
                    )
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) {
                                currentOnValueChange.value(
                                    valueForPosition(
                                        change.position.x,
                                        size.width.toFloat(),
                                        thumbRadius
                                    )
                                )
                            }
                            change.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    currentOnValueChangeFinished.value?.invoke()
                }
            }
    ) {
        val thumbRadius = if (compact) 6.dp.toPx() else 12.dp.toPx()
        val innerThumbRadius = 8.dp.toPx()
        val trackHeight = when (variant) {
            NgSliderVariant.CONTINUOUS -> 6.dp.toPx()
            NgSliderVariant.DISCRETE -> 10.dp.toPx()
            NgSliderVariant.COMPACT -> 2.dp.toPx()
        }
        val trackStart = thumbRadius
        val trackWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
        val trackTop = (size.height - trackHeight) / 2f
        val trackRadius = trackHeight / 2f
        // 刻度与滑块仍按逻辑轨道定位；可见轨道向首尾各延伸一个圆角半径，
        // 让端点刻度完整落在轨道内，同时不改变触控与吸附范围。
        val visibleTrackStart = trackStart - trackRadius
        val visibleTrackWidth = trackWidth + trackHeight
        val rangeLength = valueRange.endInclusive - valueRange.start
        val fraction = ((currentValue - valueRange.start) / rangeLength).coerceIn(0f, 1f)
        val thumbX = trackStart + trackWidth * fraction
        val enabledAlpha = if (enabled) 1f else 0.45f
        val primary = Color(colors.primary).copy(alpha = enabledAlpha)
        val inactive = Color(colors.primary).copy(alpha = 0.16f * enabledAlpha)
        val thumbSurface = Color(colors.surface).copy(alpha = enabledAlpha)

        drawRoundRect(
            color = inactive,
            topLeft = Offset(visibleTrackStart, trackTop),
            size = Size(visibleTrackWidth, trackHeight),
            cornerRadius = CornerRadius(trackRadius)
        )
        drawRoundRect(
            color = primary,
            topLeft = Offset(visibleTrackStart, trackTop),
            size = Size(
                width = (thumbX + trackRadius - visibleTrackStart)
                    .coerceIn(0f, visibleTrackWidth),
                height = trackHeight
            ),
            cornerRadius = CornerRadius(trackRadius)
        )

        if (variant == NgSliderVariant.DISCRETE) {
            val tickCount = steps + 2
            repeat(tickCount) { index ->
                val tickFraction = index.toFloat() / (tickCount - 1)
                val tickX = trackStart + trackWidth * tickFraction
                drawCircle(
                    color = if (tickFraction <= fraction) thumbSurface else primary,
                    radius = 2.25.dp.toPx(),
                    center = Offset(tickX, size.height / 2f)
                )
            }
        }

        if (compact) {
            drawCircle(
                color = primary,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2f)
            )
        } else {
            drawCircle(
                color = thumbSurface,
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2f)
            )
            drawCircle(
                color = primary,
                radius = innerThumbRadius,
                center = Offset(thumbX, size.height / 2f)
            )
            drawCircle(
                color = primary.copy(alpha = 0.42f * enabledAlpha),
                radius = thumbRadius,
                center = Offset(thumbX, size.height / 2f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
