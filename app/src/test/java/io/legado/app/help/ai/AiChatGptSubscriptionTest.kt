package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class AiChatGptSubscriptionTest {
    private val provider = AiDefaultProviders.all().first { it.isChatGptSubscription() }
        .copy(chatGptAccountId = "oaiapp_fixture")

    @Test
    fun recognizesTheSharedLegacyNativeLabelOnlyForChatGptResults() {
        val history = AiWebActivity(providerLabel = "DeepSeek 内置联网")

        assertTrue(history.hasLegacyChatGptNativeLabel(provider.id))
        assertTrue(history.copy(providerLabel = "DeepSeek built-in web access")
            .hasLegacyChatGptNativeLabel(provider.id))
        assertTrue(history.copy(inlineCitations = listOf(AiWebCitation(1, "https://example.test/book")))
            .hasLegacyChatGptNativeLabel(null))
        assertFalse(history.hasLegacyChatGptNativeLabel("deepseek"))
        assertFalse(history.hasLegacyChatGptNativeLabel(null))
        assertFalse(history.copy(providerLabel = "DeepSeek · 外置联网").hasLegacyChatGptNativeLabel(provider.id))
        assertFalse(history.copy(providerLabel = "ChatGPT 内置联网").hasLegacyChatGptNativeLabel(provider.id))
    }

    @Test
    fun rememberedAccountDistinguishesSignedOutFromAuthorizationFailureOnReload() {
        val gson = Gson()
        val stored = """{"client_id":"oaiapp_fixture","subject":"reader","email":"reader@example.test"}"""
        val signedOut = gson.fromJson(stored, AiChatGptAccount::class.java)

        assertFalse(signedOut.needsReauthorization())
        val rejected = signedOut.copy(reauthorizationRequired = true)
        val saved = gson.toJson(rejected)
        assertTrue(json(saved).get("reauthorization_required").asBoolean)
        assertTrue(gson.fromJson(saved, AiChatGptAccount::class.java).needsReauthorization())
    }

    @Test
    fun quotaAndTemporaryErrorsDoNotRequestReauthorization() {
        val rejected = R.string.ai_chatgpt_reauthorize_required

        assertEquals(rejected, chatGptRequestError(401, null).messageRes)
        assertEquals(rejected, chatGptRequestError(null, json(
            """{"error":{"code":"subscription_sharing_invalid_user"}}"""
        )).messageRes)
        assertEquals(R.string.ai_chatgpt_rate_limit, chatGptRequestError(429, null).messageRes)
        assertEquals(R.string.ai_chatgpt_temporarily_unavailable, chatGptRequestError(503, null).messageRes)
    }

    @Test
    fun rejectedAccessTokenKeepsRenewableSessionAvailableForTheNextRequest() {
        val scopes = setOf(CHATGPT_PLAN_SCOPE)
        val account = AiChatGptAccount("oaiapp_fixture", "reader", null, AiChatGptTokens("access", "refresh", 3_600, scopes))

        assertNull(account.afterAccessTokenRejected("previous-access"))
        val retry = checkNotNull(account.afterAccessTokenRejected("access"))
        val tokens = checkNotNull(retry.tokens)
        assertEquals(0L, tokens.expiresAt)
        assertEquals("refresh", tokens.refreshToken)
        assertTrue(tokens.allowsPlan)
        assertFalse(retry.needsReauthorization(nowSeconds = 1_000))

        val withoutRefresh = account.copy(tokens = AiChatGptTokens("access", null, 3_600, scopes))
        assertTrue(checkNotNull(withoutRefresh.afterAccessTokenRejected("access")).needsReauthorization(1_000))
    }

    @Test
    fun authorizationUsesTheAppRegistrationAndPkce() {
        val attempt = AiChatGptAuthorization("state", "nonce", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        val redirect = "http://127.0.0.1:13301/auth/callback"
        val fresh = attempt.url("urn:uuid:local", redirect, null, null)
        assertEquals(CHATGPT_DYNAMIC_CLIENT, fresh.queryParameter("client_id"))
        assertEquals("Reading NG", fresh.queryParameter("agent_name_hint"))
        assertEquals("auth.openai.com", fresh.host)
        assertEquals(redirect, fresh.queryParameter("redirect_uri"))
        assertEquals(CHATGPT_API, fresh.queryParameter("resource"))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", fresh.queryParameter("code_challenge"))
        assertTrue(fresh.queryParameter("scope")!!.split(' ').contains(CHATGPT_PLAN_SCOPE))
        val returning = attempt.url("urn:uuid:local", redirect, "oaiapp_existing", "reader@example.test")
        assertEquals("oaiapp_existing", returning.queryParameter("client_id"))
        assertEquals("reader@example.test", returning.queryParameter("login_hint"))
        assertNull(returning.queryParameter("agent_name_hint"))
        assertNull(returning.queryParameter("prompt"))
        assertNull(fresh.queryParameter("prompt"))

        val reconsent = attempt.url("urn:uuid:local", redirect, "oaiapp_existing", "reader@example.test",
            requestPlanConsent = true)
        assertEquals("consent", reconsent.queryParameter("prompt"))
        assertEquals(returning.queryParameter("scope"), reconsent.queryParameter("scope"))
    }

    @Test
    fun loopbackKeepsListeningAfterAnInvalidCallbackAndReturnsOnlyTheMatchingCode() {
        runBlocking {
            AiChatGptLoopback { "Reading NG" }.use { loopback ->
                val waiting = async(Dispatchers.IO) { loopback.await("owned") }
                val client = OkHttpClient()
                fun hit(query: String): Int = client.newCall(Request.Builder()
                    .url(loopback.redirectUri + query).build()).execute().use { response ->
                    assertEquals("no-store", response.header("Cache-Control"))
                    assertEquals("Reading NG", response.body.string())
                    response.code
                }
                assertEquals(400, hit("?state=wrong&error=access_denied"))
                assertEquals(200, hit("?state=owned&code=auth-code&client_id=oaiapp_issued"))
                assertEquals(AiChatGptCallback.Code("auth-code", "oaiapp_issued"), withTimeout(2500) { waiting.await() })
            }
        }
        val base = "http://127.0.0.1:1010/auth/callback"
        assertNull(readChatGptCallback("$base?state=s&code=one&code=two".toHttpUrl(), "s"))
        assertEquals(AiChatGptCallback.Rejected(true),
            readChatGptCallback("$base?state=s&error=access_denied".toHttpUrl(), "s"))
    }

    @Test
    fun verifiesSignedIdentityWithIssuerAudienceExpiryAndNonce() {
        val signing = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rsa = signing.public as RSAPublicKey
        val keys = json("""{"keys":[{"kty":"RSA","alg":"RS256","kid":"fixture","n":"${rsa.modulus.toByteArray().b64()}","e":"${rsa.publicExponent.toByteArray().b64()}"}]}""")
        val claims = json("""{"iss":"$CHATGPT_ISSUER","aud":"oaiapp_issued","sub":"user1","exp":2000,"nonce":"local-nonce","email":"r@example.test"}""")
        fun token(payload: JsonObject, algorithm: String = "RS256"): String {
            val header = """{"alg":"$algorithm","kid":"fixture"}""".toByteArray().b64()
            val signed = "$header.${payload.toString().toByteArray().b64()}"
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(signing.private)
                update(signed.toByteArray())
            }.sign()
            return "$signed.${signature.b64()}"
        }
        val signed = token(claims)
        fun verify(jwt: String = signed, id: String = "oaiapp_issued", nonce: String = "local-nonce", now: Long = 1000) =
            verifyChatGptIdentity(jwt, keys, id, nonce, now)
        assertEquals(AiChatGptIdentity("user1", "r@example.test"), verify())
        assertThrows(AiChatGptException::class.java) { verify(id = "oaiapp_other") }
        assertThrows(AiChatGptException::class.java) { verify(nonce = "wrong") }
        assertThrows(AiChatGptException::class.java) { verify(now = 2000) }
        assertThrows(AiChatGptException::class.java) { verify(jwt = token(claims, "none")) }
        assertThrows(AiChatGptException::class.java) {
            verify(jwt = token(claims.deepCopy().apply { addProperty("iss", "https://example.test") }))
        }
        val tampered = signed.split('.').toMutableList().apply { this[2] = "AAAA" }.joinToString(".")
        assertThrows(AiChatGptException::class.java) { verify(jwt = tampered) }
    }

    @Test
    fun accountCatalogKeepsVisibleServerOrderAndConfirmedReasoning() {
        val rows = json("""{"models":[
            {"visibility":"list","slug":"gpt-6.2-sol","display_name":"ChatGPT Sol","supported_reasoning_levels":[{"effort":"xhigh"}],"context_window":128000},
            {"visibility":"hidden","slug":"hidden-model"},
            {"visibility":"list","slug":"gpt-6-luna","display_name":"ChatGPT Luna"},
            {"visibility":"list","slug":{},"display_name":true}
        ]}""")
        val models = parseChatGptModels(rows, provider.modelConfigurationKey())
        assertEquals(listOf("gpt-6.2-sol", "gpt-6-luna"), models.map(AiModel::id))
        assertEquals("ChatGPT Sol", models[0].name)
        assertEquals(listOf("xhigh"), models[0].modelInfo?.effortLevels?.value)
        assertTrue(models.all { it.isTextChatModel() })
        assertFalse(provider.modelConfigurationKey() == provider.copy(chatGptAccountId = "another").modelConfigurationKey())
        assertThrows(AiChatGptException::class.java) { parseChatGptModels(json("""{"data":[]}"""), "fixture") }
    }

    @Test
    fun accountCatalogPrefersTheReportedMaximumContextWindow() {
        val data = json("""{"models":[
            {"visibility":"list","slug":"gpt-5.6-terra","context_window":272000,"max_context_window":872000},
            {"visibility":"list","slug":"gpt-6.2-sol","context_window":500000},
            {"visibility":"list","slug":"gpt-6.2-astra","context_window":272000,"max_context_window":0},
            {"visibility":"list","slug":"gpt-6.1-terra","max_context_window":500000},
            {"visibility":"list","slug":"gpt-5.6-sol"}
        ]}""")
        val models = parseChatGptModels(data, provider.modelConfigurationKey())
        assertEquals(
            listOf(872_000, 500_000, 272_000, 500_000, null),
            models.map { provider.assistantContextWindowLimit(it) },
        )
    }

    @Test
    fun assistantResolvesToolsForConfirmedPlanModelsInAnExistingCatalog() {
        val ids = listOf(
            "gpt-6.2-sol", "gpt-6.2-astra",
            "gpt-6.1-terra", "gpt-6.1-sol",
            "gpt-6-astra", "gpt-6-sol", "gpt-6-luna",
            "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna",
            "gpt-6.2-sol-unverified",
        )
        val entries = ids.joinToString(",") { """{"visibility":"list","slug":"$it"}""" }
        val cached = parseChatGptModels(json("""{"models":[$entries]}"""), provider.modelConfigurationKey())

        assertTrue(cached.all { it.modelInfo?.toolCalling == null })
        assertEquals(ids.dropLast(1), cached.mapNotNull { provider.assistantChoice(it)?.model }
            .filter { AiModelAbility.TOOL in it.abilities }.map(AiModel::id))
    }

    @Test
    fun planToolDefaultsRespectProviderScopeAndCapabilityOverrides() {
        val source = provider.modelConfigurationKey()
        val model = AiModel(id = "gpt-6.2-sol", modelInfo = AiModelInfo(source))
        val disabledByCatalog = model.copy(modelInfo = AiModelInfo(source, toolCalling = AiModelValue(false)))
        val disabledByUser = model.copy(modelSettings = AiModelSettings(
            catalogEndpoint = source,
            capabilityOverrides = setOf(AiModelCapabilityField.TOOL_CALLING),
        ))
        val openAi = AiDefaultProviders.all().first { it.id == "openai" }

        assertTrue(AiModelAbility.TOOL in provider.effectiveModelCapabilities(model).abilities)
        assertFalse(AiModelAbility.TOOL in provider.effectiveModelCapabilities(disabledByCatalog).abilities)
        assertFalse(AiModelAbility.TOOL in provider.effectiveModelCapabilities(disabledByUser).abilities)
        assertFalse(AiModelAbility.TOOL in openAi.effectiveModelCapabilities(model).abilities)
    }

    @Test
    fun responsesUsesFixedOriginAndSubscriptionEnvelope() {
        val configured = provider.copy(baseUrl = "https://custom.example.test/v1", apiKey = "not-a-plan-token")
        val events = AiNativeWebEvents { }
        val model = AiModel(id = "gpt-6.2-sol", inputModalities = listOf(AiModelModality.TEXT),
            outputModalities = listOf(AiModelModality.TEXT))
        val plan = configured.nativeWebRoute(model)?.plan(allowed = true, userText = "搜书", thinkingOff = false)
            as AiWebToolPlan.Native
        assertEquals("ChatGPT", plan.sourceName)
        val codec = AiOpenAiResponsesCodec(configured, model.id, "/unexpected",
            nativeTools = plan.definitions, webEvents = events, chatGptAccessToken = "fixture")
        val request = codec.request(listOf(json("""{"role":"system","content":"system"}"""),
            json("""{"role":"user","content":"user"}""")),
            listOf(json("""{"type":"function","function":{"name":"test","parameters":{"type":"object"}}}""")),
            AiTextParams(temperature = 0.7f, maxTokens = 512, disableThinking = true), stream = false, conversationId = null)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val body = json(buffer.readUtf8())
        assertEquals("$CHATGPT_API/responses", request.url.toString())
        assertEquals("Bearer fixture", request.header("Authorization"))
        assertTrue(body.get("stream").asBoolean)
        assertFalse(body.get("store").asBoolean)
        assertFalse(body.has("temperature") || body.has("max_output_tokens") || body.has("previous_response_id"))
        assertFalse(body.has("reasoning"))
        assertEquals("developer", body.getAsJsonArray("input")[0].asJsonObject.get("role").asString)
        assertEquals(listOf("namespace", "web_search"), body.getAsJsonArray("tools").map { it.asJsonObject.get("type").asString })
        assertTrue(configured.resolveModelGenerationRoute(model).requiresStreaming)
        assertEquals(AiModelGenerationIssue.UNSUPPORTED_PROTOCOL,
            configured.resolveModelGenerationRoute(model, AiModelGenerationTestProtocol.CHAT_COMPLETIONS).issue)
        assertEquals(AiWebToolPlan.Off, configured.nativeWebRoute(model)?.plan(false, "搜书", false))
    }

    @Test
    fun sourceAnnotationsSurviveHistoryWithoutChangingProviderText() {
        val events = AiNativeWebEvents { }
        val codec = AiOpenAiResponsesCodec(provider, "gpt-6-luna", "/responses",
            nativeTools = listOf(json("""{"type":"web_search"}""")), webEvents = events, chatGptAccessToken = "fixture")
        val response = """{"type":"response.completed","response":{"status":"completed","output":[
            {"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search"}},
            {"type":"message","content":[{"type":"output_text","text":"🔎甲。\n后续说明","annotations":[
                {"type":"url_citation","start_index":1,"end_index":3,"title":"原作","url":"https://example.test/book"},
                {"type":"url_citation","end_index":3,"url":"javascript:bad"}] }]}
        ]}}""".replace("\n", "")
        val reply = codec.readStream("data: $response\n\n".reader().buffered()) { _, _ -> }
        assertEquals("🔎甲。\n后续说明", reply.content)
        val activity = checkNotNull(events.settled)
        val restored = Gson().fromJson(Gson().toJson(activity), AiWebActivity::class.java)
        assertEquals(listOf("原作"), restored.sources.map { it.title })
        assertEquals("🔎甲。 [1](<https://example.test/book>)\n后续说明", restored.withInlineLinks(reply.content))
        val replay = AiNativeChatHistory(provider, "gpt-6-luna", "$CHATGPT_API/responses").parts(reply.message)
        assertEquals(listOf("message"), replay!!.map { it.asJsonObject.get("type").asString })
    }

    @Test
    fun streamDoesNotTreatAUsageFailureOrDoneMarkerAsSuccess() {
        val codec = AiOpenAiResponsesCodec(provider, "gpt-6-luna", "/responses", chatGptAccessToken = "fixture")
        val partial = """data: {"type":"response.output_text.delta","delta":"partial"}"""
        val failure = """data: {"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}}"""
        val frames = "$partial\n\n$failure\n\n"
        val result = assertThrows(AiChatGptException::class.java) {
            codec.readStream(frames.reader().buffered()) { _, _ -> }
        }
        assertEquals(R.string.ai_chatgpt_usage_limit, result.messageRes)
        assertThrows(AiChatIncompleteStreamException::class.java) {
            codec.readStream("$partial\n\ndata: [DONE]\n\n".reader().buffered()) { _, _ -> }
        }
    }

    @Test
    fun completedStreamKeepsTheAnswerFromDeltasWhenFinalOutputIsEmpty() {
        val codec = AiOpenAiResponsesCodec(provider, "gpt-6.2-sol", "/responses", chatGptAccessToken = "fixture")
        val frames = """
            data: {"type":"response.output_text.delta","delta":"你好！"}

            data: {"type":"response.output_text.delta","delta":"我是阅读助理。"}

            data: {"type":"response.completed","response":{"status":"completed","output":[],"usage":{"total_tokens":12}}}
        """.trimIndent().reader().buffered()
        val reply = codec.readStream(frames) { _, _ -> }
        reply.requireCompleted()
        assertEquals("你好！我是阅读助理。", reply.content)
        assertEquals(12, reply.usage?.totalTokens)
    }

    @Test
    fun finishedOutputItemRestoresCitationsWhenCompletionOmitsOutput() {
        val events = AiNativeWebEvents { }
        val codec = AiOpenAiResponsesCodec(provider, "gpt-6.2-sol", "/responses",
            nativeTools = listOf(json("""{"type":"web_search"}""")), webEvents = events, chatGptAccessToken = "fixture")
        val message = json("""
            {"type":"message","id":"msg_1","role":"assistant","status":"completed","content":[
                {"type":"output_text","text":"找到了这本书。","annotations":[
                    {"type":"url_citation","end_index":7,"url":"https://example.test/book","title":"作品页"}]}]}
        """.trimIndent())
        val frames = """
            data: {"type":"response.output_item.done","output_index":0,"item":$message}

            data: {"type":"response.completed","response":{"status":"completed","usage":{"total_tokens":20}}}
        """.trimIndent().reader().buffered()
        val reply = codec.readStream(frames) { _, _ -> }
        reply.requireCompleted()
        assertEquals("找到了这本书。", reply.content)
        assertEquals("找到了这本书。 [1](<https://example.test/book>)", events.settled?.withInlineLinks(reply.content))
    }

    @Test
    fun finalizedAnswerTakesPrecedenceAndAnIncompleteStreamStaysIncomplete() {
        val codec = AiOpenAiResponsesCodec(provider, "gpt-6.2-sol", "/responses", chatGptAccessToken = "fixture")
        val delta = """data: {"type":"response.output_text.delta","delta":"preview"}"""
        val done = """data: {"type":"response.completed","response":{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"final"}]}]}}"""
        val incomplete = """data: {"type":"response.incomplete","response":{"status":"incomplete","output":[]}}"""
        assertEquals("final", codec.readStream("$delta\n\n$done\n\n".reader().buffered()) { _, _ -> }.content)
        val partial = codec.readStream("$delta\n\n$incomplete\n\n".reader().buffered()) { _, _ -> }
        assertEquals("incomplete", partial.finishReason)
        assertThrows(IllegalStateException::class.java) { partial.requireCompleted() }
        val empty = codec.readStream(
            """data: {"type":"response.completed","response":{"status":"completed","output":[]}}"""
                .reader().buffered(),
        ) { _, _ -> }
        assertThrows(IllegalStateException::class.java) { empty.requireCompleted() }
    }

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private fun ByteArray.b64() = toByteString().base64Url().trimEnd('=')
}
