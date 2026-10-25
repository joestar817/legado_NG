package io.legado.app.ui.book.toc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.CatalogOutlineEntry
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val TOC_TAB_CHAPTERS = 0
internal const val TOC_TAB_BOOKMARKS = 1

internal data class TocChapterUiItem(
    val chapter: BookChapter,
    val displayTitle: String = chapter.title,
)

internal data class TocUiState(
    val book: Book? = null,
    val selectedTab: Int = TOC_TAB_CHAPTERS,
    val searchExpanded: Boolean = false,
    val query: String = "",
    val chapters: List<TocChapterUiItem> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val cachedFileNames: Set<String> = emptySet(),
    val chapterScrollIndex: Int = 0,
    val chapterScrollToken: Int = 0,
    val bookmarkScrollIndex: Int = 0,
    val bookmarkScrollToken: Int = 0,
    val useReplace: Boolean = false,
    val loadWordCount: Boolean = false,
    val splitLongChapter: Boolean = false,
    val isLocalTxt: Boolean = false,
)

internal enum class TocMenuAction(val itemId: Int) {
    TocRegex(0x7501),
    SplitLongChapter(0x7502),
    ReverseToc(0x7503),
    UseReplace(0x7504),
    LoadWordCount(0x7505),
    ExportBookmark(0x7506),
    ExportMarkdown(0x7507),
    Log(0x7508),
    NetworkLog(0x7509),
    ;

    companion object {
        fun fromItemId(itemId: Int): TocMenuAction? = entries.firstOrNull {
            it.itemId == itemId
        }
    }
}

