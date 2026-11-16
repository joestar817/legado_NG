package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageSwipeMotionTest {
    @Test
    fun aSecondSwipeInTheSameDirectionLeavesTheCommittedGroupPair() {
        for (direction in listOf(-1, 1)) {
            assertFalse(shouldRegrabBookshelfSwipe(direction, direction, settlingToTarget = true))
            assertTrue(shouldRegrabBookshelfSwipe(-direction, direction, settlingToTarget = true))
        }
    }

    @Test
    fun aReturningPairCanBeGrabbedAgainTowardItsNeighbor() {
        for (direction in listOf(-1, 1)) {
            assertTrue(shouldRegrabBookshelfSwipe(direction, direction, settlingToTarget = false))
            assertFalse(shouldRegrabBookshelfSwipe(-direction, direction, settlingToTarget = false))
            assertFalse(shouldRegrabBookshelfSwipe(null, direction, settlingToTarget = false))
        }
    }

    @Test
    fun noRemainingMovementEndsImmediately() {
        assertEquals(0, bookshelfGroupSettleDuration(1080, 0f, 2500f))
        assertEquals(0, pageSwipeSettleDuration(0f, 600))
    }

    @Test
    fun stationaryReleaseUsesTheRemainingPageFraction() {
        assertEquals(150, bookshelfGroupSettleDuration(1080, 540f, 0f))
        assertEquals(175, bookshelfGroupSettleDuration(1080, 810f, 0f))
    }

    @Test
    fun fasterReleaseSettlesSoonerWithTheSameTimingInBothDirections() {
        assertEquals(205, bookshelfGroupSettleDuration(1080, 540f, 5000f))
        assertEquals(144, bookshelfGroupSettleDuration(1080, -540f, -15000f))
        assertEquals(144, bookshelfGroupSettleDuration(1080, 540f, 15000f))
    }

    @Test
    fun slowReleaseAndShortReturnsHaveDistanceBasedCaps() {
        assertEquals(205, bookshelfGroupSettleDuration(1080, 540f, 1000f))
        assertEquals(113, pageSwipeSettleDuration(0.1f, 600))
        assertEquals(320, pageSwipeSettleDuration(1f, 600))
    }

    @Test
    fun evenFastReleasesGiveLongMovesEnoughAnimationTime() {
        assertEquals(135, pageSwipeSettleDuration(0.5f, 20))
        assertEquals(200, pageSwipeSettleDuration(1f, 20))
    }
}
