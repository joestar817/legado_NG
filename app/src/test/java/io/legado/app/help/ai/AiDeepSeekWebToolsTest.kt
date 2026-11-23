package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDeepSeekWebToolsTest {
    private val official = AiDefaultProviders.all().single { it.id == "deepseek" }

    @Test
    fun nativeCapabilityFollowsTheOfficialEndpointModelAndProtocol() {
        val flash = AiModel("deepseek-flash")
        val pro = AiModel("deepseek-v4-pro")
        val both = setOf(AiWebTool.SEARCH, AiWebTool.READ_LINK)
        assertEquals(both, official.deepSeekWebRoute(flash)?.supported)
        assertEquals(both, official.deepSeekWebRoute(AiModel("deepseek-v4-flash"))?.supported)
        assertEquals(both, official.deepSeekWebRoute(AiModel("deepseek-v4-flash-vision-exp"))?.supported)
        assertNull(official.deepSeekWebRoute(pro))
        assertNull(official.deepSeekWebRoute(AiModel("deepseek-chat")))
        assertNull(official.deepSeekWebRoute(flash.copy(type = AiModelType.EMBEDDING)))

        val editable = AiModelSettings(
            official.modelConfigurationKey(),
            interfacePreference = AiModelInterfacePreference.RESPONSES,
        )
        val proResponses = official.deepSeekWebRoute(pro.copy(modelSettings = editable))!!
        assertEquals(setOf(AiWebTool.SEARCH), proResponses.enabled)
        val plan = proResponses.plan(true, "", false) as AiWebToolPlan.Native
        assertEquals("DeepSeek", plan.sourceName)
        assertEquals(listOf("web_search"), plan.types())
        assertEquals(both, official.deepSeekWebRoute(flash.copy(modelSettings = editable))?.enabled)

        val senseTime = AiDefaultProviders.all().single { it.id == "sensenova" }
        assertNull(senseTime.deepSeekWebRoute(flash))
        assertNull(official.copy(baseUrl = "https://api.deepseek.com.example.org").deepSeekWebRoute(flash))
        val proxy = AiProviderSetting(
            id = "fixture", name = "Proxy", type = AiProviderType.OPENAI,
            builtIn = false, baseUrl = official.baseUrl,
            responsesApiPath = "https://proxy.example.org/responses", useResponsesApi = true,
        )
        assertNull(proxy.deepSeekWebRoute(flash))
        assertNull(official.copy(chatCompletionsPath = "/beta/chat/completions").deepSeekWebRoute(flash))
    }

    @Test
    fun linkReadingTakesPriorityWithThinkingAndModelOverridesRemainScoped() {
        val model = AiModel("deepseek-flash")
        val route = official.deepSeekWebRoute(model)!!
        val text = "请总结这篇书评（https://example.com/article）。"
        assertEquals(listOf("deepseek_read_url"), route.plan(true, text, false).types())
        assertEquals(listOf("deepseek_web_search", "deepseek_read_url"), route.plan(true, text, true).types())
        assertEquals(listOf("deepseek_web_search"), route.plan(true, "有没有新版？", false).types())
        assertEquals(listOf("deepseek_web_search"), route.plan(true, "https:// 不是网址", false).types())
        assertTrue(route.plan(false, text, false).types().isEmpty())

        val disabledSearch = AiModelSettings(official.modelConfigurationKey(), nativeWebSearch = false)
        val onlyRead = official.deepSeekWebRoute(model.copy(modelSettings = disabledSearch))!!
        assertEquals(setOf(AiWebTool.READ_LINK), onlyRead.enabled)
        assertTrue(onlyRead.plan(true, "请推荐", false).types().isEmpty())
        assertEquals(listOf("deepseek_read_url"), onlyRead.plan(true, text, false).types())
        assertEquals(route.enabled, official.deepSeekWebRoute(model.copy(
            modelSettings = disabledSearch.copy(catalogEndpoint = "other-source"),
        ))?.enabled)
    }

    @Test
    fun sourceDisplayKeepsCitationsAndActualReadFailuresWithSafeLinks() {
        val updates = mutableListOf<AiWebActivity>()
        val events = AiNativeWebEvents(updates::add)
        events.chatProgress(obj("""{"type":"call_started","call":{"id":"rl","kind":"read_link",
            "action":{"type":"read_link","url":"https://example.com/unavailable"}}}"""))
        assertEquals(AiWebActivityAction.READ, updates.last().activeAction)
        events.chatResult(obj("""{
            "server_tool_calls":[{"calls":[{"id":"rl","kind":"read_link","status":"failed",
              "action":{"url":"https://example.com/unavailable"},"error":{"code":"url_not_accessible"}},
              {"id":"s","kind":"search","status":"completed","sources":[{"url":"https://example.com/uncited"}]}]}],
            "annotations":[{"type":"url_citation","url_citation":{"url":"https://example.com/cited","title":"Cited"}},
              {"type":"url_citation","url_citation":{"url":"javascript:alert(1)","title":"Unsafe"}}]
        }"""))
        events.finishResponse()
        assertEquals(listOf("https://example.com/unavailable"), events.settled?.failedLinks)
        assertEquals(listOf(AiWebSource("https://example.com/cited", "Cited")), events.settled?.sources)
        assertNull(updates.last().activeAction)
    }

    @Test
    fun responsesCodecStreamsAndReplaysReadSignatureWithoutRequiringToolsOnNextTurn() {
        val active = mutableListOf<AiWebActivity>()
        val events = AiNativeWebEvents(active::add)
        val tool = obj("""{"type":"deepseek_read_url"}""")
        val codec = AiOpenAiResponsesCodec(
            official, "deepseek-flash", "/responses", deepSeekWeb = true, nativeTools = listOf(tool), webEvents = events,
        )
        val start = message("user", "https://example.com/a")
        val request = codec.request(listOf(start), listOf(functionTool), AiTextParams(), true, null).jsonBody()
        assertEquals(listOf("function", "deepseek_read_url"), request.arrayOrNull("tools").objectParts().types())
        val readCall = """{"type":"web_search_call","id":"1","kind":"read_link","status":"completed",
            "action":{"type":"open_page","url":"https://example.com/a"},
            "page":{"url":"https://example.com/a","title":"Example"},"signature":"signed-page"}""".onOneLine()
        val done = """{"type":"response.completed","response":{"status":"completed","output":[$readCall,
            {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Read."}]}]}}""".onOneLine()
        val reply = codec.readStream(sse(
            """{"type":"response.output_item.added","item":{"type":"web_search_call","kind":"read_link","id":"1"}}""",
            """{"type":"response.web_search_call.searching","item_id":"1"}""",
            """{"type":"response.output_text.delta","delta":"Read."}""",
            done,
        ).reader().buffered()) { _, _ -> }
        assertTrue(active.any { it.activeAction == AiWebActivityAction.READ })
        assertEquals(listOf(AiWebSource("https://example.com/a", "Example")), events.settled?.sources)
        assertTrue(reply.toolCalls.isEmpty)

        val followUp = listOf(start, reply.message, message("user", "What else?"))
        val off = AiOpenAiResponsesCodec(official, "deepseek-flash", "/responses", deepSeekWeb = true)
            .request(followUp, emptyList(), AiTextParams(), false, null).jsonBody()
        assertFalse(off.has("tools"))
        assertTrue(off.toString().contains("signed-page"))
        val otherKey = AiOpenAiResponsesCodec(official.copy(apiKey = "different"), "deepseek-flash", "/responses", true)
            .request(followUp, emptyList(), AiTextParams(), false, null).jsonBody()
        assertFalse(otherKey.toString().contains("signed-page"))
    }

    @Test
    fun messagesCodecUsesNativeDeclarationsAndPairsFetchResultById() {
        val provider = AiProviderSetting(
            id = "official-messages", name = "DeepSeek Messages", type = AiProviderType.CLAUDE,
            builtIn = false, baseUrl = "https://api.deepseek.com/anthropic/v1",
        )
        val route = provider.deepSeekWebRoute(AiModel("deepseek-flash"))!!
        val plan = route.plan(true, "https://example.com/page", false) as AiWebToolPlan.Native
        val events = AiNativeWebEvents { }
        val codec = AiClaudeMessagesCodec(
            provider, "deepseek-flash", deepSeekWeb = true, nativeTools = plan.definitions, webEvents = events,
        )
        val start = message("user", "https://example.com/page")
        val tool = codec.request(listOf(start), emptyList(), AiTextParams(), false, null)
            .jsonBody().arrayOrNull("tools").objectParts().single()
        assertEquals("deepseek_read_url", tool.stringOrNull("name"))
        assertEquals("deepseek_read_url", tool.stringOrNull("type"))
        val reply = codec.readJson(obj("""{
            "stop_reason":"end_turn","content":[
              {"type":"server_tool_use","id":"1","name":"deepseek_read_url","kind":"read_link",
                "input":{"action":"read_link","url":"https://example.com/page"}},
              {"type":"web_fetch_tool_result","tool_use_id":"1","kind":"read_link","signature":"messages-signature",
                "content":{"type":"web_fetch_result","url":"https://example.com/page","content":{"title":"Page"}}},
              {"type":"text","text":"Read."}]
        }"""))
        events.finishResponse()
        assertEquals(listOf(AiWebSource("https://example.com/page", "Page")), events.settled?.sources)
        assertTrue(reply.toolCalls.isEmpty)
        val followUp = AiClaudeMessagesCodec(provider, "deepseek-flash", deepSeekWeb = true).request(
            listOf(start, reply.message, message("user", "Continue.")), emptyList(), AiTextParams(), false, null,
        ).jsonBody()
        assertFalse(followUp.has("tools"))
        assertTrue(followUp.toString().contains("messages-signature"))
    }

    @Test
    fun oldHistoryAndNewSourcesUseStableJsonFields() {
        val gson = Gson()
        val old = gson.fromJson("""{"id":"1","title":"test","messages":[],"web_enabled":false}""", AiChatSessionSnapshot::class.java)
        assertEquals("1", old.id)
        assertFalse(JsonParser.parseString(gson.toJson(old)).asJsonObject.has("web_enabled"))
        val activity = AiWebActivity(sources = listOf(AiWebSource("https://example.com/a", "Page")))
        val snapshot = AiChatMessageSnapshot("1", "assistant", "Reply", webActivity = activity)
        val json = gson.toJson(snapshot)
        assertTrue(json.contains("\"web_activity\""))
        assertEquals(activity, gson.fromJson(json, AiChatMessageSnapshot::class.java).webActivity)
    }

    private fun AiWebToolPlan.types() = (this as? AiWebToolPlan.Native)?.definitions.orEmpty().types()
    private fun List<JsonObject>.types() = map { it.stringOrNull("type") }
    private fun obj(text: String) = JsonParser.parseString(text).asJsonObject
    private fun message(role: String, text: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", text)
    }
    private fun String.onOneLine() = trimIndent().replace("\n", "")
    private fun sse(vararg frames: String) = frames.joinToString("\n\n", postfix = "\n\n") { "data: $it" }
    private fun Request.jsonBody() = obj(Buffer().apply { body!!.writeTo(this) }.readUtf8())
    private val functionTool = obj("""{"type":"function","function":{"name":"fixture","parameters":{"type":"object"}}}""")
}
