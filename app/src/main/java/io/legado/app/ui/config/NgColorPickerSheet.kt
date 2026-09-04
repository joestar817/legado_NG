package io.legado.app.ui.config

import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.ui.design.theme.formatNgColor
import io.legado.app.ui.design.theme.parseNgColor
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * MD3 颜色选择交互的 NG 实现：恢复、确认、色板、透明度与 ARGB 输入均保留。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NgColorPickerSheet(
    show: Boolean,
    initialColor: Int,
    initialTopBarTextMode: NgTopBarTextMode? = null,
    resetColor: Int? = AndroidColor.TRANSPARENT,
    showAlphaSlider: Boolean = true,
    onDismissRequest: () -> Unit,
    onSelectionConfirmed: (Int, NgTopBarTextMode?) -> Unit
) {
    if (!show) return

    var currentColor by remember { mutableIntStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(formatNgColor(initialColor)) }
    var isHexInputError by remember { mutableStateOf(false) }
    var topBarTextMode by remember { mutableStateOf(initialTopBarTextMode) }

    LaunchedEffect(initialColor, initialTopBarTextMode) {
        currentColor = initialColor
        hexInput = formatNgColor(initialColor)
        isHexInputError = false
        topBarTextMode = initialTopBarTextMode
    }

    val parsed = parseNgColor(hexInput)
    val drawerHeightFraction = if (initialTopBarTextMode == null) 0.50f else 0.60f
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(drawerHeightFraction),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            val snapshot = NgTheme.snapshot
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (resetColor != null) {
                        NgPickerActionButton(
                            onClick = {
                                currentColor = resetColor
                                hexInput = formatNgColor(resetColor)
                                isHexInputError = false
                            },
                            contentDescription = stringResource(R.string.ng_reset_color)
                        ) {
                            Icon(
                                Icons.Rounded.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(snapshot.colors.onSurface)
                            )
                        }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    Text(
                        text = stringResource(R.string.ng_select_color),
                        color = Color(snapshot.colors.onSurface),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    NgThemeSheetSaveButton(
                        onClick = {
                            onSelectionConfirmed(currentColor, topBarTextMode)
                            onDismissRequest()
                        },
                        enabled = parsed != null && !isHexInputError,
                        contentDescription = stringResource(R.string.ng_apply_color),
                        touchSize = 48.dp
                    )
                }

                Spacer(Modifier.height(10.dp))
                NgColorPalette(
                    color = currentColor,
                    onColorChanged = { selected ->
                        currentColor = selected
                        hexInput = formatNgColor(selected)
                        isHexInputError = false
                    }
                )
                if (showAlphaSlider) {
                    Spacer(Modifier.height(10.dp))
                    NgAlphaSlider(
                        color = currentColor,
                        onAlphaChanged = { alpha ->
                            currentColor = (currentColor and 0x00FFFFFF) or (alpha shl 24)
                            hexInput = formatNgColor(currentColor)
                            isHexInputError = false
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(currentColor), RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                Color(snapshot.colors.outlineVariant),
                                RoundedCornerShape(14.dp)
                            )
                    )
                    Spacer(Modifier.size(12.dp))
                    NgFormField(
                        label = stringResource(R.string.ng_color_value),
                        value = hexInput,
                        onValueChange = { value ->
                            hexInput = normalizeHexInput(value)
                            val color = parseNgColor(hexInput)
                            if (color != null) {
                                currentColor = color
                                isHexInputError = false
                            } else {
                                isHexInputError = hexInput.isNotBlank()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        isError = isHexInputError,
                        supportingText = if (isHexInputError) {
                            stringResource(R.string.ng_color_value_hint)
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )
                }
                if (topBarTextMode != null) {
                    Spacer(Modifier.height(14.dp))
                    NgTopBarTextModeSelector(
                        selected = requireNotNull(topBarTextMode),
                        onSelected = { topBarTextMode = it }
                    )
                }
            }
        }
    }
}

/**
 * 嵌入现有 NG 容器的颜色编辑页，不创建新的 Dialog 或 BottomSheet。
 */
@Composable
internal fun NgInlineColorPicker(
    title: String,
    initialColor: Int,
    onBack: () -> Unit,
    onColorChanged: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var currentColor by remember { mutableIntStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(formatNgColor(initialColor)) }
    var isHexInputError by remember { mutableStateOf(false) }

    LaunchedEffect(initialColor) {
        currentColor = initialColor
        hexInput = formatNgColor(initialColor)
        isHexInputError = false
    }

    val colors = NgTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NgPickerActionButton(
                onClick = onBack,
                contentDescription = stringResource(R.string.back),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onSurface),
                )
            }
            Text(
                text = title,
                color = Color(colors.onSurface),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            NgPickerActionButton(
                onClick = onReset,
                contentDescription = stringResource(R.string.ng_reset_color),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onSurface),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        NgColorPalette(
            color = currentColor,
            onColorChanged = { selected ->
                currentColor = selected
                hexInput = formatNgColor(selected)
                isHexInputError = false
                onColorChanged(selected)
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(currentColor), RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        Color(colors.outlineVariant),
                        RoundedCornerShape(14.dp),
                    ),
            )
            Spacer(Modifier.size(12.dp))
            NgFormField(
                label = stringResource(R.string.ng_color_value),
                value = hexInput,
                onValueChange = { value ->
                    hexInput = normalizeHexInput(value)
                    val color = parseNgColor(hexInput)
                    if (color != null) {
                        currentColor = color
                        isHexInputError = false
                        onColorChanged(color)
                    } else {
                        isHexInputError = hexInput.isNotBlank()
                    }
                },
                modifier = Modifier.weight(1f),
                isError = isHexInputError,
                supportingText = if (isHexInputError) {
                    stringResource(R.string.ng_color_value_hint)
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
            )
        }
    }
}

