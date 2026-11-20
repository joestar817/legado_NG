package io.legado.app.ui.config

import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelCapabilityField
import io.legado.app.help.ai.AiModelInfo
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelRegistry
import io.legado.app.help.ai.AiModelSettings
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiModelValue
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.enrichUnspecifiedAiModels
import io.legado.app.help.ai.withCatalogCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderModelFilterTest {

    @Test
    fun `OCR has its own model type while rerankers and unidentified models go to other`() {
        val expected = mapOf(
            "deepseek-ai/DeepSeek-R1" to AiModelType.CHAT,
            "Kwai-Kolors/Kolors" to AiModelType.IMAGE,
            "BAAI/bge-large-en-v1.5" to AiModelType.EMBEDDING,
            "Wan-AI/Wan2.2-T2V-A14B" to AiModelType.VIDEO,
            "FunAudioLLM/SenseVoiceSmall" to AiModelType.ASR,
            "FunAudioLLM/CosyVoice2-0.5B" to AiModelType.TTS,
            "BAAI/bge-reranker-v2-m3" to null,
            "deepseek-ai/DeepSeek-OCR" to AiModelType.OCR,
            "PaddlePaddle/PaddleOCR-VL" to AiModelType.OCR,
            "Qwen/Qwen3-VL-OCR" to AiModelType.OCR,
            "ocr-lab/DeepSeek-R1" to AiModelType.CHAT,
            "private-7a3e" to null,
        )

        expected.forEach { (id, type) ->
            assertEquals(id, type, AiModelRegistry.enrich(AiModel(id)).providerListType(null))
        }
    }

    @Test
    fun `existing OCR entries with text capabilities are recognized and manual types still win`() {
        val old = AiModel(
            id = "deepseek-ai/DeepSeek-OCR",
            inputModalities = listOf(AiModelModality.TEXT, AiModelModality.IMAGE),
            outputModalities = listOf(AiModelModality.TEXT),
        )
        val manuallyTyped = old.copy(
            modelSettings = AiModelSettings("current", capabilityOverrides = setOf(AiModelCapabilityField.TYPE))
        )
        val (recognized, kept) = enrichUnspecifiedAiModels(listOf(old, manuallyTyped), "current")
        assertEquals(AiModelType.OCR, recognized.type)
        assertEquals(old.inputModalities, recognized.inputModalities)
        assertEquals(old.outputModalities, recognized.outputModalities)
        assertEquals(AiModelType.OCR, recognized.providerListType("current"))
        assertEquals(AiModelType.CHAT, kept.providerListType("current"))
    }

    @Test
    fun `catalog modalities and a current manual type can classify a custom model`() {
        val model = AiModel("private-7a3e")
        val info = AiModelInfo(
            endpoint = "current",
            inputModalities = AiModelValue(listOf("text", "image")),
            outputModalities = AiModelValue(listOf("text")),
        )
        assertEquals(
            AiModelType.CHAT,
            model.withCatalogCapabilities(info).providerListType(info.endpoint)
        )
        val manuallyTyped = model.copy(
            modelSettings = AiModelSettings("current", capabilityOverrides = setOf(AiModelCapabilityField.TYPE))
        )
        assertEquals(AiModelType.CHAT, manuallyTyped.providerListType("current"))
        assertNull(manuallyTyped.providerListType("another"))
    }

    @Test
    fun `search still matches IDs names and publishers inside the selected type`() {
        val rows = listOf(
            row("provider/Qwen-4", "Qwen Max", AiModelType.CHAT, listOf("Bailian")),
            row("qwen-reranker", "Qwen Reranker", null, listOf("Bailian")),
            row("deepseek-flash", "My Flash", AiModelType.CHAT, listOf("DeepSeek V4.1")),
            row("deepseek-ocr", "DeepSeek OCR", AiModelType.OCR),
        )
        assertSame(rows, rows.filterProviderModels("", AiProviderModelFilter.ALL))
        assertEquals(listOf(rows[0]), rows.filterProviderModels("QWEN-4", AiProviderModelFilter.CHAT))
        assertEquals(listOf(rows[0]), rows.filterProviderModels("bailian", AiProviderModelFilter.CHAT))
        assertEquals(listOf(rows[1]), rows.filterProviderModels("Qwen", AiProviderModelFilter.OTHER))
        assertEquals(listOf(rows[2]), rows.filterProviderModels("my flash", AiProviderModelFilter.CHAT))
        assertEquals(listOf(rows[2]), rows.filterProviderModels("v4.1", AiProviderModelFilter.CHAT))
        assertEquals(listOf(rows[3]), rows.filterProviderModels("seek", AiProviderModelFilter.OCR))
        assertTrue(rows.filterProviderModels("ocr", AiProviderModelFilter.OTHER).isEmpty())
        assertTrue(rows.filterProviderModels("miss", AiProviderModelFilter.CHAT).isEmpty())
    }

    @Test
    fun `filter menu lists only catalog types in the standard order`() {
        val rows = listOf(
            row("mimo-v2.5-tts", "MiMo TTS", AiModelType.TTS),
            row("mimo-v2.5-asr", "MiMo ASR", AiModelType.ASR),
            row("mimo-v2.6-flash", "MiMo Flash", AiModelType.CHAT),
            row("mimo-v2.6-pro", "MiMo Pro", AiModelType.CHAT),
        )
        val filters = listOf(
            AiProviderModelFilter.ALL,
            AiProviderModelFilter.CHAT,
            AiProviderModelFilter.ASR,
            AiProviderModelFilter.TTS,
        )
        assertEquals(filters, rows.availableModelFilters())
        assertEquals(
            filters + AiProviderModelFilter.OTHER,
            (rows + row("private", "Private model", null)).availableModelFilters(),
        )
        assertTrue(emptyList<AiProviderModelItemUiModel>().availableModelFilters().isEmpty())
    }

    @Test
    fun `a selected type falls back to all when it disappears from the catalog`() {
        val chat = row("chat", "Chat", AiModelType.CHAT)
        val ocr = row("ocr", "OCR", AiModelType.OCR)
        val filter = AiProviderModelFilter.OCR
        assertEquals(filter, filter.orAllFor(listOf(chat, ocr)))
        assertEquals(AiProviderModelFilter.ALL, filter.orAllFor(listOf(chat)))
        assertEquals(AiProviderModelFilter.ALL, filter.orAllFor(emptyList()))
    }

    @Test
    fun `bulk target is disable when all visible models are enabled and enable otherwise`() {
        val disabled = row("ocr-one", "OCR", AiModelType.OCR)
        val enabled = row("ocr-two", "OCR 2", AiModelType.OCR).copy(selected = true)
        assertNull(emptyList<AiProviderModelItemUiModel>().bulkModelEnableTarget())
        assertEquals(true, listOf(disabled).bulkModelEnableTarget())
        assertEquals(true, listOf(enabled, disabled).bulkModelEnableTarget())
        assertEquals(false, listOf(enabled).bulkModelEnableTarget())
    }

    @Test
    fun `bulk changes apply only to search and type results still in the current catalog`() {
        val provider = provider().copy(
            availableModelIds = listOf("chat-one", "chat-two", "ocr-one", "previously-listed"),
            availableModelSelectionInitialized = true,
        )
        val visibleIds = listOf(
            row("chat-one", "Matching chat", AiModelType.CHAT),
            row("chat-two", "Hidden chat", AiModelType.CHAT),
            row("ocr-one", "Matching OCR", AiModelType.OCR),
        ).filterProviderModels("Matching", AiProviderModelFilter.CHAT).mapTo(hashSetOf()) { it.id }
        val removed = provider.withModelSelection(visibleIds, enabled = false)
        assertEquals(listOf("chat-two", "ocr-one", "previously-listed"), removed.availableModelIds)
        val restored = removed.withModelSelection(visibleIds + "no-longer-listed", enabled = true)
        assertEquals(provider.availableModelIds.toSet(), restored.availableModelIds.toSet())
        assertSame(restored, restored.withModelSelection(visibleIds, enabled = true))
    }

    @Test
    fun `initial selection respects all default enables and invalid catalogs reject bulk changes`() {
        val provider = provider()
        val ids = setOf("chat-one")
        val updated = provider.withModelSelection(ids, enabled = false)
        assertEquals(listOf("chat-two", "ocr-one"), updated.availableModelIds)
        assertTrue(updated.availableModelSelectionInitialized)
        val invalidated = provider.copy(catalogRefreshRequired = true)
        assertSame(invalidated, invalidated.withModelSelection(ids, enabled = false))
        assertSame(provider, provider.withModelSelection(emptySet(), enabled = false))
    }

    private fun provider() = AiProviderSetting(
        id = "fixture",
        type = AiProviderType.OPENAI,
        builtIn = false,
        name = "Fixture",
        apiKey = "fixture-key",
        baseUrl = "https://fixture.invalid",
        models = listOf(AiModel("chat-one"), AiModel("chat-two"), AiModel("ocr-one")),
    )

    private fun row(
        id: String,
        name: String,
        type: AiModelType?,
        aliases: List<String> = emptyList(),
    ) = AiProviderModelItemUiModel(id, name, 0, emptyList(), false, type, aliases)
}
