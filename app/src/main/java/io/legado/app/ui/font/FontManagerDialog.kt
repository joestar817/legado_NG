package io.legado.app.ui.font

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.help.font.FontBatchDeletion
import io.legado.app.help.font.FontDirectoryCatalog
import io.legado.app.help.font.FontFileEntry
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.help.font.FontUsageInspector
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.LocalNgDialogColorMode
import io.legado.app.ui.design.components.compose.LocalNgDialogContainerColor
import io.legado.app.ui.design.components.compose.NgDialogColorMode
import io.legado.app.ui.design.components.compose.NgDialogSurface
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared file management only; each font setting keeps ownership of selection and application. */
@Composable
internal fun FontManagerDialog(
    onDismissRequest: () -> Unit,
    onChanged: () -> Unit = {},
    colorMode: NgDialogColorMode = NgDialogColorMode.STANDARD,
    containerColor: Color? = null,
) {
    CompositionLocalProvider(
        LocalNgDialogColorMode provides colorMode,
        LocalNgDialogContainerColor provides containerColor,
    ) {
        FontManagerDialogBody(onDismissRequest, onChanged)
    }
}

@Composable
private fun FontManagerDialogBody(onDismissRequest: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by FontLibraryStore.revision.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var forceFingerprintRefresh by remember { mutableStateOf(true) }
    var previewRevision by remember { mutableIntStateOf(0) }
    var assets by remember { mutableStateOf(emptyList<FontFileEntry>()) }
    var usages by remember { mutableStateOf(emptyMap<String, List<String>>()) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var directoryWarning by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<List<FontFileEntry>?>(null) }
    var information by remember { mutableStateOf<Pair<String, String>?>(null) }

    val selectableIds = remember(assets, usages, loadError) {
        if (loadError != null) emptySet() else assets.asSequence()
            .filter { it.canDelete && usages[it.id].isNullOrEmpty() }
            .map { it.id }.toSet()
    }
    val selectedIds = remember(selected) { selected.toSet() }

    LaunchedEffect(revision, refresh, busy) {
        if (busy) return@LaunchedEffect
        loading = true
        val forceRefresh = forceFingerprintRefresh
        try {
            val result = withContext(Dispatchers.IO) {
                // Enumeration stays live; unchanged files need not be rehashed after every delete.
                // The deletion API independently rechecks the target's bytes and usage.
                val snapshot = FontDirectoryCatalog.scan(refresh = forceRefresh)
                snapshot to runCatching {
                    FontUsageInspector.usagesByPaths(snapshot.entries.associate { it.id to listOf(it.uri) })
                }.onFailure { if (it is CancellationException) throw it }
            }
            assets = result.first.entries
            forceFingerprintRefresh = false
            usages = result.second.getOrDefault(emptyMap())
            loadError = result.second.exceptionOrNull()?.let { it.localizedMessage ?: "无法检查字体使用情况" }
            val remaining = assets.asSequence().filter {
                result.second.isSuccess && it.canDelete && usages[it.id].isNullOrEmpty()
            }.map { it.id }.toSet()
            selected = selected.filter { it in remaining }
            previewRevision++
            val warning = result.first.errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
            if (warning != null && warning != directoryWarning) context.toastOnUi(warning)
            directoryWarning = warning
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadError = error.localizedMessage ?: "字体列表读取失败"
        } finally {
            loading = false
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // Keep empty/loading states compact; grow with rows up to the previous six-row height.
    val preferredHeight = if (assets.isEmpty()) 200.dp else (assets.size.coerceAtMost(6) * 72 + 136).dp
    val maxHeight = 568.dp.coerceAtMost(screenHeight * .80f)
    Dialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
        ),
    ) {
        NgDialogSurface(
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp)
                .fillMaxWidth().height(preferredHeight.coerceAtMost(maxHeight)),
        ) {
            FontManagerDialogContent(
                assets = assets,
                usages = usages,
                selectedIds = selectedIds,
                selectableIds = selectableIds,
                loading = loading,
                busy = busy,
                loadError = loadError,
                previewRevision = previewRevision,
                onToggle = { id ->
                    if (!busy && !loading && loadError == null && id in selectableIds) {
                        selected = if (id in selectedIds) selected - id else selected + id
                    }
                },
                onSelectAll = {
                    if (!busy && !loading && loadError == null) {
                        selected = if (selectedIds.containsAll(selectableIds)) emptyList() else selectableIds.toList()
                    }
                },
                onDelete = {
                    if (!busy && !loading && loadError == null) {
                        pendingDelete = assets.filter { it.id in selectedIds && it.id in selectableIds }
                            .takeIf { it.isNotEmpty() }
                    }
                },
                onRetry = {
                    if (!busy && !loading) {
                        forceFingerprintRefresh = true
                        refresh++
                    }
                },
                onDismiss = { if (!busy) onDismissRequest() },
                onInformation = { asset ->
                    if (!busy) {
                        val usage = usages[asset.id].orEmpty()
                        val status = when {
                            loading -> "正在检查使用情况"
                            loadError != null -> "使用情况未确认"
                            usage.isNotEmpty() -> "正在用于：\n${usage.joinToString("\n")}"
                            !asset.canDelete -> asset.deleteUnavailableReason ?: "此位置没有删除权限，请重新授权目录。"
                            else -> "当前未使用"
                        }
                        information = asset.name to "$status\n\n${asset.id}"
                    }
                },
            )
        }
    }

    pendingDelete?.let { entries ->
        Dialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        ) {
            NgDialog(
                title = "确认删除 ${entries.size} 个字体",
                modifier = Modifier.widthIn(max = 300.dp),
                variant = NgDialogVariant.SIMPLE_CONFIRMATION,
                titleFontWeight = FontWeight.Medium,
                actions = {
                    NgDialogTextActionButton("取消", { pendingDelete = null }, enabled = !busy,
                        secondary = true, modifier = Modifier.weight(1f))
                    NgDialogTextActionButton(if (busy) "删除中" else "确认", {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val result = FontBatchDeletion.delete(entries, onDeleted = { deleted ->
                                        selected = selected - deleted.id
                                        assets = assets.filterNot { it.id == deleted.id }
                                    })
                                    pendingDelete = null
                                    if (result.failures.isEmpty()) {
                                        context.toastOnUi("已删除 ${result.deleted.size} 个字体")
                                    } else {
                                        val summary = if (result.deleted.isEmpty()) "未能删除" else
                                            "已删除 ${result.deleted.size} 个，${result.failures.size} 个未删除"
                                        information = summary to result.failures.joinToString("\n\n") {
                                            "${it.entry.name}\n${it.reason}\n${it.entry.id}"
                                        }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } finally {
                                    busy = false
                                    refresh++
                                    onChanged()
                                }
                            }
                        }
                    }, danger = true, enabled = !busy, modifier = Modifier.weight(1f))
                },
            ) {}
        }
    }
    information?.let { (title, message) ->
        Dialog(onDismissRequest = { information = null }) {
            NgDialog(title = title, actions = { NgDialogTextActionButton("知道了", { information = null }) }) {
                Text(message, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    color = Color(NgTheme.colors.onSurface), fontSize = 15.sp)
            }
        }
    }
}
