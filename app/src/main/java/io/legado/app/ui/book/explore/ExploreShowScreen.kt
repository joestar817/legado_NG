package io.legado.app.ui.book.explore

import androidx.compose.animation.core.MutableTransitionState
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.ui.book.search.SearchBookCover
import io.legado.app.ui.book.search.SearchResultCard
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.rememberNgExpandableActionMenuContentWidth
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgMenuSelectionStyle
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.main.explore.ExploreInfoStore
import io.legado.app.ui.main.explore.ExploreKindItem
import io.legado.app.ui.main.explore.rememberExploreKindLabel
import io.legado.app.ui.main.explore.sourceTileColor
import io.legado.app.ui.main.explore.sourceTileContentColor
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.launch

private val ExploreCategoryTileHeight = 62.dp
private val ExploreCategoryRowSpacing = 6.dp

@Composable
internal fun ExploreShowScreen(
    state: ExploreShowUiState,
    layoutMode: ExploreShowLayoutMode,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshKinds: () -> Unit,
    onSelectKind: (ExploreKind) -> Unit,
    onLayoutModeChange: (ExploreShowLayoutMode) -> Unit,
    onSelectPage: (Int) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onLoadNext: () -> Unit,
    onRetryContent: () -> Unit,
    onOpenBook: (SearchBook) -> Unit,
    onShowError: (String) -> Unit
) {
    val kindSections = remember(state.kinds) { buildExploreKindSections(state.kinds) }
    val activeSectionIndex = kindSections.sectionIndexFor(state.selectedKind)
    val visibleSections =
        if (kindSections.useTopLevelGroups) {
            kindSections.sections.getOrNull(activeSectionIndex)?.let(::listOf).orEmpty()
        } else {
            kindSections.sections
        }
    val sectionRows = visibleSections.map { section ->
        section to calculateExploreDetailKindRows(section.items)
    }
    val controlRows = calculateExploreDetailKindRows(kindSections.controls)
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val currentPage by remember(gridState, state.bookPages, state.firstLoadedPage) {
        derivedStateOf {
            val visibleBooks = gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val key = item.key as? String
                val page = key?.removePrefix("list_")?.removePrefix("grid_")
                    ?.let(state.bookPages::get)
                page?.let { item.offset.y to it }
            }
            // A grid row may straddle two source pages. Use its later page so
            // navigating to a cached boundary cannot get stuck on the same row.
            val firstRow = visibleBooks.firstOrNull()?.first
            visibleBooks.takeWhile { it.first == firstRow }.maxOfOrNull { it.second }
                ?: state.firstLoadedPage
        }
    }
    val lastBookPage = remember(state.bookPages) { state.bookPages.values.maxOrNull() ?: 0 }
    val navigationEnabled = state.selectedKind != null && !state.isContentLoading &&
            !state.isKindsLoading && !state.isRefreshing && !gridState.isScrollInProgress
    val navigate: (Int) -> Unit = { page ->
        if (navigationEnabled && page > 0) {
            val bookIndex = state.books.indexOfFirst { state.bookPages[it.bookUrl] == page }
            if (bookIndex >= 0) {
                scope.launch {
                    gridState.scrollToItem(bookIndex)
                }
            } else {
                onJumpToPage(page)
            }
        }
    }
    val columns = if (layoutMode == ExploreShowLayoutMode.LIST) 1 else 3
    var categoriesExpanded by rememberSaveable(state.source?.bookSourceUrl) { mutableStateOf(false) }
    var categoryAnchorX by remember { mutableStateOf<Float?>(null) }
    var categoryPanelLeft by remember { mutableStateOf(0f) }
    val categoryCorner = NgTheme.shapes.mediumDp.dp
    val categoryShape = remember(categoryAnchorX, categoryPanelLeft, categoryCorner) {
        ExploreCategoryBubbleShape(categoryCorner, categoryAnchorX?.minus(categoryPanelLeft))
    }
    val categoryVisibility = remember { MutableTransitionState(false) }
    categoryVisibility.targetState = categoriesExpanded
    val categoryPanelVisible = categoryVisibility.currentState || categoryVisibility.targetState
    BackHandler(enabled = categoriesExpanded) { categoriesExpanded = false }
    val selectKind: (ExploreKind) -> Unit = { kind ->
        onSelectKind(kind)
        categoriesExpanded = false
    }
    val shouldLoadNext by remember(gridState, state.books, state.hasMore, state.isContentLoading, state.contentError) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.books.isNotEmpty() &&
                    state.hasMore &&
                    !state.isContentLoading && state.contentError == null &&
                    lastVisible >= gridState.layoutInfo.totalItemsCount - 4
        }
    }

    LaunchedEffect(state.kindsError) {
        if (state.kindsError != null) categoriesExpanded = true
    }
    LaunchedEffect(shouldLoadNext) {
        if (shouldLoadNext) onLoadNext()
    }
    LaunchedEffect(state.selectionRevision) {
        if (state.selectionRevision > 0) {
            gridState.scrollToItem(0)
        }
    }

    NgPullRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        enabled = !state.isKindsLoading,
        showIndicator = false
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExploreShowTopBar(
                sourceName = state.sourceName,
                isLoading = state.isKindsLoading && !state.isRefreshing,
                onBack = onBack,
                onRefresh = onRefresh,
                categoriesExpanded = categoriesExpanded,
                onToggleCategories = { categoriesExpanded = !categoriesExpanded },
                onCategoryAnchorPosition = { categoryAnchorX = it }
            )

            Box(modifier = Modifier.weight(1f)) {
                NgGlassSurface(
                    modifier = Modifier.fillMaxSize()
                        .navigationBarsPadding()
                        .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp),
                    role = NgMaterialRole.CONTENT,
                    liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
                    shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
                    style = NgGlassDefaults.bookDetailStyle(
                        containerColor = colorResource(R.color.ng_surface_card)
                    )
                ) {
                    // Keep the header's space while the category overlay is visible.
                    if (categoryPanelVisible) {
                        Spacer(Modifier.fillMaxWidth().height(44.5.dp))
                    } else {
                        ExploreContentToolbar(
                            selectedKindLabel = state.selectedKind?.title
                                ?.let(::sanitizeExploreDetailLabel)
                                .orEmpty(),
                            page = currentPage,
                            layoutMode = layoutMode,
                            onSelectPage = { onSelectPage(currentPage) },
                            navigationEnabled = navigationEnabled,
                            canGoPrevious = currentPage > 1,
                            canGoNext = currentPage < Int.MAX_VALUE &&
                                (state.hasMore || currentPage < lastBookPage),
                            onPrevious = { navigate(currentPage - 1) },
                            onNext = { navigate(currentPage + 1) },
                            onLayoutModeChange = onLayoutModeChange
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 0.5.dp,
                            color = colorResource(R.color.secondaryText).copy(alpha = 0.12f)
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            top = 8.dp,
                            end = 8.dp,
                            bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(
                            if (layoutMode == ExploreShowLayoutMode.LIST) 0.dp else 16.dp
                        )
                    ) {
                        if (layoutMode == ExploreShowLayoutMode.LIST) {
                            items(
                                items = state.books,
                                key = { "list_${it.bookUrl}" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { book ->
                                Column {
                                    SearchResultCard(
                                        book = book,
                                        inBookshelf = state.isBookInShelf(book),
                                        originCount = 0,
                                        onClick = { onOpenBook(book) },
                                        onLongClick = { onOpenBook(book) },
                                        outerHorizontalPadding = 0.dp,
                                        outerVerticalPadding = 0.dp,
                                        cardCornerRadius = 0.dp,
                                        cardHeight = 120.dp,
                                        cardContentPadding = 8.dp,
                                        coverWidth = 68.dp,
                                        coverHeight = 92.dp,
                                        contentStartPadding = 78.dp,
                                        cardBackgroundColorRes = android.R.color.transparent,
                                        cardBorderWidth = 0.dp
                                    )
                                    if (book.bookUrl != state.books.lastOrNull()?.bookUrl) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 0.5.dp,
                                            color = colorResource(R.color.secondaryText).copy(alpha = 0.12f)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = state.books,
                                key = { "grid_${it.bookUrl}" }
                            ) { book ->
                                ExploreBookGridCard(
                                    book = book,
                                    inBookshelf = state.isBookInShelf(book),
                                    onClick = { onOpenBook(book) }
                                )
                            }
                        }

                        when {
                            state.isContentLoading && !state.isLoadingPrevious -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ExploreStatusRow(text = stringResource(R.string.is_loading), loading = true)
                                }
                            }

                            state.contentError != null -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ExploreContentErrorRow(
                                        error = state.contentError,
                                        onRetry = onRetryContent,
                                        onShowError = onShowError
                                    )
                                }
                            }

                            state.selectedKind == null && !state.isKindsLoading -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ExploreStatusRow(stringResource(R.string.explore_category_empty))
                                }
                            }

                            state.books.isEmpty() && state.selectedKind != null -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ExploreStatusRow(stringResource(R.string.empty))
                                }
                            }

                            !state.hasMore -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ExploreStatusRow(stringResource(R.string.explore_no_more))
                                }
                            }
                        }
                    }
                }
                if (categoryPanelVisible) {
                    Box(Modifier.matchParentSize().pointerInput(Unit) {
                        detectTapGestures { categoriesExpanded = false }
                    })
                }
                androidx.compose.animation.AnimatedVisibility(visibleState = categoryVisibility) {
                    NgGlassSurface(
                        modifier = Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
                            .onGloballyPositioned { categoryPanelLeft = it.boundsInRoot().left }
                            .pointerInput(Unit) { detectTapGestures { } },
                        role = NgMaterialRole.CONTROL,
                        liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
                        shape = categoryShape,
                        contentPadding = PaddingValues(top = 4.dp),
                        style = NgGlassDefaults.bookDetailStyle(
                            containerColor = colorResource(R.color.ng_surface_card)
                        )
                    ) {
                        ExploreCategoryPanel(
                            state = state,
                            kindSections = kindSections,
                            sectionRows = sectionRows,
                            controlRows = controlRows,
                            activeSectionIndex = activeSectionIndex,
                            onSelectKind = onSelectKind,
                            onOpenKind = selectKind,
                            onRefreshKinds = onRefreshKinds,
                            onShowError = onShowError
                        )

                    }
                }

            }
        }
    }
}

