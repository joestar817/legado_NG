package io.legado.app.help.ai

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderSettingTest {

    @Test
    fun parseObfuscatedReleaseProviderCache() {
        val json = """
            {
              "a": "deepseek",
              "b": "OPENAI",
              "c": true,
              "d": true,
              "e": "DeepSeek",
              "f": "sk-test",
              "g": "https://api.deepseek.com",
              "h": "deepseek-v4-pro",
              "i": [
                {
                  "a": "deepseek-v4-pro",
                  "b": "DeepSeek V4 Pro",
                  "c": "deepseek"
                }
              ],
              "j": 60,
              "k": "/chat/completions",
              "l": "https://api.deepseek.com/models",
              "m": true,
              "n": true,
              "o": "thinking",
              "p": "reasoning_effort",
              "q": "reasoning_content"
            }
        """.trimIndent()

        val provider = GSON.fromJson(json, AiProviderSetting::class.java)

        assertEquals("deepseek", provider.id)
        assertEquals(AiProviderType.OPENAI, provider.type)
        assertEquals("sk-test", provider.apiKey)
        assertEquals("https://api.deepseek.com", provider.baseUrl)
        assertEquals("deepseek-v4-pro", provider.model)
        assertEquals("deepseek-v4-pro", provider.models.single().id)
        assertEquals("DeepSeek V4 Pro", provider.models.single().name)
        assertEquals("deepseek", provider.models.single().ownedBy)
        assertEquals("https://api.deepseek.com/models", provider.modelsUrl)
        assertEquals("thinking", provider.thinkingParam)
        assertEquals("reasoning_effort", provider.effortParam)
        assertEquals("reasoning_content", provider.reasoningOutputField)
    }

    @Test
    fun parseStableModelCapabilityDeclaration() {
        val json = """
            {
              "id": "custom",
              "type": "OPENAI",
              "builtIn": false,
              "enabled": true,
              "name": "Custom",
              "baseUrl": "https://example.com",
              "models": [
                {
                  "id": "custom-image",
                  "name": "Custom Image",
                  "displayName": "Shown Image",
                  "type": "image",
                  "inputModalities": ["text"],
                  "outputModalities": ["image"],
                  "abilities": []
                }
              ]
            }
        """.trimIndent()

        val provider = GSON.fromJson(json, AiProviderSetting::class.java)
        val model = provider.models.single()

        assertEquals(AiModelType.IMAGE, model.type)
        assertEquals("Shown Image", model.displayName)
        assertEquals(listOf(AiModelModality.TEXT), model.inputModalities)
        assertEquals(listOf(AiModelModality.IMAGE), model.outputModalities)
        assertEquals(emptyList<AiModelAbility>(), model.abilities)
    }

    @Test
    fun builtInProviderEndpointsIncludeMainChineseOpenAiCompatibleProviders() {
        val providers = AiDefaultProviders.all().associateBy { it.id }

        assertEquals("https://api.siliconflow.cn/v1", providers.getValue("siliconflow").baseUrl)
        assertEquals("/models", providers.getValue("siliconflow").modelsUrl)
        assertEquals("/user/info", providers.getValue("siliconflow").balanceUrl)
        assertTrue(providers.getValue("siliconflow").enabled)

        assertEquals("https://openrouter.ai/api/v1", providers.getValue("openrouter").baseUrl)
        assertEquals("/models", providers.getValue("openrouter").modelsUrl)
        assertEquals("/credits", providers.getValue("openrouter").balanceUrl)
        assertTrue(providers.getValue("openrouter").enabled)

        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            providers.getValue("aliyun_bailian").baseUrl
        )
        assertEquals("/models", providers.getValue("aliyun_bailian").modelsUrl)
        assertTrue(providers.getValue("aliyun_bailian").enabled)

        assertEquals(
            "https://ark.cn-beijing.volces.com/api/v3",
            providers.getValue("volcengine").baseUrl
        )
        assertEquals("/models", providers.getValue("volcengine").modelsUrl)
        assertTrue(providers.getValue("volcengine").enabled)

        assertEquals("https://api.moonshot.cn/v1", providers.getValue("moonshot").baseUrl)
        assertEquals("/models", providers.getValue("moonshot").modelsUrl)
        assertEquals("/users/me/balance", providers.getValue("moonshot").balanceUrl)
        assertTrue(providers.getValue("moonshot").enabled)

        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4",
            providers.getValue("zhipu").baseUrl
        )
        assertEquals("/models", providers.getValue("zhipu").modelsUrl)
        assertTrue(providers.getValue("zhipu").enabled)
        assertTrue(providers.values.all { it.builtIn && it.enabled })
    }

    @Test
    fun enableOnlyBuiltInProvidersForOneTimeRollout() {
        val builtIn = AiDefaultProviders.all().map { it.copy(enabled = false) }
        val custom = builtIn.first().copy(id = "custom_openai", builtIn = false)
        val unknown = builtIn.first().copy(id = "other", enabled = false)

        val result = enableBuiltInAiProviders(builtIn + custom + unknown)

        assertTrue(result.take(builtIn.size).all { it.enabled })
        assertEquals(builtIn.map { it.copy(enabled = true) }, result.take(builtIn.size))
        assertFalse(result[builtIn.size].enabled)
        assertFalse(result.last().enabled)
    }
}
