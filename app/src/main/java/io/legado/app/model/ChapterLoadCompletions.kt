package io.legado.app.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** Request-local waiters for the existing chapter loader; completed results are never retained. */
internal class ChapterLoadCompletions {
    data class Key(val bookUrl: String, val generation: Long, val chapterIndex: Int)

    private val waiters = mutableMapOf<Key, MutableSet<CompletableDeferred<Unit>>>()

    @Synchronized
    fun register(key: Key): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also {
        waiters.getOrPut(key) { mutableSetOf() }.add(it)
    }

    fun unregister(key: Key, waiter: CompletableDeferred<Unit>) {
        synchronized(this) {
            waiters[key]?.let {
                it.remove(waiter)
                if (it.isEmpty()) waiters.remove(key)
            }
        }
        waiter.cancel()
    }

    fun complete(key: Key, failure: Throwable? = null) {
        val completed = synchronized(this) { waiters.remove(key)?.toList().orEmpty() }
        // Resuming a reader must not happen while holding the registry lock.
        completed.forEach {
            if (failure == null) it.complete(Unit) else it.completeExceptionally(failure)
        }
    }

    fun cancelAll() {
        val canceled = synchronized(this) {
            waiters.values.flatMap { it.toList() }.also { waiters.clear() }
        }
        canceled.forEach { it.cancel(CancellationException("Reader changed")) }
    }
}
