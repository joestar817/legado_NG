package io.legado.app.ui.book.changesource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.NgSurfaceVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import kotlinx.coroutines.launch

internal data class ChangeBookSourceProgressUi(
    val text: String,
    val current: Int,
    val total: Int,
)

enum class ChangeBookSourcePresentation {
    DIALOG,
    READING_DIALOG,
    DRAWER,
}

@Composable
internal fun ChangeBookSourceDialogContent(
    presentation: ChangeBookSourcePresentation = ChangeBookSourcePresentation.DIALOG,
    currentBookUrl: String?,
    searchBooks: List<SearchBook>,
    searching: Boolean,
    progress: ChangeBookSourceProgressUi,
    blockSourceDialogs: Boolean,
    settings: ChangeChapterSourceSettingsUi,
    getScore: (SearchBook) -> Int,
    onClose: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onRefreshToggle: () -> Unit,
    onOpenSourceManage: () -> Unit,
    onRefreshList: () -> Unit,
    onToggleBlockSourceDialogs: () -> Unit,
    onToggleCheckAuthor: () -> Unit,
    onToggleLoadWordCount: () -> Unit,
    onToggleLoadInfo: () -> Unit,
    onToggleLoadToc: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onSourceClick: (SearchBook) -> Unit,
    onSourceAction: (ChangeChapterSourceAction, SearchBook) -> Unit,
    onScoreChanged: (SearchBook, Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val closeSearch = {
        searchActive = false
        query = ""
        onQueryChanged("")
    }

    BackHandler(enabled = searchActive, onBack = closeSearch)
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }
    LaunchedEffect(searchBooks.firstOrNull()?.bookUrl) {
        if (searchBooks.isNotEmpty()) listState.scrollToItem(0)
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = if (presentation == ChangeBookSourcePresentation.DRAWER) {
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            if (presentation == ChangeBookSourcePresentation.DRAWER) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
            }
            ChangeBookSourceTopBar(
                searching = searching,
                query = query,
                searchActive = searchActive,
                searchFocusRequester = searchFocusRequester,
                settings = settings,
                onBack = onClose,
                onSearchOpen = { searchActive = true },
                onSearchClose = closeSearch,
                onQueryChanged = {
                    query = it
                    onQueryChanged(it)
                },
                onRefreshToggle = onRefreshToggle,
                onOpenSourceManage = onOpenSourceManage,
                onRefreshList = onRefreshList,
                blockSourceDialogs = blockSourceDialogs,
                onToggleBlockSourceDialogs = onToggleBlockSourceDialogs,
                onToggleCheckAuthor = onToggleCheckAuthor,
                onToggleLoadWordCount = onToggleLoadWordCount,
                onToggleLoadInfo = onToggleLoadInfo,
                onToggleLoadToc = onToggleLoadToc,
                onGroupSelected = onGroupSelected,
                onClose = onClose,
            )
            if (searching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(NgTheme.colors.primary),
                    trackColor = Color.Transparent,
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
            val resultsContent: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Transparent),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp),
                    ) {
                        itemsIndexed(
                            items = searchBooks,
                            key = { _, item -> item.bookUrl },
                        ) { index, item ->
                            ChangeBookSourceResultRow(
                                presentation = presentation,
                                item = item,
                                current = item.bookUrl == currentBookUrl,
                                score = getScore(item),
                                showWordCount = settings.loadWordCount,
                                onClick = { onSourceClick(item) },
                                onAction = { action -> onSourceAction(action, item) },
                                onScoreChanged = { score -> onScoreChanged(item, score) },
                            )
                            if (
                                presentation == ChangeBookSourcePresentation.DIALOG &&
                                index != searchBooks.lastIndex
                            ) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = Color(NgTheme.colors.outlineVariant),
                                )
                            }
                        }
                    }
                    NgLazyListFastScroller(
                        state = listState,
                        itemCount = searchBooks.size,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
            if (
                presentation == ChangeBookSourcePresentation.DRAWER ||
                presentation == ChangeBookSourcePresentation.READING_DIALOG
            ) {
                val baseSnapshot = NgTheme.snapshot
                NgAppTheme(
                    snapshot = remember(baseSnapshot, presentation) {
                        if (
                            presentation != ChangeBookSourcePresentation.DIALOG &&
                            baseSnapshot.isDark
                        ) {
                            baseSnapshot
                        } else {
                            baseSnapshot.toChangeBookSourceListSnapshot()
                        }
                    },
                    updateSystemBars = false,
                    content = resultsContent,
                )
            } else {
                resultsContent()
            }
            ChangeBookSourceProgress(
                presentation = presentation,
                progress = progress,
                onClick = {
                    val index = searchBooks.indexOfFirst { it.bookUrl == currentBookUrl }
                    if (index >= 0) {
                        scope.launch {
                            listState.scrollToItem(index)
                            listState.scrollBy(-with(density) { 60.dp.toPx() })
                        }
                    }
                },
            )
        }
    }
    when (presentation) {
        ChangeBookSourcePresentation.DIALOG -> Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(22.dp),
            color = Color(NgTheme.colors.surface),
            content = content,
        )

        ChangeBookSourcePresentation.READING_DIALOG -> NgGlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(22.dp),
            style = readFloatingGlassStyle(),
        ) { content() }

        ChangeBookSourcePresentation.DRAWER -> NgBottomDrawerSurface(
            modifier = Modifier.fillMaxSize(),
        ) { content() }
    }
}

