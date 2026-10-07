package io.legado.app.ui.main.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val HomeWidgetGridMargin = 16.dp
internal val HomeWidgetGridGap = 12.dp

/** Decorated small cards retain their frame; compact glass and large cards measure content. */
internal fun HomeWidgetSize.standardHeight(): Dp = if (this == HomeWidgetSize.SMALL) 280.dp else HomeWidgetSkinHeaderHeight

internal fun isCompactHomeWidgetSmall(size: HomeWidgetSize, typeId: String?, styleId: String?): Boolean =
    size == HomeWidgetSize.SMALL && styleId == "basic" && (typeId == "reading" || typeId == "listening")

internal data class HomeWidgetBodyKey(val typeId: String, val styleId: String, val instanceId: String? = null)

internal fun homeWidgetBodyKey(typeId: String, styleId: String, instanceId: String?): HomeWidgetBodyKey =
    HomeWidgetBodyKey(typeId, styleId, instanceId.takeIf { typeId == "calendar" })

internal fun homeWidgetToolHeight(cardWidth: Dp): Dp = when {
    cardWidth >= 152.dp -> 48.dp
    cardWidth >= 104.dp -> 96.dp
    else -> 144.dp
}

internal fun homeWidgetGridColumns(fullWidgetWidth: Dp, fontScale: Float = 1f,
    minimumSmallWidth: Dp = 0.dp): Int {
    // Reading headers reserve 96dp before their text: padding, icon, gap and 48dp arrow.
    val minimumColumn = maxOf(112.dp, 96.dp + (16f * fontScale).dp, minimumSmallWidth)
    return if ((fullWidgetWidth - HomeWidgetGridGap) / 2 < minimumColumn) 1 else 2
}

/** Only a displayed six-cover small card needs three non-overlapping 48dp cells. */
internal fun homeWidgetMinimumSmallWidth(widgets: List<HomeWidgetInstance>): Dp =
    if (widgets.any { it.typeId == "updates" && HomeWidgetCatalog.variant(it)?.size == HomeWidgetSize.SMALL })
        144.dp else 0.dp

@Immutable
internal data class HomeWidgetDimensions(
    val columns: Int,
    val widths: Map<HomeWidgetSize, Dp>,
    val headers: Map<HomeWidgetSize, Dp>,
    val bodies: Map<HomeWidgetSize, Dp>,
    val largeHeaders: Map<String, Dp> = emptyMap(),
    val largeBodies: Map<HomeWidgetBodyKey, Dp> = emptyMap(),
    val compactSmallHeader: Dp = HomeWidgetSkinHeaderHeight,
    val compactSmallBody: Dp = 0.dp,
    val updatesSmallHeader: Dp = 40.dp,
    val updatesSmallBody: Dp = 0.dp,
) {
    fun width(size: HomeWidgetSize): Dp = widths.getValue(size)
    fun header(size: HomeWidgetSize, typeId: String? = null, styleId: String? = null): Dp =
        if (size == HomeWidgetSize.SMALL && typeId == "updates") updatesSmallHeader
        else if (isCompactHomeWidgetSmall(size, typeId, styleId)) compactSmallHeader
        else if (size == HomeWidgetSize.LARGE && typeId != null) largeHeaders[typeId] ?: HomeWidgetSkinHeaderHeight
        else headers[size] ?: HomeWidgetSkinHeaderHeight
    fun body(size: HomeWidgetSize, typeId: String? = null, styleId: String? = null, instanceId: String? = null): Dp =
        if (size == HomeWidgetSize.SMALL && typeId == "updates") updatesSmallBody
        else if (isCompactHomeWidgetSmall(size, typeId, styleId)) compactSmallBody
        else if (size == HomeWidgetSize.LARGE && typeId != null && styleId != null)
            largeBodies[homeWidgetBodyKey(typeId, styleId, instanceId)] ?: 0.dp
        else bodies[size] ?: (size.standardHeight() - HomeWidgetSkinHeaderHeight)
    fun tools(size: HomeWidgetSize): Dp = homeWidgetToolHeight(width(size))
    fun height(size: HomeWidgetSize, editing: Boolean, typeId: String? = null, styleId: String? = null, instanceId: String? = null): Dp =
        header(size, typeId, styleId) + body(size, typeId, styleId, instanceId) + if (editing) tools(size) else 0.dp
}

