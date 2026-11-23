package io.legado.app.help.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

internal fun AiProviderSetting.catalogFetchRequired(): Boolean =
    catalogRefreshRequired || (!catalogLoaded && models.none { it.manualModelEndpoint == null })

internal fun AiProviderSetting.apiKeyNeedsReentry(): Boolean =
    apiKey.isNotBlank() && apiKeyOrigin != null && apiKeyOrigin != baseOrigin()

internal fun AiProviderSetting.hasRequestCredentials(): Boolean = if (isChatGptSubscription()) {
    AiChatGptSessions.account(chatGptAccountId)?.let { !it.needsReauthorization() && it.tokens?.allowsPlan == true } == true
} else {
    apiKey.isNotBlank() && !apiKeyNeedsReentry()
}

internal fun AiProviderSetting.apiKeyForRequest(): String {
    if (apiKeyNeedsReentry()) throw AiEndpointException("Re-enter the API Key for the current Base URL")
    return apiKey
}

internal fun AiProviderSetting.withRequestConfiguration(
    previous: AiProviderSetting?,
    useExistingKeyHere: Boolean = false,
): AiProviderSetting {
    val keyOrigin = when {
        apiKey.isBlank() -> null
        previous == null || apiKey != previous.apiKey || useExistingKeyHere -> baseOrigin().orEmpty()
        else -> previous.apiKeyOrigin ?: previous.baseOrigin().orEmpty()
    }
    val changed = previous != null && !hasSameCatalogConfiguration(previous)
    return copy(
        apiKeyOrigin = keyOrigin,
        catalogRevision = if (changed || previous == null) UUID.randomUUID().toString() else previous.catalogRevision,
        catalogRefreshRequired = changed || catalogRefreshRequired,
    ).reconcileManualModels()
}

internal fun AiProviderSetting.matchesCatalogRequest(requested: AiProviderSetting): Boolean =
    catalogRevision == requested.catalogRevision && hasSameCatalogConfiguration(requested)

private fun AiProviderSetting.hasSameCatalogConfiguration(other: AiProviderSetting): Boolean =
    id == other.id && type == other.type && apiKey == other.apiKey &&
        chatGptAccountId == other.chatGptAccountId &&
        baseUrl == other.baseUrl && chatCompletionsPath == other.chatCompletionsPath &&
        useCustomModelsUrl == other.useCustomModelsUrl &&
        (!useCustomModelsUrl || modelsUrl == other.modelsUrl)

private fun AiProviderSetting.baseOrigin(): String? =
    baseUrl.trim().toHttpUrlOrNull()
        ?.takeUnless { it.hasUserInfo() || it.query != null || it.fragment != null }
        ?.newBuilder()?.encodedPath("/")?.build()?.toString()

internal fun HttpUrl.catalogSourceId(): String =
    UUID.nameUUIDFromBytes(newBuilder().fragment(null).build().toString().toByteArray(Charsets.UTF_8)).toString()
