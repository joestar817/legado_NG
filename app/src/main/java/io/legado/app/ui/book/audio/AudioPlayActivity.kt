package io.legado.app.ui.book.audio

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.model.AudioPlay
import io.legado.app.model.SourceCallBack
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.config.TtsSheetLaunchDebouncer
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class AudioPlayActivity :
    VMBaseActivity<ComposeActivityBinding, AudioPlayViewModel>(
        toolBarTheme = Theme.Dark,
        imageBg = false,
        showOpenMenuIcon = false,
    ),
    ChangeBookSourceDialog.CallBack,
    AudioPlay.CallBack {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<AudioPlayViewModel>()

    private var uiState by mutableStateOf(AudioPlayerUiState())
    private var playerThemeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var coverThemeJob: Job? = null
    private var coverThemeKey: String? = null
    private var adjustingProgress = false
    private var finishImmediately = false
    private val drawerLaunchDebouncer = TtsSheetLaunchDebouncer()

    private val sourceEditResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upSource()
                refreshStaticState()
                refreshListeningTheme(force = true)
            }
        }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        AudioPlay.register(this)
        initContent()
        observeViewModel()
        initializeFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initializeFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStaticState()
        refreshListeningTheme()
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme(snapshot = playerThemeSnapshot) {
                AudioPlayScreen(
                    state = uiState,
                    onAction = ::handleAction,
                )
            }
        }
    }

    private fun observeViewModel() {
        viewModel.titleData.observe(this) { name ->
            uiState = uiState.copy(bookName = name)
            refreshLyricFromCurrentChapter()
        }
        viewModel.coverData.observe(this) { cover ->
            uiState = uiState.copy(coverPath = cover)
            refreshListeningTheme(force = true)
        }
        viewModel.customBtnListData.observe(this) {
            refreshStaticState()
        }
    }

    private fun initializeFromIntent(targetIntent: Intent) {
        val autoStart = consumeAutoStartRequest(targetIntent)
        uiState = uiState.copy(isLoading = autoStart)
        viewModel.initData(targetIntent) {
            refreshStaticState()
            refreshListeningTheme(force = true)
            if (autoStart) {
                consumeAutoStart()
            } else {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    private fun consumeAutoStartRequest(targetIntent: Intent): Boolean {
        if (!targetIntent.getBooleanExtra(EXTRA_AUTO_START, false)) return false
        val token = targetIntent.getStringExtra(EXTRA_AUTO_START_TOKEN)
        if (token == null) {
            targetIntent.removeExtra(EXTRA_AUTO_START)
            return true
        }
        if (token == viewModel.consumedAutoStartToken) return false
        viewModel.consumedAutoStartToken = token
        return true
    }

    private fun consumeAutoStart() {
        if (AudioPlay.playFromSavedProgress()) {
            uiState = uiState.copy(isLoading = false)
        }
    }

    private fun refreshStaticState() {
        val book = AudioPlay.book
        val lyric = AudioPlay.durChapter?.getVariable("lyric")
            ?.takeIf { it.isNotBlank() }
            ?: AudioPlay.durLyric
        uiState = uiState.copy(
            bookName = book?.name ?: uiState.bookName,
            bookAuthor = book?.author.orEmpty(),
            bookUrl = book?.bookUrl.orEmpty(),
            chapterTitle = AudioPlay.durChapter?.title ?: uiState.chapterTitle,
            coverPath = book?.getDisplayCover() ?: uiState.coverPath,
            sourceOrigin = AudioPlay.bookSource?.bookSourceUrl,
            sourceLabel = AudioPlay.bookSource?.bookSourceName
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.audio_player_source_context),
            lyric = lyric,
            page = if (lyric.isNullOrBlank()) AudioPlayerPage.COVER else uiState.page,
            duration = AudioPlay.durAudioSize.coerceAtLeast(0),
            position = AudioPlay.durChapterPos.coerceAtLeast(0),
            isPlaying = AudioPlay.status == Status.PLAY,
            timerLabel = AudioPlayService.timeMinute
                .takeIf { it > 0 }
                ?.let { "${it}m" }
                .orEmpty(),
            speedLabel = String.format(Locale.ROOT, "%.1fx", AudioPlayService.playSpeed),
            playMode = AudioPlay.playMode,
            canPrevious = AudioPlay.durChapterIndex > 0,
            canNext = AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1,
        )
    }

    private fun refreshLyricFromCurrentChapter() {
        val lyric = AudioPlay.durChapter?.getVariable("lyric")
            ?.takeIf { it.isNotBlank() }
            ?: AudioPlay.durLyric
        uiState = uiState.copy(
            lyric = lyric,
            page = if (lyric.isNullOrBlank()) AudioPlayerPage.COVER else uiState.page,
        )
    }

    private fun refreshListeningTheme(force: Boolean = false) {
        val book = AudioPlay.book ?: return
        val sourceOrigin = AudioPlay.bookSource?.bookSourceUrl
        val key = "${book.bookUrl}|${book.getDisplayCover()}|${sourceOrigin.orEmpty()}"
        if (!force && coverThemeKey == key) return
        coverThemeKey = key
        coverThemeJob?.cancel()
        playerThemeSnapshot = ListeningCoverTheme.cached(book, sourceOrigin)
            ?: ListeningCoverTheme.fallback(this, book)
        coverThemeJob = lifecycleScope.launch {
            playerThemeSnapshot = ListeningCoverTheme.resolve(
                context = this@AudioPlayActivity,
                book = book,
                sourceOrigin = sourceOrigin,
            )
        }
    }

    private fun handleAction(action: AudioPlayerAction) {
        when (action) {
            AudioPlayerAction.Close -> finish()
            AudioPlayerAction.OpenBookInfo -> openBookInfo()
            AudioPlayerAction.Timer -> showDrawer("audioTimer", ::AudioPlayTimerDialog)
            AudioPlayerAction.Speed -> showDrawer("audioSpeed", ::AudioPlaySpeedDialog)
            AudioPlayerAction.SkipCredits -> showDrawer(
                "audioSkipCredits",
                ::AudioSkipCreditsDialog,
            )
            AudioPlayerAction.ChangeSource -> showChangeSource()
            AudioPlayerAction.More -> showDrawer("audioMore", ::AudioPlayMoreDialog)
            AudioPlayerAction.ChangePlayMode -> AudioPlay.changePlayMode()
            AudioPlayerAction.Previous -> AudioPlay.prev()
            AudioPlayerAction.TogglePlay -> playButton()
            AudioPlayerAction.Stop -> AudioPlay.stop()
            AudioPlayerAction.Next -> AudioPlay.next()
            AudioPlayerAction.Catalog -> showDrawer("audioCatalog", ::AudioCatalogDialog)
            is AudioPlayerAction.SelectPage -> {
                if (action.page == AudioPlayerPage.COVER || !uiState.lyric.isNullOrBlank()) {
                    uiState = uiState.copy(page = action.page)
                }
            }
            is AudioPlayerAction.SeekPreview -> {
                adjustingProgress = true
                uiState = uiState.copy(
                    position = action.position.coerceIn(0, uiState.duration.coerceAtLeast(0)),
                )
            }
            AudioPlayerAction.SeekFinished -> {
                val target = uiState.position
                adjustingProgress = false
                AudioPlay.adjustProgress(target)
            }
            is AudioPlayerAction.SeekFromLyric -> {
                AudioPlay.adjustProgress(action.position)
                playButton(noLyricToggle = false)
            }
            AudioPlayerAction.ExitDialogDismiss -> {
                uiState = uiState.copy(showExitConfirmation = false)
            }
            AudioPlayerAction.AddToShelf -> addTemporaryBookToShelf()
            AudioPlayerAction.DiscardAndExit -> discardTemporaryBookAndExit()
        }
    }

    private fun showDrawer(tag: String, create: () -> DialogFragment) {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(tag) != null) return
        if (!drawerLaunchDebouncer.tryAcquire(SystemClock.elapsedRealtime())) return
        create().show(fragmentManager, tag)
    }

    private fun showChangeSource() {
        AudioPlay.book?.let {
            showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
        }
    }

    private fun openBookInfo() {
        AudioPlay.book?.let { book ->
            startActivity<BookInfoActivity> {
                putExtra("name", book.name)
                putExtra("author", book.author)
                putExtra("bookUrl", book.bookUrl)
            }
        }
    }

    internal fun hasCustomAudioAction(): Boolean =
        viewModel.customBtnListData.value == true

    internal fun handleMoreAction(action: AudioPlayMoreAction) {
        when (action) {
            AudioPlayMoreAction.CUSTOM -> {
                val source = AudioPlay.bookSource ?: return
                val book = AudioPlay.book ?: return
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_CUSTOM_BUTTON,
                    source,
                    book,
                    AudioPlay.durChapter,
                    BookType.audio,
                )
            }
            AudioPlayMoreAction.CHANGE_SOURCE -> showChangeSource()
            AudioPlayMoreAction.LOGIN -> AudioPlay.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("bookType", BookType.audio)
                }
            }
            AudioPlayMoreAction.COPY_URL -> {
                AudioPlay.book?.let { book ->
                    val url = AudioPlayService.url
                    SourceCallBack.callBackBtn(
                        this,
                        SourceCallBack.CLICK_COPY_PLAY_URL,
                        AudioPlay.bookSource,
                        book,
                        AudioPlay.durChapter,
                        BookType.audio,
                        url,
                    ) {
                        sendToClip(url)
                    }
                }
            }
            AudioPlayMoreAction.EDIT_SOURCE -> AudioPlay.bookSource?.let { source ->
                sourceEditResult.launch { putExtra("sourceUrl", source.bookSourceUrl) }
            }
            AudioPlayMoreAction.TOGGLE_WAKE_LOCK -> {
                AppConfig.audioPlayUseWakeLock = !AppConfig.audioPlayUseWakeLock
            }
            AudioPlayMoreAction.SKIP_CREDITS -> showDrawer(
                "audioSkipCredits",
                ::AudioSkipCreditsDialog,
            )
            AudioPlayMoreAction.APP_LOG -> showDialogFragment<AppLogDialog>()
            AudioPlayMoreAction.NETWORK_LOG -> showDialogFragment<NetworkLogDialog>()
        }
    }

    private fun playButton(noLyricToggle: Boolean = true) {
        when (AudioPlay.status) {
            Status.PLAY if noLyricToggle -> AudioPlay.pause(this)
            Status.PAUSE -> AudioPlay.resume(this)
            else -> AudioPlay.playFromSavedProgress()
        }
    }

    override val oldBook: Book?
        get() = AudioPlay.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isAudio) {
            viewModel.changeTo(source, book, toc)
        } else {
            AudioPlay.stop()
            lifecycleScope.launch {
                withContext(IO) {
                    AudioPlay.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    AudioPlay.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finishImmediately()
            }
        }
    }

    override fun finish() {
        if (finishImmediately) return super.finish()
        val book = AudioPlay.book ?: return finishImmediately()
        if (AudioPlay.inBookshelf) {
            callBackBookEnd()
            return finishImmediately()
        }
        if (!AppConfig.showAddToShelfAlert) {
            callBackBookEnd()
            viewModel.removeFromBookshelf { finishImmediately() }
        } else {
            uiState = uiState.copy(showExitConfirmation = true)
        }
    }

    private fun addTemporaryBookToShelf() {
        AudioPlay.book?.removeType(BookType.notShelf)
        AudioPlay.book?.save()
        SourceCallBack.callBackBook(
            SourceCallBack.ADD_BOOK_SHELF,
            AudioPlay.bookSource,
            AudioPlay.book,
        )
        AudioPlay.inBookshelf = true
        setResult(RESULT_OK)
        uiState = uiState.copy(showExitConfirmation = false)
    }

    private fun discardTemporaryBookAndExit() {
        uiState = uiState.copy(showExitConfirmation = false)
        callBackBookEnd()
        viewModel.removeFromBookshelf { finishImmediately() }
    }

    private fun finishImmediately() {
        finishImmediately = true
        super.finish()
    }

    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(
            SourceCallBack.END_READ,
            AudioPlay.bookSource,
            AudioPlay.book,
            AudioPlay.durChapter,
        )
    }

    override fun onDestroy() {
        coverThemeJob?.cancel()
        if (AudioPlay.status != Status.PLAY) {
            AudioPlay.stop()
        }
        AudioPlay.unregister(this)
        super.onDestroy()
    }

    override fun observeLiveBus() {
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) playButton()
        }
        observeEventSticky<AudioPlay.PlayMode>(EventBus.PLAY_MODE_CHANGED) {
            uiState = uiState.copy(playMode = it)
        }
        observeEventSticky<Int>(EventBus.AUDIO_STATE) {
            AudioPlay.status = it
            uiState = uiState.copy(
                isPlaying = it == Status.PLAY,
                isLoading = false,
            )
        }
        observeEventSticky<String>(EventBus.AUDIO_SUB_TITLE) { title ->
            refreshLyricFromCurrentChapter()
            uiState = uiState.copy(
                chapterTitle = title,
                canPrevious = AudioPlay.durChapterIndex > 0,
                canNext = AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1,
            )
        }
        observeEventSticky<Int>(EventBus.AUDIO_SIZE) {
            uiState = uiState.copy(duration = it.coerceAtLeast(0))
        }
        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) {
            if (!adjustingProgress) {
                uiState = uiState.copy(position = it.coerceAtLeast(0))
            }
        }
        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
            uiState = uiState.copy(bufferedPosition = it.coerceAtLeast(0))
        }
        observeEventSticky<Float>(EventBus.AUDIO_SPEED) {
            uiState = uiState.copy(
                speedLabel = String.format(Locale.ROOT, "%.1fx", it),
            )
        }
        observeEventSticky<Int>(EventBus.AUDIO_DS) {
            uiState = uiState.copy(
                timerLabel = it.takeIf { minute -> minute > 0 }
                    ?.let { minute -> "${minute}m" }
                    .orEmpty(),
            )
        }
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            viewModel.upSource()
            refreshStaticState()
            refreshListeningTheme(force = true)
        }
    }

    override fun upLoading(loading: Boolean) {
        runOnUiThread {
            uiState = uiState.copy(isLoading = loading)
        }
    }

    override fun upLyric(lyric: String?) {
        runOnUiThread {
            uiState = uiState.copy(
                lyric = lyric,
                page = if (lyric.isNullOrBlank()) AudioPlayerPage.COVER else uiState.page,
            )
        }
    }

    override fun upLyricP(position: Int) {
        if (!adjustingProgress) {
            runOnUiThread { uiState = uiState.copy(position = position.coerceAtLeast(0)) }
        }
    }

    companion object {
        const val EXTRA_AUTO_START = "audioAutoStart"
        const val EXTRA_AUTO_START_TOKEN = "audioAutoStartToken"

        fun applyAutoStart(intent: Intent) {
            intent.putExtra(EXTRA_AUTO_START, true)
            intent.putExtra(EXTRA_AUTO_START_TOKEN, UUID.randomUUID().toString())
        }
    }
}
