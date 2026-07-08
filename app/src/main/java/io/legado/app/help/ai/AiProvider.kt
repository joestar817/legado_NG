package io.legado.app.help.ai

interface AiProvider {

    suspend fun listModels(setting: AiProviderSetting): List<AiModel>

    suspend fun generateText(
        setting: AiProviderSetting,
        messages: List<AiMessage>,
        params: AiTextParams = AiTextParams()
    ): AiTextResult

}

data class AiTextParams(
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val enableThinking: Boolean = false,
    val disableThinking: Boolean = false,
    val reasoningEffort: String? = null,
    val jsonResponse: Boolean = false
)

data class AiTextResult(
    val content: String,
    val reasoning: String? = null,
    val model: String? = null,
    val finishReason: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val rawPreview: String? = null
)
