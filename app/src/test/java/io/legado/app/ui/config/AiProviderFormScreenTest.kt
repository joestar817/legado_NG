package io.legado.app.ui.config

import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderFormScreenTest {

    @Test
    fun `field update only changes requested value`() {
        val source = AiProviderFormScreenState(
            providerId = "provider",
            name = "Before",
            apiKey = "secret",
            baseUrl = "https://example.com"
        )

        val updated = source.withField(AiProviderFormField.NAME, "After")

        assertEquals("After", updated.name)
        assertEquals("secret", updated.apiKey)
        assertEquals("https://example.com", updated.baseUrl)
        assertEquals("provider", updated.providerId)
    }

    @Test
    fun `custom provider auto name follows base url while untouched`() {
        val source = AiProviderFormScreenState(
            providerId = "custom_openai_1",
            builtIn = false,
            name = "api.openai.com",
            baseUrl = "https://api.openai.com/v1"
        )

        val renamed = source.withAutoNameForBaseUrl("https://proxy.example.com/v1")

        assertEquals("proxy.example.com", renamed.name)
        assertEquals("https://api.openai.com/v1", renamed.baseUrl)
    }

    @Test
    fun `custom provider manual name is not overwritten by base url edits`() {
        val source = AiProviderFormScreenState(
            providerId = "custom_openai_1",
            builtIn = false,
            name = "My Proxy",
            baseUrl = "https://api.openai.com/v1"
        )

        val renamed = source.withAutoNameForBaseUrl("https://proxy.example.com/v1")

        assertEquals("My Proxy", renamed.name)
    }

    @Test
    fun `built in provider name is never auto renamed`() {
        val source = AiProviderFormScreenState(
            providerId = "openai",
            builtIn = true,
            name = "api.openai.com",
            baseUrl = "https://api.openai.com/v1"
        )

        val renamed = source.withAutoNameForBaseUrl("https://proxy.example.com/v1")

        assertEquals("api.openai.com", renamed.name)
    }

    @Test
    fun `custom endpoint toggles preserve entered endpoint values`() {
        val source = AiProviderFormScreenState(
            useCustomBalanceUrl = true,
            balanceUrl = "/balance",
            balanceJsonPath = "$.data.balance",
            useCustomModelsUrl = true,
            modelsUrl = "/models"
        )

        val balanceHidden = source.withToggle(
            AiProviderFormToggle.CUSTOM_BALANCE_URL,
            false
        )
        val modelsHidden = balanceHidden.withToggle(
            AiProviderFormToggle.CUSTOM_MODELS_URL,
            false
        )

        assertFalse(modelsHidden.useCustomBalanceUrl)
        assertFalse(modelsHidden.useCustomModelsUrl)
        assertEquals("/balance", modelsHidden.balanceUrl)
        assertEquals("$.data.balance", modelsHidden.balanceJsonPath)
        assertEquals("/models", modelsHidden.modelsUrl)
    }

    @Test
    fun `switch update does not alter sibling switches`() {
        val source = AiProviderFormScreenState(
            enabled = true,
            streamResponseEnabled = false,
            useCustomBalanceUrl = false
        )

        val updated = source.withToggle(AiProviderFormToggle.STREAM_RESPONSE, true)

        assertTrue(updated.enabled)
        assertTrue(updated.streamResponseEnabled)
        assertFalse(updated.useCustomBalanceUrl)
    }

    @Test
    fun `provider mapping preserves legacy save fallbacks and timeout limits`() {
        val source = provider(
            name = "Original",
            baseUrl = "https://example.com",
            timeoutSeconds = 180,
            chatPath = "/chat/completions"
        )
        val form = source.toProviderFormScreenState("OpenAI 兼容").copy(
            name = "  ",
            baseUrl = "",
            apiKey = "  key  ",
            timeoutSeconds = "999",
            chatPath = "",
            modelsUrl = "/v1/models",
            useCustomModelsUrl = true,
            balanceUrl = "/dashboard/billing",
            balanceJsonPath = "  $.data.balance  ",
            useCustomBalanceUrl = true,
            streamResponseEnabled = true
        )

        val saved = form.applyTo(source)

        assertEquals("Original", saved.name)
        assertEquals("https://example.com", saved.baseUrl)
        assertEquals("key", saved.apiKey)
        assertEquals(600, saved.timeoutSeconds)
        assertEquals("/chat/completions", saved.chatCompletionsPath)
        assertEquals("/v1/models", saved.modelsUrl)
        assertEquals("/dashboard/billing", saved.balanceUrl)
        assertEquals("$.data.balance", saved.balanceJsonPath)
        assertTrue(saved.useCustomModelsUrl)
        assertTrue(saved.useCustomBalanceUrl)
        assertTrue(saved.streamResponseEnabled)
    }

    @Test
    fun `provider mapping exposes openai-only fields without changing stored provider`() {
        val source = provider(
            type = AiProviderType.CLAUDE,
            name = "Claude",
            baseUrl = "https://api.anthropic.com",
            timeoutSeconds = 60,
            chatPath = "/messages"
        )

        val form = source.toProviderFormScreenState("Anthropic 兼容")

        assertFalse(form.openAiCompatible)
        assertEquals("Anthropic 兼容", form.providerType)
        assertEquals(source, form.applyTo(source))
    }

    private fun provider(
        type: AiProviderType = AiProviderType.OPENAI,
        name: String,
        baseUrl: String,
        timeoutSeconds: Int,
        chatPath: String
    ): AiProviderSetting {
        return AiProviderSetting(
            id = "provider",
            type = type,
            enabled = true,
            builtIn = false,
            name = name,
            apiKey = "",
            baseUrl = baseUrl,
            timeoutSeconds = timeoutSeconds,
            chatCompletionsPath = chatPath
        )
    }
}
