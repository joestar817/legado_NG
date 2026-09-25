package io.legado.app.ui.book.read

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.CatalogOutlineEntry
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgSideDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDefaults
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgDrawerOptionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal abstract class CatalogDrawerDialog : BottomSheetDialogFragment() {

    private var chapterCount by mutableStateOf(0)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private var cachedChapterFiles by mutableStateOf<Set<String>>(emptySet())
    private var cacheRunning by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var refreshing by mutableStateOf(false)
    private var dataVersion by mutableStateOf(0)
    private var catalogOutline by mutableStateOf<List<CatalogOutlineEntry>?>(null)
    private var catalogThemeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    protected abstract fun catalogBook(): Book?

    protected abstract fun currentChapterIndex(): Int

    protected abstract fun onChapterSelected(chapter: BookChapter)

    protected open fun showBookmarks(): Boolean = false

    protected open fun showCacheState(): Boolean = false

    protected open fun showCacheAction(): Boolean = false

    protected open fun refreshCatalog(book: Book, complete: (Boolean) -> Unit) = complete(false)

    protected open fun isCacheRunning(book: Book): Boolean = false

    protected open fun onCacheAction(book: Book, currentlyRunning: Boolean): Boolean = false

    protected open fun cachedChapterFileNames(book: Book): Set<String> =
        BookHelp.getChapterFiles(book)

    protected open fun isLocalBook(): Boolean = false

    protected open fun beforeCatalogContent(): Boolean = true

    protected open fun onCatalogDismissed() = Unit

    protected open fun onBookmarkSelected(bookmark: Bookmark) = Unit

    protected open fun visualStyle(): CatalogDrawerVisualStyle =
        CatalogDrawerVisualStyle.LISTENING

    protected open fun initialCatalogTheme(book: Book): NgThemeSnapshot? = null

    protected open suspend fun resolveCatalogTheme(book: Book): NgThemeSnapshot? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val book = catalogBook() ?: run {
            dismissAllowingStateLoss()
            return
        }
        if (!beforeCatalogContent()) {
            dismissAllowingStateLoss()
            return
        }
        catalogThemeSnapshot = initialCatalogTheme(book)
        cacheRunning = isCacheRunning(book)
        (view as ComposeView).setContent {
            NgAppTheme(snapshot = catalogThemeSnapshot, updateSystemBars = false) {
                ReadCatalogPanel(
                    chapterCount = chapterCount,
                    bookmarks = bookmarks,
                    cachedChapterFiles = cachedChapterFiles,
                    showBookmarks = showBookmarks(),
                    showCacheState = showCacheState(),
                    showCacheAction = showCacheAction(),
                    cacheRunning = cacheRunning,
                    isLocalBook = isLocalBook(),
                    currentChapterIndex = currentChapterIndex(),
                    visualStyle = visualStyle(),
                    book = book,
                    onDismiss = { dismissAllowingStateLoss() },
                    loading = loading,
                    refreshing = refreshing,
                    dataVersion = dataVersion,
                    outline = catalogOutline,
                    loadChapterIndices = { indices ->
                        withContext(Dispatchers.IO) {
                            val chapters = appDb.bookChapterDao.getChaptersByIndices(
                                catalogBook()?.bookUrl ?: book.bookUrl, indices,
                            ).associateBy { it.index }
                            displayChapters(indices.mapNotNull(chapters::get))
                        }
                    },
                    onRefresh = {
                        if (!refreshing) {
                            refreshing = true
                            refreshCatalog(catalogBook() ?: book) { success ->
                                refreshing = false
                                if (this@CatalogDrawerDialog.view != null) {
                                    if (success) loadCatalogData(catalogBook() ?: book)
                                    else context?.toastOnUi(R.string.error_load_toc)
                                }
                            }
                        }
                    },
                    loadChapterCount = { query ->
                        loadChapterCount(catalogBook()?.bookUrl ?: book.bookUrl, query)
                    },
                    loadChapterPosition = { descending, totalCount ->
                        loadChapterPosition(
                            bookUrl = catalogBook()?.bookUrl ?: book.bookUrl,
                            chapterIndex = currentChapterIndex(),
                            descending = descending,
                            totalCount = totalCount,
                        )
                    },
                    loadChapterPage = { query, descending, offset, limit ->
                        loadChapterPage(
                            bookUrl = catalogBook()?.bookUrl ?: book.bookUrl,
                            query = query,
                            descending = descending,
                            offset = offset,
                            limit = limit,
                        )
                    },
                    onChapterClick = ::onChapterSelected,
                    onCacheClick = {
                        cacheRunning = onCacheAction(book, cacheRunning)
                    },
                    onBookmarkClick = ::onBookmarkSelected,
                    onBookmarkDelete = ::deleteBookmark,
                )
            }
        }
        loadCatalogData(book)
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (eventBook, chapter) ->
            if (eventBook.bookUrl == book.bookUrl) {
                cachedChapterFiles = cachedChapterFiles + chapter.getFileName()
            }
        }
        observeEvent<String>(EventBus.UP_DOWNLOAD) { bookUrl ->
            if (bookUrl.isEmpty() || bookUrl == book.bookUrl) {
                cacheRunning = isCacheRunning(book)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            resolveCatalogTheme(book)?.let { catalogThemeSnapshot = it }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.18f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            isHideable = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onCatalogDismissed()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示阅读目录抽屉失败 tag:$tag", it) }
    }

    private fun loadCatalogData(book: Book) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        appDb.bookChapterDao.getChapterCount(book.bookUrl),
                        if (showBookmarks()) {
                            appDb.bookmarkDao.getByBook(book.name, book.author)
                        } else {
                            emptyList()
                        },
                        if (showCacheState()) {
                            cachedChapterFileNames(book)
                        } else {
                            emptySet()
                        },
                    ) to if (visualStyle() != CatalogDrawerVisualStyle.LISTENING) {
                        appDb.bookChapterDao.getCatalogOutline(book.bookUrl)
                    } else null
                }
            }.onSuccess { (data, outline) ->
                val (totalCount, bookmarkItems, cacheFiles) = data
                chapterCount = totalCount
                catalogOutline = outline
                bookmarks = bookmarkItems
                cachedChapterFiles = cacheFiles
                dataVersion++
            }.onFailure {
                AppLog.put("阅读目录抽屉加载失败\n${it.localizedMessage}", it)
            }
            loading = false
        }
    }

    private suspend fun loadChapterCount(bookUrl: String, query: String): Int =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                appDb.bookChapterDao.getChapterCount(bookUrl)
            } else {
                appDb.bookChapterDao.getChapterCount(bookUrl, query)
            }
        }

    private fun deleteBookmark(bookmark: Bookmark) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appDb.bookmarkDao.delete(bookmark)
                }
            }.onSuccess {
                bookmarks = bookmarks.filterNot { it.time == bookmark.time }
            }.onFailure {
                AppLog.put("阅读目录抽屉删除书签失败\n${it.localizedMessage}", it)
            }
        }
    }

    private suspend fun loadChapterPosition(
        bookUrl: String,
        chapterIndex: Int,
        descending: Boolean,
        totalCount: Int,
    ): Int = withContext(Dispatchers.IO) {
        val ascendingPosition = appDb.bookChapterDao
            .getChapterPosition(bookUrl, chapterIndex)
            .coerceIn(0, (totalCount - 1).coerceAtLeast(0))
        if (descending) {
            (totalCount - 1 - ascendingPosition).coerceAtLeast(0)
        } else {
            ascendingPosition
        }
    }

    private suspend fun loadChapterPage(
        bookUrl: String,
        query: String,
        descending: Boolean,
        offset: Int,
        limit: Int,
    ): List<CatalogChapter> = withContext(Dispatchers.IO) {
        val chapters = when {
            query.isBlank() && descending -> appDb.bookChapterDao
                .getChapterPageDescending(bookUrl, offset, limit)

            query.isBlank() -> appDb.bookChapterDao.getChapterPage(bookUrl, offset, limit)
            descending -> appDb.bookChapterDao
                .searchPageDescending(bookUrl, query, offset, limit)

            else -> appDb.bookChapterDao.searchPage(bookUrl, query, offset, limit)
        }
        displayChapters(chapters)
    }

    private fun displayChapters(chapters: List<BookChapter>): List<CatalogChapter> {
        val book = catalogBook()
        val useReplace = visualStyle() != CatalogDrawerVisualStyle.LISTENING &&
            AppConfig.tocUiUseReplace && book?.getUseReplaceRule() == true
        val rules = if (useReplace && book != null) {
            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
        } else null
        val replaceBook = if (useReplace) book?.toReplaceBook() else null
        return chapters.map {
            CatalogChapter(it, it.getDisplayTitle(rules, useReplace, replaceBook = replaceBook))
        }
    }
}

