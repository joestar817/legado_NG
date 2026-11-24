package io.legado.app.help.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

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
    private val initialModelFetchLocks = ConcurrentHashMap<String, Mutex>()
    private val catalogRequests = mutableMapOf<String, Any>()

    suspend fun generateText(
        messages: List<AiMessage>,
        params: AiTextParams = AiTextParams(temperature = AiConfig.temperature),
        providerId: String = AiProviderStore.activeProviderId(),
        modelId: String? = null
    ): AiTextResult {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        check(!setting.isOpenAiAccountProvider()) { "ChatGPT plan models are used in the AI assistant" }
        val requestModel = modelId ?: setting.model
        check(setting.enabled) { "AI provider is disabled" }
        check(requestModel.isNotBlank()) { "AI model is empty" }
        if (modelId != null) {
            check(setting.availableCatalogModel(requestModel)?.isTextChatModel() == true) {
                "Selected AI model is unavailable. Select a text model again."
            }
        }
        return providerFor(setting).generateText(setting.copy(model = requestModel), messages, params)
    }

    suspend fun listModels(providerId: String): List<AiModel> {
        val setting = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
        return listModels(setting)
    }

    private suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        if (setting.isOpenAiAccountProvider()) return setting.fetchAccountModels()
        return providerFor(setting).listModels(setting)
            .filter { it.id.isNotBlank() }
            .map { AiModelRegistry.enrich(it).withCatalogCapabilities(it.modelInfo) }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase() }
    }

    suspend fun fetchAndSaveModels(providerId: String): List<AiModel> {
        val (requested, token) = synchronized(AiProviderStore) {
            val provider = AiProviderStore.provider(providerId) ?: error("AI provider not found: $providerId")
            provider to beginCatalogRequest(providerId)
        }
        try {
            val models = listModels(requested)
            currentCoroutineContext().ensureActive()
            synchronized(AiProviderStore) {
                val setting = AiProviderStore.provider(providerId)
                    ?: error("AI provider not found: $providerId")
                check(catalogRequests[providerId] === token && setting.matchesCatalogRequest(requested)) {
                    "AI model list changed. Refresh again."
                }
                AiProviderStore.saveProvider(setting.withFetchedModels(models))
            }
            return models
        } finally {
            finishCatalogRequest(providerId, token)
        }
    }

    suspend fun ensureProviderModelCatalog(providerId: String): List<AiModel> {
        val lock = initialModelFetchLocks.getOrPut(providerId) { Mutex() }
        return lock.withLock {
            val (requested, token) = synchronized(AiProviderStore) {
                val provider = AiProviderStore.provider(providerId)
                    ?: error("AI provider not found: $providerId")
                check(provider.enabled && provider.hasRequestCredentials()) {
                    "AI provider is not ready"
                }
                if (!provider.catalogFetchRequired()) return@withLock provider.models
                provider to beginCatalogRequest(providerId)
            }
            try {
                val models = listModels(requested)
                currentCoroutineContext().ensureActive()
                synchronized(AiProviderStore) {
                    check(catalogRequests[providerId] === token) { "AI model list changed. Refresh again." }
                    val updated = AiProviderStore.saveInitialFetchedModels(requested, models)
                        ?: error("AI provider changed while fetching models")
                    updated.models
                }
            } finally {
                finishCatalogRequest(providerId, token)
            }
        }
    }

    suspend fun refreshEnabledProviderModels(): AiModelBatchRefreshResult {
        val (ready, missingKey, tokens) = synchronized(AiProviderStore) {
            val enabled = AiProviderStore.enabledProviders()
            val missing = enabled.filterNot { it.hasRequestCredentials() }
            val configured = enabled - missing.toSet()
            Triple(configured, missing.map { it.name }, configured.associate { it.id to beginCatalogRequest(it.id) })
        }
        return try {
            refreshProviderCatalogs(ready, missingKey, tokens)
        } finally {
            tokens.forEach { (id, token) -> finishCatalogRequest(id, token) }
        }
    }

    private suspend fun refreshProviderCatalogs(
        ready: List<AiProviderSetting>,
        missingKey: List<String>,
        tokens: Map<String, Any>,
    ): AiModelBatchRefreshResult {
        val semaphore = Semaphore(4)
        val attempts = coroutineScope {
            ready.map { provider ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            synchronized(AiProviderStore) {
                                val current = AiProviderStore.provider(provider.id)
                                check(
                                    current != null && current.enabled && !current.apiKeyNeedsReentry() &&
                                        catalogRequests[provider.id] === tokens[provider.id] &&
                                        current.matchesCatalogRequest(provider)
                                ) { "AI model list changed. Refresh again." }
                            }
                            currentCoroutineContext().ensureActive()
                            ProviderModelFetch(provider, listModels(provider))
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
        if (fetched.isNotEmpty()) synchronized(AiProviderStore) {
            val current = AiProviderStore.providers()
            val updated = current.map { provider ->
                val result = fetched[provider.id] ?: return@map provider
                if (!provider.enabled || catalogRequests[provider.id] !== tokens[provider.id] ||
                    !provider.matchesCatalogRequest(result.provider)
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

    // The caller captures the provider snapshot and registers the request under the store lock.
    private fun beginCatalogRequest(id: String): Any = Any().also { catalogRequests[id] = it }

    private fun finishCatalogRequest(id: String, token: Any) {
        synchronized(AiProviderStore) {
            if (catalogRequests[id] === token) catalogRequests.remove(id)
        }
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
        if (setting.isOpenAiAccountProvider()) return setting.fetchAccountModels().size
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

internal fun initialFetchedModelsUpdate(
    requested: AiProviderSetting,
    current: AiProviderSetting,
    models: List<AiModel>,
): AiProviderSetting? {
    if (!current.enabled || !current.matchesCatalogRequest(requested)) return null
    if (!current.catalogFetchRequired()) return current
    return current.withFetchedModels(models)
}

internal fun AiProviderSetting.withFetchedModels(models: List<AiModel>): AiProviderSetting {
    val modelIds = models.map { it.id }.toSet()
    val endpoint = modelConfigurationKey()
    val manual = this.models.filter { it.manualModelEndpoint == endpoint && it.id !in modelIds }
    val mergedModels = mergeModelInfo(models) + manual
    val mergedIds = mergedModels.map { it.id }.toSet()
    val unlistedEdits = (this.models + unlistedModelEdits.orEmpty())
        .filter { previous ->
            val settings = previous.modelSettings ?: return@filter false
            settings.catalogEndpoint != endpoint || previous.id !in mergedIds
        }
        .distinctBy { it.id to it.modelSettings?.catalogEndpoint }
        .map { it.copy(modelInfo = null) }
    return copy(
        model = model.takeIf { it in mergedIds } ?: models.firstOrNull { it.isTextChatModel() }?.id ?: model,
        models = mergedModels,
        availableModelIds = if (availableModelSelectionInitialized) availableModelIds else mergedModels.map { it.id },
        availableModelSelectionInitialized = availableModelSelectionInitialized || models.isNotEmpty(),
        catalogRefreshRequired = false,
        catalogLoaded = true,
        unlistedModelEdits = unlistedEdits.takeIf { it.isNotEmpty() },
    )
}
