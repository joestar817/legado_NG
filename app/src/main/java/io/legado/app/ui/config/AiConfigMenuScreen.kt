package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

internal data class AiConfigMenuScreenState(
    val providerSummary: String = "",
    val skillSummary: String = "",
    val chatFabEnabled: Boolean = false,
    val chatFabSummary: String = "",
    val purifySummary: String = "",
    val assistantSummary: String = "",
    val readAloudSummary: String = ""
)

@Composable
internal fun AiConfigMenuScreen(
    state: AiConfigMenuScreenState,
    onOpenPage: (String) -> Unit,
    onChatFabChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        NgSettingsSectionLabel(stringResource(R.string.ai_settings_section_features))
        NgSettingsGroup {
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_provider_menu),
                summary = state.providerSummary,
                iconRes = R.drawable.ic_cfg_web,
                onClick = { onOpenPage(AiConfigFragment.PAGE_PROVIDERS) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_prompt_menu),
                summary = state.skillSummary,
                iconRes = R.drawable.ic_ai_skill_puzzle,
                onClick = { onOpenPage(AiConfigFragment.PAGE_PROMPTS) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_chat_fab),
                summary = state.chatFabSummary,
                iconRes = R.drawable.ic_ai_setting,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.chatFabEnabled,
                onCheckedChange = onChatFabChanged,
                onClick = { onChatFabChanged(!state.chatFabEnabled) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_purify),
                summary = state.purifySummary,
                iconRes = R.drawable.ic_ai_purify,
                onClick = { onOpenPage(AiConfigFragment.PAGE_PURIFY) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_assistant),
                summary = state.assistantSummary,
                iconRes = R.drawable.ic_ai_chat_suggestion,
                onClick = { onOpenPage(AiConfigFragment.PAGE_ASSISTANT) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_read_aloud),
                summary = state.readAloudSummary,
                iconRes = R.drawable.ic_ai_capability_tts,
                onClick = { onOpenPage(AiConfigFragment.PAGE_READ_ALOUD) }
            )
        }
    }
}

@Composable
private fun AiConfigMenuEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    trailing: NgSettingsTrailing = NgSettingsTrailing.CHEVRON,
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = trailing,
        checked = checked,
        onCheckedChange = onCheckedChange,
        onClick = onClick,
        trailingSpacing = 0.dp,
        showClickIndication = trailing != NgSettingsTrailing.SWITCH,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}
