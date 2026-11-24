package io.legado.app.ui.config

import com.google.gson.Gson
import io.legado.app.R
import io.legado.app.help.ai.AiChatMessageSnapshot
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatMessageModelTest {
    @Test
    fun messagesWithSameModelIdUseTheirOwnProvider() {
        val providers = listOf(
            provider("openai", AiModel("shared", displayName = "GPT alias")),
            provider("deepseek", AiModel("shared", displayName = "DeepSeek alias")).copy(enabled = false),
        ).associateBy { it.id }

        assertEquals(
            AiChatMessageModel("DeepSeek alias", R.drawable.ic_provider_deepseek),
            resolveChatMessageModel("deepseek", "shared", providers),
        )
        assertEquals(
            AiChatMessageModel("GPT alias", R.drawable.ic_provider_openai),
            resolveChatMessageModel("openai", "shared", providers),
        )
    }

    @Test
    fun savedModelSurvivesMissingCatalogWithoutUsageCounts() {
        val savedReply = Gson().fromJson(
            """{
                "id":"old", "role":"assistant", "content":"Hello",
                "usage_provider_id":"deepseek", "usage_model_id":"deepseek-flash"
            }""".trimIndent(),
            AiChatMessageSnapshot::class.java,
        )

        assertEquals(
            AiChatMessageModel("deepseek-flash", R.drawable.ic_provider_deepseek),
            resolveChatMessageModel(savedReply.usageProviderId, savedReply.usageModelId, emptyMap()),
        )
    }

    @Test
    fun oldReplyWithoutModelUsesGenericAssistant() {
        val providers = mapOf("openai" to provider("openai", AiModel("gpt-6-luna")))

        assertEquals(
            AiChatMessageModel(null, R.drawable.ic_ai_setting),
            resolveChatMessageModel(null, null, providers),
        )
    }

    private fun provider(id: String, model: AiModel) = AiProviderSetting(
        id = id,
        name = id,
        type = AiProviderType.OPENAI,
        baseUrl = "https://example.com/v1",
        models = listOf(model),
    )
}
