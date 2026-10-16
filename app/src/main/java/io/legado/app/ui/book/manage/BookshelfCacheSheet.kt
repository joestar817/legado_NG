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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.roundToInt

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
    onPaceEnabledChanged: (Boolean) -> Unit,
    onPaceManualEnabledChanged: (Boolean) -> Unit,
    onPaceMinIntervalChanged: (Int) -> Unit,
    onPaceMaxIntervalChanged: (Int) -> Unit,
    onPaceIntervalChangeFinished: () -> Unit,
    onPaceLongRestChanged: (Boolean) -> Unit,
    onPaceQuietHoursChanged: (Boolean) -> Unit,
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
                    onEnabledChanged = onPaceEnabledChanged,
                    onManualEnabledChanged = onPaceManualEnabledChanged,
                    onMinIntervalChanged = onPaceMinIntervalChanged,
                    onMaxIntervalChanged = onPaceMaxIntervalChanged,
                    onIntervalChangeFinished = onPaceIntervalChangeFinished,
                    onLongRestChanged = onPaceLongRestChanged,
                    onQuietHoursChanged = onPaceQuietHoursChanged,
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
 * 三类一律列出,0 B 也保留 —— 用户点开明细就是在问「缓存都花在哪了」,
 * 缺行会被读成「漏统计」,0 B 反而是有用的答案(确实没缓存音频)。
 */
