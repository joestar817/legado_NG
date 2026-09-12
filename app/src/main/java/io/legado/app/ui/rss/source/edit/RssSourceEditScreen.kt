package io.legado.app.ui.rss.source.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssToolbarAction

@Immutable
internal data class RssSourceEditField(
    val key: String,
    val label: String,
    val value: String,
    val boolean: Boolean = false
)

internal sealed interface RssSourceEditAction {
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
    onAction: (RssSourceEditAction) -> Unit
) {
    val editFields = sourceEditFields(source, selectedTab)
    val actions = buildList {
        add(RssToolbarAction(R.id.menu_save, R.string.action_save, R.drawable.ic_save))
        add(
            RssToolbarAction(
                R.id.menu_debug_source,
                R.string.debug_source,
                R.drawable.ic_bug_report
            )
        )
        if (source.loginUrl?.isNotBlank() == true) {
            add(RssToolbarAction(R.id.menu_login, R.string.login, R.drawable.ic_lock_outline))
        }
        add(
            RssToolbarAction(
                R.id.menu_set_source_variable,
                R.string.set_source_variable,
                R.drawable.ic_code
            )
        )
        add(
            RssToolbarAction(
                R.id.menu_clear_cookie,
                R.string.cookie,
                R.drawable.ic_clear
            )
        )
        add(RssToolbarAction(R.id.menu_copy_source, R.string.copy_source, R.drawable.ic_copy))
        add(RssToolbarAction(R.id.menu_paste_source, R.string.paste_source, R.drawable.ic_paste))
        add(
            RssToolbarAction(
                R.id.menu_qr_code_camera,
                R.string.import_by_qr_code,
                R.drawable.ic_scan
            )
        )
        add(RssToolbarAction(R.id.menu_share_str, R.string.str_share, R.drawable.ic_share))
        add(RssToolbarAction(R.id.menu_share_qr, R.string.qr_share, R.drawable.ic_qr_code))
        add(RssToolbarAction(R.id.menu_log, R.string.log, R.drawable.ic_history))
        add(
            RssToolbarAction(
                R.id.menu_network_log,
                R.string.network_request_log,
                R.drawable.ic_web_outline
            )
        )
        add(RssToolbarAction(R.id.menu_help, R.string.help, R.drawable.ic_help))
    }
    RssPageScaffold(
        title = stringResource(R.string.rss_source_edit),
        onBack = { onAction(RssSourceEditAction.Back) },
        actions = actions,
        onAction = { id ->
            onAction(
                when (id) {
                    R.id.menu_save -> RssSourceEditAction.Save
                    R.id.menu_debug_source -> RssSourceEditAction.Debug
                    R.id.menu_login -> RssSourceEditAction.Login
                    R.id.menu_set_source_variable -> RssSourceEditAction.SetVariable
                    R.id.menu_clear_cookie -> RssSourceEditAction.ClearCookie
                    R.id.menu_copy_source -> RssSourceEditAction.Copy
                    R.id.menu_paste_source -> RssSourceEditAction.Paste
                    R.id.menu_qr_code_camera -> RssSourceEditAction.ImportQr
                    R.id.menu_share_str -> RssSourceEditAction.ShareText
                    R.id.menu_share_qr -> RssSourceEditAction.ShareQr
                    R.id.menu_log -> RssSourceEditAction.AppLog
                    R.id.menu_network_log -> RssSourceEditAction.NetworkLog
                    else -> RssSourceEditAction.Help
                }
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    top = 12.dp,
                    end = 14.dp,
                    bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SourceGlobalOptions(
                        source = source,
                        autoComplete = autoComplete,
                        onSourceChange = {
                            onAction(RssSourceEditAction.UpdateSource(it))
                        },
                        onAutoCompleteChange = {
                            onAction(RssSourceEditAction.AutoCompleteChanged(it))
                        }
                    )
                }
                item {
                    SourceEditTabs(selectedTab) {
                        onAction(RssSourceEditAction.SelectTab(it))
                    }
                }
                item {
                    SourceEditFieldsGroup(
                        fields = editFields,
                        selectedTab = selectedTab,
                        onAction = onAction
                    )
                }
            }
            SourceEditorHelpers(onAction)
        }
    }
}

