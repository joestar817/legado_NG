package io.legado.app.ui.font

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.help.font.FontDirectoryCatalog
import io.legado.app.help.font.FontFileEntry
import io.legado.app.help.font.FontUsageInspector
import io.legado.app.help.font.FontUsageReference
import io.legado.app.help.font.SYSTEM_DEFAULT_FONT
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.book.read.config.ReadConfigDialogTitle
import io.legado.app.ui.book.read.config.returnToReadStyleAdjustOnDismiss
import io.legado.app.ui.book.read.config.ReadConfigDock
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.compose.NgDialogColorMode
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.FileDoc
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val READER_FONT_DIALOG_MARGIN_DP = 20

internal data class ReaderFontFile(val entry: FontFileEntry?, val document: FileDoc)

internal fun readerFontFiles(
    entries: List<FontFileEntry>,
    selected: String,
    preferred: String?,
): List<ReaderFontFile> {
    val selectedIdentity = FontUsageInspector.normalizePath(selected)
    val listed = FontDirectoryCatalog.prioritize(entries, preferred).map { entry ->
        val reference = if (entry.id == selectedIdentity) selected else entry.uri
        val document = FileDoc(entry.name, false, entry.size, 0L, readerFontUri(reference))
        ReaderFontFile(entry, document)
    }
    if (selectedIdentity == null || entries.any { it.id == selectedIdentity }) return listed

    // Imported presets can use a font directly from their package, outside the library.
    val uri = readerFontUri(selected)
    if (uri.scheme != "file") return listed
    val file = File(selectedIdentity)
    if (!file.isFile || !file.canRead()) return listed
    val current = ReaderFontFile(
        entry = null,
        document = FileDoc(file.nameWithoutExtension, false, file.length(), file.lastModified(), uri),
    )
    return if (preferred == null) listOf(current) + listed else listed + current
}

private fun readerFontUri(reference: String): Uri =
    if (reference.startsWith('/')) Uri.fromFile(File(reference)) else Uri.parse(reference)

/** 字体选择与单项删除共用一个弹窗。 */
class FontSelectDialog : BaseComposeDialogFragment() {

    companion object {
        private const val MODE_SYSTEM = 0
        private const val MODE_CUSTOM = 1
        private const val ARG_TITLE_FONT = "titleFont"
        private const val ARG_READER_BODY_FONT = "readerBodyFont"
        private const val ARG_READER_TITLE_FONT = "readerTitleFont"
        private const val ARG_PREFERRED_FONT_PATH = "preferredFontPath"

        fun forTitle(): FontSelectDialog = FontSelectDialog().apply {
            arguments = bundleOf(ARG_TITLE_FONT to true)
        }

        fun forReaderBody(preferredFontPath: String? = null): FontSelectDialog = FontSelectDialog().apply {
            arguments = bundleOf(ARG_READER_BODY_FONT to true, ARG_PREFERRED_FONT_PATH to preferredFontPath)
        }

        fun forReaderTitle(preferredFontPath: String? = null): FontSelectDialog = FontSelectDialog().apply {
            arguments = bundleOf(
                ARG_TITLE_FONT to true,
                ARG_READER_TITLE_FONT to true,
                ARG_PREFERRED_FONT_PATH to preferredFontPath,
            )
        }
    }

    private val isTitleFont: Boolean
        get() = arguments?.getBoolean(ARG_TITLE_FONT) == true

    private val isReaderBodyFont: Boolean
        get() = arguments?.getBoolean(ARG_READER_BODY_FONT) == true

    private val isReaderTitleFont: Boolean
        get() = arguments?.getBoolean(ARG_READER_TITLE_FONT) == true

    private val preferredFontPath: String?
        get() = arguments?.getString(ARG_PREFERRED_FONT_PATH)

