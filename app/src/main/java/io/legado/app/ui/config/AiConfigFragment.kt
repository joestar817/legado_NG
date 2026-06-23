package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.FragmentAiConfigBinding
import io.legado.app.databinding.ItemAiProviderBinding
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiPromptStore
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiProviderType
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.databinding.ItemAiPromptBinding
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.applyTint
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class AiConfigFragment : BaseFragment(R.layout.fragment_ai_config) {

    private enum class Page { MAIN, PROVIDERS, DETAIL, PROMPTS, PROMPT_DETAIL, PURIFY_SETTINGS }

    private val binding by viewBinding(FragmentAiConfigBinding::bind)
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val providerAdapter by lazy { AiProviderAdapter() }
    private val promptAdapter by lazy { AiPromptAdapter() }
    private lateinit var providerItemTouchHelper: ItemTouchHelper
    private var currentPage = Page.MAIN
    private var currentProviderId: String? = null
    private var currentPrompt: AiPromptStore.Prompt? = null
    private var providerSearchQuery: String = ""
    private var requestJob: Job? = null
    private var modelOptions: List<String> = emptyList()
    private var ignoreModelSelection = false
    private var ignoreProviderFormChanges = false
    private var ignorePurifyFormChanges = false
    private var ignorePromptHighlight = false
    private var apiKeyVisible = false
    private val balanceNumberFormat by lazy { DecimalFormat("0.####") }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMain()
        initProviderList()
        initDetail()
        initPromptList()
        initPromptDetail()
        initPurifySettings()
        initBack()
        showMain()
    }

    override fun onResume() {
        super.onResume()
        bindTitleBarBack()
        refreshCurrentPage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requestJob?.cancel()
        waitDialog.dismiss()
    }

    private fun initMain() {
        binding.layoutProviderEntry.setOnClickListener {
            showProviderList()
        }
        binding.layoutPromptEntry.setOnClickListener {
            showPromptList()
        }
        binding.layoutPurifyEntry.setOnClickListener {
            showPurifySettings()
        }
    }

    private fun initPurifySettings() {
        binding.layoutAiPurifyAuto.setOnClickListener {
            binding.switchAiPurifyAuto.isChecked = !binding.switchAiPurifyAuto.isChecked
        }
        binding.layoutAiPurifyIntercept.setOnClickListener {
            binding.switchAiPurifyIntercept.isChecked = !binding.switchAiPurifyIntercept.isChecked
        }
        binding.layoutAiPurifyChapterAuto.setOnClickListener {
            binding.switchAiPurifyChapterAuto.isChecked =
                !binding.switchAiPurifyChapterAuto.isChecked
        }
        binding.layoutAiPurifyChapterIntercept.setOnClickListener {
            binding.switchAiPurifyChapterIntercept.isChecked =
                !binding.switchAiPurifyChapterIntercept.isChecked
        }
        binding.layoutAiPurifyChapterRuleTypes.setOnClickListener {
            showPurifyChapterRuleTypeDialog()
        }
        binding.switchAiPurifyAuto.setOnCheckedChangeListener { _, isChecked ->
            if (!ignorePurifyFormChanges) {
                AiConfig.purifyAutoApply = isChecked
                refreshPurifyAutoSummary()
                refreshMain()
            }
        }
        binding.switchAiPurifyIntercept.setOnCheckedChangeListener { _, isChecked ->
            if (!ignorePurifyFormChanges) {
                AiConfig.purifyExceptionIntercept = isChecked
                refreshMain()
            }
        }
        binding.switchAiPurifyChapterAuto.setOnCheckedChangeListener { _, isChecked ->
            if (!ignorePurifyFormChanges) {
                AiConfig.purifyChapterAutoApply = isChecked
                refreshPurifyAutoSummary()
                refreshMain()
            }
        }
        binding.switchAiPurifyChapterIntercept.setOnCheckedChangeListener { _, isChecked ->
            if (!ignorePurifyFormChanges) {
                AiConfig.purifyChapterExceptionIntercept = isChecked
                refreshMain()
            }
        }
        binding.editPurifyParagraphLimit.doOnTextChanged { text, _, _, _ ->
            if (ignorePurifyFormChanges) {
                return@doOnTextChanged
            }
            text?.toString()?.toIntOrNull()?.let {
                AiConfig.purifyParagraphLimit = it
                refreshMain()
            }
        }
        binding.editPurifyParagraphLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                refreshPurifySettings()
            }
        }
        binding.editPurifyChapterSegmentLimit.doOnTextChanged { text, _, _, _ ->
            if (ignorePurifyFormChanges) {
                return@doOnTextChanged
            }
            text?.toString()?.toIntOrNull()?.let {
                AiConfig.purifyChapterSegmentLimit = it
                refreshMain()
            }
        }
        binding.editPurifyChapterSegmentLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                refreshPurifySettings()
            }
        }
        binding.editPurifyChapterSampleLimit.doOnTextChanged { text, _, _, _ ->
            if (ignorePurifyFormChanges) {
                return@doOnTextChanged
            }
            text?.toString()?.toIntOrNull()?.let {
                AiConfig.purifyChapterSampleLimit = it
                refreshMain()
            }
        }
        binding.editPurifyChapterSampleLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                refreshPurifySettings()
            }
        }
        binding.editPurifyChapterConcurrencyLimit.doOnTextChanged { text, _, _, _ ->
            if (ignorePurifyFormChanges) {
                return@doOnTextChanged
            }
            text?.toString()?.toIntOrNull()?.let {
                AiConfig.purifyChapterConcurrencyLimit = it
                refreshMain()
            }
        }
        binding.editPurifyChapterConcurrencyLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                refreshPurifySettings()
            }
        }
        binding.editPurifyChapterRetryCount.doOnTextChanged { text, _, _, _ ->
            if (ignorePurifyFormChanges) {
                return@doOnTextChanged
            }
            text?.toString()?.toIntOrNull()?.let {
                AiConfig.purifyChapterRetryCount = it
                refreshMain()
            }
        }
        binding.editPurifyChapterRetryCount.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                refreshPurifySettings()
            }
        }
    }

    private fun initProviderList() {
        binding.recyclerProviders.layoutManager = LinearLayoutManager(requireContext())
        providerAdapter.bindToRecyclerView(binding.recyclerProviders)
        providerItemTouchHelper = ItemTouchHelper(ItemTouchCallback(providerAdapter))
        providerItemTouchHelper.attachToRecyclerView(binding.recyclerProviders)
        providerAdapter.setOnItemClickListener { _, item ->
            showDetail(item.id)
        }
        providerAdapter.onSelectProvider = { item ->
            AiProviderStore.setActiveProvider(item.id)
            refreshProviders()
            refreshMain()
        }
        bindSearchEditText(binding.editSearchProvider)
        binding.editSearchProvider.doOnTextChanged { text, _, _, _ ->
            providerSearchQuery = text?.toString().orEmpty()
            refreshProviders()
        }
    }

    private fun bindSearchEditText(editText: EditText) {
        val hint = editText.hint
        val searchColor = ContextCompat.getColor(requireContext(), R.color.tv_text_summary)
        editText.setTextColor(searchColor)
        editText.setHintTextColor(searchColor)
        fun updateHint() {
            editText.hint = if (editText.hasFocus() && editText.text.isNullOrEmpty()) {
                null
            } else {
                hint
            }
        }
        editText.setOnFocusChangeListener { _, _ ->
            updateHint()
        }
        editText.doOnTextChanged { _, _, _, _ ->
            updateHint()
        }
        updateHint()
    }

    private fun initDetail() {
        updateApiKeyVisibility()
        setupProviderAutoSave()
        setupProviderFocusClear()
        binding.buttonToggleApiKey.setOnClickListener {
            apiKeyVisible = !apiKeyVisible
            updateApiKeyVisibility()
        }
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (ignoreModelSelection || modelOptions.isEmpty()) {
                    return
                }
                saveCurrentProvider()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
        binding.buttonFetchModels.setOnClickListener {
            fetchModels()
        }
        binding.buttonTestConnection.setOnClickListener {
            testConnection()
        }
        binding.buttonQueryBalance.setOnClickListener {
            queryBalance()
        }
    }

    private fun setupProviderAutoSave() {
        val fields = listOf(
            binding.editProviderName,
            binding.editApiKey,
            binding.editBaseUrl,
            binding.editChatPath,
            binding.editModelsUrl,
            binding.editTimeoutSeconds
        )
        fields.forEach { editText ->
            editText.doOnTextChanged { _, _, _, _ ->
                if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                    val provider = saveCurrentProvider(updateHeader = editText === binding.editProviderName)
                    if (editText === binding.editApiKey && provider != null) {
                        refreshModelSpinner(provider)
                    }
                }
            }
        }
        binding.switchEnabled.setOnCheckedChangeListener { _, _ ->
            if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                saveCurrentProvider()
            }
        }
    }

    private fun setupProviderFocusClear() {
        val listener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clearProviderInputFocusIfOutside(event)
            }
            false
        }
        binding.layoutProviderDetail.setOnTouchListener(listener)
        binding.scrollDetail.setOnTouchListener(listener)
    }

    private fun initPromptList() {
        binding.recyclerPrompts.layoutManager = LinearLayoutManager(requireContext())
        promptAdapter.bindToRecyclerView(binding.recyclerPrompts)
        promptAdapter.setOnItemClickListener { _, item ->
            showPromptDetail(item)
        }
    }

    private fun initPromptDetail() {
        binding.editPrompt.typeface = Typeface.MONOSPACE
        binding.editPrompt.doOnTextChanged { _, _, _, _ ->
            if (!ignorePromptHighlight) {
                highlightSkillMarkdown()
            }
        }
        binding.buttonSavePrompt.setOnClickListener {
            val prompt = currentPrompt ?: return@setOnClickListener
            AiPromptStore.save(prompt, binding.editPrompt.text?.toString().orEmpty())
            Toast.makeText(requireContext(), R.string.ai_prompt_saved, Toast.LENGTH_SHORT).show()
            showPromptList()
        }
        binding.buttonResetPrompt.setOnClickListener {
            val prompt = currentPrompt ?: return@setOnClickListener
            AiPromptStore.reset(prompt)
            setSkillEditorText(prompt.defaultPrompt)
            Toast.makeText(requireContext(), R.string.ai_prompt_reset_done, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun initBack() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            }
        )
        bindTitleBarBack()
    }

    private fun bindTitleBarBack() {
        requireActivity().findViewById<TitleBar>(R.id.title_bar)
            ?.setNavigationOnClickListener {
                navigateBack()
            }
    }

    private fun navigateBack() {
        when (currentPage) {
            Page.DETAIL -> showProviderList()
            Page.PROMPT_DETAIL -> showPromptList()
            Page.PROVIDERS -> showMain()
            Page.PROMPTS -> showMain()
            Page.PURIFY_SETTINGS -> showMain()
            Page.MAIN -> requireActivity().finish()
        }
    }

    private fun setPageTitle(title: CharSequence) {
        activity?.title = title
        requireActivity().findViewById<TitleBar>(R.id.title_bar)?.title = title
    }

    private fun setPageTitle(resId: Int) {
        setPageTitle(getString(resId))
    }

    private fun showMain() {
        currentPage = Page.MAIN
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_setting)
        binding.layoutMainMenu.isVisible = true
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        bindTitleBarBack()
        refreshMain()
    }

    private fun showProviderList() {
        currentPage = Page.PROVIDERS
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_provider_menu)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = true
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        bindTitleBarBack()
        refreshProviders()
    }

    private fun showDetail(providerId: String) {
        currentPage = Page.DETAIL
        currentProviderId = providerId
        currentPrompt = null
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = true
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        bindTitleBarBack()
        refreshCurrentDetail()
    }

    private fun showPromptList() {
        currentPage = Page.PROMPTS
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_prompt_menu)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = true
        binding.layoutPromptDetail.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        bindTitleBarBack()
        refreshPrompts()
    }

    private fun showPromptDetail(prompt: AiPromptStore.Prompt) {
        currentPage = Page.PROMPT_DETAIL
        currentProviderId = null
        currentPrompt = prompt
        setPageTitle(prompt.displayName())
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = true
        binding.layoutPurifySettings.isVisible = false
        bindTitleBarBack()
        refreshPromptDetail()
    }

    private fun showPurifySettings() {
        currentPage = Page.PURIFY_SETTINGS
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_purify_settings)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutPurifySettings.isVisible = true
        bindTitleBarBack()
        refreshPurifySettings()
    }

    private fun refreshCurrentPage() {
        when (currentPage) {
            Page.MAIN -> refreshMain()
            Page.PROVIDERS -> refreshProviders()
            Page.DETAIL -> refreshCurrentDetail()
            Page.PROMPTS -> refreshPrompts()
            Page.PROMPT_DETAIL -> refreshPromptDetail()
            Page.PURIFY_SETTINGS -> refreshPurifySettings()
        }
    }

    private fun refreshMain() {
        val providers = AiProviderStore.providers()
        val activeProvider = AiProviderStore.activeProvider()
        val color = accentColor
        val entryIconTint = ColorStateList.valueOf(color)
        binding.textMainSectionLabel.setTextColor(color)
        binding.imageProviderEntryIcon.imageTintList = entryIconTint
        binding.imagePromptEntryIcon.imageTintList = entryIconTint
        binding.imagePurifyEntryIcon.imageTintList = entryIconTint
        binding.textProviderEntrySummary.text = getString(
            R.string.ai_provider_menu_summary,
            providers.size.toString(),
            activeProvider.name
        )
        binding.textPromptEntrySummary.text = getString(
            R.string.ai_prompt_menu_summary,
            visibleAiPrompts().size.toString()
        )
        binding.textPurifyEntrySummary.text = getString(
            R.string.ai_purify_settings_summary,
            getString(if (AiConfig.purifyAutoApply) R.string.enabled else R.string.disabled),
            getString(if (AiConfig.purifyChapterAutoApply) R.string.enabled else R.string.disabled),
            AiConfig.purifyParagraphLimit.toString(),
            AiConfig.purifyChapterSegmentLimit.toString(),
            AiConfig.purifyChapterSampleLimit.toString(),
            AiConfig.purifyChapterConcurrencyLimit.toString(),
            AiConfig.purifyChapterRetryCount.toString()
        )
    }

    private fun refreshProviders() {
        val providers = AiProviderStore.providers()
            .filter {
                providerSearchQuery.isBlank()
                    || it.name.contains(providerSearchQuery, ignoreCase = true)
                    || it.type.displayName.contains(providerSearchQuery, ignoreCase = true)
            }
        providerAdapter.activeProviderId = AiProviderStore.activeProviderId()
        providerAdapter.setItems(providers)
    }

    private fun refreshCurrentDetail() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        setPageTitle(provider.name)
        binding.textDetailTitle.text = provider.name
        binding.imageDetailIcon.setImageResource(provider.iconRes())
        ignoreProviderFormChanges = true
        try {
            binding.switchEnabled.isChecked = provider.enabled
            binding.editProviderName.setText(provider.name)
            binding.editBaseUrl.setText(provider.baseUrl)
            binding.editApiKey.setText(provider.apiKey)
            binding.editChatPath.setText(provider.chatCompletionsPath)
            binding.editModelsUrl.setText(provider.modelsUrl)
            binding.editTimeoutSeconds.setText(provider.timeoutSeconds.toString())
            val openAiCompatible = provider.type == AiProviderType.OPENAI
            binding.textOpenaiPathLabel.isVisible = openAiCompatible
            binding.editChatPath.isVisible = openAiCompatible
            binding.textModelsUrlLabel.isVisible = openAiCompatible
            binding.editModelsUrl.isVisible = openAiCompatible
            refreshModelSpinner(provider)
            updateApiKeyVisibility()
        } finally {
            ignoreProviderFormChanges = false
        }
    }

    private fun refreshPrompts() {
        promptAdapter.setItems(visibleAiPrompts())
    }

    private fun visibleAiPrompts(): List<AiPromptStore.Prompt> {
        return AiPromptStore.Prompt.entries.toList()
    }

    private fun refreshPromptDetail() {
        val prompt = currentPrompt ?: return
        binding.textPromptDetailTitle.text = prompt.displayName()
        binding.textPromptDetailSummary.text = prompt.summary()
        setSkillEditorText(AiPromptStore.prompt(prompt))
    }

    private fun setSkillEditorText(text: String) {
        ignorePromptHighlight = true
        try {
            binding.editPrompt.setText(text)
            binding.editPrompt.setSelection(0)
        } finally {
            ignorePromptHighlight = false
        }
        highlightSkillMarkdown()
    }

    private fun highlightSkillMarkdown() {
        val editable = binding.editPrompt.text ?: return
        val length = editable.length
        if (length == 0) {
            return
        }
        val selectionStart = binding.editPrompt.selectionStart
        val selectionEnd = binding.editPrompt.selectionEnd
        editable.getSpans(0, length, SkillMarkdownSpan::class.java).forEach {
            editable.removeSpan(it)
        }

        val text = editable.toString()
        val headingColor = accentColor
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant)
        val codeColor = ContextCompat.getColor(requireContext(), R.color.ng_info)
        val listColor = ContextCompat.getColor(requireContext(), R.color.ng_warning)
        val codeBackground = ContextCompat.getColor(requireContext(), R.color.ng_neutral_container)
        var lineStart = 0
        var inFence = false
        text.split('\n').forEach { line ->
            val lineEnd = lineStart + line.length
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("```") -> {
                    editable.applySkillSpan(SkillMarkdownColorSpan(codeColor), lineStart, lineEnd)
                    editable.applySkillSpan(SkillMarkdownStyleSpan(Typeface.BOLD), lineStart, lineEnd)
                    inFence = !inFence
                }
                inFence -> {
                    editable.applySkillSpan(
                        SkillMarkdownBackgroundSpan(codeBackground),
                        lineStart,
                        lineEnd
                    )
                    editable.applySkillSpan(SkillMarkdownColorSpan(codeColor), lineStart, lineEnd)
                }
                trimmed.startsWith("#") -> {
                    editable.applySkillSpan(SkillMarkdownColorSpan(headingColor), lineStart, lineEnd)
                    editable.applySkillSpan(SkillMarkdownStyleSpan(Typeface.BOLD), lineStart, lineEnd)
                }
                trimmed == "---" -> {
                    editable.applySkillSpan(SkillMarkdownColorSpan(mutedColor), lineStart, lineEnd)
                    editable.applySkillSpan(SkillMarkdownStyleSpan(Typeface.BOLD), lineStart, lineEnd)
                }
                trimmed.startsWith("- ") || trimmed.matches(Regex("""\d+\.\s+.*""")) -> {
                    val markerEnd = lineStart + line.length - trimmed.length + when {
                        trimmed.startsWith("- ") -> 1
                        else -> trimmed.indexOf('.').takeIf { it >= 0 }?.plus(1) ?: 0
                    }
                    editable.applySkillSpan(SkillMarkdownColorSpan(listColor), lineStart, markerEnd)
                }
            }
            lineStart = lineEnd + 1
        }

        Regex("`[^`\\n]+`").findAll(text).forEach { match ->
            editable.applySkillSpan(
                SkillMarkdownColorSpan(codeColor),
                match.range.first,
                match.range.last + 1
            )
            editable.applySkillSpan(
                SkillMarkdownBackgroundSpan(codeBackground),
                match.range.first,
                match.range.last + 1
            )
        }
        Regex("\\*\\*[^*\\n]+\\*\\*").findAll(text).forEach { match ->
            editable.applySkillSpan(
                SkillMarkdownStyleSpan(Typeface.BOLD),
                match.range.first,
                match.range.last + 1
            )
        }

        if (selectionStart >= 0 && selectionEnd >= 0) {
            binding.editPrompt.setSelection(
                selectionStart.coerceAtMost(editable.length),
                selectionEnd.coerceAtMost(editable.length)
            )
        }
    }

    private fun refreshPurifySettings() {
        ignorePurifyFormChanges = true
        try {
            val color = accentColor
            val entryIconTint = ColorStateList.valueOf(color)
            binding.textPurifySectionLabel.setTextColor(color)
            binding.textPurifyChapterSectionLabel.setTextColor(color)
            binding.imagePurifyAutoIcon.imageTintList = entryIconTint
            binding.imagePurifyInterceptIcon.imageTintList = entryIconTint
            binding.imagePurifyLimitIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterAutoIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterInterceptIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterLimitIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterSampleLimitIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterConcurrencyIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterRetryIcon.imageTintList = entryIconTint
            binding.imagePurifyChapterRuleTypesIcon.imageTintList = entryIconTint
            binding.switchAiPurifyAuto.isChecked = AiConfig.purifyAutoApply
            binding.switchAiPurifyIntercept.isChecked = AiConfig.purifyExceptionIntercept
            binding.switchAiPurifyChapterAuto.isChecked = AiConfig.purifyChapterAutoApply
            binding.switchAiPurifyChapterIntercept.isChecked =
                AiConfig.purifyChapterExceptionIntercept
            val limit = AiConfig.purifyParagraphLimit.toString()
            if (binding.editPurifyParagraphLimit.text?.toString() != limit) {
                binding.editPurifyParagraphLimit.setText(limit)
                binding.editPurifyParagraphLimit.setSelection(limit.length)
            }
            val chapterLimit = AiConfig.purifyChapterSegmentLimit.toString()
            if (binding.editPurifyChapterSegmentLimit.text?.toString() != chapterLimit) {
                binding.editPurifyChapterSegmentLimit.setText(chapterLimit)
                binding.editPurifyChapterSegmentLimit.setSelection(chapterLimit.length)
            }
            val chapterSampleLimit = AiConfig.purifyChapterSampleLimit.toString()
            if (binding.editPurifyChapterSampleLimit.text?.toString() != chapterSampleLimit) {
                binding.editPurifyChapterSampleLimit.setText(chapterSampleLimit)
                binding.editPurifyChapterSampleLimit.setSelection(chapterSampleLimit.length)
            }
            val chapterConcurrencyLimit = AiConfig.purifyChapterConcurrencyLimit.toString()
            if (binding.editPurifyChapterConcurrencyLimit.text?.toString() != chapterConcurrencyLimit) {
                binding.editPurifyChapterConcurrencyLimit.setText(chapterConcurrencyLimit)
                binding.editPurifyChapterConcurrencyLimit.setSelection(chapterConcurrencyLimit.length)
            }
            val chapterRetryCount = AiConfig.purifyChapterRetryCount.toString()
            if (binding.editPurifyChapterRetryCount.text?.toString() != chapterRetryCount) {
                binding.editPurifyChapterRetryCount.setText(chapterRetryCount)
                binding.editPurifyChapterRetryCount.setSelection(chapterRetryCount.length)
            }
        } finally {
            ignorePurifyFormChanges = false
        }
        refreshPurifyAutoSummary()
        refreshPurifyChapterRuleTypeSummary()
    }

    private fun refreshPurifyAutoSummary() {
        binding.textAiPurifyAutoSummary.text = getString(
            if (AiConfig.purifyAutoApply) {
                R.string.ai_purify_auto_apply_summary_on
            } else {
                R.string.ai_purify_auto_apply_summary_off
            }
        )
        binding.textAiPurifyChapterAutoSummary.text = getString(
            if (AiConfig.purifyChapterAutoApply) {
                R.string.ai_purify_auto_apply_summary_on
            } else {
                R.string.ai_purify_auto_apply_summary_off
            }
        )
    }

    private fun showPurifyChapterRuleTypeDialog() {
        val paddingHorizontal = 24.dpToPx()
        val paddingVertical = 8.dpToPx()
        val switches = arrayListOf<Switch>()
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        }
        switches += content.addPurifyChapterRuleTypeSwitch(
            text = getString(R.string.ai_purify_rule_type_typo_full),
            checked = AiConfig.purifyChapterRuleTypo
        )
        switches += content.addPurifyChapterRuleTypeSwitch(
            text = getString(R.string.ai_purify_rule_type_noise_full),
            checked = AiConfig.purifyChapterRuleNoise
        )
        switches += content.addPurifyChapterRuleTypeSwitch(
            text = getString(R.string.ai_purify_rule_type_ad_full),
            checked = AiConfig.purifyChapterRuleAd
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_purify_chapter_rule_types)
            .setView(content)
            .setPositiveButton(R.string.sure, null)
            .show()
            .applyTint()
        switches.forEach { switchView ->
            switchView.setOnCheckedChangeListener { _, isChecked ->
                when (switches.indexOf(switchView)) {
                    0 -> AiConfig.purifyChapterRuleTypo = isChecked
                    1 -> AiConfig.purifyChapterRuleNoise = isChecked
                    2 -> AiConfig.purifyChapterRuleAd = isChecked
                }
                refreshPurifyChapterRuleTypeSummary()
                refreshMain()
            }
        }
    }

    private fun LinearLayout.addPurifyChapterRuleTypeSwitch(
        text: String,
        checked: Boolean
    ): Switch {
        val switchView = Switch(requireContext()).apply {
            isChecked = checked
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            isClickable = true
            setOnClickListener {
                switchView.isChecked = !switchView.isChecked
            }
        }
        row.addView(TextView(requireContext()).apply {
            this.text = text
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        })
        row.addView(switchView)
        addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return switchView
    }

    private fun refreshPurifyChapterRuleTypeSummary() {
        val enabledLabels = listOfNotNull(
            getString(R.string.ai_purify_rule_type_typo_full)
                .takeIf { AiConfig.purifyChapterRuleTypo },
            getString(R.string.ai_purify_rule_type_noise_full)
                .takeIf { AiConfig.purifyChapterRuleNoise },
            getString(R.string.ai_purify_rule_type_ad_full)
                .takeIf { AiConfig.purifyChapterRuleAd }
        )
        binding.textAiPurifyChapterRuleTypesSummary.text = when (enabledLabels.size) {
            0 -> getString(R.string.ai_purify_chapter_rule_types_none)
            3 -> getString(R.string.ai_purify_chapter_rule_types_all)
            else -> getString(
                R.string.ai_purify_chapter_rule_types_summary,
                enabledLabels.joinToString(getString(R.string.ai_purify_chapter_rule_types_separator))
            )
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun readProviderFromForm(): AiProviderSetting? {
        val source = currentProviderId?.let { AiProviderStore.provider(it) } ?: return null
        val selectedModel = binding.spinnerModel.selectedItem?.toString()
            ?.takeIf { it in modelOptions }
            .orEmpty()
        return source.copy(
            enabled = binding.switchEnabled.isChecked,
            name = binding.editProviderName.text?.toString()?.trim().orEmpty().ifBlank {
                source.name
            },
            apiKey = binding.editApiKey.text?.toString()?.trim().orEmpty(),
            baseUrl = binding.editBaseUrl.text?.toString()?.trim().orEmpty().ifBlank {
                source.baseUrl
            },
            model = selectedModel,
            timeoutSeconds = binding.editTimeoutSeconds.text?.toString()
                ?.toIntOrNull()
                ?.coerceIn(5, 600)
                ?: source.timeoutSeconds,
            chatCompletionsPath = binding.editChatPath.text?.toString()?.trim().orEmpty()
                .ifBlank { source.chatCompletionsPath },
            modelsUrl = binding.editModelsUrl.text?.toString()?.trim().orEmpty()
        )
    }

    private fun saveCurrentProvider(
        showToast: Boolean = false,
        updateHeader: Boolean = false
    ): AiProviderSetting? {
        val provider = readProviderFromForm() ?: return null
        AiProviderStore.saveProvider(provider)
        if (updateHeader) {
            setPageTitle(provider.name)
            binding.textDetailTitle.text = provider.name
        }
        if (showToast) {
            Toast.makeText(requireContext(), R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
        }
        return provider
    }

    private fun clearProviderInputFocusIfOutside(event: MotionEvent) {
        val focus = requireActivity().currentFocus as? EditText ?: return
        if (!focus.isShown || isTouchInsideView(focus, event.rawX.toInt(), event.rawY.toInt())) {
            return
        }
        focus.clearFocus()
        focus.hideSoftInput()
    }

    private fun isTouchInsideView(view: View, rawX: Int, rawY: Int): Boolean {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(rawX, rawY)
    }

    private fun fetchModels() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            alert(getString(R.string.ai_fetch_models)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        waitDialog.setText(R.string.ai_fetch_models)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val models = AiManager.fetchAndSaveModels(provider.id)
                check(models.isNotEmpty()) { getString(R.string.ai_model_list_empty) }
                val saved = AiProviderStore.provider(provider.id) ?: provider
                if (saved.model.isBlank() || models.none { it.id == saved.model }) {
                    AiProviderStore.saveProvider(saved.copy(model = models.first().id))
                }
                refreshCurrentDetail()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.ai_model_list_count, models.size.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Throwable) {
                alert(getString(R.string.ai_fetch_models)) {
                    setMessage(getString(R.string.ai_test_failed, e.localizedMessage ?: e.toString()))
                    okButton()
                }
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun testConnection() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            alert(getString(R.string.ai_test_connection)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        waitDialog.setText(R.string.ai_test_connection)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val result = AiManager.testConnection(provider.id)
                alert(getString(R.string.ai_test_connection)) {
                    setMessage(
                        getString(
                            R.string.ai_test_success,
                            result.content.take(300),
                            result.model ?: provider.model
                        )
                    )
                    okButton()
                }
            } catch (e: Throwable) {
                alert(getString(R.string.ai_test_connection)) {
                    setMessage(getString(R.string.ai_test_failed, e.localizedMessage ?: e.toString()))
                    okButton()
                }
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun queryBalance() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            alert(getString(R.string.ai_query_balance)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        waitDialog.setText(R.string.ai_query_balance)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val result = AiManager.queryBalance(provider.id)
                alert(getString(R.string.ai_query_balance)) {
                    setMessage(formatBalanceResult(result))
                    okButton()
                }
            } catch (e: Throwable) {
                alert(getString(R.string.ai_query_balance)) {
                    setMessage(getString(R.string.ai_test_failed, e.localizedMessage ?: e.toString()))
                    okButton()
                }
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun updateApiKeyVisibility() {
        val selection = binding.editApiKey.selectionStart.coerceAtLeast(0)
        binding.editApiKey.inputType = if (apiKeyVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.buttonToggleApiKey.setImageResource(
            if (apiKeyVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        )
        binding.editApiKey.setSelection(selection.coerceAtMost(binding.editApiKey.text?.length ?: 0))
    }

    private fun formatBalanceResult(result: io.legado.app.help.ai.AiBalanceResult): String {
        val items = result.items.joinToString("\n") { item ->
            buildString {
                append(
                    getString(
                        R.string.ai_balance_item,
                        item.name,
                        item.remaining?.formatBalanceNumber() ?: "-",
                        item.unit.orEmpty()
                    ).trim()
                )
                if (item.total != null || item.used != null) {
                    append('\n')
                    append(
                        getString(
                            R.string.ai_balance_item_detail,
                            item.total?.formatBalanceNumber() ?: "-",
                            item.used?.formatBalanceNumber() ?: "-",
                            item.unit.orEmpty()
                        ).trim()
                    )
                }
                if (item.isValid == false && !item.invalidMessage.isNullOrBlank()) {
                    append('\n')
                    append(item.invalidMessage)
                }
            }
        }
        return getString(R.string.ai_balance_result, result.providerName, items)
    }

    private fun Double.formatBalanceNumber(): String {
        return balanceNumberFormat.format(this)
    }

    private fun refreshModelSpinner(provider: AiProviderSetting) {
        val options = if (provider.apiKey.isBlank()) {
            emptyList()
        } else {
            buildList {
                if (provider.model.isNotBlank()) {
                    add(provider.model)
                }
                addAll(provider.models.map { it.id }.filter { it.isNotBlank() })
            }
                .distinct()
        }
        modelOptions = options
        val displayItems = if (options.isEmpty()) {
            listOf(getString(R.string.ai_model_list_empty))
        } else {
            options
        }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ignoreModelSelection = true
        binding.spinnerModel.adapter = adapter
        binding.spinnerModel.isEnabled = options.isNotEmpty()
        val selectedIndex = options.indexOf(provider.model).takeIf { it >= 0 } ?: 0
        binding.spinnerModel.setSelection(selectedIndex, false)
        ignoreModelSelection = false
    }

    private fun AiProviderSetting.iconRes(): Int {
        return when (id) {
            "openai" -> R.drawable.ic_provider_openai
            "claude" -> R.drawable.ic_provider_claude
            "gemini" -> R.drawable.ic_provider_gemini
            "deepseek" -> R.drawable.ic_provider_deepseek
            "xiaomi_mimo" -> R.drawable.ic_provider_mimo
            else -> R.drawable.ic_ai_provider
        }
    }

    private fun AiProviderSetting.visibleModelCount(): Int {
        return if (apiKey.isBlank()) 0 else models.size
    }

    private fun AiPromptStore.Prompt.displayName(): String {
        return when (this) {
            AiPromptStore.Prompt.PARAGRAPH_PURIFY -> getString(R.string.ai_prompt_paragraph_purify)
            AiPromptStore.Prompt.RULE_GENERATE -> getString(R.string.ai_prompt_rule_generate)
        }
    }

    private fun AiPromptStore.Prompt.summary(): String {
        return when (this) {
            AiPromptStore.Prompt.PARAGRAPH_PURIFY ->
                getString(R.string.ai_prompt_paragraph_purify_summary)
            AiPromptStore.Prompt.RULE_GENERATE ->
                getString(R.string.ai_prompt_rule_generate_summary)
        }
    }

    private fun AiPromptStore.Prompt.iconText(): String {
        return when (this) {
            AiPromptStore.Prompt.PARAGRAPH_PURIFY -> "段"
            AiPromptStore.Prompt.RULE_GENERATE -> "规"
        }
    }

    private inner class AiProviderAdapter :
        RecyclerAdapter<AiProviderSetting, ItemAiProviderBinding>(requireContext()),
        ItemTouchCallback.Callback {

        var activeProviderId: String = ""
        var onSelectProvider: ((AiProviderSetting) -> Unit)? = null
        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemAiProviderBinding {
            return ItemAiProviderBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiProviderBinding,
            item: AiProviderSetting,
            payloads: MutableList<Any>
        ) {
            val isActive = item.id == activeProviderId
            binding.imageIcon.setImageResource(item.iconRes())
            binding.viewActiveIndicator.isVisible = isActive
            binding.imageDragHandle.isVisible = providerSearchQuery.isBlank()
            binding.textName.text = item.name
            binding.textEnabled.setBackgroundResource(
                if (item.enabled) R.drawable.ng_bg_tag_success else R.drawable.ng_bg_tag_warning
            )
            binding.textEnabled.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (item.enabled) R.color.ng_success else R.color.ng_warning
                )
            )
            binding.textEnabled.text =
                if (item.enabled) getString(R.string.enabled) else getString(R.string.disabled)
            binding.textModelCount.text =
                getString(R.string.ai_model_list_count, item.visibleModelCount().toString())
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun registerListener(holder: ItemViewHolder, binding: ItemAiProviderBinding) {
            binding.layoutSelectProvider.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    onSelectProvider?.invoke(item)
                }
            }
            binding.imageDragHandle.setOnTouchListener { _, event ->
                if (providerSearchQuery.isBlank()) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        providerItemTouchHelper.startDrag(holder)
                    }
                    true
                } else {
                    false
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            if (providerSearchQuery.isNotBlank()) {
                return false
            }
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved && providerSearchQuery.isBlank()) {
                AiProviderStore.saveProviders(getItems())
                refreshMain()
            }
            isMoved = false
        }
    }

    private inner class AiPromptAdapter :
        RecyclerAdapter<AiPromptStore.Prompt, ItemAiPromptBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemAiPromptBinding {
            return ItemAiPromptBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiPromptBinding,
            item: AiPromptStore.Prompt,
            payloads: MutableList<Any>
        ) {
            binding.textIcon.text = item.iconText()
            binding.textName.text = item.displayName()
            binding.textSummary.text = item.summary()
            binding.textCustom.isVisible = AiPromptStore.isCustom(item)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiPromptBinding) {
        }
    }

    private interface SkillMarkdownSpan

    private class SkillMarkdownColorSpan(color: Int) :
        ForegroundColorSpan(color),
        SkillMarkdownSpan

    private class SkillMarkdownBackgroundSpan(color: Int) :
        BackgroundColorSpan(color),
        SkillMarkdownSpan

    private class SkillMarkdownStyleSpan(style: Int) :
        StyleSpan(style),
        SkillMarkdownSpan

    private fun Editable.applySkillSpan(span: SkillMarkdownSpan, start: Int, end: Int) {
        if (start < end && start >= 0 && end <= length) {
            setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
