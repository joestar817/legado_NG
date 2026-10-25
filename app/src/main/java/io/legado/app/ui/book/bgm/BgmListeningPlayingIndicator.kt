package io.legado.app.ui.book.bgm

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.PI
import kotlin.math.sin

/** The current queue item keeps its marker while paused; only actual playback animates it. */
@Composable
internal fun BgmListeningPlayingIndicator(playing: Boolean, modifier: Modifier = Modifier) {
    val color = Color(NgTheme.colors.primary)
    val phase = if (playing && !NgTheme.snapshot.isEInk) {
        rememberInfiniteTransition(label = "bgm_queue_playing").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "bgm_queue_playing_phase",
        )
    } else null
    Canvas(modifier.size(18.dp).semantics {
        contentDescription = if (playing) "正在播放" else "当前歌曲"
    }) {
        // Read the phase while drawing so the queue does not recompose on each frame.
        val time = phase?.value
        val stroke = 2.dp.toPx()
        val step = 4.dp.toPx()
        val left = (size.width - step * 3) / 2
        val bottom = size.height - 2.dp.toPx()
        repeat(4) { index ->
            val fraction = if (time == null) {
                when (index) { 0 -> .35f; 1 -> .65f; 2 -> 1f; else -> .5f }
            } else .3f + .7f * ((sin(time + index * 1.7f) + 1f) / 2f)
            val height = 13.dp.toPx() * fraction
            val x = left + step * index
            drawLine(color, Offset(x, bottom), Offset(x, bottom - height), stroke, StrokeCap.Round)
        }
    }
}
