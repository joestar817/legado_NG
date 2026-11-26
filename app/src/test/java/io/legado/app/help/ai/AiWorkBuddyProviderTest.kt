package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.help.http.NetworkLog
import io.legado.app.ui.config.iconRes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiWorkBuddyProviderTest {
    private val preset = AiDefaultProviders.all().single { it.isWorkBuddyAccountProvider() }

    @Test
    fun catalogueRefreshKeepsAnExplicitToolOptOut() {
        val configured = preset.copy(workBuddyAccountId = "fixture")
        val endpoint = configured.modelConfigurationKey()
        val fresh = parseWorkBuddyModels(json(CATALOG).getAsJsonObject("data"), endpoint).single()
        assertTrue(AiModelAbility.TOOL in fresh.abilities)
        val edited = fresh.copy(
            abilities = emptyList(),
            modelSettings = AiModelSettings(
                catalogEndpoint = endpoint,
                capabilityOverrides = setOf(AiModelCapabilityField.TOOL_CALLING),
            ),
        )
        val refreshed = configured.copy(models = listOf(edited)).withFetchedModels(listOf(fresh))
        assertFalse(AiModelAbility.TOOL in refreshed.effectiveModelCapabilities(refreshed.models.single()).abilities)
    }

    @Test
    fun cancelledRequestPersistsRotatedCredentialsBeforeReleasingTheAccount() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val deliver = CountDownLatch(1)
        val storage = Storage(account(expiresAt = 0))
        val http = mockHttp { request ->
            assertEquals("/v2/plugin/auth/token/refresh", request.url.encodedPath)
            started.complete(Unit)
            check(deliver.await(5, TimeUnit.SECONDS))
            Reply(tokens(generation = 2, expiresIn = 3600))
        }
        val sessions = AiWorkBuddySessions(storage, { http })
        val job = async {
            sessions.withAccount("fixture") { error("A cancelled caller must not reach inference") }
        }
        try {
            withTimeout(5_000) { started.await() }
            job.cancel()
        } finally {
            deliver.countDown()
        }
        job.join()
        assertEquals("access-2", storage.read()?.accessToken)
        assertEquals("refresh-2", storage.read()?.refreshToken)
    }

    @Test
    fun storyboardOutputLimitUsesTheSelectedWorkBuddyCatalogEntry() {
        val models = listOf(
            AiModel("hy4flash-free", modelInfo = AiModelInfo(
                endpoint = WORK_BUDDY_API,
                maxOutputTokens = AiModelValue(64_000)
            )),
            AiModel("hy4std", modelInfo = AiModelInfo(
                endpoint = WORK_BUDDY_API,
                maxOutputTokens = AiModelValue(32_000)
            )),
            AiModel("unknown-output")
        )
        val provider = preset.copy(models = models)

        assertEquals(64_000, provider.workBuddyStoryboardOutputLimit("hy4flash-free"))
        assertEquals(32_000, provider.workBuddyStoryboardOutputLimit("hy4std"))
        assertNull(provider.workBuddyStoryboardOutputLimit("unknown-output"))
        assertNull(provider.copy(id = "custom").workBuddyStoryboardOutputLimit("hy4flash-free"))
    }

    @Test
    fun accountTextGenerationCollectsTheStreamWithoutAssistantTools() = runBlocking {
        val output = """{"units":[]}"""
        val http = mockHttp { request ->
            assertEquals(WORK_BUDDY_CHAT_PATH, request.url.encodedPath)
            assertEquals("Bearer access-1", request.header("Authorization"))
            assertEquals("AI听书 · 分镜归因", request.tag(NetworkLog.SourceLabel::class.java)?.value)
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8().let(::json)
            assertEquals("hy4", body.get("model").asString)
            assertTrue(body.get("stream").asBoolean)
            assertEquals(4096, body.get("max_tokens").asInt)
            assertEquals(
                listOf(message("system", "Return only JSON."), message("user", "Label this passage.")),
                body.getAsJsonArray("messages").map { it.asJsonObject },
            )
            assertFalse(body.has("tools"))
            streamReply("{", output.drop(1), reasoning = "Classifying the passage")
        }
        val sessions = AiWorkBuddySessions(Storage(account()), { http })
        val chat = AiChatClient(workBuddy = sessions) { http }
        val result = chat.generateText(
            setting = preset.copy(workBuddyAccountId = "fixture", streamResponseEnabled = false),
            model = AiModel("hy4", abilities = listOf(AiModelAbility.TOOL)),
            messages = listOf(
                AiMessage(AiMessage.Role.SYSTEM, "Return only JSON."),
                AiMessage(AiMessage.Role.USER, "Label this passage."),
            ),
            params = AiTextParams(maxTokens = 4096, jsonResponse = true, networkLogSource = "AI听书 · 分镜归因"),
        )
        assertEquals(output, result.content)
        assertEquals("hy4", result.model)
        assertEquals("stop", result.finishReason)
        assertEquals("Classifying the passage", result.reasoning)
    }

    @Test
    fun accountTextGenerationRejectsAnInterruptedWorkBuddyStream() {
        val http = mockHttp {
            Reply(
                """data: {"choices":[{"delta":{"content":"partial text"}}]}""" + "\n\n",
                type = "text/event-stream",
            )
        }
        val sessions = AiWorkBuddySessions(Storage(account()), { http })
        val chat = AiChatClient(workBuddy = sessions) { http }
        val error = assertThrows(AiAssistantReplyEndException::class.java) {
            runBlocking {
                chat.generateText(
                    setting = preset.copy(workBuddyAccountId = "fixture"),
                    model = AiModel("hy4"),
                    messages = listOf(AiMessage(AiMessage.Role.USER, "Clean this passage.")),
                    params = AiTextParams(),
                )
            }
        }
        assertEquals(AiAssistantReplyEndReason.INTERRUPTED, error.reason)
    }

    @Test
    fun sendsToolDefinitionsAndResultsForAWorkBuddyModelWithToolsEnabled() = runBlocking {
        val toolName = "mcp__legado__use_skill"
        val tool = json("""{"type":"function","function":{"name":"$toolName","parameters":{"type":"object"}}}""")
        val toolCall = json(
            """{"id":"skill-call","type":"function","function":{"name":"$toolName","arguments":"{\"name\":\"bookshelf_management\"}"}}"""
        )
        val toolResult = message("tool", """{"ok":true}""").apply {
            addProperty("tool_call_id", "skill-call")
        }
        val user = message("user", "Load the bookshelf skill.")
        var requests = 0
        val http = mockHttp { request ->
            assertEquals(WORK_BUDDY_CHAT_PATH, request.url.encodedPath)
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8().let(::json)
            assertEquals(listOf(tool), body.getAsJsonArray("tools")?.asList())
            val sentMessages = body.getAsJsonArray("messages").map { it.asJsonObject }
            if (++requests == 1) {
                assertEquals(listOf(user), sentMessages)
                val delta = toolCall.deepCopy().apply { addProperty("index", 0) }
                Reply(
                    """data: {"model":"hy4","choices":[{"delta":{"tool_calls":[$delta]},"finish_reason":"tool_calls"}]}""" +
                        "\n\ndata: [DONE]\n\n",
                    type = "text/event-stream",
                )
            } else {
                assertEquals(3, sentMessages.size)
                assertEquals(listOf(toolCall), sentMessages[1].getAsJsonArray("tool_calls").asList())
                assertEquals(toolResult, sentMessages.last())
                streamReply("Skill ", "loaded.")
            }
        }
        val sessions = AiWorkBuddySessions(Storage(account()), { http })
        val setting = preset.copy(workBuddyAccountId = "fixture")
        val model = AiModel("hy4", abilities = listOf(AiModelAbility.TOOL))
        with(AiChatClient(workBuddy = sessions) { http }) {
            val toolTurn = http.completeChat(
                setting, model, listOf(user), listOf(tool), AiTextParams(), null, assistantTurn = true,
            )
            assertEquals(listOf(toolCall), toolTurn.toolCalls.asList())
            val reply = http.completeChat(
                setting, model, listOf(user, toolTurn.message, toolResult), listOf(tool), AiTextParams(), null,
                assistantTurn = true,
            )
            assertEquals("Skill loaded.", reply.content)
        }
        assertEquals(2, requests)
    }

    @Test
    fun omitsToolDefinitionsForAWorkBuddyModelWithoutTheToolCapability() = runBlocking {
        val http = mockHttp { request ->
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8().let(::json)
            assertFalse(body.has("tools"))
            streamReply("Text ", "reply.")
        }
        val sessions = AiWorkBuddySessions(Storage(account()), { http })
        with(AiChatClient(workBuddy = sessions) { http }) {
            val reply = http.completeChat(
                setting = preset.copy(workBuddyAccountId = "fixture"),
                model = AiModel("hy4"),
                messages = listOf(message("user", "Hello")),
                tools = listOf(json("""{"type":"function","function":{"name":"lookup"}}""")),
                params = AiTextParams(),
                conversationId = null,
                assistantTurn = true,
            )
            assertEquals("Text reply.", reply.content)
        }
    }

    @Test
    fun signsInListsModelsAndStreamsTwoTurnsAcrossTokenRenewal() = runBlocking {
        val storage = Storage()
        val requests = mutableListOf<String>()
        var polls = 0
        var chatCalls = 0
        var renewals = 0
        var browserUrl: String? = null
        val http = mockHttp { request ->
            val path = request.url.encodedPath
            requests += path
            when (path) {
                "/v2/plugin/auth/state" -> {
                    assertEquals("POST", request.method)
                    assertEquals("CLI", request.url.queryParameter("platform"))
                    Reply("""{"code":0,"data":{"state":"state + encoded","authUrl":"https://www.codebuddy.cn/login","expiresIn":60}}""")
                }
                "/v2/plugin/auth/token" -> {
                    assertEquals("state + encoded", request.url.queryParameter("state"))
                    assertEquals("https://www.codebuddy.cn/login", browserUrl)
                    if (++polls == 1) Reply("""{"code":11217,"msg":"login ing..."}""")
                    else Reply(tokens(1, 600))
                }
                "/v2/plugin/login/account" -> {
                    assertEquals("Bearer access-1", request.header("Authorization"))
                    Reply("""{"code":0,"data":{"nickname":"Demo","uid":"user-fixture","enterpriseId":"team-fixture"}}""")
                }
                WORK_BUDDY_MODELS_PATH -> {
                    assertEquals("Bearer access-1", request.header("Authorization"))
                    Reply(CATALOG)
                }
                WORK_BUDDY_CHAT_PATH -> {
                    assertEquals("user-fixture", request.header("X-User-Id"))
                    assertEquals("team-fixture", request.header("X-Enterprise-Id"))
                    assertEquals("www.codebuddy.cn", request.header("X-Domain"))
                    val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8().let(::json)
                    assertTrue(body.get("stream").asBoolean)
                    assertEquals("hy4", body.get("model").asString)
                    assertFalse(body.has("tools"))
                    val messages = body.getAsJsonArray("messages").map { it.asJsonObject }
                    when (++chatCalls) {
                        1 -> {
                            assertEquals("Bearer access-1", request.header("Authorization"))
                            assertEquals(listOf("user"), messages.map { it.get("role").asString })
                            streamReply("blue", " mug", reasoning = "remember the color")
                        }
                        2, 3 -> {
                            assertEquals(listOf("user", "assistant", "user"), messages.map { it.get("role").asString })
                            assertEquals("blue mug", messages[1].get("content").asString)
                            assertEquals("remember the color", messages[1].get("reasoning_content").asString)
                            assertEquals("What color?", messages[2].get("content").asString)
                            if (chatCalls == 2) {
                                assertEquals("Bearer access-1", request.header("Authorization"))
                                Reply("", status = 401)
                            } else {
                                assertEquals("Bearer access-2", request.header("Authorization"))
                                streamReply("It is ", "blue")
                            }
                        }
                        else -> error("Unexpected additional conversation request")
                    }
                }
                "/v2/plugin/auth/token/refresh" -> {
                    assertEquals("POST", request.method)
                    assertNull(request.header("Authorization"))
                    assertEquals("refresh-${++renewals}", request.header("X-Refresh-Token"))
                    Reply(tokens(renewals + 1, 600))
                }
                else -> error("Unexpected path: $path")
            }
        }
        var nowSeconds = 1000L
        val sessions = AiWorkBuddySessions(storage, { http }, nowSeconds = { nowSeconds })
        val account = sessions.signIn { browserUrl = it }
        assertEquals("Demo", account.nickname)
        assertEquals(2, polls)
        assertEquals(1600L, account.expiresAt)
        val provider = preset.copy(workBuddyAccountId = account.id)
        val model = sessions.listModels(provider).single()
        assertEquals("hy4", model.id)
        assertTrue(model.isTextChatModel())
        assertEquals(provider.modelConfigurationKey(), model.modelInfo?.endpoint)
        assertEquals(64_000, model.modelInfo?.contextWindow?.value)
        val chat = AiChatClient(workBuddy = sessions) { http }
        val firstRequest = message("user", "Remember a blue mug.")
        val firstUpdates = mutableListOf<AiChatStreamUpdate>()
        val first = chat.run {
            http.completeChat(
                provider, model, listOf(firstRequest), emptyList(), AiTextParams(), "fixture",
                onStreamUpdate = { firstUpdates.add(it) }, assistantTurn = true,
            )
        }
        assertEquals("blue mug", first.content)
        assertEquals(listOf("blue", "blue mug"), firstUpdates.map { it.content })
        assertEquals("remember the color", first.reasoning)
        val secondUpdates = mutableListOf<AiChatStreamUpdate>()
        val second = chat.run {
            http.completeChat(
                provider, model, listOf(firstRequest, first.message, message("user", "What color?")),
                emptyList(), AiTextParams(), "fixture", onStreamUpdate = { secondUpdates.add(it) }, assistantTurn = true,
            )
        }
        assertEquals("It is blue", second.content)
        assertEquals(listOf("It is ", "It is blue"), secondUpdates.map { it.content })
        assertEquals("refresh-2", storage.read()?.refreshToken)
        assertEquals(1, renewals)
        nowSeconds = 1545L
        assertEquals("access-3", sessions.withAccount(account.id) { it.accessToken })
        assertEquals("refresh-3", storage.read()?.refreshToken)
        assertEquals(account.id, storage.read()?.id)
        assertEquals(2, renewals)
        assertEquals(3, chatCalls)
        assertEquals(10, requests.size)
        val saved = Gson().toJson(storage.read()).let(::json)
        assertEquals("refresh-3", saved.get("refresh_token").asString)
        sessions.signOut(account.id)
        assertFalse(sessions.hasCredentials(account.id))
    }

    @Test
    fun proactivelyRenewsAnExpiringTokenAndDoesNotRetryAForbiddenRequest() = runBlocking {
        val storage = Storage(account(expiresAt = 30))
        var refreshCalls = 0
        var modelCalls = 0
        val http = mockHttp { request ->
            when (request.url.encodedPath) {
                "/v2/plugin/auth/token/refresh" -> {
                    refreshCalls++
                    assertEquals("refresh-1", request.header("X-Refresh-Token"))
                    Reply(tokens(2, 600))
                }
                WORK_BUDDY_MODELS_PATH -> {
                    modelCalls++
                    assertEquals("Bearer access-2", request.header("Authorization"))
                    Reply("", status = 403)
                }
                else -> error("Unexpected request")
            }
        }
        val sessions = AiWorkBuddySessions(storage, { http }, nowSeconds = { 50 })
        val result = runCatching { sessions.listModels(preset.copy(workBuddyAccountId = "fixture")) }
        assertEquals(403, (result.exceptionOrNull() as? AiWorkBuddyException)?.status)
        assertEquals(1, refreshCalls)
        assertEquals(1, modelCalls)
        assertEquals(650L, storage.read()?.expiresAt)
        assertEquals("refresh-2", storage.read()?.refreshToken)
    }

    @Test
    fun rejectedRefreshRequiresSignIn() = runBlocking {
        val rejected = listOf(
            Reply("", status = 401),
            Reply("""{"code":12153,"msg":"refresh token is invalid"}"""),
        )
        rejected.forEach { reply ->
            val storage = Storage(account(expiresAt = 30))
            val http = mockHttp { request ->
                assertEquals("/v2/plugin/auth/token/refresh", request.url.encodedPath)
                reply
            }
            val sessions = AiWorkBuddySessions(storage, { http }, nowSeconds = { 50L })
            val result = runCatching { sessions.withAccount("fixture") { error("Unexpected account request") } }
            assertEquals(
                R.string.ai_workbuddy_session_expired,
                (result.exceptionOrNull() as? AiWorkBuddyException)?.messageRes,
            )
            assertFalse(sessions.hasCredentials("fixture"))
        }
    }

    @Test
    fun refusesUnrelatedBrowserHostsBeforeOpeningThem() = runBlocking {
        val http = mockHttp { request ->
            assertEquals("/v2/plugin/auth/state", request.url.encodedPath)
            Reply("""{"code":0,"data":{"state":"fixture","authUrl":"https://workbuddy.cn.example.test/login"}}""")
        }
        val storage = Storage()
        val sessions = AiWorkBuddySessions(storage, { http })
        val result = runCatching { sessions.signIn { error("Unexpected browser launch") } }
        assertEquals(R.string.ai_workbuddy_invalid_response, (result.exceptionOrNull() as? AiWorkBuddyException)?.messageRes)
        assertNull(storage.read())
    }

    @Test
    fun stopsPollingOnAnUnexpectedLoginError() = runBlocking {
        val storage = Storage()
        var polls = 0
        val http = mockHttp { request ->
            when (request.url.encodedPath) {
                "/v2/plugin/auth/state" ->
                    Reply("""{"code":0,"data":{"state":"fixture","authUrl":"https://www.codebuddy.cn/login"}}""")
                "/v2/plugin/auth/token" -> {
                    polls++
                    Reply("""{"code":99999,"msg":"authorization rejected"}""")
                }
                else -> error("Unexpected request")
            }
        }
        val sessions = AiWorkBuddySessions(storage, { http })
        val result = runCatching { sessions.signIn {} }
        assertEquals(R.string.ai_workbuddy_request_rejected, (result.exceptionOrNull() as? AiWorkBuddyException)?.messageRes)
        assertEquals(1, polls)
        assertNull(storage.read())
    }

    @Test
    fun fixesWorkBuddyToItsOwnStreamingEndpointAndKeepsAccountCatalogsSeparate() {
        val model = AiModel("native-chat")
        val setting = preset.copy(workBuddyAccountId = "fixture", baseUrl = "https://example.test", useResponsesApi = true)
        assertEquals(listOf(AiModelGenerationTestProtocol.CHAT_COMPLETIONS), setting.modelGenerationTestProtocols())
        assertEquals("$WORK_BUDDY_API$WORK_BUDDY_CHAT_PATH", setting.generationApiPath(AiModelGenerationTestProtocol.CHAT_COMPLETIONS))
        assertEquals(AiModelGenerationIssue.STREAMING_REQUIRED, setting.resolveModelGenerationRoute(model, stream = false).issue)
        assertNull(setting.generationApiPath(AiModelGenerationTestProtocol.RESPONSES))
        assertFalse(setting.matchesCatalogRequest(setting.copy(workBuddyAccountId = "another-account")))
        assertFalse(setting.isOpenAiAccountProvider())
        assertNull(setting.availableCodexImageProvider())
    }

    @Test
    fun reportsAnUpstreamBusinessErrorWithoutReplayingTheChat() {
        val storage = Storage(account())
        var requests = 0
        val http = mockHttp { request ->
            assertEquals(WORK_BUDDY_CHAT_PATH, request.url.encodedPath)
            requests++
            Reply("""{"code":11158,"msg":"request denied"}""")
        }
        val sessions = AiWorkBuddySessions(storage, { http })
        val chat = AiChatClient(workBuddy = sessions) { http }
        val error = assertThrows(AiWorkBuddyException::class.java) {
            runBlocking {
                chat.run {
                    http.completeChat(
                        preset.copy(workBuddyAccountId = "fixture"), AiModel("native-chat"),
                        listOf(message("user", "Hi")), emptyList(), AiTextParams(), "fixture",
                    )
                }
            }
        }
        assertEquals(R.string.ai_workbuddy_request_rejected, error.messageRes)
        assertEquals(1, requests)
    }

    @Test
    fun readsPersonalCreditsWithTheExistingAccountAndUsesTheCurrentCycle() = runBlocking {
        val storage = Storage(account())
        val http = mockHttp { request ->
            assertEquals(WORK_BUDDY_CREDITS_URL, request.url.toString())
            assertEquals("POST", request.method)
            assertEquals("Bearer access-1", request.header("Authorization"))
            val requestBody = Buffer().also { request.body!!.writeTo(it) }.readUtf8().let(::json)
            assertEquals("p_tcaca", requestBody.get("ProductCode").asString)
            assertEquals("2026-11-25 08:00:00", requestBody.get("PackageEndTimeRangeBegin").asString)
            Reply("""
                {"code":0,"data":{"Response":{"Data":{"Accounts":[
                    {"CapacitySize":4000,"CapacityRemain":2000,"CycleCapacitySize":1000,
                        "CycleCapacityRemain":800,"CycleCapacityUsed":250},
                    {"CapacitySize":500,"CapacityRemain":300}
                ]}}}}
            """.trimIndent())
        }
        val sessions = AiWorkBuddySessions(storage, { http }, nowSeconds = { 1_795_564_800L })
        assertEquals(AiWorkBuddyCredits(remaining = 1050, total = 1500), sessions.queryCredits("fixture"))
    }

    @Test
    fun rejectingCreditsDoesNotRenewAnOtherwiseValidSession() = runBlocking {
        val storage = Storage(account())
        val http = mockHttp { request ->
            assertEquals(WORK_BUDDY_CREDITS_URL, request.url.toString())
            Reply("", status = 401)
        }
        val sessions = AiWorkBuddySessions(storage, { http })
        val result = runCatching { sessions.queryCredits("fixture") }
        assertEquals(401, (result.exceptionOrNull() as? AiWorkBuddyException)?.status)
        assertEquals("access-1", storage.read()?.accessToken)
        assertTrue(sessions.hasCredentials("fixture"))
    }

    @Test
    fun checksStatusBeforeClaimingAndSerializesTwoClaimAttempts() = runBlocking {
        val requests = mutableListOf<String>()
        var checkedIn = false
        val storage = Storage(account().copy(uid = "fixture-user", domain = "www.codebuddy.cn"))
        val http = mockHttp { request ->
            val path = request.url.encodedPath
            requests += path
            assertEquals("POST", request.method)
            assertEquals("Bearer access-1", request.header("Authorization"))
            assertEquals("fixture-user", request.header("X-User-Id"))
            assertEquals("www.codebuddy.cn", request.header("X-Domain"))
            val body = requireNotNull(request.body)
            assertEquals("{}", Buffer().also(body::writeTo).readUtf8())
            when (path) {
                WORK_BUDDY_CHECK_IN_STATUS_PATH -> Reply("""
                    {"code":0,"data":{"active":true,"today_checked_in":$checkedIn,"daily_credit":100,"today_credit":200}}
                """.trimIndent())
                WORK_BUDDY_CHECK_IN_PATH -> {
                    assertTrue(body.isOneShot())
                    checkedIn = true
                    Reply("""{"code":0,"data":{"credit":200,"streak_days":1}}""")
                }
                else -> error("Unexpected request: $path")
            }
        }
        val sessions = AiWorkBuddySessions(storage, { http })
        assertEquals(AiWorkBuddyCheckIn(AiWorkBuddyCheckInStatus.AVAILABLE, 100L), sessions.queryCheckInStatus("fixture"))

        val first = async { sessions.claimDailyCredits("fixture") }
        val second = async { sessions.claimDailyCredits("fixture") }
        val results = listOf(first.await(), second.await())
        assertTrue(results.all { it.checkIn == AiWorkBuddyCheckIn(AiWorkBuddyCheckInStatus.CLAIMED, 100L) })
        assertEquals(listOf(200L), results.mapNotNull { it.awardedCredits })
        assertEquals(
            listOf(WORK_BUDDY_CHECK_IN_STATUS_PATH, WORK_BUDDY_CHECK_IN_STATUS_PATH,
                WORK_BUDDY_CHECK_IN_PATH, WORK_BUDDY_CHECK_IN_STATUS_PATH),
            requests,
        )
    }

    @Test
    fun skipsClaimWhenAlreadyClaimedOrUnavailable() = runBlocking {
        val cases = listOf(
            """{"active":true,"today_checked_in":true,"today_credit":100}""" to AiWorkBuddyCheckInStatus.CLAIMED,
            """{"active":false}""" to AiWorkBuddyCheckInStatus.UNAVAILABLE,
            """{"active":true,"today_checked_in":false,"today_credit":0}""" to AiWorkBuddyCheckInStatus.UNAVAILABLE,
        )
        cases.forEach { (data, status) ->
            val http = mockHttp { request ->
                assertEquals(WORK_BUDDY_CHECK_IN_STATUS_PATH, request.url.encodedPath)
                Reply("""{"code":0,"data":$data}""")
            }
            val sessions = AiWorkBuddySessions(Storage(account()), { http })
            assertEquals(AiWorkBuddyCheckInResult(AiWorkBuddyCheckIn(status)), sessions.claimDailyCredits("fixture"))
        }
    }

    @Test
    fun missingOrInvalidCheckInStatusNeverAllowsClaiming() = runBlocking {
        listOf("""{"active":true}""", """{"active":true,"today_checked_in":"false"}""").forEach { data ->
            val http = mockHttp { request ->
                assertEquals(WORK_BUDDY_CHECK_IN_STATUS_PATH, request.url.encodedPath)
                Reply("""{"code":0,"data":$data}""")
            }
            val sessions = AiWorkBuddySessions(Storage(account()), { http })
            val result = runCatching { sessions.claimDailyCredits("fixture") }
            assertEquals(R.string.ai_workbuddy_invalid_response, (result.exceptionOrNull() as? AiWorkBuddyException)?.messageRes)
        }
    }

    @Test
    fun rejectingClaimDoesNotReissueItOrRenewTheSession() = runBlocking {
        val requests = mutableListOf<String>()
        val storage = Storage(account())
        val http = mockHttp { request ->
            val path = request.url.encodedPath
            requests += path
            when (path) {
                WORK_BUDDY_CHECK_IN_STATUS_PATH -> Reply("""{"code":0,"data":{"active":true,"today_checked_in":false}}""")
                WORK_BUDDY_CHECK_IN_PATH -> Reply("", status = 401)
                else -> error("Unexpected request: $path")
            }
        }
        val sessions = AiWorkBuddySessions(storage, { http })
        val result = runCatching { sessions.claimDailyCredits("fixture") }
        assertEquals(401, (result.exceptionOrNull() as? AiWorkBuddyException)?.status)
        assertEquals(listOf(WORK_BUDDY_CHECK_IN_STATUS_PATH, WORK_BUDDY_CHECK_IN_PATH), requests)
        assertEquals("access-1", storage.read()?.accessToken)
        assertTrue(sessions.hasCredentials("fixture"))
    }

    @Test
    fun mapsHyModelsToTheHunyuanIconAndKeepsChannelCapabilities() {
        val models = parseWorkBuddyModels(json("""
            {"agents":[{"name":"cli","models":["hy4","hy4-flash","hy4-flash-f","space-bunny","auto"]}],
             "models":[{"id":"hy4","name":"Hy4","supportsReasoning":true},
                {"id":"hy4-flash","name":"Hy4 flash","supportsReasoning":true},
                {"id":"hy4-flash-f","name":"Hy4 flash","supportsReasoning":true},
                {"id":"space-bunny","name":"Space-Bunny"},{"id":"auto","name":"Auto"}]}
        """.trimIndent()), preset.modelConfigurationKey())
        assertEquals(R.drawable.ic_provider_workbuddy, preset.iconRes())
        assertEquals(listOf("hy4", "hy4-flash", "hy4-flash-f"), models.take(3).map { it.id })
        models.take(3).forEach { model ->
            assertTrue(model.isTextChatModel())
            assertEquals(listOf(AiModelAbility.TOOL, AiModelAbility.REASONING), model.abilities)
            assertEquals(true, model.modelInfo?.toolCalling?.value)
            assertEquals(R.drawable.ic_model_hunyuan, model.iconRes(preset.iconRes()))
        }
        models.drop(3).forEach { model ->
            assertEquals(R.drawable.ic_provider_workbuddy, model.iconRes(preset.iconRes()))
        }
    }

    @Test
    fun keepsCatalogCreditMultipliersByModelIdAndClearsMissingRatesOnRefresh() {
        val provider = preset.copy(workBuddyAccountId = "fixture")
        val data = json("""
            {"agents":[{"name":"cli","models":["hy4flash-free","hy4flash","hy4std","auto","unknown-rate"]}],
             "models":[{"id":"hy4flash","name":"Hy4 flash","credits":"x0.03","maxInputTokens":960000},
                {"id":"hy4flash-free","name":"Hy4 flash","credits":"x0.00","maxInputTokens":960000},
                {"id":"hy4std","name":"Hy4","credits":"x0.29 credits"},
                {"id":"auto","name":"Auto"},{"id":"unknown-rate","credits":"unknown"}]}
        """.trimIndent())
        val source = provider.modelConfigurationKey()
        val models = parseWorkBuddyModels(data, source)
        val gson = Gson()
        val cached = gson.fromJson(gson.toJson(provider.copy(models = models)), AiProviderSetting::class.java)
        assertEquals(
            mapOf(
                "hy4flash-free" to "0.00×",
                "hy4flash" to "0.03×",
                "hy4std" to "0.29×",
                "auto" to null,
                "unknown-rate" to null,
            ),
            cached.models.associate { it.id to cached.effectiveModelInfo(it).creditMultiplierLabel },
        )

        data.getAsJsonArray("models")[0].asJsonObject.remove("credits")
        val refreshed = cached.mergeModelInfo(parseWorkBuddyModels(data, source)).associateBy { it.id }
        assertNull(refreshed.getValue("hy4flash").modelInfo?.creditMultiplierLabel)
        assertEquals("0.00×", refreshed.getValue("hy4flash-free").modelInfo?.creditMultiplierLabel)
    }

    private fun mockHttp(respond: (Request) -> Reply): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        assertTrue(request.url.isHttps)
        val host = if (request.url.toString() == WORK_BUDDY_CREDITS_URL) "www.codebuddy.cn" else "copilot.tencent.com"
        assertEquals(host, request.url.host)
        assertEquals("Reading-NG/Android", request.header("User-Agent"))
        assertNull(request.header("X-CodeBuddy-Request"))
        if (request.url.encodedPath != "/v2/plugin/auth/token/refresh") assertNull(request.header("X-Refresh-Token"))
        val reply = respond(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(reply.status).message("Fixture").body(reply.body.toResponseBody(reply.type.toMediaType()))
            .build()
    }.build()

    private fun streamReply(first: String, last: String, reasoning: String? = null): Reply {
        fun frame(content: String, finish: String, thought: String?) = JsonObject().apply {
            addProperty("model", "hy4")
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("finish_reason", finish)
                    add("delta", JsonObject().apply {
                        addProperty("content", content)
                        thought?.let { addProperty("reasoning_content", it) }
                    })
                })
            })
        }
        val frames = listOf(frame(first, "", reasoning), frame(last, "stop", null))
        return Reply(frames.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n", type = "text/event-stream")
    }

    private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject

    private fun tokens(generation: Int, expiresIn: Int): String =
        """{"code":0,"data":{"accessToken":"access-$generation","refreshToken":"refresh-$generation","expiresIn":$expiresIn,"domain":"www.codebuddy.cn"}}"""

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }

    private fun account(expiresAt: Long? = null) =
        AiWorkBuddyAccount("fixture", null, null, null, null, "access-1", "refresh-1", expiresAt)

    private data class Reply(val body: String, val status: Int = 200, val type: String = "application/json")

    private class Storage(@Volatile private var value: AiWorkBuddyAccount? = null) : AiWorkBuddyAccountStorage {
        override fun read() = value
        override fun write(account: AiWorkBuddyAccount?) {
            value = account
        }
    }

    companion object {
        private val CATALOG = """
            {"code":0,"data":{
                "agents":[{"name":"cli","models":["hy4","not-enabled","media","completion-tiny","hy4"]}],
                "models":[{"id":"hy4","name":"Hy4","maxInputTokens":64000,"maxOutputTokens":16000},
                    {"id":"not-enabled","disabled":true},{"id":"media","tags":["text-to-image"]},
                    {"id":"completion-tiny"},{"id":"outside-cli"}]
            }}
        """.trimIndent()
    }
}