@Composable
private fun SourceGlobalOptions(
    source: RssSource,
    autoComplete: Boolean,
    onSourceChange: (RssSource) -> Unit,
    onAutoCompleteChange: (Boolean) -> Unit
) {
    val sourceTypes = stringArrayResource(R.array.rss_type)
    val layoutTypes = stringArrayResource(R.array.layout_type)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NgFormGroup(title = stringResource(R.string.rss_source_general_settings)) {
            NgFormSwitchSettingRow(
                title = stringResource(R.string.is_enable),
                checked = source.enabled,
                onCheckedChange = { onSourceChange(source.copy(enabled = it)) }
            )
            NgFormGroupDivider()
            NgFormSwitchSettingRow(
                title = stringResource(R.string.single_url),
                checked = source.singleUrl,
                onCheckedChange = { onSourceChange(source.copy(singleUrl = it)) }
            )
            NgFormGroupDivider()
            NgFormSwitchSettingRow(
                title = stringResource(R.string.auto_save_cookie),
                checked = source.enabledCookieJar == true,
                onCheckedChange = { onSourceChange(source.copy(enabledCookieJar = it)) }
            )
            NgFormGroupDivider()
            NgFormSwitchSettingRow(
                title = stringResource(R.string.enable_preload),
                checked = source.preload,
                onCheckedChange = { onSourceChange(source.copy(preload = it)) }
            )
            NgFormGroupDivider()
            NgFormSwitchSettingRow(
                title = stringResource(R.string.auto_complete),
                checked = autoComplete,
                onCheckedChange = onAutoCompleteChange
            )
        }
        NgFormGroup(title = stringResource(R.string.rss_source_display_settings)) {
            NgFormSelectRow(
                title = stringResource(R.string.book_type),
                selectedValue = source.type.coerceIn(sourceTypes.indices).toString(),
                options = sourceTypes.mapIndexed { index, value ->
                    NgFormSelectOption(value, index.toString())
                },
                onValueChange = {
                    onSourceChange(source.copy(type = it.toIntOrNull() ?: 0))
                },
                arrowIcon = painterResource(R.drawable.ic_arrow_drop_down),
                menuVariant = NgFormSelectMenuVariant.END_ANCHORED_COMPACT
            )
            NgFormGroupDivider()
            NgFormSelectRow(
                title = stringResource(R.string.layout_type),
                selectedValue = source.articleStyle.coerceIn(layoutTypes.indices).toString(),
                options = layoutTypes.mapIndexed { index, value ->
                    NgFormSelectOption(value, index.toString())
                },
                onValueChange = {
                    onSourceChange(source.copy(articleStyle = it.toIntOrNull() ?: 0))
                },
                arrowIcon = painterResource(R.drawable.ic_arrow_drop_down),
                menuVariant = NgFormSelectMenuVariant.END_ANCHORED_COMPACT
            )
        }
    }
}

@Composable
private fun SourceEditTabs(selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf(
        stringResource(R.string.source_tab_base),
        stringResource(R.string.source_tab_start),
        stringResource(R.string.source_tab_list),
        stringResource(R.string.source_tab_web_view)
    )
    NgFloatingTabBar(
        items = titles.map { NgFloatingTabSpec(text = it) },
        selectedIndex = selected,
        onTabSelected = onSelect
    )
}

@Composable
private fun SourceEditFieldsGroup(
    fields: List<RssSourceEditField>,
    selectedTab: Int,
    onAction: (RssSourceEditAction) -> Unit
) {
    val titles = listOf(
        stringResource(R.string.source_tab_base),
        stringResource(R.string.source_tab_start),
        stringResource(R.string.source_tab_list),
        stringResource(R.string.source_tab_web_view)
    )
    NgFormGroup(title = titles[selectedTab.coerceIn(titles.indices)]) {
        fields.forEachIndexed { index, field ->
            if (field.boolean) {
                NgFormSwitchSettingRow(
                    title = field.label,
                    checked = field.value.toBoolean(),
                    onCheckedChange = {
                        onAction(
                            RssSourceEditAction.UpdateField(
                                field.key,
                                it.toString()
                            )
                        )
                    }
                )
            } else {
                RssSourceEditorTextField(
                    field = field,
                    onAction = onAction
                )
            }
            if (index < fields.lastIndex) {
                NgFormGroupDivider()
            }
        }
    }
}