@Composable
private fun ChangeBookSourceTopBar(
    searching: Boolean,
    query: String,
    searchActive: Boolean,
    searchFocusRequester: FocusRequester,
    settings: ChangeChapterSourceSettingsUi,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onRefreshToggle: () -> Unit,
    onOpenSourceManage: () -> Unit,
    onRefreshList: () -> Unit,
    blockSourceDialogs: Boolean,
    onToggleBlockSourceDialogs: () -> Unit,
    onToggleCheckAuthor: () -> Unit,
    onToggleLoadWordCount: () -> Unit,
    onToggleLoadInfo: () -> Unit,
    onToggleLoadToc: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive) {
            IconButton(
                onClick = onSearchClose,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            NgSearchBar(
                query = query,
                onQueryChange = onQueryChanged,
                hint = stringResource(R.string.screen),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocusRequester),
                variant = NgSearchBarVariant.TOOLBAR,
                containerColor = Color.Transparent,
                hideHintOnFocus = false,
            )
            return@Row
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onSearchOpen,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.search),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onRefreshToggle,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (searching) Icons.Rounded.Stop else Icons.Rounded.Refresh,
                contentDescription = stringResource(
                    if (searching) R.string.stop else R.string.refresh
                ),
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        ChangeBookSourceMoreMenu(
            settings = settings,
            onOpenSourceManage = onOpenSourceManage,
            onRefreshList = onRefreshList,
            blockSourceDialogs = blockSourceDialogs,
            onToggleBlockSourceDialogs = onToggleBlockSourceDialogs,
            onToggleCheckAuthor = onToggleCheckAuthor,
            onToggleLoadWordCount = onToggleLoadWordCount,
            onToggleLoadInfo = onToggleLoadInfo,
            onToggleLoadToc = onToggleLoadToc,
            onGroupSelected = onGroupSelected,
            onClose = onClose,
        )
    }
}

