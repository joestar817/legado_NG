package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.text.method.SingleLineTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.FragmentTtsEngineConfigBinding
import io.legado.app.databinding.ItemTtsConfigFieldBinding
import io.legado.app.databinding.ItemTtsVoiceBinding
import io.legado.app.databinding.LayoutTtsVoiceParamsPopupBinding
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
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.view.NgFloatingTabItem
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.utils.applyTint
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readText
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TtsEngineConfigFragment : BaseFragment(R.layout.fragment_tts_engine_config),
    ConfigBackHandler {

    private enum class DetailTab { CONFIG, VOICES }

    private val binding by viewBinding(FragmentTtsEngineConfigBinding::bind)
    private val voiceAdapter by lazy { VoiceAdapter() }
    private val configEntities = arrayListOf<ConfigField>()
    private var currentEngineId: String? = null
    private var detailEngineSnapshot: TtsEngineSetting? = null
    private var draftEngine: TtsEngineSetting? = null
    private var configOptionsJob: Job? = null
    private var configOptionsLoadedScript: String? = null
    private var scriptCodeLoadedEngineId: String? = null
    private var formDirty = false
    private var detailTab = DetailTab.CONFIG
    private var sourceMode = false
    private var allVoices: List<TtsVoice> = emptyList()
    private var voiceSearchQuery: String = ""
    private var voicePreviewController: TtsVoicePreviewController? = null
    private var voiceParamPopup: PopupWindow? = null
    private var voiceParamPopupBinding: LayoutTtsVoiceParamsPopupBinding? = null
    private var importConflictDialog: Dialog? = null
    private var engineMenuButton: ImageButton? = null
    private var showDisabledEngines = LocalConfig.ttsEngineListShowDisabled
    private var engineScreenState by mutableStateOf(TtsEngineListScreenState())
    private var engineFormScreenState by mutableStateOf(TtsEngineFormScreenState())
    private var engineSettingsSnapshot: List<TtsEngineSetting> = emptyList()
    private var engineOrderSaveJob: Job? = null
    private var engineConfigSaveJob: Job? = null
    private var engineConfigSaveRevision = 0L
    private var engineRefreshJob: Job? = null
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
        setupEngineListMenu()
        binding.composeEngines.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    TtsEngineListScreen(
                        state = engineScreenState,
                        onAction = ::handleEngineListAction
                    )
                }
            }
        }
        binding.composeConfigForm.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    TtsEngineFormScreen(
                        state = engineFormScreenState,
                        onAction = ::handleEngineFormAction
                    )
                }
            }
        }
        binding.editScriptCode.setMaxHighlightLength(128 * 1024)
        binding.editScriptCode.addJsPattern()
        binding.recyclerVoices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVoices.setEdgeEffectColor(accentColor)
        binding.recyclerVoices.adapter = voiceAdapter
        binding.refreshEngines.setColorSchemeColors(accentColor)
        // ComposeView 本身不转发 LazyColumn 的纵向滚动能力，需查询其 AndroidComposeView 子节点。
        binding.refreshEngines.setOnChildScrollUpCallback { _, _ ->
            binding.composeEngines.getChildAt(0)?.canScrollVertically(-1) == true
        }
        binding.refreshEngines.setOnRefreshListener { refreshEnginesAsync() }

        binding.layoutEngineDetailTabs.setItems(
            items = listOf(
                NgFloatingTabItem(
                    iconRes = R.drawable.ic_ai_tab_config,
                    contentDescription = getString(R.string.tts_config_tab)
                ),
                NgFloatingTabItem(
                    iconRes = R.drawable.ic_tts_tab_voice,
                    contentDescription = getString(R.string.tts_voices)
                )
            ),
            selectedIndex = DetailTab.CONFIG.ordinal
        ) { index ->
            showDetailTab(DetailTab.entries[index])
        }
        binding.buttonConfigSource.setOnClickListener { showConfigSourceMode(!sourceMode) }
        binding.buttonTestConfig.setOnClickListener { measureCurrentEngineLatency() }
        binding.buttonSaveConfig.setOnClickListener { saveSourceEngine() }
        binding.buttonVoiceParams.setOnClickListener { toggleVoiceParamPanel() }
        binding.buttonToggleAllVoices.setOnClickListener { toggleAllVoicesEnabled() }
        applyVoiceToggleActionStyle()
        binding.refreshVoices.setColorSchemeColors(accentColor)
        binding.refreshVoices.setOnRefreshListener { fetchVoices() }
        setupVoiceSearch()
        voicePreviewController = TtsVoicePreviewController(
            context = requireContext(),
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            onStatusChanged = voiceAdapter::updatePreviewStatus
        )

        refreshEngines()
    }

    override fun onDestroyView() {
        saveFormChangesIfNeeded()
        configOptionsJob?.cancel()
        configOptionsJob = null
        removeEngineListMenu()
        voiceParamPopup?.dismiss()
        voiceParamPopup = null
        voiceParamPopupBinding = null
        importConflictDialog?.dismiss()
        importConflictDialog = null
        voicePreviewController?.release()
        voicePreviewController = null
        super.onDestroyView()
    }

    override fun onConfigBackPressed(): Boolean {
        if (sourceMode) {
            showConfigSourceMode(false)
            return true
        }
        if (binding.layoutEngineDetail.isVisible) {
            showEngineList()
            return true
        }
        return false
    }

    private fun refreshEngines() {
        engineSnapshotGate.invalidate()
        engineRefreshJob?.cancel()
        engineRefreshJob = null
        binding.refreshEngines.isRefreshing = false
        applyEngineSnapshot(TtsEngineStore.engines())
    }

    private fun refreshEnginesAsync() {
        if (engineRefreshJob?.isActive == true) return
        val snapshotToken = engineSnapshotGate.begin()
        engineRefreshJob = lifecycleScope.launch {
            try {
                awaitEngineOrderSaves()
                val allEngines = withContext(Dispatchers.IO) {
                    TtsEngineStore.engines()
                }
                if (engineSnapshotGate.isCurrent(snapshotToken)) {
                    applyEngineSnapshot(allEngines)
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
                if (engineSnapshotGate.isCurrent(snapshotToken)) {
                    binding.refreshEngines.isRefreshing = false
                    engineRefreshJob = null
                }
            }
        }
    }

    private suspend fun awaitEngineOrderSaves() {
        while (true) {
            val pendingSave = engineOrderSaveJob ?: return
            pendingSave.join()
            if (pendingSave === engineOrderSaveJob) return
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
                refreshEngines()
            }

            is TtsEngineListAction.SearchSubmitted -> Unit
            is TtsEngineListAction.OpenEngine -> {
                TtsEngineStore.engine(action.engineId)?.let(::showEngineDetail)
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

            TtsEngineListAction.Retry -> refreshEngines()
            TtsEngineListAction.OpenListMenu -> {
                engineMenuButton?.let(::showEngineMoreMenu)
            }

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
        engineSnapshotGate.invalidate()
        engineRefreshJob?.cancel()
        engineRefreshJob = null
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
        configOptionsJob?.cancel()
        configOptionsJob = null
        configOptionsLoadedScript = null
        currentEngineId = null
        detailEngineSnapshot = null
        draftEngine = null
        formDirty = false
        scriptCodeLoadedEngineId = null
        binding.editScriptCode.setText("")
        activity?.setTitle(R.string.tts_engine_settings)
        engineMenuButton?.isVisible = true
        binding.layoutEngineList.isVisible = true
        binding.layoutEngineDetail.isVisible = false
        refreshEngines()
    }

    private fun showEngineDetail(engine: TtsEngineSetting, tab: DetailTab = DetailTab.CONFIG) {
        clearEngineFormFocus()
        val isSwitchingEngine = currentEngineId != engine.id
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
            sourceMode = false
            scriptCodeLoadedEngineId = null
            binding.editScriptCode.setText("")
        }
        activity?.setTitle(engine.name)
        engineMenuButton?.isVisible = false
        binding.layoutEngineList.isVisible = false
        binding.layoutEngineDetail.isVisible = true
        if (isSwitchingEngine) {
            binding.searchVoice.setQuery("")
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

    private fun setupEngineListMenu() {
        val titleBar = requireActivity().findViewById<TitleBar>(R.id.title_bar) ?: return
        val toolbar = titleBar.toolbar
        toolbar.findViewById<View>(R.id.menu_tts_engine_more)?.let { toolbar.removeView(it) }
        val button = ImageButton(requireContext()).apply {
            id = R.id.menu_tts_engine_more
            setImageResource(R.drawable.ic_more_vert)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.primaryText))
            background = null
            contentDescription = getString(R.string.menu)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(10.dpToPx(), 10.dpToPx(), 10.dpToPx(), 10.dpToPx())
            setOnClickListener { showEngineMoreMenu(this) }
        }
        toolbar.addView(
            button,
            Toolbar.LayoutParams(48.dpToPx(), 48.dpToPx(), Gravity.END or Gravity.CENTER_VERTICAL)
        )
        engineMenuButton = button
        engineMenuButton?.isVisible = binding.layoutEngineList.isVisible
    }

    private fun removeEngineListMenu() {
        engineMenuButton?.let { button ->
            (button.parent as? ViewGroup)?.removeView(button)
        }
        engineMenuButton = null
    }

    private fun showEngineMoreMenu(anchor: View) {
        NgActionPopup(
            requireContext(),
            listOf(
                NgActionPopupItem(
                    R.id.menu_tts_engine_add,
                    R.string.add_tts_engine,
                    R.drawable.ic_add
                ),
                NgActionPopupItem(
                    R.id.menu_tts_engine_import_local,
                    R.string.import_local,
                    R.drawable.ic_import
                ),
                NgActionPopupItem(
                    R.id.menu_tts_engine_import_online,
                    R.string.import_on_line,
                    R.drawable.ic_add_online
                ),
                NgActionPopupItem(
                    itemId = R.id.menu_show_disabled,
                    titleRes = R.string.show_disabled_items,
                    iconRes = R.drawable.ic_visibility,
                    checked = showDisabledEngines,
                    dividerBefore = true
                )
            ),
            widthDp = 0
        ) { item ->
            when (item.itemId) {
                R.id.menu_tts_engine_add -> addTtsEngine()
                R.id.menu_tts_engine_import_local -> importTtsEngineFileLauncher.launch(
                    arrayOf("text/*", "application/json", "application/javascript", "application/octet-stream")
                )
                R.id.menu_tts_engine_import_online -> showImportTtsEngineUrlDialog()
                R.id.menu_show_disabled -> {
                    toggleShowDisabledEngines()
                }
            }
        }.show(anchor)
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
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.tts_engine_url_hint)
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_on_line)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importTtsEngineFromUrl(editText.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .applyTint()
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
        val dialog = Dialog(requireContext()).apply {
            setContentView(R.layout.dialog_tts_engine_import_conflict)
            setCanceledOnTouchOutside(true)
        }
        val names = conflict.conflicts
            .map { it.existingName }
            .distinct()
            .take(3)
            .joinToString("、") { "“$it”" }
        dialog.findViewById<TextView>(R.id.tv_import_conflict_message).text = getString(
            R.string.tts_engine_import_conflict_message,
            names
        )
        dialog.findViewById<TextView>(R.id.tv_import_keep_both).setOnClickListener {
            dialog.dismiss()
            importTtsEngineText(content, TtsEngineImportConflictAction.KEEP_BOTH)
        }
        dialog.findViewById<TextView>(R.id.tv_import_overwrite).apply {
            isVisible = conflict.conflicts.all { it.canOverwrite }
            setOnClickListener {
                dialog.dismiss()
                importTtsEngineText(content, TtsEngineImportConflictAction.OVERWRITE)
            }
        }
        dialog.setOnShowListener { dialog.applyNgWindow() }
        dialog.setOnDismissListener {
            if (importConflictDialog === dialog) {
                importConflictDialog = null
            }
        }
        importConflictDialog = dialog
        dialog.show()
    }

    private fun bindSystemEngineDetail(engine: TtsEngineSetting) = binding.run {
        detailTab = DetailTab.VOICES
        sourceMode = false
        voiceParamPopup?.dismiss()
        scrollConfig.isVisible = false
        layoutConfigActions.isVisible = false
        layoutEngineDetailTabs.isVisible = false
        layoutVoices.isVisible = true
        layoutVoiceSearch.isVisible = false
        layoutSystemVoiceParams.isVisible = true
        textVoiceMessage.isVisible = false
        layoutVoiceHeader.isVisible = false
        refreshVoices.isRefreshing = false
        refreshVoices.isEnabled = false
        bindSystemVoiceParams(engine)
        setVoiceItems(listOf(systemDefaultVoice(engine)))
    }

    private fun showDetailTab(tab: DetailTab) {
        if (tab != DetailTab.CONFIG) {
            saveFormChangesIfNeeded()
            clearEngineFormFocus()
        }
        detailTab = tab
        if (tab != DetailTab.CONFIG && sourceMode) {
            sourceMode = false
        }
        binding.scrollConfig.isVisible = tab == DetailTab.CONFIG
        binding.layoutConfigActions.isVisible = tab == DetailTab.CONFIG
        binding.layoutVoices.isVisible = tab == DetailTab.VOICES
        binding.layoutEngineDetailTabs.isVisible = !sourceMode
        binding.layoutVoiceSearch.isVisible = tab == DetailTab.VOICES
        binding.layoutSystemVoiceParams.isVisible = false
        binding.layoutEngineDetailTabs.select(tab.ordinal)
        if (tab == DetailTab.VOICES) {
            maybeAutoFetchVoices()
        }
    }

    private fun bindEngineForm(engine: TtsEngineSetting) = binding.run {
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
        editScriptCode.isEnabled = scriptEnabled
        refreshVoices.isEnabled = engine.supportsVoiceFetch()
        showConfigSourceMode(sourceMode)
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
        sourceMode = enabled
        moveConfigActions(sourceMode = enabled)
        binding.scrollConfigForm.isVisible = !enabled
        binding.layoutScriptEditor.isVisible = enabled
        binding.layoutEngineDetailTabs.isVisible = !enabled
        binding.buttonSaveConfig.isVisible = enabled
        binding.buttonConfigSource.setText(
            if (enabled) R.string.tts_form_mode else R.string.tts_source_mode
        )
    }

    private fun clearEngineFormFocus() {
        binding.composeConfigForm.clearFocus()
        binding.composeConfigForm.hideSoftInput()
    }

    private fun moveConfigActions(sourceMode: Boolean) = binding.run {
        val target = if (sourceMode) layoutScriptEditor else layoutConfigFormContent
        if (layoutConfigActions.parent !== target) {
            (layoutConfigActions.parent as? ViewGroup)?.removeView(layoutConfigActions)
            target.addView(layoutConfigActions)
        }
    }

    private fun setScriptCodeText(script: String?) {
        val text = script.orEmpty()
        if (text.isBlank()) {
            binding.editScriptCode.setText("")
        } else {
            binding.editScriptCode.setTextHighlighted(text)
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
        applyConfigEntities(engine, emptyList())
        if (engine.type != TtsEngineType.SCRIPT) {
            return
        }
        val requestedEngineId = engine.id
        val requestedScript = engine.script
        configOptionsJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { TtsScriptEngineClient.loadOptions(engine) }
            }
            val current = currentDisplayedEngine()
            if (
                currentEngineId != requestedEngineId ||
                current?.script != requestedScript
            ) {
                return@launch
            }
            val options = result.getOrElse {
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
        val script = binding.editScriptCode.text?.toString()?.takeIf { it.isNotBlank() }
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
        voiceParamPopupBinding?.let { bindVoiceParamPopup(it, engine) }
    }

    private fun bindSystemVoiceParams(engine: TtsEngineSetting) = binding.run {
        tintSystemVoiceParamSeekBars()
        seekSystemSpeed.setOnSeekBarChangeListener(null)
        seekSystemPitch.setOnSeekBarChangeListener(null)
        seekSystemSpeed.progress = engine.effectiveSpeed()
        seekSystemPitch.progress = engine.effectivePitch()
        updateSystemVoiceParamTexts()
        val listener = object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateSystemVoiceParamTexts()
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                currentEngineId?.let { engineId ->
                    TtsEngineStore.saveRuntimeParams(
                        engineId = engineId,
                        speed = seekSystemSpeed.progress,
                        volume = engine.effectiveVolume(),
                        pitch = seekSystemPitch.progress
                    )
                }
            }
        }
        seekSystemSpeed.setOnSeekBarChangeListener(listener)
        seekSystemPitch.setOnSeekBarChangeListener(listener)
    }

    private fun tintSystemVoiceParamSeekBars() {
        val accent = accentColor
        val trackTint = ColorStateList.valueOf(ColorUtils.adjustAlpha(accent, 0.35f))
        val thumbTint = ColorStateList.valueOf(accent)
        listOf(
            binding.seekSystemSpeed,
            binding.seekSystemPitch
        ).forEach { seekBar ->
            seekBar.progressTintList = trackTint
            seekBar.progressBackgroundTintList = trackTint
            seekBar.secondaryProgressTintList = trackTint
            seekBar.thumbTintList = thumbTint
        }
    }

    private fun updateSystemVoiceParamTexts() = binding.run {
        textSystemSpeedValue.text = seekSystemSpeed.progress.toString()
        textSystemPitchValue.text = seekSystemPitch.progress.toString()
    }

    private fun toggleVoiceParamPanel() {
        voiceParamPopup?.takeIf { it.isShowing }?.dismiss() ?: showVoiceParamPopup()
    }

    private fun showVoiceParamPopup() {
        val engine = currentDisplayedEngine() ?: return
        val popupBinding = LayoutTtsVoiceParamsPopupBinding.inflate(layoutInflater)
        attachVoiceParamPopupOwners(popupBinding.root)
        val popup = PopupWindow(
            popupBinding.root,
            binding.layoutVoiceSearch.width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 6.dpToPx().toFloat()
            setOnDismissListener {
                if (voiceParamPopup === this) {
                    voiceParamPopup = null
                    voiceParamPopupBinding = null
                }
            }
        }
        voiceParamPopup = popup
        voiceParamPopupBinding = popupBinding
        popup.showAsDropDown(binding.layoutVoiceSearch, 0, 8.dpToPx())
        (popupBinding.root.parent as? View)?.let(::attachVoiceParamPopupOwners)
        attachVoiceParamPopupOwners(popupBinding.root.rootView)
        bindVoiceParamPopup(popupBinding, engine)
        popup.update(binding.layoutVoiceSearch.width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun attachVoiceParamPopupOwners(view: View) {
        view.setViewTreeLifecycleOwner(viewLifecycleOwner)
        view.setViewTreeViewModelStoreOwner(this@TtsEngineConfigFragment)
        view.setViewTreeSavedStateRegistryOwner(this@TtsEngineConfigFragment)
    }

    private fun bindVoiceParamPopup(
        popupBinding: LayoutTtsVoiceParamsPopupBinding,
        engine: TtsEngineSetting
    ) = popupBinding.run {
        var speed by mutableStateOf(engine.effectiveSpeed())
        var volume by mutableStateOf(engine.effectiveVolume())
        var pitch by mutableStateOf(engine.effectivePitch())
        composeVoiceParams.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme {
                    TtsVoiceParamsSliderPanel(
                        speed = speed,
                        volume = volume,
                        pitch = pitch,
                        onSpeedChange = { speed = it },
                        onVolumeChange = { volume = it },
                        onPitchChange = { pitch = it },
                        onValueChangeFinished = {
                            TtsEngineStore.saveRuntimeParams(
                                engineId = engine.id,
                                speed = speed,
                                volume = volume,
                                pitch = pitch
                            )
                        }
                    )
                }
            }
        }
        bindVoiceFilterChips(this)
    }

    private fun bindVoiceFilterChips(popupBinding: LayoutTtsVoiceParamsPopupBinding) {
        if (currentDisplayedEngine()?.type == TtsEngineType.SYSTEM) {
            popupBinding.layoutLanguageFilterSection.isVisible = false
            popupBinding.layoutGenderFilterSection.isVisible = false
            return
        }
        val languageLabels = availableVoiceLanguageLabels()
        selectedVoiceLanguageFilters.retainAll(languageLabels.toSet())
        bindVoiceFilterSection(
            section = popupBinding.layoutLanguageFilterSection,
            container = popupBinding.layoutVoiceLanguageFilters,
            labels = languageLabels,
            selectedLabels = selectedVoiceLanguageFilters
        )
        bindVoiceGenderFilterSection(popupBinding)
    }

    private fun bindVoiceFilterSection(
        section: View,
        container: ViewGroup,
        labels: List<String>,
        selectedLabels: MutableSet<String>
    ) {
        section.isVisible = labels.isNotEmpty()
        container.removeAllViews()
        labels.forEach { label ->
            container.addView(
                createVoiceFilterChip(
                    container = container,
                    label = label,
                    selected = label in selectedLabels
                ) {
                    if (!selectedLabels.add(label)) {
                        selectedLabels.remove(label)
                    }
                    voiceParamPopupBinding?.let { bindVoiceFilterChips(it) }
                    applyVoiceFilter()
                }
            )
        }
    }

    private fun createVoiceFilterChip(
        container: ViewGroup,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        val context = container.context
        val textColor = ContextCompat.getColor(
            context,
            if (selected) R.color.ng_tts_language else R.color.ng_on_surface_variant
        )
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundResource(
                if (selected) R.drawable.ng_bg_tts_language_tag else R.drawable.ng_bg_tag_neutral
            )
            setPadding(10.dpToPx(), 0, 10.dpToPx(), 0)
            minWidth = 28.dpToPx()
            setOnClickListener { onClick() }
            layoutParams = if (container is FlexboxLayout) {
                FlexboxLayout.LayoutParams(
                    FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    24.dpToPx()
                ).apply {
                    rightMargin = 6.dpToPx()
                    topMargin = 3.dpToPx()
                    bottomMargin = 3.dpToPx()
                }
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    24.dpToPx()
                ).apply {
                    marginEnd = 6.dpToPx()
                }
            }
        }
    }

    private fun bindVoiceGenderFilterSection(popupBinding: LayoutTtsVoiceParamsPopupBinding) {
        popupBinding.layoutGenderFilterSection.isVisible = allVoices.isNotEmpty()
        popupBinding.layoutVoiceGenderFilters.removeAllViews()
        listOf(
            VoiceGenderFilter("男", R.drawable.ic_tts_gender_male, R.color.ng_tts_gender_male),
            VoiceGenderFilter("女", R.drawable.ic_tts_gender_female, R.color.ng_tts_gender_female)
        ).forEach { filter ->
            popupBinding.layoutVoiceGenderFilters.addView(
                createVoiceGenderFilterChip(
                    container = popupBinding.layoutVoiceGenderFilters,
                    filter = filter,
                    selected = filter.label in selectedVoiceGenderFilters
                )
            )
        }
    }

    private fun createVoiceGenderFilterChip(
        container: LinearLayout,
        filter: VoiceGenderFilter,
        selected: Boolean
    ): ImageView {
        val context = container.context
        return ImageView(context).apply {
            contentDescription = filter.label
            setImageResource(filter.iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (selected) filter.colorRes else R.color.ng_on_surface_variant
                )
            )
            setBackgroundResource(
                if (selected) R.drawable.ng_bg_tts_language_tag else R.drawable.ng_bg_tag_neutral
            )
            setPadding(5.dpToPx(), 3.dpToPx(), 5.dpToPx(), 3.dpToPx())
            setOnClickListener {
                if (!selectedVoiceGenderFilters.add(filter.label)) {
                    selectedVoiceGenderFilters.remove(filter.label)
                }
                voiceParamPopupBinding?.let { bindVoiceFilterChips(it) }
                applyVoiceFilter()
            }
            layoutParams = LinearLayout.LayoutParams(
                34.dpToPx(),
                24.dpToPx()
            ).apply {
                marginEnd = 6.dpToPx()
            }
        }
    }

    private fun pruneVoiceFilters() {
        selectedVoiceLanguageFilters.retainAll(availableVoiceLanguageLabels().toSet())
    }

    private fun availableVoiceLanguageLabels(): List<String> {
        return TtsVoiceFilterSupport.availableLanguageLabels(allVoices)
    }

    private fun setupVoiceSearch() {
        binding.searchVoice.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence,
                start: Int,
                before: Int,
                count: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                voiceSearchQuery = s?.toString()?.trim().orEmpty()
                applyVoiceFilter()
            }
        })
    }

    private fun setVoiceItems(voices: List<TtsVoice>) {
        allVoices = voices
        pruneVoiceFilters()
        voiceParamPopupBinding?.let { bindVoiceFilterChips(it) }
        applyVoiceFilter()
        updateVoiceHeader()
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
        voiceAdapter.setItems(displayVoices)
        if (allVoices.isNotEmpty()) {
            binding.textVoiceMessage.isVisible = filteredVoices.isEmpty()
            if (filteredVoices.isEmpty()) {
                binding.textVoiceMessage.setText(R.string.tts_voice_no_match)
            }
        }
        updateVoiceHeader()
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

    private fun updateVoiceHeader() {
        val engine = currentDisplayedEngine()
        val hasVoices = allVoices.isNotEmpty()
        if (engine?.type == TtsEngineType.SYSTEM) {
            binding.layoutVoiceHeader.isVisible = false
            return
        }
        binding.layoutVoiceHeader.isVisible = hasVoices
        if (!hasVoices || engine == null) {
            return
        }
        val allEnabled = allVoices.all { engine.isVoiceEnabled(it) }
        binding.buttonToggleAllVoices.setText(
            if (allEnabled) R.string.tts_disable_all_voices else R.string.tts_enable_all_voices
        )
        applyVoiceToggleActionStyle()
    }

    private fun applyVoiceToggleActionStyle() {
        val snapshot = NgThemeResolver.resolve(requireContext())
        binding.buttonToggleAllVoices.background = null
        binding.buttonToggleAllVoices.setTextColor(
            if (snapshot.isDark) {
                snapshot.colors.onSurface
            } else {
                snapshot.colors.primary
            }
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
        val source = currentDisplayedEngine() ?: return
        val engineDraft = engineFromForm(source).takeIf { it.isScriptEngine } ?: return
        if (!engineDraft.supportsVoiceFetch()) {
            binding.refreshVoices.isRefreshing = false
            binding.textVoiceMessage.isVisible = false
            return
        }
        binding.refreshVoices.isRefreshing = true
        binding.textVoiceMessage.isVisible = allVoices.isEmpty()
        if (allVoices.isEmpty()) {
            binding.textVoiceMessage.setText(R.string.tts_voice_loading)
        }
        lifecycleScope.launch {
            try {
                val engine = withContext(Dispatchers.IO) {
                    TtsEngineStore.saveEngine(engineDraft, restartReadAloud = false)
                    TtsEngineStore.engine(engineDraft.id) ?: engineDraft
                }
                detailEngineSnapshot = engine
                draftEngine = null
                activity?.setTitle(engine.name)
                bindVoiceParams(engine)

                val updated = withContext(Dispatchers.IO) {
                    TtsEngineStore.ensureVoiceCatalog(
                        engineId = engine.id,
                        forceRefresh = true,
                        restartReadAloud = false
                    )
                }
                detailEngineSnapshot = updated
                val effectiveVoices = updated.effectiveVoices()
                binding.refreshVoices.isRefreshing = false
                setVoiceItems(effectiveVoices)
                updateVoiceMessage(updated)
                requireContext().toastOnUi("已获取 ${effectiveVoices.size} 个发音人")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = error.localizedMessage ?: error.javaClass.simpleName
                binding.textVoiceMessage.isVisible = true
                binding.textVoiceMessage.text = "获取失败：$message"
                requireContext().toastOnUi("获取发音人失败")
            } finally {
                binding.refreshVoices.isRefreshing = false
                binding.refreshVoices.isEnabled =
                    currentDisplayedEngine()?.supportsVoiceFetch() == true
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
        val context = context ?: return
        val items = buildList<CharSequence> {
            add("默认")
            styles.forEach { add(it.displayName) }
        }
        context.selector("试听风格", items) { _, index ->
            previewVoice(
                engine = engine,
                voice = voice,
                styleId = styles.getOrNull(index - 1)?.id
            )
            requireContext().putPrefString(
                previewStylePrefKey(engine),
                styles.getOrNull(index - 1)?.id.orEmpty()
            )
        }
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
            val script = binding.editScriptCode.text?.toString()?.takeIf { it.isNotBlank() }
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
        binding.textVoiceMessage.isVisible = message != null
        if (message != null) {
            binding.textVoiceMessage.text = message
        }
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
        alert(getString(R.string.delete)) {
            setMessage(getString(R.string.sure_del_any, engine.name))
            okButton { dialog ->
                dialog.dismiss()
                if (TtsEngineStore.deleteEngine(engine.id)) {
                    requireContext().toastOnUi("已删除朗读引擎")
                    onDeleted()
                } else {
                    onCancel()
                }
            }
            cancelButton {
                onCancel()
            }
        }
    }

    private inner class ConfigRuleAdapter :
        RecyclerView.Adapter<ConfigRuleAdapter.ViewHolder>() {

        var enabled: Boolean = true
            @SuppressLint("NotifyDataSetChanged")
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        var editEntities: ArrayList<ConfigField> = ArrayList()
            @SuppressLint("NotifyDataSetChanged")
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemTtsConfigFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun getItemCount(): Int = editEntities.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(editEntities[position])
        }

        inner class ViewHolder(private val binding: ItemTtsConfigFieldBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(field: ConfigField) = binding.run {
                editText.getTag(R.id.tag2)?.let {
                    if (it is TextWatcher) {
                        editText.removeTextChangedListener(it)
                    }
                }
                editText.setTag(R.id.tag2, null)
                editPassword.getTag(R.id.tag2)?.let {
                    if (it is TextWatcher) {
                        editPassword.removeTextChangedListener(it)
                    }
                }
                editPassword.setTag(R.id.tag2, null)
                spinnerValue.onItemSelectedListener = null
                switchValue.setOnCheckedChangeListener(null)
                buttonTogglePassword.setOnClickListener(null)

                textLabel.text = field.label
                textLabel.isVisible = true
                editText.isVisible = false
                layoutPassword.isVisible = false
                spinnerValue.isVisible = false
                layoutSwitch.isVisible = false

                when (field.type) {
                    "select" -> bindSelectField(field)
                    "boolean" -> bindBooleanField(field)
                    "password" -> bindPasswordField(field)
                    "number" -> bindTextField(
                        field = field,
                        inputType = InputType.TYPE_CLASS_NUMBER or
                                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                                InputType.TYPE_NUMBER_FLAG_SIGNED
                    )
                    else -> bindTextField(
                        field = field,
                        inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_VARIATION_NORMAL
                    )
                }
            }

            private fun bindTextField(
                field: ConfigField,
                inputType: Int
            ) = binding.run {
                editText.isVisible = true
                editText.setBackgroundResource(R.drawable.ng_bg_tts_config_field)
                editText.backgroundTintList = null
                editText.setTag(R.id.tag, field.key)
                editText.maxLines = 1
                editText.inputType = inputType
                editText.transformationMethod = SingleLineTransformationMethod.getInstance()
                editText.isEnabled = enabled
                editText.setText(field.value.orEmpty())
                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        field.value = s?.toString()
                    }
                }
                editText.addTextChangedListener(textWatcher)
                editText.setTag(R.id.tag2, textWatcher)
                editText.clearFocus()
            }

            private fun bindPasswordField(field: ConfigField) = binding.run {
                layoutPassword.isVisible = true
                layoutPassword.isEnabled = enabled
                editPassword.isEnabled = enabled
                editPassword.setTag(R.id.tag, field.key)
                updatePasswordVisibility(field)
                editPassword.setText(field.value.orEmpty())
                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        field.value = s?.toString()
                    }
                }
                editPassword.addTextChangedListener(textWatcher)
                editPassword.setTag(R.id.tag2, textWatcher)
                buttonTogglePassword.isEnabled = enabled
                buttonTogglePassword.setOnClickListener {
                    field.passwordVisible = !field.passwordVisible
                    updatePasswordVisibility(field)
                }
                editPassword.clearFocus()
            }

            private fun updatePasswordVisibility(field: ConfigField) = binding.run {
                val selection = editPassword.selectionStart.coerceAtLeast(0)
                editPassword.inputType = if (field.passwordVisible) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                editPassword.transformationMethod = if (field.passwordVisible) {
                    SingleLineTransformationMethod.getInstance()
                } else {
                    PasswordTransformationMethod.getInstance()
                }
                buttonTogglePassword.setImageResource(
                    if (field.passwordVisible) {
                        R.drawable.ic_visibility
                    } else {
                        R.drawable.ic_visibility_off
                    }
                )
                editPassword.setSelection(selection.coerceAtMost(editPassword.text?.length ?: 0))
            }

            private fun bindSelectField(field: ConfigField) = binding.run {
                spinnerValue.isVisible = true
                spinnerValue.setBackgroundResource(R.drawable.ng_bg_tts_spinner_compact)
                spinnerValue.backgroundTintList = null
                spinnerValue.isEnabled = enabled
                val currentValue = field.value.orEmpty()
                val items = buildList {
                    if (currentValue.isNotBlank() && field.values.none { it.value == currentValue }) {
                        add(TtsScriptOptionValue(label = currentValue, value = currentValue))
                    }
                    addAll(field.values)
                }.distinctBy { it.value }
                    .ifEmpty { listOf(TtsScriptOptionValue(label = currentValue, value = currentValue)) }
                spinnerValue.adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.item_tts_spinner_text,
                    items.map { it.label }
                ).apply {
                    setDropDownViewResource(R.layout.item_tts_spinner_dropdown)
                }
                val selectedIndex = items.indexOfFirst { it.value == currentValue }.coerceAtLeast(0)
                field.value = items.getOrNull(selectedIndex)?.value.orEmpty()
                spinnerValue.setSelection(selectedIndex)
                spinnerValue.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        field.value = items.getOrNull(position)?.value.orEmpty()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                    }
                }
            }

            private fun bindBooleanField(field: ConfigField) = binding.run {
                textLabel.isVisible = false
                layoutSwitch.isVisible = true
                textSwitchLabel.text = field.label
                switchValue.isEnabled = enabled
                switchValue.isChecked = field.value.toBooleanOption()
                switchValue.setOnCheckedChangeListener { _, isChecked ->
                    field.value = isChecked.toString()
                }
            }
        }
    }

    private fun String?.toBooleanOption(): Boolean {
        return when (this?.trim()?.lowercase()) {
            "true", "1", "yes", "y", "on", "enable", "enabled", "启用", "是" -> true
            else -> false
        }
    }

    private inner class VoiceAdapter :
        RecyclerAdapter<TtsVoice, ItemTtsVoiceBinding>(requireContext()) {

        private var previewStatus = TtsVoicePreviewStatus(
            key = null,
            state = TtsVoicePreviewState.IDLE
        )

        fun updatePreviewStatus(status: TtsVoicePreviewStatus) {
            val affectedKeys = listOfNotNull(previewStatus.key, status.key).distinct()
            previewStatus = status
            affectedKeys.forEach { key ->
                val position = getItems().indexOfFirst { item -> previewKey(item) == key }
                if (position >= 0) notifyItemChanged(position)
            }
        }

        override fun getViewBinding(parent: ViewGroup): ItemTtsVoiceBinding {
            return ItemTtsVoiceBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTtsVoiceBinding,
            item: TtsVoice,
            payloads: MutableList<Any>
        ) {
            val engine = currentDisplayedEngine()
            val isSystemEngine = engine?.type == TtsEngineType.SYSTEM
            val enabled = if (isSystemEngine) {
                engine.enabled
            } else {
                engine?.isVoiceEnabled(item) != false
            }
            binding.root.alpha = when {
                isSystemEngine -> 1f
                enabled -> 1f
                else -> 0.48f
            }
            TtsVoiceCardBinder.bind(
                context = requireContext(),
                binding = binding,
                item = item,
                engine = engine,
                isSystemEngine = isSystemEngine,
                showControls = true
            )
            TtsVoiceCardBinder.bindPreviewState(
                context = requireContext(),
                binding = binding,
                state = previewStatus.takeIf { it.key == previewKey(item) }
                    ?.state
                    ?: TtsVoicePreviewState.IDLE
            )
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (isSystemEngine) {
                    saveEnabledState(isChecked)
                    refreshEngines()
                } else {
                    val engineId = currentEngineId ?: return@setOnCheckedChangeListener
                    val updated = TtsEngineStore.setVoiceEnabled(engineId, item.id, isChecked)
                    if (updated != null) {
                        detailEngineSnapshot = updated
                    }
                    binding.root.alpha = if (isChecked) 1f else 0.48f
                    updateVoiceHeader()
                    refreshEngines()
                }
            }
        }

        private fun bindVoiceHeaderTags(
            binding: ItemTtsVoiceBinding,
            item: TtsVoice,
            isSystemEngine: Boolean
        ) {
            bindGenderIcon(binding, item.takeUnless { isSystemEngine }?.gender)
            val languageLabels = item.takeUnless { isSystemEngine }
                ?.language
                ?.let { TtsVoiceFilterSupport.languageLabels(it) }
                .orEmpty()
            binding.layoutLanguageTags.removeAllViews()
            binding.layoutLanguageTags.isVisible = languageLabels.isNotEmpty()
            languageLabels.forEach { label ->
                binding.layoutLanguageTags.addView(
                    createLanguageTagView(binding.layoutLanguageTags, label)
                )
            }
            binding.layoutHeaderTags.removeAllViews()
            val style = item.takeUnless { isSystemEngine }?.style?.takeIf { it.isNotBlank() }
            binding.layoutHeaderTags.isVisible = style != null
            style?.let {
                binding.layoutHeaderTags.addView(
                    createVoiceTagView(binding.layoutHeaderTags, coloredVoiceTag(it, 0))
                )
            }
        }

        private fun bindGenderIcon(binding: ItemTtsVoiceBinding, gender: String?) {
            when (gender?.takeIf { it.isNotBlank() }?.lowercase()) {
                "male", "man" -> {
                    binding.imageGender.isVisible = true
                    binding.imageGender.setImageResource(R.drawable.ic_tts_gender_male)
                    binding.imageGender.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.ng_tts_gender_male)
                    )
                }
                "female", "woman" -> {
                    binding.imageGender.isVisible = true
                    binding.imageGender.setImageResource(R.drawable.ic_tts_gender_female)
                    binding.imageGender.imageTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.ng_tts_gender_female)
                    )
                }
                else -> {
                    binding.imageGender.isVisible = false
                    binding.imageGender.setImageDrawable(null)
                    binding.imageGender.imageTintList = null
                }
            }
        }

        private fun bindVoiceTags(
            binding: ItemTtsVoiceBinding,
            item: TtsVoice,
            engine: TtsEngineSetting?,
            isSystemEngine: Boolean
        ) {
            val container = binding.layoutTags
            container.removeAllViews()
            val tags = if (isSystemEngine) {
                listOf(VoiceTag(engine?.name.orEmpty().ifBlank { item.id }))
            } else {
                buildVoiceTags(item)
            }
            binding.scrollTags.isVisible = tags.isNotEmpty()
            tags.forEach { tag ->
                container.addView(createVoiceTagView(container, tag))
            }
        }

        private fun buildVoiceTags(item: TtsVoice): List<VoiceTag> {
            val values = item.tags.filter { it.isNotBlank() }.distinct()
            if (values.isEmpty() && item.style.isNullOrBlank()) {
                return listOf(VoiceTag(item.id))
            }
            return values.mapIndexed { index, value -> coloredVoiceTag(value, index) }
        }

        private fun coloredVoiceTag(text: String, index: Int): VoiceTag {
            return when (index % 5) {
                0 -> VoiceTag(
                    text = text,
                    backgroundRes = R.drawable.ng_bg_tts_voice_tag_blue,
                    colorRes = R.color.ng_tts_tag_blue
                )
                1 -> VoiceTag(
                    text = text,
                    backgroundRes = R.drawable.ng_bg_tts_voice_tag_purple,
                    colorRes = R.color.ng_tts_tag_purple
                )
                2 -> VoiceTag(
                    text = text,
                    backgroundRes = R.drawable.ng_bg_tts_voice_tag_orange,
                    colorRes = R.color.ng_tts_tag_orange
                )
                3 -> VoiceTag(
                    text = text,
                    backgroundRes = R.drawable.ng_bg_tts_voice_tag_green,
                    colorRes = R.color.ng_tts_tag_green
                )
                else -> VoiceTag(
                    text = text,
                    backgroundRes = R.drawable.ng_bg_tts_voice_tag_pink,
                    colorRes = R.color.ng_tts_tag_pink
                )
            }
        }

        private fun createLanguageTagView(container: LinearLayout, text: String): TextView {
            val context = container.context
            return TextView(context).apply {
                this.text = text
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(context, R.color.ng_tts_language))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundResource(R.drawable.ng_bg_tts_language_tag)
                layoutParams = LinearLayout.LayoutParams(
                    if (text.length <= 1) 24.dpToPx() else 34.dpToPx(),
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = 4.dpToPx()
                }
            }
        }

        private fun createVoiceTagView(container: LinearLayout, tag: VoiceTag): TextView {
            val context = container.context
            val textColor = ContextCompat.getColor(context, tag.colorRes)
            return TextView(context).apply {
                text = tag.text
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundResource(tag.backgroundRes)
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                minWidth = 0
                maxWidth = 116.dpToPx()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = 6.dpToPx()
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTtsVoiceBinding) {
            binding.layoutPreviewButton.setOnClickListener {
                showPreviewClickFeedback(binding.imagePreview)
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    previewCurrentVoice(it)
                }
            }
            binding.layoutPreviewButton.setOnLongClickListener {
                showPreviewClickFeedback(binding.imagePreview)
                getItemByLayoutPosition(holder.layoutPosition)?.let { voice ->
                    val engine = currentDisplayedEngine()
                        ?: return@setOnLongClickListener true
                    val styles = voice.styleOptions()
                    if (styles.isEmpty()) {
                        requireContext().toastOnUi("当前发音人没有可选风格")
                    } else {
                        showPreviewStyleSelector(engine, voice, styles)
                    }
                }
                true
            }
        }

        private fun previewKey(item: TtsVoice): String? {
            val engine = currentDisplayedEngine() ?: return null
            return TtsVoicePreviewController.keyOf(
                engine = engine,
                voice = item,
                systemDefault = engine.type == TtsEngineType.SYSTEM
            )
        }

        private fun showPreviewClickFeedback(view: ImageView) {
            view.animate().cancel()
            view.scaleX = 0.88f
            view.scaleY = 0.88f
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .start()
        }
    }

    private data class VoiceGenderFilter(
        val label: String,
        val iconRes: Int,
        val colorRes: Int
    )

    private data class VoiceTag(
        val text: String,
        val backgroundRes: Int = R.drawable.ng_bg_tag_neutral,
        val colorRes: Int = R.color.ng_on_surface_variant
    )
}
