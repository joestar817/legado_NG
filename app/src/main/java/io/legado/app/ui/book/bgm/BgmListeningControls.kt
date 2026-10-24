package io.legado.app.ui.book.bgm

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.bgm.*
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun BgmListeningLayout(
    settings: BgmSettings,
    playback: BgmPlaybackState,
    page: BgmPage,
    platform: MusicPlatform,
    onCurrent: () -> Unit,
    onPlatform: (MusicPlatform) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    previewContent: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val closeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val currentDismiss by rememberUpdatedState(onDismiss)
    // Compose owns the grab area's gesture; catalogue rows keep their independent nested scroll.
    val grabGesture = Modifier.draggable(
        state = rememberDraggableState { delta ->
            totalDrag += delta
            dragOffset = (dragOffset + delta).coerceAtLeast(0f)
        }, orientation = Orientation.Vertical,
        onDragStarted = { settleJob?.cancel(); totalDrag = 0f },
        onDragStopped = {
            if (dragOffset > closeThreshold) currentDismiss()
            else {
                if (totalDrag < -closeThreshold) expanded = true
                settleJob = scope.launch {
                    animate(dragOffset, 0f, animationSpec = tween(160)) { value, _ -> dragOffset = value }
                }
            }
        },
    )
    val browsing = page == BgmPage.LIBRARY
    val height = (LocalConfiguration.current.screenHeightDp * if (browsing || expanded) .85f else .68f).dp
    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = dragOffset },
        appearance = NgDrawerDefaults.rememberAppearance().copy(horizontalMarginDp = 0, cornerRadiusDp = 24),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        themeProfile = null,
    ) {
        Column(Modifier.fillMaxWidth().height(height).navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().height(24.dp).then(grabGesture)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "收起配乐面板" else "展开配乐面板") {
                    expanded = !expanded
                }, Alignment.Center) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("背景音乐", Modifier.weight(1f), color = Color(NgTheme.colors.onSurface),
                    fontFamily = NgTheme.fontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                NgSwitchControl(settings.enabled, { BgmRepository.setEnabled(it) },
                    enabled = settings.queue.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "启用背景音乐" })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                SourceTab("播放列表", !browsing, null, onCurrent, Modifier)
                MusicPlatform.entries.forEach { source ->
                    SourceTab(when (source) {
                        MusicPlatform.NETEASE -> "网易云"
                        MusicPlatform.QQ -> "QQ音乐"
                        MusicPlatform.LOCAL -> "本地"
                    }, browsing && source == platform, source, { onPlatform(source) }, Modifier)
                }
            }
            ListeningRule()
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) { content() }
            ListeningRule()
            if (previewContent != null) previewContent()
            else BgmListeningDock(settings, playback, onPrevious, onNext)
        }
    }
}

@Composable
private fun RowScope.SourceTab(
    label: String, selected: Boolean, platform: MusicPlatform?, onClick: () -> Unit, modifier: Modifier,
) {
    BgmListeningTab(label, selected, onClick, modifier, variant = BgmListeningTabVariant.SOURCE) { color ->
        when (platform) {
            null -> BgmIcon(R.drawable.ic_bgm_playlist, modifier = Modifier.size(20.dp), tint = color)
            MusicPlatform.LOCAL -> Box(Modifier.width(10.dp).height(20.dp), contentAlignment = Alignment.Center) {
                // Keep the 20dp drawing; only remove the single note's transparent side bearings.
                Icon(Icons.Rounded.MusicNote, null, Modifier.requiredSize(20.dp), tint = color)
            }
            else -> BgmPlatformIcon(platform, Modifier.size(20.dp))
        }
    }
}

internal enum class BgmListeningTabVariant { SOURCE, SECTION }

/** The source row is denser; both rows keep centered content above an independent underline. */
@Composable
internal fun BgmListeningTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: BgmListeningTabVariant = BgmListeningTabVariant.SECTION,
    leadingIcon: (@Composable (Color) -> Unit)? = null,
) {
    val source = variant == BgmListeningTabVariant.SOURCE
    val color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant)
        .copy(alpha = if (enabled) 1f else .48f)
    val tabModifier = if (source) modifier.width(IntrinsicSize.Max).widthIn(min = 48.dp) else modifier
    Box(tabModifier.height(48.dp).selectable(selected, enabled, Role.Tab) { if (!selected) onClick() }) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = if (source) 5.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            leadingIcon?.invoke(color)
            Text(label, color = color, fontSize = if (source) 12.sp else 14.sp,
                lineHeight = if (source) 18.sp else 20.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = NgTheme.fontFamily,
                style = if (source) LocalTextStyle.current else LocalTextStyle.current.copy(
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter)
            .then(if (source) Modifier.fillMaxWidth() else Modifier.width(28.dp)),
            thickness = 2.dp,
            color = if (selected) color else Color.Transparent)
    }
}

