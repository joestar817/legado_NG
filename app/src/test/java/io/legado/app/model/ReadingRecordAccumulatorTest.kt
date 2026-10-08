package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReadingRecordAccumulatorTest {
    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 5).toEpochDay()
    private val wallStart = Instant.parse("2026-10-05T10:00:00Z").toEpochMilli()

    private fun time(elapsed: Long, wall: Long = wallStart + elapsed, zone: ZoneId = utc) =
        ReadingRecordTime(elapsed, wall, zone)

    private fun page(
        target: ReadingRecordAccumulator,
        owner: Any = Any(),
        now: ReadingRecordTime = time(0),
        key: String = "book-a",
        name: String = "A",
        source: ReadingRecordSource = ReadingRecordSource.PAGE,
    ): Any {
        target.bindBook(source.kind, key, name, now)
        target.pageResumed(source, owner, key, now)
        return owner
    }

    private fun play(
        target: ReadingRecordAccumulator,
        source: ReadingRecordSource,
        owner: Any = Any(),
        now: ReadingRecordTime = time(0),
        key: String = "book-a",
        name: String = "A",
    ): Any {
        target.bindBook(source.kind, key, name, now)
        target.register(source, owner, now)
        target.setActive(source, owner, true, now)
        return owner
    }

    private fun assertTotals(
        target: ReadingRecordAccumulator,
        expectedBooks: Map<String, Long>,
        expectedDays: Map<Long, Long> = mapOf(day to expectedBooks.values.sum()),
    ) {
        val snapshot = target.pendingSnapshot()
        assertEquals(expectedBooks, snapshot.books.mapValues { it.value.readTime })
        assertEquals(expectedDays, snapshot.days)
        assertEquals(snapshot.books.values.sumOf { it.readTime }, snapshot.days.values.sum())
    }

    @Test
    fun leavingWithoutTurningAPageSavesTheFinalInterval() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        assertFalse(target.hasPending)
        target.remove(ReadingRecordSource.PAGE, owner, time(12_000))
        assertTotals(target, mapOf("A" to 12_000L))
        assertEquals(wallStart + 12_000, target.pendingSnapshot().books.getValue("A").lastRead)
    }

    @Test
    fun longUnchangedPageRetainsTheOldUncappedTimingSemantics() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        val duration = 4 * 3_600_000L
        target.remove(ReadingRecordSource.PAGE, owner, time(duration))
        assertTotals(target, mapOf("A" to duration))
    }

    @Test
    fun pageCheckpointsAndLeavingDoNotDoubleCount() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.checkpoint(ReadingRecordKind.TEXT, time(5_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(9_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(12_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(20_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(30_000))
        assertTotals(target, mapOf("A" to 12_000L))
    }

    @Test
    fun resumingExcludesTimeSpentInTheBackground() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.remove(ReadingRecordSource.PAGE, owner, time(10_000))
        target.pageResumed(ReadingRecordSource.PAGE, owner, "book-a", time(50_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(60_000))
        assertTotals(target, mapOf("A" to 20_000L))
    }

    @Test
    fun pausedPageKeepsItsChangedSourceKeyWhenItsOriginalIntentIsResumed() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.bindBook(ReadingRecordKind.TEXT, "new-source", "A", time(10_000))
        target.setActive(ReadingRecordSource.PAGE, owner, false, time(20_000))
        target.pageResumed(ReadingRecordSource.PAGE, owner, "book-a", time(50_000))
        target.setActive(ReadingRecordSource.PAGE, owner, false, time(60_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(70_000))
        assertTotals(target, mapOf("A" to 30_000L))
    }

    @Test
    fun switchingBooksWhilePageIsPausedUpdatesItsBindingWithoutCountingThePause() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.setActive(ReadingRecordSource.PAGE, owner, false, time(10_000))
        target.bindBook(ReadingRecordKind.TEXT, "book-b", "B", time(20_000))
        target.pageResumed(ReadingRecordSource.PAGE, owner, "book-a", time(40_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(50_000))
        assertTotals(target, mapOf("A" to 10_000L, "B" to 10_000L))
    }

    @Test
    fun repeatedPauseAndDestroyDoNotLoseOrRepeatTheLastInterval() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.setActive(ReadingRecordSource.PAGE, owner, false, time(10_000))
        target.setActive(ReadingRecordSource.PAGE, owner, false, time(20_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(30_000))
        target.setActive(ReadingRecordSource.PAGE, owner, true, time(40_000))
        assertTotals(target, mapOf("A" to 10_000L))
    }

    @Test
    fun mangaLifecycleUsesTheSameBoundaries() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target, source = ReadingRecordSource.MANGA_PAGE)
        target.checkpoint(ReadingRecordKind.MANGA, time(5_000))
        target.remove(ReadingRecordSource.MANGA_PAGE, owner, time(10_000))
        target.pageResumed(ReadingRecordSource.MANGA_PAGE, owner, "book-a", time(50_000))
        target.remove(ReadingRecordSource.MANGA_PAGE, owner, time(60_000))
        assertTotals(target, mapOf("A" to 20_000L))
    }

    @Test
    fun disablingAndReenablingDoesNotBackfillTheDisabledInterval() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.setEnabled(false, time(10_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(30_000))
        target.setEnabled(true, time(50_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(60_000))
        assertTotals(target, mapOf("A" to 20_000L))
    }

    @Test
    fun initiallyDisabledOnlyCountsAfterEnabling() {
        val target = ReadingRecordAccumulator(false)
        val owner = page(target)
        target.setEnabled(true, time(30_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(40_000))
        assertTotals(target, mapOf("A" to 10_000L))
    }

    @Test
    fun multiplePageOwnersUseIdentityAndCountTheirUnion() {
        val target = ReadingRecordAccumulator(true)
        // Equality must not let one Activity remove another Activity's session.
        data class Owner(val id: Int)
        val first = Owner(1)
        val second = Owner(1)
        page(target, first)
        target.pageResumed(ReadingRecordSource.PAGE, second, "book-a", time(10_000))
        target.remove(ReadingRecordSource.PAGE, first, time(20_000))
        target.remove(ReadingRecordSource.PAGE, second, time(30_000))
        assertTotals(target, mapOf("A" to 30_000L))
    }

    @Test
    fun readingAloudAndForegroundPagesDoNotDoubleCountAndContinueInBackground() {
        val target = ReadingRecordAccumulator(true)
        val page = page(target)
        val service = play(target, ReadingRecordSource.READ_ALOUD, now = time(10_000))
        target.remove(ReadingRecordSource.PAGE, page, time(20_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(30_000))
        target.setActive(ReadingRecordSource.READ_ALOUD, service, false, time(40_000))
        assertTotals(target, mapOf("A" to 40_000L))
    }

    @Test
    fun pausingReadingAloudKeepsAnOpenReadingPageActive() {
        val target = ReadingRecordAccumulator(true)
        val page = page(target)
        val service = play(target, ReadingRecordSource.READ_ALOUD, now = time(10_000))
        target.setActive(ReadingRecordSource.READ_ALOUD, service, false, time(20_000))
        target.remove(ReadingRecordSource.PAGE, page, time(30_000))
        assertTotals(target, mapOf("A" to 30_000L))
    }

    @Test
    fun audioPauseAndBufferingExcludeInactiveTimeAndSaveTheirTail() {
        val target = ReadingRecordAccumulator(true)
        val service = play(target, ReadingRecordSource.AUDIO)
        target.setActive(ReadingRecordSource.AUDIO, service, false, time(10_000))
        target.setActive(ReadingRecordSource.AUDIO, service, true, time(40_000))
        target.remove(ReadingRecordSource.AUDIO, service, time(55_000))
        target.remove(ReadingRecordSource.AUDIO, service, time(65_000))
        assertTotals(target, mapOf("A" to 25_000L))
    }

    @Test
    fun differentReaderModelsKeepIndependentTotalsAndTheCalendarMatchesTheirSum() {
        val target = ReadingRecordAccumulator(true)
        val page = page(target)
        val manga = page(target, key = "manga-b", name = "B", source = ReadingRecordSource.MANGA_PAGE)
        val audio = play(target, ReadingRecordSource.AUDIO, key = "audio-c", name = "C")
        target.remove(ReadingRecordSource.PAGE, page, time(10_000))
        target.remove(ReadingRecordSource.MANGA_PAGE, manga, time(20_000))
        target.remove(ReadingRecordSource.AUDIO, audio, time(30_000))
        assertTotals(target, mapOf("A" to 10_000L, "B" to 20_000L, "C" to 30_000L))
    }

    @Test
    fun duplicateServiceRegistrationDoesNotResetCurrentPlayback() {
        val target = ReadingRecordAccumulator(true)
        val service = play(target, ReadingRecordSource.AUDIO)
        target.register(ReadingRecordSource.AUDIO, service, time(10_000))
        target.remove(ReadingRecordSource.AUDIO, service, time(20_000))
        assertTotals(target, mapOf("A" to 20_000L))
    }

    @Test
    fun replacedServiceOwnerCannotStartOrStopItsReplacement() {
        val target = ReadingRecordAccumulator(true)
        val first = play(target, ReadingRecordSource.READ_ALOUD)
        val second = Any()
        target.register(ReadingRecordSource.READ_ALOUD, second, time(10_000))
        target.setActive(ReadingRecordSource.READ_ALOUD, first, true, time(20_000))
        target.setActive(ReadingRecordSource.READ_ALOUD, second, true, time(30_000))
        target.remove(ReadingRecordSource.READ_ALOUD, first, time(40_000))
        target.remove(ReadingRecordSource.READ_ALOUD, second, time(50_000))
        assertTotals(target, mapOf("A" to 30_000L))
    }

    @Test
    fun unknownOwnerCallbacksCannotMoveTheActiveIntervalAnchor() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        // A stale callback with a different date must not re-anchor the active page.
        target.remove(ReadingRecordSource.PAGE, Any(), time(10_000, wallStart + 86_400_000))
        target.setActive(ReadingRecordSource.READ_ALOUD, Any(), true, time(20_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(30_000))
        assertTotals(target, mapOf("A" to 30_000L))
    }

    @Test
    fun switchingBooksSettlesTheOldNameAndMovesTheCurrentPage() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.bindBook(ReadingRecordKind.TEXT, "book-b", "B", time(10_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(30_000))
        assertTotals(target, mapOf("A" to 10_000L, "B" to 20_000L))
        assertEquals(wallStart + 10_000, target.pendingSnapshot().books.getValue("A").lastRead)
    }

    @Test
    fun newPageResumedBeforeBindingDoesNotAccrueAgainstTheOldGlobalBook() {
        val target = ReadingRecordAccumulator(true)
        target.bindBook(ReadingRecordKind.TEXT, "book-a", "A", time(0))
        val owner = Any()
        target.pageResumed(ReadingRecordSource.PAGE, owner, "book-b", time(10_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(20_000))
        assertFalse(target.hasPending)
        target.bindBook(ReadingRecordKind.TEXT, "book-b", "B", time(30_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(40_000))
        assertTotals(target, mapOf("B" to 10_000L))
    }

    @Test
    fun waitingPageForAnotherBookDoesNotFollowAnUnrelatedBinding() {
        val target = ReadingRecordAccumulator(true)
        val oldPage = page(target)
        val waitingPage = Any()
        target.pageResumed(ReadingRecordSource.PAGE, waitingPage, "book-c", time(10_000))
        target.bindBook(ReadingRecordKind.TEXT, "book-b", "B", time(20_000))
        target.remove(ReadingRecordSource.PAGE, oldPage, time(30_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(40_000))
        target.bindBook(ReadingRecordKind.TEXT, "book-c", "C", time(50_000))
        target.remove(ReadingRecordSource.PAGE, waitingPage, time(60_000))
        assertTotals(target, mapOf("A" to 20_000L, "B" to 10_000L, "C" to 10_000L))
    }

    @Test
    fun unknownPageBookWaitsForBindingWithoutBackfill() {
        val target = ReadingRecordAccumulator(true)
        target.bindBook(ReadingRecordKind.TEXT, "old-book", "Old", time(0))
        val owner = Any()
        target.pageResumed(ReadingRecordSource.PAGE, owner, null, time(10_000))
        target.bindBook(ReadingRecordKind.TEXT, "book-a", "A", time(30_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(40_000))
        assertTotals(target, mapOf("A" to 10_000L))
    }

    @Test
    fun playbackWithoutABoundBookCannotBeBackfilledAfterBinding() {
        val target = ReadingRecordAccumulator(true)
        val owner = Any()
        target.register(ReadingRecordSource.AUDIO, owner, time(0))
        target.setActive(ReadingRecordSource.AUDIO, owner, true, time(0))
        target.bindBook(ReadingRecordKind.AUDIO, "book-a", "A", time(20_000))
        target.checkpoint(ReadingRecordKind.AUDIO, time(30_000))
        assertTrue(target.pendingSnapshot().isEmpty())
        target.setActive(ReadingRecordSource.AUDIO, owner, true, time(40_000))
        target.remove(ReadingRecordSource.AUDIO, owner, time(50_000))
        assertTotals(target, mapOf("A" to 10_000L))
    }

    @Test
    fun changingBookStopsOldServiceUntilPlaybackIsConfirmedAgain() {
        val target = ReadingRecordAccumulator(true)
        val owner = play(target, ReadingRecordSource.AUDIO)
        target.bindBook(ReadingRecordKind.AUDIO, "book-b", "B", time(10_000))
        target.checkpoint(ReadingRecordKind.AUDIO, time(20_000))
        target.setActive(ReadingRecordSource.AUDIO, owner, true, time(30_000))
        target.remove(ReadingRecordSource.AUDIO, owner, time(40_000))
        assertTotals(target, mapOf("A" to 10_000L, "B" to 10_000L))
    }

    @Test
    fun sameServiceCannotActivateAnOldOrUnknownBookAfterSwitching() {
        val target = ReadingRecordAccumulator(true)
        val owner = play(target, ReadingRecordSource.AUDIO)
        target.bindBook(ReadingRecordKind.AUDIO, "book-b", "B", time(10_000))
        target.setPlaybackActive(ReadingRecordSource.AUDIO, owner, true, "book-a", time(20_000))
        target.setPlaybackActive(ReadingRecordSource.AUDIO, owner, true, null, time(30_000))
        target.setPlaybackActive(ReadingRecordSource.AUDIO, owner, true, "book-b", time(40_000))
        // A current owner's stop remains valid even after it clears its playback key.
        target.setPlaybackActive(ReadingRecordSource.AUDIO, owner, false, null, time(50_000))
        assertTotals(target, mapOf("A" to 10_000L, "B" to 10_000L))
    }

    @Test
    fun playbackConfirmationStillRequiresTheCurrentRegisteredOwner() {
        val target = ReadingRecordAccumulator(true)
        target.bindBook(ReadingRecordKind.TEXT, "book-a", "A", time(0))
        val first = Any()
        val replacement = Any()
        target.register(ReadingRecordSource.READ_ALOUD, first, time(0))
        target.register(ReadingRecordSource.READ_ALOUD, replacement, time(10_000))
        target.setPlaybackActive(ReadingRecordSource.READ_ALOUD, first, true, "book-a", time(20_000))
        target.setPlaybackActive(ReadingRecordSource.READ_ALOUD, Any(), true, "book-a", time(30_000))
        assertFalse(target.hasPending)
        target.setPlaybackActive(ReadingRecordSource.READ_ALOUD, replacement, true, "book-a", time(40_000))
        target.setPlaybackActive(ReadingRecordSource.READ_ALOUD, first, false, null, time(50_000))
        target.setPlaybackActive(ReadingRecordSource.READ_ALOUD, replacement, false, "book-a", time(60_000))
        assertTotals(target, mapOf("A" to 20_000L))
    }

    @Test
    fun stalePlaybackConfirmationCannotReanchorCurrentBookToAnotherDay() {
        val target = ReadingRecordAccumulator(true)
        val owner = play(target, ReadingRecordSource.AUDIO)
        target.bindBook(ReadingRecordKind.AUDIO, "book-b", "B", time(10_000))
        target.setPlaybackActive(ReadingRecordSource.AUDIO, owner, true, "book-b", time(20_000))
        target.setPlaybackActive(
            ReadingRecordSource.AUDIO, owner, true, "book-a",
            time(30_000, wallStart + 86_400_000L),
        )
        target.setPlaybackActive(ReadingRecordSource.AUDIO, owner, false, "book-b", time(40_000))
        assertTotals(target, mapOf("A" to 10_000L, "B" to 20_000L))
    }

    @Test
    fun rebindingTheSameBookRetainsServiceActivityAndUsesItsUpdatedName() {
        val target = ReadingRecordAccumulator(true)
        val owner = play(target, ReadingRecordSource.AUDIO)
        target.bindBook(ReadingRecordKind.AUDIO, "book-a", "Renamed", time(10_000))
        target.remove(ReadingRecordSource.AUDIO, owner, time(20_000))
        assertTotals(target, mapOf("A" to 10_000L, "Renamed" to 10_000L))
    }

    @Test
    fun aMidnightIntervalIsSplitByItsOriginalLocalDay() {
        val target = ReadingRecordAccumulator(true)
        val zone = ZoneId.of("Asia/Shanghai")
        val wall = Instant.parse("2026-10-05T15:59:50Z").toEpochMilli()
        val owner = page(target, now = time(0, wall, zone))
        target.remove(ReadingRecordSource.PAGE, owner, time(30_000, wall + 30_000, zone))
        assertTotals(target, mapOf("A" to 30_000L), mapOf(day to 10_000L, day + 1 to 20_000L))
    }

    @Test
    fun daylightSavingDaysUseActualTwentyThreeAndTwentyFiveHourDurations() {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23, LocalDate.of(2026, 11, 1) to 25)) {
            val target = ReadingRecordAccumulator(true)
            val wall = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val duration = hours * 3_600_000L
            val owner = play(target, ReadingRecordSource.AUDIO, now = time(0, wall, zone))
            target.remove(ReadingRecordSource.AUDIO, owner, time(duration, wall + duration, zone))
            assertTotals(target, mapOf("A" to duration), mapOf(date.toEpochDay() to duration))
        }
    }

    @Test
    fun wallClockChangesNeverChangeTheElapsedTotal() {
        val target = ReadingRecordAccumulator(true)
        val owner = play(target, ReadingRecordSource.AUDIO)
        target.checkpoint(ReadingRecordKind.AUDIO, time(30_000, wallStart + 365L * 86_400_000L))
        target.checkpoint(ReadingRecordKind.AUDIO, time(60_000, wallStart - 365L * 86_400_000L))
        target.remove(ReadingRecordSource.AUDIO, owner, time(90_000, wallStart))
        assertTotals(
            target, mapOf("A" to 90_000L),
            mapOf(day to 30_000L, day + 365 to 30_000L, day - 365 to 30_000L),
        )
    }

    @Test
    fun timezoneChangeReanchorsOnlyTheNextInterval() {
        val target = ReadingRecordAccumulator(true)
        val wall = Instant.parse("2026-10-05T23:30:00Z").toEpochMilli()
        val shanghai = ZoneId.of("Asia/Shanghai")
        val owner = page(target, now = time(0, wall, utc))
        target.checkpoint(ReadingRecordKind.TEXT, time(30_000, wall + 30_000, shanghai))
        target.remove(ReadingRecordSource.PAGE, owner, time(60_000, wall + 60_000, shanghai))
        assertTotals(target, mapOf("A" to 60_000L), mapOf(day to 30_000L, day + 1 to 30_000L))
    }

    @Test
    fun monotonicRollbackDoesNotCreateANegativeDuration() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target, now = time(10_000))
        target.checkpoint(ReadingRecordKind.TEXT, time(5_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(8_000))
        assertTotals(target, mapOf("A" to 3_000L))
    }

    @Test
    fun acknowledgingASnapshotPreservesNewSamplesAndTheirLastRead() {
        val target = ReadingRecordAccumulator(true)
        val owner = page(target)
        target.checkpoint(ReadingRecordKind.TEXT, time(10_000))
        val writing = target.pendingSnapshot()
        target.checkpoint(ReadingRecordKind.TEXT, time(15_000))
        target.acknowledge(writing)
        assertTotals(target, mapOf("A" to 5_000L))
        assertEquals(wallStart + 15_000, target.pendingSnapshot().books.getValue("A").lastRead)
        assertEquals(10_000L, writing.books.getValue("A").readTime)
        target.remove(ReadingRecordSource.PAGE, owner, time(20_000))
        assertTotals(target, mapOf("A" to 10_000L))
        target.acknowledge(target.pendingSnapshot())
        assertFalse(target.hasPending)
        assertTrue(target.pendingSnapshot().isEmpty())
    }

    @Test
    fun failedWriteLeavesBothMapsIntactForRetryIncludingNewBooksAndDays() {
        val target = ReadingRecordAccumulator(true)
        val wall = Instant.parse("2026-10-05T23:59:50Z").toEpochMilli()
        val owner = page(target, now = time(0, wall))
        target.checkpoint(ReadingRecordKind.TEXT, time(20_000, wall + 20_000))
        val failedBatch = target.pendingSnapshot()
        // Failure does not acknowledge either map; newer samples remain in the retry.
        target.bindBook(ReadingRecordKind.TEXT, "book-b", "B", time(20_000, wall + 20_000))
        target.remove(ReadingRecordSource.PAGE, owner, time(30_000, wall + 30_000))
        assertEquals(mapOf(day to 10_000L, day + 1 to 10_000L), failedBatch.days)
        assertTotals(
            target, mapOf("A" to 20_000L, "B" to 10_000L),
            mapOf(day to 10_000L, day + 1 to 20_000L),
        )
        target.acknowledge(target.pendingSnapshot())
        assertFalse(target.hasPending)
    }
}
