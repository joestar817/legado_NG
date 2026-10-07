package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyReadingAccumulatorTest {
    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 5).toEpochDay()
    private val start = Instant.parse("2026-10-05T10:00:00Z").toEpochMilli()

    private fun time(elapsed: Long, wall: Long = start + elapsed, zone: ZoneId = utc) =
        DailyReadingTime(elapsed, wall, zone)

    private fun start(
        target: DailyReadingAccumulator,
        source: DailyReadingSource,
        owner: Any,
        time: DailyReadingTime,
    ) {
        target.register(source, owner, time)
        target.setActive(source, owner, true, time)
    }

    @Test
    fun foregroundReadingAndAudioCountTheirUnion() {
        val target = DailyReadingAccumulator(true)
        val page = Any()
        val audio = Any()
        start(target, DailyReadingSource.PAGE, page, time(0))
        start(target, DailyReadingSource.AUDIO, audio, time(10_000))
        target.remove(DailyReadingSource.PAGE, page, time(20_000))
        target.setActive(DailyReadingSource.AUDIO, audio, false, time(30_000))
        target.checkpoint(time(50_000))
        assertEquals(mapOf(day to 30_000L), target.pendingSnapshot())
        assertFalse(target.hasActiveSources)
    }

    @Test
    fun duplicateStartsAndStopsDoNotAddExtraTime() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        start(target, DailyReadingSource.PAGE, owner, time(0))
        start(target, DailyReadingSource.PAGE, owner, time(5_000))
        target.remove(DailyReadingSource.PAGE, owner, time(10_000))
        target.remove(DailyReadingSource.PAGE, owner, time(20_000))
        assertEquals(mapOf(day to 10_000L), target.pendingSnapshot())
    }

    @Test
    fun pausingOneResumedWindowKeepsTheOtherReadingPageActive() {
        val target = DailyReadingAccumulator(true)
        val firstPage = Any()
        val secondPage = Any()
        start(target, DailyReadingSource.PAGE, firstPage, time(0))
        start(target, DailyReadingSource.PAGE, secondPage, time(10_000))
        target.remove(DailyReadingSource.PAGE, secondPage, time(20_000))
        target.remove(DailyReadingSource.PAGE, firstPage, time(30_000))
        assertEquals(mapOf(day to 30_000L), target.pendingSnapshot())
        assertFalse(target.hasActiveSources)
    }

    @Test
    fun staleOwnerCannotRestartOrStopReplacementService() {
        val target = DailyReadingAccumulator(true)
        val oldOwner = Any()
        val newOwner = Any()
        start(target, DailyReadingSource.READ_ALOUD, oldOwner, time(0))
        target.register(DailyReadingSource.READ_ALOUD, newOwner, time(10_000))
        target.setActive(DailyReadingSource.READ_ALOUD, oldOwner, true, time(20_000))
        target.setActive(DailyReadingSource.READ_ALOUD, newOwner, true, time(30_000))
        target.remove(DailyReadingSource.READ_ALOUD, oldOwner, time(40_000))
        target.remove(DailyReadingSource.READ_ALOUD, newOwner, time(50_000))
        assertEquals(mapOf(day to 30_000L), target.pendingSnapshot())
    }

    @Test
    fun thirtySecondFlushAcknowledgesOnlyItsSnapshot() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        start(target, DailyReadingSource.AUDIO, owner, time(0))
        target.checkpoint(time(30_000))
        val writing = target.pendingSnapshot()
        target.checkpoint(time(35_000))
        target.acknowledge(writing)
        assertEquals(mapOf(day to 5_000L), target.pendingSnapshot())
        target.remove(DailyReadingSource.AUDIO, owner, time(60_000))
        val final = target.pendingSnapshot()
        assertEquals(mapOf(day to 30_000L), final)
        target.acknowledge(final)
        assertFalse(target.hasPending)
    }

    @Test
    fun failedAtomicWriteLeavesWholeBatchForRetry() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        start(target, DailyReadingSource.AUDIO, owner, time(0))
        target.checkpoint(time(30_000))
        val failedBatch = target.pendingSnapshot()
        // A failed database transaction does not acknowledge any part of this batch.
        target.checkpoint(time(60_000))
        assertEquals(mapOf(day to 30_000L), failedBatch)
        assertEquals(mapOf(day to 60_000L), target.pendingSnapshot())
        target.acknowledge(target.pendingSnapshot())
        assertFalse(target.hasPending)
    }

    @Test
    fun disablingAndReenablingExcludesTheDisabledInterval() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        start(target, DailyReadingSource.AUDIO, owner, time(0))
        target.setEnabled(false, time(10_000))
        target.checkpoint(time(30_000))
        target.setEnabled(true, time(50_000))
        target.remove(DailyReadingSource.AUDIO, owner, time(60_000))
        assertEquals(mapOf(day to 20_000L), target.pendingSnapshot())
    }

    @Test
    fun initiallyDisabledDoesNotBackfillBeforeEnabling() {
        val target = DailyReadingAccumulator(false)
        val owner = Any()
        start(target, DailyReadingSource.PAGE, owner, time(0))
        target.setEnabled(true, time(30_000))
        target.remove(DailyReadingSource.PAGE, owner, time(40_000))
        assertEquals(mapOf(day to 10_000L), target.pendingSnapshot())
    }

    @Test
    fun aMidnightIntervalIsSplitByLocalDay() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        val zone = ZoneId.of("Asia/Shanghai")
        val wall = Instant.parse("2026-10-05T15:59:50Z").toEpochMilli()
        start(target, DailyReadingSource.PAGE, owner, time(0, wall, zone))
        target.remove(DailyReadingSource.PAGE, owner, time(30_000, wall + 30_000, zone))
        assertEquals(mapOf(day to 10_000L, day + 1 to 20_000L), target.pendingSnapshot())
    }

    @Test
    fun daylightSavingDaysUseActualTwentyThreeAndTwentyFiveHourDurations() {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23, LocalDate.of(2026, 11, 1) to 25)) {
            val target = DailyReadingAccumulator(true)
            val owner = Any()
            val wall = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val duration = hours * 3_600_000L
            start(target, DailyReadingSource.AUDIO, owner, time(0, wall, zone))
            target.remove(DailyReadingSource.AUDIO, owner, time(duration, wall + duration, zone))
            assertEquals(mapOf(date.toEpochDay() to duration), target.pendingSnapshot())
        }
    }

    @Test
    fun wallClockJumpAndRollbackNeverChangeElapsedTotal() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        start(target, DailyReadingSource.AUDIO, owner, time(0))
        target.checkpoint(time(30_000, start + 365L * 86_400_000L))
        target.checkpoint(time(60_000, start - 365L * 86_400_000L))
        target.remove(DailyReadingSource.AUDIO, owner, time(90_000, start))
        assertEquals(90_000L, target.pendingSnapshot().values.sum())
        assertEquals(3, target.pendingSnapshot().size)
    }

    @Test
    fun timezoneChangeReanchorsOnlyAfterTheCurrentSample() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        val wall = Instant.parse("2026-10-05T23:30:00Z").toEpochMilli()
        val shanghai = ZoneId.of("Asia/Shanghai")
        start(target, DailyReadingSource.AUDIO, owner, time(0, wall, utc))
        target.checkpoint(time(30_000, wall + 30_000, shanghai))
        target.remove(DailyReadingSource.AUDIO, owner, time(60_000, wall + 60_000, shanghai))
        assertEquals(mapOf(day to 30_000L, day + 1 to 30_000L), target.pendingSnapshot())
    }

    @Test
    fun monotonicClockRollbackCannotCreateNegativeDuration() {
        val target = DailyReadingAccumulator(true)
        val owner = Any()
        start(target, DailyReadingSource.PAGE, owner, time(10_000))
        target.checkpoint(time(5_000))
        target.remove(DailyReadingSource.PAGE, owner, time(8_000))
        assertEquals(mapOf(day to 3_000L), target.pendingSnapshot())
    }
}
