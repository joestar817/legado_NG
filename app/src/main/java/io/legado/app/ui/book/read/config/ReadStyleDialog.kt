package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.google.gson.Gson
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.google.gson.reflect.TypeToken
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.appDb
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isEpub
import io.legado.app.help.globalExecutor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.PresetNames
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.EpubScriptFontHealth
import io.legado.app.help.config.ReadPresetPreferences
import io.legado.app.help.config.ReadValueScope
import io.legado.app.help.config.ReadValueSource
import io.legado.app.help.config.ReadStyleLanguageBinder
import io.legado.app.help.config.ReadStyleLanguageMap
import io.legado.app.help.config.ReadStylePackageManager
import io.legado.app.help.config.LatinOpticalScale
import io.legado.app.help.config.LatinOpticalScaleStore
import io.legado.app.help.config.ReadScriptTypographyStore
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadHighlightRulePackageManager
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.CreateDocumentContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.ui.design.theme.ReadingDisplayMode
import io.legado.app.ui.design.theme.ReadingPaletteCatalog
import io.legado.app.ui.design.theme.ReadingPaletteSeed
import io.legado.app.ui.design.theme.SemanticPaletteEngine
import io.legado.app.utils.hexString
import io.legado.app.utils.inputStream
import io.legado.app.utils.longToast
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.outputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

private val EDITOR_FALLBACK_BG_COLOR = 0xFFF7F3EA.toInt()

