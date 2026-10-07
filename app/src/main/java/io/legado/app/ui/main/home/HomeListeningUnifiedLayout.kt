package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Skin-independent functional slots; decorations never determine the measured content height. */
@Composable
internal fun HomeListeningUnifiedContent(
    widgetId: String,
    variant: HomeWidgetVariant,
    state: HomeListeningState,
    editing: Boolean,
    onAction: (String, HomeListeningAction) -> Unit,
    interactive: Boolean,
    onEdit: () -> Unit = {},
    measuring: Boolean = false,
    bodyMinimumHeight: Dp = 0.dp,
) {
    val context = HomeWidgetContentContext(
        editing = editing,
        listening = state,
        onListeningAction = { _, action -> onAction(widgetId, action) },
        onEdit = onEdit,
        interactive = interactive,
        measuring = measuring,
        bodyMinimumHeight = bodyMinimumHeight,
    )
    if (!interactive) {
        HomeListeningUnifiedBody(variant, context, state.preparing)
        return
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val active = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var preparationVisible by remember(state.playback, state.bookUrl) { mutableStateOf(false) }
    LaunchedEffect(state.preparing, active, state.playback, state.bookUrl) {
        preparationVisible = false
        if (state.preparing && active) {
            // Only the visual indicator waits; the original controls disable immediately.
            delay(120)
            preparationVisible = true
        }
    }
    HomeListeningUnifiedBody(variant, context, state.preparing && preparationVisible)
}

@Composable
private fun HomeListeningUnifiedBody(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
) {
    if (variant.size == HomeWidgetSize.LARGE) {
        HomeListeningRestoredLargeBody(variant, context, showPreparing)
        return
    }
    val palette = homeListeningAppearance(variant)?.palette
    val decoration = when (variant.styleId) {
        "storybook" -> R.drawable.ng_home_listening_storybook
        "night" -> R.drawable.ng_home_listening_night_desk
        else -> null
    }
    SubcomposeLayout(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { constraints ->
        val width = constraints.maxWidth
        val functionalMin = 152.dp.roundToPx()
        val skinMinimum = if (variant.styleId == "basic") 0.dp else 232.dp
        val bodyMin = maxOf(skinMinimum.roundToPx(), context.bodyMinimumHeight.roundToPx())
        val bottom = 8.dp.roundToPx()
        val functional = subcompose("functional") {
            HomeListeningUnifiedFunctional(context, showPreparing, palette, true, variant.styleId)
        }.single().measure(Constraints.fixedWidth(width))
        val sceneHeight = 72.dp.roundToPx()
        val artwork = if (functional.height <= functionalMin && decoration != null && !context.measuring)
            subcompose("decoration") {
                Image(homeWidgetArtworkPainter(decoration), null, contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomEnd, modifier = Modifier.padding(top = 8.dp))
            }.single().measure(Constraints.fixed(width, sceneHeight)) else null
        val height = constraints.constrainHeight(maxOf(bodyMin, functional.height + bottom))
        layout(width, height) {
            functional.placeRelative(0, 0)
            artwork?.placeRelative(0, (height - bottom - sceneHeight).coerceAtLeast(0))
        }
    }
}

/** The common frame does not replace the established composition of each listening skin. */
@Composable
private fun HomeListeningRestoredLargeBody(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
) {
    val palette = homeListeningAppearance(variant)?.palette
    if (variant.styleId != "basic") {
        HomeListeningRestoredReferenceBody(variant, context, showPreparing, palette)
        return
    }
    val panel = colorResource(R.color.ng_surface_panel).copy(alpha = 0.12f)
    SubcomposeLayout(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(20.dp))
        .padding(horizontal = 14.dp)) { constraints ->
        val foreground = subcompose("foreground") {
            if (context.listening.book == null) {
                Column(Modifier.fillMaxWidth()) { HomeListeningEmpty(true, palette) }
            } else {
                HomeListeningRestoredHorizontalForeground(context, showPreparing, palette, variant.styleId)
            }
        }.single().measure(Constraints.fixedWidth(constraints.maxWidth))
        val top = 6.dp.roundToPx()
        val bottom = 12.dp.roundToPx()
        val height = constraints.constrainHeight(foreground.height + top + bottom)
        layout(constraints.maxWidth, height) { foreground.placeRelative(0, top) }
    }
}

/** Captions keep their original SP sizes; their complete native text determines the column width. */
@Composable
private fun homeListeningLargeCaptionColumn(
    context: HomeWidgetContentContext,
    reference: HomeListeningReference? = null,
): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(
        fontSize = (reference?.captionSize ?: 11f).sp,
        lineHeight = (reference?.captionLine ?: 14f).sp,
        fontWeight = if (reference != null) FontWeight.SemiBold else LocalTextStyle.current.fontWeight,
    )
    val idlePrimary = stringResource(when {
        context.listening.playing -> R.string.pause
        context.listening.history != null -> R.string.home_listening_continue
        else -> R.string.audio_play
    })
    val primary = if (context.listening.preparing) stringResource(R.string.home_listening_preparing)
        else idlePrimary
    val open = stringResource(R.string.home_listening_open_player)
    val labelWidth = maxOf(measurer.measure(primary, style, maxLines = 1).size.width,
        measurer.measure(idlePrimary, style, maxLines = 1).size.width,
        measurer.measure(open, style, maxLines = 1).size.width)
    return with(density) { maxOf(48.dp.roundToPx(), labelWidth + 4.dp.roundToPx()).toDp() }
}

