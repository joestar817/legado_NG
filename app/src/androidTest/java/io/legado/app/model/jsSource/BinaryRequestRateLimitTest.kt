package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.BookSource
import io.legado.app.help.ConcurrentRateLimiter
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class BinaryRequestRateLimitTest {
    @Test fun binaryOptOutDoesNotConsumeTheSourceRateBudget() = runBlocking {
        val server = ServerSocket(0).apply { soTimeout = 5_000 }
        val url = "http://127.0.0.1:${server.localPort}/data"
        val source = BookSource(bookSourceUrl = url, concurrentRate = "100/60000")
        val worker = thread(isDaemon = true) {
            repeat(3) {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    socket.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray(),
                    )
                }
            }
        }
        fun request(binary: Boolean) = AnalyzeUrl(
            mUrl = if (binary) "$url,{\"type\":\"binary\"}" else url,
            source = source,
            hasLoginHeader = false,
            headerMapF = emptyMap(),
            callTimeout = 5_000,
        )
        ConcurrentRateLimiter.concurrentRecordMap.remove(url)
        try {
            assertEquals("ok", request(false).getStrResponseAwait().body)
            assertEquals(1, ConcurrentRateLimiter.concurrentRecordMap[url]?.frequency)
            assertEquals("6f6b", request(true).getStrResponseAwait(skipRateLimit = true).body)
            assertEquals(1, ConcurrentRateLimiter.concurrentRecordMap[url]?.frequency)
            assertEquals("6f6b", request(true).getStrResponseAwait().body)
            assertEquals(2, ConcurrentRateLimiter.concurrentRecordMap[url]?.frequency)
        } finally {
            server.close()
            worker.join(5_000)
            ConcurrentRateLimiter.concurrentRecordMap.remove(url)
        }
    }
}
