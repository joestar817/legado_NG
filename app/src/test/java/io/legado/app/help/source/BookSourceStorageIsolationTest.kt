package io.legado.app.help.source

import com.script.rhino.RhinoScriptEngine
import com.script.rhino.RhinoWrapFactory
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.CacheManager
import io.legado.app.help.http.BookSourceCookieStore
import io.legado.app.help.http.CookieStore
import io.legado.app.help.rhino.NativeBaseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.UUID

class BookSourceStorageIsolationTest {

    @Test
    fun namespaceUsesExactStableBookSourceUrl() {
        val sourceUrl = "https://example.com/source#main"

        assertEquals(
            BookSourceStorageScope.namespace(sourceUrl),
            BookSourceStorageScope.namespace(sourceUrl)
        )
        assertNotEquals(
            BookSourceStorageScope.namespace(sourceUrl),
            BookSourceStorageScope.namespace("$sourceUrl/")
        )
        assertEquals(64, BookSourceStorageScope.namespace(sourceUrl).length)
    }

    @Test
    fun sourceCacheIsSharedAcrossBookAndRssWithoutExposingAppKeys() {
        val key = "shared-${UUID.randomUUID()}"
        val bookSource = BookSource(
            bookSourceUrl = "https://example.com/book",
            bookSourceName = "书源"
        )
        val rssSource = RssSource(
            sourceUrl = "https://different.example.com/rss",
            sourceName = "RSS"
        )

        try {
            bookSource.evalJS("cache.putMemory('$key', 'from-book')")

            assertEquals("from-book", rssSource.evalJS("cache.getFromMemory('$key')"))
            assertNull(CacheManager.getFromMemory(key))

            CacheManager.putMemory(key, "app-only")
            rssSource.evalJS("cache.putMemory('$key', 'from-rss')")

            assertEquals("from-rss", bookSource.evalJS("cache.getFromMemory('$key')"))
            assertEquals("app-only", CacheManager.getFromMemory(key))
        } finally {
            SourceSharedCacheStore.deleteMemory(key)
            CacheManager.deleteMemory(key)
        }
    }

    @Test
    fun bookAndRssShareCacheButKeepCurrentCookieBoundary() {
        val bookSource = BookSource(
            bookSourceUrl = "https://example.com/book",
            bookSourceName = "书源"
        )
        val rssSource = RssSource(
            sourceUrl = "https://example.com/rss",
            sourceName = "RSS"
        )

        assertSame(SourceSharedCacheStore, bookSource.scriptCacheObject())
        assertSame(SourceSharedCacheStore, rssSource.scriptCacheObject())
        assertSame(SourceSharedWebCacheStore, bookSource.webCacheObject())
        assertSame(SourceSharedWebCacheStore, rssSource.webCacheObject())
        assertTrue(BookSourceCookieStore.forSource(bookSource) is BookSourceCookieStore)
        assertSame(CookieStore, BookSourceCookieStore.forSource(rssSource))
    }

    @Test
    fun sharedCacheKeepsExistingScriptMethodSurface() {
        val methodNames = SourceSharedCacheStore::class.java.methods.map { it.name }.toSet()
        val expectedMethods = setOf(
            "put",
            "putMemory",
            "getFromMemory",
            "deleteMemory",
            "get",
            "getInt",
            "getLong",
            "getDouble",
            "getFloat",
            "getByteArray",
            "putFile",
            "getFile",
            "delete"
        )

        assertTrue(methodNames.containsAll(expectedMethods))
    }

    @Test
    fun batchVariableCleanupOnlyMatchesSelectedSources() {
        val selected = "https://example.com/source_a"
        val other = "${selected}_other"
        val cacheKeys = listOf(
            "v_${selected}_token",
            "userInfo_$selected",
            "loginHeader_$selected",
            "sourceVariable_$selected",
            "infoMap_$selected",
            "v_${other}_token",
            "userInfo_$other",
            "nh-123-4-0-text",
            "source_cache_legacy",
            "book_source_cache_unrelated:value",
        )

        assertEquals(
            setOf(
                "v_${selected}_token",
                "userInfo_$selected",
                "loginHeader_$selected",
                "sourceVariable_$selected",
                "infoMap_$selected",
            ),
            matchingBookSourceVariableCacheKeys(cacheKeys, listOf(selected)).toSet(),
        )
    }

    @Test
    fun rhinoBindingsKeepCookieAndCacheCompatibility() {
        val sourceA = BookSource(
            bookSourceUrl = "https://example.com/source#a",
            bookSourceName = "A"
        )
        val rssSource = RssSource(
            sourceUrl = "https://different.example.com/rss",
            sourceName = "RSS"
        )
        val key = "rhino-${UUID.randomUUID()}"

        try {
            sourceA.evalJS("cache.putMemory('$key', 'source-a')")

            assertEquals("source-a", sourceA.evalJS("cache.getFromMemory('$key')"))
            assertEquals("source-a", rssSource.evalJS("cache.getFromMemory('$key')"))
            assertEquals(
                "function|function|function|function|function|function|function|function",
                sourceA.evalJS(
                    """
                        [
                          typeof cookie.getCookie,
                          typeof cookie.getKey,
                          typeof cookie.removeCookie,
                          typeof cookie.setCookie,
                          typeof cookie.setWebCookie,
                          typeof cache.get,
                          typeof cache.put,
                          typeof cache.delete
                        ].join('|')
                    """.trimIndent()
                )
            )
        } finally {
            SourceSharedCacheStore.deleteMemory(key)
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerSourceWrappers() {
            RhinoWrapFactory.register(BookSource::class.java, NativeBaseSource.factory)
            RhinoWrapFactory.register(RssSource::class.java, NativeBaseSource.factory)
            RhinoScriptEngine
        }
    }
}