/** Metadata midpoint aligns to the 24dp control-frame midpoint, independently of captions. */
@Composable
private fun HomeListeningRestoredHorizontalForeground(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette?,
    styleId: String,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val columnWidth = homeListeningLargeCaptionColumn(context)
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val controlsWidth = (columnWidth * 2 + 8.dp).roundToPx()
        val gap = 12.dp.roundToPx()
        val metadataWidth = width - controlsWidth - gap
        val inline = metadataWidth >= (64.dp + 14.dp + 84.dp * fontScale).roundToPx()
        val pending = context.interactive && context.listening.preparing && !showPreparing
        val measuringContext = context.copy(interactive = false, measuring = true)
        fun information(slot: String, content: HomeWidgetContentContext, preparing: Boolean, minHeight: Int) =
            subcompose(slot) {
                HomeListeningMetadata(content, preparing, Modifier.fillMaxWidth(), palette = palette,
                    coverWidth = 64.dp, stacked = !inline && width < (64.dp + 14.dp + 84.dp * fontScale).roundToPx())
            }.single().measure(Constraints(minWidth = if (inline) metadataWidth else width,
                maxWidth = if (inline) metadataWidth else width, minHeight = minHeight))
        fun controls(slot: String, content: HomeWidgetContentContext, preparing: Boolean, minHeight: Int) =
            subcompose(slot) {
                HomeListeningRestoredControlPair(content, preparing, palette, styleId, columnWidth)
            }.single().measure(Constraints(minWidth = if (inline) controlsWidth else width,
                maxWidth = if (inline) controlsWidth else width, minHeight = minHeight))
        val forecastInfo = if (pending) information("pending-information", measuringContext, true, 0) else null
        val metadata = information("information", context, showPreparing, forecastInfo?.height ?: 0)
        val forecastActions = if (pending) controls("pending-actions", measuringContext, true, 0) else null
        val actions = controls("actions", context, showPreparing, forecastActions?.height ?: 0)
        val actionY = if (inline) (metadata.height / 2 - 24.dp.roundToPx()).coerceAtLeast(0)
            else metadata.height + gap
        val height = constraints.constrainHeight(maxOf(metadata.height, actionY + actions.height))
        layout(width, height) {
            metadata.placeRelative(0, 0)
            actions.placeRelative(if (inline) width - controlsWidth else 0, actionY)
        }
    }
}

