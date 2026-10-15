package io.legado.app.ui.book.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MAX
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MIN
import io.legado.app.help.config.PACE_INTERVAL_MAX
import io.legado.app.help.config.PACE_INTERVAL_MIN
import io.legado.app.ui.design.components.NgFilterChipGroupVariant
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFilterChipGroup
import io.legado.app.ui.design.components.compose.NgFilterChipItem
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.roundToInt

/** 书目列表的缓存筛选标签（含各自的 key）。 */
internal val bookshelfCacheFilters: List<Pair<BookshelfCacheFilter, Int>> = listOf(
    BookshelfCacheFilter.ALL to R.string.all,
    BookshelfCacheFilter.CACHED to R.string.cache_filter_cached,
    BookshelfCacheFilter.UNCACHED to R.string.cache_filter_uncached,
    BookshelfCacheFilter.AUDIO to R.string.audio,
    BookshelfCacheFilter.LOCAL to R.string.local_book,
)

/**
 * 常驻在书目列表上方的缓存概览条。
 *
 * 收起时只显示总占用与缓存章节进度；展开后追加占用构成、拟人化节奏设置与清理动作。
 * 这样「缓存」既是筛选维度，也能在同一页里就地清理，不必再跳独立页面。
 */
@Composable
internal fun BookshelfCacheOverviewBar(
    state: BookshelfCacheState,
    modifier: Modifier = Modifier,
    onClearAllClick: () -> Unit,
    onClearInvalidClick: () -> Unit,
    onPaceManualEnabledChanged: (Boolean) -> Unit,
    onPaceMinIntervalChanged: (Int) -> Unit,
    onPaceMaxIntervalChanged: (Int) -> Unit,
    onPaceIntervalChangeFinished: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var paceExpanded by rememberSaveable { mutableStateOf(true) }
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = shape,
            style = NgGlassDefaults.bookDetailStyle(
                containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface),
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OverviewHeader(
                    state = state,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
                if (expanded) {
                    CacheSegmentedBar(state = state)
                    Spacer(Modifier.height(10.dp))
                    CategoryRow(
                        label = stringResource(R.string.cache_manager_books),
                        sizeText = formatCacheSize(state.bookBytes),
                        color = Color(NgTheme.colors.primary),
                    )
                    CategoryRow(
                        label = stringResource(R.string.cache_manager_audio),
                        sizeText = formatCacheSize(state.audioBytes),
                        color = Color(NgTheme.colors.tertiary),
                    )
                    CategoryRow(
                        label = stringResource(R.string.cache_manager_epub),
                        sizeText = formatCacheSize(state.epubBytes),
                        color = Color(NgTheme.colors.secondary),
                    )
                    CategoryRow(
                        label = stringResource(R.string.cache_manager_other),
                        sizeText = formatCacheSize(state.appBytes),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                    )
                    Spacer(Modifier.height(6.dp))
                    PacingSection(
                        state = state,
                        paceExpanded = paceExpanded,
                        onPaceExpandedChange = { paceExpanded = it },
                        onPaceManualEnabledChanged = onPaceManualEnabledChanged,
                        onPaceMinIntervalChanged = onPaceMinIntervalChanged,
                        onPaceMaxIntervalChanged = onPaceMaxIntervalChanged,
                        onPaceIntervalChangeFinished = onPaceIntervalChangeFinished,
                    )
                    Spacer(Modifier.height(2.dp))
                    NgSettingsItem(
                        title = stringResource(R.string.cache_manager_clear_invalid),
                        summary = stringResource(R.string.cache_manager_clear_invalid_summary),
                        summaryMaxLines = 2,
                        onClick = onClearInvalidClick,
                    )
                    NgSettingsItem(
                        title = stringResource(R.string.cache_manager_clear_all),
                        summary = stringResource(R.string.cache_manager_clear_all_summary),
                        summaryMaxLines = 2,
                        onClick = onClearAllClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewHeader(
    state: BookshelfCacheState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.cache_manager_total),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    R.string.cache_overview_progress,
                    state.cachedChapterTotal,
                    state.books.size,
                ),
                color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
        Text(
            text = if (state.loading) {
                stringResource(R.string.cache_manager_loading)
            } else {
                formatCacheSize(state.totalBytes)
            },
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_left_search),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(if (expanded) 90f else -90f),
            tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
        )
    }
}

