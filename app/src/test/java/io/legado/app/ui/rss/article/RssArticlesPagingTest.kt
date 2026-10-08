package io.legado.app.ui.rss.article

import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RssArticlesPagingTest {
    private val query = RssArticlesQuery(
        RssSource(sourceUrl = "https://source.test", ruleNextPage = "next"),
        "分类", "https://source.test/{{page}}", null
    )

    @Test
    fun threePagesFinishLoadingAndPermitPageTemplatesWithTheSameUrl() = runBlocking {
        val h = Harness(this)
        h.paging.refresh(query)
        assertTrue(h.paging.state.refreshing)
        for (page in 1..3) {
            val pending = h.next()
            assertEquals(page, pending.request.page)
            assertEquals(query.sortUrl, pending.request.url)
            h.complete(pending, listOf("page-$page"), if (page == 3) null else query.sortUrl)
            assertIdle(h)
            assertEquals(page < 3, h.paging.state.hasMore)
            h.paging.loadMore(query)
        }
        assertEquals(listOf("page-1", "page-2", "page-3"), h.saved.keys.toList())
        assertEquals(3, h.requests.size)
    }

    @Test
    fun emptyPagesAndAbsentOrBlankNextUrlTerminateWithoutLeavingASpinner() = runBlocking {
        for (next in listOf(null, "", "  ")) {
            val h = Harness(this)
            h.paging.refresh(query)
            h.complete(h.next(), listOf("one"), next)
            h.paging.loadMore(query)
            assertIdle(h)
            assertFalse(h.paging.state.hasMore)
            assertEquals(1, h.requests.size)
        }
        val h = Harness(this)
        h.paging.refresh(query)
        h.complete(h.next(), emptyList(), "next-page")
        assertIdle(h)
        assertFalse(h.paging.state.hasMore)
    }

    @Test
    fun whollyRepeatedPageStopsButMiddleNewArticleBetweenRepeatedEndsIsAppended() = runBlocking {
        val h = Harness(this)
        h.paging.refresh(query)
        h.complete(h.next(), listOf("first", "last"), "page-2")
        h.paging.loadMore(query)
        h.complete(h.next(), listOf("first", "middle", "last"), "page-3")
        assertIdle(h)
        assertTrue(h.paging.state.hasMore)
        assertEquals(listOf("first", "last", "middle"), h.saved.keys.toList())
        h.paging.loadMore(query)
        h.complete(h.next(), listOf("first", "middle", "last"), "page-4")
        assertIdle(h)
        assertFalse(h.paging.state.hasMore)
        h.paging.loadMore(query)
        assertEquals(3, h.requests.size)
    }

    @Test
    fun failedAppendKeepsItsPageUrlAndOrderUntilExplicitRetrySucceeds() = runBlocking {
        val h = Harness(this)
        h.paging.refresh(query)
        h.complete(h.next(), listOf("first"), "page-2")
        h.paging.loadMore(query)
        val failed = h.next()
        failed.response.completeExceptionally(IOException("offline"))
        h.awaitIdle()
        assertIdle(h)
        assertEquals("offline", h.paging.state.error)
        h.paging.loadMore(query)
        assertEquals(2, h.requests.size)
        h.paging.retry(query)
        assertNull(h.paging.state.error)
        val retry = h.next()
        assertEquals(failed.request, retry.request)
        h.complete(retry, listOf("second"), "page-3")
        h.paging.loadMore(query)
        val next = h.next()
        assertEquals(3, next.request.page)
        assertEquals(retry.request.order - 1, next.request.order)
        h.complete(next, listOf("third"), null)
    }

    @Test
    fun failedFirstPageCanRetryWhenHasMoreIsFalse() = runBlocking {
        val h = Harness(this)
        h.paging.refresh(query)
        val failed = h.next()
        failed.response.completeExceptionally(IOException("first page failed"))
        h.awaitIdle()
        assertFalse(h.paging.state.hasMore)
        h.paging.retry(query)
        val retry = h.next()
        assertEquals(failed.request, retry.request)
        assertTrue(h.paging.state.refreshing)
        h.complete(retry, listOf("restored"), null)
        assertIdle(h)
        assertNull(h.paging.state.error)
    }

    @Test
    fun duplicateLoadMoreAndRetryTriggersCannotStartConcurrentRequests() = runBlocking {
        val h = Harness(this)
        h.paging.refresh(query)
        h.paging.loadMore(query)
        h.paging.retry(query)
        h.complete(h.next(), listOf("first"), "page-2")
        repeat(5) { h.paging.loadMore(query) }
        h.paging.retry(query)
        h.complete(h.next(), listOf("second"), null)
        assertEquals(2, h.requests.size)
        assertIdle(h)
    }

    @Test
    fun refreshCancelsOldSearchAndCannotPublishItsLateResultsOrError() = runBlocking {
        val h = Harness(this)
        val oldQuery = query.copy(searchKey = "old")
        h.paging.refresh(oldQuery)
        val old = h.next()
        val newQuery = query.copy(searchKey = "new")
        h.paging.refresh(newQuery)
        old.response.complete(listOf(RssArticle(link = "obsolete")) to "obsolete-next")
        val fresh = h.next()
        assertEquals("new", fresh.request.query.searchKey)
        h.complete(fresh, listOf("current"), null)
        assertEquals(listOf("current"), h.saved.keys.toList())
        assertNull(h.paging.state.error)
        assertIdle(h)
    }

    @Test
    fun requestCapturesSourceRulesAndSelectionBeforeCallerMutatesThem() = runBlocking {
        val h = Harness(this)
        val source = query.source.copy(ruleArticles = "old-rule")
        h.paging.refresh(query.copy(source = source, searchKey = "original"))
        source.ruleArticles = "changed-rule"
        val pending = h.next()
        assertEquals("old-rule", pending.request.query.source.ruleArticles)
        assertEquals("original", pending.request.query.searchKey)
        h.complete(pending, listOf("one"), null)
    }

    @Test
    fun severalRapidRefreshesWaitForCancelledStorageBeforeWritingTheNewestSearch() = runBlocking {
        val h = Harness(this)
        val releaseStore = CompletableDeferred<Unit>()
        val enteredStore = CompletableDeferred<Unit>()
        h.beforeStore = {
            if (it.query.searchKey == "old") {
                enteredStore.complete(Unit)
                withContext(NonCancellable) { releaseStore.await() }
            }
        }
        h.paging.refresh(query.copy(searchKey = "old"))
        h.next().response.complete(listOf(RssArticle(link = "old")) to null)
        withTimeout(2_000) { enteredStore.await() }
        h.paging.refresh(query.copy(searchKey = "intermediate"))
        h.paging.refresh(query.copy(searchKey = "new"))
        yield()
        assertEquals(1, h.requests.size)
        assertTrue(h.paging.state.refreshing)
        releaseStore.complete(Unit)
        val fresh = h.next()
        assertEquals("new", fresh.request.query.searchKey)
        h.complete(fresh, listOf("new"), null)
        assertEquals(listOf("old", "new"), h.storeOrder)
        assertEquals(listOf("new"), h.saved.keys.toList())
        assertIdle(h)
    }

    @Test
    fun storageFailureDoesNotAdvanceThePageCursor() = runBlocking {
        val h = Harness(this)
        h.paging.refresh(query)
        h.complete(h.next(), listOf("first"), "page-2")
        h.beforeStore = { throw IOException("database unavailable") }
        h.paging.loadMore(query)
        val failed = h.next()
        h.complete(failed, listOf("second"), "page-3")
        assertEquals("database unavailable", h.paging.state.error)
        h.beforeStore = {}
        h.paging.retry(query)
        val retry = h.next()
        assertEquals(failed.request, retry.request)
        h.complete(retry, listOf("second"), null)
        assertEquals(listOf("first", "second"), h.saved.keys.toList())
    }

    @Test
    fun childTimeoutIsReportedAndRetryableWithoutAutomaticallyRepeatingTheRequest() = runBlocking {
        val h = Harness(this)
        h.beforeFetch = { withTimeout(10) { awaitCancellation() } }
        h.paging.refresh(query)
        h.awaitIdle()
        assertIdle(h)
        assertTrue(h.paging.state.error?.contains("Timed out") == true)
        h.paging.loadMore(query)
        assertEquals(1, h.requests.size)
        h.beforeFetch = {}
        h.paging.retry(query)
        val retry = h.next()
        assertEquals(1, retry.request.page)
        h.complete(retry, listOf("retried"), null)
        assertNull(h.paging.state.error)
    }

    private fun assertIdle(h: Harness) {
        assertFalse(h.paging.state.refreshing)
        assertFalse(h.paging.state.loadingMore)
    }

    private class Pending(val request: RssArticlesRequest) {
        val response = CompletableDeferred<Pair<List<RssArticle>, String?>>()
    }

    private class Harness(scope: CoroutineScope) {
        val requests = mutableListOf<RssArticlesRequest>()
        val saved = linkedMapOf<String, RssArticle>()
        val storeOrder = mutableListOf<String?>()
        var beforeFetch: suspend (RssArticlesRequest) -> Unit = {}
        var beforeStore: suspend (RssArticlesRequest) -> Unit = {}
        private val pending = Channel<Pending>(Channel.UNLIMITED)
        val paging = RssArticlesPaging(
            scope = scope,
            fetch = { request ->
                requests.add(request)
                beforeFetch(request)
                val call = Pending(request)
                pending.send(call)
                call.response.await()
            },
            store = { request, articles ->
                beforeStore(request)
                storeOrder.add(request.query.searchKey)
                if (request.refreshing) saved.clear()
                var count = 0
                var order = request.order
                for (article in articles.distinctBy { it.link }) {
                    if (!saved.containsKey(article.link)) {
                        saved[article.link] = article.copy(order = order)
                        count++
                    }
                    order--
                }
                RssArticlesStoredPage(count, order)
            },
            onStateChanged = {},
            errorMessage = { it.message.orEmpty() },
            now = { 1_000L }
        )

        suspend fun next(): Pending = withTimeout(2_000) { pending.receive() }

        suspend fun complete(call: Pending, links: List<String>, next: String?) {
            call.response.complete(links.map { RssArticle(link = it) } to next)
            awaitIdle()
        }

        suspend fun awaitIdle() = withTimeout(2_000) {
            while (paging.state.refreshing || paging.state.loadingMore) yield()
        }
    }
}
