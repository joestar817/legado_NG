package io.legado.app.ui.book.read.epub

/** Main-thread FIFO. A blocked head stays at the head, including during reentrant callbacks. */
internal class EpubTurnQueue<T : Any> {
    private val pending = java.util.ArrayDeque<T>()
    private var draining = false
    fun add(value: T) { pending.addLast(value) }
    fun isNotEmpty(): Boolean = pending.isNotEmpty()
    fun clear() { pending.clear() }
    fun startNext(start: (T) -> Boolean) {
        if (draining) return
        val next = pending.peekFirst() ?: return
        draining = true
        try {
            // Starting can synchronously clear or replace the queue during a chapter change.
            if (start(next) && pending.peekFirst() === next) pending.removeFirst()
        } finally {
            draining = false
        }
    }
}
