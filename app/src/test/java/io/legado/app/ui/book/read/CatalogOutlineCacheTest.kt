package io.legado.app.ui.book.read

import android.app.Application
import io.legado.app.data.entities.CatalogOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production cache; Compose lifecycle and visible flicker need separate verification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, manifest = Config.NONE)
class CatalogOutlineCacheTest {

    @Test
    fun loadedChaptersSurviveFoldingExpandingAndReversingWithoutAnotherQuery() {
        val outline = listOf(
            row(0), row(2, volume = true), row(3), row(6),
            row(7, volume = true), row(8, volume = true), row(10), row(11),
        )
        val values = outline.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(16)
        var queries = 0

        fun loadVisible(rows: List<CatalogOutlineEntry>) {
            val missing = cache.missing(rows)
            if (missing.isNotEmpty()) {
                queries++
                cache.store(missing, values.filterKeys { it in missing }, rows)
            } else {
                cache.retain(rows)
            }
            rows.forEach { assertSame(values.getValue(it), cache[it]) }
        }

        loadVisible(outline)
        val folded = visibleCatalogRows(outline, setOf("chapter-2"), false)
        assertEquals(listOf(0, 2, 7, 8, 10, 11), folded.map { it.index })
        loadVisible(folded)
        loadVisible(visibleCatalogRows(outline, setOf("chapter-2", "chapter-7", "chapter-8"), false))
        loadVisible(visibleCatalogRows(outline, emptySet(), false))
        loadVisible(visibleCatalogRows(outline, emptySet(), true))
        loadVisible(outline)

        assertEquals(1, queries)
        assertEquals(outline.size, cache.size)
    }

    @Test
    fun missingDatabaseRowsKeepTheirSlotsWhenResultsAreSparseAndOutOfOrder() {
        val requested = listOf(row(0), row(3), row(9), row(17))
        val first = LoadedChapter("first")
        val last = LoadedChapter("last")
        val cache = CatalogOutlineCache<LoadedChapter>(8)
        cache.store(requested, linkedMapOf(requested[3] to last, requested[0] to first), requested)

        assertSame(first, cache[requested[0]])
        assertNull(cache[requested[1]])
        assertNull(cache[requested[2]])
        assertSame(last, cache[requested[3]])
        assertEquals(listOf(last, null, null, first), requested.asReversed().map { cache[it] })
        // A known absence stays a placeholder until eviction or a refreshed cache instance.
        assertTrue(cache.missing(requested).isEmpty())
        assertEquals(4, cache.size)
    }

    @Test
    fun sameUrlWithChangedIndexOrVolumeFlagCannotReuseAnOldChapter() {
        val original = row(5)
        val moved = original.copy(index = 31)
        val changedKind = original.copy(isVolume = true)
        val oldValue = LoadedChapter("old chapter")
        val currentValue = LoadedChapter("current chapter")
        val cache = CatalogOutlineCache<LoadedChapter>(8)
        cache.store(listOf(original), mapOf(original to oldValue), listOf(original))

        assertNull(cache[moved])
        assertNull(cache[changedKind])
        assertEquals(listOf(moved, changedKind), cache.missing(listOf(moved, changedKind)))

        cache.store(listOf(moved), mapOf(moved to currentValue), listOf(moved))
        assertSame(currentValue, cache[moved])
        assertNull(cache[original])
        assertEquals(1, cache.size)

        // A mismatched loader result must not be accepted under the requested identity.
        cache.store(listOf(changedKind), mapOf(original to oldValue), listOf(changedKind))
        assertNull(cache[changedKind])
        assertTrue(cache.missing(listOf(changedKind)).isEmpty())
    }

    @Test
    fun compositionReadsDoNotChangeEvictionOrderButRetainedRowsDo() {
        val rows = (0..4).map { row(it) }
        val values = rows.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(4)
        cache.store(rows.take(4), values, rows.take(4))
        cache.retain(listOf(rows[0]))
        repeat(10) { assertSame(values.getValue(rows[1]), cache[rows[1]]) }

        cache.store(listOf(rows[4]), values, listOf(rows[4]))

        assertNull(cache[rows[1]])
        listOf(0, 2, 3, 4).forEach { assertSame(values.getValue(rows[it]), cache[rows[it]]) }
        assertEquals(4, cache.size)
    }

    @Test
    fun scrollingManyPagesRetainsAtMost320EntriesAndProtectsVisibleRows() {
        val rows = (0 until 512).map { row(it) }
        val values = rows.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(320)

        rows.chunked(64).forEach { page ->
            val visibleAndPreloaded = (listOf(rows[0]) + page).distinct()
            cache.store(page, values, visibleAndPreloaded)
            assertTrue(cache.size <= 320)
            visibleAndPreloaded.forEach { assertSame(values.getValue(it), cache[it]) }
        }

        assertEquals(320, cache.size)
        assertSame(values.getValue(rows[0]), cache[rows[0]])
        assertSame(values.getValue(rows.last()), cache[rows.last()])
        assertNull(cache[rows[1]])
    }

