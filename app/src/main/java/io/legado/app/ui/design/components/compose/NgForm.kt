package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgSolidButtonPalette
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val formRowStartPadding = 14.dp
private val formRowEndPadding = 10.dp
private val formChoiceArrowSize = 20.dp
private val formNumberStepWidth = 44.dp

data class NgFormSelectOption(
    val label: String,
    val value: String,
    val enabled: Boolean = true,
)

enum class NgFormSelectMenuVariant {
    MATCH_ROW,
    END_ANCHORED_COMPACT,
    END_ANCHORED_FILTER,
    END_ANCHORED_CONTENT,
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

enum class NgFormPanelVariant { STANDARD, LARGE_ROUNDED, SOURCE_EDITOR }

/** 不附带外部标题的连续亮白表单底板。 */
@Composable
fun NgFormPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NgFormPanel(NgFormPanelVariant.STANDARD, modifier, content)
}

@Composable
fun NgFormPanel(
    variant: NgFormPanelVariant,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = when (variant) {
        NgFormPanelVariant.STANDARD, NgFormPanelVariant.SOURCE_EDITOR -> NgTheme.shapes.mediumDp
        NgFormPanelVariant.LARGE_ROUNDED -> NgTheme.shapes.largeDp
    }
    val shape = RoundedCornerShape(radius.dp)
    val cardColor = if (variant == NgFormPanelVariant.SOURCE_EDITOR) {
        Color(NgTheme.colors.cardContainer).copy(alpha = 1f)
    } else ngDrawerContentCardColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ngDrawerImageContentCardShadow(
                shape = shape,
                fallback = BorderStroke(
                    0.6.dp,
                    Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f * cardColor.alpha),
                ),
            )
            .clip(shape)
            .background(cardColor),
        content = content,
    )
}

/** 连续表单底板内部的紧凑分区标题。 */
@Composable
fun NgFormPanelSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Medium,
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
            fontSize = fontSize,
            lineHeight = 18.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NgFormGroupDivider(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = horizontalPadding),
        thickness = 0.6.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
    )
}

enum class NgFormInlineTextRowVariant {
    SINGLE_LINE,
    WRAPPING_VALUE,
}

/** 分组内的紧凑文本字段，由分组容器绘制底板和分隔线。 */
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
    variant: NgFormInlineTextRowVariant = NgFormInlineTextRowVariant.SINGLE_LINE,
) {
    val colors = NgTheme.colors
    val contentAlpha = if (enabled) 1f else 0.45f
    val interactionSource = remember { MutableInteractionSource() }
    val valueColor = when {
        !enabled -> Color(colors.onSurfaceVariant).copy(alpha = contentAlpha)
        valueMuted -> Color(colors.onSurfaceVariant).copy(alpha = 0.58f)
        else -> Color(colors.onSurface)
    }
    val wrapsValue = variant == NgFormInlineTextRowVariant.WRAPPING_VALUE
    val rowHeight = if (wrapsValue) {
        Modifier.heightIn(min = 48.dp).padding(vertical = 6.dp)
    } else {
        Modifier.height(48.dp)
    }
    val valueHeight = if (wrapsValue) Modifier.heightIn(min = 36.dp) else Modifier.height(36.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(rowHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = if (wrapsValue) {
                Modifier.widthIn(max = 120.dp).padding(end = 12.dp)
            } else {
                Modifier.weight(0.38f)
            },
            color = Color(colors.onSurface).copy(alpha = contentAlpha),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = if (wrapsValue) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(if (wrapsValue) 1f else 0.62f)
                .then(valueHeight)
                .semantics { contentDescription = title },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = !wrapsValue,
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
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
                        .then(valueHeight),
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

enum class NgFormNavigationRowVariant {
    REGULAR,
    MEDIA,
}

/** 分组内打开独立页面或弹层的当前值行，MEDIA 可在值前展示缩略图。 */
@Composable
fun NgFormNavigationRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    arrowIcon: Painter,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgFormNavigationRowVariant = NgFormNavigationRowVariant.REGULAR,
    valueContent: (@Composable () -> Unit)? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    val isMediaRow = variant == NgFormNavigationRowVariant.MEDIA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isMediaRow) 80.dp else 44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = title
                stateDescription = value
            }
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface).copy(alpha = contentAlpha),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (valueContent != null) {
            valueContent()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = value,
            color = Color(
                if (isMediaRow) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant
            ).copy(alpha = contentAlpha),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = arrowIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = contentAlpha),
        )
    }
}

