package io.legado.app.ui.book.bgm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import io.legado.app.help.bgm.BgmPlaybackState
import io.legado.app.help.bgm.BgmPlaybackStatus
import io.legado.app.help.bgm.BgmPreviewController
import io.legado.app.help.bgm.MusicTrack
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

/** 104 dp player plus its existing 8 dp top/bottom host margin. */
internal val BgmPreviewBottomSpace = 120.dp

/** Frequent position/volume updates are collected here, outside the catalogue composition. */
@Composable
internal fun BgmPreviewBar(
    track: MusicTrack,
    state: BgmPlaybackState,
    controller: BgmPreviewController,
    modifier: Modifier = Modifier,
) {
    val progress by controller.progress.collectAsState()
    val volume by controller.volume.collectAsState()
    var volumeOpen by remember(track.key) { mutableStateOf(false) }
    var seekPosition by remember(track.key) { mutableStateOf<Long?>(null) }
    val primary = Color(NgTheme.colors.primary)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val duration = progress.durationMs.coerceAtLeast(0)
    val scale = if (progress.isPreview) maxOf(duration, progress.fullDurationMs) else duration
    val position = (seekPosition ?: progress.positionMs).coerceIn(0, duration)
    val seekEnabled = duration > 0 && progress.canSeek && state.status != BgmPlaybackStatus.LOADING
    LaunchedEffect(seekEnabled) { if (!seekEnabled) seekPosition = null }

    Box(modifier.fillMaxWidth()) {
        NgFormPanel {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 2.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    BgmCover(track.coverUrl, Modifier.size(46.dp), cornerRadius = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(track.title, color = Color(NgTheme.colors.onSurface), fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontFamily = NgTheme.fontFamily)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (state.status == BgmPlaybackStatus.LOADING) "加载中…" else track.artist,
                                Modifier.weight(1f, fill = false), color = secondary, fontSize = 12.sp, lineHeight = 16.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (progress.isPreview) Text("片段试听", color = primary, fontSize = 10.sp, lineHeight = 14.sp,
                                maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                    .background(primary.copy(alpha = .1f)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    PreviewActionButton(
                        if (state.status == BgmPlaybackStatus.PAUSED) R.drawable.ic_play_24dp else R.drawable.ic_pause_24dp,
                        if (state.status == BgmPlaybackStatus.PAUSED) "继续试听" else "暂停试听",
                        controller::togglePause, primary,
                        enabled = state.status == BgmPlaybackStatus.PLAYING || state.status == BgmPlaybackStatus.PAUSED,
                    )
                    PreviewActionButton(R.drawable.ic_baseline_close, "关闭试听", controller::stop,
                        Color(NgTheme.colors.onSurface))
                }
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PreviewTime(previewTime(position))
                    PreviewProgressTrack(track.key, position, duration, scale, progress.isPreview, seekEnabled,
                        onChange = { seekPosition = it }, onFinished = {
                            seekPosition?.let { controller.seekTo(it, track.key) }
                            seekPosition = null
                        }, onCancel = { seekPosition = null }, modifier = Modifier.weight(1f))
                    PreviewTime(if (scale > 0) previewTime(scale) else "--:--")
                    Row(Modifier.height(44.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .semantics(mergeDescendants = true) {
                            contentDescription = "试听音量"
                            stateDescription = "${(volume * 100).roundToInt()}%"
                        }.clickable(role = Role.Button) { volumeOpen = !volumeOpen }
                        .padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        PreviewVolumeIcon(volume == 0f, Color(NgTheme.colors.onSurface))
                        Text("${(volume * 100).roundToInt()}%", color = secondary, fontSize = 11.sp, lineHeight = 16.sp,
                            maxLines = 1)
                    }
                }
            }
        }
        if (volumeOpen) PreviewVolumePopover(volume, controller::setVolume, controller::toggleMute,
            onDismiss = { volumeOpen = false })
    }
}

@Composable
private fun PreviewActionButton(icon: Int, description: String, onClick: () -> Unit,
    tint: Color, enabled: Boolean = true) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(34.dp).background(tint.copy(alpha = .06f), CircleShape), Alignment.Center) {
            BgmIcon(icon, description, Modifier.size(20.dp), tint.copy(alpha = if (enabled) 1f else .38f))
        }
    }
}

@Composable
private fun PreviewTime(text: String) {
    Text(text, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 10.sp,
        lineHeight = 14.sp, maxLines = 1, modifier = Modifier.widthIn(min = 22.dp))
}