@Composable
private fun ExploreCategoryPanel(
    state: ExploreShowUiState,
    kindSections: ExploreKindSections,
    sectionRows: List<Pair<ExploreKindSection, List<List<Pair<ExploreKind, Int>>>>>,
    controlRows: List<List<Pair<ExploreKind, Int>>>,
    activeSectionIndex: Int,
    onSelectKind: (ExploreKind) -> Unit,
    onOpenKind: (ExploreKind) -> Unit,
    onRefreshKinds: () -> Unit,
    onShowError: (String) -> Unit
) {
    val fallbackLabel = stringResource(R.string.explore_category_default)
    val ungroupedLabel = stringResource(R.string.no_group)
    val scrollState = rememberScrollState()
    val scrollThumbColor = colorResource(R.color.secondaryText).copy(alpha = 0.32f)
    LaunchedEffect(activeSectionIndex, state.kinds) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        if (kindSections.useTopLevelGroups && kindSections.sections.size > 1) {
            val sectionLabels = kindSections.sections.map { section ->
                section.header?.let { header ->
                    sanitizeExploreDetailLabel(header.displaySectionLabel())
                        .ifBlank { fallbackLabel }
                } ?: ungroupedLabel
            }
            ExploreSectionDropdown(
                modifier = Modifier.align(Alignment.End),
                labels = sectionLabels,
                selectedIndex = activeSectionIndex,
                onSelectSection = { index ->
                    kindSections.kindForSectionSelection(index, state.selectedKind)
                        ?.takeIf { nextKind -> nextKind != state.selectedKind }
                        ?.let(onSelectKind)
                }
            )
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .drawWithContent {
                    drawContent()
                    if (scrollState.maxValue > 0 && size.height > 0f) {
                        val thumbWidth = 3.dp.toPx()
                        val thumbHeight = 40.dp.toPx().coerceAtMost(size.height)
                        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                        val offsetY = (size.height - thumbHeight) * progress
                        drawRoundRect(
                            color = scrollThumbColor,
                            topLeft = Offset(size.width - thumbWidth, offsetY),
                            size = Size(thumbWidth, thumbHeight),
                            cornerRadius = CornerRadius(thumbWidth / 2f)
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ExploreCategoryRowSpacing)
            ) {
                controlRows.forEach { row ->
                    ExploreCategoryRow(
                        row = row,
                        source = state.source,
                        selectedKind = state.selectedKind,
                        onSelectKind = onOpenKind,
                        onRefreshKinds = onRefreshKinds,
                        onShowError = onShowError
                    )
                }

                sectionRows.forEach { (_, rows) ->
                    rows.forEach { row ->
                        ExploreCategoryRow(
                            row = row,
                            source = state.source,
                            selectedKind = state.selectedKind,
                            onSelectKind = onOpenKind,
                            onRefreshKinds = onRefreshKinds,
                            onShowError = onShowError
                        )
                    }
                }

                if (state.isKindsLoading && state.kinds.isEmpty()) {
                    ExploreStatusRow(text = stringResource(R.string.is_loading), loading = true)
                }

                state.kindsError?.let { error ->
                    ExploreErrorRow(error = error, onShowError = onShowError)
                }
            }

        }
    }
}

