package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.databinding.ItemTtsVoiceBinding
import io.legado.app.databinding.LayoutTtsVoiceFiltersBinding
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.dpToPx

data class TtsVoiceOption(
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
            voice.tags.joinToString(" ")
        ).any { it.contains(query, ignoreCase = true) }
    }

    fun previewKey(): String {
        return TtsVoicePreviewController.keyOf(engine, voice, systemDefault)
    }
}

class TtsVoiceSelectionSheet(
    private val context: Context,
    lifecycleScope: LifecycleCoroutineScope,
    private val title: CharSequence? = null,
    private val searchHint: CharSequence,
    private val emptyText: CharSequence,
    private val engines: () -> List<TtsEngineSetting>,
    private val isSelected: (TtsVoiceOption) -> Boolean,
    private val onSelect: (TtsVoiceOption) -> Unit,
    private val beforePreview: () -> Unit = {},
    private val dismissOnSelect: Boolean = true,
    private val titleAction: Pair<CharSequence, () -> Unit>? = null
) {
    private lateinit var sheet: NgLongListBottomSheet
    private lateinit var recyclerVoices: RecyclerView
    private lateinit var textEmpty: TextView
    private var filterAction: ImageButton? = null
    private var filterBinding: LayoutTtsVoiceFiltersBinding? = null
    private var availableLanguageFilters = emptyList<String>()
    private var availableGenderFilters = emptyList<String>()
    private val selectedLanguageFilters = linkedSetOf<String>()
    private val selectedGenderFilters = linkedSetOf<String>()
    private val adapter = TtsVoiceSelectionAdapter(
        context = context,
        onSelect = { option ->
            onSelect(option)
            if (dismissOnSelect) dismiss()
        },
        onPreview = ::previewVoice
    )
    private val previewController = TtsVoicePreviewController(
        context = context,
        lifecycleScope = lifecycleScope,
        beforePreview = beforePreview,
        onStatusChanged = adapter::updatePreviewStatus
    )

    fun show() {
        val availableVoices = engines().flatMap { engine ->
            voiceChoices(engine).map { it.voice }
        }
        availableLanguageFilters = TtsVoiceFilterSupport.availableLanguageLabels(availableVoices)
        availableGenderFilters = listOf("男", "女").filter { label ->
            availableVoices.any { TtsVoiceFilterSupport.genderLabel(it.gender) == label }
        }
        sheet = NgLongListBottomSheet(
            context = context,
            searchHint = searchHint,
            title = title ?: context.getString(R.string.tts_voices),
            showSearch = false,
            compact = true
        )
        titleAction?.let { (text, action) ->
            sheet.setTitleAction(text) {
                action()
                dismiss()
            }
        }
        setupVoiceFilters()
        recyclerVoices = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@TtsVoiceSelectionSheet.adapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 6.dpToPx())
        }
        textEmpty = TextView(context).apply {
            text = emptyText
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            textSize = 15f
            gravity = Gravity.CENTER
            isVisible = false
        }
        val content = FrameLayout(context).apply {
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
        sheet.dialog.setOnDismissListener { previewController.release() }
        sheet.show()
    }

    fun dismiss() {
        if (::sheet.isInitialized) sheet.dismiss()
    }

    private fun renderVoiceOptions(query: String) {
        val items = buildVoiceItems(query)
        adapter.submitItems(items)
        val hasChoice = items.any { it is TtsVoiceSelectionItem.Choice }
        recyclerVoices.isVisible = hasChoice
        textEmpty.isVisible = !hasChoice
    }

    private fun buildVoiceItems(query: String): List<TtsVoiceSelectionItem> {
        val normalizedQuery = query.trim()
        return buildList {
            engines().forEach { engine ->
                val choices = voiceChoices(engine).filter { choice ->
                    (normalizedQuery.isBlank() || choice.matches(normalizedQuery)) &&
                        matchesVoiceFilters(choice.voice)
                }
                if (choices.isNotEmpty()) {
                    add(TtsVoiceSelectionItem.Header(engine.name))
                    choices.forEach { choice ->
                        add(TtsVoiceSelectionItem.Choice(choice, isSelected(choice)))
                    }
                }
            }
        }
    }

    private fun voiceChoices(engine: TtsEngineSetting): List<TtsVoiceOption> {
        if (engine.type == TtsEngineType.SYSTEM) {
            return listOf(
                TtsVoiceOption(
                    engine = engine,
                    voice = TtsVoice(
                        id = TtsEngineStore.SYSTEM_DEFAULT_ID,
                        name = context.getString(R.string.character_tts_system_default_voice)
                    ),
                    systemDefault = true
                )
            )
        }
        return engine.enabledVoices().map { voice ->
            TtsVoiceOption(engine = engine, voice = voice, systemDefault = false)
        }
    }

    private fun previewVoice(option: TtsVoiceOption) {
        previewController.preview(option.engine, option.voice, option.systemDefault)
    }

    private fun setupVoiceFilters() {
        val binding = LayoutTtsVoiceFiltersBinding.inflate(LayoutInflater.from(context))
        filterBinding = binding
        (sheet.searchEdit.parent as? ViewGroup)?.removeView(sheet.searchEdit)
        binding.layoutSearchContainer.addView(
            sheet.searchEdit,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        sheet.searchEdit.setBackgroundResource(R.drawable.ng_bg_tts_filter_search)
        val searchHint = sheet.searchEdit.hint
        sheet.searchEdit.setOnFocusChangeListener { _, hasFocus ->
            sheet.searchEdit.hint = if (hasFocus) null else searchHint
        }
        sheet.searchEdit.isVisible = true
        sheet.searchEdit.doOnTextChanged { _, _, _, _ ->
            filterAction?.let(::updateFilterActionTint)
        }
        binding.root.isVisible = false
        bindVoiceFilters(binding)
        sheet.setTopContent(binding.root)
        filterAction = sheet.addCompactTitleIcon(
            iconRes = R.drawable.ic_tts_params_grid,
            contentDescription = context.getString(R.string.tts_filter_voices)
        ) { button ->
            binding.root.isVisible = !binding.root.isVisible
            updateFilterActionTint(button)
        }
        filterAction?.let(::updateFilterActionTint)
    }

    private fun bindVoiceFilters(binding: LayoutTtsVoiceFiltersBinding) = binding.run {
        layoutLanguageFilterSection.isVisible = availableLanguageFilters.isNotEmpty()
        layoutVoiceLanguageFilters.removeAllViews()
        availableLanguageFilters.forEach { label ->
            layoutVoiceLanguageFilters.addView(
                createLanguageFilterChip(
                    container = layoutVoiceLanguageFilters,
                    label = label,
                    selected = label in selectedLanguageFilters
                ) {
                    selectedLanguageFilters.toggle(label)
                    onVoiceFiltersChanged()
                }
            )
        }
        layoutGenderFilterSection.isVisible = availableGenderFilters.isNotEmpty()
        layoutVoiceGenderFilters.removeAllViews()
        availableGenderFilters.forEach { label ->
            layoutVoiceGenderFilters.addView(
                createGenderFilterChip(
                    container = layoutVoiceGenderFilters,
                    label = label,
                    selected = label in selectedGenderFilters
                ) {
                    selectedGenderFilters.toggle(label)
                    onVoiceFiltersChanged()
                }
            )
        }
    }

    private fun onVoiceFiltersChanged() {
        filterBinding?.let(::bindVoiceFilters)
        filterAction?.let(::updateFilterActionTint)
        renderVoiceOptions(sheet.searchEdit.text?.toString().orEmpty())
    }

    private fun matchesVoiceFilters(voice: TtsVoice): Boolean {
        val languageMatches = selectedLanguageFilters.isEmpty() ||
            TtsVoiceFilterSupport.languageLabels(voice.language)
                .any { it in selectedLanguageFilters }
        val genderMatches = selectedGenderFilters.isEmpty() ||
            TtsVoiceFilterSupport.genderLabel(voice.gender)
                ?.let { it in selectedGenderFilters } == true
        return languageMatches && genderMatches
    }

    private fun createLanguageFilterChip(
        container: FlexboxLayout,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) R.color.ng_tts_language else R.color.ng_on_surface_variant
                )
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundResource(
                if (selected) R.drawable.ng_bg_tts_language_tag else R.drawable.ng_bg_tag_neutral
            )
            setPadding(10.dpToPx(), 0, 10.dpToPx(), 0)
            minWidth = 28.dpToPx()
            setOnClickListener { onClick() }
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                24.dpToPx()
            ).apply {
                rightMargin = 6.dpToPx()
            }
        }
    }

    private fun createGenderFilterChip(
        container: LinearLayout,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): ImageView {
        val isMale = label == "男"
        return ImageView(context).apply {
            contentDescription = label
            setImageResource(
                if (isMale) R.drawable.ic_tts_gender_male else R.drawable.ic_tts_gender_female
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    when {
                        !selected -> R.color.ng_on_surface_variant
                        isMale -> R.color.ng_tts_gender_male
                        else -> R.color.ng_tts_gender_female
                    }
                )
            )
            setBackgroundResource(
                if (selected) R.drawable.ng_bg_tts_language_tag else R.drawable.ng_bg_tag_neutral
            )
            setPadding(5.dpToPx(), 3.dpToPx(), 5.dpToPx(), 3.dpToPx())
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(34.dpToPx(), 24.dpToPx()).apply {
                marginEnd = 6.dpToPx()
            }
        }
    }

    private fun updateFilterActionTint(button: ImageButton) {
        val active = sheet.searchEdit.text?.isNotBlank() == true ||
            selectedLanguageFilters.isNotEmpty() || selectedGenderFilters.isNotEmpty()
        button.setColorFilter(
            if (active) context.accentColor
            else ContextCompat.getColor(context, R.color.ng_on_surface_variant)
        )
    }

    private fun MutableSet<String>.toggle(value: String) {
        if (!add(value)) remove(value)
    }
}

