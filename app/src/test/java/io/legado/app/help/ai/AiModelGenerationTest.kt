package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AiModelGenerationTest {
    @Test
    fun aTestCanUseEitherProtocolWithoutChangingTheProviderDefault() = runBlocking {
        val requests = LinkedBlockingQueue<Pair<String, JsonObject>>()
        withServer({ exchange ->
            val path = exchange.requestURI.path
            requests.add(path to exchange.readJson())
            if (path == "/v1/custom-generation") {
                val completion = """{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"OK"}]}]}"""
                exchange.respond(
                    "data: {\"type\":\"response.completed\",\"response\":$completion}\n\n",
                    contentType = "text/event-stream",
                )
            } else {
                exchange.respond(
                    """{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}""",
                )
            }
        }) { source ->
            val provider = source.copy(builtIn = false, responsesApiPath = "/custom-generation")
            val model = AiModel("selected-model")
            val client = AiChatClient()
            assertEquals(
                AiModelGenerationTestResult.Success("OK"),
                client.testModelGeneration(provider, model, true, AiModelGenerationTestProtocol.RESPONSES),
            )
            assertEquals(
                AiModelGenerationTestResult.Success("OK"),
                client.testModelGeneration(provider, model, false),
            )
            val (responsesPath, responses) = requests.remove()
            assertEquals("/v1/custom-generation", responsesPath)
            assertTrue(responses.has("input"))
            assertFalse(responses.has("messages"))
            assertTrue(responses.get("stream").asBoolean)
            val (chatPath, chat) = requests.remove()
            assertEquals("/v1/chat/completions", chatPath)
            assertTrue(chat.has("messages"))
            assertFalse(chat.has("input"))
            assertFalse(chat.get("stream").asBoolean)
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun presetsResolveTheirOwnResponsesEndpointAndKeepTheirCatalog() {
        val presets = AiDefaultProviders.all().associateBy(AiProviderSetting::id)
        val paths = mapOf(
            "deepseek" to "https://api.deepseek.com/responses",
            "openai" to "https://api.openai.com/v1/responses",
            "opencode" to "https://opencode.ai/inference/go/openai/v1/responses",
            "zhipu" to "https://open.bigmodel.cn/api/v1/responses",
        )
        paths.forEach { (id, expectedUrl) ->
            val provider = presets.getValue(id)
            val enabled = provider.copy(useResponsesApi = true)
            assertEquals(AiModelGenerationTestProtocol.CHAT_COMPLETIONS, provider.modelGenerationTestProtocol())
            assertEquals(AiModelGenerationTestProtocol.RESPONSES, enabled.modelGenerationTestProtocol())
            assertEquals(provider.chatCompletionsPath, enabled.chatCompletionsPath)
            assertEquals(provider.modelConfigurationKey(), enabled.modelConfigurationKey())
            val responsePath = requireNotNull(enabled.generationApiPath(AiModelGenerationTestProtocol.RESPONSES))
            assertEquals(expectedUrl, buildAiApiEndpoint(enabled.baseUrl, responsePath))
        }
        val changedHost = presets.getValue("deepseek").copy(baseUrl = "https://custom.example")
        assertEquals(listOf(AiModelGenerationTestProtocol.CHAT_COMPLETIONS), changedHost.modelGenerationTestProtocols())
    }

    @Test
    fun previouslyConfiguredResponsesAddressCanSwitchBackToChatCompletions() {
        val custom = AiProviderSetting(
            id = "custom",
            type = AiProviderType.OPENAI,
            builtIn = false,
            name = "Custom",
            baseUrl = "https://custom.example/v1",
            chatCompletionsPath = "/previous/responses",
        )
        assertEquals(AiModelGenerationTestProtocol.RESPONSES, custom.modelGenerationTestProtocol())
        val switched = custom.copy(useResponsesApi = false)
        assertEquals(AiModelGenerationTestProtocol.CHAT_COMPLETIONS, switched.modelGenerationTestProtocol())
        assertEquals("/chat/completions", switched.generationApiPath(AiModelGenerationTestProtocol.CHAT_COMPLETIONS))
        assertEquals(custom.chatCompletionsPath, switched.generationApiPath(AiModelGenerationTestProtocol.RESPONSES))
        assertEquals(custom.modelConfigurationKey(), switched.modelConfigurationKey())
    }

    @Test
    fun selectedModeAndModelAreUsedForExactlyOneRequestEach() = runBlocking {
        val requests = LinkedBlockingQueue<JsonObject>()
        withServer({ exchange ->
            val request = exchange.readJson()
            requests.add(request)
            if (request.get("stream").asBoolean) {
                exchange.respond(
                    listOf(
                        """data: {"choices":[{"delta":{"role":"assistant","content":"好的"},"finish_reason":null}]}""",
                        """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
                        "data: [DONE]",
                        "",
                    ).joinToString("\n\n"),
                    contentType = "text/event-stream",
                )
            } else {
                exchange.respond(
                    """{"choices":[{"message":{"role":"assistant","content":"OK。"},"finish_reason":"stop"}]}""",
                )
            }
        }) { setting ->
            val client = AiChatClient()
            val model = AiModel("selected-model")
            assertEquals(AiModelGenerationTestResult.Success("好的"), client.testModelGeneration(setting, model, true))
            assertEquals(AiModelGenerationTestResult.Success("OK。"), client.testModelGeneration(setting, model, false))
            assertEquals(listOf(true, false), requests.map { it.get("stream").asBoolean })
            requests.forEach { request ->
                assertEquals(model.id, request.stringOrNull("model"))
                val messages = request.getAsJsonArray("messages")
                assertEquals(1, messages.size())
                assertEquals("user", messages[0].asJsonObject.stringOrNull("role"))
                assertEquals("请只回复 OK。", messages[0].asJsonObject.stringOrNull("content"))
                for (field in listOf("tools", "thinking", "reasoning_effort", "temperature", "max_tokens")) {
                    assertFalse(request.has(field))
                }
            }
            assertFalse(setting.streamResponseEnabled)
        }
    }

    @Test
    fun interruptedStreamRetainsOnlyVisibleReply() = runBlocking {
        withServer({ exchange ->
            exchange.readJson()
            exchange.respond(
                """data: {"choices":[{"delta":{"content":"O","reasoning_content":"private"},"finish_reason":null}]}""",
                contentType = "text/event-stream",
            )
        }) { setting ->
            assertEquals(
                AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.INTERRUPTED, "O"),
                AiChatClient().testModelGeneration(setting, AiModel("selected"), true),
            )
        }
    }

    @Test
    fun completedStreamDoesNotWaitForDoneOrSocketClose() = runBlocking {
        val closeStream = CountDownLatch(1)
        withServer({ exchange ->
            exchange.readJson()
            exchange.respondStreaming { body ->
                body.write(
                    ("""data: {"choices":[{"delta":{"content":"OK"},"finish_reason":"stop"}]}""" +
                        "\n\n").encodeToByteArray(),
                )
                body.flush()
                closeStream.await(20, TimeUnit.SECONDS)
            }
        }) { setting ->
            try {
                val result = withTimeout(10_000) {
                    AiChatClient().testModelGeneration(setting.copy(timeoutSeconds = 20), AiModel("selected"), true)
                }
                assertEquals(AiModelGenerationTestResult.Success("OK"), result)
            } finally {
                closeStream.countDown()
            }
        }
    }

    @Test
    fun streamKeepAlivesDoNotExtendRequestDeadline() = runBlocking {
        val closeStream = CountDownLatch(1)
        withServer({ exchange ->
            exchange.readJson()
            try {
                exchange.respondStreaming { body ->
                    body.write(
                        ("""data: {"choices":[{"delta":{"content":"O"},"finish_reason":null}]}""" +
                            "\n\n").encodeToByteArray(),
                    )
                    body.flush()
                    val keepAlive = ": keep-alive\n\n".encodeToByteArray()
                    while (!closeStream.await(100, TimeUnit.MILLISECONDS)) {
                        body.write(keepAlive)
                        body.flush()
                    }
                }
            } catch (_: IOException) {
                // The client closes the socket on its deadline.
            }
        }) { setting ->
            try {
                val result = withTimeout(12_000) {
                    AiChatClient().testModelGeneration(setting, AiModel("selected"), true)
                }
                assertEquals(
                    AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.TIMEOUT, "O"),
                    result,
                )
            } finally {
                closeStream.countDown()
            }
        }
    }

    @Test
    fun reportsHttpStatusWithoutReturningProviderBody() = runBlocking {
        withServer({ exchange ->
            exchange.readJson()
            exchange.respond("""{"error":{"message":"fixture echo"}}""", code = 401)
        }) { setting ->
            for (stream in listOf(false, true)) {
                assertEquals(
                    AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.HTTP, httpStatus = 401),
                    AiChatClient().testModelGeneration(setting, AiModel("selected"), stream),
                )
            }
        }
    }

    @Test
    fun cancelStopsReadingNonStreamingBody() = runBlocking {
        val bodyStarted = CompletableDeferred<Unit>()
        val finishResponse = CountDownLatch(1)
        withServer({ exchange ->
            exchange.readJson()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { body ->
                body.write('{'.code)
                body.flush()
                bodyStarted.complete(Unit)
                finishResponse.await(5, TimeUnit.SECONDS)
            }
        }) { setting ->
            val test = launch {
                AiChatClient().testModelGeneration(setting, AiModel("selected"), false)
            }
            try {
                withTimeout(5_000) { bodyStarted.await() }
                withTimeout(2_000) { test.cancelAndJoin() }
                assertTrue(test.isCancelled)
            } finally {
                finishResponse.countDown()
            }
        }
    }

    @Test
    fun finishReasonsAndNonTextRepliesDoNotCountAsSuccess() {
        assertEquals(
            AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.TRUNCATED, "partial"),
            evaluateModelGenerationTestReply("partial", "length", true, false, false),
        )
        assertEquals(
            AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.REFUSED),
            evaluateModelGenerationTestReply("", "stop", true, false, true),
        )
        assertEquals(
            AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.TOOL_CALL),
            evaluateModelGenerationTestReply("", "stop", true, true, false),
        )
        assertEquals(
            AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.EMPTY_REPLY),
            evaluateModelGenerationTestReply("", "stop", true, false, false),
        )
    }

    private suspend fun withServer(
        handler: (HttpExchange) -> Unit,
        verify: suspend (AiProviderSetting) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange -> handler(exchange) }
        server.createContext("/v1/custom-generation") { exchange -> handler(exchange) }
        server.start()
        try {
            verify(
                AiProviderSetting(
                    id = "fixture",
                    type = AiProviderType.OPENAI,
                    name = "Fixture",
                    apiKey = "fixture",
                    baseUrl = "http://127.0.0.1:${server.address.port}/v1",
                    timeoutSeconds = 5,
                    model = "provider-default",
                ),
            )
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.readJson(): JsonObject =
        requestBody.use { JsonParser.parseString(it.readBytes().decodeToString()).asJsonObject }

    private fun HttpExchange.respondStreaming(write: (OutputStream) -> Unit) {
        responseHeaders.set("Content-Type", "text/event-stream; charset=utf-8")
        sendResponseHeaders(200, 0)
        responseBody.use(write)
    }

    private fun HttpExchange.respond(
        text: String,
        code: Int = 200,
        contentType: String = "application/json",
    ) {
        val body = text.encodeToByteArray()
        responseHeaders.set("Content-Type", "$contentType; charset=utf-8")
        sendResponseHeaders(code, body.size.toLong())
        responseBody.use { it.write(body) }
    }
}
