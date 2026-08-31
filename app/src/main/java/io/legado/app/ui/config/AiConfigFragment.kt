package io.legado.app.ui.config

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentAiConfigBinding
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
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.createNgBottomDrawerComposeHost
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
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
        const val PAGE_PROVIDERS = "providers"
        const val PAGE_PROMPTS = "prompts"
        const val PAGE_PURIFY = "purify"
        const val PAGE_READ_ALOUD = "readAloud"
        const val PAGE_ASSISTANT = "assistant"
        private const val ARG_INITIAL_PAGE = "initialPage"
        private const val ARG_RETURN_TO_MENU = "returnToMenu"
        private const val MENU_EXPORT_SKILL = 0x4E470114

        fun newMenuPageInstance(initialPage: String): AiConfigFragment {
            return AiConfigFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_PAGE, initialPage)
                    putBoolean(ARG_RETURN_TO_MENU, true)
                }
            }
        }
    }

    private enum class Page {
        MAIN,
        PROVIDERS,
        DETAIL,
        PROMPTS,
        PROMPT_DETAIL,
        PURIFY_MODEL_SETTINGS,
        READ_ALOUD_MODEL_SETTINGS,
        ASSISTANT_MODEL_SETTINGS,
        PURIFY_SETTINGS
    }

    private enum class SkillCreationSource(
        val successMessageRes: Int,
        val failureMessageRes: Int,
    ) {
        ADD(
            successMessageRes = R.string.ai_skill_add_success,
            failureMessageRes = R.string.ai_skill_add_failed,
        ),
        IMPORT(
            successMessageRes = R.string.ai_skill_import_success,
            failureMessageRes = R.string.ai_skill_import_failed,
        ),
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
    private var currentPage = Page.MAIN
    private var currentProviderId: String? = null
    private var currentSkill: AiSkillDefinition? = null
    private var currentPrompt: AiPromptStore.Prompt? = null
    private val expandedSkillDirectories = linkedSetOf<String>()
    private var providerSearchQuery: String = ""
    private var providerScreenState by mutableStateOf(AiProviderListScreenState())
    private var providerFormScreenState by mutableStateOf(AiProviderFormScreenState())
    private var providerDetailScreenState by mutableStateOf(AiProviderDetailScreenState())
    private var aiMenuScreenState by mutableStateOf(AiConfigMenuScreenState())
    private var skillListScreenItems by mutableStateOf<List<AiSkillListItemUiModel>>(emptyList())
    private var skillDetailScreenRows by mutableStateOf<List<AiSkillFileRowUiModel>>(emptyList())
    private var purifyModelSettingsScreenState by mutableStateOf(
        AiPurifyModelSettingsScreenState()
    )
    private var readAloudModelSettingsScreenState by mutableStateOf(
        AiReadAloudModelSettingsScreenState()
    )
    private var assistantModelSettingsScreenState by mutableStateOf(
        AiAssistantModelSettingsScreenState()
    )
    private var purifySettingsScreenState by mutableStateOf(AiPurifySettingsScreenState())
    private var showDisabledProviders = LocalConfig.aiProviderListShowDisabled
    private var modelSearchQuery: String = ""
    private var providerDetailTab = ProviderDetailTab.CONFIG
    private val autoFetchedModelProviderIds = hashSetOf<String>()
    private var requestJob: Job? = null
    private var skillSummaryJob: Job? = null
    private var skipNextResumeRefresh = false
    private var entryPage = Page.MAIN
    private var returnToMenuOnEntryBack = false
    private val balanceNumberFormat by lazy { DecimalFormat("0.####") }
    private val importSkillFileLauncher = registerForActivityResult(
        SelectFileContract()
    ) { uri ->
        uri ?: return@registerForActivityResult
        importSkillFromUri(uri)
    }
    private val addSkillEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        result.data?.getStringExtra("text")?.let { content ->
            importSkillFromText(content, source = SkillCreationSource.ADD)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initAiMainCompose()
        initProviderList()
        initProviderDetailCompose()
        initSkillCompose()
        initFeatureSettingsCompose()
        val initialPage = arguments?.getString(ARG_INITIAL_PAGE)
            ?: activity?.intent?.getStringExtra(EXTRA_INITIAL_PAGE)
        returnToMenuOnEntryBack = arguments?.getBoolean(ARG_RETURN_TO_MENU) == true
        entryPage = when (initialPage) {
            PAGE_PROVIDERS -> Page.PROVIDERS
            PAGE_PROMPTS -> Page.PROMPTS
            PAGE_PURIFY -> Page.PURIFY_MODEL_SETTINGS
            PAGE_READ_ALOUD -> Page.READ_ALOUD_MODEL_SETTINGS
            PAGE_ASSISTANT -> Page.ASSISTANT_MODEL_SETTINGS
            else -> Page.MAIN
        }
        when (entryPage) {
            Page.PROVIDERS -> showProviderList()
            Page.PROMPTS -> showPromptList()
            Page.PURIFY_MODEL_SETTINGS -> showPurifyModelSettings()
            Page.READ_ALOUD_MODEL_SETTINGS -> showReadAloudModelSettings()
            Page.ASSISTANT_MODEL_SETTINGS -> showAssistantModelSettings()
            else -> showMain()
        }
        if (arguments?.containsKey(ARG_INITIAL_PAGE) != true && initialPage != null) {
            activity?.intent?.removeExtra(EXTRA_INITIAL_PAGE)
        }
        skipNextResumeRefresh = true
    }

    override fun onResume() {
        super.onResume()
        if (skipNextResumeRefresh) {
            skipNextResumeRefresh = false
        } else {
            refreshCurrentPage()
        }
    }

    override fun onDestroyView() {
        activity?.findViewById<TitleBar>(R.id.title_bar)?.let { titleBar ->
            titleBar.isVisible = true
            titleBar.setTemporarySolidSurface(false)
        }
        clearPageActions()
        super.onDestroyView()
        requestJob?.cancel()
        skillSummaryJob?.cancel()
        skillSummaryJob = null
        skipNextResumeRefresh = false
        waitDialog.dismiss()
    }


    private fun initProviderList() {
        binding.composeProviders.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiProviderListScreen(
                        state = providerScreenState,
                        onAction = ::handleProviderListAction
                    )
                }
            }
        }
    }

    override fun onConfigBackPressed(): Boolean {
        val page = visiblePage()
        if (page == Page.MAIN) {
            return false
        }
        if (returnToMenuOnEntryBack && page == entryPage) {
            return false
        }
        currentPage = page
        navigateBack()
        return true
    }

    private fun visiblePage(): Page {
        return when {
            binding.composeProviderDetail.isVisible -> Page.DETAIL
            binding.composeSkillDetail.isVisible -> Page.PROMPT_DETAIL
            binding.composeSkillList.isVisible -> Page.PROMPTS
            binding.composeAssistantModelSettings.isVisible -> Page.ASSISTANT_MODEL_SETTINGS
            binding.composeReadAloudModelSettings.isVisible -> Page.READ_ALOUD_MODEL_SETTINGS
            binding.composePurifyModelSettings.isVisible -> Page.PURIFY_MODEL_SETTINGS
            binding.composePurifySettings.isVisible -> Page.PURIFY_SETTINGS
            binding.composeProviders.isVisible -> Page.PROVIDERS
            else -> Page.MAIN
        }
    }

    private fun navigateBack() {
        when (currentPage) {
            Page.DETAIL -> showProviderList()
            Page.PROMPT_DETAIL -> showPromptList()
            Page.PROVIDERS -> showMain()
            Page.PROMPTS -> showMain()
            Page.PURIFY_MODEL_SETTINGS -> showMain()
            Page.READ_ALOUD_MODEL_SETTINGS -> showMain()
            Page.ASSISTANT_MODEL_SETTINGS -> showMain()
            Page.PURIFY_SETTINGS -> showPurifyModelSettings()
            Page.MAIN -> Unit
        }
    }

    private fun setPageTitle(title: CharSequence) {
        clearPageActions()
        activity?.title = title
        requireActivity().findViewById<TitleBar>(R.id.title_bar)?.let { titleBar ->
            titleBar.isVisible = true
            titleBar.title = title
        }
    }

    private fun setPageTitle(resId: Int) {
        setPageTitle(getString(resId))
    }

    private fun setProviderFloatingChrome(enabled: Boolean) {
        setSharedTitleBarVisible(!enabled)
    }

    private fun setSharedTitleBarVisible(visible: Boolean) {
        activity?.findViewById<TitleBar>(R.id.title_bar)?.isVisible = visible
    }

    private fun configureSkillDetailPageActions(skill: AiSkillDefinition) {
        if (skill.builtIn) return
        val titleBar = activity?.findViewById<TitleBar>(R.id.title_bar) ?: return
        titleBar.menu.add(
            Menu.NONE,
            MENU_EXPORT_SKILL,
            0,
            getString(R.string.ai_skill_export),
        ).apply {
            setIcon(R.drawable.ic_export)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                exportCurrentSkill()
                true
            }
        }
        titleBar.setTemporarySolidSurface(true)
    }

    private fun toggleShowDisabledProviders() {
        showDisabledProviders = !showDisabledProviders
        LocalConfig.aiProviderListShowDisabled = showDisabledProviders
        refreshProviders()
    }

    private fun clearPageActions() {
        activity?.findViewById<TitleBar>(R.id.title_bar)?.let { titleBar ->
            titleBar.toolbar.setOnMenuItemClickListener(null)
            titleBar.menu.clear()
        }
    }

    private fun showMain() {
        currentPage = Page.MAIN
        setFeatureComposePage(Page.MAIN)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_setting)
        refreshMain()
    }

    private fun showProviderList() {
        currentPage = Page.PROVIDERS
        setFeatureComposePage(Page.PROVIDERS)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_provider_menu)
        setProviderFloatingChrome(true)
        refreshProviders()
    }

    private fun showDetail(
        providerId: String,
        tab: ProviderDetailTab = ProviderDetailTab.CONFIG
    ) {
        currentPage = Page.DETAIL
        setFeatureComposePage(Page.DETAIL)
        currentProviderId = providerId
        currentSkill = null
        currentPrompt = null
        providerDetailTab = tab
        refreshCurrentDetail()
    }

    private fun showModelDetail(modelId: String) {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val model = provider.displayModels().firstOrNull { it.safeId() == modelId } ?: return
        showModelEditDialog(provider, model)
    }

    private fun showPromptList() {
        currentPage = Page.PROMPTS
        setFeatureComposePage(Page.PROMPTS)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_prompt_menu)
        setSharedTitleBarVisible(false)
        refreshPrompts()
    }

    private fun showSkillDetail(skill: AiSkillDefinition) {
        currentPage = Page.PROMPT_DETAIL
        setFeatureComposePage(Page.PROMPT_DETAIL)
        currentProviderId = null
        currentSkill = skill
        currentPrompt = skill.editablePrompt
        expandedSkillDirectories.clear()
        setPageTitle(skill.name)
        configureSkillDetailPageActions(skill)
        refreshPromptDetail()
    }

    private fun showPurifyModelSettings() {
        currentPage = Page.PURIFY_MODEL_SETTINGS
        setFeatureComposePage(Page.PURIFY_MODEL_SETTINGS)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_purify)
        refreshModelSettings()
    }

    private fun showReadAloudModelSettings() {
        currentPage = Page.READ_ALOUD_MODEL_SETTINGS
        setFeatureComposePage(Page.READ_ALOUD_MODEL_SETTINGS)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_read_aloud)
        refreshModelSettings()
    }

    private fun showAssistantModelSettings() {
        currentPage = Page.ASSISTANT_MODEL_SETTINGS
        setFeatureComposePage(Page.ASSISTANT_MODEL_SETTINGS)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_assistant)
        refreshModelSettings()
    }

    private fun showPurifySettings() {
        currentPage = Page.PURIFY_SETTINGS
        setFeatureComposePage(Page.PURIFY_SETTINGS)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_purify_settings)
        refreshPurifySettings()
    }

    private fun setFeatureComposePage(page: Page?) {
        binding.composeAiMain.isVisible = page == Page.MAIN
        binding.composeProviders.isVisible = page == Page.PROVIDERS
        binding.composeProviderDetail.isVisible = page == Page.DETAIL
        binding.composeSkillList.isVisible = page == Page.PROMPTS
        binding.composeSkillDetail.isVisible = page == Page.PROMPT_DETAIL
        binding.composePurifyModelSettings.isVisible = page == Page.PURIFY_MODEL_SETTINGS
        binding.composeReadAloudModelSettings.isVisible = page == Page.READ_ALOUD_MODEL_SETTINGS
        binding.composeAssistantModelSettings.isVisible = page == Page.ASSISTANT_MODEL_SETTINGS
        binding.composePurifySettings.isVisible = page == Page.PURIFY_SETTINGS
        activity?.findViewById<TitleBar>(R.id.title_bar)
            ?.setTemporarySolidSurface(page == Page.PROMPT_DETAIL)
    }

    private fun refreshCurrentPage() {
        when (currentPage) {
            Page.MAIN -> refreshMain()
            Page.PROVIDERS -> refreshProviders()
            Page.DETAIL -> refreshCurrentDetail()
            Page.PROMPTS -> refreshPrompts()
            Page.PROMPT_DETAIL -> refreshPromptDetail()
            Page.PURIFY_MODEL_SETTINGS -> refreshModelSettings()
            Page.READ_ALOUD_MODEL_SETTINGS -> refreshModelSettings()
            Page.ASSISTANT_MODEL_SETTINGS -> refreshModelSettings()
            Page.PURIFY_SETTINGS -> refreshPurifySettings()
        }
    }

    private fun showOperationPermissionDialog() {
        val modes = AiOperationPermissionMode.entries.toTypedArray()
        val dialog = ComponentDialog(requireContext())
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AiOperationPermissionDialogContent(
                        title = getString(R.string.ai_operation_permission),
                        options = modes.map { mode ->
                            AiOperationPermissionOptionUiModel(
                                title = operationPermissionModeTitle(mode),
                                summary = operationPermissionModeSummary(mode),
                                selected = AiConfig.operationPermissionMode == mode,
                            )
                        },
                        onSelect = { index ->
                            modes.getOrNull(index)?.let { mode ->
                                AiConfig.operationPermissionMode = mode
                                dialog.dismiss()
                                refreshMain()
                                refreshModelSettings()
                            }
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
        dialog.applyNgWindow()
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
        refreshSkillSummary()
        refreshAiMemorySummary()
        aiMenuScreenState = AiConfigMenuScreenState(
            providerSummary = getString(
                R.string.ai_provider_menu_summary,
                providers.size.toString(),
            ),
            skillSummary = aiMenuScreenState.skillSummary,
            chatFabEnabled = AiConfig.chatFabEnabled,
            chatFabSummary = getString(
                if (AiConfig.chatFabEnabled) {
                    R.string.ai_chat_fab_summary_on
                } else {
                    R.string.ai_chat_fab_summary_off
                }
            ),
            purifySummary = getString(
                R.string.ai_model_function_summary,
                purifyModelSummaryText(providers),
                purifyReasoningSummaryText(providers),
            ),
            assistantSummary = getString(
                R.string.ai_model_function_summary,
                assistantModelSummaryText(providers),
                assistantReasoningSummaryText(providers),
            ),
            readAloudSummary = getString(
                R.string.ai_model_function_summary,
                readAloudStoryboardModelSummaryText(providers),
                readAloudStoryboardReasoningSummaryText(providers),
            ),
        )
    }

    private fun refreshSkillSummary() {
        skillSummaryJob?.cancel()
        skillSummaryJob = viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                visibleAiSkills().size
            }
            if (view == null) return@launch
            aiMenuScreenState = aiMenuScreenState.copy(
                skillSummary = getString(
                    R.string.ai_prompt_menu_summary,
                    count.toString(),
                )
            )
        }
    }

    private fun refreshAiMemorySummary() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                loadAiMemoryStats()
            }
            if (!isAdded) return@launch
            val summary = if (AiConfig.memoryEnabled) {
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
            assistantModelSettingsScreenState = assistantModelSettingsScreenState.copy(
                memoryEnabled = AiConfig.memoryEnabled,
                memorySummary = summary,
            )
        }
    }

    private fun showAiMemoryDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) {
                loadAiMemoryStats()
            }
            if (!isAdded) return@launch
            val dialog = ComponentDialog(requireContext())
            val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    NgAppTheme(updateSystemBars = false) {
                        AiMemoryDialogContent(
                            title = getString(R.string.ai_memory),
                            summary = getString(
                                if (AiConfig.memoryEnabled) {
                                    R.string.ai_memory_summary_on
                                } else {
                                    R.string.ai_memory_summary_off
                                }
                            ),
                            countLabel = getString(R.string.ai_memory_count),
                            countValue = stats.count.toString(),
                            sizeLabel = getString(R.string.ai_memory_size),
                            sizeValue = formatMemorySize(stats.estimatedSize),
                            clearEnabled = stats.count > 0,
                            cancelText = getString(R.string.dialog_cancel),
                            clearText = getString(R.string.ai_memory_clear),
                            onCancel = dialog::dismiss,
                            onClear = { confirmClearAiMemory(dialog) },
                        )
                    }
                }
            }
            dialog.setContentView(composeView)
            dialog.show()
            dialog.applyNgWindow()
        }
    }

    private fun confirmClearAiMemory(parentDialog: Dialog) {
        showAiClassicDialog(
            title = getString(R.string.ai_memory_clear),
            message = getString(R.string.ai_memory_clear_confirm),
            cancelText = getString(R.string.no),
            onConfirm = {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        appDb.agentMemoryDao.clearAll()
                    }
                    parentDialog.dismiss()
                    refreshAiMemorySummary()
                    Toast.makeText(requireContext(), R.string.ai_memory_cleared, Toast.LENGTH_SHORT).show()
                }
            },
        )
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
        val providers = ConfigListVisibilitySupport.visibleItems(
            allItems = allProviders,
            showDisabled = showDisabledProviders,
            isEnabled = AiProviderSetting::enabled
        )
            .filter {
                matchesAiProviderName(
                    name = it.name,
                    query = providerSearchQuery
                )
            }
        providerScreenState = providerScreenState.copy(
            query = providerSearchQuery,
            listState = NgListState.Content(
                providers.map { provider ->
                    AiProviderListItemUiModel(
                        id = provider.id,
                        name = provider.name,
                        iconRes = provider.iconRes(),
                        enabled = provider.enabled,
                        modelCountText = getString(
                            R.string.ai_model_list_count,
                            provider.visibleModelCount().toString()
                        ),
                        reorderable = true,
                        deletable = !provider.builtIn
                    )
                }
            ),
            isRefreshing = false,
            showDisabled = showDisabledProviders,
        )
    }

    private fun handleProviderListAction(action: AiProviderListScreenAction) {
        when (action) {
            is AiProviderListScreenAction.QueryChanged -> {
                providerSearchQuery = action.query
                refreshProviders()
            }

            is AiProviderListScreenAction.SearchSubmitted -> Unit
            is AiProviderListScreenAction.ProviderClicked -> showDetail(action.providerId)
            AiProviderListScreenAction.Back -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            AiProviderListScreenAction.AddOpenAiProvider -> {
                addProvider(AiProviderType.OPENAI)
            }
            AiProviderListScreenAction.AddClaudeProvider -> {
                addProvider(AiProviderType.CLAUDE)
            }
            AiProviderListScreenAction.ToggleShowDisabled -> {
                toggleShowDisabledProviders()
            }
            is AiProviderListScreenAction.ReorderCommitted -> {
                commitProviderOrder(action.orderedProviderIds)
            }

            is AiProviderListScreenAction.DeleteRequested -> {
                AiProviderStore.provider(action.providerId)?.let { provider ->
                    confirmDeleteProvider(
                        provider = provider,
                        onCancel = ::refreshProviders,
                        onDeleted = ::refreshProviders
                    )
                }
            }

            AiProviderListScreenAction.RetryRequested,
            AiProviderListScreenAction.RefreshRequested -> refreshProviders()
        }
    }

    private fun handleProviderFormAction(action: AiProviderFormScreenAction) {
        if (currentPage != Page.DETAIL) return
        when (action) {
            is AiProviderFormScreenAction.FieldChanged -> {
                providerFormScreenState = providerFormScreenState.withField(
                    action.field,
                    action.value
                )
                val provider = saveCurrentProvider(
                    updateHeader = action.field == AiProviderFormField.NAME
                )
                if (action.field == AiProviderFormField.API_KEY && provider != null) {
                    refreshModelList(provider)
                }
            }

            is AiProviderFormScreenAction.ToggleChanged -> {
                providerFormScreenState = providerFormScreenState.withToggle(
                    action.toggle,
                    action.checked
                )
                saveCurrentProvider()
            }

            AiProviderFormScreenAction.TestConnectionRequested -> testConnection()
            AiProviderFormScreenAction.QueryBalanceRequested -> queryBalance()
            AiProviderFormScreenAction.DeleteRequested -> confirmDeleteCurrentProvider()
        }
    }

    private fun commitProviderOrder(orderedProviderIds: List<String>) {
        if (providerSearchQuery.isNotBlank()) return
        val allProviders = AiProviderStore.providers()
        val visibleProviders = ConfigListVisibilitySupport.visibleItems(
            allItems = allProviders,
            showDisabled = showDisabledProviders,
            isEnabled = AiProviderSetting::enabled
        )
        val visibleIds = visibleProviders.map(AiProviderSetting::id)
        if (orderedProviderIds == visibleIds) return
        if (orderedProviderIds.size != visibleIds.size ||
            orderedProviderIds.toSet().size != orderedProviderIds.size ||
            orderedProviderIds.toSet() != visibleIds.toSet()
        ) {
            refreshProviders()
            return
        }
        val providersById = visibleProviders.associateBy(AiProviderSetting::id)
        val reorderedProviders = orderedProviderIds.mapNotNull(providersById::get)
        AiProviderStore.saveProviders(
            ConfigListVisibilitySupport.mergeVisibleOrder(
                allItems = allProviders,
                reorderedVisibleItems = reorderedProviders,
                showDisabled = showDisabledProviders,
                isEnabled = AiProviderSetting::enabled
            )
        )
        refreshMain()
        refreshProviders()
    }

    private fun refreshCurrentDetail() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        setPageTitle(provider.name)
        providerFormScreenState = provider.toProviderFormScreenState(
            provider.type.localizedDisplayName()
        )
        providerDetailScreenState = providerDetailScreenState.copy(
            providerName = provider.name,
            providerIconRes = provider.iconRes(),
            selectedTab = providerDetailTab.ordinal,
        )
        refreshModelList(provider)
        showProviderDetailTab(providerDetailTab)
    }

    private fun showProviderDetailTab(tab: ProviderDetailTab) {
        providerDetailTab = tab
        providerDetailScreenState = providerDetailScreenState.copy(selectedTab = tab.ordinal)
        setProviderFloatingChrome(tab == ProviderDetailTab.MODELS)
        if (tab == ProviderDetailTab.MODELS) {
            binding.composeProviderDetail.clearFocus()
            binding.composeProviderDetail.hideSoftInput()
            maybeAutoFetchModels()
        }
    }

    private fun showModelEditDialog(provider: AiProviderSetting, model: AiModel) {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(
            requireContext().createNgBottomDrawerComposeHost(
                fillMaxHeight = true,
            ) {
                AiModelEditSheet(
                    model = model,
                    onSave = { draft ->
                        saveModelEdit(provider, model, draft)
                        dialog.dismiss()
                    },
                )
            }
        )
        configureModelEditSheet(dialog)
        dialog.show()
    }

    private fun initFeatureSettingsCompose() {
        binding.composePurifyModelSettings.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiPurifyModelSettingsScreen(
                        state = purifyModelSettingsScreenState,
                        onAction = ::handlePurifyModelSettingsAction,
                    )
                }
            }
        }
        binding.composeReadAloudModelSettings.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiReadAloudModelSettingsScreen(
                        state = readAloudModelSettingsScreenState,
                        onAction = ::handleReadAloudModelSettingsAction,
                    )
                }
            }
        }
        binding.composeAssistantModelSettings.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiAssistantModelSettingsScreen(
                        state = assistantModelSettingsScreenState,
                        onAction = ::handleAssistantModelSettingsAction,
                    )
                }
            }
        }
        binding.composePurifySettings.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiPurifySettingsScreen(
                        state = purifySettingsScreenState,
                        onAction = ::handlePurifySettingsAction,
                    )
                }
            }
        }
    }

    private fun initProviderDetailCompose() {
        binding.composeProviderDetail.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiProviderDetailScreen(
                        state = providerDetailScreenState,
                        formState = providerFormScreenState,
                        onFormAction = ::handleProviderFormAction,
                        onAction = ::handleProviderDetailAction,
                    )
                }
            }
        }
    }

    private fun initSkillCompose() {
        binding.composeSkillList.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiSkillListScreen(
                        items = skillListScreenItems,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onAddSkill = ::showManualSkillEditor,
                        onImportLocal = {
                            importSkillFileLauncher.launch(
                                arrayOf("text/*", "text/markdown", "application/octet-stream")
                            )
                        },
                        onImportUrl = ::showImportSkillUrlDialog,
                        onAction = ::handleSkillListAction,
                    )
                }
            }
        }
        binding.composeSkillDetail.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiSkillDetailScreen(
                        rows = skillDetailScreenRows,
                        onAction = ::handleSkillDetailAction,
                    )
                }
            }
        }
    }

    private fun initAiMainCompose() {
        binding.composeAiMain.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    AiConfigMenuScreen(
                        state = aiMenuScreenState,
                        onOpenPage = ::handleAiMainOpenPage,
                        onChatFabChanged = { enabled ->
                            AiConfig.chatFabEnabled = enabled
                            refreshMain()
                        },
                    )
                }
            }
        }
    }

    private fun handleAiMainOpenPage(page: String) {
        when (page) {
            PAGE_PROVIDERS -> showProviderList()
            PAGE_PROMPTS -> showPromptList()
            PAGE_PURIFY -> showPurifyModelSettings()
            PAGE_READ_ALOUD -> showReadAloudModelSettings()
            PAGE_ASSISTANT -> showAssistantModelSettings()
        }
    }

    private fun handleSkillListAction(action: AiSkillListAction) {
        when (action) {
            is AiSkillListAction.OpenSkill -> {
                showSkillDetail(action.skill)
            }
            is AiSkillListAction.DeleteSkill -> {
                selectSkillForAction(action.skill)
                confirmDeleteCurrentSkill()
            }
        }
    }

    private fun selectSkillForAction(skill: AiSkillDefinition) {
        currentSkill = skill
        currentPrompt = skill.editablePrompt
    }

    private fun handleSkillDetailAction(action: AiSkillDetailAction) {
        when (action) {
            is AiSkillDetailAction.ToggleDirectory -> toggleSkillDirectory(action.path)
            is AiSkillDetailAction.OpenFile -> openSkillFile(action.path, edit = false)
            is AiSkillDetailAction.EditFile -> openSkillFile(action.path, edit = true)
        }
    }

    private fun handleProviderDetailAction(action: AiProviderDetailAction) {
        when (action) {
            is AiProviderDetailAction.TabSelected -> {
                val tab = ProviderDetailTab.entries[action.index]
                showProviderDetailTab(tab)
            }
            is AiProviderDetailAction.ModelQueryChanged -> {
                modelSearchQuery = action.query
                refreshModelList(currentProviderId?.let { AiProviderStore.provider(it) })
            }
            AiProviderDetailAction.RefreshModels -> fetchModels()
            AiProviderDetailAction.ToggleVisibleModelSelection -> toggleVisibleModelSelection()
            AiProviderDetailAction.Back -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            is AiProviderDetailAction.EditModel -> showModelDetail(action.modelId)
            is AiProviderDetailAction.ToggleModel -> {
                val provider = currentProviderId?.let(AiProviderStore::provider) ?: return
                val model = provider.displayModels().firstOrNull {
                    it.safeId() == action.modelId
                } ?: return
                toggleAvailableModel(model, action.selected)
            }
        }
    }

    private fun handlePurifyModelSettingsAction(action: AiPurifyModelSettingsAction) {
        when (action) {
            AiPurifyModelSettingsAction.SelectModel -> showPurifyModelSelectDialog()
            AiPurifyModelSettingsAction.SelectReasoning -> showPurifyReasoningDialog()
            AiPurifyModelSettingsAction.OpenSettings -> showPurifySettings()
        }
    }

    private fun handleReadAloudModelSettingsAction(action: AiReadAloudModelSettingsAction) {
        when (action) {
            AiReadAloudModelSettingsAction.SelectModel -> {
                showReadAloudStoryboardModelSelectDialog()
            }
            AiReadAloudModelSettingsAction.SelectReasoning -> {
                showReadAloudStoryboardReasoningDialog()
            }
            AiReadAloudModelSettingsAction.SelectPreloadCount -> {
                showReadAloudStoryboardPreloadDialog()
            }
        }
    }

    private fun handleAssistantModelSettingsAction(action: AiAssistantModelSettingsAction) {
        when (action) {
            AiAssistantModelSettingsAction.SelectModel -> showAssistantModelSelectDialog()
            AiAssistantModelSettingsAction.SelectReasoning -> showAssistantReasoningDialog()
            AiAssistantModelSettingsAction.SelectCompactionModel -> {
                showContextCompactionModelSelectDialog()
            }
            AiAssistantModelSettingsAction.SelectContextWindow -> {
                showAssistantContextWindowDialog()
            }
            AiAssistantModelSettingsAction.SelectCompactionThreshold -> {
                showContextCompactionThresholdDialog()
            }
            is AiAssistantModelSettingsAction.InternalMcpChanged -> {
                AiConfig.internalMcpEnabled = action.enabled
                refreshMain()
                refreshModelSettings()
            }
            is AiAssistantModelSettingsAction.MemoryChanged -> {
                AiConfig.memoryEnabled = action.enabled
                refreshMain()
                refreshModelSettings()
                refreshAiMemorySummary()
            }
            AiAssistantModelSettingsAction.OpenMemory -> showAiMemoryDialog()
            AiAssistantModelSettingsAction.OpenOperationPermission -> {
                showOperationPermissionDialog()
            }
        }
    }

    private fun handlePurifySettingsAction(action: AiPurifySettingsAction) {
        when (action) {
            is AiPurifySettingsAction.ParagraphAutoApplyChanged -> {
                AiConfig.purifyAutoApply = action.enabled
                refreshPurifySettings()
                refreshMain()
                refreshModelSettings()
            }
            is AiPurifySettingsAction.ParagraphInterceptChanged -> {
                AiConfig.purifyExceptionIntercept = action.enabled
                refreshPurifySettings()
                refreshMain()
            }
            is AiPurifySettingsAction.ChapterAutoApplyChanged -> {
                AiConfig.purifyChapterAutoApply = action.enabled
                refreshPurifySettings()
                refreshMain()
                refreshModelSettings()
            }
            is AiPurifySettingsAction.ChapterInterceptChanged -> {
                AiConfig.purifyChapterExceptionIntercept = action.enabled
                refreshPurifySettings()
                refreshMain()
            }
            is AiPurifySettingsAction.ChapterRuleTypeChanged -> {
                when (action.type) {
                    AiPurifyRuleType.TYPO -> AiConfig.purifyChapterRuleTypo = action.enabled
                    AiPurifyRuleType.NOISE -> AiConfig.purifyChapterRuleNoise = action.enabled
                    AiPurifyRuleType.AD -> AiConfig.purifyChapterRuleAd = action.enabled
                }
                refreshPurifySettings()
                refreshMain()
            }
            is AiPurifySettingsAction.NumberChanged -> {
                updatePurifyNumberDraft(action.field, action.value)
            }
            is AiPurifySettingsAction.NumberFocusLost -> refreshPurifySettings()
        }
    }

    private fun updatePurifyNumberDraft(field: AiPurifyNumberField, value: String) {
        purifySettingsScreenState = when (field) {
            AiPurifyNumberField.PARAGRAPH_LIMIT -> {
                purifySettingsScreenState.copy(paragraphLimit = value)
            }
            AiPurifyNumberField.CHAPTER_CONCURRENCY -> {
                purifySettingsScreenState.copy(chapterConcurrency = value)
            }
            AiPurifyNumberField.CHAPTER_RETRY_COUNT -> {
                purifySettingsScreenState.copy(chapterRetryCount = value)
            }
            AiPurifyNumberField.CHAPTER_SEGMENT_LIMIT -> {
                purifySettingsScreenState.copy(chapterSegmentLimit = value)
            }
            AiPurifyNumberField.CHAPTER_SAMPLE_LIMIT -> {
                purifySettingsScreenState.copy(chapterSampleLimit = value)
            }
        }
        value.toIntOrNull()?.let { parsed ->
            when (field) {
                AiPurifyNumberField.PARAGRAPH_LIMIT -> AiConfig.purifyParagraphLimit = parsed
                AiPurifyNumberField.CHAPTER_CONCURRENCY -> {
                    AiConfig.purifyChapterConcurrencyLimit = parsed
                }
                AiPurifyNumberField.CHAPTER_RETRY_COUNT -> {
                    AiConfig.purifyChapterRetryCount = parsed
                }
                AiPurifyNumberField.CHAPTER_SEGMENT_LIMIT -> {
                    AiConfig.purifyChapterSegmentLimit = parsed
                }
                AiPurifyNumberField.CHAPTER_SAMPLE_LIMIT -> {
                    AiConfig.purifyChapterSampleLimit = parsed
                }
            }
            refreshMain()
            refreshModelSettings()
        }
    }

    private fun configureModelEditSheet(dialog: BottomSheetDialog) {
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.70f).toInt()
            }
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                isDraggable = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun saveModelEdit(
        provider: AiProviderSetting,
        original: AiModel,
        draft: AiModelEditDraft,
    ) {
        val updated = original.copy(
            displayName = draft.displayName.trim(),
            type = draft.type,
            inputModalities = modelInputModalitiesForDraft(draft.type, draft.inputModalities),
            outputModalities = modelOutputModalitiesForDraft(draft.type, draft.outputModalities),
            abilities = mergeModelAbilitiesForEdit(
                original = original,
                exposedAbilities = modelAbilitiesForDraft(draft.type, draft.abilities),
            ),
        )
        AiProviderStore.saveProvider(provider.copy(models = provider.models.updatedWithModel(updated)))
        refreshModelList(AiProviderStore.provider(provider.id))
        refreshMain()
    }

    private fun addProvider(type: AiProviderType) {
        val provider = AiProviderStore.createCustomProvider(type)
        if (providerSearchQuery.isNotBlank()) {
            providerSearchQuery = ""
        }
        refreshMain()
        showDetail(provider.id)
    }

    private fun refreshPrompts() {
        val skills = visibleAiSkills()
        skillListScreenItems = skills.map { skill ->
            AiSkillListItemUiModel(
                skill = skill,
                name = skill.name,
                summary = skill.summary,
                iconText = skill.iconText(),
                headerTags = buildList {
                    add(
                        NgStatusTagSpec(
                            text = skill.scope.displayName(),
                            variant = when (skill.scope) {
                                AiSkillScope.APP -> NgStatusTagVariant.NEUTRAL
                                AiSkillScope.AGENT -> NgStatusTagVariant.INFO
                            },
                            style = NgStatusTagStyle.COMPACT,
                        )
                    )
                    if (!skill.builtIn) {
                        add(
                            NgStatusTagSpec(
                                text = getString(R.string.ai_prompt_custom),
                                variant = NgStatusTagVariant.SUCCESS,
                                style = NgStatusTagStyle.COMPACT,
                            )
                        )
                    }
                },
            )
        }
    }

    private fun visibleAiSkills(): List<AiSkillDefinition> {
        return AiSkillRegistry.managementSkills()
    }

    private fun refreshPromptDetail() {
        currentSkill ?: return
        refreshSkillFileTree()
    }

    private fun refreshSkillFileTree() {
        val skill = currentSkill ?: return
        val rows = buildSkillTreeRows(skill.id)
        skillDetailScreenRows = rows.map { row ->
            when (row) {
                is SkillTreeRow.Directory -> AiSkillFileRowUiModel.Directory(
                    path = row.path,
                    name = row.name,
                    depth = row.depth,
                    expanded = row.expanded,
                )
                is SkillTreeRow.File -> AiSkillFileRowUiModel.File(
                    path = row.path,
                    name = row.name,
                    depth = row.depth,
                    sizeText = formatMemorySize(row.size.toLong()),
                    editable = !skill.builtIn && row.path == "SKILL.md",
                )
            }
        }
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

    private fun showManualSkillEditor() {
        val template = getString(R.string.ai_skill_content_template)
        addSkillEditorLauncher.launch(
            Intent(requireContext(), CodeEditActivity::class.java).apply {
                putExtra("text", template)
                putExtra("title", getString(R.string.ai_skill_add))
                putExtra("languageName", "text.html.markdown")
                putExtra("cursorPosition", template.indexOf("my-skill").coerceAtLeast(0))
                putExtra("returnUnchangedText", true)
                putExtra(CodeEditActivity.EXTRA_CONFIRM_SAVE_ON_EXIT, true)
            }
        )
    }

    private fun showImportSkillUrlDialog() {
        val dialog = ComponentDialog(requireContext())
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AiSkillLinkImportDialogContent(
                        onCancel = dialog::dismiss,
                        onConfirm = { url ->
                            dialog.dismiss()
                            importSkillFromUrl(url)
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
        dialog.applyNgWindow()
    }

    private fun showAiClassicDialog(
        title: String,
        message: String,
        cancelText: String? = null,
        confirmText: String = getString(android.R.string.ok),
        dismissBeforeConfirm: Boolean = false,
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        val dialog = ComponentDialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AiClassicDialogContent(
                        title = title,
                        message = message,
                        cancelText = cancelText,
                        confirmText = confirmText,
                        onCancel = {
                            onCancel()
                            dialog.dismiss()
                        },
                        onConfirm = {
                            if (dismissBeforeConfirm) dialog.dismiss()
                            onConfirm()
                            if (!dismissBeforeConfirm) dialog.dismiss()
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.applyNgWindow()
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

    private fun importSkillFromText(
        content: String,
        overwriteExisting: Boolean = false,
        source: SkillCreationSource = SkillCreationSource.IMPORT,
    ) {
        val result = runCatching { AiSkillRegistry.importFromText(content, overwriteExisting) }
        handleSkillImportResult(result, source) {
            importSkillFromText(
                content = content,
                overwriteExisting = true,
                source = source,
            )
        }
    }

    private fun handleSkillImportResult(
        result: Result<io.legado.app.data.entities.AiSkill>,
        source: SkillCreationSource = SkillCreationSource.IMPORT,
        overwriteAction: (() -> Unit)? = null
    ) {
        result.onSuccess { skill ->
            Toast.makeText(
                requireContext(),
                getString(source.successMessageRes, skill.name),
                Toast.LENGTH_SHORT
            ).show()
            refreshPrompts()
        }.onFailure { error ->
            if (error is AiSkillExistsException && overwriteAction != null) {
                showAiClassicDialog(
                    title = getString(R.string.ai_skill_overwrite_title),
                    message = getString(R.string.ai_skill_overwrite_message, error.skillId),
                    cancelText = getString(android.R.string.cancel),
                    confirmText = getString(android.R.string.ok),
                    onConfirm = overwriteAction,
                )
                return@onFailure
            }
            Toast.makeText(
                requireContext(),
                getString(source.failureMessageRes, error.message.orEmpty()),
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
        showAiClassicDialog(
            title = getString(R.string.ai_skill_delete_title),
            message = getString(R.string.ai_skill_delete_message, skill.name),
            cancelText = getString(android.R.string.cancel),
            confirmText = getString(android.R.string.ok),
            onConfirm = {
                if (AiSkillRegistry.deleteSkill(skill.id)) {
                    Toast.makeText(requireContext(), R.string.ai_skill_delete_done, Toast.LENGTH_SHORT)
                        .show()
                    showPromptList()
                }
            },
        )
    }

    private fun refreshPurifySettings() {
        purifySettingsScreenState = AiPurifySettingsScreenState(
            paragraphAutoApply = AiConfig.purifyAutoApply,
            paragraphAutoApplySummary = purifyAutoApplySummary(AiConfig.purifyAutoApply),
            paragraphIntercept = AiConfig.purifyExceptionIntercept,
            paragraphLimit = AiConfig.purifyParagraphLimit.toString(),
            chapterAutoApply = AiConfig.purifyChapterAutoApply,
            chapterAutoApplySummary = purifyAutoApplySummary(AiConfig.purifyChapterAutoApply),
            chapterIntercept = AiConfig.purifyChapterExceptionIntercept,
            chapterRuleTypo = AiConfig.purifyChapterRuleTypo,
            chapterRuleNoise = AiConfig.purifyChapterRuleNoise,
            chapterRuleAd = AiConfig.purifyChapterRuleAd,
            chapterConcurrency = AiConfig.purifyChapterConcurrencyLimit.toString(),
            chapterRetryCount = AiConfig.purifyChapterRetryCount.toString(),
            chapterSegmentLimit = AiConfig.purifyChapterSegmentLimit.toString(),
            chapterSampleLimit = AiConfig.purifyChapterSampleLimit.toString(),
        )
    }
    private fun purifyAutoApplySummary(enabled: Boolean): String {
        return getString(
            if (enabled) {
                R.string.ai_purify_auto_apply_summary_on
            } else {
                R.string.ai_purify_auto_apply_summary_off
            }
        )
    }

    private fun refreshModelSettings() {
        val reasoningEnabled = selectedPurifyModel()?.model?.supportsReasoning() == true
        val readAloudReasoningEnabled =
            selectedReadAloudStoryboardModel()?.model?.supportsReasoning() == true
        val assistantReasoningEnabled = selectedAssistantModel()?.model?.supportsReasoning() == true
        val purifySettingsSummary = getString(
            R.string.ai_purify_settings_summary,
            getString(if (AiConfig.purifyAutoApply) R.string.enabled else R.string.disabled),
            getString(if (AiConfig.purifyChapterAutoApply) R.string.enabled else R.string.disabled),
            AiConfig.purifyParagraphLimit.toString(),
            AiConfig.purifyChapterSegmentLimit.toString(),
            AiConfig.purifyChapterSampleLimit.toString(),
            AiConfig.purifyChapterConcurrencyLimit.toString(),
            AiConfig.purifyChapterRetryCount.toString(),
        )
        purifyModelSettingsScreenState = AiPurifyModelSettingsScreenState(
            modelSummary = purifyModelSummaryText(),
            reasoningSummary = purifyReasoningSummaryText(),
            settingsSummary = purifySettingsSummary,
            reasoningAvailable = reasoningEnabled,
        )
        readAloudModelSettingsScreenState = AiReadAloudModelSettingsScreenState(
            modelSummary = readAloudStoryboardModelSummaryText(),
            reasoningSummary = readAloudStoryboardReasoningSummaryText(),
            preloadSummary = getString(
                R.string.ai_read_aloud_storyboard_preload_summary,
                AiConfig.readAloudStoryboardPreloadCount,
            ),
            reasoningAvailable = readAloudReasoningEnabled,
        )
        assistantModelSettingsScreenState = AiAssistantModelSettingsScreenState(
            modelSummary = assistantModelSummaryText(),
            reasoningSummary = assistantReasoningSummaryText(),
            compactionModelSummary = contextCompactionModelSummaryText(),
            contextWindowSummary = getString(
                R.string.ai_assistant_context_window_summary,
                contextWindowLabel(AiConfig.assistantContextWindowTokens),
            ),
            compactionThresholdSummary = if (AiConfig.contextCompactionThresholdPercent == 0) {
                getString(R.string.ai_context_compaction_threshold_off)
            } else {
                getString(
                    R.string.ai_context_compaction_threshold_summary,
                    AiConfig.contextCompactionThresholdPercent,
                    AiConfig.assistantContextWindowTokens *
                        AiConfig.contextCompactionThresholdPercent / 100 / 1000,
                )
            },
            internalMcpEnabled = AiConfig.internalMcpEnabled,
            internalMcpSummary = getString(
                if (AiConfig.internalMcpEnabled) {
                    R.string.ai_internal_mcp_summary_on
                } else {
                    R.string.ai_internal_mcp_summary_off
                }
            ),
            memoryEnabled = AiConfig.memoryEnabled,
            memorySummary = assistantModelSettingsScreenState.memorySummary.ifBlank {
                getString(
                    if (AiConfig.memoryEnabled) {
                        R.string.ai_memory_summary_on
                    } else {
                        R.string.ai_memory_summary_off
                    }
                )
            },
            operationPermissionSummary = operationPermissionModeSummary(
                AiConfig.operationPermissionMode
            ),
            reasoningAvailable = assistantReasoningEnabled,
        )
    }

    private fun purifyModelSummaryText(
        providers: List<AiProviderSetting>? = null
    ): String {
        val selected = selectedPurifyModel(providers)
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

    private fun purifyReasoningSummaryText(
        providers: List<AiProviderSetting>? = null
    ): String {
        val selected = selectedPurifyModel(providers)
        return when {
            selected == null -> getString(R.string.ai_purify_reasoning_select_model_first)
            !selected.model.supportsReasoning() -> getString(R.string.ai_purify_reasoning_unsupported)
            else -> getString(
                R.string.ai_purify_reasoning_level_summary,
                AiConfig.purifyReasoningLevel.displayName()
            )
        }
    }

    private fun assistantModelSummaryText(
        providers: List<AiProviderSetting>? = null
    ): String {
        val selected = selectedAssistantModel(providers)
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

    private fun assistantReasoningSummaryText(
        providers: List<AiProviderSetting>? = null
    ): String {
        val selected = selectedAssistantModel(providers)
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

    private fun readAloudStoryboardModelSummaryText(
        providers: List<AiProviderSetting>? = null
    ): String {
        val selected = selectedReadAloudStoryboardModel(providers)
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

    private fun readAloudStoryboardReasoningSummaryText(
        providers: List<AiProviderSetting>? = null
    ): String {
        val selected = selectedReadAloudStoryboardModel(providers)
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

    private fun selectedPurifyModel(
        providers: List<AiProviderSetting>? = null
    ): PurifyModelOption? {
        val providerId = AiConfig.purifyProviderId
        val modelId = AiConfig.purifyModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return null
        }
        val provider = if (providers == null) {
            AiProviderStore.provider(providerId)
        } else {
            providers.firstOrNull { it.id == providerId }
        }?.takeIf { it.enabled } ?: return null
        val model = provider.purifyEligibleModels().firstOrNull { it.safeId() == modelId } ?: return null
        return PurifyModelOption(provider, model)
    }

    private fun selectedReadAloudStoryboardModel(
        providers: List<AiProviderSetting>? = null
    ): PurifyModelOption? {
        val providerId = AiConfig.readAloudStoryboardProviderId
        val modelId = AiConfig.readAloudStoryboardModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return null
        }
        val provider = if (providers == null) {
            AiProviderStore.provider(providerId)
        } else {
            providers.firstOrNull { it.id == providerId }
        }?.takeIf { it.enabled } ?: return null
        val model = provider.purifyEligibleModels().firstOrNull { it.safeId() == modelId }
            ?: return null
        return PurifyModelOption(provider, model)
    }

    private fun selectedAssistantModel(
        providers: List<AiProviderSetting>? = null
    ): AiAssistantConfigUi.AssistantModelOption? {
        if (providers == null) return AiAssistantConfigUi.selectedModel()
        val providerId = AiConfig.assistantProviderId
        val modelId = AiConfig.assistantModelId
        if (providerId.isBlank() || modelId.isBlank()) return null
        val provider = providers.firstOrNull { it.id == providerId }
            ?.takeIf { it.enabled }
            ?: return null
        val model = provider.assistantEligibleModels()
            .firstOrNull { it.safeId() == modelId }
            ?: return null
        return AiAssistantConfigUi.AssistantModelOption(provider, model)
    }

    private enum class AiModelSelectionTarget {
        PURIFY,
        READ_ALOUD,
        ASSISTANT,
        CONTEXT_COMPACTION,
    }

    private fun showComposeModelSelectionSheet(target: AiModelSelectionTarget) {
        val sourceProviders = when (target) {
            AiModelSelectionTarget.ASSISTANT -> AiProviderStore.providers().filter { provider ->
                provider.enabled && provider.assistantEligibleModels().isNotEmpty()
            }
            else -> purifyModelProviders()
        }
        val providers = sourceProviders.map { provider ->
            val models = when (target) {
                AiModelSelectionTarget.ASSISTANT -> provider.assistantEligibleModels()
                else -> provider.purifyEligibleModels()
            }
            AiModelSelectionProviderUiModel(
                id = provider.id,
                name = provider.name,
                iconRes = provider.iconRes(),
                models = models.map { model ->
                    AiModelSelectionItemUiModel(
                        id = model.safeId(),
                        name = model.displayName(),
                        searchAliases = listOf(model.safeName()),
                        iconRes = model.iconRes(provider.iconRes()),
                        capabilities = model.capabilityTags(),
                    )
                },
            )
        }
        val selectedProviderId = when (target) {
            AiModelSelectionTarget.PURIFY -> AiConfig.purifyProviderId
            AiModelSelectionTarget.READ_ALOUD -> AiConfig.readAloudStoryboardProviderId
            AiModelSelectionTarget.ASSISTANT -> AiConfig.assistantProviderId
            AiModelSelectionTarget.CONTEXT_COMPACTION -> AiConfig.contextCompactionProviderId
        }
        val selectedModelId = when (target) {
            AiModelSelectionTarget.PURIFY -> AiConfig.purifyModelId
            AiModelSelectionTarget.READ_ALOUD -> AiConfig.readAloudStoryboardModelId
            AiModelSelectionTarget.ASSISTANT -> AiConfig.assistantModelId
            AiModelSelectionTarget.CONTEXT_COMPACTION -> AiConfig.contextCompactionModelId
        }
        val dialog = BottomSheetDialog(requireContext())
        val sheetState = AiModelSelectionSheetState(
            title = getString(R.string.ai_model_select),
            emptyText = when (target) {
                AiModelSelectionTarget.ASSISTANT ->
                    getString(R.string.ai_assistant_model_empty)
                AiModelSelectionTarget.CONTEXT_COMPACTION -> null
                else -> getString(R.string.ai_purify_model_empty)
            },
            providers = providers,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            followAssistantLabel = getString(R.string.ai_context_compaction_model_follow)
                .takeIf { target == AiModelSelectionTarget.CONTEXT_COMPACTION },
            followAssistantSelected = target == AiModelSelectionTarget.CONTEXT_COMPACTION &&
                (selectedProviderId.isBlank() || selectedModelId.isBlank()),
        )
        dialog.setContentView(
            requireContext().createNgBottomDrawerComposeHost(fillMaxHeight = true) {
                AiModelSelectionSheet(
                    state = sheetState,
                    onSelect = { providerId, modelId ->
                        when (target) {
                            AiModelSelectionTarget.PURIFY -> {
                                AiConfig.savePurifyModel(providerId, modelId)
                            }
                            AiModelSelectionTarget.READ_ALOUD -> {
                                AiConfig.saveReadAloudStoryboardModel(providerId, modelId)
                            }
                            AiModelSelectionTarget.ASSISTANT -> {
                                AiConfig.saveAssistantModel(providerId, modelId)
                            }
                            AiModelSelectionTarget.CONTEXT_COMPACTION -> {
                                AiConfig.saveContextCompactionModel(providerId, modelId)
                            }
                        }
                        refreshModelSettings()
                        refreshMain()
                        dialog.dismiss()
                    },
                    onFollowAssistant = if (target == AiModelSelectionTarget.CONTEXT_COMPACTION) {
                        {
                            AiConfig.followAssistantForContextCompaction()
                            refreshModelSettings()
                            dialog.dismiss()
                        }
                    } else {
                        null
                    },
                )
            }
        )
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * 0.88f).toInt()
            }
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun showPurifyModelSelectDialog() {
        showComposeModelSelectionSheet(AiModelSelectionTarget.PURIFY)
    }

    private fun showReadAloudStoryboardModelSelectDialog() {
        showComposeModelSelectionSheet(AiModelSelectionTarget.READ_ALOUD)
    }

    private fun purifyModelProviders(): List<AiProviderSetting> {
        return AiProviderStore.providers().filter { provider ->
            provider.enabled && provider.purifyEligibleModels().isNotEmpty()
        }
    }

    private fun showAssistantModelSelectDialog() {
        showComposeModelSelectionSheet(AiModelSelectionTarget.ASSISTANT)
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
            currentLevel = AiConfig.purifyReasoningLevel,
            iconTintWhenOff = true
        ) { level ->
            AiConfig.purifyReasoningLevel = level
            refreshModelSettings()
            refreshMain()
        }
    }

    private fun showContextCompactionModelSelectDialog() {
        showComposeModelSelectionSheet(AiModelSelectionTarget.CONTEXT_COMPACTION)
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
            currentLevel = AiConfig.readAloudStoryboardReasoningLevel,
            iconTintWhenOff = true
        ) { level ->
            AiConfig.readAloudStoryboardReasoningLevel = level
            refreshModelSettings()
            refreshMain()
        }
    }

    private fun showReadAloudStoryboardPreloadDialog() {
        val dialog = ComponentDialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AiNumberPickerDialogContent(
                        title = getString(R.string.ai_read_aloud_storyboard_preload_count),
                        minValue = AiConfig.MIN_READ_ALOUD_STORYBOARD_PRELOAD_COUNT,
                        maxValue = AiConfig.MAX_READ_ALOUD_STORYBOARD_PRELOAD_COUNT,
                        initialValue = AiConfig.readAloudStoryboardPreloadCount,
                        cancelText = getString(android.R.string.cancel),
                        confirmText = getString(android.R.string.ok),
                        onCancel = dialog::dismiss,
                        onConfirm = { value ->
                            AiConfig.readAloudStoryboardPreloadCount = value
                            refreshModelSettings()
                            refreshMain()
                            dialog.dismiss()
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.applyNgWindow()
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
        currentLevel: AiReasoningLevel,
        iconTintWhenOff: Boolean,
        onLevelChanged: (AiReasoningLevel) -> Unit
    ) {
        val levels = AiReasoningLevel.entries.toList()
        val labels = levels.map { it.displayName() }
        showDiscreteScaleDialog(
            title = title,
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
        description: String? = null,
        iconRes: Int,
        labels: List<String>,
        currentLabels: List<String> = labels,
        selectedIndex: Int,
        tintIcon: (Int) -> Boolean = { true },
        onSelectedIndexChanged: (Int) -> Unit
    ) {
        if (labels.isEmpty()) return
        showComposeDiscreteScaleDialog(
            title = title,
            description = description,
            iconRes = iconRes,
            labels = labels,
            currentLabels = currentLabels,
            selectedIndex = selectedIndex,
            tintIcon = tintIcon,
            onSelectedIndexChanged = onSelectedIndexChanged,
        )
    }
    private fun showComposeDiscreteScaleDialog(
        title: String,
        description: String?,
        iconRes: Int,
        labels: List<String>,
        currentLabels: List<String>,
        selectedIndex: Int,
        tintIcon: (Int) -> Boolean,
        onSelectedIndexChanged: (Int) -> Unit,
    ) {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    AiDiscreteScaleDialogContent(
                        title = title,
                        description = description,
                        iconRes = iconRes,
                        labels = labels,
                        currentLabels = currentLabels,
                        initialSelectedIndex = selectedIndex,
                        tintIcon = tintIcon,
                        onSelectedIndexChanged = onSelectedIndexChanged,
                    )
                }
            }
        }
        val dialog = ComponentDialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
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

    private data class PurifyModelOption(
        val provider: AiProviderSetting,
        val model: AiModel
    )

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun readProviderFromForm(): AiProviderSetting? {
        val source = currentProviderId?.let { AiProviderStore.provider(it) } ?: return null
        return providerFormScreenState.applyTo(source)
    }

    private fun saveCurrentProvider(
        showToast: Boolean = false,
        updateHeader: Boolean = false
    ): AiProviderSetting? {
        val provider = readProviderFromForm() ?: return null
        AiProviderStore.saveProvider(provider)
        if (updateHeader) {
            setPageTitle(provider.name)
            providerDetailScreenState = providerDetailScreenState.copy(
                providerName = provider.name,
            )
        }
        if (showToast) {
            Toast.makeText(requireContext(), R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
        }
        return provider
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
        if (providerDetailScreenState.isRefreshingModels) return
        providerDetailScreenState = providerDetailScreenState.copy(isRefreshingModels = true)
        val provider = saveCurrentProvider()
        if (provider == null) {
            providerDetailScreenState = providerDetailScreenState.copy(isRefreshingModels = false)
            return
        }
        if (provider.apiKey.isBlank()) {
            providerDetailScreenState = providerDetailScreenState.copy(isRefreshingModels = false)
            showAiClassicDialog(
                title = getString(R.string.ai_fetch_models),
                message = getString(R.string.ai_api_key_required),
            )
            return
        }
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
                showAiClassicDialog(
                    title = getString(R.string.ai_fetch_models),
                    message = getString(
                        R.string.ai_test_failed,
                        e.localizedMessage ?: e.toString(),
                    ),
                )
            } finally {
                providerDetailScreenState = providerDetailScreenState.copy(
                    isRefreshingModels = false,
                )
            }
        }
    }

    private fun testConnection() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            showAiClassicDialog(
                title = getString(R.string.ai_test_connection),
                message = getString(R.string.ai_api_key_required),
            )
            return
        }
        waitDialog.setText(R.string.ai_test_connection)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val modelCount = AiManager.testConnectivity(provider.id)
                showAiClassicDialog(
                    title = getString(R.string.ai_test_connection),
                    message = getString(R.string.ai_connectivity_success, modelCount.toString()),
                )
            } catch (e: Throwable) {
                showAiClassicDialog(
                    title = getString(R.string.ai_test_connection),
                    message = getString(
                        R.string.ai_test_failed,
                        e.localizedMessage ?: e.toString(),
                    ),
                )
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun queryBalance() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            showAiClassicDialog(
                title = getString(R.string.ai_query_balance),
                message = getString(R.string.ai_api_key_required),
            )
            return
        }
        waitDialog.setText(R.string.ai_query_balance)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val result = AiManager.queryBalance(provider.id)
                showAiClassicDialog(
                    title = getString(R.string.ai_query_balance),
                    message = formatBalanceResult(result),
                )
            } catch (e: Throwable) {
                showAiClassicDialog(
                    title = getString(R.string.ai_query_balance),
                    message = getString(
                        R.string.ai_test_failed,
                        e.localizedMessage ?: e.toString(),
                    ),
                )
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
        showAiClassicDialog(
            title = getString(R.string.ai_delete_provider),
            message = getString(R.string.sure_del_any, provider.name),
            cancelText = getString(android.R.string.cancel),
            dismissBeforeConfirm = true,
            onCancel = onCancel,
            onConfirm = {
                if (AiProviderStore.deleteCustomProvider(provider.id)) {
                    Toast.makeText(
                        requireContext(),
                        R.string.ai_provider_deleted,
                        Toast.LENGTH_SHORT
                    ).show()
                    onDeleted()
                    refreshMain()
                }
            },
        )
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
        val providerIconRes = provider?.iconRes() ?: R.drawable.ic_cfg_web
        providerDetailScreenState = providerDetailScreenState.copy(
            modelQuery = modelSearchQuery,
            models = models.map { model ->
                AiProviderModelItemUiModel(
                    id = model.safeId(),
                    name = model.displayName(),
                    iconRes = model.iconRes(providerIconRes),
                    capabilities = model.capabilityTags(),
                    selected = model.safeId() in selectedIds,
                )
            },
        )
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
        val actionText = when {
            selectedVisibleCount == visibleIds.size -> getString(R.string.ai_disable_all_models)
            else -> getString(R.string.ai_enable_all_models)
        }
        providerDetailScreenState = providerDetailScreenState.copy(
            modelSelectionActionText = actionText,
            modelSelectionActionEnabled = visibleIds.isNotEmpty(),
            allVisibleModelsSelected = visibleIds.isNotEmpty() &&
                selectedVisibleCount == visibleIds.size,
        )
    }

    private fun toggleVisibleModelSelection() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        val visibleIds = providerDetailScreenState.models.map { it.id }
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

    private fun modelInputModalitiesForDraft(
        type: AiModelType,
        selected: Set<AiModelModality>,
    ): List<AiModelModality> {
        return when (type) {
            AiModelType.CHAT -> normalizeModalities(
                textChecked = AiModelModality.TEXT in selected,
                imageChecked = AiModelModality.IMAGE in selected,
                videoChecked = AiModelModality.VIDEO in selected,
            )
            AiModelType.VIDEO -> normalizeModalities(
                textChecked = AiModelModality.TEXT in selected,
                imageChecked = AiModelModality.IMAGE in selected,
                videoChecked = AiModelModality.VIDEO in selected,
            )

            AiModelType.IMAGE,
            AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT)
            AiModelType.ASR -> listOf(AiModelModality.AUDIO)
            AiModelType.TTS -> listOf(AiModelModality.TEXT)
        }
    }

    private fun modelOutputModalitiesForDraft(
        type: AiModelType,
        selected: Set<AiModelModality>,
    ): List<AiModelModality> {
        return when (type) {
            AiModelType.CHAT -> normalizeModalities(
                textChecked = AiModelModality.TEXT in selected,
                imageChecked = AiModelModality.IMAGE in selected,
                videoChecked = AiModelModality.VIDEO in selected,
            )
            AiModelType.VIDEO -> normalizeModalities(
                textChecked = AiModelModality.TEXT in selected,
                imageChecked = AiModelModality.IMAGE in selected,
                videoChecked = AiModelModality.VIDEO in selected,
                defaultModality = AiModelModality.VIDEO,
            )

            AiModelType.IMAGE -> listOf(AiModelModality.IMAGE)
            AiModelType.EMBEDDING -> listOf(AiModelModality.TEXT)
            AiModelType.ASR -> listOf(AiModelModality.TEXT)
            AiModelType.TTS -> listOf(AiModelModality.AUDIO)
        }
    }

    private fun modelAbilitiesForDraft(
        type: AiModelType,
        selected: Set<AiModelAbility>,
    ): List<AiModelAbility> {
        return when (type) {
            AiModelType.CHAT -> buildList {
                if (AiModelAbility.TOOL in selected) add(AiModelAbility.TOOL)
                if (AiModelAbility.REASONING in selected) add(AiModelAbility.REASONING)
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

}

private data class AiMemoryStats(
    val count: Int,
    val estimatedSize: Long
)
