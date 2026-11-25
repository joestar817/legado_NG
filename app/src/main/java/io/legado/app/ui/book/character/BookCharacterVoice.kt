package io.legado.app.ui.book.character

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.BookTtsBindingPolicy
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsVoiceSelectionSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun FragmentActivity.showBookCharacterVoiceSheet(
    title: String,
    target: BookCharacterTtsBinding,
    binding: BookCharacterTtsBinding?,
    onCatalogRefreshed: () -> Unit,
) {
    val narrator = target.targetType == BookCharacterTtsBinding.TargetType.NARRATOR
    val selected = binding?.takeIf { narrator || it.engineId == AppConfig.multiRoleTtsEngineId }
    val database = appDb
    val dao = database.bookCharacterDao

    fun changeBinding(message: Int, write: () -> Unit) {
        lifecycleScope.launch {
            withContext(IO) { write() }
            toastOnUi(getString(message))
            refreshRunningReadAloud()
        }
    }

    fun selectVoice(engineId: String, voiceId: String?, bindingMode: String) {
        changeBinding(R.string.character_tts_binding_saved) {
            database.runInTransaction {
                val previousEngine = selected?.engineId?.takeIf { narrator && it.isNotBlank() } ?: engineId
                val current = dao.getTtsBindings(target.workKey).firstOrNull {
                    it.targetType == target.targetType && it.targetId == target.targetId &&
                        it.engineId == previousEngine
                }
                if (previousEngine != engineId) {
                    dao.deleteTtsBinding(target.workKey, target.targetType, target.targetId, previousEngine)
                }
                dao.upsertTtsBinding((current ?: target).withSelectedVoice(engineId, voiceId, bindingMode))
            }
        }
    }

    TtsVoiceSelectionSheet(
        context = this,
        lifecycleScope = lifecycleScope,
        title = title,
        searchHint = getString(R.string.default_tts_voice_search),
        emptyText = getString(R.string.character_tts_no_voice_options),
        engines = {
            if (narrator) {
                TtsEngineStore.engines().filter { it.enabled }
            } else {
                listOfNotNull(
                    TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
                        ?.takeIf { it.isSelectable && it.usesGeneratedAudio }
                )
            }
        },
        isSelected = { option ->
            val voiceId = selected?.voiceId
            selected?.engineId == option.engine.id &&
                if (option.systemDefault) voiceId.isNullOrBlank() else voiceId == option.voice.id
        },
        onSelect = { option ->
            selectVoice(
                engineId = option.engine.id,
                voiceId = option.voice.id.takeUnless { option.systemDefault },
                bindingMode = BookCharacterTtsBinding.BindingMode.MANUAL,
            )
        },
        beforePreview = {
            if (BaseReadAloudService.isPlay()) ReadAloud.pause(this)
        },
        onCatalogRefreshed = onCatalogRefreshed,
        titleAction = selected?.let { current ->
            when (target.targetType) {
                BookCharacterTtsBinding.TargetType.NARRATOR,
                BookCharacterTtsBinding.TargetType.DIALOGUE_MALE,
                BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE -> {
                    getString(R.string.clear) to {
                        changeBinding(R.string.character_tts_binding_cleared) {
                            dao.deleteTtsBinding(target.workKey, target.targetType, target.targetId, current.engineId)
                        }
                    }
                }
                else -> {
                    getString(R.string.character_tts_use_dialogue_fallback) to useDialogueFallback@{
                        val engineId = AppConfig.multiRoleTtsEngineId?.takeIf { it.isNotBlank() }
                            ?: return@useDialogueFallback
                        selectVoice(engineId, null, BookCharacterTtsBinding.BindingMode.INHERIT)
                    }
                }
            }
        }
    ).show()
}

