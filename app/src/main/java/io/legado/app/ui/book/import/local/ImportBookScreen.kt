package io.legado.app.ui.book.import.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFileEntryIcon
import io.legado.app.ui.design.components.compose.NgFileEntryIconKind
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.book.import.ImportBrowserPathRow
import io.legado.app.ui.book.import.ImportSelectionDock
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import java.util.Locale

internal data class ArchiveBookEntry(
    val entryName: String,
    val displayName: String,
    val isOnBookShelf: Boolean,
)

internal sealed interface ArchivePickerState {
    data object Hidden : ArchivePickerState

    data class Loading(val archive: FileDoc) : ArchivePickerState

    data class Ready(
        val archive: FileDoc,
        val entries: List<ArchiveBookEntry>,
        val selectedEntryNames: Set<String> = emptySet(),
        val importing: Boolean = false,
    ) : ArchivePickerState
}

@Composable
internal fun ImportBookScreen(
    items: List<ImportBook>,
    selectedItems: Set<ImportBook>,
    query: String,
    searchExpanded: Boolean,
    pathSegments: List<String>,
    isAtRoot: Boolean,
    isLoading: Boolean,
    sort: Int,
    archivePickerState: ArchivePickerState,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectFolder: () -> Unit,
    onGoUp: () -> Unit,
    onScanFolder: () -> Unit,
    onSortChange: (Int) -> Unit,
    onItemClick: (ImportBook) -> Unit,
    onToggleItem: (ImportBook) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onAddSelected: () -> Unit,
    onDismissArchive: () -> Unit,
    onArchiveEntryClick: (ArchiveBookEntry) -> Unit,
    onImportArchiveEntries: () -> Unit,
) {
    val selectedCount = selectedItems.size
    Column(modifier = Modifier.fillMaxSize()) {
        ImportBookTopBar(
            query = query,
            searchExpanded = searchExpanded,
            selectedCount = selectedCount,
            onBack = onBack,
            onSearchExpandedChange = onSearchExpandedChange,
            onQueryChange = onQueryChange,
            onSelectFolder = onSelectFolder,
            onDeleteSelected = onDeleteSelected,
        )
        ImportBookDirectoryPanel(
            items = items,
            selectedItems = selectedItems,
            pathSegments = pathSegments,
            showGoUp = !isAtRoot,
            isLoading = isLoading,
            sort = sort,
            onGoUp = onGoUp,
            onScanFolder = onScanFolder,
            onSortChange = onSortChange,
            onItemClick = onItemClick,
            onToggleItem = onToggleItem,
            selectedCount = selectedCount,
            selectableItemCount = items.count(ImportBook::isSelectableForImport),
            onSelectAll = onSelectAll,
            onInvertSelection = onInvertSelection,
            onAddSelected = onAddSelected,
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 4.dp, end = 10.dp, bottom = 10.dp),
        )
    }

    if (archivePickerState !is ArchivePickerState.Hidden) {
        ArchiveEntryPickerDialog(
            state = archivePickerState,
            onDismiss = onDismissArchive,
            onEntryClick = onArchiveEntryClick,
            onImport = onImportArchiveEntries,
        )
    }
}

