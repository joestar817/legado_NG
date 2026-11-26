package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.lang.reflect.InvocationTargetException
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class AiChatStreamCancellationTest {

    @Test
    fun `stop closes the request before the first frame`() {
        verifyStop(firstFrame = null)
    }

    @Test
    fun `stop closes the request after a partial reply`() {
        verifyStop(firstFrame = "partial")
    }

    @Test
    fun openRouterStreamReplaysOrderedReasoningWithTheToolResult() = runBlocking {
        val provider = AiDefaultProviders.all().single { it.id == "openrouter" }.copy(apiKey = "router-test")
        val model = AiModel("anthropic/claude-example")
        val route = requireNotNull(provider.openRouterChatHistory(model.id))
        val frames = listOf(
            """{"delta":{"reasoning_details":[{"type":"reasoning.summary","summary":"Find ","index":0}]}}""",
            """{"delta":{"reasoning_details":[{"type":"reasoning.summary","summary":"evidence","index":0}]}}""",
            """{"delta":{"reasoning_details":[{"type":"reasoning.encrypted","id":"r1","data":"opaque","index":0}]}}""",
            """{"delta":{"reasoning_details":[{"type":"reasoning.text","id":"r2","text":"Read ","index":0}]}}""",
            """{"delta":{"reasoning_details":[{"type":"reasoning.text","id":"r2","text":"source","index":0}]}}""",
            """{"delta":{"reasoning_details":[{"type":"reasoning.text","id":"r2","signature":"sig","index":0}]}}""",
            """{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"Check","signature":"sig-2","index":0}]}}""",
            """{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"mcp__legado__legado_ping","arguments":"{}"}}]},"finish_reason":"tool_calls"}""",
        )
        val data = frames.joinToString("") { "data: {\"choices\":[$it]}\n\n" } + "data: [DONE]\n\n"
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            thread(name = "openrouter-reasoning-fixture", isDaemon = true) { serveFinishedStream(server, data) }
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/chat/completions").build()
            val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
            val chat = AiChatClient()
            val decoded = try {
                requireNotNull(withTimeout(5_000) { readStream(chat, http, request, mutableListOf(), route) })
            } finally {
                http.connectionPool.evictAll()
                http.dispatcher.executorService.shutdown()
            }
            val message = decoded.javaClass.getDeclaredMethod("getMessage").apply { isAccessible = true }
                .invoke(decoded) as JsonObject
            val restored = JsonParser.parseString(message.toString()).asJsonObject
            val tool = JsonParser.parseString(
                """{"role":"tool","tool_call_id":"call_1","content":"pong"}"""
            ).asJsonObject
            val history = listOf(restored, tool)
            val wire = chat.buildRequestBody(provider, model, history, emptyList(), AiTextParams(), true)
                .arrayOrNull("messages").objectParts()
            assertEquals("call_1", wire[0].arrayOrNull("tool_calls").objectParts().single().stringOrNull("id"))
            assertEquals("call_1", wire[1].stringOrNull("tool_call_id"))
            assertFalse(wire[0].has(AI_NATIVE_HISTORY))
            assertEquals(JsonParser.parseString("""
                [
                  {"type":"reasoning.summary","summary":"Find evidence","index":0},
                  {"type":"reasoning.encrypted","id":"r1","data":"opaque","index":0},
                  {"type":"reasoning.text","id":"r2","text":"Read source","signature":"sig","index":0},
                  {"type":"reasoning.text","text":"Check","signature":"sig-2","index":0}
                ]
            """), wire[0].arrayOrNull(OPENROUTER_REASONING_DETAILS))
            for ((otherProvider, otherModel) in listOf(
                provider.copy(apiKey = "another-key") to model,
                provider to model.copy(id = "a-different-model"),
                provider.copy(baseUrl = "https://example.invalid/api/v1") to model,
            )) {
                val body = chat.buildRequestBody(otherProvider, otherModel, history, emptyList(), AiTextParams(), true)
                assertFalse(body.toString().contains(OPENROUTER_REASONING_DETAILS))
                assertFalse(body.toString().contains("opaque"))
            }
        }
    }

    private fun verifyStop(firstFrame: String?) = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 8_000
            val peerClosed = CompletableDeferred<Unit>()
            val serverResult = CompletableDeferred<Unit>()
            thread(name = "ai-chat-stream-fixture", isDaemon = true) {
                serverResult.completeWith(runCatching {
                    serveStalledStream(server, firstFrame)
                    peerClosed.complete(Unit)
                    serveFinishedStream(server)
                }.onFailure { peerClosed.completeExceptionally(it) })
            }
            val updates = mutableListOf<String>()
            val readingBody = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder()
                .proxy(Proxy.NO_PROXY)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val body = response.body
                    val source = object : ForwardingSource(body.source()) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            // Cancel only once the parser is reading the stalled body, past await().
                            if (firstFrame == null || updates.isNotEmpty()) {
                                readingBody.complete(Unit)
                            }
                            return super.read(sink, byteCount)
                        }
                    }.buffer()
                    response.newBuilder().body(object : ResponseBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source() = source
                    }).build()
                }
                .build()
            val chat = AiChatClient()
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/chat/completions").build()
            val exitReason = CompletableDeferred<Throwable?>()
            val sending = launch {
                exitReason.complete(runCatching { readStream(chat, client, request, updates) }.exceptionOrNull())
            }
            try {
                withTimeout(5_000) { readingBody.await() }
                sending.cancel()
                withTimeout(2_000) {
                    val error = exitReason.await()
                    assertTrue("Expected cancellation, got $error", error is CancellationException)
                    sending.join()
                    peerClosed.await()
                }
                assertEquals(listOfNotNull(firstFrame), updates)

                val nextUpdates = mutableListOf<String>()
                withTimeout(5_000) {
                    readStream(chat, client, request, nextUpdates)
                    serverResult.await()
                }
                assertEquals(listOf("next"), nextUpdates)
            } finally {
                sending.cancel()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    private fun serveStalledStream(server: ServerSocket, firstFrame: String?) {
        server.accept().use { socket ->
            socket.soTimeout = 8_000
            val input = readRequest(socket)
            val output = socket.getOutputStream()
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n").toByteArray())
            firstFrame?.let { text ->
                val data = frame(text).toByteArray()
                output.write("${data.size.toString(16)}\r\n".toByteArray())
                output.write(data)
                output.write("\r\n".toByteArray())
            }
            output.flush()
            assertEquals("Client must close the socket while the body is still open", -1, input.read())
        }
    }

    private fun serveFinishedStream(server: ServerSocket, stream: String = frame("next") + "data: [DONE]\n\n") {
        server.accept().use { socket ->
            socket.soTimeout = 5_000
            readRequest(socket)
            val data = stream.toByteArray()
            val output = socket.getOutputStream()
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                "Content-Length: ${data.size}\r\nConnection: close\r\n\r\n").toByteArray())
            output.write(data)
            output.flush()
        }
    }

    private fun readRequest(socket: Socket): BufferedReader =
        socket.getInputStream().bufferedReader(Charsets.US_ASCII).also { input ->
            while (!input.readLine().isNullOrEmpty()) Unit
        }

    private fun frame(content: String): String =
        """data: {"choices":[{"delta":{"content":"$content"}}]}""" + "\n\n"

    private suspend fun readStream(
        chat: AiChatClient,
        client: OkHttpClient,
        request: Request,
        updates: MutableList<String>,
        openRouterHistory: AiNativeChatHistory? = null,
    ): Any? = suspendCoroutineUninterceptedOrReturn { continuation ->
        val onUpdate: (AiChatStreamUpdate) -> Unit = { updates += it.content }
        try {
            executeStreamChat.invoke(
                chat, client, request, "reasoning_content", openRouterHistory, onUpdate, false, true, null, null,
                false, continuation,
            )
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private val executeStreamChat = AiChatClient::class.java.getDeclaredMethod(
        "executeStreamChat", OkHttpClient::class.java, Request::class.java, String::class.java,
        AiNativeChatHistory::class.java, Function1::class.java, Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, AiNativeChatHistory::class.java, AiNativeWebEvents::class.java,
        Boolean::class.javaPrimitiveType, Continuation::class.java,
    ).apply { isAccessible = true }
}
