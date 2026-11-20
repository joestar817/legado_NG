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

class AiGeminiContentCodecTest {
    @Test
    fun buildsNativeRequestsAndUsesBoundKeyHeader() {
        val codec = codec("models/gemini-3.8-flash")
        val messages = listOf(message("system", "Stay concise"), message("user", "Summarize"))
        val params = AiTextParams(maxTokens = 400, enableThinking = true, reasoningEffort = "medium")
        val ordinary = codec.request(messages, tools, params, false, "conversation")
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", ordinary.url.encodedPath)
        assertEquals("fixture-key", ordinary.header("x-goog-api-key"))
        assertNull(ordinary.header("Authorization"))
        assertNull(ordinary.url.query)
        val body = ordinary.json()
        assertEquals("Stay concise", body.getAsJsonObject("systemInstruction")
            .getAsJsonArray("parts")[0].asJsonObject.get("text").asString)
        assertEquals("Summarize", body.getAsJsonArray("contents")[0].asJsonObject
            .getAsJsonArray("parts")[0].asJsonObject.get("text").asString)
        val config = body.getAsJsonObject("generationConfig")
        assertEquals(400, config.get("maxOutputTokens").asInt)
        assertEquals("MEDIUM", config.getAsJsonObject("thinkingConfig").get("thinkingLevel").asString)
        assertEquals("lookup", body.getAsJsonArray("tools")[0].asJsonObject
            .getAsJsonArray("functionDeclarations")[0].asJsonObject.get("name").asString)

        val streaming = codec.request(messages, emptyList(), AiTextParams(), true, null)
        assertEquals("/v1beta/models/gemini-3.8-flash:streamGenerateContent", streaming.url.encodedPath)
        assertEquals("sse", streaming.url.queryParameter("alt"))
        assertFalse(streaming.json().has("generationConfig"))
        assertFalse(streaming.json().has("tools"))
        assertThrows(AiEndpointException::class.java) {
            AiGeminiContentCodec(setting.copy(apiKeyOrigin = "https://other.example:443"), "gemini-3.8-flash")
                .request(messages, emptyList(), AiTextParams(), false, null)
        }
    }

    @Test
    fun `assistant sends a Gemini level and avoids offering Off when thinking cannot be disabled`() {
        val model = thinkingModel("gemini-3-pro-preview")
        val selected = setting.modelAssistantReasoning(model, null, null, modelEffort = "high")
        assertEquals(listOf("auto", "low", "high"), selected.options)
        assertEquals("high", selected.selected)
        assertEquals("HIGH", assistantThinking(model, selected)?.get("thinkingLevel")?.asString)

        val unsupported = setting.modelAssistantReasoning(model, null, null, modelEffort = "off")
        assertEquals("auto", unsupported.selected)
        assertNull(assistantThinking(model, unsupported))
    }

    @Test
    fun `assistant sends the Gemini Flash budget and uses zero when thinking is disabled`() {
        val model = thinkingModel("gemini-2.5-flash")
        val choice = reasoningBudgetChoice(4321)
        val selected = setting.modelAssistantReasoning(model, null, null, modelEffort = choice)
        assertEquals(1..24576, selected.budgetRange)
        assertEquals(choice, selected.selected)
        assertEquals(4321, assistantThinking(model, selected)?.get("thinkingBudget")?.asInt)

        val disabled = setting.modelAssistantReasoning(model, null, null, modelEffort = "off")
        assertEquals("off", disabled.selected)
        val thinking = assistantThinking(model, disabled)
        assertEquals(0, thinking?.get("thinkingBudget")?.asInt)
        assertEquals(false, thinking?.get("includeThoughts")?.asBoolean)
    }

    @Test
    fun `assistant validates Gemini Pro and Flash Lite budget bounds per model`() {
        val pro = thinkingModel("gemini-2.5-pro")
        val invalidChoice = reasoningBudgetChoice(127)
        val rejected = setting.modelAssistantReasoning(pro, null, null, modelEffort = invalidChoice)
        assertEquals(128..32768, rejected.budgetRange)
        assertFalse("off" in rejected.options)
        assertEquals("auto", rejected.selected)
        assertNull(assistantThinking(pro, rejected))

        val lite = thinkingModel("gemini-2.5-flash-lite")
        val liteMin = reasoningBudgetChoice(512)
        val selected = setting.modelAssistantReasoning(lite, null, null, modelEffort = liteMin)
        assertEquals(512..24576, selected.budgetRange)
        assertEquals(liteMin, selected.selected)
        assertEquals(512, assistantThinking(lite, selected)?.get("thinkingBudget")?.asInt)
    }