class ReadStyleDialog : BaseComposeDialogFragment(),
    FontSelectDialog.CallBack {

    private val callBack get() = activity as? ReadBookActivity
    private lateinit var composeView: ComposeView
    private var page by mutableStateOf(ReadStylePage.PRESET)
    private var screenState by mutableStateOf<ReadStyleUiState?>(null)
    private var showEpubSettings by mutableStateOf(false)
    private var editorBackgroundCache: List<ReadStyleBackgroundUi>? = null
    private var backgroundColorPickerDialog: ComponentDialog? = null
    private var editingHighlightIndex: Int? = null
    private var creatingPreset = false
    private var namingPreset = false
    private val appliedPresetRebinds = ArrayDeque<Pair<String, String>>()
    private var colorSessionAppearance: ReadBookConfig.Config? = null
    private var highlightDraft: ReadHighlightRule? = null
    private var highlightSelectionMode = HighlightSelectionMode.NONE
    private var selectedHighlightIds: Set<String> = emptySet()
    private var pendingHighlightExportName: String? = null
    private var pendingPresetExportName: String? = null
    private var preparingPresetExport = false
    private var preparingHighlightExport = false
    private var openTipConfigAfterDismiss = false
    private var sessionSnapshot: ReadStyleSnapshot? = null
    private var sessionSnapshotJson: String = ""
    private var unsavedConfirmCancelled by mutableIntStateOf(0)
    private var unsavedConfirmShowing = false
    private var closing = false
    private var pendingScriptFontScope by mutableStateOf<ReadValueScope?>(null)
    private var pendingEditorScriptFontScope by mutableStateOf<ReadValueScope?>(null)
    private var currentPage: ReadStylePage = ReadStylePage.PRESET
    private val configFileName = "readConfig.zip"
    private val selectExportDocument = registerForActivityResult(
        CreateDocumentContract("application/zip")
    ) { uri ->
        val name = pendingPresetExportName
        pendingPresetExportName = null
        if (uri != null) exportPreparedPackage(uri, name, "阅读预设")
        else name?.let { File(requireContext().filesDir, it).delete() }
    }
    private val selectImportDocument = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let(::importConfig) }
    private val selectHighlightExportDocument = registerForActivityResult(
        CreateDocumentContract("application/zip")
    ) { uri ->
        val packageName = pendingHighlightExportName
        pendingHighlightExportName = null
        if (uri != null) {
            exportPreparedPackage(uri, packageName, "高亮规则")
        } else {
            packageName?.let { File(requireContext().filesDir, it).delete() }
        }
    }
    private val selectHighlightImportDocument = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let(::importHighlightRules) }
    private val selectBackgroundImage = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let(::setBackgroundFromUri) }
    private val selectHighlightBackground = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let { installHighlightResource(it, "background") } }
    private val selectHighlightFont = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let { installHighlightResource(it, "font") } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingHighlightExportName = savedInstanceState?.getString("pendingHighlightExportName")
        pendingPresetExportName = savedInstanceState?.getString("pendingPresetExportName")
        sessionSnapshotJson = savedInstanceState?.getString("sessionSnapshotJson") ?: ""
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingHighlightExportName", pendingHighlightExportName)
        outState.putString("pendingPresetExportName", pendingPresetExportName)
        outState.putString("sessionSnapshotJson", sessionSnapshotJson)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0.0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        ReadBookConfig.explicitStyleSelection = true
        (activity as ReadBookActivity).bottomDialog++
        composeView = view as ComposeView
        composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (sessionSnapshotJson.isEmpty()) {
            captureSessionSnapshot()
        } else {
            sessionSnapshot = runCatching {
                Gson().fromJson(sessionSnapshotJson, ReadStyleSnapshot::class.java)
            }.getOrNull()
        }
        refreshUi()
        // EPUB 可见 surface 报告脚本字体加载失败时，实时刷新 Language fonts 删除线。
        lifecycleScope.launch {
            EpubScriptFontHealth.failedScopes.collect { refreshUi() }
        }
        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                screenState?.let { state ->
                    val primaryStrength = ReadFloatingAppearanceState.primaryStrengthPercent
                    val colorStyle = ReadFloatingAppearanceState.colorStyle
                    NgAppTheme(
                        snapshot = ReadDrawerStyle.themeSnapshot(
                            context = requireContext(),
                            primaryStrengthPercent = primaryStrength,
                            colorStyle = colorStyle,
                        ),
                        updateSystemBars = false,
                    ) {
                        NgDismissibleDrawer(
                            onDismiss = { requestDismiss() },
                            resetSignal = unsavedConfirmCancelled,
                        ) {
                            ReadStyleScreen(
                                page = page,
                                state = state,
                                contentColor = Color(ReadDrawerStyle.contentColor(requireContext())),
                                accentColor = Color(ReadDrawerStyle.accentColor(requireContext())),
                                actions = createActions(),
                            )
                        }
                        if (showEpubSettings) {
                            ReadBook.book?.takeIf { it.isEpub }?.let { book ->
                                EpubLayoutSheet(book,
                                    onStyleChanged = {
                                        if (ReadBook.book === book) postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                                    },
                                    onDismiss = { showEpubSettings = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        clearEditorThemeOverride()
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
        if (openTipConfigAfterDismiss) {
            openTipConfigAfterDismiss = false
            if (!parentFragmentManager.isStateSaved) {
                TipConfigDialog().show(parentFragmentManager, "tipConfigDialog")
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = object : ComponentDialog(requireContext(), theme) {
            override fun cancel() {
                if (closing) {
                    super.cancel()
                } else {
                    requestDismiss()
                }
            }
        }
        // cancel() 覆盖同时覆盖返回键与点外部；Compose BackHandler 只负责页内导航。
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onDestroyView() {
        backgroundColorPickerDialog?.dismiss()
        backgroundColorPickerDialog = null
        ReadBook.book?.let(ReadStyleLanguageBinder::rememberCurrentStyle)
        // 转屏若拆掉界面，语言匹配会在新界面起来之前跑。守卫留着，避免选中被拽回第一张预设。
        if (activity?.isChangingConfigurations != true) {
            ReadBookConfig.explicitStyleSelection = false
        }
        // 旋转等场景不会走 onDismiss，这里兜底清除临时日/夜预览
        clearEditorThemeOverride()
        super.onDestroyView()
    }

    private fun createActions() = ReadStyleActions(
        onPageSelected = ::navigateTo,
        onCreatePreset = ::promptNewPreset,
        onSelectPreset = ::changeBgTextConfig,
        onImportPreset = {
            selectImportDocument.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        },
        onEditPreset = { openEditor(ReadBookConfig.styleSelect) },
        onExportPreset = {
            preparePresetExport()
        },
        onDeletePreset = ::deleteCurrentStyle,
        onRestoreCurrentPreset = ::confirmRestoreCurrentPreset,
        onRestoreAllPresets = ::confirmRestoreAllPresets,
        onOpenEpubSettings = { showEpubSettings = true },
        onOnlyThisBookChanged = { enabled ->
            if (enabled) {
                // Gate B：开启即首次提交，物化 legacy → 显式 pinned basePreset
                ReadBookConfig.setOnlyThisBook(true)
                ReadBookConfig.materializeBookBasePresetIfNeeded()
            } else {
                // Gate A：关闭 = 重置本书全部自定义（同时清 overrides 与 legacy）
                ReadBookConfig.resetBookCustomization()
            }
            editorBackgroundCache = null
            ReadFloatingAppearanceState.refreshFromConfig()
            refreshUi()
            notifyPresetRestored()
        },
        onShareLayoutChanged = { checked ->
            ReadBookConfig.shareLayout = checked
            refreshUi()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        },
        onGlobalFloatingFollowAppChanged = { checked ->
            ReadBookConfig.readFloatingFollowAppGlobally = checked
            refreshUi()
            notifyFloatingAppearanceChanged()
        },
        onLanguagePresetChanged = { script, name ->
            ReadStyleLanguageMap.set(script, name)
            refreshUi()
        },
        onImportHighlights = {
            selectHighlightImportDocument.launch(
                arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream")
            )
        },
        onExportHighlights = { beginHighlightSelection(HighlightSelectionMode.EXPORT) },
        onRestoreBuiltInHighlights = ::confirmRestoreBuiltInHighlights,
        onDeleteHighlights = { beginHighlightSelection(HighlightSelectionMode.DELETE) },
        onToggleHighlightSelection = ::toggleHighlightSelection,
        onToggleAllHighlightSelection = ::toggleAllHighlightSelection,
        onCancelHighlightSelection = ::clearHighlightSelection,
        onConfirmHighlightSelection = ::confirmHighlightSelection,
        onBack = ::navigateBack,
        onEditorThemeModeToggle = ::toggleEditorThemeMode,
        onPresetNameChanged = { value ->
            updateEditorState { copy(presetNameDraft = value) }
        },
        onTextColorChanged = ::applyEditorTextColor,
        onBackgroundColorChanged = ::applyEditorBackgroundColor,
        onTextAccentColorChanged = ::applyEditorAccentColor,
        onHighlightColorChanged = ::applyEditorHighlightColor,
        onTextColorPreviewed = { applyEditorTextColor(it, notifyReader = false) },
        onBackgroundColorPreviewed = { applyEditorBackgroundColor(it, notifyReader = false) },
        onHighlightColorPreviewed = { applyEditorHighlightColor(it, notifyReader = false) },
        onPaperLookApplied = ::applyPaperLook,
        onResetEditorColor = ::resetEditorColor,
        onBackgroundAlphaChanged = { alpha ->
            val safeAlpha = alpha.coerceIn(0, 100)
            ReadBookConfig.bgAlpha = safeAlpha
            updateEditorState { copy(editorBackgroundAlpha = safeAlpha) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(3))
        },
        onSelectBackgroundImage = {
            selectBackgroundImage.launch(arrayOf("image/*"))
        },
        onSelectBackground = ::selectBackground,
        onFloatingColorSourceChanged = ::setFloatingColorSource,
        onPickFloatingColor = ::pickFloatingColor,
        onFloatingTransparencyChanged = { value ->
            val transparency = ReadFloatingAppearanceConfig.normalizePercent(value)
            ReadBookConfig.readFloatingGlobalTransparency = transparency
            updateEditorState { copy(editorFloatingTransparency = transparency) }
            ReadFloatingAppearanceState.update(
                transparency,
                ReadBookConfig.readFloatingGlobalPrimaryStrength,
                ReadBookConfig.effectiveReadFloatingColor().colorStyle,
            )
        },
        onFloatingPrimaryStrengthChanged = { value ->
            val strength = ReadFloatingAppearanceConfig.normalizePercent(value)
            ReadBookConfig.readFloatingGlobalPrimaryStrength = strength
            updateEditorState { copy(editorFloatingPrimaryStrength = strength) }
            ReadFloatingAppearanceState.update(
                ReadBookConfig.readFloatingGlobalTransparency,
                strength,
                ReadBookConfig.effectiveReadFloatingColor().colorStyle,
            )
        },
        onFloatingColorStyleChanged = { style ->
            ReadBookConfig.readFloatingGlobalColorStyle = style
            updateEditorState { copy(editorFloatingColorStyle = style) }
            ReadFloatingAppearanceState.update(
                ReadBookConfig.readFloatingGlobalTransparency,
                ReadBookConfig.readFloatingGlobalPrimaryStrength,
                style,
            )
            notifyFloatingAppearanceChanged()
        },
        onFloatingAppearanceChangeFinished = {
            ReadBookConfig.save()
            notifyFloatingAppearanceChanged()
        },
        onFullLineUnderlineEnabledChanged = { enabled ->
            ReadBookConfig.fullLineUnderlineEnabled = enabled
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineDashedChanged = { dashed ->
            ReadBookConfig.dottedLine = dashed
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineColorChanged = { color ->
            ReadBookConfig.config.setCurUnderlineColor(color)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineWidthChanged = { width ->
            ReadBookConfig.underlineHeight = width.coerceIn(1, 20)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineOffsetChanged = { offset ->
            ReadBookConfig.underlinePadding = offset.coerceIn(0, 20)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineExtendChanged = { extend ->
            ReadBookConfig.underlineExtend = extend
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineDashLengthChanged = { length ->
            ReadBookConfig.dottedBase = length.coerceIn(1f, 20f)
            refreshFullLineUnderlineState()
        },
        onFullLineUnderlineGapLengthChanged = { length ->
            ReadBookConfig.dottedRatio = length.coerceIn(1f, 20f)
            refreshFullLineUnderlineState()
        },
        onFontWeight = ::showFontWeightSetting,
        onFont = ::selectBodyFont,
        onIndent = ::showParagraphIndentSetting,
        onChineseConverter = ::showChineseConverterSetting,
        onPadding = {
            PaddingConfigDialog().show(childFragmentManager, "paddingConfigDialog")
        },
        onTip = ::showTipConfigCentered,
        onTextSizeChanged = { value ->
            ReadBookConfig.textSize = value
            updateAdjustState { copy(textSize = value) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onLetterSpacingChanged = { value ->
            val rounded = (value * 100).roundToInt() / 100f
            ReadBookConfig.letterSpacing = rounded
            updateAdjustState { copy(letterSpacing = rounded) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onLineSpacingChanged = { value ->
            ReadBookConfig.lineSpacingExtra = value
            updateAdjustState { copy(lineSpacingExtra = value) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onParagraphSpacingChanged = { value ->
            ReadBookConfig.paragraphSpacing = value
            updateAdjustState { copy(paragraphSpacing = value) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onPageAnimChanged = { pageAnim ->
            ReadBook.book?.setPageAnim(-1)
            ReadBookConfig.pageAnim = pageAnim
            updateAdjustState { copy(pageAnim = pageAnim) }
            callBack?.upPageAnim()
            ReadBook.loadContent(false)
        },
        onCreateHighlight = { openHighlightEditor() },
        onEditHighlight = { openHighlightEditor(it) },
        onCopyHighlight = ::copyHighlightRule,
        onHighlightDraftChanged = { draft ->
            highlightDraft = draft
            updateEditorState { copy(highlightDraft = draft) }
        },
        onSelectHighlightBackground = {
            selectHighlightBackground.launch(arrayOf("image/*"))
        },
        onClearHighlightBackground = {
            updateHighlightDraft { copy(bgImage = null) }
        },
        onSelectHighlightFont = {
            selectHighlightFont.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-opentype",
                    "application/octet-stream",
                )
            )
        },
        onClearHighlightFont = {
            updateHighlightDraft { copy(fontPath = null) }
        },
        onSaveHighlight = ::saveHighlightDraft,
        onDeleteHighlight = ::deleteHighlightDraft,
        onHighlightEnabledChanged = { index, enabled ->
            val rules = currentRules().toMutableList()
            rules.getOrNull(index)?.let { rule ->
                rules[index] = rule.copy(enabled = enabled)
                applyHighlightRules(rules)
            }
        },
        onReorderHighlights = { rules ->
            if (rules.map(ReadHighlightRule::id) != currentRules().map(ReadHighlightRule::id)) {
                applyHighlightRules(rules)
            }
        },
        onDone = ::commitDone,
        onDiscard = ::discardAndLeave,
        onDismissRequest = ::requestDismiss,
        onOpenLanguageFonts = ::openLanguageFonts,
        onOpenNewBookPreset = ::openNewBookPreset,
        onOpenFloatingWindows = ::openFloatingWindows,
        onSelectScriptFont = ::selectScriptFont,
        onResetScriptFont = ::resetScriptFont,
        onSelectEditorScriptFont = ::selectEditorScriptFont,
        onResetEditorScriptFont = ::resetEditorScriptFont,
        onLatinScaleChanged = { value ->
            val rounded = (value * 100).roundToInt() / 100f
            LatinOpticalScaleStore.save(LatinOpticalScale.clamp(rounded))
            updateAdjustState { copy(latinScale = LatinOpticalScale.clamp(rounded)) }
        },
        onLatinScaleChangeFinished = {
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
        onLatinScaleReset = {
            LatinOpticalScaleStore.save(null)
            updateAdjustState { copy(latinScale = null) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        },
    )

    private fun updateAdjustState(transform: ReadStyleUiState.() -> ReadStyleUiState) {
        // 滑块拖动连续触发：只置脏，避免每帧全量 GSON 序列化（关闭/确认时 computeUnsaved 仍会精确比对）
        screenState = screenState?.transform()?.copy(hasUnsavedChanges = true)
    }

    private fun updateEditorState(transform: ReadStyleUiState.() -> ReadStyleUiState) {
        screenState = screenState?.transform()?.copy(hasUnsavedChanges = computeUnsaved())
    }

    private data class ReadStyleSnapshot(
        val configListJson: String,
        val shareConfigJson: String,
        val onlyThisBook: Boolean,
        val bookConfigJson: String?,
        val styleSelect: Int,
        val comicStyleSelect: Int,
        val scriptTypographyJson: String? = null,
        val bookOverridesJson: String? = null,
        val latinScaleJson: String? = null,
    )

    private fun captureSessionSnapshot() {
        val gson = Gson()
        val snapshot = ReadStyleSnapshot(
            configListJson = gson.toJson(ReadBookConfig.configList),
            shareConfigJson = gson.toJson(ReadBookConfig.shareConfig),
            onlyThisBook = ReadBookConfig.onlyThisBook,
            bookConfigJson = if (ReadBookConfig.onlyThisBook) gson.toJson(ReadBookConfig.durConfig) else null,
            styleSelect = ReadBookConfig.styleSelect,
            comicStyleSelect = ReadBookConfig.comicStyleSelect,
            scriptTypographyJson = ReadScriptTypographyStore.snapshotJson(),
            bookOverridesJson = ReadBookConfig.snapshotBookOverridesJson(),
            latinScaleJson = LatinOpticalScaleStore.snapshotJson(),
        )
        sessionSnapshot = snapshot
        sessionSnapshotJson = gson.toJson(snapshot)
    }

    private fun currentSnapshotJson(): String {
        val gson = Gson()
        return gson.toJson(
            ReadStyleSnapshot(
                configListJson = gson.toJson(ReadBookConfig.configList),
                shareConfigJson = gson.toJson(ReadBookConfig.shareConfig),
                onlyThisBook = ReadBookConfig.onlyThisBook,
                bookConfigJson = if (ReadBookConfig.onlyThisBook) gson.toJson(ReadBookConfig.durConfig) else null,
                styleSelect = ReadBookConfig.styleSelect,
                comicStyleSelect = ReadBookConfig.comicStyleSelect,
                scriptTypographyJson = ReadScriptTypographyStore.snapshotJson(),
                bookOverridesJson = ReadBookConfig.snapshotBookOverridesJson(),
                latinScaleJson = LatinOpticalScaleStore.snapshotJson(),
            )
        )
    }

    private fun computeUnsaved(): Boolean = sessionSnapshot != null && currentSnapshotJson() != sessionSnapshotJson

    private fun commitDone() {
        // EPUB 渲染失败的脚本字体视为不可用：保存时恢复为跟随预设，忽略本次选择。
        if (EpubScriptFontHealth.failedScopes.value.isNotEmpty()) {
            EpubScriptFontHealth.failedScopes.value.forEach { scopeName ->
                ReadValueScope.entries.firstOrNull { it.name.equals(scopeName, ignoreCase = true) }?.let { scope ->
                    ReadBookConfig.revertFailedScriptFont(scope)
                }
            }
            EpubScriptFontHealth.clear()
            refreshUi()
        }
        dismissAllowingStateLoss()
    }

    private fun discardChanges(): Boolean {
        val snapshot = sessionSnapshot ?: sessionSnapshotJson.takeIf { it.isNotBlank() }?.let {
            runCatching { Gson().fromJson(it, ReadStyleSnapshot::class.java) }.getOrNull()
        } ?: return false
        val gson = Gson()
        val configListType = object : TypeToken<List<ReadBookConfig.Config>>() {}.type
        val configList: List<ReadBookConfig.Config> = gson.fromJson(snapshot.configListJson, configListType)
        val shareConfig: ReadBookConfig.Config = gson.fromJson(snapshot.shareConfigJson, ReadBookConfig.Config::class.java)
        if (snapshot.onlyThisBook) {
            if (!ReadBookConfig.onlyThisBook) ReadBookConfig.setOnlyThisBook(true)
            snapshot.bookConfigJson?.let {
                ReadBookConfig.durConfig = gson.fromJson(it, ReadBookConfig.Config::class.java)
            }
            ReadBookConfig.restoreBookOverridesSnapshot(snapshot.bookOverridesJson)
        } else {
            if (ReadBookConfig.onlyThisBook) ReadBookConfig.resetBookCustomization()
            ReadBookConfig.configList.clear()
            ReadBookConfig.configList.addAll(configList)
            ReadBookConfig.shareConfig = shareConfig
        }
        ReadBookConfig.styleSelect = snapshot.styleSelect
        ReadBookConfig.comicStyleSelect = snapshot.comicStyleSelect
        ReadScriptTypographyStore.restoreSnapshot(snapshot.scriptTypographyJson)
        LatinOpticalScaleStore.restoreSnapshot(snapshot.latinScaleJson)
        editorBackgroundCache = null
        ReadFloatingAppearanceState.refreshFromConfig()
        return true
    }

    private fun discardAndLeave() {
        if (closing) return
        reversePresetRebinds()
        if (!discardChanges()) return
        closing = true
        notifyPresetRestored()
        dismissAllowingStateLoss()
    }

    private fun requestDismiss() {
        if (closing) {
            dismissAllowingStateLoss()
            return
        }
        if (!commitPresetNameDraft()) return
        if (computeUnsaved()) {
            showUnsavedConfirm()
        } else {
            dismissAllowingStateLoss()
        }
    }

    private fun showUnsavedConfirm() {
        if (unsavedConfirmShowing) return
        unsavedConfirmShowing = true
        showReadUnsavedConfirmDialog(
            context = requireContext(),
            title = getString(R.string.read_style_unsaved_changes),
            keepLabel = getString(R.string.read_style_keep_and_close),
            discardLabel = getString(R.string.read_style_discard_and_close),
            cancelLabel = getString(R.string.read_style_keep_editing),
            onKeep = {
                unsavedConfirmShowing = false
                closing = true
                dismissAllowingStateLoss()
            },
            onDiscard = {
                unsavedConfirmShowing = false
                discardAndLeave()
            },
            onCancelled = {
                unsavedConfirmShowing = false
                unsavedConfirmCancelled++
            },
        )
    }

    private fun refreshUi() {
        val previous = screenState
        val config = ReadBookConfig.durConfig
        val name = config.name
        val mode = if (ReadBookConfig.isNightTheme) 1 else 0
        val modeLabel = getString(
            if (mode == 1) R.string.read_style_mode_night else R.string.read_style_mode_day
        )
        val backgroundType = config.curBgType()
        val backgroundName = config.curBgStr()
        val backgroundColor = if (backgroundType == 0) {
            runCatching { backgroundName.toColorInt() }.getOrDefault(EDITOR_FALLBACK_BG_COLOR)
        } else {
            EDITOR_FALLBACK_BG_COLOR
        }
        val previewBackground = if (backgroundType == 0) {
            null
        } else {
            runCatching {
                config.curBgDrawable(352, 176).toBitmap(352, 176).asImageBitmap()
            }.getOrNull()
        }
        val rules = currentRules()
        val effectiveFloatingColor = ReadBookConfig.effectiveReadFloatingColor()
        selectedHighlightIds = selectedHighlightIds.intersect(rules.mapTo(hashSetOf()) { it.id })
        val languageBindings = ReadStyleLanguageMap.current()
        screenState = ReadStyleUiState(
            presets = (if (ReadBookConfig.onlyThisBook) listOf(-1 to config) else emptyList())
                .plus(ReadBookConfig.configList.mapIndexed { index, item -> index to item })
                .map { (index, item) ->
                ReadStylePresetUi(
                    index = index,
                    name = if (index == -1) getString(R.string.read_style_this_book)
                        else item.name,
                    textColor = item.curTextColor(),
                    background = runCatching {
                        item.curBgDrawable(176, 128).toBitmap(176, 128).asImageBitmap()
                    }.getOrNull(),
                )
            },
            selectedPresetIndex = ReadBookConfig.styleSelect,
            selectedPresetName = name,
            presetNameDraft = previous?.presetNameDraft,
            canRestoreCurrentDefault = ReadBookConfig.hasDefaultForCurrent(),
            highlightSummary = getString(
                R.string.read_highlight_summary,
                rules.count(ReadHighlightRule::enabled),
                rules.size,
            ),
            shareLayout = ReadBookConfig.shareLayout,
            isEpub = ReadBook.book?.isEpub == true,
            onlyThisBook = ReadBookConfig.onlyThisBook,
            canUseBookStyle = ReadBookConfig.canUseBookStyle,
            globalFloatingFollowApp = ReadBookConfig.readFloatingFollowAppGlobally,
            floatingFollowAppPref = ReadBookConfig.readFloatingFollowAppGlobally,
            languagePresetCjk = languageBindings.cjk,
            languagePresetLatin = languageBindings.latin,
            languagePresetOther = languageBindings.other,
            detectedScriptClass = ReadBook.book?.config?.scriptClass,
            textSize = ReadBookConfig.textSize,
            letterSpacing = ReadBookConfig.letterSpacing,
            lineSpacingExtra = ReadBookConfig.lineSpacingExtra,
            paragraphSpacing = ReadBookConfig.paragraphSpacing,
            pageAnim = ReadBook.pageAnim().coerceIn(0, 4),
            highlightRules = rules,
            highlightSelectionMode = highlightSelectionMode,
            selectedHighlightIds = selectedHighlightIds,
            editorMode = mode,
            creatingPreset = creatingPreset,
            editorModeLabel = modeLabel,
            editorPreviewBackground = previewBackground,
            editorBackgrounds = if (page.isEditorPage()) loadEditorBackgrounds() else emptyList(),
            editorBackgroundType = backgroundType,
            editorBackgroundName = backgroundName,
            editorTextColor = config.curTextColor(),
            editorBackgroundColor = backgroundColor,
            editorTextAccentColor = config.curTextAccentColor(),
            editorHighlightColor = config.curHighlightColor(),
            editorBackgroundAlpha = ReadBookConfig.bgAlpha.coerceIn(0, 100),
            editorFloatingColorSeed = ReadBookConfig.currentGlobalFloatingSeed(),
            editorFloatingColorFromBackground = !ReadBookConfig.readFloatingFollowAppGlobally,
            editorFloatingTransparency = ReadBookConfig.readFloatingGlobalTransparency,
            editorFloatingPrimaryStrength = ReadBookConfig.readFloatingGlobalPrimaryStrength,
            editorFloatingColorStyle = effectiveFloatingColor.colorStyle,
            fullLineUnderline = currentFullLineUnderlineState(),
            highlightDraft = highlightDraft,
            editingHighlightIndex = editingHighlightIndex,
            editorInitialColor = previous?.editorInitialColor,
            editorInitialColorWasUnset = previous?.editorInitialColorWasUnset ?: false,
            editorInitialTextColor = previous?.editorInitialTextColor,
            editorInitialBackgroundColor = previous?.editorInitialBackgroundColor,
            editorInitialHighlightColor = previous?.editorInitialHighlightColor,
            editorInitialTextAccentColor = previous?.editorInitialTextAccentColor,
            editorInitialBackgroundType = previous?.editorInitialBackgroundType,
            editorInitialBackgroundName = previous?.editorInitialBackgroundName,
            editorInitialBackground = previous?.editorInitialBackground,
            hasUnsavedChanges = computeUnsaved(),
            bookFont = if (ReadBookConfig.onlyThisBook) ReadBookConfig.textFont else "",
            bookFontSource = if (ReadBookConfig.onlyThisBook) {
                val resolved = ReadBookConfig.bookFontOverrideSource(config.textFont)
                when (resolved.source) {
                    ReadValueSource.THIS_BOOK -> getString(R.string.read_style_source_this_book)
                    ReadValueSource.PRESET -> if (ReadBookConfig.bookFollowsGlobal()) {
                        getString(R.string.read_style_follow_global)
                    } else {
                        getString(R.string.read_style_source_preset)
                    }
                    ReadValueSource.GLOBAL -> getString(R.string.read_style_source_global)
                    ReadValueSource.PLATFORM -> getString(R.string.read_style_source_system)
                    else -> ""
                }
            } else "",
            followsGlobal = ReadBookConfig.bookFollowsGlobal(),
            languageFonts = listOf(
                ReadValueScope.LATIN,
                ReadValueScope.CJK,
                ReadValueScope.OTHER,
            ).map { scope ->
                val globalFont = ReadScriptTypographyStore.font(scope).orEmpty()
                val failed = EpubScriptFontHealth.isFailed(scope.name.lowercase())
                val effective = ReadBookConfig.scriptFont(scope).value
                ReadScriptFontUi(
                    scope = scope,
                    label = getString(
                        when (scope) {
                            ReadValueScope.LATIN -> R.string.read_style_script_latin
                            ReadValueScope.CJK -> R.string.read_style_script_cjk
                            else -> R.string.read_style_script_other
                        }
                    ),
                    font = globalFont,
                    source = if (globalFont.isBlank()) {
                        ReadValueSource.PLATFORM
                    } else {
                        ReadValueSource.GLOBAL
                    },
                    canReset = globalFont.isNotBlank(),
                    unavailable = failed && globalFont.isNotBlank() && globalFont == effective,
                )
            },
            editorScriptFonts = listOf(
                ReadValueScope.LATIN,
                ReadValueScope.CJK,
                ReadValueScope.OTHER,
            ).map { scope ->
                val presetFont = ReadBookConfig.durConfig.scriptFonts?.forScope(scope).orEmpty()
                val failed = EpubScriptFontHealth.isFailed(scope.name.lowercase())
                val effective = ReadBookConfig.scriptFont(scope).value
                ReadScriptFontUi(
                    scope = scope,
                    label = getString(
                        when (scope) {
                            ReadValueScope.LATIN -> R.string.read_style_script_latin
                            ReadValueScope.CJK -> R.string.read_style_script_cjk
                            else -> R.string.read_style_script_other
                        }
                    ),
                    font = presetFont,
                    source = if (presetFont.isBlank()) ReadValueSource.PLATFORM else ReadValueSource.PRESET,
                    canReset = presetFont.isNotBlank(),
                    unavailable = failed && presetFont.isNotBlank() && presetFont == effective,
                )
            },
            latinScale = LatinOpticalScaleStore.manual(),
        )
    }

    private fun currentRules(): List<ReadHighlightRule> =
        ReadHighlightRuleStore.allRules().sortedBy(ReadHighlightRule::position)

    private fun changeBgTextConfig(index: Int) {
        val oldIndex = ReadBookConfig.styleSelect
        if (index !in ReadBookConfig.configList.indices) return
        if (index != oldIndex) {
            ReadBookConfig.noteUserStyleSelection(ReadBook.book?.bookUrl)
            ReadBookConfig.styleSelect = index
            ReadFloatingAppearanceState.refreshFromConfig()
            refreshUi()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            notifyFloatingAppearanceChanged()
        }
        ReadBook.book?.let(ReadStyleLanguageBinder::rememberCurrentStyle)
    }

    private fun promptNewPreset() {
        if (namingPreset) return
        namingPreset = true
        showReadPresetNameDialog(
            context = requireContext(),
            title = getString(R.string.read_style_create_title),
            label = getString(R.string.read_style_name),
            confirmLabel = getString(R.string.ok),
            cancelLabel = getString(R.string.cancel),
            onConfirm = { raw ->
                val name = PresetNames.allocate(raw, ReadBookConfig.configList.map { it.name })
                if (name == null) {
                    toastOnUi(R.string.read_style_name_required)
                    false
                } else {
                    createNamedPreset(name)
                    true
                }
            },
            onDismiss = { namingPreset = false },
        )
    }

    private fun createNamedPreset(name: String) {
        val index = ReadBookConfig.createStyle(
            ReadBookConfig.Config(
                name = name,
                readFloatingTransparency = 0,
                readFloatingPrimaryStrength = 100,
            )
        )
        if (!ReadBookConfig.onlyThisBook) ReadBookConfig.save()
        openEditor(index, isNew = true)
        if (ReadBookConfig.onlyThisBook) {
            ReadFloatingAppearanceState.refreshFromConfig()
            notifyPresetRestored()
        }
        ReadBook.book?.let(ReadStyleLanguageBinder::rememberCurrentStyle)
    }

    /**
     * 离开编辑或关闭界面时才收名字。空名字不写入：已有名字的预设保持原名，仍为空的预设继续为空。
     */
    private fun commitPresetNameDraft(): Boolean {
        val draft = screenState?.presetNameDraft ?: return true
        val oldName = ReadBookConfig.durConfig.name
        val saved = ReadBookConfig.commitPresetName(draft)
        screenState = screenState?.copy(presetNameDraft = null)
        if (saved == null) {
            toastOnUi(
                if (oldName.isBlank()) R.string.read_style_name_required
                else R.string.read_style_name_kept
            )
            refreshUi()
            return true
        }
        notePresetRebind(oldName, saved)
        refreshUi()
        return true
    }

    private fun notePresetRebind(oldName: String, newName: String) {
        if (ReadBookConfig.onlyThisBook || oldName.isBlank() || oldName == newName) return
        ReadBookConfig.rebindPresetIdentity(oldName, newName)
        rebindBooks(oldName, newName)
        appliedPresetRebinds.addLast(oldName to newName)
    }

    private fun reversePresetRebinds() {
        while (appliedPresetRebinds.isNotEmpty()) {
            val (oldName, newName) = appliedPresetRebinds.removeLast()
            ReadBookConfig.rebindPresetIdentity(newName, oldName)
            rebindBooks(newName, oldName)
        }
    }

    private fun rebindBooks(oldName: String, newName: String) {
        if (oldName.isBlank() || oldName == newName) return
        ReadBook.book?.let { book ->
            if (book.config.readStyleName == oldName) {
                book.config.readStyleName = newName
                book.save()
            }
        }
        val currentUrl = ReadBook.book?.bookUrl
        globalExecutor.execute {
            appDb.bookDao.all.forEach { book ->
                if (book.bookUrl == currentUrl) return@forEach
                if (book.readConfig?.readStyleName != oldName) return@forEach
                book.config.readStyleName = newName
                book.save()
            }
        }
    }

    private fun openEditor(index: Int, isNew: Boolean = false) {
        creatingPreset = isNew
        currentPage = ReadStylePage.EDIT
        changeBgTextConfig(index)
        page = ReadStylePage.EDIT
        ReadBookConfig.setNightThemeOverride(null)
        refreshUi()
    }

    private fun toggleEditorThemeMode() {
        val currentNight = ReadBookConfig.isNightTheme
        ReadBookConfig.setNightThemeOverride(!currentNight)
        refreshUi()
        postEditorThemePreviewChanged()
    }

    private fun clearEditorThemeOverride() {
        if (ReadBookConfig.setNightThemeOverride(null)) {
            postEditorThemePreviewChanged()
        }
    }

    private fun postEditorThemePreviewChanged() {
        postEvent(EventBus.UP_CONFIG, arrayListOf(0, 1, 2, 6, 9))
        notifyFloatingAppearanceChanged()
    }

    private fun openLanguageFonts() {
        currentPage = ReadStylePage.LANGUAGE_FONTS
        page = ReadStylePage.LANGUAGE_FONTS
        refreshUi()
    }

    private fun openNewBookPreset() {
        currentPage = ReadStylePage.NEW_BOOK_PRESET
        page = ReadStylePage.NEW_BOOK_PRESET
        refreshUi()
    }

    private fun openFloatingWindows() {
        currentPage = ReadStylePage.FLOATING_WINDOWS
        page = ReadStylePage.FLOATING_WINDOWS
        refreshUi()
    }

    private fun selectScriptFont(scope: ReadValueScope) {
        pendingEditorScriptFontScope = null
        pendingScriptFontScope = scope
        EpubScriptFontHealth.report(scope.name.lowercase(), false)
        showDialogFragment<FontSelectDialog>()
    }

    private fun selectBodyFont() {
        pendingScriptFontScope = null
        pendingEditorScriptFontScope = null
        showDialogFragment<FontSelectDialog>()
    }

    private fun resetScriptFont(scope: ReadValueScope) {
        ReadBookConfig.writeScriptFont(scope, null)
        EpubScriptFontHealth.report(scope.name.lowercase(), false)
        refreshUi()
        // 硬性要求：全局脚本字体写入后必须刷新字体表；本书覆盖路径同样刷新（幂等）。
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
    }

    private fun selectEditorScriptFont(scope: ReadValueScope) {
        pendingScriptFontScope = null
        pendingEditorScriptFontScope = scope
        EpubScriptFontHealth.report(scope.name.lowercase(), false)
        showDialogFragment<FontSelectDialog>()
    }

    private fun resetEditorScriptFont(scope: ReadValueScope) {
        ReadBookConfig.setEditorScriptFont(scope, null)
        refreshUi()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
    }

    private fun navigateTo(target: ReadStylePage) {
        currentPage = target
        if (target != ReadStylePage.HIGHLIGHT && highlightSelectionMode != HighlightSelectionMode.NONE) {
            clearHighlightSelection(refresh = false)
        }
        if (target.isColorClusterPage() || target == ReadStylePage.EDIT_AI_THEME) {
            val state = screenState ?: return
            val fromSession = page.isColorClusterPage() || page == ReadStylePage.EDIT_AI_THEME
            if (!fromSession) {
                colorSessionAppearance = ReadBookConfig.durConfig.copy()
            }
            val textSnap = if (fromSession) {
                state.editorInitialTextColor ?: state.editorTextColor
            } else {
                state.editorTextColor
            }
            val backgroundSnap = if (fromSession) {
                state.editorInitialBackgroundColor ?: state.editorBackgroundColor
            } else {
                state.editorBackgroundColor
            }
            val highlightSnap = if (fromSession) {
                state.editorInitialHighlightColor ?: state.editorHighlightColor
            } else {
                state.editorHighlightColor
            }
            val accentSnap = if (fromSession) {
                state.editorInitialTextAccentColor ?: state.editorTextAccentColor
            } else {
                state.editorTextAccentColor
            }
            val initialForPage = when (target) {
                ReadStylePage.EDIT_TEXT_COLOR -> textSnap
                ReadStylePage.EDIT_BACKGROUND_COLOR -> backgroundSnap
                ReadStylePage.EDIT_HIGHLIGHT_COLOR -> highlightSnap
                else -> accentSnap
            }
            screenState = state.copy(
                editorInitialColor = initialForPage,
                editorInitialColorWasUnset = false,
                editorInitialTextColor = textSnap,
                editorInitialBackgroundColor = backgroundSnap,
                editorInitialHighlightColor = highlightSnap,
                editorInitialTextAccentColor = accentSnap,
                editorInitialBackgroundType = if (fromSession) {
                    state.editorInitialBackgroundType
                } else {
                    state.editorBackgroundType
                },
                editorInitialBackgroundName = if (fromSession) {
                    state.editorInitialBackgroundName
                } else {
                    state.editorBackgroundName
                },
                editorInitialBackground = if (fromSession) {
                    state.editorInitialBackground
                } else {
                    state.editorPreviewBackground
                },
            )
            currentPage = target
            page = target
            return
        }
        if (target.isAnyColorEditorPage()) {
            val state = screenState ?: return
            val initialNullableColor = when (target) {
                ReadStylePage.EDIT_TEXT_COLOR -> state.editorTextColor
                ReadStylePage.EDIT_BACKGROUND_COLOR -> state.editorBackgroundColor
                ReadStylePage.EDIT_ACCENT_COLOR -> state.editorTextAccentColor
                ReadStylePage.EDIT_UNDERLINE_COLOR -> state.fullLineUnderline.color
                ReadStylePage.HIGHLIGHT_TEXT_COLOR -> state.highlightDraft?.textColor
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR -> state.highlightDraft?.bgColor
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> state.highlightDraft?.underlineColor
                else -> null
            }
            val fallbackColor = when (target) {
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR ->
                    state.highlightDraft?.bgColor
                        ?: ((state.editorTextAccentColor and 0x00FFFFFF) or 0x33000000)
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR ->
                    state.highlightDraft?.underlineColor
                        ?: state.highlightDraft?.textColor
                        ?: state.editorTextAccentColor
                ReadStylePage.HIGHLIGHT_TEXT_COLOR ->
                    state.highlightDraft?.textColor ?: state.editorTextAccentColor
                else -> state.editorTextAccentColor
            }
            screenState = state.copy(
                editorInitialColor = initialNullableColor ?: fallbackColor,
                editorInitialColorWasUnset = initialNullableColor == null,
                editorInitialBackgroundType = if (target == ReadStylePage.EDIT_BACKGROUND_COLOR) {
                    state.editorBackgroundType
                } else {
                    null
                },
                editorInitialBackgroundName = if (target == ReadStylePage.EDIT_BACKGROUND_COLOR) {
                    state.editorBackgroundName
                } else {
                    null
                },
                editorInitialBackground = if (target == ReadStylePage.EDIT_BACKGROUND_COLOR) {
                    state.editorPreviewBackground
                } else {
                    null
                },
            )
            page = target
            return
        }
        page = target
        refreshUi()
    }

    private fun navigateBack() {
        if (highlightSelectionMode != HighlightSelectionMode.NONE) {
            clearHighlightSelection()
            return
        }
        when {
            page == ReadStylePage.HIGHLIGHT_NINE_SLICE -> {
                page = ReadStylePage.HIGHLIGHT_EDIT
                refreshUi()
            }
            page.isPresetColorEditorPage() -> {
                page = ReadStylePage.EDIT
                clearEditorColorInitialState()
            }

            page == ReadStylePage.EDIT_AI_THEME -> page = ReadStylePage.EDIT

            page == ReadStylePage.EDIT_UNDERLINE_COLOR -> {
                page = ReadStylePage.EDIT_UNDERLINE
                clearEditorColorInitialState()
            }

            page.isHighlightColorEditorPage() -> {
                page = ReadStylePage.HIGHLIGHT_EDIT
                clearEditorColorInitialState()
            }

            page == ReadStylePage.EDIT_UNDERLINE -> page = ReadStylePage.EDIT

            page == ReadStylePage.HIGHLIGHT_EDIT -> {
                clearHighlightDraft()
                page = ReadStylePage.HIGHLIGHT
                refreshUi()
            }

            page == ReadStylePage.EDIT -> {
                if (!commitPresetNameDraft()) return
                page = ReadStylePage.PRESET
                clearEditorThemeOverride()
                refreshUi()
            }

            page == ReadStylePage.LANGUAGE_FONTS ||
                page == ReadStylePage.NEW_BOOK_PRESET ||
                page == ReadStylePage.FLOATING_WINDOWS -> {
                page = ReadStylePage.APP_DEFAULTS
                refreshUi()
            }
        }
        currentPage = page
    }

    private fun applyEditorTextColor(color: Int, notifyReader: Boolean = true) {
        ReadBookConfig.durConfig.readPaletteId = null
        ReadBookConfig.durConfig.setCurTextColor(color)
        updateEditorState { copy(editorTextColor = color) }
        if (notifyReader) postEditorTextColorChanged()
    }

    private fun applyEditorAccentColor(color: Int, notifyReader: Boolean = true) {
        ReadBookConfig.durConfig.readPaletteId = null
        ReadBookConfig.durConfig.setCurTextAccentColor(color)
        updateEditorState { copy(editorTextAccentColor = color) }
        if (notifyReader) postEditorTextColorChanged()
    }

    private fun applyEditorHighlightColor(color: Int, notifyReader: Boolean = true) {
        ReadBookConfig.durConfig.readPaletteId = null
        ReadBookConfig.durConfig.setCurHighlightColor(color)
        updateEditorState { copy(editorHighlightColor = color) }
        if (notifyReader) postEditorTextColorChanged()
    }

    private fun applyPaperLook(look: io.legado.app.ui.design.theme.NgPaperLook) {
        val seed = look.seed
        if (seed != null) {
            applyPaletteSeed(seed)
            return
        }
        applyEditorBackgroundColor(look.background, clearPalette = true)
        applyEditorTextColorKeepingPalette(look.foreground)
        applyEditorAccentKeepingPalette(look.accent)
        applyEditorHighlightKeepingPalette(look.highlight)
        ReadBookConfig.durConfig.readPaletteId = null
    }

    private fun applyPaletteSeed(seed: ReadingPaletteSeed) {
        val paletteId = ReadingPaletteCatalog.baseId(seed.id) ?: seed.id
        val day = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.LIGHT)
        val night = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.DARK)
        val eink = SemanticPaletteEngine.deriveTokens(seed, ReadingDisplayMode.EINK)
        ReadBookConfig.durConfig.writePaletteAppearance(
            paletteId = paletteId,
            dayBackground = day.background,
            dayText = day.text,
            dayAccent = day.accent,
            dayHighlight = day.highlightBg,
            nightBackground = night.background,
            nightText = night.text,
            nightAccent = night.accent,
            nightHighlight = night.highlightBg,
            einkBackground = eink.background,
            einkText = eink.text,
            einkAccent = eink.accent,
            einkHighlight = eink.highlightBg,
        )
        ReadingPaletteCatalog.enable(paletteId)
        refreshUi()
        postEditorBackgroundChanged()
        postEditorTextColorChanged()
    }

    private fun applyEditorTextColorKeepingPalette(color: Int) {
        ReadBookConfig.durConfig.setCurTextColor(color)
        updateEditorState { copy(editorTextColor = color) }
        postEditorTextColorChanged()
    }

    private fun applyEditorAccentKeepingPalette(color: Int) {
        ReadBookConfig.durConfig.setCurTextAccentColor(color)
        updateEditorState { copy(editorTextAccentColor = color) }
    }

    private fun applyEditorHighlightKeepingPalette(color: Int) {
        ReadBookConfig.durConfig.setCurHighlightColor(color)
        updateEditorState { copy(editorHighlightColor = color) }
    }

    private fun applyEditorBackgroundColor(
        color: Int,
        clearPalette: Boolean = true,
        notifyReader: Boolean = true,
    ) {
        if (clearPalette) ReadBookConfig.durConfig.readPaletteId = null
        ReadBookConfig.durConfig.setCurBg(0, "#${color.hexString}")
        updateEditorState {
            copy(
                editorPreviewBackground = null,
                editorBackgroundType = 0,
                editorBackgroundName = "#${color.hexString}",
                editorBackgroundColor = color,
            )
        }
        if (notifyReader) postEditorBackgroundChanged()
    }

    private fun restoreEditorAppearanceFromInitials(state: ReadStyleUiState) {
        val initialType = state.editorInitialBackgroundType ?: 0
        val initialName = state.editorInitialBackgroundName.orEmpty()
        val initialBg = state.editorInitialBackgroundColor
        if (initialType == 0 && initialBg != null) {
            applyEditorBackgroundColor(initialBg)
        } else if (initialType != 0) {
            ReadBookConfig.durConfig.setCurBg(initialType, initialName)
            updateEditorState {
                copy(
                    editorPreviewBackground = state.editorInitialBackground,
                    editorBackgroundType = initialType,
                    editorBackgroundName = initialName,
                )
            }
            postEditorBackgroundChanged()
        }
        state.editorInitialTextColor?.let(::applyEditorTextColor)
        state.editorInitialTextAccentColor?.let(::applyEditorAccentColor)
        state.editorInitialHighlightColor?.let(::applyEditorHighlightColor)
    }

    private fun resetEditorColor() {
        val state = screenState ?: return
        if (page == ReadStylePage.EDIT_AI_THEME) {
            val backup = colorSessionAppearance
            if (backup != null) {
                ReadBookConfig.durConfig.restoreAppearanceFrom(backup)
            } else {
                restoreEditorAppearanceFromInitials(state)
            }
            refreshUi()
            postEditorBackgroundChanged()
            postEditorTextColorChanged()
            return
        }
        val initialColor = state.editorInitialColor ?: return
        when (page) {
            ReadStylePage.EDIT_TEXT_COLOR -> applyEditorTextColor(initialColor)
            ReadStylePage.EDIT_BACKGROUND_COLOR -> {
                val initialType = state.editorInitialBackgroundType ?: 0
                val initialName = state.editorInitialBackgroundName.orEmpty()
                if (initialType == 0) {
                    applyEditorBackgroundColor(initialColor)
                } else {
                    ReadBookConfig.durConfig.setCurBg(initialType, initialName)
                    updateEditorState {
                        copy(
                            editorPreviewBackground = state.editorInitialBackground,
                            editorBackgroundType = initialType,
                            editorBackgroundName = initialName,
                        )
                    }
                    postEditorBackgroundChanged()
                }
            }
            ReadStylePage.EDIT_ACCENT_COLOR -> applyEditorAccentColor(initialColor)
            ReadStylePage.EDIT_HIGHLIGHT_COLOR -> applyEditorHighlightColor(initialColor)
            ReadStylePage.EDIT_UNDERLINE_COLOR -> {
                ReadBookConfig.config.setCurUnderlineColor(initialColor)
                refreshFullLineUnderlineState()
            }
            ReadStylePage.HIGHLIGHT_TEXT_COLOR -> updateHighlightDraft {
                copy(textColor = if (state.editorInitialColorWasUnset) null else initialColor)
            }
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR -> updateHighlightDraft {
                copy(bgColor = if (state.editorInitialColorWasUnset) null else initialColor)
            }
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> updateHighlightDraft {
                copy(underlineColor = if (state.editorInitialColorWasUnset) null else initialColor)
            }
            else -> return
        }
        page = when (page) {
            ReadStylePage.EDIT_UNDERLINE_COLOR -> ReadStylePage.EDIT_UNDERLINE
            ReadStylePage.HIGHLIGHT_TEXT_COLOR,
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> ReadStylePage.HIGHLIGHT_EDIT
            else -> ReadStylePage.EDIT
        }
        clearEditorColorInitialState()
    }

    private fun clearEditorColorInitialState() {
        updateEditorState {
            copy(
                editorInitialColor = null,
                editorInitialColorWasUnset = false,
                editorInitialTextColor = null,
                editorInitialBackgroundColor = null,
                editorInitialHighlightColor = null,
                editorInitialTextAccentColor = null,
                editorInitialBackgroundType = null,
                editorInitialBackgroundName = null,
                editorInitialBackground = null,
            )
        }
    }

    private fun postEditorTextColorChanged() {
        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun postEditorBackgroundChanged() {
        postEvent(EventBus.UP_CONFIG, arrayListOf(1))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun currentFullLineUnderlineState() = FullLineUnderlineUiState(
        enabled = ReadBookConfig.fullLineUnderlineEnabled,
        dashed = ReadBookConfig.dottedLine,
        color = ReadBookConfig.resolvedUnderlineColor,
        width = ReadBookConfig.underlineHeight.coerceIn(1, 20),
        offset = ReadBookConfig.underlinePadding.coerceIn(0, 20),
        extend = ReadBookConfig.underlineExtend,
        dashLength = ReadBookConfig.dottedBase.coerceIn(1f, 20f),
        gapLength = ReadBookConfig.dottedRatio.coerceIn(1f, 20f),
    )

    private fun showTipConfigCentered() {
        if (openTipConfigAfterDismiss) return
        openTipConfigAfterDismiss = true
        // 有意为之：这是抽屉内部的页面跳转（关闭阅读样式后立即打开 TipConfig），
        // 直接 dismiss 即落盘当前编辑，不弹未保存确认。
        dismiss()
    }

    private fun refreshFullLineUnderlineState() {
        updateEditorState { copy(fullLineUnderline = currentFullLineUnderlineState()) }
        postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
    }

    private fun selectBackground(type: Int, name: String) {
        val selected = loadEditorBackgrounds().firstOrNull {
            it.type == type && it.name == name
        } ?: return
        ReadBookConfig.durConfig.setCurBg(type, name)
        updateEditorState {
            copy(
                editorPreviewBackground = selected.background,
                editorBackgroundType = type,
                editorBackgroundName = name,
            )
        }
        postEditorBackgroundChanged()
    }

    private fun setFloatingColorSource(fromBackground: Boolean) {
        ReadBookConfig.readFloatingFollowAppGlobally = !fromBackground
        refreshUi()
        notifyFloatingAppearanceChanged()
    }

    private fun pickFloatingColor() {
        val config = ReadBookConfig.durConfig
        if (config.curBgType() == 0) {
            runCatching { config.curBgStr().toColorInt() }
                .onSuccess(::applyFloatingColor)
                .onFailure {
                    it.printOnDebug()
                    toastOnUi(R.string.read_style_floating_color_error)
                }
            return
        }
        val decorView = activity?.window?.decorView ?: return
        val width = decorView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = decorView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val background = runCatching {
            renderCurrentReadBackground(width, height)
        }.onFailure {
            it.printOnDebug()
            toastOnUi(it.localizedMessage ?: getString(R.string.read_style_floating_color_error))
        }.getOrNull() ?: return
        backgroundColorPickerDialog?.dismiss()
        backgroundColorPickerDialog = showReadBackgroundColorPicker(
            context = requireContext(),
            background = background,
            onPicked = { result -> applyFloatingColor(result.color) },
        )
    }

    private fun applyFloatingColor(color: Int) {
        ReadBookConfig.readFloatingFollowAppGlobally = false
        ReadBookConfig.setGlobalFloatingSeed(color)
        refreshUi()
        notifyFloatingAppearanceChanged()
    }

    private fun notifyFloatingAppearanceChanged() {
        ReadFloatingAppearanceState.refreshFromConfig()
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        callBack?.let(ReadAloudMiniPlayer::refreshAppearance)
    }

    private fun loadEditorBackgrounds(): List<ReadStyleBackgroundUi> {
        editorBackgroundCache?.let { return it }
        val customBackgrounds = linkedMapOf<String, String>()
        fun addCustomBackground(reference: String) {
            val path = resolveCustomBackgroundPath(reference)
            val file = File(path)
            if (!file.isFile) return
            val key = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            customBackgrounds.putIfAbsent(key, reference)
        }
        ReadBookConfig.durConfig.takeIf { it.curBgType() == 2 }
            ?.curBgStr()
            ?.let(::addCustomBackground)
        ReadBookConfig.getAllPicBgStr().forEach(::addCustomBackground)

        val customItems = customBackgrounds.values.mapNotNull { reference ->
            runCatching {
                val path = resolveCustomBackgroundPath(reference)
                BitmapUtils.decodeBitmap(path, 128, 112)
                    ?.asImageBitmap()
                    ?.let { bitmap ->
                        ReadStyleBackgroundUi(
                            type = 2,
                            name = reference,
                            label = FileUtils.getNameExcludeExtension(File(path).name),
                            background = bitmap,
                        )
                    }
            }.getOrNull()
        }
        val names = requireContext().assets.list("bg")?.toList().orEmpty()
        val preferred = listOf("午后沙滩.jpg", "宁静夜色.jpg", "山水墨影.jpg", "山水画.jpg")
        val ordered = preferred.filter(names::contains) + names.filterNot(preferred::contains)
        val builtInItems = ordered.mapNotNull { name ->
            runCatching {
                BitmapUtils.decodeAssetsBitmap(requireContext(), "bg/$name", 128, 112)
                    ?.asImageBitmap()
                    ?.let { bitmap ->
                        ReadStyleBackgroundUi(
                            type = 1,
                            name = name,
                            label = FileUtils.getNameExcludeExtension(name),
                            background = bitmap,
                        )
                    }
            }.getOrNull()
        }
        return (customItems + builtInItems).also { editorBackgroundCache = it }
    }

    private fun resolveCustomBackgroundPath(reference: String): String =
        if (reference.contains(File.separator)) {
            reference
        } else {
            FileUtils.getPath(requireContext().externalFiles, "bg", reference)
        }

    private fun setBackgroundFromUri(uri: Uri) {
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    ".${fileDoc.name.substringAfterLast('.', "jpg")}"
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                val file = FileUtils.createFileIfNotExist(
                    requireContext().externalFiles,
                    "bg",
                    fileName,
                )
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
            }.onSuccess {
                editorBackgroundCache = null
                postEditorBackgroundChanged()
                composeView.post { refreshUi() }
            }.onFailure {
                toastOnUi(it.localizedMessage.orEmpty())
            }
        }
    }

    private fun showFontWeightSetting() {
        val weights = (100..900 step 100).toList()
        val descriptions = resources.getStringArray(R.array.text_font_weight_levels).toList()
        ReadTypographySettingDialog.showDiscrete(
            context = requireContext(),
            avoidView = composeView,
            title = getString(R.string.text_font_weight_converter),
            stepLabels = weights.map(Int::toString),
            currentValues = descriptions,
            selectedIndex = weights.indexOf(resolveFontWeight(ReadBookConfig.textBold))
                .coerceAtLeast(0),
            previewTypeface = { index ->
                val weight = weights[index]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(Typeface.DEFAULT, weight, false)
                } else {
                    Typeface.create(
                        Typeface.DEFAULT,
                        if (weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                    )
                }
            },
        ) { index ->
            ReadBookConfig.textBold = weights[index]
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
        }
    }

    private fun showParagraphIndentSetting() {
        ReadTypographySettingDialog.showDiscrete(
            context = requireContext(),
            avoidView = composeView,
            title = getString(R.string.paragraph_first_line_indent),
            stepLabels = resources.getStringArray(R.array.indent_short).toList(),
            currentValues = resources.getStringArray(R.array.indent).toList(),
            selectedIndex = paragraphIndentLevel(),
            currentValueTextSizeSp = 24f,
        ) { index ->
            ReadBookConfig.paragraphIndent = "　".repeat(index)
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun showChineseConverterSetting() {
        ReadTypographySettingDialog.showChineseConverter(
            context = requireContext(),
            avoidView = composeView,
            title = getString(R.string.chinese_converter),
            labels = resources.getStringArray(R.array.chinese_mode_short).toList(),
            selectedIndex = AppConfig.chineseConverterType,
        ) { index ->
            AppConfig.chineseConverterType = index
            ChineseUtils.unLoad(*TransType.entries.toTypedArray())
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
    }

    private fun resolveFontWeight(type: Int): Int = when (type) {
        0 -> 400
        1 -> 900
        2 -> 300
        else -> ((type.coerceIn(100, 900) + 50) / 100) * 100
    }

    private fun paragraphIndentLevel(): Int {
        val indent = ReadBookConfig.paragraphIndent
        val ideographicSpaces = indent.count { it == '　' }
        return (if (ideographicSpaces > 0) ideographicSpaces else indent.length)
            .coerceIn(0, 4)
    }

    private fun deleteCurrentStyle() {
        if (ReadBookConfig.onlyThisBook) {
            ReadBookConfig.setOnlyThisBook(false)
            editorBackgroundCache = null
            ReadFloatingAppearanceState.refreshFromConfig()
            refreshUi()
            notifyPresetRestored()
            return
        }
        val name = ReadBookConfig.durConfig.name
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete),
            message = getString(R.string.sure_del_any, name),
            confirmLabel = getString(R.string.ok),
            cancelLabel = getString(R.string.cancel),
            onConfirm = {
                if (ReadBookConfig.deleteDur()) {
                    editorBackgroundCache = null
                    ReadBookConfig.noteUserStyleSelection(ReadBook.book?.bookUrl)
                    ReadBook.book?.let(ReadStyleLanguageBinder::rememberCurrentStyle)
                    refreshUi()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                    notifyFloatingAppearanceChanged()
                } else {
                    toastOnUi(R.string.read_style_keep_one_preset)
                }
            },
        )
    }

    private fun confirmRestoreCurrentPreset() {
        if (!ReadBookConfig.hasDefaultForCurrent()) {
            toastOnUi(R.string.read_style_restore_unavailable)
            return
        }
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.read_style_restore_current),
            message = getString(R.string.read_style_restore_current_confirm),
            confirmLabel = getString(R.string.yes),
            cancelLabel = getString(R.string.no),
            onConfirm = {
                if (ReadBookConfig.restoreCurrentDefault()) {
                    captureSessionSnapshot()
                    editorBackgroundCache = null
                    refreshUi()
                    notifyPresetRestored()
                    toastOnUi(R.string.read_style_restore_current_done)
                }
            },
        )
    }

    private fun confirmRestoreAllPresets() {
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.read_style_restore_all),
            message = getString(R.string.read_style_restore_all_confirm),
            confirmLabel = getString(R.string.yes),
            cancelLabel = getString(R.string.no),
            onConfirm = {
                if (ReadBookConfig.restoreAllDefaults()) {
                    captureSessionSnapshot()
                    editorBackgroundCache = null
                    clearHighlightDraft()
                    page = ReadStylePage.PRESET
                    refreshUi()
                    notifyPresetRestored()
                    toastOnUi(R.string.read_style_restore_all_done)
                }
            },
        )
    }

    private fun notifyPresetRestored() {
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        notifyFloatingAppearanceChanged()
    }

    private fun applyHighlightRules(rules: List<ReadHighlightRule>) {
        ReadHighlightRuleStore.replace(
            rules.mapIndexed { index, rule -> rule.copy(position = index) }
        )
        refreshUi()
        postEvent(EventBus.UP_CONFIG, arrayListOf(13))
    }

    private fun beginHighlightSelection(mode: HighlightSelectionMode) {
        val rules = currentRules()
        if (rules.isEmpty()) {
            toastOnUi(R.string.empty)
            return
        }
        highlightSelectionMode = mode
        selectedHighlightIds = if (mode == HighlightSelectionMode.EXPORT) {
            rules.mapTo(linkedSetOf()) { it.id }
        } else {
            emptySet()
        }
        refreshUi()
    }

    private fun toggleHighlightSelection(id: String) {
        if (highlightSelectionMode == HighlightSelectionMode.NONE) return
        selectedHighlightIds = selectedHighlightIds.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        refreshUi()
    }

    private fun toggleAllHighlightSelection() {
        if (highlightSelectionMode == HighlightSelectionMode.NONE) return
        val allIds = currentRules().mapTo(linkedSetOf()) { it.id }
        selectedHighlightIds = if (allIds.isNotEmpty() && selectedHighlightIds.containsAll(allIds)) {
            emptySet()
        } else {
            allIds
        }
        refreshUi()
    }

    private fun clearHighlightSelection(refresh: Boolean = true) {
        highlightSelectionMode = HighlightSelectionMode.NONE
        selectedHighlightIds = emptySet()
        if (refresh) refreshUi()
    }

    private fun confirmHighlightSelection() {
        val selectedRules = currentRules().filter { it.id in selectedHighlightIds }
        if (selectedRules.isEmpty()) return
        when (highlightSelectionMode) {
            HighlightSelectionMode.EXPORT -> {
                clearHighlightSelection(refresh = false)
                refreshUi()
                prepareHighlightExport(selectedRules)
            }

            HighlightSelectionMode.DELETE -> {
                val selectedIds = selectedRules.mapTo(hashSetOf()) { it.id }
                showReadConfirmDialog(
                    context = requireContext(),
                    title = getString(R.string.delete),
                    message = getString(
                        R.string.read_highlight_delete_selected_confirm,
                        selectedIds.size,
                    ),
                    confirmLabel = getString(R.string.delete),
                    cancelLabel = getString(R.string.cancel),
                    onConfirm = {
                        val remaining = currentRules().filterNot { it.id in selectedIds }
                        clearHighlightSelection(refresh = false)
                        applyHighlightRules(remaining)
                    },
                )
            }

            HighlightSelectionMode.NONE -> Unit
        }
    }

    private fun openHighlightEditor(position: Int? = null) {
        val oldRule = position?.let(currentRules()::getOrNull)
        editingHighlightIndex = position
        highlightDraft = oldRule ?: ReadHighlightRule(
            id = UUID.randomUUID().toString(),
            name = getString(R.string.highlight_rule_default_name),
            sampleText = getString(R.string.highlight_rule_default_sample),
            position = currentRules().size,
            textColor = ReadBookConfig.textAccentColor,
        )
        page = ReadStylePage.HIGHLIGHT_EDIT
        refreshUi()
    }

    private fun copyHighlightRule(id: String) {
        val rules = currentRules()
        val source = rules.firstOrNull { it.id == id } ?: return
        editingHighlightIndex = null
        highlightDraft = source.copy(
            id = UUID.randomUUID().toString(),
            name = getString(
                R.string.highlight_rule_copy_name,
                source.name.ifBlank { getString(R.string.highlight_rule_default_name) },
            ),
            position = rules.size,
        )
        page = ReadStylePage.HIGHLIGHT_EDIT
        refreshUi()
    }

    private fun updateHighlightDraft(transform: ReadHighlightRule.() -> ReadHighlightRule) {
        val updated = highlightDraft?.transform() ?: return
        highlightDraft = updated
        updateEditorState { copy(highlightDraft = updated) }
    }

    private fun clearHighlightDraft() {
        highlightDraft = null
        editingHighlightIndex = null
    }

    private fun saveHighlightDraft() {
        val draft = highlightDraft ?: return
        val pattern = draft.pattern
        val patternValid = pattern.isNotBlank() && runCatching { Regex(pattern) }.isSuccess
        val saved = draft.copy(
            name = draft.name.trim().ifBlank {
                getString(R.string.highlight_rule_default_name)
            },
            enabled = draft.enabled && patternValid,
        ).normalized()
        val updated = currentRules().toMutableList()
        val position = editingHighlightIndex
        if (position == null) {
            updated.add(saved)
        } else if (position in updated.indices) {
            updated[position] = saved
        }
        clearHighlightDraft()
        page = ReadStylePage.HIGHLIGHT
        applyHighlightRules(updated)
        if (!patternValid) toastOnUi(R.string.highlight_rule_invalid_pattern)
    }

    private fun deleteHighlightDraft() {
        val draft = highlightDraft ?: return
        if (editingHighlightIndex == null) return
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete),
            message = getString(R.string.sure_del_any, draft.name),
            confirmLabel = getString(R.string.delete),
            cancelLabel = getString(R.string.cancel),
            onConfirm = {
                val updated = currentRules().filterNot { it.id == draft.id }
                clearHighlightDraft()
                page = ReadStylePage.HIGHLIGHT
                applyHighlightRules(updated)
            },
        )
    }

    private fun confirmRestoreBuiltInHighlights() {
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.read_highlight_restore_built_in),
            message = getString(R.string.read_highlight_restore_built_in_confirm),
            confirmLabel = getString(R.string.menu_restore),
            cancelLabel = getString(R.string.cancel),
            onConfirm = {
                val result = ReadHighlightRuleStore.restoreBuiltIn(DefaultData.readHighlightRules)
                refreshUi()
                if (result.addedCount > 0 || result.updatedCount > 0) {
                    postEvent(EventBus.UP_CONFIG, arrayListOf(13))
                    toastOnUi(
                        getString(
                            R.string.read_highlight_restore_built_in_done,
                            result.addedCount,
                            result.updatedCount,
                        )
                    )
                } else {
                    toastOnUi(R.string.read_highlight_restore_built_in_unchanged)
                }
            },
        )
    }

    private fun installHighlightResource(uri: Uri, kind: String) {
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                val extension = fileDoc.name.substringAfterLast('.', "bin")
                val target = FileUtils.createFileIfNotExist(
                    requireContext().externalFiles,
                    "read_style_editor",
                    "${kind}_${UUID.randomUUID()}.$extension",
                )
                FileOutputStream(target).use { output -> inputStream.copyTo(output) }
                target.absolutePath
            }.onSuccess { path ->
                if (kind == "background") {
                    updateHighlightDraft { copy(bgImage = path) }
                } else {
                    updateHighlightDraft { copy(fontPath = path) }
                }
            }.onFailure {
                toastOnUi(it.localizedMessage.orEmpty())
            }
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            ReadBookConfig.importWithReport(uri.readBytes(requireContext()))
        }.onSuccess { result ->
            val appendResult = ReadBookConfig.appendImportedConfigWithReport(result.config)
            if (!ReadBookConfig.onlyThisBook) result.readerSettings?.let(ReadPresetPreferences::apply)
            ReadBookConfig.noteUserStyleSelection(ReadBook.book?.bookUrl)
            ReadBookConfig.styleSelect = appendResult.index
            ReadBook.book?.let(ReadStyleLanguageBinder::rememberCurrentStyle)
            captureSessionSnapshot()
            editorBackgroundCache = null
            refreshUi()
            postEvent(
                EventBus.UP_CONFIG,
                if (appendResult.highlightRuleMerge == null) {
                    arrayListOf(0, 1, 2, 5)
                } else {
                    arrayListOf(0, 1, 2, 5, 8)
                },
            )
            notifyFloatingAppearanceChanged()
            if (result.readerSettings != null) {
                postEvent(io.legado.app.constant.PreferKey.textSelectAble, AppConfig.textSelectAble)
                callBack?.setOrientation()
            }
            val messages = result.warnings.toMutableList().apply {
                appendResult.highlightRuleMerge?.let {
                    add("高亮规则新增 ${it.addedCount} 条，更新 ${it.updatedCount} 条，跳过 ${it.skippedCount} 条")
                }
            }
            if (messages.isEmpty()) {
                toastOnUi("导入成功")
            } else {
                longToast("导入成功\n${messages.joinToString("\n")}")
            }
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun preparePresetExport() {
        if (preparingPresetExport || pendingPresetExportName != null) {
            toastOnUi("已有阅读预设导出任务，请稍候")
            return
        }
        preparingPresetExport = true
        val exportFileName = currentExportFileName()
        val snapshot = ReadBookConfig.getExportConfig()
        val settings = ReadPresetPreferences.capture()
        val packageFile = File(requireContext().filesDir, ".pending-read-style-${UUID.randomUUID()}.zip")
        execute {
            try {
                packageFile.outputStream().use { output ->
                    ReadStylePackageManager.export(snapshot, output, settings)
                }
            } catch (error: Throwable) {
                packageFile.delete()
                throw error
            }
        }.onSuccess {
            preparingPresetExport = false
            if (!isAdded) {
                packageFile.delete()
                return@onSuccess
            }
            pendingPresetExportName = packageFile.name
            selectExportDocument.launch(exportFileName)
        }.onError {
            preparingPresetExport = false
            it.printOnDebug()
            AppLog.put("导出失败:${it.localizedMessage}", it)
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    private fun importHighlightRules(uri: Uri) {
        execute {
            ReadHighlightRulePackageManager.import(uri.readBytes(requireContext()))
        }.onSuccess { result ->
            val merge = ReadHighlightRuleStore.merge(
                importedRules = result.rules,
                replaceMatchingIds = true,
            )
            refreshUi()
            postEvent(EventBus.UP_CONFIG, arrayListOf(13))
            val messages = buildList {
                add("新增 ${merge.addedCount} 条，更新 ${merge.updatedCount} 条，跳过 ${merge.skippedCount} 条")
                addAll(result.warnings)
            }
            longToast("高亮规则导入成功\n${messages.joinToString("\n")}")
        }.onError {
            it.printOnDebug()
            longToast("高亮规则导入失败:${it.localizedMessage}")
        }
    }

    private fun prepareHighlightExport(rules: List<ReadHighlightRule>) {
        if (preparingHighlightExport || pendingHighlightExportName != null) {
            toastOnUi("已有高亮规则导出任务，请稍候")
            return
        }
        preparingHighlightExport = true
        val packageFile = File(requireContext().filesDir, ".pending-highlight-export-${UUID.randomUUID()}.zip")
        execute {
            try {
                packageFile.outputStream().use { output ->
                    ReadHighlightRulePackageManager.export(rules, output)
                }
            } catch (error: Throwable) {
                packageFile.delete()
                throw error
            }
        }.onSuccess {
            preparingHighlightExport = false
            if (!isAdded) {
                packageFile.delete()
                return@onSuccess
            }
            pendingHighlightExportName = packageFile.name
            selectHighlightExportDocument.launch("highlightRules.zip")
        }.onError {
            preparingHighlightExport = false
            it.printOnDebug()
            longToast("高亮规则导出失败:${it.localizedMessage}")
        }
    }

    private fun exportPreparedPackage(uri: Uri, packageName: String?, label: String) {
        val filesDir = requireContext().filesDir
        execute {
            require(!packageName.isNullOrBlank()) { "待导出的规则包已丢失，请重新选择规则导出" }
            val packageFile = File(filesDir, packageName)
            require(packageFile.isFile && packageFile.length() > 0) { "待导出的规则包已丢失，请重新导出" }
            try {
                uri.outputStream(requireContext()).getOrThrow().use { output ->
                    packageFile.inputStream().use { it.copyTo(output) }
                }
            } finally {
                packageFile.delete()
            }
        }.onSuccess {
            toastOnUi("${label}导出成功")
        }.onError {
            it.printOnDebug()
            longToast("${label}导出失败:${it.localizedMessage}")
        }
    }

    private fun currentExportFileName(): String {
        val presetName = ReadBookConfig.durConfig.name.normalizeFileName()
        return if (presetName.isBlank()) configFileName else "$presetName.zip"
    }

    override val curFontPath: String
        get() = pendingEditorScriptFontScope?.let { ReadBookConfig.scriptFont(it).value }
            ?: pendingScriptFontScope?.let { ReadBookConfig.scriptFont(it).value }
            ?: ReadBookConfig.textFont

    override val fontTitle: String
        get() = when (val scope = pendingEditorScriptFontScope ?: pendingScriptFontScope) {
            null -> getString(R.string.body_font)
            ReadValueScope.LATIN -> getString(R.string.read_style_script_latin)
            ReadValueScope.CJK -> getString(R.string.read_style_script_cjk)
            else -> getString(R.string.read_style_script_other)
        }

    override val isBodyFontDialog: Boolean
        get() = pendingEditorScriptFontScope == null && pendingScriptFontScope == null

    override fun selectFont(path: String) {
        val editorScope = pendingEditorScriptFontScope
        if (editorScope != null) {
            pendingEditorScriptFontScope = null
            ReadBookConfig.setEditorScriptFont(editorScope, path)
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            refreshUi()
            return
        }
        val scope = pendingScriptFontScope
        if (scope != null) {
            pendingScriptFontScope = null
            ReadBookConfig.writeScriptFont(scope, path)
            // 硬性要求：全局脚本字体写入后必须刷新字体表（UP_CONFIG → ChapterProvider.upStyle）。
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            refreshUi()
            return
        }
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        }
        refreshUi()
    }
}

private fun ReadStylePage.isColorClusterPage(): Boolean = when (this) {
    ReadStylePage.EDIT_TEXT_COLOR,
    ReadStylePage.EDIT_BACKGROUND_COLOR,
    ReadStylePage.EDIT_HIGHLIGHT_COLOR -> true

    else -> false
}

private fun ReadStylePage.isPresetColorEditorPage(): Boolean = when (this) {
    ReadStylePage.EDIT_TEXT_COLOR,
    ReadStylePage.EDIT_BACKGROUND_COLOR,
    ReadStylePage.EDIT_HIGHLIGHT_COLOR,
    ReadStylePage.EDIT_ACCENT_COLOR -> true

    else -> false
}

private fun ReadStylePage.isHighlightColorEditorPage(): Boolean = when (this) {
    ReadStylePage.HIGHLIGHT_TEXT_COLOR,
    ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
    ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> true

    else -> false
}

private fun ReadStylePage.isAnyColorEditorPage(): Boolean =
    isPresetColorEditorPage() || isHighlightColorEditorPage() ||
        this == ReadStylePage.EDIT_UNDERLINE_COLOR

private fun ReadStylePage.isEditorPage(): Boolean =
    this == ReadStylePage.EDIT ||
        this == ReadStylePage.EDIT_AI_THEME ||
        isPresetColorEditorPage() ||
        this == ReadStylePage.EDIT_UNDERLINE ||
        this == ReadStylePage.EDIT_UNDERLINE_COLOR