    @Test
    fun viewportLargerThanCapacityRemainsLoadedThenShrinksBackToTheBound() {
        val rows = (0..3).map { row(it) }
        val values = rows.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(2)
        cache.store(rows, values, rows)

        assertEquals(4, cache.size)
        rows.forEach { assertSame(values.getValue(it), cache[it]) }

        cache.retain(listOf(rows.last()))
        assertEquals(2, cache.size)
        assertSame(values.getValue(rows.last()), cache[rows.last()])
        assertNull(cache[rows.first()])
    }

    @Test
    fun absentRowsUseTheSameBoundAndCanBeQueriedAgainAfterEviction() {
        val rows = (0..2).map { row(it) }
        val value = LoadedChapter("loaded")
        val cache = CatalogOutlineCache<LoadedChapter>(2)
        cache.store(rows.take(2), mapOf(rows[1] to value), rows.take(2))

        assertNull(cache[rows[0]])
        assertTrue(cache.missing(listOf(rows[0])).isEmpty())
        assertEquals(2, cache.size)

        cache.store(listOf(rows[2]), emptyMap(), listOf(rows[2]))
        assertEquals(2, cache.size)
        assertEquals(listOf(rows[0]), cache.missing(listOf(rows[0])))
        assertTrue(cache.missing(listOf(rows[2])).isEmpty())
    }

    @Test
    fun publishingRejectsBothWritesAndRetainsFromThePreviousPagingEpoch() {
        val rows = (0..3).map { row(it) }
        val values = rows.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(2)
        cache.store(rows.take(2), values, rows.take(2))
        val oldEpoch = cache.epoch
        val currentEpoch = cache.beginProjection(listOf(rows[2]))
        assertTrue(cache.store(listOf(rows[2]), values, listOf(rows[2]), currentEpoch))
        assertTrue(cache.endProjection(currentEpoch, listOf(rows[2])))

        assertFalse(cache.store(listOf(rows[3]), values, listOf(rows[3]), oldEpoch))
        assertFalse(cache.retain(listOf(rows[1]), oldEpoch))
        assertEquals(listOf(rows[3]), cache.missing(listOf(rows[3])))
        assertSame(values.getValue(rows[2]), cache[rows[2]])

        // A rejected retain must not even touch LRU ordering.
        assertTrue(cache.store(listOf(rows[3]), values, listOf(rows[3]), currentEpoch))
        assertNull(cache[rows[1]])
        assertSame(values.getValue(rows[2]), cache[rows[2]])
    }

    @Test
    fun temporaryWindowsStayProtectedDuringPagingThenReturnToTheNormalBound() {
        val rows = (0 until 10).map { row(it) }
        val values = rows.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(2)
        val transitionRows = rows.take(6)
        val epoch = cache.beginProjection(transitionRows)
        cache.store(transitionRows, values, emptyList(), epoch)
        assertTrue(cache.hasTransition)
        assertEquals(6, cache.size)

        rows.drop(6).forEach { current ->
            cache.store(listOf(current), values, listOf(current), epoch)
            assertEquals(7, cache.size)
            transitionRows.forEach { assertSame(values.getValue(it), cache[it]) }
        }

        assertTrue(cache.endProjection(epoch, listOf(rows.last())))
        assertFalse(cache.hasTransition)
        assertEquals(2, cache.size)
        assertSame(values.getValue(rows.last()), cache[rows.last()])
    }

    @Test
    fun staleAnchorCompletionCannotReleaseANewerTransitionsProtection() {
        val rows = (0..4).map { row(it) }
        val values = rows.associateWith { LoadedChapter("title-${it.index}") }
        val cache = CatalogOutlineCache<LoadedChapter>(2)
        val oldEpoch = cache.beginProjection(rows.take(2))
        val currentRows = rows.drop(2)
        val currentEpoch = cache.beginProjection(currentRows)
        cache.store(currentRows, values, emptyList(), currentEpoch)

        assertFalse(cache.endProjection(oldEpoch, rows.take(2)))
        assertTrue(cache.hasTransition)
        assertEquals(3, cache.size)
        currentRows.forEach { assertSame(values.getValue(it), cache[it]) }

        assertTrue(cache.endProjection(currentEpoch, listOf(rows.last())))
        assertFalse(cache.hasTransition)
        assertEquals(2, cache.size)
    }

    private data class LoadedChapter(val title: String)

    private fun row(index: Int, volume: Boolean = false) =
        CatalogOutlineEntry("chapter-$index", index, volume)
}
