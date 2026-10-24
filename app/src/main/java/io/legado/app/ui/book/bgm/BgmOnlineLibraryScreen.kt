package io.legado.app.ui.book.bgm

import androidx.annotation.DrawableRes
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.bgm.*
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.cnCompare

/** Platform library and playlist management share one compact, rounded content surface. */
@Composable
internal fun BgmOnlineLibraryScreen(
    state: BgmLibraryUiState,
    addSongsError: String,
    onRetryExistingTracks: () -> Unit,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onTab: (BgmLibraryTab) -> Unit,
    onAccount: () -> Unit,
    onPlaylist: (MusicPlaylist) -> Unit,
    onPreview: (MusicTrack) -> Unit,
    onToggleTrack: (MusicTrack) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onNewPlaylist: () -> Unit,
    onEditPlaylist: (MusicPlaylist) -> Unit,
    onDeletePlaylist: (MusicPlaylist) -> Unit,
    onUnsubscribe: (MusicPlaylist) -> Unit,
    onAddSongs: () -> Unit,
    onAddTrack: (MusicTrack) -> Unit,
    onRemoveTrack: (MusicTrack) -> Unit,
    onManage: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAddSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onApply: () -> Unit,
    onAppend: () -> Unit,
    onUsePlaylist: () -> Unit,
) {
    val listening = LocalBgmListeningMode.current
    val selectingForPlayback = listening || state.selectingForPlayback
    val playlist = state.playlist
    val catalogue = state.tab == BgmLibraryTab.CATALOG
    val addingSongs = !listening && playlist == null && state.addingTo != null
    val inlineTrackLoading = playlist != null || catalogue
    val busy = state.mutationBusy || state.loadingAll || state.importing
    val addIconSize = with(LocalDensity.current) { 20.sp.toDp() }
    val swipeEnabled = playlist == null && !addingSongs && !busy
    val selectTab by rememberUpdatedState(onTab)
    val selectable = addingSongs || state.managing || selectingForPlayback
    val showingTracks = playlist != null || catalogue
    val showSelectionActions = showingTracks && selectable
    val idleListeningCatalogue = listening && playlist == null && catalogue && !state.searchSubmitted &&
        state.tracks.isEmpty() && !state.loading && state.message.isBlank()
    val selectionEnabled = !busy && (!addingSongs ||
        (!state.checkingExistingTracks && state.existingTracksError.isBlank()))
    val selectableCount = remember(state.tracks, addingSongs, state.existingTrackIds) {
        state.tracks.count { !addingSongs || it.id !in state.existingTrackIds }
    }
    var sortIndex by rememberSaveable(state.platform, playlist?.key, state.submittedQuery) {
        mutableIntStateOf(0)
    }
    val sortOptions = remember { listOf("默认顺序", "歌曲名称", "歌手", "时长") }
    var playlistSortIndex by rememberSaveable(state.platform) { mutableIntStateOf(0) }
    val playlistSortOptions = remember { listOf("默认顺序", "歌单名称", "歌曲数量") }
    val visibleTracks = remember(state.tracks, sortIndex) {
        when (sortIndex) {
            1 -> state.tracks.sortedBy { it.title.lowercase() }
            2 -> state.tracks.sortedBy { it.artist.lowercase() }
            3 -> state.tracks.sortedWith(compareBy<MusicTrack> { it.durationMs <= 0 }.thenBy { it.durationMs })
            else -> state.tracks
        }
    }
    val listBottomSpacing = when {
        listening -> 0.dp
        showSelectionActions -> 8.dp
        state.previewKey != null -> BgmPreviewBottomSpace
        else -> 16.dp
    }
    val groups = remember(state.playlists, state.query, playlistSortIndex) {
        state.playlists.filter { it.title.contains(state.query.trim(), ignoreCase = true) }
            .groupBy(MusicPlaylist::kind)
            .mapValues { (_, entries) ->
                when (playlistSortIndex) {
                    1 -> entries.sortedWith { left, right -> left.title.cnCompare(right.title) }
                    2 -> entries.sortedByDescending(MusicPlaylist::trackCount)
                    else -> entries
                }
            }
    }
    val menu: (MusicPlaylist) -> List<LibraryMenuAction> = { item ->
        buildList {
            if (!selectingForPlayback) {
                if (item.editable && state.supportsPlaylistEditing) {
                    add(LibraryMenuAction("编辑歌单", R.drawable.ic_bgm_menu_edit) { onEditPlaylist(item) })
                }
                if (playlist != null) add(LibraryMenuAction(if (state.managing) "完成管理" else "管理歌曲",
                    if (state.managing) R.drawable.ic_bgm_menu_done else R.drawable.ic_bgm_manage, action = onManage))
                if (item.editable) add(LibraryMenuAction("删除歌单", R.drawable.ic_bgm_menu_delete, true) { onDeletePlaylist(item) })
                if (item.collected && state.supportsPlaylistUnsubscribe) {
                    add(LibraryMenuAction("取消收藏", R.drawable.ic_bgm_menu_unfavorite, true) { onUnsubscribe(item) })
                }
            } else if (playlist != null) {
                add(LibraryMenuAction(if (listening) "整张歌单替换当前列表" else "整张歌单设为背景音乐",
                    R.drawable.ic_bgm_menu_play, action = onUsePlaylist))
            }
        }
    }
    val toolbarMenu: @Composable () -> Unit = {
        val account = BgmSelectionAction(if (state.loggedIn) "账号" else "登录",
            R.drawable.ic_bgm_menu_account, onAccount)
        val actions = if (playlist == null) listOf(account) else menu(playlist).map {
            BgmSelectionAction(it.title, it.icon, it.action, danger = it.danger)
        }.ifEmpty { listOf(account) }
        BgmToolbarMenu(actions,
            sortOptions = if (showingTracks) sortOptions else playlistSortOptions,
            selectedSortIndex = if (showingTracks) sortIndex else playlistSortIndex,
            onSort = { if (showingTracks) sortIndex = it else playlistSortIndex = it },
            enabled = !busy,
            sortEnabled = if (showingTracks) state.tracks.isNotEmpty()
                else state.loggedIn && groups.values.any { it.isNotEmpty() },
            loadedOnly = showingTracks && state.hasMore)
    }
    val contentModifier = if (listening) Modifier.fillMaxSize()
        else Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)
    Column(contentModifier) {
        if (listening) {
            BgmListeningLibraryHeader(state, onBack, onTab, onQuery, onSearch,
                onSearchExpandedChange, enabled = !busy, menu = toolbarMenu)
        } else if (playlist == null && (state.searchExpanded || addingSongs)) {
            NgFloatingSearchToolbar(
                query = state.query,
                onQueryChange = onQuery,
                hint = if (catalogue) "搜索${state.platform.bgmTitle()}歌曲、歌手" else "搜索我的歌单",
                onBack = onBack,
                enabled = !busy,
                onSearch = { onSearch() },
                trailingContent = { toolbarMenu() },
            )
        } else if (playlist == null) {
            NgFloatingTitleToolbar(state.platform.bgmTitle(), onBack, trailingContent = {
                NgFloatingToolbarActionButton(R.drawable.ic_search, "搜索",
                    { onSearchExpandedChange(true) }, enabled = !busy)
                toolbarMenu()
            })
        } else NgFloatingTitleToolbar(playlist.title, onBack, trailingContent = { toolbarMenu() })

        NgPullRefreshBox(state.loading, onRetry,
            Modifier.weight(1f).padding(top = if (listening) 0.dp else 10.dp, bottom = listBottomSpacing),
            showIndicator = !inlineTrackLoading,
            enabled = !busy && (playlist != null || !catalogue || state.searchSubmitted)) {
            LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(NgTheme.shapes.mediumDp.dp))
                .pointerInput(state.tab, swipeEnabled) {
                    if (!swipeEnabled) return@pointerInput
                    var horizontalDrag = 0f
                    val threshold = 64.dp.toPx()
                    // Use the same threshold as the player; vertical scroll keeps its own gesture.
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                        onDragCancel = { horizontalDrag = 0f },
                        onDragEnd = {
                            when {
                                catalogue && horizontalDrag < -threshold -> selectTab(BgmLibraryTab.PLAYLISTS)
                                !catalogue && horizontalDrag > threshold -> selectTab(BgmLibraryTab.CATALOG)
                            }
                            horizontalDrag = 0f
                        },
                    )
                }) {
                if (!listening && playlist == null && !addingSongs) item("tabs") {
                    Column(Modifier.fillMaxWidth().bgmListSurface(first = true)) {
                        NgFlatActionRail(
                            items = listOf(
                                NgFlatActionRailItem(label = "曲库", emphasized = catalogue, enabled = !busy),
                                NgFlatActionRailItem(label = "歌单", emphasized = !catalogue, enabled = !busy),
                            ),
                            onItemClick = { onTab(if (it == 0) BgmLibraryTab.CATALOG else BgmLibraryTab.PLAYLISTS) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            variant = NgFlatActionRailVariant.FORM_UNDERLINE_TABS,
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            thickness = .5.dp,
                            color = Color(NgTheme.colors.onSurface).copy(
                                alpha = if (NgTheme.snapshot.isEInk) .18f else .06f),
                        )
                    }
                } else if (!listening && playlist != null) item("playlist-header") {
                    Column(Modifier.bgmListSurface(first = true)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BgmCover(playlist.coverUrl, Modifier.size(64.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                BgmText(playlist.title, strong = true)
                                BgmText("${playlist.trackCount} 首", secondary = true)
                            }
                        }
                        if (playlist.editable && !selectingForPlayback) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                BgmText("歌曲", Modifier.weight(1f), secondary = true)
                                NgFormActionButton("添加歌曲", onAddSongs, enabled = !busy,
                                    appearance = NgFormActionButtonAppearance.TEXT,
                                    buttonHeight = 40.dp, minimumWidth = 0.dp,
                                    textSize = 14.sp, textLineHeight = 20.sp,
                                    leadingIcon = { BgmIcon(R.drawable.ic_add, modifier = Modifier.size(addIconSize),
                                        tint = LocalContentColor.current) })
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 14.dp),
                            color = Color(NgTheme.colors.outlineVariant).copy(alpha = .3f))
                    }
                }
                if (playlist == null && catalogue && (state.searchSubmitted || state.addingTo != null)) item("results-title") {
                    LibrarySection(state.addingTo?.let { "添加到：${it.title}" } ?: "搜索结果",
                        first = addingSongs)
                }
                if (inlineTrackLoading && state.loading && !state.loadingMore) item("track-loading") {
                    BgmSearchLoadingIndicator(Modifier.fillMaxWidth().bgmListSurface()
                        .height(if (state.tracks.isEmpty()) 64.dp else 48.dp))
                }
                if (playlist == null && !catalogue) {
                    if (!state.loggedIn) item("login") {
                        Box(Modifier.bgmListSurface()) { BgmLibraryLoginCard(state.platform, onAccount, inline = true) }
                    } else {
                        item("playlists-title") {
                            Row(Modifier.fillMaxWidth().bgmListSurface().heightIn(min = 48.dp)
                                .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("我的歌单", Modifier.weight(1f), color = Color(NgTheme.colors.primary), fontSize = 14.sp)
                                if (!selectingForPlayback) NgFormActionButton("新建", onNewPlaylist, enabled = !busy,
                                    appearance = NgFormActionButtonAppearance.TEXT,
                                    buttonHeight = 40.dp, minimumWidth = 0.dp,
                                    textSize = 14.sp, textLineHeight = 20.sp,
                                    leadingIcon = { BgmIcon(R.drawable.ic_add, modifier = Modifier.size(addIconSize),
                                        tint = LocalContentColor.current) })
                            }
                        }
                        listOf(MusicPlaylistKind.SYSTEM, MusicPlaylistKind.OWNED,
                            MusicPlaylistKind.COLLECTED, MusicPlaylistKind.UNKNOWN).forEach { kind ->
                            val entries = groups[kind].orEmpty()
                            if (entries.isNotEmpty()) {
                                if (kind != MusicPlaylistKind.SYSTEM) item("group:$kind") {
                                    LibrarySection(when (kind) {
                                        MusicPlaylistKind.OWNED -> "我创建的"
                                        MusicPlaylistKind.COLLECTED -> "我收藏的"
                                        else -> "其他歌单"
                                    }, secondary = true)
                                }
                                items(entries, key = { "playlist:${it.key}" }) { item ->
                                    val actions = menu(item)
                                    BgmPlaylistRow(item, { onPlaylist(item) }, enabled = !busy,
                                        trailing = if (actions.isEmpty()) null else {
                                            { LibraryMenu(actions, enabled = !busy) }
                                        })
                                }
                            }
                        }
                    }
                }
                items(visibleTracks, key = { "track:${it.key}" }) { track ->
                    BgmOnlineTrackRow(track, track.key in state.selectedKeys, { onToggleTrack(track) },
                        previewing = state.previewKey == track.key,
                        previewLoading = state.previewKey == track.key && state.previewLoading,
                        previewPaused = state.previewKey == track.key && state.previewPaused,
                        onPreview = { onPreview(track) }, enabled = selectionEnabled, selectable = selectable,
                        selectionAtEnd = addingSongs,
                        alreadyAdded = addingSongs && track.id in state.existingTrackIds,
                        query = if (playlist == null && catalogue) state.submittedQuery else "",
                        trailing = if (selectable) null else {
                            {
                                LibraryMenu(buildList {
                                    add(LibraryMenuAction(if (playlist == null) "加入歌单" else "加入其他歌单",
                                        R.drawable.ic_bgm_playlist_add) { onAddTrack(track) })
                                    if (playlist?.editable == true) add(LibraryMenuAction("移出歌单",
                                        R.drawable.ic_bgm_playlist_remove, true) { onRemoveTrack(track) })
                                }, enabled = !busy, compact = true)
                            }
                        })
                }
                if (state.message.isNotBlank()) item("error") {
                    LibraryMessage(state.message, "重试", onRetry, !busy && !state.loading)
                }
                if (!state.loading && state.message.isBlank()) {
                    val empty = when {
                        playlist != null -> state.tracks.isEmpty()
                        catalogue -> state.tracks.isEmpty()
                        else -> state.loggedIn && groups.values.all { it.isEmpty() }
                    }
                    if (empty) item("empty") {
                        if (idleListeningCatalogue) {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                BgmLibrarySearchEmptyState(state.platform)
                            }
                        } else if (listening) {
                            LibraryMessage(when {
                                playlist != null -> "歌单里还没有歌曲"
                                catalogue && !state.searchSubmitted -> "搜索歌曲或歌手"
                                catalogue -> "没有找到音乐"
                                state.query.isNotBlank() -> "没有匹配的歌单"
                                else -> "还没有歌单"
                            })
                        } else if (playlist != null) {
                            BgmLibrarySearchEmptyState(state.platform, message = "歌单里还没有歌曲")
                        } else if (catalogue && !state.searchSubmitted) {
                            BgmLibrarySearchEmptyState(state.platform, addingSongs = addingSongs)
                        } else {
                            LibraryMessage(when {
                                catalogue -> "没有找到音乐"
                                state.query.isNotBlank() -> "没有匹配的歌单"
                                else -> "还没有歌单"
                            })
                        }
                    }
                }
                if (!inlineTrackLoading && state.loading && state.tracks.isEmpty() && state.playlists.isEmpty()) item("loading") {
                    LibraryMessage("加载中…")
                }
                if (state.hasMore) item("more") {
                    Box(Modifier.fillMaxWidth().bgmListSurface().heightIn(min = 48.dp),
                        contentAlignment = Alignment.Center) {
                        if (inlineTrackLoading && state.loading && state.loadingMore) {
                            BgmSearchLoadingIndicator(Modifier.fillMaxWidth().height(48.dp))
                        } else {
                            NgDialogTextActionButton(if (state.loading && !inlineTrackLoading) "加载中…" else "加载更多",
                                onLoadMore, enabled = !state.loading && !busy)
                        }
                    }
                }
                if (!idleListeningCatalogue) item("surface-end") {
                    Spacer(Modifier.fillMaxWidth().height(8.dp).bgmListSurface(last = true))
                }
            }
        }
        if (showSelectionActions) {
            Column(Modifier.padding(bottom = when {
                listening -> 0.dp
                state.previewKey != null -> BgmPreviewBottomSpace
                else -> 8.dp
            })) {
                if (addingSongs && (addSongsError.isNotBlank() || state.existingTracksError.isNotBlank())) {
                    NgFormPanel(Modifier.padding(bottom = 8.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            if (addSongsError.isNotBlank()) Text(addSongsError,
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Color(NgTheme.colors.error), fontSize = 13.sp, lineHeight = 18.sp)
                            if (state.existingTracksError.isNotBlank()) Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(state.existingTracksError, Modifier.weight(1f),
                                    color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp, lineHeight = 18.sp)
                                NgDialogTextActionButton("重试", onRetryExistingTracks,
                                    enabled = !busy && !state.checkingExistingTracks)
                            }
                        }
                    }
                }
                BgmSelectionDock(
                    selectedCount = state.selectedKeys.size,
                    selectableCount = selectableCount,
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    actions = buildList {
                        val hasSelection = state.selectedKeys.isNotEmpty()
                        when {
                            addingSongs -> add(BgmSelectionAction("添加到歌单", R.drawable.ic_bgm_playlist_add,
                                onAddSelected, enabled = hasSelection && selectionEnabled))
                            listening -> {
                                add(BgmSelectionAction("加入当前列表", R.drawable.ic_bgm_playlist_add,
                                    onAppend, enabled = hasSelection))
                                add(BgmSelectionAction("替换当前列表", R.drawable.ic_play_outline_24dp,
                                    onApply, enabled = hasSelection))
                            }
                            selectingForPlayback -> {
                                add(BgmSelectionAction("设为背景音乐", R.drawable.ic_play_outline_24dp,
                                    onApply, enabled = hasSelection))
                                if (state.hasExistingQueue) add(BgmSelectionAction("加入当前列表", R.drawable.ic_bgm_playlist_add,
                                    onAppend, enabled = hasSelection))
                            }
                            else -> {
                                add(BgmSelectionAction("加入歌单", R.drawable.ic_bgm_playlist_add,
                                    onAddSelected, enabled = hasSelection))
                                if (playlist?.editable == true) add(BgmSelectionAction("移出歌单", R.drawable.ic_bgm_playlist_remove,
                                    onRemoveSelected, enabled = hasSelection, danger = true))
                            }
                        }
                    },
                    enabled = selectionEnabled,
                    statusText = when {
                        addingSongs && state.mutationBusy -> "添加中…"
                        addingSongs && state.checkingExistingTracks -> "核对中…"
                        state.loadingAll || state.importing -> "正在准备…"
                        else -> null
                    },
                )
            }
        }
    }
}

