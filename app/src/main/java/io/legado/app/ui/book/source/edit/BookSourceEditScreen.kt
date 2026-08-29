package io.legado.app.ui.book.source.edit

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgCodeHighlightMode
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgEditorConfigPanel
import io.legado.app.ui.design.components.compose.NgEditorSelectOption
import io.legado.app.ui.design.components.compose.NgEditorTextTabRow
import io.legado.app.ui.design.components.compose.NgEditorToggleItem
import io.legado.app.ui.design.components.compose.NgEditorTopBar
import io.legado.app.ui.design.components.compose.NgEditorTopBarAction
import io.legado.app.ui.design.components.compose.NgEditorTopBarIconButton
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgFormDensity
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormSelectField
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.rememberNgCodeVisualTransformation
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.data.entities.KeyboardAssist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val EDITOR_TOGGLE_ENABLED = "enabled"
internal const val EDITOR_TOGGLE_EXPLORE = "explore"
internal const val EDITOR_TOGGLE_COOKIE = "cookie"
internal const val EDITOR_TOGGLE_EVENT = "event"
internal const val EDITOR_TOGGLE_CUSTOM = "custom"

@Immutable
internal data class BookSourceEditControls(
    val typeIndex: Int = 0,
    val enabled: Boolean = true,
    val enabledExplore: Boolean = true,
    val enabledCookieJar: Boolean = false,
    val eventListener: Boolean = false,
    val customButton: Boolean = false,
)

@Immutable
internal data class BookSourceEditorSelection(
    val key: String,
    val start: Int,
    val end: Int,
)

@Immutable
internal data class BookSourceKeyboardHelpAction(
    val label: String,
    val value: String,
)

@Immutable
internal data class BookSourceUrlOptionInput(
    val useWebView: Boolean = false,
    val method: String = "",
    val charset: String = "",
    val headers: String = "",
    val body: String = "",
    val type: String = "",
    val retry: String = "",
    val webJs: String = "",
    val js: String = "",
    val bodyJs: String = "",
    val dnsIp: String = "",
)

