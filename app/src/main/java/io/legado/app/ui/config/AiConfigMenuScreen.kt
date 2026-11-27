package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.help.pet.PetChoice
import io.legado.app.R
import io.legado.app.help.ai.AiChatEntryStyle
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem

internal data class AiConfigMenuScreenState(
    val providerSummary: String = "",
    val skillSummary: String = "",
    val chatEntryStyle: AiChatEntryStyle = AiChatEntryStyle.NONE,
    val installedPets: List<PetChoice> = emptyList(),
    val purifySummary: String = "",
    val characterCardSummary: String = "",
    val assistantSummary: String = "",
    val readAloudSummary: String = ""
)

@Composable
internal fun AiConfigMenuScreen(
    state: AiConfigMenuScreenState,
    onOpenPage: (String) -> Unit,
    onManagePets: () -> Unit,
) {
    val searchSettings by AiExternalSearchStore.changes.collectAsState()
    val availableServices = searchSettings.services.filter { it.available }
    val selectedPet = state.installedPets.firstOrNull { it.id == state.chatEntryStyle.id }
    val builtinTitle = stringResource(
        if (state.chatEntryStyle == AiChatEntryStyle.NONE) R.string.ai_chat_entry_none else R.string.ai_chat_entry_button
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        NgSettingsGroup {
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_provider_menu),
                summary = state.providerSummary,
                iconRes = R.drawable.ic_cfg_web,
                onClick = { onOpenPage(AiConfigFragment.PAGE_PROVIDERS) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_search_services),
                summary = when (availableServices.size) {
                    0 -> stringResource(R.string.ai_search_service_disabled)
                    1 -> stringResource(R.string.ai_search_service_summary, availableServices.single().displayName)
                    else -> stringResource(R.string.ai_search_services_enabled, availableServices.size)
                },
                iconRes = R.drawable.ic_search,
                onClick = { onOpenPage(AiConfigFragment.PAGE_SEARCH_SERVICES) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_prompt_menu),
                summary = state.skillSummary,
                iconRes = R.drawable.ic_ai_skill_puzzle,
                onClick = { onOpenPage(AiConfigFragment.PAGE_PROMPTS) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_chat_entry_menu),
                summary = selectedPet?.name ?: builtinTitle,
                iconRes = R.drawable.ic_ai_setting,
                onClick = onManagePets
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_purify),
                summary = state.purifySummary,
                iconRes = R.drawable.ic_ai_purify,
                onClick = { onOpenPage(AiConfigFragment.PAGE_PURIFY) }
            )
            AiConfigMenuEntry(
                title = stringResource(R.string.ai_character_card),
                summary = state.characterCardSummary,
                iconRes = R.drawable.ic_read_menu_characters,
                onClick = { onOpenPage(AiConfigFragment.PAGE_CHARACTER_CARD) }
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
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        trailingSpacing = 0.dp,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}
