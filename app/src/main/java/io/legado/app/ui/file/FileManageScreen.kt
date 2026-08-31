package io.legado.app.ui.file

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFileEntryIcon
import io.legado.app.ui.design.components.compose.NgFileEntryIconKind
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.ConvertUtils
import java.util.Locale

internal const val FILE_MANAGE_SORT_NAME = 0
internal const val FILE_MANAGE_SORT_SIZE = 1
internal const val FILE_MANAGE_SORT_TIME = 2

@Composable
internal fun FileManageScreen(
    files: List<FileManageEntry>,
    query: String,
    searchExpanded: Boolean,
    pathSegments: List<String>,
    isLoading: Boolean,
    sortMode: Int,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onRoot: () -> Unit,
    onPathClick: (Int) -> Unit,
    onSortChange: (Int) -> Unit,
    onFileClick: (FileManageEntry) -> Unit,
    onDelete: (FileManageEntry) -> Unit,
) {
    val visibleFiles = remember(files, query, sortMode) {
        files.asSequence()
            .filter { query.isEmpty() || it.name.contains(query) }
            .sortedWith(fileManageComparator(sortMode))
            .toList()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        FileManageTopBar(
            query = query,
            searchExpanded = searchExpanded,
            onBack = onBack,
            onSearchExpandedChange = onSearchExpandedChange,
            onQueryChange = onQueryChange,
            onRoot = onRoot,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FileManagePanel(
            files = visibleFiles,
            pathSegments = pathSegments,
            isLoading = isLoading,
            sortMode = sortMode,
            onPathClick = onPathClick,
            onSortChange = onSortChange,
            onFileClick = onFileClick,
            onDelete = onDelete,
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun FileManageTopBar(
    query: String,
    searchExpanded: Boolean,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onRoot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searchExpanded) {
        NgFloatingSearchToolbar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.screen) + " · " +
                stringResource(R.string.file_manage),
            onBack = onBack,
            modifier = modifier,
        ) {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_baseline_close,
                contentDescription = stringResource(R.string.close),
                onClick = { onSearchExpandedChange(false) },
            )
        }
    } else {
        NgFloatingTitleToolbar(
            title = stringResource(R.string.file_manage),
            onBack = onBack,
            modifier = modifier,
        ) {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_search,
                contentDescription = stringResource(R.string.search),
                onClick = { onSearchExpandedChange(true) },
            )
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_folder_open,
                contentDescription = stringResource(R.string.file_manage_root),
                onClick = onRoot,
                iconSize = 22.dp,
            )
        }
    }
}