private sealed class TtsVoiceSelectionItem {
    data class Header(val title: String) : TtsVoiceSelectionItem()
    data class Choice(
        val option: TtsVoiceOption,
        val selected: Boolean
    ) : TtsVoiceSelectionItem()
}

private class TtsVoiceSelectionAdapter(
    private val context: Context,
    private val onSelect: (TtsVoiceOption) -> Unit,
    private val onPreview: (TtsVoiceOption) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<TtsVoiceSelectionItem>()
    private var previewStatus = TtsVoicePreviewStatus(null, TtsVoicePreviewState.IDLE)

    fun updatePreviewStatus(status: TtsVoicePreviewStatus) {
        val affectedKeys = listOfNotNull(previewStatus.key, status.key).distinct()
        previewStatus = status
        affectedKeys.forEach { key ->
            val position = items.indexOfFirst { item ->
                (item as? TtsVoiceSelectionItem.Choice)?.option?.previewKey() == key
            }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    fun submitItems(newItems: List<TtsVoiceSelectionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is TtsVoiceSelectionItem.Header -> VIEW_TYPE_HEADER
        is TtsVoiceSelectionItem.Choice -> VIEW_TYPE_CHOICE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(TextView(parent.context))
        } else {
            ChoiceHolder(
                ItemTtsVoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TtsVoiceSelectionItem.Header -> (holder as HeaderHolder).bind(item)
            is TtsVoiceSelectionItem.Choice -> (holder as ChoiceHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private inner class HeaderHolder(
        private val textView: TextView
    ) : RecyclerView.ViewHolder(textView) {
        fun bind(item: TtsVoiceSelectionItem.Header) {
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
        fun bind(item: TtsVoiceSelectionItem.Choice) = binding.run {
            val option = item.option
            TtsVoiceCardBinder.bind(
                context = context,
                binding = this,
                item = option.voice,
                engine = option.engine,
                isSystemEngine = option.systemDefault,
                showControls = false,
                selected = item.selected
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
            root.isSelected = item.selected
            val contentArea = layoutVoiceContent
            root.alpha = 1f
            root.isClickable = false
            root.isFocusable = false
            root.setOnClickListener(null)
            contentArea.isClickable = true
            contentArea.isFocusable = true
            contentArea.setOnClickListener { onSelect(option) }
            listOf(scrollHeader, scrollTags).forEach { scrollView ->
                scrollView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) contentArea.performClick()
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
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_CHOICE = 1
    }
}
