package io.legado.app.help.ai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogSourceTest {
    private val provider = AiProviderSetting(
        id = "proxy", name = "Proxy", type = AiProviderType.OPENAI, builtIn = false,
        apiKey = "fixture-old", baseUrl = "https://fixture.invalid/v1",
    ).withRequestConfiguration(null)
    private val configuration = provider.modelConfigurationKey()
    private val catalogModel = AiModel("model-id", modelInfo = AiModelInfo(
        configuration, contextWindow = AiModelValue(32_000),
        inputModalities = AiModelValue(listOf("text", "image")), outputModalities = AiModelValue(listOf("text")),
        effortLevels = AiModelValue(listOf("high")), toolCalling = AiModelValue(true), catalogSourceId = "source-a",
    ))

    @Test
    fun newApiKeyCannotReuseOldCatalogButKeepsManualModelConfiguration() {
        val original = provider.withFetchedModels(listOf(catalogModel)).let {
            val saved = it.models.single().copy(
                displayName = "My model",
                modelSettings = AiModelSettings(configuration, referenceContextWindowTokens = 24_000,
                    enabledReasoningEfforts = listOf("max")),
            )
            it.copy(models = listOf(saved))
        }
        val changed = original.copy(apiKey = "fixture-new").withRequestConfiguration(original)
        assertTrue(changed.catalogRefreshRequired)
        assertNull(changed.assistantModelChoice(catalogModel.id))
        assertNull(changed.effectiveModelInfo(changed.models.single()).contextWindow)
        assertNull(initialFetchedModelsUpdate(original, changed, listOf(catalogModel)))
        val sparse = AiModel(catalogModel.id, modelInfo = AiModelInfo(configuration, catalogSourceId = "source-a"))
        val refreshed = initialFetchedModelsUpdate(changed, changed, listOf(sparse))!!
        assertFalse(refreshed.catalogRefreshRequired)
        val current = refreshed.models.single()
        assertNull(current.modelInfo?.contextWindow)
        assertTrue(current.inputModalities.isEmpty())
        assertTrue(current.abilities.isEmpty())
        assertEquals("My model", current.displayName)
        assertEquals(24_000, refreshed.assistantContextWindowLimit(current))
        assertEquals(listOf("max"), current.modelSettings?.enabledReasoningEfforts)
    }

    @Test
    fun sameFinalCatalogCanFillMissingFieldsAndAnotherPathCannot() {
        val initial = provider.withFetchedModels(listOf(catalogModel))
        val sparse = AiModel(catalogModel.id, modelInfo = AiModelInfo(configuration, catalogSourceId = "source-a"))
        val fromSameRoute = initial.withFetchedModels(listOf(sparse)).models.single()
        assertEquals(AiModelValue(32_000, fromPreviousCatalog = true), fromSameRoute.modelInfo?.contextWindow)
        val anotherRoute = sparse.copy(modelInfo = sparse.modelInfo?.copy(catalogSourceId = "source-b"))
        val fromOtherRoute = initial.withFetchedModels(listOf(anotherRoute)).models.single()
        assertNull(fromOtherRoute.modelInfo?.contextWindow)
        assertTrue(fromOtherRoute.abilities.isEmpty())
    }

    @Test
    fun anInFlightCatalogStillCannotBeSavedAfterTheSettingsChangeAwayAndBack() {
        val moved = provider.copy(baseUrl = "https://other.invalid/v1").withRequestConfiguration(provider)
        val returned = moved.copy(baseUrl = provider.baseUrl).withRequestConfiguration(moved)
        assertNotEquals(provider.catalogRevision, returned.catalogRevision)
        assertFalse(returned.matchesCatalogRequest(provider))
        assertNull(initialFetchedModelsUpdate(provider, returned, listOf(catalogModel)))
        assertTrue(returned.matchesCatalogRequest(returned.copy(timeoutSeconds = 5)))
        assertEquals(provider.apiKey, returned.apiKeyForRequest())
    }

    @Test
    fun storedKeyStaysAtItsOriginalOriginUntilEdited() {
        val moved = provider.copy(baseUrl = "https://fixture.invalid:444/v1").withRequestConfiguration(provider)
        val readBack = Gson().fromJson(Gson().toJson(moved), AiProviderSetting::class.java)
        assertEquals(moved.catalogRevision, readBack.catalogRevision)
        assertTrue(readBack.apiKeyNeedsReentry())
        assertThrows(AiEndpointException::class.java) { readBack.apiKeyForRequest() }
        val explicitlyReused = readBack.withRequestConfiguration(readBack, useExistingKeyHere = true)
        assertEquals("fixture-old", explicitlyReused.apiKeyForRequest())
        val renewed = readBack.copy(apiKey = "fixture-replaced").withRequestConfiguration(readBack)
        assertEquals("fixture-replaced", renewed.apiKeyForRequest())
        val pathOnly = provider.copy(baseUrl = "https://fixture.invalid/v2").withRequestConfiguration(provider)
        assertTrue(pathOnly.catalogRefreshRequired)
        assertEquals(provider.apiKey, pathOnly.apiKeyForRequest())
    }
}
