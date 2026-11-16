package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiInitialModelCatalogTest {
    private val provider = AiProviderSetting(
        id = "test",
        type = AiProviderType.OPENAI,
        name = "Test",
        apiKey = "test-key",
        baseUrl = "https://example.invalid",
    )
    private val fetched = listOf(AiModel("first"), AiModel("second"))

    @Test
    fun firstFetchEnablesModelsButKeepsAnExplicitEmptySelection() {
        val initial = initialFetchedModelsUpdate(provider, provider, fetched)!!
        assertEquals(fetched, initial.models)
        assertEquals(listOf("first", "second"), initial.availableModelIds)
        assertEquals(true, initial.availableModelSelectionInitialized)

        val disabled = provider.copy(availableModelSelectionInitialized = true)
        val result = initialFetchedModelsUpdate(provider, disabled, fetched)!!
        assertEquals(emptyList<String>(), result.availableModelIds)
    }

    @Test
    fun requestDoesNotReplaceModelsPopulatedWhileItWasRunning() {
        val current = provider.copy(models = listOf(AiModel("newer")))
        assertEquals(current, initialFetchedModelsUpdate(provider, current, fetched))
    }

    @Test
    fun changedOrDisabledProviderCannotReceiveStaleCatalog() {
        listOf(
            provider.copy(enabled = false),
            provider.copy(apiKey = "different"),
            provider.copy(baseUrl = "https://other.invalid"),
            provider.copy(modelsUrl = "/other-models"),
            provider.copy(useCustomModelsUrl = true),
            provider.copy(type = AiProviderType.GOOGLE),
        ).forEach { current ->
            assertNull(initialFetchedModelsUpdate(provider, current, fetched))
        }
    }
}
