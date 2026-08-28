package io.legado.app.ui.book.audio

import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ListeningCartoonType
import io.legado.app.help.config.ListeningFireStyle
import io.legado.app.help.config.ListeningFluidType
import io.legado.app.help.config.ListeningMotionColorMode
import io.legado.app.help.config.ListeningMotionConfig
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.help.config.ReadAloudPlayerDisplayConfig
import io.legado.app.help.config.ReadAloudPlayerDisplaySettings
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.book.read.aloud.ListeningActionRow
import io.legado.app.ui.book.read.aloud.ListeningDivider
import io.legado.app.ui.book.read.aloud.ListeningSettingsGroup
import io.legado.app.ui.book.read.aloud.ReadAloudComposeBottomSheet
import io.legado.app.ui.book.read.aloud.ReadAloudMotionSheetContent
import io.legado.app.ui.book.read.aloud.ReadAloudSliderSheet
import io.legado.app.ui.book.read.aloud.label
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSettingsSliderItem
import io.legado.app.ui.design.theme.NgTheme
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

private enum class AudioMoreScreen {
    SETTINGS,
    MOTION,
}

internal class AudioPlayMoreDialog : AudioPlayComposeBottomSheet() {

    private var screen by mutableStateOf(AudioMoreScreen.SETTINGS)
    private var motionState by mutableStateOf(ListeningMotionConfig.current())
    private var displaySettings by mutableStateOf(ReadAloudPlayerDisplayConfig.current())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as? AudioPlayActivity ?: return
        val hasSource = AudioPlay.bookSource != null
        val customVisible = host.hasCustomAudioAction()
        val loginVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        screen = AudioMoreScreen.SETTINGS
        motionState = ListeningMotionConfig.current()
        displaySettings = ReadAloudPlayerDisplayConfig.current()
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                BackHandler(enabled = screen == AudioMoreScreen.MOTION) {
                    screen = AudioMoreScreen.SETTINGS
                }
                when (screen) {
                    AudioMoreScreen.SETTINGS -> AudioMoreSheet(
                        customVisible = customVisible,
                        loginVisible = loginVisible,
                        hasSource = hasSource,
                        hasLyricsPage = host.hasLyricsPage(),
                        wakeLockEnabled = AppConfig.audioPlayUseWakeLock,
                        displaySettings = displaySettings,
                        motionState = motionState,
                        onShowPageIndicatorChange = ::setShowPageIndicator,
                        onShowCoverChange = ::setShowCover,
                        onShowBookNameChange = ::setShowBookName,
                        onShowSubtitleChange = ::setShowSubtitle,
                        onOpenMotion = { screen = AudioMoreScreen.MOTION },
                        onAction = { action ->
                            if (action != AudioPlayMoreAction.TOGGLE_WAKE_LOCK) {
                                dismissAllowingStateLoss()
                            }
                            host.handleMoreAction(action)
                        },
                    )

                    AudioMoreScreen.MOTION -> ReadAloudMotionSheetContent(
                        state = motionState,
                        onBack = { screen = AudioMoreScreen.SETTINGS },
                        onEnabledChange = ::setMotionEnabled,
                        onEffectChange = ::setMotionEffect,
                        onFireStyleChange = ::setFireStyle,
                        onFluidTypeChange = ::setFluidType,
                        onCartoonTypeChange = ::setCartoonType,
                        onColorModeChange = ::setMotionColorMode,
                        onCustomColorChange = ::setMotionCustomColor,
                        onIntensityPreview = ::previewMotionIntensity,
                        onIntensityCommitted = ::commitMotionIntensity,
                    )
                }
            }
        }
    }

    private fun setShowPageIndicator(enabled: Boolean) {
        ReadAloudPlayerDisplayConfig.showPageIndicator = enabled
        updateDisplaySettings(displaySettings.copy(showPageIndicator = enabled))
    }

    private fun setShowCover(enabled: Boolean) {
        ReadAloudPlayerDisplayConfig.showCover = enabled
        updateDisplaySettings(displaySettings.copy(showCover = enabled))
    }

    private fun setShowBookName(enabled: Boolean) {
        ReadAloudPlayerDisplayConfig.showBookName = enabled
        updateDisplaySettings(displaySettings.copy(showBookName = enabled))
    }

    private fun setShowSubtitle(enabled: Boolean) {
        ReadAloudPlayerDisplayConfig.showSubtitle = enabled
        updateDisplaySettings(displaySettings.copy(showSubtitle = enabled))
    }

    private fun updateDisplaySettings(settings: ReadAloudPlayerDisplaySettings) {
        displaySettings = settings
        (activity as? AudioPlayActivity)?.previewDisplaySettings(settings)
    }

    private fun setMotionEnabled(enabled: Boolean) {
        ListeningMotionConfig.enabled = enabled
        motionState = motionState.copy(enabled = enabled)
        notifyMotionChanged()
    }

    private fun setMotionEffect(effect: ListeningMotionEffect) {
        ListeningMotionConfig.effect = effect
        motionState = motionState.copy(
            effect = effect,
            intensity = ListeningMotionConfig.intensityFor(effect),
        )
        notifyMotionChanged()
    }

    private fun setFireStyle(style: ListeningFireStyle) {
        ListeningMotionConfig.fireStyle = style
        motionState = motionState.copy(fireStyle = style)
        notifyMotionChanged()
    }

    private fun setFluidType(type: ListeningFluidType) {
        ListeningMotionConfig.fluidType = type
        motionState = motionState.copy(fluidType = type)
        notifyMotionChanged()
    }

    private fun setCartoonType(type: ListeningCartoonType) {
        ListeningMotionConfig.cartoonType = type
        motionState = motionState.copy(cartoonType = type)
        notifyMotionChanged()
    }

    private fun setMotionColorMode(mode: ListeningMotionColorMode) {
        ListeningMotionConfig.colorMode = mode
        motionState = motionState.copy(colorMode = mode)
        notifyMotionChanged()
    }

    private fun setMotionCustomColor(color: Int) {
        ListeningMotionConfig.customColor = color
        motionState = motionState.copy(customColor = color)
        notifyMotionChanged()
    }

    private fun previewMotionIntensity(intensity: Int) {
        motionState = motionState.copy(intensity = intensity.coerceIn(0, 100))
        notifyMotionChanged()
    }

    private fun commitMotionIntensity() {
        when (motionState.effect) {
            ListeningMotionEffect.FLAME -> ListeningMotionConfig.intensity = motionState.intensity
            ListeningMotionEffect.FLUID -> {
                ListeningMotionConfig.fluidIntensity = motionState.intensity
            }
            ListeningMotionEffect.CARTOON -> {
                ListeningMotionConfig.cartoonIntensity = motionState.intensity
            }
        }
        notifyMotionChanged()
    }

    private fun notifyMotionChanged() {
        (activity as? AudioPlayActivity)?.previewMotionSettings(motionState)
        refreshListeningSheetTheme(motionState)
    }
}

