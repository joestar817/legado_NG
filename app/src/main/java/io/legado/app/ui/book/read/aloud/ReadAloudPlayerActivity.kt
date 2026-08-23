package io.legado.app.ui.book.read.aloud

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.ReadAloudBufferProgress
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.character.BookCharacterActivity
import io.legado.app.ui.book.character.BookCharacterTtsActivity
import io.legado.app.ui.book.character.BookStoryboardActivity
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.TtsSheetLaunchDebouncer
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.observeEvent
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadAloudPlayerActivity : BaseActivity<ComposeActivityBinding>(
    imageBg = false,
    showOpenMenuIcon = false,
) {

    override val binding by viewBinding(ComposeActivityBinding::inflate)

    private var uiState by mutableStateOf(ReadAloudPlayerUiState())
    private var playerThemeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var coverThemeJob: Job? = null
    private var coverThemeKey: String? = null
    private var lastProgress = -1
    private var cachedChapterIndex = -1
    private var cachedParagraphs = emptyList<ReadAloudParagraphUi>()
    private var lastParagraphIndex = -1
    private var paragraphSeeking = false
    private var pendingSeekParagraphIndex = -1
    private var switchingChapter = false
    private var switchingVoice = false
    private var playButtonLoading = false
    private val drawerLaunchDebouncer = TtsSheetLaunchDebouncer()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        refreshListeningTheme(force = true)
        initContent()
        refreshStaticState()
        refreshPreparationState()
        refreshProgress(ReadBook.durChapterPos)
        consumeAutoStart(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshListeningTheme(force = true)
        refreshStaticState()
        refreshPreparationState()
        refreshProgress(ReadBook.durChapterPos)
        consumeAutoStart(intent)
    }

    override fun onResume() {
        super.onResume()
        ReadAloudMiniPlayer.detach(this)
        refreshStaticState()
        refreshPreparationState()
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme(snapshot = playerThemeSnapshot) {
                ReadAloudPlayerScreen(
                    state = uiState,
                    onAction = ::handleAction,
                )
            }
        }
    }

    private fun refreshListeningTheme(force: Boolean = false) {
        val book = ReadBook.book ?: return
        val sourceOrigin = ReadBook.bookSource?.bookSourceUrl
        val key = "${book.bookUrl}|${book.getDisplayCover()}|${sourceOrigin.orEmpty()}"
        if (!force && coverThemeKey == key) return
        coverThemeKey = key
        coverThemeJob?.cancel()
        playerThemeSnapshot = ListeningCoverTheme.cached(book, sourceOrigin)
            ?: ListeningCoverTheme.fallback(this, book)
        coverThemeJob = lifecycleScope.launch {
            playerThemeSnapshot = ListeningCoverTheme.resolve(
                context = this@ReadAloudPlayerActivity,
                book = book,
                sourceOrigin = sourceOrigin,
            )
        }
    }

    private fun handleAction(action: ReadAloudPlayerAction) {
        when (action) {
            ReadAloudPlayerAction.Close -> finish()
            ReadAloudPlayerAction.OpenBookInfo -> openBookInfo()
            ReadAloudPlayerAction.Timer -> {
                showDrawer("readAloudTimer", ::ReadAloudTimerDialog)
            }
            ReadAloudPlayerAction.Speed -> {
                showDrawer("readAloudSpeed", ::ReadAloudSpeedDialog)
            }
            ReadAloudPlayerAction.Refresh -> refreshCurrentChapter()
            ReadAloudPlayerAction.Original -> openOriginal()
            ReadAloudPlayerAction.More -> showMoreSheet()
            ReadAloudPlayerAction.Mode -> {
                showDrawer("readAloudMode", ::ReadAloudModeDialog)
            }
            ReadAloudPlayerAction.Catalog -> openChapterList()
            ReadAloudPlayerAction.Voice -> openVoiceOrRoleBindings()
            ReadAloudPlayerAction.TogglePlay -> togglePlay()
            ReadAloudPlayerAction.PreviousChapter -> ReadAloud.prevChapter(this)
            ReadAloudPlayerAction.NextChapter -> ReadAloud.nextChapter(this)
            is ReadAloudPlayerAction.SelectPage -> showPage(action.page)
            is ReadAloudPlayerAction.SeekPreview -> previewSeekParagraph(action.paragraphIndex)
            ReadAloudPlayerAction.SeekFinished -> finishParagraphSeek()
        }
    }

    private fun consumeAutoStart(intent: Intent) {
        if (!intent.getBooleanExtra(ReadAloudLauncher.EXTRA_AUTO_START, false)) return
        intent.removeExtra(ReadAloudLauncher.EXTRA_AUTO_START)
        if (isCurrentBookAlreadyReading(intent)) {
            if (!BaseReadAloudService.isPlay()) {
                ReadAloud.resume(this)
            }
            setPlayButtonLoading(BaseReadAloudService.isPreparing())
            refreshStaticState()
            refreshPreparationState()
            refreshProgress(ReadBook.durChapterPos)
            return
        }
        startReadAloudAfterContentReady()
    }

    private fun isCurrentBookAlreadyReading(intent: Intent): Boolean {
        if (!BaseReadAloudService.isRun) return false
        val targetBookUrl = intent.getStringExtra(ReadAloudLauncher.EXTRA_BOOK_URL)
            ?: ReadBook.book?.bookUrl
            ?: return false
        return BaseReadAloudService.activeBookUrl == targetBookUrl
    }

    private fun refreshStaticState() {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter
        val multiRole = AppConfig.readAloudMultiRole
        val engine = runCatching { TtsEngineStore.activeEngine() }.getOrNull()
        val voice = runCatching { engine?.activeVoice()?.name }.getOrNull()
        val bookUrlChanged = book?.bookUrl.orEmpty() != uiState.bookUrl
        uiState = uiState.copy(
            bookName = book?.name?.takeIf { it.isNotBlank() } ?: "阅读NG",
            bookAuthor = book?.getRealAuthor().orEmpty(),
            bookUrl = book?.bookUrl.orEmpty(),
            chapterTitle = chapter?.title ?: "正在准备朗读",
            coverPath = book?.getDisplayCover(),
            sourceOrigin = ReadBook.bookSource?.bookSourceUrl,
            engineLabel = when {
                multiRole -> "多人朗读 · 角色音色"
                engine == null || !engine.enabled -> "选择朗读音色"
                voice.isNullOrBlank() -> "朗读音色 · ${engine.name}"
                else -> "朗读音色 · $voice"
            },
            speedLabel = speedLabel(),
            timerLabel = BaseReadAloudService.timeMinute.takeIf { it > 0 }
                ?.let { "${it}分" }
                ?: "定时",
        )
        if (bookUrlChanged) refreshListeningTheme()
        refreshPlayState()
    }

    private fun refreshPlayState() {
        if (playButtonLoading) return
        uiState = uiState.copy(isPlaying = BaseReadAloudService.isPlay())
    }

    private fun refreshProgress(progress: Int) {
        if (paragraphSeeking || progress == lastProgress) return
        lastProgress = progress
        if (BaseReadAloudService.isPreparing()) {
            showPreparationMessage()
            return
        }
        if (ReadBook.curTextChapter?.isCompleted == true) {
            refreshSubtitle(progress)
        }
    }

    private fun refreshSubtitle(progress: Int) {
        if (!ensureParagraphCache()) return
        val index = currentParagraphIndex(progress)
        if (index == lastParagraphIndex) return
        lastParagraphIndex = index
        syncParagraphProgress(index)
        syncLyrics(index)
        uiState = uiState.copy(
            subtitle = cachedParagraphs.getOrNull(index)?.text ?: "正在准备朗读…"
        )
    }

    private fun ensureParagraphCache(): Boolean {
        val chapterIndex = ReadBook.durChapterIndex
        if (chapterIndex == cachedChapterIndex && cachedParagraphs.isNotEmpty()) return true
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted } ?: return false
        cachedChapterIndex = chapterIndex
        lastParagraphIndex = -1
        cachedParagraphs = chapter.getParagraphs(false).mapNotNull { paragraph ->
            val text = paragraph.text
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(160)
            if (text.isBlank()) null else ReadAloudParagraphUi(paragraph.chapterIndices, text)
        }
        uiState = uiState.copy(
            paragraphs = cachedParagraphs,
            progressMax = (cachedParagraphs.size - 1).coerceAtLeast(0),
            progress = 0,
            bufferedProgress = 0,
        )
        return cachedParagraphs.isNotEmpty()
    }

    private fun currentParagraphIndex(progress: Int): Int {
        if (cachedParagraphs.isEmpty() && !ensureParagraphCache()) return 0
        return cachedParagraphs.indexOfFirst { progress in it.range }.let { exact ->
            if (exact >= 0) exact else cachedParagraphs.indexOfLast { progress >= it.range.first }
        }.coerceIn(0, cachedParagraphs.lastIndex.coerceAtLeast(0))
    }

    private fun syncParagraphProgress(index: Int) {
        val maxIndex = (cachedParagraphs.size - 1).coerceAtLeast(0)
        val safeIndex = index.coerceIn(0, maxIndex)
        uiState = uiState.copy(
            progressMax = maxIndex,
            progress = safeIndex,
            bufferedProgress = maxOf(uiState.bufferedProgress, safeIndex),
        )
    }

    private fun syncParagraphBufferProgress(buffer: ReadAloudBufferProgress) {
        if (buffer.chapterIndex != ReadBook.durChapterIndex || !ensureParagraphCache()) return
        val bufferedIndex = currentParagraphIndex(buffer.chapterPosition)
        uiState = uiState.copy(
            bufferedProgress = maxOf(uiState.bufferedProgress, uiState.progress, bufferedIndex)
        )
    }

    private fun syncLyrics(index: Int) {
        if (cachedParagraphs.isEmpty()) return
        uiState = uiState.copy(
            currentParagraphIndex = index.coerceIn(0, cachedParagraphs.lastIndex)
        )
    }

    private fun previewSeekParagraph(index: Int) {
        if (!ensureParagraphCache()) return
        paragraphSeeking = true
        val safeIndex = index.coerceIn(0, cachedParagraphs.lastIndex)
        pendingSeekParagraphIndex = safeIndex
        lastParagraphIndex = safeIndex
        syncParagraphProgress(safeIndex)
        syncLyrics(safeIndex)
        uiState = uiState.copy(subtitle = cachedParagraphs[safeIndex].text)
    }

    private fun finishParagraphSeek() {
        if (!paragraphSeeking) return
        val targetIndex = pendingSeekParagraphIndex
        paragraphSeeking = false
        pendingSeekParagraphIndex = -1
        if (targetIndex == currentParagraphIndex(ReadBook.durChapterPos)) {
            refreshProgress(ReadBook.durChapterPos)
        } else {
            seekToParagraph(targetIndex)
        }
    }

    private fun seekToParagraph(index: Int) {
        if (!ensureParagraphCache()) return
        val target = cachedParagraphs.getOrNull(index.coerceIn(cachedParagraphs.indices)) ?: return
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted } ?: return
        val targetPos = target.range.first.coerceAtLeast(0)
        val targetPage = chapter.getPageIndexByCharIndex(targetPos).coerceAtLeast(0)
        val pageStart = chapter.getReadLength(targetPage)
        val pageStartPos = (targetPos - pageStart).coerceAtLeast(0)
        val wasRun = BaseReadAloudService.isRun
        val wasPlaying = BaseReadAloudService.isPlay()
        ReadBook.durChapterPos = targetPos
        lastProgress = -1
        refreshProgress(targetPos)
        if (wasRun) {
            ReadAloud.play(this, play = wasPlaying, pageIndex = targetPage, startPos = pageStartPos)
        }
    }

    private fun togglePlay() {
        when {
            !BaseReadAloudService.isRun -> {
                if (!TtsEngineStore.hasEnabledEngine()) {
                    toastOnUi("未启用朗读引擎")
                    return
                }
                startReadAloudAfterContentReady()
            }
            !BaseReadAloudService.isPlay() -> {
                setPlayButtonLoading(true)
                ReadAloud.resume(this)
            }
            else -> ReadAloud.pause(this)
        }
    }

    private fun openChapterList() {
        showDrawer("readAloudCatalog", ::ReadAloudCatalogDialog)
    }

    private fun refreshCurrentChapter() {
        val book = ReadBook.book ?: run {
            toastOnUi("当前书籍为空")
            return
        }
        val chapterIndex = ReadBook.durChapterIndex
        val chapterPos = ReadBook.durChapterPos
        val wasRun = BaseReadAloudService.isRun
        val wasPlaying = BaseReadAloudService.isPlay()
        switchingChapter = true
        if (wasRun) {
            setPlayButtonLoading(true)
            ReadAloud.stop(this)
        }
        resetChapterUi("正在准备朗读")
        toastOnUi("正在刷新当前章节")
        lifecycleScope.launch {
            val result = runCatching {
                withContext(IO) {
                    if (ReadBook.bookSource != null) {
                        appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.let {
                            BookHelp.delContent(book, it)
                        }
                    }
                }
                ReadBook.msg = null
                ReadBook.clearTextChapter()
                ReadBook.removeLoading(chapterIndex)
                ReadBook.durChapterIndex = chapterIndex
                ReadBook.durChapterPos = chapterPos.coerceAtLeast(0)
                ReadBook.saveRead()
                ReadBook.loadContentAwait(chapterIndex, upContent = false, resetPageOffset = false)
            }
            if (!result.isSuccess) {
                toastOnUi("刷新失败：${result.exceptionOrNull()?.localizedMessage ?: "未知错误"}")
                switchingChapter = false
                setPlayButtonLoading(false)
                return@launch
            }
            val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted }
            if (chapter == null) {
                toastOnUi("刷新失败：章节内容未加载")
                switchingChapter = false
                setPlayButtonLoading(false)
                refreshStaticState()
                return@launch
            }
            ReadBook.durChapterPos = ReadBook.durChapterPos.coerceIn(0, chapter.getContent().length)
            resetParagraphCache()
            refreshStaticState()
            refreshProgress(ReadBook.durChapterPos)
            if (wasRun) {
                resumeReadAloudAfterReload(wasPlaying)
            } else {
                setPlayButtonLoading(false)
            }
            binding.root.postDelayed({ switchingChapter = false }, 1200L)
        }
    }

    private fun startReadAloudAfterContentReady() {
        if (!TtsEngineStore.hasEnabledEngine()) {
            toastOnUi("未启用朗读引擎")
            return
        }
        setPlayButtonLoading(true)
        lifecycleScope.launch {
            val prepared = ReadAloudLauncher.loadCurrentChapter(this@ReadAloudPlayerActivity)
            if (!prepared) {
                setPlayButtonLoading(false)
                refreshStaticState()
                toastOnUi(ReadBook.msg ?: "加载正文失败")
                return@launch
            }
            resetParagraphCache()
            refreshStaticState()
            refreshProgress(ReadBook.durChapterPos)
            switchingChapter = true
            ReadAloud.upReadAloudClass()
            ReadBook.readAloud(play = true, startPos = currentPageStartPos())
            binding.root.postDelayed({ switchingChapter = false }, 1200L)
        }
    }

    private fun resumeReadAloudAfterReload(wasPlaying: Boolean) {
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted }
        val targetPage = chapter
            ?.getPageIndexByCharIndex(ReadBook.durChapterPos)
            ?.coerceAtLeast(0)
            ?: ReadBook.durPageIndex
        val pageStart = chapter?.getReadLength(targetPage) ?: 0
        val startPos = (ReadBook.durChapterPos - pageStart).coerceAtLeast(0)
        ReadAloud.play(this, play = wasPlaying, pageIndex = targetPage, startPos = startPos)
        if (!wasPlaying) {
            binding.root.postDelayed({ setPlayButtonLoading(false) }, 1200L)
        }
    }

    fun setMultiRoleEnabled(enabled: Boolean) {
        if (AppConfig.readAloudMultiRole == enabled) return
        val wasRun = BaseReadAloudService.isRun
        val wasPlaying = BaseReadAloudService.isPlay()
        val startPos = currentPageStartPos()
        AppConfig.readAloudScenarioMode = if (enabled) 1 else 0
        refreshStaticState()
        if (wasRun) {
            switchingVoice = true
            setPlayButtonLoading(true)
            ReadAloud.stop(this)
            binding.root.postDelayed({
                ReadBook.readAloud(play = wasPlaying, startPos = startPos)
                binding.root.postDelayed({
                    switchingVoice = false
                    if (!wasPlaying) setPlayButtonLoading(false)
                }, 1200L)
            }, 180L)
        }
        toastOnUi(if (AppConfig.readAloudMultiRole) "已开启多人模式" else "已切换单人模式")
    }

    fun selectMultiRoleEngine(engineId: String?) {
        val changed = AppConfig.multiRoleTtsEngineId != engineId
        AppConfig.multiRoleTtsEngineId = engineId
        if (changed) {
            val hadDialogueVoice = !AppConfig.defaultDialogueMaleTtsVoiceId.isNullOrBlank() ||
                !AppConfig.defaultDialogueFemaleTtsVoiceId.isNullOrBlank()
            AppConfig.defaultDialogueMaleTtsVoiceId = null
            AppConfig.defaultDialogueFemaleTtsVoiceId = null
            if (hadDialogueVoice && engineId != null) {
                toastOnUi(R.string.default_tts_voice_changed_engine)
            }
            if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                ReadAloud.refreshTtsRoute(this)
            }
        }
        refreshStaticState()
    }

    private fun openVoiceOrRoleBindings() {
        if (AppConfig.readAloudMultiRole) {
            openCharacterTtsBindings()
        } else {
            showDrawer("readAloudVoice", ::ReadAloudVoiceDialog)
        }
    }

    private fun showPage(page: ReadAloudPlayerPage) {
        if (uiState.page == page) return
        uiState = uiState.copy(page = page)
    }

    /** 播放器已不内嵌分镜调试列表，保留入口以兼容共享试听控制调用。 */
    fun stopStoryboardPreview() = Unit

    fun openChapterFromCatalog(chapterIndex: Int) {
        if (chapterIndex == ReadBook.durChapterIndex) return
        val wasPlaying = BaseReadAloudService.isPlay()
        switchingChapter = true
        if (BaseReadAloudService.isRun) {
            ReadAloud.stop(this)
        }
        resetChapterUi("正在加载章节")
        ReadBook.openChapter(chapterIndex, durChapterPos = 0, upContent = false) {
            refreshStaticState()
            refreshProgress(0)
            ReadBook.readAloud(wasPlaying, startPos = 0)
            binding.root.postDelayed({ switchingChapter = false }, 800L)
        }
    }

    fun runVoiceSwitch(block: () -> Unit) {
        switchingVoice = true
        setPlayButtonLoading(true)
        try {
            block()
        } finally {
            binding.root.postDelayed({ switchingVoice = false }, 1200L)
        }
    }

    fun currentPageStartPos(): Int {
        val pageStart = ReadBook.curTextChapter
            ?.takeIf { it.isCompleted }
            ?.getReadLength(ReadBook.durPageIndex)
            ?: 0
        return (ReadBook.durChapterPos - pageStart).coerceAtLeast(0)
    }

    private fun setPlayButtonLoading(loading: Boolean) {
        if (playButtonLoading == loading) {
            if (!loading) refreshPlayState()
            return
        }
        playButtonLoading = loading
        uiState = uiState.copy(
            isPreparing = loading,
            isPlaying = if (loading) uiState.isPlaying else BaseReadAloudService.isPlay(),
        )
    }

    fun openOriginal() {
        ReadBook.book?.let {
            startActivity<io.legado.app.ui.book.read.ReadBookActivity> {
                putExtra("bookUrl", it.bookUrl)
            }
        }
        finish()
    }

    private fun openBookInfo() {
        ReadBook.book?.let {
            startActivity<io.legado.app.ui.book.info.BookInfoActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra("name", it.name)
                putExtra("author", it.author)
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    private fun refreshPreparationState() {
        val preparing = BaseReadAloudService.isPreparing()
        setPlayButtonLoading(preparing)
        if (preparing) {
            showPreparationMessage()
        } else if (!paragraphSeeking) {
            val index = currentParagraphIndex(ReadBook.durChapterPos)
            uiState = uiState.copy(
                subtitle = cachedParagraphs.getOrNull(index)?.text ?: "正在准备朗读…"
            )
        }
    }

    private fun showPreparationMessage() {
        uiState = uiState.copy(
            subtitle = BaseReadAloudService.preparationMessage().ifBlank { "正在准备朗读…" }
        )
    }

    private fun openCharacterTtsBindings() {
        ReadBook.book?.let {
            startActivity<BookCharacterTtsActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra(
                    BookCharacterActivity.EXTRA_WORK_KEY,
                    BookCharacterProfile.workKey(it.name, it.author),
                )
                putExtra(BookCharacterActivity.EXTRA_BOOK_NAME, it.name)
                putExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR, it.author)
                putExtra(BookCharacterActivity.EXTRA_BOOK_URL, it.bookUrl)
            }
        }
    }

    fun openStoryboardResult() {
        ReadBook.book?.let {
            startActivity<BookStoryboardActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra(
                    BookCharacterActivity.EXTRA_WORK_KEY,
                    BookCharacterProfile.workKey(it.name, it.author),
                )
                putExtra(BookCharacterActivity.EXTRA_BOOK_NAME, it.name)
                putExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR, it.author)
                putExtra(BookCharacterActivity.EXTRA_BOOK_URL, it.bookUrl)
            }
        } ?: toastOnUi("当前书籍为空")
    }

    fun openEngineConfig() {
        startActivity<ConfigActivity> {
            ReadAloudLauncher.markPlayerDerived(this)
            putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
        }
    }

    private fun showMoreSheet() {
        showDrawer("readAloudMore", ::ReadAloudMoreDialog)
    }

    private fun showDrawer(tag: String, create: () -> DialogFragment) {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(tag) != null) return
        if (!drawerLaunchDebouncer.tryAcquire(SystemClock.elapsedRealtime())) return
        create().show(fragmentManager, tag)
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            when (it) {
                Status.LOADING -> {
                    setPlayButtonLoading(true)
                    showPreparationMessage()
                }
                Status.PAUSE, Status.STOP -> setPlayButtonLoading(false)
                Status.PLAY -> setPlayButtonLoading(BaseReadAloudService.isPreparing())
            }
            refreshStaticState()
            if (it == Status.STOP && !switchingChapter && !switchingVoice) {
                finish()
            }
        }
        observeEvent<Int>(EventBus.TTS_PROGRESS) {
            if (!BaseReadAloudService.isPreparing()) setPlayButtonLoading(false)
            refreshProgress(it)
        }
        observeEvent<ReadAloudBufferProgress>(EventBus.TTS_BUFFER_PROGRESS) {
            syncParagraphBufferProgress(it)
        }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) {
            refreshStaticState()
        }
    }

    private fun speedLabel(): String = TtsSpeedPolicy.playbackLabel(AppConfig.speechRatePlay)

    fun refreshPlaybackSpeedLabel() {
        uiState = uiState.copy(speedLabel = speedLabel())
    }

    private fun resetParagraphCache() {
        cachedChapterIndex = -1
        cachedParagraphs = emptyList()
        lastParagraphIndex = -1
        lastProgress = -1
    }

    private fun resetChapterUi(message: String) {
        resetParagraphCache()
        paragraphSeeking = false
        pendingSeekParagraphIndex = -1
        uiState = uiState.copy(
            chapterTitle = message,
            subtitle = message,
            paragraphs = emptyList(),
            currentParagraphIndex = 0,
            progressMax = 0,
            progress = 0,
            bufferedProgress = 0,
        )
    }

    override fun onDestroy() {
        coverThemeJob?.cancel()
        super.onDestroy()
    }
}
