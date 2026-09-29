package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubRequestEpochTest {
    @Test
    fun cancellingQueuedMarkerAlsoInvalidatesItsLatePixelDelivery() {
        val epoch = EpubRequestEpoch()
        val old = epoch.next()
        assertTrue(epoch.isCurrent(old))
        epoch.next() // Cancel, even if the GL thread already produced its bitmap.
        assertFalse(epoch.isCurrent(old))
        val current = epoch.next()
        assertFalse(epoch.isCurrent(old))
        assertTrue(epoch.isCurrent(current))
    }

    @Test
    fun cancellationIsVisibleToWorkOnAnotherThread() {
        val epoch = EpubRequestEpoch()
        val token = epoch.next()
        val started = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = Thread {
            started.countDown()
            resume.await()
            delivered.set(epoch.isCurrent(token))
        }
        worker.start()
        assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
        epoch.next()
        resume.countDown()
        worker.join(2000)
        assertFalse(worker.isAlive)
        assertFalse(delivered.get())
    }
}
