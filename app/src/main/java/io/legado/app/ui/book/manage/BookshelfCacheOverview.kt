package io.legado.app.ui.book.manage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.design.components.NgFilterChipGroupVariant
import io.legado.app.ui.design.components.compose.NgFilterChipGroup
import io.legado.app.ui.design.components.compose.NgFilterChipItem
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale

/**
 * 书目列表的缓存筛选标签（含各自的 key）。
 *
 * 只保留「缓存」本身的三个维度；有声/本地属于书籍分组，交给右侧的分组下拉，
 * 避免同一个概念在两处筛选里各出现一次。
 */
internal val bookshelfCacheFilters: List<Pair<BookshelfCacheFilter, Int>> = listOf(
    BookshelfCacheFilter.ALL to R.string.all,
    BookshelfCacheFilter.CACHED to R.string.cache_filter_cached,
    BookshelfCacheFilter.UNCACHED to R.string.cache_filter_uncached,
)

/**
 * 书目列表上方的缓存状态条，常驻一行。
 *
 * 这里是「一眼看状态、一只手能停」的地方：空闲时显示书籍缓存总占用并作为缓存抽屉的入口；
 * 有任务时同一行内换成书名与进度，并把暂停/继续、停止两个控制直接放在右手边。
 * 占用明细与低频设置（节奏、清理）都收进缓存抽屉。
 */
