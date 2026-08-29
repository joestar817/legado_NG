package io.legado.app.ui.login

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import io.legado.app.R
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun SourceLoginScreen(
    title: String,
    rows: List<RowUi>,
    values: Map<String, String>,
    displayNames: List<String>,
    errors: Map<String, String>,
    isV2: Boolean,
    isLoading: Boolean,
    enabledActions: Map<String, Boolean>,
    countdowns: Map<String, Int>,
    onConfirm: () -> Unit,
    onShowLoginHeader: () -> Unit,
    onDeleteLoginHeader: () -> Unit,
    onAppLog: () -> Unit,
    onNetworkLog: () -> Unit,
    onValueChange: (RowUi, String) -> Unit,
    onButton: (RowUi, Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val bodyMaxHeight = (maxHeight - 56.dp).coerceAtLeast(120.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(colorResource(R.color.background_card)),
        ) {
            SourceLoginTopBar(
                title = title,
                showConfirm = !isV2,
                onConfirm = onConfirm,
                onShowLoginHeader = onShowLoginHeader,
                onDeleteLoginHeader = onDeleteLoginHeader,
                onAppLog = onAppLog,
                onNetworkLog = onNetworkLog,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                SourceLoginFlexLayout(
                    rows = rows,
                    isV2 = isV2,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rows.forEachIndexed { index, row ->
                        SourceLoginRow(
                            row = row,
                            displayName = displayNames.getOrNull(index) ?: row.name,
                            value = values[row.valueKey(isV2)].orEmpty(),
                            error = errors[row.valueKey(isV2)],
                            isV2 = isV2,
                            enabled = row.action?.let {
                                enabledActions[it] != false &&
                                    countdowns.getOrDefault(it, 0) <= 0
                            } ?: true,
                            countdown = row.action?.let(countdowns::get),
                            onValueChange = { onValueChange(row, it) },
                            onButton = { longClick -> onButton(row, longClick) },
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(NgTheme.colors.primary),
            )
        }
    }
}

@Composable
private fun SourceLoginTopBar(
    title: String,
    showConfirm: Boolean,
    onConfirm: () -> Unit,
    onShowLoginHeader: () -> Unit,
    onDeleteLoginHeader: () -> Unit,
    onAppLog: () -> Unit,
    onNetworkLog: () -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    val context = LocalContext.current
    val toolbarColor = Color(context.primaryColor)
    val toolbarContentColor = colorResource(R.color.primaryText)
    Surface(
        color = toolbarColor,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = toolbarContentColor,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showConfirm) {
                IconButton(onClick = onConfirm) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.ok),
                        tint = toolbarContentColor,
                    )
                }
            }
            Box {
                IconButton(onClick = menuState::onAnchorClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.menu),
                        tint = toolbarContentColor,
                    )
                }
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = listOf(
                        NgExpandableActionMenuItem(
                            R.id.menu_show_login_header,
                            R.string.show_login_header,
                            0,
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_del_login_header,
                            R.string.del_login_header,
                            0,
                        ),
                        NgExpandableActionMenuItem(R.id.menu_log, R.string.log, 0),
                        NgExpandableActionMenuItem(
                            R.id.menu_network_log,
                            R.string.network_request_log,
                            R.drawable.ic_cfg_about,
                        ),
                    ),
                    onItemClick = { item ->
                        menuState.close()
                        when (item.itemId) {
                            R.id.menu_show_login_header -> onShowLoginHeader()
                            R.id.menu_del_login_header -> onDeleteLoginHeader()
                            R.id.menu_log -> onAppLog()
                            R.id.menu_network_log -> onNetworkLog()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceLoginRow(
    row: RowUi,
    displayName: String,
    value: String,
    error: String?,
    isV2: Boolean,
    enabled: Boolean,
    countdown: Int?,
    onValueChange: (String) -> Unit,
    onButton: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (row.type) {
        RowUi.Type.text,
        RowUi.Type.password -> {
            SourceLoginTextField(
                label = displayName,
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.padding(top = 3.dp),
                placeholder = row.hint,
                enabled = enabled,
                error = error,
                textAlign = row.fieldTextAlign(),
                visualTransformation = if (row.type == RowUi.Type.password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )
        }

        RowUi.Type.select -> {
            val options = if (isV2) {
                row.options.orEmpty()
            } else {
                row.chars?.filterNotNull().orEmpty()
            }
            SourceLoginSelect(
                label = displayName,
                selectedValue = value,
                options = options.map { NgFormSelectOption(it, it) },
                onValueChange = onValueChange,
                modifier = modifier,
                enabled = enabled,
            )
        }

        RowUi.Type.toggle -> {
            if (isV2) {
                Row(
                    modifier = modifier
                        .heightIn(min = 48.dp)
                        .clickable(enabled = enabled) {
                            onValueChange((value != "true").toString())
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayName,
                        modifier = Modifier.weight(1f),
                        color = colorResource(R.color.primaryText),
                        fontSize = 14.sp,
                    )
                    Switch(
                        checked = value == "true",
                        onCheckedChange = { onValueChange(it.toString()) },
                        modifier = Modifier.scale(0.82f),
                        enabled = enabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(NgTheme.colors.primary),
                        ),
                    )
                }
            } else {
                SourceLoginChip(
                    text = legacyToggleLabel(row, displayName, value),
                    enabled = enabled,
                    textAlign = row.controlTextAlign(),
                    modifier = modifier,
                    onClick = { onButton(false) },
                    onLongClick = { onButton(true) },
                )
            }
        }

        RowUi.Type.button -> {
            val label = if (countdown != null && countdown > 0) {
                "$displayName (${countdown}s)"
            } else {
                displayName
            }
            SourceLoginChip(
                text = label,
                enabled = enabled,
                textAlign = row.controlTextAlign(),
                modifier = modifier,
                onClick = { onButton(false) },
                onLongClick = { onButton(true) },
            )
        }

        RowUi.Type.label -> {
            Text(
                text = displayName,
                modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = colorResource(R.color.primaryText),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = row.textAlign(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceLoginChip(
    text: String,
    enabled: Boolean,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val buttonColor = colorResource(R.color.btn_bg_press)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                buttonColor.copy(
                    alpha = buttonColor.alpha * if (enabled) 1f else 0.5f
                )
            )
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = colorResource(R.color.primaryText)
                .copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SourceLoginTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    error: String? = null,
    textAlign: TextAlign = TextAlign.Start,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val context = LocalContext.current
    val accent = Color(context.accentColor)
    val textColor = colorResource(R.color.primaryText)
    val lineColor = textColor.copy(alpha = 0.42f)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = accent,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 16.sp,
                lineHeight = 21.sp,
                textAlign = textAlign,
            ),
            cursorBrush = SolidColor(accent),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            color = textColor.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                            textAlign = textAlign,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (error == null) lineColor else Color(NgTheme.colors.error)),
        )
        error?.let {
            Text(
                text = it,
                color = Color(NgTheme.colors.error),
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun SourceLoginSelect(
    label: String,
    selectedValue: String,
    options: List<NgFormSelectOption>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val accent = Color(context.accentColor)
    val textColor = colorResource(R.color.primaryText)
    val menuState = remember { NgPopupToggleState() }
    Row(
        modifier = modifier
            .padding(4.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(3.dp),
            color = accent,
            fontSize = 14.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable(enabled = enabled && options.isNotEmpty()) {
                    menuState.onAnchorClick()
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = options.firstOrNull { it.value == selectedValue }?.label
                        ?: selectedValue,
                    modifier = Modifier.weight(1f),
                    color = textColor.copy(alpha = if (enabled) 1f else 0.5f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = textColor,
                )
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(textColor.copy(alpha = 0.42f))
                    .align(Alignment.BottomCenter),
            )
            DropdownMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                containerColor = colorResource(R.color.background_menu),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = textColor,
                                fontSize = 14.sp,
                            )
                        },
                        onClick = {
                            menuState.close()
                            onValueChange(option.value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceLoginFlexLayout(
    rows: List<RowUi>,
    isV2: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val layoutWidth = constraints.maxWidth
        val containerPadding = 3.dp.roundToPx()
        val dividerSize = 8.dp.roundToPx()
        val chipMargin = 3.dp.roundToPx()
        val matchParentWidth = (layoutWidth - containerPadding * 2).coerceAtLeast(1)

        data class FlexItem(
            val index: Int,
            val baseWidth: Int,
            val grow: Float,
            val shrink: Float,
            val alignSelf: Int,
            val marginStart: Int,
            val marginTop: Int,
            val marginEnd: Int,
            val marginBottom: Int,
        )

        val lines = mutableListOf<List<FlexItem>>()
        var currentLine = mutableListOf<FlexItem>()
        var currentMainSize = containerPadding * 2

        fun finishLine() {
            if (currentLine.isNotEmpty()) {
                lines += currentLine
                currentLine = mutableListOf()
                currentMainSize = containerPadding * 2
            }
        }

        measurables.forEachIndexed { index, measurable ->
            val row = rows[index]
            val style = row.style()
            val basis = style.layout_flexBasisPercent
            val intrinsicWidth = measurable.maxIntrinsicWidth(Constraints.Infinity)
            val baseWidth = when {
                basis > 0f && basis <= 1f -> (layoutWidth * basis).roundToInt()
                row.type == RowUi.Type.text || row.type == RowUi.Type.password -> matchParentWidth
                row.type == RowUi.Type.toggle && row.key != null -> matchParentWidth
                else -> intrinsicWidth
            }.coerceIn(1, layoutWidth)
            val hasChipMargin = row.type == RowUi.Type.button ||
                (!isV2 && row.type == RowUi.Type.toggle)
            val margin = if (hasChipMargin) chipMargin else 0

            val divider = if (currentLine.isEmpty()) 0 else dividerSize
            val nextMainSize = currentMainSize + divider + margin + baseWidth + margin
            if (currentLine.isNotEmpty() &&
                (style.layout_wrapBefore || nextMainSize > layoutWidth)
            ) {
                finishLine()
            }
            currentLine += FlexItem(
                index = index,
                baseWidth = baseWidth,
                grow = style.layout_flexGrow.coerceAtLeast(0f),
                shrink = style.layout_flexShrink.coerceAtLeast(0f),
                alignSelf = style.alignSelf(),
                marginStart = margin,
                marginTop = margin,
                marginEnd = margin,
                marginBottom = margin,
            )
            currentMainSize += if (currentLine.size == 1) {
                margin + baseWidth + margin
            } else {
                dividerSize + margin + baseWidth + margin
            }
        }
        finishLine()

        data class MeasuredLine(
            val items: List<Pair<FlexItem, androidx.compose.ui.layout.Placeable>>,
            val height: Int,
        )

        val measuredLines = lines.map { line ->
            val fixedWidth = containerPadding * 2 +
                dividerSize * (line.size - 1).coerceAtLeast(0) +
                line.sumOf { it.marginStart + it.marginEnd }
            val baseTotal = line.sumOf(FlexItem::baseWidth)
            val growTotal = line.sumOf { it.grow.toDouble() }.toFloat()
            val shrinkTotal = line.sumOf { it.shrink.toDouble() }.toFloat()
            val freeSpace = layoutWidth - fixedWidth - baseTotal
            val lastGrowingIndex = line.indexOfLast { it.grow > 0f }
            val lastShrinkingIndex = line.indexOfLast { it.shrink > 0f }
            var allocatedGrow = 0
            var allocatedShrink = 0
            val widths = line.mapIndexed { index, item ->
                when {
                    freeSpace > 0 && growTotal > 0f && item.grow > 0f -> {
                        val extra = if (index == lastGrowingIndex) {
                            freeSpace - allocatedGrow
                        } else {
                            (freeSpace * (item.grow / growTotal)).roundToInt().also {
                                allocatedGrow += it
                            }
                        }
                        item.baseWidth + extra
                    }

                    freeSpace < 0 && shrinkTotal > 0f && item.shrink > 0f -> {
                        val overflow = -freeSpace
                        val reduction = if (index == lastShrinkingIndex) {
                            overflow - allocatedShrink
                        } else {
                            (overflow * (item.shrink / shrinkTotal)).roundToInt().also {
                                allocatedShrink += it
                            }
                        }
                        (item.baseWidth - reduction).coerceAtLeast(1)
                    }

                    else -> item.baseWidth
                }
            }
            val placeables = line.mapIndexed { index, item ->
                val width = widths[index].coerceAtLeast(1)
                item to measurables[item.index].measure(
                    constraints.copy(
                        minWidth = width,
                        maxWidth = width,
                        minHeight = 0,
                    )
                )
            }
            MeasuredLine(
                items = placeables,
                height = placeables.maxOfOrNull { (item, placeable) ->
                    item.marginTop + placeable.height + item.marginBottom
                } ?: 0,
            )
        }

        val contentHeight = containerPadding * 2 +
            measuredLines.sumOf(MeasuredLine::height) +
            dividerSize * (measuredLines.size - 1).coerceAtLeast(0)
        val layoutHeight = contentHeight.coerceIn(
            minimumValue = constraints.minHeight,
            maximumValue = constraints.maxHeight,
        )
        layout(layoutWidth, layoutHeight) {
            var y = containerPadding
            measuredLines.forEach { line ->
                var x = containerPadding
                line.items.forEach { (item, placeable) ->
                    x += item.marginStart
                    val childY = when (item.alignSelf) {
                        1 -> y + line.height - item.marginBottom - placeable.height
                        2 -> y + item.marginTop +
                            (line.height - item.marginTop - item.marginBottom - placeable.height) / 2
                        else -> y + item.marginTop
                    }
                    placeable.placeRelative(x, childY)
                    x += placeable.width + item.marginEnd + dividerSize
                }
                y += line.height + dividerSize
            }
        }
    }
}

private fun RowUi.valueKey(isV2: Boolean): String {
    return if (isV2) key.orEmpty() else name
}

private fun RowUi.textAlign(): TextAlign {
    return when (style().layout_justifySelf) {
        "center" -> TextAlign.Center
        "flex_end", "right" -> TextAlign.End
        else -> TextAlign.Start
    }
}

private fun RowUi.fieldTextAlign(): TextAlign {
    return when (style().layout_justifySelf) {
        "center" -> TextAlign.Center
        "flex_end" -> TextAlign.End
        else -> TextAlign.Start
    }
}

private fun RowUi.controlTextAlign(): TextAlign {
    return when (style().layout_justifySelf) {
        "flex_start" -> TextAlign.Start
        "flex_end" -> TextAlign.End
        else -> TextAlign.Center
    }
}

private fun legacyToggleLabel(row: RowUi, displayName: String, value: String): String {
    return if (row.style().layout_justifySelf == "right") {
        displayName + value
    } else {
        value + displayName
    }
}
