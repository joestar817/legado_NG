package io.legado.app.help.ai

import com.sun.net.httpserver.HttpServer
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AiProviderNetworkTest {
    @Test
    fun `same origin redirect preserves authentication and request body`() {
        withServer { server, base ->
            val received = AtomicReference<Triple<String, String, String>>()
            server.createContext("/start") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                exchange.responseHeaders.add("Location", "/next")
                exchange.sendResponseHeaders(307, -1)
                exchange.close()
            }
            server.createContext("/next") { exchange ->
                received.set(Triple(
                    exchange.requestMethod,
                    exchange.requestHeaders.getFirst("Authorization"),
                    exchange.requestBody.use { it.readBytes().decodeToString() },
                ))
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            val request = Request.Builder().url("$base/start")
                .header("Authorization", "Bearer fixture")
                .post("test message".toRequestBody())
                .build()
            aiHttpClient(5).newCall(request).execute().use { assertEquals(204, it.code) }
            assertEquals(Triple("POST", "Bearer fixture", "test message"), received.get())
        }
    }

    @Test
    fun `redirect to a different port sends no request to the destination`() {
        withServer { target, targetUrl ->
            val requests = AtomicInteger()
            target.createContext("/") { exchange ->
                requests.incrementAndGet()
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            withServer { source, sourceUrl ->
                source.createContext("/") { exchange ->
                    exchange.requestBody.use { it.readBytes() }
                    exchange.responseHeaders.add("Location", targetUrl)
                    exchange.sendResponseHeaders(307, -1)
                    exchange.close()
                }
                val request = Request.Builder().url(sourceUrl)
                    .header("Authorization", "Bearer fixture")
                    .post("test message".toRequestBody())
                    .build()
                assertThrows(AiEndpointException::class.java) {
                    aiHttpClient(5).newCall(request).execute().close()
                }
                assertEquals(0, requests.get())
            }
        }
    }

    @Test
    fun `default model directory is based on parsed origin`() {
        assertEquals(listOf("https://api.deepseek.com/models"),
            AiModelEndpointResolver.candidates("https://api.deepseek.com/v1"))
        assertEquals(listOf("https://fixture.invalid/api.deepseek.com/v1/models"),
            AiModelEndpointResolver.candidates("https://fixture.invalid/api.deepseek.com/v1"))
        assertEquals(listOf("https://api.deepseek.com:4431/v1/models"),
            AiModelEndpointResolver.candidates("https://api.deepseek.com:4431/v1"))
        assertEquals(listOf("https://claude/v1/models"), AiModelEndpointResolver.candidates("https://claude"))
        assertEquals(listOf("https://fixture.invalid/team/claude/v1/models", "https://fixture.invalid/team/v1/models",
            "https://fixture.invalid/team/models"),
            AiModelEndpointResolver.candidates("https://fixture.invalid/team/claude"))
    }

    @Test
    fun `alternate model and balance paths stay on the configured origin`() {
        val base = "https://fixture.invalid/v1"
        assertEquals("$base/models?limit=10", buildAiApiEndpoint(base, "/models?limit=10"))
        assertEquals("https://fixture.invalid/public/models",
            buildAiApiEndpoint(base, "https://fixture.invalid/public/models"))
        for (url in listOf("http://fixture.invalid/models", "https://other.invalid/models",
            "https://fixture.invalid:444/models", "https://user@fixture.invalid/models")
        ) {
            assertThrows(AiEndpointException::class.java) { buildAiApiEndpoint(base, url) }
        }
        assertTrue(AiModelEndpointResolver.candidates("https://user@fixture.invalid").isEmpty())
        val tenant = "https://fixture.invalid/tenant"
        val caseSensitive = "https://fixture.invalid/Tenant/models?limit=5"
        assertEquals(caseSensitive, normalizeAiApiPath(tenant, caseSensitive))
        assertEquals("/models?limit=5", normalizeAiApiPath(tenant, "$tenant/models?limit=5"))
        assertEquals("$tenant?mode=list", normalizeAiApiPath(tenant, "$tenant?mode=list"))
    }

    private fun withServer(block: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block(server, "http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