/** 连续表单内的展开行，复用导航行的密度与文字，不另铺设置卡片。 */
@Composable
fun NgFormExpandableRow(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expansionState = stringResource(
        if (expanded) R.string.ng_form_row_expanded else R.string.ng_form_row_collapsed,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        NgFormNavigationRow(
            title = title,
            value = value,
            onClick = { onExpandedChange(!expanded) },
            arrowIcon = rememberVectorPainter(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            ),
            enabled = enabled,
            modifier = Modifier.semantics {
                stateDescription = if (value.isBlank()) expansionState else "$value, $expansionState"
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                content = content,
            )
        }
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
    contentStartPadding: Dp = formRowStartPadding,
) {
    NgFormChoiceRow(
        title = title,
        selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue,
        options = options,
        isSelected = { it == selectedValue },
        onValueChange = onValueChange,
        arrowIcon = arrowIcon,
        modifier = modifier,
        enabled = enabled,
        menuVariant = menuVariant,
        multiple = false,
        contentStartPadding = contentStartPadding,
    )
}

@Composable
internal fun NgFormMultiSelectRow(
    title: String,
    selectedLabel: String,
    options: List<NgFormSelectOption>,
    selectedValues: Set<String>,
    onToggle: (String) -> Unit,
    arrowIcon: Painter,
    enabled: Boolean = true,
) {
    NgFormChoiceRow(
        title = title,
        selectedLabel = selectedLabel,
        options = options,
        isSelected = selectedValues::contains,
        onValueChange = onToggle,
        arrowIcon = arrowIcon,
        modifier = Modifier,
        enabled = enabled,
        menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
        multiple = true,
    )
}

@Composable
private fun ngFormSelectMenuWidth(
    variant: NgFormSelectMenuVariant,
    anchorWidth: Dp,
    options: List<NgFormSelectOption>,
    optionStyle: TextStyle,
    optionPadding: Dp,
    extraItemWidth: Dp = 0.dp,
): Dp = when (variant) {
    NgFormSelectMenuVariant.MATCH_ROW -> anchorWidth
    NgFormSelectMenuVariant.END_ANCHORED_CONTENT -> {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val labelWidth = remember(options, optionStyle, measurer, density) {
            val pixels = options.maxOfOrNull {
                measurer.measure(it.label, style = optionStyle, maxLines = 1).size.width
            } ?: 0
            with(density) { pixels.toDp() }
        }
        (labelWidth + optionPadding * 2 + extraItemWidth).coerceAtLeast(48.dp).coerceAtMost(anchorWidth)
    }
    NgFormSelectMenuVariant.END_ANCHORED_COMPACT,
    NgFormSelectMenuVariant.END_ANCHORED_FILTER -> minOf(anchorWidth, 156.dp)
}

@Composable
private fun NgFormChoiceRow(
    title: String,
    selectedLabel: String,
    options: List<NgFormSelectOption>,
    isSelected: (String) -> Boolean,
    onValueChange: (String) -> Unit,
    arrowIcon: Painter,
    modifier: Modifier,
    enabled: Boolean,
    menuVariant: NgFormSelectMenuVariant,
    multiple: Boolean,
    contentStartPadding: Dp = formRowStartPadding,
) {
    val colors = NgTheme.colors
    val layoutDensity = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var rowWidthPx by remember { mutableStateOf(0) }
    val contentAlpha = if (enabled) 1f else 0.45f
    val rowWidth = with(layoutDensity) { rowWidthPx.toDp() }
    val filter = menuVariant == NgFormSelectMenuVariant.END_ANCHORED_FILTER
    val contentWidth = menuVariant == NgFormSelectMenuVariant.END_ANCHORED_CONTENT
    val optionPadding = if (contentWidth) 12.dp else 18.dp
    val optionStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
    val menuWidth = ngFormSelectMenuWidth(
        menuVariant, rowWidth, options, optionStyle, optionPadding,
        extraItemWidth = if (multiple) 32.dp else 0.dp,
    )
    val menuOffset = DpOffset(x = (rowWidth - menuWidth).coerceAtLeast(0.dp), y = 0.dp)

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
                    contentDescription = if (filter) "类型筛选，$title" else title
                    stateDescription = selectedLabel
                }
                .padding(start = contentStartPadding, end = formRowEndPadding),
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
                color = Color(if (filter) colors.primary else colors.onSurfaceVariant).copy(alpha = contentAlpha),
                fontSize = if (filter) 14.sp else 13.sp,
                fontWeight = if (filter) FontWeight.Medium else null,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = arrowIcon,
                contentDescription = null,
                modifier = Modifier.size(formChoiceArrowSize),
                tint = Color(if (filter) colors.primary else colors.onSurfaceVariant).copy(alpha = contentAlpha),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth),
            offset = menuOffset,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            containerColor = ngDrawerContentCardColor(),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
        ) {
            options.forEach { option ->
                val selected = isSelected(option.value)
                DropdownMenuItem(
                    enabled = option.enabled,
                    modifier = Modifier
                        .height(44.dp)
                        .semantics { this.selected = selected },
                    text = {
                        Text(
                            text = option.label,
                            color = Color(if ((filter || multiple) && selected) colors.primary else colors.onSurface)
                                .copy(alpha = if (option.enabled) 1f else 0.45f),
                            style = if (contentWidth) optionStyle else LocalTextStyle.current,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if ((filter || multiple) && selected) {
                        {
                            Icon(Icons.Rounded.Check, contentDescription = null,
                                modifier = Modifier.size(20.dp), tint = Color(colors.primary))
                        }
                    } else null,
                    onClick = {
                        if (!multiple) expanded = false
                        onValueChange(option.value)
                    },
                    contentPadding = PaddingValues(horizontal = optionPadding),
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
    onLongPressStep: (() -> Boolean)? = null,
    onLongPressFinished: (() -> Unit)? = null,
    @DrawableRes chevronIcon: Int? = null,
    modifier: Modifier = Modifier,
) {
    if (chevronIcon != null) {
        NgSliderStepButton(
            iconRes = chevronIcon,
            contentDescription = contentDescription,
            enabled = enabled,
            onClick = onClick,
            modifier = modifier.width(formNumberStepWidth),
            onLongPressStep = onLongPressStep,
            onLongPressFinished = onLongPressFinished,
        )
        return
    }
    val hapticFeedback = LocalHapticFeedback.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPressStep by rememberUpdatedState(onLongPressStep)
    val currentOnLongPressFinished by rememberUpdatedState(onLongPressFinished)
    val repeatEnabled = onLongPressStep != null && onLongPressFinished != null
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (repeatEnabled) {
                    Modifier
                        .semantics {
                            role = Role.Button
                            if (!enabled) disabled()
                            onClick {
                                if (currentEnabled) {
                                    currentOnClick()
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            var longPressHandled = false
                            detectTapGestures(
                                onPress = press@{
                                    if (!currentEnabled) return@press
                                    longPressHandled = false
                                    coroutineScope {
                                        val repeatJob = launch {
                                            delay(NG_FORM_NUMBER_LONG_PRESS_DELAY_MS)
                                            if (!currentEnabled) return@launch
                                            longPressHandled = true
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                            while (
                                                isActive &&
                                                currentOnLongPressStep?.invoke() == true
                                            ) {
                                                delay(NG_FORM_NUMBER_REPEAT_INTERVAL_MS)
                                            }
                                        }
                                        tryAwaitRelease()
                                        repeatJob.cancelAndJoin()
                                    }
                                    if (longPressHandled) {
                                        currentOnLongPressFinished?.invoke()
                                    }
                                },
                                onTap = {
                                    if (currentEnabled && !longPressHandled) currentOnClick()
                                    longPressHandled = false
                                },
                            )
                        }
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                }
            )
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

enum class NgFormSwitchSettingRowVariant {
    DEFAULT,
    CHANGE_SOURCE,
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
    variant: NgFormSwitchSettingRowVariant = NgFormSwitchSettingRowVariant.DEFAULT,
) {
    val changeSource = variant == NgFormSwitchSettingRowVariant.CHANGE_SOURCE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (changeSource) 64.dp else if (summary.isNullOrBlank()) 44.dp else 52.dp)
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
                fontSize = if (changeSource) 15.sp else 14.sp,
                lineHeight = if (changeSource) 20.sp else 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = if (changeSource) 12.sp else 11.sp,
                    lineHeight = if (changeSource) 18.sp else 14.sp,
                    maxLines = if (changeSource) 2 else 1,
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

enum class NgFormNumberSettingRowVariant {
    OUTLINED,
    COMPACT_CHEVRONS,
}

/** 分组内左侧标题／摘要、右侧短输入框的紧凑数字设置行。 */
@Composable
fun NgFormNumberSettingRow(
    title: String,
    summary: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueWidth: Dp = 96.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusLost: () -> Unit = {},
    valueRange: IntRange? = null,
    onStepValueChange: ((Int) -> Unit)? = null,
    onStepValueChangeFinished: (() -> Unit)? = null,
    variant: NgFormNumberSettingRowVariant = NgFormNumberSettingRowVariant.OUTLINED,
    isError: Boolean = false,
) {
    val colors = NgTheme.colors
    val compact = variant == NgFormNumberSettingRowVariant.COMPACT_CHEVRONS
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
    val contentAlpha = if (enabled) 1f else 0.45f
    val borderColor = Color(when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.outline
    })
    val stepControlsVisible = valueRange != null &&
        onStepValueChange != null &&
        onStepValueChangeFinished != null
    val parsedValue = value.toIntOrNull()?.takeIf { current ->
        valueRange?.let { current in it } ?: true
    }
    var repeatedValue by remember { mutableIntStateOf(parsedValue ?: 0) }
    var repeating by remember { mutableStateOf(false) }

    fun stepValue(delta: Int, repeat: Boolean): Boolean {
        val range = valueRange ?: return false
        val base = if (repeat) {
            if (!repeating) {
                val current = parsedValue ?: return false
                repeatedValue = current
                repeating = true
            }
            repeatedValue
        } else {
            parsedValue ?: return false
        }
        val next = (base + delta).coerceIn(range)
        if (next == base) return false
        if (repeat) repeatedValue = next
        onStepValueChange?.invoke(next)
        return true
    }

    fun finishRepeatedValue() {
        repeating = false
        onStepValueChangeFinished?.invoke()
    }

    val verticalPadding = if (compact) 2.dp else 7.dp
    val endPadding = if (compact) 0.dp else formRowEndPadding
    // Align the compact step button's center to the form choice arrow while keeping its touch width.
    val countControlModifier = if (compact && stepControlsVisible) {
        Modifier.offset(x = (formNumberStepWidth - formChoiceArrowSize) / 2 - formRowEndPadding)
    } else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (summary.isNullOrBlank()) 48.dp else 56.dp)
            .alpha(contentAlpha)
            .padding(start = 14.dp, end = endPadding, top = verticalPadding, bottom = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                color = Color(colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = Color(if (isError) colors.error else colors.onSurfaceVariant),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = if (isError) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (stepControlsVisible) {
            NgStepperButton(
                text = "−",
                contentDescription = "${stringResource(R.string.reduce)} $title",
                enabled = enabled && parsedValue != null && parsedValue > valueRange.first,
                onClick = {
                    if (stepValue(delta = -1, repeat = false)) {
                        onStepValueChangeFinished()
                    }
                },
                onLongPressStep = { stepValue(delta = -1, repeat = true) },
                onLongPressFinished = ::finishRepeatedValue,
                chevronIcon = if (compact) R.drawable.ic_chevron_left_20 else null,
                modifier = countControlModifier,
            )
            if (!compact) Spacer(Modifier.width(4.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = countControlModifier
                .width(when {
                    compact -> 44.dp
                    stepControlsVisible -> valueWidth.coerceAtMost(80.dp)
                    else -> valueWidth
                })
                .height(if (compact) 44.dp else 34.dp)
                .semantics {
                    contentDescription = title
                    if (isError && summary != null) error(summary)
                },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
                color = Color(colors.onSurface),
                fontSize = if (compact) 14.sp else 13.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                if (compact) {
                    val chipShape = RoundedCornerShape(8.dp)
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.fillMaxWidth().height(24.dp)
                                .background(Color(colors.onSurface).copy(alpha = 0.08f), chipShape)
                                .then(
                                    if (focused || isError) Modifier.border(1.dp, borderColor, chipShape)
                                    else Modifier,
                                ),
                        )
                        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            innerTextField()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(shape)
                            .background(Color(colors.inputContainer))
                            .border(
                                width = if (focused) 1.5.dp else 1.dp,
                                color = borderColor,
                                shape = shape,
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        innerTextField()
                    }
                }
            },
        )
        if (stepControlsVisible) {
            if (!compact) Spacer(Modifier.width(4.dp))
            NgStepperButton(
                text = "+",
                contentDescription = "${stringResource(R.string.plus)} $title",
                enabled = enabled && parsedValue != null && parsedValue < valueRange.last,
                onClick = {
                    if (stepValue(delta = 1, repeat = false)) {
                        onStepValueChangeFinished()
                    }
                },
                onLongPressStep = { stepValue(delta = 1, repeat = true) },
                onLongPressFinished = ::finishRepeatedValue,
                chevronIcon = if (compact) R.drawable.ic_chevron_right_20 else null,
                modifier = countControlModifier,
            )
        }
    }
}

private const val NG_FORM_NUMBER_LONG_PRESS_DELAY_MS = 400L
private const val NG_FORM_NUMBER_REPEAT_INTERVAL_MS = 90L

enum class NgFormDensity {
    REGULAR,
    COMPACT,
}

enum class NgFormSwitchRowVariant {
    DEFAULT,
    GROUPED,
}

enum class NgFormFieldVariant {
    OUTLINED,
    PLAIN_UNDERLINE,
    INLINE_UNDERLINE,
    LABELED_UNDERLINE,
    DIALOG_UNDERLINE,
    DRAWER_UNDERLINE,
    INLINE_END_UNDERLINE,
}

/**
 * NG 紧凑表单字段。
 *
 * OUTLINED 保持既有 34dp 容器和焦点描边；PLAIN_UNDERLINE 提供不显示标签的
 * 32dp 单行输入线；DIALOG_UNDERLINE 提供旧网络导入弹窗同款 44dp 输入线；
 * INLINE_UNDERLINE 将标签与输入线并排；INLINE_END_UNDERLINE 在行尾放紧凑的
 * 输入线，让数值贴近单位。业务页面只提供字段含义和值。
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
    autoFocus: Boolean = false,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val borderColor = when {
        isError -> Color(colors.error)
        focused -> Color(colors.primary)
        else -> Color(colors.outline)
    }
    val contentAlpha = if (enabled) 1f else 0.45f
    val compact = density == NgFormDensity.COMPACT
    val endAligned = variant == NgFormFieldVariant.INLINE_END_UNDERLINE
    val inlineUnderlined = endAligned || variant == NgFormFieldVariant.INLINE_UNDERLINE
    val dialogUnderlined = variant == NgFormFieldVariant.DIALOG_UNDERLINE
    val drawerUnderlined = variant == NgFormFieldVariant.DRAWER_UNDERLINE
    val plainUnderlined = variant == NgFormFieldVariant.PLAIN_UNDERLINE || dialogUnderlined || drawerUnderlined
    val labeledUnderlined = variant == NgFormFieldVariant.LABELED_UNDERLINE
    val underlined = inlineUnderlined || plainUnderlined || labeledUnderlined
    val fieldHeight = when {
        drawerUnderlined -> 72.dp
        dialogUnderlined -> 44.dp
        underlined || compact -> 32.dp
        else -> 34.dp
    }
    val labelFontSize = if (inlineUnderlined) 13.sp else if (compact) 12.sp else 13.sp
    val valueFontSize = when {
        dialogUnderlined -> 16.sp
        underlined -> 15.sp
        else -> 13.sp
    }
    val valueTextAlign = when {
        endAligned -> TextAlign.End
        inlineUnderlined -> TextAlign.Center
        else -> TextAlign.Start
    }

    val inputField: @Composable (Modifier) -> Unit = { inputModifier ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = inputModifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .height(fieldHeight),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
                color = Color(colors.onSurface).copy(alpha = contentAlpha),
                fontSize = valueFontSize,
                lineHeight = when {
                    dialogUnderlined -> 22.sp
                    underlined -> 19.sp
                    else -> 16.sp
                },
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
                        .then(
                            if (dialogUnderlined) {
                                Modifier.padding(horizontal = 2.dp)
                            } else {
                                Modifier
                            }
                        )
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
                        contentAlignment = when (valueTextAlign) {
                            TextAlign.End -> Alignment.CenterEnd
                            TextAlign.Center -> Alignment.Center
                            else -> Alignment.CenterStart
                        },
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
                    modifier = if (endAligned) Modifier.weight(0.4f) else Modifier,
                    color = Color(colors.onSurfaceVariant).copy(alpha = contentAlpha),
                    fontSize = labelFontSize,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                if (endAligned) {
                    Box(Modifier.weight(0.6f), contentAlignment = Alignment.CenterEnd) {
                        inputField(Modifier.widthIn(max = 144.dp))
                    }
                } else {
                    inputField(Modifier.weight(1f))
                }
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

enum class NgFormMultilineFieldVariant {
    OUTLINED,
    DIALOG_UNDERLINE,
    COMPACT_UNDERLINE,
    INSET_ACTION,
}

/**
 * NG 多行编辑字段，供简介、说明等正文型表单复用。
 *
 * DIALOG_UNDERLINE 保留旧规则弹窗的浮动标签与底部输入线；
 * COMPACT_UNDERLINE 提供变量输入所需的紧凑下划线和尾部操作；
 * INSET_ACTION 将操作放在同一底板的行尾下方，随输入区一起增高。
 */
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    variant: NgFormMultilineFieldVariant = NgFormMultilineFieldVariant.OUTLINED,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colors = NgTheme.colors
    val insetAction = variant == NgFormMultilineFieldVariant.INSET_ACTION
    val shape = RoundedCornerShape(
        (if (insetAction) NgTheme.shapes.mediumDp else NgTheme.shapes.smallDp).dp,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = Color(if (focused) colors.primary else colors.outline)
    val contentAlpha = if (enabled) 1f else 0.45f
    val compactUnderlined = variant == NgFormMultilineFieldVariant.COMPACT_UNDERLINE
    val underlined = variant == NgFormMultilineFieldVariant.DIALOG_UNDERLINE || compactUnderlined
    val emptyHint = placeholder?.takeIf { value.isEmpty() && it.isNotBlank() }
    val hideHint = insetAction && focused
    Column(modifier = modifier.fillMaxWidth()) {
        label?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                color = Color(
                    if (underlined) colors.primary else colors.onSurfaceVariant
                ).copy(alpha = contentAlpha),
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
                .heightIn(min = minHeight, max = maxHeight)
                .then(
                    if (hideHint && emptyHint != null) {
                        Modifier.semantics { contentDescription = emptyHint }
                    } else Modifier
                ),
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
                color = Color(colors.onSurface).copy(alpha = contentAlpha),
                fontSize = 15.sp,
                lineHeight = if (compactUnderlined) 19.sp else 22.sp,
            ),
            cursorBrush = SolidColor(Color(colors.primary)),
            interactionSource = interactionSource,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                val fieldModifier = Modifier.fillMaxWidth().heightIn(min = minHeight, max = maxHeight)
                val decorationModifier = if (underlined) {
                    fieldModifier
                        .drawBehind {
                            val strokeWidth = (if (focused) 1.5.dp else 1.dp).toPx()
                            val y = size.height - strokeWidth / 2f
                            drawLine(
                                color = borderColor.copy(alpha = contentAlpha),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokeWidth,
                            )
                        }
                        .then(
                            if (compactUnderlined) Modifier
                            else Modifier.padding(horizontal = 2.dp, vertical = 8.dp)
                        )
                } else {
                    fieldModifier
                        .clip(shape)
                        .background(
                            containerColor ?: Color(
                                if (enabled) colors.inputContainer
                                else colors.surfaceContainerLow
                            )
                        )
                        .border(
                            width = if (focused) 1.5.dp else 1.dp,
                            color = if (insetAction && !focused) {
                                Color(colors.outlineVariant).copy(alpha = 0.55f * contentAlpha)
                            } else {
                                borderColor.copy(alpha = contentAlpha)
                            },
                            shape = shape,
                        )
                }
                val fieldContent: @Composable () -> Unit = {
                    if (!hideHint && emptyHint != null) {
                        Text(
                            text = emptyHint,
                            color = Color(colors.onSurfaceVariant).copy(alpha = 0.75f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    innerTextField()
                }
                when {
                    insetAction -> Row(
                        modifier = decorationModifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                                .padding(end = 5.dp, top = 10.dp, bottom = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { fieldContent() }
                        trailingContent?.invoke()
                    }
                    compactUnderlined -> Row(
                        modifier = decorationModifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { fieldContent() }
                        if (trailingContent != null) trailingContent() else Spacer(Modifier.width(10.dp))
                    }
                    underlined -> Box(decorationModifier) { fieldContent() }
                    else -> Box(decorationModifier.padding(horizontal = 12.dp, vertical = 10.dp)) { fieldContent() }
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
    menuVariant: NgFormSelectMenuVariant = NgFormSelectMenuVariant.MATCH_ROW,
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
    val fieldWidth = with(layoutDensity) { fieldWidthPx.toDp() }
    val contentWidth = menuVariant == NgFormSelectMenuVariant.END_ANCHORED_CONTENT
    val optionPadding = if (contentWidth) 12.dp else 18.dp
    val optionStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
    val menuWidth = ngFormSelectMenuWidth(menuVariant, fieldWidth, options, optionStyle, optionPadding)

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
                modifier = Modifier.width(menuWidth),
                offset = DpOffset(x = (fieldWidth - menuWidth).coerceAtLeast(0.dp), y = 0.dp),
                shape = shape,
                containerColor = ngDrawerContentCardColor(),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        enabled = option.enabled,
                        modifier = Modifier
                            .height(44.dp)
                            .semantics {
                                selected = option.value == selectedValue
                            },
                        text = {
                            Text(
                                text = option.label,
                                color = Color(colors.onSurface).copy(alpha = if (option.enabled) 1f else 0.45f),
                                style = if (contentWidth) optionStyle else LocalTextStyle.current,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option.value)
                        },
                        contentPadding = PaddingValues(horizontal = optionPadding)
                    )
                }
            }
        }
    }
}

/**
 * 高对比背景上的连续紧凑表单承载面。
 *
 * 透明玻璃使用高不透明中性承载面保证可读性；液态玻璃由公共视觉路由切换为
 * CONTROL 材质，业务页面无需判断当前视觉体系。
 */
@Composable
fun NgFormControlGroup(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val snapshot = NgTheme.snapshot
    val cornerRadius = NgTheme.shapes.mediumDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val style = NgGlassDefaults.neutralStyle(
        containerAlpha = if (snapshot.isEInk) 1f else 0.92f
    ).copy(
        borderColor = Color(NgTheme.colors.outlineVariant).copy(
            alpha = if (snapshot.isEInk) 1f else 0.22f
        ),
        shadowElevation = 0.dp,
        borderWidth = if (snapshot.isEInk) 1.dp else 0.6.dp,
    )
    NgVisualSurface(
        modifier = modifier.fillMaxWidth(),
        role = NgMaterialRole.CONTROL,
        cornerRadius = cornerRadius,
        shape = shape,
        style = style,
        contentPadding = contentPadding,
        content = content,
    )
}

/** Provider 与 TTS 编辑页的连续字段玻璃承载面。 */
@Composable
fun NgFormFieldGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NgFormControlGroup(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Provider 等开关区沿用原有 12×4dp 几何。 */
@Composable
fun NgFormSwitchGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NgFormControlGroup(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
fun NgFormSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    density: NgFormDensity = NgFormDensity.REGULAR,
    variant: NgFormSwitchRowVariant = NgFormSwitchRowVariant.DEFAULT,
    switchModifier: Modifier = Modifier,
) {
    val compact = density == NgFormDensity.COMPACT
    val grouped = variant == NgFormSwitchRowVariant.GROUPED
    val minHeight = when {
        compact -> 36.dp
        grouped -> 40.dp
        else -> 42.dp
    }
    val fontSize = when {
        compact -> 14.sp
        grouped -> 16.sp
        else -> 17.sp
    }
    val lineHeight = when {
        compact -> 18.sp
        grouped -> 20.sp
        else -> 21.sp
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NgSwitchControl(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
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
    SURFACE_CARD_BORDERLESS,
    DIALOG,
    COLOR_PICKER_CONFIRM,
    /** 阅读夜间浮窗使用浅色 Tonal 确认按钮，保持主次层级。 */
    READING_NIGHT_CONFIRM,
    /** 无底色、无描边的文字操作，默认主题色，危险操作使用错误色。 */
    TEXT,
}

@Composable
fun NgFormActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.OUTLINE,
    appearance: NgFormActionButtonAppearance = NgFormActionButtonAppearance.DEFAULT,
    buttonHeight: Dp = 36.dp,
    minimumWidth: Dp = 76.dp,
    textSize: TextUnit = 14.sp,
    textLineHeight: TextUnit = 17.sp,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = if (appearance == NgFormActionButtonAppearance.TEXT) 8.dp else 12.dp,
    ),
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = NgTheme.colors
    val primary = Color(colors.primary)
    val colorPickerConfirm = appearance == NgFormActionButtonAppearance.COLOR_PICKER_CONFIRM
    val snapshot = NgTheme.snapshot
    val readingNightConfirm = appearance == NgFormActionButtonAppearance.READING_NIGHT_CONFIRM &&
        variant == NgButtonVariant.TONAL && snapshot.isDark && !snapshot.isEInk
    val readingConfirmColors = if (readingNightConfirm) {
        remember(colors.primary, colors.surface) {
            NgSolidButtonPalette.resolveLightTonalAction(colors.primary, colors.surface)
        }
    } else null
    val drawerPrimaryContent = ngDrawerPrimaryContentColor()
    val primaryContent = if (colorPickerConfirm && variant == NgButtonVariant.PRIMARY) {
        Color.White
    } else drawerPrimaryContent
    val primaryColors = rememberNgSolidButtonColors(primary, primaryContent)
    val textAppearance = appearance == NgFormActionButtonAppearance.TEXT
    val shape = RoundedCornerShape(if (colorPickerConfirm) 13.dp else NgTheme.shapes.mediumDp.dp)
    val surfaceCardAppearance = appearance == NgFormActionButtonAppearance.DIALOG ||
        appearance == NgFormActionButtonAppearance.SURFACE_CARD ||
        appearance == NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS
    val borderlessSurfaceAppearance =
        appearance == NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS
    val containerColor = if (textAppearance) Color.Transparent else when (variant) {
        NgButtonVariant.PRIMARY,
        NgButtonVariant.PRIMARY_LIGHT_CONTENT -> Color(primaryColors.container)
        NgButtonVariant.TONAL -> Color(readingConfirmColors?.container ?: colors.selectedContainer)
        NgButtonVariant.NEUTRAL -> Color(colors.surfaceContainerHigh).copy(
            alpha = if (NgTheme.snapshot.isEInk) 1f else 0.38f
        )
        NgButtonVariant.DANGER -> rememberNgSolidButtonContainerColor(Color(colors.error))
        NgButtonVariant.ON_IMAGE -> Color.Black.copy(alpha = 0.56f)
        NgButtonVariant.OUTLINE -> if (surfaceCardAppearance) {
            ngDrawerContentCardColor()
        } else {
            Color(colors.surface)
        }
    }
    val contentColor = if (textAppearance) {
        if (variant == NgButtonVariant.DANGER) Color(colors.error) else primary
    } else when (variant) {
        NgButtonVariant.PRIMARY,
        NgButtonVariant.PRIMARY_LIGHT_CONTENT -> Color(primaryColors.content)
        NgButtonVariant.TONAL -> Color(readingConfirmColors?.content ?: colors.onSurface)
        NgButtonVariant.NEUTRAL -> Color(colors.onSurface)
        NgButtonVariant.DANGER -> Color.White
        NgButtonVariant.ON_IMAGE -> Color.White
        NgButtonVariant.OUTLINE -> primary
    }
    val iconSpacing = if (textAppearance) 4.dp else 6.dp
    Button(
        onClick = onClick,
        modifier = modifier
            .height(buttonHeight)
            .widthIn(min = minimumWidth),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (textAppearance) Color.Transparent else containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.55f)
        ),
        border = if (variant == NgButtonVariant.OUTLINE && !borderlessSurfaceAppearance && !textAppearance) {
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
        contentPadding = contentPadding,
    ) {
        leadingIcon?.let { icon ->
            icon()
            Spacer(Modifier.width(iconSpacing))
        }
        Text(
            text = text,
            fontSize = textSize,
            lineHeight = textLineHeight,
            fontWeight = if (colorPickerConfirm) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = if (colorPickerConfirm) 1.sp else TextUnit.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailingIcon?.let { icon ->
            Spacer(Modifier.width(iconSpacing))
            icon()
        }
    }
}
