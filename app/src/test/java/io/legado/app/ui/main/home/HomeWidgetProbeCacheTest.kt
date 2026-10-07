package io.legado.app.ui.main.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HomeWidgetProbeCacheTest {
    private fun key() = HomeWidgetProbeKey(
        typeId = "reading", size = HomeWidgetSize.SMALL, header = false,
        styleId = "basic", instanceId = null, widthPx = 492, density = 3f, fontScale = 1f,
    )

    @Test fun `equal probes keep the exact pixel height without remeasuring`() {
        val cache = HomeWidgetProbeCache()
        var calls = 0
        repeat(3) {
            assertEquals(497, cache.getOrMeasure(key().copy()) { calls++; 497 })
        }
        assertEquals(1, calls)
        assertEquals(1, cache.size)
        assertEquals(1L, cache.missCount)
        assertEquals(2L, cache.hitCount)
    }

    @Test fun `probe identity keeps kinds sizes styles widths and font environments separate`() {
        val cache = HomeWidgetProbeCache()
        val base = key()
        val keys = listOf(base, base.copy(typeId = "listening"), base.copy(header = true),
            base.copy(size = HomeWidgetSize.LARGE), base.copy(styleId = "night"),
            base.copy(widthPx = 493), base.copy(density = 2.75f), base.copy(fontScale = 1.5f))
        keys.forEachIndexed { index, probe ->
            assertEquals(index * 31, cache.getOrMeasure(probe) { index * 31 })
        }
        keys.forEachIndexed { index, probe ->
            assertEquals(index * 31, cache.getOrMeasure(probe) { error("Unexpected cache miss") })
        }
        assertEquals(keys.size, cache.size)
        assertEquals(keys.size.toLong(), cache.hitCount)
    }

    @Test fun `calendar instances months and selected dates retain their own measurements`() {
        val cache = HomeWidgetProbeCache()
        val base = key().copy(typeId = "calendar", size = HomeWidgetSize.LARGE,
            instanceId = "first", calendarSelection = HomeCalendarSelection(YearMonth.of(2026, 10)))
        val keys = listOf(base, base.copy(instanceId = "second"),
            base.copy(calendarSelection = HomeCalendarSelection(YearMonth.of(2026, 11))),
            base.copy(calendarSelection = HomeCalendarSelection(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 6))))
        keys.forEachIndexed { index, probe -> cache.getOrMeasure(probe) { 900 + index } }
        keys.forEachIndexed { index, probe ->
            assertEquals(900 + index, cache.getOrMeasure(probe) { error("Unexpected cache miss") })
        }
        assertEquals(4, cache.size)
    }

    @Test fun `failed measurements are not cached`() {
        val cache = HomeWidgetProbeCache()
        val failure = IllegalStateException("Measurement failed")
        assertSame(failure, runCatching { cache.getOrMeasure(key()) { throw failure } }.exceptionOrNull())
        assertEquals(0, cache.size)
        assertEquals(0, cache.getOrMeasure(key()) { 0 })
        assertEquals(0, cache.getOrMeasure(key()) { error("Zero is a valid cached height") })
        assertEquals(2L, cache.missCount)
        assertEquals(1L, cache.hitCount)
    }

    @Test fun `a new drawer generation does not reuse old content measurements`() {
        val old = HomeWidgetProbeCache()
        val fresh = HomeWidgetProbeCache()
        assertEquals(400, old.getOrMeasure(key()) { 400 })
        assertEquals(500, fresh.getOrMeasure(key()) { 500 })
        assertEquals(400, old.getOrMeasure(key()) { error("Old generation must be independent") })
    }

    private fun dimensionsKey() = HomeWidgetDimensionsKey(
        instanceId = "candidate-reading-small", typeId = "reading", variantId = "small",
        fullWidgetWidthPx = 1020, density = 3f, fontScale = 1f,
    )

    private fun dimensions(bodyHeight: Int = 160) = HomeWidgetDimensions(
        columns = 2,
        widths = mapOf(HomeWidgetSize.SMALL to 164.dp, HomeWidgetSize.LARGE to 340.dp),
        headers = mapOf(HomeWidgetSize.SMALL to 48.dp),
        bodies = mapOf(HomeWidgetSize.SMALL to bodyHeight.dp),
    )

    @Test fun `a repeated candidate reuses the same final dimensions instance`() {
        val cache = HomeWidgetProbeCache()
        val probe = dimensionsKey()
        val expected = dimensions()
        assertNull(cache.getDimensions(probe))
        cache.putDimensions(probe, expected)
        repeat(3) { assertSame(expected, cache.getDimensions(probe.copy())) }
        assertEquals(1, cache.dimensionsSize)
        assertEquals(1L, cache.dimensionsMissCount)
        assertEquals(3L, cache.dimensionsHitCount)
    }

    @Test fun `final dimensions isolate candidate identity canonical width and font environment`() {
        val cache = HomeWidgetProbeCache()
        val base = dimensionsKey()
        val keys = listOf(base, base.copy(instanceId = "another-candidate"),
            base.copy(typeId = "listening"), base.copy(variantId = "story-small"),
            base.copy(fullWidgetWidthPx = 1021), base.copy(density = 2.75f),
            base.copy(fontScale = 1.5f))
        val expected = keys.indices.map { dimensions(160 + it) }
        keys.forEachIndexed { index, probe -> cache.putDimensions(probe, expected[index]) }
        keys.forEachIndexed { index, probe -> assertSame(expected[index], cache.getDimensions(probe)) }
        assertEquals(keys.size, cache.dimensionsSize)
    }

    @Test fun `final dimensions are discarded with the content generation`() {
        val old = HomeWidgetProbeCache()
        val fresh = HomeWidgetProbeCache()
        val probe = dimensionsKey()
        val before = dimensions(160)
        val after = dimensions(240)
        old.putDimensions(probe, before)
        assertNull(fresh.getDimensions(probe))
        fresh.putDimensions(probe, after)
        assertSame(before, old.getDimensions(probe))
        assertSame(after, fresh.getDimensions(probe))
    }
}
