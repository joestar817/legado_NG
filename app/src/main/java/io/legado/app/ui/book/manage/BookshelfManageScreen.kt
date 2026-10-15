package io.legado.app.ui.book.manage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.model.CacheBook
import io.legado.app.model.localBook.canExtractOriginalCover
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.ngSlideSelect
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.theme.NgTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun BookshelfManageScreen(
    query: String,
    groups: List<BookGroup>,
    selectedGroupId: Long,
    books: List<Book>,
    selectedBookUrls: Set<String>,
    cachedChapterCounts: Map<String, Int>,
    cacheState: BookshelfCacheState,
    cacheFilter: BookshelfCacheFilter,
    coverUpdateSelection: List<Book>?,
    deleteDialogVisible: Boolean,
    deleteOriginal: Boolean,
    batchChangeSourceRunning: Boolean,
    batchChangeSourceProgress: String,
    exportedSourceUri: String?,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onGroupSelected: (Long) -> Unit,
    onGroupManage: () -> Unit,
    onSelectionChange: (Book, Boolean) -> Unit,
    onOpenDetails: (Book) -> Unit,
    onReorderStarted: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCacheFilterSelected: (BookshelfCacheFilter) -> Unit,
    onCacheSheetOpen: () -> Unit,
    onCacheSheetDismiss: () -> Unit,
    cacheSheetVisible: Boolean,
    onCacheClearAll: () -> Unit,
    onCacheClearInvalid: () -> Unit,
    onCacheClearApp: () -> Unit,
    onCachePaceEnabledChanged: (Boolean) -> Unit,
    onCachePaceManualEnabledChanged: (Boolean) -> Unit,
    onCachePaceMinIntervalChanged: (Int) -> Unit,
    onCachePaceMaxIntervalChanged: (Int) -> Unit,
    onCachePaceIntervalChangeFinished: () -> Unit,
    onCachePaceLongRestChanged: (Boolean) -> Unit,
    onCachePaceQuietHoursChanged: (Boolean) -> Unit,
    onCachePause: () -> Unit,
    onCacheResume: () -> Unit,
    onCacheStop: () -> Unit,
    onCacheBookAction: (Book, CacheBook.BookState) -> Unit,
    onCacheBookMenu: (Book, BookshelfCacheMenuAction) -> Unit,
    onDockAction: (BookshelfManageDockAction) -> Unit,
    onDismissCoverUpdate: () -> Unit,
    onConfirmCoverUpdate: () -> Unit,
    onDeleteOriginalChange: (Boolean) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelBatchChangeSource: () -> Unit,
    onDismissExportSuccess: () -> Unit,
    onCopyExportPath: () -> Unit,
) {
    val visibleUrls = remember(books) {
        books.mapTo(hashSetOf()) { it.bookUrl }
    }
    val selectedCount = selectedBookUrls.count(visibleUrls::contains)
    val listState = rememberLazyListState()
    var isReordering by remember { mutableStateOf(false) }
    var cacheMenuBook by remember { mutableStateOf<Book?>(null) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }
    val slideSelectState = rememberNgLazySlideSelectState(
        listState = listState,
        isSelected = { index ->
            books.getOrNull(index)?.bookUrl?.let(selectedBookUrls::contains) == true
        },
        onSelectionChange = { index, selected ->
            books.getOrNull(index)?.let { book ->
                onSelectionChange(book, selected)
            }
        },
    )

    Column(modifier = Modifier.fillMaxSize()) {
        BookshelfManageTopBar(
            query = query,
            onQueryChange = onQueryChange,
            onBack = onBack,
        )
        BookshelfCacheStatusBar(
            state = cacheState,
            onOpenSheet = onCacheSheetOpen,
            onPauseClick = onCachePause,
            onResumeClick = onCacheResume,
            onStopClick = onCacheStop,
        )
        BookshelfCacheFilterRail(
            selected = cacheFilter,
            onSelected = onCacheFilterSelected,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 1.dp),
        )
        BookshelfGroupFilterRail(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onGroupSelected = onGroupSelected,
            onGroupManage = onGroupManage,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 3.dp),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .ngSlideSelect(
                    state = slideSelectState,
                    enabled = books.isNotEmpty() && !isReordering,
                ),
            state = listState,
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 4.dp,
                end = 14.dp,
                bottom = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = books,
                key = { it.bookUrl },
            ) { book ->
                ReorderableItem(
                    state = reorderState,
                    key = book.bookUrl,
                ) { _ ->
                    val selected = book.bookUrl in selectedBookUrls
                    val bookCacheState =
                        cacheState.bookStates[book.bookUrl] ?: CacheBook.BookState.NONE
                    // 全局暂停时,队列里的书一律显示成已暂停,点环即全局继续;
                    // 否则每本书都停在「暂停」图标上,点下去又毫无反应。
                    val ringState = if (
                        cacheState.paused && bookCacheState != CacheBook.BookState.NONE
                    ) {
                        CacheBook.BookState.PAUSED
                    } else {
                        bookCacheState
                    }
                    BookshelfManageBookCard(
                        book = book,
                        selected = selected,
                        selectionMode = selectedCount > 0,
                        // 有选中项时,未选中的卡整卡变淡,让选中项在长列表里一眼可见。
                        selectionDimmed = selectedCount > 0 && !selected,
                        groupNames = bookGroupNames(groups, book.group),
                        cachedChapterCount = cachedChapterCounts[book.bookUrl],
                        cacheSizeBytes = cacheState.sizeByBookUrl[book.bookUrl],
                        cacheState = ringState,
                        onCacheClick = { onCacheBookAction(book, ringState) },
                        onCacheLongPress = { cacheMenuBook = book },
                        dragHandleModifier = if (books.size > 1) {
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    isReordering = true
                                    onReorderStarted()
                                },
                                onDragStopped = {
                                    isReordering = false
                                    onReorderFinished()
                                },
                            )
                        } else {
                            Modifier
                        },
                        onToggleSelected = {
                            onSelectionChange(book, !selected)
                        },
                        onOpenDetails = { onOpenDetails(book) },
                    )
                }
            }
        }
        BookshelfManageBottomDock(
            selectedCount = selectedCount,
            totalCount = books.size,
            modifier = Modifier.padding(
                start = 14.dp,
                top = 8.dp,
                end = 14.dp,
                bottom = 8.dp,
            ),
            onSelectAll = onSelectAll,
            onInvertSelection = onInvertSelection,
            onAction = onDockAction,
        )
    }

    coverUpdateSelection?.let { selection ->
        val extractableCount = remember(selection) { selection.count(::canExtractOriginalCover) }
        BookshelfCoverExtractionDialog(
            extractableCount = extractableCount,
            skippedCount = selection.size - extractableCount,
            onDismiss = onDismissCoverUpdate,
            onConfirm = onConfirmCoverUpdate,
        )
    }
    if (deleteDialogVisible) {
        BookshelfDeleteDialog(
            deleteOriginal = deleteOriginal,
            onDeleteOriginalChange = onDeleteOriginalChange,
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete,
        )
    }
    if (batchChangeSourceRunning) {
        BookshelfBatchProgressDialog(
            progress = batchChangeSourceProgress,
            onCancel = onCancelBatchChangeSource,
        )
    }
    exportedSourceUri?.let { uri ->
        BookshelfExportSuccessDialog(
            uri = uri,
            onDismiss = onDismissExportSuccess,
            onCopy = onCopyExportPath,
        )
    }
    cacheMenuBook?.let { book ->
        val rawState = cacheState.bookStates[book.bookUrl] ?: CacheBook.BookState.NONE
        BookshelfCacheMenuDialog(
            bookName = book.name,
            state = if (cacheState.paused && rawState != CacheBook.BookState.NONE) {
                CacheBook.BookState.PAUSED
            } else {
                rawState
            },
            cachedChapterCount = cachedChapterCounts[book.bookUrl] ?: 0,
            totalChapterCount = book.totalChapterNum,
            onAction = { action ->
                cacheMenuBook = null
                onCacheBookMenu(book, action)
            },
            onDismiss = { cacheMenuBook = null },
        )
    }
    if (cacheSheetVisible) {
        BookshelfCacheSheet(
            state = cacheState,
            onDismiss = onCacheSheetDismiss,
            onClearAll = onCacheClearAll,
            onClearInvalid = onCacheClearInvalid,
            onClearAppCache = onCacheClearApp,
            onPaceEnabledChanged = onCachePaceEnabledChanged,
            onPaceManualEnabledChanged = onCachePaceManualEnabledChanged,
            onPaceMinIntervalChanged = onCachePaceMinIntervalChanged,
            onPaceMaxIntervalChanged = onCachePaceMaxIntervalChanged,
            onPaceIntervalChangeFinished = onCachePaceIntervalChangeFinished,
            onPaceLongRestChanged = onCachePaceLongRestChanged,
            onPaceQuietHoursChanged = onCachePaceQuietHoursChanged,
            onPauseAll = onCachePause,
            onStopAll = onCacheStop,
        )
    }
}

