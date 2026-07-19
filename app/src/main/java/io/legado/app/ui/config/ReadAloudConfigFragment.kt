package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentDefaultTtsVoiceConfigBinding
import io.legado.app.databinding.FragmentReadAloudConfigBinding
import io.legado.app.databinding.ItemBookCharacterTtsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ReadAloudConfigFragment : BaseFragment(R.layout.fragment_read_aloud_config) {

    private val binding by viewBinding(FragmentReadAloudConfigBinding::bind)
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.read_aloud_settings)
        binding.layoutTtsEngineEntry.setOnClickListener {
            runCardAction {
                requireContext().startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
                }
            }
        }
        binding.layoutMultiRoleEngineEntry.setOnClickListener {
            runCardAction(::showMultiRoleEngineSheet)
        }
        binding.layoutDefaultVoiceEntry.setOnClickListener {
            runCardAction {
                requireContext().startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.DEFAULT_TTS_VOICE_CONFIG)
                }
            }
        }
        refreshContent()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) refreshContent()
    }

    private fun refreshContent() {
        val tint = ColorStateList.valueOf(accentColor)
        binding.textSectionLabel.setTextColor(accentColor)
        binding.imageTtsEngineIcon.imageTintList = tint
        binding.imageMultiRoleEngineIcon.imageTintList = tint
        binding.imageDefaultVoiceIcon.imageTintList = tint
        binding.textMultiRoleEngineSummary.text =
            selectedMultiRoleEngine()?.name
                ?: getString(R.string.multi_role_tts_engine_unset)
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
            titleAction = selectedId?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.clear) to { selectMultiRoleEngine(null) }
            }
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

    private fun selectedMultiRoleEngine(): TtsEngineSetting? {
        return TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
    }

}

class DefaultTtsVoiceConfigFragment : BaseFragment(R.layout.fragment_default_tts_voice_config) {

    private val binding by viewBinding(FragmentDefaultTtsVoiceConfigBinding::bind)
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.default_tts_voice)
        bindCardClick(binding.cardNarrator) { showNarratorVoiceSheet() }
        bindCardClick(binding.cardDialogueMale) { showDialogueVoiceSheet(DialogueGender.MALE) }
        bindCardClick(binding.cardDialogueFemale) {
            showDialogueVoiceSheet(DialogueGender.FEMALE)
        }
        refreshCards()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) refreshCards()
    }

    private fun bindCardClick(
        card: ItemBookCharacterTtsBinding,
        action: () -> Unit
    ) {
        card.root.setOnClickListener {
            if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) action()
        }
    }

    private fun refreshCards() {
        val engine = selectedMultiRoleEngine()
        bindDefaultVoiceCard(
            card = binding.cardNarrator,
            avatar = getString(R.string.default_tts_voice_avatar_narrator),
            avatarBackground = R.drawable.bg_character_avatar_unknown,
            title = getString(R.string.default_narrator_voice),
            summary = narratorSummary(),
            enabled = true
        )
        bindDefaultVoiceCard(
            card = binding.cardDialogueMale,
            avatar = getString(R.string.default_tts_voice_avatar_male),
            avatarBackground = R.drawable.bg_character_avatar_male,
            title = getString(R.string.default_dialogue_male_voice),
            summary = engine?.let {
                dialogueSummary(it, AppConfig.defaultDialogueMaleTtsVoiceId)
            } ?: getString(R.string.default_tts_voice_select_engine_first),
            enabled = engine != null
        )
        bindDefaultVoiceCard(
            card = binding.cardDialogueFemale,
            avatar = getString(R.string.default_tts_voice_avatar_female),
            avatarBackground = R.drawable.bg_character_avatar_female,
            title = getString(R.string.default_dialogue_female_voice),
            summary = engine?.let {
                dialogueSummary(it, AppConfig.defaultDialogueFemaleTtsVoiceId)
            } ?: getString(R.string.default_tts_voice_select_engine_first),
            enabled = engine != null
        )
    }

    private fun bindDefaultVoiceCard(
        card: ItemBookCharacterTtsBinding,
        avatar: String,
        avatarBackground: Int,
        title: String,
        summary: String,
        enabled: Boolean
    ) = card.run {
        tvAvatar.text = avatar
        tvAvatar.setBackgroundResource(avatarBackground)
        tvName.text = title
        tvRole.isVisible = false
        tvVoice.text = summary
        tvStyle.isVisible = false
        tvAction.text = null
        tvAction.setCompoundDrawablesRelativeWithIntrinsicBounds(
            R.drawable.ic_chevron_right_20,
            0,
            0,
            0
        )
        TextViewCompat.setCompoundDrawableTintList(
            tvAction,
            ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant)
            )
        )
        tvAction.background = null
        tvAction.foreground = null
        tvAction.minWidth = 0
        tvAction.setPadding(0, 0, 0, 0)
        tvAction.isClickable = false
        tvAction.isFocusable = false
        tvAction.isEnabled = enabled
        root.isEnabled = enabled
        root.alpha = if (enabled) 1f else 0.55f
    }

    private fun narratorSummary(): String {
        val engine = TtsEngineStore.engine(AppConfig.defaultNarratorTtsEngineId)
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
            engines = { TtsEngineStore.engines().filter { it.enabled } },
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
                refreshCards()
            },
            titleAction = selectedEngineId?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.clear) to {
                    AppConfig.defaultNarratorTtsEngineId = null
                    AppConfig.defaultNarratorTtsVoiceId = null
                    refreshRunningMultiRoleReadAloud(requireContext())
                    refreshCards()
                }
            }
        ).show()
    }

    private fun showDialogueVoiceSheet(gender: DialogueGender) {
        val engine = selectedMultiRoleEngine() ?: return
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
                refreshCards()
            },
            titleAction = selectedVoiceId?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.clear) to {
                    setDialogueVoice(gender, null)
                    refreshRunningMultiRoleReadAloud(requireContext())
                    refreshCards()
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

    private fun selectedMultiRoleEngine(): TtsEngineSetting? {
        return TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
    }

    private enum class DialogueGender { MALE, FEMALE }

}

private fun refreshRunningMultiRoleReadAloud(context: Context) {
    if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
        ReadAloud.refreshTtsRoute(context)
    }
}