    @Test
    fun replaysSignedParallelCallsAndConvertsHistoricalToolUseToTextForCompression() {
        val codec = codec()
        val reply = codec.readJson(json("""
            {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
              {"functionCall":{"id":"source-id","name":"lookup","args":{"query":"one"}},"thoughtSignature":"opaque"},
              {"functionCall":{"name":"lookup","args":{"query":"two"}}}
            ]}}]}
        """))
        assertEquals("tool_calls", reply.finishReason)
        val calls = reply.message.getAsJsonArray("tool_calls")
        assertEquals("source-id", calls[0].asJsonObject.get("id").asString)
        val secondId = calls[1].asJsonObject.get("id").asString
        assertTrue(secondId.startsWith("gemini_"))
        val messages = listOf(
            message("user", "Find both"),
            reply.message,
            toolResponse(secondId, "two"),
            toolResponse("source-id", """{"name":"one"}"""),
        )
        val next = codec.request(messages, tools, AiTextParams(), false, "conversation").json()
            .getAsJsonArray("contents")
        val model = next[1].asJsonObject.getAsJsonArray("parts")
        assertEquals("opaque", model[0].asJsonObject.get("thoughtSignature").asString)
        assertFalse(model[1].asJsonObject.has("thoughtSignature"))
        val results = next[2].asJsonObject.getAsJsonArray("parts")
        assertEquals(2, results.size())
        assertEquals("source-id", results[0].asJsonObject.getAsJsonObject("functionResponse").get("id").asString)
        assertFalse(results[1].asJsonObject.getAsJsonObject("functionResponse").has("id"))
        assertEquals("two", results[1].asJsonObject.getAsJsonObject("functionResponse")
            .getAsJsonObject("response").get("result").asString)

        val summary = codec.request(messages, emptyList(), AiTextParams(), false, "conversation").json()
        assertFalse(summary.has("tools"))
        assertFalse(summary.toString().contains("thoughtSignature"))
        assertFalse(summary.toString().contains("functionResponse"))
        assertTrue(summary.toString().contains("lookup"))
        assertTrue(summary.toString().contains("one"))
        assertTrue(summary.toString().contains("two"))

        val changedEndpoint = AiGeminiContentCodec(
            setting.copy(baseUrl = "https://other.example/v1beta"), "gemini-3.8-flash",
        ).request(messages, tools, AiTextParams(), false, null).json()
        assertFalse(changedEndpoint.toString().contains("thoughtSignature"))
        assertFalse(changedEndpoint.toString().contains("functionResponse"))
    }

    @Test
    fun projectsIncompleteOrNonAdjacentNativeToolGroupsAsText() {
        val codec = codec()
        val reply = codec.readJson(json("""
            {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
              {"functionCall":{"id":"first","name":"lookup","args":{"query":"one"}},"thoughtSignature":"opaque"},
              {"functionCall":{"id":"second","name":"lookup","args":{"query":"two"}}}
            ]}}]}
        """))
        val firstResult = toolResponse("first", "surviving result")
        val secondResult = toolResponse("second", "second result")
        val invalidBatches = listOf(
            listOf(firstResult),
            listOf(firstResult, message("user", "Continue"), secondResult),
            listOf(firstResult, firstResult),
            listOf(firstResult, secondResult.deepCopy().apply { addProperty("name", "different") }),
        )
        invalidBatches.forEach { batch ->
            val messages = listOf(message("user", "Find both"), reply.message) + batch
            val contents = codec.request(messages, tools, AiTextParams(), false, null).json()
                .getAsJsonArray("contents").toString()
            assertFalse(contents.contains("thoughtSignature"))
            assertFalse(contents.contains("functionCall"))
            assertFalse(contents.contains("functionResponse"))
            assertTrue(contents.contains("surviving result"))
            assertTrue(contents.contains("lookup"))
        }
    }