@Composable
internal fun BookSourceEditScreen(
    controls: BookSourceEditControls,
    selectedTab: Int,
    editEntities: List<EditEntity>,
    sourceRevision: Int,
    fieldValueRevision: Int,
    editEntityMaxLine: Int,
    autoComplete: Boolean,
    focusedField: BookSourceEditorSelection?,
    keyboardAssists: List<KeyboardAssist>,
    keyboardRowCount: Int,
    keyboardHelpActions: List<BookSourceKeyboardHelpAction>,
    onControlsChange: (BookSourceEditControls) -> Unit,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onAction: (Int) -> Unit,
    onPrepareOverflow: () -> Boolean,
    onFieldValueChange: (String, String, Int, Int) -> Unit,
    onFieldFocused: (String, Int, Int) -> Unit,
    onKeyboardHelpAction: (String) -> Unit,
    onOpenKeyboardConfig: () -> Unit,
    onInsertText: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val bookTypes = stringArrayResource(R.array.book_type)
    val typeOptions = remember(bookTypes) {
        bookTypes.mapIndexed { index, label ->
            NgEditorSelectOption(index.toString(), label)
        }
    }
    val tabTitles = listOf(
        stringResource(R.string.source_tab_base),
        stringResource(R.string.source_tab_search),
        stringResource(R.string.source_tab_find),
        stringResource(R.string.source_tab_info),
        stringResource(R.string.source_tab_toc),
        stringResource(R.string.source_tab_content),
    )
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTab, sourceRevision) {
        if (editEntities.isNotEmpty()) listState.scrollToItem(0)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(NgTheme.colors.background))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        BookSourceEditorTopBar(
            autoComplete = autoComplete,
            onBack = onBack,
            onAction = onAction,
            onPrepareOverflow = onPrepareOverflow,
        )
        NgEditorConfigPanel(
            selectTitle = stringResource(R.string.book_type).trimEnd(':', '：'),
            selectedValue = controls.typeIndex.toString(),
            selectOptions = typeOptions,
            firstRowToggles = listOf(
                NgEditorToggleItem(
                    EDITOR_TOGGLE_ENABLED,
                    stringResource(R.string.is_enable),
                    controls.enabled,
                ),
                NgEditorToggleItem(
                    EDITOR_TOGGLE_EXPLORE,
                    stringResource(R.string.discovery),
                    controls.enabledExplore,
                ),
            ),
            secondRowToggles = listOf(
                NgEditorToggleItem(
                    EDITOR_TOGGLE_COOKIE,
                    stringResource(R.string.auto_save_cookie),
                    controls.enabledCookieJar,
                ),
                NgEditorToggleItem(
                    EDITOR_TOGGLE_EVENT,
                    stringResource(R.string.is_event_listener),
                    controls.eventListener,
                ),
                NgEditorToggleItem(
                    EDITOR_TOGGLE_CUSTOM,
                    stringResource(R.string.custom_button),
                    controls.customButton,
                ),
            ),
            onSelect = { value ->
                value.toIntOrNull()?.let { onControlsChange(controls.copy(typeIndex = it)) }
            },
            onToggle = { key, checked ->
                onControlsChange(
                    when (key) {
                        EDITOR_TOGGLE_ENABLED -> controls.copy(enabled = checked)
                        EDITOR_TOGGLE_EXPLORE -> controls.copy(enabledExplore = checked)
                        EDITOR_TOGGLE_COOKIE -> controls.copy(enabledCookieJar = checked)
                        EDITOR_TOGGLE_EVENT -> controls.copy(eventListener = checked)
                        EDITOR_TOGGLE_CUSTOM -> controls.copy(customButton = checked)
                        else -> controls
                    }
                )
            },
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
        )
        NgEditorTextTabRow(
            titles = tabTitles,
            selectedIndex = selectedTab,
            onSelected = onTabSelected,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(
                items = editEntities,
                key = { index, entity -> "$sourceRevision:$selectedTab:$index:${entity.key}" },
            ) { _, entity ->
                BookSourceEditField(
                    editEntity = entity,
                    sourceRevision = sourceRevision,
                    fieldValueRevision = fieldValueRevision,
                    editEntityMaxLine = editEntityMaxLine,
                    focusedField = focusedField,
                    onFieldValueChange = onFieldValueChange,
                    onFieldFocused = onFieldFocused,
                )
            }
        }
        BookSourceKeyboardToolBar(
            assists = keyboardAssists,
            rowCount = keyboardRowCount,
            helpActions = keyboardHelpActions,
            onHelpAction = onKeyboardHelpAction,
            onOpenConfig = onOpenKeyboardConfig,
            onInsertText = onInsertText,
            onUndo = onUndo,
            onRedo = onRedo,
        )
    }
}