@Composable
internal fun BookshelfCacheStatusBar(
    state: BookshelfCacheState,
    onOpenSheet: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val taskBooks = state.activeBooks
    val running = state.downloading && taskBooks.isNotEmpty()
    val accent = if (running && state.paused) {
        colorResource(R.color.ng_warning)
    } else {
        Color(NgTheme.colors.primary)
    }
    val primaryColor = Color(NgTheme.colors.onSurface)
    val secondaryColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f)

    // 左端的环同一处承担两种含义:有任务时是当前进度,空闲时是整体缓存占比。
    // 环比一个圆点多带了「缓存到哪了」这一层信息,而宽度不变。
    val ringProgress: Float
    val taskDone: Int
    val taskTotal: Int
    if (running) {
        taskDone = taskBooks.sumOf { it.done }
        taskTotal = taskBooks.sumOf { it.total }
        ringProgress = if (taskTotal > 0) {
            (taskDone.toFloat() / taskTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
    } else {
        taskDone = 0
        taskTotal = 0
        val allChapters = state.books.sumOf { it.totalChapterNum }
        ringProgress = if (allChapters > 0) {
            (state.cachedChapterTotal.toFloat() / allChapters).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // 标题与副标题按任务态切换,同一行不因状态改变而换高。
    val title: String
    val subtitle: String
    if (running) {
        val first = taskBooks.first()
        title = if (taskBooks.size == 1) {
            first.name
        } else {
            stringResource(R.string.cache_notification_more, first.name, taskBooks.size)
        }
        val done = taskBooks.sumOf { it.done }
        val total = taskBooks.sumOf { it.total }
        val stateText = stringResource(
            if (state.paused) R.string.cache_paused else R.string.cache_notification_caching
        )
        subtitle = if (total > 0) {
            "$stateText · " + stringResource(R.string.cache_notification_progress, done, total)
        } else {
            stateText
        }
    } else {
        title = stringResource(R.string.cache_status_books)
        subtitle = if (state.loading) {
            stringResource(R.string.cache_manager_loading)
        } else {
            stringResource(
                R.string.cache_overview_progress,
                state.cachedChapterTotal,
                state.books.size,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            style = NgGlassDefaults.bookDetailStyle(
                containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface),
            ),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSheet)
                        .padding(start = 12.dp, end = 8.dp)
                        .height(STATUS_BAR_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusRing(
                        progress = ringProgress,
                        color = accent,
                        paused = running && state.paused,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = primaryColor,
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            color = secondaryColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (running) {
                        StatusBarIconButton(
                            iconRes = if (state.paused) {
                                R.drawable.ic_play_24dp
                            } else {
                                R.drawable.ic_pause_24dp
                            },
                            contentDescription = stringResource(
                                if (state.paused) R.string.resume else R.string.pause
                            ),
                            tint = Color(NgTheme.colors.primary),
                            onClick = if (state.paused) onResumeClick else onPauseClick,
                        )
                        StatusBarIconButton(
                            iconRes = R.drawable.ic_stop_black_24dp,
                            contentDescription = stringResource(R.string.stop),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                            onClick = onStopClick,
                        )
                    } else {
                        Text(
                            text = formatCacheSize(state.bookRelatedBytes),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right_20),
                            contentDescription = stringResource(R.string.cache_sheet_title),
                            modifier = Modifier.size(15.dp),
                            tint = secondaryColor,
                        )
                    }
                }
                // 缓存中时把进度画在条底缘,不额外占一行高度。
                if (running && !state.paused) {
                    val done = taskBooks.sumOf { it.done }
                    val total = taskBooks.sumOf { it.total }
                    if (total > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(secondaryColor.copy(alpha = 0.16f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        (done.toFloat() / total).coerceIn(0f, 1f)
                                    )
                                    .fillMaxHeight()
                                    .background(accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 状态条左端的进度环:底轨一圈,进度从十二点方向顺时针铺开,芯里是状态图标。 */
@Composable
private fun StatusRing(
    progress: Float,
    color: Color,
    paused: Boolean,
) {
    val trackColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.16f)
    Box(modifier = Modifier.size(27.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.2.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val inset = stroke / 2f
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke),
            )
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // 芯里的图标让环在极低进度时也能被认出是「缓存」而不是装饰。
        Icon(
            painter = painterResource(
                if (paused) R.drawable.ic_play_24dp else R.drawable.ic_arrow_down
            ),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color,
        )
    }
}

/** 状态条右手边的图标按钮:只用在缓存任务控制上,不进设计系统。 */
@Composable
private fun StatusBarIconButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

/**
 * 缓存维度的筛选轨。
 *
 * 不再带左侧的维度名:同一屏里紧跟着还有一条分组轨,两条轨各自一眼可辨,
 * 前缀文字只是重复占用宽度。
 */
@Composable
internal fun BookshelfCacheFilterRail(
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
        modifier = modifier,
        variant = NgFilterChipGroupVariant.SINGLE_ROW_RAIL,
    )
}

/** 分组维度轨末端的「管理」项 key;不是真实分组 id,故用一个不可能与 groupId 冲突的字符串。 */
private const val GROUP_MANAGE_KEY = "manage"

/**
 * 分组维度的筛选轨:全部 / 未分组 / 各命名分组,末端固定一个「管理」。
 *
 * 与缓存轨同样是 chip 语言:两套筛选并列出现,而不是一个在轨道上一个藏在下拉里,
 * 免得同一个「筛选」概念有两种操作方式。
 */
@Composable
internal fun BookshelfGroupFilterRail(
    groups: List<BookGroup>,
    selectedGroupId: Long,
    onGroupSelected: (Long) -> Unit,
    onGroupManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel = stringResource(R.string.all)
    val noGroupLabel = stringResource(R.string.no_group)
    val manageLabel = stringResource(R.string.group_manage)
    val namedGroups = remember(groups) {
        groups.filterNot {
            it.groupId == BookGroup.IdAll || it.groupId == BookGroup.IdNoGroup
        }
    }
    val items = buildList {
        add(NgFilterChipItem(key = BookGroup.IdAll.toString(), label = allLabel))
        add(NgFilterChipItem(key = BookGroup.IdNoGroup.toString(), label = noGroupLabel))
        namedGroups.forEach {
            add(NgFilterChipItem(key = it.groupId.toString(), label = it.groupName))
        }
        add(NgFilterChipItem(key = GROUP_MANAGE_KEY, label = manageLabel))
    }
    NgFilterChipGroup(
        items = items,
        selectedKeys = setOf(selectedGroupId.toString()),
        onToggle = { key ->
            when (key) {
                GROUP_MANAGE_KEY -> onGroupManage()
                else -> key.toLongOrNull()?.let(onGroupSelected)
            }
        },
        modifier = modifier,
        variant = NgFilterChipGroupVariant.SINGLE_ROW_RAIL,
    )
}

/** 与缓存抽屉一致的可读体积格式。 */
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

private val STATUS_BAR_HEIGHT = 50.dp
