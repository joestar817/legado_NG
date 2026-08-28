package io.legado.app.ui.main.bookshelf.style1

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.book.isVideo
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfLayoutMode
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgCoverMosaic
import io.legado.app.ui.design.components.compose.NgCoverMosaicPresentationVariant
import io.legado.app.ui.design.components.compose.NgCoverMosaicVariant
import io.legado.app.ui.design.components.compose.NgVisualOverlayDialog
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.main.bookshelf.bookshelfAuthorText
import io.legado.app.utils.cnCompare
import kotlin.math.max

internal data class BookshelfGroupFolder(
    val group: BookGroup,
    val books: List<Book>,
)

internal fun buildBookshelfGroupFolders(
    groups: List<BookGroup>,
    books: List<Book>,
    allCustomGroupMask: Long = groups.customGroupMask(),
): List<BookshelfGroupFolder> {
    return groups.asSequence()
        .filter { it.groupId != BookGroup.IdAll }
        .map { group ->
            val groupBooks = when (group.groupId) {
                BookGroup.IdRoot -> books.filter { book ->
                    book.isOnLineTxt && book.group and allCustomGroupMask == 0L
                }
                BookGroup.IdLocal -> books.filter { it.isLocal }
                BookGroup.IdAudio -> books.filter { it.isAudio }
                BookGroup.IdVideo -> books.filter { it.isVideo }
                else -> books.filter { it.group and group.groupId > 0L }
            }
            BookshelfGroupFolder(group, groupBooks.sortForGroup(group))
        }
        .filterNot { folder ->
            folder.group.groupId == BookGroup.IdRoot && folder.books.isEmpty()
        }
        .sortedBy { if (it.group.groupId == BookGroup.IdRoot) Int.MIN_VALUE else it.group.order }
        .toList()
}

internal fun List<BookGroup>.customGroupMask(): Long {
    return asSequence()
        .map { it.groupId }
        .filter { it > 0L }
        .fold(0L) { mask, groupId -> mask or groupId }
}

private fun List<Book>.sortForGroup(group: BookGroup): List<Book> {
    return when (group.getRealBookSort()) {
        1 -> sortedByDescending { it.latestChapterTime }
        2 -> sortedWith { first, second -> first.name.cnCompare(second.name) }
        3 -> sortedBy { it.order }
        4 -> sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
        5 -> sortedWith { first, second -> first.author.cnCompare(second.author) }
        else -> sortedByDescending { it.durChapterTime }
    }
}

@Composable
internal fun BookshelfGroupGrid(
    folders: List<BookshelfGroupFolder>,
    bottomInset: Dp,
    scrollToTopToken: Long,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
) {
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val openFolder = folders.firstOrNull { it.group.groupId == openFolderId }
    val state = rememberLazyGridState()
    val profile = AppConfig.getBookshelfLayoutProfile(BookshelfLayoutMode.GROUP_GRID)

    LaunchedEffect(openFolderId, openFolder) {
        if (openFolderId != null && openFolder == null) openFolderId = null
    }
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0L) state.animateScrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(profile.columns),
        state = state,
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 10.dp,
            end = 8.dp,
            bottom = bottomInset + 12.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(folders, key = { it.group.groupId }) { folder ->
            BookshelfGroupFolderItem(
                folder = folder,
                columns = profile.columns,
                onClick = { openFolderId = folder.group.groupId },
            )
        }
    }

    if (openFolder != null) {
        BookshelfGroupFolderDialog(
            folder = openFolder,
            onDismiss = { openFolderId = null },
            onOpenBook = { book ->
                openFolderId = null
                onOpenBook(book)
            },
            onOpenBookInfo = { book ->
                openFolderId = null
                onOpenBookInfo(book)
            },
        )
    }
}

@Composable
private fun BookshelfGroupFolderItem(
    folder: BookshelfGroupFolder,
    columns: Int,
    onClick: () -> Unit,
) {
    val normalizedColumns = columns.coerceIn(2, 4)
    val previewVariant = when (normalizedColumns) {
        2 -> NgCoverMosaicVariant.LARGE
        3 -> NgCoverMosaicVariant.MEDIUM
        else -> NgCoverMosaicVariant.COMPACT
    }
    val horizontalPadding = when (normalizedColumns) {
        2 -> 10.dp
        3 -> 6.dp
        else -> 3.dp
    }
    val verticalPadding = when (normalizedColumns) {
        2 -> 10.dp
        3 -> 8.dp
        else -> 6.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        BookshelfGroupFolderPreview(
            folder = folder,
            variant = previewVariant,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
        )
    }
}

