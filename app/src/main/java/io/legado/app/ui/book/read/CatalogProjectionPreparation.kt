package io.legado.app.ui.book.read

import io.legado.app.data.entities.CatalogOutlineEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CatalogProjectionState(val collapsedVolumes: Set<String>, val descending: Boolean)

internal data class CatalogProjectionRequest(
    val generation: Long,
    val state: CatalogProjectionState,
    val anchorIndex: Int,
    val anchorOffset: Int,
    val sort: Boolean,
)

/** Keeps rapid clicks relative to the pending intent, while publication stays latest-wins. */
internal class CatalogProjectionPreparation(initialState: CatalogProjectionState) {
    private var publishedState = initialState
    private var pending: CatalogProjectionRequest? = null
    private var generation = 0L
    private var job: Job? = null
    val desiredState: CatalogProjectionState get() = pending?.state ?: publishedState

    fun request(state: CatalogProjectionState, anchorIndex: Int, anchorOffset: Int, sort: Boolean): CatalogProjectionRequest {
        val request = CatalogProjectionRequest(++generation, state, anchorIndex, anchorOffset, sort)
        job?.cancel()
        job = null
        pending = request
        return request
    }

    fun attach(request: CatalogProjectionRequest, job: Job) {
        if (isCurrent(request)) this.job = job else job.cancel()
    }

    fun isCurrent(request: CatalogProjectionRequest): Boolean = request.generation == generation

    fun published(request: CatalogProjectionRequest): Boolean {
        if (!isCurrent(request)) return false
        publishedState = request.state
        pending = null
        return true
    }

    fun failed(request: CatalogProjectionRequest) {
        if (!isCurrent(request)) return
        generation++
        pending = null
        job?.cancel()
        job = null
    }

    fun cancel() {
        generation++
        job?.cancel()
        pending = null
    }
}

internal fun catalogProjectionWindow(
    rows: List<CatalogOutlineEntry>, firstPosition: Int, visibleCount: Int, preloadItems: Int = 16,
): List<CatalogOutlineEntry> {
    if (rows.isEmpty()) return emptyList()
    val first = firstPosition.coerceIn(rows.indices)
    return rows.subList((first - preloadItems).coerceAtLeast(0),
        (first + visibleCount.coerceAtLeast(1) + preloadItems).coerceAtMost(rows.size))
}

internal fun catalogProjectionRows(
    rows: List<CatalogOutlineEntry>, oldPosition: Int, oldAnchorIndex: Int,
    targetPosition: Int, visibleCount: Int, preloadItems: Int = 16,
): List<CatalogOutlineEntry> = listOf(oldPosition, catalogVisiblePosition(rows, oldAnchorIndex), targetPosition)
    .flatMap { catalogProjectionWindow(rows, it, visibleCount, preloadItems) }.distinctBy { it.url }

internal data class CatalogPreparedProjection<T>(
    val requested: List<CatalogOutlineEntry>, val loaded: Map<CatalogOutlineEntry, T>,
)

internal suspend fun <T> prepareCatalogProjection(
    cache: CatalogOutlineCache<T>, rows: List<CatalogOutlineEntry>, batchSize: Int = 64,
    load: suspend (List<CatalogOutlineEntry>) -> Map<CatalogOutlineEntry, T>,
): CatalogPreparedProjection<T> {
    val missing = cache.missing(rows)
    // Paging may evict a cache hit while a cold batch is in flight. Keep the entire
    // bounded target window locally so publication never depends on those hits surviving.
    val loaded = mutableMapOf<CatalogOutlineEntry, T>()
    rows.forEach { row -> cache[row]?.let { loaded[row] = it } }
    for (batch in missing.chunked(batchSize)) {
        loaded.putAll(load(batch))
        currentCoroutineContext().ensureActive()
    }
    return CatalogPreparedProjection(rows, loaded)
}
