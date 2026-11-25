package io.legado.app.ui.config

import io.legado.app.R
import io.legado.app.help.ai.AiDefaultProviders
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelInfo
import io.legado.app.help.ai.AiModelValue
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.modelConfigurationKey
import io.legado.app.help.ai.usesAccountLogin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelSelectionTabsTest {
    private val providers = listOf(
        provider("first", "First Provider", model("shared", "Alpha", "alpha-alias")),
        provider("second", "Second Provider", model("before", "Beta"), model("shared", "Gamma", "gamma-alias")),
    )

    @Test
    fun apiKeyProviderTabsRequireUsableCredentialsButNotCachedModels() {
        val base = AiProviderSetting(
            id = "base", type = AiProviderType.OPENAI,
            name = "Base", baseUrl = "https://example.invalid",
        )
        val settings = listOf(
            base.copy(id = "no-key", models = listOf(AiModel("cached"))),
            base.copy(id = "blank-key", apiKey = "  "),
            base.copy(id = "key-for-another-endpoint", apiKey = "key", apiKeyOrigin = "https://other.invalid/"),
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
    fun providerTabsAcceptAccountCredentialsWithoutAnApiKey() {
        val accounts = AiDefaultProviders.all().filter { it.usesAccountLogin() }
        val signedInIds = accounts.map { it.id }.toSet()
        val available = accounts.modelSelectionEligibleProviders { it.id in signedInIds }
        assertEquals(accounts.map { it.id }, available.map { it.id })

        val signedOutId = AiDefaultProviders.CHATGPT_ID
        val disabledId = AiDefaultProviders.CODEX_ID
        val settings = accounts.map { it.copy(enabled = it.id != disabledId) }
        assertEquals(
            listOf(AiDefaultProviders.WORK_BUDDY_ID),
            settings.modelSelectionEligibleProviders { it.id != signedOutId }.map { it.id },
        )
    }

    @Test
    fun streamingHintIsOnlyShownWhereStreamingCanBeConfigured() {
        val defaults = AiDefaultProviders.all()
        val accountModels = listOf(
            AiDefaultProviders.CHATGPT_ID to "gpt-6.2-sol",
            AiDefaultProviders.CODEX_ID to "gpt-6.2-sol",
            AiDefaultProviders.WORK_BUDDY_ID to "hy4",
        )
        accountModels.forEach { (providerId, modelId) ->
            val accountProvider = defaults.single { it.id == providerId }
            val item = accountProvider.toModelSelectionProviderUiModel(
                listOf(AiModel(modelId)), showGenerationHints = true,
            ).models.single()
            assertNull(providerId, item.generationHintRes)
        }
        val bailian = defaults.first { it.id == "aliyun_bailian" }
        val bailianItem = bailian.toModelSelectionProviderUiModel(
            listOf(AiModel("qwen3-235b-a22b")), showGenerationHints = true,
        ).models.single()

        assertEquals(R.string.ai_model_streaming_only, bailianItem.generationHintRes)
    }

    @Test
    fun modelSelectionShowsSameNameRatesById() {
        val provider = AiDefaultProviders.all().single { it.id == AiDefaultProviders.WORK_BUDDY_ID }
        val models = listOf("hy4flash-free" to "0.00", "hy4flash" to "0.03").map { (id, rate) ->
            AiModel(
                id = id,
                name = "Hy4 flash",
                modelInfo = AiModelInfo(
                    endpoint = provider.modelConfigurationKey(),
                    contextWindow = AiModelValue(960_000),
                    creditMultiplier = rate,
                ),
            )
        }
        val items = provider.toModelSelectionProviderUiModel(models).models.associateBy { it.id }

        assertEquals("0.00×", items.getValue("hy4flash-free").creditMultiplierLabel)
        assertEquals("0.03×", items.getValue("hy4flash").creditMultiplierLabel)
        assertEquals(listOf(960_000, 960_000), items.values.map { it.contextWindow })
    }

    @Test
    fun modelSelectionShowsContextWindowsFromAllProviderCatalogsWhenKnown() {
        AiDefaultProviders.all().forEach { provider ->
            val items = provider.toModelSelectionProviderUiModel(
                listOf(
                    AiModel(
                        id = "fixture-with-context",
                        modelInfo = AiModelInfo(
                            endpoint = provider.modelConfigurationKey(),
                            contextWindow = AiModelValue(128_000),
                        ),
                    ),
                    AiModel("fixture-without-context"),
                ),
            ).models

            assertEquals(provider.id, 128_000, items[0].contextWindow)
            assertNull(provider.id, items[0].creditMultiplierLabel)
            assertNull(provider.id, items[1].contextWindow)
        }
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
        val target = state.selectedTarget()!!
        val third = provider("third", "Third Provider", model("other", "Other"))
        val ordered = (providers + third).prioritizeDrawerGroup { it.id == target.providerId }
        assertEquals(listOf("second", "first", "third"), ordered.map { it.id })
        assertEquals(AiModelSelectionPosition("provider:second", 1), target.position(ordered))
        assertEquals(AiModelSelectionPosition("provider:second", 0), target.position(ordered.filterModels("Gamma")))
        assertNull(target.position(ordered.filterModels("Alpha")))
    }

    private fun provider(id: String, name: String, vararg models: AiModelSelectionItemUiModel) =
        AiModelSelectionProviderUiModel(id, name, R.drawable.ic_cfg_web, models.toList())

    private fun model(id: String, name: String, vararg aliases: String) =
        AiModelSelectionItemUiModel(id, name, aliases.toList(), R.drawable.ic_cfg_web, emptyList())
}
