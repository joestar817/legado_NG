package io.legado.app.ui.book.bgm

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.help.bgm.*
import kotlinx.coroutines.flow.collect
import java.util.Locale

/** Shared routes, callbacks and dialogs; each host owns its model and audition lifecycle. */
@Composable
internal fun BgmContent(
    model: BgmViewModel,
    preview: BgmPreviewController,
    selectingForPlayback: Boolean,
    onClose: () -> Unit,
    listening: Boolean = false,
) {
        val context = LocalContext.current
        val playback by BgmRepository.playback.collectAsState()
        val settings by BgmRepository.settings.collectAsState()
        val previewState by preview.state.collectAsState()
        var previewTrack by remember { mutableStateOf<MusicTrack?>(null) }
        val page by model.page.collectAsState()
        val library by model.library.collectAsState()
        val accounts by model.accounts.collectAsState()
        val storage by model.storage.collectAsState()
        val accountBusy by model.accountBusy.collectAsState()
        val accountProfile by model.accountProfile.collectAsState()
        val loginBitmap by model.loginBitmap.collectAsState()
        val loginStatus by model.loginStatus.collectAsState()
        val accountOpen by model.accountOpen.collectAsState()
        val loginMethod by model.loginMethod.collectAsState()
        val phoneLogin by model.phoneLogin.collectAsState()
        val phoneVerification by model.phoneVerification.collectAsState()
        val importReport by model.importReport.collectAsState()
        val playlistDialog by model.playlistDialog.collectAsState()
        var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
        var removalKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
        val savedPages = rememberSaveableStateHolder()
        val back = {
            val handled = if (listening && model.page.value != BgmPage.LIBRARY &&
                !model.accountOpen.value) false else model.back()
            if (!handled || model.page.value != BgmPage.LIBRARY) preview.stop()
            if (!handled) onClose()
        }
        val audition: (MusicTrack) -> Unit = { track ->
            when {
                previewState.trackKey == track.key && previewState.status == BgmPlaybackStatus.PAUSED -> preview.togglePause()
                previewState.trackKey == track.key &&
                    previewState.status in listOf(BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.LOADING) -> preview.stop()
                else -> { previewTrack = track; preview.play(track) }
            }
        }
        BackHandler(onBack = back)
        LaunchedEffect(model) {
            model.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
        LaunchedEffect(previewState) {
            if (previewState.status == BgmPlaybackStatus.ERROR && previewState.message.isNotBlank()) {
                model.notify(previewState.message)
            }
        }
        // Tabs and playlist details share the same audition; leaving this platform stops it.
        LaunchedEffect(page, library.platform) {
            preview.stop()
        }

        val pageContent: @Composable () -> Unit = {
            when {
                listening && page != BgmPage.LIBRARY -> savedPages.SaveableStateProvider("queue") {
                    BgmListeningQueue(settings, playback,
                        onSelect = { preview.stop(); BgmRepository.select(it) },
                        onClear = { confirmation = "clear" })
                }
                else -> when (page) {
                BgmPage.HOME -> savedPages.SaveableStateProvider("home") {
                    BgmHomeContent(
                        settings, accounts, storageLabel(storage), back,
                        onPlatform = model::openLibrary,
                        selectingForPlayback = selectingForPlayback,
                    )
                }
                BgmPage.LIBRARY -> {
                    val addingTo = library.addingTo
                    val stateKey = if (addingTo != null) "library:${library.platform}:adding:${addingTo.id}:${library.submittedQuery}"
                        else "library:${library.platform}:${library.playlist?.id ?: if (library.searchSubmitted) "search:${library.submittedQuery}" else library.tab.name}"
                    savedPages.SaveableStateProvider(stateKey) {
                        val state = library.copy(
                                previewKey = previewState.trackKey.takeIf {
                                    previewState.status == BgmPlaybackStatus.PLAYING ||
                                        previewState.status == BgmPlaybackStatus.LOADING ||
                                        previewState.status == BgmPlaybackStatus.PAUSED
                                },
                                previewLoading = previewState.status == BgmPlaybackStatus.LOADING,
                                previewPaused = previewState.status == BgmPlaybackStatus.PAUSED,
                                hasExistingQueue = settings.queue.isNotEmpty(),
                                selectingForPlayback = selectingForPlayback,
                            )
                        if (library.platform != MusicPlatform.LOCAL) BgmOnlineLibraryScreen(
                            state = state,
                            addSongsError = playlistDialog?.takeIf { it.isDirectAddition }?.error.orEmpty(),
                            onRetryExistingTracks = model::loadExistingPlaylistTracks,
                            onBack = back,
                            onQuery = model::queryChanged,
                            onSearch = model::search,
                            onSearchExpandedChange = model::setSearchExpanded,
                            onTab = model::selectLibraryTab,
                            onAccount = { preview.stop(); model.openAccount() },
                            onPlaylist = model::openPlaylist,
                            onPreview = { track ->
                                if (previewState.trackKey == track.key && previewState.status == BgmPlaybackStatus.PLAYING) {
                                    preview.togglePause()
                                } else audition(track)
                            },
                            onToggleTrack = model::toggleTrack,
                            onLoadMore = model::loadMore,
                            onRetry = model::retry,
                            onNewPlaylist = model::newPlaylist,
                            onEditPlaylist = model::editPlaylist,
                            onDeletePlaylist = model::confirmDeletePlaylist,
                            onUnsubscribe = model::confirmUnsubscribe,
                            onAddSongs = model::addSongsToCurrentPlaylist,
                            onAddTrack = { model.choosePlaylist(listOf(it)) },
                            onRemoveTrack = { model.confirmRemoveTracks(listOf(it)) },
                            onManage = model::toggleManage,
                            onSelectAll = model::selectAll,
                            onInvertSelection = model::invertSelection,
                            onAddSelected = { model.choosePlaylist() },
                            onRemoveSelected = { model.confirmRemoveTracks() },
                            onApply = { preview.stop(); model.applySelected() },
                            onAppend = { preview.stop(); model.applySelected(append = true) },
                            onUsePlaylist = { preview.stop(); model.usePlaylist() },
                        ) else BgmLocalLibraryContent(
                            state = state,
                            onBack = back,
                            onQuery = model::queryChanged,
                            onSearchExpandedChange = model::setSearchExpanded,
                            onToggleTrack = model::toggleTrack,
                            onPreview = { track ->
                                if (previewState.trackKey == track.key && previewState.status == BgmPlaybackStatus.PLAYING) {
                                    preview.togglePause()
                                } else audition(track)
                            },
                            onSelectAll = model::selectAll,
                            onInvertSelection = model::invertSelection,
                            onApply = { preview.stop(); model.applySelected() },
                            onAppend = { preview.stop(); model.applySelected(append = true) },
                            onAddLocal = { preview.stop(); BgmLocalImportActivity.open(context) },
                            onScanLocal = { preview.stop(); BgmLocalImportActivity.open(context, scan = true) },
                            onCopyLocal = { preview.stop(); BgmLocalImportActivity.open(context, copy = true) },
                            onRelocateLocal = { track -> preview.stop(); BgmLocalImportActivity.open(context, track = track) },
                            onRemoveTrack = { track -> removalKeys = arrayListOf(track.key); confirmation = "remove" },
                            onSort = model::toggleSort,
                            onRemoveSelected = {
                                removalKeys = ArrayList(model.selectedLocalTracks().map(MusicTrack::key))
                                if (removalKeys.isNotEmpty()) confirmation = "remove"
                            },
                            onManage = model::toggleManage,
                        )
                    }
                }
                BgmPage.QUEUE -> savedPages.SaveableStateProvider("queue") {
                    BgmQueueContent(settings, back,
                        onSelect = { BgmRepository.select(it) },
                        onRemove = { BgmRepository.removeFromQueue(it) },
                        onMove = { key, index -> BgmRepository.moveInQueue(key, index) },
                        onMode = { BgmRepository.setRepeatMode(it) },
                        onClear = { confirmation = "clear" })
                }
            }
            }
        }
        val currentPreview = previewTrack?.takeIf {
            previewState.trackKey == it.key && previewState.status in listOf(
                BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.LOADING, BgmPlaybackStatus.PAUSED)
        }
        if (listening) {
            BgmListeningLayout(settings, playback, page, library.platform,
                onCurrent = { preview.stop(); model.openQueue() },
                onPlatform = { preview.stop(); model.openLibrary(it) },
                onPrevious = { preview.stop(); BgmRepository.previous() },
                onNext = { preview.stop(); BgmRepository.next() },
                onDismiss = onClose,
                previewContent = currentPreview?.let { track ->
                    { BgmPreviewBar(track, previewState, preview,
                        Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) }
                },
                content = pageContent)
        } else Box(Modifier.fillMaxSize()) {
            pageContent()
            previewTrack?.takeIf {
                previewState.trackKey == it.key &&
                    previewState.status in listOf(BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.LOADING, BgmPlaybackStatus.PAUSED)
            }?.let { track ->
                BgmPreviewBar(track, previewState, preview,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        playlistDialog?.takeUnless { it.isDirectAddition }?.let { dialog ->
            BgmPlaylistDialog(dialog, model::changePlaylistName, model::changePlaylistDescription,
                onSubmit = { model.submitPlaylistDialog() },
                onChoose = model::submitPlaylistDialog,
                onNew = model::newPlaylist,
                onLoadMore = { model.loadPlaylistChoices(more = true) },
                onRetry = { model.loadPlaylistChoices() },
                onDismiss = model::dismissPlaylistDialog)
        }
        if (accountOpen && phoneVerification == null) {
            BgmAccountDialog(
                platform = library.platform,
                loggedIn = library.loggedIn,
                accountBusy = accountBusy,
                profileState = accountProfile,
                method = loginMethod,
                phoneState = phoneLogin,
                bitmap = loginBitmap,
                status = loginStatus,
                onDismiss = model::closeAccount,
                onLogout = { confirmation = "logout" },
                onRetryProfile = model::loadAccountProfile,
                onMethodChanged = model::setLoginMethod,
                onPhoneChanged = model::setLoginPhone,
                onCountryCodeChanged = model::setLoginCountryCode,
                onCodeChanged = model::setLoginCode,
                onSendCode = model::sendPhoneCode,
                onSubmitCode = model::submitPhoneCode,
                onRefreshQr = model::beginLogin,
            )
        }
        phoneVerification?.let { verification ->
            BgmPhoneVerificationDialog(verification, model::closePhoneVerification,
                onVerified = { model.completePhoneVerification(verification.id) })
        }
        when (confirmation) {
            "remove" -> BgmConfirmDialog(
                "移除本地音乐",
                "移除所选 ${removalKeys.size} 首音乐，原文件保留。" +
                    if (settings.queue.any { it.key in removalKeys }) "\n这些音乐也会移出当前列表。" else "",
                "移除",
                onConfirm = {
                    preview.stop()
                    model.removeLocal(settings.localTracks.filter { it.key in removalKeys })
                    confirmation = null
                },
                onDismiss = { confirmation = null },
            )
            "clear" -> BgmConfirmDialog("清空当前列表", "清空后停止背景音乐，曲库中的音乐保留。", "清空",
                onConfirm = { BgmRepository.clearQueue(); confirmation = null },
                onDismiss = { confirmation = null })
            "logout" -> BgmConfirmDialog("退出登录", "退出当前平台账号？", "退出",
                onConfirm = { model.logout(); confirmation = null },
                onDismiss = { confirmation = null })
        }
        importReport?.let { report ->
            BgmConfirmDialog("添加结果", report, "知道了",
                onConfirm = model::dismissImportReport, onDismiss = model::dismissImportReport, cancelText = null)
        }
    }

private fun storageLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    bytes > 0 -> "${(bytes + 1023) / 1024} KB"
    else -> "0 MB"
}
