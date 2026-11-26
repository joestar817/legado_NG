package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.legado.app.help.ai.AiModelGenerationTestProtocol.CHAT_COMPLETIONS
import io.legado.app.help.ai.AiModelGenerationTestProtocol.CLAUDE_MESSAGES
import io.legado.app.help.ai.AiModelGenerationTestProtocol.RESPONSES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelGenerationRouteTest {
    @Test
    fun `provider settings apply while per-model and test choices take precedence`() {
        val provider = customProvider().copy(useResponsesApi = true)
        val unknown = AiModel("unknown")
        val model = unknown.withInterface(provider, AiModelInterfacePreference.CHAT_COMPLETIONS)

        assertEquals(RESPONSES, provider.resolveModelGenerationRoute(unknown).protocol)
        assertEquals(CHAT_COMPLETIONS, provider.resolveModelGenerationRoute(model).protocol)
        assertEquals(RESPONSES, provider.resolveModelGenerationRoute(model, protocolOverride = RESPONSES).protocol)
        assertEquals(CHAT_COMPLETIONS, provider.resolveModelGenerationRoute(model).protocol)

        val moved = provider.copy(baseUrl = "https://another.example/v1")
        assertEquals(RESPONSES, moved.resolveModelGenerationRoute(model).protocol)
    }

    @Test
    fun `provider settings report a Responses-only conflict without silently changing API type`() {
        val provider = preset("openai")
        val model = AiModel("gpt-5.4-pro-2026-03-05")
        val issue = AiModelGenerationIssue.UNSUPPORTED_PROTOCOL

        assertEquals(AiModelGenerationRoute(CHAT_COMPLETIONS, issue), provider.resolveModelGenerationRoute(model))
        assertEquals(
            AiModelGenerationRoute(RESPONSES, null),
            provider.copy(useResponsesApi = true).resolveModelGenerationRoute(model),
        )
        val explicit = model.withInterface(provider, AiModelInterfacePreference.CHAT_COMPLETIONS)
        assertEquals(AiModelGenerationRoute(CHAT_COMPLETIONS, issue), provider.resolveModelGenerationRoute(explicit))
        assertEquals(
            AiModelGenerationRoute(RESPONSES, null),
            provider.resolveModelGenerationRoute(model.withInterface(provider, AiModelInterfacePreference.RESPONSES)),
        )
        assertEquals(
            AiModelGenerationRoute(RESPONSES, null),
            provider.resolveModelGenerationRoute(model, protocolOverride = RESPONSES),
        )

        val proxy = customProvider()
        assertEquals(AiModelGenerationRoute(CHAT_COMPLETIONS, null), proxy.resolveModelGenerationRoute(model))
        assertEquals(CHAT_COMPLETIONS, provider.resolveModelGenerationRoute(AiModel("gpt-5.4-pro-unknown")).protocol)

        val customPath = provider.copy(chatCompletionsPath = "/custom/chat/completions")
        assertEquals(AiModelGenerationRoute(CHAT_COMPLETIONS, null), customPath.resolveModelGenerationRoute(model))
        for (id in listOf("gpt-5.5-pro", "gpt-5.3-codex", "o3-deep-research", "gpt-6.2-sol-healthcare")) {
            val model = AiModel(id)
            assertEquals(id, issue, provider.resolveModelGenerationRoute(model).issue)
            assertNull(provider.copy(useResponsesApi = true).resolveModelGenerationRoute(model).issue)
        }
    }

    @Test
    fun `official GPT-6 tool availability follows the chosen API`() {
        val provider = preset("openai")
        for (id in listOf("gpt-6-astra", "gpt-6-sol", "gpt-6-luna", "gpt-6.1-sol", "gpt-6.1-terra", "gpt-6.2-astra", "gpt-6.2-sol")) {
            val model = AiModelRegistry.enrich(AiModel(id))
            val responses = model.withInterface(provider, AiModelInterfacePreference.RESPONSES)
            assertFalse(id, AiModelAbility.TOOL in provider.effectiveModelCapabilities(model).abilities)
            assertTrue(id, AiModelAbility.TOOL in provider.effectiveModelCapabilities(responses).abilities)
            assertTrue(id, AiModelAbility.TOOL in provider.copy(useResponsesApi = true).effectiveModelCapabilities(model).abilities)
        }
        val terra = AiModelRegistry.enrich(AiModel("gpt-6.1-terra"))
        val disabled = terra.copy(modelInfo = AiModelInfo(provider.modelConfigurationKey(), toolCalling = AiModelValue(false)))
        assertFalse(AiModelAbility.TOOL in provider.copy(useResponsesApi = true).effectiveModelCapabilities(disabled).abilities)
        assertNull(customProvider().effectiveModelInfo(terra).toolCalling)
    }

    @Test
    fun `GPT Audio exposes its documented tool support on the standard Chat API`() {
        val provider = preset("openai")
        val model = AiModel("gpt-audio-1.5")
        val chat = provider.effectiveModelCapabilities(model)
        assertTrue(chat.isTextChatModel())
        assertTrue(AiModelModality.AUDIO in chat.inputModalities)
        assertTrue(AiModelAbility.TOOL in chat.abilities)
        val responses = provider.copy(useResponsesApi = true)
        assertFalse(AiModelAbility.TOOL in responses.effectiveModelCapabilities(model).abilities)
        assertEquals(AiModelGenerationIssue.UNSUPPORTED_PROTOCOL, responses.resolveModelGenerationRoute(model).issue)
        val search = AiModelRegistry.enrich(AiModel("gpt-5-search-api"))
        assertFalse(AiModelAbility.TOOL in provider.effectiveModelCapabilities(search).abilities)
        assertEquals(AiModelGenerationIssue.UNSUPPORTED_PROTOCOL, responses.resolveModelGenerationRoute(search).issue)
    }

    @Test
    fun `Bailian sends tool_stream for the documented GLMs when sending tools`() {
        val provider = preset("aliyun_bailian")
        val client = AiChatClient()
        val tools = listOf(JsonObject().apply { addProperty("type", "function") })
        for (modelId in listOf("glm-5", "glm-5.1", "glm-5.2")) {
            val model = AiModel(modelId)
            val withTools = client.buildRequestBody(provider, model, emptyList(), tools, AiTextParams(), true)
            assertTrue(withTools.get("tool_stream").asBoolean)
            assertTrue(withTools.has("tools"))
            assertFalse(client.buildRequestBody(provider, model, emptyList(), emptyList(), AiTextParams(), true).has("tool_stream"))
        }
        assertFalse(client.buildRequestBody(provider, AiModel("glm-5.3"), emptyList(), tools, AiTextParams(), true).has("tool_stream"))
        assertFalse(client.buildRequestBody(customProvider(), AiModel("glm-5.2"), emptyList(), tools, AiTextParams(), true).has("tool_stream"))
    }

    @Test
    fun `a manual interface without a configured endpoint is reported instead of falling back`() {
        val provider = preset("siliconflow")
        val model = AiModel("model").withInterface(provider, AiModelInterfacePreference.RESPONSES)
        assertEquals(
            AiModelGenerationRoute(RESPONSES, AiModelGenerationIssue.UNSUPPORTED_PROTOCOL),
            provider.resolveModelGenerationRoute(model),
        )
    }

    @Test
    fun `streaming requirement applies only on verified Bailian Chat routes`() {
        val provider = preset("aliyun_bailian").copy(
            baseUrl = "https://llm-example.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
        )
        val model = AiModel("qwen3-32b")
        val route = provider.resolveModelGenerationRoute(model)

        assertEquals(CHAT_COMPLETIONS, route.protocol)
        assertEquals(AiModelGenerationIssue.STREAMING_REQUIRED, route.issue)
        assertTrue(route.requiresStreaming)
        assertNull(provider.resolveModelGenerationRoute(model, stream = true).issue)
        assertTrue(provider.resolveModelGenerationRoute(model, stream = true).requiresStreaming)
        val responses = provider.resolveModelGenerationRoute(model, protocolOverride = RESPONSES)
        assertFalse(responses.requiresStreaming)
        assertNull(responses.issue)
        assertNull(provider.resolveModelGenerationRoute(AiModel("qwen3-max")).issue)

        val proxy = customProvider()
        assertNull(proxy.resolveModelGenerationRoute(model).issue)
        val otherPath = provider.copy(chatCompletionsPath = "/proxy/chat/completions")
        assertNull(otherPath.resolveModelGenerationRoute(model).issue)
    }

    @Test
    fun `native provider ignores an imported model preference and rejects a mismatched test override`() {
        val provider = preset("claude")
        val model = AiModel("claude-sonnet").withInterface(provider, AiModelInterfacePreference.RESPONSES)
        assertEquals(AiModelGenerationRoute(CLAUDE_MESSAGES, null), provider.resolveModelGenerationRoute(model))
        assertEquals(
            AiModelGenerationIssue.UNSUPPORTED_PROTOCOL,
            provider.resolveModelGenerationRoute(model, protocolOverride = RESPONSES).issue,
        )
    }

    @Test
    fun `interface choice round-trips and remains after same source model refresh`() {
        val gson = Gson()
        val provider = customProvider()
        val stored = AiModel("model").withInterface(provider, AiModelInterfacePreference.RESPONSES)
        val json = gson.toJson(stored)
        assertTrue(json.contains("\"interfacePreference\":\"responses\""))
        val restored = gson.fromJson(json, AiModel::class.java)
        val refreshed = provider.copy(models = listOf(restored)).mergeModelInfo(listOf(AiModel("model"))).single()
        assertEquals(RESPONSES, provider.resolveModelGenerationRoute(refreshed).protocol)
        assertNull(gson.fromJson("""{"catalogEndpoint":"host"}""", AiModelSettings::class.java).interfacePreference)
        val providerDefault = gson.fromJson(
            """{"catalogEndpoint":"host","interfacePreference":"auto"}""",
            AiModelSettings::class.java,
        )
        assertEquals(AiModelInterfacePreference.PROVIDER_DEFAULT, providerDefault.interfacePreference)
    }

    private fun AiModel.withInterface(
        provider: AiProviderSetting,
        preference: AiModelInterfacePreference,
    ): AiModel = copy(
        modelSettings = AiModelSettings(provider.modelConfigurationKey(), interfacePreference = preference),
    )

    private fun preset(id: String): AiProviderSetting = AiDefaultProviders.all().first { it.id == id }

    private fun customProvider() = AiProviderSetting(
        id = "custom",
        name = "Custom",
        type = AiProviderType.OPENAI,
        builtIn = false,
        baseUrl = "https://proxy.example/v1",
    )
}
