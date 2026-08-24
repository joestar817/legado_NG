package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme

data class NgFormSelectOption(
    val label: String,
    val value: String
)

enum class NgFormSelectMenuVariant {
    MATCH_ROW,
    END_ANCHORED_COMPACT,
}

/** 带分组标题的紧凑行式表单容器。 */
@Composable
fun NgFormGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
        )
        NgFormPanel(content = content)
    }
}

/** 不附带外部标题的连续亮白表单底板。 */
@Composable
fun NgFormPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colorResource(R.color.ng_surface_card))
            .border(
                width = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
                shape = shape,
            ),
        content = content,
    )
}

/** 连续表单底板内部的紧凑分区标题。 */
@Composable
fun NgFormPanelSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NgFormGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 12.dp),
        thickness = 0.6.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
    )
}

/** 分组内的紧凑单行文本字段；分组容器负责白底、圆角与行间分隔。 */
@Composable
fun NgFormInlineTextRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    valueMuted: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = NgTheme.colors
    val contentAlpha = if (enabled) 1f else 0.45f
    val interactionSource = remember { MutableInteractionSource() }
    val valueColor = when {
        !enabled -> Color(colors.onSurfaceVariant).copy(alpha = contentAlpha)
        valueMuted -> Color(colors.onSurfaceVariant).copy(alpha = 0.58f)
        else -> Color(colors.onSurface)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(0.38f),
            color = Color(colors.onSurface).copy(alpha = contentAlpha),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(0.62f)
                .height(36.dp)
                .semantics { contentDescription = title },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(
                color = valueColor,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.End,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(
                if (readOnly) Color.Transparent else Color(colors.primary)
            ),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            color = Color(colors.onSurfaceVariant).copy(alpha = 0.72f),
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/** 分组内的紧凑下拉设置行。 */
@Composable
fun NgFormSelectRow(
    title: String,
    selectedValue: String,
    options: List<NgFormSelectOption>,
    onValueChange: (String) -> Unit,
    arrowIcon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuVariant: NgFormSelectMenuVariant = NgFormSelectMenuVariant.MATCH_ROW,
) {
    val colors = NgTheme.colors
    val layoutDensity = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var rowWidthPx by remember { mutableStateOf(0) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: selectedValue
    val contentAlpha = if (enabled) 1f else 0.45f
    val rowWidth = with(layoutDensity) { rowWidthPx.toDp() }
    val menuWidth = when (menuVariant) {
        NgFormSelectMenuVariant.MATCH_ROW -> rowWidth
        NgFormSelectMenuVariant.END_ANCHORED_COMPACT -> minOf(rowWidth, 156.dp)
    }
    val menuOffset = when (menuVariant) {
        NgFormSelectMenuVariant.MATCH_ROW -> DpOffset.Zero
        NgFormSelectMenuVariant.END_ANCHORED_COMPACT -> DpOffset(
            x = (rowWidth - menuWidth).coerceAtLeast(0.dp),
            y = 0.dp,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowWidthPx = it.size.width },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(enabled = enabled && options.isNotEmpty()) { expanded = true }
                .semantics {
                    role = Role.Button
                    contentDescription = title
                    stateDescription = selectedLabel
                }
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color(colors.onSurface).copy(alpha = contentAlpha),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selectedLabel,
                color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = arrowIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth),
            offset = menuOffset,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            containerColor = colorResource(R.color.ng_surface_card),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier
                        .height(44.dp)
                        .semantics { selected = option.value == selectedValue },
                    text = {
                        Text(
                            text = option.label,
                            color = Color(colors.onSurface),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onValueChange(option.value)
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp),
                )
            }
        }
    }
}

