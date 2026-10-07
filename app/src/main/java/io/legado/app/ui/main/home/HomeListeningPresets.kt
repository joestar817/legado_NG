package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

/** Presets reflow the existing native content; artwork has its own noninteractive space. */
@Composable
internal fun HomeListeningPreset(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    appearance: HomeListeningAppearance,
) {
    HomeListeningReferenceContent(variant, context, showPreparing, appearance)
}

/** Keep the existing small presets and the wide-font safe arrangement. */
@Composable
private fun HomeListeningPresetLegacy(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    appearance: HomeListeningAppearance,
) {
    val large = variant.size == HomeWidgetSize.LARGE
    val palette = appearance.palette
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (large) 18.dp else 12.dp,
            top = 8.dp,
            end = if (large) 18.dp else 12.dp,
            bottom = 12.dp,
        ),
    ) {
        when (variant.styleId) {
            "storybook" -> {
                if (large) {
                    HomeListeningStoryLarge(context, showPreparing, palette)
                } else {
                    HomeListeningPresetContent(context, showPreparing, palette, large = false)
                    Spacer(Modifier.height(8.dp))
                    HomeListeningDecoration(R.drawable.ng_home_listening_storybook, 88.dp)
                }
            }
            "paper" -> {
                HomeListeningPresetContent(context, showPreparing, palette, large)
            }
            "night" -> {
                HomeListeningPresetContent(context, showPreparing, palette, large, stacked = !large)
                Spacer(Modifier.height(8.dp))
                HomeListeningDecoration(R.drawable.ng_home_listening_night_desk, if (large) 56.dp else 64.dp)
            }
        }
    }
}

@Composable
private fun HomeListeningStoryLarge(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val illustrationWidth = 104.dp
        val textAndCoverWidth = 64.dp + 14.dp + 84.dp * fontScale
        if (fontScale <= 1.15f && maxWidth >= textAndCoverWidth + 12.dp + illustrationWidth) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (context.listening.book == null) {
                        HomeListeningEmpty(large = true, palette = palette)
                    } else {
                        HomeListeningMetadata(context, showPreparing, Modifier.fillMaxWidth(), palette = palette)
                        Spacer(Modifier.height(12.dp))
                        HomeListeningPresetActions(context, showPreparing, palette)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Image(
                    painter = painterResource(R.drawable.ng_home_listening_storybook_portrait),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(illustrationWidth).height(136.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                HomeListeningPresetContent(context, showPreparing, palette, large = true)
                Spacer(Modifier.height(8.dp))
                HomeListeningDecoration(R.drawable.ng_home_listening_storybook, 92.dp)
            }
        }
    }
}

@Composable
private fun HomeListeningPresetContent(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette,
    large: Boolean,
    stacked: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (context.listening.book == null) {
            HomeListeningEmpty(large = large, palette = palette)
        } else {
            val primaryWidth = (56.dp * fontScale).coerceAtLeast(48.dp)
            val secondaryWidth = (64.dp * fontScale).coerceAtLeast(48.dp)
            val actionsWidth = primaryWidth + secondaryWidth + 8.dp
            val metadataWidth = 64.dp + 14.dp + 84.dp * fontScale
            if (large && maxWidth >= metadataWidth + 12.dp + actionsWidth) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HomeListeningMetadata(
                        context, showPreparing, Modifier.weight(1f), palette = palette,
                    )
                    Spacer(Modifier.width(12.dp))
                    HomeListeningPresetActions(context, showPreparing, palette, Modifier.width(actionsWidth))
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    HomeListeningMetadata(
                        context, showPreparing, Modifier.fillMaxWidth(),
                        compact = !large && !stacked, palette = palette, stacked = stacked,
                    )
                    Spacer(Modifier.height(12.dp))
                    HomeListeningPresetActions(context, showPreparing, palette)
                }
            }
        }
    }
}

/** Captions can grow vertically; narrow cards stack controls instead of shrinking targets. */
@Composable
private fun HomeListeningPresetActions(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val contentWidth = maxWidth
        val primaryWidth = (56.dp * fontScale).coerceAtLeast(48.dp)
        val secondaryWidth = (64.dp * fontScale).coerceAtLeast(48.dp)
        if (maxWidth >= primaryWidth + secondaryWidth + 8.dp) {
            val extraWidth = (maxWidth - primaryWidth - secondaryWidth - 8.dp) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HomeListeningActionButton(
                    context, HomeListeningAction.TOGGLE, showPreparing, primaryWidth + extraWidth,
                    captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true, palette = palette,
                )
                HomeListeningActionButton(
                    context, HomeListeningAction.OPEN, false, secondaryWidth + extraWidth,
                    captionMaxLines = Int.MAX_VALUE, palette = palette,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeListeningActionButton(
                    context, HomeListeningAction.TOGGLE, showPreparing, contentWidth,
                    captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true, palette = palette,
                )
                HomeListeningActionButton(
                    context, HomeListeningAction.OPEN, false, contentWidth,
                    captionMaxLines = Int.MAX_VALUE, palette = palette,
                )
            }
        }
    }
}

@Composable
private fun HomeListeningDecoration(drawable: Int, height: Dp) {
    Image(
        painter = painterResource(drawable), contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().height(height),
    )
}
