package io.legado.app.ui.book.bgm

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.bgm.*
import io.legado.app.help.bgm.qq.QqPhoneVerification
import io.legado.app.utils.QRCodeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx
import java.util.EnumMap

internal enum class BgmPage { HOME, LIBRARY, QUEUE }
internal enum class BgmLoginMethod { PHONE, QR, WECHAT_QR }

internal data class BgmAccountProfileUiState(
    val profile: MusicAccountProfile? = null,
    val loading: Boolean = false,
    val message: String = "",
    val membership: MusicMembershipInfo? = null,
    val membershipLoading: Boolean = false,
    val musicLevel: Int? = null,
    val musicLevelIconUrl: String = "",
)

internal data class BgmPhoneLoginUiState(
    val countryCode: String = "86",
    val phone: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val codeSent: Boolean = false,
    val cooldownSeconds: Int = 0,
    val message: String = "",
    val securityUrl: String = "",
)

/** Owns pending requests and selection across configuration changes; opening a page never plays. */
internal class BgmViewModel : ViewModel() {
    private class LibrarySession(val platform: MusicPlatform) {
        var ui = BgmLibraryUiState(platform,
            supportsPlaylistEditing = platform != MusicPlatform.LOCAL && MusicProviders.get(platform).supportsPlaylistEditing,
            supportsPlaylistUnsubscribe = platform != MusicPlatform.LOCAL && MusicProviders.get(platform).supportsPlaylistUnsubscribe)
        var catalogUi = ui
        var catalogNext: Int? = null
        var catalogLoaded = false
        var playlistQuery = ""
        var playlistSearchExpanded = false
        var rootLoaded = false
        var returnTab = BgmLibraryTab.PLAYLISTS
        var addingReturn: BgmLibraryUiState? = null
        var nextOffset: Int? = null
        var loaded = false
        var rootPlaylists = emptyList<MusicPlaylist>()
        var rootNext: Int? = null
        val selection = linkedMapOf<String, MusicTrack>()
    }

    private val sessions = EnumMap<MusicPlatform, LibrarySession>(MusicPlatform::class.java)
    private val accountEpochs = EnumMap<MusicPlatform, Int>(MusicPlatform::class.java)
    private val accountJobs = EnumMap<MusicPlatform, Job>(MusicPlatform::class.java)
    private var activePlatform = MusicPlatform.NETEASE
    private var requestJob: Job? = null
    private var requestGeneration = 0L
    private var existingTracksJob: Job? = null
    private var existingTracksGeneration = 0L
    private var operationJob: Job? = null
    private var operationGeneration = 0L
    private var queueOperation: BgmQueueOperation? = null
    private var loginJob: Job? = null
    private var qrSession: Pair<MusicProvider, MusicLoginChallenge>? = null
    private var loginGeneration = 0L
    private var accountProfileJob: Job? = null
    private var accountProfileGeneration = 0L
    private var phoneSession: MusicPhoneLoginSession? = null
    private var phoneSessionPlatform: MusicPlatform? = null
    private var cooldownJob: Job? = null
    private val phoneCodeDeadlines = mutableMapOf<Triple<MusicPlatform, String, String>, Long>()
    private var selectingForPlayback = false
    private var localFilterJob: Job? = null
    private var storageJob: Job? = null
    private var importJob: Job? = null
    private var playlistDialogJob: Job? = null
    private var playlistDialogGeneration = 0L
    private val mutablePlaylistDialog = MutableStateFlow<BgmPlaylistDialogState?>(null)
    val playlistDialog = mutablePlaylistDialog.asStateFlow()

    private val mutablePage = MutableStateFlow(BgmPage.HOME)
    val page = mutablePage.asStateFlow()
    private val mutableLibrary = MutableStateFlow(BgmLibraryUiState(activePlatform))
    val library = mutableLibrary.asStateFlow()
    private val mutableAccounts = MutableStateFlow<Map<MusicPlatform, String>>(emptyMap())
    val accounts = mutableAccounts.asStateFlow()
    private val mutableStorage = MutableStateFlow(0L)
    val storage = mutableStorage.asStateFlow()
    private val mutableAccountBusy = MutableStateFlow(false)
    val accountBusy = mutableAccountBusy.asStateFlow()
    private val mutableAccountProfile = MutableStateFlow<BgmAccountProfileUiState?>(null)
    val accountProfile = mutableAccountProfile.asStateFlow()
    private val mutableLoginBitmap = MutableStateFlow<Bitmap?>(null)
    val loginBitmap = mutableLoginBitmap.asStateFlow()
    private val mutableLoginStatus = MutableStateFlow("")
    val loginStatus = mutableLoginStatus.asStateFlow()
    private val mutableAccountOpen = MutableStateFlow(false)
    val accountOpen = mutableAccountOpen.asStateFlow()
    private val mutableLoginMethod = MutableStateFlow(BgmLoginMethod.PHONE)
    val loginMethod = mutableLoginMethod.asStateFlow()
    private val mutablePhoneLogin = MutableStateFlow(BgmPhoneLoginUiState())
    val phoneLogin = mutablePhoneLogin.asStateFlow()
    private val mutablePhoneVerification = MutableStateFlow<QqPhoneVerification?>(null)
    val phoneVerification = mutablePhoneVerification.asStateFlow()
    private val mutableImportReport = MutableStateFlow<String?>(null)
    val importReport = mutableImportReport.asStateFlow()
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()

    init {
        viewModelScope.launch {
            BgmRepository.settings.map { it.localTracks }.distinctUntilChanged().collect {
                refreshLocal()
                refreshStorage()
            }
        }
        refreshStorage()
        refreshAccounts()
    }

    private fun session(platform: MusicPlatform = activePlatform) =
        sessions.getOrPut(platform) { LibrarySession(platform) }

    private fun publish(value: LibrarySession = session()) {
        if (value.platform == activePlatform) mutableLibrary.value = value.ui
    }

    fun notify(message: String) { notices.trySend(message) }

    fun configurePlaybackSelection(enabled: Boolean) { selectingForPlayback = enabled }

    /** Explicit dismissal, not configuration teardown: close the commit gate immediately. */
    fun cancelPlaybackSelectionWork() {
        cancelRequests()
        cancelOperation()
        cancelLogin()
        cancelAccountProfile(clear = true)
    }

