package io.legado.app.ui.book.import.remote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import io.legado.app.model.remote.RemoteBook
import io.legado.app.ui.book.import.ImportBrowserPathRow
import io.legado.app.ui.book.import.ImportSelectionDock
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
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
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ConvertUtils
import java.util.Locale

@Composable
internal fun RemoteBookScreen(
    items: List<RemoteBook>,
    selectedItems: Set<RemoteBook>,
    query: String,
    searchExpanded: Boolean,
    pathText: String,
    isAtRoot: Boolean,
    isLoading: Boolean,
    sortKey: RemoteBookSort,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onGoUp: () -> Unit,
    onSortChange: (RemoteBookSort) -> Unit,
    onMenuAction: (Int) -> Unit,
    onItemClick: (RemoteBook) -> Unit,
    onItemLongClick: (RemoteBook) -> Unit,
    onToggleItem: (RemoteBook) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAddSelected: () -> Unit,
) {
    val selectedCount = selectedItems.size
    val selectableItemCount = items.count { !it.isDir && !it.isOnBookShelf }
    Column(modifier = Modifier.fillMaxSize()) {
        RemoteBookTopBar(
            query = query,
            searchExpanded = searchExpanded,
            onBack = onBack,
            onSearchExpandedChange = onSearchExpandedChange,
            onQueryChange = onQueryChange,
            onMenuAction = onMenuAction,
        )
        RemoteBookDirectoryPanel(
            items = items,
            selectedItems = selectedItems,
            pathText = pathText,
            showGoUp = !isAtRoot,
            isLoading = isLoading,
            sortKey = sortKey,
            onGoUp = onGoUp,
            onRefresh = onRefresh,
            onSortChange = onSortChange,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onToggleItem = onToggleItem,
            selectedCount = selectedCount,
            selectableItemCount = selectableItemCount,
            onSelectAll = onSelectAll,
            onInvertSelection = onInvertSelection,
            onAddSelected = onAddSelected,
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 4.dp, end = 10.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun RemoteBookTopBar(
    query: String,
    searchExpanded: Boolean,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onMenuAction: (Int) -> Unit,
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
                            stringResource(R.string.remote_book),
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
                        text = stringResource(R.string.add_remote_book),
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
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_grid_menu),
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
                                    itemId = R.id.menu_server_config,
                                    titleRes = R.string.server_config,
                                    iconRes = R.drawable.ic_settings,
                                ),
                                NgExpandableActionMenuItem(
                                    itemId = R.id.menu_help,
                                    titleRes = R.string.help,
                                    iconRes = R.drawable.ic_help,
                                ),
                                NgExpandableActionMenuItem(
                                    itemId = R.id.menu_log,
                                    titleRes = R.string.log,
                                    iconRes = R.drawable.ic_bug_report,
                                    dividerBefore = true,
                                ),
                                NgExpandableActionMenuItem(
                                    itemId = R.id.menu_network_log,
                                    titleRes = R.string.network_request_log,
                                    iconRes = R.drawable.ic_web_outline,
                                ),
                            ),
                            onItemClick = { item ->
                                menuExpanded = false
                                onMenuAction(item.itemId)
                            },
                            width = 188.dp,
                            rowMinHeight = 40.dp,
                            menuContainerColor = cardColor,
                            offset = DpOffset(x = (-144).dp, y = 0.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteBookDirectoryPanel(
    items: List<RemoteBook>,
    selectedItems: Set<RemoteBook>,
    pathText: String,
    showGoUp: Boolean,
    isLoading: Boolean,
    sortKey: RemoteBookSort,
    onGoUp: () -> Unit,
    onRefresh: () -> Unit,
    onSortChange: (RemoteBookSort) -> Unit,
    onItemClick: (RemoteBook) -> Unit,
    onItemLongClick: (RemoteBook) -> Unit,
    onToggleItem: (RemoteBook) -> Unit,
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
                pathText = pathText,
                showGoUp = showGoUp,
                onGoUp = onGoUp,
            )
            HorizontalDivider(
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
            RemoteDirectoryUtilityRow(
                itemCount = items.size,
                sortKey = sortKey,
                onRefresh = onRefresh,
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
                        text = stringResource(R.string.empty),
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
                        key = { _, item -> item.path },
                    ) { index, item ->
                        RemoteBookDirectoryRow(
                            item = item,
                            selected = item in selectedItems,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
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
private fun RemoteDirectoryUtilityRow(
    itemCount: Int,
    sortKey: RemoteBookSort,
    onRefresh: () -> Unit,
    onSortChange: (RemoteBookSort) -> Unit,
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
            onClick = onRefresh,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh_black_24dp),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.refresh), fontSize = 12.sp)
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
                        checked = sortKey == RemoteBookSort.Name,
                    ),
                    NgExpandableActionMenuItem(
                        itemId = R.id.menu_sort_time,
                        titleRes = R.string.sort_by_lastUpdateTime,
                        iconRes = R.drawable.ic_history,
                        checked = sortKey == RemoteBookSort.Default,
                    ),
                ),
                onItemClick = { item ->
                    sortExpanded = false
                    onSortChange(
                        if (item.itemId == R.id.menu_sort_name) {
                            RemoteBookSort.Name
                        } else {
                            RemoteBookSort.Default
                        },
                    )
                },
                width = 168.dp,
                rowMinHeight = 40.dp,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                offset = DpOffset(x = (-104).dp, y = 0.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteBookDirectoryRow(
    item: RemoteBook,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedColor = Color(NgTheme.colors.primary).copy(alpha = 0.08f)
    val rowBackground = if (selected) selectedColor else Color.Transparent
    val primary = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val isArchive = ArchiveUtils.isArchive(item.filename)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(rowBackground)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!item.isDir && !item.isOnBookShelf) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(48.dp),
            )
        } else {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                NgFileEntryIcon(
                    kind = if (item.isDir) {
                        NgFileEntryIconKind.DIRECTORY
                    } else {
                        NgFileEntryIconKind.ON_BOOKSHELF
                    },
                    contentDescription = null,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = item.filename,
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
                        append(item.contentType.uppercase(Locale.getDefault()))
                        append(" · ")
                        append(ConvertUtils.formatFileSize(item.size))
                        append(" · ")
                        append(AppConst.dateFormat.format(item.lastModify))
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

            item.isOnBookShelf -> NgStatusTag(
                text = stringResource(R.string.local_import_on_bookshelf),
                variant = NgStatusTagVariant.SUCCESS,
                style = NgStatusTagStyle.INLINE,
            )

            isArchive -> NgStatusTag(
                text = stringResource(R.string.local_import_archive),
                variant = NgStatusTagVariant.WARNING,
                style = NgStatusTagStyle.INLINE,
            )

            else -> NgStatusTag(
                text = item.contentType.uppercase(Locale.getDefault()),
                variant = NgStatusTagVariant.INFO,
                style = NgStatusTagStyle.INLINE,
            )
        }
    }
}
