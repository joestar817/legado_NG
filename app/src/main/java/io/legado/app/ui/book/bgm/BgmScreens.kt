package io.legado.app.ui.book.bgm

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.help.bgm.*
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

/** Display-only state. Catalogue, selection and account jobs belong to the screen owner. */
internal enum class BgmLibraryTab { CATALOG, PLAYLISTS }

internal data class BgmLibraryUiState(
    val platform: MusicPlatform,
    val tab: BgmLibraryTab = BgmLibraryTab.CATALOG,
    val query: String = "",
    val searchExpanded: Boolean = false,
    val playlist: MusicPlaylist? = null,
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<MusicPlaylist> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val message: String = "",
    val hasMore: Boolean = false,
    val loggedIn: Boolean = false,
    val previewKey: String? = null,
    val previewLoading: Boolean = false,
    val previewPaused: Boolean = false,
    val importing: Boolean = false,
    val hasExistingQueue: Boolean = false,
    val searchSubmitted: Boolean = false,
    val submittedQuery: String = "",
    val loadingAll: Boolean = false,
    val sortByName: Boolean = false,
    val selectingForPlayback: Boolean = false,
    val managing: Boolean = false,
    val supportsPlaylistEditing: Boolean = false,
    val supportsPlaylistUnsubscribe: Boolean = false,
    val mutationBusy: Boolean = false,
    val addingTo: MusicPlaylist? = null,
    val existingTrackIds: Set<String> = emptySet(),
    val checkingExistingTracks: Boolean = false,
    val existingTracksError: String = "",
)

internal fun MusicPlatform.bgmTitle(): String = when (this) {
    MusicPlatform.NETEASE -> "网易云音乐"
    MusicPlatform.QQ -> "QQ 音乐"
    MusicPlatform.LOCAL -> "本地音乐"
}

