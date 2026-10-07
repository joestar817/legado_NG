package io.legado.app.ui.main.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.constant.EventBus
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.AudioPlay
import io.legado.app.model.ListeningHistoryEntry
import io.legado.app.model.ListeningHistorySource
import io.legado.app.model.ListeningHistoryStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.read.aloud.ListeningPlayback
import io.legado.app.ui.book.read.aloud.ReadAloudLauncher
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.utils.eventObservable
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class HomeFragment() : Fragment(), MainFragmentInterface {
    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")
    private val viewModel by viewModels<HomeViewModel>()
    private val activityViewModel by activityViewModels<MainViewModel>()
    private var bottomInsetPx by mutableIntStateOf(0)
    private var scrollToTopToken by mutableIntStateOf(0)
    private var listeningState by mutableStateOf(HomeListeningState())
    private var listeningRefreshJob: Job? = null
    private var historyLaunchJob: Job? = null
    private var updatesLaunchJob: Job? = null
    internal val hasListeningWidget: Boolean
        get() = viewModel.state.value.displayedWidgets.any { it.typeId == "listening" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            NgAppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val readingState by viewModel.readingState.collectAsStateWithLifecycle()
                val calendarState by viewModel.calendarState.collectAsStateWithLifecycle()
                val calendarSelections by viewModel.calendarSelections.collectAsStateWithLifecycle()
                val updatesState by viewModel.updatesState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    bottomInsetPx = bottomInsetPx,
                    scrollToTopToken = scrollToTopToken,
                    onEdit = viewModel::beginEditing,
                    onEditWidget = viewModel::beginSingleEditing,
                    onCancel = { viewModel.cancelEditing() },
                    onSave = viewModel::saveEditing,
                    onAdd = viewModel::addWidget,
                    onVariantSave = viewModel::saveVariant,
                    onSuiteSave = viewModel::saveSuite,
                    onRemove = viewModel::removeWidget,
                    onMove = viewModel::moveWidget,
                    onReset = viewModel::resetLayout,
                    listeningState = listeningState,
                    onListeningAction = ::onListeningAction,
                    readingState = readingState,
                    onOpenReading = ::openReadingRecords,
                    calendarState = calendarState,
                    calendarSelections = calendarSelections,
                    onCalendarAction = viewModel::onCalendarAction,
                    updatesState = updatesState,
                    onOpenUpdatesBook = ::openUpdatesBook,
                    onRetryUpdates = ::refreshUpdates,
                    onPreviewTypesChanged = viewModel::setWidgetPreviewTypes,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventObservable<String>(EventBus.AUDIO_SUB_TITLE).observe(viewLifecycleOwner) {
            if (viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                refreshListeningState()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.observeReadingRecords()
                }
                launch {
                    viewModel.observeCalendarRecords()
                }
                launch {
                    viewModel.observeUpdates()
                }
                launch {
                    activityViewModel.bookNewsRefreshState.collect { check ->
                        viewModel.updateUpdatesCheckState(check)
                    }
                }
                launch {
                    viewModel.state.map { state ->
                        !state.editing && state.widgets.any { it.typeId == "updates" }
                    }.distinctUntilChanged().collect { enabled ->
                        if (enabled) activityViewModel.refreshBookNews()
                    }
                }
                launch {
                    ReadAloudMiniPlayer.mainPlaybackUpdates.collect { refreshListeningState() }
                }
                launch {
                    AudioPlay.loading.collect { refreshListeningState() }
                }
                launch {
                    ListeningHistoryStore.current.collect { refreshListeningState() }
                }
                launch {
                    viewModel.state.collect { state ->
                        if (state.editing) {
                            historyLaunchJob?.cancel()
                            updatesLaunchJob?.cancel()
                        }
                        (activity as? MainActivity)?.refreshHomeListeningCapsule()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.resolveFloatingBottomContentInset { bottomInsetPx = it }
        refreshListeningState()
        (activity as? MainActivity)?.refreshHomeListeningCapsule()
    }

    private fun refreshListeningState() {
        listeningRefreshJob?.cancel()
        // A running session with temporarily missing metadata still blocks historical playback.
        if (ReadAloudMiniPlayer.currentMainPlayback() != null) {
            listeningState = readHomeListeningState()
            return
        }
        if (historyLaunchJob?.isActive == true) return
        val entry = ListeningHistoryStore.current.value
        if (entry == null) {
            listeningState = HomeListeningState()
            return
        }
        listeningRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            val history = withContext(IO) { readLastHomeListeningState(entry) }
            if (!isHomeResumed()) return@launch
            if (ReadAloudMiniPlayer.currentMainPlayback() != null) {
                listeningState = readHomeListeningState()
            } else if (ListeningHistoryStore.current.value == entry) {
                listeningState = history
            }
        }
    }

    private fun openReadingRecords() {
        if (viewModel.state.value.editing || !isHomeResumed()) return
        activity?.startActivity<ReadRecordActivity>()
    }

    private fun refreshUpdates() {
        if (viewModel.state.value.editing || !isHomeResumed() ||
            viewModel.state.value.widgets.none { it.typeId == "updates" }
        ) return
        viewModel.retryUpdates()
        activityViewModel.refreshBookNews(force = true)
    }

    private fun openUpdatesBook(expected: HomeUpdateBook) {
        if (viewModel.state.value.editing || !isHomeResumed() || updatesLaunchJob?.isActive == true) return
        val bookUrl = expected.bookUrl
        val origin = expected.origin
        updatesLaunchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val book = withContext(IO) { appDb.bookDao.getBook(bookUrl) } ?: return@launch
                if (viewModel.state.value.editing || !isHomeResumed()) return@launch
                if (book.origin != origin || book.isLocal || book.isNotShelf) {
                    viewModel.retryUpdates()
                    return@launch
                }
                startActivityForBook(book)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.put("打开今日书讯书籍失败", error)
                if (isHomeResumed()) activity?.toastOnUi(error.localizedMessage ?: "打开书籍失败")
            }
        }
    }

    private fun onListeningAction(expected: HomeListeningState, action: HomeListeningAction) {
        val host = activity as? MainActivity ?: return
        if (viewModel.state.value.editing ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        if (expected.history != null) {
            launchLastPlayback(host, expected, action)
            return
        }
        val current = readHomeListeningState()
        // Ignore a stale card after a service/book switch instead of controlling another book.
        if (!matchesHomeListeningTarget(expected, current)) {
            refreshListeningState()
            return
        }
        if (current.preparing && action != HomeListeningAction.OPEN) return
        when (action) {
            HomeListeningAction.OPEN -> ReadAloudMiniPlayer.openMainPlayer(host)
            HomeListeningAction.TOGGLE -> ReadAloudMiniPlayer.toggleMainPlayback(host)
            HomeListeningAction.PREVIOUS -> if (current.canPrevious) when (current.playback) {
                ListeningPlayback.READ_ALOUD -> ReadAloud.prevChapter(host)
                ListeningPlayback.AUDIO -> AudioPlay.prev()
                null -> Unit
            }
            HomeListeningAction.NEXT -> if (current.canNext) when (current.playback) {
                ListeningPlayback.READ_ALOUD -> ReadAloud.nextChapter(host)
                ListeningPlayback.AUDIO -> AudioPlay.next()
                null -> Unit
            }
        }
        refreshListeningState()
    }

    private fun canRestoreHistory(entry: ListeningHistoryEntry): Boolean =
        !viewModel.state.value.editing &&
            isHomeResumed() &&
            ReadAloudMiniPlayer.currentMainPlayback() == null && ListeningHistoryStore.current.value == entry

    private fun isHomeResumed(): Boolean =
        viewLifecycleOwnerLiveData.value?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    private fun launchLastPlayback(host: MainActivity, expected: HomeListeningState, action: HomeListeningAction) {
        val entry = expected.history ?: return
        if (action != HomeListeningAction.OPEN && action != HomeListeningAction.TOGGLE) return
        if (historyLaunchJob?.isActive == true) return
        if (!canRestoreHistory(entry)) {
            refreshListeningState()
            return
        }
        listeningRefreshJob?.cancel()
        listeningState = expected.copy(preparing = true)
        historyLaunchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val book = withContext(IO) {
                    readLastHomeListeningState(entry).book
                } ?: return@launch
                if (!canRestoreHistory(entry)) return@launch
                when (entry.source) {
                    ListeningHistorySource.READ_ALOUD -> {
                        if (!ReadAloudLauncher.prepareState(book, inBookshelf = true,
                                chapterChanged = true, restoreProgress = true,
                                restoreChapterIndex = entry.chapterIndex, restorePosition = entry.position,
                                restoreGuard = { canRestoreHistory(entry) })
                        ) {
                            if (canRestoreHistory(entry)) host.toastOnUi(ReadBook.msg ?: "初始化听书失败")
                            return@launch
                        }
                        if (!canRestoreHistory(entry) || ReadBook.book !== book) return@launch
                        if (action == HomeListeningAction.OPEN) {
                            // Viewing history must not overwrite a newer ordinary reading position.
                            if (!ReadAloudLauncher.loadCurrentChapter(host, saveProgress = false)) {
                                host.toastOnUi(ReadBook.msg ?: "加载正文失败")
                                return@launch
                            }
                            if (!canRestoreHistory(entry) || ReadBook.book !== book) return@launch
                            ReadAloudLauncher.openPlayer(host, autoStart = false)
                            return@launch
                        }
                        if (!ReadAloudLauncher.playPreparedHistory(
                                host, book, entry.chapterIndex, restoreGuard = { canRestoreHistory(entry) },
                            )
                        ) return@launch
                    }
                    ListeningHistorySource.AUDIO -> {
                        if (action == HomeListeningAction.OPEN) {
                            host.startActivity<AudioPlayActivity> {
                                putExtra("bookUrl", book.bookUrl)
                                putExtra("inBookshelf", true)
                                putExtra(AudioPlayActivity.EXTRA_RESUME_CHAPTER, entry.chapterIndex)
                                putExtra(AudioPlayActivity.EXTRA_RESUME_POSITION, entry.position)
                            }
                            return@launch
                        }
                        val prepared = withContext(IO) {
                            if (!canRestoreHistory(entry)) return@withContext false
                            AudioPlay.inBookshelf = true
                            AudioPlay.resetData(book)
                            AudioPlay.durChapterIndex = entry.chapterIndex
                            AudioPlay.durChapterPos = entry.position
                            AudioPlay.upDurChapter()
                            AudioPlay.bookSource != null && AudioPlay.durChapter?.index == entry.chapterIndex
                        }
                        if (!canRestoreHistory(entry) || AudioPlay.book !== book) return@launch
                        if (!prepared) {
                            host.toastOnUi("无法恢复上次音频")
                            return@launch
                        }
                        if (!AudioPlay.playFromSavedProgress {
                                canRestoreHistory(entry) && AudioPlay.book === book
                            }
                        ) return@launch
                    }
                }
                // Keep the pending card until the service publishes its real session.
                // A failed start must release the button rather than wait indefinitely.
                if (canRestoreHistory(entry)) {
                    withTimeoutOrNull(1500L) {
                        ReadAloudMiniPlayer.mainPlaybackUpdates.first { !canRestoreHistory(entry) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.put("恢复上次听书失败", error)
                host.toastOnUi("恢复听书失败")
            } finally {
                if (historyLaunchJob === currentCoroutineContext()[Job]) {
                    historyLaunchJob = null
                    if (isHomeResumed()) refreshListeningState()
                }
            }
        }
    }

    override fun onPause() {
        listeningRefreshJob?.cancel()
        historyLaunchJob?.cancel()
        updatesLaunchJob?.cancel()
        super.onPause()
    }

    fun back(): Boolean = viewModel.cancelEditing()

    fun gotoTop() {
        scrollToTopToken++
    }
}
