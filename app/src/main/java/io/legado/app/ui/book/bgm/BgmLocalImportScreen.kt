package io.legado.app.ui.book.bgm

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.bgm.*
import io.legado.app.ui.book.import.ImportBrowserPathRow
import io.legado.app.ui.book.import.ImportSelectionDock
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID

internal data class BgmLocalAction(val title: String, val icon: Int, val action: () -> Unit,
    val danger: Boolean = false, val checked: Boolean = false, val divider: Boolean = false)

@Composable
internal fun BgmLocalActions(actions: List<BgmLocalAction>, enabled: Boolean = true,
    toolbar: Boolean = false, anchorIcon: Int = if (toolbar) R.drawable.ic_grid_menu else R.drawable.ic_more_vert,
    anchorDescription: String = "更多") {
    val menu = remember { NgPopupToggleState() }
    Box {
        if (toolbar) NgFloatingToolbarActionButton(anchorIcon, anchorDescription, menu::onAnchorClick, enabled = enabled)
        else BgmTrackActionButton(anchorIcon, anchorDescription, menu::onAnchorClick, enabled)
        NgExpandableActionMenu(menu.expanded, menu::onDismissRequest,
            actions.mapIndexed { index, item -> NgExpandableActionMenuItem(index + 1, 0, item.icon,
                title = item.title, danger = item.danger, checked = item.checked, dividerBefore = item.divider) },
            onItemClick = { item -> menu.close(); actions.getOrNull(item.itemId - 1)?.action?.invoke() },
            variant = if (toolbar) NgExpandableActionMenuVariant.SIDE_SLIDE else NgExpandableActionMenuVariant.DROPDOWN,
            properties = if (toolbar) PopupProperties(focusable = true, clippingEnabled = false) else PopupProperties())
    }
}

