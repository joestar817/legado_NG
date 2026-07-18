package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.Uri
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
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
import io.legado.app.databinding.ItemAiSkillFileBinding
import io.legado.app.data.appDb
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiOperationPermissionMode
import io.legado.app.help.ai.AiPromptStore
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.help.ai.AiSkillDefinition
import io.legado.app.help.ai.AiSkillExistsException
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.ai.AiSkillScope
import io.legado.app.help.ai.normalizeAiApiPath
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.databinding.ItemAiPromptBinding
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.applyTint
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class AiConfigFragment : BaseFragment(R.layout.fragment_ai_config), ConfigBackHandler,
    CodeDialog.Callback {

    companion object {
        const val EXTRA_INITIAL_PAGE = "initialPage"
        const val PAGE_ASSISTANT = "assistant"
    }

    private enum class Page {
        MAIN,
        PROVIDERS,
        DETAIL,
        MODEL_DETAIL,
        PROMPTS,
        PROMPT_DETAIL,
        MODEL_SETTINGS,
        PURIFY_MODEL_SETTINGS,
        READ_ALOUD_MODEL_SETTINGS,
        ASSISTANT_MODEL_SETTINGS,
        PURIFY_SETTINGS
    }
    private enum class ProviderDetailTab { CONFIG, MODELS }

    private sealed interface SkillTreeRow {
        val path: String
        val name: String
        val depth: Int

        data class Directory(
            override val path: String,
            override val name: String,
            override val depth: Int,
            val expanded: Boolean
        ) : SkillTreeRow

        data class File(
            override val path: String,
            override val name: String,
            override val depth: Int,
            val size: Int
        ) : SkillTreeRow
    }

    private class SkillDirectoryNode(
        val name: String,
        val path: String
    ) {
        val directories = linkedMapOf<String, SkillDirectoryNode>()
        val files = mutableListOf<String>()
    }

    private val binding by viewBinding(FragmentAiConfigBinding::bind)
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val providerAdapter by lazy { AiProviderAdapter() }
    private val modelAdapter by lazy { AiModelAdapter() }
    private val promptAdapter by lazy { AiPromptAdapter() }
    private val skillFileAdapter by lazy { AiSkillFileAdapter() }
    private lateinit var providerItemTouchHelper: ItemTouchHelper
    private var currentPage = Page.MAIN
    private var currentProviderId: String? = null
    private var currentModelId: String? = null
    private var currentSkill: AiSkillDefinition? = null
    private var currentPrompt: AiPromptStore.Prompt? = null
    private val expandedSkillDirectories = linkedSetOf<String>()
    private var providerSearchQuery: String = ""
    private var modelSearchQuery: String = ""
    private var providerDetailTab = ProviderDetailTab.CONFIG
    private val autoFetchedModelProviderIds = hashSetOf<String>()
    private var requestJob: Job? = null
    private var ignoreMainFormChanges = false
    private var ignoreProviderFormChanges = false
    private var ignorePurifyFormChanges = false
    private var ignoreModelDetailChanges = false
    private var apiKeyVisible = false
    private val balanceNumberFormat by lazy { DecimalFormat("0.####") }
    private val importSkillFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        importSkillFromUri(uri)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMain()
        initProviderList()
        initDetail()
        initPromptList()
        initPromptDetail()
        initModelSettings()
        initPurifySettings()
        val initialPage = activity?.intent?.getStringExtra(EXTRA_INITIAL_PAGE)
        showMain()
        if (initialPage == PAGE_ASSISTANT) {
            activity?.intent?.removeExtra(EXTRA_INITIAL_PAGE)
            showAssistantModelSettings()
        }
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
            showAssistantModelSettings()
        }
        binding.layoutPromptEntry.setOnClickListener {
            showPromptList()
        }
        binding.layoutChatFab.setOnClickListener {
            binding.switchChatFab.isChecked = !binding.switchChatFab.isChecked
        }
        binding.switchChatFab.setOnCheckedChangeListener { _, isChecked ->
            if (!ignoreMainFormChanges) {
                AiConfig.chatFabEnabled = isChecked
                refreshMain()
            }
        }
        binding.layoutInternalMcp.setOnClickListener {
            binding.switchInternalMcp.isChecked = !binding.switchInternalMcp.isChecked
        }
        binding.switchInternalMcp.setOnCheckedChangeListener { _, isChecked ->
            if (!ignoreMainFormChanges) {
                AiConfig.internalMcpEnabled = isChecked
                refreshMain()
                refreshModelSettings()
            }
        }
        binding.layoutAiOperationPermission.setOnClickListener {
            showOperationPermissionDialog()
        }
        binding.layoutAiMemory.setOnClickListener {
            showAiMemoryDialog()
        }
        binding.switchAiMemory.setOnCheckedChangeListener { _, isChecked ->
            if (!ignoreMainFormChanges) {
                AiConfig.memoryEnabled = isChecked
                refreshMain()
                refreshAiMemorySummary()
                refreshModelSettings()
            }
        }
        binding.layoutPurifyEntry.setOnClickListener {
            showPurifyModelSettings()
        }
        binding.layoutReadAloudEntry.setOnClickListener {
            showReadAloudModelSettings()
        }
        binding.layoutPurifySettingsEntry.setOnClickListener {
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
        binding.recyclerModels.setEdgeEffectColor(accentColor)
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
        binding.refreshModels.setColorSchemeColors(accentColor)
        binding.refreshModels.setOnRefreshListener { fetchModels() }
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
        binding.layoutAssistantModelSettingsEntry.setOnClickListener {
            showAssistantModelSettings()
        }
        binding.layoutPurifyModelEntry.setOnClickListener {
            showPurifyModelSelectDialog()
        }
        binding.layoutPurifyReasoningEntry.setOnClickListener {
            showPurifyReasoningDialog()
        }
        binding.layoutReadAloudStoryboardModelEntry.setOnClickListener {
            showReadAloudStoryboardModelSelectDialog()
        }
        binding.layoutReadAloudStoryboardPreloadEntry.setOnClickListener {
            showReadAloudStoryboardPreloadDialog()
        }
        binding.layoutReadAloudStoryboardReasoningEntry.setOnClickListener {
            showReadAloudStoryboardReasoningDialog()
        }
        binding.layoutAssistantModelEntry.setOnClickListener {
            showAssistantModelSelectDialog()
        }
        binding.layoutAssistantReasoningEntry.setOnClickListener {
            showAssistantReasoningDialog()
        }
        binding.layoutContextCompactionModelEntry.setOnClickListener {
            showContextCompactionModelSelectDialog()
        }
        binding.layoutAssistantContextWindowEntry.setOnClickListener {
            showAssistantContextWindowDialog()
        }
        binding.layoutContextCompactionThresholdEntry.setOnClickListener {
            showContextCompactionThresholdDialog()
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
        binding.switchStreamResponse.setOnCheckedChangeListener { _, _ ->
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
            showSkillDetail(item)
        }
        binding.buttonAddSkill.setOnClickListener {
            showManualSkillDialog()
        }
        binding.buttonImportSkill.setOnClickListener {
            showImportSkillDialog()
        }
    }

    private fun initPromptDetail() {
        binding.recyclerSkillFiles.layoutManager = LinearLayoutManager(requireContext())
        skillFileAdapter.bindToRecyclerView(binding.recyclerSkillFiles)
        skillFileAdapter.setOnItemClickListener { _, item ->
            when (item) {
                is SkillTreeRow.Directory -> toggleSkillDirectory(item.path)
                is SkillTreeRow.File -> openSkillFile(item.path, edit = false)
            }
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
            binding.layoutAssistantModelSettings.isVisible -> Page.ASSISTANT_MODEL_SETTINGS
            binding.layoutReadAloudModelSettings.isVisible -> Page.READ_ALOUD_MODEL_SETTINGS
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
            Page.PURIFY_MODEL_SETTINGS -> showMain()
            Page.READ_ALOUD_MODEL_SETTINGS -> showMain()
            Page.ASSISTANT_MODEL_SETTINGS -> showMain()
            Page.PURIFY_SETTINGS -> showPurifyModelSettings()
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
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshMain()
    }

    private fun showProviderList() {
        currentPage = Page.PROVIDERS
        currentProviderId = null
        currentModelId = null
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshAccentControls()
        refreshProviders()
    }

    private fun showDetail(
        providerId: String,
        tab: ProviderDetailTab = ProviderDetailTab.CONFIG
    ) {
        currentPage = Page.DETAIL
        currentProviderId = providerId
        currentModelId = null
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshAccentControls()
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
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshPrompts()
    }

    private fun showSkillDetail(skill: AiSkillDefinition) {
        currentPage = Page.PROMPT_DETAIL
        currentProviderId = null
        currentModelId = null
        currentSkill = skill
        currentPrompt = skill.editablePrompt
        expandedSkillDirectories.clear()
        setPageTitle(skill.name)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = true
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshPromptDetail()
    }

    private fun showModelSettings() {
        currentPage = Page.MODEL_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshModelSettings()
    }

    private fun showPurifyModelSettings() {
        currentPage = Page.PURIFY_MODEL_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshModelSettings()
    }

    private fun showReadAloudModelSettings() {
        currentPage = Page.READ_ALOUD_MODEL_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_read_aloud)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutReadAloudModelSettings.isVisible = true
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = false
        refreshModelSettings()
    }

    private fun showAssistantModelSettings() {
        currentPage = Page.ASSISTANT_MODEL_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_assistant)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutModelDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        binding.layoutModelSettings.isVisible = false
        binding.layoutPurifyModelSettings.isVisible = false
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = true
        binding.layoutPurifySettings.isVisible = false
        refreshModelSettings()
    }

    private fun showPurifySettings() {
        currentPage = Page.PURIFY_SETTINGS
        currentProviderId = null
        currentModelId = null
        currentSkill = null
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
        binding.layoutReadAloudModelSettings.isVisible = false
        binding.layoutAssistantModelSettings.isVisible = false
        binding.layoutPurifySettings.isVisible = true
        refreshPurifySettings()
    }

    private fun refreshCurrentPage() {
        refreshAccentControls()
        when (currentPage) {
            Page.MAIN -> refreshMain()
            Page.PROVIDERS -> refreshProviders()
            Page.DETAIL -> refreshCurrentDetail()
            Page.MODEL_DETAIL -> refreshCurrentModelDetail()
            Page.PROMPTS -> refreshPrompts()
            Page.PROMPT_DETAIL -> refreshPromptDetail()
            Page.MODEL_SETTINGS -> refreshModelSettings()
            Page.PURIFY_MODEL_SETTINGS -> refreshModelSettings()
            Page.READ_ALOUD_MODEL_SETTINGS -> refreshModelSettings()
            Page.ASSISTANT_MODEL_SETTINGS -> refreshModelSettings()
            Page.PURIFY_SETTINGS -> refreshPurifySettings()
        }
    }

    private fun refreshAccentControls() {
        val color = accentColor
        applyAccentIconButton(binding.buttonAddProvider, color)
    }

    private fun applyAccentIconButton(button: ImageView, color: Int) {
        button.imageTintList = ColorStateList.valueOf(color)
        button.background = Selector.shapeBuild()
            .setCornerRadius(12.dpToPx())
            .setStrokeWidth(1.dpToPx())
            .setDefaultStrokeColor(color)
            .setPressedBgColor(ColorUtils.setAlphaComponent(color, 24))
            .create()
    }

    private fun createNgChoiceDialogRoot(
        title: String,
        description: String?
    ): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.ng_bg_dialog)
            clipToOutline = true
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 10.dpToPx())
                addView(TextView(requireContext()).apply {
                    text = title
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 24f
                })
                if (!description.isNullOrBlank()) {
                    addView(TextView(requireContext()).apply {
                        text = description
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
                        textSize = 14f
                        setPadding(0, 8.dpToPx(), 0, 0)
                    })
                }
            })
            addView(LinearLayout(requireContext()).apply {
                tag = "body"
                orientation = LinearLayout.VERTICAL
                setPadding(20.dpToPx(), 0, 24.dpToPx(), 22.dpToPx())
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return ContextCompat.getDrawable(requireContext(), value.resourceId)
    }

    private fun showOperationPermissionDialog() {
        val modes = AiOperationPermissionMode.entries.toTypedArray()
        val dialog = Dialog(requireContext())
        val root = createNgChoiceDialogRoot(
            title = getString(R.string.ai_operation_permission),
            description = null
        )
        val body = root.findViewWithTag<LinearLayout>("body")
        modes.forEach { mode ->
            body.addView(createOperationPermissionRow(mode, dialog))
        }
        dialog.setContentView(root)
        dialog.show()
        dialog.applyNgWindow()
    }

    private fun createOperationPermissionRow(
        mode: AiOperationPermissionMode,
        dialog: Dialog
    ): View {
        val selected = AiConfig.operationPermissionMode == mode
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = selectableItemBackground()
            isClickable = true
            isFocusable = true
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            setOnClickListener {
                AiConfig.operationPermissionMode = mode
                dialog.dismiss()
                refreshMain()
                refreshModelSettings()
            }
            addView(RadioButton(requireContext()).apply {
                isChecked = selected
                buttonTintList = ColorStateList.valueOf(accentColor)
                isClickable = false
            }, LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx()))
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(requireContext()).apply {
                    text = operationPermissionModeTitle(mode)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
                    textSize = 17f
                })
                addView(TextView(requireContext()).apply {
                    text = operationPermissionModeSummary(mode)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
                    textSize = 13f
                    setPadding(0, 4.dpToPx(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
        }
    }

    private fun operationPermissionModeTitle(mode: AiOperationPermissionMode): String {
        return getString(
            when (mode) {
                AiOperationPermissionMode.CONFIRM_WRITE ->
                    R.string.ai_operation_permission_mode_confirm_write
                AiOperationPermissionMode.TRUSTED ->
                    R.string.ai_operation_permission_mode_trusted
            }
        )
    }

    private fun operationPermissionModeSummary(mode: AiOperationPermissionMode): String {
        return getString(
            when (mode) {
                AiOperationPermissionMode.CONFIRM_WRITE ->
                    R.string.ai_operation_permission_summary_confirm_write
                AiOperationPermissionMode.TRUSTED ->
                    R.string.ai_operation_permission_summary_trusted
            }
        )
    }

    private fun refreshMain() {
        val providers = AiProviderStore.providers()
        val color = accentColor
        val entryIconTint = ColorStateList.valueOf(color)
        binding.textMainSectionLabel.setTextColor(color)
        binding.imageProviderEntryIcon.imageTintList = entryIconTint
        binding.imageModelEntryIcon.imageTintList = entryIconTint
        binding.imagePromptEntryIcon.imageTintList = entryIconTint
        binding.imageChatFabIcon.imageTintList = entryIconTint
        binding.imagePurifyEntryIcon.imageTintList = entryIconTint
        binding.imageReadAloudEntryIcon.imageTintList = entryIconTint
        ignoreMainFormChanges = true
        try {
            binding.switchChatFab.isChecked = AiConfig.chatFabEnabled
            binding.switchInternalMcp.isChecked = AiConfig.internalMcpEnabled
            binding.switchAiMemory.isChecked = AiConfig.memoryEnabled
        } finally {
            ignoreMainFormChanges = false
        }
        binding.textProviderEntrySummary.text = getString(
            R.string.ai_provider_menu_summary,
            providers.size.toString()
        )
        binding.textModelEntrySummary.text = getString(
            R.string.ai_model_function_summary,
            assistantModelSummaryText(),
            assistantReasoningSummaryText()
        )
        binding.textPromptEntrySummary.text = getString(
            R.string.ai_prompt_menu_summary,
            visibleAiSkills().size.toString()
        )
        binding.textChatFabSummary.text = getString(
            if (AiConfig.chatFabEnabled) {
                R.string.ai_chat_fab_summary_on
            } else {
                R.string.ai_chat_fab_summary_off
            }
        )
        binding.textInternalMcpSummary.text = getString(
            if (AiConfig.internalMcpEnabled) {
                R.string.ai_internal_mcp_summary_on
            } else {
                R.string.ai_internal_mcp_summary_off
            }
        )
        binding.textAiOperationPermissionSummary.text =
            operationPermissionModeSummary(AiConfig.operationPermissionMode)
        binding.textAiMemorySummary.text = getString(
            if (AiConfig.memoryEnabled) {
                R.string.ai_memory_summary_on
            } else {
                R.string.ai_memory_summary_off
            }
        )
        refreshAiMemorySummary()
        binding.textPurifyEntrySummary.text = getString(
            R.string.ai_model_function_summary,
            purifyModelSummaryText(),
            purifyReasoningSummaryText()
        )
        binding.textReadAloudEntrySummary.text = getString(
            R.string.ai_model_function_summary,
            readAloudStoryboardModelSummaryText(),
            readAloudStoryboardReasoningSummaryText()
        )
    }

    private fun refreshAiMemorySummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                loadAiMemoryStats()
            }
            if (!isAdded) return@launch
            binding.textAiMemorySummary.text = if (AiConfig.memoryEnabled) {
                getString(
                    R.string.ai_memory_summary_on_with_stats,
                    stats.count,
                    formatMemorySize(stats.estimatedSize)
                )
            } else {
                getString(
                    R.string.ai_memory_summary_off_with_stats,
                    stats.count,
                    formatMemorySize(stats.estimatedSize)
                )
            }
        }
    }

    private fun showAiMemoryDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                loadAiMemoryStats()
            }
            if (!isAdded) return@launch
            val context = requireContext()
            val dialog = Dialog(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.ng_bg_dialog)
                clipToOutline = true
                setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 18.dpToPx())
            }
            root.addView(TextView(context).apply {
                text = getString(R.string.ai_memory)
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
            })
            root.addView(TextView(context).apply {
                text = if (AiConfig.memoryEnabled) {
                    getString(R.string.ai_memory_summary_on)
                } else {
                    getString(R.string.ai_memory_summary_off)
                }
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                textSize = 14f
                setPadding(0, 8.dpToPx(), 0, 18.dpToPx())
            })
            root.addView(createMemoryStatRow(context, getString(R.string.ai_memory_count), stats.count.toString()))
            root.addView(createMemoryStatRow(context, getString(R.string.ai_memory_size), formatMemorySize(stats.estimatedSize)))
            root.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setPadding(0, 22.dpToPx(), 0, 0)
                addView(TextView(context).apply {
                    text = getString(R.string.dialog_cancel)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.ng_primary))
                    textSize = 14f
                    includeFontPadding = false
                    background = ContextCompat.getDrawable(context, R.drawable.ng_bg_button_secondary)
                    setOnClickListener {
                        dialog.dismiss()
                    }
                }, LinearLayout.LayoutParams(76.dpToPx(), 36.dpToPx()).apply {
                    rightMargin = 8.dpToPx()
                })
                addView(TextView(context).apply {
                    text = getString(R.string.ai_memory_clear)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_primary))
                    textSize = 14f
                    includeFontPadding = false
                    alpha = if (stats.count > 0) 1f else 0.45f
                    isEnabled = stats.count > 0
                    background = ContextCompat.getDrawable(context, R.drawable.ng_bg_button_primary)
                    setOnClickListener {
                        confirmClearAiMemory(dialog)
                    }
                }, LinearLayout.LayoutParams(90.dpToPx(), 36.dpToPx()))
            })
            dialog.setContentView(root)
            dialog.show()
            dialog.applyNgWindow()
        }
    }

    private fun createMemoryStatRow(context: android.content.Context, label: String, value: String): View {
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
            addView(TextView(context).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                textSize = 15f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
            addView(TextView(context).apply {
                text = value
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }

    private fun confirmClearAiMemory(parentDialog: Dialog) {
        alert(R.string.ai_memory_clear) {
            setMessage(R.string.ai_memory_clear_confirm)
            okButton {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.agentMemoryDao.clearAll()
                    }
                    parentDialog.dismiss()
                    refreshAiMemorySummary()
                    Toast.makeText(requireContext(), R.string.ai_memory_cleared, Toast.LENGTH_SHORT).show()
                }
            }
            noButton()
        }
    }

    private fun loadAiMemoryStats(): AiMemoryStats {
        return AiMemoryStats(
            count = appDb.agentMemoryDao.countAll(),
            estimatedSize = appDb.agentMemoryDao.estimatedSize()
        )
    }

    private fun formatMemorySize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
            else -> "${bytes} B"
        }
    }

    private fun refreshProviders() {
        val allProviders = AiProviderStore.providers()
        autoFetchedModelProviderIds.retainAll(allProviders.mapTo(hashSetOf()) { it.id })
        val providers = allProviders
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
            binding.switchStreamResponse.isChecked = provider.streamResponseEnabled
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
            binding.switchStreamResponse.isVisible = openAiCompatible
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
        if (tab == ProviderDetailTab.MODELS) {
            maybeAutoFetchModels()
        }
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
        sheetBinding.viewModelEditTabBasicIndicator.setBackgroundColor(activeColor)
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
        promptAdapter.setItems(visibleAiSkills())
    }

    private fun visibleAiSkills(): List<AiSkillDefinition> {
        return AiSkillRegistry.managementSkills()
    }

    private fun refreshPromptDetail() {
        val skill = currentSkill ?: return
        binding.textPromptDetailTitle.text = skill.name
        binding.textPromptDetailSummary.text = skill.summary
        refreshSkillFileTree()
    }

    private fun refreshSkillFileTree() {
        val skill = currentSkill ?: return
        skillFileAdapter.setItems(buildSkillTreeRows(skill.id))
    }

    private fun buildSkillTreeRows(skillId: String): List<SkillTreeRow> {
        val root = SkillDirectoryNode(name = "", path = "")
        AiSkillRegistry.skillFilePaths(skillId).forEach { path ->
            val segments = path.split('/')
            var directory = root
            segments.dropLast(1).forEach { name ->
                val directoryPath = listOf(directory.path, name)
                    .filter(String::isNotBlank)
                    .joinToString("/")
                directory = directory.directories.getOrPut(name) {
                    SkillDirectoryNode(name = name, path = directoryPath)
                }
            }
            directory.files += path
        }

        fun flatten(directory: SkillDirectoryNode, depth: Int): List<SkillTreeRow> {
            return buildList {
                directory.directories.values.sortedBy(SkillDirectoryNode::name).forEach { child ->
                    val expanded = child.path in expandedSkillDirectories
                    add(
                        SkillTreeRow.Directory(
                            path = child.path,
                            name = child.name,
                            depth = depth,
                            expanded = expanded
                        )
                    )
                    if (expanded) addAll(flatten(child, depth + 1))
                }
                directory.files.sortedWith(
                    compareBy<String>({ it.substringAfterLast('/') != "SKILL.md" }, { it })
                ).forEach { path ->
                    add(
                        SkillTreeRow.File(
                            path = path,
                            name = path.substringAfterLast('/'),
                            depth = depth,
                            size = AiSkillRegistry.skillFileSize(skillId, path)
                        )
                    )
                }
            }
        }

        return flatten(root, depth = 0)
    }

    private fun toggleSkillDirectory(path: String) {
        if (!expandedSkillDirectories.add(path)) {
            expandedSkillDirectories.remove(path)
        }
        refreshSkillFileTree()
    }

    private fun openSkillFile(path: String, edit: Boolean) {
        val skill = currentSkill ?: return
        val content = if (path == "SKILL.md" && skill.editablePrompt != null) {
            AiPromptStore.prompt(skill.editablePrompt)
        } else {
            AiSkillRegistry.readSkillFile(skill.id, path)
        }
        val editable = edit && !skill.builtIn && path == "SKILL.md"
        showDialogFragment(
            CodeDialog(
                code = content,
                disableEdit = !editable,
                requestId = if (editable) "${skill.id}:$path" else null,
                title = path,
                exportFilePrefix = "${skill.id}-${path.substringAfterLast('/').substringBeforeLast('.')}"
            )
        )
    }

    override fun onCodeSave(code: String, requestId: String?) {
        val skill = currentSkill ?: return
        val expectedRequestId = "${skill.id}:SKILL.md"
        if (skill.builtIn || requestId != expectedRequestId) return
        currentPrompt?.let { prompt ->
            AiPromptStore.save(prompt, code)
        } ?: saveAgentSkillContent(skill.id, code)
        currentSkill = AiSkillRegistry.get(skill.id) ?: skill
        currentPrompt = currentSkill?.editablePrompt
        refreshPrompts()
        refreshSkillFileTree()
        Toast.makeText(requireContext(), R.string.ai_prompt_saved, Toast.LENGTH_SHORT).show()
    }

    private fun showManualSkillDialog() {
        val editText = EditText(requireContext()).apply {
            minLines = 10
            maxLines = 18
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setSingleLine(false)
            hint = getString(R.string.ai_skill_content_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_skill_add)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importSkillFromText(editText.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .applyTint()
    }

    private fun showImportSkillDialog() {
        val labels = arrayOf(
            getString(R.string.ai_skill_import_file),
            getString(R.string.ai_skill_import_github)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_skill_import)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> importSkillFileLauncher.launch(arrayOf("text/*", "text/markdown", "application/octet-stream"))
                    1 -> showImportSkillUrlDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .applyTint()
    }

    private fun showImportSkillUrlDialog() {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.ai_skill_github_url_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_skill_import_github)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importSkillFromUrl(editText.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .applyTint()
    }

    private fun importSkillFromUri(uri: Uri) {
        lifecycleScope.launch {
            var content = ""
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    content = requireContext().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    AiSkillRegistry.importFromText(content)
                }
            }
            handleSkillImportResult(result) {
                importSkillFromText(content, overwriteExisting = true)
            }
        }
    }

    private fun importSkillFromUrl(url: String, overwriteExisting: Boolean = false) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AiSkillRegistry.importFromUrl(url, overwriteExisting) }
            }
            handleSkillImportResult(result) {
                importSkillFromUrl(url, overwriteExisting = true)
            }
        }
    }

    private fun importSkillFromText(content: String, overwriteExisting: Boolean = false) {
        val result = runCatching { AiSkillRegistry.importFromText(content, overwriteExisting) }
        handleSkillImportResult(result) {
            importSkillFromText(content, overwriteExisting = true)
        }
    }

    private fun handleSkillImportResult(
        result: Result<io.legado.app.data.entities.AiSkill>,
        overwriteAction: (() -> Unit)? = null
    ) {
        result.onSuccess { skill ->
            Toast.makeText(
                requireContext(),
                getString(R.string.ai_skill_import_success, skill.name),
                Toast.LENGTH_SHORT
            ).show()
            refreshPrompts()
        }.onFailure { error ->
            if (error is AiSkillExistsException && overwriteAction != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ai_skill_overwrite_title)
                    .setMessage(getString(R.string.ai_skill_overwrite_message, error.skillId))
                    .setPositiveButton(android.R.string.ok) { _, _ -> overwriteAction() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                    .applyTint()
                return@onFailure
            }
            Toast.makeText(
                requireContext(),
                getString(R.string.ai_skill_import_failed, error.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveAgentSkillContent(skillId: String, content: String) {
        AiSkillRegistry.saveSkillContent(skillId, content)
    }

    private fun exportCurrentSkill() {
        val skill = currentSkill ?: return
        val dir = File(requireContext().cacheDir, "ai-skill-export").apply {
            mkdirs()
        }
        val file = File(dir, "${skill.safeFileName()}.md")
        file.writeText(AiSkillRegistry.exportContent(skill))
        requireContext().share(file, "text/markdown")
    }

    private fun confirmDeleteCurrentSkill() {
        val skill = currentSkill ?: return
        if (skill.builtIn) {
            Toast.makeText(requireContext(), R.string.ai_skill_builtin_delete_denied, Toast.LENGTH_SHORT)
                .show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.ai_skill_delete_title)
            .setMessage(getString(R.string.ai_skill_delete_message, skill.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (AiSkillRegistry.deleteSkill(skill.id)) {
                    Toast.makeText(requireContext(), R.string.ai_skill_delete_done, Toast.LENGTH_SHORT)
                        .show()
                    showPromptList()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .applyTint()
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
            applyTint(accentColor)
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
        binding.textReadAloudModelSettingsSectionLabel.setTextColor(color)
        binding.textAssistantModelSettingsSectionLabel.setTextColor(color)
        binding.imagePurifyModelSettingsIcon.imageTintList = entryIconTint
        binding.imageAssistantModelSettingsIcon.imageTintList = entryIconTint
        binding.imagePurifyModelIcon.imageTintList = entryIconTint
        binding.imagePurifyReasoningIcon.imageTintList = entryIconTint
        binding.imagePurifySettingsEntryIcon.imageTintList = entryIconTint
        binding.imageReadAloudStoryboardModelIcon.imageTintList = entryIconTint
        binding.imageReadAloudStoryboardPreloadIcon.imageTintList = entryIconTint
        binding.imageReadAloudStoryboardReasoningIcon.imageTintList = entryIconTint
        binding.imageAssistantModelIcon.imageTintList = entryIconTint
        binding.imageAssistantReasoningIcon.imageTintList = entryIconTint
        binding.imageContextCompactionModelIcon.imageTintList = entryIconTint
        binding.imageAssistantContextWindowIcon.imageTintList = entryIconTint
        binding.imageContextCompactionThresholdIcon.imageTintList = entryIconTint
        binding.imageInternalMcpIcon.imageTintList = entryIconTint
        binding.imageAiMemoryIcon.imageTintList = entryIconTint
        binding.imageAiOperationPermissionIcon.imageTintList = entryIconTint
        ignoreMainFormChanges = true
        try {
            binding.switchInternalMcp.isChecked = AiConfig.internalMcpEnabled
            binding.switchAiMemory.isChecked = AiConfig.memoryEnabled
        } finally {
            ignoreMainFormChanges = false
        }
        binding.textPurifyModelSettingsSummary.text = getString(
            R.string.ai_model_function_summary,
            purifyModelSummaryText(),
            purifyReasoningSummaryText()
        )
        binding.textAssistantModelSettingsSummary.text = getString(
            R.string.ai_model_function_summary,
            assistantModelSummaryText(),
            assistantReasoningSummaryText()
        )
        binding.textPurifyModelSummary.text = purifyModelSummaryText()
        binding.textPurifyReasoningSummary.text = purifyReasoningSummaryText()
        binding.textReadAloudStoryboardModelSummary.text = readAloudStoryboardModelSummaryText()
        binding.textReadAloudStoryboardPreloadSummary.text = getString(
            R.string.ai_read_aloud_storyboard_preload_summary,
            AiConfig.readAloudStoryboardPreloadCount
        )
        binding.textReadAloudStoryboardReasoningSummary.text =
            readAloudStoryboardReasoningSummaryText()
        binding.textPurifySettingsEntrySummary.text = getString(
            R.string.ai_purify_settings_summary,
            getString(if (AiConfig.purifyAutoApply) R.string.enabled else R.string.disabled),
            getString(if (AiConfig.purifyChapterAutoApply) R.string.enabled else R.string.disabled),
            AiConfig.purifyParagraphLimit.toString(),
            AiConfig.purifyChapterSegmentLimit.toString(),
            AiConfig.purifyChapterSampleLimit.toString(),
            AiConfig.purifyChapterConcurrencyLimit.toString(),
            AiConfig.purifyChapterRetryCount.toString()
        )
        binding.textAssistantModelSummary.text = assistantModelSummaryText()
        binding.textAssistantReasoningSummary.text = assistantReasoningSummaryText()
        binding.textContextCompactionModelSummary.text = contextCompactionModelSummaryText()
        binding.textAssistantContextWindowSummary.text = getString(
            R.string.ai_assistant_context_window_summary,
            contextWindowLabel(AiConfig.assistantContextWindowTokens)
        )
        binding.textContextCompactionThresholdSummary.text =
            if (AiConfig.contextCompactionThresholdPercent == 0) {
                getString(R.string.ai_context_compaction_threshold_off)
            } else {
                getString(
                    R.string.ai_context_compaction_threshold_summary,
                    AiConfig.contextCompactionThresholdPercent,
                    AiConfig.assistantContextWindowTokens *
                        AiConfig.contextCompactionThresholdPercent / 100 / 1000
                )
            }
        binding.textInternalMcpSummary.text = getString(
            if (AiConfig.internalMcpEnabled) {
                R.string.ai_internal_mcp_summary_on
            } else {
                R.string.ai_internal_mcp_summary_off
            }
        )
        binding.textAiMemorySummary.text = getString(
            if (AiConfig.memoryEnabled) {
                R.string.ai_memory_summary_on
            } else {
                R.string.ai_memory_summary_off
            }
        )
        binding.textAiOperationPermissionSummary.text =
            operationPermissionModeSummary(AiConfig.operationPermissionMode)
        val reasoningEnabled = selectedPurifyModel()?.model?.supportsReasoning() == true
        binding.layoutPurifyReasoningEntry.alpha = if (reasoningEnabled) 1f else 0.55f
        val readAloudReasoningEnabled =
            selectedReadAloudStoryboardModel()?.model?.supportsReasoning() == true
        binding.layoutReadAloudStoryboardReasoningEntry.alpha =
            if (readAloudReasoningEnabled) 1f else 0.55f
        val assistantReasoningEnabled = selectedAssistantModel()?.model?.supportsReasoning() == true
        binding.layoutAssistantReasoningEntry.alpha = if (assistantReasoningEnabled) 1f else 0.55f
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

    private fun assistantModelSummaryText(): String {
        val selected = selectedAssistantModel()
        return when {
            selected == null && AiConfig.assistantModelId.isBlank() ->
                getString(R.string.ai_assistant_model_not_selected)
            selected == null ->
                getString(R.string.ai_assistant_model_unavailable)
            else ->
                getString(
                    R.string.ai_purify_model_selected_summary,
                    selected.model.displayName(),
                    selected.provider.name
                )
        }
    }

    private fun assistantReasoningSummaryText(): String {
        val selected = selectedAssistantModel()
        return when {
            selected == null -> getString(R.string.ai_assistant_reasoning_select_model_first)
            !selected.model.supportsReasoning() -> getString(R.string.ai_assistant_reasoning_unsupported)
            else -> getString(
                R.string.ai_purify_reasoning_level_summary,
                AiConfig.assistantReasoningLevel.displayName()
            )
        }
    }

    private fun contextCompactionModelSummaryText(): String {
        val providerId = AiConfig.contextCompactionProviderId
        val modelId = AiConfig.contextCompactionModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return getString(R.string.ai_context_compaction_model_follow)
        }
        val provider = AiProviderStore.provider(providerId)
        val model = provider?.purifyEligibleModels()?.firstOrNull { it.safeId() == modelId }
        return if (provider == null || model == null) {
            getString(R.string.ai_assistant_model_unavailable)
        } else {
            getString(
                R.string.ai_purify_model_selected_summary,
                model.displayName(),
                provider.name
            )
        }
    }

    private fun readAloudStoryboardModelSummaryText(): String {
        val selected = selectedReadAloudStoryboardModel()
        return when {
            selected == null && AiConfig.readAloudStoryboardModelId.isBlank() ->
                getString(R.string.ai_read_aloud_storyboard_model_not_selected)
            selected == null ->
                getString(R.string.ai_read_aloud_storyboard_model_unavailable)
            else ->
                getString(
                    R.string.ai_purify_model_selected_summary,
                    selected.model.displayName(),
                    selected.provider.name
                )
        }
    }

    private fun readAloudStoryboardReasoningSummaryText(): String {
        val selected = selectedReadAloudStoryboardModel()
        return when {
            selected == null -> getString(R.string.ai_read_aloud_reasoning_select_model_first)
            !selected.model.supportsReasoning() ->
                getString(R.string.ai_read_aloud_reasoning_unsupported)
            else -> getString(
                R.string.ai_purify_reasoning_level_summary,
                AiConfig.readAloudStoryboardReasoningLevel.displayName()
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

    private fun selectedReadAloudStoryboardModel(): PurifyModelOption? {
        val providerId = AiConfig.readAloudStoryboardProviderId
        val modelId = AiConfig.readAloudStoryboardModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return null
        }
        val provider = AiProviderStore.provider(providerId)?.takeIf { it.enabled } ?: return null
        val model = provider.purifyEligibleModels().firstOrNull { it.safeId() == modelId }
            ?: return null
        return PurifyModelOption(provider, model)
    }

    private fun selectedAssistantModel(): AiAssistantConfigUi.AssistantModelOption? {
        return AiAssistantConfigUi.selectedModel()
    }

    private fun showPurifyModelSelectDialog() {
        val sheet = NgLongListBottomSheet(
            context = requireContext(),
            searchHint = getString(R.string.ai_purify_model_search_hint)
        )
        sheet.setScrollableContent { container, query, dialog ->
            renderPurifyModelOptions(container, query, dialog)
        }
        sheet.show()
    }

    private fun showReadAloudStoryboardModelSelectDialog() {
        val sheet = NgLongListBottomSheet(
            context = requireContext(),
            searchHint = getString(R.string.ai_read_aloud_storyboard_model_search_hint)
        )
        sheet.setScrollableContent { container, query, dialog ->
            renderReadAloudStoryboardModelOptions(container, query, dialog)
        }
        sheet.show()
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

    private fun renderReadAloudStoryboardModelOptions(
        container: LinearLayout,
        query: String,
        dialog: BottomSheetDialog
    ) {
        container.removeAllViews()
        val normalizedQuery = query.trim()
        val groupedOptions = purifyModelOptions(normalizedQuery)
        if (groupedOptions.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = getString(R.string.ai_read_aloud_storyboard_model_empty)
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
                container.addView(createReadAloudStoryboardModelCard(provider, model, dialog))
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
        return createTextTaskModelCard(provider, model, selected, dialog) {
            AiConfig.savePurifyModel(provider.id, model.safeId())
        }
    }

    private fun createReadAloudStoryboardModelCard(
        provider: AiProviderSetting,
        model: AiModel,
        dialog: BottomSheetDialog
    ): LinearLayout {
        val selected = AiConfig.readAloudStoryboardProviderId == provider.id &&
            AiConfig.readAloudStoryboardModelId == model.safeId()
        return createTextTaskModelCard(provider, model, selected, dialog) {
            AiConfig.saveReadAloudStoryboardModel(provider.id, model.safeId())
        }
    }

    private fun createTextTaskModelCard(
        provider: AiProviderSetting,
        model: AiModel,
        selected: Boolean,
        dialog: BottomSheetDialog,
        onSelect: () -> Unit
    ): LinearLayout {
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
                onSelect()
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
                        addView(createModelCapabilityTag(requireContext(), it))
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

    private fun showAssistantModelSelectDialog() {
        AiAssistantConfigUi.showModelSelectSheet(requireContext()) {
            refreshModelSettings()
            refreshMain()
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
        showReasoningLevelDialog(
            title = getString(R.string.ai_purify_reasoning_title),
            description = getString(R.string.ai_purify_reasoning_desc),
            currentLevel = AiConfig.purifyReasoningLevel,
            iconTintWhenOff = true
        ) { level ->
            AiConfig.purifyReasoningLevel = level
            refreshModelSettings()
            refreshMain()
        }
    }

    private fun showContextCompactionModelSelectDialog() {
        val sheet = NgLongListBottomSheet(
            context = requireContext(),
            searchHint = getString(R.string.ai_context_compaction_model_search_hint)
        )
        sheet.setScrollableContent { container, query, dialog ->
            container.removeAllViews()
            if (query.isBlank() || getString(R.string.ai_context_compaction_model_follow)
                    .contains(query, ignoreCase = true)
            ) {
                container.addView(createFollowAssistantCompactionModelCard(dialog))
            }
            purifyModelOptions(query.trim()).forEach { (provider, models) ->
                container.addView(createPurifyProviderHeader(provider))
                models.forEach { model ->
                    val selected = AiConfig.contextCompactionProviderId == provider.id &&
                        AiConfig.contextCompactionModelId == model.safeId()
                    container.addView(
                        createTextTaskModelCard(provider, model, selected, dialog) {
                            AiConfig.saveContextCompactionModel(provider.id, model.safeId())
                        }
                    )
                }
            }
        }
        sheet.show()
    }

    private fun createFollowAssistantCompactionModelCard(
        dialog: BottomSheetDialog
    ): LinearLayout {
        val selected = AiConfig.contextCompactionProviderId.isBlank() ||
            AiConfig.contextCompactionModelId.isBlank()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = 18.dpToPx().toFloat()
                setColor(ContextCompat.getColor(requireContext(), R.color.ng_surface_card))
            }
            setPadding(14.dpToPx(), 14.dpToPx(), 14.dpToPx(), 14.dpToPx())
            setOnClickListener {
                AiConfig.followAssistantForContextCompaction()
                refreshModelSettings()
                dialog.dismiss()
            }
            addView(TextView(requireContext()).apply {
                text = getString(R.string.ai_context_compaction_model_follow)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
                typeface = Typeface.DEFAULT_BOLD
                textSize = 16f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
            addView(ImageView(requireContext()).apply {
                if (selected) {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(accentColor)
                }
            }, LinearLayout.LayoutParams(32.dpToPx(), 32.dpToPx()))
        }
    }

    private fun showAssistantContextWindowDialog() {
        val options = AiConfig.ASSISTANT_CONTEXT_WINDOW_OPTIONS
        val labels = options.map(::contextWindowLabel)
        showDiscreteScaleDialog(
            title = getString(R.string.ai_assistant_context_window_dialog_title),
            description = getString(R.string.ai_assistant_context_window_dialog_desc),
            iconRes = R.drawable.ic_ai_context_menu,
            labels = labels,
            selectedIndex = options.indexOf(AiConfig.assistantContextWindowTokens).coerceAtLeast(0)
        ) { index ->
            options.getOrNull(index)?.let { value ->
                AiConfig.assistantContextWindowTokens = value
                refreshModelSettings()
            }
        }
    }

    private fun showContextCompactionThresholdDialog() {
        val options = AiConfig.CONTEXT_COMPACTION_THRESHOLD_OPTIONS
        val labels = options.map(Int::toString)
        val currentLabels = options.map { value ->
            if (value == 0) getString(R.string.ai_context_compaction_off) else "$value%"
        }
        showDiscreteScaleDialog(
            title = getString(R.string.ai_context_compaction_threshold_dialog_title),
            description = getString(R.string.ai_context_compaction_threshold_dialog_desc),
            iconRes = R.drawable.ic_read_aloud_speed,
            labels = labels,
            currentLabels = currentLabels,
            selectedIndex = options.indexOf(AiConfig.contextCompactionThresholdPercent)
                .coerceAtLeast(0),
            tintIcon = { index -> options.getOrNull(index) != 0 }
        ) { index ->
            options.getOrNull(index)?.let { value ->
                AiConfig.contextCompactionThresholdPercent = value
                refreshModelSettings()
            }
        }
    }

    private fun showReadAloudStoryboardReasoningDialog() {
        val selected = selectedReadAloudStoryboardModel()
        if (selected == null) {
            Toast.makeText(
                requireContext(),
                R.string.ai_read_aloud_reasoning_select_model_first,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!selected.model.supportsReasoning()) {
            Toast.makeText(
                requireContext(),
                R.string.ai_read_aloud_reasoning_unsupported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showReasoningLevelDialog(
            title = getString(R.string.ai_read_aloud_reasoning_title),
            description = getString(R.string.ai_read_aloud_reasoning_desc),
            currentLevel = AiConfig.readAloudStoryboardReasoningLevel,
            iconTintWhenOff = true
        ) { level ->
            AiConfig.readAloudStoryboardReasoningLevel = level
            refreshModelSettings()
            refreshMain()
        }
    }

    private fun showReadAloudStoryboardPreloadDialog() {
        NumberPickerDialog(requireContext())
            .setTitle(getString(R.string.ai_read_aloud_storyboard_preload_count))
            .setMinValue(AiConfig.MIN_READ_ALOUD_STORYBOARD_PRELOAD_COUNT)
            .setMaxValue(AiConfig.MAX_READ_ALOUD_STORYBOARD_PRELOAD_COUNT)
            .setValue(AiConfig.readAloudStoryboardPreloadCount)
            .show { value ->
                AiConfig.readAloudStoryboardPreloadCount = value
                refreshModelSettings()
                refreshMain()
            }
    }

    private fun showAssistantReasoningDialog() {
        val selected = selectedAssistantModel()
        if (selected == null) {
            Toast.makeText(
                requireContext(),
                R.string.ai_assistant_reasoning_select_model_first,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!selected.model.supportsReasoning()) {
            Toast.makeText(
                requireContext(),
                R.string.ai_assistant_reasoning_unsupported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showReasoningLevelDialog(
            title = getString(R.string.ai_assistant_reasoning_title),
            description = getString(R.string.ai_assistant_reasoning_desc),
            currentLevel = AiConfig.assistantReasoningLevel,
            iconTintWhenOff = false
        ) { level ->
            AiConfig.assistantReasoningLevel = level
            refreshModelSettings()
            refreshMain()
        }
    }

    private fun showReasoningLevelDialog(
        title: String,
        description: String,
        currentLevel: AiReasoningLevel,
        iconTintWhenOff: Boolean,
        onLevelChanged: (AiReasoningLevel) -> Unit
    ) {
        val levels = AiReasoningLevel.entries.toList()
        val labels = levels.map { it.displayName() }
        showDiscreteScaleDialog(
            title = title,
            description = description,
            iconRes = R.drawable.ic_ai_capability_reasoning,
            labels = labels,
            selectedIndex = levels.indexOf(currentLevel).coerceAtLeast(0),
            tintIcon = { index ->
                iconTintWhenOff || levels.getOrNull(index) != AiReasoningLevel.OFF
            }
        ) { index ->
            levels.getOrNull(index)?.let(onLevelChanged)
        }
    }

    private fun showDiscreteScaleDialog(
        title: String,
        description: String,
        iconRes: Int,
        labels: List<String>,
        currentLabels: List<String> = labels,
        selectedIndex: Int,
        tintIcon: (Int) -> Boolean = { true },
        onSelectedIndexChanged: (Int) -> Unit
    ) {
        if (labels.isEmpty()) return
        val initialIndex = selectedIndex.coerceIn(0, labels.lastIndex)
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
            text = title
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 20f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(requireContext()).apply {
            text = description
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8.dpToPx(), 0, 22.dpToPx())
        })
        val currentLabel = TextView(requireContext()).apply {
            text = currentLabels.getOrElse(initialIndex) { labels[initialIndex] }
            setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface))
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val currentIcon = ImageView(requireContext()).apply {
            setImageResource(iconRes)
            imageTintList = if (tintIcon(initialIndex)) {
                ColorStateList.valueOf(accentColor)
            } else {
                null
            }
        }
        root.addView(currentIcon, LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()))
        root.addView(currentLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8.dpToPx()
            bottomMargin = 22.dpToPx()
        })
        val stepBar = ReasoningStepBar(requireContext()).apply {
            stepCount = labels.size
            this.selectedIndex = initialIndex
            stepColor = accentColor
            this.onSelectedIndexChanged = { index ->
                onSelectedIndexChanged(index)
                currentLabel.text = currentLabels.getOrElse(index) { labels[index] }
                currentIcon.imageTintList = if (tintIcon(index)) {
                    ColorStateList.valueOf(accentColor)
                } else {
                    null
                }
            }
        }
        root.addReasoningStepBar(stepBar)
        root.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            labels.forEach { label ->
                addView(TextView(requireContext()).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant))
                    textSize = if (labels.size >= 10) 11f else 13f
                    maxLines = 1
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

    private fun contextWindowLabel(tokens: Int): String {
        return if (tokens >= 1_000_000) {
            "${tokens / 1_000_000}M"
        } else {
            "${tokens / 1000}K"
        }
    }

    private fun AiProviderSetting.purifyEligibleModels(): List<AiModel> {
        val availableIds = effectiveAvailableModelIds().toSet()
        if (availableIds.isEmpty()) {
            return emptyList()
        }
        return displayModels()
            .filter { it.safeId() in availableIds }
            .filter { it.supportsChatText() }
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

    private fun LinearLayout.addReasoningStepBar(stepBar: ReasoningStepBar) {
        addView(stepBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            42.dpToPx()
        ))
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
            useCustomBalanceUrl = binding.switchCustomBalanceUrl.isChecked,
            streamResponseEnabled = binding.switchStreamResponse.isChecked
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

    private fun maybeAutoFetchModels() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val hasInitializedModels = provider.availableModelSelectionInitialized &&
            provider.displayModels().isNotEmpty()
        if (
            provider.apiKey.isBlank() ||
            hasInitializedModels ||
            !autoFetchedModelProviderIds.add(provider.id)
        ) {
            return
        }
        fetchModels()
    }

    private fun fetchModels() {
        val provider = saveCurrentProvider()
        if (provider == null) {
            binding.refreshModels.isRefreshing = false
            return
        }
        if (provider.apiKey.isBlank()) {
            binding.refreshModels.isRefreshing = false
            alert(getString(R.string.ai_fetch_models)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        binding.refreshModels.isRefreshing = true
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
                binding.refreshModels.isRefreshing = false
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
        modelAdapter.providerIconRes = provider?.iconRes() ?: R.drawable.ic_cfg_web
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

    private fun AiSkillDefinition.iconText(): String {
        return when (id) {
            "paragraph_purify" -> "段"
            "chapter_purify" -> "章"
            AiSkillRegistry.SKILL_BOOKSHELF_MANAGEMENT -> "书"
            else -> name.take(1).ifBlank { "技" }
        }
    }

    private fun AiSkillDefinition.safeFileName(): String {
        return id.ifBlank { name }
            .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
            .trim('_')
            .ifBlank { "skill" }
    }

    private fun AiSkillScope.displayName(): String {
        return when (this) {
            AiSkillScope.APP -> "APP"
            AiSkillScope.AGENT -> "Agent"
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
        var providerIconRes: Int = R.drawable.ic_cfg_web
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
                    createModelCapabilityTag(requireContext(), capability)
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

    private inner class AiSkillFileAdapter :
        RecyclerAdapter<SkillTreeRow, ItemAiSkillFileBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemAiSkillFileBinding {
            return ItemAiSkillFileBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiSkillFileBinding,
            item: SkillTreeRow,
            payloads: MutableList<Any>
        ) {
            binding.textPath.text = item.name
            binding.root.setPadding(
                (16 + item.depth * 20).dpToPx(),
                binding.root.paddingTop,
                4.dpToPx(),
                binding.root.paddingBottom
            )
            binding.root.setBackgroundColor(Color.TRANSPARENT)
            when (item) {
                is SkillTreeRow.Directory -> {
                    binding.imageExpand.isVisible = true
                    binding.imageExpand.rotation = if (item.expanded) 90f else 0f
                    binding.imageType.setImageResource(
                        if (item.expanded) R.drawable.ic_folder_open else R.drawable.ic_folder_outline
                    )
                    binding.textSize.isVisible = false
                    binding.buttonEdit.isVisible = false
                }

                is SkillTreeRow.File -> {
                    binding.imageExpand.isVisible = false
                    binding.imageExpand.rotation = 0f
                    binding.imageType.setImageResource(R.drawable.ic_code)
                    binding.textSize.isVisible = true
                    binding.textSize.text = formatMemorySize(item.size.toLong())
                    binding.buttonEdit.isVisible =
                        currentSkill?.builtIn == false && item.path == "SKILL.md"
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiSkillFileBinding) {
            binding.buttonEdit.setOnClickListener {
                (getItemByLayoutPosition(holder.layoutPosition) as? SkillTreeRow.File)
                    ?.let { item -> openSkillFile(item.path, edit = true) }
            }
        }
    }

    private inner class AiPromptAdapter :
        RecyclerAdapter<AiSkillDefinition, ItemAiPromptBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemAiPromptBinding {
            return ItemAiPromptBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiPromptBinding,
            item: AiSkillDefinition,
            payloads: MutableList<Any>
        ) {
            binding.textIcon.text = item.iconText()
            binding.textName.text = item.name
            binding.textSummary.text = item.summary
            binding.textScope.text = item.scope.displayName()
            binding.textScope.setBackgroundResource(
                when (item.scope) {
                    AiSkillScope.APP -> R.drawable.ng_bg_tag_neutral
                    AiSkillScope.AGENT -> R.drawable.ng_bg_tag_info
                }
            )
            binding.textScope.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    when (item.scope) {
                        AiSkillScope.APP -> R.color.tv_text_summary
                        AiSkillScope.AGENT -> R.color.ng_info
                    }
                )
            )
            binding.textCustom.isVisible = !item.builtIn
            binding.buttonMore.isVisible = !item.builtIn
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiPromptBinding) {
            binding.buttonMore.setOnClickListener {
                val skill = getItemByLayoutPosition(holder.layoutPosition) ?: return@setOnClickListener
                PopupMenu(requireContext(), binding.buttonMore).apply {
                    menu.add(0, 1, 0, R.string.ai_skill_export)
                    menu.add(0, 2, 1, R.string.delete)
                    setOnMenuItemClickListener { menuItem ->
                        currentSkill = skill
                        currentPrompt = skill.editablePrompt
                        when (menuItem.itemId) {
                            1 -> exportCurrentSkill()
                            2 -> confirmDeleteCurrentSkill()
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

}

private data class AiMemoryStats(
    val count: Int,
    val estimatedSize: Long
)
