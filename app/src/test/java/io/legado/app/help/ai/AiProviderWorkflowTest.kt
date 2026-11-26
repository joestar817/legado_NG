package io.legado.app.help.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.legado.app.ui.config.withModelSelection
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiProviderWorkflowTest {
    @Test
    fun purificationRejectsATruncatedPrefixBeforeProducingAReplacement() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val reply = """{"choices":[{"message":{"role":"assistant","content":"甲走进街角。"},"finish_reason":"length"}]}"""
                .toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        server.start()
        try {
            val text = AiModel("fixture", inputModalities = listOf(AiModelModality.TEXT),
                outputModalities = listOf(AiModelModality.TEXT))
            val provider = AiProviderSetting(
                id = "custom_purify_fixture",
                name = "Fixture",
                type = AiProviderType.OPENAI,
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "fixture",
                builtIn = false,
                enabled = true,
            ).withFetchedModels(listOf(text))
            AiProviderStore.saveProviders(listOf(provider))
            AiConfig.savePurifyModel(provider.id, text.id)
            val error = runCatching { AiPurifyHelper.purify("甲走进街角。乙仍在门外等候。") }.exceptionOrNull()
            assertEquals(AiAssistantReplyEndReason.TRUNCATED, (error as? AiAssistantReplyEndException)?.reason)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun bookSearchWithoutAUsableRouteStopsBeforeCallingTheModel() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.injectAsAppCtx()
        val provider = AiDefaultProviders.all().single { it.id == "xiaomi_mimo" }
            .copy(apiKey = "fixture").withFetchedModels(listOf(AiModelRegistry.enrich(AiModel("mimo-v2.6-flash"))))
        AiProviderStore.saveProviders(listOf(provider))
        assertTrue(AiConfig.saveAssistantModel(provider.id, "mimo-v2.6-flash"))
        val requests = AtomicInteger()
        val assistant = AiChatClient {
            OkHttpClient.Builder().addInterceptor {
                requests.incrementAndGet()
                error("This blocked turn must not reach the network")
            }.build()
        }
        val settingsBefore = AiExternalSearchStore.current()
        try {
            for (settings in listOf(
                AiExternalSearchSettings(),
                AiExternalSearchSettings(routing = AiWebRoutingMode.EXTERNAL),
                AiExternalSearchSettings(webEnabled = false),
            )) {
                AiExternalSearchStore.update { settings }
                val messages = mutableListOf(assistant.newSystemMessage(), assistant.newUserMessage("找一本小说"))
                val failure = runCatching {
                    assistant.send(messages, "book-search-fixture", "blocked-turn", allowWeb = true, requireWebSearch = true)
                }.exceptionOrNull()
                assertEquals(context.getString(io.legado.app.R.string.ai_book_search_web_required), failure?.message)
            }
            assertEquals(0, requests.get())
        } finally {
            AiExternalSearchStore.update { settingsBefore }
        }
    }

    @Test
    fun defaultAssistantReceivesNativeWebGuidanceForCurrentPublicInformation() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        AiConfig.internalMcpEnabled = false
        val model = AiModelRegistry.enrich(AiModel("deepseek-flash"))
        val official = AiDefaultProviders.all().single { it.id == "deepseek" }
            .copy(apiKey = "deepseek-fixture", streamResponseEnabled = false).withFetchedModels(listOf(model))
        val requests = LinkedBlockingQueue<JsonObject>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().apply { request.body!!.writeTo(this) }.readUtf8()
            val json = JsonParser.parseString(body).asJsonObject
            requests += json
            val reply = if (json.has("input")) {
                """{"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"OK"}]}]}"""
            } else {
                """{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val assistant = AiChatClient { client }
        val question = "帮我查一下天蚕土豆的新书神通者更新到了多少章"
        for (responses in listOf(false, true)) {
            AiProviderStore.saveProviders(listOf(official.copy(useResponsesApi = responses)))
            assertTrue(AiConfig.saveAssistantModel(official.id, model.id))
            val systemMessage = assistant.newSystemMessage()
            val originalSystemMessage = systemMessage.deepCopy()
            val chat = mutableListOf(systemMessage, assistant.newUserMessage(question))
            val result = assistant.send(chat, "deepseek-search-fixture", "search", allowWeb = true)
            val body = requests.remove()
            assertEquals(listOf("deepseek_web_search"), body.arrayOrNull("tools").objectParts()
                .map { it.stringOrNull("type") })
            assertFalse(body.has("tool_choice"))
            val outgoing = body.arrayOrNull(if (responses) "input" else "messages").objectParts()
            val systemPrompt = outgoing.single { it.stringOrNull("role") == "system" }.get("content").contentText()
            assertTrue(systemPrompt.contains("MCP 使用"))
            assertTrue(systemPrompt.contains("公开连载的更新"))
            assertTrue(systemPrompt.contains("先用内置网页搜索核实"))
            assertFalse(systemPrompt.contains("先读取网页"))
            assertEquals(question, outgoing.last().get("content").contentText())
            assertEquals(originalSystemMessage, result.contextMessages.first())
        }
    }

    @Test
    fun officialChatReadsLinksAndNeverLeaksTheToolOrSignatureToAnotherRoute() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val model = AiModelRegistry.enrich(AiModel("deepseek-flash"))
        val official = AiDefaultProviders.all().single { it.id == "deepseek" }
            .copy(apiKey = "deepseek-fixture", streamResponseEnabled = true).withFetchedModels(listOf(model))
        val thirdParty = AiDefaultProviders.all().single { it.id == "sensenova" }
            .copy(apiKey = "sensenova-fixture", streamResponseEnabled = true).withFetchedModels(listOf(model))
        AiProviderStore.saveProviders(listOf(official, thirdParty))
        assertTrue(AiConfig.saveAssistantModel(official.id, model.id))
        val requests = LinkedBlockingQueue<Pair<String, JsonObject>>()
        val payload = AtomicReference(sse(
            """{"choices":[{"delta":{"reasoning_content":"Before reading."}}]}""",
            """{"choices":[{"delta":{},"server_tool_event":{"type":"step_reasoning_done","step":0}}]}""",
            """{"choices":[{"delta":{},"server_tool_event":{"type":"call_started","step":0,"call":{"id":"r1","kind":"read_link","action":{"type":"read_link","url":"https://example.org/book"}}}}]}""",
            """{"choices":[{"delta":{"content":"Read.","reasoning_content":"After reading."}}]}""",
            """{"choices":[{"delta":{"annotations":[{"type":"url_citation","url_citation":{"url":"https://example.org/book","title":"Book review"}}],"server_tool_calls":[{"index":0,"reasoning_content":"Before reading.","calls":[{"id":"r1","kind":"read_link","status":"completed","action":{"type":"read_link","url":"https://example.org/book"},"page":{"url":"https://example.org/book","title":"Book review"},"signature":"read-signature"}]}]},"finish_reason":"stop"}]}""",
            "[DONE]",
        ))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().apply { request.body!!.writeTo(this) }.readUtf8()
            requests += request.url.host to JsonParser.parseString(body).asJsonObject
            Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.get().toResponseBody("text/event-stream".toMediaType()))
                .build()
        }.build()
        val assistant = AiChatClient { client }
        val updates = mutableListOf<AiWebActivity>()
        val first = assistant.send(
            messages = mutableListOf(assistant.newUserMessage("请读 https://example.org/book")),
            conversationId = "deepseek-web-fixture", turnId = "read", allowWeb = true,
            onWebUpdate = { update, access ->
                assertEquals(AiWebAccess.Native, access)
                updates += update
            },
        )
        val (firstHost, firstBody) = requests.remove()
        assertEquals("api.deepseek.com", firstHost)
        assertEquals(listOf("deepseek_read_url"), firstBody.arrayOrNull("tools").objectParts()
            .map { it.stringOrNull("type") }.filter { it != "function" })
        val readPrompt = firstBody.arrayOrNull("messages").objectParts().first()
        assertEquals("system", readPrompt.stringOrNull("role"))
        assertTrue(readPrompt.get("content").contentText().contains("先读取网页"))
        assertFalse(readPrompt.get("content").contentText().contains("内置网页搜索"))
        assertEquals("Read.", first.content)
        assertEquals("Before reading.After reading.", first.reasoning)
        assertEquals(listOf(AiWebSource("https://example.org/book", "Book review")), first.webActivity?.sources)
        assertTrue(updates.any { it.activeAction == AiWebActivityAction.READ })
        assertNull(updates.last().activeAction)

        payload.set(sse("""{"choices":[{"delta":{"content":"Follow-up."},"finish_reason":"stop"}]}""", "[DONE]"))
        fun followUp() = (first.contextMessages + assistant.newUserMessage("还有呢？")).toMutableList()
        assistant.send(followUp(), "deepseek-web-fixture", "same-route", allowWeb = false)
        val (_, sameBody) = requests.remove()
        assertTrue(sameBody.toString().contains("read-signature"))
        assertFalse(sameBody.toString().contains("先读取网页"))
        val replayed = sameBody.arrayOrNull("messages").objectParts().single { it.has("server_tool_calls") }
        assertEquals("After reading.", replayed.stringOrNull("reasoning_content"))
        assertTrue(sameBody.arrayOrNull("tools").objectParts().all { it.stringOrNull("type") == "function" })

        assertTrue(AiConfig.saveAssistantModel(thirdParty.id, model.id))
        assistant.send(followUp(), "deepseek-web-fixture", "other-route", allowWeb = true)
        val (otherHost, otherBody) = requests.remove()
        assertEquals(thirdParty.baseUrl.substringAfter("://").substringBefore('/'), otherHost)
        assertFalse(otherBody.toString().contains("read-signature"))
        assertFalse(otherBody.toString().contains("server_tool_calls"))
        assertFalse(otherBody.toString().contains("内置网页搜索"))
        assertTrue(otherBody.arrayOrNull("tools").objectParts().all { it.stringOrNull("type") == "function" })

        AiProviderStore.saveProvider(official.copy(useResponsesApi = true))
        assertTrue(AiConfig.saveAssistantModel(official.id, model.id))
        payload.set(sse(
            """{"type":"response.completed","response":{"status":"completed","output":[{"type":"web_search_call","id":"ws1","kind":"search","status":"completed","action":{"type":"search","sources":[{"title":"Publisher","url":"https://example.org/publisher"}]}},{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Found."}]}]}}""",
        ))
        val switched = assistant.send(followUp(), "deepseek-web-fixture", "responses", allowWeb = true)
        val (responsesHost, responsesBody) = requests.remove()
        assertEquals("api.deepseek.com", responsesHost)
        assertTrue(responsesBody.has("input"))
        assertEquals(listOf("deepseek_web_search"), responsesBody.arrayOrNull("tools").objectParts()
            .map { it.stringOrNull("type") }.filter { it != "function" })
        assertFalse(responsesBody.toString().contains("read-signature"))
        assertEquals(listOf(AiWebSource("https://example.org/publisher", "Publisher")), switched.webActivity?.sources)
    }

    @Test
    fun assistantUsesStandaloneSearchAndFetchWithoutMixingRoutesOrCredentials() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val model = AiModelRegistry.enrich(AiModel("deepseek-flash"))
        val official = AiDefaultProviders.all().single { it.id == "deepseek" }
            .copy(apiKey = "chat-official", streamResponseEnabled = false, useResponsesApi = true)
            .withFetchedModels(listOf(model))
        val senseTime = AiDefaultProviders.all().single { it.id == "sensenova" }
            .copy(apiKey = "chat-sense", streamResponseEnabled = false).withFetchedModels(listOf(model))
        AiProviderStore.saveProviders(listOf(official, senseTime))
        assertTrue(AiConfig.saveAssistantModel(senseTime.id, model.id))
        AiConfig.internalMcpEnabled = false
        val auto = AiExternalSearchSettings(
            deepSeek = AiExternalSearchService(apiKey = "standalone-key", enabled = true),
            selectedService = AiExternalSearchProvider.DEEPSEEK.id,
            backupEnabled = true,
        )
        val previous = AiExternalSearchStore.current()
        val searchRequests = LinkedBlockingQueue<Pair<String, JsonObject>>()
        val modelRequests = LinkedBlockingQueue<Pair<String, JsonObject>>()
        val modelReplies = LinkedBlockingQueue<String>()
        val fetchStatus = AtomicInteger(200)
        val modelClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals(if (request.url.host == "api.deepseek.com") "Bearer chat-official" else "Bearer chat-sense",
                request.header("Authorization"))
            modelRequests.add(request.url.encodedPath to JsonParser.parseString(
                Buffer().apply { request.body!!.writeTo(this) }.readUtf8(),
            ).asJsonObject)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(modelReplies.remove().toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val searchClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("Bearer standalone-key", request.header("Authorization"))
            assertEquals("api.deepseek.com", request.url.host)
            val path = request.url.encodedPath
            searchRequests.add(path to JsonParser.parseString(
                Buffer().apply { request.body!!.writeTo(this) }.readUtf8(),
            ).asJsonObject)
            val search = path == "/v1/tools/search"
            if (search) AiExternalSearchStore.update { it.copy(deepSeek = it.deepSeek.copy(enabled = false)) }
            val status = if (search) 200 else fetchStatus.get()
            val reply = when {
                search -> """{"results":[{"url":"https://example.org/book","title":"Publisher","summary":"An update this week."}]}"""
                status == 200 -> """{"title":"Chapter catalog","content":"Chapter 32 lists the launch date."}"""
                else -> """{"error":{"message":"Balance required"}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                .body(reply.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = AiChatClient(externalSearchClient = { AiExternalSearchClient(searchClient) }) { modelClient }
        val updates = mutableListOf<AiWebActivity>()
        var durable: AiChatDurableContextUpdate? = null
        val functionNames = setOf("search_web", "read_web_url")
        val link = "https://example.org/book"
        val conversationId = "standalone-web-fixture"
        try {
            AiExternalSearchStore.update { auto }
            modelReplies.add("""
                {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                  {"id":"call_s1","type":"function","function":{"name":"search_web","arguments":"{\"query\":\"book update\"}"}},
                  {"id":"call_s2","type":"function","function":{"name":"search_web","arguments":"{\"query\":\" book update \"}"}}
                ]},"finish_reason":"tool_calls"}]}
            """.trimIndent())
            modelReplies.add("""{"choices":[{"message":{"role":"assistant","content":"Found the update."},"finish_reason":"stop"}]}""")
            val search = client.send(
                mutableListOf(client.newUserMessage("What is the latest update?")), conversationId, "search",
                allowWeb = true,
                onWebUpdate = { update, access ->
                    assertEquals(AiWebAccess.External(auto.deepSeek), access)
                    updates += update
                },
                onDurableContextUpdate = { durable = it },
                onToolConfirmationRequired = { error("Web search must not need write approval") },
            )
            assertEquals("Found the update.", search.content)
            assertEquals(listOf(AiWebSource(link, "Publisher")), search.webActivity?.sources)
            assertTrue(search.webActivity?.providerLabel?.contains("DeepSeek") == true)
            assertEquals(search.webActivity, durable?.webActivity)
            assertTrue(updates.any { it.activeAction == AiWebActivityAction.SEARCH })
            assertNull(updates.last().activeAction)
            assertTrue(search.toolTrace.isEmpty())
            assertEquals(2, search.toolReceipts.size)
            val (searchPath, searchBody) = searchRequests.remove()
            assertEquals("/v1/tools/search", searchPath)
            assertEquals("book update", searchBody.stringOrNull("query"))
            assertTrue(searchRequests.isEmpty())
            repeat(2) { step ->
                val (_, request) = modelRequests.remove()
                val tools = request.arrayOrNull("tools").objectParts()
                assertTrue(tools.all { it.stringOrNull("type") == "function" })
                assertEquals(functionNames, tools.map { it.objectOrNull("function")?.stringOrNull("name") }.toSet())
                assertFalse(request.toString().contains("standalone-key"))
                if (step == 1) assertTrue(request.toString().contains("An update this week."))
            }

            AiExternalSearchStore.update { auto.copy(routing = AiWebRoutingMode.EXTERNAL) }
            assertTrue(AiConfig.saveAssistantModel(official.id, model.id))
            var followUp = (search.contextMessages + client.newUserMessage("Read $link")).toMutableList()
            for (status in listOf(200, 402)) {
                updates.clear()
                fetchStatus.set(status)
                modelReplies.add("""
                    {"status":"completed","output":[{"type":"function_call","call_id":"call_r$status",
                      "name":"read_web_url","arguments":"{\"url\":\"$link\"}","status":"completed"}]}
                """.trimIndent())
                modelReplies.add("""
                    {"status":"completed","output":[{"type":"message","role":"assistant",
                      "content":[{"type":"output_text","text":"Read result $status."}]}]}
                """.trimIndent())
                val read = client.send(
                    followUp, conversationId, "read-$status", allowWeb = true,
                    onWebUpdate = { update, access ->
                        assertEquals(AiWebAccess.External(auto.deepSeek), access)
                        updates += update
                    },
                )
                assertEquals("Read result $status.", read.content)
                assertTrue(updates.any { it.activeAction == AiWebActivityAction.READ })
                assertNull(updates.last().activeAction)
                val (fetchPath, fetchBody) = searchRequests.remove()
                assertEquals("/v1/tools/fetch", fetchPath)
                assertEquals(setOf("url"), fetchBody.keySet())
                assertEquals(link, fetchBody.stringOrNull("url"))
                assertTrue(searchRequests.isEmpty())
                val firstRequest = modelRequests.remove()
                val secondRequest = modelRequests.remove()
                for ((path, request) in listOf(firstRequest, secondRequest)) {
                    assertTrue(path.endsWith("/responses"))
                    val tools = request.arrayOrNull("tools").objectParts()
                    assertTrue(tools.all { it.stringOrNull("type") == "function" })
                    assertEquals(functionNames, tools.map { it.stringOrNull("name") }.toSet())
                    assertFalse(request.toString().contains("standalone-key"))
                }
                if (status == 200) {
                    assertEquals(listOf(AiWebSource(link, "Chapter catalog")), read.webActivity?.sources)
                    assertTrue(secondRequest.second.toString().contains("Chapter 32 lists the launch date."))
                } else {
                    assertTrue(read.webActivity?.externalError?.contains("402") == true)
                    assertTrue(read.webActivity?.sources?.isEmpty() == true)
                    assertTrue(secondRequest.second.toString().contains("HTTP 402"))
                }
                followUp = (read.contextMessages + client.newUserMessage("Read this again: $link")).toMutableList()
            }

            AiExternalSearchStore.update { it.copy(webEnabled = false) }
            for (id in listOf(conversationId, "new-web-fixture")) {
                modelReplies.add("""
                    {"status":"completed","output":[{"type":"message","role":"assistant",
                      "content":[{"type":"output_text","text":"Web disabled."}]}]}
                """.trimIndent())
                val disabled = client.send(
                    mutableListOf(client.newUserMessage("Read $link")), id, "disabled", allowWeb = true,
                    onWebUpdate = { _, _ -> error("The global switch is off") },
                )
                assertNull(disabled.webActivity)
                assertTrue(modelRequests.remove().second.arrayOrNull("tools").objectParts().isEmpty())
                assertTrue(searchRequests.isEmpty())
            }
        } finally {
            AiExternalSearchStore.update { previous }
        }
    }

    @Test
    fun readingTheSameUrlUsesTheSelectedSearchProviderAndKeepsReceiptsSeparate() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val link = "https://example.org/book"
        val arguments = JsonObject().apply { addProperty("url", link) }
        val hosts = LinkedBlockingQueue<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            hosts += request.url.host
            val reply = when (request.url.host) {
                "open.feedcoopapi.com" -> """{"Result":{"Data":{"StatusCode":200,"Url":"$link","Title":"目录","ContentText":"豆包正文"}}}"""
                "api.tavily.com" -> """{"results":[{"url":"$link","raw_content":"Tavily article"}],"failed_results":[]}"""
                else -> error("Unexpected service: " + request.url.host)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val receipts = mutableListOf<String>()
        for (provider in listOf(AiExternalSearchProvider.DOUBAO, AiExternalSearchProvider.TAVILY)) {
            val service = AiExternalSearchService(provider, apiKey = "same-fixture-key", enabled = true)
            val turn = AiExternalWebTurn(service, AiExternalSearchClient(client)) {}
            val tool = requireNotNull(turn.tool("read_web_url"))
            val receipt = turn.receiptArguments(tool, arguments)
            assertEquals(provider.id, receipt.first().asString)
            assertFalse(receipt.toString().contains(service.apiKey))
            receipts += receipt.toString()
            val alternative = when (provider) {
                AiExternalSearchProvider.DOUBAO -> service.copy(doubaoMode = AiDoubaoSearchMode.GLOBAL)
                AiExternalSearchProvider.TAVILY -> service.copy(tavilySearchDepth = AiTavilySearchDepth.ADVANCED)
                else -> error("Unexpected provider: $provider")
            }
            val alternativeTurn = AiExternalWebTurn(alternative, AiExternalSearchClient(client)) {}
            assertEquals(receipt, alternativeTurn.receiptArguments(tool, arguments))
            val query = JsonObject().apply { addProperty("query", "book updates") }
            val defaultSearch = turn.receiptArguments(AiExternalSearchTool.SEARCH, query)
            val alternativeSearch = alternativeTurn.receiptArguments(AiExternalSearchTool.SEARCH, query)
            assertEquals("book updates", defaultSearch[4].asString)
            assertEquals(5, defaultSearch.size())
            assertFalse(defaultSearch == alternativeSearch)
            val result = turn.execute(tool, arguments)
            assertEquals(result, turn.execute(tool, arguments))
            val page = requireNotNull(result.arrayOrNull("results")).single().asJsonObject
            assertEquals(if (provider == AiExternalSearchProvider.DOUBAO) "豆包正文" else "Tavily article", page["content"].asString)
            turn.acceptResult(result)
            assertTrue(turn.settled?.providerLabel?.contains(service.displayName) == true)
            assertEquals(link, turn.settled?.sources?.single()?.url)
        }
        assertEquals(2, receipts.distinct().size)
        assertEquals(listOf("open.feedcoopapi.com", "api.tavily.com"), hosts.toList())
    }

    @Test
    fun configuredNativeAndResponsesModelsReachTheirActualChatEndpoints() = runBlocking {
        val calls = LinkedBlockingQueue<Pair<String, JsonObject>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun recordRequest(exchange: HttpExchange) {
            val body = exchange.requestBody.use { it.readBytes().decodeToString() }
            calls += exchange.requestURI.path to JsonParser.parseString(body).asJsonObject
        }
        server.createContext("/v1/messages") {
            recordRequest(it)
            it.json(
                """{"model":"claude-sonnet-4-5","content":[{"type":"text","text":"OK"}],"stop_reason":"end_turn"}"""
            )
        }
        server.createContext("/v1/models/gemini-3-flash-preview:generateContent") {
            recordRequest(it)
            it.json(
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"OK"}]},"finishReason":"STOP"}]}"""
            )
        }
        server.createContext("/v1/responses") {
            recordRequest(it)
            it.json(
                """{"status":"completed","output":[{"type":"message","role":"assistant",
                  "content":[{"type":"output_text","text":"OK"}]}]}"""
            )
        }
        server.createContext("/v1/chat/completions") {
            recordRequest(it)
            it.json(
                """{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}"""
            )
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            val cases = listOf(
                Triple(AiProviderType.CLAUDE, "claude-sonnet-4-5", "/v1/messages"),
                Triple(AiProviderType.GOOGLE, "gemini-3-flash-preview", "/v1/models/gemini-3-flash-preview:generateContent"),
                Triple(AiProviderType.OPENAI, "gpt-4o", "/v1/responses"),
            )
            cases.forEachIndexed { index, (type, id, endpoint) ->
                val model = AiModelRegistry.enrich(AiModel(id))
                val provider = fixture.copy(
                    id = "workflow-protocol-$index",
                    type = type,
                    useResponsesApi = true.takeIf { type == AiProviderType.OPENAI },
                ).withFetchedModels(listOf(model))
                AiProviderStore.saveProvider(provider)
                assertTrue(AiConfig.saveAssistantModel(provider.id, model.id))
                when (type) {
                    AiProviderType.CLAUDE -> AiConfig.saveAssistantModelReasoning(provider, model, reasoningBudgetChoice(3500))
                    AiProviderType.GOOGLE -> AiConfig.saveAssistantModelReasoning(provider, model, "minimal")
                    AiProviderType.OPENAI -> Unit
                }
                assertEquals("OK", AiChatClient().send(messages(), "fixture", "case-$index").content)
                val (requestPath, requestBody) = calls.remove()
                assertEquals(endpoint, requestPath)
                if (type == AiProviderType.CLAUDE) {
                    val thinking = requestBody.getAsJsonObject("thinking")
                    assertEquals("enabled", thinking.get("type").asString)
                    assertEquals(3500, thinking.get("budget_tokens").asInt)
                }
                if (type == AiProviderType.GOOGLE) {
                    val thinking = requestBody.getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")
                    assertEquals("MINIMAL", thinking.get("thinkingLevel").asString)
                }
                if (type == AiProviderType.OPENAI) {
                    assertTrue(requestBody.has("input"))
                    assertFalse(requestBody.has("messages"))
                    val responsesProvider = requireNotNull(AiProviderStore.provider(provider.id))
                    assertEquals(true, responsesProvider.useResponsesApi)
                    assertEquals("/chat/completions", responsesProvider.chatCompletionsPath)

                    AiProviderStore.saveProvider(responsesProvider.copy(useResponsesApi = false))
                    val chatProvider = requireNotNull(AiProviderStore.provider(provider.id))
                    assertEquals(false, chatProvider.useResponsesApi)
                    assertEquals(responsesProvider.modelConfigurationKey(), chatProvider.modelConfigurationKey())
                    assertEquals(responsesProvider.availableModelIds, chatProvider.availableModelIds)
                    assertFalse(chatProvider.catalogRefreshRequired)

                    assertEquals("OK", AiChatClient().send(messages(), "fixture", "case-$index-chat").content)
                    val (chatPath, chatBody) = calls.remove()
                    assertEquals("/v1/chat/completions", chatPath)
                    assertTrue(chatBody.has("messages"))
                    assertFalse(chatBody.has("input"))

                    val modelWithPreference = chatProvider.models.single().copy(
                        modelSettings = AiModelSettings(
                            catalogEndpoint = chatProvider.modelConfigurationKey(),
                            interfacePreference = AiModelInterfacePreference.RESPONSES,
                        ),
                    )
                    AiProviderStore.saveProvider(chatProvider.copy(models = listOf(modelWithPreference)))
                    val saved = requireNotNull(AiProviderStore.provider(provider.id))
                    assertEquals(false, saved.useResponsesApi)
                    assertEquals(chatProvider.modelConfigurationKey(), saved.modelConfigurationKey())
                    assertEquals(chatProvider.availableModelIds, saved.availableModelIds)
                    assertFalse(saved.catalogRefreshRequired)
                    assertEquals(AiModelSelection(provider.id, model.id), AiConfig.requireAssistantModel())
                    assertEquals(
                        AiModelInterfacePreference.RESPONSES,
                        saved.models.single().modelSettings?.interfacePreference,
                    )

                    assertEquals("OK", AiChatClient().send(messages(), "fixture", "case-$index-model-responses").content)
                    val (modelPath, modelBody) = calls.remove()
                    assertEquals("/v1/responses", modelPath)
                    assertEquals(id, modelBody.stringOrNull("model"))
                    assertTrue(modelBody.has("input"))
                    assertFalse(modelBody.has("messages"))
                }
            }
            assertTrue(calls.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun truncatedAssistantReplyIsSentWithTheNextUserCorrection() = runBlocking {
        withAssistantReplies { fixture, reply, requests ->
            val client = AiChatClient()
            for (stream in listOf(false, true)) {
                selectFixtureAssistant(fixture, stream)
                reply.set(if (stream) {
                    sse(
                        """{"choices":[{"delta":{"content":"Partial"}}]}""",
                        """{"choices":[{"delta":{"content":" reply"},"finish_reason":"length"}]}""",
                        "[DONE]",
                    )
                } else {
                    """{"choices":[{"message":{"role":"assistant","content":"Partial reply"},"finish_reason":"length"}]}"""
                })
                var durable: AiChatDurableContextUpdate? = null
                val incomplete = runCatching {
                    client.send(messages(), "truncated-$stream", "initial", onDurableContextUpdate = { durable = it })
                }.exceptionOrNull() as AiAssistantReplyEndException
                assertEquals(AiAssistantReplyEndReason.TRUNCATED, incomplete.reason)
                assertEquals("Partial reply", incomplete.partialContent)
                assertEquals(stream, requests.remove().get("stream").asBoolean)
                val history = requireNotNull(durable).contextMessages
                assertFalse(history.any { it.stringOrNull("role") == "assistant" })

                val draft = requireNotNull(AiChatContextManager.interruptedReplyContent("Partial reply", incomplete))
                val correction = "I meant the first point."
                reply.set(if (stream) {
                    sse("""{"choices":[{"delta":{"content":"OK"},"finish_reason":"stop"}]}""")
                } else {
                    """{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}"""
                })
                val nextMessages = history.map(JsonObject::deepCopy).toMutableList().apply {
                    add(AiChatContextManager.interruptedReplyMessage("draft", draft))
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", correction)
                    })
                }
                assertEquals("OK", client.send(nextMessages, "truncated-$stream", "next").content)
                val wireHistory = requests.remove().getAsJsonArray("messages").map { it.asJsonObject }
                assertEquals(listOf(draft, correction), wireHistory.takeLast(2).map { it.stringOrNull("content") })
                assertFalse(wireHistory.any(AiChatContextManager::isInterruptedReply))
            }
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun assistantStreamEofRetainsPartialTextUntilACompletionSignalArrives() = runBlocking {
        withAssistantReplies { fixture, reply, _ ->
            selectFixtureAssistant(fixture, stream = true)
            val client = AiChatClient()
            val partial = """{"choices":[{"delta":{"content":"Partial reply"}}]}"""
            reply.set(sse(partial))
            val incomplete = runCatching {
                client.send(messages(), "stream-eof", "eof")
            }.exceptionOrNull() as AiAssistantReplyEndException
            assertEquals(AiAssistantReplyEndReason.INTERRUPTED, incomplete.reason)
            assertEquals("Partial reply", incomplete.partialContent)

            reply.set(sse(partial, "[DONE]"))
            assertEquals("Partial reply", client.send(messages(), "stream-eof", "done").content)
        }
    }

    @Test
    fun assistantStreamsTextWithToolsAndRetainsItAfterAProviderError() = runBlocking {
        val release = CountDownLatch(1)
        val request = CompletableDeferred<JsonObject>()
        val preview = CompletableDeferred<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            request.complete(JsonParser.parseString(exchange.requestBody.use { it.readBytes().decodeToString() }).asJsonObject)
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { body ->
                body.write(sse("""{"choices":[{"delta":{"content":"Draft<|DSM"}}]}""").encodeToByteArray())
                body.flush()
                if (release.await(10, TimeUnit.SECONDS)) {
                    body.write(sse(
                        """{"choices":[{"delta":{"content":"L|tool arguments"}}]}""",
                        """{"error":{"message":"gateway overloaded"}}""",
                    ).encodeToByteArray())
                }
            }
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            selectFixtureAssistant(fixture, stream = true)
            AiConfig.internalMcpEnabled = true
            val sending = async(Dispatchers.IO) {
                runCatching {
                    AiChatClient().send(
                        messages(), "tool-preview", "tool-error",
                        enabledMcpCapabilityIds = listOf("general.service_info"),
                        onStreamUpdate = { update -> preview.complete(update.content) },
                    )
                }
            }
            try {
                val recorded = withTimeout(20_000) {
                    select {
                        request.onAwait { it }
                        sending.onAwait { throw AssertionError("Assistant ended before sending its request", it.exceptionOrNull()) }
                    }
                }
                assertTrue(recorded.arrayOrNull("tools").objectParts().isNotEmpty())
                assertEquals("Draft", withTimeout(3_000) { preview.await() })
            } finally {
                release.countDown()
            }
            val error = withTimeout(5_000) { sending.await() }.exceptionOrNull() as AiAssistantReplyEndException
            assertEquals(AiAssistantReplyEndReason.INTERRUPTED, error.reason)
            assertEquals("Draft", error.partialContent)
            assertEquals("gateway overloaded", error.cause?.message)
            assertTrue(AiChatContextManager.interruptedReplyContent(error.partialContent, error)?.endsWith("Draft") == true)
        } finally {
            release.countDown()
            AiConfig.internalMcpEnabled = false
            server.stop(0)
        }
    }

    @Test
    fun assistantSettlesWithoutPeerCloseAndKeepsUsageTail() = runBlocking {
        for (sendDone in listOf(true, false)) {
            val release = CountDownLatch(1)
            val flushed = CompletableDeferred<Unit>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/v1/chat/completions") { exchange ->
                exchange.requestBody.use { it.readBytes() }
                exchange.responseHeaders.set("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                val ending = if (sendDone) sse("[DONE]") else ""
                exchange.responseBody.use { body ->
                    body.write((sse(
                        """{"choices":[{"delta":{"content":"Done."},"finish_reason":"stop"}]}""",
                        """{"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":2,"total_tokens":14}}""",
                    ) + ending).encodeToByteArray())
                    body.flush()
                    flushed.complete(Unit)
                    release.await(10, TimeUnit.SECONDS)
                }
            }
            server.start()
            try {
                val fixture = configureFixture(server).copy(supportsStreamUsage = true)
                selectFixtureAssistant(fixture, stream = true)
                val sending = async(Dispatchers.IO) {
                    AiChatClient().send(messages(), "open-connection", "done-$sendDone")
                }
                try {
                    withTimeout(5_000) { flushed.await() }
                    val reply = withTimeout(3_000) { sending.await() }
                    assertEquals("Done.", reply.content)
                    assertEquals(AiChatUsage(12, 2, 14), reply.usage)
                } finally {
                    release.countDown()
                }
            } finally {
                release.countDown()
                server.stop(0)
            }
        }
    }

    @Test
    fun responsesAssistantPreservesPartialTextWhenTheGatewayReportsAnError() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/responses") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val body = sse(
                """{"type":"response.output_text.delta","delta":"Partial text"}""",
                """{"type":"error","message":"gateway overloaded"}""",
            ).encodeToByteArray()
            exchange.responseHeaders.set("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            selectFixtureAssistant(configureFixture(server).copy(useResponsesApi = true), stream = true)
            val error = runCatching {
                AiChatClient().send(messages(), "responses-error", "partial")
            }.exceptionOrNull() as AiAssistantReplyEndException
            assertEquals(AiAssistantReplyEndReason.INTERRUPTED, error.reason)
            assertEquals("Partial text", error.partialContent)
            assertEquals("gateway overloaded", error.cause?.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun assistantRefusalDropsPartialTextEvenWithATrailingStreamError() = runBlocking {
        withAssistantReplies { fixture, reply, _ ->
            selectFixtureAssistant(fixture, stream = true)
            reply.set(
                sse(
                    """{"choices":[{"delta":{"content":"Blocked preview"}}]}""",
                    """{"choices":[{"delta":{},"finish_reason":"content_filter"}]}""",
                    """{"error":{"message":"Late stream error"}}""",
                )
            )
            var durable: AiChatDurableContextUpdate? = null
            val refusal = runCatching {
                AiChatClient().send(messages(), "refusal", "refused", onDurableContextUpdate = { durable = it })
            }.exceptionOrNull() as AiAssistantReplyEndException
            assertEquals(AiAssistantReplyEndReason.REFUSED, refusal.reason)
            assertTrue(refusal.partialContent.isNullOrBlank())
            assertTrue(refusal.partialReasoning.isNullOrBlank())
            assertFalse(requireNotNull(durable).contextMessages.any { it.stringOrNull("role") == "assistant" })
        }
    }

    @Test
    fun compactionUsesTheCurrentChatModelAndIncludesInterruptedReplies() = runBlocking {
        val requests = LinkedBlockingQueue<JsonObject>()
        val streamEnding = AtomicReference(",\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val request = JsonParser.parseString(exchange.requestBody.use { it.readBytes().decodeToString() }).asJsonObject
            requests += request
            if (request.get("stream").asBoolean) {
                val body = (
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Summary\"}" + streamEnding.get()
                    ).encodeToByteArray()
                exchange.responseHeaders.set("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.json(
                    """{"choices":[{"message":{"role":"assistant","content":"Summary"},"finish_reason":"stop"}]}"""
                )
            }
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            val model = AiModelRegistry.enrich(AiModel("gpt-4o"))
            val app = ApplicationProvider.getApplicationContext<Application>()
            app.defaultSharedPreferences.edit()
                .putString("aiContextCompactionProviderId", "old-provider")
                .putString("aiContextCompactionModelId", "old-summarizer")
                .apply()
            val client = AiChatClient()
            for (stream in listOf(false, true)) {
                val provider = fixture.copy(streamResponseEnabled = stream).withFetchedModels(listOf(model))
                AiProviderStore.saveProvider(provider)
                assertTrue(AiConfig.saveAssistantModel(provider.id, model.id))
                val draft = requireNotNull(AiChatContextManager.interruptedReplyContent(
                    "Draft to summarize",
                    AiAssistantReplyEndException(AiAssistantReplyEndReason.INTERRUPTED),
                ))
                val history = messages().apply {
                    add(JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", "Old reply")
                        addProperty("reasoning_content", "native reasoning")
                        addProperty(AI_NATIVE_HISTORY, "opaque history")
                    })
                    add(AiChatContextManager.interruptedReplyMessage("draft", draft))
                }
                val result = client.compactContext(history, "fixture")
                val request = requests.remove()
                assertEquals("gpt-4o", request.get("model").asString)
                assertEquals(stream, request.get("stream").asBoolean)
                assertTrue(result.contextMessages.any { "Summary" in it.get("content").contentText() })
                assertFalse(request.has("tools"))
                assertFalse(request.toString().contains("native reasoning"))
                assertFalse(request.toString().contains(AI_NATIVE_HISTORY))
                val wireHistory = request.getAsJsonArray("messages").map { it.asJsonObject }
                assertEquals("assistant", wireHistory.single { it.stringOrNull("content") == draft }.stringOrNull("role"))
                assertFalse(wireHistory.any(AiChatContextManager::isInterruptedReply))
            }
            streamEnding.set("}]}\n\n")
            val history = messages()
            val original = history.map(JsonObject::deepCopy)
            assertTrue(runCatching { client.compactContext(history, "fixture") }.isFailure)
            assertEquals(original, history)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun customCatalogFlowsThroughSelectionAndSendingAndAnEmptyCatalogInvalidatesTheSelection() = runBlocking {
        val catalog = AtomicReference("""{"data":[{"id":"qa-unknown"}]}""")
        val requests = LinkedBlockingQueue<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { it.json(catalog.get()) }
        server.createContext("/v1/chat/completions") {
            requests.add(JsonParser.parseString(it.requestBody.use { body -> body.readBytes().decodeToString() }).asJsonObject)
            it.json("""{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}""")
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            assertEquals(listOf("qa-unknown"), AiManager.ensureProviderModelCatalog(fixture.id).map { it.id })
            assertTrue(AiConfig.saveAssistantModel(fixture.id, "qa-unknown"))
            val client = AiChatClient()
            client.send(messages(), "fixture", "auto")
            val automatic = requests.remove()
            assertEquals("qa-unknown", automatic.get("model").asString)
            assertFalse(automatic.has("reasoning_effort"))

            val loaded = requireNotNull(AiProviderStore.provider(fixture.id))
            val model = requireNotNull(loaded.assistantModelChoice("qa-unknown")).model
            val manual = loaded.models.single().copy(
                displayName = "My fixture", modelSettings = AiModelSettings(
                    loaded.modelConfigurationKey(), referenceContextWindowTokens = 24_000,
                ),
            )
            AiProviderStore.saveProvider(loaded.copy(models = listOf(manual)))
            AiConfig.saveAssistantModelReasoning(loaded, model, "xhigh")
            client.send(messages(), "fixture", "explicit")
            assertEquals("xhigh", requests.remove().get("reasoning_effort").asString)

            val enabled = requireNotNull(AiProviderStore.provider(fixture.id))
            AiProviderStore.saveProvider(enabled.copy(availableModelIds = emptyList()))
            assertTrue(runCatching { client.send(messages(), "fixture", "disabled") }.isFailure)
            assertTrue(requests.isEmpty())
            AiProviderStore.saveProvider(enabled)

            catalog.set("""{"invalid":true}""")
            assertTrue(runCatching { AiManager.fetchAndSaveModels(fixture.id) }.isFailure)
            assertEquals(listOf("qa-unknown"), AiProviderStore.provider(fixture.id)?.models?.map { it.id })

            catalog.set("""{"data":[]}""")
            assertTrue(AiManager.fetchAndSaveModels(fixture.id).isEmpty())
            assertTrue(AiProviderStore.provider(fixture.id)?.models?.isEmpty() == true)
            assertTrue(runCatching { client.send(messages(), "fixture", "stale") }.isFailure)
            assertTrue(requests.isEmpty())

            catalog.set("""{"data":[{"id":"qa-unknown"}]}""")
            AiManager.fetchAndSaveModels(fixture.id)
            val restored = requireNotNull(AiProviderStore.provider(fixture.id))
            assertEquals("My fixture", restored.models.single().displayName)
            assertEquals(24_000, restored.models.single().modelSettings?.referenceContextWindowTokens)
            assertTrue(restored.assistantModelChoice("qa-unknown") != null)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun manualModelCanBeSelectedAndSentWhenCatalogFails() = runBlocking {
        val modelId = "qa-team/Manual-Chat.V2"
        val catalogRequests = AtomicInteger()
        val catalogAvailable = AtomicReference(false)
        val requests = LinkedBlockingQueue<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { exchange ->
            catalogRequests.incrementAndGet()
            if (catalogAvailable.get()) {
                exchange.json("""{"data":[]}""")
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }
        server.createContext("/v1/chat/completions") { exchange ->
            requests.add(JsonParser.parseString(exchange.requestBody.use { it.readBytes().decodeToString() }).asJsonObject)
            exchange.json("""{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}""")
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            val configurationKey = fixture.modelConfigurationKey()
            val previousSelection = runCatching { AiConfig.requireAssistantModel() }.getOrNull()
            assertEquals(
                AiManualModelAddResult.UNAVAILABLE,
                AiProviderStore.addManualModel(fixture.id, modelId, AiModelType.CHAT, "previous-configuration"),
            )
            assertEquals(
                AiManualModelAddResult.ADDED,
                AiProviderStore.addManualModel(fixture.id, "  $modelId  ", AiModelType.CHAT, configurationKey),
            )
            assertEquals(
                AiManualModelAddResult.DUPLICATE,
                AiProviderStore.addManualModel(fixture.id, modelId, AiModelType.IMAGE, configurationKey),
            )
            assertEquals(previousSelection, runCatching { AiConfig.requireAssistantModel() }.getOrNull())
            assertEquals(0, catalogRequests.get())
            assertTrue(requests.isEmpty())

            val saved = requireNotNull(AiProviderStore.provider(fixture.id))
            assertTrue(saved.isManualModel(modelId))
            assertEquals(listOf(modelId), saved.models.map { it.id })
            assertEquals(modelId, saved.models.single().displayName)
            assertEquals(AiModelType.CHAT, saved.models.single().safeType())
            assertEquals(listOf(modelId), saved.assistantModelChoices().map { it.model.id })

            assertTrue(runCatching { AiManager.fetchAndSaveModels(fixture.id) }.isFailure)
            assertTrue(catalogRequests.get() > 0)
            assertTrue(AiConfig.saveAssistantModel(fixture.id, modelId))
            val client = AiChatClient()
            assertEquals("OK", client.send(messages(), "fixture", "manual-no-catalog").content)
            assertEquals(modelId, requests.remove().get("model").asString)

            catalogAvailable.set(true)
            assertTrue(AiManager.fetchAndSaveModels(fixture.id).isEmpty())
            val afterRefresh = requireNotNull(AiProviderStore.provider(fixture.id))
            assertTrue(afterRefresh.isManualModel(modelId))
            assertEquals(listOf(modelId), afterRefresh.models.map { it.id })
            assertEquals("OK", client.send(messages(), "fixture", "manual-empty-catalog").content)
            assertEquals(modelId, requests.remove().get("model").asString)

            AiProviderStore.saveProvider(afterRefresh.withModelSelection(setOf(modelId), enabled = false))
            assertTrue(runCatching { client.send(messages(), "fixture", "manual-disabled").content }.isFailure)
            assertTrue(requests.isEmpty())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun manualModelAddedDuringFirstCatalogRequestKeepsItsSettingsAndMergesMetadata() = runBlocking {
        val modelId = "qa-team/Manual-Chat.V2"
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { exchange ->
            started.countDown()
            check(finish.await(15, TimeUnit.SECONDS))
            exchange.json(
                """{"data":[{"id":"$modelId","name":"Catalog name","context_window":65536},{"id":"qa-other"}]}""",
            )
        }
        server.start()
        try {
            val fixture = AiProviderStore.saveProvider(configureFixture(server).copy(timeoutSeconds = 20))
            val loading = async(Dispatchers.IO) { AiManager.ensureProviderModelCatalog(fixture.id) }
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS))
                assertEquals(
                    AiManualModelAddResult.ADDED,
                    AiProviderStore.addManualModel(fixture.id, modelId, AiModelType.CHAT),
                )
                val saved = requireNotNull(AiProviderStore.provider(fixture.id))
                val manual = saved.models.single()
                val settings = requireNotNull(manual.modelSettings).copy(referenceContextWindowTokens = 24_000)
                AiProviderStore.saveProvider(
                    saved.copy(models = listOf(manual.copy(displayName = "My manual name", modelSettings = settings))),
                )
            } finally {
                finish.countDown()
            }
            assertEquals(setOf(modelId, "qa-other"), loading.await().map { it.id }.toSet())
            val merged = requireNotNull(AiProviderStore.provider(fixture.id))
            assertTrue(merged.isManualModel(modelId))
            val manual = merged.models.single { it.id == modelId }
            assertEquals("My manual name", manual.displayName)
            assertEquals(24_000, manual.modelSettings?.referenceContextWindowTokens)
            assertEquals(65_536, manual.modelInfo?.contextWindow?.value)
            assertEquals(AiModelType.CHAT, manual.safeType())
            assertTrue(manual.isTextChatModel())
            assertTrue(AiConfig.saveAssistantModel(fixture.id, modelId))

            val other = AiProviderStore.saveProvider(fixture.copy(id = "workflow-other-provider"))
            assertEquals(AiManualModelAddResult.ADDED, AiProviderStore.addManualModel(other.id, modelId, AiModelType.CHAT))
            assertEquals(AiModelSelection(fixture.id, modelId), AiConfig.requireAssistantModel())

            AiProviderStore.saveProvider(merged.copy(baseUrl = fixture.baseUrl + "/other"))
            val moved = requireNotNull(AiProviderStore.provider(fixture.id))
            assertFalse(moved.isManualModel(modelId))
            assertTrue(moved.models.none { it.id == modelId })
            assertEquals(null, moved.assistantModelChoice(modelId))

            AiProviderStore.saveProvider(moved.copy(baseUrl = fixture.baseUrl))
            val restored = requireNotNull(AiProviderStore.provider(fixture.id))
            assertTrue(restored.isManualModel(modelId))
            val restoredModel = restored.models.single { it.id == modelId }
            assertEquals("My manual name", restoredModel.displayName)
            assertEquals(24_000, restoredModel.modelSettings?.referenceContextWindowTokens)
            assertTrue(restored.assistantModelChoice(modelId) != null)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun deletingManualModelKeepsCatalogModelsAndDoesNotSwitchAssistantSelection() = runBlocking {
        val modelId = "qa-team/Manual-Chat.V2"
        val catalog = AtomicReference("""{"data":[{"id":"gpt-4o"}]}""")
        val requests = LinkedBlockingQueue<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { it.json(catalog.get()) }
        server.createContext("/v1/chat/completions") { exchange ->
            val body = exchange.requestBody.use { it.readBytes().decodeToString() }
            requests.add(JsonParser.parseString(body).asJsonObject)
            exchange.json("""{"choices":[{"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}""")
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            AiManager.fetchAndSaveModels(fixture.id)
            assertEquals(AiManualModelAddResult.ADDED, AiProviderStore.addManualModel(fixture.id, modelId, AiModelType.CHAT))
            val added = requireNotNull(AiProviderStore.provider(fixture.id))
            val source = added.modelConfigurationKey()
            AiProviderStore.saveProvider(added.copy(models = added.models.map { model ->
                if (model.id == modelId) model.copy(displayName = "My manual name") else model
            }))
            assertFalse(AiProviderStore.removeManualModel(fixture.id, "gpt-4o", source))
            assertFalse(AiProviderStore.removeManualModel(fixture.id, modelId, "previous-configuration"))
            assertTrue(AiConfig.saveAssistantModel(fixture.id, modelId))
            val client = AiChatClient()
            assertEquals("OK", client.send(messages(), "fixture", "before-deleting-manual").content)
            assertEquals(modelId, requests.remove().get("model").asString)

            assertTrue(AiProviderStore.removeManualModel(fixture.id, modelId, source))
            assertFalse(AiProviderStore.removeManualModel(fixture.id, modelId, source))
            val deleted = requireNotNull(AiProviderStore.provider(fixture.id))
            assertEquals(listOf("gpt-4o"), deleted.models.map { it.id })
            assertEquals(listOf("gpt-4o"), deleted.availableModelIds)
            assertEquals(AiModelSelection(fixture.id, modelId), AiConfig.requireAssistantModel())
            assertTrue(runCatching { client.send(messages(), "fixture", "after-deleting-manual") }.isFailure)
            assertTrue(requests.isEmpty())

            AiManager.fetchAndSaveModels(fixture.id)
            assertEquals(listOf("gpt-4o"), AiProviderStore.provider(fixture.id)?.models?.map { it.id })

            catalog.set("""{"data":[{"id":"gpt-4o"},{"id":"$modelId"}]}""")
            AiManager.fetchAndSaveModels(fixture.id)
            val refreshed = requireNotNull(AiProviderStore.provider(fixture.id))
            val fromCatalog = refreshed.models.single { it.id == modelId }
            assertFalse(refreshed.isManualModel(modelId))
            assertFalse(fromCatalog.displayName == "My manual name")
            assertEquals(null, fromCatalog.modelSettings)
            assertFalse(modelId in refreshed.availableModelIds)
            assertFalse(AiProviderStore.removeManualModel(fixture.id, modelId, source))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun geminiMalformedMethodsKeepTheCatalogAndValidEmbeddingOnlyCanClearIt() = runBlocking {
        val catalog = AtomicReference("""{"models":[{"name":"models/gemini-fixture",
            "supportedGenerationMethods":["generateContent"]}]}""")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1beta/models") { it.json(catalog.get()) }
        server.start()
        try {
            val fixture = configureFixture(server)
            val google = AiProviderStore.saveProvider(fixture.copy(
                type = AiProviderType.GOOGLE, baseUrl = "http://127.0.0.1:${server.address.port}/v1beta",
            ))
            assertEquals(listOf("gemini-fixture"), AiManager.fetchAndSaveModels(google.id).map { it.id })
            catalog.set("""{"models":[{"name":"models/gemini-fixture","supportedGenerationMethods":[42]}]}""")
            assertTrue(runCatching { AiManager.fetchAndSaveModels(google.id) }.isFailure)
            assertEquals(listOf("gemini-fixture"), AiProviderStore.provider(google.id)?.models?.map { it.id })
            catalog.set("""{"models":[{"name":"models/embedding-fixture","supportedGenerationMethods":["embedContent"]}]}""")
            assertTrue(AiManager.fetchAndSaveModels(google.id).isEmpty())
            assertTrue(AiProviderStore.provider(google.id)?.models?.isEmpty() == true)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun paginatedCatalogKeepsThePreviousModelsWhenALaterPageFails() = runBlocking {
        val mode = AtomicReference("complete")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        for (basePath in listOf("/v1beta", "/v1")) {
            server.createContext("$basePath/catalog") { exchange ->
                if (exchange.requestURI.query == null) {
                    exchange.responseHeaders.set("Location", "$basePath/models")
                    exchange.sendResponseHeaders(302, -1)
                } else {
                    exchange.sendResponseHeaders(400, -1)
                }
                exchange.close()
            }
        }
        server.createContext("/v1beta/models") { exchange ->
            val laterPage = exchange.requestURI.query.orEmpty().contains("pageToken=second")
            when {
                laterPage && mode.get() == "fail" -> exchange.sendResponseHeaders(503, -1)
                laterPage -> exchange.json("""{"models":[{"name":"models/gemini-3-flash-preview"}]}""")
                mode.get() == "removed" -> exchange.json("""{"models":[{"name":"models/first"}]}""")
                else -> exchange.json("""{"models":[{"name":"models/first"}],"nextPageToken":"second"}""")
            }
            exchange.close()
        }
        server.createContext("/v1/models") { exchange ->
            val laterPage = exchange.requestURI.query.orEmpty().contains("after_id=first")
            when {
                laterPage && mode.get() == "fail" -> exchange.sendResponseHeaders(503, -1)
                laterPage -> exchange.json("""{"data":[{"id":"claude-sonnet-4-5"}],"has_more":false}""")
                mode.get() == "removed" -> exchange.json("""{"data":[{"id":"first"}],"has_more":false}""")
                else -> exchange.json("""{"data":[{"id":"first"}],"has_more":true,"last_id":"first"}""")
            }
            exchange.close()
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            val cases = listOf(
                Triple(AiProviderType.GOOGLE, "v1beta", "gemini-3-flash-preview"),
                Triple(AiProviderType.CLAUDE, "v1", "claude-sonnet-4-5"),
            )
            for ((type, path, modelId) in cases) {
                val provider = AiProviderStore.saveProvider(fixture.copy(
                    id = "catalog-${type.prefValue}",
                    type = type,
                    baseUrl = "http://127.0.0.1:${server.address.port}/$path",
                    useCustomModelsUrl = true,
                    modelsUrl = if (type == AiProviderType.GOOGLE) "/catalog?pageToken=old"
                        else "/catalog?after_id=old&before_id=old",
                ))
                mode.set("complete")
                AiManager.fetchAndSaveModels(provider.id)
                val complete = requireNotNull(AiProviderStore.provider(provider.id))
                assertEquals(setOf("first", modelId), complete.models.map { it.id }.toSet())
                assertEquals(1, complete.models.map { it.modelInfo?.catalogSourceId }.distinct().size)
                assertFalse(complete.isMissingCatalogModel(modelId))
                assertFalse(complete.copy(catalogRefreshRequired = true).isMissingCatalogModel("other"))
                assertTrue(AiConfig.saveAssistantModel(provider.id, modelId))

                mode.set("fail")
                assertTrue(runCatching { AiManager.fetchAndSaveModels(provider.id) }.isFailure)
                val unchanged = requireNotNull(AiProviderStore.provider(provider.id))
                assertEquals(complete.models, unchanged.models)
                assertFalse(unchanged.isMissingCatalogModel(modelId))

                mode.set("removed")
                AiManager.fetchAndSaveModels(provider.id)
                val refreshed = requireNotNull(AiProviderStore.provider(provider.id))
                assertTrue(refreshed.isMissingCatalogModel(modelId))
                assertEquals(modelId, AiConfig.assistantModelId)
                assertEquals(null, refreshed.assistantModelChoice(modelId))
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aNewCustomProviderCannotBeMistakenForADeletedProvider() {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val original = AiProviderStore.createCustomProvider(AiProviderType.OPENAI)
        assertTrue(AiProviderStore.deleteCustomProvider(original.id))
        val replacement = AiProviderStore.createCustomProvider(AiProviderType.OPENAI)
        assertFalse(original.id == replacement.id)
        assertTrue(AiProviderStore.provider(original.id) == null)
    }

    @Test
    fun slowEarlierRefreshCannotReplaceTheNewerCatalog() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newFixedThreadPool(2)
        val earlierStarted = CountDownLatch(1)
        val allowEarlierToFinish = CountDownLatch(1)
        val requests = AtomicInteger()
        server.executor = executor
        server.createContext("/v1/models") { exchange ->
            if (requests.incrementAndGet() == 1) {
                earlierStarted.countDown()
                check(allowEarlierToFinish.await(5, TimeUnit.SECONDS))
                exchange.json("""{"data":[{"id":"old"}]}""")
            } else {
                exchange.json("""{"data":[{"id":"new"}]}""")
            }
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            val earlier = async(Dispatchers.IO) { runCatching { AiManager.fetchAndSaveModels(fixture.id) } }
            try {
                assertTrue(earlierStarted.await(5, TimeUnit.SECONDS))
                val result = AiManager.refreshEnabledProviderModels()
                assertEquals(1, result.refreshedProviderCount)
            } finally {
                allowEarlierToFinish.countDown()
            }
            assertTrue(earlier.await().isFailure)
            assertEquals(listOf("new"), AiProviderStore.provider(fixture.id)?.models?.map { it.id })
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun aProviderRemovedWhileWaitingForTheBatchDoesNotReceiveARequest() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newFixedThreadPool(4)
        val requests = LinkedBlockingQueue<String>()
        val occupied = CountDownLatch(4)
        val release = CountDownLatch(1)
        server.executor = executor
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path.substringAfter('/').substringBefore('/')
            occupied.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            exchange.json("""{"data":[{"id":"fixture-model"}]}""")
        }
        server.start()
        try {
            val fixture = configureFixture(server)
            val providers = (1..5).map { index ->
                val id = "fixture-$index"
                fixture.copy(id = id, baseUrl = "http://127.0.0.1:${server.address.port}/$id/v1")
            }
            AiProviderStore.saveProviders(AiDefaultProviders.all().map { it.copy(enabled = false) } + providers)
            val refresh = async(Dispatchers.IO) { AiManager.refreshEnabledProviderModels() }
            try {
                assertTrue(occupied.await(5, TimeUnit.SECONDS))
                val queued = providers.single { it.id !in requests }
                assertTrue(AiProviderStore.deleteCustomProvider(queued.id))
            } finally {
                release.countDown()
            }
            assertEquals(4, refresh.await().refreshedProviderCount)
            assertEquals(4, requests.size)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    @Test
    fun batchSkipsBothMissingAndUnconfirmedKeyOrigins() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        server.createContext("/") {
            requests.incrementAndGet()
            it.json("""{"data":[]}""")
        }
        server.start()
        try {
            val fixture = configureFixture(server).withRequestConfiguration(null)
            val empty = fixture.copy(id = "fixture-empty", name = "Missing", apiKey = "")
            val unconfirmed = fixture.copy(baseUrl = "http://localhost:${server.address.port}/v1")
                .withRequestConfiguration(fixture)
            AiProviderStore.saveProviders(AiDefaultProviders.all().map { it.copy(enabled = false) } + empty + unconfirmed)
            val result = AiManager.refreshEnabledProviderModels()
            assertEquals(setOf("Missing", fixture.name), result.missingKeyProviders.toSet())
            assertEquals(0, requests.get())
        } finally {
            server.stop(0)
        }
    }

    private fun configureFixture(server: HttpServer): AiProviderSetting {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val fixture = AiProviderSetting(
            id = "workflow-test", name = "Workflow", type = AiProviderType.OPENAI, builtIn = false,
            baseUrl = "http://127.0.0.1:${server.address.port}/v1", apiKey = "fixture-only", timeoutSeconds = 5,
        )
        AiProviderStore.saveProviders(AiDefaultProviders.all().map { it.copy(enabled = false) } + fixture)
        return fixture
    }

    private suspend fun withAssistantReplies(
        test: suspend (AiProviderSetting, AtomicReference<String>, LinkedBlockingQueue<JsonObject>) -> Unit,
    ) {
        val reply = AtomicReference("")
        val requests = LinkedBlockingQueue<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val request = JsonParser.parseString(exchange.requestBody.use { it.readBytes().decodeToString() }).asJsonObject
            requests += request
            if (request.get("stream").asBoolean) {
                val body = reply.get().encodeToByteArray()
                exchange.responseHeaders.set("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } else {
                exchange.json(reply.get())
            }
        }
        server.start()
        try {
            test(configureFixture(server), reply, requests)
        } finally {
            server.stop(0)
        }
    }

    private fun selectFixtureAssistant(fixture: AiProviderSetting, stream: Boolean) {
        val model = AiModelRegistry.enrich(AiModel("gpt-4o"))
        AiProviderStore.saveProvider(fixture.copy(streamResponseEnabled = stream).withFetchedModels(listOf(model)))
        assertTrue(AiConfig.saveAssistantModel(fixture.id, model.id))
    }

    private fun sse(vararg frames: String): String =
        frames.joinToString(separator = "\n\n", postfix = "\n\n") { "data: $it" }

    private fun messages() = mutableListOf(JsonObject().apply {
        addProperty("role", "user")
        addProperty("content", "Reply with OK.")
    })

    private fun HttpExchange.json(content: String) {
        val body = content.encodeToByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(200, body.size.toLong())
        responseBody.use { it.write(body) }
    }
}
