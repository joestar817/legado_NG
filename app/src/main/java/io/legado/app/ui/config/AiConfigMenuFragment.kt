package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.help.ai.AiChatEntryStyle
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.ui.design.theme.NgAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiConfigMenuFragment : BaseFragment(R.layout.fragment_ai_config_menu) {

    private data class ModelSelection(
        val provider: AiProviderSetting,
        val model: AiModel
    )

    private var screenState by mutableStateOf(AiConfigMenuScreenState())
    private var skillSummaryJob: Job? = null
    private var navigationPending = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.ai_setting)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiConfigMenuScreen(
                        state = screenState,
                        onOpenPage = ::openPage,
                        onChatEntryStyleChanged = ::updateChatEntryStyle,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        navigationPending = false
        activity?.setTitle(R.string.ai_setting)
        refreshContent()
    }

    override fun onDestroyView() {
        skillSummaryJob?.cancel()
        skillSummaryJob = null
        super.onDestroyView()
    }

    private fun openPage(page: String) {
        if (navigationPending) return
        val configActivity = activity as? ConfigActivity ?: return
        navigationPending = true
        configActivity.openAiConfigPage(page)
    }

    private fun refreshContent() {
        val providers = AiProviderStore.providers()
        screenState = AiConfigMenuScreenState(
            providerSummary = getString(
                R.string.ai_provider_menu_summary,
                providers.size.toString()
            ),
            skillSummary = screenState.skillSummary,
            chatEntryStyle = AiConfig.chatEntryStyle,
            purifySummary = getString(
                R.string.ai_model_function_summary,
                purifyModelSummary(providers),
                purifyReasoningSummary(providers)
            ),
            assistantSummary = getString(
                R.string.ai_model_function_summary,
                assistantModelSummary(providers),
                assistantReasoningSummary(providers)
            ),
            readAloudSummary = getString(
                R.string.ai_model_function_summary,
                readAloudModelSummary(providers),
                readAloudReasoningSummary(providers)
            )
        )
        refreshSkillSummary()
    }

    private fun updateChatEntryStyle(style: AiChatEntryStyle) {
        AiConfig.chatEntryStyle = style
        screenState = screenState.copy(
            chatEntryStyle = AiConfig.chatEntryStyle
        )
    }

    private fun refreshSkillSummary() {
        skillSummaryJob?.cancel()
        skillSummaryJob = viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                AiSkillRegistry.managementSkills().size
            }
            if (view == null) return@launch
            screenState = screenState.copy(
                skillSummary = getString(
                    R.string.ai_prompt_menu_summary,
                    count.toString()
                )
            )
        }
    }

    private fun purifyModelSummary(providers: List<AiProviderSetting>): String {
        val selected = selectedPurifyModel(providers)
        return when {
            selected == null && AiConfig.purifyModelId.isBlank() ->
                getString(R.string.ai_purify_model_not_selected)
            selected == null -> getString(R.string.ai_purify_model_unavailable)
            else -> selected.modelSummary()
        }
    }

    private fun purifyReasoningSummary(providers: List<AiProviderSetting>): String {
        val selected = selectedPurifyModel(providers)
        return when {
            selected == null -> getString(R.string.ai_purify_reasoning_select_model_first)
            !selected.model.supportsReasoning() ->
                getString(R.string.ai_purify_reasoning_unsupported)
            else -> AiConfig.purifyReasoningLevel.displayName()
        }
    }

    private fun assistantModelSummary(providers: List<AiProviderSetting>): String {
        val selected = selectedAssistantModel(providers)
        return when {
            selected == null && AiConfig.assistantModelId.isBlank() ->
                getString(R.string.ai_assistant_model_not_selected)
            selected == null -> getString(R.string.ai_assistant_model_unavailable)
            else -> selected.modelSummary()
        }
    }

    private fun assistantReasoningSummary(providers: List<AiProviderSetting>): String {
        val selected = selectedAssistantModel(providers)
        return when {
            selected == null -> getString(R.string.ai_assistant_reasoning_select_model_first)
            !selected.model.supportsReasoning() ->
                getString(R.string.ai_assistant_reasoning_unsupported)
            else -> AiConfig.assistantReasoningLevel.displayName()
        }
    }

    private fun readAloudModelSummary(providers: List<AiProviderSetting>): String {
        val selected = selectedReadAloudModel(providers)
        return when {
            selected == null && AiConfig.readAloudStoryboardModelId.isBlank() ->
                getString(R.string.ai_read_aloud_storyboard_model_not_selected)
            selected == null -> getString(R.string.ai_read_aloud_storyboard_model_unavailable)
            else -> selected.modelSummary()
        }
    }

    private fun readAloudReasoningSummary(providers: List<AiProviderSetting>): String {
        val selected = selectedReadAloudModel(providers)
        return when {
            selected == null -> getString(R.string.ai_read_aloud_reasoning_select_model_first)
            !selected.model.supportsReasoning() ->
                getString(R.string.ai_read_aloud_reasoning_unsupported)
            else -> AiConfig.readAloudStoryboardReasoningLevel.displayName()
        }
    }

    private fun selectedPurifyModel(providers: List<AiProviderSetting>): ModelSelection? {
        return selectedChatModel(
            providers = providers,
            providerId = AiConfig.purifyProviderId,
            modelId = AiConfig.purifyModelId,
            assistantOnly = false
        )
    }

    private fun selectedAssistantModel(providers: List<AiProviderSetting>): ModelSelection? {
        return selectedChatModel(
            providers = providers,
            providerId = AiConfig.assistantProviderId,
            modelId = AiConfig.assistantModelId,
            assistantOnly = true
        )
    }

    private fun selectedReadAloudModel(providers: List<AiProviderSetting>): ModelSelection? {
        return selectedChatModel(
            providers = providers,
            providerId = AiConfig.readAloudStoryboardProviderId,
            modelId = AiConfig.readAloudStoryboardModelId,
            assistantOnly = false
        )
    }

    private fun selectedChatModel(
        providers: List<AiProviderSetting>,
        providerId: String,
        modelId: String,
        assistantOnly: Boolean
    ): ModelSelection? {
        if (providerId.isBlank() || modelId.isBlank()) return null
        val provider = providers.firstOrNull { it.id == providerId }
            ?.takeIf { it.enabled }
            ?: return null
        val models = if (assistantOnly) {
            provider.assistantEligibleModels()
        } else {
            val availableIds = provider.effectiveAvailableModelIds().toSet()
            provider.displayModels()
                .filter { it.safeId() in availableIds }
                .filter { it.supportsChatText() }
        }
        val model = models.firstOrNull { it.safeId() == modelId } ?: return null
        return ModelSelection(provider, model)
    }

    private fun ModelSelection.modelSummary(): String {
        return getString(
            R.string.ai_purify_model_selected_summary,
            model.displayName(),
            provider.name
        )
    }

    private fun AiReasoningLevel.displayName(): String {
        return getString(
            when (this) {
                AiReasoningLevel.OFF -> R.string.ai_reasoning_level_off
                AiReasoningLevel.AUTO -> R.string.ai_reasoning_level_auto
                AiReasoningLevel.LOW -> R.string.ai_reasoning_level_low
                AiReasoningLevel.MEDIUM -> R.string.ai_reasoning_level_medium
                AiReasoningLevel.HIGH -> R.string.ai_reasoning_level_high
                AiReasoningLevel.ULTRA -> R.string.ai_reasoning_level_ultra
            }
        )
    }
}
