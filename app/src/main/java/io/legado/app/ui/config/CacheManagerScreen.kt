package io.legado.app.ui.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MAX
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MIN
import io.legado.app.help.config.PACE_INTERVAL_MAX
import io.legado.app.help.config.PACE_INTERVAL_MIN
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.components.compose.NgSettingsValueChip
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun CacheManagerScreen(
    state: CacheManagerState,
    onClearAllClick: () -> Unit,
    onClearInvalidClick: () -> Unit,
    onClearBookClick: (CacheBookEntry) -> Unit,
    onPaceManualEnabledChanged: (Boolean) -> Unit,
    onPaceMinIntervalChanged: (Int) -> Unit,
    onPaceMaxIntervalChanged: (Int) -> Unit,
    onPaceIntervalChangeFinished: () -> Unit,
) {
    var paceIntervalExpanded by rememberSaveable { mutableStateOf(true) }
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
    Text(
        text = text,
        color = Color(NgTheme.colors.onSurfaceVariant),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val unit = 1024.0
    return when {
        bytes >= unit * unit * unit ->
            String.format(Locale.US, "%.2f GB", bytes / (unit * unit * unit))

        bytes >= unit * unit -> String.format(Locale.US, "%.2f MB", bytes / (unit * unit))
        bytes >= unit -> String.format(Locale.US, "%.1f KB", bytes / unit)
        else -> "$bytes B"
    }
}
