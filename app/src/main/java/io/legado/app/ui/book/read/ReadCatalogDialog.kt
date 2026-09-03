package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.observeEvent
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
    private var catalogThemeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    protected abstract fun catalogBook(): Book?

    protected abstract fun currentChapterIndex(): Int

    protected abstract fun onChapterSelected(chapter: BookChapter)

    protected open fun showBookmarks(): Boolean = false

    protected open fun showCacheState(): Boolean = false

    protected open fun showCacheAction(): Boolean = false

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
                    loading = loading,
                    loadChapterCount = { query ->
                        loadChapterCount(book.bookUrl, query)
                    },
                    loadChapterPosition = { descending, totalCount ->
                        loadChapterPosition(
                            bookUrl = book.bookUrl,
                            chapterIndex = currentChapterIndex(),
                            descending = descending,
                            totalCount = totalCount,
                        )
                    },
                    loadChapterPage = { query, descending, offset, limit ->
                        loadChapterPage(
                            bookUrl = book.bookUrl,
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
                    )
                }
            }.onSuccess { (totalCount, bookmarkItems, cacheFiles) ->
                chapterCount = totalCount
                bookmarks = bookmarkItems
                cachedChapterFiles = cacheFiles
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
        chapters.map { CatalogChapter(it, it.getDisplayTitle()) }
    }
}

internal class ReadCatalogDialog : CatalogDrawerDialog() {

    private var bottomDialogRegistered = false

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

private data class CatalogChapter(
    val chapter: BookChapter,
    val displayTitle: String,
)

private enum class CatalogTab { Chapters, Bookmarks }

internal enum class CatalogDrawerVisualStyle {
    READING_ORIGINAL,
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
    loadChapterCount: suspend (String) -> Int,
    loadChapterPosition: suspend (Boolean, Int) -> Int,
    loadChapterPage: suspend (String, Boolean, Int, Int) -> List<CatalogChapter>,
    onChapterClick: (BookChapter) -> Unit,
    onCacheClick: () -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
) {
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
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
    val listBackgroundColor = if (
        visualStyle == CatalogDrawerVisualStyle.READING_ORIGINAL
    ) {
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
    val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
    LaunchedEffect(query, chapterCount) {
        visibleChapterCount = if (query.isBlank()) chapterCount else 0
    }
    LaunchedEffect(pagerState.currentPage) {
        query = ""
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
                        hint = if (selectedTab == CatalogTab.Chapters) {
                            stringResource(R.string.read_catalog_search_chapters)
                        } else {
                            stringResource(R.string.read_catalog_search_bookmarks)
                        },
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
                    )
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
private fun CatalogTopActions(
    contentColor: Color,
    dockColor: Color,
    onSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(dockColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
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
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(40.dp)
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(dockColor)
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
            textStyle = TextStyle(color = contentColor, fontSize = 14.sp),
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
) {
    var itemCount by remember(query, descending) { mutableStateOf(0) }
    var datasetLoading by remember(query, descending) { mutableStateOf(true) }
    val loadedPages = remember(query, descending) {
        mutableStateMapOf<Int, List<CatalogChapter>>()
    }
    LaunchedEffect(query, descending, chapterCount) {
        datasetLoading = true
        if (query.isNotBlank()) {
            delay(220)
        }
        val totalCount = if (query.isBlank()) chapterCount else loadChapterCount(query)
        itemCount = totalCount
        onChapterCountChanged(totalCount)
        if (totalCount > 0) {
            withFrameNanos { }
            val currentPosition = if (query.isBlank()) {
                loadChapterPosition(descending, totalCount)
            } else {
                0
            }
            listState.scrollToItem((currentPosition - 1).coerceAtLeast(0))
        }
        datasetLoading = false
    }
    LaunchedEffect(query, descending, itemCount) {
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
                    loadedPages[pageIndex] = loadChapterPage(
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
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 6.dp,
                end = 12.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(count = itemCount, key = { it }) { position ->
                val pageIndex = position / CATALOG_PAGE_SIZE
                val pageOffset = position % CATALOG_PAGE_SIZE
                val item = loadedPages[pageIndex]?.getOrNull(pageOffset)
                if (item == null) {
                    CatalogChapterPlaceholder(mutedColor)
                } else {
                    NgCatalogChapterRow(
                        chapter = item.chapter,
                        displayTitle = item.displayTitle,
                        current = item.chapter.index == currentChapterIndex,
                        cached = isLocalBook || item.chapter.isVolume ||
                            cachedChapterFiles.contains(item.chapter.getFileName()),
                        showCacheState = showCacheState,
                        showWordCount = AppConfig.tocCountWords,
                        contentColor = contentColor,
                        mutedColor = mutedColor,
                        onClick = { onChapterClick(item.chapter) },
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
) {
    val cardColor = catalogCardColor()
    val cardShape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    val currentChapterColor = Color(NgTheme.colors.secondary)
    val wordCount = if (showCacheState && cached && showWordCount) {
        chapter.wordCount?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val chapterTag = chapter.tag
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatCatalogChapterTag)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(cardShape)
            .background(cardColor)
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
            if (showCacheState && !cached) {
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
    currentChapterIndex: Int,
    listState: LazyListState,
    listBackgroundColor: Color,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkDelete: (Bookmark) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        CatalogEmptyState(stringResource(R.string.read_catalog_no_bookmarks), mutedColor)
        return
    }
    val currentPosition = remember(bookmarks, currentChapterIndex) {
        bookmarks.indexOfLast { it.chapterIndex <= currentChapterIndex }.coerceAtLeast(0)
    }
    var pendingDeleteTime by remember { mutableStateOf<Long?>(null) }
    val expandedNoteTimes = remember { mutableStateMapOf<Long, Boolean>() }
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
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 6.dp,
                end = 12.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(bookmarks, key = { it.time }) { bookmark ->
                val deleteConfirmationVisible = pendingDeleteTime == bookmark.time
                NgCatalogBookmarkCard(
                    bookmark = bookmark,
                    deleteConfirmationVisible = deleteConfirmationVisible,
                    noteExpanded = expandedNoteTimes[bookmark.time] == true,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onClick = { onBookmarkClick(bookmark) },
                    onLongClick = { pendingDeleteTime = bookmark.time },
                    onNoteToggle = {
                        expandedNoteTimes[bookmark.time] =
                            expandedNoteTimes[bookmark.time] != true
                    },
                    onDeleteCancel = { pendingDeleteTime = null },
                    onDeleteConfirm = {
                        pendingDeleteTime = null
                        onBookmarkDelete(bookmark)
                    },
                )
            }
        }
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
