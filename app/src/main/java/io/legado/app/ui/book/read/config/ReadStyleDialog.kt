package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.DefaultData
import io.legado.app.help.background.ReadingBackground
import io.legado.app.help.background.ReadingBackgroundLibrary
import io.legado.app.help.background.selectableBuiltInBackgroundNames
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadStylePackageManager
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadHighlightRulePackageManager
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadCatalogStyle
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.LocalNgDrawerContentCardAlpha
import io.legado.app.ui.design.components.compose.NgDrawerThemeProvider
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.LocalNgDialogColorMode
import io.legado.app.ui.design.components.compose.NgDialogColorMode
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.theme.NgDrawerPalette
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.CreateDocumentContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.hexString
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.longToast
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.outputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.toastOnUi
import java.io.File
import java.io.FileOutputStream
import java.util.IdentityHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val EDITOR_FALLBACK_BG_COLOR = 0xFFF7F3EA.toInt()
private const val ARG_INITIAL_ADJUST = "initialAdjust"
private const val ARG_INITIAL_FLOATING = "initialFloating"
private const val ARG_RETURN_ADJUST_BOOK = "returnAdjustBook"
private const val ARG_RETURN_ADJUST_TAG = "returnAdjustTag"
private const val ARG_RETURN_STYLE_PAGE = "returnStylePage"
private const val STATE_TITLE_LAYOUT_PAGE = "titleLayoutPage"
private const val STATE_FLOATING_PAGE = "floatingPage"
private const val STATE_FLOATING_TAB = "floatingTab"

internal fun BaseComposeDialogFragment.returnToReadStyleAdjustOnDismiss() {
    val ownerUrl = arguments?.getString(ARG_RETURN_ADJUST_BOOK) ?: return
    val host = activity as? ReadBookActivity ?: return
    showReadStyleAdjust(host, parentFragmentManager, ownerUrl, arguments?.getString(ARG_RETURN_ADJUST_TAG),
        arguments?.getString(ARG_RETURN_STYLE_PAGE))
}

private fun showReadStyleAdjust(
    host: ReadBookActivity,
    manager: FragmentManager,
    ownerUrl: String,
    returnTag: String?,
    returnPage: String? = null,
) {
    if (host.isFinishing || host.isDestroyed || manager.isDestroyed || manager.isStateSaved ||
        ReadBook.book?.bookUrl != ownerUrl || manager.fragments.any { it is ReadStyleDialog && it.isAdded }
    ) return
    ReadStyleDialog().apply {
        arguments = bundleOf(ARG_INITIAL_ADJUST to true, ARG_RETURN_STYLE_PAGE to returnPage)
    }.show(manager, returnTag ?: ReadStyleDialog::class.java.simpleName)
}

class ReadStyleDialog : BaseComposeDialogFragment() {

    companion object {
        fun forFloating() = ReadStyleDialog().apply {
            arguments = bundleOf(ARG_INITIAL_FLOATING to true)
        }
    }

    internal fun showPresetFromResource() {
        navigateTo(ReadStylePage.PRESET)
    }

