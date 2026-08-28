package io.legado.app.ui.book.read.config

import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.utils.applyTint
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

@Composable
internal fun ReadConfigDialogSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    NgGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = readFloatingGlassStyle(),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun ReadConfigDialogTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        color = Color(NgTheme.colors.onSurface),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ReadConfigDock(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    contentColor: Color? = null,
    accessibilityLabel: String? = null,
) {
    if (labels.isEmpty()) return
    val colors = NgTheme.colors
    val unselectedContentColor = contentColor ?: Color(colors.onSurface)
    val selected = selectedIndex.coerceIn(labels.indices)
    val selectedContentColor = if (ColorUtils.calculateLuminance(colors.primary) > 0.5) {
        Color.Black
    } else {
        Color.White
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(ReadDrawerStyle.dockSurfaceColor())
            .padding(3.dp)
            .semantics {
                accessibilityLabel?.let { contentDescription = it }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) Modifier.background(Color(colors.primary)) else Modifier
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelected(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = if (isSelected) selectedContentColor else unselectedContentColor,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ReadConfigSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val dragState = remember { mutableIntStateOf(value.coerceIn(valueRange)) }
    val trackingState = remember { mutableStateOf(false) }
    val currentCallback = rememberUpdatedState(onValueChange)
    val currentRange = rememberUpdatedState(valueRange)
    val committedValue = value.coerceIn(valueRange)
    val displayValue = if (trackingState.value) dragState.intValue else committedValue
    fun applyStep(delta: Int) {
        val updatedValue = (displayValue + delta).coerceIn(currentRange.value)
        dragState.intValue = updatedValue
        trackingState.value = false
        currentCallback.value(updatedValue)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.width(48.dp),
            color = Color(colors.onSurface),
            fontSize = 14.sp,
            maxLines = 1,
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_reduce,
            contentDescription = "$title，${stringResource(R.string.reduce)}",
            enabled = displayValue > valueRange.first,
            onClick = { applyStep(-1) },
            tint = Color(colors.onSurface),
        )
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            factory = { context ->
                AppCompatSeekBar(context).apply {
                    max = currentRange.value.last - currentRange.value.first
                    progress = displayValue - currentRange.value.first
                    applyTint(colors.primary)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            if (fromUser) {
                                val range = currentRange.value
                                dragState.intValue = (progress + range.first).coerceIn(range)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar) {
                            val range = currentRange.value
                            dragState.intValue = (seekBar.progress + range.first).coerceIn(range)
                            trackingState.value = true
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar) {
                            val range = currentRange.value
                            val finalValue = (seekBar.progress + range.first).coerceIn(range)
                            dragState.intValue = finalValue
                            currentCallback.value(finalValue)
                            trackingState.value = false
                        }
                    })
                }
            },
            update = { seekBar ->
                seekBar.max = valueRange.last - valueRange.first
                val progress = displayValue - valueRange.first
                if (!seekBar.isPressed && seekBar.progress != progress) {
                    seekBar.progress = progress
                }
                seekBar.applyTint(colors.primary)
            },
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_add,
            contentDescription = "$title，${stringResource(R.string.plus)}",
            enabled = displayValue < valueRange.last,
            onClick = { applyStep(1) },
            tint = Color(colors.onSurface),
        )
        Text(
            text = displayValue.toString(),
            modifier = Modifier.width(36.dp),
            color = Color(colors.onSurface),
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/** 与迁移前 NgDiscreteStepBar 的尺寸、线宽和触控吸附保持一致。 */
@Composable
internal fun ReadConfigDiscreteStepBar(
    stepCount: Int,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stepColor: Color = Color(NgTheme.colors.primary),
    accessibilityLabel: String? = null,
) {
    if (stepCount <= 1) return
    val selected = selectedIndex.coerceIn(0, stepCount - 1)
    val currentSelected = rememberUpdatedState(selected)
    val currentCallback = rememberUpdatedState(onSelectedIndexChanged)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .semantics {
                accessibilityLabel?.let { contentDescription = it }
            }
            .pointerInput(stepCount) {
                fun updateSelection(x: Float) {
                    val segmentWidth = size.width.toFloat() / stepCount
                    val startX = segmentWidth / 2f
                    val endX = segmentWidth * (stepCount - 1) + segmentWidth / 2f
                    if (endX <= startX) return
                    val fraction = ((x.coerceIn(startX, endX) - startX) / (endX - startX))
                        .coerceIn(0f, 1f)
                    val index = (fraction * (stepCount - 1)).roundToInt()
                        .coerceIn(0, stepCount - 1)
                    if (index != currentSelected.value) currentCallback.value(index)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateSelection(down.position.x)
                    var pressed: Boolean
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) updateSelection(change.position.x)
                            change.consume()
                        }
                        pressed = event.changes.any { it.pressed }
                    } while (pressed)
                }
            },
    ) {
        val tickRadius = 4.5.dp.toPx()
        val selectedRadius = 6.dp.toPx()
        val lineWidth = 2.dp.toPx()
        val tickWidth = 1.5.dp.toPx()
        val segmentWidth = size.width / stepCount
        val centerY = size.height / 2f
        fun centerX(index: Int) = segmentWidth * index + segmentWidth / 2f
        fun radius(index: Int) = if (index == selected) selectedRadius else tickRadius

        repeat(stepCount - 1) { index ->
            drawLine(
                color = if (index < selected) stepColor else stepColor.copy(alpha = 38f / 255f),
                start = androidx.compose.ui.geometry.Offset(
                    centerX(index) + radius(index),
                    centerY,
                ),
                end = androidx.compose.ui.geometry.Offset(
                    centerX(index + 1) - radius(index + 1),
                    centerY,
                ),
                strokeWidth = lineWidth,
            )
        }
        repeat(stepCount) { index ->
            if (index == selected) {
                drawCircle(stepColor, selectedRadius, androidx.compose.ui.geometry.Offset(centerX(index), centerY))
            } else {
                drawCircle(
                    color = stepColor.copy(alpha = 190f / 255f),
                    radius = tickRadius,
                    center = androidx.compose.ui.geometry.Offset(centerX(index), centerY),
                    style = Stroke(width = tickWidth),
                )
            }
        }
    }
}

@Composable
internal fun ReadConfigSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color(colors.onSurface),
            fontSize = 14.sp,
        )
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}
