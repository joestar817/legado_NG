package io.legado.app.ui.book.read.epub

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EpubPreparationSlotTest {
    @Test fun matchingForegroundAwaitsOneSpeculativeBuild() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val slot = EpubPreparationSlot<Any, Int>(owner) { a, b -> a === b }
            val key = Any()
            val ready = CompletableDeferred<Unit>()
            var builds = 0
            val prefetched = slot.prepare(key) { ready.await(); builds++; 42 }
            val foreground = slot.prepare(key) { error("Duplicate preparation") }
            assertSame(prefetched, foreground)
            val waiter = launch { foreground.await() }
            waiter.cancelAndJoin()
            assertFalse(prefetched.isCancelled)
            ready.complete(Unit)
            assertEquals(42, foreground.await())
            assertEquals(1, builds)
        } finally { owner.cancel() }
    }
    @Test fun replacingIdentityCancelsOldPreparationAndKeepsOnlyNewResult() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val slot = EpubPreparationSlot<Any, Int>(owner) { a, b -> a === b }
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val oldKey = Any()
            val old = slot.prepare(oldKey) {
                try { started.complete(Unit); awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            started.await()
            val newKey = Any()
            assertEquals(2, slot.prepare(newKey) { 2 }.await())
            cancelled.await()
            assertTrue(old.isCancelled)
            assertNull(slot.find(oldKey))
            slot.clear()
            assertNull(slot.find(newKey))
        } finally { owner.cancel() }
    }
    @Test fun staleMappingWriteCannotReplaceNewReaderCache() {
        val old = EpubMappingPersistence.next()
        val current = EpubMappingPersistence.next()
        val published = arrayListOf<String>()
        EpubMappingPersistence.publish(current) { published.add("new") }
        EpubMappingPersistence.publish(old) { published.add("old") }
        assertEquals(listOf("new"), published)
    }
}
