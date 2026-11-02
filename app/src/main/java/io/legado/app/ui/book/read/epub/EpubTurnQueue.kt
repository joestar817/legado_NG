package io.legado.app.ui.book.read.epub

/** Main-thread FIFO. A blocked head stays at the head, including during reentrant callbacks. */
internal class EpubTurnQueue<T : Any>(private val order: Comparator<T>? = null) {
    private val pending = java.util.ArrayDeque<T>()
    private var draining = false
    fun add(value: T) {
        val compare = order
        if (compare != null && pending.peekLast()?.let { compare.compare(it, value) > 0 } == true) {
            // A hit-test can complete after a later keyboard/swipe intent was received.
            val sorted = pending.toMutableList()
            val index = sorted.indexOfFirst { compare.compare(it, value) > 0 }
            sorted.add(index, value)
            pending.clear()
            pending.addAll(sorted)
        } else pending.addLast(value)
    }
    fun peek(): T? = pending.peekFirst()
    fun hasOtherThan(value: T?): Boolean = pending.any { it !== value }
    fun isNotEmpty(): Boolean = pending.isNotEmpty()
    fun clear() { pending.clear() }
    fun startNext(start: (T) -> Boolean) {
        if (draining) return
        val next = pending.peekFirst() ?: return
        draining = true
        try {
            // Starting can synchronously clear or replace the queue during a chapter change.
            if (start(next)) {
                val iterator = pending.iterator()
                while (iterator.hasNext()) if (iterator.next() === next) { iterator.remove(); break }
            }
        } finally {
            draining = false
        }
    }
}
