package io.legado.app.ui.rss.article

import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RssArticlesLoadState(
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)

internal data class RssArticlesQuery(
    val source: RssSource,
    val sortName: String,
    val sortUrl: String,
    val searchKey: String?
) {
    fun sameLocation(other: RssArticlesQuery): Boolean =
        source.sourceUrl == other.source.sourceUrl && sortName == other.sortName &&
                sortUrl == other.sortUrl && searchKey == other.searchKey
}

internal data class RssArticlesRequest(
    val query: RssArticlesQuery,
    val url: String,
    val page: Int,
    val refreshing: Boolean,
    val order: Long
)

internal data class RssArticlesStoredPage(val addedCount: Int, val nextOrder: Long)

/** One main-thread owner for the request cursor, retry target and loading indicators. */
internal class RssArticlesPaging(
    private val scope: CoroutineScope,
    private val fetch: suspend (RssArticlesRequest) -> Pair<List<RssArticle>, String?>,
    private val store: suspend (RssArticlesRequest, List<RssArticle>) -> RssArticlesStoredPage,
    private val onStateChanged: (RssArticlesLoadState) -> Unit,
    private val errorMessage: (Throwable) -> String,
    private val now: () -> Long = System::currentTimeMillis
) {
    var state = RssArticlesLoadState()
        private set
    private var query: RssArticlesQuery? = null
    private var page = 0
    private var order = 0L
    private var nextPageUrl: String? = null
    private var failedRequest: RssArticlesRequest? = null
    private var requestJob: Job? = null
    private var requestId = 0L

    fun refresh(query: RssArticlesQuery) {
        // Never let edits to the caller's source or selection change an in-flight request.
        val snapshot = query.copy(source = query.source.copy())
        this.query = snapshot
        page = 0
        nextPageUrl = null
        failedRequest = null
        start(RssArticlesRequest(snapshot, snapshot.sortUrl, 1, true, now()))
    }

    fun loadMore(query: RssArticlesQuery) {
        val activeQuery = this.query
        if (activeQuery == null || !activeQuery.sameLocation(query)) {
            refresh(query)
            return
        }
        if (state.refreshing || state.loadingMore || state.error != null || !state.hasMore) return
        val url = nextPageUrl
        if (url.isNullOrBlank()) {
            updateState(RssArticlesLoadState())
            return
        }
        start(RssArticlesRequest(activeQuery, url, page + 1, false, order))
    }

    fun retry(query: RssArticlesQuery) {
        if (state.refreshing || state.loadingMore) return
        val request = failedRequest ?: return
        if (!request.query.sameLocation(query)) {
            refresh(query)
        } else {
            // The failed page, URL and order are retained until the whole request succeeds.
            start(request)
        }
    }

    private fun start(request: RssArticlesRequest) {
        val previousJob = requestJob
        previousJob?.cancel()
        val id = ++requestId
        updateState(
            RssArticlesLoadState(
                refreshing = request.refreshing,
                loadingMore = !request.refreshing,
                hasMore = !request.refreshing && state.hasMore
            )
        )
        requestJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // A cancelled blocking database write must finish before a new refresh can save.
                withContext(NonCancellable) { previousJob?.join() }
                currentCoroutineContext().ensureActive()
                val (articles, nextUrl) = fetch(request)
                currentCoroutineContext().ensureActive()
                val stored = store(request, articles)
                currentCoroutineContext().ensureActive()
                if (id != requestId) return@launch
                page = request.page
                order = stored.nextOrder
                nextPageUrl = nextUrl
                failedRequest = null
                updateState(
                    RssArticlesLoadState(
                        hasMore = articles.isNotEmpty() && stored.addedCount > 0 &&
                                !nextUrl.isNullOrBlank()
                    )
                )
            } catch (error: Exception) {
                // A child timeout is a retryable failure while this request is still active.
                // Only cancellation of the request itself must stay silent.
                currentCoroutineContext().ensureActive()
                if (id == requestId) {
                    failedRequest = request
                    updateState(
                        state.copy(refreshing = false, loadingMore = false, error = errorMessage(error))
                    )
                }
            } finally {
                if (id == requestId && (state.refreshing || state.loadingMore)) {
                    updateState(state.copy(refreshing = false, loadingMore = false))
                }
            }
        }
    }

    private fun updateState(value: RssArticlesLoadState) {
        state = value
        onStateChanged(value)
    }
}
