package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

/** Reading uses the same skin material as listening, with the actual title slot coordinates. */
@Composable
internal fun HomeReadingMaterialBackdrop(
    styleId: String,
    modifier: Modifier = Modifier,
    headerHeight: Dp = HomeWidgetSkinHeaderHeight,
    headerOffset: Dp = 0.dp,
) {
    HomeWidgetSkinBackdrop(styleId, headerHeight, headerOffset, modifier)
}

/** Root layout owns slot size and placement; each skin fits its complete transparent artwork. */
@Composable
internal fun HomeReadingArtwork(variant: HomeWidgetVariant, modifier: Modifier = Modifier) {
    val resource = when (variant.styleId) {
        "storybook" -> R.drawable.ng_home_reading_story_owl
        "night" -> R.drawable.ng_home_listening_night_desk
        else -> return
    }
    Image(
        painter = homeWidgetArtworkPainter(resource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // Mirror the owl toward the records while keeping its visual right edge anchored.
        alignment = if (variant.styleId == "storybook") Alignment.BottomStart else Alignment.BottomEnd,
        modifier = if (variant.styleId == "storybook") {
            modifier.graphicsLayer { scaleX = -1f }
        } else {
            modifier
        },
    )
}