    fun refreshAccounts() {
        listOf(MusicPlatform.NETEASE, MusicPlatform.QQ).forEach { platform ->
            if (accountJobs[platform]?.isActive == true) return@forEach
            val epoch = accountEpochs[platform] ?: 0
            accountJobs[platform] = viewModelScope.launch {
                val loggedIn = try {
                    withContext(Dispatchers.IO) { MusicProviders.get(platform).isLoggedIn() }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if ((accountEpochs[platform] ?: 0) != epoch) return@launch
                mutableAccounts.value = mutableAccounts.value + (platform to when (loggedIn) {
                    true -> "已登录"
                    false -> "未登录"
                    null -> "账号状态未知"
                })
                if (loggedIn != null) {
                    val value = session(platform)
                    value.ui = value.ui.copy(loggedIn = loggedIn)
                    publish(value)
                    if (platform == activePlatform && mutableAccountOpen.value) {
                        if (!loggedIn) cancelAccountProfile(clear = true)
                        else if (mutableAccountProfile.value == null) loadAccountProfile()
                    }
                }
            }
        }
    }

    fun openLibrary(platform: MusicPlatform) {
        cancelExistingPlaylistTracks()
        cancelAccountProfile(clear = true)
        cancelRequests()
        cancelOperation()
        activePlatform = platform
        mutablePage.value = BgmPage.LIBRARY
        val value = session()
        value.selection.clear()
        value.ui = value.ui.copy(selectedKeys = emptySet(), message = "", managing = false, addingTo = null,
            existingTrackIds = emptySet(), checkingExistingTracks = false, existingTracksError = "")
        value.addingReturn = null
        publish()
        if (platform == MusicPlatform.LOCAL) refreshLocal()
        else if (!value.loaded) load()
    }

    fun openQueue() {
        if (!selectingForPlayback) return
        cancelRequests()
        cancelOperation()
        cancelAccountProfile(clear = true)
        val value = session()
        value.selection.clear()
        value.ui = value.ui.copy(selectedKeys = emptySet())
        publish(value)
        mutablePage.value = BgmPage.QUEUE
    }

    fun openAccount() {
        if (mutablePlaylistDialog.value?.busy == true) return
        cancelRequests()
        cancelOperation()
        cancelLogin()
        cancelAccountProfile(clear = true)
        mutableLoginMethod.value = BgmLoginMethod.PHONE
        mutablePhoneLogin.value = BgmPhoneLoginUiState()
        mutableAccountOpen.value = true
        loadAccountProfile()
    }

    fun closeAccount() {
        cancelAccountProfile(clear = true)
        cancelLogin()
        mutableAccountOpen.value = false
        mutablePhoneLogin.value = BgmPhoneLoginUiState()
        if (mutablePage.value == BgmPage.LIBRARY && !session().loaded) load()
    }

    fun loadAccountProfile() {
        if (!mutableAccountOpen.value || !session().ui.loggedIn || mutableAccountBusy.value) return
        val platform = activePlatform
        val provider = MusicProviders.get(platform)
        if (!provider.supportsAccountProfile || mutableAccountProfile.value?.loading == true ||
            mutableAccountProfile.value?.membershipLoading == true) return
        cancelAccountProfile(clear = false)
        val generation = accountProfileGeneration
        val epoch = accountEpochs[platform] ?: 0
        mutableAccountProfile.value = (mutableAccountProfile.value ?: BgmAccountProfileUiState())
            .copy(loading = true, message = "")
        accountProfileJob = viewModelScope.launch {
            fun isCurrent() = generation == accountProfileGeneration && platform == activePlatform &&
                epoch == (accountEpochs[platform] ?: 0) && mutableAccountOpen.value && session(platform).ui.loggedIn
            try {
                val profile = withTimeout(25_000) {
                    withContext(Dispatchers.IO) { provider.accountProfile() }
                }
                ensureActive()
                if (isCurrent()) {
                    mutableAccountProfile.value = BgmAccountProfileUiState(profile = profile,
                        message = if (profile.detailsLoaded) "" else "部分资料暂未获取", membershipLoading = true)
                    loadAccountExtras(provider, profile.userId, ::isCurrent)
                }
            } catch (_: TimeoutCancellationException) {
                if (isCurrent()) mutableAccountProfile.value = mutableAccountProfile.value?.copy(
                    loading = false, membershipLoading = false, message = "资料读取超时")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent()) mutableAccountProfile.value = mutableAccountProfile.value?.copy(
                    loading = false, membershipLoading = false, message = if (error is MusicProviderException) {
                        error.message ?: "账号资料暂不可用"
                    } else "账号资料暂不可用")
            }
        }
    }

    private suspend fun loadAccountExtras(provider: MusicProvider, userId: String, isCurrent: () -> Boolean) {
        var extras = MusicAccountExtras()
        val failed = try {
            extras = withTimeout(8_000) {
                withContext(Dispatchers.IO) { provider.accountExtras(userId) }
            }
            false
        } catch (_: TimeoutCancellationException) {
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            true
        }
        val current = mutableAccountProfile.value ?: return
        if (!isCurrent() || current.profile?.userId != userId) return
        mutableAccountProfile.value = current.copy(membership = extras.membership,
            musicLevel = extras.musicLevel, musicLevelIconUrl = extras.musicLevelIconUrl,
            membershipLoading = false,
            message = if (failed || !extras.membershipLoaded || !extras.musicLevelLoaded) {
                current.message.ifBlank { "等级资料暂未获取" }
            } else current.message)
    }

    private fun cancelAccountProfile(clear: Boolean) {
        accountProfileGeneration++
        accountProfileJob?.cancel()
        accountProfileJob = null
        mutableAccountProfile.value = if (clear) null else mutableAccountProfile.value?.copy(
            loading = false, membershipLoading = false)
    }

    fun back(): Boolean {
        mutablePlaylistDialog.value?.let { dialog ->
            if (dialog.busy) return true
            dismissPlaylistDialog()
            if (!dialog.isDirectAddition) return true
        }
        if (mutableAccountOpen.value) {
            closeAccount()
            return true
        }
        cancelOperation()
        when (mutablePage.value) {
            BgmPage.HOME -> return false
            BgmPage.QUEUE -> mutablePage.value = BgmPage.HOME
            BgmPage.LIBRARY -> {
                val value = session()
                if (value.ui.addingTo != null && value.addingReturn != null) {
                    cancelRequests()
                    restoreAddingPlaylist(value)
                    publish(value)
                    load()
                    return true
                }
                if (value.ui.playlist == null && value.ui.searchExpanded) {
                    setSearchExpanded(false)
                    return true
                }
                cancelRequests()
                value.selection.clear()
                when {
                    value.ui.managing -> {
                        value.ui = value.ui.copy(managing = false, selectedKeys = emptySet())
                        publish()
                    }
                    value.ui.playlist != null -> {
                        restoreRoot(value, value.returnTab)
                        if (!value.loaded) load()
                    }
                    value.ui.tab == BgmLibraryTab.CATALOG && value.ui.searchSubmitted -> {
                        value.ui = value.ui.copy(query = "", submittedQuery = "", searchSubmitted = false,
                            tracks = emptyList(), selectedKeys = emptySet(), hasMore = false, message = "")
                        value.nextOffset = null
                        saveRoot(value)
                        publish()
                    }
                    else -> {
                        value.ui = value.ui.copy(selectedKeys = emptySet())
                        mutablePage.value = BgmPage.HOME
                    }
                }
            }
        }
        return true
    }

