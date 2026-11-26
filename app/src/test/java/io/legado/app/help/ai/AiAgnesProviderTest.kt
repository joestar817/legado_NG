package io.legado.app.help.ai

import com.google.gson.JsonParser
import io.legado.app.ui.config.AiProviderHelpDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAgnesProviderTest {
    private val provider = AiDefaultProviders.all().single { it.id == AiDefaultProviders.AGNES_ID }

    @Test
    fun presetUsesTheStandardKeyAndOpenAiRoutes() {
        assertEquals("Agnes AI", provider.name)
        assertTrue(provider.builtIn && provider.enabled && provider.streamResponseEnabled)
        assertTrue(provider.apiKey.isEmpty() && provider.assistantModelChoices().isEmpty())
        assertEquals(AiProviderType.OPENAI, provider.type)
        assertEquals("agnes-3.0-flash", provider.model)
        assertEquals(listOf("https://apihub.agnes-ai.com/v1/models"), AiModelEndpointResolver.candidates(provider))
        val route = provider.resolveModelGenerationRoute(AiModel(provider.model))
        assertEquals(AiModelGenerationTestProtocol.CHAT_COMPLETIONS, route.protocol)
        assertNull(route.issue)
        assertEquals(
            "https://apihub.agnes-ai.com/v1/chat/completions",
            provider.generationUrl(route.protocol).toString(),
        )
        assertEquals(
            "https://apihub.agnes-ai.com/v1/responses",
            provider.generationUrl(AiModelGenerationTestProtocol.RESPONSES).toString(),
        )
        assertEquals("agnesProviderHelp", AiProviderHelpDocuments.forProvider(provider.id, true, provider.baseUrl))
    }

    @Test
    fun idOnlyCatalogKeepsMediaAndDecisionModelsOutOfAssistantSelection() {
        val setting = provider.copy(apiKey = "test-key")
        val catalog = JsonParser.parseString(
            """
            {"object":"list","data":[
              {"id":"agnes-2.5-pro-alpha"},{"id":"agnes-2.5-pro-theta"},
              {"id":"agnes-video-2.5-flash"},{"id":"agnes-3.0-flash"},
              {"id":"agnes-image-2.1-flash"},{"id":"agnes-video-2.5"},
              {"id":"agnes-image-2.0-flash"},{"id":"agnes-2.5-flash"},
              {"id":"agnes-3.0-pro"},{"id":"agnes-2.5-pro"},
              {"id":"agnes-2.5-pro-beta"},{"id":"agnes-image-2.5-flash"},{"id":"agnes-sys1-1.0"}
            ]}
            """.trimIndent(),
        ).asJsonObject
        val models = OpenAiCompatibleProvider().parseModels(catalog, setting.modelConfigurationKey()).map {
            AiModelRegistry.enrich(it).withCatalogCapabilities(it.modelInfo)
        }
        val refreshed = setting.withFetchedModels(models)
        assertEquals(13, refreshed.models.size)
        assertEquals(provider.model, refreshed.model)
        assertEquals(
            setOf(
                "agnes-3.0-flash", "agnes-3.0-pro", "agnes-2.5-flash", "agnes-2.5-pro",
                "agnes-2.5-pro-alpha", "agnes-2.5-pro-beta", "agnes-2.5-pro-theta",
            ),
            refreshed.assistantModelChoices().map { it.model.id }.toSet(),
        )
        assertEquals(AiModelType.IMAGE, refreshed.models.single { it.id == "agnes-image-2.5-flash" }.type)
        assertEquals(AiModelType.VIDEO, refreshed.models.single { it.id == "agnes-video-2.5" }.type)
        assertTrue(refreshed.assistantModelChoice(provider.model)!!.model.abilities.contains(AiModelAbility.TOOL))
        for (id in listOf("agnes-3.0-pro", "agnes-2.5-pro", "agnes-2.5-pro-beta", "agnes-2.5-pro-theta")) {
            val model = requireNotNull(refreshed.assistantModelChoice(id)).model
            assertTrue(AiModelAbility.TOOL in model.abilities)
        }
    }

    @Test
    fun newToolDefaultsApplyToCachedModelsAndRespectExplicitOverrides() {
        val endpoint = provider.modelConfigurationKey()
        val cached = AiModel("agnes-3.0-pro", modelInfo = AiModelInfo(endpoint))
        assertTrue(AiModelAbility.TOOL in provider.effectiveModelCapabilities(cached).abilities)

        val disabledByCatalog = cached.copy(modelInfo = AiModelInfo(endpoint, toolCalling = AiModelValue(false)))
        assertFalse(AiModelAbility.TOOL in provider.effectiveModelCapabilities(disabledByCatalog).abilities)

        val disabledManually = cached.copy(
            modelSettings = AiModelSettings(endpoint, capabilityOverrides = setOf(AiModelCapabilityField.TOOL_CALLING)),
        )
        assertFalse(AiModelAbility.TOOL in provider.effectiveModelCapabilities(disabledManually).abilities)
    }

    @Test
    fun specificationsApplyOnlyToTheOfficialPresetAndYieldToCatalogData() {
        val model = AiModel(provider.model)
        val presetInfo = provider.effectiveModelInfo(model)
        assertEquals(512_000, presetInfo.contextWindow?.value)
        assertEquals(65_536, presetInfo.maxOutputTokens?.value)
        assertEquals(listOf("text", "image"), presetInfo.inputModalities?.value)
        val listed = model.copy(
            modelInfo = AiModelInfo(provider.modelConfigurationKey(), contextWindow = AiModelValue(128_000)),
        )
        assertEquals(128_000, provider.effectiveModelInfo(listed).contextWindow?.value)

        val alternate = provider.copy(baseUrl = "https://example.org/v1")
        assertNull(alternate.effectiveModelInfo(model).contextWindow)
        assertNull(alternate.effectiveModelInfo(model).toolCalling)
        assertNull(AiProviderHelpDocuments.forProvider(alternate.id, true, alternate.baseUrl))
    }
}