@Composable
internal fun BgmLocalImportScreen(
    state: BgmLocalImportState,
    preview: BgmPlaybackState,
    model: BgmLocalImportViewModel,
    onBack: () -> Unit,
    onChooseDirectory: () -> Unit,
    onPreview: (LocalAudioEntry) -> Unit,
    onAdd: () -> Unit,
    onExit: () -> Unit,
) {
    val foreground = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val primary = Color(NgTheme.colors.primary)
    val listState = rememberLazyListState()
    val previewActive = preview.status in listOf(BgmPlaybackStatus.LOADING, BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.PAUSED)
    LaunchedEffect(listState, state.root, state.path, state.recursive) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet() }
            .distinctUntilChanged().collect(model::visible)
    }
    LaunchedEffect(state.root, state.path, state.recursive, state.query, state.sort) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        if (state.searching) NgFloatingSearchToolbar(state.query, model::query, "搜索文件或歌曲",
            { model.search(false) }, enabled = !state.adding, trailingContent = {
                NgFloatingToolbarActionButton(R.drawable.ic_folder_open, "选择目录", onChooseDirectory, enabled = !state.adding)
            })
        else NgFloatingTitleToolbar(if (state.relocating) "重新定位音乐" else "添加本地音乐", onBack,
            trailingContent = {
                NgFloatingToolbarActionButton(R.drawable.ic_search, "搜索", { model.search(true) }, enabled = !state.adding)
                NgFloatingToolbarActionButton(R.drawable.ic_folder_open, "选择目录", onChooseDirectory, enabled = !state.adding)
            })
        Surface(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp), color = colorResource(R.color.ng_surface_card)) {
            Column {
                ImportBrowserPathRow(state.path.joinToString("  ›  ") { it.name }.ifBlank { "选择音乐目录" },
                    !state.adding && (state.path.size > 1 || state.recursive), { model.goUp() })
                HorizontalDivider(color = foreground.copy(alpha = .08f))
                Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.recursive) "找到 ${state.discovered} 首 · 含子目录"
                        else "本目录 ${state.discovered} 首", Modifier.weight(1f), color = secondary,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.adding || state.scanning || state.reading) {
                        NgDialogTextActionButton(if (state.stopping) "正在停止…" else "停止",
                            { if (state.adding) model.stopImport() else model.stopScan() }, enabled = !state.stopping)
                    } else TextButton({ model.scanChildren() }, enabled = state.root != null,
                        contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(painterResource(R.drawable.ic_scan), null, Modifier.size(16.dp), tint = primary)
                        Spacer(Modifier.width(4.dp))
                        Text("扫描子目录", color = primary, fontSize = 12.sp)
                    }
                    BgmSortMenu(listOf("名称", "歌手", "时长", "大小", "修改时间"), state.sort, model::sort,
                        enabled = !state.adding)
                    BgmLocalActions(buildList {
                        add(BgmLocalAction("包含录音", 0, model::toggleRecordings, checked = state.includeRecordings))
                        add(BgmLocalAction("显示隐藏文件", 0, model::toggleHidden, checked = state.showHidden))
                        listOf(0L to "不限时长", 30_000L to "至少 30 秒", 60_000L to "至少 1 分钟").forEach { (duration, title) ->
                            add(BgmLocalAction(title, 0, { model.duration(duration) }, checked = state.minDuration == duration, divider = duration == 0L))
                        }
                    }, enabled = !state.adding, anchorIcon = R.drawable.ic_screen, anchorDescription = "筛选")
                }
                if (state.adding || state.scanning || state.reading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = primary)
                    Text(when {
                        state.adding -> state.progress?.let { "正在添加 ${it.processed}/${it.total}  ${it.name}" }.orEmpty()
                        state.scanning -> "正在扫描 ${state.scanDirectory}"
                        else -> "正在读取歌曲信息…"
                    }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), color = secondary,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else HorizontalDivider(color = foreground.copy(alpha = .08f))
                if (state.directoryFailures.isNotEmpty()) Text("${state.directoryFailures.size} 项未能读取",
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color(NgTheme.colors.error), fontSize = 12.sp)
                if (state.error.isNotBlank() || (state.items.isEmpty() && !state.scanning)) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = secondary.copy(alpha = .3f))
                        Spacer(Modifier.height(12.dp))
                        Text(state.error.ifBlank { when {
                            state.root == null -> "选择目录，添加本地音乐"
                            state.query.isNotBlank() -> "没有匹配的音乐"
                            else -> "此目录没有可添加的音频"
                        } }, color = secondary, fontSize = 14.sp)
                        if (state.root == null || state.error.isNotBlank()) NgDialogTextActionButton("选择目录", onChooseDirectory)
                    }
                } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                    contentPadding = PaddingValues(bottom = 4.dp)) {
                    items(state.items, key = LocalAudioEntry::key) { item ->
                        LocalAudioRow(item, item.key in state.selectedKeys, item.key in state.addedUris,
                            state.canSelect(item), state.adding, state.recursive, preview,
                            onClick = { if (item.file.isDir) model.openDirectory(item) else model.toggle(item) },
                            onPreview = { onPreview(item) })
                    }
                }
                HorizontalDivider(color = foreground.copy(alpha = .08f))
                ImportSelectionDock(state.selectedKeys.size,
                    if (state.relocating) 0 else state.items.count(state::canSelect),
                    { model.selectAll() }, { model.selectAll(invert = true) }, onAdd,
                    actionLabelRes = if (state.relocating) R.string.bgm_relocate else R.string.bgm_import_add,
                    selectedCountText = "已选 ${state.selectedKeys.size} 首", enabled = !state.adding)
            }
        }
        if (previewActive) Spacer(Modifier.height(BgmPreviewBottomSpace))
    }
    state.result?.let { report ->
        Dialog(onDismissRequest = model::dismissResult) {
            NgDialog(if (state.relocating) "定位结果" else "添加结果", variant = NgDialogVariant.COMPACT_CONFIRMATION,
                actions = {
                    if (report.failed > 0) NgDialogTextActionButton("重试失败", model::retry)
                    NgDialogTextActionButton("继续选择", model::dismissResult)
                    NgDialogTextActionButton("返回曲库", onExit)
                }) {
                Text(buildString {
                    append(if (state.relocating) "已定位 ${report.imported} 首" else "新增 ${report.imported} 首 · 重复 ${report.duplicates} 首")
                    if (report.stopped) append("\n已停止，未处理 ${report.skipped} 首")
                }, color = Color(NgTheme.colors.onSurface), fontSize = 14.sp)
                if (report.failures.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(top = 10.dp)) {
                    items(report.failures) { failure ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(failure.name, color = Color(NgTheme.colors.onSurface), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(failure.reason, color = Color(NgTheme.colors.error), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalAudioRow(entry: LocalAudioEntry, checked: Boolean, added: Boolean, selectable: Boolean,
    busy: Boolean, recursive: Boolean, preview: BgmPlaybackState, onClick: () -> Unit, onPreview: () -> Unit) {
    val foreground = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val key = remember(entry.key) { "LOCAL:${UUID.nameUUIDFromBytes(entry.key.toByteArray())}" }
    val previewing = preview.trackKey == key && preview.status in listOf(BgmPlaybackStatus.LOADING, BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.PAUSED)
    Row(Modifier.fillMaxWidth().heightIn(min = if (entry.file.isDir) 54.dp else 68.dp).padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = if (entry.file.isDir) 54.dp else 68.dp)
            .then(if (entry.file.isDir) Modifier.clickable(enabled = !busy, onClick = onClick)
                else Modifier.toggleable(checked || added, enabled = selectable && !busy,
                    role = Role.Checkbox, onValueChange = { onClick() })), verticalAlignment = Alignment.CenterVertically) {
            if (entry.file.isDir) {
                Spacer(Modifier.width(6.dp))
                NgFileEntryIcon(NgFileEntryIconKind.DIRECTORY)
            } else {
                NgFileSelectionCheckbox(checked || added, { onClick() }, enabled = selectable && !busy,
                    variant = NgFileSelectionCheckboxVariant.COMPACT)
                BgmCover(entry.metadata?.coverUrl.orEmpty(), Modifier.size(36.dp), cornerRadius = 7.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(entry.title, color = if (added) secondary else foreground, fontSize = 14.sp,
                    lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.file.isDir) {
                    val info = when {
                        entry.error.isNotBlank() -> entry.error
                        added -> "已添加"
                        else -> listOfNotNull(entry.metadata?.artist?.takeIf { it.isNotBlank() },
                            LocalAudioFormat.extension(entry.file.name).uppercase().takeIf { it.isNotBlank() },
                            entry.file.size.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(LocalContext.current, it) })
                            .joinToString(" · ")
                    }
                    Text(info, color = if (entry.error.isBlank()) secondary else Color(NgTheme.colors.error),
                        fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (recursive && entry.relativePath.contains('/')) Text(entry.relativePath.substringBeforeLast('/'),
                        color = secondary, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (entry.file.isDir) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
            Modifier.padding(horizontal = 10.dp).size(20.dp), tint = secondary)
        else {
            val duration = entry.metadata?.durationMs ?: 0
            Text(if (duration > 0) "${duration / 60000}:${(duration / 1000 % 60).toString().padStart(2, '0')}" else "—",
                Modifier.padding(start = 6.dp), color = secondary, fontSize = 11.sp)
            BgmTrackActionButton(when {
                previewing && preview.status == BgmPlaybackStatus.LOADING -> R.drawable.ic_stop_black_24dp
                previewing && preview.status == BgmPlaybackStatus.PLAYING -> R.drawable.ic_pause_24dp
                else -> R.drawable.ic_play_outline_24dp
            }, "试听 ${entry.title}", onPreview, enabled = !busy && entry.error.isBlank(), active = previewing)
        }
    }
    HorizontalDivider(Modifier.padding(start = if (entry.file.isDir) 62.dp else 84.dp, end = 12.dp),
        thickness = .5.dp, color = foreground.copy(alpha = .08f))
}
