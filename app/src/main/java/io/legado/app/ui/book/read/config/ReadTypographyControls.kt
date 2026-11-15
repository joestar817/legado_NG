package io.legado.app.ui.book.read.config

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.theme.NgTheme
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Integer ticks keep the existing slider ranges and avoid floating-point stepping drift. */
internal enum class ReadTypographyMetric(
    val minTicks: Int, val maxTicks: Int, private val scale: Int,
    val step: Int = 1, private val showPlus: Boolean = false,
) {
    SIZE(5, 50, 1),
    LETTER_SPACING(-50, 50, 100),
    LINE_SPACING(-10, 10, 10),
    PARAGRAPH_SPACING(0, 20, 10),
    PARAGRAPH_INDENT(0, 4, 1),
    BODY_WEIGHT(100, 900, 1, step = 100),
    BODY_PADDING_TOP(0, 200, 1),
    BODY_PADDING(0, 100, 1),
    TITLE_SIZE(0, 20, 1, showPlus = true),
    TITLE_WEIGHT(100, 900, 1, step = 100),
    TITLE_SPACING(0, 100, 1),
    TITLE_LINE_SPACING(0, 100, 10),
    TITLE_SEGMENT_COUNT(1, 100, 1),
    TITLE_SEGMENT_SCALE(10, 200, 1),
    TITLE_SEGMENT_SPACING(0, 100, 10);

    val allowsNegative: Boolean get() = minTicks < 0
    val allowsDecimal: Boolean get() = scale != 1

    fun format(ticks: Int): String {
        val value = if (scale == 1) ticks.toString() else (ticks.toFloat() / scale).toString()
        return if (showPlus && ticks > 0) "+$value" else value
    }

    fun acceptsDraft(text: String): Boolean {
        if (text.length > 10) return false
        val pattern = when {
            !allowsDecimal -> if (showPlus) "\\+?[0-9]*" else "[0-9]*"
            allowsNegative -> "-?[0-9]*([.,][0-9]*)?"
            else -> "[0-9]*([.,][0-9]*)?"
        }
        return text.matches(Regex(pattern))
    }

    /** Incomplete drafts return null; completed values are quantized to the existing step. */
    fun parseTicks(text: String, clamp: Boolean): Int? {
        if (!acceptsDraft(text)) return null
        val number = text.replace(',', '.').toBigDecimalOrNull() ?: return null
        val divisor = BigDecimal.valueOf(scale.toLong())
        val minimum = BigDecimal.valueOf(minTicks.toLong()).divide(divisor)
        val maximum = BigDecimal.valueOf(maxTicks.toLong()).divide(divisor)
        if (!clamp && (number < minimum || number > maximum)) return null
        val bounded = number.coerceIn(minimum, maximum)
        val ticks = bounded.multiply(divisor).setScale(0, RoundingMode.HALF_UP).toInt()
        return ((ticks + step / 2) / step * step).coerceIn(minTicks, maxTicks)
    }
}

internal enum class ReadBodyPaddingSide { TOP, BOTTOM, LEFT, RIGHT }

internal data class ReadBodyPadding(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
)

internal enum class ReadTypographyGridVariant { STANDARD, HEADER_FOOTER_PADDING }

@Composable
internal fun ReadTypographyControls(
    textSize: Int,
    letterSpacing: Float,
    lineSpacingExtra: Int,
    paragraphSpacing: Int,
    paragraphIndentLevel: Int,
    textWeight: Int,
    contentColor: Color,
    onTextSizeChanged: (Int) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onLineSpacingChanged: (Int) -> Unit,
    onParagraphSpacingChanged: (Int) -> Unit,
    onParagraphIndentChanged: (Int) -> Unit,
    onTextWeightChanged: (Int) -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.text_size),
        stringResource(R.string.text_letter_spacing),
        stringResource(R.string.line_size),
        stringResource(R.string.paragraph_size),
        stringResource(R.string.text_indent),
        stringResource(R.string.font_weight),
    )
    val indentLabels = stringArrayResource(R.array.indent_short)
    val indentDescription = stringResource(R.string.paragraph_first_line_indent)
    val items = listOf(
        TypographyItem(labels[0], ReadTypographyMetric.SIZE, textSize, onChanged = onTextSizeChanged),
        TypographyItem(labels[1], ReadTypographyMetric.LETTER_SPACING, (letterSpacing * 100f).roundToInt()) {
            onLetterSpacingChanged(it / 100f)
        },
        TypographyItem(labels[2], ReadTypographyMetric.LINE_SPACING, lineSpacingExtra - 10) {
            onLineSpacingChanged(it + 10)
        },
        TypographyItem(labels[3], ReadTypographyMetric.PARAGRAPH_SPACING, paragraphSpacing, onChanged = onParagraphSpacingChanged),
        TypographyItem(
            labels[4], ReadTypographyMetric.PARAGRAPH_INDENT, paragraphIndentLevel,
            displayValue = { indentLabels[it.coerceIn(indentLabels.indices)] },
            accessibilityLabel = indentDescription, onChanged = onParagraphIndentChanged,
        ),
        TypographyItem(labels[5], ReadTypographyMetric.BODY_WEIGHT, textWeight, onChanged = onTextWeightChanged),
    )
    ReadTypographyMetricGrid(items, contentColor, endPadding = 5.dp)
}

