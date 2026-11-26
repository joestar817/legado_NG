package io.legado.app.help.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

private const val DEEPSEEK_API_HOST = "api.deepseek.com"

data class AiModelValue<T>(
    @SerializedName("value") val value: T,
    @SerializedName("fromPreviousCatalog") val fromPreviousCatalog: Boolean = false,
)

data class AiModelInfo(
    @SerializedName("endpoint") val endpoint: String,
    @SerializedName("name") val name: AiModelValue<String>? = null,
    @SerializedName("publisher") val publisher: AiModelValue<String>? = null,
    @SerializedName("contextWindow") val contextWindow: AiModelValue<Int>? = null,
    @SerializedName("maxOutputTokens") val maxOutputTokens: AiModelValue<Int>? = null,
    @SerializedName("inputModalities") val inputModalities: AiModelValue<List<String>>? = null,
    @SerializedName("outputModalities") val outputModalities: AiModelValue<List<String>>? = null,
    @SerializedName("effortLevels") val effortLevels: AiModelValue<List<String>>? = null,
    @SerializedName("defaultEffort") val defaultEffort: AiModelValue<String>? = null,
    @SerializedName("anthropicSystemPromptUpdate")
    val anthropicSystemPromptUpdate: AiModelValue<String>? = null,
    @SerializedName("toolCalling") val toolCalling: AiModelValue<Boolean>? = null,
    @SerializedName("reasoning") val reasoning: AiModelValue<Boolean>? = null,
    @SerializedName("canDisableThinking") val canDisableThinking: AiModelValue<Boolean>? = null,
    @SerializedName("catalogSourceId") val catalogSourceId: String? = null,
    @SerializedName("supportsReasoningBudget") val supportsReasoningBudget: AiModelValue<Boolean>? = null,
    @SerializedName("defaultReasoningEnabled") val defaultReasoningEnabled: AiModelValue<Boolean>? = null,
    @SerializedName("supportsAdaptiveThinking") val supportsAdaptiveThinking: AiModelValue<Boolean>? = null,
    @SerializedName("creditMultiplier") val creditMultiplier: String? = null,
    @SerializedName("catalogType") val catalogType: AiModelValue<AiModelType>? = null,
    @SerializedName("supportsTemperature") val supportsTemperature: AiModelValue<Boolean>? = null,
    @SerializedName("supportsStructuredOutput") val supportsStructuredOutput: AiModelValue<Boolean>? = null,
) {
    internal val creditMultiplierLabel: String?
        get() = creditMultiplier?.let { "$it×" }

    internal fun withPrevious(previous: AiModelInfo?): AiModelInfo {
        val sameEndpoint = previous?.takeIf { it.endpoint == endpoint && it.catalogSourceId == catalogSourceId }
        val supported = reasoning.orPrevious(sameEndpoint?.reasoning)
        val previousReasoning = sameEndpoint?.takeUnless { it.reasoning?.value == false }
        val levels = effortLevels.orPrevious(previousReasoning?.effortLevels).takeUnless { supported?.value == false }
        val default = defaultEffort.orPrevious(previousReasoning?.defaultEffort).takeUnless { supported?.value == false }
        return copy(
            name = name.orPrevious(sameEndpoint?.name),
            publisher = publisher.orPrevious(sameEndpoint?.publisher),
            contextWindow = contextWindow.orPrevious(sameEndpoint?.contextWindow),
            maxOutputTokens = maxOutputTokens.orPrevious(sameEndpoint?.maxOutputTokens),
            inputModalities = inputModalities.orPrevious(sameEndpoint?.inputModalities),
            outputModalities = outputModalities.orPrevious(sameEndpoint?.outputModalities),
            catalogType = catalogType.orPrevious(sameEndpoint?.catalogType),
            supportsTemperature = supportsTemperature.orPrevious(sameEndpoint?.supportsTemperature),
            supportsStructuredOutput = supportsStructuredOutput.orPrevious(sameEndpoint?.supportsStructuredOutput),
            effortLevels = levels,
            defaultEffort = default?.takeIf { levels?.value?.contains(it.value) != false },
            anthropicSystemPromptUpdate =
                anthropicSystemPromptUpdate.orPrevious(sameEndpoint?.anthropicSystemPromptUpdate),
            toolCalling = toolCalling.orPrevious(sameEndpoint?.toolCalling),
            reasoning = supported,
            canDisableThinking = canDisableThinking.orPrevious(previousReasoning?.canDisableThinking)
                .takeUnless { supported?.value == false },
            supportsReasoningBudget = supportsReasoningBudget.orPrevious(previousReasoning?.supportsReasoningBudget)
                .takeUnless { supported?.value == false },
            defaultReasoningEnabled = defaultReasoningEnabled.orPrevious(previousReasoning?.defaultReasoningEnabled)
                .takeUnless { supported?.value == false },
            supportsAdaptiveThinking = supportsAdaptiveThinking.orPrevious(previousReasoning?.supportsAdaptiveThinking)
                .takeUnless { supported?.value == false },
        )
    }
}