/** 按四类占用比例画的分段条；总占用为 0 时退化为一条浅底。 */
@Composable
private fun CacheSegmentedBar(state: BookshelfCacheState) {
    val segments = listOf(
        state.bookBytes to Color(NgTheme.colors.primary),
        state.audioBytes to Color(NgTheme.colors.tertiary),
        state.epubBytes to Color(NgTheme.colors.secondary),
        state.appBytes to Color(NgTheme.colors.onSurfaceVariant),
    ).filter { it.first > 0L }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.16f)),
    ) {
        if (segments.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                segments.forEach { (bytes, color) ->
                    Box(
                        modifier = Modifier
                            .weight(bytes.toFloat())
                            .fillMaxHeight()
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, sizeText: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
        )
        Text(
            text = sizeText,
            color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.82f),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PacingSection(
    state: BookshelfCacheState,
    paceExpanded: Boolean,
    onPaceExpandedChange: (Boolean) -> Unit,
    onPaceManualEnabledChanged: (Boolean) -> Unit,
    onPaceMinIntervalChanged: (Int) -> Unit,
    onPaceMaxIntervalChanged: (Int) -> Unit,
    onPaceIntervalChangeFinished: () -> Unit,
) {
    NgSettingsItem(
        title = stringResource(R.string.cache_pace_manual),
        summary = if (state.paceManualEnabled) {
            stringResource(R.string.cache_pace_manual_summary)
        } else {
            stringResource(
                R.string.cache_pace_default_summary,
                PACE_INTERVAL_DEFAULT_MIN,
                PACE_INTERVAL_DEFAULT_MAX,
            )
        },
        trailing = NgSettingsTrailing.SWITCH,
        checked = state.paceManualEnabled,
        onCheckedChange = onPaceManualEnabledChanged,
        onClick = { onPaceManualEnabledChanged(!state.paceManualEnabled) },
        summaryMaxLines = 2,
    )
    if (state.paceManualEnabled) {
        NgExpandableSettingsItem(
            title = stringResource(R.string.reading_pace_interval),
            summary = stringResource(
                R.string.reading_pace_interval_summary,
                state.paceMinInterval,
                state.paceMaxInterval,
            ),
            expanded = paceExpanded,
            onExpandedChange = onPaceExpandedChange,
        ) {
            NgDockSlider(
                title = stringResource(R.string.reading_pace_min_interval),
                valueText = state.paceMinInterval.toString(),
                minimumText = PACE_INTERVAL_MIN.toString(),
                maximumText = PACE_INTERVAL_MAX.toString(),
                value = state.paceMinInterval.toFloat(),
                valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                onValueChange = { onPaceMinIntervalChanged(it.roundToInt()) },
                onValueChangeFinished = onPaceIntervalChangeFinished,
            )
            NgDockSlider(
                title = stringResource(R.string.reading_pace_max_interval),
                valueText = state.paceMaxInterval.toString(),
                minimumText = PACE_INTERVAL_MIN.toString(),
                maximumText = PACE_INTERVAL_MAX.toString(),
                value = state.paceMaxInterval.toFloat(),
                valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                onValueChange = { onPaceMaxIntervalChanged(it.roundToInt()) },
                onValueChangeFinished = onPaceIntervalChangeFinished,
            )
        }
    }
}

/** 书目列表上方的缓存筛选标签行。 */
@Composable
internal fun BookshelfCacheFilterRow(
    selected: BookshelfCacheFilter,
    onSelected: (BookshelfCacheFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    NgFilterChipGroup(
        items = bookshelfCacheFilters.map { (filter, labelRes) ->
            NgFilterChipItem(key = filter.name, label = stringResource(labelRes))
        },
        selectedKeys = setOf(selected.name),
        onToggle = { key ->
            BookshelfCacheFilter.entries
                .firstOrNull { it.name == key }
                ?.let(onSelected)
        },
        modifier = modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp),
        variant = NgFilterChipGroupVariant.WRAP,
    )
}

/** 与缓存面板一致的可读体积格式。 */
internal fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = 1024.0 * 1024.0 * 1024.0
    val mb = 1024.0 * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