@Composable
internal fun BgmText(text: String, modifier: Modifier = Modifier, secondary: Boolean = false,
    strong: Boolean = false, maxLines: Int = 1) {
    Text(text, modifier, color = Color(if (secondary) NgTheme.colors.onSurfaceVariant else NgTheme.colors.onSurface),
        fontSize = if (secondary) 13.sp else 16.sp, lineHeight = if (secondary) 18.sp else 22.sp,
        fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun BgmIcon(@DrawableRes icon: Int, description: String? = null, modifier: Modifier = Modifier,
    tint: Color = Color(NgTheme.colors.onSurface)) {
    Icon(painterResource(icon), description, modifier.size(22.dp), tint)
}

@Composable
internal fun BgmIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit,
    enabled: Boolean = true, active: Boolean = false) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), Alignment.Center) {
        BgmIcon(icon, description, tint = Color(if (active) NgTheme.colors.primary else NgTheme.colors.onSurface)
            .copy(alpha = if (enabled) 1f else .38f))
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun BgmCover(url: String, modifier: Modifier = Modifier, cornerRadius: Dp = 10.dp,
    placeholder: ImageVector? = null) {
    Box(modifier.clip(RoundedCornerShape(cornerRadius)).background(Color(NgTheme.colors.primary).copy(alpha = .1f)),
        contentAlignment = Alignment.Center) {
        Icon(placeholder ?: Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(24.dp),
            tint = Color(NgTheme.colors.primary))
        if (url.isNotBlank()) GlideImage(model = url, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun BgmPlatformIcon(platform: MusicPlatform, modifier: Modifier = Modifier.size(42.dp)) {
    val iconModifier = modifier.clip(RoundedCornerShape(10.dp))
    if (platform == MusicPlatform.LOCAL) {
        Box(iconModifier.background(Color(NgTheme.colors.primary).copy(alpha = .1f)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null,
                modifier = Modifier.size(28.dp), tint = Color(NgTheme.colors.primary))
        }
    } else {
        Image(
            painter = painterResource(if (platform == MusicPlatform.NETEASE) {
                R.drawable.bgm_platform_netease
            } else {
                R.drawable.bgm_platform_qq
            }),
            contentDescription = null,
            modifier = iconModifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun BgmHomeContent(settings: BgmSettings,
    accounts: Map<MusicPlatform, String>, localStorageLabel: String, onBack: () -> Unit,
    onPlatform: (MusicPlatform) -> Unit, selectingForPlayback: Boolean = false) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
        NgFloatingTitleToolbar(if (selectingForPlayback) "选择配乐" else "背景音乐", onBack)
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MusicPlatform.entries, key = { it.name }) { platform ->
                NgManagementListCard(title = platform.bgmTitle(),
                    summary = if (platform == MusicPlatform.LOCAL) "${settings.localTracks.size} 首 · $localStorageLabel"
                    else accounts[platform] ?: "未登录", onClick = { onPlatform(platform) },
                    trailingContent = { BgmIcon(R.drawable.ic_arrow_right) }) {
                    BgmPlatformIcon(platform)
                }
            }
        }
    }
}

@Composable
internal fun BgmLocalLibraryContent(state: BgmLibraryUiState, onBack: () -> Unit,
    onQuery: (String) -> Unit, onSearchExpandedChange: (Boolean) -> Unit,
    onToggleTrack: (MusicTrack) -> Unit,
    onPreview: (MusicTrack) -> Unit,
    onSelectAll: () -> Unit, onInvertSelection: () -> Unit, onApply: () -> Unit, onAppend: () -> Unit,
    onAddLocal: () -> Unit, onSort: () -> Unit, onRemoveSelected: () -> Unit,
    onManage: () -> Unit,
    onScanLocal: () -> Unit,
    onCopyLocal: () -> Unit,
    onRelocateLocal: (MusicTrack) -> Unit,
    onRemoveTrack: (MusicTrack) -> Unit) {
    val listening = LocalBgmListeningMode.current
    val busy = state.importing || state.loadingAll
    val selection = state.selectedKeys.size
    val selectable = listening || state.selectingForPlayback || state.managing
    val toolbarActions: @Composable RowScope.() -> Unit = {
        BgmToolbarMenu(actions = buildList {
            add(BgmSelectionAction("添加音乐", R.drawable.ic_bgm_menu_add, onAddLocal))
            add(BgmSelectionAction("重新扫描", R.drawable.ic_bgm_menu_refresh, onScanLocal))
            add(BgmSelectionAction("导入文件副本", R.drawable.ic_bgm_import_file, onCopyLocal))
            if (!listening && !state.selectingForPlayback) add(BgmSelectionAction(if (state.managing) "完成管理" else "批量管理",
                if (state.managing) R.drawable.ic_bgm_menu_done else R.drawable.ic_bgm_manage, onManage))
        }, sortOptions = listOf("最近添加", "歌曲名称"), selectedSortIndex = if (state.sortByName) 1 else 0,
            onSort = { if ((it == 1) != state.sortByName) onSort() }, enabled = !busy)
    }
    val contentModifier = if (listening) Modifier.fillMaxSize()
        else Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)
    Column(contentModifier) {
        if (listening) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (state.searchExpanded) {
                    BgmListeningSearchField(state.query, onQuery, "搜索本地音乐", {},
                        Modifier.weight(1f), enabled = !busy, autoFocus = true)
                } else Spacer(Modifier.weight(1f))
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    BgmListeningToolbarAction(R.drawable.ic_search, "搜索",
                        { onSearchExpandedChange(!state.searchExpanded) }, enabled = !busy,
                        active = state.searchExpanded)
                }
                Row(Modifier.size(48.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) { toolbarActions() }
            }
        } else NgFloatingSearchToolbar(state.query, onQuery, "搜索本地音乐", onBack,
            enabled = !busy, trailingContent = toolbarActions)
        val listBottomSpacing = when {
            listening -> 0.dp
            selectable -> 8.dp
            state.previewKey != null -> BgmPreviewBottomSpace
            else -> 16.dp
        }
        Box(Modifier.weight(1f).padding(top = if (listening) 0.dp else 10.dp, bottom = listBottomSpacing)) {
            LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(NgTheme.shapes.mediumDp.dp))) {
                if (state.message.isNotBlank()) item("message") { BgmStateCard(state.message) }
                if (state.loading || busy) item("loading") {
                    BgmStateCard(when { state.importing -> "正在添加音乐…"; state.loadingAll -> "正在准备音乐…"; else -> "加载中…" })
                }
                if (state.tracks.isNotEmpty()) item("local_header") {
                    Row(Modifier.fillMaxWidth().bgmListSurface(first = true).heightIn(min = 48.dp).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("本地音乐", Modifier.weight(1f), color = Color(NgTheme.colors.onSurface),
                            fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        Text("${state.tracks.size} 首", color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
                items(state.tracks, key = { track -> "track:${track.key}" }) { track ->
                    BgmOnlineTrackRow(track, track.key in state.selectedKeys, { onToggleTrack(track) },
                        previewing = state.previewKey == track.key, previewLoading = state.previewKey == track.key && state.previewLoading,
                        previewPaused = state.previewKey == track.key && state.previewPaused,
                        onPreview = { onPreview(track) }, enabled = !busy, selectable = selectable,
                        query = state.query, trailing = if (listening) {
                            if (track.localReference != null) {
                                { BgmListeningTrackActions(listOf(BgmSelectionAction("重新定位文件",
                                    R.drawable.ic_folder_open, { onRelocateLocal(track) })), enabled = !busy) }
                            } else null
                        } else {
                            {
                                BgmLocalActions(buildList {
                                    if (track.localReference != null) add(BgmLocalAction("重新定位文件", R.drawable.ic_folder_open, { onRelocateLocal(track) }))
                                    add(BgmLocalAction("移出曲库", R.drawable.ic_bgm_playlist_remove, { onRemoveTrack(track) }, danger = true))
                                }, enabled = !busy)
                            }
                        })
                }
                if (state.tracks.isNotEmpty()) item("local_footer") { Spacer(Modifier.fillMaxWidth().bgmListSurface(last = true).height(8.dp)) }
                if (!state.loading && !busy && state.message.isBlank() && state.tracks.isEmpty()) item("empty") {
                    BgmContentPanel {
                        Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = .3f))
                            Spacer(Modifier.height(12.dp))
                            BgmText(if (state.query.isBlank()) "还没有本地音乐" else "没有匹配的音乐", secondary = true)
                            if (state.query.isBlank()) NgDialogTextActionButton("添加音乐", onAddLocal)
                        }
                    }
                }
            }
        }
        if (selectable) {
            BgmSelectionDock(selection, state.tracks.size, onSelectAll, onInvertSelection,
                actions = buildList {
                    if (listening) {
                        add(BgmSelectionAction("加入当前列表", R.drawable.ic_bgm_playlist_add, onAppend, enabled = selection > 0))
                        add(BgmSelectionAction("替换当前列表", R.drawable.ic_play_outline_24dp, onApply, enabled = selection > 0))
                    } else if (state.selectingForPlayback) {
                        add(BgmSelectionAction("设为背景音乐", R.drawable.ic_play_outline_24dp, onApply, enabled = selection > 0))
                        add(BgmSelectionAction("加入当前列表", R.drawable.ic_bgm_playlist_add, onAppend, enabled = selection > 0))
                    } else if (state.managing) {
                        add(BgmSelectionAction("移出曲库", R.drawable.ic_bgm_playlist_remove, onRemoveSelected,
                            enabled = selection > 0, danger = true))
                    }
                }, enabled = !busy, modifier = Modifier.padding(bottom = when {
                    listening -> 0.dp
                    state.previewKey != null -> BgmPreviewBottomSpace
                    else -> 8.dp
                }))
        }
    }
}