/** Scopes user edits and catalog fields to a provider configuration without storing URL credentials. */
internal fun AiProviderSetting.modelConfigurationKey(): String {
    if (isWorkBuddyAccountProvider()) return "workbuddy-model:${workBuddyAccountId.orEmpty()}"
    if (isOpenAiAccountProvider()) {
        val account = chatGptAccountId.orEmpty().toByteArray(Charsets.UTF_8)
        val source = if (isCodexSubscription()) "codex-model" else "chatgpt-model"
        return "$source:${UUID.nameUUIDFromBytes(account)}"
    }
    // The official DeepSeek sample already persisted this key.
    officialDeepSeekModelConfigEndpoint()?.let { return it }
    val identity = listOf(
        id, type.prefValue, baseUrl.trim().trimEnd('/'), chatCompletionsPath.trim(),
        modelsUrl.trim().takeIf { useCustomModelsUrl }.orEmpty(),
    ).joinToString("\u0000")
    return "provider-model:${UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))}"
}

internal fun AiProviderSetting.officialModelProviderId(): String? {
    if (!builtIn) return null
    val preset = officialModelProviders[id] ?: return null
    val endpoint = baseUrl.toHttpUrlOrNull() ?: return null
    val expected = preset.baseUrl.toHttpUrlOrNull() ?: return null
    if (type != preset.type || !endpoint.isHttps || endpoint.host != expected.host ||
        endpoint.port != expected.port || endpoint.username.isNotEmpty() || endpoint.password.isNotEmpty()
    ) return null
    val basePath = endpoint.encodedPath.trimEnd('/')
    val expectedPath = expected.encodedPath.trimEnd('/')
    if (basePath != expectedPath && !(id == "deepseek" && (basePath == "/v1" || basePath == "/beta"))) return null
    return id
}

private val officialModelProviders by lazy { AiDefaultProviders.all().associateBy { it.id } }

internal fun AiProviderSetting.officialDeepSeekCatalogEndpoint(): String? {
    if (id != "deepseek" || !builtIn || type != AiProviderType.OPENAI) return null
    if (baseUrl.toHttpUrlOrNull()?.isDeepSeekApi() != true) return null
    return runCatching { AiModelEndpointResolver.candidates(this).firstOrNull() }.getOrNull()
        ?.toHttpUrlOrNull()
        ?.deepSeekCatalogEndpoint()
}

internal fun AiProviderSetting.officialDeepSeekModelConfigEndpoint(): String? {
    val endpoint = officialDeepSeekCatalogEndpoint() ?: return null
    val chatUrl = "${baseUrl.trimEndSlash()}${chatCompletionsPath.ensureStartSlash()}"
        .toHttpUrlOrNull() ?: return null
    if (!chatUrl.isDeepSeekApi()) return null
    return when (chatUrl.encodedPath.trimEnd('/')) {
        "/chat/completions", "/v1/chat/completions", "/beta/chat/completions" -> endpoint
        else -> null
    }
}

