package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AiCodexProviderTest {
    private val provider = AiDefaultProviders.all().single { it.isCodexSubscription() }

    @Test
    fun routesCodexCredentialsOnlyToTheFixedResponsesEndpoint() {
        val tokenClaims = """{"https://api.openai.com/auth":{"chatgpt_account_id":"workspace-fixture"}}"""
            .toByteArray().toByteString().base64Url()
        val token = "header.$tokenClaims.signature"
        val codec = AiOpenAiResponsesCodec(
            setting = provider.copy(baseUrl = "https://example.test"),
            modelId = "gpt-fixture",
            endpoint = "/other-endpoint",
            chatGptAccessToken = token,
        )
        val request = codec.request(
            messages = listOf(json("""{"role":"system","content":"Local instructions"}"""),
                json("""{"role":"user","content":"Draw a fox."}""")),
            tools = listOf(codexImageToolDefinition()),
            params = AiTextParams(temperature = 0.8f, maxTokens = 100),
            stream = false,
            conversationId = null,
        )
        assertEquals("$CODEX_API/responses", request.url.toString())
        assertEquals("workspace-fixture", request.header("ChatGPT-Account-Id"))
        val payload = Buffer().also { request.body!!.writeTo(it) }.readUtf8().let(::json)
        assertTrue(payload.get("stream").asBoolean)
        assertFalse(payload.get("store").asBoolean)
        assertEquals("Local instructions", payload.get("instructions").asString)
        assertEquals(1, payload.getAsJsonArray("input").size())
        assertFalse(payload.has("temperature"))
        assertFalse(payload.has("max_output_tokens"))
        assertTrue(payload.getAsJsonArray("tools").toString().contains(CODEX_IMAGE_TOOL))
        assertNull(provider.generationApiPath(AiModelGenerationTestProtocol.CHAT_COMPLETIONS))
    }

    @Test
    fun usesAccountSpecificToolModelsAndIgnoresHiddenEntries() {
        val catalog = json("""
            {"models":[
                {"slug":"visible","visibility":"list","display_name":"Visible"},
                {"slug":"hidden","visibility":"hide"}
            ]}
        """.trimIndent())
        val models = parseChatGptModels(catalog, provider.modelConfigurationKey(), codex = true)
        assertEquals(listOf("visible"), models.map { it.id })
        assertTrue(AiModelAbility.TOOL in models.single().abilities)
        val siwc = AiDefaultProviders.all().single { it.isChatGptSubscription() }
        assertTrue(siwc.modelConfigurationKey() != provider.modelConfigurationKey())
    }

    @Test
    fun onlyTheSelectedEnabledCodexProviderOffersImages() {
        val codex = provider.copy(enabled = true, chatGptAccountId = "codex-account")
        val chatGpt = AiDefaultProviders.all().single { it.isChatGptSubscription() }
            .copy(enabled = true, chatGptAccountId = "chatgpt-account")

        assertEquals(codex, codex.availableCodexImageProvider())
        assertNull(chatGpt.availableCodexImageProvider())
        assertNull(codex.copy(enabled = false).availableCodexImageProvider())
        assertNull(codex.copy(chatGptAccountId = null).availableCodexImageProvider())
    }

    @Test
    fun aDirectImageRetryRequiresTheCodexChatSelection() {
        val user = json("""{"role":"user","content":"Draw a fox"}""").apply {
            setChatImageAssets(emptyList(), direct = true)
        }
        val client = AiChatClient(clientForTimeout = { error("No request should reach the network") })
        val error = assertThrows(AiChatGptException::class.java) {
            runBlocking {
                client.send(
                    messages = mutableListOf(user),
                    conversationId = "image-retry",
                    turnId = "turn",
                    selection = AiModelSelection(AiDefaultProviders.CHATGPT_ID, "gpt-fixture"),
                )
            }
        }
        assertEquals(R.string.ai_image_codex_model_required, error.messageRes)
    }

    @Test
    fun readsActualRemainingQuotaAndCreditBalance() {
        val usage = parseCodexUsage(json("""
            {"plan_type":"pro","rate_limit":{"primary_window":{
                "used_percent":37,"limit_window_seconds":604800,"reset_at":1796000000
            }},"credits":{"balance":"30.25"}}
        """.trimIndent()))
        assertEquals("pro", usage.plan)
        assertEquals("ChatGPT Pro 200", usage.planDisplayName)
        assertEquals(AiCodexUsage.Window(63, 604800, 1796000000L), usage.windows.single())
        assertEquals("30.25", usage.creditsBalance)
        assertNull(parseCodexUsage(JsonObject()).creditsBalance)
    }

    @Test
    fun formatsPlanNamesAndWholeCreditsForTheCurrentAccount() {
        val usage = parseCodexUsage(json("""{"plan_type":"pro","credits":{"balance":"31363.2319480000"}}"""))
        assertEquals("ChatGPT Pro 100", usage.copy(plan = "prolite").planDisplayName)
        assertEquals("ChatGPT Pro 500", usage.copy(plan = "promax").planDisplayName)
        assertEquals("new_tier", usage.copy(plan = "new_tier").planDisplayName)
        assertNull(usage.copy(plan = null).planDisplayName)
        assertEquals("31,363", usage.formatCredits(Locale.US))
        assertEquals("31,364", usage.copy(creditsBalance = "31363.5").formatCredits(Locale.US))
        assertEquals("31.363", usage.formatCredits(Locale.GERMANY))
        assertEquals("0", usage.copy(creditsBalance = "0.01").formatCredits(Locale.US))
        assertNull(usage.copy(creditsBalance = null).formatCredits(Locale.US))
    }

    @Test
    fun decodesImageBytesAndRejectsTextPayload() {
        val base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV4sAAAAASUVORK5CYII="
        val image = readCodexImage("""{"data":[{"b64_json":"$base64"}]}""".toResponseBody())
        assertEquals(base64, image.base64())
        assertThrows(AiChatGptException::class.java) {
            readCodexImage("""{"data":[{"b64_json":"bm90IGFuIGltYWdl"}]}""".toResponseBody())
        }
    }

    @Test
    fun imageOnlyCheckpointSurvivesWithoutAssistantText() {
        val receipt = ToolExecutionReceipt(
            receiptId = "image-receipt", conversationId = "session", turnId = "turn",
            stepId = "step", toolCallId = "call", toolName = CODEX_IMAGE_TOOL,
            argumentsHash = "hash", status = ToolExecutionStatus.SUCCEEDED,
        )
        val session = AiChatSessionSnapshot(
            id = "session", title = "A fox", createdAt = 0, updatedAt = 0, messages = emptyList(),
        )
        val update = AiChatDurableContextUpdate(emptyList(), "", null, toolReceipts = listOf(receipt))
        val saved = AiChatHistoryStore.mergeDurableTurn(session, "assistant", update, 1L).messages.single()
        val restored = Gson().fromJson(Gson().toJson(saved), AiChatMessageSnapshot::class.java)
        assertEquals("", restored.content)
        assertTrue(restored.toolReceipts.orEmpty().single().isCodexImage())
    }

    private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject
}
