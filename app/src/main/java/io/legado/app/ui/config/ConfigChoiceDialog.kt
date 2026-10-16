package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDialogValueRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgFormMultilineFieldVariant
import io.legado.app.ui.design.components.compose.NgCodeHighlightMode
import io.legado.app.ui.design.components.compose.rememberNgCodeVisualTransformation
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ConfigChoiceOption(
    val label: String,
    val value: String,
)

@Composable
internal fun ConfigChoiceDialog(
    title: String?,
    options: List<ConfigChoiceOption>,
    selectedValue: String? = null,
    onDismissRequest: () -> Unit,
    onSelected: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {},
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((options.size * 56).coerceAtMost(420).dp),
            ) {
                itemsIndexed(
                    items = options,
                    key = { _, option -> option.value },
                ) { index, option ->
                    NgDialogValueRow(
                        title = option.label,
                        value = "",
                        onClick = { onSelected(option.value) },
                        trailingContent = if (option.value == selectedValue) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ng_ic_popup_selected),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(NgTheme.colors.onSurface),
                                )
                            }
                        } else {
                            null
                        },
                    )
                    if (index < options.lastIndex) NgDialogDivider()
                }
            }
        }
    }
}

@Composable
internal fun ConfigNumberPickerDialog(
    title: String,
    minValue: Int,
    maxValue: Int,
    initialValue: Int,
    decimalMode: Boolean = false,
    defaultValue: Int? = null,
    defaultText: String? = null,
    cancelText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onValueSelected: (Int) -> Unit,
) {
    var selectedValue by remember(minValue, maxValue, initialValue) {
        mutableIntStateOf(initialValue.coerceIn(minValue, maxValue))
    }
    // 手动输入与滚轮共用同一个值:输入框文本单独持有,避免与滚轮的每次吸附互相覆盖。
    var manualText by remember(minValue, maxValue, initialValue) {
        mutableStateOf(formatNumberValue(initialValue, decimalMode))
    }
    val coerceValue: (Int) -> Int = { it.coerceIn(minValue, maxValue) }
    // 正在键盘输入时不能回写文本:中途的值(如刚清空准备重打)往往越界,
    // 一 clamp 就会把用户敲的字换掉。
    var editing by remember { mutableStateOf(false) }
    val applyManualText: (String) -> Unit = { text ->
        manualText = text
        parseNumberValue(text, decimalMode)?.let { selectedValue = coerceValue(it) }
    }
    // 滚轮选择变化时回写输入框;若当前文本已经表示同一个值则不动,免得打断输入。
    LaunchedEffect(selectedValue, decimalMode, editing) {
        if (!editing) {
            val shown = parseNumberValue(manualText, decimalMode)
            if (shown != selectedValue) {
                manualText = formatNumberValue(selectedValue, decimalMode)
            }
        }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 420.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {
                if (defaultValue != null && defaultText != null) {
                    NgDialogTextActionButton(
                        text = defaultText,
                        onClick = { onValueSelected(defaultValue.coerceIn(minValue, maxValue)) },
                        secondary = true,
                    )
                    Spacer(Modifier.weight(1f))
                }
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    // 手动输入未失焦时直接确定:以输入框当前文本为准,否则取滚轮值。
                    onClick = {
                        onValueSelected(
                            parseNumberValue(manualText, decimalMode)
                                ?.let(coerceValue) ?: selectedValue
                        )
                    },
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 选择与输入合并成一个控件:输入态就落在滚轮中间那一格上,
                // 不再单独占一行输入框。两个控件叠放时用户要在两处找同一个值。
                ConfigNumberWheel(
                    minValue = minValue,
                    maxValue = maxValue,
                    selectedValue = selectedValue,
                    decimalMode = decimalMode,
                    editingText = manualText,
                    onEditingTextChange = applyManualText,
                    onSelectedValueChange = { selectedValue = it },
                    editing = editing,
                    onEditingChange = { editing = it },
                    onEditingFinished = {
                        // 输入结束把文本收敛回合法值:中途可能停在一个越界或不可解析的串上。
                        val parsed = parseNumberValue(manualText, decimalMode)
                            ?.let(coerceValue) ?: selectedValue
                        manualText = formatNumberValue(parsed, decimalMode)
                    },
                )
                Text(
                    text = stringResource(R.string.cache_number_edit_hint),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        R.string.manual_input_range,
                        formatNumberValue(minValue, decimalMode),
                        formatNumberValue(maxValue, decimalMode),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 滚轮与输入框共用的取值/格式化:decimalMode 下内部值放大 10 倍存储。 */
private fun formatNumberValue(value: Int, decimalMode: Boolean): String =
    if (decimalMode) (value / 10.0).toString() else value.toString()

private fun parseNumberValue(text: String, decimalMode: Boolean): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val number = trimmed.toDoubleOrNull() ?: return null
    return if (decimalMode) (number * 10).roundToInt() else number.roundToInt()
}

@Composable
private fun ConfigNumberWheel(
    minValue: Int,
    maxValue: Int,
    selectedValue: Int,
    decimalMode: Boolean,
    editingText: String,
    onEditingTextChange: (String) -> Unit,
    onSelectedValueChange: (Int) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onEditingFinished: () -> Unit,
) {
    val itemCount = maxValue - minValue + 1
    val initialIndex = (selectedValue - minValue).coerceIn(0, itemCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    // onFocusChanged 在组合时会先发一次未聚焦,若直接据此收尾会在点开后立刻退出编辑。
    // 必须等真正拿到过焦点,才把「失焦」当作输入结束。
    var hasFocused by remember { mutableStateOf(false) }
    // 编辑中的那一格要钉住:输入会改变 selectedValue,若按 selectedValue 找行,
    // 输入框会在打字过程中跳到别的行上。
    var editingValue by remember { mutableStateOf<Int?>(null) }
    val centeredValue by remember(listState, minValue, itemCount) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo
                .asSequence()
                .filter { it.index in WHEEL_SPACER_ROWS until itemCount + WHEEL_SPACER_ROWS }
                .minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }
                ?.index
                ?.minus(WHEEL_SPACER_ROWS)
                ?.plus(minValue)
        }
    }
    LaunchedEffect(centeredValue) {
        centeredValue?.let(onSelectedValueChange)
    }
    // 输入结束后把滚轮滚到刚提交的值上:合并成一个控件后,打字可能让值远离当前居中行,
    // 不滚过去的话没有任何一行的值是 selectedValue,高亮格会空掉。
    LaunchedEffect(editing) {
        if (!editing) {
            listState.animateScrollToItem(selectedValue - minValue)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_ROW_HEIGHT * WHEEL_VISIBLE_ROWS),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ROW_HEIGHT)
                .padding(horizontal = 8.dp)
                .background(
                    Color(NgTheme.colors.primary).copy(alpha = 0.09f),
                    RoundedCornerShape(12.dp),
                ),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                count = itemCount + WHEEL_SPACER_ROWS * 2,
                key = { virtualIndex -> "number-wheel-$virtualIndex" },
            ) { virtualIndex ->
                if (
                    virtualIndex < WHEEL_SPACER_ROWS ||
                    virtualIndex >= itemCount + WHEEL_SPACER_ROWS
                ) {
                    Spacer(Modifier.height(WHEEL_ROW_HEIGHT))
                } else {
                    val value = minValue + virtualIndex - WHEEL_SPACER_ROWS
                    // 编辑中按钉住的那一格渲染编辑器,其余时候按当前居中值。
                    val editingHere = editing && editingValue == value
                    val selected = editingHere || value == selectedValue
                    if (selected) {
                        // 选中格:不编辑时是一枚可点的描边数字,点下后原地变输入框。
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(WHEEL_ROW_HEIGHT)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (editingHere) {
                                BasicTextField(
                                    value = editingText,
                                    onValueChange = onEditingTextChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(WHEEL_ROW_HEIGHT - 8.dp)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                hasFocused = true
                                            } else if (hasFocused && editing) {
                                                editingValue = null
                                                onEditingChange(false)
                                                onEditingFinished()
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    textStyle = TextStyle(
                                        color = Color(NgTheme.colors.primary),
                                        fontSize = 19.sp,
                                        lineHeight = 23.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (decimalMode) {
                                            KeyboardType.Decimal
                                        } else {
                                            KeyboardType.Number
                                        },
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
                                )
                                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(WHEEL_ROW_HEIGHT - 8.dp)
                                        .border(
                                            width = 1.4.dp,
                                            color = Color(NgTheme.colors.primary)
                                                .copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            // 每一轮编辑都要重新等一次聚焦,否则上一轮留下的
                                            // hasFocused 会让这一轮的首次「未聚焦」直接收尾。
                                            hasFocused = false
                                            editingValue = value
                                            onEditingChange(true)
                                            scope.launch {
                                                listState.animateScrollToItem(value - minValue)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = formatNumberValue(value, decimalMode),
                                        color = Color(NgTheme.colors.primary),
                                        fontSize = 19.sp,
                                        lineHeight = 23.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = formatNumberValue(value, decimalMode),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(WHEEL_ROW_HEIGHT)
                                .clickable {
                                    scope.launch {
                                        listState.animateScrollToItem(value - minValue)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 16.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfigConfirmationDialog(
    title: String,
    message: String,
    cancelText: String,
    confirmText: String,
    danger: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = onConfirm,
                    danger = danger,
                )
            },
        ) {
            Text(
                text = message,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
internal fun ConfigTextEditorDialog(
    title: String,
    initialValue: String,
    cancelText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = { onConfirm(value) },
                )
            },
        ) {
            NgFormField(
                label = title,
                value = value,
                onValueChange = { value = it },
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                autoFocus = true,
            )
        }
    }
}

@Composable
internal fun ConfigJsonEditorDialog(
    title: String,
    label: String,
    initialValue: String,
    cancelText: String,
    confirmText: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = title,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 560.dp),
            variant = NgDialogVariant.EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = cancelText,
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = confirmText,
                    onClick = { onConfirm(value) },
                )
            },
        ) {
            NgFormMultilineField(
                value = value,
                onValueChange = { value = it },
                label = label,
                minHeight = 160.dp,
                maxHeight = 300.dp,
                minLines = 7,
                maxLines = 14,
                visualTransformation = rememberNgCodeVisualTransformation(
                    mode = NgCodeHighlightMode.DEFAULT,
                ),
                variant = NgFormMultilineFieldVariant.DIALOG_UNDERLINE,
            )
        }
    }
}

private val WHEEL_ROW_HEIGHT = 44.dp
private const val WHEEL_VISIBLE_ROWS = 5
private const val WHEEL_SPACER_ROWS = 2
