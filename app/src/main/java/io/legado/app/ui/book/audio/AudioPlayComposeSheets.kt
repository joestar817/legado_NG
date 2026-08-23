package io.legado.app.ui.book.audio

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.book.read.aloud.ListeningActionRow
import io.legado.app.ui.book.read.aloud.ListeningDivider
import io.legado.app.ui.book.read.aloud.ListeningSettingsGroup
import io.legado.app.ui.book.read.aloud.ReadAloudComposeBottomSheet
import io.legado.app.ui.book.read.aloud.ReadAloudSliderSheet
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSettingsSliderItem
import kotlin.math.roundToInt

internal enum class AudioPlayMoreAction {
    CUSTOM,
    CHANGE_SOURCE,
    LOGIN,
    COPY_URL,
    EDIT_SOURCE,
    TOGGLE_WAKE_LOCK,
    SKIP_CREDITS,
    APP_LOG,
    NETWORK_LOG,
}

internal abstract class AudioPlayComposeBottomSheet : ReadAloudComposeBottomSheet() {
    override fun listeningBook() = AudioPlay.book

    override fun listeningSourceOrigin(): String? = AudioPlay.bookSource?.bookSourceUrl
}

internal class AudioPlayTimerDialog : AudioPlayComposeBottomSheet() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val initialMinute = AudioPlayService.timeMinute.coerceIn(0, AUDIO_TIMER_MAX_MINUTES)
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                var minute by remember(initialMinute) { mutableIntStateOf(initialMinute) }
                ReadAloudSliderSheet(
                    title = if (minute <= 0) {
                        stringResource(R.string.read_aloud_timer_close_title)
                    } else {
                        stringResource(R.string.read_aloud_timer_close_minutes, minute)
                    },
                    value = minute,
                    max = AUDIO_TIMER_MAX_MINUTES,
                    steps = AUDIO_TIMER_MAX_MINUTES - 1,
                    labels = listOf(
                        stringResource(R.string.close),
                        stringResource(R.string.timer_m, 60),
                        stringResource(R.string.timer_m, 120),
                        stringResource(R.string.timer_m, 180),
                    ),
                    onValueChange = { minute = it },
                    onValueCommitted = { AudioPlay.setTimer(minute) },
                )
            }
        }
    }
}

internal class AudioPlaySpeedDialog : AudioPlayComposeBottomSheet() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val initialProgress = ((AudioPlayService.playSpeed.coerceIn(0.5f, 3f) - 0.5f) * 10f)
            .roundToInt()
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                var progress by remember(initialProgress) { mutableIntStateOf(initialProgress) }
                val speed = 0.5f + progress / 10f
                ReadAloudSliderSheet(
                    title = stringResource(
                        R.string.read_aloud_playback_speed_title,
                        "%.1fx".format(speed),
                    ),
                    value = progress,
                    max = AUDIO_SPEED_MAX_PROGRESS,
                    steps = AUDIO_SPEED_MAX_PROGRESS - 1,
                    labels = listOf("0.5x", "3.0x"),
                    onValueChange = { progress = it },
                    onValueCommitted = { AudioPlay.setSpeed(0.5f + progress / 10f) },
                )
            }
        }
    }
}

internal class AudioSkipCreditsDialog : AudioPlayComposeBottomSheet() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val book = AudioPlay.book ?: run {
            dismissAllowingStateLoss()
            return
        }
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                var opening by remember(book.bookUrl) { mutableIntStateOf(book.getOpenCredits()) }
                var ending by remember(book.bookUrl) { mutableIntStateOf(book.getCloseCredits()) }
                NgBottomDrawerSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 18.dp),
                    ) {
                        NgLongDrawerHeader(
                            title = stringResource(R.string.skip_book_credits),
                            centerTitle = true,
                        )
                        NgSettingsSliderItem(
                            title = stringResource(R.string.audio_player_opening_credits),
                            valueText = stringResource(R.string.audio_player_seconds, opening),
                            minimumText = stringResource(R.string.audio_player_seconds, 0),
                            maximumText = stringResource(R.string.audio_player_seconds, 180),
                            value = opening.toFloat(),
                            valueRange = 0f..180f,
                            steps = 179,
                            modifier = Modifier.padding(top = 12.dp),
                            onValueChange = { opening = it.roundToInt().coerceIn(0, 180) },
                            onValueChangeFinished = {
                                book.setOpenCredits(opening)
                                book.save()
                            },
                        )
                        NgSettingsSliderItem(
                            title = stringResource(R.string.audio_player_ending_credits),
                            valueText = stringResource(R.string.audio_player_seconds, ending),
                            minimumText = stringResource(R.string.audio_player_seconds, 0),
                            maximumText = stringResource(R.string.audio_player_seconds, 180),
                            value = ending.toFloat(),
                            valueRange = 0f..180f,
                            steps = 179,
                            modifier = Modifier.padding(top = 8.dp),
                            onValueChange = { ending = it.roundToInt().coerceIn(0, 180) },
                            onValueChangeFinished = {
                                book.setCloseCredits(ending)
                                book.save()
                            },
                        )
                    }
                }
            }
        }
    }
}

