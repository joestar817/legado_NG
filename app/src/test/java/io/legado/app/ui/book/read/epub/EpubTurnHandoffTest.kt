package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubTurnHandoffTest {
    @Test
    fun renderingFirstKeepsBusinessCommitBehindOriginalAnimation() {
        val gate = EpubTurnHandoff()
        val ticket = gate.begin()
        var commits = 0
        assertTrue(gate.deferReady { commits++ })
        assertEquals(0, commits)
        gate.finishAnimation(ticket)
        gate.finishAnimation(ticket)
        assertEquals(1, commits)
        assertFalse(gate.isCurrent(ticket))
    }

    @Test
    fun animationFirstWaitsForRenderedViewport() {
        val gate = EpubTurnHandoff()
        val ticket = gate.begin()
        var commits = 0
        gate.finishAnimation(ticket)
        assertEquals(0, commits)
        assertTrue(gate.deferReady { commits++ })
        assertEquals(1, commits)
    }

    @Test
    fun cancellationDropsStagedReadyAndOldAnimationCompletion() {
        val gate = EpubTurnHandoff()
        val old = gate.begin()
        var commits = 0
        gate.deferReady { commits++ }
        gate.cancel()
        val next = gate.begin()
        gate.deferReady { commits += 10 }
        gate.finishAnimation(old)
        assertEquals(0, commits)
        gate.finishAnimation(next)
        assertEquals(10, commits)
    }

    @Test
    fun publicationCanReenterWithoutClearingTheNextGate() {
        val gate = EpubTurnHandoff()
        val first = gate.begin()
        var second = -1L
        var commits = 0
        gate.deferReady {
            commits++
            second = gate.begin()
            gate.deferReady { commits++ }
        }
        gate.finishAnimation(first)
        assertEquals(1, commits)
        assertTrue(gate.isCurrent(second))
        gate.finishAnimation(second)
        assertEquals(2, commits)
    }

    @Test
    fun ordinaryReadyRemainsWithItsExistingOwner() {
        val gate = EpubTurnHandoff()
        assertFalse(gate.deferReady { fail("No overlapped turn owns this ready") })
    }

    @Test
    fun endedAnimationDoesNotResolveNewCoordinatesOnTheOldLivePage() {
        val gate = EpubTurnHandoff()
        val ticket = gate.begin()
        assertTrue(gate.waitingForMove)
        gate.finishAnimation(ticket)
        assertTrue(gate.waitingForMove)
        assertTrue(gate.startMove(ticket))
        assertFalse(gate.waitingForMove)
        gate.deferReady {}
        assertFalse(gate.isCurrent(ticket))
    }

    @Test
    fun staleMoveCannotReleaseTheNewTurnsPendingCoordinates() {
        val gate = EpubTurnHandoff()
        val old = gate.begin()
        val next = gate.begin()
        assertFalse(gate.startMove(old))
        assertTrue(gate.waitingForMove)
        assertTrue(gate.startMove(next))
        gate.cancel()
        assertFalse(gate.waitingForMove)
    }
}