internal open class ReadCatalogDialog : CatalogDrawerDialog() {

    private var bottomDialogRegistered = false

    override fun refreshCatalog(book: Book, complete: (Boolean) -> Unit) {
        ViewModelProvider(requireActivity())[ReadBookViewModel::class.java]
            .loadChapterList(book, complete)
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.let(ReadDrawerStyle::installImeOnlyBottomSheetInsetsAnimation)
    }

    override fun catalogBook(): Book? = ReadBook.book

    override fun currentChapterIndex(): Int = ReadBook.durChapterIndex

    override fun showBookmarks(): Boolean = true

    override fun showCacheState(): Boolean = true

    override fun isLocalBook(): Boolean = ReadBook.isLocalBook

    override fun visualStyle(): CatalogDrawerVisualStyle =
        CatalogDrawerVisualStyle.READING_ORIGINAL

    override fun initialCatalogTheme(book: Book): NgThemeSnapshot =
        ReadDrawerStyle.themeSnapshot(requireContext())

    override fun beforeCatalogContent(): Boolean {
        if (bottomDialogRegistered) return true
        val readActivity = activity as? ReadBookActivity ?: return false
        if (readActivity.bottomDialog > 0) return false
        readActivity.bottomDialog += 1
        bottomDialogRegistered = true
        return true
    }

    override fun onCatalogDismissed() {
        if (!bottomDialogRegistered) return
        (activity as? ReadBookActivity)?.let {
            it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
        }
        bottomDialogRegistered = false
    }

    override fun onChapterSelected(chapter: BookChapter) {
        ReadBook.openChapter(chapter.index)
        dismissAllowingStateLoss()
    }

    override fun onBookmarkSelected(bookmark: Bookmark) {
        ReadBook.openChapter(bookmark.chapterIndex, bookmark.chapterPos)
        dismissAllowingStateLoss()
    }
}

internal class ReadCompactCatalogDialog : ReadCatalogDialog() {

    override fun visualStyle(): CatalogDrawerVisualStyle =
        CatalogDrawerVisualStyle.READING_COMPACT_SIDE

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        ComponentDialog(requireContext(), R.style.AppTheme_CompactCatalog).also {
            it.window?.let { window -> WindowCompat.setDecorFitsSystemWindows(window, false) }
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            decorView.setPadding(0, 0, 0, 0)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = AndroidColor.TRANSPARENT
            navigationBarColor = AndroidColor.TRANSPARENT
            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = !ReadDrawerStyle.themeSnapshot(requireContext()).isDark
                hide(WindowInsetsCompat.Type.statusBars())
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}

private data class CatalogChapter(
    val chapter: BookChapter,
    val displayTitle: String,
)

private enum class CatalogTab { Chapters, Bookmarks }

internal enum class CatalogDrawerVisualStyle {
    READING_ORIGINAL,
    READING_COMPACT_SIDE,
    LISTENING,
}

private const val CATALOG_PAGE_SIZE = 64
private const val CATALOG_PRELOAD_ITEMS = 16
private const val CATALOG_RETAINED_PAGE_RADIUS = 2
private val catalogUpdateTimeRegex = Regex(
    """(?:更新)?时间\s*[:：]\s*(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)"""
)
private val catalogSourceWordCountRegex = Regex(
    """(?:章节)?字数\s*[:：]\s*([0-9万千百.]+)\s*字?"""
)

@Composable
private fun ReadCatalogPanel(
    book: Book,
    onDismiss: () -> Unit,
    chapterCount: Int,
    bookmarks: List<Bookmark>,
    cachedChapterFiles: Set<String>,
    showBookmarks: Boolean,
    showCacheState: Boolean,
    showCacheAction: Boolean,
    cacheRunning: Boolean,
    isLocalBook: Boolean,
    currentChapterIndex: Int,
    visualStyle: CatalogDrawerVisualStyle,
    loading: Boolean,
    refreshing: Boolean,
    dataVersion: Int,
    outline: List<CatalogOutlineEntry>?,
    loadChapterIndices: suspend (List<Int>) -> List<CatalogChapter>,
    onRefresh: () -> Unit,
    loadChapterCount: suspend (String) -> Int,
    loadChapterPosition: suspend (Boolean, Int) -> Int,
    loadChapterPage: suspend (String, Boolean, Int, Int) -> List<CatalogChapter>,
    onChapterClick: (BookChapter) -> Unit,
    onCacheClick: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
) {
    var searchVisible by remember { mutableStateOf(false) }
    var showWordCount by remember { mutableStateOf(AppConfig.tocCountWords) }
    var useReplace by remember { mutableStateOf(AppConfig.tocUiUseReplace) }
    var autoExpandNotes by remember { mutableStateOf(AppConfig.bookmarkAutoExpandNotes) }
    val menuState = remember { NgPopupToggleState() }
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    var collapsedVolumes by remember { mutableStateOf<Set<String>>(emptySet()) }
    val volumeIds = remember(outline) {
        outline.orEmpty().filter { it.isVolume }.mapTo(linkedSetOf()) { it.url }
    }
    val allVolumesCollapsed = volumeIds.isNotEmpty() && volumeIds.all { it in collapsedVolumes }
    val visibleOutline = remember(outline, collapsedVolumes, descending) {
        outline?.let { visibleCatalogRows(it, collapsedVolumes, descending) }
    }
    var visibleChapterCount by remember(chapterCount) { mutableStateOf(chapterCount) }
    val contentColor = Color(NgTheme.colors.onSurface)
    val mutedColor = Color(NgTheme.colors.onSurfaceVariant)
    val accentColor = Color(NgTheme.colors.primary)
    val selectedContentColor = Color(NgTheme.colors.onPrimary)
    val dockColor = if (NgTheme.snapshot.isDark || NgTheme.snapshot.isEInk) {
        Color(NgTheme.colors.surfaceContainerLow)
    } else {
        contentColor.copy(alpha = 0.025f)
    }
    val listBackgroundColor = if (visualStyle != CatalogDrawerVisualStyle.LISTENING) {
        Color.Transparent
    } else {
        catalogListBackgroundColor(mutedColor)
    }
    val filteredBookmarks = remember(bookmarks, query) {
        bookmarks.filter {
            query.isBlank() || it.chapterName.contains(query, ignoreCase = true) ||
                it.bookText.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true)
        }
    }
    val chapterListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    val tabs = remember(showBookmarks) {
        if (showBookmarks) CatalogTab.entries else listOf(CatalogTab.Chapters)
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val pagerScope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage.coerceIn(tabs.indices)]
    val bookmarkMenuLabel = stringResource(R.string.bookmark_auto_expand_notes)
    val bookmarkMenuTextWidth = rememberTextMeasurer().measure(
        text = bookmarkMenuLabel,
        style = TextStyle(fontFamily = NgTheme.fontFamily, fontSize = 15.sp),
        softWrap = false,
        maxLines = 1,
    ).size.width
    val bookmarkMenuWidth = with(LocalDensity.current) {
        // 两侧内边距24dp、选中图标18dp、间距10dp，单项菜单按内容收紧。
        (bookmarkMenuTextWidth.toDp() + 48.dp).coerceAtLeast(136.dp)
    }
    val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
    LaunchedEffect(query, chapterCount) {
        visibleChapterCount = if (query.isBlank()) chapterCount else 0
    }
    LaunchedEffect(pagerState.currentPage) {
        query = ""
        menuState.onDismissRequest()
    }
    val volumeChildIndices = remember(outline) {
        buildSet {
            var insideVolume = false
            outline.orEmpty().forEach { entry ->
                if (entry.isVolume) insideVolume = true
                else if (insideVolume) add(entry.index)
            }
        }
    }
    val toggleVolume: (BookChapter) -> Unit = { chapter ->
        val rows = outline
        if (rows != null && query.isBlank()) {
            val first = chapterListState.firstVisibleItemIndex
            val offset = chapterListState.firstVisibleItemScrollOffset
            val anchor = visibleOutline?.getOrNull(first)?.index ?: chapter.index
            val nextCollapsed = if (chapter.url in collapsedVolumes) {
                collapsedVolumes - chapter.url
            } else collapsedVolumes + chapter.url
            val nextRows = visibleCatalogRows(rows, nextCollapsed, descending)
            collapsedVolumes = nextCollapsed
            pagerScope.launch {
                withFrameNanos { }
                chapterListState.scrollToItem(catalogVisiblePosition(nextRows, anchor), offset)
            }
        }
    }

