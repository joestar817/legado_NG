package io.legado.app.help.source

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.CacheManager
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSharedCacheInstrumentedTest {

    @Test
    fun rssActivationStateIsPersistedForBookSourceAndCanBeAcknowledged() {
        runBlocking {
            withContext(Dispatchers.IO) {
                val rssSource = loadSource<RssSource>("source_shared_cache_rss.json")
                val bookSource = loadSource<BookSource>("source_shared_cache_book.json")
                val appSentinelKey = probeKeys.first()

                clearProbeState()
                try {
                    CacheManager.put(appSentinelKey, "app-sentinel")

                    val rssHeaders = rssSource.getHeaderMap()
                    assertEquals("rss", rssHeaders["X-Probe-Writer"])
                    Log.i(tag, "RSS source wrote activation-like cache keys")

                    activationKeys.forEach(SourceSharedCacheStore::deleteMemory)
                    val bookHeaders = bookSource.getHeaderMap()
                    val expectedState = "1|1|{\"origin\":\"rss\"}"
                    assertEquals(expectedState, bookHeaders["X-Probe-Read"])
                    Log.i(tag, "Book source restored RSS values after memory eviction: $expectedState")

                    SourceSharedCacheStore.deleteMemory(ackKey)
                    assertEquals(
                        "book-read|$expectedState",
                        rssSource.evalJS("cache.get('$ackKey')")
                    )
                    Log.i(tag, "RSS source restored the book acknowledgement from persistent cache")

                    assertSame(SourceSharedWebCacheStore, rssSource.webCacheObject())
                    assertSame(SourceSharedWebCacheStore, bookSource.webCacheObject())
                    assertEquals("1", SourceSharedWebCacheStore.get(activationKeys.first()))
                    Log.i(tag, "Book and RSS WebView cache bridges share the same source-only backend")

                    assertEquals("app-sentinel", CacheManager.get(appSentinelKey))
                    Log.i(tag, "Raw App cache key remained isolated from the source cache")
                } finally {
                    clearProbeState()
                    probeKeys.forEach { key ->
                        assertNull(SourceSharedCacheStore.get(key))
                        assertNull(CacheManager.get(key))
                    }
                    Log.i(tag, "Probe cache keys were removed from source and App storage")
                }
            }
        }
    }

    private inline fun <reified T> loadSource(assetName: String): T {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val json = assets.open(assetName).bufferedReader().use { it.readText() }
        return GSON.fromJson(json, T::class.java)
    }

    private fun clearProbeState() {
        probeKeys.forEach { key ->
            SourceSharedCacheStore.delete(key)
            CacheManager.delete(key)
        }
    }

    private companion object {
        const val tag = "SourceSharedCacheProbe"
        val activationKeys = listOf(
            "codex_probe:XHuser",
            "codex_probe:client_type",
            "codex_probe:xhJson",
        )
        const val ackKey = "codex_probe:book_ack"
        val probeKeys = activationKeys + ackKey
    }
}
