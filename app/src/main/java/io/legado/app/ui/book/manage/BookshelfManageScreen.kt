package io.legado.app.ui.book.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
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
import io.legado.app.help.book.summarizeBookGroups
import io.legado.app.help.config.BookshelfCardAppearanceStore
import io.legado.app.model.localBook.canExtractOriginalCover
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.ngSlideSelect
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.launch
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
    cacheChapterTotals: Map<String, Int>,
    clearableBookUrls: Set<String>,
    cachedBytes: Map<String, Long>,
    sortOrder: BookshelfManageSort,
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
    onSortChange: (BookshelfManageSort) -> Unit,
    onSelectionChange: (Book, Boolean) -> Unit,
    onOpenDetails: (Book) -> Unit,
    onReorderStarted: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
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
    val isDark = NgTheme.snapshot.isDark
    val cardAppearance = remember(isDark) {
        BookshelfCardAppearanceStore.read().forNight(isDark)
    }
    val visibleUrls = remember(books) {
        books.mapTo(hashSetOf()) { it.bookUrl }
    }
    val selectedCount = selectedBookUrls.count(visibleUrls::contains)
    val eligibleCounts = remember(books, selectedBookUrls, clearableBookUrls) {
        val selected = books.filter { it.bookUrl in selectedBookUrls }
        BookshelfManageDockAction.entries.associateWith { action ->
            selected.count { action.isApplicable(it, it.bookUrl in clearableBookUrls) }
        }
    }
    val listState = rememberLazyListState()
    val sortScope = rememberCoroutineScope()
    var isReordering by remember { mutableStateOf(false) }
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
            groups = groups,
            selectedGroupId = selectedGroupId,
            sortOrder = sortOrder,
            onQueryChange = onQueryChange,
            onBack = onBack,
            onGroupSelected = onGroupSelected,
            onGroupManage = onGroupManage,
            onSortChange = { sort ->
                onSortChange(sort)
                sortScope.launch { listState.scrollToItem(0) }
            },
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
                    BookshelfManageBookCard(
                        book = book,
                        appearance = cardAppearance,
                        selected = selected,
                        groupNames = bookGroupNames(groups, book.group),
                        cachedChapterCount = cachedChapterCounts[book.bookUrl],
                        cacheChapterTotal = cacheChapterTotals[book.bookUrl],
                        cacheBytes = cachedBytes[book.bookUrl],
                        dragHandleEnabled = sortOrder.allowsDragging,
                        dragHandleModifier = if (books.size > 1 && sortOrder.allowsDragging) {
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
            eligibleCounts = eligibleCounts,
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

@Composable
private fun bookGroupNames(groups: List<BookGroup>, groupId: Long): String {
    val summary = remember(groups, groupId) { summarizeBookGroups(groups, groupId) }
    return if (summary.count > 1) {
        stringResource(R.string.bookshelf_group_count, summary.count)
    } else {
        summary.singleName.orEmpty()
    }
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
