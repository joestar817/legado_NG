package io.legado.app.ui.config

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadAloudConfigFragment : BaseFragment(R.layout.fragment_read_aloud_config) {

    private var screenState by mutableStateOf(ReadAloudConfigScreenState())
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()
    private var summaryJob: Job? = null
    private var skipNextResumeRefresh = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.read_aloud_settings)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    ReadAloudConfigScreen(
                        state = screenState,
                        onOpenTtsEngine = {
                            runCardAction {
                                requireContext().startActivity<ConfigActivity> {
                                    putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
                                }
                            }
                        },
                        onOpenMultiRoleEngine = {
                            runCardAction(::showMultiRoleEngineSheet)
                        },
                        onOpenDefaultVoice = {
                            runCardAction {
                                requireContext().startActivity<ConfigActivity> {
                                    putExtra("configTag", ConfigTag.DEFAULT_TTS_VOICE_CONFIG)
                                }
                            }
                        }
                    )
                }
            }
        }
        skipNextResumeRefresh = true
    }

    override fun onResume() {
        super.onResume()
        if (skipNextResumeRefresh) {
            skipNextResumeRefresh = false
        } else if (view != null) {
            refreshContent()
        }
    }

    override fun onDestroyView() {
        summaryJob?.cancel()
        summaryJob = null
        skipNextResumeRefresh = false
        super.onDestroyView()
    }

    private fun refreshContent() {
        refreshMultiRoleEngineSummary()
    }

    private fun refreshMultiRoleEngineSummary() {
        summaryJob?.cancel()
        val selectedId = AppConfig.multiRoleTtsEngineId
        if (selectedId.isNullOrBlank()) {
            screenState = screenState.copy(
                multiRoleEngineSummary = getString(R.string.multi_role_tts_engine_unset)
            )
            return
        }
        summaryJob = viewLifecycleOwner.lifecycleScope.launch {
            val selectedName = withContext(Dispatchers.IO) {
                TtsEngineStore.engine(selectedId)
                    ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                    ?.name
            }
            if (view == null) return@launch
            screenState = screenState.copy(
                multiRoleEngineSummary = selectedName
                    ?: getString(R.string.multi_role_tts_engine_unset)
            )
        }
    }

    private fun runCardAction(action: () -> Unit) {
        if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) action()
    }

    private fun showMultiRoleEngineSheet() {
        val selectedId = AppConfig.multiRoleTtsEngineId
        TtsEngineSelectionSheet(
            context = requireContext(),
            title = getString(R.string.multi_role_tts_engine),
            searchHint = getString(R.string.multi_role_tts_engine_search),
            emptyText = getString(R.string.multi_role_tts_engine_empty),
            engines = TtsEngineStore.engines().filter {
                it.enabled && it.type == TtsEngineType.SCRIPT
            },
            selectedEngineId = selectedId,
            onSelect = { engine -> selectMultiRoleEngine(engine.id) },
            onClear = selectedId?.takeIf { it.isNotBlank() }?.let {
                { selectMultiRoleEngine(null) }
            },
        ).show()
    }

    private fun selectMultiRoleEngine(engineId: String?) {
        val changed = AppConfig.multiRoleTtsEngineId != engineId
        AppConfig.multiRoleTtsEngineId = engineId
        if (changed) {
            val hadDialogueVoice = !AppConfig.defaultDialogueMaleTtsVoiceId.isNullOrBlank() ||
                !AppConfig.defaultDialogueFemaleTtsVoiceId.isNullOrBlank()
            AppConfig.defaultDialogueMaleTtsVoiceId = null
            AppConfig.defaultDialogueFemaleTtsVoiceId = null
            if (hadDialogueVoice && engineId != null) {
                requireContext().toastOnUi(R.string.default_tts_voice_changed_engine)
            }
        }
        refreshRunningMultiRoleReadAloud(requireContext())
        refreshContent()
    }

}

class DefaultTtsVoiceConfigFragment : BaseFragment(R.layout.fragment_default_tts_voice_config) {

