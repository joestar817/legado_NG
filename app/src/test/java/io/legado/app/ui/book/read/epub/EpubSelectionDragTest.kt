package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubSelectionDragTest {
    @Test
    fun stationaryLongPressKeepsTheInitialWordWithoutAFinalNearestHit() {
        val drag = EpubSelectionDrag(12f, 24f)
        drag.finish()
        assertFalse(drag.pressed)
        assertFalse(drag.finalHit)
        assertFalse(drag.canAdvance(10_000))
    }

    @Test
    fun movedPointerNeedsAFinalHitAndCannotAdvanceAfterUp() {
        val drag = EpubSelectionDrag(12f, 24f)
        drag.update(30f, 100f)
        drag.updateEdge(1, 0)
        assertTrue(drag.canAdvance(350))
        drag.finish()
        assertTrue(drag.finalHit)
        assertFalse(drag.canAdvance(1000))
    }

    @Test
    fun edgeRequiresDwellAndAnUnchangedDirection() {
        val drag = EpubSelectionDrag(0f, 0f)
        drag.updateEdge(1, 100)
        assertFalse(drag.canAdvance(449))
        assertTrue(drag.canAdvance(450))
        drag.updateEdge(-1, 500)
        assertFalse(drag.canAdvance(849))
        assertTrue(drag.canAdvance(850))
    }

    @Test
    fun leavingThenReturningToEdgeRestartsDwell() {
        val drag = EpubSelectionDrag(0f, 0f)
        drag.updateEdge(1, 100)
        drag.updateEdge(0, 500)
        assertFalse(drag.canAdvance(1000))
        drag.updateEdge(1, 1000)
        assertFalse(drag.canAdvance(1349))
        assertTrue(drag.canAdvance(1350))
    }

    @Test
    fun pendingHitAndViewportEachBlockASecondAdvance() {
        val drag = EpubSelectionDrag(0f, 0f)
        drag.updateEdge(1, 0)
        drag.inFlight = true
        assertFalse(drag.canAdvance(500))
        drag.inFlight = false
        drag.awaitingViewport = true
        assertFalse(drag.canAdvance(500))
        drag.awaitingViewport = false
        assertTrue(drag.canAdvance(500))
    }

    @Test
    fun scrollingRepeatsAfterItsShorterDelayWhilePagesKeepTheirDwell() {
        val drag = EpubSelectionDrag(0f, 0f)
        drag.updateEdge(1, 0)
        drag.didAdvance(400, repeatDelay = 80)
        assertFalse(drag.canAdvance(479))
        assertTrue(drag.canAdvance(480))
        drag.didAdvance(500)
        assertFalse(drag.canAdvance(849))
        assertTrue(drag.canAdvance(850))
    }

    @Test
    fun latestPointerIsCoalescedAndUpIgnoresLaterMoves() {
        val drag = EpubSelectionDrag(0f, 0f)
        drag.inFlight = true
        drag.update(1f, 2f)
        drag.update(3f, 4f)
        assertEquals(2L, drag.pointerRevision)
        assertEquals(3f, drag.x, 0f)
        assertEquals(4f, drag.y, 0f)
        drag.finish()
        drag.update(99f, 99f)
        assertEquals(2L, drag.pointerRevision)
        assertEquals(3f, drag.x, 0f)
    }
}