internal sealed interface TocUiEvent {
    data object Back : TocUiEvent
    data class TabChange(val tab: Int) : TocUiEvent
    data class SearchExpandedChange(val expanded: Boolean) : TocUiEvent
    data class QueryChange(val query: String) : TocUiEvent
    data class Menu(val action: TocMenuAction) : TocUiEvent
    data class ChapterClick(val chapter: BookChapter) : TocUiEvent
    data class ChapterLongClick(val title: String) : TocUiEvent
    data class BookmarkClick(val bookmark: Bookmark) : TocUiEvent
    data class BookmarkLongClick(val bookmark: Bookmark, val position: Int) : TocUiEvent
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TocScreen(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab,
        pageCount = { 2 },
    )
    val chapterListState = rememberLazyListState()
    val bookmarkListState = rememberLazyListState()
    val activeListState = if (pagerState.currentPage == TOC_TAB_BOOKMARKS) bookmarkListState
        else chapterListState
    val activeItemCount by remember(activeListState) {
        derivedStateOf { activeListState.layoutInfo.totalItemsCount }
    }
    var pagerHeightPx by remember { mutableIntStateOf(0) }
    val pagerHeight = with(LocalDensity.current) { pagerHeightPx.toDp() }
    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.selectedTab) {
                    onEvent(TocUiEvent.TabChange(page))
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        TocTopBar(state = state, onEvent = onEvent)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            val compact = maxHeight < 400.dp
            NgVisualSurface(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                role = NgMaterialRole.CONTENT,
                cornerRadius = NgTheme.shapes.mediumDp.dp,
                style = NgGlassDefaults.neutralStyle(),
            ) {
                state.book?.let { book -> TocBookIdentity(book = book, compact = compact) }
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    TocTab(
                        title = stringResource(R.string.chapter_list),
                        selected = state.selectedTab == TOC_TAB_CHAPTERS,
                        modifier = Modifier.weight(1f),
                        onClick = { onEvent(TocUiEvent.TabChange(TOC_TAB_CHAPTERS)) },
                    )
                    TocTab(
                        title = stringResource(R.string.bookmark),
                        selected = state.selectedTab == TOC_TAB_BOOKMARKS,
                        modifier = Modifier.weight(1f),
                        onClick = { onEvent(TocUiEvent.TabChange(TOC_TAB_BOOKMARKS)) },
                    )
                }
                TocListSummary(state = state, onEvent = onEvent)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).onSizeChanged { pagerHeightPx = it.height },
                ) { page ->
                    when (page) {
                        TOC_TAB_BOOKMARKS -> TocBookmarkPage(
                            state = state,
                            listState = bookmarkListState,
                            onEvent = onEvent,
                        )
                        else -> TocChapterPage(
                            state = state,
                            listState = chapterListState,
                            onEvent = onEvent,
                        )
                    }
                }
            }
            if (!pagerState.isScrollInProgress && pagerHeightPx > 0) {
                key(activeListState) {
                    NgLazyListFastScroller(
                        state = activeListState,
                        itemCount = activeItemCount,
                        variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .height(pagerHeight).padding(end = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TocTopBar(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    if (state.searchExpanded) {
        NgFloatingSearchToolbar(
            query = state.query,
            onQueryChange = { onEvent(TocUiEvent.QueryChange(it)) },
            hint = stringResource(R.string.search),
            onBack = { onEvent(TocUiEvent.Back) },
            modifier = Modifier.padding(horizontal = 10.dp),
            trailingContent = {
                NgFloatingToolbarActionButton(
                    iconRes = R.drawable.ic_baseline_close,
                    contentDescription = stringResource(R.string.close),
                    onClick = { onEvent(TocUiEvent.SearchExpandedChange(false)) },
                )
            },
        )
    } else {
        NgFloatingTitleToolbar(
            title = stringResource(R.string.chapter_list),
            onBack = { onEvent(TocUiEvent.Back) },
            modifier = Modifier.padding(horizontal = 10.dp),
            trailingContent = {
                NgFloatingToolbarActionButton(
                    iconRes = R.drawable.ic_search,
                    contentDescription = stringResource(R.string.search),
                    onClick = { onEvent(TocUiEvent.SearchExpandedChange(true)) },
                )
                TocMoreMenu(state = state, onEvent = onEvent)
            },
        )
    }
}

@Composable
private fun TocBookIdentity(book: Book, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = 14.dp,
            vertical = 6.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NgBookCover(
            book = book,
            modifier = Modifier.size(
                width = if (compact) 30.dp else 40.dp,
                height = if (compact) 40.dp else 52.dp,
            ),
            coverRadius = 4,
            contentDescription = book.name,
        )
        if (compact) {
            Text(
                text = if (book.author.isBlank()) book.name else "${book.name} · ${book.author}",
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = book.name,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    text = book.author,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TocTab(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp)
                    .background(Color(NgTheme.colors.primary)),
            )
        }
    }
}

@Composable
private fun TocListSummary(state: TocUiState, onEvent: (TocUiEvent) -> Unit) {
    val bookmarks = state.selectedTab == TOC_TAB_BOOKMARKS
    val count = if (bookmarks) state.bookmarks.size else state.chapters.size
    val countRes = when {
        bookmarks && state.query.isNotBlank() -> R.string.read_catalog_search_bookmark_count
        bookmarks -> R.string.read_catalog_bookmark_count
        state.query.isNotBlank() -> R.string.read_catalog_search_chapter_count
        else -> R.string.all_chapter_num
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(countRes, count),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        if (!bookmarks) {
            val reverse = state.book?.getReverseToc() == true
            Row(
                modifier = Modifier.heightIn(min = 40.dp)
                    .clickable(role = Role.Button) {
                        onEvent(TocUiEvent.Menu(TocMenuAction.ReverseToc))
                    }.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(
                        if (reverse) R.drawable.ic_catalog_sort_descending
                        else R.drawable.ic_catalog_sort_ascending,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                )
                Text(
                    text = stringResource(
                        if (reverse) R.string.read_catalog_descending else R.string.read_catalog_ascending,
                    ),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
    HorizontalDivider(color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.35f))
}

@Composable
private fun TocMoreMenu(
    state: TocUiState,
    onEvent: (TocUiEvent) -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    Box {
        NgFloatingToolbarActionButton(
            iconRes = R.drawable.ic_grid_menu,
            contentDescription = stringResource(R.string.menu),
            onClick = menuState::onAnchorClick,
        )
        NgExpandableActionMenu(
            expanded = menuState.expanded,
            onDismissRequest = menuState::onDismissRequest,
            items = tocMenuItems(state),
            rowMinHeight = 44.dp,
            variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
            menuContainerColor = colorResource(R.color.ng_surface_card),
            properties = PopupProperties(focusable = true, clippingEnabled = false),
            onItemClick = { item ->
                menuState.close()
                TocMenuAction.fromItemId(item.itemId)?.let {
                    onEvent(TocUiEvent.Menu(it))
                }
            },
        )
    }
}

@Composable
private fun tocMenuItems(state: TocUiState): List<NgExpandableActionMenuItem> = buildList {
    if (state.selectedTab == TOC_TAB_BOOKMARKS) {
        add(TocMenuAction.ExportBookmark.item(R.string.export, R.drawable.ic_export))
        add(TocMenuAction.ExportMarkdown.item(R.string.export_md, R.drawable.ic_code))
    } else {
        if (state.isLocalTxt) {
            add(TocMenuAction.TocRegex.item(R.string.txt_toc_rule, R.drawable.ic_code))
            add(
                TocMenuAction.SplitLongChapter.item(
                    R.string.split_long_chapter,
                    R.drawable.ic_chapter_list,
                    checked = state.splitLongChapter,
                ),
            )
        }
        add(
            TocMenuAction.ReverseToc.item(
                R.string.reverse_toc,
                R.drawable.ic_catalog_sort_descending,
                dividerBefore = isNotEmpty(),
            ),
        )
        add(
            TocMenuAction.UseReplace.item(
                R.string.use_replace,
                R.drawable.ic_find_replace,
                checked = state.useReplace,
            ),
        )
        add(
            TocMenuAction.LoadWordCount.item(
                R.string.load_word_count,
                R.drawable.ic_cfg_about,
                checked = state.loadWordCount,
            ),
        )
    }
    add(TocMenuAction.Log.item(R.string.log, R.drawable.ic_history, dividerBefore = true))
    add(
        TocMenuAction.NetworkLog.item(
            R.string.network_request_log,
            R.drawable.ic_network_check,
        ),
    )
}

private fun TocMenuAction.item(
    titleRes: Int,
    iconRes: Int,
    checked: Boolean = false,
    dividerBefore: Boolean = false,
): NgExpandableActionMenuItem = NgExpandableActionMenuItem(
    itemId = itemId,
    titleRes = titleRes,
    iconRes = iconRes,
    checked = checked,
    dividerBefore = dividerBefore,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocChapterPage(
    state: TocUiState,
    listState: LazyListState,
    onEvent: (TocUiEvent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bookKey = state.book?.bookUrl.orEmpty()
    var collapsedKeys by rememberSaveable(bookKey) { mutableStateOf(arrayListOf<String>()) }
    val collapsedVolumes = remember(collapsedKeys) { collapsedKeys.toSet() }
    // The reverse flag can change before the corresponding chapter batch arrives.
    val reversed = remember(bookKey, state.chapterScrollToken) {
        state.book?.getReverseToc() == true
    }
    val searching = state.query.isNotBlank()
    val searchResults = remember(bookKey, state.chapterScrollToken) {
        state.query.isNotBlank()
    }
    val foldEnabled = !searching && !searchResults
    val outline = remember(state.chapters) {
        state.chapters.map {
            CatalogOutlineEntry(it.chapter.primaryStr(), it.chapter.index, it.chapter.isVolume)
        }
    }
    val projection = remember(outline, collapsedVolumes, reversed, searchResults) {
        projectTocCatalogRows(outline, collapsedVolumes, reversed, searchResults)
    }
    val itemsByKey = remember(state.chapters) {
        state.chapters.associateBy { it.chapter.primaryStr() }
    }
    val visibleItems = remember(projection.visibleRows, itemsByKey) {
        projection.visibleRows.mapNotNull { itemsByKey[it.url] }
    }
    var foldScrollJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(bookKey, state.chapterScrollToken) {
        foldScrollJob?.cancel()
        if (state.chapters.isNotEmpty()) {
            val target = state.chapters[state.chapterScrollIndex.coerceIn(state.chapters.indices)]
            val currentKey = state.chapters.firstOrNull {
                it.chapter.index == state.book?.durChapterIndex
            }?.chapter?.primaryStr()
            val currentVolume = currentKey?.let(projection.parentVolumeByKey::get)
            val nextCollapsed = if (currentVolume != null) collapsedVolumes - currentVolume
                else collapsedVolumes
            val targetProjection = if (nextCollapsed != collapsedVolumes) {
                collapsedKeys = ArrayList(nextCollapsed)
                projectTocCatalogRows(outline, nextCollapsed, reversed, searchResults)
            } else projection
            if (nextCollapsed != collapsedVolumes) withFrameNanos { }
            listState.scrollToItem(tocCatalogAnchorPosition(targetProjection, target.chapter.primaryStr()))
        }
    }
    fun toggleVolume(item: TocChapterUiItem) {
        if (!foldEnabled || !item.chapter.isVolume) return
        val anchorKey = projection.visibleRows.getOrNull(listState.firstVisibleItemIndex)?.url
        val offset = listState.firstVisibleItemScrollOffset
        val volumeKey = item.chapter.primaryStr()
        val nextCollapsed = if (volumeKey in collapsedVolumes) collapsedVolumes - volumeKey
            else collapsedVolumes + volumeKey
        val nextProjection = projectTocCatalogRows(outline, nextCollapsed, reversed, false)
        foldScrollJob?.cancel()
        collapsedKeys = ArrayList(nextCollapsed)
        foldScrollJob = scope.launch {
            withFrameNanos { }
            val anchorVisible = nextProjection.visibleRows.any { it.url == anchorKey }
            listState.scrollToItem(
                tocCatalogAnchorPosition(nextProjection, anchorKey),
                if (anchorVisible) offset else 0,
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(
                items = visibleItems,
                key = { it.chapter.primaryStr() },
                contentType = { if (it.chapter.isVolume) "volume" else "chapter" },
            ) { item ->
                TocChapterRow(
                    item = item,
                    current = item.chapter.index == state.book?.durChapterIndex,
                    cached = state.book?.isLocal == true ||
                        item.chapter.isVolume ||
                        item.chapter.getFileName() in state.cachedFileNames,
                    showWordCount = state.loadWordCount,
                    collapsed = item.chapter.primaryStr() in collapsedVolumes,
                    onToggleVolume = if (item.chapter.isVolume && foldEnabled) {
                        { toggleVolume(item) }
                    } else null,
                    onClick = {
                        if (item.chapter.isVolume && foldEnabled && state.book?.isVideo != true) {
                            toggleVolume(item)
                        } else onEvent(TocUiEvent.ChapterClick(item.chapter))
                    },
                    onLongClick = { onEvent(TocUiEvent.ChapterLongClick(item.displayTitle)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocChapterRow(
    item: TocChapterUiItem,
    current: Boolean,
    cached: Boolean,
    showWordCount: Boolean,
    collapsed: Boolean,
    onToggleVolume: (() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val chapter = item.chapter
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val leading = if (chapter.isVolume) 44.dp else 8.dp
    Box(
        modifier = Modifier.fillMaxWidth()
            .background(
                if (current) Color(NgTheme.colors.primary).copy(
                    alpha = if (NgTheme.snapshot.isDark) 0.20f else 0.12f,
                )
                else Color.Transparent,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 20.dp).heightIn(min = 44.dp)
                .semantics { selected = current }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = leading, end = 0.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chapter.isVip) {
                Icon(
                    imageVector = if (chapter.isPay) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    contentDescription = stringResource(
                        if (chapter.isPay) R.string.read_catalog_vip_purchased
                        else R.string.read_catalog_vip_unpaid,
                    ),
                    modifier = Modifier.size(14.dp),
                    tint = secondary.copy(alpha = secondary.alpha * 0.72f),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    color = Color(if (current) NgTheme.colors.primary else NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (current || chapter.isVolume) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                chapter.tag?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 2.dp),
                        color = secondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            chapter.wordCount?.takeIf {
                showWordCount && !chapter.isVolume && it.isNotBlank()
            }?.let {
                Text(
                    text = it,
                    modifier = Modifier.widthIn(max = 68.dp),
                    color = if (current) Color(NgTheme.colors.primary) else secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!cached && !chapter.isVolume) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_cloud_24),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 2.dp).size(14.dp),
                    tint = secondary,
                )
            }
        }
        if (onToggleVolume != null) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).size(40.dp)
                    .clickable(role = Role.Button, onClick = onToggleVolume),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painter = painterResource(
                        if (collapsed) R.drawable.ic_arrow_right else R.drawable.ic_arrow_down,
                    ),
                    contentDescription = stringResource(
                        if (collapsed) R.string.read_catalog_expand_volume
                        else R.string.read_catalog_collapse_volume,
                    ),
                    modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    tint = if (current) Color(NgTheme.colors.primary) else secondary,
                )
            }
        }
        if (current) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .padding(start = if (chapter.isVolume) 28.dp else 0.dp)
                    .size(if (chapter.isVolume) 10.dp else 6.dp).clip(CircleShape)
                    .background(Color(NgTheme.colors.primary))
                    .border(if (chapter.isVolume) 1.5.dp else 1.dp, Color.White, CircleShape),
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = leading, end = 22.dp),
        thickness = 0.5.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.30f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocBookmarkPage(
    state: TocUiState,
    listState: LazyListState,
    onEvent: (TocUiEvent) -> Unit,
) {
    LaunchedEffect(state.bookmarkScrollToken) {
        if (state.bookmarks.isNotEmpty()) {
            listState.scrollToItem(state.bookmarkScrollIndex.coerceIn(state.bookmarks.indices))
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = state.bookmarks,
                key = { _, item -> item.time },
                contentType = { _, _ -> "bookmark" },
            ) { position, bookmark ->
                TocBookmarkRow(
                    bookmark = bookmark,
                    onClick = { onEvent(TocUiEvent.BookmarkClick(bookmark)) },
                    onLongClick = { onEvent(TocUiEvent.BookmarkLongClick(bookmark, position)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TocBookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(NgTheme.shapes.mediumDp.dp))
            .background(colorResource(R.color.ng_surface_card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bookmark.chapterName,
            color = colorResource(R.color.primaryText),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        bookmark.bookText.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                color = colorResource(R.color.secondaryText),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        bookmark.content.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                color = Color(NgTheme.colors.primary),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