/** Compact glass measures only itself; decorated small listening skins still share their maximum. */
internal fun homeWidgetMeasureVariants(typeId: String, size: HomeWidgetSize,
    selected: HomeWidgetVariant? = null): List<HomeWidgetVariant> {
    val variants = HomeWidgetCatalog.variantsForSize(typeId, size)
    if (size == HomeWidgetSize.LARGE && selected != null) return listOf(selected)
    if (selected != null && isCompactHomeWidgetSmall(size, typeId, selected.styleId)) return listOf(selected)
    // Reading decorations retain their footer even though basic glass no longer reserves it.
    if (size == HomeWidgetSize.SMALL && typeId == "reading" && selected != null) return listOf(selected)
    return if (typeId == "listening") variants.filter { selected == null || it.styleId != "basic" }
        else variants.filter { it.styleId == "basic" }
}

private data class HomeWidgetMeasureKey(
    val typeId: String,
    val size: HomeWidgetSize,
    val header: Boolean,
    val styleId: String = "basic",
    val instanceId: String? = null,
)
private object HomeWidgetMeasuredContentKey

/**
 * Measure content without surfaces or image loading. Basic reading/listening small cards
 * share a compact maximum; news small cards have their own natural height. Other groups stay separate.
 * Measurements are recomputed from real content, never from already expanded outer frames.
 */
