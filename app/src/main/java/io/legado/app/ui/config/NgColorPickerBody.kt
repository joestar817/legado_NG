package io.legado.app.ui.config

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiMessage
import io.legado.app.help.ai.AiTextParams
import io.legado.app.help.config.AiThemeDeckStore
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgSegmentedDock
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderStepButton
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.defaultDockSurfaceColor
import io.legado.app.ui.design.components.compose.ngSliderStepValue
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorPickerSlot
import io.legado.app.ui.design.theme.NgColorPickerTab
import io.legado.app.ui.design.theme.NgPaperLook
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.ReadingPaletteCatalog
import io.legado.app.ui.design.theme.SwatchMatrix
import io.legado.app.ui.design.theme.formatNgColor
import io.legado.app.ui.design.theme.AI_THEME_SYSTEM_PROMPT
import io.legado.app.ui.design.theme.buildAiThemeUserPrompt
import io.legado.app.ui.design.theme.parseAiPaperLooks
import io.legado.app.ui.design.theme.parseCommittedNgColor
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NgColorPickerContent(
    color: Int,
    onPreviewColor: (Int) -> Unit,
    onCommitColor: (Int) -> Unit,
    counterpartBackground: Int,
    counterpartForeground: Int,
    counterpartHighlight: Int = 0xFFFDF3B8.toInt(),
    slot: NgColorPickerSlot = NgColorPickerSlot.GENERIC,
    showAlphaSlider: Boolean = false,
    dockContentColor: Color = Color.Unspecified,
    dockSelectedContainerColor: Color = Color.Unspecified,
    dockSelectedContentColor: Color = Color.Unspecified,
    dockSurfaceColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val tabContentColor = if (dockContentColor == Color.Unspecified) {
        Color(colors.onSurface)
    } else {
        dockContentColor
    }
    val tabSelectedContainer = if (dockSelectedContainerColor == Color.Unspecified) {
        Color(colors.primary)
    } else {
        dockSelectedContainerColor
    }
    val tabSelectedContent = if (dockSelectedContentColor == Color.Unspecified) {
        Color(colors.onPrimary)
    } else {
        dockSelectedContentColor
    }
    val tabSurface = if (dockSurfaceColor == Color.Unspecified) {
        defaultDockSurfaceColor()
    } else {
        dockSurfaceColor
    }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val tabs = listOf(
        NgColorPickerTab.PALETTE to stringResource(R.string.ng_picker_palette),
        NgColorPickerTab.WHEEL to stringResource(R.string.ng_picker_wheel),
        NgColorPickerTab.CONTINUOUS to stringResource(R.string.ng_picker_continuous),
    )
    var tab by remember {
        val saved = context.getPrefInt(PreferKey.ngColorPickerTab, 0)
        mutableStateOf(NgColorPickerTab.entries.getOrElse(saved) { NgColorPickerTab.PALETTE })
    }
    var hexInput by remember(color) { mutableStateOf(formatNgColor(color)) }
    var hexError by remember { mutableStateOf(false) }
    var hsvMode by remember { mutableStateOf(true) }
    var lastPreview by remember { mutableIntStateOf(color) }
    var swatchFamily by remember {
        val saved = context.getPrefString(PreferKey.ngColorSwatchFamily).orEmpty()
        mutableStateOf(
            if (saved in SwatchMatrix.optionIds) saved else SwatchMatrix.SPECTRUM_ID
        )
    }

    fun preview(next: Int) {
        lastPreview = next
        onPreviewColor(next)
        hexInput = formatNgColor(next)
        hexError = false
    }

    fun commit(next: Int = lastPreview) {
        val flattened = when (slot) {
            NgColorPickerSlot.TEXT -> NgColorMath.flatten(next, counterpartBackground)
            NgColorPickerSlot.BACKGROUND -> NgColorMath.opaque(next)
            else -> next
        }
        preview(flattened)
        onCommitColor(flattened)
    }

    fun submitHex(allowRgb: Boolean): Boolean {
        val parsed = parseCommittedNgColor(hexInput, allowRgb = allowRgb)
        if (parsed != null) {
            hexError = false
            commit(parsed)
            return true
        }
        if (allowRgb) {
            hexInput = formatNgColor(color)
            hexError = false
        } else {
            hexError = hexInput.trim().removePrefix("#").length == 7
        }
        return false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        NgSegmentedDock(
            labels = tabs.map { it.second },
            selectedIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0),
            onSelected = { index ->
                val next = tabs.getOrNull(index)?.first ?: return@NgSegmentedDock
                if (next != tab) {
                    commit()
                    tab = next
                    context.putPrefInt(PreferKey.ngColorPickerTab, next.ordinal)
                }
            },
            contentColor = tabContentColor,
            selectedContainerColor = tabSelectedContainer,
            selectedContentColor = tabSelectedContent,
            dockSurfaceColor = tabSurface,
        )
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .heightIn(max = 280.dp),
        ) {
            val bodyHeight = if (maxHeight.isFinite) maxHeight else 200.dp
            val bodyModifier = Modifier
                .fillMaxWidth()
                .height(bodyHeight)
            when (tab) {
                NgColorPickerTab.PALETTE -> Column(modifier = bodyModifier) {
                    NgSwatchFamilyBar(
                        selectedId = swatchFamily,
                        onSelected = { id ->
                            swatchFamily = id
                            context.putPrefString(PreferKey.ngColorSwatchFamily, id)
                        },
                        contentColor = tabContentColor,
                        selectedContainerColor = tabSelectedContainer,
                        selectedContentColor = tabSelectedContent,
                        dockSurfaceColor = tabSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    NgColorPalette(
                        color = color,
                        familyId = swatchFamily,
                        onColorChanged = { preview(it) },
                        onChangeFinished = { commit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                NgColorPickerTab.WHEEL -> NgHueSatValueWheel(
                    color = color,
                    onPreview = { preview(it) },
                    onCommit = { commit(it) },
                    modifier = bodyModifier,
                )
                NgColorPickerTab.CONTINUOUS -> NgContinuousColorSliders(
                    color = color,
                    hsvMode = hsvMode,
                    onHsvMode = { hsvMode = it },
                    onPreview = { preview(it) },
                    onCommit = { commit() },
                    modifier = bodyModifier,
                )
            }
        }
        if (showAlphaSlider) {
            Spacer(Modifier.height(8.dp))
            NgAlphaSlider(
                color = color,
                onAlphaChanged = { alpha ->
                    preview((color and 0x00FFFFFF) or (alpha shl 24))
                },
                onChangeFinished = { commit() },
            )
        }
        Spacer(Modifier.height(8.dp))
        NgColorPreviewRow(
            color = color,
            counterpartBackground = counterpartBackground,
            counterpartForeground = counterpartForeground,
            counterpartHighlight = counterpartHighlight,
            slot = slot,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(color), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(colors.outlineVariant), RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.size(12.dp))
            NgFormField(
                label = stringResource(R.string.ng_color_value),
                value = hexInput,
                onValueChange = { value ->
                    hexInput = normalizeHexInput(value)
                    submitHex(allowRgb = false)
                },
                modifier = Modifier.weight(1f),
                isError = hexError,
                supportingText = if (hexError) {
                    stringResource(R.string.ng_color_value_hint)
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        submitHex(allowRgb = true)
                        keyboardController?.hide()
                    },
                ),
                onFocusLost = { submitHex(allowRgb = true) },
            )
        }
    }
}