/**
 * 长按书卡环形按钮弹出的单本书操作。
 *
 * 环形按钮的单击只做最常用的一步,完整动作在这里,免得为低频操作挤占卡片。
 */
@Composable
private fun BookshelfCacheMenuDialog(
    bookName: String,
    state: CacheBook.BookState,
    cachedChapterCount: Int,
    totalChapterCount: Int,
    onAction: (BookshelfCacheMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = bookName,
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    secondary = true,
                )
            },
        ) {
            Text(
                text = stringResource(
                    R.string.cache_ring_cached,
                    cachedChapterCount,
                    totalChapterCount,
                ),
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(6.dp))
            // 只列出当前状态下真正可执行的动作,免得「继续」出现在没暂停的书上。
            if (state == CacheBook.BookState.NONE) {
                NgCacheMenuRow(
                    text = stringResource(R.string.cache_menu_re_cache),
                    iconRes = R.drawable.ic_refresh_black_24dp,
                    onClick = { onAction(BookshelfCacheMenuAction.CACHE) },
                )
            }
            if (state == CacheBook.BookState.RUNNING || state == CacheBook.BookState.WAITING) {
                NgCacheMenuRow(
                    text = stringResource(R.string.pause),
                    iconRes = R.drawable.ic_pause_24dp,
                    onClick = { onAction(BookshelfCacheMenuAction.PAUSE) },
                )
            }
            if (state == CacheBook.BookState.PAUSED) {
                NgCacheMenuRow(
                    text = stringResource(R.string.resume),
                    iconRes = R.drawable.ic_play_24dp,
                    onClick = { onAction(BookshelfCacheMenuAction.RESUME) },
                )
            }
            if (state != CacheBook.BookState.NONE) {
                NgCacheMenuRow(
                    text = stringResource(R.string.stop),
                    iconRes = R.drawable.ic_stop_black_24dp,
                    onClick = { onAction(BookshelfCacheMenuAction.STOP) },
                )
            }
            NgCacheMenuRow(
                text = stringResource(R.string.cache_menu_clear_book),
                iconRes = R.drawable.ic_outline_delete,
                onClick = { onAction(BookshelfCacheMenuAction.CLEAR) },
                danger = true,
            )
        }
    }
}

