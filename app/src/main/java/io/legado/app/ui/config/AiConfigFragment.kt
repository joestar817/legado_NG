package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogAiModelEditBinding
import io.legado.app.databinding.FragmentAiConfigBinding
import io.legado.app.databinding.ItemAiModelBinding
import io.legado.app.databinding.ItemAiProviderBinding
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiPromptStore
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.help.ai.normalizeAiApiPath
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

class AiConfigFragment : BaseFragment(R.layout.fragment_ai_config), ConfigBackHandler {

    private enum class Page {
        MAIN,
        PROVIDERS,
        DETAIL,
        MODEL_DETAIL,
        PROMPTS,
        PROMPT_DETAIL,
        MODEL_SETTINGS,
        PURIFY_MODEL_SETTINGS,
        PURIFY_SETTINGS
    }
    private enum class ProviderDetailTab { CONFIG, MODELS }

    private val binding by viewBinding(FragmentAiConfigBinding::bind)
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val providerAdapter by lazy { AiProviderAdapter() }
    private val modelAdapter by lazy { AiModelAdapter() }
    private val promptAdapter by lazy { AiPromptAdapter() }
    private lateinit var providerItemTouchHelper: ItemTouchHelper
    private var currentPage = Page.MAIN
    private var currentProviderId: String? = null
    private var currentModelId: String? = null
    private var currentPrompt: AiPromptStore.Prompt? = null
    private var providerSearchQuery: String = ""
    private var modelSearchQuery: String = ""
    private var providerDetailTab = ProviderDetailTab.CONFIG
    private var requestJob: Job? = null
    private var ignoreProviderFormChanges = false
    private var ignorePurifyFormChanges = false
    private var ignorePromptHighlight = false
    private var ignoreModelDetailChanges = false
    private var apiKeyVisible = false
    private val balanceNumberFormat by lazy { DecimalFormat("0.####") }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMain()
        initProviderList()
        initDetail()
        initPromptList()
        initPromptDetail()
        initModelSettings()
        initPurifySettings()
        showMain()
    }

    override fun onResume() {
        super.onResume()
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
        binding.layoutModelEntry.setOnClickListener {
            showModelSettings()
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
        providerItemTouchHelper = ItemTouchHelper(ItemTouchCallback(providerAdapter).apply {
            isCanSwipe = true
        })
        providerItemTouchHelper.attachToRecyclerView(binding.recyclerProviders)
        providerAdapter.setOnItemClickListener { _, item ->
            showDetail(item.id)
        }
        binding.buttonAddProvider.setOnClickListener {
            showAddProviderDialog()
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
        binding.recyclerModels.layoutManager = LinearLayoutManager(requireContext())
        modelAdapter.bindToRecyclerView(binding.recyclerModels)
        modelAdapter.onToggleModel = { model, checked ->
            toggleAvailableModel(model, checked)
        }
        modelAdapter.setOnItemClickListener { _, item ->
            item.safeId().takeIf { it.isNotBlank() }?.let(::showModelDetail)
        }
        binding.buttonToggleModelSelection.isVisible = true
        binding.buttonToggleModelSelection.setOnClickListener {
            toggleVisibleModelSelection()
        }
        bindSearchEditText(binding.editSearchModel)
        binding.editSearchModel.doOnTextChanged { text, _, _, _ ->
            modelSearchQuery = text?.toString().orEmpty()
            refreshModelList(currentProviderId?.let { AiProviderStore.provider(it) })
        }
        binding.buttonToggleApiKey.setOnClickListener {
            apiKeyVisible = !apiKeyVisible
            updateApiKeyVisibility()
        }
        binding.buttonProviderTabConfig.setOnClickListener {
            showProviderDetailTab(ProviderDetailTab.CONFIG)
        }
        binding.buttonProviderTabModels.setOnClickListener {
            showProviderDetailTab(ProviderDetailTab.MODELS)
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
        binding.switchCustomModelsUrl.setOnCheckedChangeListener { _, _ ->
            if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                saveCurrentProvider()
                updateCustomEndpointVisibility()
            }
        }
        binding.switchCustomBalanceUrl.setOnCheckedChangeListener { _, _ ->
            if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                saveCurrentProvider()
                updateCustomEndpointVisibility()
            }
        }
        binding.buttonDeleteProvider.setOnClickListener {
            confirmDeleteCurrentProvider()
        }
        setupModelDetailAutoSave()
    }

    private fun initModelSettings() {
        binding.layoutPurifyModelSettingsEntry.setOnClickListener {
            showPurifyModelSettings()
        }
        binding.layoutPurifyModelEntry.setOnClickListener {
            showPurifyModelSelectDialog()
        }
        binding.layoutPurifyReasoningEntry.setOnClickListener {
            showPurifyReasoningDialog()
        }
    }

    private fun setupModelDetailAutoSave() {
        binding.editModelDisplayName.doOnTextChanged { _, _, _, _ ->
            if (!ignoreModelDetailChanges && currentPage == Page.MODEL_DETAIL) {
                saveCurrentModel()
            }
        }
        binding.segmentModelTypeChat.setOnClickListener {
            selectModelType(AiModelType.CHAT, save = true)
        }
        binding.segmentModelTypeImage.setOnClickListener {
            selectModelType(AiModelType.IMAGE, save = true)
        }
        binding.segmentModelTypeEmbedding.setOnClickListener {
            selectModelType(AiModelType.EMBEDDING, save = true)
        }
        binding.segmentModelTypeAsr.setOnClickListener {
            selectModelType(AiModelType.ASR, save = true)
        }
        binding.segmentModelTypeTts.setOnClickListener {
            selectModelType(AiModelType.TTS, save = true)
        }
        binding.segmentModelTypeVideo.setOnClickListener {
            selectModelType(AiModelType.VIDEO, save = true)
        }
        listOf(
            binding.segmentModelInputText,
            binding.segmentModelInputImage,
            binding.segmentModelInputVideo,
            binding.segmentModelOutputText,
            binding.segmentModelOutputImage,
            binding.segmentModelOutputVideo,
            binding.segmentModelAbilityTool,
            binding.segmentModelAbilityReasoning
        ).forEach { segment ->
            segment.setOnClickListener {
                segment.isSelected = !segment.isSelected
                refreshModelSegmentStyles()
                if (!ignoreModelDetailChanges && currentPage == Page.MODEL_DETAIL) {
                    saveCurrentModel()
                }
            }
        }
    }

    private fun setupProviderAutoSave() {
        val fields = listOf(
            binding.editProviderName,
            binding.editApiKey,
            binding.editBaseUrl,
            binding.editChatPath,
            binding.editModelsUrl,
            binding.editBalanceUrl,
            binding.editBalanceJsonPath,
            binding.editTimeoutSeconds
        )
        fields.forEach { editText ->
            editText.doOnTextChanged { _, _, _, _ ->
                if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                    val provider = saveCurrentProvider(updateHeader = editText === binding.editProviderName)
                    if (editText === binding.editApiKey && provider != null) {
                        refreshModelList(provider)
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

    override fun onConfigBackPressed(): Boolean {
        val page = visiblePage()
        if (page == Page.MAIN) {
            return false
        }
        currentPage = page
        navigateBack()
        return true
    }

    private fun visiblePage(): Page {
        return when {
            binding.layoutModelDetail.isVisible -> Page.MODEL_DETAIL
            binding.layoutProviderDetail.isVisible -> Page.DETAIL
            binding.layoutProviderList.isVisible -> Page.PROVIDERS
            binding.layoutPromptDetail.isVisible -> Page.PROMPT_DETAIL
            binding.layoutPromptList.isVisible -> Page.PROMPTS
            binding.layoutPurifyModelSettings.isVisible -> Page.PURIFY_MODEL_SETTINGS
            binding.layoutModelSettings.isVisible -> Page.MODEL_SETTINGS
            binding.layoutPurifySettings.isVisible -> Page.PURIFY_SETTINGS
            else -> Page.MAIN
        }
    }

    private fun navigateBack() {
        when (currentPage) {
            Page.MODEL_DETAIL -> showDetail(currentProviderId ?: return, ProviderDetailTab.MODELS)
            Page.DETAIL -> showProviderList()
            Page.PROMPT_DETAIL -> showPromptList()
            Page.PROVIDERS -> showMain()
            Page.PROMPTS -> showMain()
            Page.MODEL_SETTINGS -> showMain()
            Page.PURIFY_MODEL_SETTINGS -> showModelSettings()
            Page.PURIFY_SETTINGS -> showMain()
            Page.MAIN -> Unit
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
        currentModelId = null
        currentPrompt = null
        setPageTitle(R.string.ai_setting)
        binding.layoutMainMenu.isVisible = true
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshMain()
    }

    private fun showProviderList() {
        currentPage = Page.PROVIDERS
        currentProviderId = null
        currentModelId = null
        currentPrompt = null
        setPageTitle(R.string.ai_provider_menu)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = true
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshProviders()
    }

    private fun showDetail(
        providerId: String,
        tab: ProviderDetailTab = ProviderDetailTab.CONFIG
    ) {
        currentPage = Page.DETAIL
        currentProviderId = providerId
        currentModelId = null
        currentPrompt = null
        providerDetailTab = tab
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = true
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshCurrentDetail()
    }

    private fun showModelDetail(modelId: String) {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val model = provider.displayModels().firstOrNull { it.safeId() == modelId } ?: return
        showModelEditDialog(provider, model)
    }

    private fun showPromptList() {
        currentPage = Page.PROMPTS
        currentProviderId = null
        currentModelId = null
        currentPrompt = null
        setPageTitle(R.string.ai_prompt_menu)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = true
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshPrompts()
    }

    private fun showPromptDetail(prompt: AiPromptStore.Prompt) {
        currentPage = Page.PROMPT_DETAIL
        currentProviderId = null
        currentModelId = null
        currentPrompt = prompt
        setPageTitle(prompt.displayName())
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = true
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshPromptDetail()
    }

    private fun showModelSettings() {
        currentPage = Page.MODEL_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentPrompt = null
        setPageTitle(R.string.ai_model_settings)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = true
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshModelSettings()
    }

    private fun showPurifyModelSettings() {
        currentPage = Page.PURIFY_MODEL_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentPrompt = null
        setPageTitle(R.string.ai_purify)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = true
        binding.layoutPurifySettings.isVisible = false
        refreshModelSettings()
    }

    private fun showPurifySettings() {
        currentPage = Page.PURIFY_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentPrompt = null
        setPageTitle(R.string.ai_purify_settings)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = true
        refreshPurifySettings()
    }

    private fun refreshCurrentPage() {
        when (currentPage) {
            Page.MAIN -> refreshMain()
            Page.PROVIDERS -> refreshProviders()
            Page.DETAIL -> refreshCurrentDetail()
            Page.MODEL_DETAIL -> refreshCurrentModelDetail()
            Page.PROMPTS -> refreshPrompts()
            Page.PROMPT_DETAIL -> refreshPromptDetail()
            Page.MODEL_SETTINGS -> refreshModelSettings()
            Page.PURIFY_MODEL_SETTINGS -> refreshModelSettings()
            Page.PURIFY_SETTINGS -> refreshPurifySettings()
        }
    }

    private fun refreshMain() {
        val providers = AiProviderStore.providers()
        val color = accentColor
        val entryIconTint = ColorStateList.valueOf(color)
        binding.textMainSectionLabel.setTextColor(color)
        binding.imageProviderEntryIcon.imageTintList = entryIconTint
        binding.imageModelEntryIcon.imageTintList = entryIconTint
        binding.imagePromptEntryIcon.imageTintList = entryIconTint
        binding.imagePurifyEntryIcon.imageTintList = entryIconTint
        binding.textProviderEntrySummary.text = getString(
            R.string.ai_provider_menu_summary,
            providers.size.toString()
        )
        binding.textModelEntrySummary.text = getString(
            R.string.ai_model_settings_summary,
            purifyModelSummaryText()
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
                    || it.type.localizedDisplayName().contains(providerSearchQuery, ignoreCase = true)
            }
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
            binding.textProviderTypeValue.text = provider.type.localizedDisplayName()
            binding.textProviderTypeLabel.isVisible = !provider.builtIn
            binding.textProviderTypeValue.isVisible = !provider.builtIn
            binding.editBaseUrl.setText(provider.baseUrl)
            binding.editApiKey.setText(provider.apiKey)
            binding.editChatPath.setText(provider.chatCompletionsPath)
            binding.editModelsUrl.setText(normalizeAiApiPath(provider.baseUrl, provider.modelsUrl))
            binding.switchCustomModelsUrl.isChecked = provider.useCustomModelsUrl
            binding.switchCustomBalanceUrl.isChecked = provider.useCustomBalanceUrl
            binding.editBalanceUrl.setText(normalizeAiApiPath(provider.baseUrl, provider.balanceUrl))
            binding.editBalanceJsonPath.setText(provider.balanceJsonPath)
            binding.editTimeoutSeconds.setText(provider.timeoutSeconds.toString())
            binding.buttonDeleteProvider.isVisible = !provider.builtIn
            val openAiCompatible = provider.type == AiProviderType.OPENAI
            binding.textOpenaiPathLabel.isVisible = openAiCompatible
            binding.editChatPath.isVisible = openAiCompatible
            refreshModelList(provider)
            updateApiKeyVisibility()
            updateCustomEndpointVisibility()
            showProviderDetailTab(providerDetailTab)
        } finally {
            ignoreProviderFormChanges = false
        }
    }

    private fun showProviderDetailTab(tab: ProviderDetailTab) {
        providerDetailTab = tab
        binding.scrollDetail.isVisible = tab == ProviderDetailTab.CONFIG
        binding.layoutDetailHeader.isVisible = tab == ProviderDetailTab.CONFIG
        binding.layoutConfigTab.isVisible = tab == ProviderDetailTab.CONFIG
        binding.layoutModelTab.isVisible = tab == ProviderDetailTab.MODELS
        val activeColor = accentColor
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant)
        binding.buttonProviderTabConfig.imageTintList = ColorStateList.valueOf(
            if (tab == ProviderDetailTab.CONFIG) activeColor else inactiveColor
        )
        binding.buttonProviderTabModels.imageTintList = ColorStateList.valueOf(
            if (tab == ProviderDetailTab.MODELS) activeColor else inactiveColor
        )
        binding.buttonProviderTabConfig.setBackgroundResource(android.R.color.transparent)
        binding.buttonProviderTabModels.setBackgroundResource(android.R.color.transparent)
    }

    private fun refreshCurrentModelDetail() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val model = provider.displayModels().firstOrNull { it.safeId() == currentModelId } ?: return
        refreshModelDetail(provider, model)
    }

    private fun showModelEditDialog(provider: AiProviderSetting, model: AiModel) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = DialogAiModelEditBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        configureModelEditSheet(dialog)
        sheetBinding.editModelId.setText(model.safeId())
        sheetBinding.editModelDisplayName.setText(model.displayName())
        bindModelEditTabs(sheetBinding)
        bindModelEditSegments(sheetBinding, model)
        sheetBinding.buttonClose.setOnClickListener { dialog.dismiss() }
        sheetBinding.buttonCancel.setOnClickListener { dialog.dismiss() }
        sheetBinding.buttonConfirm.setOnClickListener {
            saveModelEdit(provider, model, sheetBinding)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun configureModelEditSheet(dialog: BottomSheetDialog) {
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.95f).toInt()
            }
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                isDraggable = false
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun bindModelEditTabs(sheetBinding: DialogAiModelEditBinding) {
        val activeColor = accentColor
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant)
        sheetBinding.tabBasic.setTextColor(activeColor)
        sheetBinding.tabBasic.typeface = Typeface.DEFAULT_BOLD
        sheetBinding.tabBasic.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(0, Color.TRANSPARENT)
        }
        sheetBinding.tabAdvanced.setTextColor(inactiveColor)
        sheetBinding.tabBuiltinTools.setTextColor(inactiveColor)
        sheetBinding.tabAdvanced.alpha = 0.58f
        sheetBinding.tabBuiltinTools.alpha = 0.58f
    }

    private fun bindModelEditSegments(
        sheetBinding: DialogAiModelEditBinding,
        model: AiModel
    ) {
        val type = model.safeType()
        selectModelEditType(sheetBinding, type)
        sheetBinding.segmentModelInputText.isSelected =
            AiModelModality.TEXT in model.safeInputModalities()
        sheetBinding.segmentModelInputImage.isSelected =
            AiModelModality.IMAGE in model.safeInputModalities()
        sheetBinding.segmentModelOutputText.isSelected =
            AiModelModality.TEXT in model.safeOutputModalities()
        sheetBinding.segmentModelOutputImage.isSelected =
            AiModelModality.IMAGE in model.safeOutputModalities()
        sheetBinding.segmentModelInputVideo.isSelected =
            AiModelModality.VIDEO in model.safeInputModalities()
        sheetBinding.segmentModelOutputVideo.isSelected =
            AiModelModality.VIDEO in model.safeOutputModalities()
        sheetBinding.segmentModelAbilityTool.isSelected =
            AiModelAbility.TOOL in model.safeAbilities()
        sheetBinding.segmentModelAbilityReasoning.isSelected =
            AiModelAbility.REASONING in model.safeAbilities()
        sheetBinding.segmentModelTypeChat.setOnClickListener {
            selectModelEditType(sheetBinding, AiModelType.CHAT)
        }
        sheetBinding.segmentModelTypeImage.setOnClickListener {
            selectModelEditType(sheetBinding, AiModelType.IMAGE)
        }
        sheetBinding.segmentModelTypeEmbedding.setOnClickListener {
            selectModelEditType(sheetBinding, AiModelType.EMBEDDING)
        }
        sheetBinding.segmentModelTypeAsr.setOnClickListener {
            selectModelEditType(sheetBinding, AiModelType.ASR)
        }
        sheetBinding.segmentModelTypeTts.setOnClickListener {
            selectModelEditType(sheetBinding, AiModelType.TTS)
        }
        sheetBinding.segmentModelTypeVideo.setOnClickListener {
            selectModelEditType(sheetBinding, AiModelType.VIDEO)
        }
        listOf(
            sheetBinding.segmentModelInputText,
            sheetBinding.segmentModelInputImage,
            sheetBinding.segmentModelInputVideo,
            sheetBinding.segmentModelOutputText,
            sheetBinding.segmentModelOutputImage,
            sheetBinding.segmentModelOutputVideo,
            sheetBinding.segmentModelAbilityTool,
            sheetBinding.segmentModelAbilityReasoning
        ).forEach { segment ->
            segment.setOnClickListener {
                segment.isSelected = !segment.isSelected
                refreshModelEditSegmentStyles(sheetBinding)
            }
        }
        refreshModelEditSegmentStyles(sheetBinding)
    }

    private fun selectModelEditType(
        sheetBinding: DialogAiModelEditBinding,
        type: AiModelType
    ) {
        sheetBinding.segmentModelTypeChat.isSelected = type == AiModelType.CHAT
        sheetBinding.segmentModelTypeImage.isSelected = type == AiModelType.IMAGE
        sheetBinding.segmentModelTypeEmbedding.isSelected = type == AiModelType.EMBEDDING
        sheetBinding.segmentModelTypeAsr.isSelected = type == AiModelType.ASR
        sheetBinding.segmentModelTypeTts.isSelected = type == AiModelType.TTS
        sheetBinding.segmentModelTypeVideo.isSelected = type == AiModelType.VIDEO
        val showModalities = type == AiModelType.CHAT || type == AiModelType.VIDEO
        val showChatAbilities = type == AiModelType.CHAT
        sheetBinding.textModelInputModalitiesLabel.isVisible = showModalities
        sheetBinding.layoutModelInputModalities.isVisible = showModalities
        sheetBinding.textModelOutputModalitiesLabel.isVisible = showModalities
        sheetBinding.layoutModelOutputModalities.isVisible = showModalities
        sheetBinding.textModelAbilitiesLabel.isVisible = showChatAbilities
        sheetBinding.layoutModelAbilities.isVisible = showChatAbilities
        refreshModelEditSegmentStyles(sheetBinding)
    }

    private fun refreshModelEditSegmentStyles(sheetBinding: DialogAiModelEditBinding) {
        applyModelEditSegmentGroupStyles(
            sheetBinding.layoutModelType,
            sheetBinding.segmentModelTypeChat,
            sheetBinding.segmentModelTypeImage,
            sheetBinding.segmentModelTypeEmbedding,
            sheetBinding.segmentModelTypeAsr,
            sheetBinding.segmentModelTypeTts,
            sheetBinding.segmentModelTypeVideo
        )
        applyModelEditSegmentGroupStyles(
            sheetBinding.layoutModelInputModalities,
            sheetBinding.segmentModelInputText,
            sheetBinding.segmentModelInputImage,
            sheetBinding.segmentModelInputVideo
        )
        applyModelEditSegmentGroupStyles(
            sheetBinding.layoutModelOutputModalities,
            sheetBinding.segmentModelOutputText,
            sheetBinding.segmentModelOutputImage,
            sheetBinding.segmentModelOutputVideo
        )
        applyModelEditSegmentGroupStyles(
            sheetBinding.layoutModelAbilities,
            sheetBinding.segmentModelAbilityTool,
            sheetBinding.segmentModelAbilityReasoning
        )
    }

    private fun applyModelEditSegmentGroupStyles(
        group: LinearLayout,
        vararg segments: TextView
    ) {
        val activeColor = accentColor
        val outlineColor = ContextCompat.getColor(requireContext(), R.color.ng_outline_strong)
        val textColor = ContextCompat.getColor(requireContext(), R.color.ng_on_surface)
        val selectedBackground = ColorUtils.setAlphaComponent(activeColor, 22)
        group.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        group.dividerDrawable = GradientDrawable().apply {
            setColor(outlineColor)
            setSize(1.dpToPx(), 1.dpToPx())
        }
        group.setPadding(1.dpToPx(), 1.dpToPx(), 1.dpToPx(), 1.dpToPx())
        group.background = GradientDrawable().apply {
            cornerRadius = 22.dpToPx().toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(1.dpToPx(), outlineColor)
        }
        segments.forEachIndexed { index, segment ->
            val rawText = segment.text.toString().removePrefix("✓ ").trim()
            val selected = segment.isSelected
            segment.text = if (selected) "✓ $rawText" else rawText
            segment.setTextColor(textColor)
            segment.typeface = Typeface.defaultFromStyle(Typeface.NORMAL)
            segment.background = GradientDrawable().apply {
                setColor(if (selected) selectedBackground else Color.TRANSPARENT)
                cornerRadii = segmentCornerRadii(index, segments.lastIndex, 21.dpToPx().toFloat())
            }
        }
    }

    private fun segmentCornerRadii(index: Int, lastIndex: Int, radius: Float): FloatArray {
        return when {
            index == 0 && index == lastIndex -> floatArrayOf(
                radius, radius, radius, radius, radius, radius, radius, radius
            )
            index == 0 -> floatArrayOf(
                radius, radius, 0f, 0f, 0f, 0f, radius, radius
            )
            index == lastIndex -> floatArrayOf(
                0f, 0f, radius, radius, radius, radius, 0f, 0f
            )
            else -> FloatArray(8)
        }
    }

    private fun saveModelEdit(
        provider: AiProviderSetting,
        original: AiModel,
        sheetBinding: DialogAiModelEditBinding
    ) {
        val type = currentModelEditType(sheetBinding)
        val updated = original.copy(
            displayName = sheetBinding.editModelDisplayName.text?.toString()?.trim().orEmpty(),
            type = type,
            inputModalities = modelInputModalitiesFor(sheetBinding, type),
            outputModalities = modelOutputModalitiesFor(sheetBinding, type),
            abilities = mergeModelAbilitiesForEdit(
                original = original,
                exposedAbilities = modelAbilitiesFor(sheetBinding, type)
            )
        )
        AiProviderStore.saveProvider(provider.copy(models = provider.models.updatedWithModel(updated)))
        refreshModelList(AiProviderStore.provider(provider.id))
        refreshMain()
    }

    private fun refreshModelDetail(provider: AiProviderSetting, model: AiModel) {
        setPageTitle(model.displayName())
        ignoreModelDetailChanges = true
        try {
            binding.editModelId.setText(model.safeId())
            binding.editModelDisplayName.setText(model.displayName())
            selectModelType(model.safeType(), save = false)
            binding.segmentModelInputText.isSelected =
                AiModelModality.TEXT in model.safeInputModalities()
            binding.segmentModelInputImage.isSelected =
                AiModelModality.IMAGE in model.safeInputModalities()
            binding.segmentModelOutputText.isSelected =
                AiModelModality.TEXT in model.safeOutputModalities()
            binding.segmentModelOutputImage.isSelected =
                AiModelModality.IMAGE in model.safeOutputModalities()
            binding.segmentModelInputVideo.isSelected =
                AiModelModality.VIDEO in model.safeInputModalities()
            binding.segmentModelOutputVideo.isSelected =
                AiModelModality.VIDEO in model.safeOutputModalities()
            binding.segmentModelAbilityTool.isSelected = AiModelAbility.TOOL in model.safeAbilities()
            binding.segmentModelAbilityReasoning.isSelected =
                AiModelAbility.REASONING in model.safeAbilities()
            refreshModelSegmentStyles()
        } finally {
            ignoreModelDetailChanges = false
        }
        modelAdapter.availableModelIds = provider.effectiveAvailableModelIds().toSet()
    }

    private fun selectModelType(type: AiModelType, save: Boolean) {
        binding.segmentModelTypeChat.isSelected = type == AiModelType.CHAT
        binding.segmentModelTypeImage.isSelected = type == AiModelType.IMAGE
        binding.segmentModelTypeEmbedding.isSelected = type == AiModelType.EMBEDDING
        binding.segmentModelTypeAsr.isSelected = type == AiModelType.ASR
        binding.segmentModelTypeTts.isSelected = type == AiModelType.TTS
        binding.segmentModelTypeVideo.isSelected = type == AiModelType.VIDEO
        val showModalities = type == AiModelType.CHAT || type == AiModelType.VIDEO
        val showChatAbilities = type == AiModelType.CHAT
        binding.textModelInputModalitiesLabel.isVisible = showModalities
        binding.layoutModelInputModalities.isVisible = showModalities
        binding.textModelOutputModalitiesLabel.isVisible = showModalities
        binding.layoutModelOutputModalities.isVisible = showModalities
        binding.textModelAbilitiesLabel.isVisible = showChatAbilities
        binding.layoutModelAbilities.isVisible = showChatAbilities
        refreshModelSegmentStyles()
        if (save && !ignoreModelDetailChanges && currentPage == Page.MODEL_DETAIL) {
            saveCurrentModel()
        }
    }

    private fun refreshModelSegmentStyles() {
        applySegmentStyles(
            binding.segmentModelTypeChat,
            binding.segmentModelTypeImage,
            binding.segmentModelTypeEmbedding,
            binding.segmentModelTypeAsr,
            binding.segmentModelTypeTts,
            binding.segmentModelTypeVideo
        )
        applySegmentStyles(
            binding.segmentModelInputText,
            binding.segmentModelInputImage,
            binding.segmentModelInputVideo
        )
        applySegmentStyles(
            binding.segmentModelOutputText,
            binding.segmentModelOutputImage,
            binding.segmentModelOutputVideo
        )
        applySegmentStyles(binding.segmentModelAbilityTool, binding.segmentModelAbilityReasoning)
    }

    private fun applySegmentStyles(vararg segments: TextView) {
        val activeColor = accentColor
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant)
        val selectedBackground = ContextCompat.getColor(requireContext(), R.color.ng_success_container)
        val unselectedBackground = ContextCompat.getColor(requireContext(), R.color.ng_neutral_container)
        segments.forEach { segment ->
            val selected = segment.isSelected
            segment.setTextColor(if (selected) activeColor else inactiveColor)
            segment.typeface = Typeface.defaultFromStyle(if (selected) Typeface.BOLD else Typeface.NORMAL)
            segment.background = GradientDrawable().apply {
                cornerRadius = 18.dpToPx().toFloat()
                setColor(if (selected) selectedBackground else unselectedBackground)
                setStroke(1.dpToPx(), if (selected) activeColor else inactiveColor)
            }
        }
    }

    private fun currentModelTypeFromSegments(): AiModelType {
        return when {
            binding.segmentModelTypeImage.isSelected -> AiModelType.IMAGE
            binding.segmentModelTypeEmbedding.isSelected -> AiModelType.EMBEDDING
            binding.segmentModelTypeAsr.isSelected -> AiModelType.ASR
            binding.segmentModelTypeTts.isSelected -> AiModelType.TTS
            binding.segmentModelTypeVideo.isSelected -> AiModelType.VIDEO
            else -> AiModelType.CHAT
        }
    }

    private fun currentModelEditType(sheetBinding: DialogAiModelEditBinding): AiModelType {
        return when {
            sheetBinding.segmentModelTypeImage.isSelected -> AiModelType.IMAGE
            sheetBinding.segmentModelTypeEmbedding.isSelected -> AiModelType.EMBEDDING
            sheetBinding.segmentModelTypeAsr.isSelected -> AiModelType.ASR
            sheetBinding.segmentModelTypeTts.isSelected -> AiModelType.TTS
            sheetBinding.segmentModelTypeVideo.isSelected -> AiModelType.VIDEO
            else -> AiModelType.CHAT
        }
    }

    private fun updateCustomEndpointVisibility() {
        val customModelsUrl = binding.switchCustomModelsUrl.isChecked
        binding.textModelsUrlLabel.isVisible = customModelsUrl
        binding.editModelsUrl.isVisible = customModelsUrl
        val customBalanceUrl = binding.switchCustomBalanceUrl.isChecked
        binding.textBalanceUrlLabel.isVisible = customBalanceUrl
        binding.editBalanceUrl.isVisible = customBalanceUrl
        binding.textBalanceJsonPathLabel.isVisible = customBalanceUrl
        binding.editBalanceJsonPath.isVisible = customBalanceUrl
    }

    private fun showAddProviderDialog() {
        val types = listOf(AiProviderType.OPENAI, AiProviderType.CLAUDE)
        val labels = arrayOf(
            getString(R.string.ai_add_provider_openai),
            getString(R.string.ai_add_provider_anthropic)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_add_provider)
            .setItems(labels) { _, which ->
                val provider = AiProviderStore.createCustomProvider(types[which])
                if (providerSearchQuery.isNotBlank()) {
                    providerSearchQuery = ""
                    binding.editSearchProvider.setText("")
                }
                refreshMain()
                showDetail(provider.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .applyTint()
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

    private fun refreshModelSettings() {
        val color = accentColor
        val entryIconTint = ColorStateList.valueOf(color)
        binding.textModelSettingsSectionLabel.setTextColor(color)
        binding.textPurifyModelSettingsSectionLabel.setTextColor(color)
        binding.imagePurifyModelSettingsIcon.imageTintList = entryIconTint
        binding.imagePurifyModelIcon.imageTintList = entryIconTint
        binding.imagePurifyReasoningIcon.imageTintList = entryIconTint
        binding.textPurifyModelSettingsSummary.text = getString(
            R.string.ai_model_function_summary,
            purifyModelSummaryText(),
            purifyReasoningSummaryText()
        )
        binding.textPurifyModelSummary.text = purifyModelSummaryText()
        binding.textPurifyReasoningSummary.text = purifyReasoningSummaryText()
        val reasoningEnabled = selectedPurifyModel()?.model?.supportsReasoning() == true
        binding.layoutPurifyReasoningEntry.alpha = if (reasoningEnabled) 1f else 0.55f
    }

    private fun purifyModelSummaryText(): String {
        val selected = selectedPurifyModel()
        return when {
            selected == null && AiConfig.purifyModelId.isBlank() ->
                getString(R.string.ai_purify_model_not_selected)
            selected == null ->
                getString(R.string.ai_purify_model_unavailable)
            else ->
                getString(
                    R.string.ai_purify_model_selected_summary,
                    selected.model.displayName(),
                    selected.provider.name
                )
        }
    }

    private fun purifyReasoningSummaryText(): String {
        val selected = selectedPurifyModel()
        return when {
            selected == null -> getString(R.string.ai_purify_reasoning_select_model_first)
            !selected.model.supportsReasoning() -> getString(R.string.ai_purify_reasoning_unsupported)
            else -> getString(
                R.string.ai_purify_reasoning_level_summary,
                AiConfig.purifyReasoningLevel.displayName()
            )
        }
    }

    private fun selectedPurifyModel(): PurifyModelOption? {
        val providerId = AiConfig.purifyProviderId
        val modelId = AiConfig.purifyModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return null
        }
        val provider = AiProviderStore.provider(providerId)?.takeIf { it.enabled } ?: return null
        val model = provider.purifyEligibleModels().firstOrNull { it.safeId() == modelId } ?: return null
        return PurifyModelOption(provider, model)
    }

    private fun showPurifyModelSelectDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 18.dpToPx())
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    28.dpToPx().toFloat(), 28.dpToPx().toFloat(),
                    28.dpToPx().toFloat(), 28.dpToPx().toFloat(),
                    0f, 0f,
                    0f, 0f
                )
                setColor(ContextCompat.getColor(requireContext(), R.color.ng_surface_soft))
            }
        }
        val searchEdit = EditText(requireContext()).apply {
            background = GradientDrawable().apply {
                cornerRadius = 28.dpToPx().toFloat()
                setColor(ContextCompat.getColor(requireContext(), R.color.ng_neutral_container))
            }
            hint = getString(R.string.ai_purify_model_search_hint)
            setSingleLine(true)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
            textSize = 16f
            compoundDrawablePadding = 10.dpToPx()
            setPadding(18.dpToPx(), 0, 18.dpToPx(), 0)
        }
        root.addView(searchEdit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            52.dpToPx()
        ).apply {
            bottomMargin = 16.dpToPx()
        })
        val listScroll = androidx.core.widget.NestedScrollView(requireContext()).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, 10.dpToPx())
        }
        val listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        listScroll.addView(listContainer)
        root.addView(listScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        ).apply {
            weight = 1f
        })
        fun render(query: String) {
            renderPurifyModelOptions(listContainer, query, dialog)
        }
        searchEdit.doOnTextChanged { text, _, _, _ ->
            render(text?.toString().orEmpty())
        }
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        render("")
        dialog.show()
    }

    private fun renderPurifyModelOptions(
        container: LinearLayout,
        query: String,
        dialog: BottomSheetDialog
    ) {
        container.removeAllViews()
        val normalizedQuery = query.trim()
        val groupedOptions = purifyModelOptions(normalizedQuery)
        if (groupedOptions.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.ai_purify_model_empty)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 44.dpToPx(), 0, 44.dpToPx())
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return
        }
        groupedOptions.forEach { (provider, models) ->
            container.addView(createPurifyProviderHeader(provider))
            models.forEach { model ->
                container.addView(createPurifyModelCard(provider, model, dialog))
            }
        }
    }

    private fun purifyModelOptions(query: String): List<Pair<AiProviderSetting, List<AiModel>>> {
        return AiProviderStore.providers()
            .filter { it.enabled }
            .mapNotNull { provider ->
                val models = provider.purifyEligibleModels()
                    .filter { model ->
                        query.isBlank()
                            || provider.name.contains(query, ignoreCase = true)
                            || model.safeId().contains(query, ignoreCase = true)
                            || model.safeName().contains(query, ignoreCase = true)
                            || model.displayName().contains(query, ignoreCase = true)
                    }
                models.takeIf { it.isNotEmpty() }?.let { provider to it }
            }
    }

    private fun createPurifyProviderHeader(provider: AiProviderSetting): TextView {
        return TextView(requireContext()).apply {
            text = provider.name
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
            setPadding(2.dpToPx(), 12.dpToPx(), 2.dpToPx(), 8.dpToPx())
        }
    }

    private fun createPurifyModelCard(
        provider: AiProviderSetting,
        model: AiModel,
        dialog: BottomSheetDialog
    ): LinearLayout {
        val selected = AiConfig.purifyProviderId == provider.id && AiConfig.purifyModelId == model.safeId()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = 18.dpToPx().toFloat()
                setColor(ContextCompat.getColor(requireContext(), R.color.ng_surface_card))
            }
            setPadding(14.dpToPx(), 12.dpToPx(), 14.dpToPx(), 12.dpToPx())
            setOnClickListener {
                AiConfig.savePurifyModel(provider.id, model.safeId())
                refreshModelSettings()
                refreshMain()
                dialog.dismiss()
            }
            addView(ImageView(requireContext()).apply {
                setImageResource(model.iconRes(provider.iconRes()))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.ng_bg_icon_circle)
                setPadding(7.dpToPx(), 7.dpToPx(), 7.dpToPx(), 7.dpToPx())
            }, LinearLayout.LayoutParams(46.dpToPx(), 46.dpToPx()).apply {
                marginEnd = 12.dpToPx()
            })
            val info = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(requireContext()).apply {
                    text = model.displayName()
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 16f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6.dpToPx(), 0, 0)
                    model.capabilityTags().forEach {
                        addView(createModelCapabilityTag(it))
                    }
                })
            }
            addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
            addView(ImageView(requireContext()).apply {
                if (selected) {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(accentColor)
                } else {
                    imageTintList = null
                }
            }, LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()).apply {
                marginStart = 10.dpToPx()
            })
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }
    }

    private fun showPurifyReasoningDialog() {
        val selected = selectedPurifyModel()
        if (selected == null) {
            Toast.makeText(
                requireContext(),
                R.string.ai_purify_reasoning_select_model_first,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!selected.model.supportsReasoning()) {
            Toast.makeText(
                requireContext(),
                R.string.ai_purify_reasoning_unsupported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val levels = AiReasoningLevel.entries.toList()
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dpToPx(), 26.dpToPx(), 24.dpToPx(), 24.dpToPx())
            background = GradientDrawable().apply {
                cornerRadius = 24.dpToPx().toFloat()
                setColor(ContextCompat.getColor(requireContext(), R.color.ng_surface_soft))
            }
        }
        root.addView(TextView(requireContext()).apply {
            text = getString(R.string.ai_purify_reasoning_title)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 20f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(requireContext()).apply {
            text = getString(R.string.ai_purify_reasoning_desc)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8.dpToPx(), 0, 22.dpToPx())
        })
        val currentLabel = TextView(requireContext()).apply {
            text = AiConfig.purifyReasoningLevel.displayName()
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
            textSize = 18f
            gravity = Gravity.CENTER
        }
        root.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_ai_capability_reasoning)
            imageTintList = ColorStateList.valueOf(accentColor)
        }, LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()))
        root.addView(currentLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8.dpToPx()
            bottomMargin = 22.dpToPx()
        })
        val seekBar = SeekBar(requireContext()).apply {
            max = levels.lastIndex
            progress = levels.indexOf(AiConfig.purifyReasoningLevel).coerceAtLeast(0)
            progressTintList = ColorStateList.valueOf(accentColor)
            thumbTintList = ColorStateList.valueOf(accentColor)
            progressBackgroundTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(accentColor, 45)
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val level = levels.getOrNull(progress) ?: AiReasoningLevel.AUTO
                    AiConfig.purifyReasoningLevel = level
                    currentLabel.text = level.displayName()
                    refreshModelSettings()
                    refreshMain()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        root.addView(seekBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            levels.forEach { level ->
                addView(TextView(requireContext()).apply {
                    text = level.displayName()
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
                    textSize = 13f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                })
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12.dpToPx()
        })
        val dialog = AlertDialog.Builder(requireContext())
            .setView(root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun AiProviderSetting.purifyEligibleModels(): List<AiModel> {
        val availableIds = effectiveAvailableModelIds().toSet()
        if (availableIds.isEmpty()) {
            return emptyList()
        }
        return displayModels()
            .filter { it.safeId() in availableIds }
            .filter { it.supportsPurifyText() }
    }

    private fun AiModel.supportsPurifyText(): Boolean {
        return safeType() == AiModelType.CHAT &&
            AiModelModality.TEXT in safeInputModalities() &&
            AiModelModality.TEXT in safeOutputModalities()
    }

    private fun AiModel.supportsReasoning(): Boolean {
        return AiModelAbility.REASONING in safeAbilities()
    }

    private fun AiReasoningLevel.displayName(): String {
        return when (this) {
            AiReasoningLevel.OFF -> getString(R.string.ai_reasoning_level_off)
            AiReasoningLevel.AUTO -> getString(R.string.ai_reasoning_level_auto)
            AiReasoningLevel.LOW -> getString(R.string.ai_reasoning_level_low)
            AiReasoningLevel.MEDIUM -> getString(R.string.ai_reasoning_level_medium)
            AiReasoningLevel.HIGH -> getString(R.string.ai_reasoning_level_high)
            AiReasoningLevel.ULTRA -> getString(R.string.ai_reasoning_level_ultra)
        }
    }

    private data class PurifyModelOption(
        val provider: AiProviderSetting,
        val model: AiModel
    )

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun readProviderFromForm(): AiProviderSetting? {
        val source = currentProviderId?.let { AiProviderStore.provider(it) } ?: return null
        val baseUrl = binding.editBaseUrl.text?.toString()?.trim().orEmpty().ifBlank {
            source.baseUrl
        }
        return source.copy(
            enabled = binding.switchEnabled.isChecked,
            name = binding.editProviderName.text?.toString()?.trim().orEmpty().ifBlank {
                source.name
            },
            apiKey = binding.editApiKey.text?.toString()?.trim().orEmpty(),
            baseUrl = baseUrl,
            model = source.model,
            timeoutSeconds = binding.editTimeoutSeconds.text?.toString()
                ?.toIntOrNull()
                ?.coerceIn(5, 600)
                ?: source.timeoutSeconds,
            chatCompletionsPath = binding.editChatPath.text?.toString()?.trim().orEmpty()
                .ifBlank { source.chatCompletionsPath },
            modelsUrl = normalizeAiApiPath(
                baseUrl,
                binding.editModelsUrl.text?.toString()?.trim().orEmpty()
            ),
            useCustomModelsUrl = binding.switchCustomModelsUrl.isChecked,
            balanceUrl = normalizeAiApiPath(
                baseUrl,
                binding.editBalanceUrl.text?.toString()?.trim().orEmpty()
            ),
            balanceJsonPath = binding.editBalanceJsonPath.text?.toString()?.trim().orEmpty(),
            useCustomBalanceUrl = binding.switchCustomBalanceUrl.isChecked
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
                val modelCount = AiManager.testConnectivity(provider.id)
                alert(getString(R.string.ai_test_connection)) {
                    setMessage(getString(R.string.ai_connectivity_success, modelCount.toString()))
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

    private fun confirmDeleteCurrentProvider() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        confirmDeleteProvider(provider) {
            showProviderList()
        }
    }

    private fun confirmDeleteProvider(
        provider: AiProviderSetting,
        onCancel: () -> Unit = {},
        onDeleted: () -> Unit = {}
    ) {
        if (provider.builtIn) {
            onCancel()
            return
        }
        alert(getString(R.string.ai_delete_provider)) {
            setMessage(getString(R.string.sure_del_any, provider.name))
            okButton {
                if (AiProviderStore.deleteCustomProvider(provider.id)) {
                    Toast.makeText(
                        requireContext(),
                        R.string.ai_provider_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                    onDeleted()
                    refreshMain()
                }
            }
            cancelButton {
                onCancel()
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

    private fun refreshModelList(provider: AiProviderSetting?) {
        val selectedIds = provider?.effectiveAvailableModelIds().orEmpty().toSet()
        val models = provider?.displayModels().orEmpty()
            .filter { model ->
                modelSearchQuery.isBlank()
                    || model.safeId().contains(modelSearchQuery, ignoreCase = true)
                    || model.safeName().contains(modelSearchQuery, ignoreCase = true)
                    || model.displayName().contains(modelSearchQuery, ignoreCase = true)
                    || model.safeOwnedBy().contains(modelSearchQuery, ignoreCase = true)
            }
            .sortSelectedModelsFirst(selectedIds)
        modelAdapter.providerIconRes = provider?.iconRes() ?: R.drawable.ic_ai_provider
        modelAdapter.availableModelIds = selectedIds
        modelAdapter.setItems(models)
        updateModelSelectionAction(provider, models)
    }

    private fun List<AiModel>.sortSelectedModelsFirst(selectedIds: Set<String>): List<AiModel> {
        if (isEmpty() || selectedIds.isEmpty() || all { it.safeId() in selectedIds }) {
            return this
        }
        return sortedByDescending { it.safeId() in selectedIds }
    }

    private fun updateModelSelectionAction(
        provider: AiProviderSetting?,
        visibleModels: List<AiModel>
    ) {
        val visibleIds = visibleModels.map { it.safeId() }.filter { it.isNotBlank() }.toSet()
        val selectedIds = provider?.effectiveAvailableModelIds().orEmpty().toSet()
        val selectedVisibleCount = visibleIds.count { it in selectedIds }
        binding.buttonToggleModelSelection.isEnabled = visibleIds.isNotEmpty()
        binding.buttonToggleModelSelection.alpha = if (visibleIds.isEmpty()) 0.45f else 1f
        binding.buttonToggleModelSelection.text = when {
            selectedVisibleCount == visibleIds.size -> getString(R.string.ai_disable_all_models)
            else -> getString(R.string.ai_enable_all_models)
        }
    }

    private fun toggleVisibleModelSelection() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val visibleIds = modelAdapter.getItems().map { it.safeId() }
            .filter { it.isNotBlank() }
            .toSet()
        if (visibleIds.isEmpty()) {
            return
        }
        val selected = provider.effectiveAvailableModelIds().toMutableSet()
        val selectedVisibleCount = visibleIds.count { it in selected }
        if (selectedVisibleCount == visibleIds.size) {
            selected.removeAll(visibleIds)
        } else {
            selected.addAll(visibleIds)
        }
        AiProviderStore.saveProvider(
            provider.copy(
                availableModelIds = selected.toList(),
                availableModelSelectionInitialized = true
            )
        )
        refreshModelList(AiProviderStore.provider(provider.id))
        refreshMain()
    }

    private fun toggleAvailableModel(model: AiModel, checked: Boolean) {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val modelId = model.safeId()
        if (modelId.isBlank()) {
            return
        }
        val selected = provider.effectiveAvailableModelIds().toMutableSet()
        if (checked) {
            selected.add(modelId)
        } else {
            selected.remove(modelId)
        }
        AiProviderStore.saveProvider(
            provider.copy(
                availableModelIds = selected.toList(),
                availableModelSelectionInitialized = true
            )
        )
        refreshModelList(AiProviderStore.provider(provider.id))
        refreshMain()
    }

    private fun saveCurrentModel() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val modelId = currentModelId ?: return
        val current = provider.displayModels().firstOrNull { it.safeId() == modelId } ?: return
        val type = currentModelTypeFromSegments()
        val updated = current.copy(
            displayName = binding.editModelDisplayName.text?.toString()?.trim().orEmpty(),
            type = type,
            inputModalities = modelInputModalitiesFor(type),
            outputModalities = modelOutputModalitiesFor(type),
            abilities = mergeModelAbilitiesForEdit(
                original = current,
                exposedAbilities = modelAbilitiesFor(type)
            )
        )
        AiProviderStore.saveProvider(
            provider.copy(models = provider.models.updatedWithModel(updated))
        )
        setPageTitle(updated.displayName())
        refreshModelList(AiProviderStore.provider(provider.id))
    }

    private fun modelInputModalitiesFor(type: AiModelType): List<AiModelModality> {
        return when (type) {
            AiModelType.CHAT -> normalizeModalities(
                textChecked = binding.segmentModelInputText.isSelected,
                imageChecked = binding.segmentModelInputImage.isSelected,
                videoChecked = binding.segmentModelInputVideo.isSelected
            )
            AiModelType.VIDEO -> normalizeModalities(
                textChecked = binding.segmentModelInputText.isSelected,
                imageChecked = binding.segmentModelInputImage.isSelected,
                videoChecked = binding.segmentModelInputVideo.isSelected
            )

            AiModelType.IMAGE,
            AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT)
            AiModelType.ASR -> listOf(AiModelModality.AUDIO)
            AiModelType.TTS -> listOf(AiModelModality.TEXT)
        }
    }

    private fun modelOutputModalitiesFor(type: AiModelType): List<AiModelModality> {
        return when (type) {
            AiModelType.CHAT -> normalizeModalities(
                textChecked = binding.segmentModelOutputText.isSelected,
                imageChecked = binding.segmentModelOutputImage.isSelected,
                videoChecked = binding.segmentModelOutputVideo.isSelected
            )
            AiModelType.VIDEO -> normalizeModalities(
                textChecked = binding.segmentModelOutputText.isSelected,
                imageChecked = binding.segmentModelOutputImage.isSelected,
                videoChecked = binding.segmentModelOutputVideo.isSelected,
                defaultModality = AiModelModality.VIDEO
            )

            AiModelType.IMAGE -> listOf(AiModelModality.IMAGE)
            AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT)
            AiModelType.ASR -> listOf(AiModelModality.TEXT)
            AiModelType.TTS -> listOf(AiModelModality.AUDIO)
        }
    }

    private fun modelAbilitiesFor(type: AiModelType): List<AiModelAbility> {
        return when (type) {
            AiModelType.CHAT -> buildList {
                if (binding.segmentModelAbilityTool.isSelected) add(AiModelAbility.TOOL)
                if (binding.segmentModelAbilityReasoning.isSelected) add(AiModelAbility.REASONING)
            }

            AiModelType.IMAGE,
            AiModelType.EMBEDDING,
            AiModelType.VIDEO -> emptyList()
            AiModelType.ASR -> listOf(AiModelAbility.ASR)
            AiModelType.TTS -> listOf(AiModelAbility.TTS)
        }
    }

    private fun modelInputModalitiesFor(
        sheetBinding: DialogAiModelEditBinding,
        type: AiModelType
    ): List<AiModelModality> {
        return when (type) {
            AiModelType.CHAT -> normalizeModalities(
                textChecked = sheetBinding.segmentModelInputText.isSelected,
                imageChecked = sheetBinding.segmentModelInputImage.isSelected,
                videoChecked = sheetBinding.segmentModelInputVideo.isSelected
            )
            AiModelType.VIDEO -> normalizeModalities(
                textChecked = sheetBinding.segmentModelInputText.isSelected,
                imageChecked = sheetBinding.segmentModelInputImage.isSelected,
                videoChecked = sheetBinding.segmentModelInputVideo.isSelected
            )

            AiModelType.IMAGE,
            AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT)
            AiModelType.ASR -> listOf(AiModelModality.AUDIO)
            AiModelType.TTS -> listOf(AiModelModality.TEXT)
        }
    }

    private fun modelOutputModalitiesFor(
        sheetBinding: DialogAiModelEditBinding,
        type: AiModelType
    ): List<AiModelModality> {
        return when (type) {
            AiModelType.CHAT -> normalizeModalities(
                textChecked = sheetBinding.segmentModelOutputText.isSelected,
                imageChecked = sheetBinding.segmentModelOutputImage.isSelected,
                videoChecked = sheetBinding.segmentModelOutputVideo.isSelected
            )
            AiModelType.VIDEO -> normalizeModalities(
                textChecked = sheetBinding.segmentModelOutputText.isSelected,
                imageChecked = sheetBinding.segmentModelOutputImage.isSelected,
                videoChecked = sheetBinding.segmentModelOutputVideo.isSelected,
                defaultModality = AiModelModality.VIDEO
            )

            AiModelType.IMAGE -> listOf(AiModelModality.IMAGE)
            AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT)
            AiModelType.ASR -> listOf(AiModelModality.TEXT)
            AiModelType.TTS -> listOf(AiModelModality.AUDIO)
        }
    }

    private fun modelAbilitiesFor(
        sheetBinding: DialogAiModelEditBinding,
        type: AiModelType
    ): List<AiModelAbility> {
        return when (type) {
            AiModelType.CHAT -> buildList {
                if (sheetBinding.segmentModelAbilityTool.isSelected) add(AiModelAbility.TOOL)
                if (sheetBinding.segmentModelAbilityReasoning.isSelected) {
                    add(AiModelAbility.REASONING)
                }
            }

            AiModelType.IMAGE,
            AiModelType.EMBEDDING,
            AiModelType.VIDEO -> emptyList()
            AiModelType.ASR -> listOf(AiModelAbility.ASR)
            AiModelType.TTS -> listOf(AiModelAbility.TTS)
        }
    }

    private fun mergeModelAbilitiesForEdit(
        original: AiModel,
        exposedAbilities: List<AiModelAbility>
    ): List<AiModelAbility> {
        val editedAbilities = setOf(
            AiModelAbility.ASR,
            AiModelAbility.TTS,
            AiModelAbility.TOOL,
            AiModelAbility.REASONING
        )
        return (original.safeAbilities().filter { it !in editedAbilities } + exposedAbilities)
            .distinct()
    }

    private fun normalizeModalities(
        textChecked: Boolean,
        imageChecked: Boolean,
        videoChecked: Boolean = false,
        defaultModality: AiModelModality = AiModelModality.TEXT
    ): List<AiModelModality> {
        return buildList {
            if (textChecked) add(AiModelModality.TEXT)
            if (imageChecked) add(AiModelModality.IMAGE)
            if (videoChecked) add(AiModelModality.VIDEO)
        }.ifEmpty { listOf(defaultModality) }
    }

    private fun List<AiModel>.updatedWithModel(model: AiModel): List<AiModel> {
        var found = false
        val updated = map {
            if (it.safeId() == model.safeId()) {
                found = true
                model
            } else {
                it
            }
        }
        return if (found) updated else listOf(model) + updated
    }

    private fun AiProviderSetting.displayModels(): List<AiModel> {
        if (apiKey.isBlank()) {
            return emptyList()
        }
        return models
            .filter { it.safeId().isNotBlank() }
            .distinctBy { it.safeId() }
    }

    private fun AiProviderSetting.effectiveAvailableModelIds(): List<String> {
        val cachedIds = models.map { it.safeId() }.filter { it.isNotBlank() }
        return if (availableModelSelectionInitialized) {
            availableModelIds.filter { it in cachedIds }
        } else {
            cachedIds
        }
    }

    private fun AiModel.displayName(): String {
        return safeDisplayName().ifBlank {
            val rawName = safeName().ifBlank { safeId() }.trim()
            rawName.substringAfterLast('/').ifBlank {
                safeId().substringAfterLast('/').ifBlank { safeId() }
            }
        }
    }

    private fun AiModel.safeId(): String {
        return runCatching { id }.getOrNull().orEmpty()
    }

    private fun AiModel.safeName(): String {
        return runCatching { name }.getOrNull().orEmpty()
    }

    private fun AiModel.safeDisplayName(): String {
        return runCatching { displayName }.getOrNull().orEmpty()
    }

    private fun AiModel.safeOwnedBy(): String {
        return runCatching { ownedBy }.getOrNull().orEmpty()
    }

    private fun AiModel.safeType(): AiModelType {
        return runCatching { type }.getOrNull() ?: AiModelType.CHAT
    }

    private fun AiModel.safeInputModalities(): List<AiModelModality> {
        return runCatching { inputModalities }.getOrNull().orEmpty()
    }

    private fun AiModel.safeOutputModalities(): List<AiModelModality> {
        return runCatching { outputModalities }.getOrNull().orEmpty()
    }

    private fun AiModel.safeAbilities(): List<AiModelAbility> {
        return runCatching { abilities }.getOrNull().orEmpty()
    }

    private fun AiProviderSetting.iconRes(): Int {
        return when (id) {
            "openai" -> R.drawable.ic_provider_openai
            "claude" -> R.drawable.ic_provider_claude
            "gemini" -> R.drawable.ic_provider_gemini
            "deepseek" -> R.drawable.ic_provider_deepseek
            "xiaomi_mimo" -> R.drawable.ic_provider_mimo
            "siliconflow" -> R.drawable.ic_provider_siliconflow
            "openrouter" -> R.drawable.ic_provider_openrouter
            "aliyun_bailian" -> R.drawable.ic_provider_aliyun
            "volcengine" -> R.drawable.ic_provider_volcengine
            "moonshot" -> R.drawable.ic_provider_moonshot
            "zhipu" -> R.drawable.ic_provider_zhipu
            else -> when (type) {
                AiProviderType.OPENAI -> R.drawable.ic_provider_openai
                AiProviderType.CLAUDE -> R.drawable.ic_provider_claude
                AiProviderType.GOOGLE -> R.drawable.ic_provider_gemini
            }
        }
    }

    private fun AiModel.iconRes(defaultIconRes: Int): Int {
        val modelName = listOf(safeId(), safeName(), safeOwnedBy())
            .joinToString(" ")
            .lowercase()
        return when {
            Regex("(gpt|openai|o\\d)").containsMatchIn(modelName) -> R.drawable.ic_provider_openai
            Regex("(gemini|nano-banana)").containsMatchIn(modelName) -> R.drawable.ic_provider_gemini
            Regex("google").containsMatchIn(modelName) -> R.drawable.ic_model_google
            Regex("claude").containsMatchIn(modelName) -> R.drawable.ic_provider_claude
            Regex("anthropic").containsMatchIn(modelName) -> R.drawable.ic_model_anthropic
            Regex("deepseek").containsMatchIn(modelName) -> R.drawable.ic_provider_deepseek
            Regex("grok").containsMatchIn(modelName) -> R.drawable.ic_model_grok
            Regex("qwen|qwq|qvq").containsMatchIn(modelName) -> R.drawable.ic_model_qwen
            Regex("doubao").containsMatchIn(modelName) -> R.drawable.ic_model_doubao
            Regex("openrouter").containsMatchIn(modelName) -> R.drawable.ic_provider_openrouter
            Regex("zhipu|智谱|glm").containsMatchIn(modelName) -> R.drawable.ic_provider_zhipu
            Regex("mistral").containsMatchIn(modelName) -> R.drawable.ic_model_mistral
            Regex("meta\\b|(?<!o)llama").containsMatchIn(modelName) -> R.drawable.ic_model_meta
            Regex("hunyuan|tencent").containsMatchIn(modelName) -> R.drawable.ic_model_hunyuan
            Regex("gemma").containsMatchIn(modelName) -> R.drawable.ic_model_gemma
            Regex("perplexity").containsMatchIn(modelName) -> R.drawable.ic_model_perplexity
            Regex("aliyun|阿里云|百炼").containsMatchIn(modelName) -> R.drawable.ic_provider_aliyun
            Regex("bytedance|火山|seed").containsMatchIn(modelName) -> R.drawable.ic_provider_volcengine
            Regex("silicon|硅基").containsMatchIn(modelName) -> R.drawable.ic_provider_siliconflow
            Regex("aihubmix").containsMatchIn(modelName) -> R.drawable.ic_model_aihubmix
            Regex("ollama").containsMatchIn(modelName) -> R.drawable.ic_model_ollama
            Regex("github").containsMatchIn(modelName) -> R.drawable.ic_model_github
            Regex("cloudflare").containsMatchIn(modelName) -> R.drawable.ic_model_cloudflare
            Regex("minimax").containsMatchIn(modelName) -> R.drawable.ic_model_minimax
            Regex("xai").containsMatchIn(modelName) -> R.drawable.ic_model_xai
            Regex("juhenext").containsMatchIn(modelName) -> R.drawable.ic_model_juhenext
            Regex("kimi").containsMatchIn(modelName) -> R.drawable.ic_model_kimi
            Regex("moonshot|月之暗面").containsMatchIn(modelName) -> R.drawable.ic_provider_moonshot
            Regex("302").containsMatchIn(modelName) -> R.drawable.ic_model_302ai
            Regex("step|阶跃").containsMatchIn(modelName) -> R.drawable.ic_model_stepfun
            Regex("intern|书生").containsMatchIn(modelName) -> R.drawable.ic_model_internlm
            Regex("cohere|command-.+").containsMatchIn(modelName) -> R.drawable.ic_model_cohere
            Regex("tavern").containsMatchIn(modelName) -> R.drawable.ic_model_tavern
            Regex("cerebras").containsMatchIn(modelName) -> R.drawable.ic_model_cerebras
            Regex("nvidia").containsMatchIn(modelName) -> R.drawable.ic_model_nvidia
            Regex("ppio|派欧").containsMatchIn(modelName) -> R.drawable.ic_model_ppio
            Regex("vercel").containsMatchIn(modelName) -> R.drawable.ic_model_vercel
            Regex("groq").containsMatchIn(modelName) -> R.drawable.ic_model_groq
            Regex("tokenpony|小马算力").containsMatchIn(modelName) -> R.drawable.ic_model_tokenpony
            Regex("ling|ring|百灵").containsMatchIn(modelName) -> R.drawable.ic_model_ling
            Regex("mimo|xiaomi|小米").containsMatchIn(modelName) -> R.drawable.ic_provider_mimo
            Regex("longcat").containsMatchIn(modelName) -> R.drawable.ic_model_longcat
            else -> defaultIconRes
        }
    }

    private fun AiProviderSetting.visibleModelCount(): Int {
        return if (apiKey.isBlank()) 0 else displayModels().size
    }

    private fun AiProviderType.localizedDisplayName(): String {
        return when (this) {
            AiProviderType.OPENAI -> getString(R.string.ai_add_provider_openai)
            AiProviderType.CLAUDE -> getString(R.string.ai_add_provider_anthropic)
            AiProviderType.GOOGLE -> displayName
        }
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
            binding.imageIcon.setImageResource(item.iconRes())
            binding.viewActiveIndicator.isVisible = false
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
                    showDetail(item.id)
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

        override fun getSwipeFlags(adapterPosition: Int, defaultFlags: Int): Int {
            val item = getItems().getOrNull(adapterPosition) ?: return 0
            return if (!item.builtIn) ItemTouchHelper.RIGHT else 0
        }

        override fun onSwiped(adapterPosition: Int, direction: Int) {
            val item = getItems().getOrNull(adapterPosition)
            if (item == null || item.builtIn || direction != ItemTouchHelper.RIGHT) {
                refreshProviders()
                return
            }
            confirmDeleteProvider(
                provider = item,
                onCancel = { refreshProviders() },
                onDeleted = { refreshProviders() }
            )
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved && providerSearchQuery.isBlank()) {
                AiProviderStore.saveProviders(getItems())
                refreshMain()
            }
            isMoved = false
        }
    }

    private inner class AiModelAdapter :
        RecyclerAdapter<AiModel, ItemAiModelBinding>(requireContext()) {

        var availableModelIds: Set<String> = emptySet()
        var providerIconRes: Int = R.drawable.ic_ai_provider
        var onToggleModel: ((AiModel, Boolean) -> Unit)? = null

        override fun getViewBinding(parent: ViewGroup): ItemAiModelBinding {
            return ItemAiModelBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiModelBinding,
            item: AiModel,
            payloads: MutableList<Any>
        ) {
            binding.imageIcon.setImageResource(item.iconRes(providerIconRes))
            binding.textName.text = item.displayName()
            binding.checkSelected.setOnCheckedChangeListener(null)
            binding.checkSelected.isVisible = true
            binding.checkSelected.isChecked = item.safeId() in availableModelIds
            binding.checkSelected.setOnCheckedChangeListener { _, isChecked ->
                onToggleModel?.invoke(item, isChecked)
            }
            binding.layoutTags.removeAllViews()
            item.capabilityTags().forEach { capability ->
                binding.layoutTags.addView(
                    createModelCapabilityTag(capability)
                )
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiModelBinding) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    item.safeId().takeIf { it.isNotBlank() }?.let(::showModelDetail)
                }
            }
        }
    }

    private enum class ModelCapabilityTag(
        val iconRes: Int,
        val labelRes: Int,
        val color: Int
    ) {
        TEXT(R.drawable.ic_ai_capability_text, R.string.ai_model_modality_text, 0xFF7C3AED.toInt()),
        VISION(R.drawable.ic_ai_capability_vision, R.string.ai_model_modality_image, 0xFFF59E0B.toInt()),
        VIDEO(R.drawable.ic_ai_capability_video, R.string.ai_model_modality_video, 0xFFEA580C.toInt()),
        ASR(R.drawable.ic_ai_capability_asr, R.string.ai_model_ability_asr, 0xFFDB2777.toInt()),
        TTS(R.drawable.ic_ai_capability_tts, R.string.ai_model_ability_tts, 0xFF0891B2.toInt()),
        TOOL(R.drawable.ic_ai_capability_tool, R.string.ai_model_ability_tool, 0xFF2563EB.toInt()),
        REASONING(
            R.drawable.ic_ai_capability_reasoning,
            R.string.ai_model_ability_reasoning,
            0xFF16A34A.toInt()
        )
    }

    private fun createModelCapabilityTag(
        capability: ModelCapabilityTag
    ): ImageView {
        val view = ImageView(requireContext())
        val tintColor = capability.color
        val backgroundColor = ColorUtils.setAlphaComponent(tintColor, 24)
        view.setImageResource(capability.iconRes)
        view.imageTintList = ColorStateList.valueOf(tintColor)
        view.background = GradientDrawable().apply {
            cornerRadius = 5.dpToPx().toFloat()
            setColor(backgroundColor)
            setStroke(1.dpToPx(), tintColor)
        }
        view.contentDescription = getString(capability.labelRes)
        view.scaleType = ImageView.ScaleType.CENTER_INSIDE
        view.setPadding(3.dpToPx(), 3.dpToPx(), 3.dpToPx(), 3.dpToPx())
        view.layoutParams = LinearLayout.LayoutParams(
            20.dpToPx(),
            20.dpToPx()
        ).apply {
            marginEnd = 4.dpToPx()
        }
        return view
    }

    private fun AiModel.capabilityTags(): List<ModelCapabilityTag> {
        val abilities = safeAbilities()
        if (AiModelAbility.ASR in abilities) {
            return listOf(ModelCapabilityTag.ASR)
        }
        if (AiModelAbility.TTS in abilities) {
            return listOf(ModelCapabilityTag.TTS)
        }
        return buildList {
            if (
                AiModelModality.TEXT in safeInputModalities()
                || AiModelModality.TEXT in safeOutputModalities()
            ) {
                add(ModelCapabilityTag.TEXT)
            }
            if (
                AiModelModality.IMAGE in safeInputModalities()
                || AiModelModality.IMAGE in safeOutputModalities()
            ) {
                add(ModelCapabilityTag.VISION)
            }
            if (
                AiModelModality.VIDEO in safeInputModalities()
                || AiModelModality.VIDEO in safeOutputModalities()
            ) {
                add(ModelCapabilityTag.VIDEO)
            }
            if (AiModelAbility.TOOL in safeAbilities()) {
                add(ModelCapabilityTag.TOOL)
            }
            if (AiModelAbility.REASONING in abilities) {
                add(ModelCapabilityTag.REASONING)
            }
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
