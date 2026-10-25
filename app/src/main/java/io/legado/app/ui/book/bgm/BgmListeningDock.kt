package io.legado.app.ui.book.bgm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.bgm.BgmPlaybackController
import io.legado.app.help.bgm.BgmPlaybackState
import io.legado.app.help.bgm.BgmPlaybackStatus
import io.legado.app.help.bgm.BgmRepository
import io.legado.app.help.bgm.BgmSettings
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.LocalNgPaneBounds
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

/** Position ticks stay in this leaf; the queue and catalogue do not collect them. */
@Composable
internal fun BgmListeningDock(
    settings: BgmSettings,
    playback: BgmPlaybackState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val progress by BgmRepository.progress.collectAsState()
    val track = settings.currentTrack
    val revision = settings.selectionRevision
    val matchingProgress = track != null && progress.trackKey == track.key &&
        progress.selectionRevision == revision
    val duration = (if (matchingProgress && progress.durationMs > 0) progress.durationMs
        else track?.durationMs ?: 0).coerceAtLeast(0)
    val canSeek = matchingProgress && progress.canSeek && duration > 0
    val canToggle = track != null && settings.enabled && (!matchingProgress || progress.canTogglePause)
    val playing = matchingProgress && !progress.pausedByUser &&
        playback.trackKey == track?.key && playback.status == BgmPlaybackStatus.PLAYING
    val showPause = matchingProgress && canToggle && !progress.pausedByUser &&
        playback.trackKey == track?.key &&
        playback.status in listOf(BgmPlaybackStatus.PLAYING, BgmPlaybackStatus.LOADING)
    var seekPosition by remember(track?.key, revision) { mutableStateOf<Long?>(null) }
    val position = (seekPosition ?: if (matchingProgress) progress.positionMs else 0)
        .coerceIn(0, duration)
    val primary = Color(NgTheme.colors.primary)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    LaunchedEffect(canSeek) { if (!canSeek) seekPosition = null }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            BgmCover(track?.coverUrl.orEmpty(), Modifier.size(42.dp), cornerRadius = 7.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(track?.title ?: "未选择音乐", color = Color(NgTheme.colors.onSurface), fontSize = 15.sp,
                    lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontFamily = NgTheme.fontFamily)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(listOfNotNull(track?.platform?.bgmTitle(),
                        if (playing) track?.artist?.takeIf { it.isNotBlank() }
                        else playbackLabel(settings, playback)).joinToString(" · "),
                        modifier = Modifier.weight(1f, fill = false), color = secondary,
                        fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track != null) BgmTrackVipBadge(track)
                }
            }
            Spacer(Modifier.width(4.dp))
            ListeningAction(R.drawable.ic_skip_previous, "上一曲", settings.queue.size > 1, onPrevious)
            ListeningAction(if (showPause) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp,
                if (showPause) "暂停配乐" else "继续配乐", canToggle,
                onClick = { track?.let { BgmPlaybackController.toggleCurrentPause(context, it.key, revision) } },
                tint = primary, outlined = true)
            ListeningAction(R.drawable.ic_skip_next, "下一曲", settings.queue.size > 1, onNext)
        }
        if (settings.enabled && playback.status == BgmPlaybackStatus.ERROR && playback.message.isNotBlank()) {
            Text(playback.message, color = Color(NgTheme.colors.error), fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ListeningTime(listeningTime(position))
            key(track?.key, revision) {
                NgSlider(position.toFloat(), { seekPosition = it.toLong().coerceIn(0, duration) },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = "配乐进度" },
                    variant = NgSliderVariant.MANGA_PROGRESS, enabled = canSeek,
                    onValueChangeFinished = {
                        val target = seekPosition
                        if (target != null && track != null) {
                            BgmPlaybackController.seekCurrentTo(target, track.key, revision)
                        }
                        seekPosition = null
                    })
            }
            ListeningTime(if (duration > 0) listeningTime(duration) else "--:--")
            ListeningVolume(settings.volume)
        }
    }
}

@Composable
private fun ListeningAction(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit,
    tint: Color = Color(NgTheme.colors.onSurface), outlined: Boolean = false) {
    val color = tint.copy(alpha = if (enabled) 1f else .35f)
    Box(Modifier.size(44.dp).clip(CircleShape)
        .semantics { contentDescription = description }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), Alignment.Center) {
        Box(Modifier.size(34.dp).then(if (outlined) Modifier.border(1.dp, color, CircleShape) else Modifier),
            Alignment.Center) {
            BgmIcon(icon, modifier = Modifier.size(22.dp), tint = color)
        }
    }
}

@Composable
private fun ListeningTime(text: String) {
    Text(text, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 10.sp, lineHeight = 14.sp,
        maxLines = 1, modifier = Modifier.widthIn(min = 24.dp))
}

