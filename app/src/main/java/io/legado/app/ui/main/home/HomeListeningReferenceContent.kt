package io.legado.app.ui.main.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import kotlin.math.max
import kotlin.math.roundToInt

/** Same title measurement is consumed by the header and the backdrop, without a second-frame update. */
@Composable
internal fun homeListeningReferenceHeaderHeight(reference: HomeListeningReference, width: Dp): Dp {
    val density = LocalDensity.current
    val factor = width.value / reference.width
    val measurer = rememberTextMeasurer()
    val title = stringResource(R.string.home_widget_listening)
    val style = LocalTextStyle.current.copy(fontSize = reference.headerTextSize.sp,
        lineHeight = (reference.headerTextSize + 3f).sp, fontWeight = FontWeight.SemiBold)
    val pixels = measurer.measure(title, style, constraints = Constraints(maxWidth = with(density) {
        ((reference.headerSafeRight - reference.headerTextX) * factor).dp.roundToPx().coerceAtLeast(1)
    })).size.height
    return maxOf((reference.headerHeight * factor).dp,
        (reference.headerTextY * factor).dp + with(density) { pixels.toDp() } + (2f * factor).dp,
        ((reference.headerIconY + reference.headerIconSize) * factor).dp)
}

/** Native content is placed in the same coordinate system as the selected reference artwork. */
@Composable
internal fun HomeListeningReferenceHeader(
    variant: HomeWidgetVariant,
    appearance: HomeListeningAppearance,
    headerMinHeight: Dp = 0.dp,
) {
    val reference = homeListeningReference(variant)
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Icon(painterResource(R.drawable.ic_tts_headphones), null,
                tint = if (variant.styleId == "night") appearance.palette.foreground else appearance.palette.primary)
            Text(
                stringResource(R.string.home_widget_listening),
                color = appearance.palette.foreground,
                fontSize = reference.headerTextSize.sp,
                lineHeight = (reference.headerTextSize + 3f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val scale = width / reference.width
        fun coordinate(value: Float) = (value * scale).roundToInt()
        val iconSize = coordinate(reference.headerIconSize).coerceAtLeast(1)
        val icon = measurables[0].measure(Constraints.fixed(iconSize, iconSize))
        val titleX = coordinate(reference.headerTextX)
        val titleY = coordinate(reference.headerTextY)
        val title = measurables[1].measure(Constraints(
            maxWidth = coordinate(reference.headerSafeRight - reference.headerTextX)
                .coerceIn(1, (width - titleX).coerceAtLeast(1)),
        ))
        val height = maxOf(
            coordinate(reference.headerHeight), headerMinHeight.roundToPx(),
            titleY + title.height + coordinate(2f),
            coordinate(reference.headerIconY) + icon.height,
        )
        layout(width, constraints.constrainHeight(height)) {
            icon.placeRelative(coordinate(reference.headerIconX), coordinate(reference.headerIconY))
            title.placeRelative(titleX, titleY)
        }
    }
}

@Composable
internal fun HomeListeningReferenceContent(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    appearance: HomeListeningAppearance,
) {
    val reference = homeListeningReference(variant)
    val large = variant.size == HomeWidgetSize.LARGE
    val illustratedSmall = !large && (reference.styleId == "storybook" || reference.styleId == "night")
    val hasBook = context.listening.book != null
    val continueCaption = if (context.listening.history != null &&
        !context.listening.playing && !showPreparing
    ) stringResource(R.string.home_listening_continue) else null
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            if (hasBook) {
                HomeListeningCover(context, interactive = true)
                HomeListeningText(
                    context, showPreparing, palette = appearance.palette,
                    centered = reference.centeredText, interactive = true, reference = reference,
                )
                HomeListeningActionButton(
                    context, HomeListeningAction.TOGGLE, showPreparing, if (illustratedSmall) 64.dp else 48.dp,
                    captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true,
                    palette = appearance.palette, captionOverride = continueCaption,
                    reference = reference,
                )
                HomeListeningActionButton(
                    context, HomeListeningAction.OPEN, false,
                    if (illustratedSmall) 64.dp else if (large) 56.dp else 48.dp,
                    captionMaxLines = Int.MAX_VALUE, palette = appearance.palette,
                    reference = reference,
                )
            } else {
                HomeListeningEmpty(large, appearance.palette)
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val scale = width / reference.width
        fun coordinate(value: Float) = (value * scale).roundToInt()
        val header = coordinate(reference.headerHeight)
        fun bodyY(value: Float) = (coordinate(value) - header).coerceAtLeast(0)
        val baseHeight = coordinate(reference.height) - header
        if (!hasBook) {
            val safeWidth = coordinate(reference.sceneStartX ?: reference.width) - coordinate(20f)
            val empty = measurables[0].measure(Constraints(
                maxWidth = safeWidth.coerceIn(1, width),
            ))
            val top = coordinate(8f)
            val height = max(baseHeight, top + empty.height + coordinate(12f))
            layout(width, constraints.constrainHeight(height)) {
                empty.placeRelative(coordinate(10f), top)
            }
        } else {
            val coverWidth = coordinate(reference.cover.width).coerceAtLeast(1)
            val coverHeight = (coverWidth * 4f / 3f).roundToInt().coerceAtLeast(1)
            val cover = measurables[0].measure(Constraints.fixed(coverWidth, coverHeight))
            val textX = coordinate(reference.textX)
            val textWidth = coordinate(reference.textWidth).coerceIn(1, (width - textX).coerceAtLeast(1))
            val text = measurables[1].measure(Constraints(maxWidth = textWidth))
            val primary = measurables[2].measure(Constraints(maxWidth = width))
            val secondary = measurables[3].measure(Constraints(maxWidth = width))
            val textOverflow = (text.height - coordinate(reference.textHeight)).coerceAtLeast(0)
            val expectedActionHeight = 48.dp.roundToPx() +
                reference.captionGap.dp.roundToPx() + reference.captionLine.dp.roundToPx()
            val actionOverflow = (max(primary.height, secondary.height) - expectedActionHeight).coerceAtLeast(0)
            val actionsY = bodyY(reference.actionsY) + textOverflow
            val actionRowWidth = primary.width + secondary.width + 8.dp.roundToPx()
            val smallRowFits = actionRowWidth <= width
            val actionRowStart = (width - actionRowWidth) / 2
            val primaryCenter = if (illustratedSmall) {
                if (smallRowFits) actionRowStart + primary.width / 2 else width / 2
            } else coordinate(reference.primaryCenterX)
                .coerceIn(primary.width / 2, width - (primary.width + 1) / 2)
            val secondaryCenter = if (illustratedSmall) {
                if (smallRowFits) actionRowStart + primary.width + 8.dp.roundToPx() + secondary.width / 2 else width / 2
            } else coordinate(reference.secondaryCenterX)
                .coerceIn(secondary.width / 2, width - (secondary.width + 1) / 2)
            val separated = secondaryCenter - secondary.width / 2 >= primaryCenter + (primary.width + 1) / 2
            val secondaryY = if (separated) actionsY else actionsY + primary.height + 8.dp.roundToPx()
            val stackedExtra = if (separated) 0 else primary.height + 8.dp.roundToPx()
            // The desk stays bottom-aligned; keep its full height clear of the measured caption.
            val artworkClearance = when {
                illustratedSmall -> coordinate(93f) + 3.dp.roundToPx()
                reference.styleId != "night" -> 0
                large -> 62.dp.roundToPx()
                else -> coordinate(66f) + 3.dp.roundToPx()
            }
            val height = maxOf(
                baseHeight + textOverflow + actionOverflow + stackedExtra,
                bodyY(reference.cover.top) + cover.height + coordinate(8f),
                bodyY(reference.textY) + text.height + coordinate(8f),
                secondaryY + secondary.height + coordinate(4f),
                maxOf(actionsY + primary.height, secondaryY + secondary.height) + artworkClearance,
            )
            layout(width, constraints.constrainHeight(height)) {
                cover.placeRelative(coordinate(reference.cover.left), bodyY(reference.cover.top))
                text.placeRelative(textX, bodyY(reference.textY))
                primary.placeRelative(primaryCenter - primary.width / 2, actionsY)
                secondary.placeRelative(secondaryCenter - secondary.width / 2, secondaryY)
            }
        }
    }
}
