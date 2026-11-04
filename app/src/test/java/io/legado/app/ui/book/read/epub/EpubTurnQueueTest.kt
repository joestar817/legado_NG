package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubTurnQueueTest {
    private class Turn(val direction: Int)

    @Test
    fun blockedHeadDoesNotRotateBehindReverseTurn() {
        val queue = EpubTurnQueue<Turn>()
        val next = Turn(1)
        val previous = Turn(-1)
        queue.add(next); queue.add(previous)
        repeat(3) { queue.startNext { assertSame(next, it); false } }
        queue.startNext { assertSame(next, it); true }
        queue.startNext { assertSame(previous, it); true }
        assertFalse(queue.isNotEmpty())
    }

    @Test
    fun fourWaitingClicksStillProduceFourTurns() {
        val queue = EpubTurnQueue<Turn>()
        val requests = List(4) { Turn(1) }
        requests.forEach { queue.add(it); queue.startNext { false } }
        val accepted = arrayListOf<Turn>()
        repeat(4) { queue.startNext { accepted.add(it); true } }
        assertEquals(requests, accepted)
        assertFalse(queue.isNotEmpty())
    }

    @Test
    fun synchronousChapterReplacementDoesNotRemoveNewRequest() {
        val queue = EpubTurnQueue<Turn>()
        val old = Turn(1)
        val replacement = Turn(-1)
        queue.add(old)
        queue.startNext {
            assertSame(old, it)
            queue.clear()
            queue.add(replacement)
            true
        }
        queue.startNext { assertSame(replacement, it); true }
        assertFalse(queue.isNotEmpty())
    }

    @Test
    fun reentrantReadyDoesNotStartHeadTwice() {
        val queue = EpubTurnQueue<Turn>()
        queue.add(Turn(1))
        var starts = 0
        queue.startNext {
            starts++
            queue.startNext { starts++; true }
            true
        }
        assertEquals(1, starts)
        assertFalse(queue.isNotEmpty())
    }

    @Test
    fun delayedHitKeepsItsInputOrderBeforeLaterKnownTurn() {
        val queue = EpubTurnQueue<Int>(naturalOrder())
        queue.add(3)
        queue.add(1)
        queue.add(2)
        val started = arrayListOf<Int>()
        repeat(3) { queue.startNext { started.add(it); true } }
        assertEquals(listOf(1, 2, 3), started)
    }

    @Test
    fun activeHeadIsNotMistakenForQueuedInterruption() {
        val queue = EpubTurnQueue<Turn>()
        val active = Turn(1)
        queue.add(active)
        queue.startNext {
            assertFalse(queue.hasOtherThan(active))
            queue.add(Turn(-1))
            assertTrue(queue.hasOtherThan(active))
            true
        }
        assertEquals(-1, queue.peek()?.direction)
    }

    @Test
    fun earlierReentrantInputDoesNotReplayAcceptedHead() {
        val queue = EpubTurnQueue<Int>(naturalOrder())
        queue.add(2)
        queue.startNext { queue.add(1); true }
        queue.startNext { assertEquals(1, it); true }
        assertFalse(queue.isNotEmpty())
    }
}