@Composable
private fun NgTopBarTextModeSelector(
    selected: NgTopBarTextMode,
    onSelected: (NgTopBarTextMode) -> Unit
) {
    val colors = NgTheme.colors
    val options = listOf(
        NgTopBarTextMode.AUTO to stringResource(R.string.ng_top_bar_text_auto),
        NgTopBarTextMode.LIGHT to stringResource(R.string.ng_top_bar_text_light),
        NgTopBarTextMode.DARK to stringResource(R.string.ng_top_bar_text_dark)
    )
    Text(
        text = stringResource(R.string.ng_top_bar_text),
        modifier = Modifier.fillMaxWidth(),
        color = Color(colors.onSurface),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(8.dp))
    NgFlatActionRail(
        items = options.map { (mode, label) ->
            NgFlatActionRailItem(
                label = label,
                emphasized = mode == selected,
            )
        },
        onItemClick = { index ->
            options.getOrNull(index)?.first?.let(onSelected)
        },
        variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
    )
}

@Composable
internal fun NgDrawerBackground(
    drawable: Drawable,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(drawable)
            }
        },
        update = { imageView ->
            if (imageView.drawable !== drawable) {
                imageView.setImageDrawable(drawable)
            }
        }
    )
}

@Composable
private fun NgPickerActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    NgThemeSheetActionButton(
        onClick = onClick,
        contentDescription = contentDescription,
        enabled = enabled,
        touchSize = 48.dp
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
internal fun NgColorPalette(
    color: Int,
    onColorChanged: (Int) -> Unit
) {
    val rows = 8
    val hueColumns = 12
    val columns = hueColumns + 1
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val alpha = color ushr 24 and 0xFF
    val outlineColor = Color(NgTheme.colors.outlineVariant)
    val palette = remember(alpha) {
        buildList {
            repeat(rows) { row ->
                repeat(columns) { column ->
                    add(paletteColor(row, column, rows, hueColumns, alpha))
                }
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.85f)
            .clip(RoundedCornerShape(22.dp))
            .onSizeChanged { canvasSize = it }
            .pointerInput(alpha, canvasSize) {
                fun select(offset: Offset) {
                    if (canvasSize.width <= 0 || canvasSize.height <= 0) return
                    val column = floor(offset.x / canvasSize.width * columns)
                        .toInt().coerceIn(0, columns - 1)
                    val row = floor(offset.y / canvasSize.height * rows)
                        .toInt().coerceIn(0, rows - 1)
                    onColorChanged(palette[row * columns + column])
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    select(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) select(change.position)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        palette.forEachIndexed { index, value ->
            val row = index / columns
            val column = index % columns
            drawRect(
                color = Color(value),
                topLeft = Offset(column * cellWidth, row * cellHeight),
                size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
            )
        }
        drawRoundRect(
            color = outlineColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

private fun paletteColor(
    row: Int,
    column: Int,
    rows: Int,
    hueColumns: Int,
    alpha: Int
): Int {
    if (column == hueColumns) {
        val value = 1f - row.toFloat() / (rows - 1)
        val channel = (value * 255).roundToInt().coerceIn(0, 255)
        return AndroidColor.argb(alpha, channel, channel, channel)
    }
    val hue = column * (360f / hueColumns)
    val saturation = when (row) {
        0 -> 0.12f
        1 -> 0.36f
        2 -> 0.62f
        else -> 0.92f
    }
    val value = when (row) {
        0, 1, 2 -> 1f
        else -> 1f - (row - 2) * 0.145f
    }.coerceAtLeast(0.20f)
    return AndroidColor.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
}

@Composable
private fun NgAlphaSlider(
    color: Int,
    onAlphaChanged: (Int) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val alpha = color ushr 24 and 0xFF
    val opaque = (color and 0x00FFFFFF) or 0xFF000000.toInt()
    val outlineColor = Color(NgTheme.colors.outline)
    val outlineVariantColor = Color(NgTheme.colors.outlineVariant)
    val thumbColor = Color(NgTheme.colors.surface)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize, opaque) {
                fun select(offset: Offset) {
                    if (canvasSize.width <= 0) return
                    val thumbRadius = 13.dp.toPx()
                    val usableWidth = (canvasSize.width - thumbRadius * 2f)
                        .coerceAtLeast(1f)
                    val progress = ((offset.x - thumbRadius) / usableWidth)
                        .coerceIn(0f, 1f)
                    onAlphaChanged((progress * 255f).roundToInt())
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    select(down.position)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { change ->
                            if (change.pressed) select(change.position)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val trackTop = 8.dp.toPx()
        val trackHeight = 28.dp.toPx()
        val trackBottom = trackTop + trackHeight
        val trackCornerRadius = trackHeight / 2f
        val trackPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, trackTop, size.width, trackBottom),
                    cornerRadius = CornerRadius(trackCornerRadius)
                )
            )
        }
        val checker = 7.dp.toPx()
        clipPath(trackPath) {
            var y = trackTop
            var row = 0
            while (y < trackBottom) {
                var x = 0f
                var column = 0
                while (x < size.width) {
                    drawRect(
                        color = if ((row + column) % 2 == 0) {
                            Color(0xFFE5E1E6)
                        } else {
                            Color(0xFFBDB8BF)
                        },
                        topLeft = Offset(x, y),
                        size = Size(
                            minOf(checker, size.width - x),
                            minOf(checker, trackBottom - y)
                        )
                    )
                    x += checker
                    column++
                }
                y += checker
                row++
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(opaque).copy(alpha = 0f), Color(opaque))
                ),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight)
            )
        }
        drawRoundRect(
            color = outlineVariantColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackCornerRadius),
            style = Stroke(width = 1.dp.toPx())
        )
        val radius = 13.dp.toPx()
        val thumbX = radius + (size.width - radius * 2f).coerceAtLeast(0f) * alpha / 255f
        drawCircle(
            color = thumbColor,
            radius = radius,
            center = Offset(thumbX, size.height / 2f)
        )
        drawCircle(
            color = outlineColor,
            radius = radius,
            center = Offset(thumbX, size.height / 2f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

internal fun normalizeHexInput(input: String): String {
    val trimmed = input.trim().uppercase()
    return if (trimmed.startsWith("#")) {
        "#${trimmed.removePrefix("#")}"
    } else {
        trimmed
    }
}