@Composable
internal fun BgmLibraryLoginCard(platform: MusicPlatform, onLogin: () -> Unit, inline: Boolean = false) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BgmPlatformIcon(platform)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BgmText("我的歌单", strong = true)
                BgmText("登录后同步你的歌单", secondary = true, maxLines = 2)
            }
            NgFormActionButton(
                text = "登录",
                onClick = onLogin,
                variant = NgButtonVariant.PRIMARY,
                minimumWidth = 64.dp,
                buttonHeight = 36.dp,
            )
        }
    }
    if (inline || LocalBgmListeningMode.current) content() else NgFormPanel { content() }
}

@Composable
internal fun Modifier.bgmListSurface(first: Boolean = false, last: Boolean = false): Modifier {
    if (LocalBgmListeningMode.current) return this
    val corner = NgTheme.shapes.mediumDp.dp
    return clip(RoundedCornerShape(
        topStart = if (first) corner else 0.dp,
        topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp,
        bottomEnd = if (last) corner else 0.dp,
    )).background(ngDrawerContentCardColor())
}

@Composable
internal fun BgmPlaylistRow(playlist: MusicPlaylist, onClick: () -> Unit, last: Boolean = false,
    enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val listening = LocalBgmListeningMode.current
    Column(Modifier.bgmListSurface(last = last)) {
        Row(Modifier.fillMaxWidth().height(if (listening) 60.dp else 64.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BgmCover(playlist.coverUrl, Modifier.size(if (listening) 40.dp else 44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(playlist.title, color = Color(NgTheme.colors.onSurface), fontSize = 15.sp,
                    lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.trackCount} 首", color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
            }
            if (trailing != null) trailing() else BgmIcon(R.drawable.ic_arrow_right)
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 70.dp, end = 14.dp),
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = .3f))
    }
}