@Composable
internal fun NgSwatchFamilyBar(
    selectedId: String,
    onSelected: (String) -> Unit,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    dockSurfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(dockSurfaceColor)
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(SwatchMatrix.optionIds, key = { it }) { id ->
            val selected = id == selectedId
            Text(
                text = stringResource(SwatchMatrix.labelRes(id)),
                color = if (selected) selectedContentColor else contentColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (selected) Modifier.background(selectedContainerColor) else Modifier
                    )
                    .clickable(role = Role.Tab) { onSelected(id) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun NgColorPreviewRow(
    color: Int,
    counterpartBackground: Int,
    counterpartForeground: Int,
    counterpartHighlight: Int,
    slot: NgColorPickerSlot,
) {
    val colors = NgTheme.colors
    val previewBg = when (slot) {
        NgColorPickerSlot.BACKGROUND -> color
        else -> counterpartBackground
    }
    val previewFg = when (slot) {
        NgColorPickerSlot.BACKGROUND, NgColorPickerSlot.HIGHLIGHT -> counterpartForeground
        else -> color
    }
    val previewHighlight = when (slot) {
        NgColorPickerSlot.HIGHLIGHT -> color
        else -> counterpartHighlight
    }
    val sample = stringResource(R.string.ng_paper_preview_sample)
    val splitIndex = sample.indexOfFirst { it == '，' || it == ',' }.takeIf { it >= 0 } ?: (sample.length / 2)
    val plain = sample.substring(0, splitIndex).trimEnd(',', '，', ' ')
    val marked = sample.substring(splitIndex).trimStart(',', '，', ' ')
    val ratio = NgColorMath.displayedContrast(previewFg, previewBg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(NgColorMath.opaque(previewBg)))
            .border(1.dp, Color(colors.outlineVariant), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = buildAnnotatedString {
                append(plain)
                append("  ")
                withStyle(
                    SpanStyle(
                        color = Color(previewFg),
                        background = Color(NgColorMath.opaque(previewHighlight)),
                    ),
                ) {
                    append(marked)
                }
            },
            color = Color(previewFg),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = NgColorMath.wcagContrastLabel(ratio),
            color = Color(previewFg).copy(alpha = 0.72f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NgContinuousColorSliders(
    color: Int,
    hsvMode: Boolean,
    onHsvMode: (Boolean) -> Unit,
    onPreview: (Int) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(color) {
        FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
    }
    val hue = hsv[0].coerceIn(0f, 360f)
    val sat = hsv[1].coerceIn(0f, 1f)
    val value = hsv[2].coerceIn(0f, 1f)
    val alpha = color ushr 24 and 0xFF
    val red = AndroidColor.red(color)
    val green = AndroidColor.green(color)
    val blue = AndroidColor.blue(color)
    val thumb = Color(color)
    val hueBrush = remember {
        Brush.horizontalGradient(
            listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { stop ->
                Color.hsv(stop, 1f, 1f)
            }
        )
    }
    val satBrush = remember(hue, value) {
        Brush.horizontalGradient(
            listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))
        )
    }
    val valueBrush = remember(hue, sat) {
        Brush.horizontalGradient(
            listOf(Color.Black, Color.hsv(hue, sat, 1f))
        )
    }
    val redBrush = remember(green, blue) {
        Brush.horizontalGradient(
            listOf(Color(0, green, blue), Color(255, green, blue))
        )
    }
    val greenBrush = remember(red, blue) {
        Brush.horizontalGradient(
            listOf(Color(red, 0, blue), Color(red, 255, blue))
        )
    }
    val blueBrush = remember(red, green) {
        Brush.horizontalGradient(
            listOf(Color(red, green, 0), Color(red, green, 255))
        )
    }
    val modes = listOf(
        true to stringResource(R.string.ng_picker_hsv),
        false to stringResource(R.string.ng_picker_rgb),
    )
    Column(modifier = modifier.fillMaxHeight()) {
        NgFlatActionRail(
            items = modes.map { (mode, label) ->
                NgFlatActionRailItem(label = label, emphasized = mode == hsvMode)
            },
            onItemClick = { index -> modes.getOrNull(index)?.first?.let(onHsvMode) },
            variant = NgFlatActionRailVariant.FORM_TEXT_PICKER,
        )
        if (hsvMode) {
            ChannelSlider(
                stringResource(R.string.ng_picker_hue),
                hue,
                0f,
                360f,
                steps = 359,
                onFinished = onCommit,
                trackBrush = hueBrush,
                thumbColor = thumb,
                displayValue = "${hue.roundToInt()}°",
            ) { nextHue ->
                onPreview(hsvToArgb(alpha, floatArrayOf(nextHue, sat, value)))
            }
            ChannelSlider(
                stringResource(R.string.ng_picker_saturation),
                sat,
                0f,
                1f,
                steps = 99,
                onFinished = onCommit,
                trackBrush = satBrush,
                thumbColor = thumb,
                displayValue = "${(sat * 100f).roundToInt()}%",
            ) { nextSat ->
                onPreview(hsvToArgb(alpha, floatArrayOf(hue, nextSat, value)))
            }
            ChannelSlider(
                stringResource(R.string.ng_picker_value),
                value,
                0f,
                1f,
                steps = 99,
                onFinished = onCommit,
                trackBrush = valueBrush,
                thumbColor = thumb,
                displayValue = "${(value * 100f).roundToInt()}%",
            ) { nextValue ->
                onPreview(hsvToArgb(alpha, floatArrayOf(hue, sat, nextValue)))
            }
        } else {
            ChannelSlider(
                stringResource(R.string.ng_picker_red),
                red.toFloat(),
                0f,
                255f,
                steps = 254,
                onFinished = onCommit,
                trackBrush = redBrush,
                thumbColor = thumb,
            ) { nextRed ->
                onPreview(AndroidColor.argb(alpha, nextRed.roundToInt(), green, blue))
            }
            ChannelSlider(
                stringResource(R.string.ng_picker_green),
                green.toFloat(),
                0f,
                255f,
                steps = 254,
                onFinished = onCommit,
                trackBrush = greenBrush,
                thumbColor = thumb,
            ) { nextGreen ->
                onPreview(AndroidColor.argb(alpha, red, nextGreen.roundToInt(), blue))
            }
            ChannelSlider(
                stringResource(R.string.ng_picker_blue),
                blue.toFloat(),
                0f,
                255f,
                steps = 254,
                onFinished = onCommit,
                trackBrush = blueBrush,
                thumbColor = thumb,
            ) { nextBlue ->
                onPreview(AndroidColor.argb(alpha, red, green, nextBlue.roundToInt()))
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    from: Float,
    until: Float,
    steps: Int,
    onFinished: () -> Unit,
    trackBrush: Brush,
    thumbColor: Color,
    displayValue: String? = null,
    onChange: (Float) -> Unit,
) {
    val latest = rememberUpdatedState(onChange)
    val finished = rememberUpdatedState(onFinished)
    val range = from..until
    val current = value.coerceIn(range)
    val contentColor = Color(NgTheme.colors.onSurface)
    val valueText = displayValue ?: if (until <= 1f) {
        (current * 100f).roundToInt().toString()
    } else {
        current.roundToInt().toString()
    }
    fun stepBy(delta: Int) {
        val next = ngSliderStepValue(current, range, steps, delta)
        latest.value(next)
        finished.value()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            color = contentColor,
            fontSize = 15.sp,
            maxLines = 1,
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_reduce,
            contentDescription = "$label，${stringResource(R.string.reduce)}",
            enabled = current > from,
            onClick = { stepBy(-1) },
            tint = contentColor,
        )
        NgSlider(
            value = current,
            onValueChange = { latest.value(it) },
            valueRange = range,
            steps = steps,
            variant = NgSliderVariant.COMPACT,
            modifier = Modifier.weight(1f),
            trackBrush = trackBrush,
            thumbColor = thumbColor,
            onValueChangeFinished = { finished.value() },
        )
        NgSliderStepButton(
            iconRes = R.drawable.ic_add,
            contentDescription = "$label，${stringResource(R.string.plus)}",
            enabled = current < until,
            onClick = { stepBy(1) },
            tint = contentColor,
        )
        Text(
            text = valueText,
            modifier = Modifier.width(48.dp),
            color = contentColor,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun NgHueSatValueWheel(
    color: Int,
    onPreview: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(color) {
        FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
    }
    val alpha = color ushr 24 and 0xFF
    val hueState = rememberUpdatedState(hsv[0])
    val satState = rememberUpdatedState(hsv[1])
    val valueState = rememberUpdatedState(hsv[2])
    val previewState = rememberUpdatedState(onPreview)
    val commitState = rememberUpdatedState(onCommit)
    val surfaceColor = Color(NgTheme.colors.surface)
    val outline = Color(NgTheme.colors.outline)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(alpha, canvasSize) {
                fun apply(offset: Offset, commit: Boolean) {
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
                    val cx = canvasSize.width / 2f
                    val cy = canvasSize.height / 2f
                    val minSide = minOf(canvasSize.width, canvasSize.height).toFloat()
                    val outer = minSide * 0.48f
                    val inner = minSide * 0.32f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val dist = hypot(dx, dy)
                    val next = floatArrayOf(hueState.value, satState.value, valueState.value)
                    when {
                        dist in inner..outer -> {
                            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (deg < 0f) deg += 360f
                            next[0] = deg
                        }
                        else -> {
                            val box = inner * 1.25f
                            val left = cx - box / 2f
                            val top = cy - box / 2f
                            next[1] = ((offset.x - left) / box).coerceIn(0f, 1f)
                            next[2] = (1f - (offset.y - top) / box).coerceIn(0f, 1f)
                        }
                    }
                    val argb = hsvToArgb(alpha, next)
                    if (commit) commitState.value(argb) else previewState.value(argb)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = down.position
                    apply(last, false)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) {
                                last = change.position
                                apply(last, false)
                            }
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    apply(last, true)
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minSide = minOf(size.width, size.height)
        val outer = minSide * 0.48f
        val inner = minSide * 0.32f
        val hues = List(13) { index ->
            Color.hsv((index * 30f) % 360f, 1f, 1f)
        }
        drawCircle(
            brush = Brush.sweepGradient(hues),
            radius = outer,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = surfaceColor,
            radius = inner,
            center = Offset(cx, cy),
        )
        val box = inner * 1.25f
        val left = cx - box / 2f
        val top = cy - box / 2f
        val hueColor = Color.hsv(hsv[0], 1f, 1f)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
            topLeft = Offset(left, top),
            size = Size(box, box),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = Offset(left, top),
            size = Size(box, box),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        val handle = Offset(left + hsv[1] * box, top + (1f - hsv[2]) * box)
        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = handle)
        drawCircle(color = outline, radius = 7.dp.toPx(), center = handle, style = Stroke(1.5.dp.toPx()))
        val ringAngle = Math.toRadians(hsv[0].toDouble())
        val ringPoint = Offset(
            cx + cos(ringAngle).toFloat() * ((inner + outer) / 2f),
            cy + sin(ringAngle).toFloat() * ((inner + outer) / 2f),
        )
        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = ringPoint)
        drawCircle(color = outline, radius = 6.dp.toPx(), center = ringPoint, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
internal fun NgAiThemePane(
    isNight: Boolean,
    isEink: Boolean,
    onSelectLook: (NgPaperLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val scope = rememberCoroutineScope()
    var looks by remember(isNight, isEink) {
        mutableStateOf(loadAiThemeDeck(isNight, isEink))
    }
    var query by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    val assistant = remember { AiAssistantConfigUi.selectedModel() }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun requestGenerate() {
        val preference = query.trim()
        if (assistant == null || preference.isBlank() || generating) return
        generating = true
        keyboard?.hide()
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val result = AiManager.generateText(
                        messages = listOf(
                            AiMessage(
                                AiMessage.Role.SYSTEM,
                                AI_THEME_SYSTEM_PROMPT,
                            ),
                            AiMessage(
                                AiMessage.Role.USER,
                                buildAiThemeUserPrompt(preference, isNight, isEink),
                            ),
                        ),
                        params = AiTextParams(
                            temperature = 0.5f,
                            maxTokens = 2048,
                            jsonResponse = true,
                            disableThinking = true,
                        ),
                        providerId = assistant.provider.id,
                        modelId = assistant.model.id,
                    )
                    parseAiPaperLooks(result.content, isNight, isEink)
                }
            }
            val next = outcome.getOrNull().orEmpty()
            if (next.isNotEmpty()) {
                looks = AiThemeDeckStore.appendGenerated(isNight, isEink, looks, next)
            } else {
                val err = outcome.exceptionOrNull()?.localizedMessage
                context.toastOnUi(err ?: context.getString(R.string.ng_paper_generate_empty))
            }
            generating = false
        }
    }
    Column(
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            looks.forEach { look ->
                NgPaperLookRow(
                    look = look,
                    onClick = { onSelectLook(look) },
                )
            }
        }
        if (assistant != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                NgFormField(
                    label = stringResource(R.string.ng_paper_query_hint),
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { requestGenerate() }),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(colors.primary).copy(alpha = if (generating) 0.4f else 1f))
                        .clickable(
                            enabled = query.isNotBlank() && !generating,
                            role = Role.Button,
                            onClick = { requestGenerate() },
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.ng_paper_generate),
                        color = Color(colors.onPrimary),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NgPaperLookRow(
    look: NgPaperLook,
    onClick: () -> Unit,
) {
    val colors = NgTheme.colors
    val label = ReadingPaletteCatalog.labelRes(look.labelKey)?.let { stringResource(it) }
        ?: when (look.labelKey) {
            "d1" -> stringResource(R.string.ng_paper_d1)
            "d3" -> stringResource(R.string.ng_paper_d3)
            "d4" -> stringResource(R.string.ng_paper_d4)
            "d5" -> stringResource(R.string.ng_paper_d5)
            "n1" -> stringResource(R.string.ng_paper_n1)
            "n3" -> stringResource(R.string.ng_paper_n3)
            "n5" -> stringResource(R.string.ng_paper_n5)
            "generated" -> stringResource(R.string.ng_paper_generated)
            else -> look.labelKey
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(look.background))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(look.foreground),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = NgColorMath.wcagContrastLabel(look.contrastRatio),
                color = Color(look.foreground).copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color(look.accent), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(colors.outlineVariant), RoundedCornerShape(4.dp)),
        )
    }
}

private fun loadAiThemeDeck(isNight: Boolean, isEink: Boolean): List<NgPaperLook> {
    val saved = AiThemeDeckStore.loadDeck(isNight, isEink)
    if (saved.isNotEmpty()) return saved
    val seed = AiThemeDeckStore.defaultSeedDeck(isNight, isEink)
    AiThemeDeckStore.saveDeck(isNight, isEink, seed)
    return seed
}

private fun hsvToArgb(alpha: Int, hsv: FloatArray): Int {
    return (AndroidColor.HSVToColor(hsv) and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
}