/** Reference coordinates describe the body only; the common 48dp header is supplied by the shell. */
@Composable
private fun HomeListeningRestoredReferenceBody(
    variant: HomeWidgetVariant,
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette?,
) {
    val reference = homeListeningReference(variant)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val columnWidth = homeListeningLargeCaptionColumn(context, reference)
    val story = variant.styleId == "storybook"
    val night = variant.styleId == "night"
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        fun px(value: Float) = value.dp.roundToPx()
        fun bodyY(value: Float) = px(value - reference.headerHeight).coerceAtLeast(0)
        val baseHeight = px(reference.height - reference.headerHeight)
        val coverX = px(reference.cover.left)
        val coverRight = px(reference.cover.right)
        val textX = px(reference.textX)
        val textWidth = px(reference.textWidth)
        val actionColumn = columnWidth.roundToPx()
        val actionWidth = actionColumn * 2 + 8.dp.roundToPx()
        val secondaryCenter = minOf(px(reference.secondaryCenterX),
            width - 14.dp.roundToPx() - actionColumn / 2)
        val actionsX = if (story) maxOf(px(reference.primaryCenterX) - actionColumn / 2,
            coverRight + 8.dp.roundToPx()) else secondaryCenter - actionWidth + actionColumn / 2
        val foregroundRight = maxOf(200.dp.roundToPx(), textX + textWidth, actionsX + actionWidth)
        val storySceneWidth = (width - foregroundRight - 2.dp.roundToPx())
            .coerceAtMost(145.dp.roundToPx()).coerceAtLeast(0)
        val hasBook = context.listening.book != null
        val emptyInset = minOf(10.dp.roundToPx(), (width - 1).coerceAtLeast(0) / 2)
        val emptyAvailableWidth = (width - emptyInset * 2).coerceAtLeast(1)
        val canShowArtwork = width >= 320.dp.roundToPx() && fontScale <= 1.15f &&
            (story && storySceneWidth >= 112.dp.roundToPx() || night)
        val ordinary = width >= 320.dp.roundToPx() && fontScale <= 1.15f &&
            (if (story) storySceneWidth >= 112.dp.roundToPx()
            else actionsX >= textX + textWidth + 8.dp.roundToPx() &&
                actionsX + actionWidth <= width)
        if (!ordinary && hasBook) {
            val fallback = subcompose("fallback") {
                HomeListeningRestoredReferenceFallback(context, showPreparing, palette, reference, columnWidth)
            }.single().measure(Constraints.fixedWidth(width))
            val height = constraints.constrainHeight(maxOf(baseHeight, fallback.height))
            layout(width, height) { fallback.placeRelative(0, 0) }
        } else {
            val pending = context.interactive && context.listening.preparing && !showPreparing
            val measuringContext = context.copy(interactive = false, measuring = true)
            var cover: Placeable? = null
            var metadata: Placeable? = null
            var actions: Placeable? = null
            var empty: Placeable? = null
            var actionsY = bodyY(reference.actionsY)
            val naturalHeight = if (!hasBook) {
                val safeWidth = when {
                    !canShowArtwork -> emptyAvailableWidth
                    story -> minOf(emptyAvailableWidth, px(reference.sceneStartX ?: 190f) - 20.dp.roundToPx())
                    else -> emptyAvailableWidth - 123.dp.roundToPx()
                }.coerceIn(1, emptyAvailableWidth)
                empty = subcompose("empty") {
                    Column(Modifier.fillMaxWidth()) { HomeListeningEmpty(true, palette) }
                }.single().measure(Constraints.fixedWidth(safeWidth.coerceAtMost(width)))
                maxOf(baseHeight, empty.height + 20.dp.roundToPx())
            } else {
                val coverWidth = px(reference.cover.width)
                cover = subcompose("cover") {
                    HomeListeningCover(context, width = reference.cover.width.dp, interactive = true)
                }.single().measure(Constraints.fixed(coverWidth, (coverWidth * 4f / 3f).roundToInt()))
                fun text(slot: String, content: HomeWidgetContentContext, preparing: Boolean, minHeight: Int) =
                    subcompose(slot) {
                        HomeListeningText(content, preparing, Modifier.fillMaxWidth(), palette = palette,
                            interactive = true, reference = reference)
                    }.single().measure(Constraints(minWidth = textWidth, maxWidth = textWidth, minHeight = minHeight))
                fun controls(slot: String, content: HomeWidgetContentContext, preparing: Boolean, minHeight: Int) =
                    subcompose(slot) {
                        HomeListeningRestoredControlPair(content, preparing, palette, variant.styleId,
                            columnWidth, reference)
                    }.single().measure(Constraints(minWidth = actionWidth, maxWidth = actionWidth, minHeight = minHeight))
                val forecastText = if (pending) text("pending-text", measuringContext, true, 0) else null
                metadata = text("text", context, showPreparing, forecastText?.height ?: 0)
                val forecastActions = if (pending) controls("pending-actions", measuringContext, true, 0) else null
                actions = controls("actions", context, showPreparing, forecastActions?.height ?: 0)
                actionsY += (metadata.height - px(reference.textHeight)).coerceAtLeast(0)
                val captionBottom = actionsY + actions.height
                maxOf(baseHeight, bodyY(reference.cover.top) + cover.height + 8.dp.roundToPx(),
                    bodyY(reference.textY) + metadata.height + 8.dp.roundToPx(),
                    captionBottom + if (night) 62.dp.roundToPx() else 4.dp.roundToPx())
            }
            val height = constraints.constrainHeight(naturalHeight)
            val artwork = if (!context.measuring && canShowArtwork) subcompose("scene") {
                Image(homeWidgetArtworkPainter(if (story) R.drawable.ng_home_listening_large_story_scene
                    else R.drawable.ng_home_listening_night_desk), null,
                    contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd)
            }.single().measure(Constraints.fixed(
                if (story) storySceneWidth else minOf(width, 123.dp.roundToPx()),
                if (story) 128.dp.roundToPx() else 59.dp.roundToPx(),
            )) else null
            layout(width, height) {
                empty?.placeRelative(emptyInset, 8.dp.roundToPx())
                cover?.placeRelative(coverX, bodyY(reference.cover.top))
                metadata?.placeRelative(textX, bodyY(reference.textY))
                actions?.placeRelative(actionsX, actionsY)
                artwork?.let { it.placeRelative(width - it.width, (height - it.height).coerceAtLeast(0)) }
            }
        }
    }
}