@Composable
internal fun HomeWidgetMeasuredLayout(
    widgets: List<HomeWidgetInstance>,
    listeningState: HomeListeningState,
    readingState: HomeReadingState,
    modifier: Modifier = Modifier,
    fullWidgetWidth: Dp? = null,
    calendarState: HomeCalendarState = HomeCalendarState(),
    calendarSelections: Map<String, HomeCalendarSelection> = emptyMap(),
    updatesState: HomeUpdatesState = HomeUpdatesState(),
    probeCache: HomeWidgetProbeCache? = null,
    previewWidget: HomeWidgetInstance? = null,
    content: @Composable (HomeWidgetDimensions) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val fullWidth = fullWidgetWidth ?: (constraints.maxWidth.toDp() - HomeWidgetGridMargin * 2)
        val dimensionsKey = if (probeCache != null && previewWidget != null) HomeWidgetDimensionsKey(
            previewWidget.id, previewWidget.typeId, previewWidget.variantId,
            fullWidth.roundToPx(), density, fontScale,
        ) else null
        val cachedDimensions = dimensionsKey?.let { probeCache?.getDimensions(it) }
        val dimensions = cachedDimensions ?: run {
            val columns = homeWidgetGridColumns(fullWidth, fontScale, homeWidgetMinimumSmallWidth(widgets))
            // The grid can distribute an odd pixel between columns; measure the narrower column.
            val smallWidth = ((fullWidth.roundToPx() - HomeWidgetGridGap.roundToPx()) / 2).toDp()
            val widths = mapOf(
                HomeWidgetSize.SMALL to if (columns == 1) fullWidth else smallWidth,
                HomeWidgetSize.LARGE to fullWidth,
            )
            val headers = mutableMapOf<HomeWidgetSize, Dp>()
            val bodies = mutableMapOf<HomeWidgetSize, Dp>()
            val largeHeaders = mutableMapOf<String, Dp>()
            val largeBodies = mutableMapOf<HomeWidgetBodyKey, Dp>()
            var compactSmallHeader = HomeWidgetSkinHeaderHeight
            var compactSmallBody = 0.dp
            var updatesSmallHeader = 40.dp
            var updatesSmallBody = 0.dp
            val measuredHeaders = mutableMapOf<Pair<String, HomeWidgetSize>, Dp>()
            widgets.distinctBy {
                val variant = HomeWidgetCatalog.variant(it)!!
                Triple(it.typeId, variant.size, if (it.typeId == "calendar") it.id
                    else if (variant.size == HomeWidgetSize.LARGE) variant.styleId
                    else if (isCompactHomeWidgetSmall(variant.size, it.typeId, variant.styleId)) "compact-basic" else "small")
            }.forEach { widget ->
                val selected = HomeWidgetCatalog.variant(widget)!!
                val size = selected.size
                val compactSmall = isCompactHomeWidgetSmall(size, widget.typeId, selected.styleId)
                val updatesSmall = size == HomeWidgetSize.SMALL && widget.typeId == "updates"
                val headerMinimumHeight = if (updatesSmall) 40.dp else HomeWidgetSkinHeaderHeight
                // Header geometry is shared; body compositions are measured independently below.
                val variant = HomeWidgetCatalog.variantsForSize(widget.typeId, size).first { it.styleId == "basic" }
                val measureConstraints = Constraints.fixedWidth(widths.getValue(size).roundToPx().coerceAtLeast(1))
                fun measureHeight(key: HomeWidgetMeasureKey, selection: HomeCalendarSelection? = null,
                    measure: () -> Int): Dp = (probeCache?.getOrMeasure(HomeWidgetProbeKey(
                        key.typeId, key.size, key.header, key.styleId, key.instanceId,
                        measureConstraints.maxWidth, density, fontScale, selection,
                    ), measure) ?: measure()).toDp()
                val headerKey = widget.typeId to size
                val headerMeasureKey = HomeWidgetMeasureKey(widget.typeId, size, true)
                val header = measuredHeaders[headerKey] ?: measureHeight(headerMeasureKey) {
                    subcompose(headerMeasureKey) {
                        Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                            HomeWidgetHeader(widget.typeId, variant, false, false, headerMinimumHeight, {}, {},
                                updatesState = updatesState)
                        }
                    }.single().measure(measureConstraints).height
                }.also { measuredHeaders[headerKey] = it }
                if (updatesSmall) updatesSmallHeader = maxOf(updatesSmallHeader, header)
                else if (compactSmall) compactSmallHeader = maxOf(compactSmallHeader, header)
                else if (size == HomeWidgetSize.SMALL) headers[size] = maxOf(headers[size] ?: HomeWidgetSkinHeaderHeight, header)
                else largeHeaders[widget.typeId] = header
                homeWidgetMeasureVariants(widget.typeId, size, selected).forEach { bodyVariant ->
                    val bodyKey = homeWidgetBodyKey(widget.typeId, bodyVariant.styleId, widget.id)
                    val bodyMeasureKey = HomeWidgetMeasureKey(widget.typeId, size, false, bodyVariant.styleId, bodyKey.instanceId)
                    val calendarSelection = calendarState.selectionFor(widget.id, calendarSelections)
                    val body = measureHeight(bodyMeasureKey, calendarSelection.takeIf { widget.typeId == "calendar" }) {
                        subcompose(bodyMeasureKey) {
                            Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                                HomeWidgetContents.find(widget.typeId).render(bodyVariant, HomeWidgetContentContext(
                                    editing = false, listening = listeningState, onListeningAction = { _, _ -> },
                                    onEdit = {}, interactive = false, reading = readingState, measuring = true,
                                    calendar = calendarState,
                                    calendarSelection = calendarSelection,
                                    updates = updatesState,
                                ))
                            }
                        }.single().measure(measureConstraints).height
                    }
                    if (updatesSmall) updatesSmallBody = maxOf(updatesSmallBody, body)
                    else if (compactSmall) compactSmallBody = maxOf(compactSmallBody, body)
                    else if (size == HomeWidgetSize.SMALL)
                        bodies[size] = maxOf(bodies[size] ?: (size.standardHeight() - HomeWidgetSkinHeaderHeight), body)
                    else largeBodies[bodyKey] = body
                }
            }
            val measuredDimensions = HomeWidgetDimensions(columns, widths, headers, bodies, largeHeaders, largeBodies,
                compactSmallHeader, compactSmallBody, updatesSmallHeader, updatesSmallBody)
            dimensionsKey?.let { probeCache?.putDimensions(it, measuredDimensions) }
            measuredDimensions
        }
        val main = subcompose(HomeWidgetMeasuredContentKey) { content(dimensions) }.single().measure(constraints)
        layout(main.width, main.height) { main.placeRelative(0, 0) }
    }
}
