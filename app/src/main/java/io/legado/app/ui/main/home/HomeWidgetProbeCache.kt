package io.legado.app.ui.main.home

/** In-memory measurement identity; the drawer owner invalidates content and ambient changes. */
internal data class HomeWidgetProbeKey(
    val typeId: String,
    val size: HomeWidgetSize,
    val header: Boolean,
    val styleId: String,
    val instanceId: String?,
    val widthPx: Int,
    val density: Float,
    val fontScale: Float,
    val calendarSelection: HomeCalendarSelection? = null,
)

/** Final geometry remains specific to one candidate and the canonical home width. */
internal data class HomeWidgetDimensionsKey(
    val instanceId: String,
    val typeId: String,
    val variantId: String,
    val fullWidgetWidthPx: Int,
    val density: Float,
    val fontScale: Float,
)

/** UI-thread-only numeric geometry for one drawer generation, never layout nodes or views. */
internal class HomeWidgetProbeCache {
    private val heights = mutableMapOf<HomeWidgetProbeKey, Int>()
    private val dimensions = mutableMapOf<HomeWidgetDimensionsKey, HomeWidgetDimensions>()

    var hitCount: Long = 0
        private set
    var missCount: Long = 0
        private set
    val size: Int get() = heights.size
    var dimensionsHitCount: Long = 0
        private set
    var dimensionsMissCount: Long = 0
        private set
    val dimensionsSize: Int get() = dimensions.size

    fun getOrMeasure(key: HomeWidgetProbeKey, measure: () -> Int): Int {
        heights[key]?.let {
            hitCount++
            return it
        }
        missCount++
        return measure().also { heights[key] = it }
    }

    fun getDimensions(key: HomeWidgetDimensionsKey): HomeWidgetDimensions? = dimensions[key].also {
        if (it == null) dimensionsMissCount++ else dimensionsHitCount++
    }

    fun putDimensions(key: HomeWidgetDimensionsKey, value: HomeWidgetDimensions) {
        dimensions[key] = value
    }
}
