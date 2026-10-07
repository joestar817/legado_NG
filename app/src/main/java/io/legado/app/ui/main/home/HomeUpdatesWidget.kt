package io.legado.app.ui.main.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The home host owns the surface and dated header; every skin shares this real-data body. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeUpdatesWidget(
    variant: HomeWidgetVariant,
    state: HomeUpdatesState,
    bodyMinimumHeight: Dp,
    interactive: Boolean,
    measuring: Boolean,
    onOpenBook: (HomeUpdateBook) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    if (measuring) {
        HomeUpdatesContent(variant, state, bodyMinimumHeight, false, true, null, null,
            onOpenBook, onRetry, onEdit)
    } else key(state.today, state.books.isEmpty()) {
        // Stable across style changes. A new local day starts a new viewport at the first book.
        val pager = rememberPagerState(pageCount = { homeUpdatesPageCount(state.books.size).coerceAtLeast(1) })
        val list = rememberLazyListState()
        LaunchedEffect(state.books.size) {
            val page = homeUpdatesClampedPage(pager.currentPage, state.books.size)
            if (pager.currentPage != page) pager.scrollToPage(page)
            if (state.books.isNotEmpty() && list.firstVisibleItemIndex >= state.books.size) {
                list.scrollToItem(state.books.lastIndex)
            }
        }
        HomeUpdatesContent(variant, state, bodyMinimumHeight, interactive, false, pager, list,
            onOpenBook, onRetry, onEdit)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeUpdatesContent(
    variant: HomeWidgetVariant,
    state: HomeUpdatesState,
    bodyMinimumHeight: Dp,
    interactive: Boolean,
    measuring: Boolean,
    pager: PagerState?,
    list: LazyListState?,
    onOpenBook: (HomeUpdateBook) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
) {
    val small = variant.size == HomeWidgetSize.SMALL
    val palette = homeWidgetSkinPalette(variant.styleId)
    val foreground = palette?.foreground ?: Color(NgTheme.colors.onSurface)
    val secondary = palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant)
    val primary = palette?.primary ?: Color(NgTheme.colors.primary)
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().heightIn(min = bodyMinimumHeight)
            .padding(top = 8.dp, bottom = if (small) 8.dp else 4.dp)) {
            if (state.books.isNotEmpty()) {
                if (small) HomeUpdatesSmallBooks(state.books, pager, foreground, secondary, primary,
                    interactive, measuring, onOpenBook, onEdit)
                else HomeUpdatesLargeBooks(state.books, list, foreground, primary,
                    interactive, measuring, onOpenBook, onEdit)
            } else if (!state.loadFailed) {
                Text(stringResource(if (state.loaded) R.string.home_updates_empty else R.string.home_updates_loading),
                    modifier = Modifier.padding(horizontal = if (small) 12.dp else 14.dp), color = secondary,
                    fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (state.loadFailed) {
                Box(Modifier.fillMaxWidth().padding(horizontal = if (small) 12.dp else 14.dp)
                    .heightIn(min = 48.dp).then(if (interactive)
                        Modifier.clickable(enabled = !state.checking, role = Role.Button, onClick = onRetry) else Modifier),
                    contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.home_updates_retry), color = palette?.error ?: Color(NgTheme.colors.error),
                        fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            HomeUpdatesCheckFooter(state, small, primary, secondary, interactive, measuring, onRetry)
        }
    }
    if (variant.styleId == "night" && !small) {
        val density = LocalDensity.current
        val itemWidth = 64.dp * density.fontScale.coerceAtLeast(1f)
        val count = state.books.size
        val occupiedBooksWidth = if (count == 0) 0.dp else itemWidth * count + 8.dp * (count - 1) + 28.dp
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bodyWidth = maxWidth
            val availableWidth = (bodyWidth - 14.dp - occupiedBooksWidth - 8.dp).coerceAtLeast(0.dp)
            if (!measuring && !state.loadFailed && availableWidth >= 24.dp) {
                val topSafe = if (count == 0) {
                    val label = stringResource(if (state.loaded) R.string.home_updates_empty else R.string.home_updates_loading)
                    val style = LocalTextStyle.current.copy(fontSize = 12.sp, lineHeight = 17.sp)
                    val measurer = rememberTextMeasurer()
                    val textWidth = with(density) { (bodyWidth - 28.dp).roundToPx().coerceAtLeast(1) }
                    val textHeight = remember(label, style, measurer, density, textWidth) {
                        with(density) {
                            measurer.measure(label, style, constraints = Constraints.fixedWidth(textWidth)).size.height.toDp()
                        }
                    }
                    maxOf(30.dp, 8.dp + textHeight + 4.dp)
                } else 0.dp
                val lamp = homeWidgetArtworkPainter(R.drawable.ng_home_calendar_night_lamp)
                Canvas(Modifier.matchParentSize().clipToBounds().clearAndSetSemantics {}) {
                    val edge = minOf(100.dp.toPx(), availableWidth.toPx(),
                        (size.height - topSafe.toPx() - 4.dp.toPx()).coerceAtLeast(0f))
                    if (edge >= 24.dp.toPx()) {
                        // Align the visible lamp base, excluding the resource's transparent bottom.
                        translate(size.width - 14.dp.toPx() - edge,
                            size.height - 2.dp.toPx() - edge * (1054f / 1254f)) {
                            with(lamp) { draw(Size(edge, edge)) }
                        }
                    }
                }
            }
            body()
        }
    } else body()
}

/** Network state is supplied by the home owner; this row only renders it and requests refresh. */
@Composable
private fun HomeUpdatesCheckFooter(
    state: HomeUpdatesState,
    small: Boolean,
    primary: Color,
    secondary: Color,
    interactive: Boolean,
    measuring: Boolean,
    onRefresh: () -> Unit,
) {
    val checkedTime = homeUpdatesCheckedTime(state)
    val checkingLabel = stringResource(R.string.home_updates_checking)
    val status = checkedTime ?: if (state.checking) checkingLabel
        else stringResource(R.string.home_updates_not_checked)
    val fontSize = if (small) 10.sp else 11.sp
    val lineHeight = if (small) 14.sp else 16.sp
    if (!small) {
        // The status now lives in the header. Retain its previous measured space so
        // moving it does not change the card height or shift the book row.
        val textStyle = LocalTextStyle.current.copy(fontSize = fontSize, lineHeight = lineHeight)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 4.dp)) {
            val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
            val height = remember(status, textStyle, measurer, density, widthPx) {
                with(density) {
                    measurer.measure(status, textStyle, constraints = Constraints.fixedWidth(widthPx),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis).size.height.toDp()
                }
            }
            Spacer(Modifier.height(height))
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = if (small) 12.dp else 14.dp)
        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        val description = checkedTime?.let { stringResource(R.string.home_updates_last_checked, it) }
            ?: status
        Text(status, Modifier.weight(1f).padding(end = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
            color = if (checkedTime == null && state.checking) primary else secondary,
            fontSize = fontSize, lineHeight = lineHeight,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        HomeUpdatesRefreshButton(state, primary, interactive, onRefresh, measuring)
    }
}

@Composable
internal fun homeUpdatesCheckedTime(state: HomeUpdatesState): String? {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val zone = ZoneId.systemDefault()
    return remember(state.lastCheckedAt, state.today, locale, zone) {
        state.lastCheckedAt?.let { formatHomeUpdatesCheckTime(it, state.today, zone, locale) }
    }
}

/** Large cards place the same 48dp action in their header; small cards keep their footer action. */
@Composable
internal fun HomeUpdatesRefreshButton(
    state: HomeUpdatesState,
    primary: Color,
    interactive: Boolean,
    onRefresh: () -> Unit,
    measuring: Boolean = false,
    headerDiscStyleId: String? = null,
) {
    val label = stringResource(if (state.checking) R.string.home_updates_checking else R.string.home_updates_refresh)
    val icon: @Composable () -> Unit = {
        Icon(Icons.Outlined.Refresh, label, modifier = Modifier.size(20.dp), tint = primary)
    }
    val glyph: @Composable () -> Unit = {
        if (state.checking && interactive) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(14.dp).semantics { contentDescription = label },
                    color = primary, strokeWidth = 1.6.dp)
            }
        } else icon()
    }
    val content: @Composable () -> Unit = {
        if (headerDiscStyleId == null) glyph()
        else Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            HomeWidgetControlDisc(headerDiscStyleId, primary = false, editing = false,
                modifier = Modifier.matchParentSize())
            glyph()
        }
    }
    when {
        measuring -> Spacer(Modifier.size(48.dp))
        !interactive -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { content() }
        else -> NgIconButton(onClick = onRefresh, enabled = !state.checking, modifier = Modifier.size(48.dp)) {
            content()
        }
    }
}

