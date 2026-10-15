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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MAX
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MIN
import io.legado.app.help.config.PACE_INTERVAL_MAX
import io.legado.app.help.config.PACE_INTERVAL_MIN
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgCompactSettingsDivider
import io.legado.app.ui.design.components.compose.NgCompactSettingsGroup
import io.legado.app.ui.design.components.compose.NgCompactSettingsItem
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.theme.NgTheme

/**
 * 缓存抽屉:承载全部占用明细与低频动作。
 *
 * 状态条只回答「现在什么状态、能不能停」;报表(占用构成、应用临时缓存)与
 * 设置一次就不动的项(节奏、清理)都收在这里,首屏才还得出高度给书目列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfCacheSheet(
    state: BookshelfCacheState,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onClearInvalid: () -> Unit,
    onClearAppCache: () -> Unit,
    onPaceManualEnabledChanged: (Boolean) -> Unit,
    onPaceMinIntervalChanged: (Int) -> Unit,
    onPaceMaxIntervalChanged: (Int) -> Unit,
    onPaceIntervalChangeFinished: () -> Unit,
    onPauseAll: () -> Unit,
    onStopAll: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        shape = RectangleShape,
        dragHandle = null,
    ) {
        NgBottomDrawerSurface(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SheetTitle(state)
                CacheComposition(state)
                AppCacheRow(state, onClearAppCache)
                if (state.downloading && state.activeBooks.isNotEmpty()) {
                    CacheTaskSection(state, onPauseAll, onStopAll)
                }
                PaceSection(
                    state = state,
                    onManualEnabledChanged = onPaceManualEnabledChanged,
                    onMinIntervalChanged = onPaceMinIntervalChanged,
                    onMaxIntervalChanged = onPaceMaxIntervalChanged,
                    onIntervalChangeFinished = onPaceIntervalChangeFinished,
                )
                ClearSection(onClearInvalid, onClearAll)
            }
        }
    }
}

/** 标题行:标题在左,主数字在右,与状态条同一口径。 */
@Composable
private fun SheetTitle(state: BookshelfCacheState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.cache_sheet_title),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatCacheSize(state.bookRelatedBytes),
            color = Color(NgTheme.colors.primary),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 占用构成:一条分段占比条 + 分类明细。
 *
 * 分类行只在有占用时渲染 —— 旧面板把三行 0 B 与有效数据同等占位,
 * 用户滚过三行才知道这台机器其实只有一本书缓存。
 */