@Composable
private fun BookSourceEditorTopBar(
    autoComplete: Boolean,
    onBack: () -> Unit,
    onAction: (Int) -> Unit,
    onPrepareOverflow: () -> Boolean,
) {
    val menuState = remember { NgPopupToggleState() }
    var loginVisible by remember { mutableStateOf(false) }
    val overflowItems = buildList {
        if (loginVisible) {
            add(NgExpandableActionMenuItem(R.id.menu_login, R.string.login, 0))
        }
        add(NgExpandableActionMenuItem(R.id.menu_search, R.string.search, 0))
        add(NgExpandableActionMenuItem(R.id.menu_clear_cookie, R.string.cookie, 0))
        add(
            NgExpandableActionMenuItem(
                R.id.menu_auto_complete,
                R.string.auto_complete,
                0,
                checked = autoComplete,
            )
        )
        add(NgExpandableActionMenuItem(R.id.menu_copy_source, R.string.copy_source, 0))
        add(NgExpandableActionMenuItem(R.id.menu_paste_source, R.string.paste_source, 0))
        add(
            NgExpandableActionMenuItem(
                R.id.menu_set_source_variable,
                R.string.set_source_variable,
                0,
            )
        )
        add(NgExpandableActionMenuItem(R.id.menu_qr_code_camera, R.string.import_by_qr_code, 0))
        add(NgExpandableActionMenuItem(R.id.menu_share_qr, R.string.qr_share, 0))
        add(NgExpandableActionMenuItem(R.id.menu_share_str, R.string.str_share, 0))
        add(NgExpandableActionMenuItem(R.id.menu_log, R.string.log, 0))
        add(
            NgExpandableActionMenuItem(
                R.id.menu_network_log,
                R.string.network_request_log,
                R.drawable.ic_cfg_about,
            )
        )
        add(NgExpandableActionMenuItem(R.id.menu_help, R.string.help, 0))
    }
    NgEditorTopBar(
        title = stringResource(R.string.edit_book_source),
        onBack = onBack,
        actions = listOf(
            NgEditorTopBarAction(
                painterResource(R.drawable.ic_code),
                stringResource(R.string.edit_content),
                { onAction(R.id.menu_fullscreen_edit) },
            ),
            NgEditorTopBarAction(
                painterResource(R.drawable.ic_save),
                stringResource(R.string.action_save),
                { onAction(R.id.menu_save) },
            ),
            NgEditorTopBarAction(
                painterResource(R.drawable.ic_bug_report),
                stringResource(R.string.debug_source),
                { onAction(R.id.menu_debug_source) },
                iconSize = 21.dp,
            ),
        ),
        trailingContent = {
            Box {
                NgEditorTopBarIconButton(
                    icon = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.menu),
                    onClick = {
                        loginVisible = onPrepareOverflow()
                        menuState.onAnchorClick()
                    },
                )
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = overflowItems,
                    onItemClick = { item ->
                        menuState.close()
                        onAction(item.itemId)
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSourceEditField(
    editEntity: EditEntity,
    sourceRevision: Int,
    fieldValueRevision: Int,
    editEntityMaxLine: Int,
    focusedField: BookSourceEditorSelection?,
    onFieldValueChange: (String, String, Int, Int) -> Unit,
    onFieldFocused: (String, Int, Int) -> Unit,
) {
    var value by remember(editEntity.key, sourceRevision) {
        mutableStateOf(
            TextFieldValue(
                text = editEntity.value.orEmpty(),
                selection = TextRange(editEntity.value.orEmpty().length),
            )
        )
    }
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val visualTransformation = rememberNgCodeVisualTransformation(
        mode = NgCodeHighlightMode.SOURCE,
        sourceKey = editEntity.key,
    )
    LaunchedEffect(fieldValueRevision, editEntity.value) {
        val text = editEntity.value.orEmpty()
        if (value.text != text || focusedField?.key == editEntity.key) {
            val selection = focusedField?.takeIf { it.key == editEntity.key }?.let {
                TextRange(
                    it.start.coerceIn(0, text.length),
                    it.end.coerceIn(0, text.length),
                )
            } ?: TextRange(text.length)
            value = TextFieldValue(text = text, selection = selection)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = editEntity.hint,
            color = Color(NgTheme.colors.primary),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = { next ->
                value = next
                onFieldValueChange(
                    editEntity.key,
                    next.text,
                    next.selection.start,
                    next.selection.end,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 36.dp,
                    max = editorMaxHeight(editEntityMaxLine),
                )
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        onFieldFocused(
                            editEntity.key,
                            value.selection.start,
                            value.selection.end,
                        )
                        scope.launch {
                            delay(120L)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
                .padding(vertical = 4.dp),
            textStyle = TextStyle(
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            visualTransformation = visualTransformation,
            minLines = 1,
            maxLines = editEntityMaxLine.coerceAtLeast(1),
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(NgTheme.colors.outline)),
        )
    }
}

private enum class BookSourceKeyboardToolAction {
    HELP,
    UNDO,
    REDO,
    INSERT,
}

private data class BookSourceKeyboardToolItem(
    val id: String,
    val label: String,
    val action: BookSourceKeyboardToolAction,
    val value: String = "",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSourceKeyboardToolBar(
    assists: List<KeyboardAssist>,
    rowCount: Int,
    helpActions: List<BookSourceKeyboardHelpAction>,
    onHelpAction: (String) -> Unit,
    onOpenConfig: () -> Unit,
    onInsertText: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var showHelp by remember { mutableStateOf(false) }
    if (imeVisible) {
        val rows = rowCount.coerceIn(1, 5)
        val toolItems = buildList {
            add(BookSourceKeyboardToolItem("help", "❓", BookSourceKeyboardToolAction.HELP))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                add(BookSourceKeyboardToolItem("undo", "↩️", BookSourceKeyboardToolAction.UNDO))
                add(BookSourceKeyboardToolItem("redo", "↪️", BookSourceKeyboardToolAction.REDO))
            }
            assists.forEach { assist ->
                add(
                    BookSourceKeyboardToolItem(
                        id = "assist:${assist.type}:${assist.key}",
                        label = assist.key,
                        action = BookSourceKeyboardToolAction.INSERT,
                        value = assist.value,
                    )
                )
            }
        }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier
                .fillMaxWidth()
                .height((rows * 38 + 10).dp)
                .background(Color(NgTheme.colors.cardContainer)),
            contentPadding = PaddingValues(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(toolItems, key = BookSourceKeyboardToolItem::id) { item ->
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 44.dp)
                        .background(
                            Color(NgTheme.colors.surfaceContainerHigh),
                            RoundedCornerShape(NgTheme.shapes.smallDp.dp),
                        )
                        .clickable {
                            when (item.action) {
                                BookSourceKeyboardToolAction.HELP -> showHelp = true
                                BookSourceKeyboardToolAction.UNDO -> onUndo()
                                BookSourceKeyboardToolAction.REDO -> onRedo()
                                BookSourceKeyboardToolAction.INSERT -> onInsertText(item.value)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        text = item.label,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    if (showHelp) {
        BookSourceKeyboardHelpDialog(
            actions = helpActions,
            onDismiss = { showHelp = false },
            onOpenConfig = {
                showHelp = false
                onOpenConfig()
            },
            onAction = { action ->
                showHelp = false
                onHelpAction(action)
            },
        )
    }
}

@Composable
private fun BookSourceKeyboardHelpDialog(
    actions: List<BookSourceKeyboardHelpAction>,
    onDismiss: () -> Unit,
    onOpenConfig: () -> Unit,
    onAction: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.help),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                item {
                    BookSourceKeyboardHelpRow(
                        label = stringResource(R.string.assists_key_config),
                        onClick = onOpenConfig,
                    )
                }
                itemsIndexed(actions, key = { _, action -> action.value }) { _, action ->
                    BookSourceKeyboardHelpRow(
                        label = action.label,
                        onClick = { onAction(action.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookSourceKeyboardHelpRow(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 21.sp,
        )
    }
}

private fun editorMaxHeight(maxLines: Int): Dp {
    return if (maxLines >= 999) 20_000.dp else (maxLines * 20 + 16).dp
}

@Composable
internal fun BookSourceExitDialog(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.exit),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.yes),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.no),
                    onClick = onDiscard,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.exit_no_save),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
internal fun BookSourceGroupSelectorDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.group_select),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                itemsIndexed(groups) { _, group ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelected(group) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = group,
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookSourceUrlOptionDialog(
    charsets: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (BookSourceUrlOptionInput) -> Unit,
) {
    var input by remember { mutableStateOf(BookSourceUrlOptionInput()) }
    val arrow = painterResource(R.drawable.ic_arrow_drop_down)
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.url_option),
            variant = NgDialogVariant.FORM_EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(input) },
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NgFormSwitchRow(
                    title = "useWebView",
                    checked = input.useWebView,
                    onCheckedChange = { input = input.copy(useWebView = it) },
                    density = NgFormDensity.COMPACT,
                )
                NgFormSelectField(
                    label = "method",
                    selectedValue = input.method,
                    options = listOf("POST", "GET").map { NgFormSelectOption(it, it) },
                    onValueChange = { input = input.copy(method = it) },
                    arrowIcon = arrow,
                    density = NgFormDensity.COMPACT,
                )
                NgFormSelectField(
                    label = "charset",
                    selectedValue = input.charset,
                    options = charsets.map { NgFormSelectOption(it, it) },
                    onValueChange = { input = input.copy(charset = it) },
                    arrowIcon = arrow,
                    density = NgFormDensity.COMPACT,
                )
                UrlOptionField("headers", input.headers) { input = input.copy(headers = it) }
                UrlOptionField("body", input.body) { input = input.copy(body = it) }
                UrlOptionField("type", input.type) { input = input.copy(type = it) }
                UrlOptionField(
                    label = "retry",
                    value = input.retry,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                ) { input = input.copy(retry = it) }
                UrlOptionField("webJs", input.webJs) { input = input.copy(webJs = it) }
                UrlOptionField("js", input.js) { input = input.copy(js = it) }
                UrlOptionField("bodyJs", input.bodyJs) { input = input.copy(bodyJs = it) }
                UrlOptionField("dnsIp", input.dnsIp) { input = input.copy(dnsIp = it) }
            }
        }
    }
}

@Composable
private fun UrlOptionField(
    label: String,
    value: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChange: (String) -> Unit,
) {
    NgFormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        density = NgFormDensity.COMPACT,
        keyboardOptions = keyboardOptions,
    )
}
