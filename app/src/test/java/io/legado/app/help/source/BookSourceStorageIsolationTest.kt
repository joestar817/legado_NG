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
    fun cacheIsSharedBySameUrlUpdateButNotAcrossSources() {
        val key = "isolation-${UUID.randomUUID()}"
        val sourceUrl = "https://example.com/source#stable"
        val firstInstall = BookSourceCacheStore(sourceUrl)
        val sameUrlUpdate = BookSourceCacheStore(sourceUrl)
        val otherSource = BookSourceCacheStore("https://example.com/source#other")

        try {
            firstInstall.putMemory(key, "kept-after-update")

            assertEquals("kept-after-update", sameUrlUpdate.getFromMemory(key))
            assertNull(otherSource.getFromMemory(key))
            assertNull(CacheManager.getFromMemory(key))
        } finally {
            firstInstall.deleteMemory(key)
            otherSource.deleteMemory(key)
        }
    }

    @Test
    fun nonBookSourcesKeepExistingGlobalObjects() {
        val bookSource = BookSource(
            bookSourceUrl = "https://example.com/book",
            bookSourceName = "书源"
        )
        val rssSource = RssSource(
            sourceUrl = "https://example.com/rss",
            sourceName = "RSS"
        )

        assertTrue(bookSource.scriptCacheObject() is BookSourceCacheStore)
        assertTrue(BookSourceCookieStore.forSource(bookSource) is BookSourceCookieStore)
        assertSame(CacheManager, rssSource.scriptCacheObject())
        assertSame(CookieStore, BookSourceCookieStore.forSource(rssSource))
    }

    @Test
    fun scopedCacheKeepsExistingScriptMethodSurface() {
        val methodNames = BookSourceCacheStore::class.java.methods.map { it.name }.toSet()
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
    fun rhinoBindingsKeepCookieAndCacheCompatibility() {
        val sourceA = BookSource(
            bookSourceUrl = "https://example.com/source#a",
            bookSourceName = "A"
        )
        val sourceB = BookSource(
            bookSourceUrl = "https://example.com/source#b",
            bookSourceName = "B"
        )
        val key = "rhino-${UUID.randomUUID()}"

        try {
            sourceA.evalJS("cache.putMemory('$key', 'source-a')")

            assertEquals("source-a", sourceA.evalJS("cache.getFromMemory('$key')"))
            assertNull(sourceB.evalJS("cache.getFromMemory('$key')"))
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
            BookSourceCacheStore(sourceA.bookSourceUrl).deleteMemory(key)
            BookSourceCacheStore(sourceB.bookSourceUrl).deleteMemory(key)
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