@Composable
internal fun ReadBodyPaddingControls(
    padding: ReadBodyPadding,
    contentColor: Color,
    onPaddingChanged: (ReadBodyPaddingSide, Int) -> Unit,
) {
    val items = listOf(
        TypographyItem(
            stringResource(R.string.top), ReadTypographyMetric.BODY_PADDING_TOP, padding.top,
            accessibilityLabel = stringResource(R.string.padding_top),
        ) { onPaddingChanged(ReadBodyPaddingSide.TOP, it) },
        TypographyItem(
            stringResource(R.string.bottom), ReadTypographyMetric.BODY_PADDING, padding.bottom,
            accessibilityLabel = stringResource(R.string.padding_bottom),
        ) { onPaddingChanged(ReadBodyPaddingSide.BOTTOM, it) },
        TypographyItem(
            stringResource(R.string.left), ReadTypographyMetric.BODY_PADDING, padding.left,
            accessibilityLabel = stringResource(R.string.padding_left),
        ) { onPaddingChanged(ReadBodyPaddingSide.LEFT, it) },
        TypographyItem(
            stringResource(R.string.right), ReadTypographyMetric.BODY_PADDING, padding.right,
            accessibilityLabel = stringResource(R.string.padding_right),
        ) { onPaddingChanged(ReadBodyPaddingSide.RIGHT, it) },
    )
    ReadTypographyMetricGrid(items, contentColor, endPadding = 5.dp)
}

