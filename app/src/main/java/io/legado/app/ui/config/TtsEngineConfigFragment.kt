package io.legado.app.ui.config

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppConst
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.tts.DEFAULT_TTS_RANDOM_NUMBER_DIGITS
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineImportConflictAction
import io.legado.app.help.tts.TtsEngineImportConflictException
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsScriptOption
import io.legado.app.help.tts.TtsScriptOptionValue
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.TtsVoiceStyle
import io.legado.app.help.tts.generateTtsRandomNumber
import io.legado.app.help.tts.styleOptions
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class TtsEngineConfigFragment : BaseFragment(0),
    ConfigBackHandler {

    private enum class DetailTab { CONFIG, VOICES }

    private val configEntities = arrayListOf<ConfigField>()
    private var currentEngineId: String? = null
    private var detailEngineSnapshot: TtsEngineSetting? = null
    private var draftEngine: TtsEngineSetting? = null
    private var configOptionsJob: Job? = null
    private var configOptionsWarmJob: Job? = null
    private var configOptionsLoadedScript: String? = null
    private var scriptCodeLoadedEngineId: String? = null
    private var formDirty = false
    private var screenRoute by mutableStateOf(TtsEngineConfigRoute.ENGINE_LIST)
    private val sourceMode: Boolean
        get() = screenRoute == TtsEngineConfigRoute.SCRIPT_SOURCE
    private var allVoices: List<TtsVoice> = emptyList()
    private var voiceSearchQuery: String = ""
    private var voicePreviewController: TtsVoicePreviewController? = null
    private var voiceParamPanelExpanded by mutableStateOf(false)
    private var voiceParamPanelState by mutableStateOf(TtsVoiceParamPanelState())
    private var importConflictDialog: Dialog? = null
    private var modalDialog: Dialog? = null
    private var showDisabledEngines = LocalConfig.ttsEngineListShowDisabled
    private var engineScreenState by mutableStateOf(TtsEngineListScreenState())
    private var engineFormScreenState by mutableStateOf(TtsEngineFormScreenState())
    private var voiceListScreenState by mutableStateOf(TtsEngineVoiceListScreenState())
    private var voiceControlsState by mutableStateOf(TtsEngineVoiceControlsState())
    private var engineSettingsSnapshot: List<TtsEngineSetting> = emptyList()
    private var engineOrderSaveJob: Job? = null
    private var engineConfigSaveJob: Job? = null
    private var engineConfigSaveRevision = 0L
    private var engineRefreshJob: Job? = null
    private var voiceRefreshJob: Job? = null
    private var voiceRefreshRevision = 0L
    private var engineRefreshing by mutableStateOf(false)
    private var voiceRefreshing by mutableStateOf(false)
    private var voiceRefreshEnabled by mutableStateOf(false)
    private var rootComposeView: ComposeView? = null
    private var scriptEditorView: CodeView? = null
    private val engineSnapshotGate = TtsEngineSnapshotGate()
    private val autoFetchedVoiceEngineIds = hashSetOf<String>()
    private val selectedVoiceLanguageFilters = linkedSetOf<String>()
    private val selectedVoiceGenderFilters = linkedSetOf<String>()
    private val importTtsEngineFileLauncher = registerForActivityResult(
        SelectFileContract()
    ) { uri ->
        uri ?: return@registerForActivityResult
        importTtsEngineFromUri(uri)
    }

    private data class ConfigField(
        val key: String,
        var value: String?,
        val label: String,
        val type: String = "text",
        val values: List<TtsScriptOptionValue> = emptyList(),
        val randomNumberDigits: Int = DEFAULT_TTS_RANDOM_NUMBER_DIGITS,
        val randomNumberAllowsLeadingZero: Boolean = false,
        var passwordVisible: Boolean = false
    ) {
        val isOption: Boolean get() = key.startsWith("option:")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun ConfigField.toFormScreenField(): TtsEngineFormFieldState {
        val currentValue = value.orEmpty()
        val formType = type.toTtsEngineFormFieldType()
        val formOptions = if (formType == TtsEngineFormFieldType.SELECT) {
            buildTtsEngineFormOptions(
                currentValue = currentValue,
                options = values.map { TtsEngineFormOption(it.label, it.value) }
            )
        } else {
            emptyList()
        }
        return TtsEngineFormFieldState(
            key = key,
            label = label,
            value = currentValue,
            type = formType,
            options = formOptions,
            randomNumberDigits = randomNumberDigits,
            randomNumberAllowsLeadingZero = randomNumberAllowsLeadingZero
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.tts_engine_settings)
        val editor = createScriptEditorView()
        scriptEditorView = editor
        (view as ComposeView).apply {
            rootComposeView = this
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    TtsEngineConfigScreen(
                        route = screenRoute,
                        engineRefreshing = engineRefreshing,
                        voiceRefreshing = voiceRefreshing,
                        voiceRefreshEnabled = voiceRefreshEnabled,
                        scriptEditorView = editor,
                        onRefreshEngines = ::requestEngineRefresh,
                        onRefreshVoices = ::fetchVoices,
                        engineListContent = { listState ->
                            TtsEngineListScreen(
                                state = engineScreenState,
                                onAction = ::handleEngineListAction,
                                listState = listState,
                            )
                        },
                        formContent = {
                            TtsEngineFormScreen(
                                state = engineFormScreenState,
                                onAction = ::handleEngineFormAction,
                            )
                        },
                        formActions = { inSourceMode ->
                            TtsEngineFormActions(
                                sourceMode = inSourceMode,
                                onToggleSourceMode = {
                                    showConfigSourceMode(!inSourceMode)
                                },
                                onMeasureLatency = ::measureCurrentEngineLatency,
                                onSaveSource = { saveSourceEngine() },
                            )
                        },
                        voiceControlsContent = {
                            TtsEngineVoiceControlsScreen(
                                state = voiceControlsState,
                                paramPanelState = voiceParamPanelState,
                                paramPanelExpanded = voiceParamPanelExpanded,
                                onBack = { onConfigBackPressed() },
                                onQueryChange = ::updateVoiceSearchQuery,
                                onOpenParams = ::toggleVoiceParamPanel,
                                onDismissParams = { voiceParamPanelExpanded = false },
                                onParamSpeedChange = { value ->
                                    voiceParamPanelState = voiceParamPanelState.copy(speed = value)
                                },
                                onParamVolumeChange = { value ->
                                    voiceParamPanelState = voiceParamPanelState.copy(volume = value)
                                },
                                onParamPitchChange = { value ->
                                    voiceParamPanelState = voiceParamPanelState.copy(pitch = value)
                                },
                                onParamValueChangeFinished = ::saveVoiceParamPanel,
                                onToggleLanguage = ::toggleVoiceLanguageFilter,
                                onToggleGender = ::toggleVoiceGenderFilter,
                                onToggleAll = ::toggleAllVoicesEnabled,
                                onSystemSpeedChange = { value ->
                                    voiceControlsState = voiceControlsState.copy(speed = value)
                                },
                                onSystemPitchChange = { value ->
                                    voiceControlsState = voiceControlsState.copy(pitch = value)
                                },
                                onSystemValueChangeFinished = ::saveSystemVoiceParams,
                            )
                        },
                        voiceListContent = { listState ->
                            TtsEngineVoiceListScreen(
                                state = voiceListScreenState,
                                onAction = ::handleVoiceListAction,
                                listState = listState,
                            )
                        },
                        detailTabsContent = {
                            TtsEngineDetailTabBar(
                                selectedIndex = screenRoute.detailTabIndex,
                                onSelected = { index ->
                                    DetailTab.entries.getOrNull(index)?.let(::showDetailTab)
                                },
                            )
                        },
                    )
                }
            }
        }
        navigateToRoute(TtsEngineConfigRoute.ENGINE_LIST)
        voicePreviewController = TtsVoicePreviewController(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            onStatusChanged = { status ->
                voiceListScreenState = voiceListScreenState.copy(preview = status)
            }
        )

        refreshEngines()
    }

    override fun onDestroyView() {
        saveFormChangesIfNeeded()
        configOptionsJob?.cancel()
        configOptionsJob = null
        configOptionsWarmJob?.cancel()
        configOptionsWarmJob = null
        cancelEngineRefresh()
        cancelVoiceRefresh()
        voiceRefreshEnabled = false
        voiceParamPanelExpanded = false
        importConflictDialog?.dismiss()
        importConflictDialog = null
        modalDialog?.dismiss()
        modalDialog = null
        voicePreviewController?.release()
        voicePreviewController = null
        rootComposeView = null
        scriptEditorView = null
        setSharedTitleBarVisible(true)
        super.onDestroyView()
    }

    override fun onConfigBackPressed(): Boolean {
        return when (screenRoute.backDestination()) {
            TtsEngineConfigRoute.SCRIPT_FORM -> {
                showConfigSourceMode(false)
                true
            }

            TtsEngineConfigRoute.ENGINE_LIST -> {
                showEngineList()
                true
            }

            else -> false
        }
    }

    private fun refreshEngines() {
        cancelEngineRefresh()
        if (engineSettingsSnapshot.isNotEmpty()) {
            applyEngineSnapshot(engineSettingsSnapshot)
        }
        startEngineRefresh(forceReload = false, showIndicator = false)
    }

    private fun requestEngineRefresh() {
        cancelEngineRefresh()
        startEngineRefresh(forceReload = true, showIndicator = true)
    }

    private fun cancelEngineRefresh() {
        engineSnapshotGate.invalidate()
        engineRefreshJob?.cancel()
        engineRefreshJob = null
        engineRefreshing = false
    }

    private fun cancelVoiceRefresh() {
        voiceRefreshRevision++
        voiceRefreshJob?.cancel()
        voiceRefreshJob = null
        voiceRefreshing = false
    }

    private fun startEngineRefresh(
        forceReload: Boolean,
        showIndicator: Boolean,
    ) {
        if (engineRefreshJob?.isActive == true) return
        val snapshotToken = engineSnapshotGate.begin()
        val refreshJob = lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (showIndicator) {
                    // 缓存命中可能在同一帧完成；至少跨过一帧，让 PullToRefreshBox
                    // 观察到 refreshing=true 后再收口，否则指示器会停在触发位置。
                    delay(24L)
                }
                awaitEngineOrderSaves()
                val allEngines = withContext(Dispatchers.IO) {
                    if (forceReload) {
                        TtsEngineStore.reloadEngines()
                    } else {
                        TtsEngineStore.engines()
                    }
                }
                if (engineSnapshotGate.isCurrent(snapshotToken)) {
                    applyEngineSnapshot(allEngines)
                    prewarmNextEdgeOptions(allEngines)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (engineSnapshotGate.isCurrent(snapshotToken)) {
                    context?.toastOnUi(
                        "刷新朗读引擎失败：${e.localizedMessage ?: e.javaClass.simpleName}"
                    )
                }
            } finally {
                if (engineRefreshJob === coroutineContext[Job]) {
                    engineRefreshing = false
                    engineRefreshJob = null
                }
            }
        }
        engineRefreshJob = refreshJob
        engineRefreshing = showIndicator
        refreshJob.start()
    }

    private suspend fun awaitEngineOrderSaves() {
        while (true) {
            val pendingSave = engineOrderSaveJob ?: return
            pendingSave.join()
            if (pendingSave === engineOrderSaveJob) return
        }
    }

    private fun prewarmNextEdgeOptions(engines: List<TtsEngineSetting>) {
        val engine = engines.firstOrNull {
            it.id == TtsEngineStore.NEXT_EDGE_PROXY_ID &&
                it.enabled &&
                it.type == TtsEngineType.SCRIPT
        } ?: return
        if (TtsScriptEngineClient.cachedOptions(engine) != null) return
        configOptionsWarmJob?.cancel()
        configOptionsWarmJob = lifecycleScope.launch(Dispatchers.Default) {
            runCatching { TtsScriptEngineClient.loadOptions(engine) }
        }
    }

    private fun applyEngineSnapshot(allEngines: List<TtsEngineSetting>) {
        engineSettingsSnapshot = allEngines
        autoFetchedVoiceEngineIds.retainAll(allEngines.mapTo(hashSetOf()) { it.id })
        val visibleEngines = ConfigListVisibilitySupport.visibleItems(
            allItems = allEngines,
            showDisabled = showDisabledEngines,
            isEnabled = TtsEngineSetting::enabled
        )
            .filter { engine ->
                engineScreenState.query.isBlank() ||
                    engine.name.contains(engineScreenState.query, ignoreCase = true)
            }
        engineScreenState = engineScreenState.copy(
            listState = NgListState.Content(
                visibleEngines.map { it.toListItemUiModel() }
            ),
            showDisabled = showDisabledEngines
        )
    }

    private fun TtsEngineSetting.toListItemUiModel(): TtsEngineListItemUiModel {
        return TtsEngineListItemUiModel(
            id = id,
            name = name,
            enabled = enabled,
            engineTypeText = getString(
                when (type) {
                    TtsEngineType.SYSTEM -> R.string.tts_engine_type_system
                    TtsEngineType.SCRIPT -> R.string.tts_engine_type_script
                }
            ),
            voiceCountText = when {
                type == TtsEngineType.SYSTEM ->
                    getString(R.string.character_tts_system_default_voice)
                effectiveVoices().isEmpty() ->
                    getString(R.string.tts_engine_voice_not_loaded)
                else -> getString(R.string.tts_engine_voice_count, effectiveVoices().size)
            },
            reorderable = true,
            deletable = TtsEngineStore.isDeletableEngine(this),
            actionContentDescription = getString(R.string.tts_engine_drag_sort)
        )
    }

    private fun handleEngineListAction(action: TtsEngineListAction) {
        when (action) {
            is TtsEngineListAction.QueryChanged -> {
                engineScreenState = engineScreenState.copy(query = action.query)
                applyEngineSnapshot(engineSettingsSnapshot)
            }

            is TtsEngineListAction.SearchSubmitted -> Unit
            is TtsEngineListAction.OpenEngine -> {
                engineSettingsSnapshot
                    .firstOrNull { it.id == action.engineId }
                    ?.let(::showEngineDetail)
            }

            is TtsEngineListAction.ReorderCommitted -> {
                commitEngineOrder(action.orderedEngineIds)
            }

            is TtsEngineListAction.DeleteRequested -> {
                TtsEngineStore.engine(action.engineId)?.let { engine ->
                    confirmDeleteEngine(
                        engine = engine,
                        onCancel = ::refreshEngines,
                        onDeleted = {
                            autoFetchedVoiceEngineIds.remove(engine.id)
                            refreshEngines()
                        }
                    )
                }
            }

            TtsEngineListAction.Retry -> requestEngineRefresh()
            TtsEngineListAction.Back -> requireActivity().onBackPressedDispatcher.onBackPressed()

            TtsEngineListAction.CreateEngine -> addTtsEngine()
            TtsEngineListAction.ImportLocal -> {
                importTtsEngineFileLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/json",
                        "application/javascript",
                        "application/octet-stream"
                    )
                )
            }

            TtsEngineListAction.ImportOnline -> showImportTtsEngineUrlDialog()
            TtsEngineListAction.ToggleShowDisabled -> {
                toggleShowDisabledEngines()
            }
        }
    }

    private fun toggleShowDisabledEngines() {
        showDisabledEngines = !showDisabledEngines
        LocalConfig.ttsEngineListShowDisabled = showDisabledEngines
        refreshEngines()
    }

    private fun commitEngineOrder(orderedEngineIds: List<String>) {
        if (engineScreenState.query.isNotBlank()) return
        val allEngines = engineSettingsSnapshot
        if (allEngines.isEmpty()) {
            refreshEngines()
            return
        }
        val visibleEngines = ConfigListVisibilitySupport.visibleItems(
            allItems = allEngines,
            showDisabled = showDisabledEngines,
            isEnabled = TtsEngineSetting::enabled
        )
        val visibleIds = visibleEngines.map(TtsEngineSetting::id)
        if (orderedEngineIds == visibleIds) return
        if (orderedEngineIds.size != visibleIds.size ||
            orderedEngineIds.toSet().size != orderedEngineIds.size ||
            orderedEngineIds.toSet() != visibleIds.toSet()
        ) {
            refreshEngines()
            return
        }
        val enginesById = visibleEngines.associateBy(TtsEngineSetting::id)
        val reorderedEngines = orderedEngineIds.mapNotNull(enginesById::get)
        val mergedEngines = ConfigListVisibilitySupport.mergeVisibleOrder(
            allItems = allEngines,
            reorderedVisibleItems = reorderedEngines,
            showDisabled = showDisabledEngines,
            isEnabled = TtsEngineSetting::enabled
        )
        cancelEngineRefresh()
        engineSettingsSnapshot = mergedEngines
        engineScreenState = engineScreenState.copy(
            listState = NgListState.Content(
                ConfigListVisibilitySupport.visibleItems(
                    allItems = mergedEngines,
                    showDisabled = showDisabledEngines,
                    isEnabled = TtsEngineSetting::enabled
                ).map { it.toListItemUiModel() }
            )
        )
        val previousSave = engineOrderSaveJob
        engineOrderSaveJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                previousSave?.join()
                if (!TtsEngineStore.saveVisibleEngineOrder(orderedEngineIds)) {
                    withContext(Dispatchers.Main) {
                        refreshEngines()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    refreshEngines()
                    context?.toastOnUi(
                        "保存朗读引擎顺序失败：${e.localizedMessage ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    private fun currentDisplayedEngine(): TtsEngineSetting? {
        val id = currentEngineId ?: return null
        return detailEngineSnapshot?.takeIf { it.id == id }
            ?: draftEngine?.takeIf { it.id == id }
            ?: TtsEngineStore.engine(id)
    }

    private fun showEngineList() {
        saveFormChangesIfNeeded()
        clearEngineFormFocus()
        cancelVoiceRefresh()
        voiceRefreshEnabled = false
        voiceParamPanelExpanded = false
        configOptionsJob?.cancel()
        configOptionsJob = null
        configOptionsLoadedScript = null
        currentEngineId = null
        detailEngineSnapshot = null
        draftEngine = null
        formDirty = false
        scriptCodeLoadedEngineId = null
        setScriptCodeText("")
        activity?.setTitle(R.string.tts_engine_settings)
        navigateToRoute(TtsEngineConfigRoute.ENGINE_LIST)
        refreshEngines()
    }

    private fun showEngineDetail(engine: TtsEngineSetting, tab: DetailTab = DetailTab.CONFIG) {
        clearEngineFormFocus()
        val isSwitchingEngine = currentEngineId != engine.id
        if (isSwitchingEngine) {
            cancelVoiceRefresh()
        }
        configOptionsJob?.cancel()
        configOptionsJob = null
        configOptionsLoadedScript = null
        currentEngineId = engine.id
        detailEngineSnapshot = engine
        formDirty = false
        if (draftEngine?.id != engine.id) {
            draftEngine = null
        }
        if (isSwitchingEngine) {
            scriptCodeLoadedEngineId = null
            setScriptCodeText("")
        }
        activity?.setTitle(engine.name)
        if (isSwitchingEngine) {
            updateVoiceSearchQuery("")
        }
        if (engine.type == TtsEngineType.SYSTEM) {
            bindSystemEngineDetail(engine)
            return
        }
        bindEngineForm(engine)
        bindVoiceParams(engine)
        setVoiceItems(engine.effectiveVoices())
        updateVoiceMessage(engine)
        showDetailTab(tab)
    }

    private fun addTtsEngine() {
        val engine = TtsEngineStore.createCustomScriptEngine()
        draftEngine = engine
        showEngineDetail(engine)
        showConfigSourceMode(true)
    }

    private fun importTtsEngineFromUri(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    uri.readText(requireContext())
                }
            }
            result.onSuccess { importTtsEngineText(it) }
                .onFailure { requireContext().toastOnUi(it.localizedMessage ?: "导入失败") }
        }
    }

    private fun showImportTtsEngineUrlDialog() {
        modalDialog?.dismiss()
        val dialog = ComponentDialog(requireContext())
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    TtsImportUrlDialogContent(
                        initialValue = "",
                        onCancel = dialog::dismiss,
                        onConfirm = { value ->
                            dialog.dismiss()
                            importTtsEngineFromUrl(value)
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnDismissListener {
            if (modalDialog === dialog) {
                modalDialog = null
            }
        }
        modalDialog = dialog
        dialog.show()
        dialog.applyNgWindow()
    }

    private fun importTtsEngineFromUrl(url: String) {
        val target = url.trim()
        if (!target.isAbsUrl()) {
            requireContext().toastOnUi(getString(R.string.wrong_format))
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    okHttpClient.newCallResponseBody {
                        if (target.endsWith("#requestWithoutUA")) {
                            url(target.substringBeforeLast("#requestWithoutUA"))
                            header(AppConst.UA_NAME, "null")
                        } else {
                            url(target)
                        }
                    }.decompressed().text()
                }
            }
            result.onSuccess { importTtsEngineText(it) }
                .onFailure { requireContext().toastOnUi(it.localizedMessage ?: "导入失败") }
        }
    }

    private fun importTtsEngineText(
        content: String,
        conflictAction: TtsEngineImportConflictAction = TtsEngineImportConflictAction.ASK
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TtsEngineStore.importEngineText(content, conflictAction)
            }
            handleTtsEngineImportResult(result, content)
        }
    }

    private fun handleTtsEngineImportResult(
        result: Result<List<TtsEngineSetting>>,
        content: String
    ) {
        result.onSuccess { engines ->
            autoFetchedVoiceEngineIds.removeAll(engines.map { it.id }.toSet())
            refreshEngines()
            requireContext().toastOnUi("已导入 ${engines.size} 个朗读引擎")
        }.onFailure {
            val conflict = it as? TtsEngineImportConflictException
            if (conflict != null) {
                showTtsEngineImportConflictDialog(content, conflict)
            } else {
                requireContext().toastOnUi(it.localizedMessage ?: "导入失败")
            }
        }
    }

    private fun showTtsEngineImportConflictDialog(
        content: String,
        conflict: TtsEngineImportConflictException
    ) {
        importConflictDialog?.dismiss()
        val names = conflict.conflicts
            .map { it.existingName }
            .distinct()
            .take(3)
            .joinToString("、") { "“$it”" }
        val message = getString(
            R.string.tts_engine_import_conflict_message,
            names
        )
        val dialog = ComponentDialog(requireContext())
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    TtsImportConflictDialogContent(
                        message = message,
                        canOverwrite = conflict.conflicts.all { it.canOverwrite },
                        onKeepBoth = {
                            dialog.dismiss()
                            importTtsEngineText(content, TtsEngineImportConflictAction.KEEP_BOTH)
                        },
                        onOverwrite = {
                            dialog.dismiss()
                            importTtsEngineText(content, TtsEngineImportConflictAction.OVERWRITE)
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnDismissListener {
            if (importConflictDialog === dialog) {
                importConflictDialog = null
            }
        }
        importConflictDialog = dialog
        dialog.show()
        dialog.applyNgWindow()
    }

    private fun bindSystemEngineDetail(engine: TtsEngineSetting) {
        cancelVoiceRefresh()
        voiceRefreshEnabled = false
        voiceParamPanelExpanded = false
        voiceControlsState = TtsEngineVoiceControlsState(
            mode = TtsEngineVoiceControlsMode.SYSTEM,
            speed = engine.effectiveSpeed(),
            pitch = engine.effectivePitch(),
        )
        setVoiceItems(listOf(systemDefaultVoice(engine)))
        navigateToRoute(TtsEngineConfigRoute.SYSTEM_DETAIL)
    }

    private fun showDetailTab(tab: DetailTab) {
        if (tab != DetailTab.CONFIG) {
            saveFormChangesIfNeeded()
            clearEngineFormFocus()
        }
        if (tab != DetailTab.VOICES) {
            voiceParamPanelExpanded = false
        }
        navigateToRoute(
            if (tab == DetailTab.CONFIG) {
                TtsEngineConfigRoute.SCRIPT_FORM
            } else {
                TtsEngineConfigRoute.SCRIPT_VOICES
            }
        )
        if (tab == DetailTab.VOICES) {
            voiceControlsState = voiceControlsState.copy(
                mode = TtsEngineVoiceControlsMode.SCRIPT,
                query = voiceSearchQuery,
            )
        }
        if (tab == DetailTab.VOICES) {
            maybeAutoFetchVoices()
        }
    }

    private fun navigateToRoute(route: TtsEngineConfigRoute) {
        screenRoute = route
        setSharedTitleBarVisible(route.showsSharedTitleBar)
    }

    private fun setSharedTitleBarVisible(visible: Boolean) {
        activity?.findViewById<TitleBar>(R.id.title_bar)?.isVisible = visible
    }

    private fun bindEngineForm(engine: TtsEngineSetting) {
        bindConfigEntities(engine)
        if (sourceMode) {
            ensureScriptCodeLoaded(engine)
        }

        val scriptEnabled = engine.type == TtsEngineType.SCRIPT
        engineFormScreenState = engineFormScreenState.copy(
            engineId = engine.id,
            engineEnabled = engine.enabled,
            formEnabled = scriptEnabled
        )
        scriptEditorView?.isEnabled = scriptEnabled
        voiceRefreshEnabled = engine.supportsVoiceFetch()
    }

    private fun handleEngineFormAction(action: TtsEngineFormScreenAction) {
        val actionEngineId = when (action) {
            is TtsEngineFormScreenAction.FieldChanged -> action.engineId
            is TtsEngineFormScreenAction.FieldEditFinished -> action.engineId
            is TtsEngineFormScreenAction.RandomNumberRegenerateRequested -> action.engineId
            is TtsEngineFormScreenAction.EngineEnabledChanged -> action.engineId
        }
        if (
            actionEngineId != currentEngineId ||
            actionEngineId != engineFormScreenState.engineId
        ) {
            return
        }
        when (action) {
            is TtsEngineFormScreenAction.FieldChanged -> {
                configEntities.firstOrNull { it.key == action.key }?.value = action.value
                engineFormScreenState = engineFormScreenState.withFieldValue(
                    action.key,
                    action.value
                )
                formDirty = true
                val fieldType = engineFormScreenState.fields
                    .firstOrNull { it.key == action.key }
                    ?.type
                if (fieldType?.let(::shouldSaveTtsEngineFieldImmediately) == true) {
                    saveFormChangesIfNeeded()
                }
            }

            is TtsEngineFormScreenAction.FieldEditFinished -> {
                saveFormChangesIfNeeded()
            }

            is TtsEngineFormScreenAction.RandomNumberRegenerateRequested -> {
                val field = engineFormScreenState.fields.firstOrNull {
                    it.key == action.key && it.type == TtsEngineFormFieldType.RANDOM_NUMBER
                } ?: return
                val randomNumber = generateTtsRandomNumber(
                    digits = field.randomNumberDigits,
                    allowLeadingZero = field.randomNumberAllowsLeadingZero
                )
                configEntities.firstOrNull { it.key == action.key }?.value = randomNumber
                engineFormScreenState = engineFormScreenState.withFieldValue(
                    action.key,
                    randomNumber
                )
                formDirty = true
                saveFormChangesIfNeeded()
                requireContext().toastOnUi(R.string.tts_random_number_regenerated)
            }

            is TtsEngineFormScreenAction.EngineEnabledChanged -> {
                engineFormScreenState = engineFormScreenState.copy(
                    engineEnabled = action.checked
                )
                formDirty = true
                saveFormChangesIfNeeded()
            }
        }
    }

    private fun saveEnabledState(enabled: Boolean) {
        val source = currentDisplayedEngine() ?: return
        if (source.enabled == enabled) {
            return
        }
        val updated = source.copy(enabled = enabled)
        detailEngineSnapshot = updated
        engineFormScreenState = engineFormScreenState.copy(engineEnabled = enabled)
        TtsEngineStore.saveEngine(updated)
    }

    private fun showConfigSourceMode(enabled: Boolean) {
        if (enabled) {
            voiceParamPanelExpanded = false
        }
        if (enabled) {
            clearEngineFormFocus()
            saveFormChangesIfNeeded()
        }
        if (enabled && !sourceMode) {
            currentDisplayedEngine()?.let(::ensureScriptCodeLoaded)
        } else if (!enabled && sourceMode) {
            val source = currentDisplayedEngine() ?: return
            bindConfigEntities(source)
            engineFormScreenState = engineFormScreenState.copy(
                engineEnabled = source.enabled
            )
            activity?.setTitle(source.name)
            formDirty = false
        }
        navigateToRoute(
            if (enabled) {
                TtsEngineConfigRoute.SCRIPT_SOURCE
            } else {
                TtsEngineConfigRoute.SCRIPT_FORM
            }
        )
    }

    private fun clearEngineFormFocus() {
        rootComposeView?.clearFocus()
        rootComposeView?.hideSoftInput()
    }

    private fun createScriptEditorView(): CodeView {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).roundToInt()
        return CodeView(requireContext()).apply {
            setMaxHighlightLength(128 * 1024)
            addJsPattern()
            background = AppCompatResources.getDrawable(
                context,
                R.drawable.ng_bg_outlined_field,
            )
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minimumHeight = dp(220)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
    }

    private fun setScriptCodeText(script: String?) {
        val text = script.orEmpty()
        if (text.isBlank()) {
            scriptEditorView?.setText("")
        } else {
            scriptEditorView?.setTextHighlighted(text)
        }
    }

    private fun ensureScriptCodeLoaded(engine: TtsEngineSetting) {
        if (scriptCodeLoadedEngineId == engine.id) {
            return
        }
        setScriptCodeText(engine.script)
        scriptCodeLoadedEngineId = engine.id
    }

    private fun bindConfigEntities(engine: TtsEngineSetting) {
        configOptionsJob?.cancel()
        configOptionsLoadedScript = null
        if (engine.type != TtsEngineType.SCRIPT) {
            applyConfigEntities(engine, emptyList())
            return
        }
        TtsScriptEngineClient.cachedOptions(engine)?.let { options ->
            applyConfigEntities(engine, options)
            configOptionsLoadedScript = engine.script
            return
        }
        engineFormScreenState = TtsEngineFormScreenState(
            engineId = engine.id,
            engineEnabled = engine.enabled,
            formEnabled = false,
            loading = true,
        )
        val requestedEngineId = engine.id
        val requestedScript = engine.script
        configOptionsJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { TtsScriptEngineClient.loadOptions(engine) }
            }
            val current = currentDisplayedEngine() ?: return@launch
            if (
                currentEngineId != requestedEngineId ||
                current.script != requestedScript
            ) {
                return@launch
            }
            val options = result.getOrElse {
                applyConfigEntities(current, emptyList())
                configOptionsLoadedScript = null
                return@launch
            }
            val currentName = configValue("name").ifBlank { current.name }
            applyConfigEntities(current.copy(name = currentName), options)
            configOptionsLoadedScript = requestedScript
        }
    }

    private fun applyConfigEntities(
        engine: TtsEngineSetting,
        options: List<TtsScriptOption>
    ) {
        val entities = arrayListOf(
            ConfigField("name", engine.name, getString(R.string.name))
        )
        val values = engine.effectiveOptionValues(options)
        options.forEach { option ->
            val type = option.normalizedType
            val value = normalizeTtsEngineFormFieldValue(
                type = type,
                value = values[option.safeKey].orEmpty(),
                digits = option.randomNumberDigits,
                allowLeadingZero = option.randomNumberAllowsLeadingZero
            )
            entities.add(
                ConfigField(
                    "option:${option.safeKey}",
                    value,
                    option.displayLabel,
                    type,
                    option.safeValues,
                    option.randomNumberDigits,
                    option.randomNumberAllowsLeadingZero
                )
            )
        }
        configEntities.clear()
        configEntities.addAll(entities)
        engineFormScreenState = TtsEngineFormScreenState(
            engineId = engine.id,
            engineEnabled = engine.enabled,
            formEnabled = engine.type == TtsEngineType.SCRIPT,
            loading = false,
            fields = entities.map { it.toFormScreenField() }
        )
    }

    private fun saveFormChangesIfNeeded(
        restartReadAloud: Boolean = true
    ): TtsEngineSetting? {
        val source = currentDisplayedEngine()
            ?: draftEngine?.takeIf { it.id == currentEngineId }
            ?: return null
        if (sourceMode || !formDirty) {
            return source
        }
        val effective = enqueueEngineSave(
            updated = engineFromForm(source),
            restartReadAloud = restartReadAloud,
            showToast = false
        )
        formDirty = false
        return effective
    }

    private fun saveSourceEngine(): TtsEngineSetting? {
        val source = currentDisplayedEngine()
            ?: draftEngine?.takeIf { it.id == currentEngineId }
            ?: return null
        val script = scriptEditorView?.text?.toString()?.takeIf { it.isNotBlank() }
        if (script == null) {
            requireContext().toastOnUi("源码不能为空")
            return null
        }
        val effective = enqueueEngineSave(
            updated = engineFromState(source, script),
            restartReadAloud = true,
            showToast = true
        )
        scriptCodeLoadedEngineId = effective.id
        bindConfigEntities(effective)
        formDirty = false
        return effective
    }

    private fun enqueueEngineSave(
        updated: TtsEngineSetting,
        restartReadAloud: Boolean,
        showToast: Boolean
    ): TtsEngineSetting {
        detailEngineSnapshot = updated
        activity?.setTitle(updated.name)
        bindVoiceParams(updated)

        val revision = ++engineConfigSaveRevision
        val previousSave = engineConfigSaveJob
        engineConfigSaveJob = lifecycleScope.launch {
            try {
                previousSave?.join()
                val effective = withContext(Dispatchers.IO) {
                    TtsEngineStore.saveEngine(updated, restartReadAloud)
                    TtsEngineStore.engine(updated.id) ?: updated
                }
                if (revision == engineConfigSaveRevision) {
                    if (currentEngineId == updated.id) {
                        detailEngineSnapshot = effective
                        draftEngine = null
                        activity?.setTitle(effective.name)
                        bindVoiceParams(effective)
                    }
                    refreshEngines()
                    if (showToast) {
                        context?.toastOnUi("保存成功")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (revision == engineConfigSaveRevision) {
                    if (currentEngineId == updated.id && !sourceMode) {
                        formDirty = true
                    }
                    context?.toastOnUi(
                        "保存失败：${error.localizedMessage ?: error.javaClass.simpleName}"
                    )
                }
            }
        }
        return updated
    }

    private suspend fun awaitEngineConfigSaves() {
        while (true) {
            val pendingSave = engineConfigSaveJob ?: return
            pendingSave.join()
            if (pendingSave === engineConfigSaveJob) return
        }
    }

    private fun engineFromForm(source: TtsEngineSetting): TtsEngineSetting {
        return engineFromState(source, source.script)
    }

    private fun engineFromState(
        source: TtsEngineSetting,
        script: String
    ): TtsEngineSetting {
        val metadata = TtsEngineStore.parseScriptMetadata(script)
        val displayedOptionValues = configEntities
            .filter { it.isOption }
            .associate { it.key.removePrefix("option:") to it.value.orEmpty() }
        val optionValues = mergeTtsEngineOptionValues(
            sourceValues = source.optionValues,
            displayedValues = displayedOptionValues,
            schemaMatchesCurrentScript = configOptionsLoadedScript == script
        )
        return source.copy(
            name = configValue("name").ifBlank { source.name },
            enabled = engineFormScreenState.engineEnabled,
            script = script,
            sampleText = metadata["sampletext"]?.takeIf { it.isNotBlank() },
            optionValues = optionValues,
            enabledCookieJar = cookieJarFromScript(source, script)
        )
    }

    private fun cookieJarFromScript(
        source: TtsEngineSetting,
        script: String
    ): Boolean? {
        return when (
            TtsEngineStore.parseScriptMetadata(script)["cookiejar"]
                ?.trim()
                ?.lowercase()
        ) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> source.enabledCookieJar
        }
    }

    private fun configValue(key: String): String {
        return configEntities.firstOrNull { it.key == key }?.value?.trim().orEmpty()
    }

    private fun bindVoiceParams(engine: TtsEngineSetting) {
        voiceParamPanelState = TtsVoiceParamPanelState(
            speed = engine.effectiveSpeed(),
            volume = engine.effectiveVolume(),
            pitch = engine.effectivePitch(),
            languages = availableVoiceLanguageLabels(),
            selectedLanguages = selectedVoiceLanguageFilters.toSet(),
            selectedGenders = selectedVoiceGenderFilters.toSet(),
            showFilters = engine.type != TtsEngineType.SYSTEM,
            showGenderFilters = allVoices.isNotEmpty(),
        )
    }

    private fun saveSystemVoiceParams() {
        val engine = currentDisplayedEngine()?.takeIf { it.type == TtsEngineType.SYSTEM }
            ?: return
        TtsEngineStore.saveRuntimeParams(
            engineId = engine.id,
            speed = voiceControlsState.speed,
            volume = engine.effectiveVolume(),
            pitch = voiceControlsState.pitch,
        )
    }

    private fun toggleVoiceParamPanel() {
        val engine = currentDisplayedEngine() ?: return
        if (!voiceParamPanelExpanded) {
            bindVoiceParams(engine)
        }
        voiceParamPanelExpanded = !voiceParamPanelExpanded
    }

    private fun saveVoiceParamPanel() {
        val engineId = currentEngineId ?: return
        TtsEngineStore.saveRuntimeParams(
            engineId = engineId,
            speed = voiceParamPanelState.speed,
            volume = voiceParamPanelState.volume,
            pitch = voiceParamPanelState.pitch,
        )?.let { updated ->
            detailEngineSnapshot = updated
        }
    }

    private fun toggleVoiceLanguageFilter(label: String) {
        if (!selectedVoiceLanguageFilters.add(label)) {
            selectedVoiceLanguageFilters.remove(label)
        }
        voiceParamPanelState = voiceParamPanelState.copy(
            selectedLanguages = selectedVoiceLanguageFilters.toSet()
        )
        applyVoiceFilter()
    }

    private fun toggleVoiceGenderFilter(label: String) {
        if (!selectedVoiceGenderFilters.add(label)) {
            selectedVoiceGenderFilters.remove(label)
        }
        voiceParamPanelState = voiceParamPanelState.copy(
            selectedGenders = selectedVoiceGenderFilters.toSet()
        )
        applyVoiceFilter()
    }

    private fun pruneVoiceFilters() {
        selectedVoiceLanguageFilters.retainAll(availableVoiceLanguageLabels().toSet())
    }

    private fun availableVoiceLanguageLabels(): List<String> {
        return TtsVoiceFilterSupport.availableLanguageLabels(allVoices)
    }

    private fun updateVoiceSearchQuery(query: String) {
        voiceSearchQuery = query.trim()
        voiceControlsState = voiceControlsState.copy(query = query)
        applyVoiceFilter()
    }

    private fun setVoiceItems(voices: List<TtsVoice>) {
        allVoices = voices
        pruneVoiceFilters()
        currentDisplayedEngine()?.let(::bindVoiceParams)
        applyVoiceFilter()
        updateVoiceToggleState()
    }

    private fun applyVoiceFilter() {
        val query = voiceSearchQuery.lowercase()
        val filteredVoices = allVoices.filter { voice ->
            matchesVoiceSearch(voice, query) &&
                    matchesVoiceLanguageFilter(voice) &&
                    matchesVoiceGenderFilter(voice)
        }
        val engine = currentDisplayedEngine()
        val displayVoices = if (
            engine != null &&
            filteredVoices.any { !engine.isVoiceEnabled(it) }
        ) {
            filteredVoices.sortedByDescending { engine.isVoiceEnabled(it) }
        } else {
            filteredVoices
        }
        voiceListScreenState = voiceListScreenState.copy(
            items = engine?.let { currentEngine ->
                displayVoices.map { voice -> voice.toVoiceListItemUiModel(currentEngine) }
            }.orEmpty(),
        )
        if (allVoices.isNotEmpty()) {
            voiceControlsState = voiceControlsState.copy(
                message = getString(R.string.tts_voice_no_match)
                    .takeIf { filteredVoices.isEmpty() }
            )
        }
        updateVoiceToggleState()
    }

    private fun TtsVoice.toVoiceListItemUiModel(
        engine: TtsEngineSetting,
    ): TtsEngineVoiceListItemUiModel {
        val isSystemEngine = engine.type == TtsEngineType.SYSTEM
        val checked = if (isSystemEngine) engine.enabled else engine.isVoiceEnabled(this)
        val styleLabel = takeUnless { isSystemEngine }
            ?.style
            ?.takeIf { it.isNotBlank() }
        val detailTags = if (isSystemEngine) {
            listOf(engine.name.ifBlank { id })
        } else {
            tags.filter { it.isNotBlank() }
                .distinct()
                .ifEmpty {
                    if (styleLabel == null) listOf(id) else emptyList()
                }
        }
        return TtsEngineVoiceListItemUiModel(
            id = id,
            previewKey = TtsVoicePreviewController.keyOf(
                engine = engine,
                voice = this,
                systemDefault = isSystemEngine,
            ),
            name = name,
            genderLabel = takeUnless { isSystemEngine }
                ?.gender
                ?.let(TtsVoiceFilterSupport::genderLabel),
            languageLabels = takeUnless { isSystemEngine }
                ?.language
                ?.let(TtsVoiceFilterSupport::languageLabels)
                .orEmpty(),
            style = styleLabel,
            tags = detailTags,
            checked = checked,
            dimmed = !isSystemEngine && !checked,
        )
    }

    private fun handleVoiceListAction(action: TtsEngineVoiceListAction) {
        val voiceId = when (action) {
            is TtsEngineVoiceListAction.EnabledChanged -> action.voiceId
            is TtsEngineVoiceListAction.Preview -> action.voiceId
            is TtsEngineVoiceListAction.PreviewStyle -> action.voiceId
        }
        val voice = allVoices.firstOrNull { it.id == voiceId } ?: return
        when (action) {
            is TtsEngineVoiceListAction.EnabledChanged -> {
                val engine = currentDisplayedEngine() ?: return
                if (engine.type == TtsEngineType.SYSTEM) {
                    saveEnabledState(action.checked)
                } else {
                    val updated = TtsEngineStore.setVoiceEnabled(
                        engineId = engine.id,
                        voiceId = voice.id,
                        enabled = action.checked,
                    )
                    if (updated != null) {
                        detailEngineSnapshot = updated
                    }
                }
                applyVoiceFilter()
                refreshEngines()
            }

            is TtsEngineVoiceListAction.Preview -> previewCurrentVoice(voice)
            is TtsEngineVoiceListAction.PreviewStyle -> {
                val engine = currentDisplayedEngine() ?: return
                val styles = voice.styleOptions()
                if (styles.isEmpty()) {
                    requireContext().toastOnUi("当前发音人没有可选风格")
                } else {
                    showPreviewStyleSelector(engine, voice, styles)
                }
            }
        }
    }

    private fun matchesVoiceSearch(voice: TtsVoice, query: String): Boolean {
        return TtsVoiceFilterSupport.matchesName(voice, query)
    }

    private fun matchesVoiceLanguageFilter(voice: TtsVoice): Boolean {
        if (selectedVoiceLanguageFilters.isEmpty()) {
            return true
        }
        return TtsVoiceFilterSupport.languageLabels(voice.language)
            .any { it in selectedVoiceLanguageFilters }
    }

    private fun matchesVoiceGenderFilter(voice: TtsVoice): Boolean {
        if (selectedVoiceGenderFilters.isEmpty()) {
            return true
        }
        return TtsVoiceFilterSupport.genderLabel(voice.gender)
            ?.let { it in selectedVoiceGenderFilters } == true
    }

    private fun updateVoiceToggleState() {
        val engine = currentDisplayedEngine()
        val hasVoices = allVoices.isNotEmpty()
        if (engine?.type == TtsEngineType.SYSTEM) {
            voiceControlsState = voiceControlsState.copy(canToggleAll = false)
            return
        }
        if (!hasVoices || engine == null) {
            voiceControlsState = voiceControlsState.copy(canToggleAll = false)
            return
        }
        val allEnabled = allVoices.all { engine.isVoiceEnabled(it) }
        voiceControlsState = voiceControlsState.copy(
            canToggleAll = true,
            allEnabled = allEnabled,
        )
    }

    private fun toggleAllVoicesEnabled() {
        val engineId = currentEngineId ?: return
        val engine = currentDisplayedEngine()?.takeIf { it.id == engineId } ?: return
        if (allVoices.isEmpty()) {
            return
        }
        val allEnabled = allVoices.all { engine.isVoiceEnabled(it) }
        val updated = TtsEngineStore.setAllVoicesEnabled(
            engineId = engineId,
            voiceIds = allVoices.map { it.id },
            enabled = !allEnabled
        )
        if (updated != null) {
            detailEngineSnapshot = updated
        }
        applyVoiceFilter()
        refreshEngines()
    }

    private fun maybeAutoFetchVoices() {
        val engine = currentDisplayedEngine() ?: return
        if (
            !engine.supportsVoiceFetch() ||
            engine.effectiveVoices().isNotEmpty() ||
            !autoFetchedVoiceEngineIds.add(engine.id)
        ) {
            return
        }
        fetchVoices()
    }

    private fun fetchVoices() {
        if (voiceRefreshJob?.isActive == true) return
        val source = currentDisplayedEngine() ?: return
        val engineDraft = engineFromForm(source).takeIf { it.isScriptEngine } ?: return
        if (!engineDraft.supportsVoiceFetch()) {
            voiceRefreshing = false
            voiceRefreshEnabled = false
            voiceControlsState = voiceControlsState.copy(message = null)
            return
        }
        val requestedEngineId = engineDraft.id
        val refreshRevision = ++voiceRefreshRevision
        voiceRefreshing = true
        voiceRefreshEnabled = true
        if (allVoices.isEmpty()) {
            voiceControlsState = voiceControlsState.copy(
                message = getString(R.string.tts_voice_loading)
            )
        }
        voiceRefreshJob = lifecycleScope.launch {
            try {
                awaitEngineConfigSaves()
                val updated = withContext(Dispatchers.IO) {
                    TtsEngineStore.ensureVoiceCatalog(
                        engineId = engineDraft.id,
                        forceRefresh = true,
                        restartReadAloud = false
                    )
                }
                if (
                    refreshRevision != voiceRefreshRevision ||
                    currentEngineId != requestedEngineId
                ) {
                    return@launch
                }
                detailEngineSnapshot = updated
                draftEngine = null
                activity?.setTitle(updated.name)
                bindVoiceParams(updated)
                val effectiveVoices = updated.effectiveVoices()
                setVoiceItems(effectiveVoices)
                updateVoiceMessage(updated)
                requireContext().toastOnUi("已获取 ${effectiveVoices.size} 个发音人")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (
                    refreshRevision == voiceRefreshRevision &&
                    currentEngineId == requestedEngineId
                ) {
                    val message = error.localizedMessage ?: error.javaClass.simpleName
                    voiceControlsState = voiceControlsState.copy(message = "获取失败：$message")
                    requireContext().toastOnUi("获取发音人失败")
                }
            } finally {
                if (refreshRevision == voiceRefreshRevision) {
                    voiceRefreshing = false
                    voiceRefreshEnabled = currentEngineId == requestedEngineId &&
                        currentDisplayedEngine()?.supportsVoiceFetch() == true
                    voiceRefreshJob = null
                }
            }
        }
    }

    private fun previewCurrentVoice(voice: TtsVoice? = null) {
        val currentEngine = currentDisplayedEngine() ?: return
        if (currentEngine.type == TtsEngineType.SYSTEM) {
            voicePreviewController?.preview(
                engine = currentEngine,
                voice = systemDefaultVoice(currentEngine),
                systemDefault = true
            )
            return
        }
        val engine = engineFromForm(currentEngine).takeIf { it.isScriptEngine } ?: return
        val voices = engine.effectiveVoices()
        val selectedVoice = voice ?: voices.firstOrNull { it.id == engine.activeVoiceId }
            ?: voices.firstOrNull()
        val styles = selectedVoice?.styleOptions().orEmpty()
        val styleId = selectedVoice?.let { savedPreviewStyleId(engine, it, styles) }
        previewVoice(engine, selectedVoice, styleId = styleId)
    }

    private fun showPreviewStyleSelector(
        engine: TtsEngineSetting,
        voice: TtsVoice,
        styles: List<TtsVoiceStyle>
    ) {
        if (context == null) return
        val items = buildList {
            add("默认")
            styles.forEach { add(it.displayName) }
        }
        modalDialog?.dismiss()
        val dialog = ComponentDialog(requireContext())
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    TtsPreviewStyleDialogContent(
                        items = items,
                        onSelect = { index ->
                            dialog.dismiss()
                            previewVoice(
                                engine = engine,
                                voice = voice,
                                styleId = styles.getOrNull(index - 1)?.id
                            )
                            requireContext().putPrefString(
                                previewStylePrefKey(engine),
                                styles.getOrNull(index - 1)?.id.orEmpty()
                            )
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnDismissListener {
            if (modalDialog === dialog) {
                modalDialog = null
            }
        }
        modalDialog = dialog
        dialog.show()
        dialog.applyNgWindow()
    }

    private fun savedPreviewStyleId(
        engine: TtsEngineSetting,
        voice: TtsVoice,
        styles: List<TtsVoiceStyle>
    ): String? {
        val saved = requireContext().getPrefString(previewStylePrefKey(engine)).orEmpty()
        if (saved.isBlank()) return null
        return saved.takeIf { styleId -> styles.any { it.id == styleId || it.value == styleId } }
    }

    private fun previewStylePrefKey(engine: TtsEngineSetting): String {
        return "ttsPreviewStyle:${engine.id}"
    }

    private fun previewVoice(
        engine: TtsEngineSetting,
        voice: TtsVoice?,
        styleId: String?
    ) {
        val previewVoice = voice ?: TtsVoice(
            id = TtsEngineStore.SYSTEM_DEFAULT_ID,
            name = engine.name
        )
        voicePreviewController?.preview(
            engine = engine,
            voice = previewVoice,
            systemDefault = voice == null,
            styleId = styleId
        )
    }

    private fun measureCurrentEngineLatency() {
        val source = currentDisplayedEngine() ?: return
        val engine = if (sourceMode) {
            val script = scriptEditorView?.text?.toString()?.takeIf { it.isNotBlank() }
                ?: source.script
            engineFromState(source, script)
        } else {
            saveFormChangesIfNeeded() ?: source
        }.takeIf { it.isScriptEngine } ?: return
        val useSourceDraft = sourceMode
        val context = context ?: return
        context.toastOnUi("正在测速...")
        lifecycleScope.launch {
            val result = runCatching {
                awaitEngineConfigSaves()
                withContext(Dispatchers.IO) {
                    val effectiveEngine = if (useSourceDraft) {
                        engine
                    } else {
                        TtsEngineStore.engine(engine.id) ?: engine
                    }
                    val request = TtsScriptEngineClient.buildSynthesisRequest(
                        engine = effectiveEngine,
                        text = TtsScriptEngineClient.sampleText(effectiveEngine, null)
                    )
                    val targetUrl = ttsLatencyProbeUrl(request.url)
                        ?: error("脚本未生成可测速的接口")
                    val started = SystemClock.elapsedRealtime()
                    okHttpClient.newBuilder()
                        .callTimeout(15, TimeUnit.SECONDS)
                        .build()
                        .newCallResponse {
                            url(targetUrl)
                            head()
                        }.use {
                            SystemClock.elapsedRealtime() - started
                        }.let { elapsed ->
                            "网络延迟：${elapsed}ms"
                        }
                }
            }
            result.onSuccess {
                context.toastOnUi(it)
            }.onFailure {
                context.toastOnUi("测速失败：${it.localizedMessage ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun systemDefaultVoice(engine: TtsEngineSetting): TtsVoice {
        return TtsVoice(
            id = "${engine.id}:default",
            name = getString(R.string.tts_system_default_voice),
            sampleText = getString(R.string.tts_system_preview_text)
        )
    }

    private fun updateVoiceMessage(engine: TtsEngineSetting) {
        val message = when {
            engine.type != TtsEngineType.SCRIPT -> "系统 TTS 暂不支持发音人列表"
            engine.effectiveVoices().isEmpty() -> getString(R.string.tts_voice_not_loaded)
            else -> null
        }
        voiceControlsState = voiceControlsState.copy(message = message)
    }

    private fun confirmDeleteEngine(
        engine: TtsEngineSetting,
        onCancel: () -> Unit = {},
        onDeleted: () -> Unit = {}
    ) {
        if (!TtsEngineStore.isDeletableEngine(engine)) {
            onCancel()
            return
        }
        modalDialog?.dismiss()
        val dialog = ComponentDialog(requireContext())
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    TtsConfirmDialogContent(
                        title = getString(R.string.delete),
                        message = getString(R.string.sure_del_any, engine.name),
                        onCancel = {
                            dialog.dismiss()
                            onCancel()
                        },
                        onConfirm = {
                            dialog.dismiss()
                            if (TtsEngineStore.deleteEngine(engine.id)) {
                                requireContext().toastOnUi("已删除朗读引擎")
                                onDeleted()
                            } else {
                                onCancel()
                            }
                        },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnDismissListener {
            if (modalDialog === dialog) {
                modalDialog = null
            }
        }
        modalDialog = dialog
        dialog.show()
        dialog.applyNgWindow()
    }

}