    if (visualStyle == CatalogDrawerVisualStyle.READING_COMPACT_SIDE) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
        val sideWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.76f)
            .coerceAtMost(420.dp)
        val compactMenuWidth = if (selectedTab == CatalogTab.Bookmarks) {
            bookmarkMenuWidth.coerceAtMost(sideWidth - 24.dp)
        } else 136.dp
        LaunchedEffect(drawerState.currentValue) {
            if (drawerState.currentValue == DrawerValue.Closed) onDismiss()
        }
        BackHandler { pagerScope.launch { drawerState.close() } }
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.48f),
            drawerContent = {
                NgSideDrawerSurface(
                    modifier = Modifier.fillMaxHeight().width(sideWidth),
                    appearance = NgDrawerDefaults.currentAppearance().copy(transparencyPercent = 0),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        CompactCatalogBookHeader(book, contentColor, mutedColor)
                        if (searchVisible) {
                            CatalogSearchField(
                                query = query,
                                hint = stringResource(
                                    if (selectedTab == CatalogTab.Chapters) R.string.read_catalog_search_chapters
                                    else R.string.read_catalog_search_bookmarks
                                ),
                                contentColor = contentColor,
                                mutedColor = mutedColor,
                                accentColor = accentColor,
                                dockColor = dockColor,
                                onQueryChange = { query = it },
                                onClose = { query = ""; searchVisible = false },
                                compact = true,
                            )
                        } else Row(
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                                .padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    if (selectedTab == CatalogTab.Chapters) R.string.chapter_list
                                    else R.string.bookmark
                                ),
                                color = mutedColor,
                                fontSize = 13.sp,
                            )
                            if (selectedTab == CatalogTab.Chapters) {
                                CompactCatalogSortAction(
                                    descending = descending,
                                    contentColor = contentColor,
                                    onSort = { descending = !descending },
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { searchVisible = !searchVisible }) {
                                Icon(
                                    painterResource(R.drawable.ic_search),
                                    stringResource(R.string.search),
                                    modifier = Modifier.size(20.dp),
                                    tint = contentColor,
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = menuState::onAnchorClick,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_grid_menu),
                                        stringResource(R.string.menu),
                                        modifier = Modifier.size(20.dp),
                                        tint = contentColor,
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuState.expanded,
                                    onDismissRequest = menuState::onDismissRequest,
                                    modifier = Modifier.width(compactMenuWidth),
                                    offset = DpOffset(x = 48.dp - compactMenuWidth, y = 0.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 4.dp,
                                    shadowElevation = 8.dp,
                                ) {
                                    if (selectedTab == CatalogTab.Chapters) {
                                        NgDrawerOptionMenuItem(
                                            label = stringResource(R.string.load_word_count),
                                            selected = showWordCount,
                                            contentColor = contentColor,
                                            accentColor = accentColor,
                                            onClick = {
                                                showWordCount = !showWordCount
                                                AppConfig.tocCountWords = showWordCount
                                            },
                                        )
                                        HorizontalDivider(
                                            Modifier.padding(horizontal = 12.dp),
                                            color = mutedColor.copy(alpha = 0.14f),
                                        )
                                        NgDrawerOptionMenuItem(
                                            label = stringResource(R.string.use_replace),
                                            selected = useReplace,
                                            contentColor = contentColor,
                                            accentColor = accentColor,
                                            onClick = {
                                                useReplace = !useReplace
                                                AppConfig.tocUiUseReplace = useReplace
                                            },
                                        )
                                        if (volumeIds.isNotEmpty()) {
                                            HorizontalDivider(
                                                Modifier.padding(horizontal = 12.dp),
                                                color = mutedColor.copy(alpha = 0.14f),
                                            )
                                            NgDrawerOptionMenuItem(
                                                label = stringResource(
                                                    if (allVolumesCollapsed) R.string.read_catalog_expand_volume
                                                    else R.string.read_catalog_collapse_volume
                                                ),
                                                selected = allVolumesCollapsed,
                                                contentColor = contentColor,
                                                accentColor = accentColor,
                                                onClick = {
                                                    val anchor = visibleOutline
                                                        ?.getOrNull(chapterListState.firstVisibleItemIndex)
                                                        ?.index ?: currentChapterIndex
                                                    val offset = chapterListState.firstVisibleItemScrollOffset
                                                    val nextCollapsed = if (allVolumesCollapsed) emptySet() else volumeIds
                                                    collapsedVolumes = nextCollapsed
                                                    menuState.onDismissRequest()
                                                    pagerScope.launch {
                                                        withFrameNanos { }
                                                        chapterListState.scrollToItem(
                                                            catalogVisiblePosition(
                                                                visibleCatalogRows(outline.orEmpty(), nextCollapsed, descending),
                                                                anchor,
                                                            ),
                                                            offset,
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        NgDrawerOptionMenuItem(
                                            label = stringResource(R.string.bookmark_auto_expand_notes),
                                            selected = autoExpandNotes,
                                            contentColor = contentColor,
                                            accentColor = accentColor,
                                            onClick = {
                                                autoExpandNotes = !autoExpandNotes
                                                AppConfig.bookmarkAutoExpandNotes = autoExpandNotes
                                            },
                                        )
                                    }
                                    HorizontalDivider(
                                        Modifier.padding(horizontal = 12.dp),
                                        color = mutedColor.copy(alpha = 0.14f),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                            .clickable(enabled = !refreshing) {
                                                menuState.onDismissRequest()
                                                onRefresh()
                                            }.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (refreshing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = accentColor,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                painterResource(R.drawable.ic_refresh_black_24dp),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = contentColor,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            stringResource(R.string.read_catalog_refresh),
                                            color = contentColor,
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
                            }
                        }
                        if (searchVisible && query.isNotBlank()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(24.dp)
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = stringResource(
                                        if (selectedTab == CatalogTab.Chapters) {
                                            R.string.read_catalog_search_chapter_count
                                        } else R.string.read_catalog_search_bookmark_count,
                                        if (selectedTab == CatalogTab.Chapters) visibleChapterCount
                                        else filteredBookmarks.size,
                                    ),
                                    color = mutedColor,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) { page ->
                            if (loading) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentColor)
                                }
                            } else if (tabs[page] == CatalogTab.Chapters) {
                                Column(Modifier.fillMaxSize()) {
                                    CatalogChapterList(
                                        visibleOutline = visibleOutline.takeIf { query.isBlank() },
                                        collapsedVolumes = collapsedVolumes,
                                        loadChapterIndices = loadChapterIndices,
                                        onVolumeClick = toggleVolume,
                                        dataVersion = dataVersion,
                                        useReplace = useReplace,
                                        showWordCount = showWordCount,
                                        showUncachedWordCount = true,
                                        chapterCount = chapterCount,
                                        query = query,
                                        descending = descending,
                                        cachedChapterFiles = cachedChapterFiles,
                                        showCacheState = showCacheState,
                                        isLocalBook = isLocalBook,
                                        currentChapterIndex = currentChapterIndex,
                                        listState = chapterListState,
                                        listBackgroundColor = Color.Transparent,
                                        contentColor = contentColor,
                                        mutedColor = mutedColor,
                                        accentColor = accentColor,
                                        loadChapterCount = loadChapterCount,
                                        loadChapterPosition = loadChapterPosition,
                                        loadChapterPage = loadChapterPage,
                                        onChapterCountChanged = { visibleChapterCount = it },
                                        onChapterClick = onChapterClick,
                                        compact = true,
                                        volumeChildIndices = volumeChildIndices,
                                    )
                                }
                            } else {
                                CatalogBookmarkList(
                                    bookmarks = filteredBookmarks,
                                    autoExpandNotes = autoExpandNotes,
                                    currentChapterIndex = currentChapterIndex,
                                    listState = bookmarkListState,
                                    listBackgroundColor = Color.Transparent,
                                    contentColor = contentColor,
                                    mutedColor = mutedColor,
                                    accentColor = accentColor,
                                    onBookmarkClick = onBookmarkClick,
                                    onBookmarkDelete = onBookmarkDelete,
                                    compact = true,
                                )
                            }
                        }
                        CompactCatalogTabs(
                            tabs = tabs,
                            selectedTab = selectedTab,
                            contentColor = contentColor,
                            mutedColor = mutedColor,
                            onSelected = { tab ->
                                pagerScope.launch {
                                    pagerState.animateScrollToPage(tabs.indexOf(tab))
                                }
                            },
                        )
                    }
                }
            },
        ) { Box(Modifier.fillMaxSize()) }
        return
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(
                    top = if (visualStyle == CatalogDrawerVisualStyle.READING_ORIGINAL) {
                        8.dp
                    } else {
                        6.dp
                    }
                ),
        ) {
            if (visualStyle == CatalogDrawerVisualStyle.READING_ORIGINAL) {
                CatalogDragHandle(mutedColor = mutedColor)
                if (searchVisible) {
                    CatalogSearchField(
                        query = query,
                        hint = stringResource(
                            if (selectedTab == CatalogTab.Chapters) R.string.read_catalog_search_chapters
                            else R.string.read_catalog_search_bookmarks
                        ),
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        dockColor = dockColor,
                        onQueryChange = { query = it },
                        onClose = {
                            query = ""
                            searchVisible = false
                        },
                    )
                } else {
                    CatalogTopActions(
                        contentColor = contentColor,
                        dockColor = dockColor,
                        onSearch = { searchVisible = true },
                        menuExpanded = menuState.expanded,
                        menuWidth = if (selectedTab == CatalogTab.Bookmarks) bookmarkMenuWidth else 136.dp,
                        onMenuClick = menuState::onAnchorClick,
                        onMenuDismiss = menuState::onDismissRequest,
                    ) {
                        if (selectedTab == CatalogTab.Bookmarks) {
                            NgDrawerOptionMenuItem(
                                label = stringResource(R.string.bookmark_auto_expand_notes),
                                selected = autoExpandNotes,
                                contentColor = contentColor,
                                accentColor = accentColor,
                                onClick = {
                                    autoExpandNotes = !autoExpandNotes
                                    AppConfig.bookmarkAutoExpandNotes = autoExpandNotes
                                },
                            )
                        } else {
                            NgDrawerOptionMenuItem(
                                label = stringResource(R.string.load_word_count),
                                selected = showWordCount,
                                contentColor = contentColor,
                                accentColor = accentColor,
                                onClick = {
                                    showWordCount = !showWordCount
                                    AppConfig.tocCountWords = showWordCount
                                },
                            )
                            HorizontalDivider(
                                Modifier.padding(horizontal = 12.dp), color = mutedColor.copy(alpha = 0.14f),
                            )
                            NgDrawerOptionMenuItem(
                                label = stringResource(R.string.use_replace),
                                selected = useReplace,
                                contentColor = contentColor,
                                accentColor = accentColor,
                                onClick = {
                                    useReplace = !useReplace
                                    AppConfig.tocUiUseReplace = useReplace
                                },
                            )
                            HorizontalDivider(
                                Modifier.padding(horizontal = 12.dp), color = mutedColor.copy(alpha = 0.14f),
                            )
                            if (volumeIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                        .clickable {
                                            val rows = outline.orEmpty()
                                            val anchor = visibleOutline?.getOrNull(chapterListState.firstVisibleItemIndex)?.index
                                                ?: currentChapterIndex
                                            val offset = chapterListState.firstVisibleItemScrollOffset
                                            val nextCollapsed = if (allVolumesCollapsed) emptySet() else volumeIds
                                            val nextRows = visibleCatalogRows(rows, nextCollapsed, descending)
                                            collapsedVolumes = nextCollapsed
                                            menuState.onDismissRequest()
                                            pagerScope.launch {
                                                withFrameNanos { }
                                                chapterListState.scrollToItem(catalogVisiblePosition(nextRows, anchor), offset)
                                            }
                                        }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(if (allVolumesCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp), tint = contentColor,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(
                                            if (allVolumesCollapsed) R.string.read_catalog_expand_volume
                                            else R.string.read_catalog_collapse_volume
                                        ),
                                        color = contentColor, fontSize = 15.sp,
                                    )
                                }
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 12.dp), color = mutedColor.copy(alpha = 0.14f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                                    .clickable(enabled = !refreshing) {
                                        menuState.onDismissRequest()
                                        onRefresh()
                                    }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (refreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp), color = accentColor, strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.ic_refresh_black_24dp), contentDescription = null,
                                        modifier = Modifier.size(18.dp), tint = contentColor,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.read_catalog_refresh), color = contentColor, fontSize = 15.sp)
                            }
                        }
                    }
                }
            } else {
                NgLongDrawerHeader(
                    title = stringResource(R.string.chapter_list),
                    actionIconRes = if (searchVisible) null else R.drawable.ic_search,
                    actionContentDescription = if (searchVisible) {
                        null
                    } else {
                        stringResource(R.string.search)
                    },
                    onActionClick = if (searchVisible) null else ({ searchVisible = true }),
                    secondaryActionIconRes = if (!searchVisible && showCacheAction) {
                        if (cacheRunning) R.drawable.ic_stop_black_24dp
                        else R.drawable.ic_download_line
                    } else {
                        null
                    },
                    secondaryActionContentDescription = if (cacheRunning) {
                        stringResource(R.string.cancel)
                    } else {
                        stringResource(R.string.book_cache)
                    },
                    secondaryActionActive = cacheRunning,
                    onSecondaryActionClick = if (!searchVisible && showCacheAction) {
                        onCacheClick
                    } else {
                        null
                    },
                    centerTitle = true,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (searchVisible) {
                    CatalogSearchField(
                        query = query,
                        hint = stringResource(R.string.read_catalog_search_chapters),
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        dockColor = dockColor,
                        onQueryChange = { query = it },
                        onClose = {
                            query = ""
                            searchVisible = false
                        },
                    )
                }
            }
            if (tabs.size > 1) {
                Spacer(Modifier.height(4.dp))
                CatalogTabDock(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    selectedContentColor = selectedContentColor,
                    dockColor = dockColor,
                    onTabSelected = {
                        query = ""
                        pagerScope.launch {
                            pagerState.animateScrollToPage(tabs.indexOf(it).coerceAtLeast(0))
                        }
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageTab = tabs[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(listBackgroundColor),
                ) {
                    CatalogSummaryRow(
                        selectedTab = pageTab,
                        currentChapterIndex = currentChapterIndex,
                        chapterCount = chapterCount,
                        itemCount = if (pageTab == CatalogTab.Chapters) {
                            visibleChapterCount
                        } else {
                            filteredBookmarks.size
                        },
                        descending = descending,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        onSort = { descending = !descending },
                    )
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = accentColor,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else if (pageTab == CatalogTab.Chapters) {
                        CatalogChapterList(
                            visibleOutline = visibleOutline.takeIf { query.isBlank() },
                            collapsedVolumes = collapsedVolumes,
                            loadChapterIndices = loadChapterIndices,
                            onVolumeClick = toggleVolume,
                            dataVersion = dataVersion,
                            useReplace = useReplace,
                            showWordCount = showWordCount,
                            showUncachedWordCount = visualStyle == CatalogDrawerVisualStyle.READING_ORIGINAL,
                            chapterCount = chapterCount,
                            query = query,
                            descending = descending,
                            cachedChapterFiles = cachedChapterFiles,
                            showCacheState = showCacheState,
                            isLocalBook = isLocalBook,
                            currentChapterIndex = currentChapterIndex,
                            listState = chapterListState,
                            listBackgroundColor = listBackgroundColor,
                            contentColor = contentColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            loadChapterCount = loadChapterCount,
                            loadChapterPosition = loadChapterPosition,
                            loadChapterPage = loadChapterPage,
                            onChapterCountChanged = { visibleChapterCount = it },
                            onChapterClick = onChapterClick,
                        )
                    } else {
                        CatalogBookmarkList(
                            bookmarks = filteredBookmarks,
                            autoExpandNotes = autoExpandNotes,
                            currentChapterIndex = currentChapterIndex,
                            listState = bookmarkListState,
                            listBackgroundColor = listBackgroundColor,
                            contentColor = contentColor,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            onBookmarkClick = onBookmarkClick,
                            onBookmarkDelete = onBookmarkDelete,
                        )
                    }
                }
            }
        }
    }
    if (visualStyle == CatalogDrawerVisualStyle.READING_ORIGINAL) {
        NgGlassSurface(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollInteropConnection),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            style = readFloatingGlassStyle(),
        ) {
            content()
        }
    } else {
        NgBottomDrawerSurface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun CatalogDragHandle(
    mutedColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun CompactCatalogBookHeader(book: Book, contentColor: Color, mutedColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp)
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgBookCover(
            book = book,
            modifier = Modifier.size(width = 46.dp, height = 64.dp),
            coverRadius = 5,
            contentDescription = book.name,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(book.name, color = contentColor, fontSize = 16.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(book.author, color = mutedColor, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = mutedColor.copy(alpha = 0.18f))
}

@Composable
private fun CompactCatalogSortAction(
    descending: Boolean,
    contentColor: Color,
    onSort: () -> Unit,
) {
    Row(
        modifier = Modifier.height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSort)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (descending) R.drawable.ic_catalog_sort_descending
                else R.drawable.ic_catalog_sort_ascending),
            contentDescription = stringResource(R.string.swap_sort),
            modifier = Modifier.size(16.dp), tint = contentColor,
        )
        Spacer(Modifier.width(5.dp))
        Text(stringResource(if (descending) R.string.read_catalog_descending
            else R.string.read_catalog_ascending), color = contentColor, fontSize = 12.sp)
    }
}

