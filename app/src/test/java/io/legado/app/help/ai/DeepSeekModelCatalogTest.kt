package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekModelCatalogTest {
    private val endpoint = "https://api.deepseek.com/models"
    private val provider = AiDefaultProviders.all().single { it.id == "deepseek" }
    private val client = OpenAiCompatibleProvider()

    @Test
    fun `official Flash and Pro have independent read only model data`() {
        val fetched = parse(OFFICIAL_MODELS)
        val flash = requireNotNull(fetched[0].modelInfo)
        val pro = requireNotNull(fetched[1].modelInfo)

        assertEquals(endpoint, flash.endpoint)
        assertEquals(AiModelValue("DeepSeek-V4.1-Flash"), flash.name)
        assertEquals(AiModelValue("deepseek"), flash.publisher)
        assertEquals(AiModelValue(1_048_576), flash.contextWindow)
        assertEquals(AiModelValue(393_216), pro.maxOutputTokens)
        assertEquals(listOf("text", "image"), flash.inputModalities?.value)
        assertEquals(listOf("text"), pro.inputModalities?.value)
        assertEquals(listOf("text"), pro.outputModalities?.value)
        assertEquals(listOf("low", "high", "max"), flash.effortLevels?.value)
        assertEquals("high", flash.defaultEffort?.value)
        assertEquals("in-history", flash.anthropicSystemPromptUpdate?.value)
        assertEquals("leading-only", pro.anthropicSystemPromptUpdate?.value)
    }

    @Test
    fun `partial refresh uses only previous values of surviving model ids`() {
        val earlier = provider.withFetchedModels(parse(OFFICIAL_MODELS)).copy(
            availableModelIds = listOf("deepseek-v4-pro"),
        )
        val refreshed = earlier.withFetchedModels(parse(
            """{"data":[{"id":"deepseek-v4-pro"},{"id":"new-model"}]}""",
        ))
        val pro = requireNotNull(refreshed.models[0].modelInfo)
        val new = requireNotNull(refreshed.models[1].modelInfo)

        assertEquals(listOf("deepseek-v4-pro", "new-model"), refreshed.models.map { it.id })
        assertEquals(listOf("deepseek-v4-pro"), refreshed.availableModelIds)
        assertEquals(AiModelValue(1_048_576, fromPreviousCatalog = true), pro.contextWindow)
        assertEquals(AiModelValue("high", fromPreviousCatalog = true), pro.defaultEffort)
        assertEquals(AiModelInfo(endpoint), new)
    }

    @Test
    fun `new modalities replace prior ones without merging inferred image support`() {
        val earlier = provider.withFetchedModels(parse(OFFICIAL_MODELS))
        val next = earlier.withFetchedModels(parse(
            """{"data":[{"id":"deepseek-flash","input_modalities":["text"],"output_modalities":[]}]}""",
        ))
        val info = requireNotNull(next.models.single().modelInfo)

        assertEquals(AiModelValue(listOf("text")), info.inputModalities)
        assertEquals(AiModelValue(emptyList<String>()), info.outputModalities)
        assertTrue(info.contextWindow!!.fromPreviousCatalog)
        assertTrue(AiModelModality.IMAGE in AiModelRegistry.capabilities("deepseek-flash").inputModalities)
    }

    @Test
    fun `invalid optional fields do not replace known data or fabricate limits`() {
        val earlier = provider.withFetchedModels(parse(OFFICIAL_MODELS))
        val malformed = parse(
            """{"data":[{"id":"deepseek-flash","context_window":"bad","max_output_tokens":0,
                "input_modalities":[{}],"effort":{"supported_levels":["max"]}}]}""",
        )
        val info = requireNotNull(earlier.withFetchedModels(malformed).models.single().modelInfo)
        val empty = requireNotNull(provider.withFetchedModels(malformed).models.single().modelInfo)

        assertEquals(AiModelValue(1_048_576, true), info.contextWindow)
        assertEquals(AiModelValue(393_216, true), info.maxOutputTokens)
        assertEquals(AiModelValue(listOf("text", "image"), true), info.inputModalities)
        assertEquals(AiModelValue(listOf("max")), info.effortLevels)
        assertNull(info.defaultEffort)
        assertNull(empty.contextWindow)
        assertNull(empty.maxOutputTokens)
        assertNull(empty.inputModalities)
    }

    @Test
    fun `different model endpoints cannot reuse a cached value`() {
        val first = provider.withFetchedModels(parse(OFFICIAL_MODELS))
        val customOfficial = first.copy(modelsUrl = "/v1/models", useCustomModelsUrl = true)
        val updated = customOfficial.withFetchedModels(parse(
            """{"data":[{"id":"deepseek-flash"}]}""",
            customOfficial.officialDeepSeekCatalogEndpoint(),
        ))

        assertEquals("https://api.deepseek.com/v1/models", updated.models.single().modelInfo?.endpoint)
        assertNull(updated.models.single().modelInfo?.contextWindow)
    }

    @Test
    fun `proxy and other provider responses use the original parser`() {
        val unrelatedProviders = listOf(
            provider.copy(baseUrl = "https://api.deepseek.com.example.invalid"),
            provider.copy(baseUrl = "https://proxy.example.invalid"),
            provider.copy(baseUrl = "http://api.deepseek.com"),
            provider.copy(baseUrl = "https://api.deepseek.com:8443"),
            provider.copy(modelsUrl = "https://proxy.example.invalid/models", useCustomModelsUrl = true),
            provider.copy(modelsUrl = "/anthropic/v1/models", useCustomModelsUrl = true),
            provider.copy(id = "custom-deepseek", builtIn = false),
            AiDefaultProviders.all().single { it.id == "openai" },
        )

        assertEquals(endpoint, provider.officialDeepSeekCatalogEndpoint())
        unrelatedProviders.forEach {
            val source = it.officialDeepSeekCatalogEndpoint()
            assertNull(source)
            assertTrue(parse(OFFICIAL_MODELS, source).all { model -> model.modelInfo == null })
        }
        assertNull("https://other.example.invalid/models".toHttpUrl().deepSeekCatalogEndpoint())
        val inFlight = provider.copy(baseUrl = "https://proxy.example.invalid")
            .withFetchedModels(parse(OFFICIAL_MODELS))
        assertTrue(inFlight.models.all { it.modelInfo == null })
    }

    @Test
    fun `catalog metadata is optional in persisted models and survives a reload`() {
        val gson = Gson()
        val old = gson.fromJson("""{"a":"old-model","b":"Old","d":["text"]}""", AiModel::class.java)
        val current = provider.withFetchedModels(parse(OFFICIAL_MODELS)).models.first()
        val reloaded = gson.fromJson(gson.toJson(current), AiModel::class.java)

        assertEquals("old-model", old.id)
        assertEquals("Old", old.name)
        assertEquals(listOf(AiModelModality.TEXT), old.inputModalities)
        assertNull(old.modelInfo)
        assertEquals(current, reloaded)
    }

    @Test
    fun `read only data does not change fields consumed by selection and generation`() {
        val original = parse(OFFICIAL_MODELS, null).map(AiModelRegistry::enrich)
        val withInfo = parse(OFFICIAL_MODELS).map(AiModelRegistry::enrich)

        assertFalse(withInfo.all { it.modelInfo == null })
        assertEquals(original, withInfo.map { it.copy(modelInfo = null) })
    }

    private fun parse(json: String, source: String? = endpoint): List<AiModel> =
        client.parseModels(JsonParser.parseString(json).asJsonObject, source)

    private companion object {
        const val OFFICIAL_MODELS = """
            {"data":[
                {"id":"deepseek-flash","name":"DeepSeek-V4.1-Flash","owned_by":"deepseek",
                 "context_window":1048576,"max_output_tokens":393216,
                 "input_modalities":["text","image"],"output_modalities":["text"],
                 "effort":{"supported_levels":["low","high","max"],"default_level":"high"},
                 "api_capabilities":{"anthropic_messages":{"system_prompt_update":"in-history"}}},
                {"id":"deepseek-v4-pro","name":"DeepSeek-V4-Pro","owned_by":"deepseek",
                 "context_window":1048576,"max_output_tokens":393216,
                 "input_modalities":["text"],"output_modalities":["text"],
                 "effort":{"supported_levels":["low","high","max"],"default_level":"high"},
                 "api_capabilities":{"anthropic_messages":{"system_prompt_update":"leading-only"}}}
            ]}
        """
    }
}
