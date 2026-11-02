package io.legado.app.model

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChapterLoadCompletionsTest {
    private val key = ChapterLoadCompletions.Key("book", 1L, 8)

    @Test
    fun uncachedCompletionDoesNotRequireTheOriginalNavigationCallback() = runBlocking {
        val completions = ChapterLoadCompletions()
        val waiter = completions.register(key)
        assertFalse(waiter.isCompleted)
        completions.complete(key)
        waiter.await()
        assertTrue(waiter.isCompleted)
    }

    @Test
    fun downloadOrLayoutFailureIsDeliveredToEveryWaitingRequest() = runBlocking {
        val completions = ChapterLoadCompletions()
        val first = completions.register(key)
        val second = completions.register(key)
        val failure = IOException("download failed")
        completions.complete(key, failure)
        for (waiter in listOf(first, second)) {
            try {
                waiter.await()
                fail("failure must be delivered")
            } catch (actual: IOException) {
                assertSame(failure, actual)
            }
        }
    }

    @Test
    fun bookEpochAndChapterMustAllMatch() {
        val completions = ChapterLoadCompletions()
        val waiter = completions.register(key)
        completions.complete(key.copy(bookUrl = "another"))
        completions.complete(key.copy(generation = 2))
        completions.complete(key.copy(chapterIndex = 9))
        assertFalse(waiter.isCompleted)
        completions.unregister(key, waiter)
        assertTrue(waiter.isCancelled)
    }

    @Test
    fun canceledOldRequestCannotRemoveANewerSameChapterWaiter() = runBlocking {
        val completions = ChapterLoadCompletions()
        val old = completions.register(key)
        completions.cancelAll()
        val latest = completions.register(key)
        completions.unregister(key, old)
        assertTrue(old.isCancelled)
        assertFalse(latest.isCompleted)
        completions.complete(key)
        latest.await()
    }

    @Test
    fun unregisteringOneListenerDoesNotCancelOtherListeners() = runBlocking {
        val completions = ChapterLoadCompletions()
        val first = completions.register(key)
        val second = completions.register(key)
        completions.unregister(key, first)
        assertTrue(first.isCancelled)
        assertFalse(second.isCompleted)
        completions.complete(key)
        second.await()
    }

    @Test
    fun navigationCancellationReleasesAllChaptersAndBooks() {
        val completions = ChapterLoadCompletions()
        val waiters = listOf(key, key.copy(bookUrl = "other"), key.copy(chapterIndex = 9))
            .map(completions::register)
        completions.cancelAll()
        assertTrue(waiters.all { it.isCancelled })
    }

    @Test
    fun completedResultsAreNotReplayedIntoLaterRequests() {
        val completions = ChapterLoadCompletions()
        completions.complete(key)
        val first = completions.register(key)
        assertFalse(first.isCompleted)
        completions.complete(key)
        val next = completions.register(key)
        assertFalse(next.isCompleted)
        completions.unregister(key, next)
    }

    @Test
    fun completionResumesReadersOutsideTheRegistryLock() = runBlocking {
        val completions = ChapterLoadCompletions()
        val waiter = completions.register(key)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val resumed = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                waiter.await()
                val other = executor.submit<Boolean> {
                    val next = completions.register(key.copy(chapterIndex = 9))
                    completions.unregister(key.copy(chapterIndex = 9), next)
                    true
                }
                assertTrue(other.get(2, TimeUnit.SECONDS))
            }
            completions.complete(key)
            resumed.join()
        } finally {
            executor.shutdownNow()
        }
    }
}