@Composable
private fun ChangeBookSourceMoreMenu(
    settings: ChangeChapterSourceSettingsUi,
    onOpenSourceManage: () -> Unit,
    onRefreshList: () -> Unit,
    blockSourceDialogs: Boolean,
    onToggleBlockSourceDialogs: () -> Unit,
    onToggleCheckAuthor: () -> Unit,
    onToggleLoadWordCount: () -> Unit,
    onToggleLoadInfo: () -> Unit,
    onToggleLoadToc: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val groupTitle = if (settings.selectedGroup.isBlank()) {
        stringResource(R.string.group)
    } else {
        "${stringResource(R.string.group)}(${settings.selectedGroup})"
    }
    val allSourceTitle = stringResource(R.string.all_source)
    val menuItems = listOf(
        NgExpandableActionMenuItem(
            itemId = MENU_SOURCE_MANAGE,
            titleRes = R.string.book_source_manage,
            iconRes = R.drawable.ic_cfg_source,
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_REFRESH_LIST,
            titleRes = R.string.refresh_list,
            iconRes = R.drawable.ic_refresh_black_24dp,
        ),
        NgExpandableActionMenuItem(
            itemId = R.id.menu_block_source_dialogs,
            titleRes = R.string.block_source_dialogs,
            iconRes = R.drawable.ic_popup_blocked,
            checked = blockSourceDialogs,
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_CHECK_AUTHOR,
            titleRes = R.string.checkAuthor,
            iconRes = R.drawable.ic_author,
            checked = settings.checkAuthor,
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_LOAD_WORD_COUNT,
            titleRes = R.string.load_word_count,
            iconRes = R.drawable.ic_chapter_list,
            checked = settings.loadWordCount,
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_LOAD_INFO,
            titleRes = R.string.load_info,
            iconRes = R.drawable.ic_bookshelf_action_detail,
            checked = settings.loadInfo,
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_LOAD_TOC,
            titleRes = R.string.load_toc,
            iconRes = R.drawable.ic_toc,
            checked = settings.loadToc,
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_GROUP,
            titleRes = 0,
            title = groupTitle,
            iconRes = R.drawable.ic_groups,
            children = buildList {
                add(
                    NgExpandableActionMenuItem(
                        itemId = MENU_GROUP_ALL,
                        titleRes = 0,
                        title = allSourceTitle,
                        iconRes = 0,
                        checked = settings.selectedGroup.isBlank(),
                    )
                )
                settings.groups.forEachIndexed { index, group ->
                    add(
                        NgExpandableActionMenuItem(
                            itemId = MENU_GROUP_BASE + index,
                            titleRes = 0,
                            title = group,
                            iconRes = 0,
                            checked = settings.selectedGroup == group,
                        )
                    )
                }
            },
        ),
        NgExpandableActionMenuItem(
            itemId = MENU_CLOSE,
            titleRes = R.string.close,
            iconRes = R.drawable.ic_baseline_close,
        ),
    )

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(24.dp),
            )
        }
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = menuItems,
            onItemClick = { item ->
                expanded = false
                when (item.itemId) {
                    MENU_SOURCE_MANAGE -> onOpenSourceManage()
                    MENU_REFRESH_LIST -> onRefreshList()
                    R.id.menu_block_source_dialogs -> onToggleBlockSourceDialogs()
                    MENU_CHECK_AUTHOR -> onToggleCheckAuthor()
                    MENU_LOAD_WORD_COUNT -> onToggleLoadWordCount()
                    MENU_LOAD_INFO -> onToggleLoadInfo()
                    MENU_LOAD_TOC -> onToggleLoadToc()
                    MENU_GROUP_ALL -> onGroupSelected("")
                    MENU_CLOSE -> onClose()
                    else -> {
                        val index = item.itemId - MENU_GROUP_BASE
                        settings.groups.getOrNull(index)?.let(onGroupSelected)
                    }
                }
            },
            width = 152.dp,
            rowMinHeight = 44.dp,
            menuContainerColor = Color(NgTheme.colors.cardContainer).copy(alpha = 1f),
            variant = NgExpandableActionMenuVariant.DRILL_IN,
            properties = PopupProperties(
                focusable = true,
                clippingEnabled = false,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangeBookSourceResultRow(
    presentation: ChangeBookSourcePresentation,
    item: SearchBook,
    current: Boolean,
    score: Int,
    showWordCount: Boolean,
    onClick: () -> Unit,
    onAction: (ChangeChapterSourceAction) -> Unit,
    onScoreChanged: (Int) -> Unit,
) {
    var actionMenuExpanded by remember(item.bookUrl) { mutableStateOf(false) }
    var displayedScore by remember(item.bookUrl) { mutableIntStateOf(score) }
    LaunchedEffect(score) { displayedScore = score }
    val colors = NgTheme.colors
    val primaryText = Color(colors.onSurface)
    val secondaryText = Color(colors.onSurfaceVariant)
    val primary = Color(colors.primary)

    val content: @Composable () -> Unit = {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .background(
                        if (current) primary.copy(alpha = 0.04f) else Color.Transparent
                    )
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { if (!current) onClick() },
                        onLongClick = { actionMenuExpanded = true },
                    )
                    .padding(start = 30.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.originName,
                            modifier = Modifier.weight(1f),
                            color = primaryText,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.author,
                            modifier = Modifier.widthIn(max = 160.dp),
                            color = secondaryText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (current) {
                            Spacer(Modifier.width(10.dp))
                            val currentSourceDescription = stringResource(
                                R.string.change_source_current
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = primary,
                                        shape = CircleShape,
                                    )
                                    .semantics {
                                        contentDescription = currentSourceDescription
                                    },
                            )
                        }
                    }
                    Text(
                        text = item.getDisplayLastChapterTitle(),
                        color = secondaryText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showWordCount && !item.chapterWordCountText.isNullOrBlank()) {
                        Text(
                            text = item.chapterWordCountText.orEmpty(),
                            color = secondaryText,
                            fontSize = 14.sp,
                        )
                    }
                    if (showWordCount && item.respondTime >= 0) {
                        Text(
                            text = stringResource(R.string.respondTime, item.respondTime),
                            color = secondaryText,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Box(Modifier.matchParentSize()) {
                Column(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (displayedScore >= 0) {
                        IconButton(
                            onClick = {
                                displayedScore = if (displayedScore > 0) 0 else 1
                                onScoreChanged(displayedScore)
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_praise),
                                contentDescription = stringResource(R.string.like_source),
                                tint = colorResource(
                                    if (displayedScore > 0) R.color.md_red_A200
                                    else R.color.md_red_100
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (displayedScore <= 0) {
                        IconButton(
                            onClick = {
                                displayedScore = if (displayedScore < 0) 0 else -1
                                onScoreChanged(displayedScore)
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_praise),
                                contentDescription = stringResource(R.string.not_like_source),
                                tint = colorResource(
                                    if (displayedScore < 0) R.color.md_blue_A200
                                    else R.color.md_blue_100
                                ),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationX = 180f },
                            )
                        }
                    }
                }
            }
            NgExpandableActionMenu(
                expanded = actionMenuExpanded,
                onDismissRequest = { actionMenuExpanded = false },
                items = changeBookSourceActionMenuItems(),
                onItemClick = { item ->
                    actionMenuExpanded = false
                    when (item.itemId) {
                        SOURCE_ACTION_TOP -> onAction(ChangeChapterSourceAction.TOP)
                        SOURCE_ACTION_BOTTOM -> onAction(ChangeChapterSourceAction.BOTTOM)
                        SOURCE_ACTION_EDIT -> onAction(ChangeChapterSourceAction.EDIT)
                        SOURCE_ACTION_DISABLE -> onAction(ChangeChapterSourceAction.DISABLE)
                        SOURCE_ACTION_DELETE -> onAction(ChangeChapterSourceAction.DELETE)
                    }
                },
                width = 144.dp,
                menuContainerColor = Color(NgTheme.colors.cardContainer).copy(alpha = 1f),
            )
        }
    }
    if (presentation != ChangeBookSourcePresentation.DIALOG) {
        NgSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            variant = NgSurfaceVariant.BORDERLESS_CARD,
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun changeBookSourceActionMenuItems(): List<NgExpandableActionMenuItem> = listOf(
    NgExpandableActionMenuItem(
        SOURCE_ACTION_TOP,
        R.string.to_top,
        R.drawable.ic_arrow_drop_up,
    ),
    NgExpandableActionMenuItem(
        SOURCE_ACTION_BOTTOM,
        R.string.to_bottom,
        R.drawable.ic_arrow_down,
    ),
    NgExpandableActionMenuItem(
        SOURCE_ACTION_EDIT,
        R.string.edit_source,
        R.drawable.ic_edit,
    ),
    NgExpandableActionMenuItem(
        SOURCE_ACTION_DISABLE,
        R.string.disable_source,
        R.drawable.ic_baseline_close,
        dividerBefore = true,
    ),
    NgExpandableActionMenuItem(
        SOURCE_ACTION_DELETE,
        R.string.delete_source,
        R.drawable.ic_outline_delete,
    ),
)

@Composable
private fun ChangeBookSourceProgress(
    presentation: ChangeBookSourcePresentation,
    progress: ChangeBookSourceProgressUi,
    onClick: () -> Unit,
) {
    val fraction = if (progress.total > 0) {
        (progress.current.toFloat() / progress.total).coerceIn(0f, 1f)
    } else {
        0f
    }
    when (presentation) {
        ChangeBookSourcePresentation.DIALOG -> ChangeBookSourceDialogProgress(
            text = progress.text,
            fraction = fraction,
            onClick = onClick,
        )

        ChangeBookSourcePresentation.READING_DIALOG,
        ChangeBookSourcePresentation.DRAWER -> ChangeBookSourceDrawerProgress(
            text = progress.text,
            fraction = fraction,
            onClick = onClick,
        )
    }
}

@Composable
private fun ChangeBookSourceDialogProgress(
    text: String,
    fraction: Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val colors = NgTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .height(30.dp)
            .background(Color(colors.cardContainer), shape)
            .border(0.8.dp, Color(colors.outlineVariant), shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Color(NgTheme.colors.primary).copy(alpha = 96f / 255f),
                        shape,
                    ),
            )
        }
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp),
            color = Color(colors.onSurfaceVariant),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChangeBookSourceDrawerProgress(
    text: String,
    fraction: Float,
    onClick: () -> Unit,
) {
    val colors = NgTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            thickness = 0.5.dp,
            color = Color(colors.outlineVariant).copy(alpha = 0.42f),
        )
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp),
            color = Color(colors.onSurfaceVariant),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(colors.outlineVariant).copy(alpha = 0.28f)),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(Color(colors.primary)),
                )
            }
        }
    }
}

