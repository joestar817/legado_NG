package io.legado.app.ui.book.read.epub

/** A small owned window, never a chapter-wide bitmap cache. */
internal class EpubFrameWindow<T>(private val dispose: (T) -> Unit) {
    private val values = LinkedHashMap<Int, T>()
    operator fun get(page: Int): T? = values[page]
    fun put(page: Int, value: T) { values.put(page, value)?.let(dispose) }
    fun retain(pages: Set<Int>) {
        val iterator = values.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.key !in pages) { iterator.remove(); dispose(item.value) }
        }
    }
    fun clear() { values.values.forEach(dispose); values.clear() }
}
