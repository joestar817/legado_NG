package io.legado.app.ui.config

import io.legado.app.R
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelSelectionTabsTest {
    private val providers = listOf(
        provider("first", "First Provider", model("shared", "Alpha", "alpha-alias")),
        provider("second", "Second Provider", model("before", "Beta"), model("shared", "Gamma", "gamma-alias")),
    )

    @Test
    fun providerTabsRequireEnabledAndNonBlankKeyButNotCachedModels() {
        val base = AiProviderSetting(
            id = "base", type = AiProviderType.OPENAI,
            name = "Base", baseUrl = "https://example.invalid",
        )
        val settings = listOf(
            base.copy(id = "no-key", models = listOf(AiModel("cached"))),
            base.copy(id = "blank-key", apiKey = "  "),
            base.copy(id = "disabled", enabled = false, apiKey = "key"),
            base.copy(id = "empty-catalog", apiKey = "key"),
            base.copy(id = "cached", apiKey = "key", models = listOf(AiModel("model"))),
        )
        assertEquals(
            listOf("empty-catalog", "cached"),
            settings.modelSelectionEligibleProviders().map { it.id },
        )
    }

    @Test
    fun updatingOneCatalogKeepsOtherProviderResultsAndOrder() {
        val state = AiModelSelectionSheetState(
            title = "Model", emptyText = null, providers = providers,
        )
        val updatedFirst = provider("first", "First Provider", model("new", "New"))
        val updatedSecond = provider("second", "Second Provider", model("later", "Later"))
        val afterBoth = state.withUpdatedProvider("second", updatedSecond)
            .withUpdatedProvider("first", updatedFirst)
        assertEquals(listOf(updatedFirst, updatedSecond), afterBoth.providers)
        assertEquals(listOf(updatedSecond), afterBoth.withUpdatedProvider("first", null).providers)
    }

    @Test
    fun searchKeepsProviderPagesAndMatchesOnlyModelFields() {
        val result = providers.filterModels("GAMMA-ALIAS")
        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(emptyList<AiModelSelectionItemUiModel>(), result[0].models)
        assertEquals(listOf("shared"), result[1].models.map { it.id })
        assertEquals(listOf(0, 0), providers.filterModels("First Provider").map { it.models.size })
        assertEquals(listOf(1, 1), providers.filterModels("shared").map { it.models.size })
    }

    @Test
    fun selectedModelIsLocatedWithinTheCorrectProviderAfterSearch() {
        val state = AiModelSelectionSheetState(
            title = "Model", emptyText = null, providers = providers,
            selectedProviderId = "second", selectedModelId = "shared",
        )
        val target = state.selectedTarget(includeFollowAssistant = false)!!
        assertEquals(AiModelSelectionPosition("provider:second", 1), target.position(providers, false))
        assertEquals(AiModelSelectionPosition("provider:second", 0), target.position(providers.filterModels("Gamma"), false))
        assertNull(target.position(providers.filterModels("Alpha"), false))
    }

    @Test
    fun followAssistantRemainsASelectableAndLocatablePage() {
        val state = AiModelSelectionSheetState(
            title = "Model", emptyText = null, providers = providers,
            followAssistantLabel = "Follow chat model", followAssistantSelected = true,
        )
        val target = state.selectedTarget(includeFollowAssistant = true)!!
        assertEquals(AiModelSelectionPosition("follow-assistant", 0), target.position(providers, true))
        assertNull(target.position(providers, false))
        assertNull(state.selectedTarget(includeFollowAssistant = false))
    }

    private fun provider(id: String, name: String, vararg models: AiModelSelectionItemUiModel) =
        AiModelSelectionProviderUiModel(id, name, R.drawable.ic_cfg_web, models.toList())

    private fun model(id: String, name: String, vararg aliases: String) =
        AiModelSelectionItemUiModel(id, name, aliases.toList(), R.drawable.ic_cfg_web, emptyList())
}