@Composable
internal fun ReadTypographyMetricGrid(
    items: List<TypographyItem>,
    contentColor: Color,
    endPadding: Dp = 12.dp,
    variant: ReadTypographyGridVariant = ReadTypographyGridVariant.STANDARD,
) {
    val labelWidth = readTypographyMetricLabelWidth(items.map { it.label })
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth -
            (if (variant == ReadTypographyGridVariant.HEADER_FOOTER_PADDING) 26.dp else 32.dp)) / 2
        val buttonWidth = if (variant == ReadTypographyGridVariant.HEADER_FOOTER_PADDING) 32.dp else 36.dp
        val inlineLabels = cellWidth >= labelWidth +
            (if (variant == ReadTypographyGridVariant.HEADER_FOOTER_PADDING) 92.dp else 100.dp)
        val singleColumn = cellWidth < 116.dp
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = endPadding)) {
            val rows = if (singleColumn) items.map { listOf(it) } else items.chunked(2)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { index, item ->
                        TypographyMetricCell(
                            item = item,
                            inlineLabel = inlineLabels,
                            labelWidth = labelWidth,
                            contentColor = contentColor,
                            buttonWidth = buttonWidth,
                            // Keep the first column fixed; the second uses the extra space at the end.
                            modifier = if (row.size == 2 && index == 0) Modifier.width(cellWidth) else Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun readTypographyMetricLabelWidth(labels: List<String>): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontFamily = NgTheme.fontFamily, fontSize = 14.sp)
    val widestLabel = labels.maxOf { textMeasurer.measure(it, style = labelStyle).size.width }
    // Include room for glyph overhang and font fallback, especially three-character labels.
    return (with(density) { widestLabel.toDp() } + 4.dp).coerceAtLeast(32.dp)
}

internal data class TypographyItem(
    val label: String,
    val metric: ReadTypographyMetric,
    val ticks: Int,
    val displayValue: ((Int) -> String)? = null,
    val accessibilityLabel: String? = null,
    val onChanged: (Int) -> Unit,
)

@Composable
internal fun ReadTypographyStandaloneMetric(
    item: TypographyItem,
    labelWidth: Dp,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier) {
        if (maxWidth >= labelWidth + 100.dp) {
            val stepperWidth = (maxWidth - labelWidth).coerceAtMost(125.dp * fontScale)
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, Modifier.width(labelWidth), color = contentColor, fontSize = 14.sp,
                    fontFamily = NgTheme.fontFamily, maxLines = 1)
                Spacer(Modifier.weight(1f))
                TypographyStepper(item, contentColor, Modifier.width(stepperWidth))
            }
        } else {
            TypographyMetricCell(item, inlineLabel = false, labelWidth = labelWidth,
                contentColor = contentColor, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TypographyMetricCell(
    item: TypographyItem,
    inlineLabel: Boolean,
    labelWidth: androidx.compose.ui.unit.Dp,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buttonWidth: Dp = 36.dp,
) {
    if (inlineLabel) {
        Row(modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, Modifier.width(labelWidth), color = contentColor, fontSize = 14.sp,
                fontFamily = NgTheme.fontFamily, maxLines = 1)
            TypographyStepper(item, contentColor, Modifier.weight(1f), buttonWidth)
        }
    } else {
        Column(modifier.padding(vertical = 4.dp)) {
            Text(
                item.label, color = contentColor, fontSize = 14.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            TypographyStepper(item, contentColor, Modifier.fillMaxWidth(), buttonWidth)
        }
    }
}

private class TypographyRepeatState {
    var direction = 0
    var ticks = 0
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypographyStepper(
    item: TypographyItem,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buttonWidth: Dp = 36.dp,
) {
    val metric = item.metric
    val currentTicks = item.ticks.coerceIn(metric.minTicks, metric.maxTicks)
    val displayText = item.displayValue?.invoke(currentTicks) ?: metric.format(currentTicks)
    val accessibilityLabel = item.accessibilityLabel ?: item.label
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var draft by remember(metric) { mutableStateOf(TextFieldValue(displayText)) }
    val repeatState = remember(metric) { TypographyRepeatState() }
    LaunchedEffect(currentTicks, focused, displayText) {
        if (!focused) draft = TextFieldValue(displayText)
    }
    LaunchedEffect(focused) {
        if (focused) {
            delay(200)
            bringIntoView.bringIntoView()
        }
    }

    fun step(delta: Int, repeat: Boolean = false): Boolean {
        val base = if (repeat && repeatState.direction == delta) {
            repeatState.ticks
        } else if (focused) {
            metric.parseTicks(draft.text, clamp = true) ?: currentTicks
        } else {
            currentTicks
        }
        if (focused) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
        val next = (base + delta * metric.step).coerceIn(metric.minTicks, metric.maxTicks)
        if (next == base) return false
        if (repeat) {
            repeatState.direction = delta
            repeatState.ticks = next
        }
        item.onChanged(next)
        return if (delta < 0) next > metric.minTicks else next < metric.maxTicks
    }

    fun finishRepeat(delta: Int) {
        if (repeatState.direction == delta) repeatState.direction = 0
    }

    val accent = Color(NgTheme.colors.primary)
    val valueFill = accent.copy(alpha = if (NgTheme.snapshot.isDark) 0.24f else 0.12f)
    Row(modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        NgSliderStepButton(
            iconRes = R.drawable.ic_chevron_left_20,
            contentDescription = "$accessibilityLabel，${stringResource(R.string.reduce)}",
            enabled = currentTicks > metric.minTicks,
            onClick = { step(-1) },
            modifier = Modifier.width(buttonWidth),
            tint = accent,
            onLongPressStep = { step(-1, repeat = true) },
            onLongPressFinished = { finishRepeat(-1) },
        )
        BasicTextField(
            value = draft,
            onValueChange = { next ->
                if (metric.acceptsDraft(next.text)) {
                    draft = next
                    metric.parseTicks(next.text, clamp = false)?.let { ticks ->
                        if (ticks != currentTicks) item.onChanged(ticks)
                    }
                }
            },
            modifier = Modifier.weight(1f).height(44.dp)
                .bringIntoViewRequester(bringIntoView)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        if (event.type == KeyEventType.KeyUp) {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                        true
                    } else false
                }
                .onFocusChanged { focus ->
                    if (focus.isFocused && !focused) {
                        focused = true
                        val text = metric.format(currentTicks)
                        draft = TextFieldValue(text, selection = TextRange(0, text.length))
                    } else if (!focus.isFocused && focused) {
                        focused = false
                        val ticks = metric.parseTicks(draft.text, clamp = true) ?: currentTicks
                        if (ticks != currentTicks) item.onChanged(ticks)
                        draft = TextFieldValue(item.displayValue?.invoke(ticks) ?: metric.format(ticks))
                    }
                }
                .semantics { contentDescription = accessibilityLabel },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
                color = contentColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
            // Compose's numeric keyboard cannot request a sign; text keyboards keep negative values editable.
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    metric.allowsNegative -> KeyboardType.Text
                    metric.allowsDecimal -> KeyboardType.Decimal
                    else -> KeyboardType.Number
                },
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
            cursorBrush = SolidColor(accent),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.widthIn(max = 44.dp).fillMaxWidth().height(24.dp)
                            .clip(RoundedCornerShape(8.dp)).background(valueFill),
                    )
                    innerTextField()
                }
            },
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_chevron_right_20,
            contentDescription = "$accessibilityLabel，${stringResource(R.string.plus)}",
            enabled = currentTicks < metric.maxTicks,
            onClick = { step(1) },
            modifier = Modifier.width(buttonWidth),
            tint = accent,
            onLongPressStep = { step(1, repeat = true) },
            onLongPressFinished = { finishRepeat(1) },
        )
    }
}
