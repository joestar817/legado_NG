package io.legado.app.ui.main.bookshelf.style1.books

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isUpError
import io.legado.app.help.config.BookshelfLayoutMode
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgBookshelfUnreadBadge
import io.legado.app.ui.design.components.compose.NgBookshelfUpdateIndicator
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.main.bookshelf.bookshelfAuthorText
import io.legado.app.utils.toTimeAgo

private val BookshelfGridOuterEdge = 16.dp
private val BookshelfGridItemInternalInset = 8.dp

@Composable
internal fun BookshelfBooksScreen(
    books: List<Book>,
    layoutMode: BookshelfLayoutMode,
    columns: Int,
    spacing: Int,
    showBookName: Int,
    coverRadius: Int,
    showUnread: Boolean,
    showLastUpdateTime: Boolean,
    bottomInset: Dp,
    scrollToTopToken: Long,
    coverRevision: Int,
    lastUpdateTick: Long,
    isEInk: Boolean,
    updatingBookUrls: Set<String>,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
    onOpenBookActions: (Book) -> Unit,
) {
    NgPullRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        enabled = refreshEnabled,
        // 书架只用卡内更新圈反馈进度，避免重复显示整页刷新指示器。
        showIndicator = false,
    ) {
        if (books.isEmpty()) {
            Text(
                text = stringResource(R.string.bookshelf_empty),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            when (layoutMode) {
                BookshelfLayoutMode.LIST,
                BookshelfLayoutMode.COMPACT -> BookshelfBookList(
                    books = books,
                    compact = layoutMode == BookshelfLayoutMode.COMPACT,
                    spacing = spacing,
                    bottomInset = bottomInset,
                    showLastUpdateTime = showLastUpdateTime,
                    scrollToTopToken = scrollToTopToken,
                    coverRevision = coverRevision,
                    lastUpdateTick = lastUpdateTick,
                    isEInk = isEInk,
                    updatingBookUrls = updatingBookUrls,
                    onOpenBook = onOpenBook,
                    onOpenBookInfo = onOpenBookInfo,
                    onOpenBookActions = onOpenBookActions,
                )

                BookshelfLayoutMode.GRID -> BookshelfBookGrid(
                    books = books,
                    columns = columns,
                    spacing = spacing,
                    showBookName = showBookName,
                    coverRadius = coverRadius,
                    showUnread = showUnread,
                    bottomInset = bottomInset,
                    scrollToTopToken = scrollToTopToken,
                    coverRevision = coverRevision,
                    isEInk = isEInk,
                    updatingBookUrls = updatingBookUrls,
                    onOpenBook = onOpenBook,
                    onOpenBookInfo = onOpenBookInfo,
                )

                BookshelfLayoutMode.GROUP_GRID -> Unit
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfBookList(
    books: List<Book>,
    compact: Boolean,
    spacing: Int,
    bottomInset: Dp,
    showLastUpdateTime: Boolean,
    scrollToTopToken: Long,
    coverRevision: Int,
    lastUpdateTick: Long,
    isEInk: Boolean,
    updatingBookUrls: Set<String>,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
    onOpenBookActions: (Book) -> Unit,
) {
    val state = rememberLazyListState()
    val density = LocalDensity.current
    val itemSpacing = with(density) { spacing.toDp() }
    val firstItemExtra = with(density) { 4.toDp() }
    val lastItemExtra = with(density) { 12.toDp() }
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0L) {
            if (isEInk) state.scrollToItem(0) else state.animateScrollToItem(0)
        }
    }
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(
            bottom = bottomInset,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(books, key = { _, book -> book.bookUrl }) { index, book ->
            Box(
                modifier = Modifier
                    .animateItem()
                    .padding(
                        top = itemSpacing + 2.dp + if (index == 0) firstItemExtra else 0.dp,
                        bottom = itemSpacing + 2.dp +
                                if (index == books.lastIndex) lastItemExtra else 0.dp,
                    )
            ) {
                BookshelfListBookItem(
                    book = book,
                    compact = compact,
                    updating = !book.isLocal && book.bookUrl in updatingBookUrls,
                    showLastUpdateTime = showLastUpdateTime && !compact,
                    coverRevision = coverRevision,
                    lastUpdateTick = lastUpdateTick,
                    onClick = { onOpenBook(book) },
                    onLongClick = { onOpenBookInfo(book) },
                    onMoreClick = { onOpenBookActions(book) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfListBookItem(
    book: Book,
    compact: Boolean,
    updating: Boolean,
    showLastUpdateTime: Boolean,
    coverRevision: Int,
    lastUpdateTick: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val context = LocalContext.current
    val cardHeight = if (compact) 76.dp else 100.dp
    val coverWidth = if (compact) 46.dp else 62.dp
    val coverHeight = if (compact) 60.dp else 84.dp
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(dimensionResource(R.dimen.ng_radius_s))
    val cardColor = colorResource(
        if (isPressed) {
            R.color.ng_bookshelf_list_card_pressed
        } else {
            R.color.ng_bookshelf_list_card_surface
        }
    )
    val cardStrokeColor = colorResource(R.color.ng_bookshelf_list_card_stroke)
    val titleColor = colorResource(R.color.primaryText)
    val summaryColor = colorResource(R.color.tv_text_summary)
    val lastUpdateText = if (showLastUpdateTime && !book.isLocal) {
        remember(book.latestChapterTime, lastUpdateTick) {
            book.latestChapterTime.toTimeAgo()
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(cardHeight)
            .clip(shape)
            .background(cardColor)
            .border(0.6.dp, cardStrokeColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 10.dp, top = 7.dp, end = 14.dp, bottom = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookshelfCover(
                book = book,
                coverRevision = coverRevision,
                contentDescription = stringResource(R.string.img_cover),
                modifier = Modifier
                    .width(coverWidth)
                    .height(coverHeight),
            )
            if (compact) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = book.name,
                        color = titleColor,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(end = 60.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 34.dp),
                    ) {
                        BookshelfSummaryIcon(
                            icon = R.drawable.ic_author,
                            contentDescription = stringResource(R.string.author),
                        )
                        Text(
                            text = book.bookshelfAuthorText(context),
                            color = summaryColor,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "•",
                            color = summaryColor,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Text(
                            text = book.durChapterTitle.orEmpty(),
                            color = summaryColor,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    BookshelfSummaryLine(
                        icon = R.drawable.ic_book_last,
                        contentDescription = stringResource(R.string.lasted_show),
                        text = book.latestChapterTitle.orEmpty(),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(end = 34.dp, bottom = 4.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = book.name,
                        color = titleColor,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            end = 60.dp,
                            bottom = 4.dp,
                        ),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 34.dp),
                    ) {
                        BookshelfSummaryIcon(
                            icon = R.drawable.ic_author,
                            contentDescription = stringResource(R.string.author),
                        )
                        Text(
                            text = book.bookshelfAuthorText(context),
                            color = summaryColor,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (lastUpdateText != null) {
                            Text(
                                text = lastUpdateText,
                                color = summaryColor,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
                            )
                        }
                    }
                    BookshelfSummaryLine(
                        icon = R.drawable.ic_history,
                        contentDescription = stringResource(R.string.read_dur_progress),
                        text = book.durChapterTitle.orEmpty(),
                        modifier = Modifier.padding(end = 34.dp),
                    )
                    BookshelfSummaryLine(
                        icon = R.drawable.ic_book_last,
                        contentDescription = stringResource(R.string.lasted_show),
                        text = book.latestChapterTitle.orEmpty(),
                        modifier = Modifier.padding(end = 34.dp, bottom = 4.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(26.dp)) {
                if (updating) {
                    BookshelfLoading(modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                painter = painterResource(R.drawable.ic_more_horiz),
                contentDescription = stringResource(R.string.more),
                tint = summaryColor,
                modifier = Modifier
                    .size(32.dp)
                    .combinedClickable(onClick = onMoreClick, onLongClick = {})
                    .padding(6.dp),
            )
        }
        if (book.isUpError) {
            Icon(
                painter = painterResource(R.drawable.ic_book_update_error),
                contentDescription = stringResource(R.string.update_book_fail),
                tint = Color.Unspecified,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun BookshelfSummaryLine(
    icon: Int,
    contentDescription: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        BookshelfSummaryIcon(icon, contentDescription)
        Text(
            text = text,
            color = colorResource(R.color.tv_text_summary),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookshelfSummaryIcon(
    icon: Int,
    contentDescription: String,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = colorResource(R.color.tv_text_summary),
        modifier = Modifier
            .size(dimensionResource(R.dimen.desc_icon_size))
            .padding(horizontal = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfBookGrid(
    books: List<Book>,
    columns: Int,
    spacing: Int,
    showBookName: Int,
    coverRadius: Int,
    showUnread: Boolean,
    bottomInset: Dp,
    scrollToTopToken: Long,
    coverRevision: Int,
    isEInk: Boolean,
    updatingBookUrls: Set<String>,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
) {
    val state = rememberLazyGridState()
    val density = LocalDensity.current
    val itemSpacing = with(density) { spacing.toDp() }
    val edgeExtra = with(density) { 24.toDp() }
    val horizontalContentPadding = (
        BookshelfGridOuterEdge - itemSpacing - BookshelfGridItemInternalInset
    ).coerceAtLeast(0.dp)
    val spanCount = columns.coerceIn(2, 6)
    val lastRowIndex = books.lastIndex / spanCount
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0L) {
            if (isEInk) state.scrollToItem(0) else state.animateScrollToItem(0)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(spanCount),
        state = state,
        contentPadding = PaddingValues(
            start = horizontalContentPadding,
            end = horizontalContentPadding,
            bottom = bottomInset,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItemsIndexed(books, key = { _, book -> book.bookUrl }) { index, book ->
            val rowIndex = index / spanCount
            BookshelfGridBookItem(
                book = book,
                modifier = Modifier.animateItem(),
                itemPadding = PaddingValues(
                    start = itemSpacing,
                    top = itemSpacing + if (rowIndex == 0) edgeExtra else 0.dp,
                    end = itemSpacing,
                    bottom = itemSpacing + if (rowIndex == lastRowIndex) edgeExtra else 0.dp,
                ),
                showBookName = showBookName,
                coverRadius = coverRadius,
                showUnread = showUnread,
                updating = !book.isLocal && book.bookUrl in updatingBookUrls,
                coverRevision = coverRevision,
                onClick = { onOpenBook(book) },
                onLongClick = { onOpenBookInfo(book) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfGridBookItem(
    book: Book,
    modifier: Modifier = Modifier,
    itemPadding: PaddingValues,
    showBookName: Int,
    coverRadius: Int,
    showUnread: Boolean,
    updating: Boolean,
    coverRevision: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(itemPadding)
            .background(
                if (isFocused) colorResource(R.color.btn_bg_press) else Color.Transparent
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .aspectRatio(3f / 4f),
            ) {
                BookshelfCover(
                    book = book,
                    coverRadius = coverRadius,
                    coverRevision = coverRevision,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showBookName == 2) {
                    Text(
                        text = book.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color(0x60000000),
                                    1f to Color(0xA0000000),
                                )
                            )
                            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
                    )
                }
                if (book.isUpError) {
                    Icon(
                        painter = painterResource(R.drawable.ic_book_update_error),
                        contentDescription = stringResource(R.string.update_book_fail),
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 6.dp)
                            .size(16.dp),
                    )
                }
            }
            when {
                updating -> BookshelfLoading(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp),
                )

                showUnread -> BookshelfUnreadBadge(
                    count = book.getUnreadChapterNum(),
                    highlight = book.lastCheckCount > 0,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentSize(),
                )
            }
        }
        if (showBookName == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = book.name,
                color = colorResource(R.color.primaryText),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp),
            )
        }
    }
}

@Composable
private fun BookshelfCover(
    book: Book,
    modifier: Modifier = Modifier,
    coverRadius: Int? = null,
    contentDescription: String? = null,
    coverRevision: Int,
) {
    NgBookCover(
        book = book,
        modifier = modifier,
        coverRadius = coverRadius,
        contentDescription = contentDescription,
        revision = coverRevision,
    )
}

@Composable
private fun BookshelfLoading(modifier: Modifier = Modifier) {
    NgBookshelfUpdateIndicator(modifier = modifier)
}

@Composable
private fun BookshelfUnreadBadge(
    count: Int,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    NgBookshelfUnreadBadge(
        count = count,
        highlight = highlight,
        modifier = modifier,
    )
}
