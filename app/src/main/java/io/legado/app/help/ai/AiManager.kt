package io.legado.app.help.ai

object AiManager {

    private val openAIProvider by lazy { OpenAiCompatibleProvider() }
    private val googleProvider by lazy { GoogleAiProvider() }
    private val claudeProvider by lazy { ClaudeAiProvider() }

    suspend fun generateText(
        messages: List<AiMessage>,
        params: AiTextParams = AiTextParams(temperature = AiConfig.temperature),
        providerId: String = AiProviderStore.activeProviderId()
    ): AiTextResult {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        check(setting.enabled) { "AI provider is disabled" }
        check(setting.model.isNotBlank()) { "AI model is empty" }
        return providerFor(setting).generateText(setting, messages, params)
    }

    suspend fun listModels(providerId: String): List<AiModel> {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        return providerFor(setting).listModels(setting)
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase() }
    }

    suspend fun fetchAndSaveModels(providerId: String): List<AiModel> {
        val models = listModels(providerId)
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        AiProviderStore.saveProvider(setting.copy(models = models))
        return models
    }

    suspend fun testConnection(providerId: String): AiTextResult {
        val result = generateText(
            providerId = providerId,
            messages = listOf(
                AiMessage(AiMessage.Role.SYSTEM, "You are a connection test endpoint. Reply with OK only."),
                AiMessage(AiMessage.Role.USER, "Reply OK.")
            ),
            params = AiTextParams(temperature = 0f, maxTokens = 64, disableThinking = true)
        )
        check(result.content.isNotBlank()) {
            val reasoning = result.reasoning.orEmpty().take(160)
            if (reasoning.isNotBlank()) {
                "AI provider returned reasoning only: $reasoning"
            } else {
                "AI provider returned empty content. finish_reason=${result.finishReason.orEmpty()}"
            }
        }
        return result
    }

    suspend fun queryBalance(providerId: String): AiBalanceResult {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        return AiBalanceProvider.query(setting)
    }

    private fun providerFor(setting: AiProviderSetting): AiProvider {
        return when (setting.type) {
            AiProviderType.OPENAI -> openAIProvider
            AiProviderType.GOOGLE -> googleProvider
            AiProviderType.CLAUDE -> claudeProvider
        }
    }
}
