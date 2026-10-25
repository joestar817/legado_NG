package io.legado.app.ui.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.gsyVideo.VideoPlaybackBridge
import io.legado.app.help.gsyVideo.VideoPlaybackState
import io.legado.app.model.VideoPlay
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class VideoPresentation { WINDOW, IMMERSIVE, FULLSCREEN }

@Composable
fun VideoPlayerScreen(
    owner: Any, mode: VideoPresentation, revision: Int, intro: View,
    onMode: (VideoPresentation) -> Unit, onBack: () -> Unit, onAction: (Int) -> Unit,
    onEpisode: (Int, Int) -> Unit,
) {
    val state by VideoPlaybackBridge.state.collectAsState()
    val windowed = mode == VideoPresentation.WINDOW
    var controls by rememberSaveable { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf("") }
    var panelVolume by rememberSaveable { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(VideoPlay.lockCurScreen) }
    var danmaku by remember { mutableStateOf(VideoPlay.danmakuShow) }
    var expandedIntro by rememberSaveable { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val title = remember(revision, state.title) { VideoPlay.book?.name ?: VideoPlay.videoTitle ?: state.title }
    val episodes = remember(revision, state.mediaRevision) { VideoPlay.episodes.orEmpty() }
    val volumes = remember(revision) { VideoPlay.volumes.toList() }
    val catalog = remember(revision, VideoPlay.toc) {
        videoEpisodeGroups(VideoPlay.toc.orEmpty(), { it.isVolume }, {
            it.url.isNotBlank() && !it.url.startsWith(it.title)
        })
    }
    val hasCatalog = VideoPlay.book != null
    fun openEpisodes(volume: Int = VideoPlay.durVolumeIndex) {
        panelVolume = volume
        panel = "episodes"
    }
    val colors = videoContentColors()
    val primary = colors.accent
    val ink = colors.content
    val surface = colors.surface
    val stablePlayback = state.playing && !state.preparing && !state.buffering && state.error == null && !state.networkConfirmation
    LaunchedEffect(stablePlayback) { if (!stablePlayback) controls = true }
    LaunchedEffect(controls, stablePlayback, interaction, panel, more, hint, scrubbing) {
        if (controls && stablePlayback && panel.isEmpty() && !more && hint == null && !scrubbing) {
            delay(2500); controls = false
        }
    }
    val contentModifier = Modifier.fillMaxSize()
        .then(if (windowed) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
        .background(if (windowed) surface else Color.Black)
    BoxWithConstraints(contentModifier) {
        val playerHeight = if (windowed) (maxWidth / state.aspect).coerceAtMost(maxHeight / 2) else maxHeight
        val fitWidth = minOf(maxWidth, playerHeight * state.aspect)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(playerHeight).background(Color.Black)) {
                VideoRenderHost(owner, state, danmaku,
                    Modifier.align(Alignment.Center).width(fitWidth).height(fitWidth / state.aspect))
                // 全屏画布使用完整窗口，控件和手势单独避让安全区。
                Box(Modifier.matchParentSize().then(
                    if (windowed) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                )) {
                    VideoGestureLayer(state, mode, locked, enabled = panel.isEmpty() && !more,
                        onTap = { controls = !controls; interaction++ }, onHint = { hint = it })
                    if (controls) {
                        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = .55f), Color.Transparent))))
                        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (!locked) VideoIcon(R.drawable.ic_arrow_back, "返回", onBack)
                            if (mode == VideoPresentation.FULLSCREEN && !locked) Text(state.title.ifBlank { title.orEmpty() },
                                Modifier.weight(1f), color = Color.White, fontSize = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            else Spacer(Modifier.weight(1f))
                            if (mode == VideoPresentation.IMMERSIVE && !locked) VideoIcon(R.drawable.ic_fullscreen, "窗口",
                                { onMode(VideoPresentation.WINDOW) })
                            if (!locked) Box {
                                VideoIcon(R.drawable.ic_more_vert, "更多", { more = true })
                                NgExpandableActionMenu(more, { more = false }, videoMoreItems(), { item ->
                                    more = false
                                    if (item.itemId == R.id.menu_config_settings) panel = "settings"
                                    else onAction(item.itemId)
                                }, properties = PopupProperties(focusable = true))
                            }
                        }
                        if (!windowed || locked) TextButton(onClick = {
                            locked = !locked; VideoPlay.lockCurScreen = locked; interaction++
                        }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Text(if (locked) "解锁" else "锁定", color = Color.White, fontSize = 12.sp)
                        }
                        if (!locked) {
                            if (!windowed && !state.preparing && !state.buffering && state.error == null) VideoIcon(
                                if (state.playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp,
                                if (state.playing) "暂停" else if (state.ended) "重播" else "播放", { VideoPlaybackBridge.toggle(); interaction++ },
                                Modifier.align(Alignment.Center).size(64.dp))
                            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f))))
                                .padding(horizontal = 16.dp, vertical = 8.dp)) {
                                if (mode == VideoPresentation.IMMERSIVE) {
                                    Text(title.orEmpty(), color = Color.White, fontSize = 16.sp, lineHeight = 22.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (state.title != title) Text(state.title, color = Color.White.copy(alpha = .8f),
                                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (windowed) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VideoIcon(if (state.playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp,
                                            if (state.playing) "暂停" else if (state.ended) "重播" else "播放", { VideoPlaybackBridge.toggle(); interaction++ })
                                        Box(Modifier.weight(1f)) { VideoProgress(state, { interaction++ }, { scrubbing = it }) }
                                        VideoIcon(R.drawable.ic_fullscreen, "全屏", { onMode(VideoPresentation.FULLSCREEN) })
                                    }
                                } else VideoProgress(state, { interaction++ }, { scrubbing = it })
                                if (mode == VideoPresentation.IMMERSIVE) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        if (hasCatalog) VideoAction(R.drawable.ic_chapter_list, "选集", Color.White) { openEpisodes() }
                                        VideoAction(R.drawable.ic_read_aloud_speed, if (state.speed == 1f) "倍速" else "${state.speed}×", Color.White,
                                            enabled = state.prepared) { panel = "speed" }
                                        if (VideoPlay.danmakuFile != null || !VideoPlay.danmakuStr.isNullOrBlank()) VideoAction(R.drawable.ic_video_danmaku,
                                            if (danmaku) "关弹幕" else "开弹幕", Color.White) { danmaku = !danmaku; VideoPlay.danmakuShow = danmaku }
                                        VideoAction(R.drawable.ic_float_window, "小窗", Color.White) { onAction(R.id.menu_float_window) }
                                    }
                                    if (episodes.size > 1) Text("上滑下一集", Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                                        color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                                }
                                if (mode == VideoPresentation.FULLSCREEN) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    if (hasCatalog) VideoTextAction("选集") { openEpisodes() }
                                    if (episodes.size > 1) VideoIcon(R.drawable.ic_skip_next, "下一集", { VideoPlaybackBridge.next(1) })
                                    VideoTextAction(if (state.speed == 1f) "倍速" else "${state.speed}×", enabled = state.prepared) { panel = "speed" }
                                    if (VideoPlay.danmakuFile != null || !VideoPlay.danmakuStr.isNullOrBlank()) {
                                        VideoTextAction(if (danmaku) "关弹幕" else "开弹幕") { danmaku = !danmaku; VideoPlay.danmakuShow = danmaku }
                                    }
                                    if (!windowed) VideoIcon(R.drawable.ic_float_window, "小窗", { onAction(R.id.menu_float_window) })
                                    VideoIcon(R.drawable.ic_fullscreen, if (windowed) "全屏" else "窗口", {
                                        onMode(if (windowed) VideoPresentation.FULLSCREEN else VideoPresentation.WINDOW)
                                    })
                                }
                            }
                        }
                    } else if (!windowed && VideoPlay.fullBottomProgressBar) {
                        LinearProgressIndicator(progress = { if (state.duration > 0) state.position.toFloat() / state.duration else 0f },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp), color = primary)
                    }
                    if (state.preparing || state.buffering) CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(32.dp), color = Color.White, strokeWidth = 2.dp)
                    state.error?.let { error ->
                        Column(Modifier.align(Alignment.Center).widthIn(max = 360.dp).padding(horizontal = 20.dp)
                            .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(12.dp)).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂时无法播放", color = Color.White, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                            Text(error, Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = .8f),
                                fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            VideoTextAction("重试") { VideoPlaybackBridge.retry() }
                        }
                    }
                    hint?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                        .background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp)).padding(12.dp, 6.dp),
                        color = Color.White, fontSize = 14.sp) }
                }
            }
            if (windowed) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(title.orEmpty(), color = ink, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                VideoPlay.book?.getRealAuthor()?.takeIf { it.isNotBlank() }?.let {
                    Text(it, Modifier.padding(top = 6.dp), color = colors.muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (VideoPlay.rssRecord != null || VideoPlay.rssStar != null) VideoAction(R.drawable.ic_star_border,
                        if (VideoPlay.rssStar != null) "已收藏" else "收藏", ink) { onAction(R.id.menu_rss_star) }
                    VideoAction(R.drawable.ic_read_aloud_speed, if (state.speed == 1f) "倍速" else "${state.speed}×", ink,
                        enabled = state.prepared) { panel = "speed" }
                    VideoAction(R.drawable.ic_video_portrait, "竖屏", ink) { onMode(VideoPresentation.IMMERSIVE) }
                    if (VideoPlay.danmakuFile != null || !VideoPlay.danmakuStr.isNullOrBlank()) {
                        VideoAction(R.drawable.ic_video_danmaku, if (danmaku) "关弹幕" else "开弹幕", ink) { danmaku = !danmaku; VideoPlay.danmakuShow = danmaku }
                    }
                    VideoAction(R.drawable.ic_float_window, "小窗", ink) { onAction(R.id.menu_float_window) }
                }
                HorizontalDivider(color = ink.copy(alpha = .08f))
                if (volumes.isNotEmpty()) ChapterStrip(volumes, VideoPlay.durVolumeIndex, { openEpisodes(it) }, true)
                if (VideoPlay.book != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (episodes.isEmpty()) "选集" else "选集 · ${episodes.size}", Modifier.weight(1f), color = ink, fontSize = 14.sp)
                        TextButton(onClick = { openEpisodes() }) { Text("全部", color = primary) }
                    }
                    if (episodes.isNotEmpty()) ChapterStrip(episodes, VideoPlay.chapterInVolumeIndex,
                        { onEpisode(VideoPlay.durVolumeIndex, it) })
                }
                if (VideoPlay.book != null) {
                    Row(Modifier.fillMaxWidth().height(48.dp).clickable { expandedIntro = !expandedIntro },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("简介", Modifier.weight(1f), color = ink, fontWeight = FontWeight.Medium)
                        Text(if (expandedIntro) "收起" else "展开", color = colors.muted, fontSize = 13.sp)
                    }
                    run {
                        val text = VideoPlay.book?.getDisplayIntro().orEmpty()
                        if (text.startsWith("<useweb>") || text.startsWith("<usehtml>") || text.startsWith("<md>")) {
                            AndroidView(factory = { (intro.parent as? android.view.ViewGroup)?.removeView(intro); intro },
                                modifier = if (expandedIntro) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().heightIn(max = 120.dp))
                        } else Text(text, maxLines = if (expandedIntro) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis, color = colors.muted, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    if (state.networkConfirmation) Dialog({ VideoPlaybackBridge.confirmNetwork(false) }) {
        NgDialog("提示", variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontSize = 18.sp, titleFontWeight = FontWeight.Medium, actions = {
            NgDialogTextActionButton("取消", { VideoPlaybackBridge.confirmNetwork(false) })
            NgDialogTextActionButton("继续播放", { VideoPlaybackBridge.confirmNetwork(true) })
        }) { Text("当前非 Wi-Fi 网络，是否继续播放？", color = Color(NgTheme.colors.onSurface), fontSize = 14.sp, lineHeight = 20.sp) }
    }
    when (panel) {
        "speed" -> VideoSpeedSheet(state.speed,
            onSelect = { speed -> VideoPlaybackBridge.speed(speed); panel = "" },
            onDismiss = { panel = "" })
        "episodes" -> VideoEpisodeSheet(catalog, panelVolume,
            VideoPlay.durVolumeIndex, VideoPlay.chapterInVolumeIndex, state.duration, onEpisode, { panel = "" })
        "settings" -> VideoSettings { panel = "" }
    }
}

@Composable
internal fun VideoIcon(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick, modifier.size(48.dp)) { Icon(painterResource(icon), label, Modifier.size(24.dp), tint = Color.White) }
}

@Composable
private fun VideoTextAction(text: String, color: Color = Color.White, enabled: Boolean = true, action: () -> Unit) {
    TextButton(onClick = action, enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp)) {
        Text(text, color = if (enabled) color else color.copy(alpha = .45f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun VideoProgress(state: VideoPlaybackState, onTouch: () -> Unit, onScrubbing: (Boolean) -> Unit) {
    DisposableEffect(Unit) { onDispose { onScrubbing(false) } }
    var seeking by remember { mutableStateOf<Float?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(videoTime((seeking?.toLong() ?: state.position)), color = Color.White, fontSize = 11.sp)
        NgSlider(seeking ?: state.position.toFloat(), { seeking = it; onScrubbing(true); onTouch() },
            0f..state.duration.coerceAtLeast(1).toFloat(), Modifier.weight(1f).padding(horizontal = 10.dp),
            variant = NgSliderVariant.COMPACT, enabled = state.duration > 0, onValueChangeFinished = {
                seeking?.let { VideoPlaybackBridge.seekTo(it.toLong()) }; seeking = null; onScrubbing(false)
            })
        Text(videoTime(state.duration), color = Color.White, fontSize = 11.sp)
    }
}

internal fun videoTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun ChapterStrip(chapters: List<BookChapter>, selected: Int, onSelect: (Int) -> Unit, volume: Boolean = false) {
    val colors = videoContentColors()
    val listState = rememberLazyListState()
    LaunchedEffect(selected, chapters) { if (chapters.isNotEmpty()) listState.scrollToItem(selected.coerceIn(chapters.indices)) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(chapters, key = { _, chapter -> chapter.url + "#" + chapter.index }) { index, chapter ->
            Box(Modifier.widthIn(min = if (volume) 80.dp else 100.dp, max = if (volume) 180.dp else 160.dp)
                .height(if (volume) 48.dp else 56.dp)
                .clip(RoundedCornerShape(8.dp)).background(if (selected == index) colors.selectedContainer else colors.cell)
                .clickable { onSelect(index) }.padding(6.dp), contentAlignment = Alignment.Center) {
                Text(chapter.title, color = if (selected == index) colors.onAccent else colors.content,
                    fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                    maxLines = if (volume) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun videoMoreItems() = buildList {
    fun item(id: Int, title: Int, icon: Int = R.drawable.ic_cfg_about) = add(NgExpandableActionMenuItem(id, title, icon))
    if ((VideoPlay.source as? BookSource)?.customButton == true) item(R.id.menu_custom_btn, R.string.custom_button, R.drawable.ic_custom)
    item(R.id.menu_config_settings, R.string.config_settings, R.drawable.ic_settings)
    if (!VideoPlay.source?.loginUrl.isNullOrBlank()) item(R.id.menu_login, R.string.login, R.drawable.ic_lock_outline)
    item(R.id.menu_copy_video_url, R.string.copy_play_url, R.drawable.ic_copy)
    item(R.id.menu_open_other_video_player, R.string.open_other_video_player)
    item(R.id.menu_edit_source, R.string.edit_book_source, R.drawable.ic_edit)
    item(R.id.menu_log, R.string.log, R.drawable.ic_bug_report)
    item(R.id.menu_network_log, R.string.network_request_log, R.drawable.ic_network_check)
}

@Composable
private fun VideoGestureLayer(state: VideoPlaybackState, mode: VideoPresentation, locked: Boolean,
                              enabled: Boolean, onTap: () -> Unit, onHint: (String?) -> Unit) {
    val current by rememberUpdatedState(state)
    val context = LocalContext.current
    val canSwitchEpisode = VideoPlay.episodes.orEmpty().size > 1
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    Box(Modifier.fillMaxSize().pointerInput(locked, enabled) {
        if (enabled) detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (!locked) VideoPlaybackBridge.toggle() },
            onLongPress = { if (!locked && current.playing) {
                VideoPlaybackBridge.speed(VideoPlay.longPressSpeed / 10f, temporary = true)
                onHint("${VideoPlay.longPressSpeed / 10f}×")
            } }, onPress = { try { awaitRelease() } finally { VideoPlaybackBridge.restoreSpeed(); onHint(null) } })
    }.pointerInput(mode, locked, enabled, canSwitchEpisode) {
        if (locked || !enabled) return@pointerInput
        var startX = 0f; var dx = 0f; var dy = 0f; var position = 0L; var axis = 0
        var volume = 0; var brightness = .5f
        val activity = context as? Activity
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        detectDragGestures(onDragStart = {
            startX = it.x; dx = 0f; dy = 0f; axis = 0; position = current.position
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            brightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: .5f
        }, onDrag = { change, amount ->
            change.consume(); dx += amount.x; dy += amount.y
            if (axis == 0) axis = if (abs(dx) > abs(dy)) 1 else 2
            if (axis == 1) onHint(videoTime((position + dx / size.width * current.duration).toLong().coerceIn(0, current.duration.coerceAtLeast(0))))
            else if (mode == VideoPresentation.IMMERSIVE && canSwitchEpisode && startX in size.width * .2f..size.width * .8f) {
                onHint(if (dy < 0) "下一集" else "上一集")
            } else if (startX > size.width / 2) {
                val target = (volume - dy / size.height * maxVolume).toInt().coerceIn(0, maxVolume)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                onHint("音量 ${(target * 100 / maxVolume.coerceAtLeast(1))}%")
            } else {
                val target = (brightness - dy / size.height).coerceIn(.01f, 1f)
                activity?.window?.let { it.attributes = it.attributes.apply { screenBrightness = target } }
                onHint("亮度 ${(target * 100).toInt()}%")
            }
        }, onDragCancel = { onHint(null) }, onDragEnd = {
            if (axis == 1) VideoPlaybackBridge.seekTo((position + dx / size.width * current.duration).toLong())
            else if (mode == VideoPresentation.IMMERSIVE && canSwitchEpisode && startX in size.width * .2f..size.width * .8f && abs(dy) >= 64.dp.toPx()) {
                VideoPlaybackBridge.next(if (dy < 0) 1 else -1)
            }
            onHint(null)
        })
    })
}

@Composable
private fun VideoAction(icon: Int, title: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val contentColor = if (enabled) color else color.copy(alpha = .45f)
    Column(Modifier.widthIn(min = 56.dp).heightIn(min = 48.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = contentColor)
        Text(title, Modifier.padding(top = 6.dp), color = contentColor, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
