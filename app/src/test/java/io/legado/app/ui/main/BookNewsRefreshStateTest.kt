package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookNewsRefreshStateTest {
    private val first = BookNewsCheckTarget("book-a", "source-a")
    private val second = BookNewsCheckTarget("book-b", "source-a")

    @Test
    fun scopeOnlyContainsUpdatableOnlineShelfBooks() {
        assertTrue(BookNewsRefreshPolicy.isEligible(false, false, true))
        assertFalse(BookNewsRefreshPolicy.isEligible(true, false, true))
        assertFalse(BookNewsRefreshPolicy.isEligible(false, true, true))
        assertFalse(BookNewsRefreshPolicy.isEligible(false, false, false))
    }

    @Test
    fun completionTimeRequiresEveryTargetAndIgnoresRepeatedCompletions() {
        val tracker = BookNewsRefreshTracker()
        assertTrue(tracker.tryStart(100, 100, false))
        tracker.setTargets(listOf(first, second), 100, reuseSuccessful = true)
        tracker.complete(first.bookUrl, first.origin, true, 110)
        tracker.complete(first.bookUrl, first.origin, true, 120)
        assertTrue(tracker.state.checking)
        assertNull(tracker.state.lastCheckedAt)
        tracker.complete(second.bookUrl, second.origin, true, 130)
        assertFalse(tracker.state.checking)
        assertFalse(tracker.state.checkFailed)
        assertEquals(130L, tracker.state.lastCheckedAt)
        tracker.complete(second.bookUrl, second.origin, true, 140)
        assertEquals(130L, tracker.state.lastCheckedAt)
    }

    @Test
    fun failedTargetIsSkippedWhileSuccessfulTargetRemainsReusable() {
        val tracker = BookNewsRefreshTracker(10)
        assertTrue(tracker.tryStart(100, 100, true))
        tracker.setTargets(listOf(first, second), 100, reuseSuccessful = false)
        tracker.complete(first.bookUrl, first.origin, true, 110)
        tracker.complete(second.bookUrl, second.origin, false, 120)
        assertEquals(120L, tracker.state.lastCheckedAt)
        assertFalse(tracker.state.checkFailed)
        assertFalse(tracker.state.checking)
        assertTrue(tracker.wasSuccessfullyChecked(first, 130))
        assertFalse(tracker.wasSuccessfullyChecked(second, 130))
    }

    @Test
    fun emptyScopeCompletesWithoutWaitingForQueue() {
        val tracker = BookNewsRefreshTracker()
        assertTrue(tracker.tryStart(100, 100, false))
        tracker.setTargets(emptyList(), 100, reuseSuccessful = true)
        assertFalse(tracker.state.checking)
        assertFalse(tracker.state.checkFailed)
        assertEquals(100L, tracker.state.lastCheckedAt)
    }

    @Test
    fun forceBypassesAttemptCooldownButNeverDuplicatesActiveBatch() {
        val tracker = BookNewsRefreshTracker()
        assertTrue(tracker.tryStart(100, 100, false))
        assertFalse(tracker.tryStart(101, 101, true))
        tracker.cancel()
        assertFalse(tracker.tryStart(102, 102, false))
        assertTrue(tracker.tryStart(102, 102, true))
    }

    @Test
    fun cancelNeverAdvancesCompletionTimeAndLateResultCannotFinishCancelledBatch() {
        val tracker = BookNewsRefreshTracker(10)
        assertTrue(tracker.tryStart(100, 100, true))
        tracker.setTargets(listOf(first), 100, reuseSuccessful = false)
        tracker.cancel()
        tracker.complete(first.bookUrl, first.origin, false, 110)
        assertFalse(tracker.state.checking)
        assertTrue(tracker.state.checkFailed)
        assertEquals(10L, tracker.state.lastCheckedAt)
    }

    @Test
    fun invalidatedIdentityIsSkippedWithoutReusingItsOldSuccess() {
        val tracker = BookNewsRefreshTracker()
        tracker.complete(first.bookUrl, first.origin, true, 90)
        assertTrue(tracker.tryStart(100, 100, true))
        tracker.setTargets(listOf(first), 100, reuseSuccessful = false)
        tracker.complete(first.bookUrl, "source-b", true, 110)
        assertFalse(tracker.state.checkFailed)
        assertEquals(110L, tracker.state.lastCheckedAt)
        assertFalse(tracker.wasSuccessfullyChecked(first, 120))
        assertTrue(tracker.wasSuccessfullyChecked(BookNewsCheckTarget(first.bookUrl, "source-b"), 120))
    }

    @Test
    fun startupSuccessIsReusedOnlyForCoveredTargetsAndManualStillChecksAll() {
        val tracker = BookNewsRefreshTracker()
        // 在首页批次注册前完成的启动刷新也可以复用。
        tracker.complete(first.bookUrl, first.origin, true, 100)
        assertTrue(tracker.tryStart(110, 110, false))
        tracker.setTargets(listOf(first, second), 110, reuseSuccessful = true)
        assertFalse(tracker.needsCheck(first))
        assertTrue(tracker.needsCheck(second))
        tracker.complete(second.bookUrl, second.origin, true, 120)
        assertEquals(120L, tracker.state.lastCheckedAt)
        assertTrue(tracker.tryStart(130, 130, true))
        tracker.setTargets(listOf(first, second), 130, reuseSuccessful = false)
        assertTrue(tracker.needsCheck(first))
        assertTrue(tracker.needsCheck(second))
    }

    @Test
    fun startupNeverSkipsFailedOrExpiredTargets() {
        val tracker = BookNewsRefreshTracker()
        tracker.complete(first.bookUrl, first.origin, true, 100)
        tracker.complete(second.bookUrl, second.origin, false, 110)
        assertTrue(tracker.wasSuccessfullyChecked(first, 120))
        assertFalse(tracker.wasSuccessfullyChecked(second, 120))
        assertFalse(tracker.wasSuccessfullyChecked(first, 100 + BookNewsRefreshPolicy.CHECK_INTERVAL_MS))
        tracker.complete(first.bookUrl, first.origin, false, 130)
        assertFalse(tracker.wasSuccessfullyChecked(first, 140))
    }

    @Test
    fun skippedFailureUsesNormalCheckIntervalWhileForceStillRetries() {
        val tracker = BookNewsRefreshTracker(100)
        assertTrue(tracker.tryStart(110, 110, true))
        tracker.setTargets(listOf(first), 110, reuseSuccessful = false)
        tracker.complete(first.bookUrl, first.origin, false, 120)
        assertFalse(tracker.tryStart(130, 130, false))
        assertFalse(tracker.tryStart(120 + BookNewsRefreshPolicy.ATTEMPT_COOLDOWN_MS,
            110 + BookNewsRefreshPolicy.ATTEMPT_COOLDOWN_MS, false))
        assertTrue(tracker.tryStart(130, 130, true))
        tracker.setTargets(listOf(first), 130, reuseSuccessful = false)
        tracker.complete(first.bookUrl, first.origin, false, 140)
        assertTrue(tracker.tryStart(140 + BookNewsRefreshPolicy.CHECK_INTERVAL_MS,
            130 + BookNewsRefreshPolicy.CHECK_INTERVAL_MS, false))
    }

    @Test
    fun allFailedTargetsFinishNormallyWithoutCreatingSuccessfulCache() {
        val tracker = BookNewsRefreshTracker(10)
        assertTrue(tracker.tryStart(100, 100, true))
        tracker.setTargets(listOf(first, second), 100, reuseSuccessful = false)
        tracker.complete(first.bookUrl, first.origin, false, 110)
        assertTrue(tracker.state.checking)
        tracker.complete(second.bookUrl, null, false, 120)
        assertFalse(tracker.state.checking)
        assertFalse(tracker.state.checkFailed)
        assertEquals(120L, tracker.state.lastCheckedAt)
        assertFalse(tracker.wasSuccessfullyChecked(first, 130))
        assertFalse(tracker.wasSuccessfullyChecked(second, 130))
    }

    @Test
    fun fullyReusedScopeKeepsEarliestRealCheckTimeInsteadOfDisplayTime() {
        val tracker = BookNewsRefreshTracker()
        tracker.complete(first.bookUrl, first.origin, true, 100)
        tracker.complete(second.bookUrl, second.origin, true, 105)
        assertTrue(tracker.tryStart(110, 110, false))
        tracker.setTargets(listOf(first, second), 110, reuseSuccessful = true)
        assertFalse(tracker.state.checking)
        assertEquals(100L, tracker.state.lastCheckedAt)
        assertFalse(tracker.needsCheck(first))
        assertFalse(tracker.needsCheck(second))
    }

    @Test
    fun fullIntervalAndBackwardClockAllowAutomaticRecheck() {
        val tracker = BookNewsRefreshTracker(100)
        assertFalse(tracker.tryStart(101, 1000, false))
        assertTrue(tracker.tryStart(100 + BookNewsRefreshPolicy.CHECK_INTERVAL_MS, 1000, false))
        tracker.cancel()
        assertTrue(tracker.tryStart(99, 1000 + BookNewsRefreshPolicy.ATTEMPT_COOLDOWN_MS, false))
        assertFalse(tracker.wasSuccessfullyChecked(first, 99))
    }
}
