package io.legado.app.ui.rss.source.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.design.components.compose.NgCodeHighlightMode
import io.legado.app.ui.design.components.compose.NgEditorSelectOption
import io.legado.app.ui.design.components.compose.NgEditorTextTabRow
import io.legado.app.ui.design.components.compose.NgEditorToggleItem
import io.legado.app.ui.design.components.compose.NgEditorTopBar
import io.legado.app.ui.design.components.compose.NgEditorTopBarAction
import io.legado.app.ui.design.components.compose.NgEditorTopBarIconButton
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgRssEditorConfigPanel
import io.legado.app.ui.design.components.compose.rememberNgCodeVisualTransformation
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay

@Immutable
internal data class RssSourceEditField(
    val key: String,
    val label: String,
    val value: String,
    val boolean: Boolean = false
)

internal sealed interface RssSourceEditAction {
    data object Undo : RssSourceEditAction
    data object Redo : RssSourceEditAction
    data object KeyboardConfig : RssSourceEditAction
    data class InsertText(val text: String) : RssSourceEditAction
    data class EditText(val key: String, val value: String, val start: Int, val end: Int) : RssSourceEditAction
    data object Back : RssSourceEditAction
    data object Save : RssSourceEditAction
    data object Debug : RssSourceEditAction
    data object Login : RssSourceEditAction
    data object SetVariable : RssSourceEditAction
    data object ClearCookie : RssSourceEditAction
    data object Copy : RssSourceEditAction
    data object Paste : RssSourceEditAction
    data object ImportQr : RssSourceEditAction
    data object ShareText : RssSourceEditAction
    data object ShareQr : RssSourceEditAction
    data object AppLog : RssSourceEditAction
    data object NetworkLog : RssSourceEditAction
    data object Help : RssSourceEditAction
    data object InsertUrlOption : RssSourceEditAction
    data object JsHelp : RssSourceEditAction
    data object RegexHelp : RssSourceEditAction
    data object SelectFile : RssSourceEditAction
    data class SelectTab(val index: Int) : RssSourceEditAction
    data class UpdateField(val key: String, val value: String) : RssSourceEditAction
    data class FocusField(
        val key: String,
        val selectionStart: Int,
        val selectionEnd: Int
    ) : RssSourceEditAction
    data class ExpandField(val key: String, val label: String) : RssSourceEditAction
    data class UpdateSource(val source: RssSource) : RssSourceEditAction
    data class AutoCompleteChanged(val enabled: Boolean) : RssSourceEditAction
}