    private val backgroundLibraryCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
            if (fragment !is ReadingBackgroundDialog) return
            fragment.setOnDismissListener {
                val target = editorSession.backgroundLibraryTarget
                editorSession.backgroundLibraryTarget = null
                val host = activity ?: return@setOnDismissListener
                if (!isAdded || isRemoving || host.isFinishing || host.isDestroyed || host.isChangingConfigurations ||
                    parentFragmentManager.isStateSaved || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) return@setOnDismissListener
                if (target?.bookUrl != ReadBook.book?.bookUrl) {
                    dismissAllowingStateLoss()
                    return@setOnDismissListener
                }
                dialog?.show()
                refreshUi(reusePresetThumbnails = true)
            }
            dialog?.hide()
        }
    }

    private val callBack get() = activity as? ReadBookActivity
    private lateinit var composeView: ComposeView
    private var page by mutableStateOf(ReadStylePage.PRESET)
    private var floatingTab = FloatingSettingsTab.COLOR
    private var screenState by mutableStateOf<ReadStyleUiState?>(null)
    private val presetIds = IdentityHashMap<ReadBookConfig.Config, Long>()
    private var nextPresetId = 0L
    private var editorBackgroundCache: List<ReadStyleBackgroundUi>? = null
    private var presetNameDialog: ComponentDialog? = null
    private var editingHighlightIndex: Int? = null
    private var creatingPreset = false
    private var highlightDraft: ReadHighlightRule? = null
    private val editorSession by viewModels<ReadStyleEditorSession>()
    private val presetEditorMode get() = editorSession.presetMode ?: ReadAppearanceMode.current()
    private var highlightColorMode: Int
        get() = editorSession.highlightMode ?: if (ReadBookConfig.isNightTheme) 1 else 0
        set(value) { editorSession.selectHighlightMode(value) }
    private var highlightColorInitialDraft: ReadHighlightRule? = null
    private var highlightSelectionMode = HighlightSelectionMode.NONE
    private var selectedHighlightIds: Set<String> = emptySet()
    private var pendingHighlightExportName: String? = null
    private var pendingPresetExportName: String? = null
    private var preparingPresetExport = false
    private var preparingHighlightExport = false
    private var previewReturnPage = ReadStylePage.ADJUST
    private var editorReturnPage = ReadStylePage.PRESET
    private var colorReturnPage = ReadStylePage.APPEARANCE
    private var underlineReturnPage = ReadStylePage.ADJUST
    private var highlightReturnPage = ReadStylePage.HIGHLIGHT
    private var highlightLinkReturnPage = ReadStylePage.PRESET
    private enum class PreviewDialog(val tag: String) {
        FONT_WEIGHT("fontWeightDialog"), FONT("bodyFontDialog"),
        TITLE_FONT("titleFontDialog"), TITLE_COLOR("titleColorDialog"),
        INDENT("paragraphIndentDialog"), CHINESE_CONVERTER("chineseConverterDialog"),
        INFORMATION("tipConfigDialog"), HEADER("tipHeaderConfigDialog"),
        FOOTER("tipFooterConfigDialog"), TIP_TEXT_COLOR("tipTextColorDialog"),
        TIP_DIVIDER_COLOR("tipDividerColorDialog"), PADDING("paddingConfigDialog")
    }
    private var previewTitleNight = false
    private var openPreviewAfterDismiss: PreviewDialog? = null
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
    ) { uri ->
        val target = editorSession.backgroundTarget
        editorSession.backgroundTarget = null
        if (uri != null) {
            if (target == null) {
                editorSession.importingBackground = false
                toastOnUi(R.string.read_style_editor_target_lost)
            }
            else setBackgroundFromUri(uri, target)
        } else {
            editorSession.importingBackground = false
        }
    }
    private val selectHighlightBackground = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let { installHighlightResource(it, "background") } }
    private val selectHighlightFont = registerForActivityResult(
        SelectFileContract()
    ) { uri -> uri?.let { installHighlightResource(it, "font") } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.registerFragmentLifecycleCallbacks(backgroundLibraryCallbacks, false)
        if (arguments?.getBoolean(ARG_INITIAL_ADJUST) == true) {
            page = runCatching { ReadStylePage.valueOf(arguments?.getString(ARG_RETURN_STYLE_PAGE).orEmpty()) }
                .getOrDefault(ReadStylePage.ADJUST)
        }
        if (arguments?.getBoolean(ARG_INITIAL_FLOATING) == true) page = ReadStylePage.FLOATING
        savedInstanceState?.getString(STATE_FLOATING_TAB)?.let { saved ->
            floatingTab = FloatingSettingsTab.valueOf(saved)
        }
        savedInstanceState?.getString(STATE_FLOATING_PAGE)?.let { saved ->
            page = if (saved == ReadStylePage.FLOATING_COLOR.name && editorSession.colorTarget == null) {
                ReadStylePage.FLOATING
            } else ReadStylePage.valueOf(saved)
        }
        if (editorSession.backgroundLibraryTarget != null) page = ReadStylePage.APPEARANCE
        if (savedInstanceState?.getBoolean(STATE_TITLE_LAYOUT_PAGE) == true) {
            page = ReadStylePage.TITLE
        }
        pendingHighlightExportName = savedInstanceState?.getString("pendingHighlightExportName")
        pendingPresetExportName = savedInstanceState?.getString("pendingPresetExportName")
        if (savedInstanceState?.containsKey("presetEditorMode") == true && editorSession.presetMode == null) {
            editorSession.selectPresetMode(savedInstanceState.getInt("presetEditorMode"))
        }
        if (savedInstanceState?.containsKey("highlightEditorMode") == true && editorSession.highlightMode == null) {
            editorSession.selectHighlightMode(savedInstanceState.getInt("highlightEditorMode"))
        }
        editorSession.allowEInk = editorSession.allowEInk || savedInstanceState?.getBoolean("allowEInkEditing") == true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pendingHighlightExportName", pendingHighlightExportName)
        outState.putString("pendingPresetExportName", pendingPresetExportName)
        editorSession.presetMode?.let { outState.putInt("presetEditorMode", it.ordinal) }
        editorSession.highlightMode?.let { outState.putInt("highlightEditorMode", it) }
        outState.putBoolean("allowEInkEditing", editorSession.allowEInk)
        outState.putBoolean(STATE_TITLE_LAYOUT_PAGE, page == ReadStylePage.TITLE)
        if (page == ReadStylePage.FLOATING || page == ReadStylePage.FLOATING_COLOR ||
            page == ReadStylePage.FLOATING_TOOLBAR || page == ReadStylePage.FLOATING_SIDE_TOOLBAR
        ) {
            outState.putString(STATE_FLOATING_PAGE, page.name)
            outState.putString(STATE_FLOATING_TAB, floatingTab.name)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0.0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (childFragmentManager.fragments.any { it is ReadingBackgroundDialog && it.isAdded }) dialog?.hide()
    }

    override fun onDestroy() {
        childFragmentManager.unregisterFragmentLifecycleCallbacks(backgroundLibraryCallbacks)
        super.onDestroy()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        composeView = view as ComposeView
        composeView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        refreshUi()
        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                screenState?.let { state ->
                    val primaryStrength = ReadFloatingAppearanceState.primaryStrengthPercent
                    val colorStyle = ReadFloatingAppearanceState.colorStyle
                    val baseSnapshot = ReadDrawerStyle.themeSnapshot(
                        context = requireContext(),
                        primaryStrengthPercent = primaryStrength,
                        colorStyle = colorStyle,
                    )
                    val adaptiveCard = NgDrawerPalette.resolveAdaptiveContentCardColor(
                        snapshot = baseSnapshot,
                        primaryStrengthPercent = primaryStrength,
                    )
                    NgDrawerThemeProvider(
                        snapshot = baseSnapshot.copy(colors = baseSnapshot.colors.copy(cardContainer = adaptiveCard)),
                        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
                        hasThemeProfile = false,
                    ) {
                        CompositionLocalProvider(
                            LocalNgDrawerContentCardAlpha provides
                                ReadFloatingAppearanceConfig.surfaceAlpha(state.editorFloatingTransparency),
                        ) {
                            val viewport = if (page == ReadStylePage.FLOATING_COLOR || page.isAnyColorEditorPage()) {
                                Modifier.heightIn(max = rememberColorDrawerViewportHeight(composeView))
                            } else {
                                Modifier
                            }
                            Box(viewport) {
                                NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                                    ReadStyleScreen(
                                        page = page,
                                        state = state,
                                        contentColor = Color(ReadDrawerStyle.contentColor(requireContext())),
                                        accentColor = Color(ReadDrawerStyle.accentColor(requireContext())),
                                        actions = createActions(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        val destination = openPreviewAfterDismiss
        openPreviewAfterDismiss = null
        val manager = if (destination != null) parentFragmentManager else null
        val host = activity as? ReadBookActivity
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
        if (destination != null && manager != null && host != null && !host.isFinishing && !host.isDestroyed &&
            !manager.isStateSaved && manager.findFragmentByTag(destination.tag) == null
        ) {
            val ownerUrl = ReadBook.book?.bookUrl
            val returnTag = tag ?: ReadStyleDialog::class.java.simpleName
            var returnRequested = false
            val reopenAdjust = {
                if (!returnRequested) {
                    returnRequested = true
                    if (ownerUrl != null) {
                        showReadStyleAdjust(host, manager, ownerUrl, returnTag, previewReturnPage.name)
                    }
                }
            }
            fun showTarget(target: BaseComposeDialogFragment) {
                target.arguments = (target.arguments ?: Bundle()).apply {
                    putString(ARG_RETURN_ADJUST_BOOK, ownerUrl)
                    putString(ARG_RETURN_ADJUST_TAG, returnTag)
                    putString(ARG_RETURN_STYLE_PAGE, previewReturnPage.name)
                }
                target.show(manager, destination.tag)
            }
            when (destination) {
                PreviewDialog.FONT_WEIGHT -> showFontWeightSetting(host, reopenAdjust)
                PreviewDialog.FONT -> showTarget(FontSelectDialog.forReaderBody())
                PreviewDialog.TITLE_FONT -> showTarget(FontSelectDialog.forReaderTitle())
                PreviewDialog.TITLE_COLOR -> showTarget(TipConfigDialog.forTitleColor(previewTitleNight))
                PreviewDialog.INDENT -> showParagraphIndentSetting(host, reopenAdjust)
                PreviewDialog.CHINESE_CONVERTER -> showChineseConverterSetting(host, reopenAdjust)
                PreviewDialog.INFORMATION -> showTarget(TipConfigDialog())
                PreviewDialog.HEADER -> showTarget(TipConfigDialog.forHeader())
                PreviewDialog.FOOTER -> showTarget(TipConfigDialog.forFooter())
                PreviewDialog.TIP_TEXT_COLOR -> showTarget(TipConfigDialog.forTipColor(divider = false))
                PreviewDialog.TIP_DIVIDER_COLOR -> showTarget(TipConfigDialog.forTipColor(divider = true))
                PreviewDialog.PADDING -> showTarget(PaddingConfigDialog())
            }
        }
    }

    override fun onDestroyView() {
        presetNameDialog?.dismiss()
        presetNameDialog = null
        super.onDestroyView()
    }

    private fun createActions() = ReadStyleActions(
        onPageSelected = ::navigateTo,
        onCreatePreset = { showPresetNameDialog() },
        onSelectPreset = ::changeBgTextConfig,
        onImportPreset = {
            selectImportDocument.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        },
        onEditPreset = { showPresetNameDialog(ReadBookConfig.styleSelect) },
        onExportPreset = {
            preparePresetExport()
        },
        onDeletePreset = ::deleteCurrentStyle,
        onRestoreCurrentPreset = ::confirmRestoreCurrentPreset,
        onRestoreAllPresets = ::confirmRestoreAllPresets,
        onEpubStyleChanged = { book ->
            if (ReadBook.book === book) postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        },
        onOnlyThisBookChanged = { enabled ->
            ReadBookConfig.setOnlyThisBook(enabled)
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
        onImportHighlights = {
            selectHighlightImportDocument.launch(
                arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream")
            )
        },
        onBeginHighlightSelection = ::beginHighlightSelection,
        onRestoreBuiltInHighlights = ::confirmRestoreBuiltInHighlights,
        onToggleHighlightSelection = ::toggleHighlightSelection,
        onToggleAllHighlightSelection = ::toggleAllHighlightSelection,
        onCancelHighlightSelection = ::clearHighlightSelection,
        onConfirmHighlightSelection = ::confirmHighlightSelection,
        onBack = ::navigateBack,
        onPresetNameChanged = { value ->
            ReadBookConfig.durConfig.name = value
            updateEditorState {
                copy(
                    selectedPresetName = value,
                    canRestoreCurrentDefault = ReadBookConfig.hasDefaultForCurrent(),
                )
            }
        },
        onTextColorChanged = ::applyEditorTextColor,
        onBackgroundColorChanged = ::applyEditorBackgroundColor,
        onTextAccentColorChanged = ::applyEditorAccentColor,
        onEditorModeChanged = { selected ->
            editorSession.selectPresetMode(selected)
            refreshUi(reusePresetThumbnails = true)
        },
        onResetEditorColor = ::resetEditorColor,
        onBackgroundAlphaChanged = { alpha ->
            val safeAlpha = alpha.coerceIn(0, 100)
            ReadBookConfig.bgAlpha = safeAlpha
            updateEditorState { copy(editorBackgroundAlpha = safeAlpha) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(3))
        },
        onSelectBackgroundImage = ::openBackgroundLibrary,
        onSelectBackground = ::selectBackground,
        onPickFloatingColor = ::openFloatingColorEditor,
        onFloatingColorChanged = ::applyEditorFloatingColor,
        onRestoreFloatingOriginal = { restoreFloatingColorSnapshot() },
        onFloatingTransparencyChanged = { value ->
            val config = ReadBookConfig.durConfig
            config.readFloatingTransparency = ReadFloatingAppearanceConfig.normalizePercent(value)
            updateEditorState {
                copy(editorFloatingTransparency = config.curReadFloatingTransparency())
            }
            ReadFloatingAppearanceState.update(
                config.curReadFloatingTransparency(),
                config.curReadFloatingPrimaryStrength(),
                ReadBookConfig.effectiveReadFloatingColor(config).colorStyle,
            )
        },
        onFloatingPrimaryStrengthChanged = { value ->
            val config = ReadBookConfig.durConfig
            config.readFloatingPrimaryStrength = ReadFloatingAppearanceConfig.normalizePercent(value)
            updateEditorState {
                copy(editorFloatingPrimaryStrength = config.curReadFloatingPrimaryStrength())
            }
            ReadFloatingAppearanceState.update(
                config.curReadFloatingTransparency(),
                config.curReadFloatingPrimaryStrength(),
                ReadBookConfig.effectiveReadFloatingColor(config).colorStyle,
            )
        },
        onFloatingColorStyleChanged = { style ->
            val config = ReadBookConfig.durConfig
            if (ReadBookConfig.floatingColorManagedGlobally) {
                ReadBookConfig.readFloatingGlobalColorStyle = style
            } else {
                config.readFloatingColorStyle = style
            }
            updateEditorState { copy(editorFloatingColorStyle = style) }
            ReadFloatingAppearanceState.update(
                config.curReadFloatingTransparency(),
                config.curReadFloatingPrimaryStrength(),
                ReadBookConfig.effectiveReadFloatingColor(config).colorStyle,
            )
            if (!ReadBookConfig.floatingColorManagedGlobally) ReadBookConfig.save()
            notifyFloatingAppearanceChanged()
        },
        onFloatingAppearanceChangeFinished = {
            ReadBookConfig.save()
            notifyFloatingAppearanceChanged()
        },
        onFloatingCatalogStyleChanged = { value ->
            requireContext().putPrefString(PreferKey.readCatalogStyle, value)
            updateEditorState { copy(floatingCatalogStyle = value) }
        },
        onFloatingToolbarChanged = { value ->
            requireContext().putPrefBoolean(PreferKey.showBrightnessView, value)
            updateEditorState { copy(floatingShowToolbar = value) }
            postEvent(PreferKey.showBrightnessView, "")
        },
        onFloatingChapterProgressChanged = { value ->
            requireContext().putPrefBoolean(PreferKey.readMenuShowChapterProgress, value)
            updateEditorState { copy(floatingShowChapterProgress = value) }
            postEvent(PreferKey.readMenuShowChapterProgress, "")
        },
        onFloatingProgressBehaviorChanged = { value ->
            requireContext().putPrefString(PreferKey.progressBarBehavior, value)
            updateEditorState { copy(floatingProgressBehavior = value) }
            postEvent(EventBus.UP_SEEK_BAR, true)
        },
        onFloatingToolbarOrder = { page = ReadStylePage.FLOATING_TOOLBAR },
        onFloatingSideToolOrder = { page = ReadStylePage.FLOATING_SIDE_TOOLBAR },
        onFloatingSideToolsChanged = { postEvent(PreferKey.readFloatingToolOrder, "") },
        floatingSideToolCapacity = { (requireActivity() as ReadBookActivity).floatingToolMaxCapacity() },
        onFloatingTabChanged = { tab ->
            floatingTab = tab
            updateEditorState { copy(floatingTab = tab) }
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
            val target = editorSession.colorTarget ?: captureEditorTarget()
            target.layout.setUnderlineColorForMode(color, target.mode)
            refreshFullLineUnderlineState(target)
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
        onFontWeight = { showConfigPreview(PreviewDialog.FONT_WEIGHT) },
        onFont = { showConfigPreview(PreviewDialog.FONT) },
        onTitleFont = { showConfigPreview(PreviewDialog.TITLE_FONT) },
        onTitleColor = { night -> previewTitleNight = night; showConfigPreview(PreviewDialog.TITLE_COLOR) },
        onTitleChanged = ::notifyCompactTitleChanged,
        onIndent = { showConfigPreview(PreviewDialog.INDENT) },
        onFontWeightChanged = { value ->
            val weight = ((value.coerceIn(100, 900) + 50) / 100) * 100
            ReadBookConfig.textBold = weight
            updateAdjustState { copy(textWeight = weight) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
        },
        onIndentChanged = { value ->
            val level = value.coerceIn(0, 4)
            ReadBookConfig.paragraphIndent = "　".repeat(level)
            updateAdjustState { copy(paragraphIndentLevel = level) }
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        },
        onChineseConverter = { showConfigPreview(PreviewDialog.CHINESE_CONVERTER) },
        onPadding = { showConfigPreview(PreviewDialog.PADDING) },
        onTip = { showConfigPreview(PreviewDialog.INFORMATION) },
        onTipColorChanged = { postEvent(EventBus.UP_CONFIG, arrayListOf(2)) },
        onTipTextColor = { showConfigPreview(PreviewDialog.TIP_TEXT_COLOR) },
        onTipDividerColor = { showConfigPreview(PreviewDialog.TIP_DIVIDER_COLOR) },
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
        onHeader = { showConfigPreview(PreviewDialog.HEADER) },
        onFooter = { showConfigPreview(PreviewDialog.FOOTER) },
        onEditPresetAt = { index -> showPresetNameDialog(index) },
        onExportPresetAt = { index -> preparePresetExport(index) },
        onDeletePresetAt = { index -> deleteStyle(index) },
        onCopyPresetAt = { index ->
            ReadBookConfig.getExportConfig(index)?.let { source ->
                val copy = source.copyForBook().apply { name = "${source.name.ifBlank { getString(R.string.text) }} 副本" }
                val newIndex = ReadBookConfig.createStyle(copy)
                ReadBookConfig.styleSelect = newIndex
                ReadBookConfig.save()
                ReadFloatingAppearanceState.refreshFromConfig()
                notifyPresetRestored()
                refreshUi()
            }
        },
        onRestorePresetAt = ::confirmRestorePreset,
        onLinkedHighlightModeChanged = { useLinked ->
            val config = ReadBookConfig.durConfig
            if (useLinked && config.linkedHighlightRuleIds.orEmpty().none { id -> currentRules().any { it.id == id } }) {
                navigateTo(ReadStylePage.HIGHLIGHT_LINK)
            } else {
                config.useLinkedHighlightRules = useLinked
                ReadBookConfig.save()
                refreshUi(reusePresetThumbnails = true)
                postEvent(EventBus.UP_CONFIG, arrayListOf(13))
            }
        },
        onLinkedHighlightRuleChanged = { id, checked ->
            val config = ReadBookConfig.durConfig
            val ids = config.linkedHighlightRuleIds.orEmpty().toMutableSet()
            if (checked) ids.add(id) else ids.remove(id)
            config.linkedHighlightRuleIds = ids.toList()
            config.useLinkedHighlightRules = ids.isNotEmpty()
            ReadBookConfig.save()
            refreshUi(reusePresetThumbnails = true)
            postEvent(EventBus.UP_CONFIG, arrayListOf(13))
        },
        onCreateHighlight = { openHighlightEditor() },
        onEditHighlight = { openHighlightEditor(it) },
        onCopyHighlight = ::copyHighlightRule,
        onExportHighlight = { id ->
            currentRules().firstOrNull { it.id == id }?.let { prepareHighlightExport(listOf(it)) }
        },
        onRestoreHighlight = ::confirmRestoreHighlight,
        onDeleteHighlightById = ::confirmDeleteHighlight,
        onHighlightDraftChanged = { draft ->
            highlightDraft = draft
            updateEditorState { copy(highlightDraft = draft) }
        },
        onHighlightColorModeChanged = { mode ->
            val selectedMode = editorSession.selectHighlightMode(mode)
            updateEditorState { withHighlightColorMode(selectedMode) }
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
    )

    private fun updateAdjustState(transform: ReadStyleUiState.() -> ReadStyleUiState) {
        screenState = screenState?.transform()
    }

    private fun updateEditorState(transform: ReadStyleUiState.() -> ReadStyleUiState) {
        screenState = screenState?.transform()
    }

    private var presetPreviewMode: ReadAppearanceMode? = null
    private var editorBackgroundOwner: ReadBookConfig.Config? = null

    private fun refreshUi() = refreshUi(reusePresetThumbnails = false)

    private fun refreshUi(reusePresetThumbnails: Boolean) {
        val config = ReadBookConfig.durConfig
        val runtimeMode = ReadAppearanceMode.current()
        val cachedPresets = screenState?.presets.takeIf { reusePresetThumbnails && presetPreviewMode == runtimeMode }
        presetPreviewMode = runtimeMode
        val name = config.name.ifBlank { getString(R.string.text) }
        val mode = presetEditorMode
        editorSession.allowEInk = editorSession.allowEInk || AppConfig.isEInkMode || mode == ReadAppearanceMode.EINK
        val modeLabel = getString(
            when (mode) {
                ReadAppearanceMode.NIGHT -> R.string.read_style_mode_night
                ReadAppearanceMode.EINK -> R.string.read_style_mode_eink
                else -> R.string.read_style_mode_day
            }
        )
        val backgroundType = config.backgroundTypeForMode(mode)
        val backgroundName = config.backgroundForMode(mode)
        val backgroundColor = if (backgroundType == 0) {
            runCatching { backgroundName.toColorInt() }.getOrDefault(EDITOR_FALLBACK_BG_COLOR)
        } else {
            EDITOR_FALLBACK_BG_COLOR
        }
        val previewBackground = if (backgroundType == 0) {
            null
        } else {
            runCatching {
                config.backgroundDrawableForMode(mode, 352, 176).toBitmap(352, 176).asImageBitmap()
            }.getOrNull()
        }
        val rules = currentRules()
        val effectiveFloatingColor = ReadBookConfig.effectiveReadFloatingColor(config, mode)
        selectedHighlightIds = selectedHighlightIds.intersect(rules.mapTo(hashSetOf()) { it.id })
        val activeBook = ReadBook.book
        screenState = ReadStyleUiState(
            presets = cachedPresets ?: (if (ReadBookConfig.onlyThisBook) listOf(-1 to config) else emptyList())
                .plus(ReadBookConfig.configList.mapIndexed { index, item -> index to item })
                .map { (index, item) ->
                ReadStylePresetUi(
                    index = index,
                    stableId = presetIds.getOrPut(item) { nextPresetId++ },
                    name = if (index == -1) getString(R.string.read_style_this_book)
                        else item.name.ifBlank { getString(R.string.text) },
                    textColor = item.textColorForMode(runtimeMode),
                    canRestoreDefault = ReadBookConfig.hasDefaultForPreset(index),
                    background = runCatching {
                        item.backgroundDrawableForMode(runtimeMode, 176, 128).toBitmap(176, 128).asImageBitmap()
                    }.getOrNull(),
                )
            },
            selectedPresetIndex = ReadBookConfig.styleSelect,
            selectedPresetName = name,
            canRestoreCurrentDefault = ReadBookConfig.hasDefaultForCurrent(),
            highlightSummary = getString(
                R.string.read_highlight_summary,
                rules.count(ReadHighlightRule::enabled),
                rules.size,
            ),
            shareLayout = ReadBookConfig.shareLayout,
            isEpub = activeBook?.isEpub == true,
            epubBook = activeBook?.takeIf { it.isEpub },
            onlyThisBook = ReadBookConfig.onlyThisBook,
            canUseBookStyle = ReadBookConfig.canUseBookStyle,
            globalFloatingFollowApp = ReadBookConfig.floatingColorManagedGlobally,
            floatingStandalone = arguments?.getBoolean(ARG_INITIAL_FLOATING) == true,
            floatingTab = floatingTab,
            floatingCatalogStyle = requireContext().getPrefString(
                PreferKey.readCatalogStyle, ReadCatalogStyle.CARD.value,
            ).orEmpty(),
            floatingShowToolbar = requireContext().getPrefBoolean(PreferKey.showBrightnessView, true),
            floatingShowChapterProgress = AppConfig.readMenuShowChapterProgress,
            floatingProgressBehavior = requireContext().getPrefString(PreferKey.progressBarBehavior, "page").orEmpty(),
            textSize = ReadBookConfig.textSize,
            textWeight = resolveFontWeight(ReadBookConfig.textBold),
            letterSpacing = ReadBookConfig.letterSpacing,
            lineSpacingExtra = ReadBookConfig.lineSpacingExtra,
            paragraphSpacing = ReadBookConfig.paragraphSpacing,
            paragraphIndentLevel = paragraphIndentLevel(),
            bodyPadding = ReadBodyPadding(
                top = ReadBookConfig.paddingTop,
                bottom = ReadBookConfig.paddingBottom,
                left = ReadBookConfig.paddingLeft,
                right = ReadBookConfig.paddingRight,
            ),
            pageAnim = ReadBook.pageAnim().coerceIn(0, 4),
            highlightRules = rules,
            builtInHighlightRuleIds = DefaultData.readHighlightRules.mapTo(hashSetOf(), ReadHighlightRule::id),
            highlightSelectionMode = highlightSelectionMode,
            selectedHighlightIds = selectedHighlightIds,
            editorMode = mode.ordinal,
            allowEInkEditing = editorSession.allowEInk,
            colorPreviewTarget = editorSession.colorTarget ?: captureEditorTarget(),
            creatingPreset = creatingPreset,
            editorModeLabel = modeLabel,
            editorPreviewBackground = previewBackground,
            editorBackgrounds = emptyList(),
            editorBackgroundType = backgroundType,
            editorBackgroundName = backgroundName,
            editorTextColor = config.textColorForMode(mode),
            editorBackgroundColor = backgroundColor,
            editorTextAccentColor = config.textAccentColorForMode(mode),
            editorBackgroundAlpha = ReadBookConfig.bgAlpha.coerceIn(0, 100),
            editorFloatingColorSeed = effectiveFloatingColor.seed,
            editorFloatingColorIsCustom = !effectiveFloatingColor.followsApplication,
            floatingDayColor = ReadBookConfig.effectiveReadFloatingColor(config, ReadAppearanceMode.DAY),
            floatingNightColor = ReadBookConfig.effectiveReadFloatingColor(config, ReadAppearanceMode.NIGHT),
            editorFloatingTransparency = config.curReadFloatingTransparency(),
            editorFloatingPrimaryStrength = config.curReadFloatingPrimaryStrength(),
            editorFloatingColorStyle = effectiveFloatingColor.colorStyle,
            fullLineUnderline = currentFullLineUnderlineState(),
            highlightDraft = highlightDraft,
            highlightColorMode = highlightColorMode,
            editingHighlightIndex = editingHighlightIndex,
            editorInitialColor = null,
            editorInitialColorWasUnset = false,
            editorInitialBackgroundType = null,
            editorInitialBackgroundName = null,
            editorInitialBackground = null,
            bodyFontLabel = ReadBookConfig.textFont.let { path ->
                if (path.isBlank() || path == "system") "系统字体"
                else Uri.decode(path.substringAfterLast('/').substringAfterLast('\\')).substringBeforeLast('.')
            },
            linkedHighlightRuleIds = config.linkedHighlightRuleIds.orEmpty(),
            useLinkedHighlightRules = config.useLinkedHighlightRules,
        )
    }

    private fun currentRules(): List<ReadHighlightRule> =
        ReadHighlightRuleStore.allRules().sortedBy(ReadHighlightRule::position)

    private fun changeBgTextConfig(index: Int) {
        val oldIndex = ReadBookConfig.styleSelect
        if (index !in ReadBookConfig.configList.indices || index == oldIndex) return
        ReadBookConfig.styleSelect = index
        ReadFloatingAppearanceState.refreshFromConfig()
        refreshUi()
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 13))
        notifyFloatingAppearanceChanged()
    }

    private fun showPresetNameDialog(index: Int? = null) {
        if (presetNameDialog?.isShowing == true) return
        val ownerUrl = ReadBook.book?.bookUrl
        val onlyThisBook = ReadBookConfig.onlyThisBook
        val target = when {
            index == null -> null
            onlyThisBook && index == -1 -> ReadBookConfig.durConfig
            !onlyThisBook -> ReadBookConfig.configList.getOrNull(index) ?: return
            else -> return
        }
        val initialName = target?.name ?: defaultPresetName(
            ReadBookConfig.configList.map { it.name } +
                if (onlyThisBook) listOf(ReadBookConfig.durConfig.name) else emptyList(),
        )
        presetNameDialog = showReadComposeDialog(requireContext(), onDismiss = { presetNameDialog = null }) { dismiss ->
            var name by remember { mutableStateOf(initialName) }
            CompositionLocalProvider(LocalNgDialogColorMode provides NgDialogColorMode.INHERIT_HOST) {
                NgDialog(
                    title = if (target == null) "新建预设" else "重命名预设",
                    variant = NgDialogVariant.FORM_EDITOR,
                    titleFontSize = 20.sp,
                    titleFontWeight = FontWeight.Medium,
                    titleLineHeight = 24.sp,
                    actions = {
                        NgFormActionButton("取消", onClick = dismiss, modifier = Modifier.weight(1f))
                        NgFormActionButton("确定", onClick = {
                            val validTarget = target == null || if (onlyThisBook) {
                                ReadBookConfig.durConfig === target
                            } else {
                                ReadBookConfig.configList.any { it === target }
                            }
                            if (ownerUrl != ReadBook.book?.bookUrl || onlyThisBook != ReadBookConfig.onlyThisBook || !validTarget) {
                                dismiss()
                                toastOnUi("阅读状态已变化，请重试")
                            } else {
                                val trimmed = name.trim()
                                if (target == null) {
                                    val newIndex = ReadBookConfig.createStyle(ReadBookConfig.Config(
                                        name = trimmed,
                                        bgStr = "新羊皮纸.jpg",
                                        readFloatingTransparency = 0,
                                        readFloatingPrimaryStrength = 100,
                                    ))
                                    if (onlyThisBook) {
                                        ReadFloatingAppearanceState.refreshFromConfig()
                                        refreshUi()
                                        notifyPresetRestored()
                                    } else {
                                        changeBgTextConfig(newIndex)
                                        ReadBookConfig.save()
                                    }
                                } else {
                                    target.name = trimmed
                                    ReadBookConfig.save()
                                    refreshUi()
                                }
                                dismiss()
                            }
                        }, modifier = Modifier.weight(1f), enabled = name.isNotBlank(), variant = NgButtonVariant.PRIMARY)
                    },
                ) {
                    NgFormField(
                        label = getString(R.string.read_style_name),
                        value = name,
                        onValueChange = { name = it },
                        variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                        autoFocus = true,
                    )
                    if (target == null && onlyThisBook) {
                        Text("确认后将替换当前本书预设", Modifier.padding(top = 8.dp),
                            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    private fun openEditor(index: Int, isNew: Boolean = false) {
        creatingPreset = isNew
        editorReturnPage = if (page == ReadStylePage.PRESET_MANAGE) ReadStylePage.PRESET_MANAGE else ReadStylePage.PRESET
        if (editorSession.presetMode == null) editorSession.presetMode = ReadAppearanceMode.current()
        changeBgTextConfig(index)
        page = ReadStylePage.EDIT
        refreshUi()
    }

    private fun navigateTo(target: ReadStylePage) {
        if (target == ReadStylePage.FLOATING && page == ReadStylePage.APPEARANCE) {
            floatingTab = FloatingSettingsTab.COLOR
        }
        if (target != ReadStylePage.HIGHLIGHT && highlightSelectionMode != HighlightSelectionMode.NONE) {
            clearHighlightSelection(refresh = false)
        }
        if (target == ReadStylePage.HIGHLIGHT_LINK && page != ReadStylePage.HIGHLIGHT_LINK) {
            highlightLinkReturnPage = page
        }
        if (target.isPresetColorEditorPage()) colorReturnPage = page
        if (target == ReadStylePage.EDIT_UNDERLINE) underlineReturnPage = page
        if (target.isAnyColorEditorPage()) {
            val state = screenState ?: return
            val highlightNight = state.highlightColorMode == 1
            if (target.isHighlightColorEditorPage()) highlightColorInitialDraft = highlightDraft
            val previewTarget = captureEditorTarget(
                if (target.isHighlightColorEditorPage()) ReadAppearanceMode.fromIndex(state.highlightColorMode)
                else presetEditorMode,
            )
            editorSession.colorTarget = previewTarget
            val initialNullableColor = when (target) {
                ReadStylePage.EDIT_TEXT_COLOR -> state.editorTextColor
                ReadStylePage.EDIT_BACKGROUND_COLOR -> state.editorBackgroundColor
                ReadStylePage.EDIT_ACCENT_COLOR -> state.editorTextAccentColor
                ReadStylePage.EDIT_UNDERLINE_COLOR -> state.fullLineUnderline.color
                ReadStylePage.HIGHLIGHT_TEXT_COLOR ->
                    state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_TEXT, highlightNight)
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR ->
                    state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_BACKGROUND, highlightNight)
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR ->
                    state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_UNDERLINE, highlightNight)
                else -> null
            }
            val fallbackColor = when (target) {
                ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR ->
                    state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_BACKGROUND, highlightNight)
                        ?: ((state.editorTextAccentColor and 0x00FFFFFF) or 0x33000000)
                ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR ->
                    state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_UNDERLINE, highlightNight)
                        ?: state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_TEXT, highlightNight)
                        ?: state.editorTextAccentColor
                ReadStylePage.HIGHLIGHT_TEXT_COLOR ->
                    state.highlightDraft?.colorValue(ReadHighlightRule.COLOR_TEXT, highlightNight)
                        ?: state.editorTextAccentColor
                else -> state.editorTextAccentColor
            }
            screenState = state.copy(
                colorPreviewTarget = previewTarget,
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
                page = colorReturnPage
                clearEditorColorInitialState()
            }

            page == ReadStylePage.EDIT_UNDERLINE_COLOR -> {
                page = ReadStylePage.EDIT_UNDERLINE
                clearEditorColorInitialState()
            }

            page == ReadStylePage.FLOATING_COLOR -> {
                ReadBookConfig.save()
                page = ReadStylePage.FLOATING
                clearEditorColorInitialState()
                refreshUi(reusePresetThumbnails = true)
            }

            page.isHighlightColorEditorPage() -> {
                page = ReadStylePage.HIGHLIGHT_EDIT
                clearEditorColorInitialState()
            }

            page == ReadStylePage.EDIT_UNDERLINE -> page = underlineReturnPage

            page == ReadStylePage.HIGHLIGHT_EDIT -> {
                clearHighlightDraft()
                page = highlightReturnPage
                refreshUi()
            }

            page == ReadStylePage.EDIT -> {
                page = editorReturnPage
                refreshUi()
            }
            page == ReadStylePage.PRESET_MANAGE -> { page = ReadStylePage.PRESET; refreshUi() }
            page == ReadStylePage.FLOATING_TOOLBAR || page == ReadStylePage.FLOATING_SIDE_TOOLBAR ->
                page = ReadStylePage.FLOATING
            page == ReadStylePage.FLOATING -> {
                if (arguments?.getBoolean(ARG_INITIAL_FLOATING) == true) dismissAllowingStateLoss()
                else { page = ReadStylePage.APPEARANCE; refreshUi() }
            }
            page == ReadStylePage.HIGHLIGHT_LINK -> {
                page = highlightLinkReturnPage
                refreshUi(reusePresetThumbnails = true)
            }
        }
    }

    private fun applyEditorTextColor(color: Int) {
        val target = editorSession.colorTarget ?: captureEditorTarget()
        target.appearance.setTextColorForMode(color, target.mode)
        updateEditorState { copy(editorTextColor = color) }
        postEditorTextColorChanged(target)
    }

    private fun applyEditorAccentColor(color: Int) {
        val target = editorSession.colorTarget ?: captureEditorTarget()
        target.appearance.setTextAccentColorForMode(color, target.mode)
        updateEditorState { copy(editorTextAccentColor = color) }
        postEditorTextColorChanged(target)
    }

    private fun applyEditorBackgroundColor(color: Int) {
        val target = editorSession.colorTarget ?: captureEditorTarget()
        target.appearance.setBackgroundForMode(0, "#${color.hexString}", target.mode)
        updateEditorState {
            copy(
                editorPreviewBackground = null,
                editorBackgroundType = 0,
                editorBackgroundName = "#${color.hexString}",
                editorBackgroundColor = color,
            )
        }
        postEditorBackgroundChanged(target)
    }

    private fun resetEditorColor() {
        val state = screenState ?: return
        val initialColor = state.editorInitialColor ?: return
        val resettingFloatingColor = page == ReadStylePage.FLOATING_COLOR
        when (page) {
            ReadStylePage.EDIT_TEXT_COLOR -> applyEditorTextColor(initialColor)
            ReadStylePage.EDIT_BACKGROUND_COLOR -> {
                val initialType = state.editorInitialBackgroundType ?: 0
                val initialName = state.editorInitialBackgroundName.orEmpty()
                if (initialType == 0) {
                    applyEditorBackgroundColor(initialColor)
                } else {
                    val target = editorSession.colorTarget ?: captureEditorTarget()
                    target.appearance.setBackgroundForMode(initialType, initialName, target.mode)
                    updateEditorState {
                        copy(
                            editorPreviewBackground = state.editorInitialBackground,
                            editorBackgroundType = initialType,
                            editorBackgroundName = initialName,
                        )
                    }
                    postEditorBackgroundChanged(target)
                }
            }
            ReadStylePage.EDIT_ACCENT_COLOR -> applyEditorAccentColor(initialColor)
            ReadStylePage.EDIT_UNDERLINE_COLOR -> {
                val target = editorSession.colorTarget ?: captureEditorTarget()
                target.layout.setUnderlineColorForMode(initialColor, target.mode)
                refreshFullLineUnderlineState(target)
            }
            ReadStylePage.FLOATING_COLOR -> {
                if (!restoreFloatingColorSnapshot()) return
                ReadBookConfig.save()
            }
            ReadStylePage.HIGHLIGHT_TEXT_COLOR,
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> updateHighlightDraft {
                highlightColorInitialDraft ?: this
            }
            else -> return
        }
        page = when (page) {
            ReadStylePage.EDIT_UNDERLINE_COLOR -> ReadStylePage.EDIT_UNDERLINE
            ReadStylePage.FLOATING_COLOR -> ReadStylePage.FLOATING
            ReadStylePage.HIGHLIGHT_TEXT_COLOR,
            ReadStylePage.HIGHLIGHT_BACKGROUND_COLOR,
            ReadStylePage.HIGHLIGHT_UNDERLINE_COLOR -> ReadStylePage.HIGHLIGHT_EDIT
            else -> colorReturnPage
        }
        clearEditorColorInitialState()
        if (resettingFloatingColor) refreshUi(reusePresetThumbnails = true)
    }

    private fun clearEditorColorInitialState() {
        highlightColorInitialDraft = null
        editorSession.colorTarget = null
        editorSession.floatingColorSnapshot = null
        updateEditorState {
            copy(
                editorInitialColor = null,
                editorInitialColorWasUnset = false,
                editorInitialBackgroundType = null,
                editorInitialBackgroundName = null,
                editorInitialBackground = null,
            )
        }
    }

    private fun captureEditorTarget(mode: ReadAppearanceMode = presetEditorMode) = ReadStyleEditTarget(
        ReadBookConfig.durConfig, ReadBookConfig.config, mode, ReadBook.book?.bookUrl,
    )

    private fun isActiveAppearanceTarget(target: ReadStyleEditTarget): Boolean =
        target.appearance === ReadBookConfig.durConfig && target.mode == ReadAppearanceMode.current()

    private fun isSameBook(target: ReadStyleEditTarget): Boolean {
        if (target.bookUrl == ReadBook.book?.bookUrl) return true
        toastOnUi(R.string.read_style_editor_target_lost)
        return false
    }

    private fun postEditorTextColorChanged(target: ReadStyleEditTarget? = null) {
        if (target != null && !isActiveAppearanceTarget(target)) return
        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
    }

    private fun postEditorBackgroundChanged(target: ReadStyleEditTarget? = null) {
        if (target != null && !isActiveAppearanceTarget(target)) return
        postEvent(EventBus.UP_CONFIG, arrayListOf(1))
    }

    private fun currentFullLineUnderlineState() = FullLineUnderlineUiState(
        enabled = ReadBookConfig.fullLineUnderlineEnabled,
        dashed = ReadBookConfig.dottedLine,
        color = ReadBookConfig.config.underlineColorForMode(presetEditorMode),
        width = ReadBookConfig.underlineHeight.coerceIn(1, 20),
        offset = ReadBookConfig.underlinePadding.coerceIn(0, 20),
        extend = ReadBookConfig.underlineExtend,
        dashLength = ReadBookConfig.dottedBase.coerceIn(1f, 20f),
        gapLength = ReadBookConfig.dottedRatio.coerceIn(1f, 20f),
    )

    private fun showConfigPreview(destination: PreviewDialog) {
        val manager = parentFragmentManager
        if (openPreviewAfterDismiss != null || manager.isStateSaved ||
            manager.findFragmentByTag(destination.tag) != null
        ) return
        ReadBookConfig.save()
        previewReturnPage = page
        openPreviewAfterDismiss = destination
        dismiss()
    }

    private fun notifyCompactTitleChanged() {
        ReadBookConfig.save()
        val hasImage = ReadBookConfig.config.run { headerImage.isNotBlank() || footerImage.isNotBlank() }
        postEvent(EventBus.UP_CONFIG, if (hasImage) arrayListOf(2, 5) else arrayListOf(8, 5))
    }

    private fun refreshFullLineUnderlineState(target: ReadStyleEditTarget? = null) {
        updateEditorState { copy(fullLineUnderline = currentFullLineUnderlineState()) }
        // Underline colors have day/night slots, including when the reader is in E-ink mode.
        if (target != null && (target.layout !== ReadBookConfig.config ||
                (target.mode == ReadAppearanceMode.NIGHT) != ReadBookConfig.isNightTheme)) return
        // EPUB owns a separate renderer; refresh it after the existing native-page updates.
        postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11, 14))
    }

    private fun selectBackground(type: Int, name: String) {
        val selected = loadEditorBackgrounds().firstOrNull {
            it.type == type && it.name == name
        } ?: return
        val target = captureEditorTarget()
        target.appearance.setBackgroundForMode(type, name, target.mode)
        updateEditorState {
            copy(
                editorPreviewBackground = selected.background,
                editorBackgroundType = type,
                editorBackgroundName = name,
            )
        }
        postEditorBackgroundChanged(target)
    }

    private fun openBackgroundLibrary() {
        if (childFragmentManager.isStateSaved || childFragmentManager.findFragmentByTag("readingBackgrounds") != null) return
        editorSession.backgroundLibraryTarget = captureEditorTarget()
        ReadingBackgroundDialog().show(childFragmentManager, "readingBackgrounds")
    }

    internal fun backgroundLibraryTarget(): ReadStyleEditTarget? {
        val original = editorSession.backgroundLibraryTarget ?: return null
        if (original.bookUrl != ReadBook.book?.bookUrl || original.appearance !== ReadBookConfig.durConfig) return null
        return ReadStyleEditTarget(original.appearance, original.layout, presetEditorMode, original.bookUrl)
    }

    internal fun backgroundLibraryAllowsEInk(): Boolean = editorSession.allowEInk

    internal fun backgroundLibraryImporting(): Boolean = editorSession.importingBackground

    internal fun setBackgroundLibraryMode(index: Int) {
        if (backgroundLibraryTarget() == null || editorSession.importingBackground) return
        editorSession.selectPresetMode(index)
        refreshUi(reusePresetThumbnails = true)
    }

    internal fun importLibraryBackground() {
        val target = backgroundLibraryTarget() ?: return
        if (editorSession.importingBackground) return
        editorSession.backgroundTarget = target
        editorSession.importingBackground = true
        selectBackgroundImage.launch(arrayOf("image/*"))
    }

    internal fun applyLibraryBackground(entry: ReadingBackground) {
        val target = backgroundLibraryTarget() ?: return
        target.appearance.setBackgroundForMode(entry.type, entry.reference, target.mode)
        editorBackgroundCache = null
        postEditorBackgroundChanged(target)
        refreshUi(reusePresetThumbnails = true)
    }

    private fun openFloatingColorEditor() {
        if (ReadBookConfig.floatingColorManagedGlobally || presetEditorMode == ReadAppearanceMode.EINK) return
        val target = captureEditorTarget()
        val state = screenState ?: return
        editorSession.colorTarget = target
        editorSession.floatingColorSnapshot = ReadFloatingColorSnapshot.capture(target.appearance, target.mode)
        val initialColor = target.appearance.readFloatingSeedForMode(target.mode).takeIf { it != 0 }
            ?: ReadDrawerStyle.themeSnapshotForMode(
                requireContext(), target.appearance, target.mode,
            ).colors.surfaceTint
        screenState = state.copy(
            colorPreviewTarget = target,
            editorInitialColor = initialColor,
        )
        page = ReadStylePage.FLOATING_COLOR
    }

    private fun applyEditorFloatingColor(color: Int) {
        val target = editorSession.colorTarget ?: return
        applyFloatingColor(color, target)
    }

    private fun restoreFloatingColorSnapshot(): Boolean {
        if (ReadBookConfig.floatingColorManagedGlobally) return false
        val target = editorSession.colorTarget ?: return false
        val snapshot = editorSession.floatingColorSnapshot ?: return false
        if (!isSameBook(target)) return false
        snapshot.restore(target.appearance)
        updateFloatingColorUi(target)
        notifyFloatingAppearanceChanged(target)
        return true
    }

    private fun applyFloatingColor(color: Int, target: ReadStyleEditTarget) {
        if (ReadBookConfig.floatingColorManagedGlobally) return
        if (!isSameBook(target)) return
        target.appearance.setReadFloatingSeedForMode(color, target.mode)
        updateFloatingColorUi(target)
        notifyFloatingAppearanceChanged(target)
    }

    private fun updateFloatingColorUi(target: ReadStyleEditTarget) {
        val effectiveColor = ReadBookConfig.effectiveReadFloatingColor(target.appearance, target.mode)
        updateEditorState {
            copy(
                editorFloatingColorSeed = effectiveColor.seed,
                editorFloatingColorIsCustom = !effectiveColor.followsApplication,
                floatingDayColor = if (target.mode == ReadAppearanceMode.DAY) effectiveColor else floatingDayColor,
                floatingNightColor = if (target.mode == ReadAppearanceMode.NIGHT) effectiveColor else floatingNightColor,
            )
        }
    }

    private fun notifyFloatingAppearanceChanged(target: ReadStyleEditTarget? = null) {
        if (target != null && !isActiveAppearanceTarget(target)) return
        ReadFloatingAppearanceState.refreshFromConfig()
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        callBack?.let(ReadAloudMiniPlayer::refreshAppearance)
    }

    private fun loadEditorBackgrounds(): List<ReadStyleBackgroundUi> {
        if (editorBackgroundOwner !== ReadBookConfig.durConfig) {
            editorBackgroundOwner = ReadBookConfig.durConfig
            editorBackgroundCache = null
        }
        editorBackgroundCache?.let { return it }
        val customBackgrounds = linkedMapOf<String, String>()
        fun addCustomBackground(reference: String) {
            val path = resolveCustomBackgroundPath(reference)
            val file = File(path)
            if (!file.isFile) return
            val key = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            customBackgrounds.putIfAbsent(key, reference)
        }
        val config = ReadBookConfig.durConfig
        ReadAppearanceMode.entries.forEach { mode ->
            if (config.backgroundTypeForMode(mode) == 2) addCustomBackground(config.backgroundForMode(mode))
        }
        ReadBookConfig.getAllPicBgStr().forEach(::addCustomBackground)
        io.legado.app.help.resource.ResourceFiles.backgroundFiles()
            .forEach { addCustomBackground(it.absolutePath) }

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
        val ordered = selectableBuiltInBackgroundNames(names)
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

    private fun setBackgroundFromUri(uri: Uri, target: ReadStyleEditTarget) {
        if (!isSameBook(target)) { editorSession.importingBackground = false; return }
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                val fileName = withContext(Dispatchers.IO) { ReadingBackgroundLibrary.importImage(context, uri) }
                if (isSameBook(target)) {
                    target.appearance.setBackgroundForMode(2, fileName, target.mode)
                    editorBackgroundCache = null
                    postEditorBackgroundChanged(target)
                    refreshUi(reusePresetThumbnails = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                toastOnUi(error.localizedMessage.orEmpty())
            } finally {
                editorSession.importingBackground = false
            }
        }
    }

    private fun showFontWeightSetting(host: ReadBookActivity, onDismiss: () -> Unit) {
        val weights = (100..900 step 100).toList()
        val descriptions = host.resources.getStringArray(R.array.text_font_weight_levels).toList()
        ReadTypographySettingDialog.showDiscrete(
            context = host,
            avoidView = null,
            title = host.getString(R.string.text_font_weight_converter),
            stepLabels = weights.map(Int::toString),
            currentValues = descriptions,
            selectedIndex = weights.indexOf(resolveFontWeight(ReadBookConfig.textBold))
                .coerceAtLeast(0),
            useNgSlider = true,
            onDismiss = onDismiss,
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

    private fun showParagraphIndentSetting(host: ReadBookActivity, onDismiss: () -> Unit) {
        ReadTypographySettingDialog.showDiscrete(
            context = host,
            avoidView = null,
            title = host.getString(R.string.paragraph_first_line_indent),
            stepLabels = host.resources.getStringArray(R.array.indent_short).toList(),
            currentValues = host.resources.getStringArray(R.array.indent).toList(),
            selectedIndex = paragraphIndentLevel(),
            useNgSlider = true,
            currentValueTextSizeSp = 24f,
            onDismiss = onDismiss,
        ) { index ->
            ReadBookConfig.paragraphIndent = "　".repeat(index)
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun showChineseConverterSetting(host: ReadBookActivity, onDismiss: () -> Unit) {
        ReadTypographySettingDialog.showChineseConverter(
            context = host,
            avoidView = null,
            title = host.getString(R.string.chinese_converter),
            labels = host.resources.getStringArray(R.array.chinese_mode_short).toList(),
            selectedIndex = AppConfig.chineseConverterType,
            onDismiss = onDismiss,
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

    private fun deleteCurrentStyle() = deleteStyle(ReadBookConfig.styleSelect)

    private fun deleteStyle(index: Int) {
        if (ReadBookConfig.onlyThisBook) {
            if (index != -1) return
            ReadBookConfig.setOnlyThisBook(false)
            editorBackgroundCache = null
            ReadFloatingAppearanceState.refreshFromConfig()
            refreshUi()
            notifyPresetRestored()
            return
        }
        val target = ReadBookConfig.configList.getOrNull(index) ?: return
        val name = target.name.ifBlank { getString(R.string.text) }
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete),
            message = getString(R.string.sure_del_any, name),
            confirmLabel = getString(R.string.ok),
            cancelLabel = getString(R.string.cancel),
            onConfirm = {
                val currentIndex = ReadBookConfig.configList.indexOfFirst { it === target }
                if (currentIndex < 0) return@showReadConfirmDialog
                if (ReadBookConfig.deleteStyle(currentIndex)) {
                    ReadBookConfig.save()
                    editorBackgroundCache = null
                    refreshUi()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 13))
                    notifyFloatingAppearanceChanged()
                } else {
                    toastOnUi(R.string.read_style_keep_one_preset)
                }
            },
        )
    }

    private fun confirmRestoreCurrentPreset() = confirmRestorePreset(ReadBookConfig.styleSelect)

    private fun confirmRestorePreset(index: Int) {
        if (!ReadBookConfig.hasDefaultForPreset(index)) {
            toastOnUi(R.string.read_style_restore_unavailable)
            return
        }
        val onlyThisBook = ReadBookConfig.onlyThisBook
        val target = if (onlyThisBook && index == -1) ReadBookConfig.durConfig
            else ReadBookConfig.configList.getOrNull(index) ?: return
        val ownerUrl = ReadBook.book?.bookUrl
        showReadConfirmDialog(
            context = requireContext(),
            title = "恢复默认",
            message = "将「${target.name.ifBlank { getString(R.string.text) }}」恢复为默认配置？",
            confirmLabel = getString(R.string.yes),
            cancelLabel = getString(R.string.no),
            onConfirm = {
                if (ownerUrl != ReadBook.book?.bookUrl || onlyThisBook != ReadBookConfig.onlyThisBook) return@showReadConfirmDialog
                val currentIndex = if (onlyThisBook) {
                    if (ReadBookConfig.durConfig === target) -1 else return@showReadConfirmDialog
                } else ReadBookConfig.configList.indexOfFirst { it === target }
                if (!onlyThisBook && currentIndex < 0) return@showReadConfirmDialog
                val affectsCurrent = currentIndex == ReadBookConfig.styleSelect
                if (ReadBookConfig.restorePresetDefault(currentIndex)) {
                    editorBackgroundCache = null
                    refreshUi()
                    if (affectsCurrent) {
                        ReadFloatingAppearanceState.refreshFromConfig()
                        notifyPresetRestored()
                    }
                    toastOnUi("已恢复默认配置")
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
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 13))
        notifyFloatingAppearanceChanged()
    }

    private fun applyHighlightRules(rules: List<ReadHighlightRule>) {
        ReadHighlightRuleStore.replace(
            rules.mapIndexed { index, rule -> rule.copy(position = index) }
        )
        refreshUi()
        postEvent(EventBus.UP_CONFIG, arrayListOf(13))
    }

    private fun beginHighlightSelection() {
        if (currentRules().isEmpty()) {
            toastOnUi(R.string.empty)
            return
        }
        highlightSelectionMode = HighlightSelectionMode.MULTI
        selectedHighlightIds = emptySet()
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

    private fun confirmHighlightSelection(action: HighlightBatchAction) {
        if (highlightSelectionMode != HighlightSelectionMode.MULTI) return
        val selectedRules = currentRules().filter { it.id in selectedHighlightIds }
        if (selectedRules.isEmpty()) return
        when (action) {
            HighlightBatchAction.EXPORT -> {
                clearHighlightSelection(refresh = false)
                refreshUi()
                prepareHighlightExport(selectedRules)
            }

            HighlightBatchAction.DELETE -> {
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

        }
    }

    private fun openHighlightEditor(position: Int? = null) {
        highlightReturnPage = ReadStylePage.HIGHLIGHT
        val oldRule = position?.let(currentRules()::getOrNull)
        editingHighlightIndex = position
        if (editorSession.highlightMode == null) highlightColorMode = if (ReadBookConfig.isNightTheme) 1 else 0
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
        highlightReturnPage = ReadStylePage.HIGHLIGHT
        val rules = currentRules()
        val source = rules.firstOrNull { it.id == id } ?: return
        editingHighlightIndex = null
        if (editorSession.highlightMode == null) highlightColorMode = if (ReadBookConfig.isNightTheme) 1 else 0
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
        highlightColorInitialDraft = null
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
        page = highlightReturnPage
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
                page = highlightReturnPage
                applyHighlightRules(updated)
            },
        )
    }

    private fun confirmDeleteHighlight(id: String) {
        val rule = currentRules().firstOrNull { it.id == id } ?: return
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete),
            message = getString(R.string.sure_del_any, rule.name.ifBlank { rule.pattern }),
            confirmLabel = getString(R.string.delete),
            cancelLabel = getString(R.string.cancel),
            onConfirm = { applyHighlightRules(currentRules().filterNot { it.id == id }) },
        )
    }

    private fun confirmRestoreHighlight(id: String) {
        val defaultRule = DefaultData.readHighlightRules.firstOrNull { it.id == id } ?: return
        val currentRule = currentRules().firstOrNull { it.id == id } ?: return
        showReadConfirmDialog(
            context = requireContext(),
            title = getString(R.string.restore_default),
            message = getString(R.string.read_highlight_restore_one_confirm, currentRule.name.ifBlank { defaultRule.name }),
            confirmLabel = getString(R.string.menu_restore),
            cancelLabel = getString(R.string.cancel),
            onConfirm = {
                if (currentRules().any { it.id == id }) {
                    val result = ReadHighlightRuleStore.restoreBuiltIn(listOf(defaultRule))
                    refreshUi()
                    if (result.addedCount > 0 || result.updatedCount > 0) {
                        postEvent(EventBus.UP_CONFIG, arrayListOf(13))
                    }
                    toastOnUi(R.string.read_highlight_restore_one_done)
                }
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
            ReadBookConfig.styleSelect = appendResult.index
            editorBackgroundCache = null
            refreshUi()
            postEvent(
                EventBus.UP_CONFIG,
                if (appendResult.highlightRuleMerge == null) {
                    arrayListOf(0, 1, 2, 5, 13)
                } else {
                    arrayListOf(0, 1, 2, 5, 8, 13)
                },
            )
            notifyFloatingAppearanceChanged()
            val messages = result.warnings.toMutableList().apply {
                if (result.readerSettings != null) add("已忽略预设包中的全局阅读设置")
                appendResult.warning?.let(::add)
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

    private fun preparePresetExport(index: Int? = null) {
        if (preparingPresetExport || pendingPresetExportName != null) {
            toastOnUi("已有阅读预设导出任务，请稍候")
            return
        }
        preparingPresetExport = true
        val snapshot = if (index == null) ReadBookConfig.getExportConfig()
            else ReadBookConfig.getExportConfig(index) ?: run { preparingPresetExport = false; return }
        val exportFileName = currentExportFileName(snapshot.name)
        val packageFile = File(requireContext().filesDir, ".pending-read-style-${UUID.randomUUID()}.zip")
        execute {
            try {
                packageFile.outputStream().use { output ->
                    ReadStylePackageManager.export(snapshot, output)
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

    private fun currentExportFileName(name: String): String {
        val presetName = name.normalizeFileName()
        return if (presetName.isBlank()) configFileName else "$presetName.zip"
    }

}

private fun ReadStylePage.isPresetColorEditorPage(): Boolean = when (this) {
    ReadStylePage.EDIT_TEXT_COLOR,
    ReadStylePage.EDIT_BACKGROUND_COLOR,
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

@Composable
private fun rememberColorDrawerViewportHeight(view: View): Dp {
    var visibleHeightPx by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val frame = Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            // WRAP_CONTENT 会先按屏幕高度试测。使用窗口可见区域的高度，
            // 避免试测把颜色列表的滚动上限临时缩到零。
            view.getWindowVisibleDisplayFrame(frame)
            visibleHeightPx = frame.height().coerceAtLeast(0)
        }
        val observer = view.viewTreeObserver
        observer.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }
    return if (visibleHeightPx > 0) with(LocalDensity.current) { visibleHeightPx.toDp() } else Dp.Infinity
}

private fun ReadStylePage.isEditorPage(): Boolean =
    this == ReadStylePage.EDIT || isPresetColorEditorPage() ||
        this == ReadStylePage.EDIT_UNDERLINE || this == ReadStylePage.EDIT_UNDERLINE_COLOR