    fun queryChanged(query: String) {
        val value = session()
        value.ui = value.ui.copy(query = query)
        publish()
        if (activePlatform == MusicPlatform.LOCAL) refreshLocal()
    }

    fun setSearchExpanded(expanded: Boolean) {
        val value = session()
        if (value.ui.playlist != null) return
        value.ui = value.ui.copy(searchExpanded = expanded)
        if (value.platform == MusicPlatform.LOCAL) {
            if (expanded) publish(value) else queryChanged("")
            return
        }
        if (!expanded) {
            value.ui = value.ui.copy(query = "")
            if (value.ui.tab == BgmLibraryTab.CATALOG) {
                // An empty catalogue search cancels pending results and restores the empty state.
                search()
                return
            }
            saveRoot(value)
        }
        publish(value)
    }

    fun search() {
        val value = session()
        if (activePlatform == MusicPlatform.LOCAL) return
        if (value.ui.tab == BgmLibraryTab.PLAYLISTS && value.ui.playlist == null) return
        cancelRequests()
        val query = value.ui.query.trim()
        value.loaded = false
        if (value.ui.addingTo == null) value.selection.clear()
        value.ui = value.ui.copy(query = query, submittedQuery = query, playlist = null, searchSubmitted = query.isNotEmpty(),
            tracks = emptyList(), playlists = emptyList(),
            selectedKeys = value.selection.keys.toSet(), message = "", hasMore = false)
        value.nextOffset = null
        publish()
        load()
    }

    fun openPlaylist(playlist: MusicPlaylist) {
        cancelRequests()
        val value = session()
        if (value.ui.playlist == null) {
            saveRoot(value)
            value.returnTab = value.ui.tab
        }
        value.loaded = false
        value.selection.clear()
        value.ui = value.ui.copy(playlist = playlist, query = "", searchExpanded = false, searchSubmitted = false,
            tracks = emptyList(), playlists = emptyList(), selectedKeys = emptySet(), hasMore = false,
            message = "")
        value.nextOffset = null
        publish()
        load()
    }

    fun selectLibraryTab(tab: BgmLibraryTab) {
        val value = session()
        if (value.ui.addingTo != null) return
        if (value.platform == MusicPlatform.LOCAL || (value.ui.playlist == null && value.ui.tab == tab)) return
        cancelRequests()
        if (value.ui.playlist == null) saveRoot(value)
        value.addingReturn = null
        restoreRoot(value, tab)
        if (!value.loaded) load()
    }

    private fun saveRoot(value: LibrarySession) {
        if (value.ui.playlist != null || value.ui.addingTo != null) return
        if (value.ui.tab == BgmLibraryTab.CATALOG) {
            value.catalogUi = value.ui.copy(addingTo = null)
            value.catalogNext = value.nextOffset
            value.catalogLoaded = value.loaded
        } else {
            value.playlistQuery = value.ui.query
            value.playlistSearchExpanded = value.ui.searchExpanded
        }
    }

    private fun restoreRoot(value: LibrarySession, tab: BgmLibraryTab) {
        value.selection.clear()
        val common = value.ui
        value.ui = if (tab == BgmLibraryTab.CATALOG) value.catalogUi.copy(loggedIn = common.loggedIn,
            managing = false, selectedKeys = emptySet(), loading = false, loadingMore = false)
        else common.copy(tab = tab, playlist = null, query = value.playlistQuery,
            searchExpanded = value.playlistSearchExpanded,
            searchSubmitted = false, submittedQuery = "", tracks = emptyList(),
            playlists = value.rootPlaylists, managing = false, selectedKeys = emptySet(),
            message = "", loading = false, loadingMore = false, hasMore = value.rootNext != null, addingTo = null)
        value.nextOffset = if (tab == BgmLibraryTab.CATALOG) value.catalogNext else value.rootNext
        value.loaded = if (tab == BgmLibraryTab.CATALOG) value.catalogLoaded else value.rootLoaded
        publish(value)
    }

    fun addSongsToCurrentPlaylist() {
        val value = session()
        if (selectingForPlayback || value.ui.mutationBusy || value.ui.loadingAll) return
        val playlist = value.ui.playlist?.takeIf { it.editable } ?: return
        cancelRequests()
        val detail = value.ui
        restoreRoot(value, BgmLibraryTab.CATALOG)
        value.addingReturn = detail
        value.ui = value.ui.copy(addingTo = playlist, searchExpanded = true,
            existingTrackIds = detail.tracks.mapTo(HashSet(), MusicTrack::id))
        publish(value)
        loadExistingPlaylistTracks()
        if (!value.loaded) load()
    }

    fun loadExistingPlaylistTracks() {
        val value = session()
        val target = value.ui.addingTo ?: return
        if (value.ui.mutationBusy) return
        cancelExistingPlaylistTracks()
        val generation = existingTracksGeneration
        val epoch = accountEpochs[value.platform] ?: 0
        value.ui = value.ui.copy(checkingExistingTracks = true, existingTracksError = "")
        publish(value)
        existingTracksJob = viewModelScope.launch {
            fun isCurrent() = generation == existingTracksGeneration &&
                value.platform == activePlatform && (accountEpochs[value.platform] ?: 0) == epoch &&
                value.ui.addingTo?.key == target.key
            try {
                val ids = withTimeout(15_000) {
                    withContext(Dispatchers.IO) { MusicProviders.get(value.platform).playlistTrackIds(target) }
                }
                ensureActive()
                if (!isCurrent()) return@launch
                value.selection.entries.removeAll { it.value.id in ids }
                value.ui = value.ui.copy(existingTrackIds = ids, selectedKeys = value.selection.keys.toSet())
            } catch (_: TimeoutCancellationException) {
                if (isCurrent()) value.ui = value.ui.copy(existingTracksError = "已有歌曲核对超时，可重试")
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                if (isCurrent()) value.ui = value.ui.copy(existingTracksError = "已有歌曲状态获取失败，可重试")
            } finally {
                if (isCurrent()) {
                    value.ui = value.ui.copy(checkingExistingTracks = false)
                    publish(value)
                }
            }
        }
    }

    private fun cancelExistingPlaylistTracks() {
        existingTracksGeneration++
        existingTracksJob?.cancel()
        existingTracksJob = null
    }

    private fun restoreAddingPlaylist(value: LibrarySession) {
        val detail = value.addingReturn ?: return
        cancelExistingPlaylistTracks()
        value.selection.clear()
        value.ui = detail.copy(selectedKeys = emptySet(), managing = false,
            loading = false, loadingMore = false, mutationBusy = value.ui.mutationBusy)
        value.addingReturn = null
        value.loaded = false
        value.nextOffset = null
    }

    fun loadMore() {
        if (!session().ui.loading) session().nextOffset?.let { load(it) }
    }

    fun retry() { load() }

    private fun cancelRequests() {
        requestGeneration++
        requestJob?.cancel()
        sessions.values.forEach { it.ui = it.ui.copy(loading = false, loadingMore = false) }
        publish()
    }

