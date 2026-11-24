package io.legado.app.ui.config

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import io.legado.app.help.pet.PetPluginStore
import io.legado.app.ui.pet.PetManagerActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentAiConfigBinding
import io.legado.app.data.appDb
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiChatGptException
import io.legado.app.help.ai.AiCodexUsage
import io.legado.app.help.ai.accountSessions
import io.legado.app.help.ai.fetchCodexUsage
import io.legado.app.help.ai.isCodexSubscription
import io.legado.app.help.ai.CHATGPT_USAGE_URL
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiManualModelAddResult
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
import io.legado.app.help.ai.aiProviderHost
import io.legado.app.help.ai.apiKeyNeedsReentry
import io.legado.app.help.ai.assistantContextWindowLabel
import io.legado.app.help.ai.assistantContextWindowLimit
import io.legado.app.help.ai.assistantContextWindowOptions
import io.legado.app.help.ai.assistantModelChoice
import io.legado.app.help.ai.capAssistantContextWindow
import io.legado.app.help.ai.catalogFetchRequired
import io.legado.app.help.ai.effectiveModelCapabilities
import io.legado.app.help.ai.effectiveModelInfo
import io.legado.app.help.ai.hasRequestCredentials
import io.legado.app.help.ai.isOpenAiAccountProvider
import io.legado.app.help.ai.isManualModel
import io.legado.app.help.ai.modelConfigurationKey
import io.legado.app.help.ai.modelPresetProviderId
import io.legado.app.help.ai.modelSettingsFor
import io.legado.app.help.ai.withCatalogCapabilities
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
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
import io.legado.app.utils.showHelp
import io.legado.app.utils.showWithAppNavigationBarVisibility
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class AiConfigFragment : BaseFragment(R.layout.fragment_ai_config), ConfigBackHandler,
    CodeDialog.Callback {

    companion object {
        const val EXTRA_INITIAL_PAGE = "initialPage"
        const val PAGE_PROVIDERS = "providers"
        const val PAGE_SEARCH_SERVICES = "searchServices"
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

    private data class AddModelTarget(val providerId: String, val configurationKey: String)

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
    private var addModelTarget by mutableStateOf<AddModelTarget?>(null)
    private var aiMenuScreenState by mutableStateOf(AiConfigMenuScreenState())
    private var petManagerNavigationPending = false
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
    private var providerDetailTab = ProviderDetailTab.CONFIG
    private val autoFetchedModelProviderRevisions = hashMapOf<String, String?>()
    private var requestJob: Job? = null
    private var chatGptJob: Job? = null
    private var chatGptSigningIn = false
    private var chatGptSigningOut = false
    private var chatGptNotice: Int? = null
    private var codexUsageKey: Pair<String, String?>? = null
    private var codexUsageJob: Job? = null
    private var codexUsage: AiCodexUsage? = null
    private var codexUsageLoading = false
    private var modelRefreshJob: Job? = null
    private var isRefreshingAllModels by mutableStateOf(false)
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
        petManagerNavigationPending = false
        if (skipNextResumeRefresh) {
            skipNextResumeRefresh = false
        } else {
            refreshCurrentPage()
        }
    }

    override fun onDestroyView() {
        addModelTarget = null
        activity?.findViewById<TitleBar>(R.id.title_bar)?.let { titleBar ->
            titleBar.isVisible = true
            titleBar.setTemporarySolidSurface(false)
        }
        clearPageActions()
        super.onDestroyView()
        requestJob?.cancel()
        modelRefreshJob?.cancel()
        modelRefreshJob = null
        isRefreshingAllModels = false
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

    internal fun usesFloatingSettingsTitleBar(): Boolean = when (currentPage) {
        Page.MAIN,
        Page.PURIFY_MODEL_SETTINGS,
        Page.READ_ALOUD_MODEL_SETTINGS,
        Page.ASSISTANT_MODEL_SETTINGS,
        Page.PURIFY_SETTINGS -> true
        else -> false
    }

    private fun setPageTitle(title: CharSequence) {
        clearPageActions()
        activity?.title = title
        requireActivity().findViewById<TitleBar>(R.id.title_bar)?.let { titleBar ->
            titleBar.isVisible = true
            titleBar.title = title
        }
        (activity as? ConfigActivity)?.refreshTitleBarChrome(this)
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
        addModelTarget = null
        currentPage = Page.MAIN
        setFeatureComposePage(Page.MAIN)
        currentProviderId = null
        currentSkill = null
        currentPrompt = null
        setPageTitle(R.string.ai_setting)
        refreshMain()
    }

    private fun showProviderList() {
        if (chatGptSigningIn) chatGptJob?.cancel()
        addModelTarget = null
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
        if (currentProviderId != providerId) {
            addModelTarget = null
            providerDetailScreenState = providerDetailScreenState.copy(
                modelQuery = "",
                modelFilter = AiProviderModelFilter.ALL,
                revealModelId = null,
            )
        }
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
            chatEntryStyle = AiConfig.chatEntryStyle,
            installedPets = PetPluginStore.installedChoices(),
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
        autoFetchedModelProviderRevisions.keys.retainAll(allProviders.mapTo(hashSetOf()) { it.id })
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
                    val modelCount = provider.visibleModelCount()
                    val providerHost = aiProviderHost(provider.baseUrl)
                    val chatGptAccount = provider.accountSessions()?.account(provider.chatGptAccountId)
                    AiProviderListItemUiModel(
                        id = provider.id,
                        name = provider.name,
                        summary = providerHost.takeIf { it != provider.name }.orEmpty(),
                        iconRes = provider.iconRes(),
                        enabled = provider.enabled,
                        modelCountText = if (modelCount == 0) {
                            getString(R.string.ai_provider_models_not_fetched)
                        } else {
                            getString(R.string.ai_provider_model_count, modelCount.toString())
                        },
                        modelCountWarning = modelCount == 0,
                        keyConfigured = provider.hasRequestCredentials(),
                        missingCredentialRes = when {
                            !provider.isOpenAiAccountProvider() -> R.string.ai_provider_no_api_key
                            chatGptAccount?.needsReauthorization() == true -> R.string.ai_chatgpt_session_expired
                            chatGptAccount?.tokens == null -> R.string.ai_chatgpt_not_signed_in
                            else -> R.string.ai_chatgpt_plan_not_authorized
                        },
                        reorderable = true,
                        deletable = !provider.builtIn
                    )
                }
            ),
            isRefreshing = isRefreshingAllModels,
            showDisabled = showDisabledProviders,
        )
    }

    private fun refreshEnabledModels(onModelsUpdated: (() -> Unit)? = null) {
        if (isRefreshingAllModels) return
        val refreshJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Give the pull indicator a frame even when every enabled provider has no API key.
                delay(24L)
                val result = AiManager.refreshEnabledProviderModels()
                refreshProviders()
                refreshModelSettings()
                refreshMain()
                onModelsUpdated?.invoke()
                Toast.makeText(
                    requireContext(),
                    getString(
                        R.string.ai_refresh_models_result,
                        result.refreshedProviderCount,
                        result.refreshedModelCount,
                        result.failedProviders.size,
                        result.missingKeyProviders.size,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                if (result.failedProviders.isNotEmpty()) {
                    showAiClassicDialog(
                        title = getString(R.string.ai_refresh_all_models),
                        message = getString(
                            R.string.ai_refresh_models_failed_providers,
                            result.failedProviders.joinToString(", "),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (isAdded) {
                    showAiClassicDialog(
                        title = getString(R.string.ai_refresh_all_models),
                        message = getString(
                            R.string.ai_test_failed,
                            e.localizedMessage ?: e.javaClass.simpleName,
                        ),
                    )
                }
            } finally {
                if (modelRefreshJob === coroutineContext[Job]) {
                    modelRefreshJob = null
                    isRefreshingAllModels = false
                    providerScreenState = providerScreenState.copy(isRefreshing = false)
                }
            }
        }
        modelRefreshJob = refreshJob
        isRefreshingAllModels = true
        providerScreenState = providerScreenState.copy(isRefreshing = true)
        refreshJob.start()
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

            AiProviderListScreenAction.RetryRequested -> refreshProviders()
            AiProviderListScreenAction.RefreshRequested -> refreshEnabledModels()
        }
    }

    private fun handleProviderFormAction(action: AiProviderFormScreenAction) {
        if (currentPage != Page.DETAIL) return
        when (action) {
            is AiProviderFormScreenAction.FieldChanged -> {
                if (action.field == AiProviderFormField.BASE_URL) {
                    providerFormScreenState =
                        providerFormScreenState.withAutoNameForBaseUrl(action.value)
                }
                val updated = providerFormScreenState.withField(
                    action.field,
                    action.value
                )
                if (updated == providerFormScreenState) return
                providerFormScreenState = updated
                val provider = saveCurrentProvider(
                    updateHeader = action.field == AiProviderFormField.NAME ||
                        action.field == AiProviderFormField.BASE_URL,
                    keepProtocolPathDraft = action.field == AiProviderFormField.CHAT_PATH ||
                        action.field == AiProviderFormField.RESPONSES_PATH,
                )
                if (provider != null && action.field in setOf(
                        AiProviderFormField.API_KEY, AiProviderFormField.BASE_URL,
                        AiProviderFormField.CHAT_PATH, AiProviderFormField.MODELS_URL,
                    )) {
                    refreshModelList(provider)
                }
            }

            is AiProviderFormScreenAction.ToggleChanged -> {
                val updated = providerFormScreenState.withToggle(
                    action.toggle,
                    action.checked
                )
                if (updated == providerFormScreenState) return
                providerFormScreenState = updated
                val provider = saveCurrentProvider()
                if (action.toggle == AiProviderFormToggle.CUSTOM_MODELS_URL && provider != null) {
                    refreshModelList(provider)
                }
            }

            AiProviderFormScreenAction.TestConnectionRequested -> testConnection()
            AiProviderFormScreenAction.QueryBalanceRequested -> queryBalance()
            AiProviderFormScreenAction.UseKeyForCurrentAddress -> {
                saveCurrentProvider(useExistingKeyHere = true)?.let(::refreshModelList)
            }
            AiProviderFormScreenAction.ResetRequested -> confirmResetCurrentProvider()
            AiProviderFormScreenAction.DeleteRequested -> confirmDeleteCurrentProvider()
        }
    }

    private fun commitProviderOrder(orderedProviderIds: List<String>) {
        if (providerSearchQuery.isNotBlank()) return
        val reordered = synchronized(AiProviderStore) {
            val allProviders = AiProviderStore.providers()
            val visibleProviders = ConfigListVisibilitySupport.visibleItems(
                allItems = allProviders,
                showDisabled = showDisabledProviders,
                isEnabled = AiProviderSetting::enabled,
            )
            val visibleIds = visibleProviders.map(AiProviderSetting::id)
            if (orderedProviderIds == visibleIds) return
            if (orderedProviderIds.size != visibleIds.size || orderedProviderIds.toSet() != visibleIds.toSet()) {
                false
            } else {
                val providersById = visibleProviders.associateBy(AiProviderSetting::id)
                AiProviderStore.saveProviders(
                    ConfigListVisibilitySupport.mergeVisibleOrder(
                        allItems = allProviders,
                        reorderedVisibleItems = orderedProviderIds.mapNotNull(providersById::get),
                        showDisabled = showDisabledProviders,
                        isEnabled = AiProviderSetting::enabled,
                    )
                )
                true
            }
        }
        if (reordered) refreshMain()
        refreshProviders()
    }

    private fun refreshCurrentDetail() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        setPageTitle(provider.name)
        providerFormScreenState = provider.toProviderFormScreenState(
            provider.type.localizedDisplayName()
        )
        providerDetailScreenState = providerDetailScreenState.copy(
            providerId = provider.id,
            providerName = provider.name,
            providerIconRes = provider.iconRes(),
            selectedTab = providerDetailTab.ordinal,
            chatGpt = if (provider.isOpenAiAccountProvider()) chatGptUiState(provider) else null,
        )
        refreshModelList(provider)
        showProviderDetailTab(providerDetailTab)
        if (provider.isCodexSubscription()) refreshCodexUsage(provider)
    }

    private fun showProviderDetailTab(tab: ProviderDetailTab) {
        providerDetailTab = tab
        providerDetailScreenState = providerDetailScreenState.copy(selectedTab = tab.ordinal)
        setProviderFloatingChrome(true)
        if (tab == ProviderDetailTab.MODELS) {
            binding.composeProviderDetail.clearFocus()
            binding.composeProviderDetail.hideSoftInput()
            val provider = currentProviderId?.let(AiProviderStore::provider) ?: return
            if (provider.builtIn && provider.id == "deepseek") refreshModelList(provider)
            maybeAutoFetchModels(provider)
        }
    }

    private fun showModelEditDialog(provider: AiProviderSetting, model: AiModel) {
        val modelInfo = provider.effectiveModelInfo(model)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(
            requireContext().createNgBottomDrawerComposeHost(
                fillMaxHeight = true,
                contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
            ) {
                AiModelEditSheet(
                    provider = provider,
                    model = model,
                    info = modelInfo,
                    modelForDraft = { modelWithEdits(model, it) },
                    onSave = { draft ->
                        if (draft == null || saveModelEdit(provider, model, draft)) dialog.dismiss()
                        else toastOnUi(R.string.ai_model_catalog_changed)
                    },
                )
            }
        )
        configureModelEditSheet(dialog, 0.80f)
        dialog.showWithAppNavigationBarVisibility()
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
                    val searchSettings by AiExternalSearchStore.changes.collectAsState()
                    AiAssistantModelSettingsScreen(
                        state = assistantModelSettingsScreenState,
                        searchSettings = searchSettings,
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
                    val formState = providerFormScreenState
                    val helpDocument = AiProviderHelpDocuments.forProvider(
                        providerId = formState.providerId,
                        builtIn = formState.builtIn,
                        baseUrl = formState.baseUrl,
                    )
                    AiProviderDetailScreen(
                        state = providerDetailScreenState,
                        formState = formState,
                        onFormAction = ::handleProviderFormAction,
                        onAction = ::handleProviderDetailAction,
                        onHelp = helpDocument?.let { fileName -> { showHelp(fileName) } },
                    )
                    val target = addModelTarget
                    if (target != null && target.providerId == providerDetailScreenState.providerId) {
                        val models = providerDetailScreenState.models
                        val ids = remember(models) { models.mapTo(hashSetOf()) { it.id } }
                        AiAddModelDialog(
                            existingModelIds = ids,
                            onDismissRequest = { addModelTarget = null },
                            onAdd = { id, type -> addProviderModel(target, id, type) },
                            onViewExisting = { id ->
                                addModelTarget = null
                                showModelDetail(id)
                            },
                        )
                    }
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
                        onManagePets = {
                            if (!petManagerNavigationPending) {
                                petManagerNavigationPending = true
                                startActivity(Intent(requireContext(), PetManagerActivity::class.java))
                            }
                        },
                    )
                }
            }
        }
    }

    private fun handleAiMainOpenPage(page: String) {
        when (page) {
            PAGE_PROVIDERS -> showProviderList()
            PAGE_SEARCH_SERVICES -> (activity as? ConfigActivity)?.openAiConfigPage(page)
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
            is AiProviderDetailAction.ChatGptSignIn -> signInToChatGpt(action.addAccount)
            AiProviderDetailAction.ChatGptCancelSignIn -> if (chatGptSigningIn) chatGptJob?.cancel()
            AiProviderDetailAction.ChatGptSignOut -> signOutOfChatGpt()
            AiProviderDetailAction.ChatGptOpenUsage -> {
                val provider = currentProviderId?.let(AiProviderStore::provider)
                if (provider?.isCodexSubscription() == true) {
                    refreshCodexUsage(provider, force = true)
                    return
                }
                try {
                    openChatGptBrowser(CHATGPT_USAGE_URL)
                } catch (_: AiChatGptException) {
                    toastOnUi(R.string.ai_chatgpt_browser_unavailable)
                }
            }
            is AiProviderDetailAction.ChatGptSelectAccount -> {
                if (chatGptJob?.isActive == true) return
                val provider = currentProviderId?.let(AiProviderStore::provider) ?: return
                val account = provider.accountSessions()?.account(action.accountId) ?: return
                if (account.tokens == null || account.needsReauthorization()) {
                    signInToChatGpt(addAccount = false, registeredAccountId = account.clientId)
                    return
                }
                chatGptNotice = null
                AiProviderStore.selectChatGptAccount(action.accountId, providerId = provider.id)
                refreshChatGptDetail(fetch = true)
            }
            is AiProviderDetailAction.ChatGptEnabled -> {
                val provider = currentProviderId?.let(AiProviderStore::provider)
                    ?.takeIf { it.isOpenAiAccountProvider() } ?: return
                AiProviderStore.saveProvider(provider.copy(enabled = action.enabled))
                refreshChatGptDetail(fetch = action.enabled)
            }
            is AiProviderDetailAction.TabSelected -> {
                val tab = ProviderDetailTab.entries[action.index]
                showProviderDetailTab(tab)
            }
            is AiProviderDetailAction.ModelQueryChanged -> {
                providerDetailScreenState = providerDetailScreenState.copy(modelQuery = action.query)
            }
            is AiProviderDetailAction.ModelFilterChanged -> {
                providerDetailScreenState = providerDetailScreenState.copy(
                    modelFilter = action.filter.orAllFor(providerDetailScreenState.models),
                )
            }
            AiProviderDetailAction.AddModelRequested -> {
                val provider = currentProviderId?.let(AiProviderStore::provider) ?: return
                addModelTarget = AddModelTarget(provider.id, provider.modelConfigurationKey())
            }
            is AiProviderDetailAction.ModelRevealed -> {
                if (providerDetailScreenState.revealModelId == action.modelId) {
                    providerDetailScreenState = providerDetailScreenState.copy(revealModelId = null)
                }
            }
            AiProviderDetailAction.RefreshModels -> fetchModels()
            is AiProviderDetailAction.SetModelsEnabled -> setModelsEnabled(action.ids, action.enabled)
            AiProviderDetailAction.Back -> {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            is AiProviderDetailAction.EditModel -> showModelDetail(action.modelId)
            is AiProviderDetailAction.DeleteModelRequested -> confirmDeleteManualModel(action.modelId)
            is AiProviderDetailAction.ToggleModel -> {
                setModelsEnabled(setOf(action.modelId), action.selected)
            }
        }
    }

    private fun chatGptUiState(provider: AiProviderSetting): AiChatGptProviderUiState =
        AiChatGptProviderUiState.fromAccounts(
            registeredAccounts = provider.accountSessions()?.accounts().orEmpty(),
            registeredAccountId = provider.chatGptAccountId,
            defaultLabel = getString(R.string.ai_chatgpt_account),
            codex = provider.isCodexSubscription(),
        ).copy(
            signingIn = chatGptSigningIn,
            signingOut = chatGptSigningOut,
            noticeRes = chatGptNotice,
            codexUsage = codexUsage.takeIf { codexUsageKey?.first == provider.chatGptAccountId },
            usageLoading = codexUsageLoading && codexUsageKey?.first == provider.chatGptAccountId,
        )

    private fun openChatGptBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            throw AiChatGptException(R.string.ai_chatgpt_browser_unavailable)
        }
    }

    private fun signInToChatGpt(addAccount: Boolean, registeredAccountId: String? = null) {
        if (chatGptJob?.isActive == true) return
        val provider = currentProviderId?.let(AiProviderStore::provider)
            ?.takeIf { it.isOpenAiAccountProvider() } ?: return
        val sessions = provider.accountSessions() ?: return
        chatGptSigningIn = true
        chatGptNotice = null
        refreshChatGptDetail()
        chatGptJob = viewLifecycleOwner.lifecycleScope.launch {
            var fetch = false
            try {
                val account = sessions.signIn(
                    clientId = (registeredAccountId ?: provider.chatGptAccountId).takeUnless { addAccount },
                    openBrowser = ::openChatGptBrowser,
                )
                AiProviderStore.selectChatGptAccount(account.clientId, refresh = true, providerId = provider.id)
                fetch = sessions.allowsPlan(account)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                chatGptNotice = (error as? AiChatGptException)?.messageRes ?: R.string.ai_chatgpt_sign_in_failed
            } finally {
                chatGptSigningIn = false
                if (isAdded && view != null && viewLifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    refreshChatGptDetail(fetch && isActive)
                }
            }
        }
    }

    private fun signOutOfChatGpt() {
        if (chatGptJob?.isActive == true) return
        val provider = currentProviderId?.let(AiProviderStore::provider)
            ?.takeIf { it.isOpenAiAccountProvider() } ?: return
        val sessions = provider.accountSessions() ?: return
        val clientId = provider.chatGptAccountId ?: return
        chatGptSigningOut = true
        chatGptNotice = null
        refreshChatGptDetail()
        chatGptJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val confirmed = sessions.signOut(clientId)
                if (!confirmed) chatGptNotice = R.string.ai_chatgpt_revoke_unconfirmed
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                chatGptNotice = (error as? AiChatGptException)?.messageRes ?: R.string.ai_chatgpt_sign_out_failed
            } finally {
                chatGptSigningOut = false
                if (isActive && isAdded && view != null) refreshChatGptDetail()
            }
        }
    }

    private fun refreshChatGptDetail(fetch: Boolean = false) {
        if (currentPage == Page.PROVIDERS) refreshProviders()
        if (currentPage != Page.DETAIL) return
        val provider = currentProviderId?.let(AiProviderStore::provider)
            ?.takeIf { it.isOpenAiAccountProvider() } ?: return
        refreshCurrentDetail()
        if (fetch) maybeAutoFetchModels(provider)
    }

    private fun refreshCodexUsage(provider: AiProviderSetting, force: Boolean = false) {
        val key = provider.chatGptAccountId?.takeIf { provider.hasRequestCredentials() }
            ?.let { it to provider.catalogRevision }
        if (key == null) {
            codexUsageJob?.cancel()
            codexUsageKey = null
            codexUsage = null
            codexUsageLoading = false
            return
        }
        if (!force && codexUsageKey == key) return
        codexUsageJob?.cancel()
        codexUsageKey = key
        codexUsage = null
        codexUsageLoading = true
        providerDetailScreenState = providerDetailScreenState.copy(chatGpt = chatGptUiState(provider))
        codexUsageJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                provider.fetchCodexUsage()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            codexUsage = result
            codexUsageLoading = false
            val current = currentProviderId?.let(AiProviderStore::provider)
            if (currentPage == Page.DETAIL && current?.id == provider.id && current.chatGptAccountId == key.first) {
                providerDetailScreenState = providerDetailScreenState.copy(chatGpt = chatGptUiState(current))
            }
        }
    }

    private fun addProviderModel(target: AddModelTarget, id: String, type: AiModelType) {
        when (AiProviderStore.addManualModel(target.providerId, id, type, target.configurationKey)) {
            AiManualModelAddResult.ADDED -> {
                addModelTarget = null
                refreshModelList(AiProviderStore.provider(target.providerId))
                val state = providerDetailScreenState
                val item = state.models.firstOrNull { it.id == id }
                if (item != null) {
                    val queryMatches = listOf(item).filterProviderModels(state.modelQuery, AiProviderModelFilter.ALL)
                        .isNotEmpty()
                    providerDetailScreenState = state.copy(
                        modelQuery = state.modelQuery.takeIf { queryMatches }.orEmpty(),
                        modelFilter = state.modelFilter.takeIf { it.matches(item.type) } ?: AiProviderModelFilter.ALL,
                        revealModelId = item.id,
                    )
                }
                refreshMain()
            }
            AiManualModelAddResult.DUPLICATE -> refreshModelList(AiProviderStore.provider(target.providerId))
            AiManualModelAddResult.UNAVAILABLE -> {
                addModelTarget = null
                toastOnUi(R.string.ai_model_catalog_changed)
            }
        }
    }

    private fun confirmDeleteManualModel(modelId: String) {
        val provider = currentProviderId?.let(AiProviderStore::provider) ?: return
        val configurationKey = provider.modelConfigurationKey()
        val model = provider.models.firstOrNull {
            it.safeId() == modelId && it.manualModelEndpoint == configurationKey
        } ?: return
        showAiClassicDialog(
            title = getString(R.string.ai_delete_model),
            message = getString(R.string.ai_delete_model_message, model.displayName()),
            cancelText = getString(android.R.string.cancel),
            dismissBeforeConfirm = true,
            onConfirm = {
                if (AiProviderStore.removeManualModel(provider.id, modelId, configurationKey)) {
                    refreshModelList(AiProviderStore.provider(provider.id))
                    refreshMain()
                    toastOnUi(R.string.ai_model_deleted)
                }
            },
        )
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
            is AiReadAloudModelSettingsAction.PreloadCountChanged -> {
                val value = action.value.coerceIn(
                    AiConfig.MIN_READ_ALOUD_STORYBOARD_PRELOAD_COUNT,
                    AiConfig.MAX_READ_ALOUD_STORYBOARD_PRELOAD_COUNT,
                )
                readAloudModelSettingsScreenState = readAloudModelSettingsScreenState.copy(
                    preloadCount = value,
                    preloadSummary = getString(
                        R.string.ai_read_aloud_storyboard_preload_summary,
                        value,
                    ),
                )
            }
            AiReadAloudModelSettingsAction.PreloadCountChangeFinished -> {
                AiConfig.readAloudStoryboardPreloadCount =
                    readAloudModelSettingsScreenState.preloadCount
                refreshModelSettings()
                refreshMain()
            }
        }
    }

    private fun handleAssistantModelSettingsAction(action: AiAssistantModelSettingsAction) {
        when (action) {
            AiAssistantModelSettingsAction.SelectModel -> showAssistantModelSelectDialog()
            AiAssistantModelSettingsAction.SelectReasoning -> showAssistantReasoningDialog()
            AiAssistantModelSettingsAction.SelectContextWindow -> {
                showAssistantContextWindowDialog()
            }
            AiAssistantModelSettingsAction.SelectCompactionThreshold -> {
                showContextCompactionThresholdDialog()
            }
            AiAssistantModelSettingsAction.OpenWebSettings -> {
                AiAssistantWebSettingsDialog.show(childFragmentManager)
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
            is AiPurifySettingsAction.NumberStepChanged -> {
                updatePurifyNumberDraft(
                    field = action.field,
                    value = action.value.toString(),
                    persist = false,
                )
            }
            is AiPurifySettingsAction.NumberStepFinished -> {
                commitPurifyNumberDraft(action.field)
            }
            is AiPurifySettingsAction.NumberFocusLost -> refreshPurifySettings()
        }
    }

    private fun updatePurifyNumberDraft(
        field: AiPurifyNumberField,
        value: String,
        persist: Boolean = true,
    ) {
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
        if (persist) {
            value.toIntOrNull()?.let { parsed ->
                persistPurifyNumber(field, parsed)
            }
        }
    }

    private fun commitPurifyNumberDraft(field: AiPurifyNumberField) {
        val value = when (field) {
            AiPurifyNumberField.PARAGRAPH_LIMIT -> purifySettingsScreenState.paragraphLimit
            AiPurifyNumberField.CHAPTER_CONCURRENCY -> purifySettingsScreenState.chapterConcurrency
            AiPurifyNumberField.CHAPTER_RETRY_COUNT -> purifySettingsScreenState.chapterRetryCount
            AiPurifyNumberField.CHAPTER_SEGMENT_LIMIT -> purifySettingsScreenState.chapterSegmentLimit
            AiPurifyNumberField.CHAPTER_SAMPLE_LIMIT -> purifySettingsScreenState.chapterSampleLimit
        }.toIntOrNull() ?: return
        persistPurifyNumber(field, value)
    }

    private fun persistPurifyNumber(field: AiPurifyNumberField, value: Int) {
        when (field) {
            AiPurifyNumberField.PARAGRAPH_LIMIT -> AiConfig.purifyParagraphLimit = value
            AiPurifyNumberField.CHAPTER_CONCURRENCY -> {
                AiConfig.purifyChapterConcurrencyLimit = value
            }
            AiPurifyNumberField.CHAPTER_RETRY_COUNT -> {
                AiConfig.purifyChapterRetryCount = value
            }
            AiPurifyNumberField.CHAPTER_SEGMENT_LIMIT -> {
                AiConfig.purifyChapterSegmentLimit = value
            }
            AiPurifyNumberField.CHAPTER_SAMPLE_LIMIT -> {
                AiConfig.purifyChapterSampleLimit = value
            }
        }
        refreshMain()
        refreshModelSettings()
    }

    private fun configureModelEditSheet(dialog: BottomSheetDialog, heightFraction: Float) {
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.layoutParams = sheet.layoutParams.apply {
                height = (resources.displayMetrics.heightPixels * heightFraction).toInt()
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
    ): Boolean {
        val settings = draft.modelSettings ?: return false
        synchronized(AiProviderStore) {
            val current = AiProviderStore.provider(provider.id) ?: return false
            val modelId = original.safeId()
            if (current.catalogRevision != provider.catalogRevision ||
                (current.catalogRefreshRequired && !current.isManualModel(modelId)) ||
                current.modelConfigurationKey() != settings.catalogEndpoint
            ) return false
            val currentModel = current.models.firstOrNull { it.safeId() == modelId }
                ?: return false
            if (currentModel != original) return false
            val initial = current.effectiveModelCapabilities(currentModel)
            val capabilitiesChanged = draft.type != initial.safeType() ||
                draft.inputModalities != initial.safeInputModalities().toSet() ||
                draft.outputModalities != initial.safeOutputModalities().toSet() ||
                draft.abilities != initial.safeAbilities().toSet()
            val updated = if (capabilitiesChanged) {
                modelWithEdits(currentModel, draft)
            } else {
                currentModel.copy(displayName = draft.displayName.trim(), modelSettings = settings)
            }
            AiProviderStore.saveProvider(current.copy(models = current.models.updatedWithModel(updated)))
        }
        refreshModelList(AiProviderStore.provider(provider.id))
        refreshMain()
        return true
    }

    private fun modelWithEdits(original: AiModel, draft: AiModelEditDraft): AiModel {
        val explicitModalities = draft.modelSettings != null &&
            (draft.type == AiModelType.CHAT || draft.type == AiModelType.VIDEO)
        return original.copy(
            displayName = draft.displayName.trim(),
            type = draft.type,
            inputModalities = if (explicitModalities) draft.inputModalities.toList()
                else modelInputModalitiesForDraft(draft.type, draft.inputModalities),
            outputModalities = if (explicitModalities) draft.outputModalities.toList()
                else modelOutputModalitiesForDraft(draft.type, draft.outputModalities),
            abilities = mergeModelAbilitiesForEdit(
                original = original,
                exposedAbilities = modelAbilitiesForDraft(draft.type, draft.abilities),
            ),
            modelSettings = draft.modelSettings ?: original.modelSettings,
        )
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
        val assistantModel = selectedAssistantModel()
        val assistantReasoningEnabled = assistantModel?.assistantReasoning()?.hasControls == true
        val assistantWindow = capAssistantContextWindow(
            AiConfig.assistantContextWindowTokens,
            assistantModel?.let { it.provider.assistantContextWindowLimit(it.model) },
        )
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
            preloadCount = AiConfig.readAloudStoryboardPreloadCount,
            preloadSummary = getString(
                R.string.ai_read_aloud_storyboard_preload_summary,
                AiConfig.readAloudStoryboardPreloadCount,
            ),
            reasoningAvailable = readAloudReasoningEnabled,
        )
        assistantModelSettingsScreenState = AiAssistantModelSettingsScreenState(
            modelSummary = assistantModelSummaryText(),
            reasoningSummary = assistantReasoningSummaryText(),
            contextWindowSummary = getString(
                R.string.ai_assistant_context_window_summary,
                assistantContextWindowLabel(assistantWindow),
            ),
            compactionThresholdSummary = if (AiConfig.contextCompactionThresholdPercent == 0) {
                getString(R.string.ai_context_compaction_threshold_off)
            } else {
                getString(
                    R.string.ai_context_compaction_threshold_summary,
                    AiConfig.contextCompactionThresholdPercent,
                    assistantWindow.toLong() * AiConfig.contextCompactionThresholdPercent / 100 / 1000,
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
                AiAssistantConfigUi.missingSelectedModelLabel(providers)
                    ?: getString(R.string.ai_assistant_model_unavailable)
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
        val picker = selectedAssistantModel(providers)?.reasoningPicker()
        return when {
            picker == null -> getString(R.string.ai_assistant_reasoning_select_model_first)
            !picker.available -> getString(R.string.ai_assistant_reasoning_unsupported)
            else -> getString(
                R.string.ai_purify_reasoning_level_summary,
                picker.selectedLabel(requireContext())
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
        val model = provider.assistantModelChoice(modelId)?.model ?: return null
        return AiAssistantConfigUi.AssistantModelOption(provider, model)
    }

    private enum class AiModelSelectionTarget {
        PURIFY,
        READ_ALOUD,
        ASSISTANT,
    }

    private fun modelSelectionProviders(
        target: AiModelSelectionTarget
    ): List<AiModelSelectionProviderUiModel> {
        if (target == AiModelSelectionTarget.ASSISTANT) {
            return assistantModelSelectionProviders()
        }
        return AiProviderStore.providers().modelSelectionEligibleProviders().map { provider ->
            provider.toModelSelectionProviderUiModel(provider.purifyEligibleModels())
        }
    }

    private fun showComposeModelSelectionSheet(target: AiModelSelectionTarget) {
        val selectedProviderId = when (target) {
            AiModelSelectionTarget.PURIFY -> AiConfig.purifyProviderId
            AiModelSelectionTarget.READ_ALOUD -> AiConfig.readAloudStoryboardProviderId
            AiModelSelectionTarget.ASSISTANT -> AiConfig.assistantProviderId
        }
        val selectedModelId = when (target) {
            AiModelSelectionTarget.PURIFY -> AiConfig.purifyModelId
            AiModelSelectionTarget.READ_ALOUD -> AiConfig.readAloudStoryboardModelId
            AiModelSelectionTarget.ASSISTANT -> AiConfig.assistantModelId
        }
        val dialog = BottomSheetDialog(requireContext())
        var sheetState by mutableStateOf(AiModelSelectionSheetState(
            title = getString(R.string.ai_model_select),
            emptyText = when (target) {
                AiModelSelectionTarget.ASSISTANT ->
                    getString(R.string.ai_assistant_model_empty)
                else -> getString(R.string.ai_purify_model_empty)
            },
            providers = modelSelectionProviders(target),
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
        ))
        fun finishSelection() {
            refreshModelSettings()
            refreshMain()
            dialog.dismiss()
        }
        dialog.setContentView(
            requireContext().createNgBottomDrawerComposeHost(
                fillMaxHeight = true,
                contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
            ) {
                AiModelSelectionSheet(
                    state = sheetState,
                    isRefreshing = isRefreshingAllModels,
                    onRefresh = {
                        refreshEnabledModels {
                            if (dialog.isShowing) {
                                sheetState = sheetState.copy(
                                    providers = modelSelectionProviders(target)
                                )
                            }
                        }
                    },
                    onFetchProvider = { providerId ->
                        val provider = withContext(Dispatchers.IO) {
                            AiManager.ensureProviderModelCatalog(providerId)
                            modelSelectionProviders(target).firstOrNull { it.id == providerId }
                        }
                        if (dialog.isShowing) {
                            sheetState = sheetState.withUpdatedProvider(providerId, provider)
                            refreshModelSettings()
                            refreshMain()
                        }
                    },
                    onClear = {
                        when (target) {
                            AiModelSelectionTarget.PURIFY -> AiConfig.clearPurifyModel()
                            AiModelSelectionTarget.READ_ALOUD -> AiConfig.clearReadAloudStoryboardModel()
                            AiModelSelectionTarget.ASSISTANT -> AiConfig.clearAssistantModel()
                        }
                        finishSelection()
                    },
                    onSelect = select@{ providerId, modelId ->
                        when (target) {
                            AiModelSelectionTarget.PURIFY -> {
                                AiConfig.savePurifyModel(providerId, modelId)
                            }
                            AiModelSelectionTarget.READ_ALOUD -> {
                                AiConfig.saveReadAloudStoryboardModel(providerId, modelId)
                            }
                            AiModelSelectionTarget.ASSISTANT -> {
                                if (!AiConfig.saveAssistantModel(providerId, modelId)) {
                                    toastOnUi(R.string.ai_assistant_model_unavailable)
                                    sheetState = sheetState.copy(providers = modelSelectionProviders(target))
                                    return@select
                                }
                            }
                        }
                        finishSelection()
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
                height = (resources.displayMetrics.heightPixels * 0.90f).toInt()
            }
            BottomSheetBehavior.from(sheet).apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.showWithAppNavigationBarVisibility()
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

    private fun showAssistantContextWindowDialog() {
        val selectedModel = selectedAssistantModel()
        val limit = selectedModel?.let { it.provider.assistantContextWindowLimit(it.model) }
        val options = assistantContextWindowOptions(limit)
        val initialWindow = capAssistantContextWindow(AiConfig.assistantContextWindowTokens, limit)
        showComposeAiScaleDialog(traceName = "context_window") {
            AiAssistantContextWindowDialogContent(options, initialWindow, selectedModel) { value ->
                AiConfig.assistantContextWindowTokens = value
                refreshModelSettings()
            }
        }
    }

    private fun showContextCompactionThresholdDialog() {
        val initialPercent = AiConfig.contextCompactionThresholdPercent
        showComposeAiScaleDialog(traceName = "compaction_threshold") {
            AiCompactionThresholdDialogContent(initialPercent) { value ->
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
        val picker = selected.reasoningPicker()
        if (!picker.available) {
            Toast.makeText(
                requireContext(),
                R.string.ai_assistant_reasoning_unsupported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showComposeAiScaleDialog(traceName = "reasoning_depth") {
            AiAssistantReasoningControls(selected) {
                refreshModelSettings()
                refreshMain()
            }
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
        fitLabels: Boolean = false,
        onSelectedIndexChanged: (Int) -> Unit
    ) {
        if (labels.isEmpty()) return
        showComposeAiScaleDialog {
            AiDiscreteScaleDialogContent(
                title = title,
                description = description,
                iconRes = iconRes,
                labels = labels,
                currentLabels = currentLabels,
                initialSelectedIndex = selectedIndex,
                tintIcon = tintIcon,
                fitLabels = fitLabels,
                onSelectedIndexChanged = onSelectedIndexChanged,
            )
        }
    }

    private fun showComposeAiScaleDialog(
        traceName: String? = null,
        content: @Composable () -> Unit,
    ) {
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    content()
                }
            }
        }
        val dialog = ComponentDialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(composeView)
        dialog.setCanceledOnTouchOutside(true)
        val trace = AiScaleDialogTrace.start(traceName, dialog.window, composeView)
        trace?.snapshot("before_window_config")
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        trace?.snapshot("before_show")
        dialog.show()
        trace?.snapshot("show_returned")
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

    private fun readProviderFromForm(): AiProviderSetting? {
        val source = currentProviderId?.let { AiProviderStore.provider(it) } ?: return null
        return providerFormScreenState.applyTo(source)
    }

    private fun saveCurrentProvider(
        showToast: Boolean = false,
        updateHeader: Boolean = false,
        useExistingKeyHere: Boolean = false,
        keepProtocolPathDraft: Boolean = false,
    ): AiProviderSetting? {
        val provider = synchronized(AiProviderStore) {
            val candidate = readProviderFromForm() ?: return null
            AiProviderStore.saveProvider(candidate, useExistingKeyHere)
        }
        providerFormScreenState = providerFormScreenState
            .copy(apiKeyNeedsReentry = provider.apiKeyNeedsReentry())
            .withProtocolFields(provider, keepPathDraft = keepProtocolPathDraft)
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

    private fun maybeAutoFetchModels(provider: AiProviderSetting) {
        if (providerDetailScreenState.isRefreshingModels) return
        if (provider.isOpenAiAccountProvider() && !provider.enabled) return
        val hasInitializedModels = !provider.catalogFetchRequired() &&
            (provider.catalogLoaded || provider.availableModelSelectionInitialized)
        if (
            !provider.hasRequestCredentials() ||
            hasInitializedModels ||
            (provider.id in autoFetchedModelProviderRevisions &&
                autoFetchedModelProviderRevisions[provider.id] == provider.catalogRevision)
        ) {
            return
        }
        autoFetchedModelProviderRevisions[provider.id] = provider.catalogRevision
        fetchModels()
    }

    private fun fetchModels() {
        if (providerDetailScreenState.isRefreshingModels) return
        providerDetailScreenState = providerDetailScreenState.copy(isRefreshingModels = true)
        val provider = currentProviderId?.let(AiProviderStore::provider)?.takeIf { it.isOpenAiAccountProvider() }
            ?: saveCurrentProvider()
        if (provider == null) {
            providerDetailScreenState = providerDetailScreenState.copy(isRefreshingModels = false)
            return
        }
        if (!provider.hasRequestCredentials()) {
            providerDetailScreenState = providerDetailScreenState.copy(isRefreshingModels = false)
            showAiClassicDialog(
                title = getString(R.string.ai_fetch_models),
                message = getString(
                    when {
                        provider.isOpenAiAccountProvider() -> R.string.ai_chatgpt_sign_in_required
                        provider.apiKeyNeedsReentry() -> R.string.ai_api_key_wrong_origin
                        else -> R.string.ai_api_key_required
                    },
                ),
            )
            return
        }
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val models = AiManager.fetchAndSaveModels(provider.id)
                val current = currentProviderId?.let(AiProviderStore::provider)
                if (current == null || current.id != provider.id || current.catalogRevision != provider.catalogRevision) {
                    return@launch
                }
                if (current.isOpenAiAccountProvider()) chatGptNotice = null
                refreshCurrentDetail()
                val missing = AiAssistantConfigUi.missingSelectedModelLabel(listOf(current))
                val currentIds = models.mapTo(HashSet()) { it.id }
                val removedEnabled = if (provider.catalogLoaded && !provider.catalogRefreshRequired) {
                    provider.effectiveAvailableModelIds().count { it !in currentIds && !current.isManualModel(it) }
                } else 0
                val countMessage = if (removedEnabled > 0) {
                    getString(R.string.ai_model_list_count_with_missing, models.size, removedEnabled)
                } else {
                    getString(R.string.ai_model_list_count, models.size.toString())
                }
                Toast.makeText(
                    requireContext(),
                    missing ?: countMessage,
                    if (missing != null || removedEnabled > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                val current = currentProviderId?.let(AiProviderStore::provider)
                if (current == null || current.id != provider.id || current.catalogRevision != provider.catalogRevision) {
                    return@launch
                }
                if (addModelTarget?.providerId == provider.id) {
                    toastOnUi(R.string.ai_model_refresh_failed)
                    return@launch
                }
                if (current.isOpenAiAccountProvider() && error is AiChatGptException) {
                    chatGptNotice = error.messageRes
                    refreshChatGptDetail()
                    showAiClassicDialog(
                        title = getString(R.string.ai_model_refresh_failed),
                        message = error.message,
                    )
                    return@launch
                }
                showAiClassicDialog(
                    title = getString(R.string.ai_model_refresh_failed),
                    message = if (current.catalogLoaded || current.models.isNotEmpty()) {
                        getString(R.string.ai_model_refresh_kept_previous)
                    } else {
                        getString(R.string.ai_model_refresh_retry)
                    },
                    cancelText = getString(android.R.string.cancel),
                    confirmText = getString(R.string.retry),
                    dismissBeforeConfirm = true,
                    onConfirm = ::fetchModels,
                )
            } finally {
                providerDetailScreenState = providerDetailScreenState.copy(
                    isRefreshingModels = false,
                )
                if (isActive && isAdded && currentPage == Page.DETAIL) {
                    val current = currentProviderId?.let(AiProviderStore::provider)
                    if (current != null && (providerDetailTab == ProviderDetailTab.MODELS || current.isOpenAiAccountProvider()) &&
                        (current.id != provider.id || current.catalogRevision != provider.catalogRevision)) {
                        maybeAutoFetchModels(current)
                    }
                }
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

    private fun confirmResetCurrentProvider() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        showAiClassicDialog(
            title = getString(R.string.ai_provider_reset),
            message = getString(R.string.ai_provider_reset_message),
            cancelText = getString(android.R.string.cancel),
            dismissBeforeConfirm = true,
            onConfirm = {
                if (AiProviderStore.resetProvider(provider.id) != null) {
                    Toast.makeText(
                        requireContext(),
                        R.string.ai_provider_reset_done,
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshCurrentDetail()
                    refreshProviders()
                    refreshMain()
                }
            },
        )
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
        val modelSource = provider?.modelConfigurationKey()
        val presetProvider = provider?.modelPresetProviderId()
        val catalogCurrent = provider?.catalogRefreshRequired == false
        val selectedIds = provider?.effectiveAvailableModelIds().orEmpty().toSet()
        val providerIconRes = provider?.iconRes() ?: R.drawable.ic_cfg_web
        val models = provider?.displayModels().orEmpty().sortSelectedModelsFirst(selectedIds).map { model ->
            val catalogInfo = modelSource?.let { model.effectiveModelInfo(it, presetProvider, catalogCurrent) }
            val effective = model.withCatalogCapabilities(catalogInfo)
            val contextWindow = model.modelSettingsFor(modelSource)?.referenceContextWindow(catalogInfo)
                ?: catalogInfo?.contextWindow?.value
            AiProviderModelItemUiModel(
                id = model.safeId(),
                name = model.displayName(),
                iconRes = model.iconRes(providerIconRes),
                capabilities = effective.capabilityTags(),
                selected = model.safeId() in selectedIds,
                type = effective.providerListType(modelSource),
                searchAliases = listOf(model.safeName(), model.safeOwnedBy()),
                contextWindowLabel = contextWindow?.let { tokens ->
                    getString(R.string.ai_catalog_context_badge, assistantContextWindowLabel(tokens))
                },
                isManual = modelSource != null && model.manualModelEndpoint == modelSource,
            )
        }
        providerDetailScreenState = providerDetailScreenState.copy(
            modelFilter = providerDetailScreenState.modelFilter.orAllFor(models),
            emptyModelNoticeRes = when {
                provider?.apiKeyNeedsReentry() == true -> R.string.ai_api_key_wrong_origin
                provider?.isOpenAiAccountProvider() == true && !provider.hasRequestCredentials() ->
                    R.string.ai_chatgpt_sign_in_required
                provider?.catalogRefreshRequired == true && provider.hasRequestCredentials() ->
                    R.string.ai_model_catalog_refresh_required
                provider?.catalogLoaded == true && provider.models.isEmpty() && provider.hasRequestCredentials() ->
                    R.string.ai_model_catalog_empty
                else -> null
            },
            models = models,
        )
    }

    private fun List<AiModel>.sortSelectedModelsFirst(selectedIds: Set<String>): List<AiModel> {
        if (isEmpty() || selectedIds.isEmpty() || all { it.safeId() in selectedIds }) {
            return this
        }
        return sortedByDescending { it.safeId() in selectedIds }
    }

    private fun setModelsEnabled(ids: Set<String>, enabled: Boolean) {
        val updated = synchronized(AiProviderStore) {
            val provider = currentProviderId?.let(AiProviderStore::provider) ?: return
            val settings = provider.withModelSelection(ids, enabled)
            if (settings === provider) return
            AiProviderStore.saveProvider(settings)
        }
        refreshModelList(updated)
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
            AiModelType.OCR -> listOf(AiModelModality.TEXT, AiModelModality.IMAGE)
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
            AiModelType.EMBEDDING,
            AiModelType.ASR,
            AiModelType.OCR -> listOf(AiModelModality.TEXT)
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
            AiModelType.OCR,
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
        return if (!hasRequestCredentials()) 0 else displayModels().size
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
