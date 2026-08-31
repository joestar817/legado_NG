package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

sealed interface AllBookmarkScreenAction {
    data object Back : AllBookmarkScreenAction
    data object ExportJson : AllBookmarkScreenAction
    data object ExportMarkdown : AllBookmarkScreenAction
    data class QueryChanged(val query: String) : AllBookmarkScreenAction
    data class Open(val bookmark: Bookmark, val position: Int) : AllBookmarkScreenAction
    data class Edit(val bookmark: Bookmark, val position: Int) : AllBookmarkScreenAction
}

private data class BookmarkGroupKey(
    val bookName: String,
    val bookAuthor: String,
)

private sealed interface BookmarkTimelineItem {
    val stableKey: String

    data class Header(
        val bookName: String,
        val bookAuthor: String,
        val firstGroup: Boolean,
    ) : BookmarkTimelineItem {
        override val stableKey: String = "group:${bookName.length}:$bookName\u0000$bookAuthor"
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
    val timelineItems = remember(bookmarks, query) {
        val normalizedQuery = query.trim()
        buildBookmarkTimelineItems(
            if (normalizedQuery.isEmpty()) {
                bookmarks
            } else {
                bookmarks.filter { it.matchesBookmarkQuery(normalizedQuery) }
            }
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        AllBookmarkFloatingToolbar(
            query = query,
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
                AllBookmarkEmptyState(searching = query.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                            is BookmarkTimelineItem.Header -> BookmarkBookHeader(item)
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
    onAction: (AllBookmarkScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                itemId = BOOKMARK_EXPORT_JSON,
                titleRes = R.string.export,
                iconRes = R.drawable.ic_export,
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
                iconRes = R.drawable.ic_grid_menu,
                contentDescription = stringResource(R.string.menu),
                onClick = menuState::onAnchorClick,
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
private fun BookmarkBookHeader(item: BookmarkTimelineItem.Header) {
    val primary = Color(NgTheme.colors.primary)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    Text(
        text = bookmarkGroupTitle(
            bookName = item.bookName,
            bookAuthor = item.bookAuthor,
            primary = primary,
            secondary = secondary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 2.dp,
                top = if (item.firstGroup) 4.dp else 12.dp,
                end = 2.dp,
                bottom = 8.dp,
            ),
        fontSize = 16.sp,
        lineHeight = 21.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
private fun AllBookmarkEmptyState(searching: Boolean) {
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
                    R.string.all_bookmark_search_empty
                } else {
                    R.string.read_catalog_no_bookmarks
                }
            ),
            color = mutedColor,
            fontSize = 14.sp,
        )
    }
}

private fun Bookmark.matchesBookmarkQuery(query: String): Boolean {
    return bookName.contains(query, ignoreCase = true) ||
        bookAuthor.contains(query, ignoreCase = true) ||
        chapterName.contains(query, ignoreCase = true) ||
        bookText.contains(query, ignoreCase = true) ||
        content.contains(query, ignoreCase = true)
}

private fun buildBookmarkTimelineItems(
    bookmarks: List<Bookmark>,
): List<BookmarkTimelineItem> {
    if (bookmarks.isEmpty()) return emptyList()
    val grouped = linkedMapOf<BookmarkGroupKey, MutableList<IndexedValue<Bookmark>>>()
    bookmarks.withIndex().forEach { indexedBookmark ->
        val bookmark = indexedBookmark.value
        grouped.getOrPut(
            BookmarkGroupKey(bookmark.bookName, bookmark.bookAuthor)
        ) { mutableListOf() }.add(indexedBookmark)
    }
    return buildList(bookmarks.size + grouped.size) {
        grouped.entries.forEachIndexed { groupIndex, (group, groupBookmarks) ->
            add(
                BookmarkTimelineItem.Header(
                    bookName = group.bookName,
                    bookAuthor = group.bookAuthor,
                    firstGroup = groupIndex == 0,
                )
            )
            groupBookmarks.forEachIndexed { itemIndex, indexedBookmark ->
                add(
                    BookmarkTimelineItem.Entry(
                        bookmark = indexedBookmark.value,
                        position = indexedBookmark.index,
                        firstInGroup = itemIndex == 0,
                        lastInGroup = itemIndex == groupBookmarks.lastIndex,
                    )
                )
            }
        }
    }
}
