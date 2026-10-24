package io.legado.app.help.bgm

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MusicHttpCleanupTest {
    @Test
    fun closingInvalidatesImmediatelyWithoutClosingSocketsOnTheCaller() {
        val executor = QueuedExecutor()
        val events = mutableListOf<String>()
        val cleanup = MusicHttpCleanup(
            { events += "cookies" }, { events += "cancel" }, { events += "evict" },
            { events += "shutdown" }, executor,
        )

        assertFalse(cleanup.isClosed)
        cleanup.close()
        assertTrue(cleanup.isClosed)
        assertEquals(listOf("cookies"), events)

        executor.runPending()
        assertEquals(listOf("cookies", "cancel", "evict", "shutdown"), events)
    }

    @Test
    fun productionExecutorClosesSocketsOffTheCallerThread() {
        val caller = Thread.currentThread().id
        val worker = AtomicLong(caller)
        val finished = CountDownLatch(1)
        val cleanup = MusicHttpCleanup(
            clearCookies = {},
            cancelCalls = { worker.set(Thread.currentThread().id) },
            evictConnections = {},
            shutdownExecutor = { finished.countDown() },
        )

        cleanup.close()

        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertNotEquals(caller, worker.get())
    }

    @Test
    fun repeatedCloseQueuesOnlyOneTeardown() {
        val executor = QueuedExecutor()
        val events = mutableListOf<String>()
        val cleanup = MusicHttpCleanup(
            { events += "cookies" }, { events += "cancel" }, { events += "evict" },
            { events += "shutdown" }, executor,
        )

        repeat(10) { cleanup.close() }
        assertEquals(1, executor.size)
        executor.runPending()
        cleanup.close()
        assertEquals(0, executor.size)
        assertEquals(listOf("cookies", "cancel", "evict", "shutdown"), events)
    }

    @Test
    fun aFailedSocketCloseStillReleasesTheOtherResources() {
        val executor = QueuedExecutor()
        val events = mutableListOf<String>()
        val cleanup = MusicHttpCleanup(
            clearCookies = { events += "cookies" },
            cancelCalls = { events += "cancel"; throw IOException("cancel failed") },
            evictConnections = { events += "evict"; throw IOException("close failed") },
            shutdownExecutor = { events += "shutdown" },
            executor = executor,
        )

        cleanup.close()
        executor.runPending()

        assertEquals(listOf("cookies", "cancel", "evict", "shutdown"), events)
    }

    @Test
    fun cleanupFromFinallySurvivesTheCancelledLoginJob() = runBlocking {
        val executor = QueuedExecutor()
        val events = mutableListOf<String>()
        val cleanup = MusicHttpCleanup(
            { events += "cookies" }, { events += "cancel" }, { events += "evict" },
            { events += "shutdown" }, executor,
        )
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                cleanup.close()
            }
        }

        job.cancelAndJoin()
        assertTrue(cleanup.isClosed)
        assertEquals(listOf("cookies"), events)
        executor.runPending()
        assertEquals(listOf("cookies", "cancel", "evict", "shutdown"), events)
    }

    private class QueuedExecutor : Executor {
        private val pending = ConcurrentLinkedQueue<Runnable>()
        val size: Int get() = pending.size

        override fun execute(command: Runnable) {
            pending.add(command)
        }

        fun runPending() {
            while (true) (pending.poll() ?: return).run()
        }
    }
}