@Composable
private fun HomeListeningRestoredReferenceFallback(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette?,
    reference: HomeListeningReference,
    columnWidth: Dp,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    SubcomposeLayout(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) { constraints ->
        val width = constraints.maxWidth
        val coverWidth = reference.cover.width.dp.roundToPx()
        val gap = 14.dp.roundToPx()
        val stack = width < coverWidth + gap + (84.dp * fontScale).roundToPx()
        val textWidth = if (stack) width else width - coverWidth - gap
        val forecasting = context.listening.preparing
        val measuringContext = context.copy(interactive = false, measuring = true)
        fun forecastText(slot: String, preparing: Boolean) = subcompose(slot) {
            HomeListeningText(measuringContext, preparing, Modifier.fillMaxWidth(), palette = palette,
                reference = reference, centered = stack)
        }.single().measure(Constraints.fixedWidth(textWidth))
        // Keep both grace-period and preparing text budgets while preparation remains active.
        val textMinimum = if (forecasting) maxOf(forecastText("pending-text", true).height,
            forecastText("grace-text", false).height).toDp() else 0.dp
        val information = subcompose("information") {
            val metadata: @Composable (Modifier) -> Unit = { modifier ->
                HomeListeningText(context, showPreparing, modifier.heightIn(min = textMinimum), palette = palette,
                    reference = reference, interactive = true, centered = stack)
            }
            if (stack) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeListeningCover(context, width = reference.cover.width.dp, interactive = true)
                    metadata(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeListeningCover(context, width = reference.cover.width.dp, interactive = true)
                    metadata(Modifier.weight(1f))
                }
            }
        }.single().measure(Constraints.fixedWidth(width))
        fun controls(slot: String, content: HomeWidgetContentContext, preparing: Boolean, minHeight: Int) =
            subcompose(slot) {
                HomeListeningRestoredControlPair(content, preparing, palette, reference.styleId,
                    columnWidth, reference)
            }.single().measure(Constraints(minWidth = width, maxWidth = width, minHeight = minHeight))
        val actionMinimum = if (forecasting) maxOf(controls("pending-actions", measuringContext, true, 0).height,
            controls("grace-actions", measuringContext, false, 0).height) else 0
        val actions = controls("actions", context, showPreparing, actionMinimum)
        val actionsY = information.height + 8.dp.roundToPx()
        val height = constraints.constrainHeight(actionsY + actions.height)
        layout(width, height) {
            information.placeRelative(0, 0)
            actions.placeRelative(0, actionsY)
        }
    }
}

@Composable
private fun HomeListeningRestoredControlPair(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette?,
    styleId: String,
    columnWidth: Dp,
    captionReference: HomeListeningReference? = null,
) {
    val continueCaption = if (context.listening.history != null && !context.listening.playing && !showPreparing)
        stringResource(R.string.home_listening_continue) else null
    val actions: @Composable (Dp) -> Unit = { width ->
        HomeListeningActionButton(context, HomeListeningAction.TOGGLE, showPreparing, width,
            captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true, palette = palette,
            captionOverride = continueCaption, discStyleId = styleId.takeIf { it != "basic" },
            captionReference = captionReference)
        HomeListeningActionButton(context, HomeListeningAction.OPEN, false, width,
            captionMaxLines = Int.MAX_VALUE, palette = palette, discStyleId = styleId.takeIf { it != "basic" },
            captionReference = captionReference)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val contentWidth = maxWidth
        if (contentWidth >= columnWidth * 2 + 8.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top) {
                actions(columnWidth)
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions(contentWidth)
            }
        }
    }
}

