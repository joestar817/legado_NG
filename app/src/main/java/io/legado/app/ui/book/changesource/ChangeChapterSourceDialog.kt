package io.legado.app.ui.book.changesource

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setLayout
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChangeChapterSourceDialog() : BaseComposeDialogFragment() {

    constructor(name: String, author: String, chapterIndex: Int, chapterTitle: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
            putInt("chapterIndex", chapterIndex)
            putString("chapterTitle", chapterTitle)
        }
    }

    private val callBack: CallBack?
        get() = activity as? CallBack
    private val viewModel: ChangeChapterSourceViewModel by viewModels()
    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            viewModel.startSearch()
        }

    private var searchBooks by mutableStateOf(emptyList<SearchBook>())
    private var toc by mutableStateOf(emptyList<BookChapter>())
    private var groups by mutableStateOf(emptyList<String>())
    private var enabledSources = emptyList<BookSourcePart>()
    private var blockSourceDialogs by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var searching by mutableStateOf(false)
    private var showToc by mutableStateOf(false)
    private var tocLoading by mutableStateOf(false)
    private var tocCurrentIndex by mutableIntStateOf(-1)
    private var settingsRevision by mutableIntStateOf(0)
    private var scoreRevision by mutableIntStateOf(0)
    private var searchBook: SearchBook? = null

    private val contentSuccess: (content: String) -> Unit = {
        tocLoading = false
        callBack?.replaceContent(it)
        dismissAllowingStateLoss()
    }

    private val searchFinishCallback: (isEmpty: Boolean) -> Unit = { isEmpty ->
        if (isEmpty) {
            val searchGroup = AppConfig.searchGroup
            val sameTypeOnly = AppConfig.changeSourceSameTypeOnly
            if (searchGroup.isNotEmpty() && !viewModel.selectedGroupUnavailable) {
                lifecycleScope.launch {
                    if (AppConfig.searchGroup != searchGroup ||
                        AppConfig.changeSourceSameTypeOnly != sameTypeOnly ||
                        viewModel.selectedGroupUnavailable
                    ) return@launch
                    showReadConfirmDialog(
                        context = requireContext(),
                        title = "搜索结果为空",
                        message = "${searchGroup}分组搜索结果为空,是否切换到全部分组",
                        confirmLabel = getString(R.string.yes),
                        cancelLabel = getString(R.string.no),
                        onConfirm = {
                            if (AppConfig.searchGroup == searchGroup &&
                                AppConfig.changeSourceSameTypeOnly == sameTypeOnly
                            ) {
                                AppConfig.searchGroup = ""
                                updateGroups()
                                viewModel.startSearch()
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
        }
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.initData(arguments, callBack?.oldBook, activity is ReadBookActivity)
        blockSourceDialogs = requireContext().getPrefBoolean(PreferKey.searchBlockSourceDialogs)
        viewModel.setBlockSourceDialogs(blockSourceDialogs)
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                settingsRevision
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(requireContext()),
                    updateSystemBars = false,
                ) {
                    ChangeChapterSourceDialogContent(
                        title = viewModel.chapterTitle,
                        currentSourceName = callBack?.oldBook?.originName.orEmpty(),
                        oldBookUrl = oldBookUrl,
                        searchBooks = searchBooks,
                        toc = toc,
                        tocCurrentIndex = tocCurrentIndex,
                        showToc = showToc,
                        searching = searching,
                        tocLoading = tocLoading,
                        query = query,
                        blockSourceDialogs = blockSourceDialogs,
                        settings = ChangeChapterSourceSettingsUi(
                            checkAuthor = AppConfig.changeSourceCheckAuthor,
                            loadWordCount = AppConfig.changeSourceLoadWordCount,
                            loadInfo = AppConfig.changeSourceLoadInfo,
                            loadToc = AppConfig.changeSourceLoadToc,
                            selectedGroup = AppConfig.searchGroup,
                            groups = groups,
                            sameTypeOnly = AppConfig.changeSourceSameTypeOnly,
                            selectedGroupUnavailable = viewModel.selectedGroupUnavailable,
                        ),
                        scoreRevision = scoreRevision,
                        getScore = viewModel::getBookScore,
                        onQueryChanged = {
                            query = it
                            viewModel.screen(it)
                        },
                        onRefreshToggle = viewModel::startOrStopSearch,
                        onClose = { dismissAllowingStateLoss() },
                        onOpenSourceManage = { startActivity<BookSourceActivity>() },
                        onToggleSameTypeOnly = ::toggleSameTypeOnly,
                        onToggleBlockSourceDialogs = ::toggleBlockSourceDialogs,
                        onToggleCheckAuthor = ::toggleCheckAuthor,
                        onToggleLoadWordCount = ::toggleLoadWordCount,
                        onToggleLoadInfo = ::toggleLoadInfo,
                        onToggleLoadToc = ::toggleLoadToc,
                        onGroupSelected = ::selectGroup,
                        onSourceClick = ::openToc,
                        onSourceAction = ::handleSourceAction,
                        onScoreChanged = ::setBookScore,
                        onHideToc = { showToc = false },
                        onChapterClick = ::clickChapter,
                    )
                }
            }
        }
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            searching = it
        }
        lifecycleScope.launch {
            lifecycle.currentStateFlow.first { it.isAtLeast(STARTED) }
            viewModel.searchDataFlow.conflate().collect {
                searchBooks = it
                delay(1000)
            }
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabled().conflate().collect {
                enabledSources = it
                updateGroups()
                viewModel.execute { viewModel.refresh() }
            }
        }
        viewModel.searchFinishCallback = searchFinishCallback
    }

    override fun onDestroy() {
        viewModel.searchFinishCallback = null
        super.onDestroy()
    }

    private fun updateGroups() {
        groups = viewModel.sourceFilterGroups(enabledSources)
        settingsRevision++
    }

    private fun toggleSameTypeOnly() {
        AppConfig.changeSourceSameTypeOnly = !AppConfig.changeSourceSameTypeOnly
        viewModel.onSearchScopeChanged()
        updateGroups()
    }

    private fun toggleBlockSourceDialogs() {
        blockSourceDialogs = !blockSourceDialogs
        requireContext().putPrefBoolean(PreferKey.searchBlockSourceDialogs, blockSourceDialogs)
        viewModel.setBlockSourceDialogs(blockSourceDialogs)
    }

    private fun toggleCheckAuthor() {
        AppConfig.changeSourceCheckAuthor = !AppConfig.changeSourceCheckAuthor
        settingsRevision++
        viewModel.execute { viewModel.refresh() }
    }

    private fun toggleLoadWordCount() {
        AppConfig.changeSourceLoadWordCount = !AppConfig.changeSourceLoadWordCount
        settingsRevision++
        viewModel.onLoadWordCountChecked(AppConfig.changeSourceLoadWordCount)
    }

    private fun toggleLoadInfo() {
        AppConfig.changeSourceLoadInfo = !AppConfig.changeSourceLoadInfo
        settingsRevision++
    }

    private fun toggleLoadToc() {
        AppConfig.changeSourceLoadToc = !AppConfig.changeSourceLoadToc
        settingsRevision++
    }

    private fun selectGroup(group: String) {
        if (AppConfig.searchGroup == group) return
        AppConfig.searchGroup = group
        viewModel.onSearchScopeChanged()
        updateGroups()
    }

    private fun openToc(searchBook: SearchBook) {
        this.searchBook = searchBook
        toc = emptyList()
        showToc = true
        tocLoading = true
        val book = searchBook.toBook()
        viewModel.getToc(book, { chapters: List<BookChapter>, _: BookSource ->
            tocCurrentIndex = BookHelp.getDurChapter(
                viewModel.chapterIndex,
                viewModel.chapterTitle,
                chapters,
            )
            tocLoading = false
            toc = chapters
        }, {
            tocLoading = false
            showToc = false
            AppLog.put("单章换源获取目录出错\n$it", it, true)
        })
    }

    private fun clickChapter(bookChapter: BookChapter, nextChapterUrl: String?) {
        val selectedBook = searchBook ?: return
        tocLoading = true
        viewModel.getContent(
            selectedBook.toBook(),
            bookChapter,
            nextChapterUrl,
            contentSuccess,
        ) { message ->
            tocLoading = false
            showToc = false
            toastOnUi(message)
        }
    }

    private fun handleSourceAction(action: ChangeChapterSourceAction, searchBook: SearchBook) {
        when (action) {
            ChangeChapterSourceAction.TOP -> viewModel.topSource(searchBook)
            ChangeChapterSourceAction.BOTTOM -> viewModel.bottomSource(searchBook)
            ChangeChapterSourceAction.EDIT -> editSourceResult.launch {
                putExtra("sourceUrl", searchBook.origin)
            }
            ChangeChapterSourceAction.DISABLE -> viewModel.disableSource(searchBook)
            ChangeChapterSourceAction.DELETE -> {
                viewModel.del(searchBook)
                if (oldBookUrl == searchBook.bookUrl) {
                    viewModel.autoChangeSource(callBack?.oldBook?.type) { book, toc, source ->
                        callBack?.changeTo(source, book, toc)
                    }
                }
            }
        }
    }

    private fun setBookScore(searchBook: SearchBook, score: Int) {
        viewModel.setBookScore(searchBook, score)
        scoreRevision++
    }

    private val oldBookUrl: String?
        get() = callBack?.oldBook?.bookUrl

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            scoreRevision++
        }
    }

    interface CallBack {
        val oldBook: Book?
        fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>)
        fun replaceContent(content: String)
    }
}
