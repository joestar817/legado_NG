package io.legado.app.ui.book.toc

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isVideo
import io.legado.app.help.config.AppConfig
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.model.ReadBook
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.toc.rule.TxtTocRuleDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 目录／书签。页面渲染使用 Compose，业务协议沿用原实现。 */
class TocActivity : VMBaseActivity<ComposeActivityBinding, TocViewModel>(
    toolBarTheme = Theme.Dark,
), TxtTocRuleDialog.CallBack {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<TocViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private val waitDialog by lazy { WaitDialog(this) }
    private val exportDir = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.saveBookmark(uri)
                2 -> viewModel.saveBookmarkMd(uri)
            }
        }
    }
    private var uiState by mutableStateOf(
        TocUiState(
            useReplace = AppConfig.tocUiUseReplace,
            loadWordCount = AppConfig.tocCountWords,
        ),
    )
    private var fullChapterList: List<BookChapter> = emptyList()
    private var chapterJob: Job? = null
    private var displayTitleJob: Job? = null
    private var bookmarkJob: Job? = null
    private var cacheJob: Job? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initContent()
        viewModel.bookData.observe(this, ::onBookChanged)
        intent.getStringExtra("bookUrl")?.let(viewModel::initBook)
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                TocScreen(state = uiState, onEvent = ::handleUiEvent)
            }
        }
    }

    private fun onBookChanged(book: Book) {
        uiState = uiState.copy(
            book = book.copy(),
            splitLongChapter = book.getSplitLongChapter(),
            isLocalTxt = book.isLocalTxt,
        )
        loadCachedFiles(book)
        if (uiState.selectedTab == TOC_TAB_BOOKMARKS) {
            observeBookmarks()
        } else {
            loadChapters()
        }
    }

    private fun handleUiEvent(event: TocUiEvent) {
        when (event) {
            TocUiEvent.Back -> onBackPressedDispatcher.onBackPressed()
            is TocUiEvent.TabChange -> changeTab(event.tab)
            is TocUiEvent.SearchExpandedChange -> {
                if (event.expanded) {
                    uiState = uiState.copy(searchExpanded = true)
                } else {
                    closeSearch()
                }
            }
            is TocUiEvent.QueryChange -> changeQuery(event.query)
            is TocUiEvent.Menu -> handleMenuAction(event.action)
            is TocUiEvent.ChapterClick -> openChapter(event.chapter)
            is TocUiEvent.ChapterLongClick -> longToastOnUi(event.title)
            is TocUiEvent.BookmarkClick -> openBookmark(event.bookmark)
            is TocUiEvent.BookmarkLongClick -> showDialogFragment(
                BookmarkDialog(event.bookmark, event.position),
            )
        }
    }

    private fun changeTab(tab: Int) {
        if (uiState.selectedTab == tab) return
        uiState = uiState.copy(selectedTab = tab)
        if (tab == TOC_TAB_BOOKMARKS) {
            chapterJob?.cancel()
            displayTitleJob?.cancel()
            observeBookmarks()
        } else {
            bookmarkJob?.cancel()
            loadChapters()
        }
    }

    private fun changeQuery(query: String) {
        if (uiState.query == query) return
        uiState = uiState.copy(query = query)
        if (uiState.selectedTab == TOC_TAB_BOOKMARKS) observeBookmarks() else loadChapters()
    }

    private fun closeSearch() {
        val clearQuery = uiState.query.isNotEmpty()
        uiState = uiState.copy(searchExpanded = false, query = "")
        if (clearQuery) {
            if (uiState.selectedTab == TOC_TAB_BOOKMARKS) observeBookmarks() else loadChapters()
        }
    }

    private fun loadChapters() {
        val book = viewModel.bookData.value ?: return
        val query = uiState.query
        chapterJob?.cancel()
        chapterJob = lifecycleScope.launch {
            val chapters = viewModel.loadChapters(query)
            if (uiState.query != query || uiState.selectedTab != TOC_TAB_CHAPTERS) return@launch
            if (query.isBlank()) fullChapterList = chapters
            val scrollIndex = chapters.indexOfLast { it.index < book.durChapterIndex }
                .coerceAtLeast(0)
            uiState = uiState.copy(
                chapters = chapters.map(::TocChapterUiItem),
                chapterScrollIndex = scrollIndex,
                chapterScrollToken = uiState.chapterScrollToken + 1,
            )
            updateDisplayTitles(book, query, chapters)
        }
    }

    private fun updateDisplayTitles(
        book: Book,
        query: String,
        chapters: List<BookChapter>,
    ) {
        displayTitleJob?.cancel()
        displayTitleJob = lifecycleScope.launch {
            val useReplace = AppConfig.tocUiUseReplace && book.getUseReplaceRule()
            val displayItems = withContext(Default) {
                val replaceRules = ContentProcessor.get(book.name, book.origin)
                    .getTitleReplaceRules()
                val replaceBook = book.toReplaceBook()
                chapters.map { chapter ->
                    TocChapterUiItem(
                        chapter = chapter,
                        displayTitle = chapter.getDisplayTitle(
                            replaceRules = replaceRules,
                            useReplace = useReplace,
                            replaceBook = replaceBook,
                        ),
                    )
                }
            }
            if (
                uiState.query == query &&
                uiState.selectedTab == TOC_TAB_CHAPTERS &&
                uiState.chapters.map { it.chapter.primaryStr() } ==
                displayItems.map { it.chapter.primaryStr() }
            ) {
                uiState = uiState.copy(chapters = displayItems)
            }
        }
    }

    private fun observeBookmarks() {
        val book = viewModel.bookData.value ?: return
        val query = uiState.query
        bookmarkJob?.cancel()
        bookmarkJob = lifecycleScope.launch {
            viewModel.bookmarkFlow(query)
                .catch {
                    AppLog.put("目录界面获取书签数据失败\n${it.localizedMessage}", it)
                }
                .flowOn(IO)
                .collect { bookmarks ->
                    if (
                        uiState.query != query ||
                        uiState.selectedTab != TOC_TAB_BOOKMARKS
                    ) return@collect
                    val scrollIndex = bookmarks.indexOfLast {
                        it.chapterIndex < book.durChapterIndex
                    }.coerceAtLeast(0)
                    uiState = uiState.copy(
                        bookmarks = bookmarks,
                        bookmarkScrollIndex = scrollIndex,
                        bookmarkScrollToken = uiState.bookmarkScrollToken + 1,
                    )
                }
        }
    }

    private fun loadCachedFiles(book: Book) {
        cacheJob?.cancel()
        cacheJob = lifecycleScope.launch {
            val cacheFiles = withContext(IO) {
                if (book.isAudio) {
                    appDb.bookSourceDao.getBookSource(book.origin)?.let {
                        AudioDownloadCache.getCachedChapterFileNames(it, book)
                    }.orEmpty()
                } else {
                    BookHelp.getChapterFiles(book).toSet()
                }
            }
            if (viewModel.bookData.value?.bookUrl == book.bookUrl) {
                uiState = uiState.copy(cachedFileNames = cacheFiles)
            }
        }
    }

    private fun handleMenuAction(action: TocMenuAction) {
        when (action) {
            TocMenuAction.TocRegex -> showDialogFragment(
                TxtTocRuleDialog(viewModel.bookData.value?.tocUrl),
            )
            TocMenuAction.SplitLongChapter -> toggleSplitLongChapter()
            TocMenuAction.ReverseToc -> reverseToc()
            TocMenuAction.UseReplace -> {
                AppConfig.tocUiUseReplace = !AppConfig.tocUiUseReplace
                uiState = uiState.copy(useReplace = AppConfig.tocUiUseReplace)
                viewModel.bookData.value?.let {
                    updateDisplayTitles(it, uiState.query, uiState.chapters.map { item -> item.chapter })
                }
            }
            TocMenuAction.LoadWordCount -> {
                AppConfig.tocCountWords = !AppConfig.tocCountWords
                uiState = uiState.copy(loadWordCount = AppConfig.tocCountWords)
            }
            TocMenuAction.ExportBookmark -> exportDir.launch(
                SelectDirectoryContract.Request(requestCode = 1),
            )
            TocMenuAction.ExportMarkdown -> exportDir.launch(
                SelectDirectoryContract.Request(requestCode = 2),
            )
            TocMenuAction.Log -> showDialogFragment<AppLogDialog>()
            TocMenuAction.NetworkLog -> showDialogFragment<NetworkLogDialog>()
        }
    }

    private fun toggleSplitLongChapter() {
        viewModel.bookData.value?.let { book ->
            val enabled = !book.getSplitLongChapter()
            book.setSplitLongChapter(enabled)
            uiState = uiState.copy(splitLongChapter = enabled)
            upBookAndToc(book)
        }
    }

    private fun reverseToc() {
        viewModel.reverseToc { book ->
            uiState = uiState.copy(book = book.copy())
            loadChapters()
            setResult(RESULT_OK, Intent().apply {
                putExtra("index", book.durChapterIndex)
                putExtra("chapterPos", 0)
            })
        }
    }

    override fun onTocRegexDialogResult(tocRegex: String) {
        viewModel.bookData.value?.let { book ->
            book.tocUrl = tocRegex
            upBookAndToc(book)
        }
    }

    private fun upBookAndToc(book: Book) {
        waitDialog.show()
        viewModel.upBookTocRule(book) {
            waitDialog.dismiss()
            if (ReadBook.book == book) {
                if (it == null) {
                    ReadBook.upMsg(null)
                } else {
                    ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                }
            }
        }
    }

    private fun openChapter(bookChapter: BookChapter) {
        val book = viewModel.bookData.value ?: return
        if (book.isVideo) {
            val chapters = fullChapterList.ifEmpty { uiState.chapters.map { it.chapter } }
            val volumes = chapters.filter(BookChapter::isVolume)
            var chapterInVolumeIndex = bookChapter.index
            var durVolumeIndex = 0
            if (volumes.isNotEmpty()) {
                for ((index, volume) in volumes.reversed().withIndex()) {
                    when {
                        volume.index < bookChapter.index -> {
                            chapterInVolumeIndex = bookChapter.index - volume.index - 1
                            durVolumeIndex = volumes.size - index - 1
                            break
                        }
                        volume.index == bookChapter.index -> {
                            chapterInVolumeIndex = 0
                            durVolumeIndex = volumes.size - index - 1
                            break
                        }
                    }
                }
            }
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != book.durChapterIndex)
                    .putExtra("durVolumeIndex", durVolumeIndex)
                    .putExtra("chapterInVolumeIndex", chapterInVolumeIndex),
            )
        } else {
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != book.durChapterIndex),
            )
        }
        finish()
    }

    private fun openBookmark(bookmark: Bookmark) {
        setResult(RESULT_OK, Intent().apply {
            putExtra("index", bookmark.chapterIndex)
            putExtra("chapterPos", bookmark.chapterPos)
        })
        finish()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            if (viewModel.bookData.value?.bookUrl == book.bookUrl) {
                uiState = uiState.copy(
                    cachedFileNames = uiState.cachedFileNames + chapter.getFileName(),
                )
            }
        }
    }
}
