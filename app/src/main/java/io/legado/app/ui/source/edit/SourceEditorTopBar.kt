package io.legado.app.ui.source.edit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgEditorTopBar
import io.legado.app.ui.design.components.compose.NgEditorTopBarAction
import io.legado.app.ui.design.components.compose.NgEditorTopBarIconButton
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgTheme

internal fun sourceEditorMenuItems(hasSearch: Boolean, hasLogin: Boolean) = buildList {
    if (hasSearch) add(NgExpandableActionMenuItem(R.id.menu_search, R.string.search, R.drawable.ic_search))
    if (hasLogin) add(NgExpandableActionMenuItem(R.id.menu_login, R.string.login, R.drawable.ic_book_info_menu_login))
    add(NgExpandableActionMenuItem(R.id.menu_copy_source, R.string.copy_source, R.drawable.ic_copy))
    add(NgExpandableActionMenuItem(R.id.menu_paste_source, R.string.paste_source, R.drawable.ic_import))
    add(NgExpandableActionMenuItem(R.id.menu_set_source_variable, R.string.set_source_variable, R.drawable.ic_code))
    add(NgExpandableActionMenuItem(
        R.id.source_editor_settings, R.string.setting, R.drawable.ic_book_info_menu_settings,
        dividerBefore = true,
    ))
    add(NgExpandableActionMenuItem(
        R.id.source_editor_transfer, R.string.source_editor_import_share, R.drawable.ic_share,
        children = listOf(
            NgExpandableActionMenuItem(R.id.menu_qr_code_camera, R.string.import_by_qr_code, R.drawable.ic_qr_code),
            NgExpandableActionMenuItem(R.id.menu_share_qr, R.string.qr_share, R.drawable.ic_qr_code),
            NgExpandableActionMenuItem(R.id.menu_share_str, R.string.str_share, R.drawable.ic_book_info_menu_share),
        ),
    ))
    add(NgExpandableActionMenuItem(
        R.id.source_editor_diagnostics, R.string.book_info_menu_data_and_diagnostics, R.drawable.ic_cfg_about,
        children = listOf(
            NgExpandableActionMenuItem(R.id.menu_clear_cookie, R.string.cookie, R.drawable.ic_book_info_menu_clear_cache),
            NgExpandableActionMenuItem(R.id.menu_log, R.string.log, R.drawable.ic_bug_report),
            NgExpandableActionMenuItem(R.id.menu_network_log, R.string.network_request_log, R.drawable.ic_book_info_menu_network),
        ),
    ))
    add(NgExpandableActionMenuItem(R.id.menu_help, R.string.help, R.drawable.ic_help))
}

@Composable
internal fun SourceEditorTopBar(
    @StringRes title: Int,
    hasSearch: Boolean,
    hasLogin: () -> Boolean,
    autoComplete: Boolean,
    onBack: () -> Unit,
    onAction: (Int) -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    var loginVisible by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    NgEditorTopBar(
        title = stringResource(title),
        onBack = onBack,
        actions = listOf(
            NgEditorTopBarAction(painterResource(R.drawable.ic_code), stringResource(R.string.edit_content),
                { onAction(R.id.menu_fullscreen_edit) }),
            NgEditorTopBarAction(painterResource(R.drawable.ic_save), stringResource(R.string.action_save),
                { onAction(R.id.menu_save) }),
            NgEditorTopBarAction(painterResource(R.drawable.ic_bug_report), stringResource(R.string.debug_source),
                { onAction(R.id.menu_debug_source) }, iconSize = 21.dp),
        ),
        trailingContent = {
            Box {
                NgEditorTopBarIconButton(
                    icon = painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.more),
                    onClick = {
                        loginVisible = hasLogin()
                        menuState.onAnchorClick()
                    },
                )
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = remember(hasSearch, loginVisible) { sourceEditorMenuItems(hasSearch, loginVisible) },
                    offset = DpOffset(0.dp, 4.dp),
                    menuContainerColor = Color(NgTheme.colors.cardContainer).copy(alpha = 1f),
                    properties = PopupProperties(focusable = true, clippingEnabled = false),
                    onItemClick = { item ->
                        menuState.close()
                        if (item.itemId == R.id.source_editor_settings) {
                            keyboard?.hide()
                            settingsOpen = true
                        } else onAction(item.itemId)
                    },
                )
            }
        },
    )
    if (settingsOpen) {
        SourceEditorSettingsSheet(
            autoComplete = autoComplete,
            onAutoCompleteChange = { if (it != autoComplete) onAction(R.id.menu_auto_complete) },
            onDismiss = { settingsOpen = false },
        )
    }
}
