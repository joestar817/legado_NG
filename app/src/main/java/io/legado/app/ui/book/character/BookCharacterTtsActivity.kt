package io.legado.app.ui.book.character

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import io.legado.app.databinding.ItemTtsVoiceBinding
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.styleOptions
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsVoiceCardBinder
import io.legado.app.ui.config.TtsVoicePreviewController
import io.legado.app.ui.config.TtsVoicePreviewState
import io.legado.app.ui.config.TtsVoicePreviewStatus
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookCharacterTtsActivity : BaseActivity<ActivityBookCharacterTtsBinding>() {

    override val binding by viewBinding(ActivityBookCharacterTtsBinding::inflate)
    private val adapter by lazy { Adapter() }
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
                val rows = buildRows(characters, bindings)
                adapter.setItems(rows)
                binding.tvSummary.text = getString(
                    R.string.character_tts_summary,
                    1,
                    2,
                    characters.count { it.enabled }
                )
                if (rows.isEmpty()) {
                    binding.tvEmpty.visible()
                } else {
                    binding.tvEmpty.gone()
                }
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
        CharacterTtsVoiceSheet(
            activity = this,
            selectedBinding = row.binding(),
            onSelect = { option ->
                saveBinding(row, option)
            }
        ).show()
    }

    private fun saveBinding(row: Row, option: VoiceOption) {
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
            ReadAloud.upTtsSpeechRate(this)
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
        return getString(R.string.character_tts_engine_voice, engine.name, voiceName)
    }

    private fun bindingStyleLabel(binding: BookCharacterTtsBinding?): String? {
        binding ?: return null
        return getString(R.string.character_tts_emotion_runtime)
    }

    private fun globalVoiceLabel(): String {
        val engine = runCatching { TtsEngineStore.activeEngine() }.getOrNull()
        val voice = runCatching { engine?.activeVoice()?.name }.getOrNull()
        return when {
            engine == null || !engine.enabled -> getString(R.string.character_tts_no_engine)
            voice.isNullOrBlank() -> getString(R.string.character_tts_engine_only, engine.name)
            else -> getString(R.string.character_tts_engine_voice, engine.name, voice)
        }
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
                    tvName.text = getString(R.string.character_tts_narrator)
                    tvRole.text = getString(R.string.character_tts_narrator)
                    tvVoice.text = bindingVoiceLabel(item.binding)
                        ?: getString(R.string.character_tts_follow_global, globalVoiceLabel())
                    tvStyle.text = bindingStyleLabel(item.binding)
                        ?: getString(R.string.character_tts_narrator_style)
                    tvAction.text = getString(R.string.character_tts_select_voice)
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
                    tvName.text = getString(
                        if (male) {
                            R.string.character_tts_dialogue_male
                        } else {
                            R.string.character_tts_dialogue_female
                        }
                    )
                    tvRole.text = getString(R.string.character_tts_dialogue_fallback)
                    tvVoice.text = bindingVoiceLabel(item.binding)
                        ?: getString(R.string.character_tts_unbound)
                    tvStyle.text = bindingStyleLabel(item.binding)
                        ?: getString(
                            if (male) {
                                R.string.character_tts_dialogue_male_hint
                            } else {
                                R.string.character_tts_dialogue_female_hint
                            }
                        )
                    tvAction.text = getString(R.string.character_tts_select_voice)
                }
                is Row.Character -> {
                    val character = item.character
                    tvAvatar.text = character.name.firstOrNull()?.toString().orEmpty()
                    tvAvatar.setBackgroundResource(character.avatarBackground())
                    tvName.text = character.name
                    tvRole.text = BookCharacterLabels.roleLabel(context, character.roleTag)
                    tvVoice.text = bindingVoiceLabel(item.binding)
                        ?: getString(R.string.character_tts_unbound)
                    tvStyle.text = bindingStyleLabel(item.binding) ?: character.bindingHint()
                    tvAction.text = getString(R.string.character_tts_select_voice)
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemBookCharacterTtsBinding) {
            binding.tvAction.setOnClickListener {
                getItemByLayoutPosition(holder)?.let { showVoiceSheet(it) }
            }
            binding.tvAction.setOnLongClickListener {
                getItemByLayoutPosition(holder)?.let { clearBinding(it) }
                true
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

        private fun BookCharacter.bindingHint(): String {
            val aliases = aliases().takeIf { it.isNotEmpty() }?.joinToString(" / ")
            return aliases ?: displayIntro()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.character_tts_fallback_narrator)
        }

        private fun BookCharacter.aliases(): List<String> {
            return aliasesJson?.let {
                GSON.fromJsonObject<List<String>>(it).getOrNull()
            }.orEmpty()
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

private class CharacterTtsVoiceSheet(
    private val activity: BookCharacterTtsActivity,
    private val selectedBinding: BookCharacterTtsBinding?,
    private val onSelect: (VoiceOption) -> Unit
) {
    private lateinit var sheet: NgLongListBottomSheet
    private lateinit var recyclerVoices: RecyclerView
    private lateinit var textEmpty: TextView
    private val previewController by lazy {
        TtsVoicePreviewController(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            beforePreview = {
                if (BaseReadAloudService.isPlay()) {
                    ReadAloud.pause(activity)
                }
            },
            onStatusChanged = adapter::updatePreviewStatus
        )
    }
    private val adapter by lazy {
        CharacterTtsVoiceAdapter(
            context = activity,
            selectedBinding = selectedBinding,
            onPreview = ::previewVoice,
            onSelect = { option ->
                sheet.dialog.dismiss()
                onSelect(option)
            }
        )
    }

    fun show() {
        sheet = NgLongListBottomSheet(
            context = activity,
            searchHint = activity.getString(R.string.character_tts_search_voice),
            title = activity.getString(R.string.character_tts_select_voice)
        )
        recyclerVoices = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@CharacterTtsVoiceSheet.adapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 6.dpToPx())
        }
        textEmpty = TextView(activity).apply {
            text = activity.getString(R.string.character_tts_no_voice_options)
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 15f
            gravity = Gravity.CENTER
            isVisible = false
        }
        val content = FrameLayout(activity).apply {
            addView(
                recyclerVoices,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                textEmpty,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        sheet.setContent(content, ::renderVoiceOptions)
        sheet.dialog.setOnDismissListener {
            previewController.release()
        }
        sheet.show()
    }

    private fun renderVoiceOptions(query: String) {
        val items = buildVoiceItems(query)
        adapter.submitItems(items)
        val hasChoice = items.any { it is VoiceSheetItem.Choice }
        recyclerVoices.isVisible = hasChoice
        textEmpty.isVisible = !hasChoice
    }

    private fun buildVoiceItems(query: String): List<VoiceSheetItem> {
        val normalizedQuery = query.trim()
        return buildList {
            TtsEngineStore.engines()
                .filter { it.enabled }
                .forEach { engine ->
                    val choices = voiceChoices(engine)
                        .filter { choice ->
                            normalizedQuery.isBlank() || choice.matches(normalizedQuery)
                        }
                    if (choices.isNotEmpty()) {
                        add(VoiceSheetItem.Header(engine.name))
                        choices.forEach { choice ->
                            add(VoiceSheetItem.Choice(choice))
                        }
                    }
                }
        }
    }

    private fun voiceChoices(engine: TtsEngineSetting): List<VoiceOption> {
        if (engine.type == TtsEngineType.SYSTEM) {
            return listOf(
                VoiceOption(
                    engine = engine,
                    voice = TtsVoice(
                        id = TtsEngineStore.SYSTEM_DEFAULT_ID,
                        name = activity.getString(R.string.character_tts_system_default_voice)
                    ),
                    systemDefault = true
                )
            )
        }
        return engine.enabledVoices().map { voice ->
            VoiceOption(engine = engine, voice = voice, systemDefault = false)
        }
    }

    private fun previewVoice(option: VoiceOption) {
        previewController.preview(option.engine, option.voice, option.systemDefault)
    }
}

private sealed class VoiceSheetItem {
    data class Header(val title: String) : VoiceSheetItem()
    data class Choice(val option: VoiceOption) : VoiceSheetItem()
}

private data class VoiceOption(
    val engine: TtsEngineSetting,
    val voice: TtsVoice,
    val systemDefault: Boolean
) {
    fun matches(query: String): Boolean {
        return listOf(
            engine.name,
            voice.name,
            voice.id,
            voice.language.orEmpty(),
            voice.gender.orEmpty(),
            voice.style.orEmpty(),
            voice.tags.joinToString(" "),
            voice.styleOptions().joinToString(" ") { it.displayName }
        ).any { it.contains(query, ignoreCase = true) }
    }
}

private class CharacterTtsVoiceAdapter(
    private val context: BookCharacterTtsActivity,
    private val selectedBinding: BookCharacterTtsBinding?,
    private val onPreview: (VoiceOption) -> Unit,
    private val onSelect: (VoiceOption) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<VoiceSheetItem>()
    private var previewStatus = TtsVoicePreviewStatus(
        key = null,
        state = TtsVoicePreviewState.IDLE
    )

    fun updatePreviewStatus(status: TtsVoicePreviewStatus) {
        val affectedKeys = listOfNotNull(previewStatus.key, status.key).distinct()
        previewStatus = status
        affectedKeys.forEach { key ->
            val position = items.indexOfFirst { item ->
                (item as? VoiceSheetItem.Choice)?.option?.previewKey() == key
            }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    fun submitItems(newItems: List<VoiceSheetItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is VoiceSheetItem.Header -> VIEW_TYPE_HEADER
            is VoiceSheetItem.Choice -> VIEW_TYPE_CHOICE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(TextView(parent.context))
        } else {
            ChoiceHolder(
                ItemTtsVoiceBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is VoiceSheetItem.Header -> (holder as HeaderHolder).bind(item)
            is VoiceSheetItem.Choice -> (holder as ChoiceHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private inner class HeaderHolder(
        private val textView: TextView
    ) : RecyclerView.ViewHolder(textView) {
        fun bind(item: VoiceSheetItem.Header) {
            textView.text = item.title
            textView.setTextColor(context.accentColor)
            textView.typeface = Typeface.DEFAULT_BOLD
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            textView.includeFontPadding = false
            textView.setPadding(4.dpToPx(), 12.dpToPx(), 4.dpToPx(), 8.dpToPx())
            textView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private inner class ChoiceHolder(
        private val binding: ItemTtsVoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VoiceSheetItem.Choice) = binding.run {
            val option = item.option
            TtsVoiceCardBinder.bind(
                context = context,
                binding = this,
                item = option.voice,
                engine = option.engine,
                isSystemEngine = option.systemDefault,
                showControls = false
            )
            switchEnabled.isVisible = false
            layoutPreviewButton.isVisible = true
            TtsVoiceCardBinder.bindPreviewState(
                context = context,
                binding = this,
                state = previewStatus.takeIf { it.key == option.previewKey() }
                    ?.state
                    ?: TtsVoicePreviewState.IDLE
            )
            val contentArea = root.getChildAt(0)
            root.alpha = if (isSelected(option)) 0.72f else 1f
            root.isClickable = false
            root.isFocusable = false
            root.setOnClickListener(null)
            contentArea.isClickable = true
            contentArea.isFocusable = true
            contentArea.setOnClickListener { onSelect(option) }
            listOf(scrollHeader, scrollTags).forEach { scrollView ->
                scrollView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        contentArea.performClick()
                    }
                    true
                }
            }
            layoutPreviewButton.setOnClickListener {
                imagePreview.animate().cancel()
                imagePreview.scaleX = 0.9f
                imagePreview.scaleY = 0.9f
                imagePreview.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .start()
                onPreview(option)
            }
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
        }

        private fun isSelected(option: VoiceOption): Boolean {
            val binding = selectedBinding ?: return false
            return binding.engineId == option.engine.id &&
                    if (option.systemDefault) {
                        binding.voiceId.isNullOrBlank()
                    } else {
                        binding.voiceId == option.voice.id
                    }
        }
    }

    private fun VoiceOption.previewKey(): String {
        return TtsVoicePreviewController.keyOf(engine, voice, systemDefault)
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHOICE = 1
    }
}
