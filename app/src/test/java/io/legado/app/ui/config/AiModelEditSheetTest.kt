package io.legado.app.ui.config

import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelCapabilityField
import io.legado.app.help.ai.AiModelInterfacePreference
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiModelInfo
import io.legado.app.help.ai.AiModelSettings
import io.legado.app.help.ai.AiModelValue
import io.legado.app.help.ai.AiDefaultProviders
import io.legado.app.help.ai.withFetchedModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelEditSheetTest {

    @Test
    fun `known chat model exposes registry capabilities`() {
        val defaults = AiModel(id = "gpt-4o").registryCapabilityDefaults()

        assertEquals(AiModelType.CHAT, defaults?.type)
        assertTrue(AiModelModality.TEXT in defaults!!.inputModalities)
        assertTrue(AiModelModality.IMAGE in defaults.inputModalities)
        assertTrue(AiModelModality.TEXT in defaults.outputModalities)
        assertTrue(AiModelAbility.TOOL in defaults.abilities)
    }

    @Test
    fun `known image model exposes image type`() {
        val defaults = AiModel(id = "gemini-2.5-flash-image").registryCapabilityDefaults()

        assertEquals(AiModelType.IMAGE, defaults?.type)
        assertTrue(AiModelModality.IMAGE in defaults!!.outputModalities)
    }

    @Test
    fun `restore selects OCR in the model editor and clears the manual type`() {
        val info = AiModelInfo("current")
        val draft = AiModelEditDraft(
            "DeepSeek OCR", AiModelType.CHAT, emptySet(), emptySet(), emptySet(),
            AiModelSettings(info.endpoint, capabilityOverrides = setOf(AiModelCapabilityField.TYPE)),
        )
        val detected = detectedModelCapabilities(
            info, AiModel("deepseek-ai/DeepSeek-OCR").registryCapabilityDefaults()
        )
        val restored = restoreModelDraft(draft, info, detected)
        assertEquals(AiModelType.OCR, restored.type)
        assertEquals(setOf(AiModelModality.TEXT, AiModelModality.IMAGE), restored.inputModalities)
        assertEquals(setOf(AiModelModality.TEXT), restored.outputModalities)
        assertFalse(requireNotNull(restored.modelSettings).overridesCapability(AiModelCapabilityField.TYPE))
    }

    @Test
    fun `unknown model has no registry defaults`() {
        assertNull(AiModel(id = "my-private-model-xyz").registryCapabilityDefaults())
        assertNull(AiModel(id = "").registryCapabilityDefaults())
    }

    @Test
    fun `restore uses an explicit catalog modality instead of adding the registry vision hint`() {
        val endpoint = "https://api.deepseek.com/models"
        val model = AiModel(id = "deepseek-flash")
        val info = AiModelInfo(
            endpoint = endpoint,
            inputModalities = AiModelValue(listOf("text"), fromPreviousCatalog = true),
            outputModalities = AiModelValue(listOf("text")),
            contextWindow = AiModelValue(1_048_576),
            maxOutputTokens = AiModelValue(393_216),
            effortLevels = AiModelValue(listOf("low", "high", "max")),
        )
        val draft = AiModelEditDraft(
            "我的 Flash", AiModelType.CHAT, setOf(AiModelModality.IMAGE), emptySet(), emptySet(),
            AiModelSettings(
                endpoint,
                contextWindowTokens = 32_768,
                maxOutputTokens = 4096,
                reasoningEffort = "max",
                referenceContextWindowTokens = 128_123,
                referenceMaxOutputTokens = 12_345,
                enabledReasoningEfforts = listOf("ultra"),
            ),
        )
        val restored = restoreModelDraft(draft, info, detectedModelCapabilities(info, model.registryCapabilityDefaults()))
        assertEquals("我的 Flash", restored.displayName)
        assertEquals(setOf(AiModelModality.TEXT), restored.inputModalities)
        assertEquals(setOf(AiModelModality.TEXT), restored.outputModalities)
        assertTrue(AiModelAbility.TOOL in restored.abilities)
        assertTrue(AiModelAbility.REASONING in restored.abilities)
        val preferences = requireNotNull(restored.modelSettings)
        assertFalse(preferences.capabilitiesEdited)
        assertFalse(preferences.followModelContext)
        assertEquals(32_768, preferences.contextWindowTokens)
        assertNull(preferences.referenceContextWindowTokens)
        assertEquals(1_048_576, preferences.referenceContextWindow(info))
        assertNull(preferences.referenceMaxOutputTokens)
        assertEquals(393_216, preferences.referenceMaxOutput(info))
        assertEquals(4_096, preferences.maxOutputTokens)
        assertNull(preferences.enabledReasoningEfforts)
        assertEquals("max", preferences.reasoningEffort)
    }

    @Test
    fun `restore keeps values without catalog data or a fallback rule`() {
        val endpoint = "https://api.deepseek.com/models"
        val info = AiModelInfo(endpoint, inputModalities = AiModelValue(emptyList()))
        val settings = AiModelSettings(
            endpoint,
            capabilitiesEdited = true,
            contextWindowTokens = 32_768,
            maxOutputTokens = 4_096,
            referenceContextWindowTokens = 128_123,
            referenceMaxOutputTokens = 12_345,
            enabledReasoningEfforts = listOf("max"),
        )
        val draft = AiModelEditDraft(
            "Custom", AiModelType.CHAT, setOf(AiModelModality.IMAGE), setOf(AiModelModality.TEXT),
            setOf(AiModelAbility.TOOL), settings,
        )
        val restored = restoreModelDraft(draft, info, detectedModelCapabilities(info, null))
        assertTrue(restored.inputModalities.isEmpty())
        assertEquals(draft.outputModalities, restored.outputModalities)
        assertEquals(draft.abilities, restored.abilities)
        val preferences = requireNotNull(restored.modelSettings)
        assertEquals(settings.referenceContextWindowTokens, preferences.referenceContextWindowTokens)
        assertEquals(settings.referenceMaxOutputTokens, preferences.referenceMaxOutputTokens)
        assertEquals(settings.enabledReasoningEfforts, preferences.enabledReasoningEfforts)
        assertFalse(preferences.overridesCapability(AiModelCapabilityField.INPUT_MODALITIES))
        assertTrue(preferences.overridesCapability(AiModelCapabilityField.OUTPUT_MODALITIES))
        assertTrue(preferences.overridesCapability(AiModelCapabilityField.TOOL_CALLING))
    }

    @Test
    fun `restoring a draft releases known defaults for the next refresh after saving`() {
        val provider = AiDefaultProviders.all().single { it.id == "deepseek" }
        val endpoint = "https://api.deepseek.com/models"
        val oldInfo = AiModelInfo(endpoint, inputModalities = AiModelValue(listOf("text", "image")),
            outputModalities = AiModelValue(listOf("text")), reasoning = AiModelValue(true))
        val text = setOf(AiModelModality.TEXT)
        val draft = AiModelEditDraft(
            "My name", AiModelType.CHAT, text, text, setOf(AiModelAbility.TOOL),
            AiModelSettings(
                endpoint,
                capabilitiesEdited = true,
                referenceContextWindowTokens = 32_123,
                interfacePreference = AiModelInterfacePreference.RESPONSES,
            ),
        )
        val model = AiModel("deepseek-flash", displayName = draft.displayName, inputModalities = text.toList(),
            outputModalities = text.toList(), abilities = draft.abilities.toList(),
            modelInfo = oldInfo, modelSettings = draft.modelSettings)
        val restored = restoreModelDraft(draft, oldInfo, detectedModelCapabilities(oldInfo, model.registryCapabilityDefaults()))
        assertEquals(text, draft.inputModalities)
        assertTrue(draft.modelSettings!!.capabilitiesEdited)
        assertEquals(setOf(AiModelModality.TEXT, AiModelModality.IMAGE), restored.inputModalities)
        assertTrue(AiModelAbility.REASONING in restored.abilities)
        val saved = model.copy(
            inputModalities = restored.inputModalities.toList(), abilities = restored.abilities.toList(),
            modelSettings = restored.modelSettings,
        )
        val refreshedInfo = oldInfo.copy(inputModalities = AiModelValue(listOf("text")), reasoning = AiModelValue(false))
        val fetched = AiModel(model.id, modelInfo = refreshedInfo)
        val cancelled = provider.copy(models = listOf(model)).withFetchedModels(listOf(fetched)).models.single()
        assertTrue(cancelled.modelSettings!!.capabilitiesEdited)
        assertEquals(AiModelInterfacePreference.RESPONSES, cancelled.modelSettings?.interfacePreference)
        val refreshed = provider.copy(models = listOf(saved)).withFetchedModels(listOf(fetched)).models.single()
        assertEquals(text, refreshed.inputModalities.toSet())
        assertFalse(AiModelAbility.REASONING in refreshed.abilities)
        assertEquals("My name", refreshed.displayName)
        assertEquals(32_123, refreshed.modelSettings?.referenceContextWindowTokens)
        assertNull(refreshed.modelSettings?.interfacePreference)
    }
}
