package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme

private const val BOOKMARK_EXPORT_JSON = 1
private const val BOOKMARK_EXPORT_MARKDOWN = 2
private const val BOOKMARK_EXPAND_ALL = 3
private const val BOOKMARK_COLLAPSE_ALL = 4

sealed interface AllBookmarkScreenAction {
    data object Back : AllBookmarkScreenAction
    data object ExportJson : AllBookmarkScreenAction
    data object ExportMarkdown : AllBookmarkScreenAction
    data class QueryChanged(val query: String) : AllBookmarkScreenAction
    data class Open(val bookmark: Bookmark, val position: Int) : AllBookmarkScreenAction
    data class Edit(val bookmark: Bookmark, val position: Int) : AllBookmarkScreenAction
}

private sealed interface BookmarkTimelineItem {
    val stableKey: String

    data class Header(
        val book: BookmarkBookOption,
        val matchedCount: Int,
        val filtered: Boolean,
        val collapsed: Boolean,
        val firstGroup: Boolean,
    ) : BookmarkTimelineItem {
        override val stableKey: String = book.key
    }

    data class Entry(
        val bookmark: Bookmark,
        val position: Int,
        val firstInGroup: Boolean,
        val lastInGroup: Boolean,
    ) : BookmarkTimelineItem {
        override val stableKey: String = "bookmark:${bookmark.time}"
    }
}

