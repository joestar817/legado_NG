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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import io.legado.app.ui.book.bookmark.AllBookmarkFilter
import io.legado.app.ui.book.bookmark.BookmarkNoteFilter
import io.legado.app.ui.book.bookmark.buildAllBookmarkCollection
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgSideDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDefaults
import io.legado.app.ui.design.components.compose.rememberNgDrawerThemeProfile
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgDrawerPalette
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
            height = (resources.displayMetrics.heightPixels * 0.80f).toInt()
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
        ReadBook.openBookmark(bookmark)
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

@OptIn(ExperimentalLayoutApi::class)
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
    var showWordCount by remember { mutableStateOf(AppConfig.tocCountWords) }
    var useReplace by remember { mutableStateOf(AppConfig.tocUiUseReplace) }
    var autoExpandNotes by remember { mutableStateOf(AppConfig.bookmarkAutoExpandNotes) }
    var toolsExpanded by rememberSaveable(book.bookUrl) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var bookmarkNoteFilter by rememberSaveable(book.bookUrl) { mutableStateOf(BookmarkNoteFilter.ALL) }
    var bookmarkColorFilter by rememberSaveable(book.bookUrl) { mutableStateOf<Int?>(null) }
    val bookmarkFilter = remember(bookmarkNoteFilter, bookmarkColorFilter) {
        AllBookmarkFilter(noteFilter = bookmarkNoteFilter, color = bookmarkColorFilter)
    }
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
    val drawerThemeProfile = when (visualStyle) {
        CatalogDrawerVisualStyle.READING_ORIGINAL -> null
        CatalogDrawerVisualStyle.READING_COMPACT_SIDE -> rememberReadDrawerThemeProfile()
        CatalogDrawerVisualStyle.LISTENING -> rememberNgDrawerThemeProfile()
    }
    val baseSnapshot = NgTheme.snapshot
    val drawerAppearance = NgDrawerDefaults.rememberAppearance()
    val primaryStrength = drawerAppearance.primaryStrengthPercent
    val drawerColors = remember(baseSnapshot, drawerThemeProfile, primaryStrength) {
        val backgroundColor = drawerThemeProfile
            ?.takeIf { !baseSnapshot.isEInk && it.source == "custom_color" }
            ?.forNight(baseSnapshot.isDark)?.backgroundColor
        if (backgroundColor == null) baseSnapshot.colors else NgDrawerPalette.applyAdaptiveContentCardRoles(
            baseSnapshot, primaryStrength, backgroundColor,
        ).colors
    }
    val contentColor = Color(drawerColors.onSurface)
    val mutedColor = Color(drawerColors.onSurfaceVariant)
    val accentColor = Color(drawerColors.primary)
    val selectedContentColor = Color(drawerColors.onPrimary)
    val dockColor = if (NgTheme.snapshot.isDark || NgTheme.snapshot.isEInk) {
        Color(drawerColors.surfaceContainerLow)
    } else {
        contentColor.copy(alpha = 0.025f)
    }
    val listBackgroundColor = if (visualStyle != CatalogDrawerVisualStyle.LISTENING) {
        Color.Transparent
    } else {
        catalogListBackgroundColor(mutedColor)
    }
    val bookmarkCollection = remember(bookmarks, query, bookmarkFilter) {
        buildAllBookmarkCollection(bookmarks, query, bookmarkFilter, searchBookMetadata = false)
    }
    val filteredBookmarks = remember(bookmarkCollection) {
        bookmarkCollection.groups.flatMap { group -> group.matches.map { it.value } }
    }
    val chapterListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    val tabs = remember(showBookmarks) {
        if (showBookmarks) CatalogTab.entries else listOf(CatalogTab.Chapters)
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val pagerScope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage.coerceIn(tabs.indices)]
    val clearBookmarkConditions = {
        bookmarkNoteFilter = BookmarkNoteFilter.ALL
        bookmarkColorFilter = null
        query = ""
    }
    val collapseTools: () -> Unit = {
        toolsExpanded = false
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val onToolsAction: () -> Unit = {
        if (toolsExpanded) collapseTools() else toolsExpanded = true
    }
    val toolsActive = toolsExpanded || query.isNotBlank() ||
        (selectedTab == CatalogTab.Bookmarks && bookmarkFilter.isActive)
    val bookmarkToolsPanel: @Composable () -> Unit = {
        CatalogBookmarkFilterPanel(
            query = query,
            onQueryChange = { query = it },
            filter = bookmarkFilter,
            colors = bookmarkCollection.colors,
            onFilterChanged = {
                bookmarkNoteFilter = it.noteFilter
                bookmarkColorFilter = it.color
            },
            onClearConditions = clearBookmarkConditions,
            autoExpandNotes = autoExpandNotes,
            onAutoExpandNotesChanged = { autoExpandNotes = it; AppConfig.bookmarkAutoExpandNotes = it },
            extraActions = {
                if (visualStyle == CatalogDrawerVisualStyle.READING_COMPACT_SIDE) {
                    CatalogToolAction(stringResource(R.string.read_catalog_refresh),
                        R.drawable.ic_refresh_black_24dp, refreshing, onClick = onRefresh)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
    LaunchedEffect(query, chapterCount) {
        visibleChapterCount = if (query.isBlank()) chapterCount else 0
    }
    LaunchedEffect(pagerState.currentPage) {
        query = ""
        collapseTools()
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

    val toggleAllVolumes: () -> Unit = {
        val anchor = visibleOutline?.getOrNull(chapterListState.firstVisibleItemIndex)?.index
            ?: currentChapterIndex
        val offset = chapterListState.firstVisibleItemScrollOffset
        val nextCollapsed = if (allVolumesCollapsed) emptySet() else volumeIds
        val nextRows = visibleCatalogRows(outline.orEmpty(), nextCollapsed, descending)
        collapsedVolumes = nextCollapsed
        pagerScope.launch {
            withFrameNanos { }
            chapterListState.scrollToItem(catalogVisiblePosition(nextRows, anchor), offset)
        }
    }
    val chapterToolsPanel: @Composable () -> Unit = {
        CatalogToolsPanel(query, stringResource(R.string.read_catalog_search_chapters), { query = it },
            modifier = Modifier.padding(horizontal = 16.dp)) {
            if (visualStyle != CatalogDrawerVisualStyle.LISTENING) {
                FlowRow(Modifier.fillMaxWidth().padding(top = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 3) {
                    CatalogToolSwitch(stringResource(R.string.load_word_count), showWordCount,
                        modifier = Modifier.weight(1f).widthIn(min = 96.dp)) {
                        showWordCount = it; AppConfig.tocCountWords = it
                    }
                    CatalogToolSwitch(stringResource(R.string.use_replace), useReplace,
                        modifier = Modifier.weight(1f).widthIn(min = 96.dp)) {
                        useReplace = it; AppConfig.tocUiUseReplace = it
                    }
                    CatalogToolAction(stringResource(R.string.read_catalog_refresh),
                        R.drawable.ic_refresh_black_24dp, refreshing,
                        modifier = Modifier.weight(1f), onClick = onRefresh)
                    if (volumeIds.isNotEmpty()) {
                        CatalogToolAction(stringResource(if (allVolumesCollapsed) R.string.read_catalog_expand_volume
                            else R.string.read_catalog_collapse_volume),
                            if (allVolumesCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less,
                            onClick = toggleAllVolumes)
                    }
                }
            }
        }
    }

    if (visualStyle == CatalogDrawerVisualStyle.READING_COMPACT_SIDE) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
        val sideWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.75f)
            .coerceAtMost(420.dp)
        LaunchedEffect(drawerState.currentValue) {
            if (drawerState.currentValue == DrawerValue.Closed) onDismiss()
        }
        BackHandler {
            if (toolsExpanded) collapseTools()
            else pagerScope.launch { drawerState.close() }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.48f),
            drawerContent = {
                NgSideDrawerSurface(
                    modifier = Modifier.fillMaxHeight().width(sideWidth),
                    appearance = drawerAppearance.copy(transparencyPercent = 0),
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
                    themeProfile = drawerThemeProfile,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        CompactCatalogBookHeader(book, contentColor, mutedColor)
                        Row(Modifier.fillMaxWidth().height(42.dp).padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (selectedTab == CatalogTab.Chapters) R.string.chapter_list else R.string.bookmark),
                                color = mutedColor, fontSize = 13.sp)
                            if (selectedTab == CatalogTab.Chapters) {
                                CompactCatalogSortAction(descending, contentColor) { descending = !descending }
                            }
                            Spacer(Modifier.weight(1f))
                            CatalogToolsButton(toolsActive, contentColor, accentColor, onToolsAction)
                        }
                        if (toolsExpanded) {
                            if (selectedTab == CatalogTab.Bookmarks) bookmarkToolsPanel() else chapterToolsPanel()
                        }
                        if (selectedTab == CatalogTab.Bookmarks || query.isNotBlank()) {
                            Text(stringResource(if (selectedTab == CatalogTab.Bookmarks) R.string.read_catalog_bookmark_count
                                else R.string.read_catalog_search_chapter_count,
                                if (selectedTab == CatalogTab.Bookmarks) filteredBookmarks.size else visibleChapterCount),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                                color = mutedColor, fontSize = 11.sp)
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
                                    filter = bookmarkFilter,
                                    query = query,
                                    onClearConditions = clearBookmarkConditions,
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

    BackHandler(enabled = toolsExpanded) { collapseTools() }
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
                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    CatalogToolsButton(toolsActive, contentColor, accentColor, onToolsAction)
                }
            } else {
                NgLongDrawerHeader(
                    title = stringResource(R.string.chapter_list),
                    actionIconRes = R.drawable.ic_tts_params_grid,
                    actionContentDescription = stringResource(R.string.menu),
                    actionActive = toolsActive,
                    onActionClick = onToolsAction,
                    secondaryActionIconRes = if (showCacheAction) {
                        if (cacheRunning) R.drawable.ic_stop_black_24dp else R.drawable.ic_download_line
                    } else null,
                    secondaryActionContentDescription = stringResource(if (cacheRunning) R.string.cancel else R.string.book_cache),
                    secondaryActionActive = cacheRunning,
                    onSecondaryActionClick = if (showCacheAction) onCacheClick else null,
                    centerTitle = true,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
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
                    if (toolsExpanded) {
                        if (pageTab == CatalogTab.Bookmarks) bookmarkToolsPanel() else chapterToolsPanel()
                    }
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
                            filter = bookmarkFilter,
                            query = query,
                            onClearConditions = clearBookmarkConditions,
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
        NgBottomDrawerSurface(modifier = Modifier.fillMaxSize(), themeProfile = drawerThemeProfile) {
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
private fun CatalogToolsButton(active: Boolean, contentColor: Color, accentColor: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(painterResource(R.drawable.ic_tts_params_grid), stringResource(R.string.menu),
            Modifier.size(20.dp), tint = if (active) accentColor else contentColor)
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
    volumeExpanded: Boolean? = null,
) {
    val cardColor = catalogCardColor()
    val cardShape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    val currentChapterColor = Color(NgTheme.colors.secondary)
    val metadata = catalogChapterMetadata(chapter.tag, chapter.wordCount, cached)
    val wordCount = if (showWordCount && !chapter.isVolume) {
        metadata.wordCount
    } else {
        null
    }
    val chapterTag = if (chapter.isVolume) chapter.tag?.takeIf(String::isNotBlank) else metadata.description
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
            } else {
                if (wordCount != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = wordCount,
                        color = if (current) currentChapterColor else mutedColor.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                if (showCacheState && !cached && !chapter.isVolume) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_outline_cloud_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = mutedColor.copy(alpha = 0.72f),
                    )
                }
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
        catalogChapterMetadata(chapter.tag, chapter.wordCount, cached).wordCount
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
        }
        if (showCacheState && !cached && !chapter.isVolume) {
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
    filter: AllBookmarkFilter = AllBookmarkFilter(),
    query: String = "",
    onClearConditions: (() -> Unit)? = null,
) {
    val hasConditions = filter.isActive || query.isNotBlank()
    if (bookmarks.isEmpty()) {
        if (hasConditions) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.all_bookmark_filter_empty), color = mutedColor, fontSize = 15.sp)
                if (onClearConditions != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.all_bookmark_filter_clear_conditions),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearConditions).padding(12.dp),
                        color = accentColor, fontSize = 14.sp)
                }
            }
        } else CatalogEmptyState(stringResource(R.string.read_catalog_no_bookmarks), mutedColor)
        return
    }
    val currentPosition = remember(bookmarks, currentChapterIndex) {
        bookmarks.indexOfLast { it.chapterIndex <= currentChapterIndex }.coerceAtLeast(0)
    }
    var pendingDeleteTime by remember { mutableStateOf<Long?>(null) }
    val expandedNoteTimes = remember(autoExpandNotes) { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(filter, query, bookmarks.isNotEmpty()) {
        listState.scrollToItem(if (hasConditions) 0 else (currentPosition - 1).coerceAtLeast(0))
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
