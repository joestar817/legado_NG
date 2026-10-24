package io.legado.app.help.bgm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** At most four read-only preparations ahead; the caller alone consumes and commits in selection order. */
internal suspend fun <T, R> prepareLocalAudioInOrder(
    items: List<T>,
    prepare: suspend (T) -> R,
    consume: suspend (T, Result<R>) -> Unit,
) = coroutineScope {
    val pending = ArrayDeque<Deferred<Result<R>>>()
    var next = 0
    fun enqueue() {
        if (next >= items.size) return
        val item = items[next++]
        pending.addLast(async(Dispatchers.IO) {
            try { Result.success(prepare(item)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { Result.failure(error) }
        })
    }
    repeat(minOf(4, items.size)) { enqueue() }
    try {
        for (item in items) {
            consume(item, pending.removeFirst().await())
            enqueue()
        }
    } finally { pending.forEach { it.cancel() } }
}
