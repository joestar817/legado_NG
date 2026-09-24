package io.legado.app.model.localBook

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalBookCoverUpdatesTest {
    @Test fun updatesOnlyTheCompletedBook() {
        val store = LocalBookCoverRevisionStore()
        store.notifyChanged("A")
        assertNotEquals(0, store.revisionOf("A"))
        assertEquals(0, store.revisionOf("B"))
        val first = store.revisionOf("A")
        store.notifyChanged("A")
        assertNotEquals(first, store.revisionOf("A"))
    }

    @Test fun returningCollectorsReceiveEveryBooksLatestRevision() = runBlocking {
        val store = LocalBookCoverRevisionStore()
        store.notifyChanged("A")
        val firstA = store.observe("A").first()
        // 无订阅者期间连续更新，不能只保留最后一本的事件。
        store.notifyChanged("A")
        store.notifyChanged("C")
        assertNotEquals(firstA, store.observe("A").first())
        assertEquals(store.revisionOf("A"), store.observe("A").first())
        assertEquals(store.revisionOf("C"), store.observe("C").first())
        assertEquals(0, store.observe("D").first())
    }
}