@Composable
private fun FileManagePanel(
    files: List<FileManageEntry>,
    pathSegments: List<String>,
    isLoading: Boolean,
    sortMode: Int,
    onPathClick: (Int) -> Unit,
    onSortChange: (Int) -> Unit,
    onFileClick: (FileManageEntry) -> Unit,
    onDelete: (FileManageEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        shadowElevation = NgTheme.effects.cardElevationDp.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FileManagePathRow(
                pathSegments = pathSegments,
                onPathClick = onPathClick,
            )
            HorizontalDivider(
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
            FileManageUtilityRow(
                itemCount = files.size,
                sortMode = sortMode,
                onSortChange = onSortChange,
            )
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(NgTheme.colors.primary),
                    trackColor = Color(NgTheme.colors.surfaceContainerLow),
                )
            } else {
                HorizontalDivider(
                    color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
                )
            }
            if (files.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    itemsIndexed(
                        items = files,
                        key = { _, entry -> entry.file.absolutePath },
                    ) { index, entry ->
                        FileManageRow(
                            entry = entry,
                            onClick = { onFileClick(entry) },
                            onDelete = { onDelete(entry) },
                        )
                        if (index < files.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 58.dp, end = 12.dp),
                                thickness = 0.6.dp,
                                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.18f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileManagePathRow(
    pathSegments: List<String>,
    onPathClick: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(pathSegments, scrollState.maxValue) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pathSegments.forEachIndexed { index, segment ->
            if (index > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.52f),
                    modifier = Modifier.size(17.dp),
                )
            }
            TextButton(
                onClick = { onPathClick(index) },
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = if (index == 0) 0.dp else 5.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (index == pathSegments.lastIndex) {
                        Color(NgTheme.colors.onSurface)
                    } else {
                        Color(NgTheme.colors.onSurfaceVariant)
                    },
                ),
            ) {
                Text(
                    text = segment,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (index == pathSegments.lastIndex) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FileManageUtilityRow(
    itemCount: Int,
    sortMode: Int,
    onSortChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
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
        Box {
            TextButton(
                onClick = { expanded = true },
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
                expanded = expanded,
                onDismissRequest = { expanded = false },
                items = listOf(
                    NgExpandableActionMenuItem(
                        itemId = FILE_MANAGE_SORT_NAME,
                        titleRes = R.string.sort_by_name,
                        iconRes = R.drawable.ic_baseline_sort_24,
                        checked = sortMode == FILE_MANAGE_SORT_NAME,
                    ),
                    NgExpandableActionMenuItem(
                        itemId = FILE_MANAGE_SORT_SIZE,
                        titleRes = R.string.sort_by_size,
                        iconRes = R.drawable.ic_storage_black_24dp,
                        checked = sortMode == FILE_MANAGE_SORT_SIZE,
                    ),
                    NgExpandableActionMenuItem(
                        itemId = FILE_MANAGE_SORT_TIME,
                        titleRes = R.string.sort_by_time,
                        iconRes = R.drawable.ic_history,
                        checked = sortMode == FILE_MANAGE_SORT_TIME,
                    ),
                ),
                width = 152.dp,
                rowMinHeight = 40.dp,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                offset = DpOffset(x = (-88).dp, y = 0.dp),
                onItemClick = { item ->
                    expanded = false
                    onSortChange(item.itemId)
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileManageRow(
    entry: FileManageEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(entry.file.absolutePath) { mutableStateOf(false) }
    val interactionSource = remember(entry.file.absolutePath) { MutableInteractionSource() }
    val primary = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.delete),
                onLongClick = { menuExpanded = true },
            )
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            NgFileEntryIcon(
                kind = if (entry.isDirectory) {
                    NgFileEntryIconKind.DIRECTORY
                } else {
                    NgFileEntryIconKind.FILE
                },
                contentDescription = null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = primary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDirectory) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildFileMetadata(entry),
                    color = secondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (entry.isDirectory) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    tint = secondary.copy(alpha = 0.72f),
                    modifier = Modifier.size(20.dp),
                )
            }
            NgExpandableActionMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                items = listOf(
                    NgExpandableActionMenuItem(
                        itemId = R.id.menu_del,
                        titleRes = R.string.delete,
                        iconRes = R.drawable.ic_book_info_delete,
                        danger = true,
                    ),
                ),
                width = 132.dp,
                rowMinHeight = 40.dp,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                offset = DpOffset(x = (-96).dp, y = 0.dp),
                onItemClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

private fun fileManageComparator(sortMode: Int): Comparator<FileManageEntry> {
    val directoryFirst = compareBy<FileManageEntry> { !it.isDirectory }
    return when (sortMode) {
        FILE_MANAGE_SORT_SIZE -> directoryFirst
            .thenByDescending(FileManageEntry::size)
            .thenBy { it.name.lowercase(Locale.getDefault()) }

        FILE_MANAGE_SORT_TIME -> directoryFirst
            .thenByDescending(FileManageEntry::lastModified)
            .thenBy { it.name.lowercase(Locale.getDefault()) }

        else -> directoryFirst.thenBy { it.name.lowercase(Locale.getDefault()) }
    }
}

private fun buildFileMetadata(entry: FileManageEntry): String = buildString {
    val extension = entry.extension.uppercase(Locale.getDefault())
    if (extension.isNotEmpty()) {
        append(extension)
        append(" · ")
    }
    append(ConvertUtils.formatFileSize(entry.size))
    append(" · ")
    append(AppConst.dateFormat.format(entry.lastModified))
}
