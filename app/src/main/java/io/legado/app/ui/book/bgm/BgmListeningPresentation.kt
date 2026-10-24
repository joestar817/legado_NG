package io.legado.app.ui.book.bgm

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

/** Only the listening drawer opts in; full-screen management keeps its existing geometry. */
internal val LocalBgmListeningMode = staticCompositionLocalOf { false }

@Composable
internal fun BgmContentPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalBgmListeningMode.current) Column(modifier.fillMaxWidth(), content = content)
    else NgFormPanel(modifier, content)
}

@Composable
internal fun BgmListeningToolbarAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    NgFloatingToolbarActionButton(icon, description, onClick,
        modifier = Modifier.size(44.dp), enabled = enabled,
        tint = Color(if (active) NgTheme.colors.primary else NgTheme.colors.onSurface))
}

/** The inline search uses theme colors rather than the full-page toolbar's fixed light resources. */
@Composable
internal fun BgmListeningSearchField(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    NgFormField(label = hint, value = query, onValueChange = onQuery,
        modifier = modifier, placeholder = hint, enabled = enabled,
        variant = NgFormFieldVariant.PLAIN_UNDERLINE, autoFocus = autoFocus,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(); keyboard?.hide() }),
        trailingContent = if (query.isNotEmpty()) {
            { BgmListeningToolbarAction(R.drawable.ic_baseline_close, "清空搜索", { onQuery("") }, enabled) }
        } else null)
}

@Composable
internal fun BgmListeningLibraryHeader(
    state: BgmLibraryUiState,
    onBack: () -> Unit,
    onTab: (BgmLibraryTab) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    menu: @Composable () -> Unit,
) {
    val playlist = state.playlist
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .padding(horizontal = if (playlist == null) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (playlist != null) {
                BgmListeningToolbarAction(R.drawable.ic_chevron_left_search, "返回歌单", onBack, enabled)
                BgmText(playlist.title, Modifier.weight(1f), strong = true)
                BgmText("${playlist.trackCount} 首", Modifier.padding(start = 8.dp, end = 4.dp), secondary = true)
                menu()
            } else {
                BgmText(if (state.tab == BgmLibraryTab.CATALOG) "曲库" else "歌单",
                    Modifier.weight(1f).padding(start = 4.dp, end = 8.dp), strong = true)
                BgmListeningLibraryViewSwitch(state.tab, onTab, enabled)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    BgmListeningToolbarAction(R.drawable.ic_search, "搜索",
                        { onSearchExpandedChange(!state.searchExpanded) }, enabled,
                        active = state.searchExpanded)
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { menu() }
            }
        }
        if (playlist == null && state.searchExpanded) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BgmListeningSearchField(state.query, onQuery,
                    if (state.tab == BgmLibraryTab.CATALOG) "搜索歌曲或歌手" else "搜索我的歌单",
                    onSearch, Modifier.weight(1f), enabled, autoFocus = true)
                BgmListeningToolbarAction(R.drawable.ic_search, "搜索", onSearch, enabled)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), thickness = .5.dp,
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = .35f))
    }
}

/** A content-view switch, visually distinct from the source tabs above it. */
@Composable
private fun BgmListeningLibraryViewSwitch(
    tab: BgmLibraryTab,
    onTab: (BgmLibraryTab) -> Unit,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(width = 88.dp, height = 48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 88.dp, height = 28.dp).clip(shape)
            .background(Color(NgTheme.colors.onSurface).copy(alpha = .035f))
            .border(.5.dp, Color(NgTheme.colors.outlineVariant).copy(alpha = .35f), shape))
        Row(Modifier.selectableGroup()) {
            BgmListeningLibraryViewOption(R.drawable.ic_bgm_catalog, "曲库",
                tab == BgmLibraryTab.CATALOG, enabled) { onTab(BgmLibraryTab.CATALOG) }
            BgmListeningLibraryViewOption(R.drawable.ic_bgm_playlist, "歌单",
                tab == BgmLibraryTab.PLAYLISTS, enabled) { onTab(BgmLibraryTab.PLAYLISTS) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BgmListeningLibraryViewOption(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val tint = (if (selected) primary else Color(NgTheme.colors.onSurfaceVariant))
        .copy(alpha = if (enabled) 1f else .38f)
    val shape = RoundedCornerShape(10.dp)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, fontFamily = NgTheme.fontFamily) } },
        state = rememberTooltipState(),
        focusable = false,
    ) {
        Box(Modifier.size(width = 44.dp, height = 48.dp).clip(shape)
            .semantics { contentDescription = label }
            .selectable(selected, enabled, Role.RadioButton) { if (!selected) onClick() },
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 44.dp, height = 28.dp).clip(shape)
                .background(if (selected) primary.copy(alpha = if (enabled) .18f else .07f)
                    else Color.Transparent), contentAlignment = Alignment.Center) {
                BgmIcon(icon, modifier = Modifier.size(20.dp), tint = tint)
            }
        }
    }
}

@Composable
internal fun BgmListeningTrackActions(actions: List<BgmSelectionAction>, enabled: Boolean) {
    val menu = remember { NgPopupToggleState() }
    LaunchedEffect(enabled) { if (!enabled) menu.close() }
    Box {
        BgmTrackActionButton(R.drawable.ic_more_vert, "更多", menu::onAnchorClick, enabled)
        NgExpandableActionMenu(menu.expanded, menu::onDismissRequest,
            items = actions.mapIndexed { index, action ->
                NgExpandableActionMenuItem(index + 1, 0, action.icon, title = action.title,
                    enabled = enabled && action.enabled, danger = action.danger)
            },
            onItemClick = { item ->
                menu.close()
                actions.getOrNull(item.itemId - 1)?.takeIf { enabled && it.enabled }?.action?.invoke()
            },
            menuContainerColor = Color(NgTheme.colors.surfaceContainerHigh),
            properties = PopupProperties(focusable = true))
    }
}