@Composable
private fun ImportBookTopBar(
    query: String,
    searchExpanded: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectFolder: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cardColor = colorResource(R.color.ng_surface_card)
    val contentColor = Color(NgTheme.colors.onSurface)
    Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            style = NgGlassDefaults.bookDetailStyle(
                containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.back),
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (searchExpanded) {
                    NgSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        hint = stringResource(R.string.screen) + " · " +
                            stringResource(R.string.local_book),
                        modifier = Modifier.weight(1f),
                        variant = NgSearchBarVariant.TOOLBAR,
                        containerColor = Color.Transparent,
                        hideHintOnFocus = false,
                    )
                    IconButton(
                        onClick = { onSearchExpandedChange(false) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_close),
                            contentDescription = stringResource(R.string.close),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.add_local_book),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        color = contentColor,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onSearchExpandedChange(true) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                            tint = contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = onSelectFolder, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_folder_open),
                            contentDescription = stringResource(R.string.select_folder),
                            tint = contentColor,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    if (selectedCount > 0) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.menu),
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            NgExpandableActionMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                items = listOf(
                                    NgExpandableActionMenuItem(
                                        itemId = R.id.menu_del_selection,
                                        titleRes = R.string.delete,
                                        iconRes = R.drawable.ic_book_info_delete,
                                        danger = true,
                                    ),
                                ),
                                onItemClick = {
                                    menuExpanded = false
                                    onDeleteSelected()
                                },
                                width = 148.dp,
                                rowMinHeight = 40.dp,
                                menuContainerColor = cardColor,
                                offset = DpOffset(x = (-104).dp, y = 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportBookDirectoryPanel(
    items: List<ImportBook>,
    selectedItems: Set<ImportBook>,
    pathSegments: List<String>,
    showGoUp: Boolean,
    isLoading: Boolean,
    sort: Int,
    onGoUp: () -> Unit,
    onScanFolder: () -> Unit,
    onSortChange: (Int) -> Unit,
    onItemClick: (ImportBook) -> Unit,
    onToggleItem: (ImportBook) -> Unit,
    selectedCount: Int,
    selectableItemCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAddSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = colorResource(R.color.ng_surface_card)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        shadowElevation = NgTheme.effects.cardElevationDp.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ImportBrowserPathRow(
                pathText = pathSegments.joinToString("  ›  ").ifBlank {
                    stringResource(R.string.local_import_no_folder)
                },
                showGoUp = showGoUp,
                onGoUp = onGoUp,
            )
            HorizontalDivider(
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
            DirectoryUtilityRow(
                itemCount = items.size,
                sort = sort,
                onScanFolder = onScanFolder,
                onSortChange = onSortChange,
            )
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(NgTheme.colors.primary),
                    trackColor = Color(NgTheme.colors.surfaceContainerLow),
                )
            } else {
                HorizontalDivider(
                    color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
                )
            }
            if (items.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty_msg_import_book),
                        modifier = Modifier.padding(24.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.file.uri.toString() },
                    ) { index, item ->
                        ImportBookDirectoryRow(
                            item = item,
                            selected = item in selectedItems,
                            onClick = { onItemClick(item) },
                            onToggle = { onToggleItem(item) },
                        )
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 62.dp, end = 12.dp),
                                thickness = 0.6.dp,
                                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.18f),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
            ImportSelectionDock(
                selectedCount = selectedCount,
                itemCount = selectableItemCount,
                onSelectAll = onSelectAll,
                onInvertSelection = onInvertSelection,
                onAddSelected = onAddSelected,
            )
        }
    }
}

@Composable
private fun DirectoryUtilityRow(
    itemCount: Int,
    sort: Int,
    onScanFolder: () -> Unit,
    onSortChange: (Int) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val contentColor = Color(NgTheme.colors.onSurfaceVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.local_import_current_count, itemCount),
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 12.sp,
            maxLines = 1,
        )
        TextButton(
            onClick = onScanFolder,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scan),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.local_import_scan_children), fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(Color(NgTheme.colors.outlineVariant).copy(alpha = 0.28f)),
        )
        Box {
            TextButton(
                onClick = { sortExpanded = true },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_sort_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sort), fontSize = 12.sp)
            }
            NgExpandableActionMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
                items = listOf(
                    NgExpandableActionMenuItem(
                        itemId = R.id.menu_sort_name,
                        titleRes = R.string.sort_by_name,
                        iconRes = R.drawable.ic_baseline_sort_24,
                        checked = sort == 0,
                    ),
                    NgExpandableActionMenuItem(
                        itemId = R.id.menu_sort_size,
                        titleRes = R.string.sort_by_size,
                        iconRes = R.drawable.ic_storage_black_24dp,
                        checked = sort == 1,
                    ),
                    NgExpandableActionMenuItem(
                        itemId = R.id.menu_sort_time,
                        titleRes = R.string.sort_by_time,
                        iconRes = R.drawable.ic_history,
                        checked = sort == 2,
                    ),
                ),
                onItemClick = { item ->
                    sortExpanded = false
                    onSortChange(
                        when (item.itemId) {
                            R.id.menu_sort_size -> 1
                            R.id.menu_sort_time -> 2
                            else -> 0
                        },
                    )
                },
                width = 152.dp,
                rowMinHeight = 40.dp,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                offset = DpOffset(x = (-88).dp, y = 0.dp),
            )
        }
    }
}