@Composable
internal fun RssSourceEditScreen(
    source: RssSource,
    selectedTab: Int,
    autoComplete: Boolean,
    sourceRevision: Int,
    fieldValueRevision: Int,
    focusedField: RssEditorSelection?,
    draftTextValues: Map<String, String>,
    editEntityMaxLine: Int,
    keyboardAssists: List<KeyboardAssist>,
    keyboardRowCount: Int,
    onAction: (RssSourceEditAction) -> Unit,
) {
    val fields = sourceEditFields(source, selectedTab).map { field ->
        draftTextValues[field.key]?.let { field.copy(value = it) } ?: field
    }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val focusManager = LocalFocusManager.current
    val listStates = List(4) { rememberLazyListState() }
    LaunchedEffect(sourceRevision) {
        listStates.forEach { it.scrollToItem(0) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(NgTheme.colors.background))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        RssSourceEditorTopBar(
            source = source,
            autoComplete = autoComplete,
            onExpand = {
                val field = fields.firstOrNull { it.key == focusedField?.key }
                onAction(RssSourceEditAction.ExpandField(field?.key.orEmpty(), field?.label.orEmpty()))
            },
            onAction = onAction,
        )
        if (!imeVisible) {
            SourceGlobalOptions(source, onAction)
        }
        NgEditorTextTabRow(
            titles = listOf(
                stringResource(R.string.source_tab_base),
                stringResource(R.string.source_tab_start),
                stringResource(R.string.source_tab_list),
                stringResource(R.string.source_tab_web_view),
            ),
            selectedIndex = selectedTab,
            onSelected = {
                if (it != selectedTab) {
                    focusManager.clearFocus()
                    onAction(RssSourceEditAction.SelectTab(it))
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listStates[selectedTab],
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (selectedTab == 3) {
                item(key = "web-options") {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        fields.filter { it.boolean }.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { field ->
                                    RssEditorCheckBox(field, onAction, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            items(fields.filterNot { it.boolean }, key = { "$sourceRevision:${it.key}" }) { field ->
                RssSourceEditorTextField(
                    field = field,
                    sourceRevision = sourceRevision,
                    fieldValueRevision = fieldValueRevision,
                    focusedField = focusedField,
                    maxLines = editEntityMaxLine,
                    imeVisible = imeVisible,
                    onAction = onAction,
                )
            }
        }
        RssSourceKeyboardToolBar(keyboardAssists, keyboardRowCount, imeVisible, onAction)
    }
}

@Composable
private fun RssSourceEditorTopBar(
    source: RssSource,
    autoComplete: Boolean,
    onExpand: () -> Unit,
    onAction: (RssSourceEditAction) -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    val overflowItems = buildList {
        if (!source.loginUrl.isNullOrBlank()) {
            add(NgExpandableActionMenuItem(R.id.menu_login, R.string.login, 0))
        }
        add(NgExpandableActionMenuItem(R.id.menu_auto_complete, R.string.auto_complete, 0, checked = autoComplete))
        add(NgExpandableActionMenuItem(R.id.menu_clear_cookie, R.string.cookie, 0))
        add(NgExpandableActionMenuItem(R.id.menu_set_source_variable, R.string.set_source_variable, 0))
        add(NgExpandableActionMenuItem(R.id.menu_copy_source, R.string.copy_source, 0))
        add(NgExpandableActionMenuItem(R.id.menu_paste_source, R.string.paste_source, 0))
        add(NgExpandableActionMenuItem(R.id.menu_qr_code_camera, R.string.import_by_qr_code, 0))
        add(NgExpandableActionMenuItem(R.id.menu_share_str, R.string.str_share, 0))
        add(NgExpandableActionMenuItem(R.id.menu_share_qr, R.string.qr_share, 0))
        add(NgExpandableActionMenuItem(R.id.menu_log, R.string.log, 0))
        add(NgExpandableActionMenuItem(R.id.menu_network_log, R.string.network_request_log, 0))
        add(NgExpandableActionMenuItem(R.id.menu_help, R.string.help, 0))
    }
    NgEditorTopBar(
        title = stringResource(R.string.rss_source_edit),
        onBack = { onAction(RssSourceEditAction.Back) },
        actions = listOf(
            NgEditorTopBarAction(painterResource(R.drawable.ic_code), stringResource(R.string.edit_content), onExpand),
            NgEditorTopBarAction(painterResource(R.drawable.ic_save), stringResource(R.string.action_save), { onAction(RssSourceEditAction.Save) }),
            NgEditorTopBarAction(painterResource(R.drawable.ic_bug_report), stringResource(R.string.debug_source), { onAction(RssSourceEditAction.Debug) }, iconSize = 21.dp),
        ),
        trailingContent = {
            Box {
                NgEditorTopBarIconButton(
                    icon = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.menu),
                    onClick = menuState::onAnchorClick,
                )
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = overflowItems,
                    onItemClick = { item ->
                        menuState.close()
                        onAction(when (item.itemId) {
                            R.id.menu_login -> RssSourceEditAction.Login
                            R.id.menu_auto_complete -> RssSourceEditAction.AutoCompleteChanged(!autoComplete)
                            R.id.menu_clear_cookie -> RssSourceEditAction.ClearCookie
                            R.id.menu_set_source_variable -> RssSourceEditAction.SetVariable
                            R.id.menu_copy_source -> RssSourceEditAction.Copy
                            R.id.menu_paste_source -> RssSourceEditAction.Paste
                            R.id.menu_qr_code_camera -> RssSourceEditAction.ImportQr
                            R.id.menu_share_str -> RssSourceEditAction.ShareText
                            R.id.menu_share_qr -> RssSourceEditAction.ShareQr
                            R.id.menu_log -> RssSourceEditAction.AppLog
                            R.id.menu_network_log -> RssSourceEditAction.NetworkLog
                            else -> RssSourceEditAction.Help
                        })
                    },
                )
            }
        },
    )
}

@Composable
private fun SourceGlobalOptions(source: RssSource, onAction: (RssSourceEditAction) -> Unit) {
    val sourceTypes = stringArrayResource(R.array.rss_type)
    val layoutTypes = stringArrayResource(R.array.layout_type)
    NgRssEditorConfigPanel(
        toggles = listOf(
            NgEditorToggleItem("enabled", stringResource(R.string.is_enable), source.enabled),
            NgEditorToggleItem("singleUrl", stringResource(R.string.single_url), source.singleUrl),
            NgEditorToggleItem("enabledCookieJar", stringResource(R.string.auto_save_cookie), source.enabledCookieJar == true),
            NgEditorToggleItem("preload", stringResource(R.string.enable_preload), source.preload),
        ),
        typeTitle = stringResource(R.string.book_type).trimEnd(':', '：'),
        typeValue = source.type.coerceIn(sourceTypes.indices).toString(),
        typeOptions = sourceTypes.mapIndexed { index, text -> NgEditorSelectOption(index.toString(), text) },
        styleTitle = stringResource(R.string.layout_type).trimEnd(':', '：'),
        styleValue = source.articleStyle.coerceIn(layoutTypes.indices).toString(),
        styleOptions = layoutTypes.mapIndexed { index, text -> NgEditorSelectOption(index.toString(), text) },
        onTypeSelected = { onAction(RssSourceEditAction.UpdateSource(source.copy(type = it.toInt()))) },
        onStyleSelected = { onAction(RssSourceEditAction.UpdateSource(source.copy(articleStyle = it.toInt()))) },
        onToggle = { key, checked ->
            onAction(RssSourceEditAction.UpdateSource(when (key) {
                "enabled" -> source.copy(enabled = checked)
                "singleUrl" -> source.copy(singleUrl = checked)
                "enabledCookieJar" -> source.copy(enabledCookieJar = checked)
                else -> source.copy(preload = checked)
            }))
        },
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun RssEditorCheckBox(
    field: RssSourceEditField,
    onAction: (RssSourceEditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 44.dp).toggleable(
            value = field.value.toBoolean(),
            role = Role.Checkbox,
            onValueChange = { onAction(RssSourceEditAction.UpdateField(field.key, it.toString())) },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = field.value.toBoolean(),
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Color(NgTheme.colors.primary)),
        )
        Text(field.label, fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssSourceEditorTextField(
    field: RssSourceEditField,
    sourceRevision: Int,
    fieldValueRevision: Int,
    focusedField: RssEditorSelection?,
    maxLines: Int,
    imeVisible: Boolean,
    onAction: (RssSourceEditAction) -> Unit,
) {
    var value by remember(field.key, sourceRevision) {
        mutableStateOf(TextFieldValue(field.value))
    }
    var focused by remember { mutableStateOf(false) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val bringIntoView = remember { BringIntoViewRequester() }
    val transformation = rememberNgCodeVisualTransformation(NgCodeHighlightMode.SOURCE, field.key)
    val paddingTop = with(LocalDensity.current) { 4.dp.toPx() }
    LaunchedEffect(sourceRevision, fieldValueRevision) {
        val selection = focusedField?.takeIf { it.key == field.key }?.let {
            TextRange(it.start.coerceIn(0, field.value.length), it.end.coerceIn(0, field.value.length))
        } ?: TextRange(value.selection.start.coerceIn(0, field.value.length), value.selection.end.coerceIn(0, field.value.length))
        if (value.text != field.value || value.selection != selection) {
            value = TextFieldValue(field.value, selection)
        }
    }
    LaunchedEffect(focused, imeVisible, value.selection, textLayout) {
        if (focused && imeVisible) {
            delay(120L)
            val layout = textLayout
            val cursor = layout?.getCursorRect(value.selection.end.coerceIn(0, layout.layoutInput.text.length))
            bringIntoView.bringIntoView(cursor?.translate(0f, paddingTop))
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(field.label, color = Color(NgTheme.colors.primary), fontSize = 12.sp, lineHeight = 16.sp)
        BasicTextField(
            value = value,
            onValueChange = { next ->
                value = next
                onAction(RssSourceEditAction.EditText(field.key, next.text, next.selection.start, next.selection.end))
            },
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 36.dp, max = if (maxLines >= 999) 20_000.dp else (maxLines.coerceAtLeast(1) * 20 + 16).dp)
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onAction(RssSourceEditAction.FocusField(field.key, value.selection.start, value.selection.end))
                }
                .padding(vertical = 4.dp),
            textStyle = TextStyle(fontFamily = NgTheme.fontFamily, color = Color(NgTheme.colors.onSurface), fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            visualTransformation = transformation,
            onTextLayout = { textLayout = it },
            minLines = 1,
            maxLines = maxLines.coerceAtLeast(1),
        )
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(NgTheme.colors.outline)))
    }
}

@Composable
private fun sourceEditFields(source: RssSource, tab: Int): List<RssSourceEditField> {
    return when (tab) {
        1 -> listOf(
            RssSourceEditField("startHtml", stringResource(R.string.r_startHtml), source.startHtml.orEmpty()),
            RssSourceEditField("startStyle", stringResource(R.string.r_startStyle), source.startStyle.orEmpty()),
            RssSourceEditField("startJs", stringResource(R.string.r_startJs), source.startJs.orEmpty()),
            RssSourceEditField("preloadJs", stringResource(R.string.r_preloadJs), source.preloadJs.orEmpty())
        )
        2 -> listOf(
            RssSourceEditField("ruleArticles", stringResource(R.string.r_articles), source.ruleArticles.orEmpty()),
            RssSourceEditField("ruleNextPage", stringResource(R.string.r_next), source.ruleNextPage.orEmpty()),
            RssSourceEditField("ruleTitle", stringResource(R.string.r_title), source.ruleTitle.orEmpty()),
            RssSourceEditField("rulePubDate", stringResource(R.string.r_date), source.rulePubDate.orEmpty()),
            RssSourceEditField("ruleDescription", stringResource(R.string.r_description), source.ruleDescription.orEmpty()),
            RssSourceEditField("ruleImage", stringResource(R.string.r_image), source.ruleImage.orEmpty()),
            RssSourceEditField("ruleLink", stringResource(R.string.r_link), source.ruleLink.orEmpty())
        )
        3 -> listOf(
            RssSourceEditField("enableJs", stringResource(R.string.enable_js), source.enableJs.toString(), true),
            RssSourceEditField("loadWithBaseUrl", stringResource(R.string.load_with_base_url), source.loadWithBaseUrl.toString(), true),
            RssSourceEditField("showWebLog", stringResource(R.string.load_with_web_log), source.showWebLog.toString(), true),
            RssSourceEditField("cacheFirst", stringResource(R.string.cache_first), source.cacheFirst.toString(), true),
            RssSourceEditField("ruleContent", stringResource(R.string.r_content), source.ruleContent.orEmpty()),
            RssSourceEditField("style", stringResource(R.string.r_style), source.style.orEmpty()),
            RssSourceEditField("injectJs", stringResource(R.string.r_inject_js), source.injectJs.orEmpty()),
            RssSourceEditField("contentWhitelist", stringResource(R.string.c_whitelist), source.contentWhitelist.orEmpty()),
            RssSourceEditField("contentBlacklist", stringResource(R.string.c_blacklist), source.contentBlacklist.orEmpty()),
            RssSourceEditField(
                "shouldOverrideUrlLoading",
                "url跳转拦截",
                source.shouldOverrideUrlLoading.orEmpty()
            )
        )
        else -> listOf(
            RssSourceEditField("sourceName", stringResource(R.string.source_name), source.sourceName),
            RssSourceEditField("sourceUrl", stringResource(R.string.source_url), source.sourceUrl),
            RssSourceEditField("sourceIcon", stringResource(R.string.source_icon), source.sourceIcon),
            RssSourceEditField("sourceGroup", stringResource(R.string.source_group), source.sourceGroup.orEmpty()),
            RssSourceEditField("sourceComment", stringResource(R.string.comment), source.sourceComment.orEmpty()),
            RssSourceEditField("searchUrl", stringResource(R.string.r_search_url), source.searchUrl.orEmpty()),
            RssSourceEditField("sortUrl", stringResource(R.string.sort_url), source.sortUrl.orEmpty()),
            RssSourceEditField("loginUrl", stringResource(R.string.login_url), source.loginUrl.orEmpty()),
            RssSourceEditField("loginUi", stringResource(R.string.login_ui), source.loginUi.orEmpty()),
            RssSourceEditField("loginCheckJs", stringResource(R.string.login_check_js), source.loginCheckJs.orEmpty()),
            RssSourceEditField("coverDecodeJs", stringResource(R.string.cover_decode_js), source.coverDecodeJs.orEmpty()),
            RssSourceEditField("header", stringResource(R.string.source_http_header), source.header.orEmpty()),
            RssSourceEditField("variableComment", stringResource(R.string.variable_comment), source.variableComment.orEmpty()),
            RssSourceEditField("concurrentRate", stringResource(R.string.concurrent_rate), source.concurrentRate.orEmpty()),
            RssSourceEditField("jsLib", "jsLib", source.jsLib.orEmpty())
        )
    }
}

@Composable
internal fun RssSourceExitDialog(
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exit)) },
        text = { Text(stringResource(R.string.exit_no_save)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.no)) }
        }
    )
}
