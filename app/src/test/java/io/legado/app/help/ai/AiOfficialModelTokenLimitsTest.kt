package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiOfficialModelTokenLimitsTest {
    @Test
    fun chatGptSubscriptionUsesItsOwnCatalogContext() {
        val defaults = AiDefaultProviders.all()
        val subscription = defaults.first { it.isChatGptSubscription() }
        val openAi = defaults.first { it.id == "openai" }
        val model = AiModel("gpt-5.6-sol")
        val listed = model.copy(modelInfo = AiModelInfo(
            endpoint = subscription.modelConfigurationKey(),
            contextWindow = AiModelValue(272_000),
        ))

        assertEquals(272_000, subscription.assistantContextWindowLimit(listed))
        assertNull(subscription.assistantContextWindowLimit(model))
        assertEquals(1_050_000, openAi.assistantContextWindowLimit(model))
    }

    @Test
    fun catalogLimitsTakePrecedenceOverTheExactOfficialReference() {
        val provider = AiDefaultProviders.all().first { it.id == "volcengine" }
        val model = AiModel(
            id = "doubao-seed-2-1-pro-260915",
            modelInfo = AiModelInfo(
                endpoint = provider.modelConfigurationKey(),
                contextWindow = AiModelValue(983_040),
            ),
        )

        val info = provider.effectiveModelInfo(model)
        assertEquals(983_040, info.contextWindow?.value)
        assertEquals(262_144, info.maxOutputTokens?.value)
    }

    @Test
    fun officialReferencesDoNotApplyToAnotherRouteOrAGuessedModelId() {
        val provider = AiDefaultProviders.all().first { it.id == "openai" }
        val model = AiModel("gpt-4.1")
        val officialInfo = provider.effectiveModelInfo(model)
        assertEquals(1_047_576, officialInfo.contextWindow?.value)

        val proxy = provider.copy(baseUrl = "https://another.example/v1")
        assertNull(proxy.effectiveModelInfo(model).contextWindow)
        assertNull(provider.effectiveModelInfo(AiModel("gpt-4.1-custom")).contextWindow)
        assertNull(provider.effectiveModelInfo(AiModel("gpt-4.1-custom")).maxOutputTokens)
    }
}
