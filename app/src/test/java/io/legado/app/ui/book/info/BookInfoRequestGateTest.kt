package io.legado.app.ui.book.info

import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoRequestGateTest {
    private class Request(val bookUrl: String)

    @Test
    fun resultQueuedBeforeNavigationIsDiscardedWhenActuallyPublished() {
        val pending = mutableListOf<() -> Unit>()
        val first = Request("book-a")
        val gate = BookInfoRequestGate(first) { pending += it }
        val updates = mutableListOf<String>()

        gate.publish(first) { updates += "old book and chapters" }
        val second = Request("book-b")
        gate.begin(second)
        gate.publish(second) { updates += "new book and chapters" }

        pending.forEach { it() }
        assertEquals(listOf("new book and chapters"), updates)
    }

    @Test
    fun lateResultsFromAnEarlierVisitDoNotReplaceTheSameBookAfterReturning() {
        val pending = mutableListOf<() -> Unit>()
        val firstVisit = Request("book-a")
        val gate = BookInfoRequestGate(firstVisit) { pending += it }
        val updates = mutableListOf<String>()

        gate.begin(Request("book-b"))
        val returnVisit = Request("book-a")
        gate.begin(returnVisit)
        gate.publish(firstVisit) { updates += "stale details" }
        gate.publish(returnVisit) { updates += "current details" }

        pending.reversed().forEach { it() }
        assertEquals(listOf("current details"), updates)
    }

    @Test
    fun activeRequestCanPublishMultipleUpdatesInOrder() {
        val pending = mutableListOf<() -> Unit>()
        val request = Request("book-a")
        val gate = BookInfoRequestGate(request) { pending += it }
        val updates = mutableListOf<String>()

        gate.publish(request) { updates += "details" }
        gate.publish(request) { updates += "chapters" }
        pending.forEach { it() }

        assertEquals(listOf("details", "chapters"), updates)
    }
}