/** Date-qualified timestamps prevent a previous day's successful check from looking current. */
internal fun formatHomeUpdatesCheckTime(
    checkedAt: Long,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val time = Instant.ofEpochMilli(checkedAt).atZone(zoneId)
    val pattern = when {
        time.toLocalDate() == today -> "HH:mm"
        time.year == today.year -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd HH:mm"
    }
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private data class HomeUpdatesTextMetrics(
    val nameStyle: TextStyle,
    val countStyle: TextStyle,
    val nameHeight: Dp,
    val countHeight: Dp,
)

private data class HomeUpdatesBookGeometry(
    val coverWidth: Dp,
    val coverHeight: Dp,
    val coverToNameGap: Dp,
    val nameToCountGap: Dp,
)

private fun homeUpdatesBookGeometry(small: Boolean, itemWidth: Dp) = HomeUpdatesBookGeometry(
    coverWidth = if (small) 42.dp else itemWidth,
    coverHeight = if (small) 58.dp else itemWidth * (84f / 64f),
    coverToNameGap = if (small) 3.dp else 6.dp,
    nameToCountGap = 2.dp,
)

/** Measure all real books, including offscreen pages, before a pager or lazy list chooses items. */
@Composable
private fun homeUpdatesTextMetrics(books: List<HomeUpdateBook>, width: Dp, small: Boolean): HomeUpdatesTextMetrics {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = LocalTextStyle.current.copy(fontSize = if (small) 10.sp else 11.sp,
        lineHeight = if (small) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    val countStyle = LocalTextStyle.current.copy(fontSize = if (small) 9.sp else 10.sp,
        lineHeight = if (small) 12.sp else 14.sp, letterSpacing = 0.sp)
    val labels = books.map { homeUpdatesChapterCountLabel(it.updatedChapterCount) }
    val unknownLabel = stringResource(R.string.home_updates_updated)
    return remember(books, width, small, measurer, density, nameStyle, countStyle, labels, unknownLabel) {
        val constraints = Constraints.fixedWidth(with(density) { width.roundToPx().coerceAtLeast(1) })
        fun textHeight(value: String, style: TextStyle, lines: Int): Int = measurer.measure(
            text = value, style = style, constraints = constraints, maxLines = lines, overflow = TextOverflow.Ellipsis,
        ).size.height
        val nameHeight = maxOf(textHeight("书", nameStyle, 1),
            books.maxOfOrNull { textHeight(it.name, nameStyle, 1) } ?: 0)
        val countHeight = maxOf(textHeight(unknownLabel, countStyle, 1),
            labels.maxOfOrNull { textHeight(it, countStyle, 1) } ?: 0)
        HomeUpdatesTextMetrics(nameStyle, countStyle, with(density) { nameHeight.toDp() },
            with(density) { countHeight.toDp() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeUpdatesSmallBooks(
    books: List<HomeUpdateBook>,
    pager: PagerState?,
    foreground: Color,
    secondary: Color,
    primary: Color,
    interactive: Boolean,
    measuring: Boolean,
    onOpenBook: (HomeUpdateBook) -> Unit,
    onEdit: () -> Unit,
) {
    val metrics = homeUpdatesTextMetrics(books, 48.dp, small = true)
    val geometry = homeUpdatesBookGeometry(true, 48.dp)
    val rowHeight = geometry.coverHeight + geometry.coverToNameGap + metrics.nameHeight +
        geometry.nameToCountGap + metrics.countHeight
    val gridHeight = rowHeight * 2 + 2.dp
    val pages = homeUpdatesPageCount(books.size)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sidePadding = ((maxWidth - 144.dp) / 2).coerceIn(0.dp, 10.dp)
        val narrow = maxWidth < 144.dp
        Column(Modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalAlignment = Alignment.CenterHorizontally) {
            if (measuring) {
                // Same two-row geometry without images, pager state or layout-triggered effects.
                Spacer(Modifier.width(144.dp).height(gridHeight))
            } else {
                // Below the supported home width, keep real 48dp cells instead of overlapping targets.
                val pageContent: @Composable () -> Unit = {
                    HorizontalPager(state = requireNotNull(pager), modifier = Modifier.width(144.dp).height(gridHeight),
                        userScrollEnabled = interactive && !narrow, key = { it }) { page ->
                        HomeUpdatesSmallPage(homeUpdatesPageItems(books, page), metrics, rowHeight,
                            foreground, primary, interactive, onOpenBook, onEdit)
                    }
                }
                if (narrow) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = interactive)) {
                    pageContent()
                } else pageContent()
            }
            Text(stringResource(R.string.home_updates_page,
                homeUpdatesClampedPage(pager?.currentPage ?: 0, books.size) + 1, pages),
                modifier = Modifier.padding(top = 4.dp), color = secondary,
                fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HomeUpdatesSmallPage(
    books: List<HomeUpdateBook>,
    metrics: HomeUpdatesTextMetrics,
    rowHeight: Dp,
    foreground: Color,
    primary: Color,
    interactive: Boolean,
    onOpenBook: (HomeUpdateBook) -> Unit,
    onEdit: () -> Unit,
) {
    Column(Modifier.width(144.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth().height(rowHeight)) {
                repeat(3) { column ->
                    val update = books.getOrNull(row * 3 + column)
                    if (update == null) Spacer(Modifier.width(48.dp).height(rowHeight))
                    else key(update.bookUrl) {
                        HomeUpdatesBookCell(update, 48.dp, metrics, true, foreground, primary,
                            interactive, onOpenBook, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeUpdatesLargeBooks(
    books: List<HomeUpdateBook>,
    list: LazyListState?,
    foreground: Color,
    primary: Color,
    interactive: Boolean,
    measuring: Boolean,
    onOpenBook: (HomeUpdateBook) -> Unit,
    onEdit: () -> Unit,
) {
    val itemWidth = 64.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val metrics = homeUpdatesTextMetrics(books, itemWidth, small = false)
    val geometry = homeUpdatesBookGeometry(false, itemWidth)
    val rowHeight = geometry.coverHeight + geometry.coverToNameGap + metrics.nameHeight +
        geometry.nameToCountGap + metrics.countHeight
    if (measuring) {
        // All name/count heights are already measured; no lazy row or cover work during the probe.
        Spacer(Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = 14.dp))
    } else {
        LazyRow(state = requireNotNull(list), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(rowHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = interactive) {
            items(books, key = { it.bookUrl }) { update ->
                HomeUpdatesBookCell(update, itemWidth, metrics, false, foreground, primary,
                    interactive, onOpenBook, onEdit)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeUpdatesBookCell(
    update: HomeUpdateBook,
    width: Dp,
    metrics: HomeUpdatesTextMetrics,
    small: Boolean,
    foreground: Color,
    primary: Color,
    interactive: Boolean,
    onOpenBook: (HomeUpdateBook) -> Unit,
    onEdit: () -> Unit,
) {
    val geometry = homeUpdatesBookGeometry(small, width)
    // Thumbnail density scales the cover; its native View still uses device resource density.
    // Keep native clipping inside the exact 6dp Compose clip at both render sizes.
    val nativeCoverRadius = (6f * LocalDensity.current.density /
        LocalContext.current.resources.displayMetrics.density).toInt().coerceAtLeast(0)
    val countLabel = homeUpdatesChapterCountLabel(update.updatedChapterCount)
    Column(Modifier.width(width).then(if (interactive) Modifier.combinedClickable(
        role = Role.Button, onClickLabel = stringResource(R.string.home_updates_open_book),
        onClick = { onOpenBook(update) }, onLongClickLabel = stringResource(R.string.home_edit), onLongClick = onEdit,
    ) else Modifier).semantics(mergeDescendants = true) {
        contentDescription = "${update.name}，$countLabel"
    }, horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start) {
        NgBookCover(update.book, Modifier.size(geometry.coverWidth, geometry.coverHeight)
            .clip(RoundedCornerShape(6.dp)).clearAndSetSemantics {}, coverRadius = nativeCoverRadius,
            coverAspectRatio = geometry.coverWidth / geometry.coverHeight)
        Spacer(Modifier.height(geometry.coverToNameGap))
        Text(update.name, Modifier.fillMaxWidth().height(metrics.nameHeight).clearAndSetSemantics {},
            color = foreground, style = metrics.nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (small) TextAlign.Center else TextAlign.Start)
        Spacer(Modifier.height(geometry.nameToCountGap))
        Text(countLabel, Modifier.fillMaxWidth().height(metrics.countHeight).clearAndSetSemantics {},
            color = primary, style = metrics.countStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (small) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
private fun homeUpdatesChapterCountLabel(count: Int?): String =
    if (count == null) stringResource(R.string.home_updates_updated)
    else stringResource(R.string.home_updates_chapter_count, count)
