package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfGestureConfigTest {
    @Test fun existingModeAlwaysLeavesPagingToTheMainPager() {
        for (direction in listOf(-1, 1)) {
            assertEquals(0, resolveBookshelfSwipe(BookshelfSwipeMode.MAIN_PAGES, direction, true))
            assertEquals(0, resolveBookshelfSwipe(BookshelfSwipeMode.MAIN_PAGES, direction, false))
        }
    }

    @Test fun groupModeConsumesOnlyDirectionsWithANeighbor() {
        assertEquals(-1, resolveBookshelfSwipe(BookshelfSwipeMode.GROUPS_FIRST, -1, true))
        assertEquals(1, resolveBookshelfSwipe(BookshelfSwipeMode.GROUPS_FIRST, 1, true))
        assertEquals(0, resolveBookshelfSwipe(BookshelfSwipeMode.GROUPS_FIRST, -1, false))
        assertEquals(0, resolveBookshelfSwipe(BookshelfSwipeMode.GROUPS_FIRST, 1, false))
    }

    @Test fun disabledPagingStillAllowsTheIndependentAiGesture() {
        for (direction in listOf(-1, 1)) {
            assertEquals(2, resolveBookshelfSwipe(BookshelfSwipeMode.DISABLED, direction, true))
            assertEquals(2, resolveBookshelfSwipe(BookshelfSwipeMode.DISABLED, direction, false))
        }
        assertTrue(allowsBookshelfAiSwipe(BookshelfSwipeMode.DISABLED, true))
        assertTrue(allowsBookshelfAiSwipe(BookshelfSwipeMode.MAIN_PAGES, true))
    }

    @Test fun groupModePreventsGoingBackAndOpeningAiTogether() {
        assertFalse(allowsBookshelfAiSwipe(BookshelfSwipeMode.GROUPS_FIRST, true))
        assertTrue(allowsBookshelfAiSwipe(BookshelfSwipeMode.GROUPS_FIRST, false))
    }
}
