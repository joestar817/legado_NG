package io.legado.app.model.jsSource

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JsSourceCoroutineContextTest {
    @Test
    fun saturatedCallerPoolCanCompleteBlockingBridges() {
        val executor = Executors.newFixedThreadPool(9)
        val dispatcher = executor.asCoroutineDispatcher()
        val ready = CountDownLatch(9)
        val job = Job()
        val policy = CoroutineName("source-policy")
        try {
            val futures = (1..9).map {
                executor.submit<Int> {
                    ready.countDown()
                    check(ready.await(5, TimeUnit.SECONDS))
                    runBlocking((dispatcher + job + policy).withoutScriptDispatcher()) {
                        assertSame(policy, coroutineContext[CoroutineName])
                        delay(10)
                        1
                    }
                }
            }
            assertEquals(9, futures.sumOf { it.get(5, TimeUnit.SECONDS) })
            assertSame(job, (dispatcher + job).withoutScriptDispatcher()[Job])
        } finally {
            job.cancel()
            executor.shutdownNow()
            dispatcher.close()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun parentCancellationReleasesBlockingBridge() {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val entered = CountDownLatch(1)
        val job = Job()
        try {
            val future = executor.submit<Boolean> {
                try {
                    runBlocking((dispatcher + job).withoutScriptDispatcher()) {
                        entered.countDown()
                        delay(Long.MAX_VALUE)
                    }
                    false
                } catch (_: CancellationException) {
                    true
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
            assertTrue(future.get(5, TimeUnit.SECONDS))
        } finally {
            job.cancel()
            executor.shutdownNow()
            dispatcher.close()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
