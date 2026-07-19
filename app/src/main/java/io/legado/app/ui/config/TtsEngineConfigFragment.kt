package io.legado.app.ui.config

import android.annotation.SuppressLint
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
import android.view.MotionEvent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.FragmentTtsEngineConfigBinding
import io.legado.app.databinding.ItemTtsConfigFieldBinding
import io.legado.app.databinding.ItemTtsEngineBinding
import io.legado.app.databinding.ItemTtsVoiceBinding
import io.legado.app.databinding.LayoutTtsVoiceParamsPopupBinding
import io.legado.app.constant.AppConst
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsScriptOption
import io.legado.app.help.tts.TtsScriptOptionValue
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.TtsVoiceStyle
import io.legado.app.help.tts.styleOptions
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.utils.applyTint
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readText
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsEngineConfigFragment : BaseFragment(R.layout.fragment_tts_engine_config),
    ConfigBackHandler {

    private enum class DetailTab { CONFIG, VOICES }

    private val binding by viewBinding(FragmentTtsEngineConfigBinding::bind)
    private val engineAdapter by lazy { EngineAdapter() }
    private val configAdapter by lazy { ConfigRuleAdapter() }
    private val voiceAdapter by lazy { VoiceAdapter() }
    private lateinit var engineItemTouchHelper: ItemTouchHelper
    private val configEntities = arrayListOf<ConfigField>()
    private var currentEngineId: String? = null
    private var detailEngineSnapshot: TtsEngineSetting? = null
    private var draftEngine: TtsEngineSetting? = null
    private var configOptionsJob: Job? = null
    private var configOptionsLoadedScript: String? = null
    private var scriptCodeLoadedEngineId: String? = null
    private var detailTab = DetailTab.CONFIG
    private var sourceMode = false
    private var allVoices: List<TtsVoice> = emptyList()
    private var voiceSearchQuery: String = ""
    private var voicePreviewController: TtsVoicePreviewController? = null
    private var voiceParamPopup: PopupWindow? = null
    private var voiceParamPopupBinding: LayoutTtsVoiceParamsPopupBinding? = null
    private var engineMenuButton: ImageButton? = null
    private val autoFetchedVoiceEngineIds = hashSetOf<String>()
    private val selectedVoiceLanguageFilters = linkedSetOf<String>()
    private val selectedVoiceGenderFilters = linkedSetOf<String>()
    private val importTtsEngineFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
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
        var passwordVisible: Boolean = false
    ) {
        val isOption: Boolean get() = key.startsWith("option:")
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.tts_engine_settings)
        setupEngineListMenu()
        binding.recyclerEngines.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEngines.setEdgeEffectColor(accentColor)
        binding.recyclerEngines.adapter = engineAdapter
        binding.refreshEngines.setColorSchemeColors(accentColor)
        binding.refreshEngines.setOnRefreshListener {
            refreshEngines()
            binding.refreshEngines.isRefreshing = false
        }
        engineItemTouchHelper = ItemTouchHelper(ItemTouchCallback(engineAdapter).apply {
            isCanDrag = true
            isCanSwipe = true
        })
        engineItemTouchHelper.attachToRecyclerView(binding.recyclerEngines)
        binding.recyclerConfigRules.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerConfigRules.setEdgeEffectColor(accentColor)
        binding.recyclerConfigRules.adapter = configAdapter
        binding.editScriptCode.setMaxHighlightLength(128 * 1024)
        binding.editScriptCode.addJsPattern()
        binding.recyclerVoices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVoices.setEdgeEffectColor(accentColor)
        binding.recyclerVoices.adapter = voiceAdapter

        binding.buttonTabConfig.setOnClickListener { showDetailTab(DetailTab.CONFIG) }
        binding.buttonTabVoices.setOnClickListener { showDetailTab(DetailTab.VOICES) }
        binding.buttonConfigSource.setOnClickListener { showConfigSourceMode(!sourceMode) }
        binding.buttonTestConfig.setOnClickListener { testCurrentEngineConnection() }
        binding.buttonSaveConfig.setOnClickListener { saveCurrentEngine() }
        binding.buttonVoiceParams.setOnClickListener { toggleVoiceParamPanel() }
        binding.buttonToggleAllVoices.setOnClickListener { toggleAllVoicesEnabled() }
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
        configOptionsJob?.cancel()
        configOptionsJob = null
        removeEngineListMenu()
        voiceParamPopup?.dismiss()
        voiceParamPopup = null
        voiceParamPopupBinding = null
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
        val engines = TtsEngineStore.engines()
        autoFetchedVoiceEngineIds.retainAll(engines.mapTo(hashSetOf()) { it.id })
        engineAdapter.setItems(engines)
    }

    private fun currentDisplayedEngine(): TtsEngineSetting? {
        val id = currentEngineId ?: return null
        return detailEngineSnapshot?.takeIf { it.id == id }
            ?: draftEngine?.takeIf { it.id == id }
            ?: TtsEngineStore.engine(id)
    }

    private fun showEngineList() {
        configOptionsJob?.cancel()
        configOptionsJob = null
        configOptionsLoadedScript = null
        currentEngineId = null
        detailEngineSnapshot = null
        draftEngine = null
        scriptCodeLoadedEngineId = null
        binding.editScriptCode.setText("")
        activity?.setTitle(R.string.tts_engine_settings)
        engineMenuButton?.isVisible = true
        binding.layoutEngineList.isVisible = true
        binding.layoutEngineDetail.isVisible = false
        refreshEngines()
    }

    private fun showEngineDetail(engine: TtsEngineSetting, tab: DetailTab = DetailTab.CONFIG) {
        val isSwitchingEngine = currentEngineId != engine.id
        configOptionsJob?.cancel()
        configOptionsJob = null
        configOptionsLoadedScript = null
        currentEngineId = engine.id
        detailEngineSnapshot = engine
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
            binding.editSearchVoice.setText("")
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
                )
            ),
            widthDp = 180
        ) { item ->
            when (item.itemId) {
                R.id.menu_tts_engine_add -> addTtsEngine()
                R.id.menu_tts_engine_import_local -> importTtsEngineFileLauncher.launch(
                    arrayOf("text/*", "application/json", "application/javascript", "application/octet-stream")
                )
                R.id.menu_tts_engine_import_online -> showImportTtsEngineUrlDialog()
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
                    TtsEngineStore.importEngineText(uri.readText(requireContext())).getOrThrow()
                }
            }
            handleTtsEngineImportResult(result)
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
                    val content = okHttpClient.newCallResponseBody {
                        if (target.endsWith("#requestWithoutUA")) {
                            url(target.substringBeforeLast("#requestWithoutUA"))
                            header(AppConst.UA_NAME, "null")
                        } else {
                            url(target)
                        }
                    }.decompressed().text()
                    TtsEngineStore.importEngineText(content).getOrThrow()
                }
            }
            handleTtsEngineImportResult(result)
        }
    }

    private fun handleTtsEngineImportResult(result: Result<List<TtsEngineSetting>>) {
        result.onSuccess { engines ->
            autoFetchedVoiceEngineIds.removeAll(engines.map { it.id }.toSet())
            refreshEngines()
            requireContext().toastOnUi("已导入 ${engines.size} 个朗读引擎")
        }.onFailure {
            requireContext().toastOnUi(it.localizedMessage ?: "导入失败")
        }
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
        setVoiceSearchPillVisible(true)
        val activeColor = accentColor
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.ng_on_surface_variant)
        binding.buttonTabConfig.imageTintList = ColorStateList.valueOf(
            if (tab == DetailTab.CONFIG) activeColor else inactiveColor
        )
        binding.buttonTabVoices.imageTintList = ColorStateList.valueOf(
            if (tab == DetailTab.VOICES) activeColor else inactiveColor
        )
        binding.buttonTabConfig.setBackgroundResource(android.R.color.transparent)
        binding.buttonTabVoices.setBackgroundResource(android.R.color.transparent)
        if (tab == DetailTab.VOICES) {
            maybeAutoFetchVoices()
        }
    }

    private fun bindEngineForm(engine: TtsEngineSetting) = binding.run {
        switchEnabled.setOnCheckedChangeListener(null)
        switchEnabled.isChecked = engine.enabled
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            saveEnabledState(isChecked)
        }
        bindConfigEntities(engine)
        if (sourceMode) {
            ensureScriptCodeLoaded(engine)
        }

        val scriptEnabled = engine.type == TtsEngineType.SCRIPT
        configAdapter.enabled = scriptEnabled
        editScriptCode.isEnabled = scriptEnabled
        refreshVoices.isEnabled = engine.supportsVoiceFetch()
        showConfigSourceMode(sourceMode)
    }

    private fun saveEnabledState(enabled: Boolean) {
        val source = currentDisplayedEngine() ?: return
        if (source.enabled == enabled) {
            return
        }
        val updated = source.copy(enabled = enabled)
        detailEngineSnapshot = updated
        TtsEngineStore.saveEngine(updated)
    }

    private fun showConfigSourceMode(enabled: Boolean) {
        val source = currentDisplayedEngine()
        if (enabled && !sourceMode) {
            source?.let {
                ensureScriptCodeLoaded(engineFromForm(it))
            }
        } else if (!enabled && sourceMode) {
            val updated = source?.let { engineFromForm(it) } ?: return
            detailEngineSnapshot = updated
            bindConfigEntities(updated)
            binding.switchEnabled.isChecked = updated.enabled
            activity?.setTitle(updated.name)
        }
        sourceMode = enabled
        binding.scrollConfigForm.isVisible = !enabled
        binding.layoutScriptEditor.isVisible = enabled
        binding.layoutEngineDetailTabs.isVisible = !enabled
        binding.buttonConfigSource.setText(
            if (enabled) R.string.tts_form_mode else R.string.tts_source_mode
        )
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
            val options = withContext(Dispatchers.Default) {
                runCatching { TtsScriptEngineClient.loadOptions(engine) }
                    .getOrDefault(emptyList())
            }
            val current = currentDisplayedEngine()
            if (
                currentEngineId != requestedEngineId ||
                current?.script != requestedScript
            ) {
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
            entities.add(
                ConfigField(
                    "option:${option.safeKey}",
                    values[option.safeKey].orEmpty(),
                    option.displayLabel,
                    option.normalizedType,
                    option.safeValues
                )
            )
        }
        configEntities.clear()
        configEntities.addAll(entities)
        configAdapter.editEntities = ArrayList(configEntities)
    }

    private fun saveCurrentEngine(
        showToast: Boolean = true,
        restartReadAloud: Boolean = true
    ): TtsEngineSetting? {
        val source = currentDisplayedEngine()
            ?: draftEngine?.takeIf { it.id == currentEngineId }
            ?: return null
        val updated = engineFromForm(source)
        TtsEngineStore.saveEngine(updated, restartReadAloud)
        val effective = TtsEngineStore.engine(updated.id) ?: updated
        detailEngineSnapshot = effective
        draftEngine = null
        activity?.setTitle(effective.name)
        bindVoiceParams(effective)
        refreshEngines()
        if (showToast) {
            requireContext().toastOnUi("保存成功")
        }
        return effective
    }

    private fun engineFromForm(source: TtsEngineSetting): TtsEngineSetting {
        val script = binding.editScriptCode.text?.toString()?.takeIf { it.isNotBlank() }
            ?: source.script
        val metadata = TtsEngineStore.parseScriptMetadata(script)
        val displayedOptionValues = configEntities
            .filter { it.isOption }
            .associate { it.key.removePrefix("option:") to it.value.orEmpty() }
        val optionValues = if (configOptionsLoadedScript == script) {
            displayedOptionValues
        } else {
            source.optionValues + displayedOptionValues
        }
        return source.copy(
            name = configValue("name").ifBlank { source.name },
            enabled = binding.switchEnabled.isChecked,
            script = script,
            sampleText = metadata["sampletext"]?.takeIf { it.isNotBlank() },
            optionValues = optionValues,
            enabledCookieJar = cookieJarFromScript(source)
        )
    }

    private fun cookieJarFromScript(source: TtsEngineSetting): Boolean? {
        val script = binding.editScriptCode.text?.toString()?.takeIf { it.isNotBlank() }
            ?: source.script
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
        setupVoiceParamSeekBars(popupBinding)
        bindVoiceParamPopup(popupBinding, engine)
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
    }

    private fun bindVoiceParamPopup(
        popupBinding: LayoutTtsVoiceParamsPopupBinding,
        engine: TtsEngineSetting
    ) = popupBinding.run {
        seekSpeed.progress = engine.effectiveSpeed()
        seekVolume.progress = engine.effectiveVolume()
        seekPitch.progress = engine.effectivePitch()
        updateVoiceParamTexts(this)
        bindVoiceFilterChips(this)
    }

    private fun setupVoiceParamSeekBars(popupBinding: LayoutTtsVoiceParamsPopupBinding) {
        tintVoiceParamSeekBars(popupBinding)
        val listener = object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateVoiceParamTexts(popupBinding)
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                currentEngineId?.let { engineId ->
                    TtsEngineStore.saveRuntimeParams(
                        engineId = engineId,
                        speed = popupBinding.seekSpeed.progress,
                        volume = popupBinding.seekVolume.progress,
                        pitch = popupBinding.seekPitch.progress
                    )
                }
            }
        }
        popupBinding.seekSpeed.setOnSeekBarChangeListener(listener)
        popupBinding.seekVolume.setOnSeekBarChangeListener(listener)
        popupBinding.seekPitch.setOnSeekBarChangeListener(listener)
    }

    private fun tintVoiceParamSeekBars(popupBinding: LayoutTtsVoiceParamsPopupBinding) {
        val accent = accentColor
        val trackTint = ColorStateList.valueOf(ColorUtils.adjustAlpha(accent, 0.35f))
        val thumbTint = ColorStateList.valueOf(accent)
        listOf(
            popupBinding.seekSpeed,
            popupBinding.seekVolume,
            popupBinding.seekPitch
        ).forEach { seekBar ->
            seekBar.progressTintList = trackTint
            seekBar.progressBackgroundTintList = trackTint
            seekBar.secondaryProgressTintList = trackTint
            seekBar.thumbTintList = thumbTint
        }
    }

    private fun updateVoiceParamTexts(popupBinding: LayoutTtsVoiceParamsPopupBinding) = popupBinding.run {
        textSpeedValue.text = seekSpeed.progress.toString()
        textVolumeValue.text = seekVolume.progress.toString()
        textPitchValue.text = seekPitch.progress.toString()
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
                    bottomMargin = 6.dpToPx()
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
        return sortVoiceFilterLabels(
            allVoices
                .flatMap { voiceLanguageLabels(it.language) }
                .distinct()
        )
    }

    private fun sortVoiceFilterLabels(labels: List<String>): List<String> {
        val preferredOrder = listOf("中", "英", "日", "韩", "法", "德", "西", "葡", "粤", "吴")
        return labels.sortedWith(
            compareBy<String> {
                preferredOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it }
        )
    }

    private fun voiceLanguageLabels(language: String?): List<String> {
        val raw = language?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val values = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace(';', ',')
            .replace('；', ',')
            .replace('/', ',')
            .replace('|', ',')
            .replace('+', ',')
            .split(',', ' ', '\n', '\t')
            .mapNotNull { voiceLanguageLabel(it) }
            .distinct()
        return values.ifEmpty {
            voiceLanguageLabel(raw)?.let { listOf(it) }.orEmpty()
        }
    }

    private fun voiceLanguageLabel(language: String?): String? {
        val code = language?.takeIf { it.isNotBlank() }
            ?.substringBefore("-")
            ?.substringBefore("_")
            ?.lowercase()
            ?: return null
        return when (code) {
            "zh", "cmn" -> "中"
            "yue" -> "粤"
            "wuu" -> "吴"
            "en" -> "英"
            "ja", "jp", "jpn" -> "日"
            "ko", "kr", "kor" -> "韩"
            "fr", "fra", "fre" -> "法"
            "de", "deu", "ger" -> "德"
            "es", "esp", "spa" -> "西"
            "pt", "por" -> "葡"
            else -> code.uppercase()
        }
    }

    private fun voiceGenderLabel(gender: String?): String? {
        return when (gender?.takeIf { it.isNotBlank() }?.lowercase()) {
            "male", "man" -> "男"
            "female", "woman" -> "女"
            else -> null
        }
    }

    private fun setupVoiceSearch() {
        binding.editSearchVoice.addTextChangedListener(object : TextWatcher {
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
        updateVoiceHeader()
    }

    private fun matchesVoiceSearch(voice: TtsVoice, query: String): Boolean {
        if (query.isBlank()) {
            return true
        }
        return listOfNotNull(
            voice.name,
            voice.id,
            voice.language,
            voice.gender,
            voice.style,
            voice.tags.joinToString("/")
        ).any { it.lowercase().contains(query) }
    }

    private fun matchesVoiceLanguageFilter(voice: TtsVoice): Boolean {
        if (selectedVoiceLanguageFilters.isEmpty()) {
            return true
        }
        return voiceLanguageLabels(voice.language).any { it in selectedVoiceLanguageFilters }
    }

    private fun matchesVoiceGenderFilter(voice: TtsVoice): Boolean {
        if (selectedVoiceGenderFilters.isEmpty()) {
            return true
        }
        return voiceGenderLabel(voice.gender)?.let { it in selectedVoiceGenderFilters } == true
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
        val engine = saveCurrentEngine(
            showToast = false,
            restartReadAloud = false
        )?.takeIf { it.isScriptEngine } ?: return
        if (!engine.supportsVoiceFetch()) {
            binding.refreshVoices.isRefreshing = false
            binding.textVoiceMessage.isVisible = false
            return
        }
        binding.refreshVoices.isRefreshing = true
        binding.textVoiceMessage.isVisible = false
        lifecycleScope.launch {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        TtsScriptEngineClient.fetchVoices(engine)
                    }
                }.onSuccess { voices ->
                    if (voices.isEmpty()) {
                        binding.refreshVoices.isRefreshing = false
                        binding.textVoiceMessage.isVisible = true
                        binding.textVoiceMessage.text = "接口返回为空或暂不支持解析"
                        requireContext().toastOnUi("未获取到发音人")
                        return@onSuccess
                    }
                    val updated = TtsEngineStore.upsertVoiceList(
                        engine.id,
                        voices,
                        restartReadAloud = false
                    )
                    if (updated != null) {
                        detailEngineSnapshot = updated
                        val effectiveVoices = updated.effectiveVoices()
                        setVoiceItems(effectiveVoices)
                        updateVoiceMessage(updated)
                        refreshEngines()
                        requireContext().toastOnUi("已获取 ${effectiveVoices.size} 个发音人")
                    }
                }.onFailure {
                    val message = it.localizedMessage ?: it.javaClass.simpleName
                    binding.textVoiceMessage.isVisible = true
                    binding.textVoiceMessage.text = "获取失败：$message"
                    requireContext().toastOnUi("获取发音人失败")
                }
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
        val engine = saveCurrentEngine(
            showToast = false,
            restartReadAloud = false
        )?.takeIf { it.isScriptEngine } ?: return
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

    private fun testCurrentEngineConnection() {
        val engine = saveCurrentEngine(
            showToast = false,
            restartReadAloud = false
        )?.takeIf { it.isScriptEngine } ?: return
        val voice = engine.effectiveVoices().firstOrNull { it.id == engine.activeVoiceId }
            ?: engine.effectiveVoices().firstOrNull()
        val styleId = voice?.let { savedPreviewStyleId(engine, it, it.styleOptions()) }
        val context = context ?: return
        context.toastOnUi("正在测试接口...")
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val started = SystemClock.elapsedRealtime()
                    val response = TtsScriptEngineClient.getSynthesisResponse(
                        engine = engine,
                        text = TtsScriptEngineClient.sampleText(engine, voice),
                        voiceId = voice?.id,
                        styleId = styleId
                    )
                    response.use {
                        val contentType = it.header("Content-Type").orEmpty()
                        val normalizedContentType = contentType.substringBefore(";")
                        if (normalizedContentType == "application/json" ||
                            normalizedContentType.startsWith("text/")
                        ) {
                            error(it.body.string().take(200))
                        }
                        val firstBytes = ByteArray(512)
                        val readBytes = it.body.byteStream().use { input ->
                            input.read(firstBytes)
                        }
                        if (readBytes <= 0) {
                            error("接口未返回音频内容")
                        }
                        val elapsed = SystemClock.elapsedRealtime() - started
                        "接口可用：${elapsed}ms，$normalizedContentType"
                    }
                }
            }
            result.onSuccess {
                context.toastOnUi(it)
            }.onFailure {
                context.toastOnUi("接口测试失败：${it.localizedMessage ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun setVoiceSearchPillVisible(visible: Boolean) {
        binding.layoutVoiceSearch.getChildAt(0)?.visibility = if (visible) {
            View.VISIBLE
        } else {
            View.INVISIBLE
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
            okButton {
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

    private inner class EngineAdapter :
        RecyclerAdapter<TtsEngineSetting, ItemTtsEngineBinding>(requireContext()),
        ItemTouchCallback.Callback {

        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemTtsEngineBinding {
            return ItemTtsEngineBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTtsEngineBinding,
            item: TtsEngineSetting,
            payloads: MutableList<Any>
        ) {
            binding.textName.text = item.name
            binding.imageIcon.imageTintList = ColorStateList.valueOf(accentColor)
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
            binding.textType.text = when (item.type) {
                TtsEngineType.SYSTEM -> "系统"
                TtsEngineType.SCRIPT -> "脚本"
            }
            binding.textVoiceCount.text = when {
                item.type == TtsEngineType.SYSTEM -> "默认发音人"
                item.effectiveVoices().isEmpty() -> "未获取"
                else -> "${item.effectiveVoices().size} 个发音人"
            }
            binding.imageDragHandle.alpha = 1f
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun registerListener(holder: ItemViewHolder, binding: ItemTtsEngineBinding) {
            binding.layoutSelectEngine.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { showEngineDetail(it) }
            }
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { showEngineDetail(it) }
            }
            binding.imageDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    engineItemTouchHelper.startDrag(holder)
                }
                true
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun getSwipeFlags(adapterPosition: Int, defaultFlags: Int): Int {
            val item = getItems().getOrNull(adapterPosition) ?: return 0
            return if (TtsEngineStore.isDeletableEngine(item)) ItemTouchHelper.RIGHT else 0
        }

        override fun onSwiped(adapterPosition: Int, direction: Int) {
            val item = getItems().getOrNull(adapterPosition)
            if (
                item == null ||
                !TtsEngineStore.isDeletableEngine(item) ||
                direction != ItemTouchHelper.RIGHT
            ) {
                refreshEngines()
                return
            }
            confirmDeleteEngine(
                engine = item,
                onCancel = { refreshEngines() },
                onDeleted = {
                    autoFetchedVoiceEngineIds.remove(item.id)
                    refreshEngines()
                }
            )
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved) {
                TtsEngineStore.saveEngines(getItems())
            }
            isMoved = false
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
                ?.let { voiceLanguageLabels(it) }
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