    private var screenState by mutableStateOf<DefaultTtsVoiceConfigScreenState?>(null)
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()
    private var engineSnapshot = emptyList<TtsEngineSetting>()
    private var engineSnapshotLoaded = false
    private var refreshCardsJob: Job? = null
    private var skipNextResumeRefresh = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.default_tts_voice)
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    screenState?.let { state ->
                        DefaultTtsVoiceConfigScreen(
                            state = state,
                            onAction = ::handleScreenAction
                        )
                    }
                }
            }
        }
        refreshCards()
        skipNextResumeRefresh = true
    }

    override fun onResume() {
        super.onResume()
        if (skipNextResumeRefresh) {
            skipNextResumeRefresh = false
        } else if (view != null) {
            refreshCards()
        }
    }

    override fun onDestroyView() {
        refreshCardsJob?.cancel()
        refreshCardsJob = null
        skipNextResumeRefresh = false
        super.onDestroyView()
    }

    private fun handleScreenAction(action: DefaultTtsVoiceConfigScreenAction) {
        if (!cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) return
        when (action) {
            DefaultTtsVoiceConfigScreenAction.NarratorClicked -> showNarratorVoiceSheet()
            DefaultTtsVoiceConfigScreenAction.DialogueMaleClicked -> {
                showDialogueVoiceSheet(DialogueGender.MALE)
            }
            DefaultTtsVoiceConfigScreenAction.DialogueFemaleClicked -> {
                showDialogueVoiceSheet(DialogueGender.FEMALE)
            }
        }
    }

    private fun refreshCards() {
        bindCards(engineSnapshot, loading = !engineSnapshotLoaded)
        refreshCardsJob?.cancel()
        refreshCardsJob = viewLifecycleOwner.lifecycleScope.launch {
            val engines = withContext(Dispatchers.IO) {
                TtsEngineStore.engines()
            }
            if (view == null) return@launch
            engineSnapshot = engines
            engineSnapshotLoaded = true
            bindCards(engines)
        }
    }

    private fun bindCards(
        engines: List<TtsEngineSetting>,
        loading: Boolean = false
    ) {
        val engine = selectedMultiRoleEngine(engines)
        val fallbackTag = getString(R.string.character_tts_dialogue_fallback)
        screenState = DefaultTtsVoiceConfigScreenState(
            narrator = DefaultTtsVoiceCardUiModel(
                slot = DefaultTtsVoiceSlot.NARRATOR,
                title = getString(R.string.default_narrator_voice),
                summary = if (loading) {
                    getString(R.string.loading)
                } else {
                    narratorSummary(engines)
                },
                avatarText = getString(R.string.default_tts_voice_avatar_narrator),
                avatarRole = DefaultTtsVoiceAvatarRole.NARRATOR
            ),
            dialogueMale = DefaultTtsVoiceCardUiModel(
                slot = DefaultTtsVoiceSlot.DIALOGUE_MALE,
                title = getString(R.string.default_dialogue_male_voice),
                summary = if (loading) {
                    getString(R.string.loading)
                } else {
                    engine?.let {
                        dialogueSummary(it, AppConfig.defaultDialogueMaleTtsVoiceId)
                    } ?: getString(R.string.default_tts_voice_select_engine_first)
                },
                avatarText = getString(R.string.default_tts_voice_avatar_male),
                avatarRole = DefaultTtsVoiceAvatarRole.MALE,
                fallbackTag = fallbackTag,
                enabled = !loading && engine != null
            ),
            dialogueFemale = DefaultTtsVoiceCardUiModel(
                slot = DefaultTtsVoiceSlot.DIALOGUE_FEMALE,
                title = getString(R.string.default_dialogue_female_voice),
                summary = if (loading) {
                    getString(R.string.loading)
                } else {
                    engine?.let {
                        dialogueSummary(it, AppConfig.defaultDialogueFemaleTtsVoiceId)
                    } ?: getString(R.string.default_tts_voice_select_engine_first)
                },
                avatarText = getString(R.string.default_tts_voice_avatar_female),
                avatarRole = DefaultTtsVoiceAvatarRole.FEMALE,
                fallbackTag = fallbackTag,
                enabled = !loading && engine != null
            )
        )
    }

    private fun narratorSummary(engines: List<TtsEngineSetting>): String {
        val engine = engines.firstOrNull { it.id == AppConfig.defaultNarratorTtsEngineId }
            ?.takeIf { it.enabled }
            ?: return getString(R.string.default_tts_voice_unset)
        if (engine.type == TtsEngineType.SYSTEM) {
            return getString(
                R.string.character_tts_engine_voice,
                getString(R.string.character_tts_system_default_voice),
                engine.name
            )
        }
        val voice = engine.enabledVoices()
            .firstOrNull { it.id == AppConfig.defaultNarratorTtsVoiceId }
            ?: return getString(R.string.default_tts_voice_unset)
        return getString(R.string.character_tts_engine_voice, voice.name, engine.name)
    }

    private fun dialogueSummary(engine: TtsEngineSetting, voiceId: String?): String {
        val voice = engine.enabledVoices().firstOrNull { it.id == voiceId }
            ?: return getString(R.string.default_tts_voice_unset)
        return getString(R.string.character_tts_engine_voice, voice.name, engine.name)
    }

    private fun showNarratorVoiceSheet() {
        val selectedEngineId = AppConfig.defaultNarratorTtsEngineId
        val selectedVoiceId = AppConfig.defaultNarratorTtsVoiceId
        TtsVoiceSelectionSheet(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            title = getString(R.string.default_narrator_voice),
            searchHint = getString(R.string.default_tts_voice_search),
            emptyText = getString(R.string.default_tts_voice_empty),
            engines = {
                (engineSnapshot.takeIf { engineSnapshotLoaded } ?: TtsEngineStore.engines())
                    .filter { it.enabled }
            },
            isSelected = { option ->
                selectedEngineId == option.engine.id && if (option.systemDefault) {
                    selectedVoiceId.isNullOrBlank()
                } else {
                    selectedVoiceId == option.voice.id
                }
            },
            onSelect = { option ->
                AppConfig.defaultNarratorTtsEngineId = option.engine.id
                AppConfig.defaultNarratorTtsVoiceId = option.voice.id
                    .takeUnless { option.systemDefault }
                refreshRunningMultiRoleReadAloud(requireContext())
                bindCards(engineSnapshot)
            },
            titleAction = selectedEngineId?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.clear) to {
                    AppConfig.defaultNarratorTtsEngineId = null
                    AppConfig.defaultNarratorTtsVoiceId = null
                    refreshRunningMultiRoleReadAloud(requireContext())
                    bindCards(engineSnapshot)
                }
            }
        ).show()
    }

    private fun showDialogueVoiceSheet(gender: DialogueGender) {
        val engine = selectedMultiRoleEngine(engineSnapshot) ?: return
        val selectedVoiceId = when (gender) {
            DialogueGender.MALE -> AppConfig.defaultDialogueMaleTtsVoiceId
            DialogueGender.FEMALE -> AppConfig.defaultDialogueFemaleTtsVoiceId
        }
        TtsVoiceSelectionSheet(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            title = getString(
                if (gender == DialogueGender.MALE) {
                    R.string.default_dialogue_male_voice
                } else {
                    R.string.default_dialogue_female_voice
                }
            ),
            searchHint = getString(R.string.default_tts_voice_search),
            emptyText = getString(R.string.default_tts_voice_empty),
            engines = { listOf(engine) },
            isSelected = { option -> selectedVoiceId == option.voice.id },
            onSelect = { option ->
                setDialogueVoice(gender, option.voice.id)
                refreshRunningMultiRoleReadAloud(requireContext())
                bindCards(engineSnapshot)
            },
            titleAction = selectedVoiceId?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.clear) to {
                    setDialogueVoice(gender, null)
                    refreshRunningMultiRoleReadAloud(requireContext())
                    bindCards(engineSnapshot)
                }
            }
        ).show()
    }

    private fun setDialogueVoice(gender: DialogueGender, voiceId: String?) {
        when (gender) {
            DialogueGender.MALE -> AppConfig.defaultDialogueMaleTtsVoiceId = voiceId
            DialogueGender.FEMALE -> AppConfig.defaultDialogueFemaleTtsVoiceId = voiceId
        }
    }

    private fun selectedMultiRoleEngine(
        engines: List<TtsEngineSetting> = engineSnapshot
    ): TtsEngineSetting? {
        return engines.firstOrNull { it.id == AppConfig.multiRoleTtsEngineId }
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
    }

    private enum class DialogueGender { MALE, FEMALE }

}

private fun refreshRunningMultiRoleReadAloud(context: Context) {
    if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
        ReadAloud.refreshTtsRoute(context)
    }
}
