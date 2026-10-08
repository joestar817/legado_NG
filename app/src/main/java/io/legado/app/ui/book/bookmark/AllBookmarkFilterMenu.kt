package io.legado.app.ui.book.bookmark

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.roundToInt

/** The compound filter content shares NG menu styling without changing ordinary action menus. */
@Composable
internal fun AllBookmarkFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    filter: AllBookmarkFilter,
    books: List<BookmarkBookOption>,
    colors: List<Int>,
    matchCount: Int,
    onFilterChanged: (AllBookmarkFilter) -> Unit,
) {
    val slideFraction = remember { Animatable(if (expanded) 0f else 1f) }
    var popupVisible by remember { mutableStateOf(expanded) }
    var booksExpanded by remember { mutableStateOf(false) }
    val motion = NgTheme.snapshot.motion
    val durationMs = if (motion.enabled) motion.mediumDurationMs else 0
    LaunchedEffect(expanded, durationMs) {
        if (expanded) {
            popupVisible = true
            slideFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
            )
        } else if (popupVisible) {
            slideFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = FastOutLinearInEasing),
            )
            popupVisible = false
            booksExpanded = false
        }
    }
    if (!expanded && !popupVisible) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val width = (configuration.screenWidthDp.dp - 16.dp).coerceIn(1.dp, 256.dp)
    // Match the action menu's stable window envelope; only the inner card resizes.
    val maxHeight = (configuration.screenHeightDp.dp - 220.dp).coerceAtLeast(44.dp)
    val marginPx = with(density) { 8.dp.roundToPx() }
    val anchorOffsetPx = with(density) { 16.dp.roundToPx() }
    // Popup observes this stable provider's snapshot reads throughout the animation.
    val positionProvider = remember(marginPx, anchorOffsetPx) {
        BookmarkFilterPositionProvider(marginPx, anchorOffsetPx) { slideFraction.value }
    }
    val primary = Color(NgTheme.colors.primary)
    val outline = Color(NgTheme.colors.outlineVariant)
    val isEInk = NgTheme.snapshot.isEInk
    val selectedBook = remember(books, filter.bookKey) {
        books.firstOrNull { it.key == filter.bookKey }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        Column(modifier = Modifier.width(width).height(maxHeight)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
                color = colorResource(R.color.ng_surface_card),
                contentColor = Color(NgTheme.colors.onSurface),
                border = BorderStroke(
                    if (isEInk) 1.dp else 0.5.dp,
                    outline.copy(alpha = if (isEInk) 1f else 0.45f),
                ),
                tonalElevation = 0.dp,
                shadowElevation = NgTheme.effects.overlayElevationDp.dp,
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.all_bookmark_filter_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.all_bookmark_count, matchCount),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val expandedDescription = stringResource(
                        if (booksExpanded) R.string.all_bookmark_expanded
                        else R.string.all_bookmark_collapsed
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(NgTheme.colors.onSurface).copy(alpha = 0.035f))
                            .clickable(role = Role.Button) { booksExpanded = !booksExpanded }
                            .semantics { stateDescription = expandedDescription }
                            .heightIn(min = 36.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = when {
                                filter.bookKey == null -> stringResource(R.string.all_bookmark_filter_books)
                                selectedBook != null -> selectedBook.bookName
                                else -> stringResource(R.string.all_bookmark_filter_selected_book)
                            },
                            modifier = Modifier.weight(1f),
                            color = if (filter.bookKey != null) primary else Color(NgTheme.colors.onSurface),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_down),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).rotate(if (booksExpanded) 180f else 0f),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                        )
                    }
                    if (booksExpanded) {
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 224.dp.coerceAtMost(maxHeight / 2))
                                .border(0.5.dp, outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(3.dp)
                                .selectableGroup(),
                        ) {
                            item(key = "all-books", contentType = "book-option") {
                                BookmarkFilterBookOption(
                                    name = stringResource(R.string.all_bookmark_filter_books),
                                    author = null,
                                    selected = filter.bookKey == null,
                                    onClick = {
                                        onFilterChanged(filter.copy(bookKey = null))
                                        booksExpanded = false
                                    },
                                )
                            }
                            items(books, key = { "book:${it.key}" }, contentType = { "book-option" }) { book ->
                                BookmarkFilterBookOption(
                                    name = book.bookName,
                                    author = book.bookAuthor,
                                    selected = filter.bookKey == book.key,
                                    onClick = {
                                        onFilterChanged(filter.copy(bookKey = book.key))
                                        booksExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BookmarkNoteFilter.entries.forEach { noteFilter ->
                            val selected = filter.noteFilter == noteFilter
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) Color(NgTheme.colors.selectedContainer)
                                        else Color(NgTheme.colors.onSurface).copy(alpha = 0.035f)
                                    )
                                    .selectable(selected, role = Role.RadioButton) {
                                        onFilterChanged(filter.copy(noteFilter = noteFilter))
                                    }
                                    .heightIn(min = 36.dp)
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(
                                        when (noteFilter) {
                                            BookmarkNoteFilter.ALL -> R.string.all
                                            BookmarkNoteFilter.WITH_NOTE -> R.string.all_bookmark_filter_with_note
                                            BookmarkNoteFilter.WITHOUT_NOTE -> R.string.all_bookmark_filter_without_note
                                        }
                                    ),
                                    color = if (selected) primary else Color(NgTheme.colors.onSurface),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    BookmarkFilterColors(
                        colors = colors,
                        selectedColor = filter.color,
                        onSelect = { onFilterChanged(filter.copy(color = it)) },
                    )
                    if (filter.isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { onFilterChanged(AllBookmarkFilter()) }
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResource(R.string.all_bookmark_filter_clear),
                                color = primary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
        }
    }
}

@Composable
private fun BookmarkFilterBookOption(
    name: String,
    author: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(NgTheme.colors.primary).copy(alpha = 0.09f) else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!author.isNullOrBlank()) {
                Text(
                    text = author,
                    fontSize = 11.sp,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(NgTheme.colors.primary),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookmarkFilterColors(
    colors: List<Int>,
    selectedColor: Int?,
    onSelect: (Int?) -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val cardColor = colorResource(R.color.ng_surface_card)
    Column {
        Text(
            text = stringResource(R.string.all_bookmark_filter_color),
            modifier = Modifier.padding(horizontal = 4.dp),
            fontSize = 12.sp,
            color = Color(NgTheme.colors.onSurfaceVariant),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(
                        selected = selectedColor == null,
                        role = Role.RadioButton,
                        onClick = { onSelect(null) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.all),
                    fontSize = 12.sp,
                    color = if (selectedColor == null) primary else Color(NgTheme.colors.onSurface),
                    fontWeight = if (selectedColor == null) FontWeight.Medium else FontWeight.Normal,
                )
            }
            colors.forEach { argb ->
                val selected = selectedColor == argb
                val swatchColor = Color(argb)
                val description = stringResource(
                    R.string.all_bookmark_filter_color_value,
                    String.format(Locale.ROOT, "#%08X", argb),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(argb) },
                        )
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .then(if (selected) Modifier.border(1.5.dp, primary, CircleShape) else Modifier)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(0.5.dp, Color(NgTheme.colors.outlineVariant), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (swatchColor.compositeOver(cardColor).luminance() > 0.5f) {
                                    Color.Black
                                } else {
                                    Color.White
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keep the menu at the window's right edge, independent of the filter button's position. */
internal class BookmarkFilterPositionProvider(
    private val marginPx: Int,
    private val anchorOffsetPx: Int,
    private val horizontalSlideFraction: () -> Float = { 0f },
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val slideOffsetPx = (
            (popupContentSize.width + marginPx) * horizontalSlideFraction().coerceIn(0f, 1f)
            ).roundToInt()
        val x = (maxX - marginPx).coerceAtLeast(0) + slideOffsetPx
        val minY = marginPx.coerceAtMost(maxY)
        val safeMaxY = (maxY - marginPx).coerceAtLeast(minY)
        val y = (anchorBounds.bottom + anchorOffsetPx).coerceIn(minY, safeMaxY)
        return IntOffset(x, y)
    }
}
