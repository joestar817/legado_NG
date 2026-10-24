package io.legado.app.ui.book.bgm

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme

/**
 * Native rendering of svg-spinners/3-dots-fade by Utkarsh Verma (MIT).
 * Original: https://github.com/n3r4zzurr0/svg-spinners/blob/main/svg-css/3-dots-fade.svg
 * License: assets/licenses/svg-spinners-MIT.txt
 */
@Composable
internal fun BgmSearchLoadingIndicator(modifier: Modifier = Modifier) {
    val color = Color(NgTheme.colors.primary)
    val phase = if (NgTheme.snapshot.isEInk) null else {
        rememberInfiniteTransition(label = "bgm_search_loading").animateFloat(
            initialValue = 0f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "bgm_search_loading_phase",
        )
    }
    Box(
        modifier.semantics {
            contentDescription = "加载中"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            // Read the animated value only while drawing; the result list does not recompose per frame.
            val elapsed = phase?.value
            val unit = size.minDimension / 24f
            repeat(3) { index ->
                val opacity = if (elapsed == null) 1f else {
                    val time = (elapsed + 800f - index * 150f) % 800f
                    (1f - .8f * (time / 750f)).coerceAtLeast(.2f)
                }
                drawCircle(
                    color = color.copy(alpha = opacity),
                    radius = 3f * unit,
                    center = Offset(size.width / 2f + (index - 1) * 8f * unit, size.height / 2f),
                )
            }
        }
    }
}
