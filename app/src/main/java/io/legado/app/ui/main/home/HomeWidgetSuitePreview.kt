package io.legado.app.ui.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One selected suite uses the real card renderers at one shared density, with a calendar viewport. */
@Composable
internal fun HomeWidgetSuitePreview(
    styleId: String,
    widgets: List<HomeWidgetInstance>,
    listeningState: HomeListeningState,
    fullWidgetWidth: Dp,
    readingState: HomeReadingState,
    calendarState: HomeCalendarState,
    calendarSelections: Map<String, HomeCalendarSelection>,
    updatesState: HomeUpdatesState,
    probeCache: HomeWidgetProbeCache,
) {
    val candidates = remember(styleId, widgets) {
        listOf("updates", "reading", "listening", "calendar").map { typeId ->
            val size = if (typeId == "updates" || typeId == "calendar") HomeWidgetSize.LARGE
                else HomeWidgetSize.SMALL
            val variant = HomeWidgetCatalog.variantsForSize(typeId, size).first { it.styleId == styleId }
            val id = widgets.firstOrNull { it.typeId == typeId }?.id ?: "suite_preview_$typeId"
            HomeWidgetInstance(id, typeId, variant.id)
        }
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.widthIn(max = 254.dp).fillMaxSize().clip(RoundedCornerShape(12.dp))) {
        val slotWidth = maxWidth
        val slotHeight = maxHeight
        HomeWidgetMeasuredLayout(candidates, listeningState, readingState, Modifier.fillMaxWidth(),
            fullWidgetWidth, calendarState, calendarSelections, updatesState, probeCache = probeCache) { dimensions ->
            val variants = candidates.map { HomeWidgetCatalog.variant(it)!! }
            val heights = candidates.mapIndexed { index, widget ->
                val variant = variants[index]
                dimensions.height(variant.size, false, widget.typeId, variant.styleId, widget.id)
            }
            val middleHeight = if (dimensions.columns > 1) maxOf(heights[1], heights[2])
                else heights[1] + heights[2] + HomeWidgetGridGap
            val beforeCalendar = heights[0] + middleHeight + HomeWidgetGridGap * 2
            val totalHeight = beforeCalendar + heights[3]
            // Keep the real LARGE calendar; the viewport can crop its lower weeks, never change its size.
            val scale = minOf(slotWidth / dimensions.width(HomeWidgetSize.LARGE),
                slotHeight / (beforeCalendar + minOf(heights[3], 180.dp)), 1f)
            if (scale > 0f) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                    Column(Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .requiredWidth(dimensions.width(HomeWidgetSize.LARGE)).requiredHeight(totalHeight),
                        verticalArrangement = Arrangement.spacedBy(HomeWidgetGridGap)) {
                        HomeWidgetSuiteCard(candidates[0], variants[0], dimensions, listeningState,
                            readingState, calendarState, calendarSelections, updatesState)
                        if (dimensions.columns > 1) Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(HomeWidgetGridGap)) {
                            for (index in 1..2) HomeWidgetSuiteCard(candidates[index], variants[index],
                                dimensions, listeningState, readingState, calendarState, calendarSelections, updatesState)
                        } else Column(verticalArrangement = Arrangement.spacedBy(HomeWidgetGridGap)) {
                            for (index in 1..2) HomeWidgetSuiteCard(candidates[index], variants[index],
                                dimensions, listeningState, readingState, calendarState, calendarSelections, updatesState)
                        }
                        HomeWidgetSuiteCard(candidates[3], variants[3], dimensions, listeningState,
                            readingState, calendarState, calendarSelections, updatesState)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWidgetSuiteCard(
    widget: HomeWidgetInstance,
    variant: HomeWidgetVariant,
    dimensions: HomeWidgetDimensions,
    listeningState: HomeListeningState,
    readingState: HomeReadingState,
    calendarState: HomeCalendarState,
    calendarSelections: Map<String, HomeCalendarSelection>,
    updatesState: HomeUpdatesState,
) {
    HomeWidgetCard(widget = widget, variant = variant, editing = false,
        onLongClick = {}, onSelect = {}, listeningState = listeningState,
        onListeningAction = { _, _ -> }, modifier = Modifier.width(dimensions.width(variant.size)),
        interactive = false, readingState = readingState,
        headerMinHeight = dimensions.header(variant.size, widget.typeId, variant.styleId),
        bodyMinHeight = dimensions.body(variant.size, widget.typeId, variant.styleId, widget.id),
        minimumHeight = dimensions.height(variant.size, false, widget.typeId, variant.styleId, widget.id),
        calendarState = calendarState,
        calendarSelection = calendarState.selectionFor(widget.id, calendarSelections),
        updatesState = updatesState)
}
