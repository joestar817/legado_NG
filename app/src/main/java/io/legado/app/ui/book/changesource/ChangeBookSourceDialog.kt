package io.legado.app.ui.book.changesource

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.isWebFile
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 单本书换源界面。业务状态仍由 [ChangeBookSourceViewModel] 管理，界面统一由 Compose 渲染。
 */
open class ChangeBookSourceDialog() : BaseComposeDialogFragment() {

    constructor(name: String, author: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
        }
    }

    private val callBack: CallBack?
        get() = parentFragment as? CallBack ?: activity as? CallBack
    private val viewModel: ChangeBookSourceViewModel by viewModels()
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            val origin = it.data?.getStringExtra("origin") ?: return@registerForActivityResult
            viewModel.startSearch(origin)
        }

    private var searchBooks by mutableStateOf(emptyList<SearchBook>())
    private var searching by mutableStateOf(false)
    private var blockSourceDialogs by mutableStateOf(false)
    private var progress by mutableStateOf(ChangeBookSourceProgressUi("", 0, 0))
    private var groups by mutableStateOf(emptyList<String>())
    private var settingsRevision by mutableIntStateOf(0)
    private var resultsRevision by mutableIntStateOf(0)
    private var scoreRevision by mutableIntStateOf(0)
    private var currentSourceRevision by mutableIntStateOf(0)

    private val searchFinishCallback: (isEmpty: Boolean) -> Unit = { isEmpty ->
        if (isEmpty) {
            val searchGroup = AppConfig.searchGroup
            if (searchGroup.isNotEmpty()) {
                lifecycleScope.launch {
                    showReadConfirmDialog(
                        context = requireContext(),
                        title = "搜索结果为空",
                        message = "${searchGroup}分组搜索结果为空,是否切换到全部分组",
                        confirmLabel = getString(R.string.yes),
                        cancelLabel = getString(R.string.no),
                        onConfirm = {
                            AppConfig.searchGroup = ""
                            settingsRevision++
                            viewModel.startSearch()
                        },
                        themeSnapshot = themeSnapshot(),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyPresentationWindow()
    }

    protected open fun applyPresentationWindow() {
        applyNgDialogWindow(height = ngDialogMaxHeight(0.92f))
    }

    protected open fun themeSnapshot(): NgThemeSnapshot =
        ReadDrawerStyle.themeSnapshot(requireContext())

    protected open fun contentPresentation(): ChangeBookSourcePresentation =
        if (activity is ReadBookActivity) {
            ChangeBookSourcePresentation.READING_DIALOG
        } else {
            ChangeBookSourcePresentation.DIALOG
        }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.initData(arguments, callBack?.oldBook, activity is ReadBookActivity)
        blockSourceDialogs = requireContext().getPrefBoolean(PreferKey.searchBlockSourceDialogs)
        viewModel.setBlockSourceDialogs(blockSourceDialogs)
        progress = ChangeBookSourceProgressUi(
            text = callBack?.oldBook?.originName.orEmpty(),
            current = 0,
            total = 0,
        )
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                settingsRevision
                resultsRevision
                scoreRevision
                currentSourceRevision
                NgAppTheme(
                    snapshot = themeSnapshot(),
                    updateSystemBars = false,
                ) {
                    ChangeBookSourceDialogContent(
                        presentation = contentPresentation(),
                        currentBookUrl = oldBookUrl,
                        searchBooks = searchBooks,
                        searching = searching,
                        progress = progress,
                        blockSourceDialogs = blockSourceDialogs,
                        settings = ChangeChapterSourceSettingsUi(
                            checkAuthor = AppConfig.changeSourceCheckAuthor,
                            loadWordCount = AppConfig.changeSourceLoadWordCount,
                            loadInfo = AppConfig.changeSourceLoadInfo,
                            loadToc = AppConfig.changeSourceLoadToc,
                            selectedGroup = AppConfig.searchGroup,
                            groups = groups,
                        ),
                        getScore = viewModel::getBookScore,
                        onClose = { dismissAllowingStateLoss() },
                        onQueryChanged = viewModel::screen,
                        onRefreshToggle = viewModel::startOrStopSearch,
                        onOpenSourceManage = { startActivity<BookSourceActivity>() },
                        onRefreshList = { viewModel.startRefreshList() },
                        onToggleBlockSourceDialogs = ::toggleBlockSourceDialogs,
                        onToggleCheckAuthor = ::toggleCheckAuthor,
                        onToggleLoadWordCount = ::toggleLoadWordCount,
                        onToggleLoadInfo = ::toggleLoadInfo,
                        onToggleLoadToc = ::toggleLoadToc,
                        onGroupSelected = ::selectGroup,
                        onSourceClick = ::changeTo,
                        onSourceAction = ::handleSourceAction,
                        onScoreChanged = ::setBookScore,
                    )
                }
            }
        }
        initLiveData()
        viewModel.searchFinishCallback = searchFinishCallback
    }

    override fun onDestroy() {
        viewModel.searchFinishCallback = null
        super.onDestroy()
    }

    private fun initLiveData() {
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            searching = it
        }
        lifecycleScope.launch {
            lifecycle.currentStateFlow.first { it.isAtLeast(STARTED) }
            viewModel.searchDataFlow.conflate().collect {
                searchBooks = it.toList()
                resultsRevision++
                delay(1000)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                viewModel.changeSourceProgress
                    .drop(1)
                    .collect { (count, name) ->
                        progress = ChangeBookSourceProgressUi(
                            text = getString(
                                R.string.change_source_progress,
                                searchBooks.size,
                                count,
                                viewModel.totalSourceCount,
                                name,
                            ),
                            current = count,
                            total = viewModel.totalSourceCount,
                        )
                        delay(500)
                    }
            }
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().conflate().collect {
                groups = it.toList()
            }
        }
    }

    private fun toggleCheckAuthor() {
        AppConfig.changeSourceCheckAuthor = !AppConfig.changeSourceCheckAuthor
        settingsRevision++
        viewModel.refresh()
    }

    private fun toggleBlockSourceDialogs() {
        blockSourceDialogs = !blockSourceDialogs
        requireContext().putPrefBoolean(
            PreferKey.searchBlockSourceDialogs,
            blockSourceDialogs,
        )
        viewModel.setBlockSourceDialogs(blockSourceDialogs)
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
        settingsRevision++
        lifecycleScope.launch(IO) {
            viewModel.stopSearch()
            if (viewModel.refresh()) viewModel.startSearch()
        }
    }

    private fun handleSourceAction(
        action: ChangeChapterSourceAction,
        searchBook: SearchBook,
    ) {
        when (action) {
            ChangeChapterSourceAction.TOP -> viewModel.topSource(searchBook)
            ChangeChapterSourceAction.BOTTOM -> viewModel.bottomSource(searchBook)
            ChangeChapterSourceAction.EDIT -> editSourceResult.launch {
                putExtra("sourceUrl", searchBook.origin)
            }

            ChangeChapterSourceAction.DISABLE -> viewModel.disableSource(searchBook)
            ChangeChapterSourceAction.DELETE -> confirmDeleteSource(searchBook)
        }
    }

    private fun confirmDeleteSource(searchBook: SearchBook) {
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.draw),
            message = getString(R.string.sure_del) + "\n" + searchBook.originName,
            confirmLabel = getString(R.string.yes),
            cancelLabel = getString(R.string.no),
            onConfirm = { deleteSource(searchBook) },
            themeSnapshot = themeSnapshot(),
        )
    }

    private fun changeTo(searchBook: SearchBook) {
        val oldBookType = callBack?.oldBook?.type ?: 0
        if (searchBook.sameBookTypeLocal(oldBookType)) {
            changeSource(searchBook) {
                dismissAllowingStateLoss()
            }
        } else {
            showReadConfirmDialog(
                context = requireContext(),
                title = getString(R.string.book_type_different),
                message = getString(R.string.soure_change_source),
                confirmLabel = getString(R.string.yes),
                cancelLabel = getString(R.string.no),
                onConfirm = {
                    changeSource(searchBook) {
                        dismissAllowingStateLoss()
                    }
                },
                themeSnapshot = themeSnapshot(),
            )
        }
    }

    private val oldBookUrl: String?
        get() = callBack?.oldBook?.bookUrl

    private fun deleteSource(searchBook: SearchBook) {
        viewModel.del(searchBook)
        if (oldBookUrl == searchBook.bookUrl) {
            viewModel.autoChangeSource(callBack?.oldBook?.type) { book, toc, source ->
                callBack?.changeTo(source, book, toc)
            }
        }
    }

    private fun setBookScore(searchBook: SearchBook, score: Int) {
        viewModel.setBookScore(searchBook, score)
        scoreRevision++
    }

    private fun changeSource(searchBook: SearchBook, onSuccess: (() -> Unit)? = null) {
        waitDialog.setText(R.string.load_toc)
        waitDialog.show()
        val book = viewModel.bookMap[searchBook.primaryStr()] ?: searchBook.toBook()
        if (book.isWebFile) {
            val source = appDb.bookSourceDao.getBookSource(book.origin)
            if (source == null) {
                AppLog.put("书源不存在", null, true)
                return
            }
            waitDialog.dismiss()
            callBack?.changeTo(source, book, emptyList())
            onSuccess?.invoke()
            return
        }
        val coroutine = viewModel.getToc(book, { toc, source ->
            waitDialog.dismiss()
            callBack?.changeTo(source, book, toc)
            onSuccess?.invoke()
        }, {
            waitDialog.dismiss()
            AppLog.put("换源获取目录出错\n$it", it, true)
        })
        waitDialog.setOnCancelListener {
            coroutine.cancel()
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            currentSourceRevision++
        }
    }

    interface CallBack {
        val oldBook: Book?
        fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>)
    }
}