@Composable
private fun ImportBookDirectoryRow(
    item: ImportBook,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedColor = Color(NgTheme.colors.primary).copy(alpha = 0.08f)
    val rowBackground = if (selected) selectedColor else Color.Transparent
    val primary = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(rowBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isSelectableForImport) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(48.dp),
            )
        } else {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                NgFileEntryIcon(
                    kind = when {
                        item.isDir -> NgFileEntryIconKind.DIRECTORY
                        item.isArchive -> NgFileEntryIconKind.ARCHIVE
                        else -> NgFileEntryIconKind.ON_BOOKSHELF
                    },
                    contentDescription = null,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = item.name,
                color = primary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.isDir) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(item.name.substringAfterLast('.', "").uppercase(Locale.getDefault()))
                        append(" · ")
                        append(ConvertUtils.formatFileSize(item.size))
                        append(" · ")
                        append(AppConst.dateFormat.format(item.lastModified))
                    },
                    color = secondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            item.isDir -> Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                tint = secondary.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
            )

            item.isArchive -> {
                NgStatusTag(
                    text = stringResource(R.string.local_import_archive),
                    variant = NgStatusTagVariant.WARNING,
                    style = NgStatusTagStyle.INLINE,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    tint = secondary.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }

            item.isOnBookShelf -> NgStatusTag(
                text = stringResource(R.string.local_import_on_bookshelf),
                variant = NgStatusTagVariant.SUCCESS,
                style = NgStatusTagStyle.INLINE,
            )

            else -> NgStatusTag(
                text = item.name.substringAfterLast('.', "").uppercase(Locale.getDefault()),
                variant = NgStatusTagVariant.INFO,
                style = NgStatusTagStyle.INLINE,
            )
        }
    }
}

@Composable
private fun ArchiveEntryPickerDialog(
    state: ArchivePickerState,
    onDismiss: () -> Unit,
    onEntryClick: (ArchiveBookEntry) -> Unit,
    onImport: () -> Unit,
) {
    val archive = when (state) {
        ArchivePickerState.Hidden -> return
        is ArchivePickerState.Loading -> state.archive
        is ArchivePickerState.Ready -> state.archive
    }
    val ready = state as? ArchivePickerState.Ready
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.local_import_archive_contents),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .heightIn(max = 620.dp),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(92.dp)
                        .height(42.dp),
                    enabled = ready?.importing != true,
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
                NgButton(
                    onClick = onImport,
                    modifier = Modifier
                        .width(116.dp)
                        .height(42.dp),
                    enabled = ready?.selectedEntryNames?.isNotEmpty() == true && !ready.importing,
                    variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                ) {
                    Text(
                        text = if (ready?.importing == true) {
                            stringResource(R.string.importing)
                        } else {
                            stringResource(R.string.nb_file_add_shelf)
                        },
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            },
        ) {
            Text(
                text = archive.name,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            when (state) {
                is ArchivePickerState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(NgTheme.colors.primary),
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 14.sp,
                        )
                    }
                }

                is ArchivePickerState.Ready -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 390.dp)
                            .clip(RoundedCornerShape(NgTheme.shapes.mediumDp.dp))
                            .background(colorResource(R.color.ng_surface_card)),
                    ) {
                        itemsIndexed(
                            items = state.entries,
                            key = { _, entry -> entry.entryName },
                        ) { index, entry ->
                            ArchiveEntryRow(
                                entry = entry,
                                selected = entry.entryName in state.selectedEntryNames,
                                enabled = !state.importing,
                                onClick = { onEntryClick(entry) },
                            )
                            if (index < state.entries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 48.dp),
                                    thickness = 0.6.dp,
                                    color = Color(NgTheme.colors.outlineVariant)
                                        .copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.local_import_archive_selected_count,
                            state.selectedEntryNames.size,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                    )
                }

                ArchivePickerState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveBookEntry,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(
                if (selected) Color(NgTheme.colors.primary).copy(alpha = 0.08f)
                else Color.Transparent,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.isOnBookShelf) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                NgFileEntryIcon(
                    kind = NgFileEntryIconKind.ON_BOOKSHELF,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        } else {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = { onClick() },
                enabled = enabled,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.displayName,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.isOnBookShelf) {
            NgStatusTag(
                text = stringResource(R.string.local_import_on_bookshelf),
                variant = NgStatusTagVariant.SUCCESS,
                style = NgStatusTagStyle.INLINE,
            )
        }
    }
}
