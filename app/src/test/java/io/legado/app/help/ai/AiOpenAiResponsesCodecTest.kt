package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOpenAiResponsesCodecTest {
    @Test
    fun nativeRequestPreservesPromptOrderAndUsesResponsesFields() {
        val setting = setting("https://api.openai.com/v1")
        val codec = AiOpenAiResponsesCodec(setting, "gpt-6.2-astra", "${setting.baseUrl}/responses")
        val messages = listOf(
            message("system", "Base instructions"),
            message("user", "Original request"),
            message("system", "Follow-up instructions")
        )
        val request = codec.request(
            messages,
            listOf(toolDefinition),
            AiTextParams(maxTokens = 600, temperature = 0.1f, enableThinking = true, reasoningEffort = "medium"),
            true,
            "fixture-session"
        )
        assertEquals("https://api.openai.com/v1/responses", request.url.toString())
        assertEquals("Bearer fixture-key", request.header("Authorization"))
        val body = request.bodyJson()
        assertFalse(body.get("store").asBoolean)
        assertTrue(body.get("stream").asBoolean)
        assertFalse(body.has("messages"))
        assertFalse(body.has("temperature"))
        assertEquals(600, body.intOrNull("max_output_tokens"))
        assertEquals("medium", body.objectOrNull("reasoning")?.stringOrNull("effort"))
        assertEquals(listOf("Base instructions", "Original request", "Follow-up instructions"),
            body.items().map { it.stringOrNull("content") })
        val tool = body.arrayOrNull("tools")!!.single().asJsonObject
        assertEquals("mcp__test", tool.stringOrNull("name"))
        assertEquals("object", tool.objectOrNull("parameters")?.stringOrNull("type"))
        assertFalse(tool.get("strict").asBoolean)
        assertFalse(tool.has("function"))
    }

    @Test
    fun onlyOpenRouterSerializesAnIndependentResponsesThinkingBudget() {
        val router = AiDefaultProviders.all().single { it.id == "openrouter" }.copy(useResponsesApi = true)
        val model = AiModel(
            id = "~anthropic/claude-example",
            modelInfo = AiModelInfo(
                endpoint = router.modelConfigurationKey(),
                reasoning = AiModelValue(true),
                supportsReasoningBudget = AiModelValue(true),
                maxOutputTokens = AiModelValue(8192),
            ),
        )
        val route = router.resolveModelGenerationRoute(model)
        assertEquals(AiModelGenerationTestProtocol.RESPONSES, route.protocol)
        val budget = reasoningBudgetChoice(2048)
        val resolved = router.modelAssistantReasoning(model, null, null, modelEffort = budget)
        assertEquals(1024..8191, resolved.budgetRange)
        assertEquals(budget, resolved.selected)
        val params = resolved.applyTo(AiTextParams())
        val codec = AiOpenAiResponsesCodec(router, model.id, "/responses")
        for (stream in listOf(false, true)) {
            val request = codec.request(emptyList(), emptyList(), params, stream, null).bodyJson()
            assertEquals(8192, request.intOrNull("max_output_tokens"))
            assertEquals(objectFrom("""{"max_tokens":2048}"""), request.objectOrNull("reasoning"))
        }

        val standard = setting("https://api.openai.com/v1")
        val standardRequest = AiOpenAiResponsesCodec(standard, "gpt-5", "/responses")
            .request(emptyList(), emptyList(), params, false, null).bodyJson()
        assertFalse(standardRequest.has("reasoning"))
    }

    @Test
    fun openRouterResponsesPreservesAutoOffAndSelectedEffort() {
        val router = setting("https://openrouter.ai/api/v1")
        val codec = AiOpenAiResponsesCodec(router, "test-model", "/responses")
        fun body(params: AiTextParams) = codec.request(emptyList(), emptyList(), params, false, null).bodyJson()
        assertFalse(body(AiTextParams()).has("reasoning"))
        assertEquals(objectFrom("""{"enabled":false}"""),
            body(AiTextParams(disableThinking = true)).objectOrNull("reasoning"))
        assertEquals(objectFrom("""{"enabled":true}"""),
            body(AiTextParams(enableThinking = true)).objectOrNull("reasoning"))
        assertEquals(objectFrom("""{"effort":"high"}"""),
            body(AiTextParams(enableThinking = true, reasoningEffort = "high")).objectOrNull("reasoning"))
    }

    @Test
    fun terminalSnapshotProducesUsageAndPairedToolCallId() {
        val codec = codec()
        val reply = codec.readJson(nativeToolReply)
        reply.requireCompleted()
        assertEquals("tool_calls", reply.finishReason)
        assertEquals("working", reply.content)
        assertEquals("reason summary", reply.reasoning)
        assertEquals(AiChatUsage(12, 5, 17), reply.usage)
        assertEquals("call_pair", reply.toolCalls.single().asJsonObject.stringOrNull("id"))
        val result = objectFrom("""{"role":"tool","tool_call_id":"call_pair","name":"mcp__test","content":"done"}""")
        val body = codec.body(listOf(reply.message, result), listOf(toolDefinition))
        assertEquals(listOf("reasoning", "message", "function_call", "function_call_output"),
            body.items().map { it.stringOrNull("type") })
        assertEquals("ciphertext", body.items()[0].stringOrNull("encrypted_content"))
        assertEquals("commentary", body.items()[1].stringOrNull("phase"))
        assertEquals("call_pair", body.items()[2].stringOrNull("call_id"))
        assertEquals("done", body.items()[3].stringOrNull("output"))
    }

    @Test
    fun switchingRoutesOrSummarizingKeepsReadableToolHistory() {
        val reply = codec().readJson(nativeToolReply)
        val result = objectFrom("""{"role":"tool","tool_call_id":"call_pair","name":"mcp__test","content":"done"}""")
        val switched = codec(path = "/alternate/responses").body(listOf(reply.message, result), listOf(toolDefinition))
        assertEquals(listOf("message", "function_call", "function_call_output"),
            switched.items().map { it.stringOrNull("type") })
        assertFalse(switched.toString().contains("ciphertext"))

        val copied = reply.message.deepCopy().apply { remove(AI_NATIVE_HISTORY) }
        val summary = codec().body(listOf(copied, result), emptyList())
        assertEquals(listOf("message", "message"), summary.items().map { it.stringOrNull("type") })
        assertTrue(summary.items().last().stringOrNull("content")!!.contains("done"))
        assertFalse(summary.toString().contains("ciphertext"))
        assertFalse(summary.has("tools"))
    }

    @Test
    fun streamingDeltasAndFinishedSnapshotAgree() {
        val updates = mutableListOf<Pair<String, String?>>()
        val codec = codec()
        val response = codec.readStream(
            sse(
                """{"type":"response.reasoning_summary_text.delta","delta":"reason "}""",
                """{"type":"response.reasoning_summary_text.delta","delta":"summary"}""",
                """{"type":"response.output_text.delta","delta":"work"}""",
                """{"type":"response.output_text.delta","delta":"ing"}""",
                """{"type":"response.completed","response":$nativeToolReply}"""
            )
        ) { text, reasoning -> updates.add(text to reasoning) }
        response.requireCompleted()
        assertTrue(("work" to "reason summary") in updates)
        assertEquals("working" to "reason summary", updates.last())
        assertEquals("call_pair", response.toolCalls.single().asJsonObject.stringOrNull("id"))
    }

    @Test
    fun streamFailureAndTruncationCannotBeAcceptedAsCompleted() {
        val codec = codec()
        val error = assertThrows(IllegalStateException::class.java) {
            codec.readStream(sse("""{"type":"error","message":"gateway message"}""")) { _, _ -> }
        }
        assertEquals("gateway message", error.message)
        assertThrows(IllegalStateException::class.java) {
            codec.readStream(sse("""{"type":"response.output_text.delta","delta":"partial"}""")) { _, _ -> }
        }
        val incomplete = codec.readStream(
            sse("""{"type":"response.incomplete","response":{"incomplete_details":{"reason":"max_output_tokens"}}}""")
        ) { _, _ -> }
        assertEquals("length", incomplete.finishReason)
        assertThrows(IllegalStateException::class.java) { incomplete.requireCompleted() }
    }

    @Test
    fun proprietaryAndMalformedToolCallsCannotEnterAssistantToolLoop() {
        val codec = codec()
        assertThrows(IllegalStateException::class.java) {
            codec.readJson(objectFrom("""{"status":"completed","output":[{"type":"custom_tool_call"}]}"""))
        }
        assertThrows(IllegalStateException::class.java) {
            codec.readJson(objectFrom("""
                {"status":"completed","output":[{"type":"function_call","id":"fc_only",
                  "name":"mcp__test","arguments":"{}"}]}
            """.trimIndent()))
        }
    }

    @Test
    fun deepSeekReplaysPlainNativeReasoningWithoutEncryptedContent() {
        val setting = setting("https://api.deepseek.com")
        val codec = AiOpenAiResponsesCodec(setting, "deepseek-flash", "${setting.baseUrl}/responses")
        val reply = codec.readJson(objectFrom("""
            {"status":"completed","output":[
              {"type":"reasoning","content":[{"type":"reasoning_text","text":"thinking"}]},
              {"type":"message","role":"assistant","content":[{"type":"output_text","text":"answer"}]}
            ]}
        """.trimIndent()))
        val body = codec.body(listOf(reply.message), emptyList())
        assertEquals("thinking", reply.reasoning)
        assertEquals("reasoning", body.items()[0].stringOrNull("type"))
        assertNull(body.arrayOrNull("include"))
        assertFalse(body.items()[0].has("encrypted_content"))
    }

    private fun AiChatCodec.body(messages: List<JsonObject>, tools: List<JsonObject>): JsonObject {
        return request(messages, tools, AiTextParams(), false, null).bodyJson()
    }

    private fun okhttp3.Request.bodyJson(): JsonObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return objectFrom(buffer.readUtf8())
    }

    private fun JsonObject.items(): List<JsonObject> = arrayOrNull("input").objectParts()

    private fun codec(path: String = "/responses"): AiOpenAiResponsesCodec {
        val setting = setting("https://fixture.invalid/v1")
        return AiOpenAiResponsesCodec(setting, "fixture-model", setting.baseUrl + path)
    }

    private fun setting(base: String) =
        AiProviderSetting("fixture", AiProviderType.OPENAI, name = "fixture", apiKey = "fixture-key", baseUrl = base)

    private fun objectFrom(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    private fun sse(vararg frames: String) = frames.joinToString("") { "data: $it\n\n" }.reader().buffered()

    private val toolDefinition = objectFrom("""
        {"type":"function","function":{"name":"mcp__test","parameters":{"type":"object"}}}
    """.trimIndent())

    private val nativeToolReply = objectFrom("""
        {"status":"completed","model":"fixture-model","usage":{"input_tokens":12,"output_tokens":5,"total_tokens":17},
          "output":[
            {"type":"reasoning","id":"rs_1","encrypted_content":"ciphertext",
              "summary":[{"type":"summary_text","text":"reason summary"}]},
            {"type":"message","id":"msg_1","role":"assistant","phase":"commentary",
              "content":[{"type":"output_text","text":"working"}]},
            {"type":"function_call","id":"fc_item","call_id":"call_pair","name":"mcp__test","arguments":"{}"}
        ]}
    """.trimIndent())
}
