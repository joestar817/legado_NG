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
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.NgSurfaceVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgMaterialRole
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
    MANGA_DIALOG,
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
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var settingsActive by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val closeSearch = {
        searchActive = false
        query = ""
        onQueryChanged("")
    }

    BackHandler(enabled = searchActive && !settingsActive, onBack = closeSearch)
    BackHandler(enabled = settingsActive) { settingsActive = false }
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
                    onOpenSettings = { settingsActive = true },
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
                                key = { _, item -> "${item.origin}\u0000${item.bookUrl}" },
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
                        if (searchBooks.isEmpty() && settings.selectedGroupUnavailable) {
                            Text(
                                text = stringResource(R.string.change_source_no_matching_group_hint),
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                color = Color(NgTheme.colors.onSurfaceVariant),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                if (
                    presentation == ChangeBookSourcePresentation.DRAWER ||
                    presentation == ChangeBookSourcePresentation.READING_DIALOG ||
                    presentation == ChangeBookSourcePresentation.MANGA_DIALOG
                ) {
                    val baseSnapshot = NgTheme.snapshot
                    NgAppTheme(
                        snapshot = remember(baseSnapshot, presentation) {
                            if (
                                presentation == ChangeBookSourcePresentation.MANGA_DIALOG ||
                                (presentation != ChangeBookSourcePresentation.DIALOG && baseSnapshot.isDark)
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

        ChangeBookSourcePresentation.MANGA_DIALOG -> NgGlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(22.dp),
            style = NgGlassDefaults.mangaPanelStyle(),
            role = NgMaterialRole.MANGA_PANEL,
        ) { content() }

        ChangeBookSourcePresentation.DRAWER -> NgBottomDrawerSurface(
            modifier = Modifier.fillMaxSize(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
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
    onOpenSettings: () -> Unit,
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
        ChangeSourceMoreMenu(
            settings = settings,
            onOpenSourceManage = onOpenSourceManage,
            onRefreshList = onRefreshList,
            onOpenSettings = onOpenSettings,
            onGroupSelected = onGroupSelected,
            onClose = onClose,
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
        ChangeBookSourcePresentation.MANGA_DIALOG,
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


private const val SOURCE_ACTION_TOP = 0x62001001
private const val SOURCE_ACTION_BOTTOM = 0x62001002
private const val SOURCE_ACTION_EDIT = 0x62001003
private const val SOURCE_ACTION_DISABLE = 0x62001004
private const val SOURCE_ACTION_DELETE = 0x62001005
