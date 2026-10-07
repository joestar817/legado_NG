package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal fun homeListeningLargeDesignSupported(width: Dp, fontScale: Float): Boolean =
    width >= 320.dp && fontScale <= 1.15f

internal object HomeListeningStoryGeometry {
    val inset = 14.dp
    val coverWidth = 60.dp
    val textGap = 10.dp
    val textWidth = 116.dp
    val foregroundEnd = inset + coverWidth + textGap + textWidth
    fun artworkWidth(cardWidth: Dp) = (cardWidth - foregroundEnd - 2.dp).coerceAtMost(145.dp)
}

/** Artwork sits behind native content, so it neither inflates rows nor intercepts actions. */
@Composable
internal fun BoxScope.HomeListeningLargeArtwork(styleId: String, cardWidth: Dp) {
    when (styleId) {
        "storybook" -> {
            val scale = cardWidth.value / 340f
            val sceneWidth = minOf(HomeListeningStoryGeometry.artworkWidth(cardWidth), 145.dp * scale)
                .coerceAtLeast(0.dp)
            // Only the outermost 80 source pixels contain expendable foliage/empty edge.
            // A taller crop would cut the book/left hand, so narrow cards reduce scene height.
            val sceneHeight = minOf(129.dp * scale, sceneWidth * (637f / (768f - 80f)))
            Image(
                painterResource(R.drawable.ng_home_listening_large_paper_bottom), null,
                contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, alpha = 0.70f,
                modifier = Modifier.align(Alignment.TopEnd).size(100.dp * scale, 68.dp * scale).rotate(180f),
            )
            Image(
                painterResource(R.drawable.ng_home_listening_large_paper_bottom), null,
                contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, alpha = 0.75f,
                modifier = Modifier.align(Alignment.BottomStart).size(78.dp * scale, 49.dp * scale),
            )
            if (sceneWidth > 0.dp) Image(
                painterResource(R.drawable.ng_home_listening_large_story_scene), null,
                contentScale = ContentScale.Crop, alignment = Alignment.BottomEnd,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .width(sceneWidth).height(sceneHeight).clipToBounds(),
            )
        }
        "paper" -> {
            Image(
                painterResource(R.drawable.ng_home_listening_large_paper_top), null,
                contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, alpha = 0.85f,
                modifier = Modifier.align(Alignment.TopEnd).size(97.dp, 75.dp),
            )
            Image(
                painterResource(R.drawable.ng_home_listening_large_paper_bottom), null,
                contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, alpha = 0.75f,
                modifier = Modifier.align(Alignment.BottomStart).size(72.dp, 57.dp),
            )
        }
        "night" -> {
            val scale = cardWidth.value / 340f
            Image(
                painterResource(R.drawable.ng_home_listening_large_night_leaves), null,
                contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, alpha = 0.55f,
                modifier = Modifier.align(Alignment.TopEnd).size(107.dp * scale, 63.dp * scale),
            )
            Image(
                painterResource(R.drawable.ng_home_listening_large_night_leaves), null,
                contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, alpha = 0.60f,
                modifier = Modifier.align(Alignment.BottomStart)
                    .size(102.dp * scale, 53.dp * scale).clipToBounds().rotate(180f),
            )
            Image(
                painterResource(R.drawable.ng_home_listening_large_wood), null,
                contentScale = ContentScale.Crop, alpha = 0.60f,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(17.dp * scale),
            )
            Image(
                painterResource(R.drawable.ng_home_listening_night_desk), null,
                contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd,
                // Preserve the complete lamp in the existing 59dp band. Moving four dp right
                // clips peripheral foliage at the card edge without cutting shade/books/base.
                modifier = Modifier.align(Alignment.BottomEnd)
                    .size(123.dp * scale, minOf(59.dp * scale, 59.dp)).offset(x = 4.dp * scale),
            )
        }
    }
}
