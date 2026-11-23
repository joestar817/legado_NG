package io.legado.app.web.mcp

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import io.legado.app.constant.BookSourceType
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSearchMedia
import io.legado.app.help.ai.AiBookSourceHit
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.help.ai.AiConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class McpPreciseBookSearchTest {
    private val book = AiBookSearchCandidate("问道红尘", "姬叉", AiBookSearchMedia.NOVEL)
    private val requests = ConcurrentLinkedQueue<String>()
    private lateinit var database: AppDatabase
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val workers = Executors.newFixedThreadPool(4)
    private val finishStalledRequest = CountDownLatch(1)

    @Before
    fun startFixture() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.injectAsAppCtx()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val delegate = Class.forName("io.legado.app.data.AppDatabaseKt")
            .getDeclaredField("appDb\$delegate").apply { isAccessible = true }.get(null)
        delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(delegate, database)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        base = "http://127.0.0.1:${server.address.port}"
        server.executor = workers
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            requests.add(path)
            exchange.use {
                val json = when (path) {
                    "/empty" -> """{"rows":[]}"""
                    "/namesake" -> resultJson(author = "见秋")
                    "/other-title" -> resultJson(title = "另一本书", author = book.title)
                    "/missing-author" -> resultJson(author = "")
                    "/missing-detail" -> resultJson(link = "")
                    "/error" -> return@use
                    "/stall" -> {
                        finishStalledRequest.await(35, TimeUnit.SECONDS)
                        return@use
                    }
                    else -> resultJson()
                }
                val body = json.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.write(body)
            }
        }
        server.start()
    }

    @After
    fun stopFixture() {
        finishStalledRequest.countDown()
        if (::server.isInitialized) server.stop(0)
        workers.shutdownNow()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun scopeAndMediaAreFilteredBeforeAnyBookSourceRequest() = runBlocking {
        val image = source("image", "/image", "selected", BookSourceType.image)
        source("text", "/text", "selected")
        source("video", "/video", "selected", BookSourceType.video)
        source("other-image", "/other-image", "other", BookSourceType.image)
        source("disabled-image", "/disabled-image", "selected", BookSourceType.image, enabled = false)
        source("no-search", null, "selected", BookSourceType.image)
        val manga = book.copy(media = AiBookSearchMedia.MANGA)
        val match = lookup("selected", manga)
        assertEquals(AiBookSourceStatus.COMPLETE, match.status)
        assertEquals(listOf("/image"), requests.toList())
        assertEquals(image.bookSourceUrl, match.hits.single().origin)

        requests.clear()
        assertEquals(AiBookSourceStatus.COMPLETE, lookup("图源::${image.bookSourceUrl}", manga).status)
        assertEquals(listOf("/image"), requests.toList())
    }

    @Test
    fun unavailableScopeAndAmbiguousAudioAvoidSendingRequests() = runBlocking {
        source("audio", "/audio", "混合内容", BookSourceType.audio)
        source("音乐专源", "/music", "混合内容", BookSourceType.audio)
        source("text", "/text", "仅小说")
        assertEquals(AiBookSourceStatus.INVALID_SCOPE, lookup("已移除").status)
        assertEquals(AiBookSourceStatus.INVALID_SCOPE, lookup("书源::$base/removed").status)
        assertEquals(AiBookSourceStatus.NO_SOURCES, lookup("仅小说", book.copy(media = AiBookSearchMedia.MANGA)).status)
        val audio = book.copy(media = AiBookSearchMedia.AUDIOBOOK)
        assertEquals(AiBookSourceStatus.AUDIO_SCOPE_REQUIRED, lookup("混合内容", audio).status)
        assertTrue(requests.isEmpty())
        assertEquals(AiBookSourceStatus.COMPLETE, lookup("混合内容", audio, confirmedAudio = true).status)
        assertEquals(listOf("/audio"), requests.toList())
    }

    @Test
    fun openingAHistoricalHitRecoversItsOwnSourceWhenTheUrlCacheWasOverwritten() = runBlocking {
        val first = source("first", "/first", "首选")
        val other = source("other", "/other", "其它")
        val hit = lookup("首选").hits.single()

        requests.clear()
        assertEquals(first.bookSourceUrl, McpPreciseBookSearch.resolveHit(hit, book.title, AiBookSearchMedia.NOVEL)?.origin)
        assertTrue(requests.isEmpty())

        lookup("其它")
        assertEquals(other.bookSourceUrl, appDb.searchBookDao.getSearchBook(hit.url)?.origin)
        requests.clear()

        val restored = McpPreciseBookSearch.resolveHit(hit, book.title, AiBookSearchMedia.NOVEL)
        assertEquals(first.bookSourceUrl, restored?.origin)
        assertEquals(hit.url, restored?.bookUrl)
        assertEquals(listOf("/first"), requests.toList())

        requests.clear()
        assertNull(McpPreciseBookSearch.resolveHit(hit, book.title, AiBookSearchMedia.MANGA))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun namesakesMissingAuthorAndEmptyResultsAreDistinguished() = runBlocking {
        source("namesake", "/namesake", "同名")
        source("missing-author", "/missing-author", "缺作者")
        source("other-title", "/other-title", "作者栏")
        source("empty", "/empty", "无结果")
        val cases = mapOf(
            "同名" to AiBookSourceStatus.NAMESAKE,
            "缺作者" to AiBookSourceStatus.UNVERIFIED,
            "作者栏" to AiBookSourceStatus.COMPLETE,
            "无结果" to AiBookSourceStatus.COMPLETE,
        )
        for ((scope, status) in cases) {
            val result = lookup(scope)
            assertEquals(scope, status, result.status)
            assertTrue(scope, result.hits.isEmpty())
        }
    }

    @Test
    fun networkFailuresPreserveAnyVerifiedHitsAndAreNotReportedAsEmptySuccess() = runBlocking {
        source("good", "/ok", "部分命中")
        source("error-with-hit", "/error", "部分命中")
        source("error-without-hit", "/error", "部分无命中")
        source("empty-with-error", "/empty", "部分无命中")
        source("all-error", "/error", "全失败")
        val hit = lookup("部分命中")
        assertEquals(AiBookSourceStatus.INCOMPLETE, hit.status)
        assertEquals(1, hit.hits.size)
        assertEquals(1, hit.failedSources)
        val partial = lookup("部分无命中")
        assertEquals(AiBookSourceStatus.INCOMPLETE, partial.status)
        assertTrue(partial.hits.isEmpty())
        assertEquals(AiBookSourceStatus.FAILED, lookup("全失败").status)
    }

    @Test
    fun missingDetailLinkCurrentlyFallsBackToTheSearchPage() = runBlocking {
        source("missing-detail", "/missing-detail", "详情链接空")
        val result = lookup("详情链接空")
        // Some sources intentionally use the search response as the detail page. Record the fallback
        // here so the AI book-search audit can distinguish it from the missing-author state.
        assertEquals(AiBookSourceStatus.COMPLETE, result.status)
        assertEquals("$base/missing-detail", result.hits.single().url)
    }

    @Test
    fun stoppingABookSourceSearchCancelsTheWorkAndKeepsItsDeliveredHit() = runBlocking {
        source("fast", "/ok", "可停止")
        source("slow", "/stall", "可停止")
        val delivered = CompletableDeferred<List<AiBookSourceHit>>()
        val task = async { lookup("可停止", onHits = { delivered.complete(it) }) }
        val first = withTimeout(10_000) { delivered.await() }
        withTimeout(5_000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
        assertEquals(book.title, first.single().title)
    }

    @Test
    fun realBookSourceTimeoutReturnsPartialInsteadOfNoResults() = runBlocking {
        source("stalled", "/stall", "超时")
        val result = withTimeout(32_000) { lookup("超时") }
        assertEquals(AiBookSourceStatus.INCOMPLETE, result.status)
        assertTrue(result.hits.isEmpty())
        assertTrue(requests.contains("/stall"))
    }

    @Test
    fun mcpOffStopsBookSourcesBeforeSendingAnyRequest() = runBlocking {
        source("disabled-mcp", "/ok", "未开启")
        AiConfig.internalMcpEnabled = false
        val result = McpInternalChannel.searchPreciseBook(
            book, book.title, "未开启", false, listOf(McpPreciseBookSearch.CAPABILITY),
        ) {}
        assertEquals(AiBookSourceStatus.DISABLED, result.status)
        assertTrue(requests.isEmpty())
    }

    private suspend fun lookup(
        scope: String,
        candidate: AiBookSearchCandidate = book,
        confirmedAudio: Boolean = false,
        onHits: (List<AiBookSourceHit>) -> Unit = {},
    ) = McpPreciseBookSearch.run(candidate, candidate.title, scope, confirmedAudio, onHits)

    private fun source(
        id: String,
        path: String?,
        group: String,
        type: Int = BookSourceType.default,
        enabled: Boolean = true,
    ) = BookSource(
        bookSourceUrl = "$base/source/$id", bookSourceName = id, bookSourceGroup = group,
        bookSourceType = type, enabled = enabled, enabledCookieJar = false, searchUrl = path?.let { "$base$it" },
        ruleSearch = SearchRule(bookList = "$.rows[*]", name = "$.name", author = "$.author", bookUrl = "$.url"),
    ).also {
        appDb.bookSourceDao.insert(it)
    }

    private fun resultJson(title: String = book.title, author: String = book.author, link: String = "/book/42") =
        """{"rows":[{"name":"$title","author":"$author","url":"$link"}]}"""
}
