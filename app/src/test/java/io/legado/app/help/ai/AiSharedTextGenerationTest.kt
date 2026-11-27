package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.http.NetworkLog
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSharedTextGenerationTest {
    private val input = listOf(
        AiMessage(AiMessage.Role.SYSTEM, "Return a JSON object."),
        AiMessage(AiMessage.Role.USER, "Use the synthetic sample."),
    )

    @Test
    fun textWorkflowsHonorTheModelResponseRouteAndStreaming() = runBlocking {
        val setting = setting(AiProviderType.OPENAI).copy(streamResponseEnabled = true)
        val model = AiModel(
            "review",
            modelSettings = AiModelSettings(
                catalogEndpoint = setting.modelConfigurationKey(),
                interfacePreference = AiModelInterfacePreference.RESPONSES,
                maxOutputTokens = 256,
            ),
        )
        val frame = """{"type":"response.completed","response":{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"{\"ok\":true}"}]}]}}"""
        val client = mockClient("data: $frame\n\n", "text/event-stream") { request, body ->
            assertEquals("/v1/responses", request.url.encodedPath)
            assertTrue(body.get("stream").asBoolean)
            assertEquals(256, body.get("max_output_tokens").asInt)
            assertEquals("json_object", body.getAsJsonObject("text").getAsJsonObject("format").get("type").asString)
            assertFalse(body.has("tools"))
        }
        val result = client.generateText(setting, model, input, AiTextParams(maxTokens = 400, jsonResponse = true))
        assertEquals("""{"ok":true}""", result.content)
    }

    @Test
    fun rejectsATextPrefixWhenAnOldChatCompletionsProviderReportsLength() {
        assertTruncated(
            AiProviderType.OPENAI,
            """/v1/chat/completions""",
            """{"choices":[{"message":{"role":"assistant","content":"Only the first sentence."},"finish_reason":"length"}]}""",
        )
    }

    @Test
    fun legacyNonStreamingGatewayCanReturnDeltaText() = runBlocking {
        val reply = """{"choices":[{"delta":{"content":"OK"},"finish_reason":"stop"}]}"""
        val client = mockClient(reply) { _, body -> assertFalse(body.get("stream").asBoolean) }
        val result = client.generateText(setting(AiProviderType.OPENAI), AiModel("review"), input, AiTextParams())
        assertEquals("OK", result.content)
    }

    @Test
    fun mapsClaudeAndGeminiLengthStopsToTheSharedTruncationError() {
        assertTruncated(
            AiProviderType.CLAUDE,
            "/v1/messages",
            """{"content":[{"type":"text","text":"Prefix"}],"stop_reason":"max_tokens"}""",
        )
        assertTruncated(
            AiProviderType.GOOGLE,
            "/v1/models/review:generateContent",
            """{"candidates":[{"content":{"parts":[{"text":"Prefix"}]},"finishReason":"MAX_TOKENS"}]}""",
        )
    }

    @Test
    fun responseTruncationGetsTheSameTypeForNonAssistantCalls() {
        val frame = """{"type":"response.incomplete","response":{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Prefix"}]}],"usage":{"input_tokens":45,"output_tokens":12,"total_tokens":57}}}"""
        val usageReports = mutableListOf<AiChatUsage?>()
        lateinit var sent: Request
        val client = mockClient("data: $frame\n\n", "text/event-stream", usageReports::add) { request, _ ->
            sent = request
        }
        val setting = setting(AiProviderType.OPENAI).copy(
            useResponsesApi = true,
            streamResponseEnabled = true,
        )
        val error = assertThrows(AiAssistantReplyEndException::class.java) {
            runBlocking { client.generateText(setting, AiModel("review"), input, AiTextParams()) }
        }
        assertEquals(AiAssistantReplyEndReason.TRUNCATED, error.reason)
        assertEquals(listOf(AiChatUsage(45, 12, 57)), usageReports)
        assertEquals(
            NetworkLog.GenerationState.TRUNCATED,
            sent.tag(NetworkLog.GenerationTrace::class.java)?.result?.state,
        )
    }

    @Test
    fun streamingUsageReportsOncePerRequestWithTheLatestCounts() = runBlocking {
        val response = """
            data: {"usage":{"prompt_tokens":40,"completion_tokens":0},"choices":[]}

            data: {"choices":[{"delta":{"content":"OK"},"finish_reason":"stop"}]}

            data: {"usage":{"prompt_tokens":0,"completion_tokens":8,"total_tokens":48},"choices":[]}

            data: [DONE]
        """.trimIndent()
        val usageReports = mutableListOf<AiChatUsage?>()
        val client = mockClient(response, "text/event-stream", usageReports::add) { _, _ -> }
        val setting = setting(AiProviderType.OPENAI).copy(streamResponseEnabled = true)

        repeat(2) { client.generateText(setting, AiModel("review"), input, AiTextParams()) }

        assertEquals(listOf(AiChatUsage(40, 8, 48), AiChatUsage(40, 8, 48)), usageReports)
    }

    @Test
    fun textWorkflowsCollectStreamingForAnExistingStreamingOnlyModel() = runBlocking {
        val setting = AiDefaultProviders.all().single { it.id == "aliyun_bailian" }.copy(apiKey = "fixture")
        val frame = """{"choices":[{"delta":{"content":"OK"},"finish_reason":"stop"}]}"""
        val client = mockClient("data: $frame\n\ndata: [DONE]\n\n", "text/event-stream") { request, body ->
            assertEquals("/compatible-mode/v1/chat/completions", request.url.encodedPath)
            assertTrue(body.get("stream").asBoolean)
        }
        assertEquals("OK", client.generateText(setting, AiModel("qwen3-32b"), input, AiTextParams()).content)
        assertFalse(setting.streamResponseEnabled)
    }

    @Test
    fun officialOpenAiOSeriesUsesTheSupportedOutputBudget() = runBlocking {
        val setting = AiDefaultProviders.all().single { it.id == "openai" }.copy(apiKey = "fixture")
        val reply = """{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}"""
        val client = mockClient(reply) { _, body ->
            assertEquals(1024, body.get("max_completion_tokens").asInt)
            assertFalse(body.has("max_tokens"))
            assertFalse(body.has("temperature"))
        }
        assertEquals(
            "OK",
            client.generateText(setting, AiModel("o3"), input, AiTextParams(maxTokens = 1024, temperature = 0f)).content,
        )
    }

    private fun assertTruncated(type: AiProviderType, path: String, response: String) {
        val client = mockClient(response) { request, body ->
            assertEquals(path, request.url.encodedPath)
            assertFalse(body.has("tools"))
            if (type == AiProviderType.CLAUDE) assertTrue(body.get("max_tokens").asInt > 0)
        }
        val error = assertThrows(AiAssistantReplyEndException::class.java) {
            runBlocking { client.generateText(setting(type), AiModel("review"), input, AiTextParams()) }
        }
        assertEquals(AiAssistantReplyEndReason.TRUNCATED, error.reason)
    }

    private fun setting(type: AiProviderType) = AiProviderSetting(
        id = "fixture",
        name = "Fixture",
        type = type,
        baseUrl = "https://example.invalid/v1",
        apiKey = "fixture",
        builtIn = false,
    )

    private fun mockClient(
        reply: String,
        contentType: String = "application/json",
        onModelRequestUsage: ((AiChatUsage?) -> Unit)? = null,
        verify: (Request, JsonObject) -> Unit,
    ): AiChatClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            verify(request, JsonParser.parseString(body).asJsonObject)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body(reply.toResponseBody(contentType.toMediaType())).build()
        }.build()
        return AiChatClient(onModelRequestUsage = onModelRequestUsage) { http }
    }
}