internal class AudioPlayMoreDialog : AudioPlayComposeBottomSheet() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? AudioPlayActivity ?: return
        val hasSource = AudioPlay.bookSource != null
        val customVisible = host.hasCustomAudioAction()
        val loginVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                AudioMoreSheet(
                    customVisible = customVisible,
                    loginVisible = loginVisible,
                    hasSource = hasSource,
                    wakeLockEnabled = AppConfig.audioPlayUseWakeLock,
                    onAction = { action ->
                        if (action != AudioPlayMoreAction.TOGGLE_WAKE_LOCK) {
                            dismissAllowingStateLoss()
                        }
                        host.handleMoreAction(action)
                    },
                )
            }
        }
    }
}

@Composable
private fun AudioMoreSheet(
    customVisible: Boolean,
    loginVisible: Boolean,
    hasSource: Boolean,
    wakeLockEnabled: Boolean,
    onAction: (AudioPlayMoreAction) -> Unit,
) {
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    var wakeLock by remember(wakeLockEnabled) { mutableStateOf(wakeLockEnabled) }
    NgBottomDrawerSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.audio_player_more_settings),
                centerTitle = true,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ListeningSettingsGroup(
                        title = stringResource(R.string.audio_player_source_actions),
                    ) {
                        if (customVisible) {
                            ListeningActionRow(
                                title = stringResource(R.string.custom_button),
                                summary = stringResource(R.string.audio_more_custom_summary),
                                onClick = { onAction(AudioPlayMoreAction.CUSTOM) },
                            )
                            ListeningDivider()
                        }
                        ListeningActionRow(
                            title = stringResource(R.string.change_origin),
                            summary = stringResource(R.string.audio_more_change_source_summary),
                            enabled = hasSource,
                            onClick = { onAction(AudioPlayMoreAction.CHANGE_SOURCE) },
                        )
                        if (loginVisible) {
                            ListeningDivider()
                            ListeningActionRow(
                                title = stringResource(R.string.login),
                                summary = stringResource(R.string.audio_more_login_summary),
                                onClick = { onAction(AudioPlayMoreAction.LOGIN) },
                            )
                        }
                        ListeningDivider()
                        ListeningActionRow(
                            title = stringResource(R.string.copy_play_url),
                            summary = stringResource(R.string.audio_more_copy_url_summary),
                            enabled = hasSource,
                            onClick = { onAction(AudioPlayMoreAction.COPY_URL) },
                        )
                        ListeningDivider()
                        ListeningActionRow(
                            title = stringResource(R.string.edit_book_source),
                            summary = stringResource(R.string.audio_more_edit_source_summary),
                            enabled = hasSource,
                            onClick = { onAction(AudioPlayMoreAction.EDIT_SOURCE) },
                        )
                    }
                }
                item {
                    ListeningSettingsGroup(
                        title = stringResource(R.string.audio_player_playback_actions),
                    ) {
                        NgFormSwitchSettingRow(
                            title = stringResource(R.string.audio_play_wake_lock),
                            summary = stringResource(R.string.audio_play_wake_lock_summary),
                            checked = wakeLock,
                            onCheckedChange = {
                                wakeLock = it
                                onAction(AudioPlayMoreAction.TOGGLE_WAKE_LOCK)
                            },
                        )
                        ListeningDivider()
                        ListeningActionRow(
                            title = stringResource(R.string.audio_player_skip_opening),
                            summary = stringResource(R.string.audio_more_skip_opening_summary),
                            onClick = { onAction(AudioPlayMoreAction.SKIP_CREDITS) },
                        )
                    }
                }
                item {
                    ListeningSettingsGroup(
                        title = stringResource(R.string.audio_player_diagnostic_actions),
                    ) {
                        ListeningActionRow(
                            title = stringResource(R.string.log),
                            summary = stringResource(R.string.audio_more_app_log_summary),
                            onClick = { onAction(AudioPlayMoreAction.APP_LOG) },
                        )
                        ListeningDivider()
                        ListeningActionRow(
                            title = stringResource(R.string.network_request_log),
                            summary = stringResource(R.string.network_request_log_summary),
                            onClick = { onAction(AudioPlayMoreAction.NETWORK_LOG) },
                        )
                    }
                }
            }
        }
    }
}

private const val AUDIO_TIMER_MAX_MINUTES = 180
private const val AUDIO_SPEED_MAX_PROGRESS = 25