@Composable
private fun RssSourceEditorTextField(
    field: RssSourceEditField,
    onAction: (RssSourceEditAction) -> Unit
) {
    var fieldValue by remember(field.key) {
        mutableStateOf(TextFieldValue(field.value))
    }
    LaunchedEffect(field.value) {
        if (fieldValue.text != field.value) {
            fieldValue = TextFieldValue(
                text = field.value,
                selection = TextRange(field.value.length)
            )
        }
    }
    val compact = field.key in compactSourceFields
    val fieldShape = RoundedCornerShape(NgTheme.shapes.smallDp.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = field.label,
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onAction(RssSourceEditAction.ExpandField(field.key, field.label))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_code),
                    contentDescription = stringResource(R.string.edit_content),
                    tint = Color(NgTheme.colors.primary),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { next ->
                fieldValue = next
                onAction(RssSourceEditAction.UpdateField(field.key, next.text))
                onAction(
                    RssSourceEditAction.FocusField(
                        field.key,
                        next.selection.start,
                        next.selection.end
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(
                    min = if (compact) 38.dp else 68.dp,
                    max = if (compact) 62.dp else 132.dp
                )
                .clip(fieldShape)
                .background(Color(NgTheme.colors.inputContainer))
                .border(
                    width = 0.8.dp,
                    color = Color(NgTheme.colors.outline),
                    shape = fieldShape
                )
                .onFocusChanged {
                    if (it.isFocused) {
                        onAction(
                            RssSourceEditAction.FocusField(
                                field.key,
                                fieldValue.selection.start,
                                fieldValue.selection.end
                            )
                        )
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontFamily = NgTheme.fontFamily,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 19.sp
            ),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            minLines = if (compact) 1 else 2,
            maxLines = if (compact) 2 else 6
        )
    }
}

@Composable
private fun SourceEditorHelpers(onAction: (RssSourceEditAction) -> Unit) {
    val items = listOf(
        NgFlatActionRailItem(
            iconRes = R.drawable.ic_code,
            label = stringResource(R.string.rss_source_tool_url),
            contentDescription = stringResource(R.string.rss_source_insert_url_option)
        ),
        NgFlatActionRailItem(
            iconRes = R.drawable.ic_help,
            label = stringResource(R.string.rss_source_tool_guide),
            contentDescription = stringResource(R.string.rss_source_tutorial)
        ),
        NgFlatActionRailItem(
            iconRes = R.drawable.ic_code,
            label = "JS",
            contentDescription = stringResource(R.string.rss_source_js_tutorial)
        ),
        NgFlatActionRailItem(
            iconRes = R.drawable.ic_find_replace,
            label = stringResource(R.string.rss_source_tool_regex),
            contentDescription = stringResource(R.string.rss_source_regex_tutorial)
        ),
        NgFlatActionRailItem(
            iconRes = R.drawable.ic_folder_open,
            label = stringResource(R.string.rss_source_tool_file),
            contentDescription = stringResource(R.string.select_file)
        )
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorResource(R.color.ng_surface_card),
        tonalElevation = 0.dp,
        shadowElevation = if (NgTheme.snapshot.isEInk) 0.dp else 2.dp
    ) {
        NgFlatActionRail(
            items = items,
            onItemClick = { index ->
                onAction(
                    when (index) {
                        0 -> RssSourceEditAction.InsertUrlOption
                        1 -> RssSourceEditAction.Help
                        2 -> RssSourceEditAction.JsHelp
                        3 -> RssSourceEditAction.RegexHelp
                        else -> RssSourceEditAction.SelectFile
                    }
                )
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            variant = NgFlatActionRailVariant.SEGMENTED
        )
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

private val compactSourceFields = setOf(
    "sourceName",
    "sourceUrl",
    "sourceIcon",
    "sourceGroup",
    "concurrentRate"
)

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
