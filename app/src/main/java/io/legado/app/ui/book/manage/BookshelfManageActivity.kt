package io.legado.app.ui.book.manage

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.contains
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.model.CacheBook
import io.legado.app.service.ExportBookService
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.main.bookshelf.BookshelfBookGroupSheet
import io.legado.app.utils.ACache
import io.legado.app.utils.CreateFileContract
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.cnCompare
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.observeEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max

/** 书架管理。页面宿主、列表、选择与拖排均使用 Compose。 */
class BookshelfManageActivity :
    VMBaseActivity<ComposeActivityBinding, BookshelfManageViewModel>(),
    SourcePickerDialog.Callback,
    ExportSettingsDialog.Callback {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookshelfManageViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private val groupList: ArrayList<BookGroup> = arrayListOf()
    private var isManualSort = false
    private var booksFlowJob: Job? = null
    private var topBarQuery by mutableStateOf("")
    private var topBarGroups by mutableStateOf<List<BookGroup>>(emptyList())
    private var topBarSelectedGroupId by mutableLongStateOf(BookGroup.IdAll)
    private var books: List<Book> = emptyList()
    private var visibleBooks by mutableStateOf<List<Book>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var selectedBookUrls by mutableStateOf<Set<String>>(emptySet())
    private var cachedChapterCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var deleteDialogVisible by mutableStateOf(false)
    private var deleteOriginal by mutableStateOf(false)
    private var batchChangeSourceRunning by mutableStateOf(false)
    private var batchChangeSourceProgress by mutableStateOf("")
    private var exportedSourceUri by mutableStateOf<String?>(null)
    private val exportBookPathKey = "exportBookPath"
    private var pendingExportBooks: List<Book> = emptyList()
    private var pendingExportSettings = ExportSettingsResult()
    private val exportContentDir = registerForActivityResult(SelectDirectoryContract()) { result ->
        val books = pendingExportBooks
        if (books.isEmpty()) return@registerForActivityResult
        result.uri?.let { uri ->
            val path = if (uri.isContentScheme()) uri.toString() else uri.path ?: uri.toString()
            ACache.get().put(exportBookPathKey, path)
            startExportBooks(books, path, pendingExportSettings)
        }
    }
    private val exportSourceFile = registerForActivityResult(CreateFileContract()) {
        it.save(this, this) { uri ->
            exportedSourceUri = uri.toString()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.groupId = intent.getLongExtra("groupId", -1)
        topBarSelectedGroupId = viewModel.groupId
        initContent()
        initGroupData()
        upBookDataByGroupId()
    }

    override fun observeLiveBus() {
        viewModel.cacheChapterCountLiveData.observe(this, ::notifyBookChanged)
        viewModel.batchChangeSourceState.observe(this) {
            batchChangeSourceRunning = it
            if (!it) {
                batchChangeSourceProgress = ""
            }
        }
        viewModel.batchChangeSourceProcessLiveData.observe(this) {
            batchChangeSourceProgress = it
        }
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.addCachedChapter(book.bookUrl, chapter.url)
        }
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                BookshelfManageScreen(
                    query = topBarQuery,
                    groups = topBarGroups,
                    selectedGroupId = topBarSelectedGroupId,
                    books = visibleBooks,
                    selectedBookUrls = selectedBookUrls,
                    cachedChapterCounts = cachedChapterCounts,
                    deleteDialogVisible = deleteDialogVisible,
                    deleteOriginal = deleteOriginal,
                    batchChangeSourceRunning = batchChangeSourceRunning,
                    batchChangeSourceProgress = batchChangeSourceProgress,
                    exportedSourceUri = exportedSourceUri,
                    onQueryChange = {
                        topBarQuery = it
                        updateVisibleBooks()
                    },
                    onBack = ::finish,
                    onGroupSelected = { groupId ->
                        viewModel.groupId = groupId
                        topBarSelectedGroupId = groupId
                        upBookDataByGroupId()
                    },
                    onGroupManage = { showDialogFragment<GroupManageDialog>() },
                    onSelectionChange = ::setBookSelected,
                    onOpenDetails = ::openBook,
                    onReorderStarted = ::prepareManualSort,
                    onMove = ::moveBook,
                    onReorderFinished = ::finishDrag,
                    onSelectAll = ::selectAll,
                    onInvertSelection = ::invertSelection,
                    onDockAction = ::onDockAction,
                    onDeleteOriginalChange = { deleteOriginal = it },
                    onDismissDelete = { deleteDialogVisible = false },
                    onConfirmDelete = ::confirmDeleteSelection,
                    onCancelBatchChangeSource = {
                        viewModel.batchChangeSourceCoroutine?.cancel()
                    },
                    onDismissExportSuccess = { exportedSourceUri = null },
                    onCopyExportPath = {
                        exportedSourceUri?.let(::sendToClip)
                        exportedSourceUri = null
                    },
                )
            }
        }
    }

    private fun initGroupData() {
        lifecycleScope.launch {
            appDb.bookGroupDao.flowAll().catch {
                AppLog.put("书架管理界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                groupList.clear()
                groupList.addAll(it)
                topBarGroups = it.toList()
            }
        }
    }

    private fun upBookDataByGroupId() {
        booksFlowJob?.cancel()
        booksFlowJob = lifecycleScope.launch {
            appDb.bookDao.flowByGroup(viewModel.groupId).map { list ->
                val bookSort = AppConfig.getBookSortByGroupId(viewModel.groupId)
                val sortedBooks = when (bookSort) {
                    1 -> list.sortedByDescending { it.latestChapterTime }
                    2 -> list.sortedWith { first, second ->
                        first.name.cnCompare(second.name)
                    }

                    3 -> list.sortedBy { it.order }
                    4 -> list.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }

                    else -> list.sortedByDescending { it.durChapterTime }
                }
                sortedBooks to bookSort
            }.catch {
                AppLog.put("书架管理界面获取书籍列表失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                books = it.first
                isManualSort = it.second == 3
                updateVisibleBooks()
                viewModel.loadCacheFiles(it.first)
            }
        }
    }

    private fun updateVisibleBooks() {
        visibleBooks = if (topBarQuery.isEmpty()) {
            books
        } else {
            books.filter { it.contains(topBarQuery) }
        }
    }

    private fun notifyBookChanged(bookUrl: String) {
        val count = viewModel.cacheChapters[bookUrl]?.size ?: return
        cachedChapterCounts = cachedChapterCounts + (bookUrl to count)
    }

    private fun setBookSelected(book: Book, selected: Boolean) {
        selectedBookUrls = if (selected) {
            selectedBookUrls + book.bookUrl
        } else {
            selectedBookUrls - book.bookUrl
        }
    }

    private fun selectAll() {
        val visibleUrls = visibleBooks.mapTo(hashSetOf()) { it.bookUrl }
        if (visibleUrls.isEmpty()) return
        selectedBookUrls = if (visibleUrls.count(selectedBookUrls::contains) < visibleUrls.size) {
            selectedBookUrls + visibleUrls
        } else {
            emptySet()
        }
    }

    private fun invertSelection() {
        var updated = selectedBookUrls
        visibleBooks.forEach { book ->
            updated = if (book.bookUrl in updated) {
                updated - book.bookUrl
            } else {
                updated + book.bookUrl
            }
        }
        selectedBookUrls = updated
    }

    private fun selectedBooks(): List<Book> {
        return visibleBooks.filter { it.bookUrl in selectedBookUrls }
    }

    private fun onDockAction(action: BookshelfManageDockAction) {
        val selected = selectedBooks()
        if (selected.isEmpty()) return
        when (action) {
            BookshelfManageDockAction.CACHE -> selected.forEach { book ->
                cacheBook(book, 0, book.lastChapterIndex)
            }

            BookshelfManageDockAction.EXPORT_CONTENT -> showExportSettings(selected)
            BookshelfManageDockAction.GROUP -> BookshelfBookGroupSheet(this, selected).show()
            BookshelfManageDockAction.EXPORT_SOURCE -> exportBookSources(selected)
            BookshelfManageDockAction.CHANGE_SOURCE -> showDialogFragment<SourcePickerDialog>()
            BookshelfManageDockAction.ENABLE_UPDATE -> viewModel.upCanUpdate(selected, true)
            BookshelfManageDockAction.DISABLE_UPDATE -> viewModel.upCanUpdate(selected, false)
            BookshelfManageDockAction.REMOVE_GROUP -> clearSelectedBookGroups(selected)
            BookshelfManageDockAction.CLEAR_CACHE -> viewModel.clearCache(selected)
            BookshelfManageDockAction.DELETE -> requestDeleteSelection()
        }
    }

    private fun requestDeleteSelection() {
        deleteOriginal = LocalConfig.deleteBookOriginal
        deleteDialogVisible = true
    }

    private fun confirmDeleteSelection() {
        val selected = selectedBooks()
        deleteDialogVisible = false
        if (selected.isEmpty()) return
        LocalConfig.deleteBookOriginal = deleteOriginal
        viewModel.deleteBook(selected, deleteOriginal)
    }

    private fun clearSelectedBookGroups(books: List<Book>) {
        viewModel.updateBook(*books.map { it.copy(group = 0L) }.toTypedArray())
    }

    private fun openBook(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    private fun moveBook(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in visibleBooks.indices ||
            toIndex !in visibleBooks.indices ||
            fromIndex == toIndex
        ) {
            return
        }
        val reordered = visibleBooks.toMutableList()
        val source = reordered[fromIndex]
        val target = reordered[toIndex]
        val sourceOrder = source.order
        source.order = target.order
        target.order = sourceOrder
        reordered.add(toIndex, reordered.removeAt(fromIndex))
        visibleBooks = reordered
    }

    private fun finishDrag() {
        books.takeIf { it.isNotEmpty() }?.let {
            viewModel.updateBook(*it.toTypedArray())
        }
    }

    private fun prepareManualSort() {
        books.forEachIndexed { index, book ->
            book.order = index + 1
        }
        if (!isManualSort) {
            val currentGroup = groupList.firstOrNull { it.groupId == viewModel.groupId }
            if (currentGroup != null && currentGroup.bookSort >= 0) {
                currentGroup.bookSort = 3
                appDb.bookGroupDao.update(currentGroup)
            } else {
                AppConfig.bookshelfSort = 3
            }
            isManualSort = true
        }
    }

    private fun cacheBook(book: Book, start: Int, end: Int) {
        if (book.isLocal) return
        CacheBook.start(this, book, start, end)
    }

    private fun showExportSettings(books: List<Book>) {
        pendingExportBooks = books
        ExportSettingsDialog.show(supportFragmentManager, books.size)
    }

    override fun onExportSettingsConfirmed(result: ExportSettingsResult) {
        val books = pendingExportBooks
        if (books.isEmpty()) return
        pendingExportSettings = result
        val initialUri = ACache.get().getAsString(exportBookPathKey)
            ?.takeIf { it.startsWith("content://") }
            ?.let(android.net.Uri::parse)
        exportContentDir.launch(SelectDirectoryContract.Request(initialUri = initialUri))
    }

    private fun startExportBooks(
        books: List<Book>,
        path: String,
        settings: ExportSettingsResult,
    ) {
        val exportType = if (settings.exportType == 1) "epub" else "txt"
        books.forEach { book ->
            startService<ExportBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("exportType", exportType)
                putExtra("exportPath", path)
                putExtra("exportUseReplace", true)
                putExtra("parallelExport", true)
                putExtra("exportToWebDav", false)
                putExtra("includeChapterName", true)
                putExtra("exportPlainText", settings.plainText)
                putExtra("exportFilterInteractiveImages", settings.filterInteractiveImages)
                putExtra("exportPictureFile", settings.exportPictures)
            }
        }
        toastOnUi(R.string.export_wait)
    }

    private fun exportBookSources(books: List<Book>) {
        BookSourceExportSheet(
            context = this,
            onShare = { shareBookSources(books) },
            onSaveLocally = { saveBookSourcesLocally(books) },
        ).show()
    }

    private fun shareBookSources(books: List<Book>) {
        viewModel.saveBookSourcesToFile(books) { file, _ ->
            share(file)
        }
    }

    private fun saveBookSourcesLocally(books: List<Book>) {
        viewModel.saveBookSourcesToFile(books) { file, name ->
            exportSourceFile.launch(CreateFileContract.FileData(name, file, "application/json"))
        }
    }

    override fun sourceOnClick(source: BookSource) {
        viewModel.changeSource(selectedBooks(), source)
        viewModel.batchChangeSourceState.value = true
    }
}