private fun NgThemeSnapshot.toChangeBookSourceListSnapshot(): NgThemeSnapshot = copy(
    isDark = false,
    colors = colors.copy(
        background = CHANGE_SOURCE_LIST_SURFACE,
        onBackground = CHANGE_SOURCE_LIST_CONTENT,
        surface = CHANGE_SOURCE_LIST_SURFACE,
        onSurface = CHANGE_SOURCE_LIST_CONTENT,
        surfaceVariant = CHANGE_SOURCE_LIST_SURFACE_VARIANT,
        onSurfaceVariant = CHANGE_SOURCE_LIST_SECONDARY_CONTENT,
        surfaceContainerLow = CHANGE_SOURCE_LIST_SURFACE,
        surfaceContainer = CHANGE_SOURCE_LIST_SURFACE,
        surfaceContainerHigh = CHANGE_SOURCE_LIST_SURFACE,
        outline = CHANGE_SOURCE_LIST_OUTLINE,
        outlineVariant = CHANGE_SOURCE_LIST_DIVIDER,
        cardContainer = CHANGE_SOURCE_LIST_SURFACE,
        dialogContainer = CHANGE_SOURCE_LIST_SURFACE,
        inputContainer = CHANGE_SOURCE_LIST_SURFACE,
    ),
)

