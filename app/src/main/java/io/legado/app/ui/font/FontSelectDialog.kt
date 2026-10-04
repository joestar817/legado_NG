package io.legado.app.ui.font

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.book.read.config.ReadConfigDialogTitle
import io.legado.app.ui.book.read.config.ReadConfigDock
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.listFileDocs
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import java.io.File
import java.net.URLDecoder
import kotlin.math.roundToInt

/** 字体选择对话框。内容使用 Compose，目录读取与字体应用行为保持不变。 */
class FontSelectDialog : BaseComposeDialogFragment() {

    private companion object {
        const val MODE_SYSTEM = 0
        const val MODE_CUSTOM = 1
    }

    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private lateinit var composeView: ComposeView
    private var fontMode by mutableIntStateOf(MODE_SYSTEM)
    private var customFonts by mutableStateOf<List<FileDoc>>(emptyList())
    private var selectedFontPath by mutableStateOf("")
    private var selectedSystemFont by mutableIntStateOf(0)
    private var surfaceOffsetX by mutableIntStateOf(0)
    private var surfaceOffsetY by mutableIntStateOf(0)

    private val selectFontDir = registerForActivityResult(SelectDirectoryContract()) { result ->
        result.uri?.let {
            putPrefString(PreferKey.fontFolder, it.toString())
            if (it.isContentScheme()) {
                kotlin.runCatching {
                    requireContext().contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onFailure { error ->
                    AppLog.put("保存字体目录读取权限失败\n${error.localizedMessage}", error)
                }
                DocumentFile.fromTreeUri(requireContext(), it)?.let { doc ->
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } ?: loadFontFiles()
            } else {
                it.path?.let(::File)?.takeIf(File::canRead)?.let { directory ->
                    loadFontFiles(FileDoc.fromFile(directory))
                } ?: loadFontFiles()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(
            marginDp = 20,
            height = 360.dpToPx(),
            dimAmount = 0.4f,
        )
        parentFragment?.view?.let {
            ReadDrawerStyle.positionDialogAbove(dialog, it)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        composeView = view as ComposeView
        composeView.setBackgroundColor(AndroidColor.TRANSPARENT)
        selectedFontPath = callBack?.curFontPath.orEmpty()
        selectedSystemFont = AppConfig.systemTypefaces
        fontMode = if (selectedFontPath.isEmpty()) MODE_SYSTEM else MODE_CUSTOM
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

    @Composable
    private fun FontSelectContent() {
        val systemNames = resources.getStringArray(R.array.system_typefaces).toList()
        ReadConfigDialogSurface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(surfaceOffsetX, surfaceOffsetY) },
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
                    .height(16.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            surfaceOffsetX += dragAmount.x.roundToInt()
                            surfaceOffsetY += dragAmount.y.roundToInt()
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = getString(R.string.font_dialog_drag_hint),
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                    modifier = Modifier.size(28.dp, 14.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReadConfigDialogTitle(callBack?.fontTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.body_font))
            }
            Spacer(Modifier.height(8.dp))
            ReadConfigDock(
                labels = listOf(
                    getString(R.string.font_mode_system),
                    getString(R.string.font_mode_custom),
                ),
                selectedIndex = fontMode,
                onSelected = { fontMode = it },
                accessibilityLabel = getString(R.string.body_font),
            )
            Spacer(Modifier.height(8.dp))
            if (fontMode == MODE_CUSTOM) {
                FontDirectoryRow(onClick = ::openFolder)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (fontMode == MODE_SYSTEM) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        itemsIndexed(systemNames) { index, name ->
                            val typeface = when (index) {
                                1 -> Typeface.SERIF
                                2 -> Typeface.MONOSPACE
                                else -> Typeface.SANS_SERIF
                            }
                            FontRow(
                                name = name,
                                typeface = typeface,
                                selected = selectedFontPath.isEmpty() &&
                                    selectedSystemFont == index,
                                onClick = { selectSystemFont(index) },
                            )
                        }
                    }
                } else if (customFonts.isEmpty()) {
                    Text(
                        text = getString(R.string.font_folder_empty),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        itemsIndexed(
                            items = customFonts,
                            key = { _, item -> item.uri.toString() },
                        ) { _, item ->
                            val typeface = remember(item) { loadTypeface(item) }
                            FontRow(
                                name = item.name,
                                typeface = typeface,
                                selected = item.name == currentFontName(),
                                onClick = { selectCustomFont(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FontDirectoryRow(onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_create_folder_outline),
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = getString(R.string.add_font_directory),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurface),
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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_interface_setting),
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 16.sp,
                fontFamily = typeface?.let(::FontFamily),
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ng_ic_popup_selected),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary),
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        }
    }

    private fun loadConfiguredFonts() {
        val fontPath = getPrefString(PreferKey.fontFolder)
        if (fontPath.isNullOrEmpty()) {
            loadFontFiles()
        } else if (fontPath.isContentScheme()) {
            val doc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(fontPath))
            if (doc?.canRead() == true) {
                loadFontFiles(FileDoc.fromDocumentFile(doc))
            } else {
                loadFontFiles()
            }
        } else {
            File(fontPath).takeIf { it.canRead() }?.let {
                loadFontFiles(FileDoc.fromFile(it))
            } ?: loadFontFiles()
        }
    }

    private fun openFolder() {
        val initialUri = getPrefString(PreferKey.fontFolder)
            ?.takeIf { it.isContentScheme() }
            ?.let(Uri::parse)
        selectFontDir.launch(SelectDirectoryContract.Request(initialUri = initialUri))
    }

    private fun getLocalFonts(): ArrayList<FileDoc> {
        val path = FileUtils.getPath(requireContext().externalFiles, "font")
        return File(path).listFileDocs { it.name.matches(fontRegex) }
    }

    private fun loadFontFiles(fileDoc: FileDoc? = null) {
        execute {
            val fontItems = fileDoc?.list { it.name.matches(fontRegex) } ?: ArrayList()
            mergeFontItems(fontItems, getLocalFonts())
        }.onSuccess {
            customFonts = it
        }.onError {
            AppLog.put("加载字体文件失败\n${it.localizedMessage}", it)
            toastOnUi("getFontFiles:${it.localizedMessage}")
        }
    }

    private fun mergeFontItems(
        items1: ArrayList<FileDoc>,
        items2: ArrayList<FileDoc>,
    ): List<FileDoc> {
        val items = ArrayList(items1)
        items2.forEach { item2 ->
            if (items1.none { it.name == item2.name }) items.add(item2)
        }
        return items.sortedWith { first, second -> first.name.cnCompare(second.name) }
    }

    private fun loadTypeface(item: FileDoc): Typeface? = runCatching {
        if (item.isContentScheme) {
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
    }.onFailure {
        AppLog.put("读取字体 ${item.name} 出错\n${it.localizedMessage}", it, true)
    }.getOrNull()

    private fun selectCustomFont(item: FileDoc) {
        selectedFontPath = item.toString()
        callBack?.selectFont(selectedFontPath)
        dismissAllowingStateLoss()
    }

    private fun selectSystemFont(index: Int) {
        selectedSystemFont = index
        selectedFontPath = ""
        // 正文字体对话框：更新全局系统字体偏好并清空正文字体；
        // 脚本字体对话框：写入 system:index 标记，保持「该脚本用系统衬线/等宽」的选择。
        if (callBack?.isBodyFontDialog != false) {
            AppConfig.systemTypefaces = index
            callBack?.selectFont("")
        } else {
            callBack?.selectFont(ReadBookConfig.systemFontValue(index))
        }
        dismissAllowingStateLoss()
    }

    private fun currentFontName(): String? {
        if (selectedFontPath.isEmpty()) return null
        return runCatching { URLDecoder.decode(selectedFontPath, "utf-8") }
            .getOrNull()
            ?.substringAfterLast(File.separator)
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
        val fontTitle: String get() = ""
        val isBodyFontDialog: Boolean get() = true
    }
}
