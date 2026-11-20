package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekModelSettingsTest {
    private val endpoint = "https://api.deepseek.com/models"
    private val provider = AiDefaultProviders.all().first { it.id == "deepseek" }
    private val info = AiModelInfo(
        endpoint = endpoint,
        contextWindow = AiModelValue(1_048_576),
        maxOutputTokens = AiModelValue(393_216),
        effortLevels = AiModelValue(listOf("low", "high", "max")),
        defaultEffort = AiModelValue("high"),
    )
    private val model = AiModel(
        id = "deepseek-flash",
        abilities = listOf(AiModelAbility.REASONING),
        modelInfo = info,
    )

    @Test
    fun `model settings require the built in official catalog and chat route`() {
        assertEquals(endpoint, provider.officialDeepSeekModelConfigEndpoint())
        assertEquals(endpoint, provider.copy(baseUrl = "https://api.deepseek.com/v1").officialDeepSeekModelConfigEndpoint())
        assertNull(provider.copy(builtIn = false).officialDeepSeekModelConfigEndpoint())
        assertNull(provider.copy(baseUrl = "https://proxy.example.invalid").officialDeepSeekModelConfigEndpoint())
        assertNull(provider.copy(baseUrl = "https://api.deepseek.com/anthropic").officialDeepSeekModelConfigEndpoint())
        assertNull(provider.copy(chatCompletionsPath = "/responses").officialDeepSeekModelConfigEndpoint())
        assertNull(provider.copy(useCustomModelsUrl = true, modelsUrl = "https://proxy.example.invalid/models")
            .officialDeepSeekModelConfigEndpoint())
    }

    @Test
    fun `assistant limit comes from the model specification and matching provider endpoint`() {
        assertEquals(1_048_576, provider.assistantContextWindowLimit(model))
        assertNull(provider.assistantContextWindowLimit(model.copy(modelInfo = null)))
        assertNull(provider.assistantContextWindowLimit(
            model.copy(modelInfo = info.copy(endpoint = "https://api.deepseek.com/v1/models")),
        ))
        assertNull(provider.copy(baseUrl = "https://proxy.example.invalid").assistantContextWindowLimit(model))

        val previousBudget = AiModelSettings(endpoint, followModelContext = true, contextWindowTokens = 32_768)
        assertEquals(1_048_576, provider.assistantContextWindowLimit(model.copy(modelSettings = previousBudget)))
        val edited = previousBudget.copy(referenceContextWindowTokens = 1_000_123)
        assertEquals(
            1_000_123,
            provider.assistantContextWindowLimit(model.copy(modelSettings = edited)),
        )
    }

    @Test
    fun `context reference uses exact model data and edits`() {
        val settings = AiModelSettings(endpoint)
        assertEquals(1_048_576, settings.referenceContextWindow(info))
        assertNull(settings.referenceContextWindow(null))
        assertNull(settings.referenceContextWindow(info.copy(endpoint = "https://api.deepseek.com/v1/models")))

        val edited = settings.copy(referenceContextWindowTokens = 1_000_123)
        assertEquals(1_000_123, edited.referenceContextWindow(info))
    }

    @Test
    fun `output reference uses same source data and stays separate from the request limit`() {
        val settings = AiModelSettings(endpoint)
        assertEquals(393_216, settings.referenceMaxOutput(info))
        assertNull(settings.referenceMaxOutput(null))
        assertNull(settings.referenceMaxOutput(info.copy(endpoint = "https://api.deepseek.com/v1/models")))

        val reference = settings.copy(referenceMaxOutputTokens = 1_024)
        assertEquals(1_024, reference.referenceMaxOutput(info))
        assertNull(reference.applyOutputLimit(AiTextParams(), info, 32_768).maxTokens)

        val custom = reference.copy(maxOutputTokens = 4_096)
        assertEquals(4_096, custom.applyOutputLimit(AiTextParams(), info, 32_768).maxTokens)
    }

    @Test
    fun `effort filters narrow available values and empty filters use all available values`() {
        val candidates = listOf("low", "high", "max")
        val detected = provider.modelReasoning(model).efforts
        assertEquals(candidates, detected.candidates)
        assertEquals(candidates, detected.checked)
        assertEquals(candidates, detected.enabled)

        val pro = provider.modelReasoning(model.copy(id = "deepseek-v4-pro", modelInfo = null)).efforts
        assertEquals(detected.enabled, pro.enabled)
        val noEffort = model.copy(modelInfo = info.copy(effortLevels = AiModelValue(emptyList())))
        assertTrue(provider.modelReasoning(noEffort).efforts.enabled.isEmpty())

        fun filter(vararg values: String) = provider.modelReasoning(model.copy(
            modelSettings = AiModelSettings(endpoint, enabledReasoningEfforts = values.toList()),
        )).efforts
        assertEquals(listOf("low"), filter("ultra", "low", "none").enabled)
        assertTrue(filter().checked.isEmpty())
        assertEquals(candidates, filter().enabled)
        assertEquals(candidates, filter("ultra").enabled)
        val extra = provider.modelReasoning(model.copy(
            modelInfo = info.copy(effortLevels = AiModelValue(listOf("minimal"))),
        )).efforts
        assertEquals(listOf("minimal"), extra.candidates)
        assertEquals(listOf("minimal"), extra.enabled)
    }

    @Test
    fun `assistant uses manual options only for the official model route`() {
        val settings = AiModelSettings(endpoint, reasoningEffort = "low", enabledReasoningEfforts = listOf("max"))
        val edited = model.copy(modelSettings = settings)
        val max = provider.modelAssistantReasoning(edited, "max", AiReasoningLevel.OFF)
        assertEquals(listOf("off", "auto", "max"), max.options)
        assertEquals("max", max.selected)
        assertEquals("auto", provider.modelAssistantReasoning(edited, "low", AiReasoningLevel.OFF).selected)
        val pro = model.copy(id = "deepseek-v4-pro", modelSettings = settings.copy(enabledReasoningEfforts = null))
        assertEquals("max", provider.modelAssistantReasoning(pro, "max", AiReasoningLevel.OFF).selected)
        assertEquals("low", provider.copy(baseUrl = "https://proxy.example.invalid")
            .modelAssistantReasoning(edited, "low", AiReasoningLevel.OFF).selected)
        assertEquals("auto", provider.modelAssistantReasoning(
            edited.copy(abilities = emptyList(), modelSettings = settings.copy(capabilitiesEdited = true)),
            "max", AiReasoningLevel.HIGH,
        ).selected)
        assertEquals("high", provider.modelAssistantReasoning(model, null, AiReasoningLevel.ULTRA).selected)
        assertEquals("low", provider.modelAssistantReasoning(
            model.copy(modelSettings = settings.copy(enabledReasoningEfforts = null)),
            null, AiReasoningLevel.OFF,
        ).selected)
    }

    @Test
    fun `thinking max is explicit auto omits and off disables without changing output limits`() {
        val settings = AiModelSettings(endpoint, maxOutputTokens = 16_384)
        val inherited = AiTextParams(temperature = 0.6f, disableThinking = true)
        val output = settings.applyOutputLimit(inherited, info, 32_768)
        val fields = AiModelReasoningOptions(thinkingParam = "thinking", effortParam = "reasoning_effort")
        fun reason(choice: String) = provider.modelAssistantReasoning(model, choice, AiReasoningLevel.OFF)
        fun body(params: AiTextParams) = JsonObject().apply { addReasoningParams(params, fields) }

        val max = reason("max").applyTo(output)
        assertEquals("max", max.reasoningEffort)
        assertEquals(16_384, max.maxTokens)
        assertEquals(inherited.temperature, max.temperature)
        assertTrue(max.enableThinking)
        assertFalse(max.disableThinking)
        assertEquals("max", body(max).get("reasoning_effort").asString)
        assertEquals("enabled", body(max).getAsJsonObject("thinking").get("type").asString)

        val followModel = reason(DEFAULT_REASONING_CHOICE).applyTo(output)
        assertFalse(followModel.enableThinking)
        assertFalse(followModel.disableThinking)
        assertNull(followModel.reasoningEffort)
        assertEquals(0, body(followModel).size())

        val off = reason(DISABLE_REASONING_CHOICE).applyTo(output)
        assertFalse(off.enableThinking)
        assertTrue(off.disableThinking)
        assertNull(off.reasoningEffort)
        assertEquals("disabled", body(off).getAsJsonObject("thinking").get("type").asString)
        assertFalse(body(off).has("reasoning_effort"))
        assertEquals(8_191, settings.applyOutputLimit(inherited, info, 8_192).maxTokens)
    }

    @Test
    fun `catalog refresh keeps a model edit and updates official limits`() {
        val settings = AiModelSettings(
            endpoint, capabilitiesEdited = true, contextWindowTokens = 32_768, referenceContextWindowTokens = 128_123,
            referenceMaxOutputTokens = 12_345,
            enabledReasoningEfforts = listOf("max"),
        )
        val edited = model.copy(
            displayName = "阅读模型",
            inputModalities = listOf(AiModelModality.TEXT),
            outputModalities = listOf(AiModelModality.TEXT),
            abilities = emptyList(),
            modelSettings = settings,
        )
        val fetched = model.copy(
            inputModalities = listOf(AiModelModality.TEXT, AiModelModality.IMAGE),
            modelInfo = info.copy(contextWindow = AiModelValue(262_144)),
        )
        val refreshed = provider.copy(models = listOf(edited)).withFetchedModels(listOf(fetched)).models.single()
        assertEquals(settings, refreshed.modelSettings)
        assertEquals("阅读模型", refreshed.displayName)
        assertEquals(listOf(AiModelModality.TEXT), refreshed.inputModalities)
        assertTrue(refreshed.abilities.isEmpty())
        assertEquals(262_144, refreshed.modelInfo?.contextWindow?.value)

        val other = provider.copy(models = listOf(edited), useCustomModelsUrl = true, modelsUrl = "/v1/models")
        val replacement = fetched.copy(modelInfo = fetched.modelInfo?.copy(endpoint = "https://api.deepseek.com/v1/models"))
        assertNull(other.withFetchedModels(listOf(replacement)).models.single().modelSettings)
    }

    @Test
    fun `empty explicit abilities stay empty when the saved official model is loaded`() {
        val tuned = AiModel(id = "deepseek-flash", modelSettings = AiModelSettings(endpoint, capabilitiesEdited = true))
        assertEquals(tuned, enrichUnspecifiedAiModels(listOf(tuned), endpoint).single())
        assertTrue(AiModelAbility.REASONING in enrichUnspecifiedAiModels(listOf(tuned)).single().abilities)
    }

    @Test
    fun `legacy and current models round trip with optional settings`() {
        val gson = Gson()
        val old = gson.fromJson("""{"a":"deepseek-flash","b":"Flash"}""", AiModel::class.java)
        assertEquals("deepseek-flash", old.id)
        assertNull(old.modelSettings)

        val previousSettings = gson.fromJson(
            """{"catalogEndpoint":"$endpoint","contextWindowTokens":32768,"maxOutputTokens":4096}""",
            AiModelSettings::class.java,
        )
        assertEquals(32_768, previousSettings.contextWindowTokens)
        assertNull(previousSettings.referenceContextWindowTokens)
        assertEquals(4_096, previousSettings.maxOutputTokens)
        assertNull(previousSettings.referenceMaxOutputTokens)
        assertNull(previousSettings.enabledReasoningEfforts)

        val settings = AiModelSettings(
            endpoint,
            capabilitiesEdited = true,
            followModelContext = true,
            contextWindowTokens = 32_768,
            maxOutputTokens = 4_096,
            reasoningEffort = "max",
            referenceContextWindowTokens = 1_000_123,
            referenceMaxOutputTokens = 12_345,
            enabledReasoningEfforts = emptyList(),
        )
        val tuned = model.copy(modelSettings = settings)
        val restored = gson.fromJson(gson.toJson(tuned), AiModel::class.java)
        assertEquals(settings, restored.modelSettingsFor(endpoint))
        assertNull(restored.modelSettingsFor("https://api.deepseek.com/v1/models"))
        assertNull(model.copy(id = "deepseek-v4-pro").modelSettingsFor(endpoint))
    }
}
