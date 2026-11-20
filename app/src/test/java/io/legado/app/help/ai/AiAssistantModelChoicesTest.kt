package io.legado.app.help.ai

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantModelChoicesTest {
    private val provider = AiProviderSetting(
        id = "local-fixture", name = "Local", type = AiProviderType.OPENAI, builtIn = false,
        apiKey = "fixture-only", baseUrl = "https://example.invalid/v1",
    )

    @Test
    fun idOnlyCustomModelCanBeSelectedAndUsesAutomaticEffortUntilExplicitlyChanged() {
        val configured = provider.withFetchedModels(importModels("""[{"id":"qa-7f3e"}]"""))
        val option = configured.assistantModelChoices().single()
        assertTrue(option.textTrial)
        assertEquals(option, configured.assistantModelChoice("qa-7f3e"))
        val reasoning = configured.modelAssistantReasoning(option.model, "high", AiReasoningLevel.HIGH)
        assertEquals(listOf("auto", "low", "medium", "high", "xhigh", "max", "ultra"), reasoning.options)
        assertEquals("auto", reasoning.selected)
        assertNull(reasoning.applyTo(AiTextParams()).reasoningEffort)
        assertNotNull(configured.models.single().modelInfo)
        assertTrue(configured.models.single().inputModalities.isEmpty())
    }

    @Test
    fun onlyCurrentEnabledModelsOfAnAssistantCompatibleProviderCanBeUsed() {
        val configured = provider.withFetchedModels(importModels("""[{"id":"qa-7f3e"},{"id":"qa-r38s"}]"""))
        val removed = configured.withFetchedModels(importModels("""[{"id":"qa-r38s"}]"""))
        val disabledModel = configured.copy(availableModelIds = listOf("qa-r38s"))
        val unavailable = listOf(
            disabledModel, removed, configured.copy(enabled = false), configured.copy(apiKey = ""),
        )
        unavailable.forEach { setting ->
            assertFalse(setting.assistantModelChoices().any { it.model.id == "qa-7f3e" })
            assertNull(setting.assistantModelChoice("qa-7f3e"))
        }
        assertNotNull(disabledModel.assistantModelChoice("qa-r38s"))
        assertNotNull(removed.assistantModelChoice("qa-r38s"))
    }

    @Test
    fun nativeProviderCatalogsUseTheSameAssistantEligibilityChecks() {
        listOf(
            AiProviderType.CLAUDE to "claude-sonnet-4-5",
            AiProviderType.GOOGLE to "gemini-3-flash-preview",
        ).forEach { (type, id) ->
            val model = AiModelRegistry.enrich(AiModel(id))
            val configured = provider.copy(type = type).withFetchedModels(listOf(model))
            assertEquals(listOf(id), configured.assistantModelChoices().map { it.model.id })
            assertNotNull(configured.assistantModelChoice(id))
            assertNull(configured.copy(enabled = false).assistantModelChoice(id))
            assertNull(configured.copy(availableModelIds = emptyList()).assistantModelChoice(id))
        }
    }

    @Test
    fun explicitNonChatAndNonTextCapabilitiesAreNeverTreatedAsUnknown() {
        val fetched = importModels(
            """[{"id":"qa-image","output_modalities":["image"]},
                 {"id":"qa-no-output","output_modalities":[]},
                 {"id":"qa-audio","input_modalities":["audio"]},
                 {"id":"text-embedding-3-small"},
                 {"id":"gpt-4o"}]""",
        )
        val configured = provider.withFetchedModels(fetched)
        val options = configured.assistantModelChoices()
        assertEquals(listOf("gpt-4o"), options.map { it.model.id })
        assertFalse(options.single().textTrial)
    }

    @Test
    fun legitimateEmptyCatalogEndsOldSelectionsAndInvalidResponsesDoNotCountAsEmpty() {
        val last = provider.withFetchedModels(importModels("""[{"id":"qa-7f3e"}]"""))
        val empty = last.withFetchedModels(importModels("[]"))
        assertNull(empty.assistantModelChoice("qa-7f3e"))
        assertFalse(empty.catalogFetchRequired())
        assertFalse(provider.withFetchedModels(emptyList()).availableModelSelectionInitialized)
        assertThrows(IllegalStateException::class.java) { importModels("""[{}]""") }
        assertThrows(IllegalStateException::class.java) { importModels("""[{"id":"qa-7f3e"},{}]""") }
        assertThrows(IllegalStateException::class.java) {
            OpenAiCompatibleProvider().parseModels(JsonParser.parseString("{}").asJsonObject, provider.modelConfigurationKey())
        }
    }

    @Test
    fun cachedOpenAiEmbeddingIdentifiersCannotBeUsedAsTextChat() {
        val ids = listOf("text-embedding-3-small", "text-embedding-3-large", "text-embedding-ada-002")
        val previous = ids.map { id ->
            AiModel(id, inputModalities = listOf(AiModelModality.TEXT), outputModalities = listOf(AiModelModality.TEXT))
        }
        assertTrue(previous.all { AiModelRegistry.enrich(AiModel(it.id)).safeType() == AiModelType.EMBEDDING })
        val current = enrichUnspecifiedAiModels(previous)
        assertTrue(current.all { it.safeType() == AiModelType.EMBEDDING })
        assertTrue(provider.copy(models = current).assistantModelChoices().isEmpty())
    }

    private fun importModels(entries: String): List<AiModel> =
        OpenAiCompatibleProvider().parseModels(
            JsonParser.parseString("""{"data":$entries}""").asJsonObject,
            provider.modelConfigurationKey(),
        ).map { AiModelRegistry.enrich(it).withCatalogCapabilities(it.modelInfo) }
}
