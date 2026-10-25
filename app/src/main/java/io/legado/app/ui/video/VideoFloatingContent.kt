package io.legado.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.gsyVideo.VideoPlaybackBridge
import kotlinx.coroutines.delay

@Composable
fun VideoFloatingContent(owner: Any, onClose: () -> Unit, onOpen: () -> Unit,
                         onDragStart: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val state by VideoPlaybackBridge.state.collectAsState()
    var controls by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    val stablePlayback = state.playing && !state.preparing && !state.buffering && state.error == null && !state.networkConfirmation
    LaunchedEffect(stablePlayback) { if (!stablePlayback) controls = true }
    LaunchedEffect(controls, stablePlayback, dragging) {
        if (controls && stablePlayback && !dragging) { delay(2500); controls = false }
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
        VideoRenderHost(owner, state, false, Modifier.fillMaxSize(), allowDanmaku = false)
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { controls = !controls }
        }.pointerInput(Unit) {
            detectDragGestures(onDragStart = { dragging = true; controls = true; onDragStart() },
                onDragEnd = { dragging = false; onDragEnd() }, onDragCancel = { dragging = false; onDragEnd() },
                onDrag = { change, amount -> change.consume(); onDrag(amount.x, amount.y) })
        })
        if (controls) {
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color.Black.copy(alpha = .3f))) {
                VideoIcon(R.drawable.ic_baseline_close, "关闭", onClose, Modifier.size(36.dp))
                Spacer(Modifier.weight(1f))
                VideoIcon(R.drawable.ic_fullscreen, "返回播放器", onOpen, Modifier.size(36.dp))
            }
            if (!state.preparing && !state.buffering && state.error == null) VideoIcon(if (state.playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp,
                if (state.playing) "暂停" else if (state.ended) "重播" else "播放", { VideoPlaybackBridge.toggle() }, Modifier.align(Alignment.Center))
        }
        if (state.preparing || state.buffering) CircularProgressIndicator(
            Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
        if (state.error != null) Column(Modifier.align(Alignment.Center)
            .background(Color.Black.copy(alpha = .8f)).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("播放失败", color = Color.White)
            TextButton(onClick = { VideoPlaybackBridge.retry() }) { Text("重试", color = Color.White) }
        }
        if (state.networkConfirmation) Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .9f)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("当前非 Wi-Fi 网络", color = Color.White)
            Row {
                TextButton(onClick = { VideoPlaybackBridge.confirmNetwork(false) }) { Text("取消") }
                TextButton(onClick = { VideoPlaybackBridge.confirmNetwork(true) }) { Text("继续播放") }
            }
        }
        LinearProgressIndicator(progress = { if (state.duration > 0) state.position.toFloat() / state.duration else 0f },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.5.dp))
    }
}