internal fun HttpUrl.deepSeekCatalogEndpoint(): String? {
    val path = encodedPath.trimEnd('/')
    if (!isDeepSeekApi() || (path != "/models" && path != "/v1/models")) return null
    return "https://$DEEPSEEK_API_HOST$path"
}

internal fun HttpUrl.isDeepSeekApi(): Boolean =
    isHttps && host == DEEPSEEK_API_HOST && port == 443 && username.isEmpty() && password.isEmpty()

internal fun readCommonModelInfo(model: JsonObject, endpoint: String): AiModelInfo {
    val effort = model.objectOrNull("effort")
    val anthropic = model.objectOrNull("api_capabilities")?.objectOrNull("anthropic_messages")
    return AiModelInfo(
        endpoint = endpoint,
        name = model.get("name").catalogString().catalogValue(),
        publisher = model.get("owned_by").catalogString().catalogValue(),
        contextWindow = model.positiveInt("context_window").catalogValue(),
        maxOutputTokens = model.positiveInt("max_output_tokens").catalogValue(),
        inputModalities = model.catalogStrings("input_modalities").catalogValue(),
        outputModalities = model.catalogStrings("output_modalities").catalogValue(),
        effortLevels = effort?.catalogStrings("supported_levels").catalogValue(),
        defaultEffort = effort?.get("default_level").catalogString().catalogValue(),
        anthropicSystemPromptUpdate = anthropic?.get("system_prompt_update").catalogString()
            .catalogValue(),
    )
}

internal fun AiProviderSetting.mergeModelInfo(fetched: List<AiModel>): List<AiModel> {
    val endpoint = modelConfigurationKey()
    val previousModels = models.associateBy { it.id }
    val unlistedModels = unlistedModelEdits.orEmpty().filter { it.modelSettingsFor(endpoint) != null }
        .associateBy { it.id }
    return fetched.map { model ->
        val previous = previousModels[model.id]
        val info = model.modelInfo?.takeIf { it.endpoint == endpoint }
        val previousInfo = previous?.modelInfo?.takeUnless { catalogRefreshRequired }
        val mergedInfo = info?.withPrevious(previousInfo)
        val updated = model.copy(modelInfo = mergedInfo).withCatalogCapabilities(mergedInfo)
        val edited = previous?.takeIf { it.modelSettingsFor(endpoint) != null } ?: unlistedModels[model.id]
            ?: return@map updated
        val settings = requireNotNull(edited.modelSettingsFor(endpoint))
        val manualEndpoint = edited.manualModelEndpoint?.takeIf { it == endpoint }
        val catalogModel = if (manualEndpoint == null) updated else updated.copy(
            inputModalities = updated.safeInputModalities().ifEmpty {
                edited.safeInputModalities().takeIf { mergedInfo?.inputModalities?.knownModalities() == null }.orEmpty()
            },
            outputModalities = updated.safeOutputModalities().ifEmpty {
                edited.safeOutputModalities().takeIf { mergedInfo?.outputModalities?.knownModalities() == null }.orEmpty()
            },
        )
        catalogModel.withCapabilityOverrides(edited, settings).copy(
            displayName = edited.displayName,
            modelSettings = settings,
            manualModelEndpoint = manualEndpoint,
        )
    }
}

private fun JsonElement?.catalogString(): String? =
    this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf(String::isNotEmpty)

internal fun JsonObject.catalogStrings(key: String): List<String>? {
    val values = arrayOrNull(key) ?: return null
    return values.map { item -> item.catalogString() ?: return null }
}

internal fun JsonObject.positiveInt(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asString?.toIntOrNull()?.takeIf { it > 0 }

internal fun <T> T?.catalogValue(): AiModelValue<T>? = this?.let { AiModelValue(it) }

private fun <T> AiModelValue<T>?.orPrevious(
    previous: AiModelValue<T>?,
): AiModelValue<T>? = this ?: previous?.copy(fromPreviousCatalog = true)