@Composable
private fun NgCacheMenuRow(
    text: String,
    onClick: () -> Unit,
    @DrawableRes iconRes: Int? = null,
    danger: Boolean = false,
) {
    val color = Color(
        if (danger) NgTheme.colors.error else NgTheme.colors.onSurface
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            // 每个动作配一个圆底图标:纯文字的四行动作读起来像设置项,不像操作。
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.54f),
        )
    }
}

@Composable
private fun BookshelfCoverExtractionDialog(
    extractableCount: Int,
    skippedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.update_book_cover),
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 520.dp),
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier.width(80.dp).height(36.dp),
                    variant = NgButtonVariant.OUTLINE,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp, lineHeight = 20.sp)
                }
                NgButton(
                    onClick = onConfirm,
                    enabled = extractableCount > 0,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.extract_book_cover_confirm), fontSize = 14.sp, lineHeight = 20.sp)
                }
            },
        ) {
            Column(
                modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.extract_book_cover_description),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Text(
                    text = stringResource(R.string.extract_book_cover_selection, extractableCount, skippedCount),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun bookGroupNames(groups: List<BookGroup>, groupId: Long): String {
    return groups
        .filter { it.groupId > 0 && (it.groupId and groupId) > 0 }
        .joinToString(",") { it.groupName }
}

@Composable
private fun BookshelfDeleteDialog(
    deleteOriginal: Boolean,
    onDeleteOriginalChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.draw),
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(92.dp)
                        .height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 15.sp)
                }
                NgButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .width(92.dp)
                        .height(42.dp),
                    variant = NgButtonVariant.DANGER,
                ) {
                    Text(stringResource(R.string.delete), fontSize = 15.sp)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.sure_del),
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Checkbox,
                        onClick = { onDeleteOriginalChange(!deleteOriginal) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = deleteOriginal,
                    onCheckedChange = onDeleteOriginalChange,
                )
                Text(
                    text = stringResource(R.string.delete_book_file),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun BookshelfBatchProgressDialog(
    progress: String,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(
            color = colorResource(R.color.ng_surface_card),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                NgTheme.shapes.largeDp.dp,
            ),
            shadowElevation = NgTheme.effects.overlayElevationDp.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = Color(NgTheme.colors.primary),
                    strokeWidth = 3.dp,
                )
                Text(
                    text = progress.ifBlank {
                        stringResource(R.string.change_source_batch)
                    },
                    modifier = Modifier.padding(start = 12.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookshelfExportSuccessDialog(
    uri: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.export_success),
            actions = {
                NgButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .width(92.dp)
                        .height(42.dp),
                ) {
                    Text(stringResource(R.string.ok), fontSize = 15.sp)
                }
            },
        ) {
            SelectionContainer {
                Text(
                    text = uri,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