    private fun load(offset: Int = 0) {
        if (activePlatform == MusicPlatform.LOCAL) return
        cancelRequests()
        val generation = requestGeneration
        val value = session()
        val snapshot = value.ui
        if (snapshot.playlist == null && snapshot.tab == BgmLibraryTab.CATALOG && !snapshot.searchSubmitted) {
            value.loaded = true
            value.nextOffset = null
            value.ui = value.ui.copy(loading = false, loadingMore = false, hasMore = false)
            saveRoot(value)
            publish(value)
            return
        }
        val submittedQuery = snapshot.submittedQuery
        value.ui = value.ui.copy(loading = true, loadingMore = offset > 0, message = "")
        publish(value)
        requestJob = viewModelScope.launch {
            try {
                val provider = MusicProviders.get(value.platform)
                val loggedIn = withContext(Dispatchers.IO) { provider.isLoggedIn() }
                if (generation != requestGeneration) return@launch
                mutableAccounts.value = mutableAccounts.value + (value.platform to if (loggedIn) "已登录" else "未登录")
                value.ui = value.ui.copy(loggedIn = loggedIn)
                when {
                    snapshot.playlist != null -> {
                        val result = withContext(Dispatchers.IO) { provider.playlistTracks(snapshot.playlist, offset) }
                        ensureActive()
                        if (generation != requestGeneration) return@launch
                        value.ui = value.ui.copy(tracks = ((if (offset == 0) emptyList() else value.ui.tracks) +
                            result.items).distinctBy(MusicTrack::key), playlists = emptyList(),
                            playlist = snapshot.playlist.copy(trackCount = result.total ?: snapshot.playlist.trackCount))
                        value.nextOffset = advancingOffset(offset, result.nextOffset)
                    }
                    snapshot.searchSubmitted -> {
                        val result = withContext(Dispatchers.IO) { provider.search(submittedQuery, offset) }
                        ensureActive()
                        if (generation != requestGeneration) return@launch
                        value.ui = value.ui.copy(tracks = ((if (offset == 0) emptyList() else value.ui.tracks) +
                            result.items).distinctBy(MusicTrack::key), playlists = emptyList())
                        value.nextOffset = advancingOffset(offset, result.nextOffset)
                    }
                    !loggedIn -> {
                        value.rootPlaylists = emptyList()
                        value.rootNext = null
                        value.rootLoaded = true
                        value.nextOffset = null
                        value.ui = value.ui.copy(playlists = emptyList(), tracks = emptyList())
                    }
                    else -> {
                        val result = withContext(Dispatchers.IO) { provider.playlists(offset) }
                        ensureActive()
                        if (generation != requestGeneration) return@launch
                        val combined = ((if (offset == 0) emptyList() else value.ui.playlists) + result.items)
                            .distinctBy(MusicPlaylist::key)
                        value.rootPlaylists = combined
                        value.rootLoaded = true
                        value.rootNext = advancingOffset(offset, result.nextOffset)
                        value.nextOffset = value.rootNext
                        value.ui = value.ui.copy(playlists = combined, tracks = emptyList())
                    }
                }
                value.loaded = true
                value.ui = value.ui.copy(hasMore = value.nextOffset != null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == requestGeneration) value.ui = value.ui.copy(message =
                    (error as? MusicProviderException)?.message ?: "加载失败，请重试")
            } finally {
                if (generation == requestGeneration) {
                    value.ui = value.ui.copy(loading = false, loadingMore = false)
                    saveRoot(value)
                    publish(value)
                }
            }
        }
    }

    private fun advancingOffset(current: Int, next: Int?): Int? {
        if (next != null && next <= current) throw MusicProviderException("分页不可用")
        return next
    }

    fun toggleTrack(track: MusicTrack) {
        val value = session()
        if (!canChangeTrackSelection(value.ui)) return
        if (value.ui.addingTo != null && track.id in value.ui.existingTrackIds) return
        if (value.ui.addingTo != null && track.key !in value.selection && value.selection.size >= 100) {
            notify("每次最多选择 100 首")
            return
        }
        if (value.selection.remove(track.key) == null) value.selection[track.key] = track
        value.ui = value.ui.copy(selectedKeys = value.selection.keys.toSet())
        publish()
    }

    fun toggleManage() {
        val value = session()
        value.selection.clear()
        value.ui = value.ui.copy(managing = !value.ui.managing, selectedKeys = emptySet())
        publish()
    }

    fun newPlaylist() {
        if (!session().ui.loggedIn) { openAccount(); return }
        val current = mutablePlaylistDialog.value
        if (current?.busy == true) return
        playlistDialogJob?.cancel()
        playlistDialogGeneration++
        mutablePlaylistDialog.value = BgmPlaylistDialogState(BgmPlaylistAction.CREATE,
            tracks = current?.tracks.orEmpty(), choices = current?.choices.orEmpty())
    }

    fun editPlaylist(playlist: MusicPlaylist) {
        if (!playlist.editable || !session().ui.supportsPlaylistEditing) return
        mutablePlaylistDialog.value = BgmPlaylistDialogState(BgmPlaylistAction.EDIT,
            playlist = playlist, name = playlist.title, description = playlist.description)
    }

    fun confirmDeletePlaylist(playlist: MusicPlaylist) {
        if (!playlist.editable) return
        mutablePlaylistDialog.value = BgmPlaylistDialogState(BgmPlaylistAction.DELETE, playlist)
    }

    fun confirmUnsubscribe(playlist: MusicPlaylist) {
        if (!playlist.collected || !session().ui.supportsPlaylistUnsubscribe) return
        mutablePlaylistDialog.value = BgmPlaylistDialogState(BgmPlaylistAction.UNSUBSCRIBE, playlist)
    }

    fun confirmRemoveTracks(tracks: List<MusicTrack> = session().selection.values.toList()) {
        val playlist = session().ui.playlist?.takeIf { it.editable } ?: return
        if (tracks.isEmpty()) return
        mutablePlaylistDialog.value = BgmPlaylistDialogState(BgmPlaylistAction.REMOVE_TRACKS,
            playlist, tracks)
    }

    fun choosePlaylist(tracks: List<MusicTrack> = session().selection.values.toList()) {
        if (mutablePlaylistDialog.value?.busy == true || session().ui.mutationBusy) return
        val target = session().ui.addingTo
        if (target != null && session().ui.checkingExistingTracks) return
        val additions = if (target == null) tracks else tracks.filterNot { it.id in session().ui.existingTrackIds }
        if (additions.isEmpty()) return
        if (!session().ui.loggedIn) { openAccount(); return }
        if (additions.size > 100) { notify("每次最多选择 100 首"); return }
        mutablePlaylistDialog.value = BgmPlaylistDialogState(BgmPlaylistAction.PICK,
            playlist = target, tracks = additions)
        if (target != null) {
            mutablePlaylistDialog.value = mutablePlaylistDialog.value?.copy(choices = listOf(target))
            submitPlaylistDialog(target)
        } else loadPlaylistChoices()
    }