@Composable
private fun CacheComposition(state: BookshelfCacheState) {
    val segments = listOf(
        Triple(
            stringResource(R.string.cache_manager_books),
            state.bookBytes,
            Color(NgTheme.colors.primary),
        ),
        Triple(
            stringResource(R.string.cache_manager_audio),
            state.audioBytes,
            Color(NgTheme.colors.tertiary),
        ),
        Triple(
            stringResource(R.string.cache_manager_epub),
            state.epubBytes,
            Color(NgTheme.colors.secondary),
        ),
    )
    val visible = segments.filter { it.second > 0L }
    val total = state.bookRelatedBytes

    NgCompactSettingsGroup {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.cache_manager_total),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = stringResource(
                    R.string.cache_overview_progress,
                    state.cachedChapterTotal,
                    state.books.size,
                ),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            if (total > 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                ) {
                    segments.forEach { (_, bytes, color) ->
                        if (bytes > 0L) {
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
            Spacer(Modifier.height(2.dp))
        }
        if (visible.isEmpty()) {
            NgCompactSettingsItem(
                title = stringResource(
                    if (state.loading) R.string.cache_manager_loading else R.string.cache_manager_empty
                ),
                trailing = NgSettingsTrailing.NONE,
            )
        } else {
            visible.forEachIndexed { index, (label, bytes, color) ->
                CompositionRow(label = label, sizeText = formatCacheSize(bytes), dotColor = color)
                if (index < visible.lastIndex) NgCompactSettingsDivider()
            }
        }
    }
}

@Composable
private fun CompositionRow(label: String, sizeText: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 11.dp, end = 14.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = sizeText,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}

/**
 * 应用临时缓存单列一行,并给独立的清理入口。
 *
 * 它和书籍缓存不是一回事(cacheDir 由应用自己回收),混进主数字会让
 * 「书籍缓存 908 KB」被「其他应用缓存 8.3 MB」盖住。
 */
@Composable
private fun AppCacheRow(state: BookshelfCacheState, onClearAppCache: () -> Unit) {
    NgCompactSettingsGroup {
        NgCompactSettingsItem(
            title = stringResource(R.string.cache_manager_other),
            summary = formatCacheSize(state.appBytes),
            trailing = NgSettingsTrailing.NONE,
        )
        NgCompactSettingsDivider()
        NgCompactSettingsItem(
            title = stringResource(R.string.clear),
            trailing = NgSettingsTrailing.NONE,
            onClick = onClearAppCache,
        )
    }
}

/** 缓存任务:逐本进度 + 全局暂停/停止。状态条只显示第一本,完整队列在这里。 */
@Composable
private fun CacheTaskSection(
    state: BookshelfCacheState,
    onPauseAll: () -> Unit,
    onStopAll: () -> Unit,
) {
    NgCompactSettingsGroup {
        Text(
            text = stringResource(R.string.cache_sheet_tasks),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 2.dp),
        )
        state.activeBooks.forEach { book ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.name,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.cache_notification_progress,
                            book.done,
                            book.total,
                        ),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
                if (book.total > 0) {
                    LinearProgressIndicator(
                        progress = { (book.done.toFloat() / book.total).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = if (book.paused) {
                            Color(NgTheme.colors.onSurfaceVariant)
                        } else {
                            Color(NgTheme.colors.primary)
                        },
                        trackColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.16f),
                    )
                }
            }
            NgCompactSettingsDivider()
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            SheetTextAction(
                text = stringResource(R.string.cache_sheet_pause_all),
                onClick = onPauseAll,
                modifier = Modifier.weight(1f),
            )
            SheetTextAction(
                text = stringResource(R.string.cache_sheet_stop_all),
                onClick = onStopAll,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 缓存节奏:开关关闭时摘要显示内置默认值,免得用户误读成「关掉了还在用手动值」。 */
@Composable
private fun PaceSection(
    state: BookshelfCacheState,
    onManualEnabledChanged: (Boolean) -> Unit,
    onMinIntervalChanged: (Int) -> Unit,
    onMaxIntervalChanged: (Int) -> Unit,
    onIntervalChangeFinished: () -> Unit,
) {
    NgCompactSettingsGroup {
        NgCompactSettingsItem(
            title = stringResource(R.string.cache_pace_manual),
            summary = if (state.paceManualEnabled) {
                stringResource(
                    R.string.reading_pace_interval_summary,
                    state.paceMinInterval,
                    state.paceMaxInterval,
                )
            } else {
                stringResource(
                    R.string.cache_pace_default_summary,
                    PACE_INTERVAL_DEFAULT_MIN,
                    PACE_INTERVAL_DEFAULT_MAX,
                )
            },
            trailing = NgSettingsTrailing.SWITCH,
            checked = state.paceManualEnabled,
            onCheckedChange = onManualEnabledChanged,
            onClick = { onManualEnabledChanged(!state.paceManualEnabled) },
        )
        if (state.paceManualEnabled) {
            NgCompactSettingsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NgDockSlider(
                    title = stringResource(R.string.reading_pace_min_interval),
                    valueText = "${state.paceMinInterval} ms",
                    minimumText = "$PACE_INTERVAL_MIN ms",
                    maximumText = "$PACE_INTERVAL_MAX ms",
                    value = state.paceMinInterval.toFloat(),
                    valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                    onValueChange = { onMinIntervalChanged(it.toInt()) },
                    onValueChangeFinished = onIntervalChangeFinished,
                )
                NgDockSlider(
                    title = stringResource(R.string.reading_pace_max_interval),
                    valueText = "${state.paceMaxInterval} ms",
                    minimumText = "$PACE_INTERVAL_MIN ms",
                    maximumText = "$PACE_INTERVAL_MAX ms",
                    value = state.paceMaxInterval.toFloat(),
                    valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                    onValueChange = { onMaxIntervalChanged(it.toInt()) },
                    onValueChangeFinished = onIntervalChangeFinished,
                )
            }
        }
    }
}

/** 清理:残留是常规操作,清全部是破坏性的,颜色分开。 */
@Composable
private fun ClearSection(onClearInvalid: () -> Unit, onClearAll: () -> Unit) {
    NgCompactSettingsGroup {
        NgCompactSettingsItem(
            title = stringResource(R.string.cache_manager_clear_invalid),
            summary = stringResource(R.string.cache_manager_clear_invalid_summary),
            trailing = NgSettingsTrailing.NONE,
            onClick = onClearInvalid,
        )
        NgCompactSettingsDivider()
        NgCompactSettingsItem(
            title = stringResource(R.string.cache_manager_clear_all),
            summary = stringResource(R.string.cache_manager_clear_all_summary),
            trailing = NgSettingsTrailing.NONE,
            onClick = onClearAll,
        )
    }
}

@Composable
private fun SheetTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
    }
}