private fun ExploreShowUiState.isBookInShelf(book: SearchBook): Boolean {
    return "${book.name}-${book.author}" in bookshelfKeys ||
            book.name in bookshelfKeys ||
            book.bookUrl in bookshelfKeys
}

@Composable
private fun ExploreShowTopBar(
    sourceName: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    categoriesExpanded: Boolean,
    onToggleCategories: () -> Unit,
    onCategoryAnchorPosition: (Float) -> Unit
) {
    val contentColor = colorResource(R.color.ng_search_icon)
    // Keep the same geometry and material as BookSourceManageTopBar.
    NgGlassSurface(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp),
        role = NgMaterialRole.CONTROL,
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp)
                .padding(start = 0.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NgFloatingToolbarBackButton(onClick = onBack, width = 32.dp)
            Text(
                text = sourceName,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = colorResource(R.color.primaryText),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            ExploreToolbarButton(
                iconRes = R.drawable.ic_grid_menu,
                description = stringResource(if (categoriesExpanded)
                    R.string.explore_hide_categories else R.string.explore_show_categories),
                tint = if (categoriesExpanded) Color(NgTheme.colors.primary) else contentColor,
                onClick = onToggleCategories,
                modifier = Modifier.onGloballyPositioned {
                    onCategoryAnchorPosition(it.boundsInRoot().center.x)
                }.background(
                    if (categoriesExpanded) Color(NgTheme.colors.primary).copy(alpha = 0.13f)
                    else Color.Transparent, RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(2.dp))
            if (isLoading) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = contentColor)
                }
            } else {
                ExploreToolbarButton(
                    iconRes = R.drawable.ic_refresh_black_24dp,
                    description = stringResource(R.string.refresh_sort),
                    tint = contentColor,
                    onClick = onRefresh
                )
            }
        }
    }
}

