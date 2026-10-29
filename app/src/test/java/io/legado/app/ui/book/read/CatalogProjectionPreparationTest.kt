package io.legado.app.ui.book.read

import android.app.Application
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.CatalogOutlineEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real preparation/cache helpers; Compose publication and JS title evaluation need separate checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, manifest = Config.NONE)
class CatalogProjectionPreparationTest {

    @Test
    fun allBatchesAreStagedWithoutEvictingOrPartiallyUpdatingTheVisibleCache() = runBlocking {
        val oldRow = row(0)
        val oldChapter = chapter(oldRow)
        val nextRows = (1..3).map { row(it) }
        val chapters = nextRows.associateWith { chapter(it) }
        val cache = CatalogOutlineCache<BookChapter>(1)
        cache.store(listOf(oldRow), mapOf(oldRow to oldChapter), listOf(oldRow))
        val lastBatchReady = CompletableDeferred<Unit>()
        val releaseLastBatch = CompletableDeferred<Unit>()
        val batches = mutableListOf<List<CatalogOutlineEntry>>()
        val prepared = async(start = CoroutineStart.UNDISPATCHED) {
            prepareCatalogProjection(cache, nextRows, batchSize = 2) { batch ->
                batches.add(batch)
                if (batches.size == 2) {
                    lastBatchReady.complete(Unit)
                    releaseLastBatch.await()
                }
                batch.associateWith { chapters.getValue(it) }
            }
        }
        try {
            withTimeout(5_000) { lastBatchReady.await() }
            assertFalse(prepared.isCompleted)
            assertEquals(listOf(nextRows.take(2), nextRows.drop(2)), batches)
            assertEquals(nextRows, cache.missing(nextRows))
            assertEquals(1, cache.size)
            assertSame(oldChapter, cache[oldRow])

            releaseLastBatch.complete(Unit)
            val result = withTimeout(5_000) { prepared.await() }
            assertEquals(nextRows, result.requested)
            nextRows.forEach { assertSame(chapters.getValue(it), result.loaded[it]) }
            // Preparation itself never publishes, including after its final batch.
            assertEquals(nextRows, cache.missing(nextRows))
            assertSame(oldChapter, cache[oldRow])
        } finally {
            releaseLastBatch.complete(Unit)
            prepared.cancel()
        }
    }

    @Test
    fun preparationLoadsOnlyMissingIdentitiesAndKeepsDatabaseAbsenceInItsSlot() = runBlocking {
        val rows = (0..3).map { row(it) }
        val cached = chapter(rows[0])
        val loaded = chapter(rows[3])
        val cache = CatalogOutlineCache<BookChapter>(8)
        cache.store(rows.take(2), mapOf(rows[0] to cached), rows.take(2))
        val result = prepareCatalogProjection(cache, rows) { requested ->
            assertEquals(rows.drop(2), requested)
            mapOf(rows[3] to loaded)
        }

        assertEquals(rows, result.requested)
        assertEquals(setOf(rows[0], rows[3]), result.loaded.keys)
        assertSame(cached, result.loaded[rows[0]])
        assertSame(loaded, result.loaded[rows[3]])
        assertSame(cached, cache[rows[0]])
        assertTrue(cache.missing(rows.take(2)).isEmpty())
        assertEquals(rows.drop(2), cache.missing(rows.drop(2)))
    }

    @Test
    fun stagedExistingEntitiesSurviveOrdinaryPagingEvictionWhileMissingRowsLoad() = runBlocking {
        val existing = row(0)
        val missing = row(1)
        val unrelated = row(9)
        val existingChapter = chapter(existing)
        val newChapter = chapter(missing)
        val cache = CatalogOutlineCache<BookChapter>(1)
        cache.store(listOf(existing), mapOf(existing to existingChapter), listOf(existing))
        val release = CompletableDeferred<Unit>()
        val prepared = async(start = CoroutineStart.UNDISPATCHED) {
            prepareCatalogProjection(cache, listOf(existing, missing)) { requested ->
                assertEquals(listOf(missing), requested)
                release.await()
                mapOf(missing to newChapter)
            }
        }
        try {
            cache.store(listOf(unrelated), mapOf(unrelated to chapter(unrelated)), listOf(unrelated))
            assertEquals(listOf(existing), cache.missing(listOf(existing)))
            release.complete(Unit)
            val result = withTimeout(5_000) { prepared.await() }
            assertEquals(listOf(existing, missing), result.requested)
            assertSame(existingChapter, result.loaded[existing])
            assertSame(newChapter, result.loaded[missing])
            val epoch = cache.beginProjection(result.requested)
            assertTrue(cache.store(result.requested, result.loaded, result.requested, epoch))
            assertSame(existingChapter, cache[existing])
            assertSame(newChapter, cache[missing])
        } finally {
            release.complete(Unit)
            prepared.cancel()
        }
    }

