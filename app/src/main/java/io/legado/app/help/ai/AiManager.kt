package io.legado.app.help.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class AiModelBatchRefreshResult(
    val refreshedProviderCount: Int,
    val refreshedModelCount: Int,
    val missingKeyProviders: List<String>,
    val failedProviders: List<String>
)

object AiManager {

    private val openAIProvider by lazy { OpenAiCompatibleProvider() }
    private val googleProvider by lazy { GoogleAiProvider() }
    private val claudeProvider by lazy { ClaudeAiProvider() }

    suspend fun generateText(
        messages: List<AiMessage>,
        params: AiTextParams = AiTextParams(temperature = AiConfig.temperature),
        providerId: String = AiProviderStore.activeProviderId(),
        modelId: String? = null
    ): AiTextResult {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        val requestModel = modelId ?: setting.model
        check(setting.enabled) { "AI provider is disabled" }
        check(requestModel.isNotBlank()) { "AI model is empty" }
        return providerFor(setting).generateText(setting.copy(model = requestModel), messages, params)
    }

    suspend fun listModels(providerId: String): List<AiModel> {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        return listModels(setting)
    }

    private suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        return providerFor(setting).listModels(setting)
            .filter { it.id.isNotBlank() }
            .map { AiModelRegistry.enrich(it) }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase() }
    }

    suspend fun fetchAndSaveModels(providerId: String): List<AiModel> {
        val models = listModels(providerId)
        if (models.isEmpty()) return models
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        AiProviderStore.saveProvider(setting.withFetchedModels(models))
        return models
    }

    suspend fun refreshEnabledProviderModels(): AiModelBatchRefreshResult {
        val enabled = AiProviderStore.enabledProviders()
        val missingKey = enabled.filter { it.apiKey.isBlank() }.map { it.name }
        val ready = enabled.filter { it.apiKey.isNotBlank() }
        val semaphore = Semaphore(4)
        val attempts = coroutineScope {
            ready.map { provider ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val models = listModels(provider)
                            check(models.isNotEmpty()) { "No available models returned" }
                            ProviderModelFetch(provider, models)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            ProviderModelFetch(provider, error = e)
                        }
                    }
                }
            }.awaitAll()
        }
        val fetched = attempts.filter { it.models != null }.associateBy { it.provider.id }
        val failed = attempts.filter { it.error != null }.map { it.provider.name }.toMutableList()
        var refreshedCount = 0
        var modelCount = 0
        if (fetched.isNotEmpty()) {
            val current = AiProviderStore.providers()
            val updated = current.map { provider ->
                val result = fetched[provider.id] ?: return@map provider
                if (!provider.enabled || provider.apiKey != result.provider.apiKey ||
                    provider.baseUrl != result.provider.baseUrl ||
                    provider.modelsUrl != result.provider.modelsUrl ||
                    provider.type != result.provider.type
                ) {
                    failed += provider.name
                    provider
                } else {
                    val models = requireNotNull(result.models)
                    refreshedCount++
                    modelCount += models.size
                    provider.withFetchedModels(models)
                }
            }
            if (refreshedCount > 0) AiProviderStore.saveProviders(updated)
        }
        return AiModelBatchRefreshResult(refreshedCount, modelCount, missingKey, failed)
    }

    suspend fun testConnection(providerId: String): AiTextResult {
        val result = generateText(
            providerId = providerId,
            messages = listOf(
                AiMessage(AiMessage.Role.SYSTEM, "You are a connection test endpoint. Reply with OK only."),
                AiMessage(AiMessage.Role.USER, "Reply OK.")
            ),
            params = AiTextParams(temperature = 0f, disableThinking = true)
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

    suspend fun testConnectivity(providerId: String): Int {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        check(setting.apiKey.isNotBlank()) { "API key is empty" }
        return providerFor(setting).listModels(setting)
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .size
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

    private data class ProviderModelFetch(
        val provider: AiProviderSetting,
        val models: List<AiModel>? = null,
        val error: Throwable? = null
    )
}

internal fun AiProviderSetting.withFetchedModels(models: List<AiModel>): AiProviderSetting {
    val modelIds = models.map { it.id }.toSet()
    val availableModelIds = if (availableModelSelectionInitialized) {
        availableModelIds.filter { it in modelIds }
    } else {
        models.map { it.id }
    }
    return copy(
        models = models,
        availableModelIds = availableModelIds,
        availableModelSelectionInitialized = true
    )
}