/** A local range-limited timeline; the generic slider has no separately shaded playable endpoint. */
@Composable
private fun PreviewProgressTrack(
    trackKey: String, position: Long, duration: Long, scale: Long, fragment: Boolean, enabled: Boolean,
    onChange: (Long) -> Unit, onFinished: () -> Unit, onCancel: () -> Unit, modifier: Modifier,
) {
    val primary = Color(NgTheme.colors.primary)
    val inactive = Color(NgTheme.colors.onSurface).copy(alpha = .16f)
    val surface = ngDrawerContentCardColor()
    val fraction = if (scale > 0) (position.toDouble() / scale).toFloat().coerceIn(0f, 1f) else 0f
    val cutoff = if (scale > 0) (duration.toDouble() / scale).toFloat().coerceIn(0f, 1f) else 0f
    val showCutoff = fragment && duration > 0 && scale > 0
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    val cancel by rememberUpdatedState(onCancel)
    BoxWithConstraints(modifier.height(44.dp)) {
        if (showCutoff) {
            val labelWidth = 48.dp
            val labelOffset = (6.dp + (maxWidth - 12.dp).coerceAtLeast(0.dp) * cutoff - labelWidth / 2)
                .coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))
            Text("试听截止", Modifier.offset(x = labelOffset).width(labelWidth),
                color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 9.sp, lineHeight = 12.sp,
                maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = if (showCutoff) "试听进度，${previewTime(duration)}截止" else "试听进度"
            if (duration > 0) {
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..duration.toFloat())
            } else progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            if (!enabled) disabled()
            setProgress { target ->
                if (!enabled || !target.isFinite()) false else {
                    change(target.toLong().coerceIn(0, duration)); finish(); true
                }
            }
        }.pointerInput(trackKey, enabled, duration, scale) {
            if (!enabled || scale <= 0) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val inset = 6.dp.toPx()
                fun update(x: Float) {
                    val ratio = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, cutoff)
                    change((ratio * scale).toLong().coerceIn(0, duration))
                }
                down.consume()
                update(down.position.x)
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        update(pointer.position.x)
                        pointer.consume()
                        if (!pointer.pressed) { released = true; break }
                    }
                    if (released) finish() else cancel()
                } finally {
                    if (!released) cancel()
                }
            }
        }) {
            val radius = 8.dp.toPx()
            val start = 6.dp.toPx()
            val length = (size.width - start * 2).coerceAtLeast(0f)
            val y = size.height / 2
            val stroke = 3.dp.toPx()
            drawLine(inactive, Offset(start, y), Offset(start + length, y), stroke, StrokeCap.Round)
            if (scale > 0 && duration > 0) {
                val alpha = if (enabled) 1f else .45f
                drawLine(primary.copy(alpha = .22f * alpha), Offset(start, y),
                    Offset(start + length * cutoff, y), stroke, StrokeCap.Round)
                drawLine(primary.copy(alpha = alpha), Offset(start, y),
                    Offset(start + length * fraction, y), stroke, StrokeCap.Round)
                if (showCutoff) {
                    drawCircle(surface, 4.5.dp.toPx(), Offset(start + length * cutoff, y))
                    drawCircle(primary.copy(alpha = alpha), 3.dp.toPx(), Offset(start + length * cutoff, y))
                }
                drawCircle(surface, radius, Offset(start + length * fraction, y))
                drawCircle(primary.copy(alpha = alpha), 6.dp.toPx(), Offset(start + length * fraction, y))
            }
        }
    }
}

@Composable
private fun PreviewVolumePopover(volume: Float, onChange: (Float) -> Unit, onMute: () -> Unit, onDismiss: () -> Unit) {
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(margin, gap) { PreviewPopoverPosition(margin, gap) }
    val width = (LocalConfiguration.current.screenWidthDp - 24).coerceIn(0, 212).dp
    val surface = ngDrawerContentCardColor()
    Popup(position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Column(Modifier.width(width)) {
            NgFormPanel {
                Column(Modifier.padding(end = 12.dp, top = 10.dp, bottom = 2.dp)) {
                    Text("试听音量", Modifier.padding(start = 12.dp), color = Color(NgTheme.colors.onSurface),
                        fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).semantics {
                            contentDescription = if (volume == 0f) "取消试听静音" else "试听静音"
                        }.clickable(role = Role.Button, onClick = onMute), Alignment.Center) {
                            PreviewVolumeIcon(volume == 0f, Color(NgTheme.colors.onSurface))
                        }
                        NgSlider(volume, onChange, 0f..1f,
                            modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = "试听音量滑块" },
                            variant = NgSliderVariant.AUDIO_PREVIEW)
                        Text("${(volume * 100).roundToInt()}%", Modifier.width(32.dp),
                            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 11.sp, lineHeight = 16.sp,
                            maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
            Canvas(Modifier.align(Alignment.End).padding(end = 26.dp).size(14.dp, 6.dp)) {
                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, surface)
            }
        }
    }
}

/** The audition entry and popover share the same light outline speaker. */
@Composable
private fun PreviewVolumeIcon(muted: Boolean, tint: Color) {
    Box(Modifier.size(20.dp)) {
        BgmIcon(R.drawable.ic_bgm_preview_volume, modifier = Modifier.fillMaxSize(), tint = tint)
        if (muted) Canvas(Modifier.fillMaxSize()) {
            drawLine(tint, Offset(size.width * .15f, size.height * .15f),
                Offset(size.width * .85f, size.height * .85f), 1.8.dp.toPx(), StrokeCap.Round)
        }
    }
}

private class PreviewPopoverPosition(private val margin: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)
        val maxY = (windowSize.height - margin - popupContentSize.height).coerceAtLeast(margin)
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        return IntOffset(x.coerceIn(margin, maxX),
            (anchorBounds.top - gap - popupContentSize.height).coerceIn(margin, maxY))
    }
}

private fun previewTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
