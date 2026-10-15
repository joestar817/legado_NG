package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MAX
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MIN
import io.legado.app.help.config.PACE_INTERVAL_MAX
import io.legado.app.help.config.PACE_INTERVAL_MIN
import io.legado.app.help.config.THREAD_COUNT_DEFAULT
import io.legado.app.help.config.THREAD_COUNT_MAX
import io.legado.app.help.config.THREAD_COUNT_MIN
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import kotlin.math.roundToInt

@Immutable
internal data class AdvancedConfigScreenState(
    val userAgent: String = "",
    val cronet: Boolean = false,
    val antiAlias: Boolean = false,
    val threadCount: Int = THREAD_COUNT_DEFAULT,
    val recordLog: Boolean = false,
    val recordNetworkLog: Boolean = false,
    val recordHeapDump: Boolean = false,
    val readingPaceEnabled: Boolean = true,
    val readingPaceMinInterval: Int = PACE_INTERVAL_DEFAULT_MIN,
    val readingPaceMaxInterval: Int = PACE_INTERVAL_DEFAULT_MAX,
    val readingPaceLongRest: Boolean = true,
    val readingPaceQuietHours: Boolean = false,
)

@Composable
internal fun AdvancedConfigScreen(
    state: AdvancedConfigScreenState,
    onUserAgentClick: () -> Unit,
    onCustomHostsClick: () -> Unit,
    onUploadRuleClick: () -> Unit,
    onCronetChanged: (Boolean) -> Unit,
    onAntiAliasChanged: (Boolean) -> Unit,
    onThreadCountChanged: (Int) -> Unit,
    onThreadCountChangeFinished: () -> Unit,
    onRecordLogChanged: (Boolean) -> Unit,
    onRecordNetworkLogChanged: (Boolean) -> Unit,
    onNetworkRequestLogClick: () -> Unit,
    onRecordHeapDumpChanged: (Boolean) -> Unit,
    onReadingPaceEnabledChanged: (Boolean) -> Unit,
    onReadingPaceMinIntervalChanged: (Int) -> Unit,
    onReadingPaceMaxIntervalChanged: (Int) -> Unit,
    onReadingPaceIntervalChangeFinished: () -> Unit,
    onReadingPaceLongRestChanged: (Boolean) -> Unit,
    onReadingPaceQuietHoursChanged: (Boolean) -> Unit,
) {
    var threadCountExpanded by rememberSaveable { mutableStateOf(false) }
    var readingPaceIntervalExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        NgSettingsGroup {
            AdvancedActionSettingItem(
                title = stringResource(R.string.user_agent),
                summary = state.userAgent,
                onClick = onUserAgentClick,
            )
            AdvancedActionSettingItem(
                title = stringResource(R.string.custom_hosts),
                summary = stringResource(R.string.custom_hosts_summary),
                onClick = onCustomHostsClick,
            )
            AdvancedActionSettingItem(
                title = stringResource(R.string.direct_link_upload_rule),
                summary = stringResource(R.string.direct_link_upload_rule_summary),
                onClick = onUploadRuleClick,
            )
            AdvancedSwitchSettingItem(
                title = "Cronet",
                summary = stringResource(R.string.pref_cronet_summary),
                checked = state.cronet,
                onCheckedChange = onCronetChanged,
            )
            AdvancedSwitchSettingItem(
                title = stringResource(R.string.anti_alias),
                summary = stringResource(R.string.pref_anti_alias_summary),
                checked = state.antiAlias,
                onCheckedChange = onAntiAliasChanged,
            )
            NgExpandableSettingsItem(
                title = stringResource(R.string.threads_num_title),
                summary = stringResource(R.string.threads_num, state.threadCount),
                expanded = threadCountExpanded,
                onExpandedChange = { threadCountExpanded = it },
            ) {
                NgDockSlider(
                    title = stringResource(R.string.thread_count),
                    valueText = state.threadCount.toString(),
                    minimumText = THREAD_COUNT_MIN.toString(),
                    maximumText = THREAD_COUNT_MAX.toString(),
                    value = state.threadCount.toFloat(),
                    valueRange = THREAD_COUNT_MIN.toFloat()..THREAD_COUNT_MAX.toFloat(),
                    onValueChange = { value ->
                        onThreadCountChanged(value.roundToInt())
                    },
                    onValueChangeFinished = onThreadCountChangeFinished,
                )
            }
            AdvancedSwitchSettingItem(
                title = stringResource(R.string.reading_pace),
                summary = stringResource(R.string.reading_pace_summary),
                checked = state.readingPaceEnabled,
                onCheckedChange = onReadingPaceEnabledChanged,
            )
            if (state.readingPaceEnabled) {
                NgExpandableSettingsItem(
                    title = stringResource(R.string.reading_pace_interval),
                    summary = stringResource(
                        R.string.reading_pace_interval_summary,
                        state.readingPaceMinInterval,
                        state.readingPaceMaxInterval,
                    ),
                    expanded = readingPaceIntervalExpanded,
                    onExpandedChange = { readingPaceIntervalExpanded = it },
                ) {
                    NgDockSlider(
                        title = stringResource(R.string.reading_pace_min_interval),
                        valueText = state.readingPaceMinInterval.toString(),
                        minimumText = PACE_INTERVAL_MIN.toString(),
                        maximumText = PACE_INTERVAL_MAX.toString(),
                        value = state.readingPaceMinInterval.toFloat(),
                        valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                        onValueChange = { value ->
                            onReadingPaceMinIntervalChanged(value.roundToInt())
                        },
                        onValueChangeFinished = onReadingPaceIntervalChangeFinished,
                    )
                    NgDockSlider(
                        title = stringResource(R.string.reading_pace_max_interval),
                        valueText = state.readingPaceMaxInterval.toString(),
                        minimumText = PACE_INTERVAL_MIN.toString(),
                        maximumText = PACE_INTERVAL_MAX.toString(),
                        value = state.readingPaceMaxInterval.toFloat(),
                        valueRange = PACE_INTERVAL_MIN.toFloat()..PACE_INTERVAL_MAX.toFloat(),
                        onValueChange = { value ->
                            onReadingPaceMaxIntervalChanged(value.roundToInt())
                        },
                        onValueChangeFinished = onReadingPaceIntervalChangeFinished,
                    )
                }
                AdvancedSwitchSettingItem(
                    title = stringResource(R.string.reading_pace_long_rest),
                    summary = stringResource(R.string.reading_pace_long_rest_summary),
                    checked = state.readingPaceLongRest,
                    onCheckedChange = onReadingPaceLongRestChanged,
                )
                AdvancedSwitchSettingItem(
                    title = stringResource(R.string.reading_pace_quiet_hours),
                    summary = stringResource(R.string.reading_pace_quiet_hours_summary),
                    checked = state.readingPaceQuietHours,
                    onCheckedChange = onReadingPaceQuietHoursChanged,
                )
            }
            AdvancedSwitchSettingItem(
                title = stringResource(R.string.record_log),
                summary = stringResource(R.string.record_debug_log),
                checked = state.recordLog,
                onCheckedChange = onRecordLogChanged,
            )
            AdvancedSwitchSettingItem(
                title = stringResource(R.string.record_network_log),
                summary = stringResource(R.string.record_network_log_summary),
                checked = state.recordNetworkLog,
                onCheckedChange = onRecordNetworkLogChanged,
            )
            AdvancedActionSettingItem(
                title = stringResource(R.string.network_request_log),
                summary = stringResource(R.string.network_request_log_summary),
                onClick = onNetworkRequestLogClick,
            )
            AdvancedSwitchSettingItem(
                title = stringResource(R.string.record_heap_dump_t),
                summary = stringResource(R.string.record_heap_dump_s),
                checked = state.recordHeapDump,
                onCheckedChange = onRecordHeapDumpChanged,
            )
        }
    }
}

@Composable
private fun AdvancedActionSettingItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        summaryMaxLines = 2,
    )
}

@Composable
private fun AdvancedSwitchSettingItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.SWITCH,
        checked = checked,
        onCheckedChange = onCheckedChange,
        onClick = { onCheckedChange(!checked) },
        summaryMaxLines = 2,
    )
}