@Composable
private fun LibrarySection(title: String, secondary: Boolean = false, first: Boolean = false) {
    Text(title, Modifier.fillMaxWidth().bgmListSurface(first = first).padding(horizontal = 14.dp, vertical = 10.dp),
        color = Color(if (secondary) NgTheme.colors.onSurfaceVariant else NgTheme.colors.primary),
        fontSize = if (secondary) 12.sp else 14.sp, maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

@Composable
private fun LibraryMessage(message: String, action: String? = null, onAction: () -> Unit = {}, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth().bgmListSurface().padding(horizontal = 14.dp, vertical = 16.dp)) {
        if (message.isNotBlank()) BgmText(message, secondary = true, maxLines = 4)
        if (action != null) NgDialogTextActionButton(action, onAction, enabled = enabled)
    }
}

private data class LibraryMenuAction(val title: String, val icon: Int, val danger: Boolean = false, val action: () -> Unit)

@Composable
private fun LibraryMenu(
    actions: List<LibraryMenuAction>,
    enabled: Boolean,
    compact: Boolean = false,
    @DrawableRes anchorIcon: Int = R.drawable.ic_more_vert,
    variant: NgExpandableActionMenuVariant = NgExpandableActionMenuVariant.DROPDOWN,
) {
    val listening = LocalBgmListeningMode.current
    val menuState = remember { NgPopupToggleState() }
    Box {
        if (compact) BgmTrackActionButton(anchorIcon, "更多", menuState::onAnchorClick, enabled)
        else BgmIconButton(anchorIcon, "更多", menuState::onAnchorClick, enabled = enabled)
        NgExpandableActionMenu(menuState.expanded, menuState::onDismissRequest,
            actions.mapIndexed { index, action ->
                NgExpandableActionMenuItem(index + 1, 0, action.icon, title = action.title, danger = action.danger, enabled = enabled)
            },
            variant = variant,
            menuContainerColor = if (listening) Color(NgTheme.colors.surfaceContainerHigh) else null,
            properties = if (variant == NgExpandableActionMenuVariant.SIDE_SLIDE) {
                PopupProperties(focusable = true, clippingEnabled = false)
            } else PopupProperties(),
            onItemClick = { item ->
                menuState.close()
                if (enabled) actions.getOrNull(item.itemId - 1)?.action?.invoke()
            })
    }
}