@Composable
private fun CompactCatalogTabs(
    tabs: List<CatalogTab>,
    selectedTab: CatalogTab,
    contentColor: Color,
    mutedColor: Color,
    onSelected: (CatalogTab) -> Unit,
) {
    HorizontalDivider(thickness = 0.5.dp, color = mutedColor.copy(alpha = 0.18f))
    Row(Modifier.fillMaxWidth().height(52.dp)) {
        tabs.forEach { tab ->
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable { onSelected(tab) }
                    .semantics { selected = tab == selectedTab },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (tab == CatalogTab.Chapters) R.string.chapter_list else R.string.bookmark),
                    color = if (tab == selectedTab) contentColor else mutedColor,
                    fontSize = 15.sp,
                    fontWeight = if (tab == selectedTab) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CatalogTopActions(
    contentColor: Color,
    dockColor: Color,
    onSearch: () -> Unit,
    menuExpanded: Boolean,
    menuWidth: Dp,
    onMenuClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(dockColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_search), stringResource(R.string.search),
                    Modifier.size(20.dp), tint = contentColor,
                )
            }
        }
        Box {
            Box(
                modifier = Modifier.size(40.dp).clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(dockColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_grid_menu), stringResource(R.string.menu),
                        Modifier.size(20.dp), tint = contentColor,
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
                modifier = Modifier.width(menuWidth),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                menuContent()
            }
        }
    }
}