@Composable
private fun AudioMoreSheet(
    customVisible: Boolean,
    loginVisible: Boolean,
    hasSource: Boolean,
    hasLyricsPage: Boolean,
    wakeLockEnabled: Boolean,
    displaySettings: ReadAloudPlayerDisplaySettings,
    motionState: ListeningMotionSettings,
    onShowPageIndicatorChange: (Boolean) -> Unit,
    onShowCoverChange: (Boolean) -> Unit,
    onShowBookNameChange: (Boolean) -> Unit,
    onShowSubtitleChange: (Boolean) -> Unit,
    onOpenMotion: () -> Unit,
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
                        title = stringResource(R.string.listening_display_settings),
                    ) {
                        if (hasLyricsPage) {
                            NgFormSwitchSettingRow(
                                title = stringResource(
                                    R.string.listening_display_page_indicator
                                ),
                                summary = stringResource(
                                    R.string.listening_display_page_indicator_summary
                                ),
                                checked = displaySettings.showPageIndicator,
                                onCheckedChange = onShowPageIndicatorChange,
                            )
                            ListeningDivider()
                        }
                        NgFormSwitchSettingRow(
                            title = stringResource(R.string.listening_display_cover),
                            checked = displaySettings.showCover,
                            onCheckedChange = onShowCoverChange,
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = stringResource(R.string.listening_display_book_name),
                            checked = displaySettings.showBookName,
                            onCheckedChange = onShowBookNameChange,
                        )
                        ListeningDivider()
                        NgFormSwitchSettingRow(
                            title = stringResource(R.string.listening_display_chapter_lyrics),
                            checked = displaySettings.showSubtitle,
                            onCheckedChange = onShowSubtitleChange,
                        )
                    }
                }
                item {
                    ListeningActionRow(
                        title = stringResource(R.string.listening_motion_entry),
                        summary = if (!motionState.enabled) {
                            stringResource(R.string.close)
                        } else {
                            when (motionState.effect) {
                                ListeningMotionEffect.FLAME -> stringResource(
                                    R.string.listening_motion_flame_summary,
                                    motionState.fireStyle.label(),
                                )
                                ListeningMotionEffect.FLUID -> stringResource(
                                    R.string.listening_motion_fluid_summary,
                                    motionState.fluidType.label(),
                                )
                                ListeningMotionEffect.CARTOON -> stringResource(
                                    R.string.listening_motion_cartoon_summary,
                                    motionState.cartoonType.label(),
                                )
                            }
                        },
                        leadingIcon = Icons.Rounded.AutoAwesome,
                        onClick = onOpenMotion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(NgTheme.colors.surface).copy(alpha = 0.82f)),
                    )
                }
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