internal fun BookCharacterTtsBinding.withSelectedVoice(
    engineId: String,
    voiceId: String?,
    bindingMode: String,
    now: Long = System.currentTimeMillis(),
): BookCharacterTtsBinding = copy(
    engineId = engineId,
    voiceId = voiceId,
    bindingMode = bindingMode,
    createdAt = createdAt.takeIf { it > 0L } ?: now,
    updatedAt = now,
)

internal fun Context.refreshRunningReadAloud() {
    if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
        ReadAloud.refreshTtsRoute(this)
    }
}

internal data class BookCharacterVoiceCatalog(
    val loaded: Boolean = false,
    val engines: Map<String, TtsEngineSetting> = emptyMap(),
    val enabledVoiceIds: Map<String, Set<String>> = emptyMap(),
    val voiceNames: Map<Pair<String, String>, String> = emptyMap(),
    val activeEngineId: String? = null,
) {
    fun roleVoiceName(context: Context, binding: BookCharacterTtsBinding?, gender: String): String {
        if (binding?.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) {
            return context.fallbackVoiceName(gender)
        }
        if (!loaded) {
            return bindingVoiceName(context, binding) ?: context.fallbackVoiceName(gender)
        }
        val engine = binding?.let { engines[it.engineId] }?.takeIf { it.isSelectable }
        val usableVoiceIds = engine?.let { enabledVoiceIds[it.id] }.orEmpty()
        return when (BookTtsBindingPolicy.autoState(binding, usableVoiceIds)) {
            BookTtsBindingPolicy.AutoState.PENDING -> context.pendingVoiceName(gender)
            BookTtsBindingPolicy.AutoState.PROVISIONAL -> context.getString(
                R.string.character_tts_provisional_voice,
                bindingVoiceName(context, binding) ?: context.pendingVoiceName(gender)
            )
            BookTtsBindingPolicy.AutoState.STABLE,
            BookTtsBindingPolicy.AutoState.PROTECTED ->
                bindingVoiceName(context, binding) ?: context.fallbackVoiceName(gender)
        }
    }

    fun bindingVoiceName(context: Context, binding: BookCharacterTtsBinding?): String? {
        binding ?: return null
        if (binding.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) return null
        val voiceId = binding.voiceId
        return if (voiceId.isNullOrBlank()) {
            context.getString(R.string.character_tts_system_default_voice)
        } else {
            voiceNames[binding.engineId to voiceId] ?: voiceId
        }
    }

    companion object {
        fun load(): BookCharacterVoiceCatalog = fromEngines(
            TtsEngineStore.engines(),
            TtsEngineStore.activeEngineId().takeIf { it.isNotBlank() }
        )

        fun loadMultiRole(): BookCharacterVoiceCatalog =
            fromEngines(listOfNotNull(TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)), null)

        private fun fromEngines(
            engines: List<TtsEngineSetting>,
            activeEngineId: String?,
        ) = BookCharacterVoiceCatalog(
            loaded = true,
            engines = engines.associateBy { it.id },
            enabledVoiceIds = engines.associate { engine ->
                engine.id to engine.enabledVoices().mapTo(mutableSetOf()) { it.id }
            },
            voiceNames = engines.flatMap { engine ->
                engine.effectiveVoices().map { voice ->
                    (engine.id to voice.id) to voice.name
                }
            }.toMap(),
            activeEngineId = activeEngineId,
        )
    }
}

private fun Context.fallbackVoiceName(gender: String): String = getString(
    when (gender) {
        BookCharacter.Gender.MALE -> R.string.character_tts_fallback_male
        BookCharacter.Gender.FEMALE -> R.string.character_tts_fallback_female
        else -> R.string.character_tts_fallback_generic
    }
)

private fun Context.pendingVoiceName(gender: String): String = getString(
    when (gender) {
        BookCharacter.Gender.MALE -> R.string.character_tts_pending_male
        BookCharacter.Gender.FEMALE -> R.string.character_tts_pending_female
        else -> R.string.character_tts_pending_generic
    }
)
