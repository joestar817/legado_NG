package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

@Composable
internal fun AiDiscreteScaleDialogContent(
    title: String,
    description: String?,
    @DrawableRes iconRes: Int,
    labels: List<String>,
    currentLabels: List<String>,
    initialSelectedIndex: Int,
    tintIcon: (Int) -> Boolean,
    onSelectedIndexChanged: (Int) -> Unit,
) {
    var selectedIndex by remember(labels, initialSelectedIndex) {
        mutableIntStateOf(initialSelectedIndex.coerceIn(labels.indices))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colorResource(R.color.ng_surface_card))
            .padding(start = 24.dp, top = 26.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            color = colorResource(R.color.ng_on_surface),
            fontSize = 20.sp,
            lineHeight = 25.sp,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (description.isNullOrBlank()) {
            Spacer(Modifier.height(22.dp))
        } else {
            Text(
                text = description,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 22.dp),
                color = colorResource(R.color.ng_on_surface_variant),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
            )
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = if (tintIcon(selectedIndex)) {
                Color(NgTheme.colors.primary)
            } else {
                Color.Unspecified
            },
        )
        Text(
            text = currentLabels.getOrElse(selectedIndex) { labels[selectedIndex] },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 22.dp),
            color = colorResource(R.color.ng_on_surface),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
        AiDiscreteStepBar(
            stepCount = labels.size,
            selectedIndex = selectedIndex,
            onSelectedIndexChanged = { index ->
                if (index != selectedIndex) {
                    selectedIndex = index
                    onSelectedIndexChanged(index)
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = if (labels.size >= 10) 11.sp else 13.sp,
                    lineHeight = if (labels.size >= 10) 14.sp else 16.sp,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AiDiscreteStepBar(
    stepCount: Int,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val inactive = primary.copy(alpha = 38f / 255f)
    val tick = primary.copy(alpha = 190f / 255f)
    fun indexForX(x: Float, width: Float): Int {
        if (stepCount <= 1 || width <= 0f) return 0
        val segmentWidth = width / stepCount
        val startX = segmentWidth / 2f
        val endX = width - segmentWidth / 2f
        val progress = ((x.coerceIn(startX, endX) - startX) / (endX - startX))
            .coerceIn(0f, 1f)
        return (progress * (stepCount - 1)).roundToInt().coerceIn(0, stepCount - 1)
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .pointerInput(stepCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onSelectedIndexChanged(indexForX(down.position.x, size.width.toFloat()))
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) {
                                onSelectedIndexChanged(
                                    indexForX(change.position.x, size.width.toFloat())
                                )
                            }
                            change.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                }
            },
    ) {
        if (stepCount <= 1) return@Canvas
        val y = size.height / 2f
        val segmentWidth = size.width / stepCount
        fun centerX(index: Int): Float = segmentWidth * index + segmentWidth / 2f
        fun radius(index: Int): Float = if (index == selectedIndex) 6.dp.toPx() else 4.5.dp.toPx()
        repeat(stepCount - 1) { index ->
            drawLine(
                color = if (index < selectedIndex) primary else inactive,
                start = Offset(centerX(index) + radius(index), y),
                end = Offset(centerX(index + 1) - radius(index + 1), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        repeat(stepCount) { index ->
            if (index == selectedIndex) {
                drawCircle(
                    color = primary,
                    radius = 6.dp.toPx(),
                    center = Offset(centerX(index), y),
                )
            } else {
                drawCircle(
                    color = tick,
                    radius = 4.5.dp.toPx(),
                    center = Offset(centerX(index), y),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}