@Composable
private fun CacheComposition(state: BookshelfCacheState) {
    val segments = listOf(
        CacheSegment(
            label = stringResource(R.string.cache_manager_books),
            description = stringResource(R.string.cache_manager_books_desc),
            bytes = state.bookBytes,
            color = Color(NgTheme.colors.primary),
        ),
        CacheSegment(
            label = stringResource(R.string.cache_manager_audio),
            description = stringResource(R.string.cache_manager_audio_desc),
            bytes = state.audioBytes,
            color = Color(NgTheme.colors.tertiary),
        ),
        CacheSegment(
            label = stringResource(R.string.cache_manager_epub),
            description = stringResource(R.string.cache_manager_epub_desc),
            bytes = state.epubBytes,
            color = Color(NgTheme.colors.secondary),
        ),
    )
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
                        .height(9.dp)
                        .clip(CircleShape),
                ) {
                    segments.forEach { segment ->
                        if (segment.bytes > 0L) {
                            Box(
                                modifier = Modifier
                                    .weight(segment.bytes.toFloat())
                                    .fillMaxHeight()
                                    .background(segment.color),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
        segments.forEachIndexed { index, segment ->
            CompositionRow(
                label = segment.label,
                description = segment.description,
                sizeText = formatCacheSize(segment.bytes),
                dotColor = segment.color,
            )
            if (index < segments.lastIndex) NgCompactSettingsDivider()
        }
    }
}

private data class CacheSegment(
    val label: String,
    val description: String,
    val bytes: Long,
    val color: Color,
)

@Composable
private fun CompositionRow(
    label: String,
    description: String,
    sizeText: String,
    dotColor: Color,
) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = sizeText,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
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
    val sizeText = formatCacheSize(state.appBytes)
    NgCompactSettingsGroup {
        NgCompactSettingsItem(
            title = stringResource(R.string.cache_manager_other),
            summary = stringResource(R.string.cache_manager_other_desc, sizeText),
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
    onEnabledChanged: (Boolean) -> Unit,
    onManualEnabledChanged: (Boolean) -> Unit,
    onMinIntervalChanged: (Int) -> Unit,
    onMaxIntervalChanged: (Int) -> Unit,
    onIntervalChangeFinished: () -> Unit,
    onLongRestChanged: (Boolean) -> Unit,
    onQuietHoursChanged: (Boolean) -> Unit,
) {
    NgCompactSettingsGroup {
        NgCompactSettingsItem(
            title = stringResource(R.string.reading_pace),
            summary = stringResource(R.string.reading_pace_summary),
            trailing = NgSettingsTrailing.SWITCH,
            checked = state.paceEnabled,
            onCheckedChange = onEnabledChanged,
            onClick = { onEnabledChanged(!state.paceEnabled) },
        )
        if (state.paceEnabled) {
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.cache_pace_manual),
                summary = if (state.paceManualEnabled) {
                    stringResource(
                        R.string.reading_pace_interval_summary,
                        formatPaceSeconds(state.paceMinInterval),
                        formatPaceSeconds(state.paceMaxInterval),
                    )
                } else {
                    stringResource(
                        R.string.cache_pace_default_summary,
                        formatPaceSeconds(PACE_INTERVAL_DEFAULT_MIN),
                        formatPaceSeconds(PACE_INTERVAL_DEFAULT_MAX),
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
                        .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.cache_pace_seconds_range,
                            formatPaceSeconds(PACE_INTERVAL_MIN),
                            formatPaceSeconds(PACE_INTERVAL_MAX),
                        ),
                        color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    PaceIntervalField(
                        label = stringResource(R.string.reading_pace_min_interval),
                        intervalMillis = state.paceMinInterval,
                        onValueChange = onMinIntervalChanged,
                        onEditingFinished = onIntervalChangeFinished,
                    )
                    PaceIntervalField(
                        label = stringResource(R.string.reading_pace_max_interval),
                        intervalMillis = state.paceMaxInterval,
                        onValueChange = onMaxIntervalChanged,
                        onEditingFinished = onIntervalChangeFinished,
                    )
                }
            }
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.reading_pace_long_rest),
                summary = stringResource(R.string.reading_pace_long_rest_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.paceLongRest,
                onCheckedChange = onLongRestChanged,
                onClick = { onLongRestChanged(!state.paceLongRest) },
            )
            NgCompactSettingsDivider()
            NgCompactSettingsItem(
                title = stringResource(R.string.reading_pace_quiet_hours),
                summary = stringResource(R.string.reading_pace_quiet_hours_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.paceQuietHours,
                onCheckedChange = onQuietHoursChanged,
                onClick = { onQuietHoursChanged(!state.paceQuietHours) },
            )
        }
    }
}

/** 毫秒转成用户填写的秒:1000ms → 1,1200ms → 1.2,1250ms → 1.25(整数不带小数点)。 */
private fun formatPaceSeconds(millis: Int): String = when {
    millis % 1000 == 0 -> (millis / 1000).toString()
    millis % 100 == 0 -> "%.1f".format(Locale.US, millis / 1000.0)
    else -> "%.2f".format(Locale.US, millis / 1000.0)
}

/** 解析用户输入的秒为毫秒;越界按上下限夹住,非法输入返回 null(保留上一次生效值)。 */
private fun parsePaceSeconds(text: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val seconds = trimmed.toDoubleOrNull() ?: return null
    if (seconds.isNaN() || seconds.isInfinite()) return null
    return (seconds * 1000).roundToInt().coerceIn(PACE_INTERVAL_MIN, PACE_INTERVAL_MAX)
}

/**
 * 间隔输入框:单位是秒。滑块换成直接输入后,1200–4500ms 这类值不必再拖到位。
 * 输入期间只写草稿,失焦时才提交并回正格式,免得中途的 "1" 被当成最终值存进偏好。
 */
@Composable
private fun PaceIntervalField(
    label: String,
    intervalMillis: Int,
    onValueChange: (Int) -> Unit,
    onEditingFinished: () -> Unit,
) {
    var text by remember { mutableStateOf(formatPaceSeconds(intervalMillis)) }
    // 外部值变化时回写;能被 formatPaceSeconds 原样还原的文本不回写,避免打断输入
    LaunchedEffect(intervalMillis) {
        val shown = formatPaceSeconds(intervalMillis)
        if (parsePaceSeconds(text) != intervalMillis) text = shown
    }
    NgFormField(
        label = label,
        value = text,
        onValueChange = { raw ->
            text = raw
            parsePaceSeconds(raw)?.let(onValueChange)
        },
        supportingText = stringResource(R.string.seconds),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        variant = NgFormFieldVariant.OUTLINED,
        onFocusLost = {
            val parsed = parsePaceSeconds(text)
            text = formatPaceSeconds(parsed ?: intervalMillis)
            onEditingFinished()
        },
    )
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