@Composable
private fun ExploreToolbarButton(
    iconRes: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = tint
        )
    }
}

@Composable
private fun ExploreSectionDropdown(
    labels: List<String>,
    selectedIndex: Int,
    onSelectSection: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val menuState = remember { NgPopupToggleState() }
    val baseItems = remember(labels) {
        labels.mapIndexed { index, label ->
            NgExpandableActionMenuItem(
                itemId = index,
                titleRes = R.string.explore_category_default,
                iconRes = 0,
                title = label,
                selectionStyle = NgMenuSelectionStyle.TEXT_ONLY
            )
        }
    }
    val items = remember(baseItems, selectedIndex) {
        baseItems.map { it.copy(checked = it.itemId == selectedIndex) }
    }
    // Measure all labels without selection state, so switching groups never resizes the menu.
    val preferredMenuWidth = rememberNgExpandableActionMenuContentWidth(baseItems)
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val menuWidth = preferredMenuWidth.coerceAtMost(maxWidth)
        // Anchor the popup to the entire panel content area, then align its right
        // edge inside that area instead of letting Material align it to the label.
        val menuOffset = if (layoutDirection == LayoutDirection.Ltr) maxWidth - menuWidth else 0.dp
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = 200.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = menuState::onAnchorClick)
                .padding(horizontal = 8.dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labels.getOrNull(selectedIndex).orEmpty(),
                modifier = Modifier.weight(1f, fill = false),
                color = Color(NgTheme.colors.primary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(R.string.group),
                tint = Color(NgTheme.colors.primary),
                modifier = Modifier.size(18.dp)
            )
        }
        NgExpandableActionMenu(
            expanded = menuState.expanded,
            onDismissRequest = menuState::onDismissRequest,
            items = items,
            width = menuWidth,
            offset = DpOffset(menuOffset, 0.dp),
            onItemClick = { item ->
                menuState.close()
                onSelectSection(item.itemId)
            }
        )
    }
}

