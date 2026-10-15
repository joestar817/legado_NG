package io.legado.app.ui.book.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MAX
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MIN
import io.legado.app.help.config.PACE_INTERVAL_MAX
import io.legado.app.help.config.PACE_INTERVAL_MIN
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.components.compose.NgSettingsValueChip
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 书架管理页内嵌的缓存管理面板。
 *
 * 顶部是页内二级标题栏（带返回），下方沿用设置页的 NgSettings 分组样式。
 */
@Composable
internal fun BookshelfCacheScreen(
    state: BookshelfCacheState,
    onBack: () -> Unit,
    onClearAllClick: () -> Unit,
    onClearInvalidClick: () -> Unit,
    onClearBookClick: (BookshelfCacheEntry) -> Unit,
    onPaceManualEnabledChanged: (Boolean) -> Unit,
    onPaceMinIntervalChanged: (Int) -> Unit,
    onPaceMaxIntervalChanged: (Int) -> Unit,
    onPaceIntervalChangeFinished: () -> Unit,
) {
    var paceIntervalExpanded by rememberSaveable { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxSize()) {
        BookshelfCacheTopBar(onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
        ) {
            item {
                NgSettingsSectionLabel(stringResource(R.string.cache_manager_overview))
            }
            item {
                NgSettingsGroup {
                    CacheInfoSettingItem(
                        title = stringResource(R.string.cache_manager_total),
                        value = formatSize(state.totalBytes),
                    )
                    CacheInfoSettingItem(
                        title = stringResource(R.string.cache_manager_books),
                        value = formatSize(state.bookBytes),
                    )
                    CacheInfoSettingItem(
                        title = stringResource(R.string.cache_manager_audio),
                        value = formatSize(state.audioBytes),
                    )
                    CacheInfoSettingItem(
                        title = stringResource(R.string.cache_manager_epub),
                        value = formatSize(state.epubBytes),
                    )
                    CacheInfoSettingItem(
                        title = stringResource(R.string.cache_manager_other),
                        value = formatSize(state.appBytes),
                    )
                }
            }
            item {
                NgSettingsSectionLabel(stringResource(R.string.reading_pace))
            }
            item {
                NgSettingsGroup {
                    NgSettingsItem(
                        title = stringResource(R.string.cache_pace_manual),
                        summary = if (state.paceManualEnabled) {
                            stringResource(R.string.cache_pace_manual_summary)
                        } else {
                            // 关闭时显示内置默认节奏,而不是用户上次存下的自定义值。
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
                            expanded = paceIntervalExpanded,
                            onExpandedChange = { paceIntervalExpanded = it },
                        ) {
                            NgDockSlider(
                                title = stringResource(R.string.reading_pace_min_interval),
                                valueText = state.paceMinInterval.toString(),
                                minimumText = PACE_INTERVAL_MIN.toString(),
                                maximumText = PACE_INTERVAL_MAX.toString(),
                                value = state.paceMinInterval.toFloat(),
                                valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                                onValueChange = { value ->
                                    onPaceMinIntervalChanged(value.roundToInt())
                                },
                                onValueChangeFinished = onPaceIntervalChangeFinished,
                            )
                            NgDockSlider(
                                title = stringResource(R.string.reading_pace_max_interval),
                                valueText = state.paceMaxInterval.toString(),
                                minimumText = PACE_INTERVAL_MIN.toString(),
                                maximumText = PACE_INTERVAL_MAX.toString(),
                                value = state.paceMaxInterval.toFloat(),
                                valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                                onValueChange = { value ->
                                    onPaceMaxIntervalChanged(value.roundToInt())
                                },
                                onValueChangeFinished = onPaceIntervalChangeFinished,
                            )
                        }
                    }
                }
            }
            item {
                NgSettingsSectionLabel(stringResource(R.string.cache_manager_actions))
            }
            item {
                NgSettingsGroup {
                    NgSettingsItem(
                        title = stringResource(R.string.cache_manager_clear_all),
                        summary = stringResource(R.string.cache_manager_clear_all_summary),
                        onClick = onClearAllClick,
                        summaryMaxLines = 2,
                    )
                    NgSettingsItem(
                        title = stringResource(R.string.cache_manager_clear_invalid),
                        summary = stringResource(R.string.cache_manager_clear_invalid_summary),
                        onClick = onClearInvalidClick,
                        summaryMaxLines = 2,
                    )
                }
            }
            item {
                NgSettingsSectionLabel(stringResource(R.string.cache_manager_book_list))
            }
            when {
                state.loading -> item {
                    CacheHintText(stringResource(R.string.cache_manager_loading))
                }

                state.books.isEmpty() -> item {
                    CacheHintText(stringResource(R.string.cache_manager_empty))
                }

                else -> items(state.books, key = { it.bookUrl }) { entry ->
                    val sizeText = formatSize(entry.sizeBytes)
                    val summary = buildString {
                        if (entry.author.isNotBlank()) {
                            append(entry.author)
                        }
                        if (entry.chapterCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(
                                stringResource(
                                    R.string.cache_manager_chapter_count,
                                    entry.chapterCount,
                                )
                            )
                        }
                        if (entry.isAudio) {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.cache_manager_audio_tag))
                        }
                        if (entry.isEpub) {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.cache_manager_epub_tag))
                        }
                    }
                    NgSettingsItem(
                        title = entry.name,
                        modifier = Modifier.padding(top = 6.dp),
                        summary = summary.ifEmpty { null },
                        trailing = NgSettingsTrailing.CUSTOM,
                        customTrailing = { NgSettingsValueChip(sizeText) },
                        onClick = { onClearBookClick(entry) },
                    )
                }
            }
        }
    }
}

/** 与书架管理页同款的玻璃标题栏,只是把搜索框换成页面标题。 */
@Composable
private fun BookshelfCacheTopBar(onBack: () -> Unit) {
    val headerShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val headerStyle = NgGlassDefaults.bookDetailStyle(
        containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = headerShape,
            style = headerStyle
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NgFloatingToolbarBackButton(onClick = onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cache_manager),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CacheInfoSettingItem(title: String, value: String) {
    NgSettingsItem(
        title = title,
        trailing = NgSettingsTrailing.CUSTOM,
        customTrailing = { NgSettingsValueChip(value) },
    )
}

@Composable
private fun CacheHintText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
        )
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes <= 0L -> "0 B"
        bytes >= 1024L * 1024L * 1024L ->
            String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)

        bytes >= 1024L * 1024L ->
            String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)

        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