    private lateinit var composeView: ComposeView
    private var fontMode by mutableIntStateOf(MODE_SYSTEM)
    private var customFonts by mutableStateOf<List<ReaderFontFile>>(emptyList())
    private var selectedFontPath by mutableStateOf("")
    private var selectedSystemFont by mutableIntStateOf(0)
    private var readerPreviewHost: ReadBookActivity? = null
    private var fontListRequest = 0
    private var loadingFontFiles by mutableStateOf(false)
    private var fontPreviewRevision by mutableIntStateOf(0)

    private val selectFontDir = registerForActivityResult(SelectDirectoryContract()) { result ->
        result.uri?.let {
            putPrefString(PreferKey.fontFolder, it.toString())
            fontMode = MODE_CUSTOM
            if (it.isContentScheme()) {
                kotlin.runCatching {
                    requireContext().contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onFailure { error ->
                    AppLog.put("保存字体目录读取权限失败\n${error.localizedMessage}", error)
                }
            }
            FontLibraryStore.notifyChanged()
            loadConfiguredFonts()
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(
            marginDp = READER_FONT_DIALOG_MARGIN_DP,
            height = 360.dpToPx(),
            dimAmount = 0.4f,
        )
        if (isReaderBodyFont || isTitleFont) {
            dialog?.window?.apply {
                setGravity(Gravity.CENTER)
                attributes = attributes.apply { x = 0; y = 0 }
            }
        } else {
            parentFragment?.view?.let {
                ReadDrawerStyle.positionDialogAbove(dialog, it)
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if ((isReaderBodyFont || isReaderTitleFont) && readerPreviewHost == null) {
            (activity as? ReadBookActivity)?.let {
                it.beginReadConfigPreview()
                readerPreviewHost = it
            }
        }
        composeView = view as ComposeView
        composeView.setBackgroundColor(AndroidColor.TRANSPARENT)
        selectedFontPath = currentFontPath()
        selectedSystemFont = AppConfig.systemTypefaces
        fontMode = if (preferredFontPath == null &&
            (selectedFontPath.isEmpty() || selectedFontPath == SYSTEM_DEFAULT_FONT)
        ) MODE_SYSTEM else MODE_CUSTOM
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NgAppTheme(
                snapshot = ReadDrawerStyle.themeSnapshot(requireContext()),
                updateSystemBars = false,
            ) {
                FontSelectContent()
            }
        }
        loadConfiguredFonts()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (isReaderBodyFont || isReaderTitleFont) ReadBookConfig.save()
        endReaderPreview()
        super.onDismiss(dialog)
        returnToReadStyleAdjustOnDismiss()
    }

    override fun onDestroyView() {
        endReaderPreview()
        super.onDestroyView()
    }

    private fun endReaderPreview() {
        readerPreviewHost?.let {
            readerPreviewHost = null
            it.endReadConfigPreview()
        }
    }

    @Composable
    private fun FontSelectContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val libraryRevision by FontLibraryStore.revision.collectAsState()
        var observedLibraryRevision by remember { mutableStateOf(libraryRevision) }
        var fontManager by rememberSaveable { mutableStateOf(false) }
        var returnToFontMode by rememberSaveable { mutableStateOf<Int?>(null) }
        val deletionMode = returnToFontMode != null
        val visibleFonts = remember(customFonts, deletionMode) {
            if (deletionMode) customFonts.filter { it.entry != null } else customFonts
        }
        var pendingDeletion by remember { mutableStateOf<FontDeletionRequest?>(null) }
        var deletionBusy by remember { mutableStateOf(false) }
        val exitDeletionMode: () -> Unit = {
            if (!deletionBusy && pendingDeletion == null) {
                returnToFontMode?.let { fontMode = it }
                returnToFontMode = null
            }
        }
        BackHandler(enabled = deletionMode, onBack = exitDeletionMode)

        fun requestDeletion(file: FontFileEntry) {
            if (returnToFontMode == null || !file.canDelete || loadingFontFiles ||
                deletionBusy || pendingDeletion != null
            ) return
            deletionBusy = true
            scope.launch {
                try {
                    val usages = withContext(Dispatchers.IO) {
                        FontUsageInspector.deletionUsageReferences(listOf(file.uri))
                    }
                    pendingDeletion = FontDeletionRequest(file, usages)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    context.toastOnUi(failure.localizedMessage ?: "无法检查字体使用情况")
                } finally {
                    deletionBusy = false
                }
            }
        }

        LaunchedEffect(libraryRevision, fontManager, deletionBusy) {
            if (fontManager || deletionBusy) return@LaunchedEffect
            if (observedLibraryRevision != libraryRevision) {
                observedLibraryRevision = libraryRevision
                loadConfiguredFonts()
            }
        }
        val systemFonts = resources.getStringArray(R.array.system_typefaces)
        val systemNames = if (isTitleFont) {
            listOf(getString(R.string.read_title_follow_body_font), systemFonts.first())
        } else {
            systemFonts.toList()
        }
        val fontTitle = getString(if (isTitleFont) R.string.read_title_font else R.string.body_font)
        ReadConfigDialogSurface(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 12.dp,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (deletionMode) {
                    FontHeaderAction(
                        iconRes = R.drawable.ic_arrow_back,
                        description = stringResource(R.string.back),
                        enabled = !deletionBusy,
                        onClick = exitDeletionMode,
                    )
                    ReadConfigDialogTitle(stringResource(R.string.font_delete_mode_title), Modifier.weight(1f))
                    Spacer(Modifier.size(44.dp))
                } else {
                    FontHeaderAction(
                        iconRes = R.drawable.ic_create_folder_outline,
                        description = getString(R.string.add_font_directory),
                        enabled = fontMode == MODE_CUSTOM,
                        onClick = ::openFolder,
                    )
                    ReadConfigDialogTitle(fontTitle, Modifier.weight(1f))
                    FontHeaderAction(
                        iconRes = R.drawable.ic_settings,
                        description = "字体管理",
                        onClick = {
                            if (isReaderBodyFont || isTitleFont) {
                                returnToFontMode = fontMode
                            } else {
                                // Invalidate an earlier list request; the manager owns scanning until closed.
                                fontListRequest++
                                loadingFontFiles = true
                                fontManager = true
                            }
                        },
                    )
                }
            }
            if (!deletionMode) {
                Spacer(Modifier.height(8.dp))
                ReadConfigDock(
                    labels = listOf(
                        getString(R.string.font_mode_system),
                        getString(R.string.font_mode_custom),
                    ),
                    selectedIndex = fontMode,
                    onSelected = { fontMode = it },
                    accessibilityLabel = fontTitle,
                )
            }
            Spacer(Modifier.height(if (deletionMode) 12.dp else 8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (fontMode == MODE_SYSTEM && !deletionMode) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().selectableGroup(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(systemNames) { index, name ->
                            val typeface = when {
                                isTitleFont -> Typeface.DEFAULT
                                index == 1 -> Typeface.SERIF
                                index == 2 -> Typeface.MONOSPACE
                                else -> Typeface.SANS_SERIF
                            }
                            val selected = when (selectedFontPath) {
                                SYSTEM_DEFAULT_FONT -> index == if (isTitleFont) 1 else 0
                                "" -> index == if (isTitleFont) 0 else selectedSystemFont
                                else -> false
                            }
                            FontRow(
                                name = name,
                                typeface = typeface,
                                selected = selected,
                                onClick = { selectSystemFont(index) },
                            )
                        }
                    }
                } else if (visibleFonts.isEmpty()) {
                    Text(
                        text = stringResource(
                            when {
                                !deletionMode -> R.string.font_folder_empty
                                loadingFontFiles -> R.string.loading
                                else -> R.string.font_delete_mode_empty
                            },
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier = if (deletionMode) Modifier.fillMaxSize() else Modifier.fillMaxSize().selectableGroup(),
                        contentPadding = PaddingValues(vertical = if (deletionMode) 0.dp else 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = visibleFonts,
                            key = { _, item -> item.document.uri.toString() },
                        ) { _, item ->
                            val document = item.document
                            val typeface by produceState<Typeface?>(
                                null, document.uri, fontPreviewRevision, loadingFontFiles, fontManager,
                            ) {
                                if (loadingFontFiles || fontManager) return@produceState
                                value = loadTypeface(document)
                            }
                            val deletionEntry = if (deletionMode) item.entry else null
                            val onDelete: (() -> Unit)? = deletionEntry?.let { { requestDeletion(it) } }
                            FontRow(
                                name = document.name,
                                typeface = typeface,
                                selected = document.toString() == selectedFontPath || document.uri.toString() == selectedFontPath,
                                onClick = { selectCustomFont(document) },
                                enabled = !loadingFontFiles && !fontManager &&
                                    (!deletionMode || (deletionEntry?.canDelete == true && !deletionBusy)),
                                onDelete = onDelete,
                            )
                        }
                    }
                }
            }
        }
        pendingDeletion?.let { deletion ->
            FontDeletionConfirmDialog(
                usages = deletion.usages,
                busy = deletionBusy,
                style = FontDeletionConfirmStyle.READER,
                onDismissRequest = { pendingDeletion = null },
                onConfirm = {
                    if (!deletionBusy && pendingDeletion === deletion) {
                        deletionBusy = true
                        scope.launch {
                            try {
                                FontDirectoryCatalog.delete(
                                    deletion.file,
                                    confirmedUsages = deletion.usages.map(FontUsageReference::location),
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                context.toastOnUi(failure.localizedMessage ?: "字体删除失败")
                            } finally {
                                deletionBusy = false
                                pendingDeletion = null
                            }
                            val currentPath = currentFontPath()
                            if (currentPath != selectedFontPath && currentPath == SYSTEM_DEFAULT_FONT) {
                                returnToFontMode = MODE_SYSTEM
                            }
                            selectedFontPath = currentPath
                            observedLibraryRevision = FontLibraryStore.revision.value
                            loadConfiguredFonts()
                        }
                    }
                },
            )
        }
        if (fontManager) {
            FontManagerDialog(
                colorMode = NgDialogColorMode.INHERIT_HOST,
                containerColor = readFloatingGlassStyle().containerTop.copy(alpha = 1f),
                onDismissRequest = {
                    observedLibraryRevision = libraryRevision
                    fontManager = false
                    loadConfiguredFonts()
                },
            )
        }
    }

    @Composable
    private fun FontHeaderAction(
        @DrawableRes iconRes: Int,
        description: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ) {
        NgThemeSheetActionButton(
            onClick = onClick,
            contentDescription = description,
            enabled = enabled,
            containerColor = ReadDrawerStyle.dockSurfaceColor(alpha = .53f),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color(NgTheme.colors.secondary).copy(alpha = if (enabled) 1f else .38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    @Composable
    private fun FontRow(
        name: String,
        typeface: Typeface?,
        selected: Boolean,
        onClick: () -> Unit,
        enabled: Boolean = true,
        onDelete: (() -> Unit)? = null,
    ) {
        val colors = NgTheme.colors
        val accent = Color(colors.primary)
        val contentColor = Color(colors.onSurface)
        val surface = ReadDrawerStyle.dockSurfaceColor(alpha = .56f)
        val shape = RoundedCornerShape(12.dp)
        val fontFamily = typeface?.let(::FontFamily)
        val cardColor = if (selected) accent.copy(alpha = .10f).compositeOver(surface) else surface
        val outline = if (selected) Modifier.border(1.dp, accent.copy(alpha = .28f), shape) else Modifier
        val selection = if (onDelete == null) {
            Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
        } else Modifier
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(cardColor)
                .then(outline)
                .then(selection)
                .padding(start = 10.dp, top = 4.dp, end = if (onDelete == null) 12.dp else 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sampleBackground = if (selected) accent.copy(alpha = .12f) else contentColor.copy(alpha = .06f)
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                    .background(sampleBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    color = if (selected) accent else Color(colors.onSurfaceVariant),
                    fontFamily = fontFamily,
                    fontSize = 18.sp,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                Text(
                    text = name,
                    color = contentColor,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                )
                if (onDelete != null && selected) {
                    Text(
                        text = stringResource(R.string.ng_theme_in_use),
                        color = Color(colors.secondary),
                        fontSize = 12.sp,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bgm_menu_delete),
                        contentDescription = "${stringResource(R.string.delete)} $name",
                        tint = Color(colors.secondary).copy(alpha = if (enabled) 1f else .38f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ng_ic_popup_selected),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    }

    private fun loadConfiguredFonts() {
        loadFontFiles()
    }

    private fun openFolder() {
        if (fontMode != MODE_CUSTOM) return
        val initialUri = getPrefString(PreferKey.fontFolder)
            ?.takeIf { it.isContentScheme() }
            ?.let(Uri::parse)
        selectFontDir.launch(SelectDirectoryContract.Request(initialUri = initialUri))
    }

    private fun loadFontFiles() {
        val request = ++fontListRequest
        loadingFontFiles = true
        val selected = selectedFontPath
        execute(scope = viewLifecycleOwner.lifecycleScope) {
            readerFontFiles(FontDirectoryCatalog.list(), selected, preferredFontPath)
        }.onSuccess {
            if (request == fontListRequest) {
                // Publish the new rows before allowing previews to restart.
                customFonts = it
                fontPreviewRevision++
                loadingFontFiles = false
            }
        }.onError {
            if (request == fontListRequest) {
                customFonts = emptyList()
                loadingFontFiles = false
                AppLog.put("加载字体文件失败\n${it.localizedMessage}", it)
                toastOnUi("字体列表读取失败，请重新选择目录")
            }
        }
    }

    private suspend fun loadTypeface(item: FileDoc): Typeface? = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val typeface = if (item.isContentScheme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().contentResolver.openFileDescriptor(item.uri, "r")?.use {
                        Typeface.Builder(it.fileDescriptor).build()
                    }
                } else {
                    Typeface.createFromFile(RealPathUtil.getPath(requireContext(), item.uri))
                }
            } else {
                Typeface.createFromFile(item.uri.path!!)
            }
            currentCoroutineContext().ensureActive()
            typeface
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            // A passive preview may lose its source during deletion; keep diagnostics out of toasts.
            AppLog.put("读取字体 ${item.name} 出错\n${error.localizedMessage}", error)
            null
        }
    }

    private fun selectCustomFont(item: FileDoc) {
        selectedFontPath = item.toString()
        selectFont(selectedFontPath)
    }

    private fun selectSystemFont(index: Int) {
        selectedSystemFont = index
        selectedFontPath = if (isTitleFont && index == 1) SYSTEM_DEFAULT_FONT else ""
        if (!isTitleFont) AppConfig.systemTypefaces = index
        selectFont(selectedFontPath)
    }

    private fun selectFont(path: String) {
        if (isReaderBodyFont) {
            if (path != ReadBookConfig.textFont || path.isEmpty()) {
                ReadBookConfig.textFont = path
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
            }
        } else if (isReaderTitleFont) {
            if (path != ReadBookConfig.titleFont) {
                ReadBookConfig.titleFont = path
                ReadBookConfig.save()
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
            }
        } else {
            callBack?.selectFont(path)
        }
    }

    private fun currentFontPath(): String = when {
        isReaderBodyFont -> ReadBookConfig.textFont
        isReaderTitleFont -> ReadBookConfig.titleFont
        else -> callBack?.curFontPath.orEmpty()
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }
}
