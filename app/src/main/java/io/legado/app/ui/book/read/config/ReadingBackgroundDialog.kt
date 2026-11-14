package io.legado.app.ui.book.read.config

import android.app.Dialog as AndroidDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.background.BackgroundDeletionTarget
import io.legado.app.help.background.ReadingBackground
import io.legado.app.help.background.ReadingBackgroundLibrary
import io.legado.app.help.background.ReadingBackgroundSnapshot
import io.legado.app.help.resource.ResourceRepository
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A child window keeps the preset editor and its scroll position alive underneath. */
class ReadingBackgroundDialog : BaseComposeDialogFragment() {
    private val editor get() = parentFragment as? ReadStyleDialog
    private var resumeRevision by mutableIntStateOf(0)

    override fun onCreateDialog(savedInstanceState: Bundle?): AndroidDialog = ComponentDialog(requireContext(), theme)

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onResume() {
        super.onResume()
        resumeRevision++
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(requireContext(),
                        ReadFloatingAppearanceState.primaryStrengthPercent, ReadFloatingAppearanceState.colorStyle),
                    updateSystemBars = false,
                ) {
                    val owner = editor
                    val target = owner?.backgroundLibraryTarget()
                    if (target == null) {
                        LaunchedEffect(Unit) { dismissAllowingStateLoss() }
                    } else {
                        ReadingBackgroundScreen(
                            target = target,
                            allowEInk = owner.backgroundLibraryAllowsEInk(),
                            importing = owner.backgroundLibraryImporting(),
                            resumeRevision = resumeRevision,
                            onDismiss = { dismissAllowingStateLoss() },
                            onMode = owner::setBackgroundLibraryMode,
                            onImport = owner::importLibraryBackground,
                            onApply = owner::applyLibraryBackground,
                            onBusy = { isCancelable = !it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingBackgroundScreen(
    target: ReadStyleEditTarget,
    allowEInk: Boolean,
    importing: Boolean,
    resumeRevision: Int,
    onDismiss: () -> Unit,
    onMode: (Int) -> Unit,
    onImport: () -> Unit,
    onApply: (ReadingBackground) -> Unit,
    onBusy: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by ReadingBackgroundLibrary.revision.collectAsState()
    val resourceState by ResourceRepository.state.collectAsState()
    var snapshot by remember { mutableStateOf<ReadingBackgroundSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<BackgroundDeletionTarget>?>(null) }
    var information by remember { mutableStateOf<String?>(null) }
    val contentColor = Color(ReadDrawerStyle.contentColor(context))
    val accentColor = Color(ReadDrawerStyle.accentColor(context))
    val selectedContent = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    val gridState = rememberLazyGridState()
    SideEffect { onBusy(busy || importing) }
    val entries = snapshot?.entries.orEmpty()
    val visibleEntries = remember(entries, managing) {
        if (managing) entries.filter { it.type != 1 } else entries
    }
    val selectable = remember(snapshot) {
        if (snapshot?.usageError != null) emptySet() else entries.filter {
            it.deletable && snapshot?.usages?.get(it.id).isNullOrEmpty()
        }.map { it.id }.toSet()
    }

    LaunchedEffect(revision, resourceState.installations, resourceState.localStates, resumeRevision, refresh, busy, importing) {
        if (busy || importing) return@LaunchedEffect
        loading = true
        try {
            snapshot = withContext(Dispatchers.IO) { ReadingBackgroundLibrary.scan() }
            error = null
            selected = selected.filter { id -> snapshot?.entries?.any { it.id == id } == true }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.localizedMessage ?: "背景列表读取失败"
        } finally {
            loading = false
        }
    }

    fun close() {
        if (busy || importing) return
        if (managing) { managing = false; selected = emptyList() } else onDismiss()
    }
    BackHandler { close() }
    NgDismissibleDrawer(onDismiss = onDismiss, enabled = !busy && !importing) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            style = readFloatingGlassStyle(),
        ) {
            Column(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * 0.84f).dp)
                .padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp)) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
                Box(Modifier.fillMaxWidth().height(52.dp)) {
                    Text(
                        if (managing) "管理背景" else "阅读背景",
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                            .padding(horizontal = if (managing) 44.dp else 92.dp),
                        color = contentColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NgThemeSheetActionButton(
                        onClick = ::close,
                        contentDescription = "返回",
                        modifier = Modifier.align(Alignment.CenterStart),
                        enabled = !busy && !importing,
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), null, Modifier.size(22.dp), tint = contentColor)
                    }
                    if (!managing) {
                        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                            NgThemeSheetActionButton(
                                onClick = onImport,
                                contentDescription = "导入图片",
                                enabled = !busy && !importing,
                            ) {
                                Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(22.dp), tint = accentColor)
                            }
                            Spacer(Modifier.width(4.dp))
                            NgThemeSheetActionButton(
                                onClick = {
                                    managing = true; selected = emptyList(); refresh++
                                },
                                contentDescription = "管理背景",
                                enabled = !busy && !importing,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = accentColor,
                                )
                            }
                        }
                    }
                }
                if (!managing) {
                    Spacer(Modifier.height(4.dp))
                    ReadStylePaletteModeSelector(
                        mode = target.mode.ordinal,
                        allowEInk = allowEInk,
                        contentColor = contentColor,
                        enabled = !busy && !importing,
                        onSelected = onMode,
                    )
                }
                Spacer(Modifier.height(16.dp))
                val message = error ?: snapshot?.usageError?.takeIf { managing }
                if (message != null) {
                    Text(message, color = contentColor, fontSize = 13.sp)
                    Text("重新加载", Modifier.padding(vertical = 8.dp).clickable(enabled = !busy) { refresh++ }, color = accentColor, fontSize = 14.sp)
                }
                if (visibleEntries.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Text(if (loading) "加载中…" else if (managing) "暂无可管理背景" else "暂无背景",
                            color = contentColor.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4), state = gridState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(visibleEntries, key = { it.id }) { entry ->
                            val current = entry.type == target.appearance.backgroundTypeForMode(target.mode) &&
                                if (entry.type == 1) entry.reference == target.appearance.backgroundForMode(target.mode)
                                else entry.id == ReadingBackgroundLibrary.resolveFile(target.appearance.backgroundForMode(target.mode)).absolutePath
                            val selectableEntry = entry.id in selectable
                            val inUse = !snapshot?.usages?.get(entry.id).isNullOrEmpty()
                            val status = when {
                                managing && inUse -> "使用中"
                                managing && !entry.deletable -> "外部"
                                else -> null
                            }
                            BackgroundCard(entry, current, managing, entry.id in selected, selectableEntry, status,
                                contentColor, accentColor, selectedContent, enabled = !busy && !importing) {
                                if (managing) {
                                    if (selectableEntry) selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                                    else context.toastOnUi(if (entry.type == 1) "内置背景不可删除" else if (inUse) "背景仍在使用，请先更换并返回保存" else "此图片不可删除")
                                } else {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val exists = withContext(Dispatchers.IO) { entry.file?.isFile ?: true }
                                            if (exists) { onApply(entry); refresh++ } else context.toastOnUi("图片不存在，请刷新")
                                        } finally { busy = false }
                                    }
                                }
                            }
                        }
                    }
                }
                if (managing) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已选 ${selected.size}", Modifier.weight(1f), color = contentColor.copy(alpha = 0.72f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.width(220.dp)) {
                            val selectionEnabled = !busy && !importing && !loading && selectable.isNotEmpty()
                            NgFlatActionRail(
                                items = listOf(
                                    NgFlatActionRailItem(R.drawable.ic_select_all, "全选", enabled = selectionEnabled),
                                    NgFlatActionRailItem(R.drawable.ic_refresh_black_24dp, "反选", enabled = selectionEnabled),
                                    NgFlatActionRailItem(R.drawable.ic_book_info_delete, "删除",
                                        enabled = !busy && !importing && !loading && selected.isNotEmpty(),
                                        dangerIcon = true, dangerLabel = true),
                                ),
                                variant = NgFlatActionRailVariant.INLINE_ACTIONS,
                                onItemClick = { index ->
                                    when (index) {
                                        0 -> selected = selectable.toList()
                                        1 -> {
                                            val previous = selected.toSet()
                                            selected = selectable.filterNot { it in previous }
                                        }
                                        else -> {
                                            busy = true
                                            scope.launch {
                                                try {
                                                    pendingDelete = withContext(Dispatchers.IO) {
                                                        ReadingBackgroundLibrary.prepareDeletion(entries.filter { it.id in selected && it.id in selectable })
                                                    }.takeIf { it.isNotEmpty() }
                                                } catch (failure: Exception) {
                                                    if (failure is CancellationException) throw failure
                                                    information = failure.localizedMessage
                                                } finally { busy = false }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalNgDialogColorMode provides NgDialogColorMode.INHERIT_HOST) {
        pendingDelete?.let { targets ->
            Dialog(onDismissRequest = { if (!busy) pendingDelete = null },
                properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
                NgDialog("删除 ${targets.size} 张背景？", variant = NgDialogVariant.SIMPLE_CONFIRMATION, actions = {
                    NgDialogTextActionButton("取消", { pendingDelete = null }, enabled = !busy, secondary = true, modifier = Modifier.weight(1f))
                    NgDialogTextActionButton(if (busy) "删除中" else "删除", {
                        busy = true
                        scope.launch {
                            try {
                                val result = ResourceRepository.deleteReadingBackgrounds(targets)
                                selected = selected.filterNot { it in result.deleted }
                                pendingDelete = null
                                if (result.failures.isNotEmpty()) information = result.failures.joinToString("\n")
                                else context.toastOnUi("已删除 ${result.deleted.size} 张背景")
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                pendingDelete = null
                                information = failure.localizedMessage
                            } finally { busy = false; refresh++ }
                        }
                    }, enabled = !busy, danger = true, modifier = Modifier.weight(1f))
                }) {}
            }
        }
        information?.let { message ->
            Dialog(onDismissRequest = { information = null }) {
                NgDialog("未能删除", actions = { NgDialogTextActionButton("知道了", { information = null }) }) {
                    Text(message, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), color = contentColor, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundCard(
    entry: ReadingBackground, current: Boolean, managing: Boolean, checked: Boolean, selectable: Boolean,
    status: String?, color: Color, accent: Color, onAccent: Color, enabled: Boolean, onClick: () -> Unit,
) {
    val context = LocalContext.current
    var showFullName by remember(entry.id) { mutableStateOf(false) }
    val bitmap by produceState<ImageBitmap?>(null, entry.id, entry.size, entry.modified) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (entry.type == 1) BitmapUtils.decodeAssetsBitmap(context, "bg/${entry.reference}", 216, 300)?.asImageBitmap()
                else BitmapUtils.decodeBitmap(requireNotNull(entry.file).path, 216, 300)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val marked = if (managing) checked else current
    Column(
        Modifier.semantics { selected = marked }
            .combinedClickable(
                enabled = enabled,
                role = if (managing && selectable) Role.Checkbox else Role.Button,
                onLongClickLabel = "查看完整名称",
                onLongClick = { showFullName = true },
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(shape)
                .background(color.copy(alpha = 0.05f))
                .then(if (marked) Modifier.border(1.5.dp, accent, shape) else Modifier),
        ) {
            bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Icon(painterResource(R.drawable.ic_image), null, Modifier.size(28.dp).align(Alignment.Center), tint = color.copy(alpha = 0.4f))
            if (status != null) {
                NgStatusTag(status, NgStatusTagVariant.NEUTRAL,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                    style = NgStatusTagStyle.INLINE)
            }
            if (marked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(17.dp)
                        .background(accent, CircleShape).border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_check), if (managing) "已选择" else "当前背景", Modifier.size(12.dp), tint = onAccent)
                }
            } else if (managing && selectable) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(17.dp).background(Color.White.copy(alpha = 0.85f), CircleShape)
                    .border(1.dp, color.copy(alpha = 0.4f), CircleShape))
            }
        }
        Text(entry.name, Modifier.fillMaxWidth().padding(top = 6.dp), color = color,
            fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.sp,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (showFullName) {
        CompositionLocalProvider(LocalNgDialogColorMode provides NgDialogColorMode.INHERIT_HOST) {
            Dialog(onDismissRequest = { showFullName = false }) {
                NgDialog("背景名称", actions = { NgDialogTextActionButton("知道了", { showFullName = false }) }) {
                    Text(entry.name, Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        color = color, fontSize = 14.sp)
                }
            }
        }
    }
}