@Composable
fun AllBookmarkScreen(
    bookmarks: List<Bookmark>,
    query: String,
    onAction: (AllBookmarkScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBookKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNoteFilter by rememberSaveable { mutableStateOf(BookmarkNoteFilter.ALL) }
    var selectedColor by rememberSaveable { mutableStateOf<Int?>(null) }
    val filter = remember(selectedBookKey, selectedNoteFilter, selectedColor) {
        AllBookmarkFilter(selectedBookKey, selectedNoteFilter, selectedColor)
    }
    val normalizedQuery = query.trim()
    val hasConditions = filter.isActive || normalizedQuery.isNotEmpty()
    var collapsedBookKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var filteredCollapsedBookKeys by rememberSaveable(
        selectedBookKey, selectedNoteFilter, selectedColor, normalizedQuery,
    ) { mutableStateOf(emptyList<String>()) }
    val collapsedKeys = if (hasConditions) filteredCollapsedBookKeys else collapsedBookKeys
    val collection = remember(bookmarks, normalizedQuery, filter) {
        buildAllBookmarkCollection(bookmarks, normalizedQuery, filter)
    }
    val timelineItems = remember(collection.groups, collapsedKeys, hasConditions) {
        buildBookmarkTimelineItems(collection.groups, collapsedKeys.toSet(), hasConditions)
    }
    val listState = rememberLazyListState()
    var previousConditions by remember { mutableStateOf(filter to normalizedQuery) }
    LaunchedEffect(filter, normalizedQuery) {
        val conditions = filter to normalizedQuery
        if (previousConditions != conditions) {
            previousConditions = conditions
            listState.scrollToItem(0)
        }
    }
    fun updateCollapsed(keys: List<String>) {
        if (hasConditions) filteredCollapsedBookKeys = keys else collapsedBookKeys = keys
    }
    fun updateFilter(value: AllBookmarkFilter) {
        selectedBookKey = value.bookKey
        selectedNoteFilter = value.noteFilter
        selectedColor = value.color
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        AllBookmarkFloatingToolbar(
            query = query,
            filter = filter,
            collection = collection,
            onFilterChanged = ::updateFilter,
            onExpandAll = { updateCollapsed(emptyList()) },
            onCollapseAll = { updateCollapsed(collection.groups.map { it.book.key }) },
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        NgVisualSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            role = NgMaterialRole.CONTENT,
            cornerRadius = NgTheme.shapes.mediumDp.dp,
            style = NgGlassDefaults.neutralStyle(),
        ) {
            if (timelineItems.isEmpty()) {
                AllBookmarkEmptyState(
                    searching = hasConditions,
                    onClearConditions = if (hasConditions) {
                        {
                            updateFilter(AllBookmarkFilter())
                            onAction(AllBookmarkScreenAction.QueryChanged(""))
                        }
                    } else null,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        top = 10.dp,
                        end = 14.dp,
                        bottom = 14.dp,
                    ),
                ) {
                    items(
                        items = timelineItems,
                        key = BookmarkTimelineItem::stableKey,
                        contentType = {
                            when (it) {
                                is BookmarkTimelineItem.Header -> "bookmark_group"
                                is BookmarkTimelineItem.Entry -> "bookmark_entry"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is BookmarkTimelineItem.Header -> BookmarkBookHeader(item) {
                                updateCollapsed(
                                    if (item.collapsed) collapsedKeys - item.book.key
                                    else collapsedKeys + item.book.key
                                )
                            }
                            is BookmarkTimelineItem.Entry -> BookmarkTimelineCard(
                                item = item,
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllBookmarkFloatingToolbar(
    query: String,
    filter: AllBookmarkFilter,
    collection: AllBookmarkCollection,
    onFilterChanged: (AllBookmarkFilter) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onAction: (AllBookmarkScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = remember { NgPopupToggleState() }
    val filterState = remember { NgPopupToggleState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                itemId = BOOKMARK_EXPAND_ALL,
                titleRes = R.string.all_bookmark_expand_all,
                iconRes = R.drawable.ic_expand_more,
            ),
            NgExpandableActionMenuItem(
                itemId = BOOKMARK_COLLAPSE_ALL,
                titleRes = R.string.all_bookmark_collapse_all,
                iconRes = R.drawable.ic_expand_less,
            ),
            NgExpandableActionMenuItem(
                itemId = BOOKMARK_EXPORT_JSON,
                titleRes = R.string.export,
                iconRes = R.drawable.ic_export,
                dividerBefore = true,
            ),
            NgExpandableActionMenuItem(
                itemId = BOOKMARK_EXPORT_MARKDOWN,
                titleRes = R.string.export_md,
                iconRes = R.drawable.ic_export,
            ),
        )
    }
    NgFloatingSearchToolbar(
        query = query,
        onQueryChange = { onAction(AllBookmarkScreenAction.QueryChanged(it)) },
        hint = stringResource(R.string.all_bookmark_search_hint),
        onBack = { onAction(AllBookmarkScreenAction.Back) },
        modifier = modifier,
    ) {
        Box {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_screen,
                contentDescription = stringResource(
                    if (filter.isActive) R.string.all_bookmark_filter_active
                    else R.string.all_bookmark_filter_title
                ),
                iconSize = 14.dp,
                tint = if (filter.isActive) Color(NgTheme.colors.primary) else null,
                onClick = {
                    menuState.close()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    filterState.onAnchorClick()
                },
            )
            if (filter.isActive) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(4.dp)
                        .background(Color(NgTheme.colors.primary), CircleShape)
                )
            }
            AllBookmarkFilterMenu(
                expanded = filterState.expanded,
                onDismissRequest = filterState::onDismissRequest,
                filter = filter,
                books = collection.books,
                colors = collection.colors,
                matchCount = collection.matchCount,
                onFilterChanged = onFilterChanged,
            )
        }
        Spacer(Modifier.width(2.dp))
        Box {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_grid_menu,
                contentDescription = stringResource(R.string.menu),
                onClick = {
                    filterState.close()
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    menuState.onAnchorClick()
                },
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                properties = PopupProperties(focusable = true, clippingEnabled = false),
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        BOOKMARK_EXPAND_ALL -> onExpandAll()
                        BOOKMARK_COLLAPSE_ALL -> onCollapseAll()
                        BOOKMARK_EXPORT_JSON -> onAction(AllBookmarkScreenAction.ExportJson)
                        BOOKMARK_EXPORT_MARKDOWN -> {
                            onAction(AllBookmarkScreenAction.ExportMarkdown)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun BookmarkBookHeader(item: BookmarkTimelineItem.Header, onToggle: () -> Unit) {
    val primary = Color(NgTheme.colors.primary)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val expandedDescription = stringResource(
        if (item.collapsed) R.string.all_bookmark_collapsed else R.string.all_bookmark_expanded
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { stateDescription = expandedDescription }
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(
                start = 2.dp,
                top = if (item.firstGroup) 4.dp else 12.dp,
                end = 2.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bookmarkGroupTitle(
                bookName = item.book.bookName,
                bookAuthor = item.book.bookAuthor,
                primary = primary,
                secondary = secondary,
            ),
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (item.filtered) {
                stringResource(R.string.all_bookmark_filtered_count, item.matchedCount, item.book.count)
            } else {
                stringResource(R.string.all_bookmark_count, item.book.count)
            },
            color = secondary.copy(alpha = 0.72f),
            fontSize = 11.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(
                if (item.collapsed) R.drawable.ic_arrow_right else R.drawable.ic_expand_more
            ),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = secondary.copy(alpha = 0.72f),
        )
    }
}

private fun bookmarkGroupTitle(
    bookName: String,
    bookAuthor: String,
    primary: Color,
    secondary: Color,
): AnnotatedString = buildAnnotatedString {
    pushStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold))
    append(bookName)
    pop()
    if (bookAuthor.isNotBlank()) {
        pushStyle(SpanStyle(color = secondary, fontWeight = FontWeight.Normal))
        append(" · ")
        append(bookAuthor)
        pop()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkTimelineCard(
    item: BookmarkTimelineItem.Entry,
    onAction: (AllBookmarkScreenAction) -> Unit,
) {
    val bookmark = item.bookmark
    val cardShape = RoundedCornerShape(10.dp)
    val contentColor = Color(NgTheme.colors.onSurface)
    val mutedColor = Color(NgTheme.colors.onSurfaceVariant)
    val accentColor = Color(NgTheme.colors.primary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        BookmarkTimelineRail(
            firstInGroup = item.firstInGroup,
            lastInGroup = item.lastInGroup,
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight(),
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .clip(cardShape)
                .combinedClickable(
                    onClick = {
                        onAction(AllBookmarkScreenAction.Open(bookmark, item.position))
                    },
                    onLongClickLabel = stringResource(R.string.edit),
                    onLongClick = {
                        onAction(AllBookmarkScreenAction.Edit(bookmark, item.position))
                    },
                ),
            shape = cardShape,
            color = colorResource(R.color.ng_surface_card),
            contentColor = contentColor,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 14.dp,
                    top = 11.dp,
                    end = 14.dp,
                    bottom = 10.dp,
                ),
            ) {
                Text(
                    text = bookmark.chapterName,
                    color = contentColor,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bookmark.bookText.isNotBlank()) {
                    Text(
                        text = bookmark.bookText.replace('\n', ' ').trim(),
                        modifier = Modifier.padding(top = 5.dp),
                        color = contentColor.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (bookmark.content.isNotBlank()) {
                    Text(
                        text = bookmark.content.replace('\n', ' ').trim(),
                        modifier = Modifier.padding(top = 5.dp),
                        color = accentColor.copy(alpha = 0.86f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.all_bookmark_position,
                        bookmark.chapterIndex.coerceAtLeast(0) + 1,
                        bookmark.chapterPos.coerceAtLeast(0),
                    ),
                    modifier = Modifier.padding(top = 5.dp),
                    color = mutedColor.copy(alpha = 0.72f),
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
private fun BookmarkTimelineRail(
    firstInGroup: Boolean,
    lastInGroup: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = Color(NgTheme.colors.primary)
    val nodeSurface = colorResource(R.color.ng_surface_card)
    Canvas(modifier = modifier) {
        val x = size.width / 2f
        val nodeY = 22.dp.toPx().coerceAtMost(size.height / 2f)
        drawLine(
            color = accent.copy(alpha = 0.74f),
            start = androidx.compose.ui.geometry.Offset(
                x,
                if (firstInGroup) nodeY else 0f,
            ),
            end = androidx.compose.ui.geometry.Offset(
                x,
                if (lastInGroup) nodeY else size.height,
            ),
            strokeWidth = 1.25.dp.toPx(),
        )
        drawCircle(
            color = nodeSurface,
            radius = 7.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x, nodeY),
        )
        drawCircle(
            color = accent,
            radius = 5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x, nodeY),
        )
        drawCircle(
            color = nodeSurface.copy(alpha = 0.70f),
            radius = 5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x, nodeY),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun AllBookmarkEmptyState(searching: Boolean, onClearConditions: (() -> Unit)?) {
    val mutedColor = Color(NgTheme.colors.onSurfaceVariant)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookmark),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = Color(NgTheme.colors.primary),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(
                if (searching) {
                    R.string.all_bookmark_filter_empty
                } else {
                    R.string.read_catalog_no_bookmarks
                }
            ),
            color = mutedColor,
            fontSize = 14.sp,
        )
        if (onClearConditions != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClearConditions) {
                Text(
                    text = stringResource(R.string.all_bookmark_filter_clear_conditions),
                    color = Color(NgTheme.colors.primary),
                )
            }
        }
    }
}

private fun buildBookmarkTimelineItems(
    groups: List<BookmarkTimelineGroup>,
    collapsedKeys: Set<String>,
    filtered: Boolean,
): List<BookmarkTimelineItem> {
    return buildList {
        groups.forEachIndexed { groupIndex, group ->
            val collapsed = group.book.key in collapsedKeys
            add(
                BookmarkTimelineItem.Header(
                    book = group.book,
                    matchedCount = group.matches.size,
                    filtered = filtered,
                    collapsed = collapsed,
                    firstGroup = groupIndex == 0,
                )
            )
            if (collapsed) return@forEachIndexed
            group.matches.forEachIndexed { itemIndex, indexedBookmark ->
                add(
                    BookmarkTimelineItem.Entry(
                        bookmark = indexedBookmark.value,
                        position = indexedBookmark.index,
                        firstInGroup = itemIndex == 0,
                        lastInGroup = itemIndex == group.matches.lastIndex,
                    )
                )
            }
        }
    }
}