@Composable
private fun CatalogTabDock(
    tabs: List<CatalogTab>,
    selectedTab: CatalogTab,
    contentColor: Color,
    accentColor: Color,
    selectedContentColor: Color,
    dockColor: Color,
    onTabSelected: (CatalogTab) -> Unit,
) {
    val selectedShape = RoundedCornerShape(10.dp)
    val selectedShadowColor = Color.Black.copy(
        alpha = if (NgTheme.snapshot.isDark) 0.32f else 0.16f
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(dockColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(3.dp)
                    .then(
                        if (selected && !NgTheme.snapshot.isEInk) {
                            Modifier.shadow(
                                elevation = 4.dp,
                                shape = selectedShape,
                                clip = false,
                                ambientColor = selectedShadowColor,
                                spotColor = selectedShadowColor,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(selectedShape)
                    .background(
                        if (selected) accentColor.copy(alpha = 0.86f) else Color.Transparent
                    )
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (tab == CatalogTab.Chapters) R.string.chapter_list else R.string.bookmark
                    ),
                    color = if (selected) selectedContentColor else contentColor,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CatalogSearchField(
    query: String,
    hint: String,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    dockColor: Color,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    compact: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val fieldShape = RoundedCornerShape(if (compact) 14.dp else 13.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 12.dp else 20.dp)
            .height(if (compact) 42.dp else 40.dp)
            .then(if (compact) Modifier else Modifier.padding(bottom = 4.dp))
            .clip(fieldShape)
            .background(if (compact) Color(NgTheme.colors.inputContainer) else dockColor)
            .then(if (compact) {
                Modifier.border(0.5.dp, mutedColor.copy(alpha = 0.14f), fieldShape)
            } else Modifier)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = accentColor,
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = TextStyle(fontFamily = NgTheme.fontFamily, color = contentColor, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = hint, color = mutedColor.copy(alpha = 0.72f), fontSize = 14.sp)
                }
                inner()
            },
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable {
                    keyboard?.hide()
                    onClose()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(18.dp),
                tint = mutedColor,
            )
        }
    }
}

@Composable
private fun CatalogSummaryRow(
    selectedTab: CatalogTab,
    currentChapterIndex: Int,
    chapterCount: Int,
    itemCount: Int,
    descending: Boolean,
    contentColor: Color,
    mutedColor: Color,
    onSort: () -> Unit,
) {
    val currentChapterNumber = if (chapterCount > 0) {
        (currentChapterIndex + 1).coerceIn(1, chapterCount)
    } else {
        0
    }
    val readingProgress = if (chapterCount > 0) {
        currentChapterNumber.toDouble() / chapterCount * 100
    } else {
        0.0
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedTab == CatalogTab.Chapters) {
                stringResource(
                    R.string.read_catalog_reading_progress,
                    currentChapterNumber,
                    chapterCount,
                    readingProgress,
                )
            } else {
                stringResource(R.string.read_catalog_bookmark_count, itemCount)
            },
            color = mutedColor,
            fontSize = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        if (selectedTab == CatalogTab.Chapters) {
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSort),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (descending) R.drawable.ic_catalog_sort_descending
                        else R.drawable.ic_catalog_sort_ascending
                    ),
                    contentDescription = stringResource(R.string.swap_sort),
                    modifier = Modifier.size(17.dp),
                    tint = contentColor,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (descending) R.string.read_catalog_descending
                        else R.string.read_catalog_ascending
                    ),
                    color = contentColor,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun CatalogChapterList(
    visibleOutline: List<CatalogOutlineEntry>?,
    collapsedVolumes: Set<String>,
    loadChapterIndices: suspend (List<Int>) -> List<CatalogChapter>,
    onVolumeClick: (BookChapter) -> Unit,
    dataVersion: Int,
    useReplace: Boolean,
    showWordCount: Boolean,
    showUncachedWordCount: Boolean,
    chapterCount: Int,
    query: String,
    descending: Boolean,
    cachedChapterFiles: Set<String>,
    showCacheState: Boolean,
    isLocalBook: Boolean,
    currentChapterIndex: Int,
    listState: LazyListState,
    listBackgroundColor: Color,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    loadChapterCount: suspend (String) -> Int,
    loadChapterPosition: suspend (Boolean, Int) -> Int,
    loadChapterPage: suspend (String, Boolean, Int, Int) -> List<CatalogChapter>,
    onChapterCountChanged: (Int) -> Unit,
    onChapterClick: (BookChapter) -> Unit,
    compact: Boolean = false,
    volumeChildIndices: Set<Int> = emptySet(),
) {
    var itemCount by remember(query, descending) { mutableStateOf(0) }
    var datasetLoading by remember(query, descending) { mutableStateOf(true) }
    var initialPositionApplied by remember(query, descending) { mutableStateOf(false) }
    val loadedPages = remember(query, descending, dataVersion, useReplace, visibleOutline) {
        mutableStateMapOf<Int, List<CatalogChapter>>()
    }
    LaunchedEffect(query, descending, chapterCount, dataVersion, visibleOutline) {
        datasetLoading = true
        if (query.isNotBlank()) {
            delay(220)
        }
        val totalCount = visibleOutline?.size ?: if (query.isBlank()) chapterCount else loadChapterCount(query)
        itemCount = totalCount
        onChapterCountChanged(totalCount)
        if (totalCount > 0 && !initialPositionApplied) {
            withFrameNanos { }
            val currentPosition = if (visibleOutline != null) {
                catalogVisiblePosition(visibleOutline, currentChapterIndex)
            } else if (query.isBlank()) {
                loadChapterPosition(descending, totalCount)
            } else {
                0
            }
            listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
            initialPositionApplied = true
        }
        datasetLoading = false
    }
    LaunchedEffect(query, descending, itemCount, dataVersion, useReplace, visibleOutline) {
        if (itemCount <= 0) return@LaunchedEffect
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val first = visibleItems.firstOrNull()?.index ?: listState.firstVisibleItemIndex
            val last = visibleItems.lastOrNull()?.index ?: first
            first to last
        }.distinctUntilChanged().collectLatest { (firstVisible, lastVisible) ->
            val preloadStart = (firstVisible - CATALOG_PRELOAD_ITEMS).coerceAtLeast(0)
            val preloadEnd = (lastVisible + CATALOG_PRELOAD_ITEMS)
                .coerceAtMost(itemCount - 1)
            val firstPage = preloadStart / CATALOG_PAGE_SIZE
            val lastPage = preloadEnd / CATALOG_PAGE_SIZE
            for (pageIndex in firstPage..lastPage) {
                if (loadedPages[pageIndex] == null) {
                    loadedPages[pageIndex] = if (visibleOutline != null) {
                        loadChapterIndices(
                            catalogPageIndices(visibleOutline, pageIndex * CATALOG_PAGE_SIZE, CATALOG_PAGE_SIZE)
                        )
                    } else loadChapterPage(
                        query,
                        descending,
                        pageIndex * CATALOG_PAGE_SIZE,
                        CATALOG_PAGE_SIZE,
                    )
                }
            }
            val centerPage = ((firstVisible + lastVisible) / 2) / CATALOG_PAGE_SIZE
            loadedPages.keys.toList()
                .filter { kotlin.math.abs(it - centerPage) > CATALOG_RETAINED_PAGE_RADIUS }
                .forEach(loadedPages::remove)
        }
    }
    if (itemCount == 0) {
        if (datasetLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = accentColor,
                    strokeWidth = 2.dp,
                )
            }
            return
        }
        CatalogEmptyState(stringResource(R.string.chapter_list_empty), mutedColor)
        return
    }
    CatalogScrollableList(
        itemCount = itemCount,
        listState = listState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(listBackgroundColor),
            contentPadding = if (compact) PaddingValues(bottom = 12.dp) else PaddingValues(
                start = 12.dp, top = 6.dp, end = 12.dp, bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 6.dp),
        ) {
            items(count = itemCount, key = { visibleOutline?.getOrNull(it)?.url ?: it }) { position ->
                val pageIndex = position / CATALOG_PAGE_SIZE
                val pageOffset = position % CATALOG_PAGE_SIZE
                val item = loadedPages[pageIndex]?.getOrNull(pageOffset)
                if (item == null) {
                    if (compact) CompactCatalogPlaceholder(mutedColor)
                    else CatalogChapterPlaceholder(mutedColor)
                } else {
                    val cached = isLocalBook || item.chapter.isVolume ||
                        cachedChapterFiles.contains(item.chapter.getFileName())
                    val volumeExpanded = if (showUncachedWordCount && item.chapter.isVolume && query.isBlank()) {
                        item.chapter.url !in collapsedVolumes
                    } else null
                    val click = {
                        if (showUncachedWordCount && item.chapter.isVolume) onVolumeClick(item.chapter)
                        else onChapterClick(item.chapter)
                    }
                    if (compact) CompactCatalogChapterRow(
                        chapter = item.chapter,
                        displayTitle = item.displayTitle,
                        current = item.chapter.index == currentChapterIndex,
                        isVolumeChild = item.chapter.index in volumeChildIndices,
                        cached = cached,
                        showCacheState = showCacheState,
                        showWordCount = showWordCount,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        volumeExpanded = volumeExpanded,
                        onClick = click,
                    ) else NgCatalogChapterRow(
                        chapter = item.chapter,
                        displayTitle = item.displayTitle,
                        current = item.chapter.index == currentChapterIndex,
                        cached = cached,
                        showCacheState = showCacheState,
                        showWordCount = showWordCount,
                        showUncachedWordCount = showUncachedWordCount,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        volumeExpanded = volumeExpanded,
                        onClick = click,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun NgCatalogChapterRow(
    chapter: BookChapter,
    displayTitle: String,
    current: Boolean,
    cached: Boolean,
    showCacheState: Boolean,
    showWordCount: Boolean,
    contentColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showUncachedWordCount: Boolean = false,
    volumeExpanded: Boolean? = null,
) {
    val cardColor = catalogCardColor()
    val cardShape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    val currentChapterColor = Color(NgTheme.colors.secondary)
    val wordCount = if (showCacheState && (cached || showUncachedWordCount) && showWordCount && !chapter.isVolume) {
        chapter.wordCount?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val chapterTag = chapter.tag
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatCatalogChapterTag)
    val currentChapterIndicatorColor = Color(NgTheme.colors.primary)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(cardShape)
            .background(cardColor)
            .drawBehind {
                if (current) {
                    drawRect(
                        color = currentChapterIndicatorColor,
                        size = Size(width = 6.dp.toPx(), height = size.height),
                    )
                }
            }
            .semantics { selected = current }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = if (chapterTag == null) Arrangement.Center else Arrangement.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (chapter.isVip) {
                Icon(
                    imageVector = if (chapter.isPay) {
                        Icons.Rounded.LockOpen
                    } else {
                        Icons.Rounded.Lock
                    },
                    contentDescription = stringResource(
                        if (chapter.isPay) {
                            R.string.read_catalog_vip_purchased
                        } else {
                            R.string.read_catalog_vip_unpaid
                        }
                    ),
                    modifier = Modifier.size(16.dp),
                    tint = mutedColor.copy(alpha = 0.72f),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = displayTitle,
                modifier = Modifier.weight(1f),
                color = if (current) currentChapterColor else contentColor,
                fontSize = if (chapter.isVolume) 16.sp else 15.sp,
                fontWeight = if (chapter.isVolume) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (volumeExpanded != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(if (volumeExpanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right_20),
                    stringResource(if (volumeExpanded) R.string.read_catalog_collapse_volume else R.string.read_catalog_expand_volume),
                    modifier = Modifier.size(20.dp), tint = mutedColor,
                )
            } else if (showCacheState && !cached && wordCount == null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_outline_cloud_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = mutedColor.copy(alpha = 0.72f),
                )
            } else if (showCacheState && wordCount != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = wordCount,
                    color = if (current) currentChapterColor else mutedColor.copy(alpha = 0.82f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        if (chapterTag != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = chapterTag,
                color = mutedColor.copy(alpha = 0.78f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactCatalogChapterRow(
    chapter: BookChapter,
    displayTitle: String,
    current: Boolean,
    isVolumeChild: Boolean,
    cached: Boolean,
    showCacheState: Boolean,
    showWordCount: Boolean,
    contentColor: Color,
    mutedColor: Color,
    volumeExpanded: Boolean?,
    onClick: () -> Unit,
) {
    val activeColor = Color(NgTheme.colors.secondary)
    val wordCount = if (showWordCount && !chapter.isVolume) {
        chapter.wordCount?.takeIf(String::isNotBlank)
    } else null
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp)
            .background(if (current) activeColor.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { selected = current }
            .padding(start = if (isVolumeChild) 32.dp else 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (volumeExpanded != null) {
            Icon(
                painterResource(if (volumeExpanded) R.drawable.ic_expand_more else R.drawable.ic_chevron_right_20),
                contentDescription = stringResource(if (volumeExpanded)
                    R.string.read_catalog_collapse_volume else R.string.read_catalog_expand_volume),
                modifier = Modifier.size(16.dp), tint = mutedColor,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (chapter.isVip) {
            Icon(
                imageVector = if (chapter.isPay) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                contentDescription = stringResource(if (chapter.isPay)
                    R.string.read_catalog_vip_purchased else R.string.read_catalog_vip_unpaid),
                modifier = Modifier.size(14.dp), tint = mutedColor,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            displayTitle, modifier = Modifier.weight(1f),
            color = if (current) activeColor else contentColor,
            fontSize = 14.sp,
            fontWeight = if (chapter.isVolume || current) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (wordCount != null) {
            Spacer(Modifier.width(8.dp))
            Text(wordCount, color = if (current) activeColor else mutedColor,
                fontSize = 11.sp, maxLines = 1)
        } else if (showCacheState && !cached && !chapter.isVolume) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_outline_cloud_24), null,
                modifier = Modifier.size(14.dp), tint = mutedColor)
        }
    }
}

@Composable
private fun CompactCatalogPlaceholder(mutedColor: Color) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth(0.62f).height(12.dp).clip(CircleShape)
            .background(mutedColor.copy(alpha = 0.08f)))
    }
}

@Composable
private fun CatalogChapterPlaceholder(mutedColor: Color) {
    val cardColor = catalogCardColor()
    val cardShape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(cardShape)
            .background(cardColor)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.58f)
                .height(14.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(10.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.05f)),
        )
    }
}

private fun formatCatalogChapterTag(tag: String): String {
    val updateTime = catalogUpdateTimeRegex.find(tag)?.groupValues?.getOrNull(1)?.trim()
    val sourceWordCount = catalogSourceWordCountRegex.find(tag)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (updateTime == null && sourceWordCount == null) return tag
    return listOfNotNull(
        updateTime,
        sourceWordCount?.let { "字数：$it" },
    ).joinToString("  ")
}

@Composable
private fun CatalogBookmarkList(
    bookmarks: List<Bookmark>,
    autoExpandNotes: Boolean,
    currentChapterIndex: Int,
    listState: LazyListState,
    listBackgroundColor: Color,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
    compact: Boolean = false,
) {
    if (bookmarks.isEmpty()) {
        CatalogEmptyState(stringResource(R.string.read_catalog_no_bookmarks), mutedColor)
        return
    }
    val currentPosition = remember(bookmarks, currentChapterIndex) {
        bookmarks.indexOfLast { it.chapterIndex <= currentChapterIndex }.coerceAtLeast(0)
    }
    var pendingDeleteTime by remember { mutableStateOf<Long?>(null) }
    val expandedNoteTimes = remember(autoExpandNotes) { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(bookmarks.isNotEmpty()) {
        listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
    }
    CatalogScrollableList(
        itemCount = bookmarks.size,
        listState = listState,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(listBackgroundColor),
            contentPadding = if (compact) PaddingValues(bottom = 12.dp) else PaddingValues(
                start = 12.dp, top = 6.dp, end = 12.dp, bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 6.dp),
        ) {
            items(bookmarks, key = { it.time }) { bookmark ->
                val deleteConfirmationVisible = pendingDeleteTime == bookmark.time
                val noteExpanded = expandedNoteTimes[bookmark.time] ?: autoExpandNotes
                val onNoteToggle = {
                    expandedNoteTimes[bookmark.time] = !noteExpanded
                }
                val onDeleteConfirm = {
                    pendingDeleteTime = null
                    onBookmarkDelete(bookmark)
                }
                if (compact) {
                    CompactCatalogBookmarkRow(
                        bookmark = bookmark,
                        deleteConfirmationVisible = deleteConfirmationVisible,
                        noteExpanded = noteExpanded,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        onClick = { onBookmarkClick(bookmark) },
                        onLongClick = { pendingDeleteTime = bookmark.time },
                        onNoteToggle = onNoteToggle,
                        onDeleteCancel = { pendingDeleteTime = null },
                        onDeleteConfirm = onDeleteConfirm,
                    )
                } else NgCatalogBookmarkCard(
                    bookmark = bookmark,
                    deleteConfirmationVisible = deleteConfirmationVisible,
                    noteExpanded = noteExpanded,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onClick = { onBookmarkClick(bookmark) },
                    onLongClick = { pendingDeleteTime = bookmark.time },
                    onNoteToggle = onNoteToggle,
                    onDeleteCancel = { pendingDeleteTime = null },
                    onDeleteConfirm = onDeleteConfirm,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CompactCatalogBookmarkRow(
    bookmark: Bookmark,
    deleteConfirmationVisible: Boolean,
    noteExpanded: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNoteToggle: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick,
                    onLongClickLabel = stringResource(R.string.delete), onLongClick = onLongClick)
                .padding(start = 20.dp, top = 11.dp, end = 18.dp, bottom = 10.dp),
        ) {
            Text(bookmark.chapterName, color = contentColor, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (bookmark.bookText.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(bookmark.bookText.replace('\n', ' '), color = mutedColor,
                    fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (deleteConfirmationVisible) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.read_catalog_delete_bookmark_confirmation),
                    modifier = Modifier.weight(1f), color = mutedColor, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                CatalogBookmarkInlineAction(stringResource(R.string.cancel), mutedColor, onDeleteCancel)
                CatalogBookmarkInlineAction(stringResource(R.string.delete),
                    Color(NgTheme.colors.error), onDeleteConfirm)
            }
        } else if (bookmark.content.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp)
                    .clickable(onClick = onNoteToggle)
                    .padding(start = 20.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.bookmark_note),
                    modifier = Modifier.weight(1f), color = accentColor, fontSize = 11.sp)
                Icon(
                    imageVector = if (noteExpanded) Icons.Rounded.KeyboardArrowUp
                        else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(if (noteExpanded)
                        R.string.read_catalog_collapse_note else R.string.read_catalog_expand_note),
                    modifier = Modifier.size(16.dp), tint = mutedColor,
                )
            }
            if (noteExpanded) {
                Text(bookmark.content.trim(),
                    modifier = Modifier.padding(start = 20.dp, end = 18.dp, bottom = 8.dp),
                    color = contentColor, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 20.dp, end = 18.dp),
            thickness = 0.5.dp, color = mutedColor.copy(alpha = 0.14f),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun NgCatalogBookmarkCard(
    bookmark: Bookmark,
    deleteConfirmationVisible: Boolean,
    noteExpanded: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onNoteToggle: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    val cardColor = catalogCardColor()
    val cardShape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    val errorColor = Color(NgTheme.colors.error)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardColor)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.delete),
                onLongClick = onLongClick,
            )
            .padding(start = 12.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
    ) {
        Text(
            text = bookmark.chapterName,
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (bookmark.bookText.isNotBlank()) {
            Text(
                text = bookmark.bookText.replace('\n', ' '),
                modifier = Modifier.padding(end = 4.dp),
                color = mutedColor.copy(alpha = 0.84f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (bookmark.content.isNotBlank() || deleteConfirmationVisible) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp, end = 4.dp),
                thickness = 0.5.dp,
                color = mutedColor.copy(alpha = 0.16f),
            )
            if (deleteConfirmationVisible) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.read_catalog_delete_bookmark_confirmation),
                        modifier = Modifier.weight(1f),
                        color = mutedColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CatalogBookmarkInlineAction(
                        text = stringResource(R.string.cancel),
                        color = mutedColor,
                        onClick = onDeleteCancel,
                    )
                    CatalogBookmarkInlineAction(
                        text = stringResource(R.string.delete),
                        color = errorColor,
                        onClick = onDeleteConfirm,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable(onClick = onNoteToggle)
                        .padding(start = 2.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_chat_suggestion),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accentColor,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.bookmark_note),
                        color = Color(NgTheme.colors.secondary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (noteExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (noteExpanded) {
                                R.string.read_catalog_collapse_note
                            } else {
                                R.string.read_catalog_expand_note
                            }
                        ),
                        modifier = Modifier.size(15.dp),
                        tint = mutedColor,
                    )
                }
                if (noteExpanded) {
                    Text(
                        text = bookmark.content.trim(),
                        modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 6.dp),
                        color = contentColor.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogBookmarkInlineAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun catalogListBackgroundColor(mutedColor: Color): Color = when {
    NgTheme.snapshot.isEInk -> Color(NgTheme.colors.inputContainer)
    NgTheme.snapshot.isDark -> Color(NgTheme.colors.surface)
    else -> mutedColor.copy(alpha = 0.035f)
}

@Composable
private fun catalogCardColor(): Color = if (NgTheme.snapshot.isDark) {
    Color(NgTheme.colors.cardContainer).copy(alpha = 1f)
} else {
    Color.White
}

@Composable
private fun CatalogScrollableList(
    itemCount: Int,
    listState: LazyListState,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        NgLazyListFastScroller(
            state = listState,
            itemCount = itemCount,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
        )
    }
}

@Composable
private fun CatalogEmptyState(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = color.copy(alpha = 0.72f), fontSize = 15.sp)
    }
}