@Composable
private fun ListeningVolume(savedVolume: Float) {
    var open by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(savedVolume) }
    var changing by remember { mutableStateOf(false) }
    var audibleVolume by rememberSaveable { mutableFloatStateOf(savedVolume.takeIf { it > 0f } ?: .05f) }
    LaunchedEffect(savedVolume) {
        if (!changing) volume = savedVolume
        if (savedVolume > 0f) audibleVolume = savedVolume
    }
    Box(Modifier.size(44.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).semantics {
            contentDescription = "配乐音量"
            stateDescription = "${(volume * 100).roundToInt()}%"
        }.clickable(role = Role.Button) { open = !open }, Alignment.Center) {
            ListeningVolumeIcon(volume == 0f)
        }
        if (open) {
            val density = LocalDensity.current
            val pane = LocalNgPaneBounds.current
            val paneSize = pane?.let { IntSize(it.width, it.height) }
            val margin = with(density) { 12.dp.roundToPx() }
            val gap = with(density) { 8.dp.roundToPx() }
            val position = remember(margin, gap, paneSize) { ListeningVolumePosition(margin, gap, paneSize) }
            val popupWidth = if (pane == null) 64.dp else 64.dp.coerceAtMost(
                (with(density) { pane.width.toDp() } - 24.dp).coerceAtLeast(1.dp)
            )
            val popupHeight = if (pane == null) 216.dp else 216.dp.coerceAtMost(
                (with(density) { pane.height.toDp() } - 24.dp).coerceAtLeast(1.dp)
            )
            Popup(position, onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
                NgFormPanel(Modifier.width(popupWidth)) {
                    Column(Modifier.fillMaxWidth().height(popupHeight).padding(top = 8.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(volume * 100).roundToInt()}%", color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 11.sp, lineHeight = 20.sp, maxLines = 1,
                            modifier = Modifier.height(20.dp))
                        ListeningVolumeTrack(volume, onChange = { changing = true; volume = it },
                            onFinished = { changing = false; BgmRepository.setVolume(volume) },
                            onCancel = { changing = false; volume = savedVolume },
                            modifier = Modifier.width(48.dp).weight(1f))
                        Box(Modifier.size(48.dp).clip(CircleShape).semantics {
                            contentDescription = if (volume == 0f) "取消配乐静音" else "配乐静音"
                        }.clickable(role = Role.Button) {
                            if (volume > 0f) { audibleVolume = volume; volume = 0f }
                            else volume = audibleVolume
                            changing = false
                            BgmRepository.setVolume(volume)
                        }, Alignment.Center) { ListeningVolumeIcon(volume == 0f) }
                    }
                }
            }
        }
    }
}

/** A vertical rail keeps pointer coordinates and accessibility progress in the same orientation. */
@Composable
private fun ListeningVolumeTrack(value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit,
    onCancel: () -> Unit, modifier: Modifier) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val cancel by rememberUpdatedState(onCancel)
    val primary = Color(NgTheme.colors.primary)
    val inactive = Color(NgTheme.colors.onSurface).copy(alpha = .16f)
    val thumbRadius = 11.dp
    Canvas(modifier.semantics {
        contentDescription = "配乐音量滑块"
        stateDescription = "${(value * 100).roundToInt()}%"
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        setProgress { target ->
            if (!target.isFinite()) false else { change(target.coerceIn(0f, 1f)); finish(); true }
        }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val inset = thumbRadius.toPx()
            fun update(y: Float) {
                val ratio = 1f - (y - inset) / (size.height - 2 * inset).coerceAtLeast(1f)
                change(ratio.coerceIn(0f, 1f))
            }
            down.consume()
            update(down.position.y)
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    update(pointer.position.y)
                    pointer.consume()
                    if (!pointer.pressed) { released = true; break }
                }
                if (released) finish() else cancel()
            } finally {
                if (!released) cancel()
            }
        }
    }) {
        val x = size.width / 2
        val top = thumbRadius.toPx()
        val bottom = (size.height - top).coerceAtLeast(top)
        val y = bottom - (bottom - top) * value.coerceIn(0f, 1f)
        val stroke = 6.dp.toPx()
        drawLine(inactive, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
        drawLine(primary, Offset(x, bottom), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(primary.copy(alpha = .18f), thumbRadius.toPx(), Offset(x, y))
        drawCircle(primary, 7.dp.toPx(), Offset(x, y))
    }
}

@Composable
private fun ListeningVolumeIcon(muted: Boolean) {
    val tint = Color(NgTheme.colors.onSurface)
    Box(Modifier.size(22.dp)) {
        BgmIcon(R.drawable.ic_bgm_preview_volume, modifier = Modifier.fillMaxSize(), tint = tint)
        if (muted) Canvas(Modifier.fillMaxSize()) {
            drawLine(tint, Offset(size.width * .15f, size.height * .15f),
                Offset(size.width * .85f, size.height * .85f), 1.6.dp.toPx(), StrokeCap.Round)
        }
    }
}

private class ListeningVolumePosition(
    private val margin: Int,
    private val gap: Int,
    private val paneSize: IntSize? = null,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val availableSize = paneSize?.let {
            IntSize(minOf(it.width, windowSize.width), minOf(it.height, windowSize.height))
        } ?: windowSize
        val maxX = (availableSize.width - margin - popupContentSize.width).coerceAtLeast(margin)
        val maxY = (availableSize.height - margin - popupContentSize.height).coerceAtLeast(margin)
        val x = anchorBounds.center.x - popupContentSize.width / 2
        return IntOffset(x.coerceIn(margin, maxX),
            (anchorBounds.top - gap - popupContentSize.height).coerceIn(margin, maxY))
    }
}

private fun playbackLabel(settings: BgmSettings, state: BgmPlaybackState): String = when {
    settings.currentTrack == null -> "未设置"
    !settings.enabled -> "已关闭"
    state.trackKey != null && state.trackKey != settings.currentTrack?.key -> "准备中"
    state.status == BgmPlaybackStatus.ERROR -> "暂不可用"
    state.status == BgmPlaybackStatus.PLAYING -> "播放中"
    state.status == BgmPlaybackStatus.LOADING -> "准备中"
    state.status == BgmPlaybackStatus.PAUSED -> "已暂停"
    else -> if (state.trackKey == null) "待播放" else "等待听书"
}

private fun listeningTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