    @Test
    fun cancellationRejectsAResultEvenWhenTheLoaderIgnoresCancellation() = runBlocking {
        val target = row(4)
        val cache = CatalogOutlineCache<BookChapter>(2)
        val release = CompletableDeferred<Unit>()
        var returnedResult = false
        val work = async(start = CoroutineStart.UNDISPATCHED) {
            prepareCatalogProjection(cache, listOf(target)) {
                withContext(NonCancellable) { release.await() }
                mapOf(target to chapter(target))
            }.also { returnedResult = true }
        }
        try {
            work.cancel()
            release.complete(Unit)
            try {
                withTimeout(5_000) { work.await() }
                fail("Cancelled preparation returned a result")
            } catch (_: CancellationException) {
                // The helper checks the caller's job after the uncooperative loader returns.
            }
            withTimeout(5_000) { work.join() }
            assertFalse(returnedResult)
            assertEquals(0, cache.size)
            assertEquals(listOf(target), cache.missing(listOf(target)))
        } finally {
            release.complete(Unit)
            work.cancel()
        }
    }

    @Test
    fun aLaterBatchFailureLeavesPreviouslyVisibleEntitiesUntouched() = runBlocking {
        val old = row(0)
        val oldChapter = chapter(old)
        val nextRows = listOf(row(1), row(2))
        val cache = CatalogOutlineCache<BookChapter>(1)
        cache.store(listOf(old), mapOf(old to oldChapter), listOf(old))
        val failure = IllegalStateException("database failed")
        var batches = 0
        try {
            prepareCatalogProjection(cache, nextRows, batchSize = 1) { batch ->
                if (++batches == 2) throw failure
                batch.associateWith { chapter(it) }
            }
            fail("Preparation unexpectedly succeeded")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals(2, batches)
        assertEquals(1, cache.size)
        assertSame(oldChapter, cache[old])
        assertEquals(nextRows, cache.missing(nextRows))
    }

    @Test
    fun repeatedFoldAndSortIntentsBuildOnTheLatestPendingState() {
        val initial = CatalogProjectionState(emptySet(), false)
        val preparation = CatalogProjectionPreparation(initial)
        val first = preparation.request(initial.copy(collapsedVolumes = setOf("volume")), 7, 19, false)
        val firstJob = Job()
        var oldPublishedDuringCancellation: Boolean? = null
        firstJob.invokeOnCompletion { oldPublishedDuringCancellation = preparation.published(first) }
        preparation.attach(first, firstJob)
        val second = preparation.request(
            preparation.desiredState.copy(collapsedVolumes = emptySet()), 7, 19, false,
        )
        assertTrue(firstJob.isCancelled)
        assertEquals(false, oldPublishedDuringCancellation)
        assertEquals(initial, preparation.desiredState)
        val folded = preparation.request(
            preparation.desiredState.copy(collapsedVolumes = setOf("other")), 7, 19, false,
        )
        val sorted = preparation.request(
            preparation.desiredState.copy(descending = true), 42, 0, true,
        )

        assertFalse(preparation.published(first))
        assertFalse(preparation.published(second))
        assertFalse(preparation.published(folded))
        assertTrue(preparation.published(sorted))
        assertEquals(CatalogProjectionState(setOf("other"), true), preparation.desiredState)
        assertEquals(42, sorted.anchorIndex)
        assertEquals(0, sorted.anchorOffset)
        assertTrue(sorted.sort)
    }

    @Test
    fun failedLatestRequestRestoresPublishedStateAndStaleFailureCannotResetNewIntent() {
        val initial = CatalogProjectionState(emptySet(), false)
        val preparation = CatalogProjectionPreparation(initial)
        val foldedState = initial.copy(collapsedVolumes = setOf("volume"))
        val folded = preparation.request(foldedState, 2, 11, false)
        assertTrue(preparation.published(folded))
        val sorted = preparation.request(foldedState.copy(descending = true), 2, 0, true)
        val newerState = initial.copy(descending = true)
        val newer = preparation.request(newerState, 2, 11, false)

        preparation.failed(sorted)
        assertEquals(newerState, preparation.desiredState)
        preparation.failed(newer)
        assertEquals(foldedState, preparation.desiredState)
        assertFalse(preparation.isCurrent(newer))
        assertFalse(preparation.published(newer))
        val retry = preparation.request(preparation.desiredState.copy(descending = true), 2, 0, true)
        assertEquals(CatalogProjectionState(setOf("volume"), true), retry.state)
        assertTrue(preparation.published(retry))
    }

    @Test
    fun contextCancellationRejectsLatePublicationAndLateAttachedWork() {
        val initial = CatalogProjectionState(emptySet(), false)
        val preparation = CatalogProjectionPreparation(initial)
        val request = preparation.request(initial.copy(descending = true), 4, 0, true)
        val job = Job()
        preparation.attach(request, job)
        preparation.cancel()

        assertTrue(job.isCancelled)
        assertFalse(preparation.isCurrent(request))
        assertFalse(preparation.published(request))
        assertEquals(initial, preparation.desiredState)
        val lateJob = Job()
        preparation.attach(request, lateJob)
        assertTrue(lateJob.isCancelled)
        val current = preparation.request(initial.copy(collapsedVolumes = setOf("volume")), 4, 9, false)
        preparation.failed(request)
        assertEquals(current.state, preparation.desiredState)
        assertTrue(preparation.published(current))
    }

    @Test
    fun preparationWindowsCoverNumericStableKeyAndFinalAnchorsWithoutLoadingTheWholeBook() {
        val rows = (0 until 1_000).map { row(it) }
        val preparedRows = catalogProjectionRows(rows, 50, 400, 800, 5, preloadItems = 2)
        val expected = (48..56).toList() + (398..406).toList() + (798..806).toList()
        assertEquals(expected, preparedRows.map { it.index })
        assertEquals(27, preparedRows.size)

        val descending = rows.asReversed()
        val overlapped = catalogProjectionRows(descending, 50, 948, 52, 5, preloadItems = 2)
        assertEquals((951 downTo 941).toList(), overlapped.map { it.index })
        assertEquals(overlapped.size, overlapped.map { it.url }.distinct().size)
    }

    @Test
    fun windowEdgesAndFoldedAwayAnchorsRemainBounded() {
        val rows = (0 until 100).map { row(it) }
        assertTrue(catalogProjectionWindow(emptyList(), 0, 0).isEmpty())
        assertEquals((0..6).toList(), catalogProjectionWindow(rows, -9, 5, 2).map { it.index })
        assertEquals((97..99).toList(), catalogProjectionWindow(rows, 999, 5, 2).map { it.index })
        assertEquals(listOf(50), catalogProjectionWindow(rows, 50, 0, 0).map { it.index })
        val folded = listOf(row(0), row(2, true), row(20, true), row(21))
        assertEquals(listOf(0, 2, 21),
            catalogProjectionRows(folded, 0, 10, 3, 1, 0).map { it.index })
    }

    private fun row(index: Int, volume: Boolean = false) =
        CatalogOutlineEntry("chapter-$index", index, volume)

    private fun chapter(row: CatalogOutlineEntry) = BookChapter(
        url = row.url,
        title = "title-${row.index}",
        isVolume = row.isVolume,
        baseUrl = "https://example.test/chapter/",
        bookUrl = "book-fixture",
        index = row.index,
        isVip = true,
        isPay = true,
        resourceUrl = "https://example.test/audio/${row.index}",
        tag = "chapter-tag",
        wordCount = "1234",
        start = 12L,
        end = 1234L,
        startFragmentId = "start-fragment",
        endFragmentId = "end-fragment",
        variable = "{\"fixture\":\"chapter-variable\"}",
        imgUrl = "https://example.test/cover.png",
    )
}