@Composable
internal fun BgmTrackRow(track: MusicTrack, checked: Boolean, onToggle: () -> Unit,
    previewing: Boolean, previewLoading: Boolean, onPreview: () -> Unit,
    enabled: Boolean = true, selectable: Boolean = false,
    first: Boolean = false, last: Boolean = false,
    compactMetadata: Boolean = false, previewPaused: Boolean = false,
    trailing: (@Composable () -> Unit)? = null) {
    Column(Modifier.bgmListSurface(first = first, last = last)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            val action = if (selectable) Modifier.toggleable(value = checked, enabled = enabled,
                role = Role.Checkbox, onValueChange = { onToggle() }).semantics { selected = checked }
            else Modifier.clickable(enabled = enabled, role = Role.Button,
                onClickLabel = if (previewing) "停止试听" else "试听", onClick = onPreview)
            Row(Modifier.weight(1f).then(action).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectable) {
                    Box(Modifier.width(48.dp), Alignment.Center) {
                        Box(Modifier.size(22.dp).clip(CircleShape).then(if (checked) Modifier.background(Color(NgTheme.colors.primary))
                            else Modifier.border(1.5.dp, Color(NgTheme.colors.onSurfaceVariant).copy(alpha = .6f), CircleShape)), Alignment.Center) {
                            if (checked) BgmIcon(R.drawable.ic_check, modifier = Modifier.size(15.dp), tint = Color.White)
                        }
                    }
                } else Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track.title, color = Color(if (previewing) NgTheme.colors.primary else NgTheme.colors.onSurface), fontSize = 15.sp,
                        lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist.ifBlank { if (track.platform == MusicPlatform.LOCAL) "本地音频" else track.platform.bgmTitle() } +
                        if (compactMetadata && track.durationMs > 0) " · ${track.durationMs / 60000}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}" else "",
                        color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp,
                        lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!compactMetadata && track.durationMs > 0) BgmText("${track.durationMs / 60000}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}",
                    Modifier.padding(start = 8.dp), secondary = true)
            }
            BgmIconButton(if (previewPaused) R.drawable.ic_play_24dp else if (previewing) R.drawable.ic_stop_black_24dp else R.drawable.ic_tts_headphones,
                if (previewPaused) "继续试听" else if (previewLoading) "停止加载试听" else if (previewing) "停止试听" else "试听 ${track.title}", onPreview, enabled, previewing)
            trailing?.invoke()
        }
        if (!last) HorizontalDivider(Modifier.padding(start = if (selectable) 48.dp else 14.dp, end = 14.dp),
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = .3f))
    }
}

@Composable
private fun BgmStateCard(message: String, action: String? = null, onAction: () -> Unit = {}, enabled: Boolean = true) {
    BgmContentPanel {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (message.isNotBlank()) BgmText(message, secondary = true, maxLines = 3)
            if (action != null) NgFormActionButton(action, onAction, enabled = enabled,
                appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS)
        }
    }
}

@Composable
internal fun BgmQueueContent(settings: BgmSettings, onBack: () -> Unit,
    onSelect: (String) -> Unit, onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit, onMode: (BgmRepeatMode) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp)) {
        NgFloatingTitleToolbar("当前列表", onBack, trailingContent = {
            BgmLocalActions(buildList {
                BgmRepeatMode.entries.forEach { mode ->
                    val icon = when (mode) {
                        BgmRepeatMode.LIST -> R.drawable.ic_play_mode_list_loop
                        BgmRepeatMode.ONE -> R.drawable.ic_play_mode_single_loop
                        BgmRepeatMode.SHUFFLE -> R.drawable.ic_play_mode_random
                    }
                    add(BgmLocalAction(mode.label, icon, { onMode(mode) }, checked = mode == settings.repeatMode))
                }
                if (settings.queue.isNotEmpty()) add(BgmLocalAction("清空列表", R.drawable.ic_bgm_playlist_remove,
                    onClear, danger = true, divider = true))
            }, toolbar = true)
        })
        LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("mode") { NgFormPanelSectionTitle("${settings.repeatMode.label} · ${settings.queue.size} 首") }
            if (settings.queue.isEmpty()) item("empty") { BgmStateCard("当前列表为空") }
            itemsIndexed(settings.queue, key = { _, track -> track.key }) { index, track ->
                NgManagementListCard(track.title,
                    summary = listOf(track.artist, track.platform.bgmTitle()).filter(String::isNotBlank).joinToString(" · "),
                    selected = settings.currentTrack?.key == track.key, onClick = { onSelect(track.key) },
                    trailingContent = { BgmLocalActions(buildList {
                        if (index > 0) add(BgmLocalAction("上移", R.drawable.ic_arrow_drop_up, { onMove(track.key, index - 1) }))
                        if (index < settings.queue.lastIndex) add(BgmLocalAction("下移", R.drawable.ic_arrow_drop_down, { onMove(track.key, index + 1) }))
                        add(BgmLocalAction("移出当前列表", R.drawable.ic_bgm_playlist_remove, { onRemove(track.key) }, danger = true))
                    }) }) { BgmCover(track.coverUrl, Modifier.size(42.dp)) }
            }
        }
    }
}

@Composable
internal fun BgmConfirmDialog(title: String, message: String, confirmText: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, cancelText: String? = "取消") {
    NgPaneDialog(onDismissRequest = onDismiss) {
        NgDialog(title, actions = {
            if (cancelText != null) NgDialogTextActionButton(cancelText, onDismiss, secondary = true)
            NgDialogTextActionButton(confirmText, onConfirm)
        }) {
            BgmText(message, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), maxLines = Int.MAX_VALUE)
        }
    }
}
