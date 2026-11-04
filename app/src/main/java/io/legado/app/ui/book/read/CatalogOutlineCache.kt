package io.legado.app.ui.book.read

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import io.legado.app.data.entities.CatalogOutlineEntry

/** A bounded entity cache independent of the catalog's folded/sorted projection. */
internal class CatalogOutlineCache<T>(private val capacity: Int) {
    private data class Entry<T>(val row: CatalogOutlineEntry, val value: T?)

    private val entries = mutableStateMapOf<String, Entry<T>>()
    private val recency = linkedSetOf<String>()
    private var projectionProtection = emptyList<CatalogOutlineEntry>()
    var epoch by mutableIntStateOf(0)
        private set
    val hasTransition: Boolean get() = projectionProtection.isNotEmpty()

    fun beginProjection(protectedRows: List<CatalogOutlineEntry>): Int {
        projectionProtection = protectedRows
        return ++epoch
    }

    fun endProjection(expectedEpoch: Int, protectedRows: List<CatalogOutlineEntry>): Boolean {
        if (epoch != expectedEpoch) return false
        projectionProtection = emptyList()
        return retain(protectedRows, expectedEpoch)
    }

    init {
        require(capacity > 0)
    }

    val size: Int get() = entries.size

    // Reading during composition must not mutate the LRU or any snapshot state.
    operator fun get(row: CatalogOutlineEntry): T? =
        entries[row.url]?.takeIf { it.row == row }?.value

    fun missing(rows: List<CatalogOutlineEntry>): List<CatalogOutlineEntry> =
        rows.filter { entries[it.url]?.row != it }

    fun store(
        requested: List<CatalogOutlineEntry>,
        loaded: Map<CatalogOutlineEntry, T>,
        protectedRows: List<CatalogOutlineEntry>,
        expectedEpoch: Int = epoch,
    ): Boolean {
        if (epoch != expectedEpoch) return false
        requested.forEach { row ->
            // Remember missing database rows too, without shifting another chapter into their place.
            entries[row.url] = Entry(row, loaded[row])
            touch(row.url)
        }
        return retain(protectedRows, expectedEpoch)
    }

    fun retain(protectedRows: List<CatalogOutlineEntry>, expectedEpoch: Int = epoch): Boolean {
        if (epoch != expectedEpoch) return false
        val protected = protectedRows + projectionProtection
        val protectedUrls = protected.mapTo(hashSetOf()) { it.url }
        protected.forEach { row ->
            if (entries[row.url]?.row == row) touch(row.url)
        }
        val limit = maxOf(capacity, protectedUrls.size)
        val iterator = recency.iterator()
        while (entries.size > limit && iterator.hasNext()) {
            val url = iterator.next()
            if (url !in protectedUrls) {
                iterator.remove()
                entries.remove(url)
            }
        }
        return true
    }

    private fun touch(url: String) {
        recency.remove(url)
        recency.add(url)
    }
}
