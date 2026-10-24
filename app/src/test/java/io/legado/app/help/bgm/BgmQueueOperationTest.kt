package io.legado.app.help.bgm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BgmQueueOperationTest {
    @Test
    fun cancellationBeforeCommitPreventsAllCommitSideEffects() {
        val operation = BgmQueueOperation()
        operation.cancel()
        var ran = false
        assertFalse(operation.commitIfActive { ran = true; true })
        assertFalse(ran)
        assertTrue(operation.isCancelled)
    }

    @Test
    fun completedCommitCannotBeCancelledOrAppliedTwice() {
        val operation = BgmQueueOperation()
        assertTrue(operation.commitIfActive { true })
        operation.cancel()
        assertFalse(operation.isCancelled)
        assertFalse(operation.commitIfActive { error("An operation is single use") })
    }

    @Test
    fun rejectedCommitCanStillBeCancelled() {
        val operation = BgmQueueOperation()
        assertFalse(operation.commitIfActive { false })
        operation.cancel()
        assertTrue(operation.isCancelled)
        assertFalse(operation.commitIfActive { error("A cancelled operation cannot commit") })
    }

    @Test
    fun throwingCommitDoesNotHoldLockOrMarkOperationCompleted() {
        val operation = BgmQueueOperation()
        val failure = runCatching { operation.commitIfActive { error("write failed") } }
        assertTrue(failure.isFailure)
        operation.cancel()
        assertTrue(operation.isCancelled)
    }

    @Test
    fun cancellationDuringCommitWaitsForAtomicCompletion() {
        val operation = BgmQueueOperation()
        val entered = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val cancelling = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val commit = executor.submit<Boolean> {
                operation.commitIfActive {
                    entered.countDown()
                    check(releaseCommit.await(5, TimeUnit.SECONDS))
                    true
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val cancel = executor.submit {
                cancelling.countDown()
                operation.cancel()
            }
            assertTrue(cancelling.await(5, TimeUnit.SECONDS))
            releaseCommit.countDown()
            assertTrue(commit.get(5, TimeUnit.SECONDS))
            cancel.get(5, TimeUnit.SECONDS)
            assertFalse(operation.isCancelled)
        } finally {
            releaseCommit.countDown()
            executor.shutdownNow()
        }
    }
}
