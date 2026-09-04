package io.legado.app.ui.book.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.ui.book.source.BookSourceGroupIcon
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarActionButton
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val SOURCE_SCOPE_ITEM_ID_BASE = 0x01010000
private const val SELECTED_SOURCE_ITEM_ID = 0x0100ffff

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SearchScreen(
    state: SearchViewModel.SearchUiState,
    query: String,
    history: List<SearchKeyword>,
    bookshelfMatches: List<Book>,
    groups: List<String>,
    scopeSources: List<BookSourcePart>,
    scopeNames: List<String>,
    isSourceScope: Boolean,
    precisionSearch: Boolean,
    blockSourceDialogs: Boolean,
    focusRequestToken: Long,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onBack: () -> Unit,
    onHistoryClick: suspend (String) -> Boolean,
    onDeleteHistory: (SearchKeyword) -> Unit,
    onBookshelfBookClick: (Book) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
    isInBookshelf: (SearchBook) -> Boolean,
    onClearHistory: () -> Unit,
    onTogglePrecisionSearch: () -> Unit,
    onToggleBlockSourceDialogs: () -> Unit,
    onSourceManage: () -> Unit,
    onScopeSourceQueryChange: (String) -> Unit,
    onApplySearchScope: (SearchScope) -> Unit,
    onShowLog: () -> Unit,
    onShowNetworkLog: () -> Unit,
    onAllSources: () -> Unit,
    onDynamicScope: (String, Boolean) -> Unit,
    onStopSearch: () -> Unit,
    onLoadMore: () -> Unit
) {
    BackHandler(onBack = onBack)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var showInputHelp by rememberSaveable { mutableStateOf(state.activeQuery.isEmpty()) }
    var showScopeDialog by rememberSaveable { mutableStateOf(false) }

    fun submit(value: String) {
        if (value.isBlank()) return
        showInputHelp = false
        focusManager.clearFocus()
        keyboardController?.hide()
        onSubmitSearch(value)
    }

    LaunchedEffect(focusRequestToken) {
        if (focusRequestToken > 0L) {
            delay(80)
            showInputHelp = true
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        SearchTopBar(
            query = query,
            onQueryChange = {
                showInputHelp = true
                onQueryChange(it)
            },
            onSubmit = ::submit,
            onBack = onBack,
            focusRequester = focusRequester,
            onFocusChanged = { focused ->
                if (focused && !state.isSearching) {
                    showInputHelp = true
                } else if (!focused && state.books.isNotEmpty() && query.isNotBlank()) {
                    showInputHelp = false
                }
            },
            historyVisible = history.isNotEmpty(),
            groups = groups,
            scopeNames = scopeNames,
            isSourceScope = isSourceScope,
            precisionSearch = precisionSearch,
            blockSourceDialogs = blockSourceDialogs,
            onClearHistory = onClearHistory,
            onTogglePrecisionSearch = {
                showInputHelp = false
                onTogglePrecisionSearch()
            },
            onToggleBlockSourceDialogs = {
                showInputHelp = false
                onToggleBlockSourceDialogs()
            },
            onSourceManage = onSourceManage,
            onSearchScope = { showScopeDialog = true },
            onShowLog = onShowLog,
            onShowNetworkLog = onShowNetworkLog,
            onAllSources = onAllSources,
            onDynamicScope = onDynamicScope
        )

        SearchProgress(state.progress)

        Box(Modifier.weight(1f)) {
            if (showInputHelp) {
                SearchInputHelp(
                    history = history,
                    bookshelfMatches = bookshelfMatches,
                    onHistoryClick = { keyword ->
                        coroutineScope.launch {
                            if (onHistoryClick(keyword.word)) {
                                showInputHelp = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    },
                    onDeleteHistory = onDeleteHistory,
                    onBookshelfBookClick = onBookshelfBookClick
                )
            } else {
                SearchResultList(
                    books = state.books,
                    isSearching = state.isSearching,
                    hasMore = state.hasMore,
                    manualStopped = state.manualStopped,
                    resultRevision = state.resultRevision,
                    bookshelfRevision = state.bookshelfRevision,
                    onBookClick = onBookClick,
                    onBookLongClick = onBookLongClick,
                    isInBookshelf = isInBookshelf,
                    onLoadMore = onLoadMore
                )
            }

            val queryMatchesSearch = query.trim() == state.activeQuery
            val showSearchAction = queryMatchesSearch && state.activeQuery.isNotEmpty() &&
                (state.isSearching || (!state.manualStopped && state.hasMore))
            if (!showInputHelp && showSearchAction) {
                FloatingActionButton(
                    onClick = if (state.isSearching) onStopSearch else onLoadMore,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    containerColor = Color(NgTheme.colors.primary).copy(alpha = 1f),
                    contentColor = Color.White
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.isSearching) {
                                R.drawable.ic_stop_black_24dp
                            } else {
                                R.drawable.ic_play_24dp
                            }
                        ),
                        contentDescription = stringResource(
                            if (state.isSearching) R.string.stop else R.string.start
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showScopeDialog) {
        SearchScopeDialog(
            groups = groups,
            sources = scopeSources,
            onSourceQueryChange = onScopeSourceQueryChange,
            onApply = onApplySearchScope,
            onDismiss = { showScopeDialog = false }
        )
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    historyVisible: Boolean,
    groups: List<String>,
    scopeNames: List<String>,
    isSourceScope: Boolean,
    precisionSearch: Boolean,
    blockSourceDialogs: Boolean,
    onClearHistory: () -> Unit,
    onTogglePrecisionSearch: () -> Unit,
    onToggleBlockSourceDialogs: () -> Unit,
    onSourceManage: () -> Unit,
    onSearchScope: () -> Unit,
    onShowLog: () -> Unit,
    onShowNetworkLog: () -> Unit,
    onAllSources: () -> Unit,
    onDynamicScope: (String, Boolean) -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NgFloatingToolbarBackButton(onClick = onBack)
        SearchQueryField(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box {
            NgSearchBarActionButton(
                onClick = menuState::onAnchorClick,
                contentDescription = stringResource(R.string.menu),
            )
            val menuItems = searchMenuItems(
                historyVisible = historyVisible,
                groups = groups,
                scopeNames = scopeNames,
                isSourceScope = isSourceScope,
                precisionSearch = precisionSearch,
                blockSourceDialogs = blockSourceDialogs
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        R.id.menu_clear_history -> onClearHistory()
                        R.id.menu_precision_search -> onTogglePrecisionSearch()
                        R.id.menu_block_source_dialogs -> onToggleBlockSourceDialogs()
                        R.id.menu_source_manage -> onSourceManage()
                        R.id.menu_search_scope -> onSearchScope()
                        R.id.menu_log -> onShowLog()
                        R.id.menu_network_log -> onShowNetworkLog()
                        R.id.menu_1 -> onAllSources()
                        else -> item.title?.let { onDynamicScope(it, item.checked) }
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    NgSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.search_book_key),
        modifier = modifier.focusRequester(focusRequester),
        variant = NgSearchBarVariant.TOOLBAR,
        hideHintOnFocus = true,
        onFocusChanged = onFocusChanged,
        onSearch = onSubmit
    )
}

@Composable
private fun searchMenuItems(
    historyVisible: Boolean,
    groups: List<String>,
    scopeNames: List<String>,
    isSourceScope: Boolean,
    precisionSearch: Boolean,
    blockSourceDialogs: Boolean
): List<NgExpandableActionMenuItem> = buildList {
    if (historyVisible) {
        add(
            NgExpandableActionMenuItem(
                R.id.menu_clear_history,
                R.string.clear_records,
                R.drawable.ic_clear_all
            )
        )
    }
    add(
        NgExpandableActionMenuItem(
            R.id.menu_precision_search,
            R.string.precision_search,
            R.drawable.ic_archery_target,
            checked = precisionSearch
        )
    )
    add(
        NgExpandableActionMenuItem(
            R.id.menu_block_source_dialogs,
            R.string.block_source_dialogs,
            R.drawable.ic_popup_blocked,
            checked = blockSourceDialogs
        )
    )
    add(
        NgExpandableActionMenuItem(
            R.id.menu_source_manage,
            R.string.book_source_manage,
            R.drawable.ic_cfg_source
        )
    )
    add(
        NgExpandableActionMenuItem(
            R.id.menu_search_scope,
            R.string.groups_or_source,
            R.drawable.ic_groups
        )
    )
    add(
        NgExpandableActionMenuItem(
            R.id.menu_diagnostics,
            R.string.diagnostics,
            R.drawable.ic_bug_report,
            children = listOf(
                NgExpandableActionMenuItem(
                    R.id.menu_log,
                    R.string.log,
                    R.drawable.ic_cfg_about
                ),
                NgExpandableActionMenuItem(
                    R.id.menu_network_log,
                    R.string.network_request_log,
                    R.drawable.ic_network_check
                )
            )
        )
    )
    if (isSourceScope) {
        scopeNames.firstOrNull()?.let { sourceName ->
            add(
                NgExpandableActionMenuItem(
                    itemId = SELECTED_SOURCE_ITEM_ID,
                    titleRes = 0,
                    iconRes = R.drawable.ic_groups,
                    dividerBefore = true,
                    title = sourceName,
                    checked = true
                )
            )
        }
    }
    add(
        NgExpandableActionMenuItem(
            itemId = R.id.menu_1,
            titleRes = R.string.all_source,
            iconRes = R.drawable.ic_check_source,
            dividerBefore = !isSourceScope,
            checked = !isSourceScope && scopeNames.none { it in groups }
        )
    )
    if (groups.isNotEmpty()) {
        add(
            NgExpandableActionMenuItem(
                R.id.menu_source_groups,
                R.string.book_source_groups,
                R.drawable.ic_groups,
                children = groups.mapIndexed { index, group ->
                    NgExpandableActionMenuItem(
                        itemId = SOURCE_SCOPE_ITEM_ID_BASE + index,
                        titleRes = 0,
                        iconRes = BookSourceGroupIcon.resolve(group),
                        title = group,
                        checked = !isSourceScope && group in scopeNames
                    )
                }
            )
        )
    }

}

@Composable
private fun SearchProgress(progress: SearchViewModel.SearchProgress?) {
    if (progress == null || progress.total <= 0) return
    val fraction = (progress.progress.toFloat() / progress.total).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(30.dp)
            .clip(shape)
            .background(colorResource(R.color.ng_surface_card))
            .border(0.8.dp, colorResource(R.color.ng_settings_item_stroke), shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Color(NgTheme.colors.primary).copy(alpha = 0.38f))
        )
        Text(
            text = stringResource(
                R.string.change_source_progress,
                progress.resultCount,
                progress.progress,
                progress.total,
                progress.sourceName
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchInputHelp(
    history: List<SearchKeyword>,
    bookshelfMatches: List<Book>,
    onHistoryClick: (SearchKeyword) -> Unit,
    onDeleteHistory: (SearchKeyword) -> Unit,
    onBookshelfBookClick: (Book) -> Unit
) {
    val bookshelfNames = remember(bookshelfMatches) {
        bookshelfMatches.mapTo(hashSetOf()) { it.name }
    }
    val visibleHistory = remember(history, bookshelfNames) {
        history.filterNot { it.word in bookshelfNames }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 6.dp,
            end = 6.dp,
            bottom = 12.dp
        )
    ) {
        if (bookshelfMatches.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
                ) {
                    bookshelfMatches.forEach { book ->
                        BookshelfMatchChip(
                            book = book,
                            onClick = { onBookshelfBookClick(book) }
                        )
                    }
                }
            }
        }
        if (visibleHistory.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp)
                ) {
                    visibleHistory.forEach { keyword ->
                        SearchHelpChip(
                            text = keyword.word,
                            containerColor = Color(NgTheme.colors.primary).copy(alpha = 1f),
                            contentColor = Color.White,
                            onClick = { onHistoryClick(keyword) },
                            onLongClick = { onDeleteHistory(keyword) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookshelfMatchChip(book: Book, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val text = if (book.author.isBlank()) book.name else "${book.name} · ${book.author}"
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colorResource(R.color.ng_surface_card).copy(alpha = 1f))
            .border(0.8.dp, colorResource(R.color.ng_card_stroke), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bottom_books_s),
            contentDescription = stringResource(R.string.bookshelf),
            tint = Color(NgTheme.colors.primary),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchHelpChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = contentColor,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultList(
    books: List<SearchBook>,
    isSearching: Boolean,
    hasMore: Boolean,
    manualStopped: Boolean,
    resultRevision: Long,
    bookshelfRevision: Long,
    onBookClick: (SearchBook) -> Unit,
    onBookLongClick: (SearchBook) -> Unit,
    isInBookshelf: (SearchBook) -> Boolean,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(
        listState,
        books.size,
        isSearching,
        hasMore,
        manualStopped,
        resultRevision
    ) {
        snapshotFlow {
            listState.isScrollInProgress to
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.distinctUntilChanged().collect { (scrolling, lastVisible) ->
            if (scrolling && books.isNotEmpty() && lastVisible >= books.lastIndex &&
                !isSearching && hasMore && !manualStopped
            ) {
                onLoadMore()
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 2.dp,
            bottom = 10.dp
        )
    ) {
        items(
            items = books,
            key = { "${it.name}\u0000${it.author}\u0000${it.bookUrl}" }
        ) { book ->
            val inBookshelf = remember(
                book.name,
                book.author,
                book.bookUrl,
                bookshelfRevision
            ) {
                isInBookshelf(book)
            }
            SearchResultCard(
                book = book,
                inBookshelf = inBookshelf,
                originCount = book.origins.size,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) },
                cardBackgroundColorRes = R.color.ng_search_result_card_surface,
                cardStrokeColorRes = R.color.ng_search_result_card_stroke
            )
        }
    }
}
