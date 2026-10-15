package io.legado.app.ui.book.changesource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.launch

internal data class ChangeChapterSourceSettingsUi(
    val checkAuthor: Boolean,
    val loadWordCount: Boolean,
    val loadInfo: Boolean,
    val loadToc: Boolean,
    val selectedGroup: String,
    val groups: List<String>,
    val sameTypeOnly: Boolean = true,
    val selectedGroupUnavailable: Boolean = false,
)

@Composable
internal fun ChangeChapterSourceDialogContent(
    title: String,
    currentSourceName: String,
    oldBookUrl: String?,
    searchBooks: List<SearchBook>,
    toc: List<BookChapter>,
    tocCurrentIndex: Int,
    showToc: Boolean,
    searching: Boolean,
    tocLoading: Boolean,
    query: String,
    settings: ChangeChapterSourceSettingsUi,
    scoreRevision: Int,
    getScore: (SearchBook) -> Int,
    onQueryChanged: (String) -> Unit,
    onRefreshToggle: () -> Unit,
    onClose: () -> Unit,
    onOpenSourceManage: () -> Unit,
    blockSourceDialogs: Boolean,
    onToggleSameTypeOnly: () -> Unit,
    onToggleBlockSourceDialogs: () -> Unit,
    onToggleCheckAuthor: () -> Unit,
    onToggleLoadWordCount: () -> Unit,
    onToggleLoadInfo: () -> Unit,
    onToggleLoadToc: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onSourceClick: (SearchBook) -> Unit,
    onSourceAction: (ChangeChapterSourceAction, SearchBook) -> Unit,
    onScoreChanged: (SearchBook, Int) -> Unit,
    onHideToc: () -> Unit,
    onChapterClick: (BookChapter, String?) -> Unit,
    onRefreshList: (() -> Unit)? = null,
) {
    scoreRevision
    val colors = NgTheme.colors
    val listState = rememberLazyListState()
    val tocListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var settingsActive by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showToc && !settingsActive, onBack = onHideToc)
    BackHandler(enabled = settingsActive) { settingsActive = false }
    LaunchedEffect(showToc, tocCurrentIndex, toc.size) {
        if (showToc && toc.isNotEmpty()) {
            val currentPosition = toc.indexOfFirst { it.index == tocCurrentIndex }
                .takeIf { it >= 0 }
                ?: 0
            tocListState.scrollToItem((currentPosition - 5).coerceAtLeast(0))
        }
    }
    NgGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(0.dp),
        style = NgGlassDefaults.style(containerAlpha = NgTheme.effects.dialogAlpha),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (settingsActive) {
                ChangeSourceSettingsContent(
                    settings = settings,
                    blockSourceDialogs = blockSourceDialogs,
                    onBack = { settingsActive = false },
                    onToggleSameTypeOnly = onToggleSameTypeOnly,
                    onToggleBlockSourceDialogs = onToggleBlockSourceDialogs,
                    onToggleCheckAuthor = onToggleCheckAuthor,
                    onToggleLoadWordCount = onToggleLoadWordCount,
                    onToggleLoadInfo = onToggleLoadInfo,
                    onToggleLoadToc = onToggleLoadToc,
                )
            } else {
                ChangeChapterSourceHeader(
                    title = title,
                    searching = searching,
                    settings = settings,
                    onRefreshToggle = onRefreshToggle,
                    onClose = onClose,
                    onOpenSourceManage = onOpenSourceManage,
                    onOpenSettings = { settingsActive = true },
                    onGroupSelected = onGroupSelected,
                    onRefreshList = onRefreshList,
                )
                NgSearchBar(
                    query = query,
                    onQueryChange = onQueryChanged,
                    hint = stringResource(R.string.screen),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    containerColor = Color(colors.inputContainer),
                )
                if (searching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(colors.primary),
                        trackColor = Color(colors.outline).copy(alpha = 0.32f),
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = searchBooks,
                            key = { _, item -> "${item.origin}\u0000${item.bookUrl}" },
                        ) { _, item ->
                            ChangeChapterSourceRow(
                                item = item,
                                current = item.bookUrl == oldBookUrl,
                                score = getScore(item),
                                showWordCount = AppConfig.changeSourceLoadWordCount,
                                onClick = { onSourceClick(item) },
                                onAction = { action -> onSourceAction(action, item) },
                                onScoreChanged = { score -> onScoreChanged(item, score) },
                            )
                        }
                    }
                    if (searchBooks.isEmpty() && settings.selectedGroupUnavailable) {
                        Text(
                            text = stringResource(R.string.change_source_no_matching_group_hint),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = Color(colors.onSurfaceVariant),
                            fontSize = 13.sp,
                        )
                    }
                    if (showToc) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(colors.dialogContainer)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clickable(role = Role.Button, onClick = onHideToc),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_down),
                                    contentDescription = stringResource(R.string.close),
                                    tint = Color(colors.onSurface),
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                LazyColumn(
                                    state = tocListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 6.dp),
                                ) {
                                    itemsIndexed(
                                        items = toc,
                                        key = { _, chapter -> "${chapter.index}:${chapter.url}" },
                                    ) { index, chapter ->
                                        ChangeChapterTocRow(
                                            chapter = chapter,
                                            current = chapter.index == tocCurrentIndex,
                                            onClick = {
                                                onChapterClick(chapter, toc.getOrNull(index + 1)?.url)
                                            },
                                        )
                                    }
                                }
                                if (tocLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(44.dp),
                                        color = Color(colors.primary),
                                        trackColor = Color(colors.outline).copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentSourceName,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Button) {
                                val index = searchBooks.indexOfFirst { it.bookUrl == oldBookUrl }
                                if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_drop_up),
                            contentDescription = stringResource(R.string.go_to_top),
                            tint = Color(colors.onSurface),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (searchBooks.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(searchBooks.lastIndex) }
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_drop_down),
                            contentDescription = stringResource(R.string.go_to_bottom),
                            tint = Color(colors.onSurface),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeChapterSourceHeader(
    title: String,
    searching: Boolean,
    settings: ChangeChapterSourceSettingsUi,
    onRefreshToggle: () -> Unit,
    onClose: () -> Unit,
    onOpenSourceManage: () -> Unit,
    onOpenSettings: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onRefreshList: (() -> Unit)?,
) {
    val colors = NgTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(colors.onSurface),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRefreshToggle) {
            Icon(
                painter = painterResource(
                    if (searching) R.drawable.ic_stop_black_24dp
                    else R.drawable.ic_refresh_black_24dp
                ),
                contentDescription = stringResource(if (searching) R.string.stop else R.string.refresh),
                tint = Color(colors.onSurface),
            )
        }
        ChangeSourceMoreMenu(
            settings = settings,
            onOpenSourceManage = onOpenSourceManage,
            onOpenSettings = onOpenSettings,
            onGroupSelected = onGroupSelected,
            onClose = onClose,
            onRefreshList = onRefreshList,
        )
    }
}

