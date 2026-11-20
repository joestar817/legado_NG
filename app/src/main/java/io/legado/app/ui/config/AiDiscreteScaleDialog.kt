package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.assistantContextWindowLabel
import io.legado.app.help.ai.assistantContextWindowPosition
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
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
    fitLabels: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    onSelectedIndexChanged: (Int) -> Unit,
) {
    var selectedIndex by remember(labels, initialSelectedIndex) {
        mutableIntStateOf(initialSelectedIndex.coerceIn(labels.indices))
    }
    AiScaleDialogFrame(
        title = title,
        description = description,
        iconRes = iconRes,
        iconTint = if (tintIcon(selectedIndex)) Color(NgTheme.colors.primary) else Color.Unspecified,
        currentLabel = currentLabels.getOrElse(selectedIndex) { labels[selectedIndex] },
        horizontalPadding = if (fitLabels && labels.size >= 8) 12.dp else 24.dp,
    ) {
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
        AiDiscreteScaleLabels(labels, fitLabels)
        footer?.invoke()
    }
}

@Composable
private fun AiDiscreteScaleLabels(labels: List<String>, fitLabels: Boolean) {
    val compact = !fitLabels && labels.size >= 10
    val labelContent: @Composable (String, Modifier) -> Unit = { label, modifier ->
        Text(
            text = label,
            modifier = modifier,
            color = colorResource(R.color.ng_on_surface_variant),
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 14.sp else 16.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = if (fitLabels) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
    val modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    if (!fitLabels) {
        Row(modifier) {
            labels.forEach { labelContent(it, Modifier.weight(1f)) }
        }
        return
    }

    Layout(
        modifier = modifier,
        content = { labels.forEach { labelContent(it, Modifier) } },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val stepWidth = width.toFloat() / labels.size
        val gap = minOf(2.dp.roundToPx(), width / labels.size)
        val centers = labels.indices.map { ((it + 0.5f) * stepWidth).roundToInt() }
        val labelWidths = measurables.map {
            it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(width)
        }
        val centeredStarts = labelWidths.mapIndexed { index, labelWidth ->
            (centers[index] - labelWidth / 2).coerceIn(0, width - labelWidth)
        }
        val stagger = (1 until labels.size).any { index ->
            centeredStarts[index] < centeredStarts[index - 1] + labelWidths[index - 1] + gap
        }

        // When adjacent labels collide, alternate rows while keeping each label on its tick.
        val startBounds = IntArray(labels.size) { index ->
            if (stagger && index >= 2) centers[index - 1] + gap / 2 else 0
        }
        val endBounds = IntArray(labels.size) { index ->
            if (stagger && index + 2 < labels.size) centers[index + 1] - (gap - gap / 2) else width
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints(maxWidth = endBounds[index] - startBounds[index]))
        }
        val rowHeight = placeables.maxOf { it.height }
        val secondRow = if (stagger) rowHeight + 4.dp.roundToPx() else 0
        layout(width, rowHeight + secondRow) {
            placeables.forEachIndexed { index, placeable ->
                val x = (centers[index] - placeable.width / 2)
                    .coerceIn(startBounds[index], endBounds[index] - placeable.width)
                placeable.place(x, (index % 2) * secondRow)
            }
        }
    }
}

@Composable
internal fun AiAssistantContextWindowDialogContent(
    options: List<Int>,
    initialWindow: Int,
    selectedModel: AiAssistantConfigUi.AssistantModelOption?,
    onWindowSelected: (Int) -> Unit,
) {
    var selectedWindow by remember(options, initialWindow) { mutableIntStateOf(initialWindow) }
    var committedWindow by remember(options, initialWindow) { mutableIntStateOf(initialWindow) }
    val labels = remember(options) { options.map(::assistantContextWindowLabel) }
    val currentLabel = assistantContextWindowLabel(selectedWindow)

    AiScaleDialogFrame(
        title = stringResource(R.string.ai_assistant_context_window_dialog_title),
        description = stringResource(R.string.ai_assistant_context_window_dialog_desc),
        iconRes = R.drawable.ic_ai_context_menu,
        iconTint = Color(NgTheme.colors.primary),
        currentLabel = currentLabel,
        descriptionOverlay = {
            if (selectedModel != null) {
                val modelIconRes = remember(selectedModel) {
                    selectedModel.model.iconRes(selectedModel.provider.iconRes())
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(modelIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified,
                    )
                    Text(
                        text = selectedModel.model.displayName(),
                        color = colorResource(R.color.ng_on_surface_variant),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        AiNgStepScale(
            labels = labels,
            currentLabel = currentLabel,
            position = assistantContextWindowPosition(options, selectedWindow),
            onPositionChange = { position ->
                selectedWindow = options[position.roundToInt().coerceIn(options.indices)]
            },
            onPositionChangeFinished = {
                if (selectedWindow != committedWindow) {
                    committedWindow = selectedWindow
                    onWindowSelected(selectedWindow)
                }
            },
        )
    }
}

@Composable
internal fun AiCompactionThresholdDialogContent(
    initialPercent: Int,
    onThresholdSelected: (Int) -> Unit,
) {
    val options = AiConfig.CONTEXT_COMPACTION_THRESHOLD_OPTIONS
    val labels = remember(options) { options.map(Int::toString) }
    val initialIndex = options.indexOf(initialPercent).coerceAtLeast(0)
    var selectedIndex by remember(initialIndex) { mutableIntStateOf(initialIndex) }
    var committedIndex by remember(initialIndex) { mutableIntStateOf(initialIndex) }
    val selectedPercent = options[selectedIndex]
    val currentLabel = if (selectedPercent == 0) {
        stringResource(R.string.ai_context_compaction_off)
    } else {
        "$selectedPercent%"
    }
    AiScaleDialogFrame(
        title = stringResource(R.string.ai_context_compaction_threshold_dialog_title),
        description = stringResource(R.string.ai_context_compaction_threshold_dialog_desc),
        iconRes = R.drawable.ic_read_aloud_speed,
        iconTint = if (selectedPercent == 0) Color.Unspecified else Color(NgTheme.colors.primary),
        currentLabel = currentLabel,
    ) {
        AiNgStepScale(
            labels = labels,
            currentLabel = currentLabel,
            position = selectedIndex.toFloat(),
            onPositionChange = { position ->
                selectedIndex = position.roundToInt().coerceIn(options.indices)
            },
            onPositionChangeFinished = {
                if (selectedIndex != committedIndex) {
                    committedIndex = selectedIndex
                    onThresholdSelected(options[selectedIndex])
                }
            },
        )
    }
}

@Composable
private fun AiNgStepScale(
    labels: List<String>,
    currentLabel: String,
    position: Float,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val thumbInset = 12.dp
        val endpointInset = (maxWidth / 14).coerceAtLeast(thumbInset)
        Column {
            NgSlider(
                value = position,
                onValueChange = onPositionChange,
                valueRange = 0f..labels.lastIndex.coerceAtLeast(1).toFloat(),
                modifier = Modifier
                    .height(42.dp)
                    .padding(horizontal = endpointInset - thumbInset)
                    .semantics { stateDescription = currentLabel },
                steps = (labels.size - 2).coerceAtLeast(0),
                variant = if (labels.size > 1) NgSliderVariant.DISCRETE else NgSliderVariant.CONTINUOUS,
                onValueChangeFinished = onPositionChangeFinished,
            )
            AiNgStepTickLabels(labels, endpointInset)
        }
    }
}

@Composable
private fun AiNgStepTickLabels(labels: List<String>, endpointInset: Dp) {
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        content = {
            labels.forEach { label ->
                Text(
                    text = label,
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val inset = endpointInset.roundToPx().coerceAtMost(width / 2)
        val trackWidth = width - inset * 2
        val centers = labels.indices.map { index ->
            if (labels.size == 1) width - inset
            else inset + (trackWidth.toFloat() * index / labels.lastIndex).roundToInt()
        }
        val boundaries = buildList {
            add(0)
            addAll(centers.zipWithNext { left, right -> (left + right) / 2 })
            add(width)
        }.toMutableList()
        if (labels.size > 1) {
            val lastLabelWidth = measurables.last().maxIntrinsicWidth(constraints.maxHeight)
            boundaries[labels.lastIndex] = minOf(
                boundaries[labels.lastIndex],
                maxOf(centers[labels.lastIndex - 1], width - lastLabelWidth),
            )
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints(maxWidth = boundaries[index + 1] - boundaries[index]))
        }
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { index, placeable ->
                val x = (centers[index] - placeable.width / 2)
                    .coerceIn(boundaries[index], boundaries[index + 1] - placeable.width)
                placeable.place(x, 0)
            }
        }
    }
}

@Composable
private fun AiScaleDialogFrame(
    title: String,
    description: String?,
    @DrawableRes iconRes: Int,
    iconTint: Color,
    currentLabel: String,
    horizontalPadding: Dp = 24.dp,
    descriptionOverlay: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ngDrawerContentCardColor())
            .padding(start = horizontalPadding, top = 26.dp, end = horizontalPadding, bottom = 24.dp),
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
            // Keep the original description footprint at the current width and text scale.
            val descriptionVisibility = if (descriptionOverlay != null) {
                Modifier.alpha(0f).clearAndSetSemantics {}
            } else {
                Modifier
            }
            Box {
                Text(
                    text = description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 22.dp)
                        .then(descriptionVisibility),
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                )
                if (descriptionOverlay != null) {
                    Box(
                        modifier = Modifier.matchParentSize().padding(top = 8.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        descriptionOverlay()
                    }
                }
            }
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = iconTint,
        )
        Text(
            text = currentLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 22.dp),
            color = colorResource(R.color.ng_on_surface),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
        content()
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
