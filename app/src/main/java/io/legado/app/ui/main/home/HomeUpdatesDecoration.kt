package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

@Composable
private fun homeUpdatesDateStyle(small: Boolean): TextStyle = LocalTextStyle.current.copy(
    fontSize = if (small) 9.sp else 10.sp,
    lineHeight = if (small) 12.sp else 14.sp,
)

/** Only a nonempty update list adds a compact count below the title. */
@Composable
private fun homeUpdatesHeaderSubtitle(state: HomeUpdatesState): String? =
    if (state.books.isNotEmpty()) stringResource(R.string.home_updates_count_compact, state.books.size)
    else null

@Composable
internal fun HomeUpdatesHeaderTitle(
    title: String,
    state: HomeUpdatesState,
    small: Boolean,
    foreground: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, color = foreground, style = homeWidgetHeaderTitleStyle(small))
        homeUpdatesHeaderSubtitle(state)?.let {
            Text(it, color = secondary, style = homeUpdatesDateStyle(small))
        }
    }
}

@Immutable
internal data class HomeUpdatesHeaderCheck(val label: String, val time: String?)

@Composable
internal fun homeUpdatesHeaderCheck(state: HomeUpdatesState): HomeUpdatesHeaderCheck {
    val time = homeUpdatesCheckedTime(state)
    val label = stringResource(when {
        time != null -> R.string.home_updates_last_checked_label
        state.checking -> R.string.home_updates_checking
        else -> R.string.home_updates_not_checked
    })
    return HomeUpdatesHeaderCheck(label, time)
}

@Composable
private fun homeUpdatesCheckStyle(): TextStyle = LocalTextStyle.current.copy(
    fontSize = 11.sp, lineHeight = 14.sp,
)

/** The same measured width is used by both the status and the header artwork's spare-space guard. */
@Composable
internal fun homeUpdatesHeaderCheckWidth(check: HomeUpdatesHeaderCheck): Dp {
    val style = homeUpdatesCheckStyle()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(check, style, measurer, density) {
        with(density) {
            measurer.measure(check.time ?: check.label, style, softWrap = false, maxLines = 1).size.width.toDp()
        }
    }
}

@Composable
internal fun HomeUpdatesHeaderCheckStatus(
    check: HomeUpdatesHeaderCheck,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val description = check.time?.let { stringResource(R.string.home_updates_last_checked, it) }
        ?: check.label
    Text(check.time ?: check.label,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        color = color, style = homeUpdatesCheckStyle(), textAlign = TextAlign.End,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/** Artwork uses spare title space; it never takes width or height from the functional header. */
@Composable
internal fun HomeUpdatesDecoratedHeader(
    variant: HomeWidgetVariant,
    title: String,
    state: HomeUpdatesState,
    trailingActionWidth: Dp = 0.dp,
    checkWidth: Dp = 0.dp,
    content: @Composable (Dp) -> Unit,
) {
    val artwork = when (variant.styleId) {
        "storybook" -> R.drawable.ng_home_updates_story_girl.takeIf { variant.size == HomeWidgetSize.SMALL }
        "night" -> R.drawable.ng_home_calendar_night_lamp.takeIf { variant.size == HomeWidgetSize.SMALL }
        else -> null
    }
    val small = variant.size == HomeWidgetSize.SMALL
    if (artwork == null && small) {
        content(0.dp)
        return
    }
    val titleStyle = homeWidgetHeaderTitleStyle(small)
    val dateStyle = homeUpdatesDateStyle(small)
    val date = homeUpdatesHeaderSubtitle(state)
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleWidth = remember(title, titleStyle, density, textMeasurer) {
        with(density) {
            textMeasurer.measure(title, titleStyle, softWrap = false, maxLines = 1).size.width.toDp()
        }
    }
    val textWidth = remember(titleWidth, date, dateStyle, density, textMeasurer) {
        with(density) {
            maxOf(titleWidth,
                date?.let { textMeasurer.measure(it, dateStyle, softWrap = false, maxLines = 1).size.width.toDp() }
                    ?: 0.dp)
        }
    }
    val padding = if (small) 12.dp else 14.dp
    val iconSize = homeWidgetHeaderIconSize(small)
    val iconGap = homeWidgetHeaderIconGap(small)
    val artSize = if (small) 40.dp else 48.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Preserve the refresh touch slot and at least one title's width before
        // assigning room to an older date or a larger interface font.
        val textRoom = (maxWidth - padding * 2 - iconSize - iconGap - trailingActionWidth - 4.dp)
            .coerceAtLeast(0.dp)
        val titleMinimumWidth = minOf(titleWidth, textRoom * 0.6f)
        val statusWidth = if (small) 0.dp else minOf(checkWidth, textRoom - titleMinimumWidth)
        val occupiedTrailingWidth = trailingActionWidth + if (small) 0.dp else statusWidth + 4.dp
        content(statusWidth)
        if (artwork != null && maxWidth >= padding * 2 + iconSize + iconGap + textWidth + 8.dp + artSize + occupiedTrailingWidth) {
            Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
                Image(homeWidgetArtworkPainter(artwork), null, contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = padding + occupiedTrailingWidth).size(artSize))
            }
        }
    }
}
