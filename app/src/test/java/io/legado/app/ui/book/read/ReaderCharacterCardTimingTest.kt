package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderCharacterCardTimingTest {
    @Test
    fun runningTimingKeepsCountingFromTheOriginalStart() {
        val timing = ReaderCharacterCardTiming(startedAtMs = 10_000L)

        assertEquals(0L, timing.elapsedMillis(10_000L))
        assertEquals(3_200L, timing.elapsedMillis(13_200L))
        assertEquals(8_450L, timing.elapsedMillis(18_450L))
    }

    @Test
    fun completedTimingReturnsTheSameTotalWhenReadLater() {
        val timing = ReaderCharacterCardTiming(startedAtMs = 10_000L)
            .finish(nowMs = 28_600L)

        assertEquals(18_600L, timing.elapsedMillis(28_600L))
        assertEquals(18_600L, timing.elapsedMillis(65_000L))
    }

    @Test
    fun aNewGenerationStartsFromZeroAfterThePreviousOneFinishes() {
        val previous = ReaderCharacterCardTiming(startedAtMs = 10_000L)
            .finish(nowMs = 28_600L)
        val next = ReaderCharacterCardTiming(startedAtMs = 40_000L)

        assertEquals(18_600L, previous.elapsedMillis(40_000L))
        assertEquals(0L, next.elapsedMillis(40_000L))
    }

    @Test
    fun candidatePickingTimeDoesNotCountTowardTheNextPhase() {
        val scan = ReaderCharacterCardTiming(startedAtMs = 5_000L).finish(nowMs = 8_000L)
        val generation = ReaderCharacterCardTiming(startedAtMs = 40_000L, previousElapsedMs = scan.totalElapsedMs!!)

        assertEquals(3_000L, generation.elapsedMillis(40_000L))
        assertEquals(9_000L, generation.finish(46_000L).elapsedMillis(58_000L))
    }
}