@Composable
private fun ExploreCategoryRow(
    row: List<Pair<ExploreKind, Int>>,
    source: BookSource?,
    selectedKind: ExploreKind?,
    onSelectKind: (ExploreKind) -> Unit,
    onRefreshKinds: () -> Unit,
    onShowError: (String) -> Unit
) {
    val fallbackLabel = stringResource(R.string.explore_category_default)
    val context = androidx.compose.ui.platform.LocalContext.current
    val infoMap = remember(source?.bookSourceUrl) {
        val sourceUrl = source?.bookSourceUrl.orEmpty()
        ExploreInfoStore.infoMapList[sourceUrl] ?: InfoMap(sourceUrl).also {
            ExploreInfoStore.infoMapList.put(sourceUrl, it)
        }
    }
    val scope = rememberCoroutineScope()
    val sourceJsExtensions = remember(source, infoMap) {
        SourceLoginJsExtensions(
            context as? AppCompatActivity,
            source,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit

                override fun reUiView(deltaUp: Boolean) {
                    scope.launch { onRefreshKinds() }
                }
            }
        )
    }
    val usedSpan = row.sumOf { it.second }.coerceAtMost(EXPLORE_DETAIL_MAX_SPAN)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        row.forEach { (kind, span) ->
            if (kind.type == Type.url && !kind.url.isNullOrBlank()) {
                ExploreCategoryTile(
                    kind = kind,
                    source = source,
                    infoMap = infoMap,
                    selected = kind == selectedKind,
                    wide = span >= EXPLORE_DETAIL_MAX_SPAN,
                    onClick = { onSelectKind(kind) },
                    modifier = Modifier.weight(span.toFloat())
                )
            } else {
                ExploreKindItem(
                    kind = kind,
                    source = source,
                    infoMap = infoMap,
                    sourceJsExtensions = sourceJsExtensions,
                    onOpenKind = onSelectKind,
                    onShowError = onShowError,
                    displayLabelTransform = { label ->
                        sanitizeExploreDetailLabel(label).ifBlank { fallbackLabel }
                    },
                    modifier = Modifier.weight(span.toFloat())
                )
            }
        }
        if (usedSpan < EXPLORE_DETAIL_MAX_SPAN) {
            Spacer(Modifier.weight((EXPLORE_DETAIL_MAX_SPAN - usedSpan).toFloat()))
        }
    }
}

