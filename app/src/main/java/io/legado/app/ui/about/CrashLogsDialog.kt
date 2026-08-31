package io.legado.app.ui.about

import android.app.Application
import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.delete
import io.legado.app.utils.exportTextContent
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.isActive
import java.io.FileFilter

/** 崩溃日志列表抽屉；文件读取、导出和清理仍由原 ViewModel 持有。 */
class CrashLogsDialog : BottomSheetDialogFragment() {

    private val viewModel by viewModels<CrashViewModel>()
    private var logFiles by mutableStateOf<List<FileDoc>>(emptyList())
    private var loaded by mutableStateOf(false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                CrashLogsDrawerContent(
                    files = logFiles,
                    loaded = loaded,
                    onExportAll = ::exportAll,
                    onClear = viewModel::clearCrashLog,
                    onFileClick = ::showLogFile,
                )
            }
        }
        viewModel.logLiveData.observe(viewLifecycleOwner) {
            logFiles = it
            loaded = true
        }
        viewModel.initData()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isFitToContents = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示崩溃日志抽屉失败 tag:$tag", it) }
    }

    private fun exportAll() {
        val files = logFiles
        if (files.isEmpty()) {
            requireContext().toastOnUi(R.string.export_content_empty)
            return
        }
        viewModel.readFiles(files) {
            if (lifecycleScope.isActive) {
                requireContext().exportTextContent(it, filePrefix = "legado-crash-log")
            }
        }
    }

    private fun showLogFile(fileDoc: FileDoc) {
        viewModel.readFile(fileDoc) {
            if (lifecycleScope.isActive) {
                val content = formatLogContent(fileDoc.name, it)
                showDialogFragment(
                    CodeDialog(
                        code = content,
                        title = getString(R.string.crash_log),
                        highlightMode = CodeDialog.HighlightMode.DebugLog,
                        exportFilePrefix = "legado-crash-log",
                    )
                )
            }
        }
    }

    private fun formatLogContent(fileName: String, content: String): String = buildString {
        append("// ===== ").append(fileName).append(" =====\n")
        append(content)
    }

    class CrashViewModel(application: Application) : BaseViewModel(application) {

        val logLiveData = MutableLiveData<List<FileDoc>>()

        fun initData() {
            execute {
                val list = arrayListOf<FileDoc>()
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.listFiles(FileFilter { it.isFile })
                    ?.forEach {
                        list.add(FileDoc.fromFile(it))
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.list {
                            !it.isDir
                        }?.let {
                            list.addAll(it)
                        }
                }
                return@execute list.sortedByDescending { it.name }.distinctBy { it.name }
            }.onSuccess {
                logLiveData.postValue(it)
            }
        }

        fun readFile(fileDoc: FileDoc, success: (String) -> Unit) {
            execute {
                String(fileDoc.readBytes())
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
        }

        fun readFiles(fileDocs: List<FileDoc>, success: (String) -> Unit) {
            execute {
                fileDocs.joinToString("\n\n") { fileDoc ->
                    buildString {
                        append("// ===== ").append(fileDoc.name).append(" =====\n")
                        append(String(fileDoc.readBytes()))
                    }
                }
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
        }

        fun clearCrashLog() {
            execute {
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.let {
                        FileUtils.delete(it, false)
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.delete()
                }
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }.onFinally {
                initData()
            }
        }
    }

    private companion object {
        const val SHEET_HEIGHT_RATIO = 0.82f
    }
}

@Composable
private fun CrashLogsDrawerContent(
    files: List<FileDoc>,
    loaded: Boolean,
    onExportAll: () -> Unit,
    onClear: () -> Unit,
    onFileClick: (FileDoc) -> Unit,
) {
    val listState = rememberLazyListState()
    NgBottomDrawerSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.crash_log),
                secondaryTrailingActionText = stringResource(R.string.export_all)
                    .takeIf { files.isNotEmpty() },
                onSecondaryTrailingActionClick = onExportAll,
                trailingActionText = stringResource(R.string.clear),
                onTrailingActionClick = onClear,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = colorResource(R.color.ng_surface_panel),
                contentColor = colorResource(R.color.ng_on_surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    width = if (NgTheme.snapshot.isEInk) 1.dp else 0.6.dp,
                    color = colorResource(R.color.ng_card_stroke),
                ),
            ) {
                when {
                    !loaded -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(NgTheme.colors.primary),
                            strokeWidth = 2.5.dp,
                        )
                    }

                    files.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.empty),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 15.sp,
                        )
                    }

                    else -> Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(files, key = { it.name }) { file ->
                                CrashLogFileRow(
                                    fileName = file.name,
                                    onClick = { onFileClick(file) },
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                )
                            }
                        }
                        NgLazyListFastScroller(
                            state = listState,
                            itemCount = files.size,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashLogFileRow(
    fileName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = colorResource(R.color.ng_surface_card),
        contentColor = colorResource(R.color.ng_on_surface),
        shape = RoundedCornerShape(dimensionResource(R.dimen.ng_radius_m)),
        border = BorderStroke(0.8.dp, colorResource(R.color.ng_card_stroke)),
    ) {
        MiddleEllipsisText(
            text = fileName,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun MiddleEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(
        color = colorResource(R.color.ng_on_surface),
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth
        val displayedText = remember(text, availableWidth, style, density) {
            middleEllipsize(
                text = text,
                maxWidthPx = with(density) { availableWidth.roundToPx() },
                measureWidth = { candidate ->
                    textMeasurer.measure(
                        text = candidate,
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                    ).size.width
                },
            )
        }
        Text(
            text = displayedText,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun middleEllipsize(
    text: String,
    maxWidthPx: Int,
    measureWidth: (String) -> Int,
): String {
    if (maxWidthPx <= 0 || text.isEmpty() || measureWidth(text) <= maxWidthPx) return text
    val ellipsis = "…"
    if (measureWidth(ellipsis) > maxWidthPx) return ""
    val codePointCount = text.codePointCount(0, text.length)
    var low = 0
    var high = codePointCount
    var best = ellipsis
    while (low <= high) {
        val kept = (low + high) ushr 1
        val prefixLength = (kept + 1) / 2
        val suffixLength = kept / 2
        val prefixEnd = text.offsetByCodePoints(0, prefixLength)
        val suffixStart = text.offsetByCodePoints(text.length, -suffixLength)
        val candidate = buildString(prefixEnd + 1 + text.length - suffixStart) {
            append(text, 0, prefixEnd)
            append(ellipsis)
            append(text, suffixStart, text.length)
        }
        if (measureWidth(candidate) <= maxWidthPx) {
            best = candidate
            low = kept + 1
        } else {
            high = kept - 1
        }
    }
    return best
}