/** 分组内的紧凑整数步进设置行。 */
@Composable
fun NgFormStepperRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NgStepperButton(
            text = "−",
            contentDescription = "$title -1",
            enabled = value > valueRange.first,
            onClick = { onValueChange((value - 1).coerceIn(valueRange)) },
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(28.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        NgStepperButton(
            text = "+",
            contentDescription = "$title +1",
            enabled = value < valueRange.last,
            onClick = { onValueChange((value + 1).coerceIn(valueRange)) },
        )
    }
}

@Composable
private fun NgStepperButton(
    text: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurfaceVariant).copy(
                alpha = if (enabled) 1f else 0.36f
            ),
            fontSize = 22.sp,
            lineHeight = 22.sp,
        )
    }
}

/** 分组内的紧凑滑动设置行。 */
@Composable
fun NgFormSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.widthIn(min = 74.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        NgSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(valueRange)) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
            variant = NgSliderVariant.COMPACT,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(30.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** 分组内的紧凑开关设置行。整行与开关本身均可切换。 */
@Composable
fun NgFormSwitchSettingRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (summary.isNullOrBlank()) 44.dp else 52.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NgSwitchControl(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            variant = NgSwitchControlVariant.COMPACT,
        )
    }
}

enum class NgFormDensity {
    REGULAR,
    COMPACT,
}

enum class NgFormFieldVariant {
    OUTLINED,
    PLAIN_UNDERLINE,
    INLINE_UNDERLINE,
    LABELED_UNDERLINE,
}

/**
 * NG 紧凑表单字段。
 *
 * OUTLINED 保持既有 34dp 容器和焦点描边；PLAIN_UNDERLINE 提供不显示标签的
 * 单行输入线；INLINE_UNDERLINE 将标签与 32dp 无容器输入线并排。业务页面只
 * 提供字段含义和值，不再自行拼装输入外观。
 */