@Composable
internal fun BgmListeningQueue(
    settings: BgmSettings,
    playback: BgmPlaybackState,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    val modeMenu = remember { NgPopupToggleState() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("播放列表 · ${settings.queue.size}", Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Box {
                Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { modeMenu.onAnchorClick() }
                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    BgmIcon(modeIcon(settings.repeatMode), modifier = Modifier.size(20.dp))
                    Text(settings.repeatMode.label, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp)
                    BgmIcon(R.drawable.ic_arrow_drop_down, modifier = Modifier.size(16.dp))
                }
                NgExpandableActionMenu(modeMenu.expanded, modeMenu::onDismissRequest,
                    BgmRepeatMode.entries.mapIndexed { index, mode ->
                        NgExpandableActionMenuItem(index + 1, 0, modeIcon(mode), title = mode.label,
                            checked = mode == settings.repeatMode)
                    } + NgExpandableActionMenuItem(10, 0, R.drawable.ic_bgm_playlist_remove,
                        title = "清空列表", enabled = settings.queue.isNotEmpty(), danger = true, dividerBefore = true),
                    onItemClick = {
                        modeMenu.close()
                        if (it.itemId == 10) onClear()
                        else BgmRepository.setRepeatMode(BgmRepeatMode.entries[it.itemId - 1])
                    }, menuContainerColor = Color(NgTheme.colors.surface),
                    properties = PopupProperties(focusable = true))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 6.dp)) {
            if (settings.queue.isEmpty()) item("empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(40.dp),
                        tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = .4f))
                    Spacer(Modifier.height(12.dp))
                    BgmText("从上方曲库选择音乐", secondary = true)
                }
            }
            itemsIndexed(settings.queue, key = { _, track -> track.key }) { index, track ->
                val selected = settings.currentTrack?.key == track.key
                Row(Modifier.fillMaxWidth().heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).selectable(selected, role = Role.Button,
                        onClick = { onSelect(track.key) }).padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BgmCover(track.coverUrl, Modifier.size(42.dp), cornerRadius = 7.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
                                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontFamily = NgTheme.fontFamily)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(track.platform.bgmTitle(), color = Color(NgTheme.colors.onSurfaceVariant),
                                    fontSize = 12.sp, lineHeight = 16.sp)
                                BgmTrackVipBadge(track)
                            }
                        }
                        if (selected) BgmListeningPlayingIndicator(
                            playing = settings.enabled && playback.trackKey == track.key &&
                                playback.status == BgmPlaybackStatus.PLAYING,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    BgmLocalActions(buildList {
                        if (index > 0) add(BgmLocalAction("上移", R.drawable.ic_arrow_drop_up,
                            { BgmRepository.moveInQueue(track.key, index - 1) }))
                        if (index < settings.queue.lastIndex) add(BgmLocalAction("下移", R.drawable.ic_arrow_drop_down,
                            { BgmRepository.moveInQueue(track.key, index + 1) }))
                        add(BgmLocalAction("移出当前列表", R.drawable.ic_bgm_playlist_remove,
                            { BgmRepository.removeFromQueue(track.key) }, danger = true))
                    })
                }
                ListeningRule()
            }
        }
    }
}

private fun modeIcon(mode: BgmRepeatMode) = when (mode) {
    BgmRepeatMode.LIST -> R.drawable.ic_play_mode_list_loop
    BgmRepeatMode.ONE -> R.drawable.ic_play_mode_single_loop
    BgmRepeatMode.SHUFFLE -> R.drawable.ic_play_mode_random
}

@Composable
private fun ListeningRule() {
    HorizontalDivider(Modifier.padding(horizontal = 12.dp), thickness = .5.dp,
        color = Color(NgTheme.colors.onSurface).copy(alpha = .1f))
}
