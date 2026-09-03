package io.legado.app.ui.main.bookshelf.style1.books

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfLayoutMode
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.CacheBook
import io.legado.app.model.BookCacheManager
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.SourceCallBack
import io.legado.app.model.localBook.LocalBook
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.service.ExportBookService
import io.legado.app.ui.book.character.BookCharacterActivity
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDrawer
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.ui.book.info.BookAiAssistantLauncher
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.manage.ExportSettingsDialog
import io.legado.app.ui.book.manage.ExportSettingsResult
import io.legado.app.ui.book.read.aloud.ReadAloudLauncher
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.bookshelf.BookshelfBookActionSheet
import io.legado.app.ui.main.bookshelf.BookshelfBookGroupSheet
import io.legado.app.ui.main.bookshelf.SimulatedReadingDialog
import io.legado.app.ui.main.MainViewModel
import io.legado.app.utils.ACache
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max

/**
 * 书架界面
 */
class BooksFragment() : BaseFragment(0),
    BookshelfBookActionSheet.Callback,
    ChangeBookSourceDialog.CallBack,
    ExportSettingsDialog.Callback,
    SimulatedReadingDialog.Callback {

    constructor(group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        bundle.putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        arguments = bundle
    }

    private val activityViewModel by activityViewModels<MainViewModel>()
    private val layoutMode by lazy { AppConfig.activeBookshelfLayoutMode }
    private var layoutProfile by mutableStateOf(AppConfig.getBookshelfLayoutProfile(layoutMode))
    private var bookItems by mutableStateOf<List<Book>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var bottomInsetPx by mutableIntStateOf(0)
    private var scrollToTopToken by mutableLongStateOf(0L)
    private var coverRevision by mutableIntStateOf(0)
    private var lastUpdateTick by mutableLongStateOf(System.currentTimeMillis())
    private var updatingBookUrls by mutableStateOf<Set<String>>(emptySet())
    private var booksFlowJob: Job? = null
    var groupId = -1L
        private set
    val configuredGroupId: Long
        get() = arguments?.getLong("groupId", groupId) ?: groupId
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private var refreshAllowed by mutableStateOf(true)
    private var onlyUpdateRead = false
    private var actionBook: Book? = null
    private var tocBook: Book? = null
    private var exportBook: Book? = null
    private var pendingExportSettings = ExportSettingsResult()
    private val exportBookPathKey = "exportBookPath"
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) { result ->
        val book = tocBook ?: return@registerForActivityResult
        tocBook = null
        result ?: return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val chapterChanged = result[2] as Boolean
            val updatedBook = withContext(IO) {
                book.copy().apply {
                    durChapterIndex = result[0] as Int
                    durChapterPos = result[1] as Int
                    durVolumeIndex = result[3] as Int
                    chapterInVolumeIndex = result[4] as Int
                    appDb.bookDao.update(this)
                }
            }
            startActivityForBook(updatedBook) {
                putExtra("chapterChanged", chapterChanged)
            }
        }
    }
    private val exportDir = registerForActivityResult(SelectDirectoryContract()) { result ->
        val book = exportBook ?: return@registerForActivityResult
        result.uri?.let { uri ->
            val path = if (uri.isContentScheme()) uri.toString() else uri.path ?: uri.toString()
            ACache.get().put(exportBookPathKey, path)
            startExportBook(book, path, pendingExportSettings)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            refreshAllowed = it.getBoolean("enableRefresh", true)
            onlyUpdateRead = it.getBoolean("onlyUpdateRead", false)
        }
        (view as ComposeView).setContent {
            NgAppTheme {
                val bottomInset = with(LocalDensity.current) { bottomInsetPx.toDp() }
                BookshelfBooksScreen(
                    books = bookItems,
                    layoutMode = layoutMode,
                    columns = layoutProfile.columns,
                    spacing = layoutProfile.spacing,
                    showBookName = layoutProfile.showBookName,
                    coverRadius = layoutProfile.coverRadius,
                    showUnread = layoutProfile.showUnread,
                    showLastUpdateTime = layoutProfile.showLastUpdateTime,
                    bottomInset = bottomInset,
                    scrollToTopToken = scrollToTopToken,
                    coverRevision = coverRevision,
                    lastUpdateTick = lastUpdateTick,
                    isEInk = AppConfig.isEInkMode,
                    updatingBookUrls = updatingBookUrls,
                    refreshEnabled = refreshAllowed && bookItems.isNotEmpty(),
                    onRefresh = {
                        // 保留旧书架行为：手势完成后直接后台更新目录，不维持加载态。
                        activityViewModel.upToc(bookItems, onlyUpdateRead)
                    },
                    onOpenBook = ::open,
                    onOpenBookInfo = ::openBookInfo,
                    onOpenBookActions = ::openBookActions,
                )
            }
        }
        (activity as? MainActivity)?.resolveFloatingBottomContentInset { inset ->
            bottomInsetPx = inset
        }
        upRecyclerData()
        startLastUpdateTimeJob()
    }

    fun updateGroup(group: BookGroup) {
        val sort = group.getRealBookSort()
        val sortChanged = bookSort != sort
        arguments?.apply {
            putLong("groupId", group.groupId)
            putInt("bookSort", sort)
            putBoolean("enableRefresh", group.enableRefresh)
            putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        }
        groupId = group.groupId
        bookSort = sort
        refreshAllowed = group.enableRefresh
        onlyUpdateRead = group.onlyUpdateRead
        if (view != null && sortChanged) {
            view?.post { upRecyclerData() }
        }
    }

    /**
     * 更新书籍列表信息
     */
    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序
                when (bookSort) {
                    1 -> list.sortedByDescending { it.latestChapterTime }
                    2 -> list.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> list.sortedBy { it.order }

                    // 综合排序 issue #3192
                    4 -> list.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }
                    // 按作者排序
                    5 -> list.sortedWith { o1, o2 ->
                        o1.author.cnCompare(o2.author)
                    }

                    else -> list.sortedByDescending { it.durChapterTime }
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                bookItems = list
                updatingBookUrls = list.asSequence()
                    .map(Book::bookUrl)
                    .filter(activityViewModel::isUpdate)
                    .toSet()
                delay(100)
            }
        }
    }

    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!layoutProfile.showLastUpdateTime || layoutMode != BookshelfLayoutMode.LIST) {
            return
        }
        upLastUpdateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    lastUpdateTick = System.currentTimeMillis()
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> {
        return bookItems
    }

    fun gotoTop() {
        scrollToTopToken++
    }

    fun getBooksCount(): Int {
        return bookItems.size
    }

    private fun open(book: Book) {
        startActivityForBook(book)
    }

    private fun openBookInfo(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    private fun openBookActions(book: Book) {
        actionBook = book
        BookshelfBookActionSheet(this, book, this).show()
    }

    override fun onDetail(book: Book) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
        }
    }

    override fun onChapterList(book: Book) {
        tocBook = book
        tocActivityResult.launch(book.bookUrl)
    }

    override fun onCharacters(book: Book) {
        startActivity<BookCharacterActivity> {
            putExtra(BookCharacterActivity.EXTRA_WORK_KEY, BookCharacterProfile.workKey(book.name, book.author))
            putExtra(BookCharacterActivity.EXTRA_BOOK_NAME, book.name)
            putExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR, book.author)
            putExtra(BookCharacterActivity.EXTRA_BOOK_URL, book.bookUrl)
        }
    }

    override fun onGroup(book: Book) {
        BookshelfBookGroupSheet(this, book).show()
    }

    override fun onExport(book: Book) {
        exportBook = book
        ExportSettingsDialog.show(childFragmentManager, selectedCount = 1)
    }

    override fun onExportSettingsConfirmed(result: ExportSettingsResult) {
        if (exportBook == null) return
        pendingExportSettings = result
        val initialUri = ACache.get().getAsString(exportBookPathKey)
            ?.takeIf { it.startsWith("content://") }
            ?.let(android.net.Uri::parse)
        exportDir.launch(SelectDirectoryContract.Request(initialUri = initialUri))
    }

    private fun startExportBook(
        book: Book,
        path: String,
        settings: ExportSettingsResult,
    ) {
        val exportType = if (settings.exportType == 1) "epub" else "txt"
        requireContext().startService<ExportBookService> {
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
        toastOnUi(R.string.export_wait)
    }

    override fun onListen(book: Book) {
        if (book.isAudio) {
            startActivity<AudioPlayActivity> {
                putExtra("bookUrl", book.bookUrl)
                putExtra("inBookshelf", true)
                AudioPlayActivity.applyAutoStart(this)
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val prepared = ReadAloudLauncher.prepareState(
                book = book,
                inBookshelf = true,
                chapterChanged = false
            )
            if (prepared) {
                ReadAloudLauncher.openPlayer(requireContext(), autoStart = true)
            } else {
                toastOnUi(ReadBook.msg ?: "初始化听书失败")
            }
        }
    }

    override fun onDownload(book: Book) {
        if (book.isLocal) {
            toastOnUi(R.string.local_book)
            return
        }
        CacheBook.cacheBookMap[book.bookUrl]?.let {
            if (!it.isStop()) {
                CacheBook.remove(requireContext(), book.bookUrl)
            } else {
                CacheBook.start(requireContext(), book, 0, book.lastChapterIndex)
            }
        } ?: CacheBook.start(requireContext(), book, 0, book.lastChapterIndex)
    }

    override fun onChangeSource(book: Book) {
        actionBook = book
        showDialogFragment(ChangeBookSourceDrawer(book.name, book.author))
    }

    override fun onSimulatedReading(book: Book) {
        showDialogFragment(SimulatedReadingDialog(book))
    }

    override fun onSimulatedReadingConfirmed(
        bookUrl: String,
        enabled: Boolean,
        startDate: LocalDate,
        startChapter: Int,
        dailyChapters: Int,
    ) {
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            val book = appDb.bookDao.getBook(bookUrl) ?: return@launch
            book.setStartDate(startDate)
            book.setDailyChapters(dailyChapters)
            book.setStartChapter(startChapter)
            book.setReadSimulating(enabled)
            book.save()
            postEvent(EventBus.UP_BOOKSHELF, bookUrl)
        }
    }

    override fun onBookScan(book: Book) {
        BookAiAssistantLauncher.openBookScan(requireContext(), book)
    }

    override fun onAllowUpdateChanged(book: Book, allowUpdate: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            val updatedBook = book.copy(canUpdate = allowUpdate)
            if (!allowUpdate) {
                updatedBook.removeType(BookType.updateError)
            }
            appDb.bookDao.update(updatedBook)
            postEvent(EventBus.UP_BOOKSHELF, book.bookUrl)
        }
    }

    override fun onClearCache(book: Book) {
        val hostActivity = activity as? AppCompatActivity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val source = withContext(IO) {
                appDb.bookSourceDao.getBookSource(book.origin)
            }
            SourceCallBack.callBackBtn(
                hostActivity,
                SourceCallBack.CLICK_CLEAR_CACHE,
                source,
                book,
                null,
            ) {
                clearBookCache(book)
            }
        }
    }

    private fun clearBookCache(book: Book) {
        viewLifecycleOwner.lifecycleScope.launch {
            val error = withContext(IO) {
                runCatching {
                    BookCacheManager.clear(book)
                    if (ReadBook.book?.bookUrl == book.bookUrl) {
                        ReadBook.clearTextChapter()
                    }
                    if (ReadManga.book?.bookUrl == book.bookUrl) {
                        ReadManga.clearMangaChapter()
                    }
                }.exceptionOrNull()
            }
            if (error == null) {
                toastOnUi(R.string.clear_cache_success)
            } else {
                toastOnUi("清理缓存出错\n${error.localizedMessage}")
            }
        }
    }

    override fun onDelete(book: Book) {
        alert(
            titleResource = R.string.draw,
            messageResource = R.string.sure_del
        ) {
            var checkBox: CheckBox? = null
            if (book.isLocal) {
                checkBox = CheckBox(requireContext()).apply {
                    setText(R.string.delete_book_file)
                    isChecked = LocalConfig.deleteBookOriginal
                }
                customView {
                    LinearLayout(requireContext()).apply {
                        setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                        addView(checkBox)
                    }
                }
            }
            yesButton {
                checkBox?.let {
                    LocalConfig.deleteBookOriginal = it.isChecked
                }
                deleteBook(book, checkBox?.isChecked == true)
            }
            noButton()
        }
    }

    private fun deleteBook(book: Book, deleteOriginal: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val source = appDb.bookSourceDao.getBookSource(book.origin)
            SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, source, book)
            book.delete()
            if (book.isLocal) {
                LocalBook.deleteBook(book, deleteOriginal)
            }
            postEvent(EventBus.UP_BOOKSHELF, book.bookUrl)
        }
    }

    override val oldBook: Book?
        get() = actionBook

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        val oldBook = actionBook ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            book.removeType(BookType.updateError)
            oldBook.delete()
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
            postEvent(EventBus.UP_BOOKSHELF, book.bookUrl)
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) { bookUrl ->
            if (bookItems.none { it.bookUrl == bookUrl }) {
                return@observeEvent
            }
            updatingBookUrls = if (activityViewModel.isUpdate(bookUrl)) {
                updatingBookUrls + bookUrl
            } else {
                updatingBookUrls - bookUrl
            }
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            layoutProfile = AppConfig.getBookshelfLayoutProfile(layoutMode)
            updatingBookUrls = bookItems.asSequence()
                .map(Book::bookUrl)
                .filter(activityViewModel::isUpdate)
                .toSet()
            coverRevision++
            startLastUpdateTimeJob()
        }
    }
}
