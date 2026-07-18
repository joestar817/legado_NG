package io.legado.app.ui.config

import io.legado.app.R
import io.legado.app.help.ai.AiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class AiModelIconResolverTest {

    @Test
    fun modelIdentityTakesPriorityOverConflictingOwner() {
        val cases = listOf(
            AiModel(id = "deepseek-v4-flash", ownedBy = "openai") to
                R.drawable.ic_provider_deepseek,
            AiModel(id = "grok-4.3-fast", ownedBy = "openai") to
                R.drawable.ic_model_grok,
            AiModel(id = "cf-qwen3-30b-free", ownedBy = "openai") to
                R.drawable.ic_model_qwen,
            AiModel(id = "cf-glm-4.7-flash-free", ownedBy = "openai") to
                R.drawable.ic_provider_zhipu,
            AiModel(id = "cf-llama-3.2-1b-free", ownedBy = "openai") to
                R.drawable.ic_model_meta,
            AiModel(id = "mimo-v2.5-new", ownedBy = "deepseek") to
                R.drawable.ic_provider_mimo
        )

        cases.forEach { (model, expectedIconRes) ->
            assertEquals(expectedIconRes, model.iconRes(R.drawable.ic_cfg_web))
        }
    }

    @Test
    fun ownerIsUsedWhenModelIdentityIsUnknown() {
        val model = AiModel(id = "custom-chat-latest", ownedBy = "deepseek")

        assertEquals(
            R.drawable.ic_provider_deepseek,
            model.iconRes(R.drawable.ic_cfg_web)
        )
    }

    @Test
    fun providerIconIsFinalFallback() {
        val model = AiModel(id = "custom-chat-latest", ownedBy = "custom")

        assertEquals(R.drawable.ic_cfg_web, model.iconRes(R.drawable.ic_cfg_web))
    }
}