@Composable
fun NgFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onFocusLost: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null,
    density: NgFormDensity = NgFormDensity.REGULAR,
    variant: NgFormFieldVariant = NgFormFieldVariant.OUTLINED,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            wasFocused = true
        } else if (wasFocused) {
            wasFocused = false
            onFocusLost()
        }
    }
    val borderColor = when {
        isError -> Color(colors.error)
        focused -> Color(colors.primary)
        else -> Color(colors.outline)
    }
    val contentAlpha = if (enabled) 1f else 0.45f
    val compact = density == NgFormDensity.COMPACT
    val inlineUnderlined = variant == NgFormFieldVariant.INLINE_UNDERLINE
    val plainUnderlined = variant == NgFormFieldVariant.PLAIN_UNDERLINE
    val labeledUnderlined = variant == NgFormFieldVariant.LABELED_UNDERLINE
    val underlined = inlineUnderlined || plainUnderlined || labeledUnderlined
    val fieldHeight = if (underlined || compact) 32.dp else 34.dp
    val labelFontSize = if (inlineUnderlined) 13.sp else if (compact) 12.sp else 13.sp
    val valueFontSize = if (underlined) 15.sp else 13.sp
    val valueTextAlign = if (inlineUnderlined) TextAlign.Center else TextAlign.Start

    val inputField: @Composable (Modifier) -> Unit = { inputModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = inputModifier
                .fillMaxWidth()
                .height(fieldHeight),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(
                color = Color(colors.onSurface).copy(alpha = contentAlpha),
                fontSize = valueFontSize,
                lineHeight = if (underlined) 19.sp else 16.sp,
                textAlign = valueTextAlign,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                val decorationModifier = if (underlined) {
                    Modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .drawBehind {
                            val strokeWidth = (
                                if (focused || isError) 1.5.dp else 1.dp
                            ).toPx()
                            val y = size.height - strokeWidth / 2f
                            drawLine(
                                color = borderColor.copy(alpha = contentAlpha),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeWidth,
                            )
                        }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(fieldHeight)
                        .clip(shape)
                        .background(
                            Color(
                                if (enabled) colors.inputContainer
                                else colors.surfaceContainerLow
                            )
                        )
                        .border(
                            width = if (focused || isError) 1.5.dp else 1.dp,
                            color = borderColor.copy(alpha = contentAlpha),
                            shape = shape
                        )
                        .padding(start = 10.dp)
                }
                Row(
                    modifier = decorationModifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = if (inlineUnderlined) {
                            Alignment.Center
                        } else {
                            Alignment.CenterStart
                        }
                    ) {
                        if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                            Text(
                                text = placeholder,
                                modifier = if (inlineUnderlined) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier
                                },
                                color = Color(colors.onSurfaceVariant).copy(alpha = 0.75f),
                                fontSize = valueFontSize,
                                textAlign = valueTextAlign,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (trailingContent != null) {
                        trailingContent()
                    } else if (!inlineUnderlined) {
                        Box(Modifier.size(10.dp))
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (inlineUnderlined) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fieldHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
                    fontSize = labelFontSize,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                inputField(Modifier.weight(1f))
            }
        } else if (plainUnderlined) {
            inputField(Modifier)
        } else {
            Text(
                text = label,
                modifier = Modifier.padding(start = if (labeledUnderlined) 0.dp else 12.dp),
                color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
                fontSize = labelFontSize,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            inputField(
                Modifier.padding(top = if (compact) 4.dp else 6.dp)
            )
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(
                    start = if (underlined) 0.dp else 12.dp,
                    top = 4.dp,
                    end = if (underlined) 0.dp else 12.dp,
                ),
                color = Color(if (isError) colors.error else colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
        }
    }
}

/** NG 多行编辑字段，供简介、说明等正文型表单复用。 */
@Composable
fun NgFormMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 160.dp,
    maxHeight: androidx.compose.ui.unit.Dp = 320.dp,
    minLines: Int = 4,
    maxLines: Int = 12,
    containerColor: Color? = null,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = Color(if (focused) colors.primary else colors.outline)
    val contentAlpha = if (enabled) 1f else 0.45f
    Column(modifier = modifier.fillMaxWidth()) {
        label?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight),
            enabled = enabled,
            textStyle = TextStyle(
                color = Color(colors.onSurface).copy(alpha = contentAlpha),
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            minLines = minLines,
            maxLines = maxLines,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight, max = maxHeight)
                        .clip(shape)
                        .background(
                            containerColor ?: Color(
                                if (enabled) colors.inputContainer
                                else colors.surfaceContainerLow
                            )
                        )
                        .border(
                            width = if (focused) 1.5.dp else 1.dp,
                            color = borderColor.copy(alpha = contentAlpha),
                            shape = shape,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (value.isEmpty() && !placeholder.isNullOrBlank()) {
                        Text(
                            text = placeholder,
                            color = Color(colors.onSurfaceVariant).copy(alpha = 0.75f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
fun NgPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hiddenIcon: Painter,
    visibleIcon: Painter,
    showPasswordDescription: String,
    hidePasswordDescription: String,
    modifier: Modifier = Modifier,
    visibilityResetKey: Any? = null,
    enabled: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusLost: () -> Unit = {}
) {
    var passwordVisible by rememberSaveable(visibilityResetKey) { mutableStateOf(false) }
    val colors = NgTheme.colors
    NgFormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        onFocusLost = onFocusLost,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = enabled) {
                        passwordVisible = !passwordVisible
                    }
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = if (passwordVisible) visibleIcon else hiddenIcon,
                    contentDescription = if (passwordVisible) {
                        hidePasswordDescription
                    } else {
                        showPasswordDescription
                    },
                    tint = Color(colors.onSurfaceVariant)
                )
            }
        }
    )
}

/**
 * NG 紧凑选择字段。
 *
 * 保持与文本字段相同的 34dp 几何，界面显示 label，回调只返回稳定 value。
 */
