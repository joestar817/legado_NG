package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel

internal data class ReadAloudConfigScreenState(
    val multiRoleEngineSummary: String = "",
    val mediaButtonOnExit: Boolean = true,
    val readAloudByMediaButton: Boolean = false,
    val ignoreAudioFocus: Boolean = false
)

@Composable
internal fun ReadAloudConfigScreen(
    state: ReadAloudConfigScreenState,
    onOpenTtsEngine: () -> Unit,
    onOpenMultiRoleEngine: () -> Unit,
    onOpenDefaultVoice: () -> Unit,
    onMediaButtonOnExitChanged: (Boolean) -> Unit,
    onReadAloudByMediaButtonChanged: (Boolean) -> Unit,
    onIgnoreAudioFocusChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        NgSettingsSectionLabel(stringResource(R.string.read_aloud_settings_section_features))
        NgSettingsGroup {
            ReadAloudConfigEntry(
                title = stringResource(R.string.tts_engine_settings),
                summary = stringResource(R.string.tts_engine_settings_summary),
                iconRes = R.drawable.ic_ai_capability_tts,
                onClick = onOpenTtsEngine
            )
            ReadAloudConfigEntry(
                title = stringResource(R.string.multi_role_tts_engine),
                summary = state.multiRoleEngineSummary,
                iconRes = R.drawable.ic_groups,
                onClick = onOpenMultiRoleEngine
            )
            ReadAloudConfigEntry(
                title = stringResource(R.string.default_tts_voice),
                summary = stringResource(R.string.default_tts_voice_summary),
                iconRes = R.drawable.ic_tts_tab_voice,
                onClick = onOpenDefaultVoice
            )
        }

        NgSettingsSectionLabel(stringResource(R.string.read_aloud_settings_section_controls))
        NgSettingsGroup {
            ReadAloudConfigSwitchEntry(
                title = stringResource(R.string.media_button_on_exit_title),
                summary = stringResource(R.string.media_button_on_exit_summary),
                iconRes = R.drawable.ic_play_outline_24dp,
                checked = state.mediaButtonOnExit,
                onCheckedChange = onMediaButtonOnExitChanged
            )
            ReadAloudConfigSwitchEntry(
                title = stringResource(R.string.read_aloud_by_media_button_title),
                summary = stringResource(R.string.read_aloud_by_media_button_summary),
                iconRes = R.drawable.ic_tts_headphones,
                checked = state.readAloudByMediaButton,
                onCheckedChange = onReadAloudByMediaButtonChanged
            )
            ReadAloudConfigSwitchEntry(
                title = stringResource(R.string.ignore_audio_focus_title),
                summary = stringResource(R.string.ignore_audio_focus_summary),
                iconRes = R.drawable.ic_volume_up,
                checked = state.ignoreAudioFocus,
                onCheckedChange = onIgnoreAudioFocusChanged
            )
        }
    }
}

@Composable
private fun ReadAloudConfigSwitchEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.SWITCH,
        checked = checked,
        onCheckedChange = onCheckedChange,
        onClick = { onCheckedChange(!checked) },
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}

@Composable
private fun ReadAloudConfigEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}