internal enum class ChangeChapterSourceAction {
    TOP,
    BOTTOM,
    EDIT,
    DISABLE,
    DELETE,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangeChapterSourceRow(
    item: SearchBook,
    current: Boolean,
    score: Int,
    showWordCount: Boolean,
    onClick: () -> Unit,
    onAction: (ChangeChapterSourceAction) -> Unit,
    onScoreChanged: (Int) -> Unit,
) {
    val colors = NgTheme.colors
    var menuExpanded by remember(item.bookUrl) { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(colors.inputContainer))
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onScoreChanged(if (score > 0) 0 else 1) },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_praise),
                        contentDescription = stringResource(R.string.like_source),
                        tint = Color(colors.error).copy(alpha = if (score > 0) 1f else 0.34f),
                    )
                }
                IconButton(
                    onClick = { onScoreChanged(if (score < 0) 0 else -1) },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_praise),
                        contentDescription = stringResource(R.string.not_like_source),
                        modifier = Modifier.rotate(180f),
                        tint = colorResource(R.color.ng_info)
                            .copy(alpha = if (score < 0) 1f else 0.34f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.originName,
                        modifier = Modifier.weight(1f),
                        color = Color(colors.onSurface),
                        fontSize = 15.sp,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.author,
                        modifier = Modifier.padding(start = 8.dp),
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(18.dp),
                            tint = Color(colors.primary),
                        )
                    }
                }
                Text(
                    text = item.getDisplayLastChapterTitle(),
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showWordCount) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.chapterWordCountText?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color(colors.onSurfaceVariant), fontSize = 11.sp)
                        }
                        if (item.respondTime >= 0) {
                            Text(
                                text = stringResource(R.string.respondTime, item.respondTime),
                                color = Color(colors.onSurfaceVariant),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = Color(colors.dialogContainer),
        ) {
            SourceActionMenuItem(R.string.to_top, ChangeChapterSourceAction.TOP) {
                menuExpanded = false
                onAction(it)
            }
            SourceActionMenuItem(R.string.to_bottom, ChangeChapterSourceAction.BOTTOM) {
                menuExpanded = false
                onAction(it)
            }
            SourceActionMenuItem(R.string.edit_source, ChangeChapterSourceAction.EDIT) {
                menuExpanded = false
                onAction(it)
            }
            SourceActionMenuItem(R.string.disable_source, ChangeChapterSourceAction.DISABLE) {
                menuExpanded = false
                onAction(it)
            }
            SourceActionMenuItem(R.string.delete_source, ChangeChapterSourceAction.DELETE) {
                menuExpanded = false
                onAction(it)
            }
        }
    }
}

@Composable
private fun SourceActionMenuItem(
    labelRes: Int,
    action: ChangeChapterSourceAction,
    onClick: (ChangeChapterSourceAction) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = { onClick(action) },
    )
}

@Composable
private fun ChangeChapterTocRow(
    chapter: BookChapter,
    current: Boolean,
    onClick: () -> Unit,
) {
    val colors = NgTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (chapter.isVolume) {
                    Color(colors.selectedContainer).copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapter.title,
            modifier = Modifier.weight(1f),
            color = Color(if (current) colors.primary else colors.onSurface),
            fontSize = 14.sp,
            fontWeight = if (current || chapter.isVolume) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        chapter.tag?.takeIf { it.isNotBlank() && !chapter.isVolume }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 10.dp),
                color = Color(colors.onSurfaceVariant),
                fontSize = 11.sp,
            )
        }
    }
}