private val CHANGE_SOURCE_LIST_SURFACE = 0xFFFFFFFF.toInt()
private val CHANGE_SOURCE_LIST_SURFACE_VARIANT = 0xFFF7F7F7.toInt()
private val CHANGE_SOURCE_LIST_CONTENT = 0xFF1D1B20.toInt()
private val CHANGE_SOURCE_LIST_SECONDARY_CONTENT = 0xFF6E6B70.toInt()
private val CHANGE_SOURCE_LIST_OUTLINE = 0xFF79747E.toInt()
private const val CHANGE_SOURCE_LIST_DIVIDER = 0x1F000000

private const val MENU_SOURCE_MANAGE = 0x62000001
private const val MENU_REFRESH_LIST = 0x62000002
private const val MENU_CHECK_AUTHOR = 0x62000003
private const val MENU_LOAD_WORD_COUNT = 0x62000004
private const val MENU_LOAD_INFO = 0x62000005
private const val MENU_LOAD_TOC = 0x62000006
private const val MENU_GROUP = 0x62000007
private const val MENU_GROUP_ALL = 0x62000008
private const val MENU_CLOSE = 0x62000009
private const val MENU_GROUP_BASE = 0x62000100

private const val SOURCE_ACTION_TOP = 0x62001001
private const val SOURCE_ACTION_BOTTOM = 0x62001002
private const val SOURCE_ACTION_EDIT = 0x62001003
private const val SOURCE_ACTION_DISABLE = 0x62001004
private const val SOURCE_ACTION_DELETE = 0x62001005