    fun changePlaylistName(name: String) {
        mutablePlaylistDialog.value?.takeUnless { it.busy }?.let {
            mutablePlaylistDialog.value = it.copy(name = name.take(40), error = "")
        }
    }

    fun changePlaylistDescription(description: String) {
        mutablePlaylistDialog.value?.takeUnless { it.busy }?.let {
            mutablePlaylistDialog.value = it.copy(description = description.take(1000), error = "")
        }
    }

    fun dismissPlaylistDialog() {
        if (mutablePlaylistDialog.value?.busy == true) return
        playlistDialogGeneration++
        playlistDialogJob?.cancel()
        mutablePlaylistDialog.value = null
    }

    fun loadPlaylistChoices(more: Boolean = false) {
        val state = mutablePlaylistDialog.value?.takeIf {
            it.action == BgmPlaylistAction.PICK && !it.busy && !it.loading
        } ?: return
        val offset = if (more) state.nextOffset ?: return else 0
        playlistDialogJob?.cancel()
        val generation = ++playlistDialogGeneration
        val platform = activePlatform
        mutablePlaylistDialog.value = state.copy(loading = true, error = "")
        playlistDialogJob = viewModelScope.launch {
            try {
                val result = MusicProviders.get(platform).playlists(offset)
                ensureActive()
                if (generation != playlistDialogGeneration) return@launch
                mutablePlaylistDialog.value = state.copy(
                    choices = ((if (offset == 0) emptyList() else state.choices) +
                        result.items.filter { it.editable }).distinctBy(MusicPlaylist::key),
                    nextOffset = advancingOffset(offset, result.nextOffset))
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == playlistDialogGeneration) mutablePlaylistDialog.value =
                    state.copy(error = (error as? MusicProviderException)?.message ?: "歌单加载失败，请重试")
            }
        }
    }

    fun submitPlaylistDialog(target: MusicPlaylist? = null) {
        val dialog = mutablePlaylistDialog.value?.takeUnless { it.busy || it.loading } ?: return
        if (dialog.action == BgmPlaylistAction.PICK && target == null) return
        val value = session()
        val provider = MusicProviders.get(value.platform)
        cancelRequests()
        val generation = ++playlistDialogGeneration
        mutablePlaylistDialog.value = dialog.copy(busy = true, error = "")
        value.ui = value.ui.copy(mutationBusy = true)
        publish(value)
        playlistDialogJob = viewModelScope.launch {
            var created: MusicPlaylist? = null
            try {
                when (dialog.action) {
                    BgmPlaylistAction.CREATE -> {
                        created = provider.createPlaylist(dialog.name)
                        value.rootPlaylists = listOf(created!!) + value.rootPlaylists.filterNot { it.key == created!!.key }
                        if (dialog.tracks.isNotEmpty()) {
                            // Keep the newly created target if the following add fails; retry must not create twice.
                            provider.addTracks(created!!, dialog.tracks)
                        }
                    }
                    BgmPlaylistAction.EDIT -> {
                        val original = requireNotNull(dialog.playlist)
                        val edited = provider.editPlaylist(original, dialog.name, dialog.description)
                        value.rootPlaylists = value.rootPlaylists.map { if (it.key == original.key) edited else it }
                        if (value.ui.playlist?.key == original.key) value.ui = value.ui.copy(playlist = edited)
                    }
                    BgmPlaylistAction.DELETE, BgmPlaylistAction.UNSUBSCRIBE -> {
                        val playlist = requireNotNull(dialog.playlist)
                        if (dialog.action == BgmPlaylistAction.DELETE) provider.deletePlaylist(playlist)
                        else provider.unsubscribePlaylist(playlist)
                        value.rootPlaylists = value.rootPlaylists.filterNot { it.key == playlist.key }
                        if (value.ui.playlist?.key == playlist.key) restoreRoot(value, BgmLibraryTab.PLAYLISTS)
                    }
                    BgmPlaylistAction.REMOVE_TRACKS ->
                        provider.removeTracks(requireNotNull(dialog.playlist), dialog.tracks)
                    BgmPlaylistAction.PICK -> provider.addTracks(requireNotNull(target), dialog.tracks)
                }
                ensureActive()
                if (generation != playlistDialogGeneration) return@launch
                mutablePlaylistDialog.value = null
                value.selection.clear()
                value.ui = value.ui.copy(selectedKeys = emptySet(), managing = false)
                if (dialog.isDirectAddition && target?.key == value.ui.addingTo?.key) {
                    restoreAddingPlaylist(value)
                }
                notify(when (dialog.action) {
                    BgmPlaylistAction.CREATE -> if (dialog.tracks.isEmpty()) "歌单已创建" else "已创建并加入歌单"
                    BgmPlaylistAction.EDIT -> "歌单已保存"
                    BgmPlaylistAction.DELETE -> "歌单已删除"
                    BgmPlaylistAction.UNSUBSCRIBE -> "已取消收藏"
                    BgmPlaylistAction.REMOVE_TRACKS -> "已移出歌单"
                    BgmPlaylistAction.PICK -> "已加入歌单"
                })
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == playlistDialogGeneration) {
                    val message = (error as? MusicProviderException)?.message
                        ?: "未能确认修改结果，请先刷新歌单核对"
                    mutablePlaylistDialog.value = if (created != null) BgmPlaylistDialogState(
                        BgmPlaylistAction.PICK, tracks = dialog.tracks,
                        choices = listOf(created!!) + dialog.choices.filterNot { it.key == created!!.key },
                        error = "歌单已创建，歌曲未能添加。$message")
                    else dialog.copy(error = message)
                }
            } finally {
                if (generation == playlistDialogGeneration) {
                    value.rootLoaded = false
                    value.ui = value.ui.copy(mutationBusy = false)
                    publish(value)
                    // A successful write and a failed read are different outcomes; never retry the write here.
                    if (mutablePlaylistDialog.value == null &&
                        (value.ui.playlist != null || value.ui.tab == BgmLibraryTab.PLAYLISTS)) load()
                }
            }
        }
    }

    fun selectAll() {
        changeTrackSelection(BgmTrackSelectionAction.TOGGLE_ALL)
    }

    fun invertSelection() {
        changeTrackSelection(BgmTrackSelectionAction.INVERT)
    }

    private fun canChangeTrackSelection(state: BgmLibraryUiState): Boolean =
        !state.mutationBusy && !state.loadingAll && !state.importing &&
            (state.addingTo == null || (!state.checkingExistingTracks && state.existingTracksError.isBlank()))

    private fun changeTrackSelection(action: BgmTrackSelectionAction) {
        val value = session()
        if (!canChangeTrackSelection(value.ui)) return
        val adding = value.ui.addingTo != null
        val result = selectBgmTracks(value.selection, value.ui.tracks, action,
            excludedIds = if (adding) value.ui.existingTrackIds else emptySet(),
            limit = if (adding) 100 else null)
        value.selection.clear()
        value.selection.putAll(result.tracks)
        value.ui = value.ui.copy(selectedKeys = value.selection.keys.toSet())
        publish(value)
        if (result.limitReached) notify("每次最多选择 100 首")
    }

    fun toggleSort() {
        val value = session(MusicPlatform.LOCAL)
        value.ui = value.ui.copy(sortByName = !value.ui.sortByName)
        refreshLocal()
    }

    fun refreshLocalFiles() { refreshLocal(); refreshStorage() }

    private fun refreshLocal() {
        localFilterJob?.cancel()
        val value = session(MusicPlatform.LOCAL)
        val snapshot = BgmRepository.settings.value.localTracks
        val query = value.ui.query.trim()
        val sortByName = value.ui.sortByName
        localFilterJob = viewModelScope.launch {
            val visible = withContext(Dispatchers.Default) {
                val filtered = snapshot.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
                if (sortByName) filtered.sortedBy { it.title.lowercase() } else filtered.asReversed()
            }
            ensureActive()
            value.selection.keys.retainAll(snapshot.mapTo(HashSet(), MusicTrack::key))
            value.ui = value.ui.copy(tracks = visible, selectedKeys = value.selection.keys.toSet())
            publish(value)
        }
    }

    fun applySelected(append: Boolean = false) {
        if (!selectingForPlayback) return
        val selected = session().selection.values.toList()
        if (selected.isNotEmpty()) applyTracks(append) { selected }
    }

    fun usePlaylist() {
        if (!selectingForPlayback) return
        val playlist = session().ui.playlist ?: return
        applyTracks(false) {
            val result = linkedMapOf<String, MusicTrack>()
            val provider = MusicProviders.get(playlist.platform)
            var cursor: Int? = 0
            var pages = 0
            while (cursor != null) {
                if (++pages > 500) throw MusicProviderException("歌单过大，请分批选择")
                val current = cursor ?: break
                val page = withContext(Dispatchers.IO) { provider.playlistTracks(playlist, current) }
                page.items.forEach { result[it.key] = it }
                cursor = advancingOffset(current, page.nextOffset)
            }
            result.values.toList()
        }
    }

    private fun cancelOperation() {
        operationGeneration++
        queueOperation?.cancel()
        queueOperation = null
        operationJob?.cancel()
        sessions.values.forEach { it.ui = it.ui.copy(loadingAll = false) }
        publish()
    }

    private fun applyTracks(append: Boolean, tracks: suspend () -> List<MusicTrack>) {
        cancelRequests()
        cancelOperation()
        val generation = operationGeneration
        val commit = BgmQueueOperation().also { queueOperation = it }
        val value = session()
        value.ui = value.ui.copy(loadingAll = true, message = "")
        publish(value)
        operationJob = viewModelScope.launch {
            try {
                val selected = tracks()
                if (selected.isEmpty()) throw MusicProviderException("没有可用的音乐")
                if (!append) withTimeout(20_000) { BgmRepository.resolve(selected.first()) }
                ensureActive()
                if (generation != operationGeneration) return@launch
                val saved = if (append) BgmRepository.addToQueue(selected, operation = commit).await()
                    else BgmRepository.replaceQueue(selected, operation = commit).await()
                if (!saved) throw MusicProviderException("无法保存背景音乐设置")
                value.selection.clear()
                value.ui = value.ui.copy(selectedKeys = emptySet())
                if (append) notify("已加入当前列表") else mutablePage.value = BgmPage.QUEUE
            } catch (_: TimeoutCancellationException) {
                notify("音乐加载超时，请重试")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                notify(if (error is MusicProviderException)
                    error.message ?: "音乐暂不可用" else "音乐暂不可用，请重试")
            } finally {
                if (generation == operationGeneration) {
                    queueOperation = null
                    value.ui = value.ui.copy(loadingAll = false)
                    publish(value)
                }
            }
        }
    }

    fun selectedLocalTracks(): List<MusicTrack> = session(MusicPlatform.LOCAL).selection.values.toList()

    fun removeLocal(tracks: List<MusicTrack>) {
        viewModelScope.launch {
            val saved = BgmRepository.removeLocal(tracks).await()
            refreshStorage()
            notify(if (saved) "已移除" else "未能完成副本清理，请重试")
        }
    }

    fun importLocal(uris: List<Uri>) {
        if (uris.isEmpty() || importJob?.isActive == true) return
        val value = session(MusicPlatform.LOCAL)
        value.ui = value.ui.copy(importing = true)
        publish(value)
        importJob = viewModelScope.launch {
            val temporaryGrants = mutableListOf<Uri>()
            val resolver = appCtx.contentResolver
            try {
                withContext(Dispatchers.IO) {
                    val existing = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
                    uris.distinct().filterNot { it in existing }.forEach { uri ->
                        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            .onSuccess { temporaryGrants += uri }
                    }
                }
                val result = BgmRepository.importLocalBatch(uris)
                val summary = buildString {
                    append("已添加 ${result.imported} 首")
                    if (result.duplicates > 0) append("，${result.duplicates} 首已存在")
                    if (result.failed > 0) append("，${result.failed} 首未添加")
                    if (result.skipped > 0) append("，${result.skipped} 首未处理")
                }
                if (result.failures.isEmpty() && result.skipped == 0) notify(summary)
                else mutableImportReport.value = buildString {
                    append(summary)
                    result.failures.take(8).forEach { append("\n${it.name}：${it.reason}") }
                    if (result.failures.size > 8) append("\n其余 ${result.failures.size - 8} 项未添加")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                notify("添加失败，请选择可读取的音频")
            } finally {
                temporaryGrants.forEach { uri ->
                    runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                value.ui = value.ui.copy(importing = false)
                publish(value)
                refreshStorage()
            }
        }
    }

    fun dismissImportReport() { mutableImportReport.value = null }

    private fun refreshStorage() {
        storageJob?.cancel()
        storageJob = viewModelScope.launch {
            val bytes = BgmRepository.localStorageBytes()
            ensureActive()
            mutableStorage.value = bytes
        }
    }

    fun logout() {
        if (mutableAccountBusy.value || mutablePlaylistDialog.value?.busy == true) return
        cancelExistingPlaylistTracks()
        dismissPlaylistDialog()
        cancelAccountProfile(clear = false)
        cancelLogin()
        cancelRequests()
        cancelOperation()
        val platform = activePlatform
        accountJobs[platform]?.cancel()
        accountEpochs[platform] = (accountEpochs[platform] ?: 0) + 1
        mutableAccountBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { MusicProviders.get(platform).logout() }
                val value = session(platform)
                value.selection.clear()
                value.rootPlaylists = emptyList()
                value.rootNext = null
                value.rootLoaded = false
                value.catalogLoaded = false
                value.catalogNext = null
                value.playlistQuery = ""
                value.playlistSearchExpanded = false
                value.nextOffset = null
                value.loaded = false
                value.ui = BgmLibraryUiState(platform,
                    supportsPlaylistEditing = MusicProviders.get(platform).supportsPlaylistEditing,
                    supportsPlaylistUnsubscribe = MusicProviders.get(platform).supportsPlaylistUnsubscribe)
                value.catalogUi = value.ui
                mutableAccounts.value = mutableAccounts.value + (platform to "未登录")
                mutableAccountProfile.value = null
                publish(value)
                mutableLoginMethod.value = BgmLoginMethod.PHONE
                mutablePhoneLogin.value = BgmPhoneLoginUiState()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                notify("退出失败，请重试")
                if (mutableAccountProfile.value?.profile == null) {
                    mutableAccountProfile.value = mutableAccountProfile.value?.copy(message = "账号资料暂不可用")
                }
            } finally {
                mutableAccountBusy.value = false
            }
        }
    }

    fun beginLogin() {
        val method = if (activePlatform == MusicPlatform.QQ && mutableLoginMethod.value == BgmLoginMethod.WECHAT_QR)
            BgmLoginMethod.WECHAT_QR else BgmLoginMethod.QR
        cancelLogin()
        val generation = loginGeneration
        val platform = activePlatform
        mutableLoginMethod.value = method
        mutableLoginStatus.value = "正在获取二维码…"
        loginJob = viewModelScope.launch {
            val provider = MusicProviders.get(platform)
            var challenge: MusicLoginChallenge? = null
            try {
                withTimeout(180_000) {
                    withContext(Dispatchers.IO) {
                        (if (method == BgmLoginMethod.WECHAT_QR) provider.beginWechatLogin()
                            else provider.beginLogin()).also { challenge = it }
                    }
                    val current = challenge!!
                    qrSession = provider to current
                    val bitmap = withContext(Dispatchers.Default) {
                        current.image?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                                ?: throw MusicProviderException("二维码加载失败，请刷新")
                        } ?: QRCodeUtils.createQRCode(current.qrContent, 480)
                    }
                    ensureActive()
                    if (generation != loginGeneration) return@withTimeout
                    mutableLoginBitmap.value = bitmap
                    mutableLoginStatus.value = "等待扫码"
                    while (true) {
                        if (System.currentTimeMillis() >= current.expiresAt) {
                            mutableLoginStatus.value = "二维码已过期"
                            break
                        }
                        delay(2500)
                        val result = withContext(Dispatchers.IO) { provider.pollLogin(current) }
                        ensureActive()
                        if (generation != loginGeneration) return@withTimeout
                        when (result.state) {
                            MusicLoginState.AUTHENTICATED -> {
                                completeLogin(platform)
                                return@withTimeout
                            }
                            MusicLoginState.WAITING_SCAN -> mutableLoginStatus.value = "等待扫码"
                            MusicLoginState.WAITING_CONFIRMATION -> mutableLoginStatus.value = "请在手机上确认"
                            MusicLoginState.EXPIRED -> { mutableLoginStatus.value = "二维码已过期"; break }
                            MusicLoginState.FAILED -> {
                                mutableLoginStatus.value = result.message.ifBlank { "登录失败，请重新获取" }
                                break
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == loginGeneration) mutableLoginStatus.value = "二维码已过期"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == loginGeneration) mutableLoginStatus.value = loginError(error)
            } finally {
                challenge?.let(provider::cancelLogin)
                if (generation == loginGeneration) {
                    qrSession = null
                    mutableLoginBitmap.value = null
                }
            }
        }
    }

    fun setLoginMethod(method: BgmLoginMethod) {
        if (mutablePhoneLogin.value.busy || method == mutableLoginMethod.value) return
        if (method == BgmLoginMethod.WECHAT_QR && activePlatform != MusicPlatform.QQ) return
        mutableLoginMethod.value = method
        mutablePhoneLogin.value = BgmPhoneLoginUiState()
        if (method != BgmLoginMethod.PHONE) beginLogin() else {
            cancelLogin()
        }
    }

    fun setLoginPhone(value: String) {
        if (mutablePhoneLogin.value.busy) return
        val phone = value.filter(Char::isDigit).take(15)
        if (phone == mutablePhoneLogin.value.phone) return
        val previous = mutablePhoneLogin.value
        cancelLogin()
        mutablePhoneLogin.value = BgmPhoneLoginUiState(
            countryCode = previous.countryCode, phone = phone)
        refreshPhoneCooldown()
    }

    fun setLoginCountryCode(value: String) {
        if (mutablePhoneLogin.value.busy) return
        val country = value.filter(Char::isDigit).take(4)
        if (country == mutablePhoneLogin.value.countryCode) return
        val previous = mutablePhoneLogin.value
        cancelLogin()
        mutablePhoneLogin.value = BgmPhoneLoginUiState(countryCode = country, phone = previous.phone)
        refreshPhoneCooldown()
    }

    fun setLoginCode(value: String) {
        if (!mutablePhoneLogin.value.busy) {
            mutablePhoneLogin.value = mutablePhoneLogin.value.copy(code = value.filter(Char::isDigit).take(8))
        }
    }

    private fun openPhoneVerification() {
        val input = mutablePhoneLogin.value
        val request = phoneSession ?: return
        if (!mutableAccountOpen.value || input.busy || activePlatform != MusicPlatform.QQ ||
            mutableLoginMethod.value != BgmLoginMethod.PHONE || input.securityUrl.isBlank()) return
        try {
            request.requireActive()
            mutablePhoneVerification.value?.cancel()
            mutablePhoneVerification.value = QqPhoneVerification(request.id, input.securityUrl)
        } catch (error: Exception) {
            mutablePhoneLogin.value = input.copy(message = loginError(error))
        }
    }

    fun closePhoneVerification() {
        mutablePhoneVerification.value?.cancel()
        mutablePhoneVerification.value = null
    }

    fun completePhoneVerification(id: String) {
        val verification = mutablePhoneVerification.value ?: return
        val request = phoneSession ?: return
        if (verification.id != id || verification.sessionId != request.id ||
            verification.sourceUrl != mutablePhoneLogin.value.securityUrl ||
            activePlatform != MusicPlatform.QQ || phoneSessionPlatform != activePlatform ||
            mutableLoginMethod.value != BgmLoginMethod.PHONE || !mutableAccountOpen.value) return
        closePhoneVerification()
        requestPhoneCode(reuseSession = true)
    }

    fun sendPhoneCode() = requestPhoneCode(reuseSession = activePlatform == MusicPlatform.QQ &&
        phoneSessionPlatform == MusicPlatform.QQ && phoneSession != null &&
        mutablePhoneLogin.value.securityUrl.isNotBlank())

    private fun requestPhoneCode(reuseSession: Boolean) {
        val input = mutablePhoneLogin.value
        if (!mutableAccountOpen.value || input.busy || mutableLoginMethod.value != BgmLoginMethod.PHONE) return
        val key = phoneCodeKey(input)
        if ((phoneCodeDeadlines[key] ?: 0) > SystemClock.elapsedRealtime()) {
            refreshPhoneCooldown()
            return
        }
        if (!input.countryCode.matches(Regex("[1-9][0-9]{0,3}")) ||
            !input.phone.matches(Regex("[0-9]{5,15}"))) {
            mutablePhoneLogin.value = input.copy(message = "请填写有效的区号和手机号")
            return
        }
        val existing = if (reuseSession) {
            phoneSession?.takeIf {
                phoneSessionPlatform == activePlatform && it.phone == input.phone && it.countryCode == input.countryCode
            } ?: return
        } else null
        if (!reuseSession) cancelLogin()
        val generation = loginGeneration
        val platform = activePlatform
        val provider = MusicProviders.get(platform)
        // Captcha clearance belongs to this device/session; replacing it discards the verification.
        val request = existing ?: MusicPhoneLoginSession(input.phone, input.countryCode)
        phoneSession = request
        phoneSessionPlatform = platform
        mutablePhoneLogin.value = input.copy(busy = true, codeSent = false, code = "",
            message = "", securityUrl = "")
        loginJob = viewModelScope.launch {
            var needsVerification = false
            try {
                val result = withTimeout(30_000) {
                    withContext(Dispatchers.IO) { provider.sendPhoneCode(request) }
                }
                ensureActive()
                if (generation != loginGeneration) return@launch
                val sent = result.state == MusicPhoneCodeState.SENT
                val qqVerification = platform == MusicPlatform.QQ && result.state == MusicPhoneCodeState.SECURITY_REQUIRED
                needsVerification = qqVerification && result.securityUrl.isNotBlank()
                mutablePhoneLogin.value = mutablePhoneLogin.value.copy(
                    codeSent = sent,
                    message = when {
                        needsVerification -> ""
                        qqVerification -> "安全验证暂不可用，请重新获取"
                        else -> result.message.ifBlank { if (sent) "验证码已发送" else "未能发送验证码，请重试" }
                    },
                    securityUrl = result.securityUrl,
                )
                if (sent || result.state == MusicPhoneCodeState.RATE_LIMITED) {
                    phoneCodeDeadlines.entries.removeAll { it.value <= SystemClock.elapsedRealtime() }
                    phoneCodeDeadlines[key] = SystemClock.elapsedRealtime() + 60_000
                    refreshPhoneCooldown()
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == loginGeneration) {
                    mutablePhoneLogin.value = mutablePhoneLogin.value.copy(message = "请求超时，请稍后重试")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == loginGeneration) {
                    mutablePhoneLogin.value = mutablePhoneLogin.value.copy(message = loginError(error))
                }
            } finally {
                if (generation == loginGeneration) {
                    mutablePhoneLogin.value = mutablePhoneLogin.value.copy(busy = false)
                    if (needsVerification) openPhoneVerification()
                }
            }
        }
    }

    private fun phoneCodeKey(input: BgmPhoneLoginUiState = mutablePhoneLogin.value) =
        Triple(activePlatform, input.countryCode, input.phone)

    private fun refreshPhoneCooldown() {
        cooldownJob?.cancel()
        val key = phoneCodeKey()
        cooldownJob = viewModelScope.launch {
            while (mutableAccountOpen.value && mutableLoginMethod.value == BgmLoginMethod.PHONE &&
                phoneCodeKey() == key) {
                val remaining = ((phoneCodeDeadlines[key] ?: 0) - SystemClock.elapsedRealtime())
                    .coerceAtLeast(0)
                mutablePhoneLogin.value = mutablePhoneLogin.value.copy(
                    cooldownSeconds = ((remaining + 999) / 1000).toInt())
                if (remaining == 0L) break
                delay(1000)
            }
        }
    }

    fun submitPhoneCode() {
        val input = mutablePhoneLogin.value
        val request = phoneSession ?: return
        val platform = phoneSessionPlatform ?: return
        if (!mutableAccountOpen.value || input.busy || !input.codeSent) return
        if (!input.code.matches(Regex("[0-9]{4,8}"))) {
            mutablePhoneLogin.value = input.copy(message = "请填写有效的验证码")
            return
        }
        val generation = loginGeneration
        mutablePhoneLogin.value = input.copy(busy = true, message = "", securityUrl = "")
        loginJob = viewModelScope.launch {
            try {
                val result = withTimeout(30_000) {
                    withContext(Dispatchers.IO) { MusicProviders.get(platform).loginPhoneCode(request, input.code) }
                }
                ensureActive()
                if (generation != loginGeneration) return@launch
                if (result.state == MusicLoginState.AUTHENTICATED) completeLogin(platform)
                else mutablePhoneLogin.value = mutablePhoneLogin.value.copy(
                    message = result.message.ifBlank { "登录未完成，请重试" })
            } catch (_: TimeoutCancellationException) {
                if (generation == loginGeneration) {
                    mutablePhoneLogin.value = mutablePhoneLogin.value.copy(message = "登录超时，请重试")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == loginGeneration) {
                    mutablePhoneLogin.value = mutablePhoneLogin.value.copy(message = loginError(error))
                }
            } finally {
                if (generation == loginGeneration) {
                    mutablePhoneLogin.value = mutablePhoneLogin.value.copy(busy = false)
                }
            }
        }
    }

    private fun loginError(error: Exception): String =
        if (error is MusicProviderException) error.message ?: "登录失败，请重试"
        else "网络请求失败，请重试"

    private fun completeLogin(platform: MusicPlatform) {
        cancelAccountProfile(clear = true)
        accountEpochs[platform] = (accountEpochs[platform] ?: 0) + 1
        accountJobs[platform]?.cancel()
        mutableAccounts.value = mutableAccounts.value + (platform to "已登录")
        val value = session(platform)
        value.ui = value.ui.copy(loggedIn = true)
        value.loaded = false
        value.rootLoaded = false
        mutableAccountOpen.value = false
        mutablePhoneLogin.value = BgmPhoneLoginUiState()
        cooldownJob?.cancel()
        load()
        if (value.ui.addingTo != null) loadExistingPlaylistTracks()
    }

    private fun cancelLogin() {
        closePhoneVerification()
        loginGeneration++
        loginJob?.cancel()
        qrSession?.let { (provider, challenge) -> provider.cancelLogin(challenge) }
        qrSession = null
        cooldownJob?.cancel()
        phoneSession?.let { request ->
            phoneSessionPlatform?.let { MusicProviders.get(it).cancelPhoneLogin(request) }
        }
        phoneSession = null
        phoneSessionPlatform = null
        mutableLoginBitmap.value = null
        mutableLoginStatus.value = ""
        mutablePhoneLogin.value = mutablePhoneLogin.value.copy(busy = false)
    }

    override fun onCleared() {
        cancelExistingPlaylistTracks()
        cancelAccountProfile(clear = true)
        queueOperation?.cancel()
        cancelLogin()
        super.onCleared()
    }
}
