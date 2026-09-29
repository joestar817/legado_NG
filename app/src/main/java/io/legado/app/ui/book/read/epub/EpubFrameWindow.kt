package io.legado.app.ui.book.read.epub

/** A small owned window, never a chapter-wide bitmap cache. */
internal class EpubFrameWindow<T>(
    private val maxCount: Int = 4,
    private val maxBytes: Long = Long.MAX_VALUE,
    private val sizeOf: (T) -> Long = { 0L },
    private val dispose: (T) -> Unit,
) {
    private class Entry<T>(val value: T, val bytes: Long, var readers: Int = 0, var cached: Boolean = true)
    class Lease<T> internal constructor(val value: T, private val release: () -> Unit) : AutoCloseable {
        private var closed = false
        override fun close() { if (!closed) { closed = true; release() } }
    }
    private val values = LinkedHashMap<Int, Entry<T>>()
    var ownedBytes = 0L
        private set
    var ownedCount = 0
        private set
    operator fun get(page: Int): T? = values[page]?.value

    fun acquire(page: Int): Lease<T>? = values[page]?.let { entry ->
        entry.readers++
        Lease(entry.value) {
            entry.readers--
            if (!entry.cached && entry.readers == 0) disposeEntry(entry)
        }
    }

    fun put(page: Int, value: T) {
        if (values[page]?.value === value) return
        values.remove(page)?.let(::retire)
        val entry = Entry(value, sizeOf(value).coerceAtLeast(0L))
        if (entry.bytes > maxBytes || maxCount == 0) { dispose(value); return }
        values[page] = entry
        ownedBytes += entry.bytes
        ownedCount++
        // Borrowed frames count against the budget. If all old frames are in use,
        // decline the new cache entry instead of recycling a frame still being composed.
        val iterator = values.iterator()
        while ((ownedCount > maxCount || ownedBytes > maxBytes) && iterator.hasNext()) {
            val candidate = iterator.next().value
            if (candidate.readers != 0) continue
            iterator.remove()
            retire(candidate)
        }
    }
    fun retain(pages: Set<Int>) {
        val iterator = values.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.key !in pages) { iterator.remove(); retire(item.value) }
        }
    }
    fun clear() { values.values.forEach(::retire); values.clear() }
    private fun retire(entry: Entry<T>) {
        entry.cached = false
        if (entry.readers == 0) disposeEntry(entry)
    }
    private fun disposeEntry(entry: Entry<T>) {
        ownedBytes -= entry.bytes
        ownedCount--
        dispose(entry.value)
    }
}
