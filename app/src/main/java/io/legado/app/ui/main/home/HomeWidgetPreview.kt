package io.legado.app.ui.main.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val HomeWidgetAddPreviewHeight = 128.dp

/** Keep the home-grid dp width while measuring the display-only renderer at thumbnail density. */
@Composable
internal fun HomeWidgetPreview(
    widget: HomeWidgetInstance,
    variant: HomeWidgetVariant,
    listeningState: HomeListeningState,
    fullWidgetWidth: Dp,
    readingState: HomeReadingState,
    widgets: List<HomeWidgetInstance>,
    calendarState: HomeCalendarState = HomeCalendarState(),
    calendarSelections: Map<String, HomeCalendarSelection> = emptyMap(),
    updatesState: HomeUpdatesState = HomeUpdatesState(),
    maximumHeight: Dp? = null,
    probeCache: HomeWidgetProbeCache? = null,
) {
    val baseDensity = LocalDensity.current
    val previewWidget = widget.copy(variantId = variant.id)
    val previewWidgets = widgets.map { if (it.id == widget.id) previewWidget else it }
    if (maximumHeight != null) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(maximumHeight).clipToBounds().clearAndSetSemantics {},
            contentAlignment = Alignment.Center) {
            val slotWidth = maxWidth
            val slotHeight = maxHeight
            // Measure canonical home geometry at the real density, without surfaces or image loading.
            HomeWidgetMeasuredLayout(previewWidgets, listeningState, readingState,
                Modifier.fillMaxWidth(), fullWidgetWidth, calendarState, calendarSelections, updatesState,
                probeCache = probeCache, previewWidget = previewWidget) { dimensions ->
                val canonicalWidth = dimensions.width(variant.size)
                val canonicalHeight = dimensions.height(variant.size, false, widget.typeId, variant.styleId, widget.id)
                val roundingSpace = with(baseDensity) { 1.toDp() }
                val scale = minOf(
                    (slotWidth - roundingSpace).coerceAtLeast(0.dp) / canonicalWidth,
                    (slotHeight - roundingSpace).coerceAtLeast(0.dp) / canonicalHeight,
                    1f,
                )
                // Do not propagate the slot's minimum width/height into the scaled native card.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, propagateMinConstraints = false) {
                    if (scale > 0f) CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale),
                    ) {
                        HomeWidgetCard(
                            widget = previewWidget,
                            variant = variant,
                            editing = false,
                            onLongClick = {}, onSelect = {},
                            listeningState = listeningState,
                            onListeningAction = { _, _ -> },
                            modifier = Modifier.width(canonicalWidth),
                            interactive = false,
                            readingState = readingState,
                            headerMinHeight = dimensions.header(variant.size, widget.typeId, variant.styleId),
                            bodyMinHeight = dimensions.body(variant.size, widget.typeId, variant.styleId, widget.id),
                            minimumHeight = canonicalHeight,
                            calendarState = calendarState,
                            calendarSelection = calendarState.selectionFor(widget.id, calendarSelections),
                            updatesState = updatesState,
                        )
                    }
                }
            }
        }
        return
    }
    val width = if (variant.size == HomeWidgetSize.LARGE || homeWidgetGridColumns(fullWidgetWidth,
            baseDensity.fontScale, homeWidgetMinimumSmallWidth(previewWidgets)) == 1)
        fullWidgetWidth else (fullWidgetWidth - HomeWidgetGridGap) / 2
    BoxWithConstraints(
        Modifier.fillMaxWidth().clipToBounds().clearAndSetSemantics {},
        contentAlignment = Alignment.TopCenter,
    ) {
        val scale = (maxWidth / width).coerceIn(0.01f, 1f)
        // Measure the actual Surface/View at its visible size; a scaled large layer
        // leaves backdrop sampling at the unscaled dimensions.
        CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale),
        ) {
            HomeWidgetMeasuredLayout(previewWidgets, listeningState, readingState,
                Modifier.width(width), fullWidgetWidth, calendarState, calendarSelections, updatesState) { dimensions ->
            HomeWidgetCard(
                widget = previewWidget,
                variant = variant,
                editing = false,
                onLongClick = {}, onSelect = {},
                listeningState = listeningState,
                onListeningAction = { _, _ -> },
                modifier = Modifier.width(width),
                interactive = false,
                readingState = readingState,
                headerMinHeight = dimensions.header(variant.size, widget.typeId, variant.styleId),
                bodyMinHeight = dimensions.body(variant.size, widget.typeId, variant.styleId, widget.id),
                minimumHeight = dimensions.height(variant.size, false, widget.typeId, variant.styleId, widget.id),
                calendarState = calendarState,
                calendarSelection = calendarState.selectionFor(widget.id, calendarSelections),
                updatesState = updatesState,
            )
            }
        }
    }
}
