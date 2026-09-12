package io.legado.app.ui.config

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

private data class InterfaceFontEntry(val uri: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeInterfaceFontEditorSheet(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val names = stringArrayResource(R.array.system_typefaces)
    var choice by rememberSaveable { mutableStateOf(NgInterfaceFontStore.choice(context)) }
    var chosenName by rememberSaveable { mutableStateOf(NgInterfaceFontStore.name(context)) }
    var customTab by rememberSaveable { mutableStateOf(choice !in NgInterfaceFontStore.systemChoices) }
    var directory by rememberSaveable {
        mutableStateOf(context.getPrefString(NgInterfaceFontStore.DIRECTORY)
            ?: context.getPrefString(PreferKey.fontFolder).orEmpty())
    }
    var entries by remember { mutableStateOf(emptyList<InterfaceFontEntry>()) }
    var loadingList by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Pair<Typeface, File?>?>(null) }
    var previewChoice by remember { mutableStateOf<String?>(null) }
    var loadRequest by rememberSaveable { mutableIntStateOf(0) }
    var previewRequest by remember { mutableStateOf<Int?>(null) }
    var refreshedTypefaces by remember { mutableStateOf<Map<String, Typeface>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(directory) {
        loadingList = true
        val result = withContext(Dispatchers.IO) { runCatching {
            val files = mutableListOf<InterfaceFontEntry>()
            if (directory.startsWith("content:")) {
                DocumentFile.fromTreeUri(context, Uri.parse(directory))?.listFiles()?.forEach {
                    if (it.isFile && it.name.orEmpty().matches(Regex("(?i).*\\.[ot]tf"))) {
                        files += InterfaceFontEntry(it.uri.toString(), it.name.orEmpty())
                    }
                }
            } else if (directory.isNotEmpty()) {
                val path = Uri.parse(directory).path ?: directory
                File(path).listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("ttf", "otf") }
                    ?.forEach { files += InterfaceFontEntry(it.toURI().toString(), it.name) }
            }
            File(context.externalFiles, "font").listFiles()?.filter {
                it.isFile && it.extension.lowercase() in listOf("ttf", "otf")
            }?.forEach { files += InterfaceFontEntry(it.toURI().toString(), it.name) }
            files.distinctBy { it.uri }.sortedBy { it.name.lowercase() }
        } }
        entries = result.getOrElse { context.toastOnUi("字体目录读取失败：${it.localizedMessage}"); emptyList() }
        loadingList = false
    }
    LaunchedEffect(choice, loadRequest) {
        val requestedChoice = choice
        val requestedSequence = loadRequest
        preview = null
        previewChoice = null
        previewRequest = null
        error = null
        val result = runCatching {
            NgInterfaceFontStore.load(
                context,
                requestedChoice,
                reloadFromSource = requestedSequence > 0 &&
                    requestedChoice !in NgInterfaceFontStore.systemChoices,
            )
        }
            .onFailure { if (it is CancellationException) throw it }
        if (choice == requestedChoice && loadRequest == requestedSequence) {
            result.onSuccess {
                preview = it
                previewChoice = requestedChoice
                previewRequest = requestedSequence
                if (requestedSequence > 0) {
                    refreshedTypefaces = refreshedTypefaces.toMutableMap().apply {
                        remove(requestedChoice)
                        put(requestedChoice, it.first)
                        if (size > 12) remove(keys.first())
                    }
                }
            }.onFailure { error = "字体无法加载：${it.localizedMessage}" }
        }
    }
    val directoryPicker = rememberLauncherForActivityResult(SelectDirectoryContract()) { result ->
        result.uri?.let { uri ->
            if (uri.scheme == "content") runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { context.toastOnUi("未能保留字体目录权限") }
            directory = uri.toString()
            context.putPrefString(NgInterfaceFontStore.DIRECTORY, directory)
            customTab = true
        }
    }
    val base = NgTheme.snapshot
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismissRequest() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, contentColor = Color(base.colors.onSurface),
        shape = RectangleShape, dragHandle = null,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.40f),
            contentCardStyle = NgDrawerContentCardStyle.LEGACY,
        ) {
            val color = Color(NgTheme.colors.onSurface)
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NgThemeSheetActionButton(
                        onClick = { directoryPicker.launch(SelectDirectoryContract.Request(initialUri = directory.takeIf { it.startsWith("content:") }?.let(Uri::parse))) },
                        contentDescription = stringResource(R.string.add_font_directory), enabled = !saving,
                    ) {
                        Icon(painterResource(R.drawable.ic_create_folder_outline), null, Modifier.size(20.dp), tint = Color(NgTheme.colors.primary))
                    }
                    Text(stringResource(R.string.interface_font), Modifier.weight(1f), color = color,
                        fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    NgThemeSheetSaveButton(
                        enabled = !saving && preview != null && previewChoice == choice &&
                            previewRequest == loadRequest,
                        contentDescription = stringResource(R.string.save),
                        onClick = {
                            if (saving || previewChoice != choice || previewRequest != loadRequest) {
                                return@NgThemeSheetSaveButton
                            }
                            val selected = preview ?: return@NgThemeSheetSaveButton
                            val selectedChoice = choice
                            val label = NgInterfaceFontStore.systemChoices.indexOf(choice).takeIf { it >= 0 }?.let { names[it] }
                                ?: chosenName.ifBlank { Uri.parse(choice).lastPathSegment.orEmpty() }
                            saving = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching {
                                    NgInterfaceFontStore.save(context, selectedChoice, label, selected.second)
                                } }
                                saving = false
                                result.onSuccess { onDismissRequest(); postEvent(EventBus.RECREATE, "") }
                                    .onFailure { context.toastOnUi("字体保存失败：${it.localizedMessage}") }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                NgFloatingTabBar(
                    items = listOf(NgFloatingTabSpec(text = stringResource(R.string.font_mode_system)),
                        NgFloatingTabSpec(text = stringResource(R.string.font_mode_custom))),
                    selectedIndex = if (customTab) 1 else 0,
                    onTabSelected = { if (!saving) customTab = it == 1 }, modifier = Modifier.fillMaxWidth(),
                    size = NgFloatingTabBarSize.COMPACT,
                )
                Spacer(Modifier.height(8.dp))
                error?.let { Text(it, color = Color(NgTheme.colors.error), fontSize = 12.sp) }
                val rows = if (!customTab) NgInterfaceFontStore.systemChoices.mapIndexed { i, value -> InterfaceFontEntry(value, names[i]) }
                    else entries.let { files ->
                        if (choice !in NgInterfaceFontStore.systemChoices && files.none { it.uri == choice }) {
                            listOf(InterfaceFontEntry(choice, chosenName.ifBlank { Uri.parse(choice).lastPathSegment.orEmpty() })) + files
                        } else files
                    }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.uri }) { font ->
                        val fontTypeface by produceState<Typeface?>(
                            initialValue = font.uri.takeIf { it in NgInterfaceFontStore.systemChoices }?.let(NgInterfaceFontStore::systemTypeface),
                            key1 = font.uri,
                        ) {
                            value = runCatching { NgInterfaceFontStore.load(context, font.uri).first }
                                .onFailure { if (it is CancellationException) throw it }.getOrNull()
                        }
                        val displayedTypeface = if (choice == font.uri) preview?.first
                            else refreshedTypefaces[font.uri] ?: fontTypeface
                        val displayedFontFamily = remember(displayedTypeface) {
                            displayedTypeface?.let(::FontFamily)
                        }
                        NgManagementListCard(
                            title = font.name,
                            selected = choice == font.uri,
                            containerColor = Color.White,
                            titleColor = Color(0xFF202124),
                            titleFontFamily = displayedFontFamily,
                            size = NgManagementListCardSize.COMPACT_SINGLE_LINE,
                            onClick = if (saving) null else ({
                                choice = font.uri
                                chosenName = font.name
                                loadRequest++
                                preview = null
                                previewChoice = null
                                previewRequest = null
                                error = null
                            }),
                        ) {
                            Text("Aa", color = Color(0xFF202124), fontSize = 20.sp,
                                fontFamily = displayedFontFamily)
                        }
                    }
                    if (customTab && rows.isEmpty()) item {
                        Text(if (loadingList) stringResource(R.string.loading) else stringResource(R.string.font_folder_empty),
                            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