    @Test
    fun keepsEmptyFinalSignaturePartAndMergesUsageAcrossFrames() {
        val updates = mutableListOf<Pair<String, String?>>()
        val codec = codec()
        val sse = """
            data: {"modelVersion":"gemini-3.8-flash","candidates":[{"content":{"parts":[{"text":"Inspecting","thought":true}]}}],"usageMetadata":{"promptTokenCount":12}}

            data: {"candidates":[{"content":{"parts":[{"text":"Found it."}]}}],"usageMetadata":{"candidatesTokenCount":3}}

            data: {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"","thoughtSignature":"tail"}]}}],"usageMetadata":{"thoughtsTokenCount":4,"totalTokenCount":19}}
        """.trimIndent()
        val reply = codec.readStream(sse.reader().buffered()) { text, reasoning -> updates += text to reasoning }
        assertEquals(listOf("" to "Inspecting", "Found it." to "Inspecting"), updates)
        assertEquals("stop", reply.finishReason)
        assertEquals(12, reply.usage?.promptTokens)
        assertEquals(7, reply.usage?.completionTokens)
        assertEquals(19, reply.usage?.totalTokens)
        val messages = listOf(message("user", "Find it"), reply.message, message("user", "Continue"))
        val parts = codec.request(messages, emptyList(), AiTextParams(), false, null).json()
            .getAsJsonArray("contents")[1].asJsonObject.getAsJsonArray("parts")
        assertEquals(3, parts.size())
        assertEquals("tail", parts[2].asJsonObject.get("thoughtSignature").asString)
        assertEquals("", parts[2].asJsonObject.get("text").asString)
        assertFalse(parts[1].asJsonObject.has("thoughtSignature"))
    }

    @Test
    fun distinguishesTruncationBlockingAndServerErrorsFromACompletedReply() {
        val codec = codec()
        val length = codec.readJson(json("""
            {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"partial"}]}}]}
        """))
        assertEquals("length", length.finishReason)
        val blocked = codec.readJson(json("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals("content_filter", blocked.finishReason)
        val data = """data: {"candidates":[{"content":{"parts":[{"text":"partial"}]}}]}"""
        assertThrows(IllegalStateException::class.java) {
            codec.readStream("$data\n\n".reader().buffered()) { _, _ -> }
        }
        val error = """data: {"error":{"message":"quota exceeded"}}"""
        assertEquals("quota exceeded", assertThrows(IllegalStateException::class.java) {
            codec.readStream("$data\n\n$error\n\n".reader().buffered()) { _, _ -> }
        }.message)
    }

    @Test
    fun disabledThinkingUsesOnlyModelAcceptedControls() {
        val params = AiTextParams(disableThinking = true)
        fun thinking(modelId: String): JsonObject? = codec(modelId)
            .request(listOf(message("user", "Summarize")), emptyList(), params, false, null).json()
            .objectOrNull("generationConfig")?.objectOrNull("thinkingConfig")
        assertEquals(0, thinking("gemini-2.5-flash")?.get("thinkingBudget")?.asInt)
        assertEquals(0, thinking("gemini-2.5-flash-lite")?.get("thinkingBudget")?.asInt)
        assertNull(thinking("gemini-2.5-pro"))
        assertNull(thinking("gemini-3.6-flash"))
        assertNull(thinking("gemini-3.8-flash"))
        assertNull(thinking("gemini-4-hydrogen"))
        assertNull(thinking("gemini-2.0-flash"))
    }

    private val setting = AiProviderSetting(
        id = "gemini-fixture",
        type = AiProviderType.GOOGLE,
        name = "Gemini fixture",
        baseUrl = "https://gemini.example/v1beta",
        apiKey = "fixture-key",
    )

    private val tools = listOf(json("""
        {"type":"function","function":{"name":"lookup","description":"Find a record",
        "parameters":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}}
    """))

    private fun codec(id: String = "gemini-3.8-flash") = AiGeminiContentCodec(setting, id)

    private fun thinkingModel(id: String): AiModel {
        val info = readModelInfo(
            json("""{"thinking":true}"""), setting.modelConfigurationKey(), AiModelCatalogFormat.GEMINI,
        )
        return AiModel(id, modelInfo = info).withCatalogCapabilities(info)
    }

    private fun assistantThinking(model: AiModel, selection: AiAssistantReasoning): JsonObject? =
        codec(model.id).request(
            listOf(message("user", "Summarize")), emptyList(), selection.applyTo(AiTextParams()), false, null,
        ).json().objectOrNull("generationConfig")?.objectOrNull("thinkingConfig")

    private fun message(role: String, text: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", text)
    }

    private fun toolResponse(id: String, text: String) = message("tool", text).apply {
        addProperty("tool_call_id", id)
        addProperty("name", "lookup")
    }

    private fun Request.json(): JsonObject = json(Buffer().apply { requireNotNull(body).writeTo(this) }.readUtf8())

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject
}