@Composable
private fun ExploreCategoryTile(
    kind: ExploreKind,
    source: BookSource?,
    infoMap: InfoMap,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceLabel by rememberExploreKindLabel(kind, source, infoMap)
    val fallbackLabel = stringResource(R.string.explore_category_default)
    val label = remember(sourceLabel, fallbackLabel) {
        sanitizeExploreDetailLabel(sourceLabel).ifBlank { fallbackLabel }
    }
    val compactLabel = remember(label) {
        if (label.length > 4) "${label.take(4)}…" else label
    }
    val primary = Color(NgTheme.colors.primary)
    val tileColorInt = remember(source?.bookSourceUrl, kind.title) {
        sourceTileColor("${source?.bookSourceUrl}#${kind.title}")
    }
    val tileColor = Color(tileColorInt)
    val tileContentColor = remember(tileColorInt) { sourceTileContentColor(tileColorInt) }
    if (wide) {
        Row(
            modifier = modifier
                .height(ExploreCategoryTileHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExploreCategoryGlyph(label, tileColor, tileContentColor, 36.dp)
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                color = if (selected) primary else colorResource(R.color.primaryText),
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Column(
            modifier = modifier
                .height(ExploreCategoryTileHeight)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ExploreCategoryGlyph(label, tileColor, tileContentColor, 36.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = compactLabel,
                color = if (selected) primary else colorResource(R.color.primaryText),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun ExploreCategoryGlyph(
    label: String,
    background: Color,
    foreground: Color,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.firstOrNull()?.toString()?.uppercase() ?: "源",
            color = foreground,
            fontSize = when {
                size >= 48.dp -> 23.sp
                size >= 40.dp -> 20.sp
                else -> 18.sp
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ExploreContentToolbar(
    selectedKindLabel: String,
    page: Int,
    layoutMode: ExploreShowLayoutMode,
    onSelectPage: () -> Unit,
    navigationEnabled: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLayoutModeChange: (ExploreShowLayoutMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = selectedKindLabel.ifBlank {
                stringResource(R.string.explore_category_empty)
            },
            modifier = Modifier.weight(1f),
            color = colorResource(R.color.primaryText),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ExplorePageButton(
            iconRes = R.drawable.ic_chevron_left_20,
            description = stringResource(R.string.prev_page),
            enabled = navigationEnabled && canGoPrevious,
            onClick = onPrevious
        )
        Text(
            text = stringResource(R.string.menu_page, page),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = navigationEnabled, onClick = onSelectPage)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            color = colorResource(R.color.secondaryText),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ExplorePageButton(
            iconRes = R.drawable.ic_chevron_right_20,
            description = stringResource(R.string.next_page),
            enabled = navigationEnabled && canGoNext,
            onClick = onNext
        )
        ExploreLayoutButton(
            selected = layoutMode == ExploreShowLayoutMode.LIST,
            iconRes = R.drawable.ic_chapter_list,
            description = stringResource(R.string.replace_view_list),
            onClick = { onLayoutModeChange(ExploreShowLayoutMode.LIST) }
        )
        Spacer(Modifier.width(2.dp))
        ExploreLayoutButton(
            selected = layoutMode == ExploreShowLayoutMode.GRID,
            iconRes = R.drawable.ic_view_quilt,
            description = stringResource(R.string.explore_view_grid),
            onClick = { onLayoutModeChange(ExploreShowLayoutMode.GRID) }
        )
    }
}

@Composable
private fun ExplorePageButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(width = 36.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = colorResource(R.color.primaryText).copy(alpha = if (enabled) 1f else 0.3f)
        )
    }
}

@Composable
private fun ExploreLayoutButton(
    selected: Boolean,
    iconRes: Int,
    description: String,
    onClick: () -> Unit
) {
    val primary = Color(NgTheme.colors.primary)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) primary.copy(alpha = 0.13f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                modifier = Modifier.size(16.dp),
                tint = if (selected) primary else colorResource(R.color.secondaryText)
            )
        }
    }
}

@Composable
private fun ExploreBookGridCard(
    book: SearchBook,
    inBookshelf: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            SearchBookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f),
                coverWidth = 108.dp,
                coverHeight = 146.dp
            )
            if (inBookshelf) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(9.dp)
                        .background(colorResource(R.color.md_green_600), CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            color = colorResource(R.color.primaryText),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        Text(
            text = book.author,
            color = colorResource(R.color.secondaryText),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExploreStatusRow(text: String, loading: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, color = colorResource(R.color.secondaryText), fontSize = 14.sp)
    }
}

@Composable
private fun ExploreErrorRow(error: String, onShowError: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ng_surface_card).copy(alpha = 0.76f))
            .clickable { onShowError(error) }
            .padding(14.dp)
    ) {
        Text(
            text = stringResource(R.string.load_error_retry),
            color = colorResource(R.color.primaryText),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExploreContentErrorRow(
    error: String,
    onRetry: () -> Unit,
    onShowError: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ng_surface_card).copy(alpha = 0.76f))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.load_error_retry),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRetry)
                .padding(10.dp),
            color = colorResource(R.color.primaryText),
            fontSize = 14.sp
        )
        Text(
            text = stringResource(R.string.explore_error_details),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onShowError(error) }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            color = Color(NgTheme.colors.primary),
            fontSize = 13.sp
        )
    }
}
