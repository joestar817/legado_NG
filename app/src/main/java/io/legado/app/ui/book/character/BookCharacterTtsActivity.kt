package io.legado.app.ui.book.character

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.databinding.ActivityBookCharacterTtsBinding
import io.legado.app.databinding.ItemBookCharacterTtsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoiceSelectionSheet
import io.legado.app.ui.config.TtsSheetLaunchDebouncer
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookCharacterTtsActivity : BaseActivity<ActivityBookCharacterTtsBinding>() {

    override val binding by viewBinding(ActivityBookCharacterTtsBinding::inflate)
    private val adapter by lazy { Adapter() }
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var bookUrl: String? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        bookUrl = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_URL)
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        appDb.bookCharacterDao.getOrCreateProfile(bookName, bookAuthor, bookUrl)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        observeData()
    }

    private fun observeData() {
        lifecycleScope.launch {
            combine(
                appDb.bookCharacterDao.flowCharacters(workKey),
                appDb.bookCharacterDao.flowTtsBindings(workKey)
            ) { characters, bindings ->
                characters to bindings
            }.catch {
                toastOnUi(it.localizedMessage)
            }.flowOn(IO).collect { (characters, bindings) ->
                adapter.setItems(buildRows(characters, bindings))
            }
        }
    }

    private fun buildRows(
        characters: List<BookCharacter>,
        bindings: List<BookCharacterTtsBinding>
    ): List<Row> {
        val bindingMap = bindings.associateBy { it.targetType to it.targetId }
        return buildList {
            add(
                Row.Narrator(
                    binding = bindingMap[BookCharacterTtsBinding.TargetType.NARRATOR to 0L]
                )
            )
            add(
                Row.DialogueFallback(
                    gender = BookCharacter.Gender.MALE,
                    binding = bindingMap[BookCharacterTtsBinding.TargetType.DIALOGUE_MALE to 0L]
                )
            )
            add(
                Row.DialogueFallback(
                    gender = BookCharacter.Gender.FEMALE,
                    binding = bindingMap[BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE to 0L]
                )
            )
            characters.filter { it.enabled }.forEach { character ->
                add(
                    Row.Character(
                        character = character,
                        binding = bindingMap[
                            BookCharacterTtsBinding.TargetType.CHARACTER to character.id
                        ]
                    )
                )
            }
        }
    }

    private fun showVoiceSheet(row: Row) {
        val selectedBinding = row.binding()
        TtsVoiceSelectionSheet(
            context = this,
            lifecycleScope = lifecycleScope,
            title = row.title(),
            searchHint = getString(R.string.default_tts_voice_search),
            emptyText = getString(R.string.character_tts_no_voice_options),
            engines = { TtsEngineStore.engines().filter { it.enabled } },
            isSelected = { option -> isSelected(selectedBinding, option) },
            onSelect = { option -> saveBinding(row, option) },
            beforePreview = {
                if (BaseReadAloudService.isPlay()) ReadAloud.pause(this)
            },
            titleAction = selectedBinding?.let {
                getString(R.string.clear) to {
                    clearBinding(row)
                }
            }
        ).show()
    }

    private fun saveBinding(row: Row, option: TtsVoiceOption) {
        lifecycleScope.launch(IO) {
            val now = System.currentTimeMillis()
            val old = row.binding()
            val binding = (old ?: row.newBinding()).apply {
                engineId = option.engine.id
                voiceId = option.voice.id.takeUnless { option.systemDefault }
                if (createdAt <= 0L) {
                    createdAt = now
                }
                updatedAt = now
            }
            appDb.bookCharacterDao.upsertTtsBinding(binding)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_saved))
                refreshRunningReadAloud()
            }
        }
    }

    private fun clearBinding(row: Row) {
        val target = row.target()
        lifecycleScope.launch(IO) {
            appDb.bookCharacterDao.deleteTtsBinding(workKey, target.first, target.second)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_cleared))
                refreshRunningReadAloud()
            }
        }
    }

    private fun refreshRunningReadAloud() {
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.refreshTtsRoute(this)
        }
    }

    private fun bindingVoiceLabel(binding: BookCharacterTtsBinding?): String? {
        binding ?: return null
        val engine = TtsEngineStore.engine(binding.engineId) ?: return null
        val voiceName = if (binding.voiceId.isNullOrBlank()) {
            getString(R.string.character_tts_system_default_voice)
        } else {
            TtsEngineStore.voice(engine.id, binding.voiceId)?.name ?: binding.voiceId.orEmpty()
        }
        return getString(R.string.character_tts_engine_voice, voiceName, engine.name)
    }

    private fun globalVoiceLabel(): String {
        val engine = runCatching { TtsEngineStore.activeEngine() }.getOrNull()
        val voice = runCatching { engine?.activeVoice()?.name }.getOrNull()
        return when {
            engine == null || !engine.enabled -> getString(R.string.character_tts_no_engine)
            voice.isNullOrBlank() -> getString(R.string.character_tts_engine_only, engine.name)
            else -> getString(R.string.character_tts_engine_voice, voice, engine.name)
        }
    }

    private fun defaultNarratorVoiceLabel(): String? {
        return configuredVoiceLabel(
            engineId = AppConfig.defaultNarratorTtsEngineId,
            voiceId = AppConfig.defaultNarratorTtsVoiceId,
            allowSystemDefault = true
        )
    }

    private fun defaultDialogueVoiceLabel(gender: String): String? {
        val voiceId = if (gender == BookCharacter.Gender.MALE) {
            AppConfig.defaultDialogueMaleTtsVoiceId
        } else {
            AppConfig.defaultDialogueFemaleTtsVoiceId
        }
        return configuredVoiceLabel(
            engineId = AppConfig.multiRoleTtsEngineId,
            voiceId = voiceId,
            allowSystemDefault = false
        )
    }

    private fun configuredVoiceLabel(
        engineId: String?,
        voiceId: String?,
        allowSystemDefault: Boolean
    ): String? {
        val engine = TtsEngineStore.engine(engineId)?.takeIf { it.enabled } ?: return null
        val voiceName = when {
            !voiceId.isNullOrBlank() -> engine.enabledVoices()
                .firstOrNull { it.id == voiceId }
                ?.name
                ?: return null
            allowSystemDefault && engine.type == TtsEngineType.SYSTEM -> {
                getString(R.string.character_tts_system_default_voice)
            }
            else -> return null
        }
        return getString(R.string.character_tts_engine_voice, voiceName, engine.name)
    }

    private fun isSelected(
        binding: BookCharacterTtsBinding?,
        option: TtsVoiceOption
    ): Boolean {
        binding ?: return false
        return binding.engineId == option.engine.id && if (option.systemDefault) {
            binding.voiceId.isNullOrBlank()
        } else {
            binding.voiceId == option.voice.id
        }
    }

    private fun Row.title(): String = when (this) {
        is Row.Narrator -> getString(R.string.character_tts_narrator)
        is Row.DialogueFallback -> getString(
            if (gender == BookCharacter.Gender.MALE) {
                R.string.character_tts_dialogue_male
            } else {
                R.string.character_tts_dialogue_female
            }
        )
        is Row.Character -> character.name
    }

    private fun Row.binding(): BookCharacterTtsBinding? {
        return when (this) {
            is Row.Narrator -> binding
            is Row.DialogueFallback -> binding
            is Row.Character -> binding
        }
    }

    private fun Row.newBinding(): BookCharacterTtsBinding {
        return when (this) {
            is Row.Narrator -> BookCharacterTtsBinding.narrator(workKey)
            is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
                BookCharacterTtsBinding.dialogueMale(workKey)
            } else {
                BookCharacterTtsBinding.dialogueFemale(workKey)
            }
            is Row.Character -> BookCharacterTtsBinding.character(workKey, character.id)
        }
    }

    private fun Row.target(): Pair<String, Long> {
        return when (this) {
            is Row.Narrator -> BookCharacterTtsBinding.TargetType.NARRATOR to 0L
            is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
                BookCharacterTtsBinding.TargetType.DIALOGUE_MALE to 0L
            } else {
                BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE to 0L
            }
            is Row.Character -> BookCharacterTtsBinding.TargetType.CHARACTER to character.id
        }
    }

    private inner class Adapter :
        RecyclerAdapter<Row, ItemBookCharacterTtsBinding>(this@BookCharacterTtsActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemBookCharacterTtsBinding {
            return ItemBookCharacterTtsBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBookCharacterTtsBinding,
            item: Row,
            payloads: MutableList<Any>
        ) = binding.run {
            when (item) {
                is Row.Narrator -> {
                    tvAvatar.text = getString(R.string.character_tts_narrator_avatar)
                    tvAvatar.setBackgroundResource(R.drawable.bg_character_avatar_unknown)
                    tvName.text = item.title()
                    tvVoice.text = bindingVoiceLabel(item.binding)
                        ?: getString(
                            R.string.character_tts_follow_global,
                            defaultNarratorVoiceLabel() ?: globalVoiceLabel()
                        )
                }
                is Row.DialogueFallback -> {
                    val male = item.gender == BookCharacter.Gender.MALE
                    tvAvatar.text = getString(
                        if (male) {
                            R.string.character_tts_dialogue_male_avatar
                        } else {
                            R.string.character_tts_dialogue_female_avatar
                        }
                    )
                    tvAvatar.setBackgroundResource(
                        if (male) {
                            R.drawable.bg_character_avatar_male
                        } else {
                            R.drawable.bg_character_avatar_female
                        }
                    )
                    tvName.text = item.title()
                    tvVoice.text = bindingVoiceLabel(item.binding)
                        ?: defaultDialogueVoiceLabel(item.gender)?.let { voiceLabel ->
                            getString(R.string.character_tts_follow_global, voiceLabel)
                        }
                        ?: getString(R.string.character_tts_no_dialogue_default)
                }
                is Row.Character -> {
                    val character = item.character
                    tvAvatar.text = character.name.firstOrNull()?.toString().orEmpty()
                    tvAvatar.setBackgroundResource(character.avatarBackground())
                    tvName.text = item.title()
                    tvVoice.text = bindingVoiceLabel(item.binding)
                        ?: getString(R.string.character_tts_unbound)
                }
            }
            val roleLabel = when (item) {
                is Row.DialogueFallback -> getString(R.string.character_tts_dialogue_fallback)
                is Row.Character -> BookCharacterLabels.roleLabel(
                    this@BookCharacterTtsActivity,
                    item.character.roleTag
                )
                is Row.Narrator -> null
            }
            tvRole.text = roleLabel
            tvRole.isVisible = !roleLabel.isNullOrBlank()
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
                    ContextCompat.getColor(
                        this@BookCharacterTtsActivity,
                        R.color.ng_on_surface_variant
                    )
                )
            )
            tvAction.background = null
            tvAction.foreground = null
            tvAction.minWidth = 0
            tvAction.setPadding(0, 0, 0, 0)
            tvAction.isClickable = false
            tvAction.isFocusable = false
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBookCharacterTtsBinding) {
            binding.root.setOnClickListener {
                if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
                    getItemByLayoutPosition(holder)?.let { showVoiceSheet(it) }
                }
            }
        }

        private fun getItemByLayoutPosition(holder: ItemViewHolder): Row? {
            val position = holder.bindingAdapterPosition
            return getItem(position)
        }

        private fun BookCharacter.avatarBackground(): Int {
            return when (gender) {
                BookCharacter.Gender.MALE -> R.drawable.bg_character_avatar_male
                BookCharacter.Gender.FEMALE -> R.drawable.bg_character_avatar_female
                else -> R.drawable.bg_character_avatar_unknown
            }
        }

    }

    private sealed interface Row {
        data class Narrator(val binding: BookCharacterTtsBinding?) : Row
        data class DialogueFallback(
            val gender: String,
            val binding: BookCharacterTtsBinding?
        ) : Row
        data class Character(
            val character: BookCharacter,
            val binding: BookCharacterTtsBinding?
        ) : Row
    }
}
