package io.legado.app.ui.book.read.epub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** Main-thread owner of one derived chapter. Awaiting does not transfer cancellation ownership. */
internal class EpubPreparationSlot<K, V>(private val scope: CoroutineScope, private val matches: (K, K) -> Boolean) {
    private var entry: Pair<K, Deferred<V>>? = null

    fun find(key: K): Deferred<V>? = entry?.takeIf { matches(it.first, key) }?.second

    fun prepare(key: K, build: suspend () -> V): Deferred<V> {
        find(key)?.let { return it }
        clear()
        return scope.async(Dispatchers.Default) { build() }.also { entry = key to it }
    }

    fun clear() { entry?.second?.cancel(); entry = null }
}