@Composable
private fun BookshelfGroupFolderPreview(
    folder: BookshelfGroupFolder,
    variant: NgCoverMosaicVariant,
    modifier: Modifier = Modifier,
) {
    NgCoverMosaic(
        label = folder.group.groupName,
        itemCount = folder.books.size,
        modifier = modifier,
        variant = variant,
        presentationVariant = NgCoverMosaicPresentationVariant.FOLDER,
    ) {
        BookshelfGroupFolderCover(folder.books[it])
    }
}

@Composable
private fun BookshelfGroupFolderCover(book: Book) {
    NgBookCover(
        book = book,
        loadOnlyWifi = AppConfig.loadCoverOnlyWifi,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BookshelfGroupFolderDialog(
    folder: BookshelfGroupFolder,
    onDismiss: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val profile = AppConfig.getBookshelfLayoutProfile(BookshelfLayoutMode.GROUP_GRID)
    val dialogHeight = bookshelfFolderDialogHeight(
        bookCount = folder.books.size,
        columns = profile.innerColumns,
        showBookName = profile.showBookName,
        screenHeight = screenHeight,
    )

    NgVisualOverlayDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(dialogHeight),
    ) {
        Text(
            text = folder.group.groupName,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = 20.dp,
                top = 18.dp,
                end = 20.dp,
                bottom = 8.dp,
            ),
        )
        if (folder.books.isEmpty()) {
            BookshelfFolderEmptyState(
                modifier = Modifier.weight(1f),
            )
        } else {
            BookshelfFolderBookGrid(
                books = folder.books,
                columns = profile.innerColumns,
                showBookName = profile.showBookName,
                coverRadius = profile.coverRadius,
                spacing = profile.spacing,
                onOpenBook = onOpenBook,
                onOpenBookInfo = onOpenBookInfo,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun bookshelfFolderDialogHeight(
    bookCount: Int,
    columns: Int,
    showBookName: Int,
    screenHeight: Int,
): Dp {
    val contentHeight = when {
        bookCount == 0 -> 128
        else -> {
            val normalizedColumns = columns.coerceIn(2, 6)
            val rowCount = (bookCount + normalizedColumns - 1) / normalizedColumns
            rowCount * if (showBookName == 1) 132 else 158
        }
    }
    return (76 + contentHeight)
        .coerceAtLeast(204)
        .coerceAtMost((screenHeight * 0.74f).toInt())
        .dp
}

@Composable
private fun BookshelfFolderEmptyState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_book),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun BookshelfFolderBookGrid(
    books: List<Book>,
    columns: Int,
    showBookName: Int,
    coverRadius: Int,
    spacing: Int,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(
            horizontal = (spacing / 2f).dp,
            vertical = (spacing / 2f).dp,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(books, key = { it.bookUrl }) { book ->
            BookshelfFolderGridBookItem(
                book = book,
                showBookName = showBookName,
                coverRadius = coverRadius,
                spacing = spacing,
                onClick = { onOpenBook(book) },
                onLongClick = { onOpenBookInfo(book) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfFolderGridBookItem(
    book: Book,
    showBookName: Int,
    coverRadius: Int,
    spacing: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding((spacing / 2f).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(coverRadius.dp)),
        ) {
            BookshelfFolderBookCover(
                book = book,
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.54f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
        if (showBookName == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = book.name,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 30.dp),
            )
        }
    }
}

@Composable
private fun BookshelfFolderBookList(
    books: List<Book>,
    compact: Boolean,
    onOpenBook: (Book) -> Unit,
    onOpenBookInfo: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        lazyItems(books, key = { it.bookUrl }) { book ->
            BookshelfFolderListBookItem(
                book = book,
                compact = compact,
                onClick = { onOpenBook(book) },
                onLongClick = { onOpenBookInfo(book) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfFolderListBookItem(
    book: Book,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val rowHeight = if (compact) 64.dp else 82.dp
    val coverWidth = if (compact) 36.dp else 46.dp
    val coverHeight = if (compact) 50.dp else 64.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookshelfFolderBookCover(
            book = book,
            modifier = Modifier
                .width(coverWidth)
                .height(coverHeight)
                .clip(RoundedCornerShape(5.dp)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = book.name,
                color = Color(NgTheme.colors.onSurface),
                fontSize = if (compact) 14.sp else 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.bookshelfAuthorText(context),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && !book.durChapterTitle.isNullOrBlank()) {
                Text(
                    text = book.durChapterTitle.orEmpty(),
                    color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookshelfFolderBookCover(
    book: Book,
    modifier: Modifier = Modifier,
) {
    NgBookCover(
        book = book,
        loadOnlyWifi = AppConfig.loadCoverOnlyWifi,
        modifier = modifier,
    )
}