@Composable
fun NgFormSelectField(
    label: String,
    selectedValue: String,
    options: List<NgFormSelectOption>,
    onValueChange: (String) -> Unit,
    arrowIcon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    density: NgFormDensity = NgFormDensity.REGULAR,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val layoutDensity = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: selectedValue
    val contentAlpha = if (enabled) 1f else 0.45f
    val compact = density == NgFormDensity.COMPACT
    val fieldHeight = if (compact) 32.dp else 34.dp
    val labelFontSize = if (compact) 12.sp else 13.sp
    val topSpacing = if (compact) 4.dp else 6.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 12.dp),
            color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
            fontSize = labelFontSize,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .padding(top = topSpacing)
                .fillMaxWidth()
                .onGloballyPositioned { fieldWidthPx = it.size.width }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
                    .clip(shape)
                    .background(
                        Color(
                            if (enabled) colors.inputContainer
                            else colors.surfaceContainerLow
                        )
                    )
                    .border(1.dp, Color(colors.outline), shape)
                    .clickable(enabled = enabled && options.isNotEmpty()) {
                        expanded = true
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        stateDescription = selectedLabel
                    }
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    color = Color(colors.onSurface).copy(alpha = contentAlpha),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    painter = arrowIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(
                    with(layoutDensity) { fieldWidthPx.toDp() }
                ),
                shape = shape,
                containerColor = colorResource(R.color.ng_surface_card),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        modifier = Modifier
                            .height(44.dp)
                            .semantics {
                                selected = option.value == selectedValue
                            },
                        text = {
                            Text(
                                text = option.label,
                                color = Color(colors.onSurface),
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option.value)
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NgFormSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    density: NgFormDensity = NgFormDensity.REGULAR,
) {
    val compact = density == NgFormDensity.COMPACT
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 36.dp else 42.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = if (compact) 14.sp else 17.sp,
            lineHeight = if (compact) 18.sp else 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NgSwitchControl(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 表单操作区跟随内容自然排列，不将按钮固定到页面底部。
 */
@Composable
fun NgFormActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun NgFormActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

enum class NgFormActionButtonAppearance {
    DEFAULT,
    SURFACE_CARD,
    DIALOG,
}

@Composable
fun NgFormActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.OUTLINE,
    appearance: NgFormActionButtonAppearance = NgFormActionButtonAppearance.DEFAULT,
) {
    val colors = NgTheme.colors
    val primary = Color(colors.primary)
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val surfaceCardAppearance = appearance == NgFormActionButtonAppearance.DIALOG ||
        appearance == NgFormActionButtonAppearance.SURFACE_CARD
    val containerColor = when (variant) {
        NgButtonVariant.PRIMARY,
        NgButtonVariant.PRIMARY_LIGHT_CONTENT -> primary
        NgButtonVariant.TONAL -> Color(colors.selectedContainer)
        NgButtonVariant.NEUTRAL -> Color(colors.surfaceContainerHigh).copy(
            alpha = if (NgTheme.snapshot.isEInk) 1f else 0.38f
        )
        NgButtonVariant.DANGER -> Color(colors.error)
        NgButtonVariant.ON_IMAGE -> Color.Black.copy(alpha = 0.56f)
        NgButtonVariant.OUTLINE -> if (surfaceCardAppearance) {
            colorResource(R.color.ng_surface_card)
        } else {
            Color(colors.surface)
        }
    }
    val contentColor = when (variant) {
        NgButtonVariant.PRIMARY -> Color.White
        NgButtonVariant.PRIMARY_LIGHT_CONTENT -> Color.White
        NgButtonVariant.TONAL,
        NgButtonVariant.NEUTRAL -> Color(colors.onSurface)
        NgButtonVariant.DANGER -> Color.White
        NgButtonVariant.ON_IMAGE -> Color.White
        NgButtonVariant.OUTLINE -> primary
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .widthIn(min = 76.dp),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.55f)
        ),
        border = if (variant == NgButtonVariant.OUTLINE) {
            BorderStroke(1.dp, primary)
        } else {
            null
        },
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
