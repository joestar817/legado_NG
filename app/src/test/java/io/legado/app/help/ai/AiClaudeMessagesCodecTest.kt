package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClaudeMessagesCodecTest {
    private val setting = AiProviderSetting(
        id = "claude-fixture",
        type = AiProviderType.CLAUDE,
        name = "Claude fixture",
        apiKey = "fixture-key",
        baseUrl = "https://fixture.invalid/v1",
        apiKeyOrigin = "https://fixture.invalid/",
    )
    private val model = "claude-sonnet-4-6"
    private val user = json("""{"role":"user","content":"查询书名"}""")
    private val tool = json("""{"type":"function","function":{"name":"book_search","parameters":{"type":"object"}}}""")
    private val toolResult = json("""{"role":"tool","tool_call_id":"toolu_1","name":"book_search","content":"查到了"}""")

    @Test
    fun `sends native URL headers system and required token budget`() {
        val codec = AiClaudeMessagesCodec(setting, model)
        val request = codec.request(
            listOf(json("""{"role":"system","content":"系统提示"}"""), user),
            listOf(tool),
            AiTextParams(),
            false,
            null,
        )
        val body = request.jsonBody()
        assertEquals("https://fixture.invalid/v1/messages", request.url.toString())
        assertEquals("fixture-key", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        assertNull(request.header("Authorization"))
        assertEquals(model, body.get("model").asString)
        assertEquals(8192, body.get("max_tokens").asInt)
        assertFalse(body.get("stream").asBoolean)
        assertEquals("系统提示", body.get("system").asString)
        assertEquals(1, body.getAsJsonArray("messages").size())
        assertEquals("object", body.getAsJsonArray("tools")[0].asJsonObject
            .getAsJsonObject("input_schema").get("type").asString)
        assertThrows(AiEndpointException::class.java) {
            AiClaudeMessagesCodec(setting.copy(baseUrl = "https://other.invalid/v1"), model)
                .request(listOf(user), emptyList(), AiTextParams(), false, null)
        }
    }

    @Test
    fun `assistant sends the enabled Claude effort and can explicitly disable thinking`() {
        val source = setting.modelConfigurationKey()
        val info = readModelInfo(json("""
            {"capabilities":{
              "thinking":{"supported":true,"types":{"adaptive":{"supported":true},"disabled":{"supported":true}}},
              "effort":{"medium":{"supported":false},"high":{"supported":true},"max":{"supported":true}}
            }}
        """), source, AiModelCatalogFormat.CLAUDE)
        val sonnet = AiModel(
            model,
            modelInfo = info,
            modelSettings = AiModelSettings(source, enabledReasoningEfforts = listOf("max", "medium")),
        ).withCatalogCapabilities(info)
        val codec = AiClaudeMessagesCodec(setting, model)

        val selected = setting.modelAssistantReasoning(sonnet, null, null, modelEffort = "max")
        assertEquals(listOf("off", "auto", "max"), selected.options)
        assertEquals("max", selected.selected)
        val body = codec.request(listOf(user), emptyList(), selected.applyTo(AiTextParams()), true, null)
            .jsonBody()
        assertEquals("adaptive", body.getAsJsonObject("thinking").get("type").asString)
        assertEquals("max", body.getAsJsonObject("output_config").get("effort").asString)

        val disabled = setting.modelAssistantReasoning(sonnet, null, null, modelEffort = "off")
        val disabledBody = codec.request(listOf(user), emptyList(), disabled.applyTo(AiTextParams()), false, null)
            .jsonBody()
        assertEquals("disabled", disabledBody.getAsJsonObject("thinking").get("type").asString)
        assertFalse(disabledBody.has("output_config"))
    }

    @Test
    fun `Claude catalog can expose reasoning for an unrecognized model alias`() {
        val alias = "claude-research-alias"
        assertFalse(setting.modelAssistantReasoning(AiModel(alias), null, null).hasControls)
        val info = readModelInfo(json("""
            {"capabilities":{
              "thinking":{"supported":true,"types":{"adaptive":{"supported":true},"disabled":{"supported":false}}},
              "effort":{"high":{"supported":true}}
            }}
        """), setting.modelConfigurationKey(), AiModelCatalogFormat.CLAUDE)
        val discovered = AiModel(alias, modelInfo = info).withCatalogCapabilities(info)
        val selected = setting.modelAssistantReasoning(discovered, null, null, modelEffort = "high")
        assertEquals(listOf("auto", "high"), selected.options)
        assertEquals("high", selected.selected)
        val body = AiClaudeMessagesCodec(setting, alias, modelInfo = info)
            .request(listOf(user), emptyList(), selected.applyTo(AiTextParams()), false, null)
            .jsonBody()
        assertEquals("adaptive", body.getAsJsonObject("thinking").get("type").asString)
        assertEquals("high", body.getAsJsonObject("output_config").get("effort").asString)
    }

    @Test
    fun `assistant sends a Claude manual budget below the default output ceiling`() {
        val opus = AiModel("claude-opus-4-5-20251101")
        val choice = reasoningBudgetChoice(4321)
        val selected = setting.modelAssistantReasoning(opus, null, null, modelEffort = choice)
        assertEquals(1024..8191, selected.budgetRange)
        assertEquals(choice, selected.selected)
        val body = AiClaudeMessagesCodec(setting, opus.id)
            .request(listOf(user), emptyList(), selected.applyTo(AiTextParams()), false, null)
            .jsonBody()
        val thinking = body.getAsJsonObject("thinking")
        assertEquals("enabled", thinking.get("type").asString)
        assertEquals(4321, thinking.get("budget_tokens").asInt)
        assertTrue(thinking.get("budget_tokens").asInt < body.get("max_tokens").asInt)
    }

    @Test
    fun `native thinking signature and streaming tool arguments replay with immediate result`() {
        val codec = AiClaudeMessagesCodec(setting, model)
        val shown = mutableListOf<Pair<String, String?>>()
        val completion = codec.readStream(events(
            """{"type":"message_start","message":{"model":"$model","usage":{"input_tokens":10,"cache_read_input_tokens":7}}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"look"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"opaque"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"book_search","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"q\":"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"book\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":6}}""",
            """{"type":"message_stop"}""",
        )) { text, thinking -> shown += text to thinking }

        assertEquals(listOf("" to "look"), shown)
        assertEquals("tool_calls", completion.finishReason)
        assertEquals(23, completion.usage?.totalTokens)
        val request = codec.request(
            listOf(user, completion.message, toolResult, user),
            listOf(tool),
            AiTextParams(enableThinking = true, reasoningEffort = "max"),
            true,
            "fixture-chat",
        ).jsonBody()
        assertTrue(request.get("stream").asBoolean)
        assertEquals("adaptive", request.getAsJsonObject("thinking").get("type").asString)
        assertEquals("max", request.getAsJsonObject("output_config").get("effort").asString)
        val turns = request.getAsJsonArray("messages")
        val assistant = turns[1].asJsonObject.getAsJsonArray("content")
        assertEquals("opaque", assistant[0].asJsonObject.get("signature").asString)
        assertEquals("book", assistant[1].asJsonObject.getAsJsonObject("input").get("q").asString)
        val toolReply = turns[2].asJsonObject.getAsJsonArray("content")
        assertEquals("tool_result", toolReply[0].asJsonObject.get("type").asString)
        assertEquals("toolu_1", toolReply[0].asJsonObject.get("tool_use_id").asString)
        assertEquals("text", toolReply[1].asJsonObject.get("type").asString)
    }

    @Test
    fun `summary and another provider use text projection rather than opaque native blocks`() {
        val codec = AiClaudeMessagesCodec(setting, model)
        val completion = codec.readJson(json("""
            {"stop_reason":"tool_use","content":[
              {"type":"thinking","thinking":"draft","signature":"opaque"},
              {"type":"tool_use","id":"toolu_1","name":"book_search","input":{}}
            ]}
        """))
        val turns = listOf(user, completion.message, toolResult, user)
        val summary = codec.request(turns, emptyList(), AiTextParams(), false, null).jsonBody()
        assertFalse(summary.has("tools"))
        assertFalse(summary.toString().contains("opaque"))
        assertFalse(summary.toString().contains("tool_result"))
        assertTrue(summary.toString().contains("查到了"))
        val other = AiClaudeMessagesCodec(setting.copy(id = "claude-alternate"), model)
            .request(turns, listOf(tool), AiTextParams(), false, null).jsonBody()
        assertFalse(other.toString().contains("opaque"))
        assertFalse(other.toString().contains("tool_result"))
        val incomplete = codec.request(listOf(user, completion.message, user), listOf(tool), AiTextParams(), false, null)
            .jsonBody()
        assertFalse(incomplete.toString().contains("tool_use"))
    }

    @Test
    fun `streaming error and EOF leave partial reply unsuccessful`() {
        val codec = AiClaudeMessagesCodec(setting, model)
        val first = """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""
        val fragment = """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"partial"}}"""
        val shown = mutableListOf<String>()
        val error = assertThrows(IllegalStateException::class.java) {
            codec.readStream(events(first, fragment, """{"type":"error","error":{"message":"overloaded"}}""")) {
                    value, _ -> shown += value
            }
        }
        assertEquals("overloaded", error.message)
        assertEquals(listOf("partial"), shown)
        assertThrows(IllegalStateException::class.java) {
            codec.readStream(events(first, fragment)) { _, _ -> }
        }
        val truncated = codec.readJson(json("""{"stop_reason":"max_tokens","content":[{"type":"text","text":"partial"}]}"""))
        assertEquals("length", truncated.finishReason)
        assertEquals("partial", truncated.content)
    }

    @Test
    fun `mandatory thinking models omit disabled mode and temperature`() {
        val codec = AiClaudeMessagesCodec(setting, "claude-opus-5-5")
        val body = codec.request(
            listOf(user), emptyList(), AiTextParams(disableThinking = true, temperature = 0f), false, null,
        ).jsonBody()
        assertFalse(body.has("thinking"))
        assertFalse(body.has("temperature"))
    }

    @Test
    fun `preserved thinking needs the same prefix while paired parallel tool results remain valid`() {
        val model = "claude-opus-5-5"
        val system = json("""{"role":"system","content":"原提示"}""")
        val initial = listOf(system, user)
        val codec = AiClaudeMessagesCodec(setting, model)
        codec.request(initial, listOf(tool), AiTextParams(), false, null)
        val reply = codec.readJson(json("""
            {"stop_reason":"tool_use","content":[
              {"type":"thinking","thinking":"draft","signature":"opaque"},
              {"type":"tool_use","id":"toolu_1","name":"book_search","input":{}},
              {"type":"tool_use","id":"toolu_2","name":"book_search","input":{}}
            ]}
        """))
        val secondResult = toolResult.deepCopy().apply { addProperty("tool_call_id", "toolu_2") }
        val activity = listOf(reply.message, toolResult, secondResult)
        val same = AiClaudeMessagesCodec(setting, model).request(
            initial + activity, listOf(tool), AiTextParams(), false, null,
        ).jsonBody()
        assertTrue(same.toString().contains("opaque"))
        val editedSystem = system.deepCopy().apply { addProperty("content", "修改后的提示") }
        val edited = AiClaudeMessagesCodec(setting, model).request(
            listOf(editedSystem, user) + activity, listOf(tool), AiTextParams(), false, null,
        ).jsonBody()
        assertFalse(edited.toString().contains("opaque"))
        val projected = edited.getAsJsonArray("messages")
        assertEquals(2, projected[1].asJsonObject.getAsJsonArray("content").size())
        assertEquals(2, projected[2].asJsonObject.getAsJsonArray("content").size())
        val incomplete = AiClaudeMessagesCodec(setting, model).request(
            initial + activity.dropLast(1), listOf(tool), AiTextParams(), false, null,
        ).jsonBody()
        assertFalse(incomplete.toString().contains("tool_use"))
        assertFalse(incomplete.toString().contains("tool_result"))
    }

    private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject

    private fun Request.jsonBody(): JsonObject = json(Buffer().apply { body!!.writeTo(this) }.readUtf8())

    private fun events(vararg frames: String) =
        frames.joinToString(separator = "", transform = { "data: $it\n\n" }).reader().buffered()
}