@Composable
private fun HomeListeningUnifiedFunctional(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette?,
    small: Boolean,
    styleId: String,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val coverWidth = if (small) 44.dp else 60.dp
        val metadataGap = if (small) 8.dp else 14.dp
        val stackMetadata = width < (coverWidth + metadataGap + 64.dp * fontScale).roundToPx()
        val informationMin = (if (small) 72.dp else 80.dp).roundToPx()
        val functionalMin = (if (small) 152.dp else 160.dp).roundToPx()
        if (context.listening.book == null) {
            val empty = subcompose("empty") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                    HomeListeningEmpty(large = !small, palette = palette)
                }
            }.single().measure(Constraints(minWidth = width, maxWidth = width, minHeight = functionalMin))
            layout(width, constraints.constrainHeight(empty.height)) {
                empty.placeRelative(0, 0)
            }
        } else {
            // Reserve the complete pending captions before the 120ms indicator becomes visible.
            val measurePending = context.interactive && context.listening.preparing && !showPreparing
            val measureContext = context.copy(interactive = false, measuring = true)
            fun measureInformation(slot: String, contentContext: HomeWidgetContentContext,
                preparing: Boolean, minHeight: Int) = subcompose(slot) {
                    HomeListeningMetadata(
                        contentContext, preparing, Modifier.fillMaxWidth(), compact = small,
                        palette = palette, stacked = stackMetadata, coverWidth = coverWidth,
                    )
                }.single().measure(Constraints(minWidth = width, maxWidth = width, minHeight = minHeight))
            fun measureActions(slot: String, contentContext: HomeWidgetContentContext,
                preparing: Boolean, minHeight: Int) = subcompose(slot) {
                    HomeListeningUnifiedActions(
                        contentContext, preparing, palette, styleId,
                        primaryCenterX = if (stackMetadata) null else coverWidth / 2,
                    )
                }.single().measure(Constraints(minWidth = width, maxWidth = width, minHeight = minHeight))
            val pendingInformation = if (measurePending) {
                measureInformation("pending-information", measureContext, true, informationMin)
            } else null
            val information = measureInformation("information", context, showPreparing,
                maxOf(informationMin, pendingInformation?.height ?: 0))
            val pendingActions = if (measurePending) {
                measureActions("pending-actions", measureContext, true, 72.dp.roundToPx())
            } else null
            val actions = measureActions("actions", context, showPreparing,
                maxOf(72.dp.roundToPx(), pendingActions?.height ?: 0))
            val actionY = information.height + 8.dp.roundToPx()
            val height = constraints.constrainHeight(maxOf(functionalMin, actionY + actions.height))
            layout(width, height) {
                information.placeRelative(0, 0)
                actions.placeRelative(0, actionY)
            }
        }
    }
}

@Composable
private fun HomeListeningUnifiedActions(
    context: HomeWidgetContentContext,
    showPreparing: Boolean,
    palette: HomeListeningPalette?,
    styleId: String,
    primaryCenterX: Dp?,
) {
    val continueCaption = if (context.listening.history != null &&
        !context.listening.playing && !showPreparing
    ) stringResource(R.string.home_listening_continue) else null
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
        val contentWidth = maxWidth
        val actionColumn = 64.dp
        if (contentWidth >= actionColumn * 2 + 8.dp) {
            // Match the cover center while keeping the secondary action in its existing slot.
            val primaryOffset = primaryCenterX?.let {
                it - (contentWidth - actionColumn * 2 - 8.dp) / 2 - actionColumn / 2
            } ?: 0.dp
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.offset(x = primaryOffset)) {
                    HomeListeningActionButton(
                        context, HomeListeningAction.TOGGLE, showPreparing, actionColumn,
                        captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true, palette = palette,
                        captionOverride = continueCaption,
                        discStyleId = styleId.takeIf { it != "basic" },
                    )
                }
                HomeListeningActionButton(
                    context, HomeListeningAction.OPEN, false, actionColumn,
                    captionMaxLines = Int.MAX_VALUE, palette = palette,
                    discStyleId = styleId.takeIf { it != "basic" },
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeListeningActionButton(
                    context, HomeListeningAction.TOGGLE, showPreparing, contentWidth,
                    captionMaxLines = Int.MAX_VALUE, showPrimaryCaption = true, palette = palette,
                    captionOverride = continueCaption,
                    discStyleId = styleId.takeIf { it != "basic" },
                )
                HomeListeningActionButton(
                    context, HomeListeningAction.OPEN, false, contentWidth,
                    captionMaxLines = Int.MAX_VALUE, palette = palette,
                    discStyleId = styleId.takeIf { it != "basic" },
                )
            }
        }
    }
}
