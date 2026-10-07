package io.legado.app.ui.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

/** Ordinary-width large presets reserve art in their surface, outside native content measurement. */
@Composable
internal fun HomeListeningLargeDesign(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    appearance: HomeListeningAppearance,
) {
    val palette = appearance.palette
    when (variant.styleId) {
        "storybook" -> HomeListeningStorybookLargeDesign(context, showPreparing, palette)
        "paper" -> HomeListeningHorizontalLargeDesign(context, showPreparing, palette, night = false)
        "night" -> HomeListeningHorizontalLargeDesign(context, showPreparing, palette, night = true)
    }
}

@Composable
private fun HomeListeningStorybookLargeDesign(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette,
) {
    val geometry = HomeListeningStoryGeometry
    Column(Modifier.fillMaxWidth().padding(start = geometry.inset, top = 8.dp,
        end = geometry.inset, bottom = 4.dp)) {
        if (context.listening.book == null) {
            Box(Modifier.width(168.dp)) {
                HomeListeningEmpty(large = true, palette = palette)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                HomeListeningCover(context, width = geometry.coverWidth, interactive = true)
                Spacer(Modifier.width(geometry.textGap))
                Column(Modifier.width(geometry.textWidth)) {
                    HomeListeningText(
                        context, showPreparing, Modifier.fillMaxWidth(), palette = palette,
                        textScale = 0.84f, spacing = 4.dp, interactive = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    HomeListeningLargeDesignActions(context, showPreparing, palette, secondaryWidth = 60.dp)
                }
            }
        }
    }
}

@Composable
private fun HomeListeningHorizontalLargeDesign(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette,
    night: Boolean,
) {
    val bottomReserve = if (night && context.listening.book != null)
        HomeListeningNightBottomReserve(context, showPreparing) else if (night) 44.dp else 20.dp
    Column(
        Modifier.fillMaxWidth().padding(
            start = 18.dp, top = if (night) 12.dp else 18.dp,
            end = 18.dp, bottom = bottomReserve,
        ),
    ) {
        if (context.listening.book == null) {
            HomeListeningEmpty(large = true, palette = palette)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HomeListeningMetadata(
                    context, showPreparing, Modifier.weight(1f), palette = palette,
                    coverWidth = if (night) 64.dp else 60.dp, textScale = 0.90f, spacing = 4.dp,
                )
                Spacer(Modifier.width(12.dp))
                HomeListeningLargeDesignActions(context, showPreparing, palette, secondaryWidth = 64.dp,
                    paper = !night)
            }
        }
    }
}

/** Keep the lamp below the real multiline captions, rather than guessing from fontScale. */
@Composable
private fun HomeListeningNightBottomReserve(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val captionStyle = LocalTextStyle.current.copy(fontSize = 11.sp, lineHeight = 12.sp)
    val primaryCaption = stringResource(when {
        showPreparing -> R.string.home_listening_preparing
        context.listening.playing -> R.string.pause
        context.listening.history != null -> R.string.home_listening_continue
        else -> R.string.audio_play
    })
    val secondaryCaption = stringResource(R.string.home_listening_open_player)
    val primaryHeight = with(density) {
        measurer.measure(primaryCaption, captionStyle, constraints = Constraints(maxWidth = 48.dp.roundToPx()))
            .size.height.toDp()
    }
    val secondaryHeight = with(density) {
        measurer.measure(secondaryCaption, captionStyle, constraints = Constraints(maxWidth = 64.dp.roundToPx()))
            .size.height.toDp()
    }
    val actionHeight = 52.dp + maxOf(primaryHeight, secondaryHeight)
    val coverHeight = 64.dp * 4f / 3f
    return (62.dp - (coverHeight - actionHeight).coerceAtLeast(0.dp) / 2).coerceAtLeast(44.dp)
}

@Composable
private fun HomeListeningLargeDesignActions(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette,
    secondaryWidth: Dp,
    paper: Boolean = false,
) {
    Row(Modifier.width(56.dp + secondaryWidth), horizontalArrangement = Arrangement.Start) {
        HomeListeningActionButton(
            context, HomeListeningAction.TOGGLE, showPreparing, 48.dp,
            captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true, palette = palette,
            compactCaption = true, visualSize = if (paper) 48.dp else 44.dp,
            captionOverride = if (context.listening.history != null &&
                !context.listening.playing && !showPreparing) stringResource(R.string.home_listening_continue) else null,
        )
        Spacer(Modifier.width(8.dp))
        HomeListeningActionButton(
            context, HomeListeningAction.OPEN, false, secondaryWidth,
            captionMaxLines = Int.MAX_VALUE, palette = palette,
            compactCaption = true, visualSize = 44.dp,
        )
    }
}
