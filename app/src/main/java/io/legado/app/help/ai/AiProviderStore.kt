package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

object AiProviderStore {

    fun providers(): List<AiProviderSetting> {
        val saved = readSavedProviders()
        if (saved.isEmpty()) {
            val migrated = migrateLegacyConfig(AiDefaultProviders.all())
            saveProviders(migrated)
            return migrated
        }
        val merged = mergeWithDefaults(saved)
        if (merged != saved) {
            saveProviders(merged)
        }
        return merged
    }

    fun enabledProviders(): List<AiProviderSetting> {
        return providers().filter { it.enabled }
    }

    fun provider(id: String): AiProviderSetting? {
        return providers().firstOrNull { it.id == id }
    }

    fun saveProvider(provider: AiProviderSetting) {
        val providers = providers()
        if (providers.any { it.id == provider.id }) {
            saveProviders(providers.map { if (it.id == provider.id) provider else it })
        } else {
            saveProviders(providers + provider)
        }
    }

    fun createCustomProvider(type: AiProviderType): AiProviderSetting {
        val provider = defaultCustomProvider(type).copy(id = nextCustomProviderId(type))
        saveProvider(provider)
        return provider
    }

    fun deleteCustomProvider(id: String): Boolean {
        val providers = providers()
        val provider = providers.firstOrNull { it.id == id } ?: return false
        if (provider.builtIn) {
            return false
        }
        val wasActive = appCtx.getPrefString(PreferKey.aiActiveProviderId) == id
        val remaining = providers.filterNot { it.id == id }
        saveProviders(remaining)
        if (wasActive) {
            val nextActive = remaining.firstOrNull { it.enabled } ?: remaining.firstOrNull()
            if (nextActive != null) {
                setActiveProvider(nextActive.id)
            }
        }
        return true
    }

    fun saveProviders(providers: List<AiProviderSetting>) {
        appCtx.putPrefString(
            PreferKey.aiProvidersJson,
            GSON.toJson(providers.map { normalize(it) })
        )
    }

    fun activeProviderId(): String {
        val current = appCtx.getPrefString(PreferKey.aiActiveProviderId)
        val providers = providers()
        return providers.firstOrNull { it.id == current }?.id
            ?: providers.firstOrNull { it.enabled }?.id
            ?: providers.first().id
    }

    fun setActiveProvider(id: String) {
        appCtx.putPrefString(PreferKey.aiActiveProviderId, id)
    }

    fun activeProvider(): AiProviderSetting {
        return provider(activeProviderId()) ?: providers().first()
    }

    private fun readSavedProviders(): List<AiProviderSetting> {
        val json = appCtx.getPrefString(PreferKey.aiProvidersJson).orEmpty()
        if (json.isBlank()) {
            return emptyList()
        }
        return runCatching {
            GSON.fromJson(json, JsonArray::class.java)
                ?.mapNotNull { parseProvider(it) }
                ?: emptyList()
        }.getOrDefault(emptyList())
            .mapNotNull { sanitize(it) }
            .filter { it.id.isNotBlank() && it.baseUrl.isNotBlank() }
    }

    private fun parseProvider(element: JsonElement): AiProviderSetting? {
        return runCatching {
            GSON.fromJson(element, AiProviderSetting::class.java)
        }.getOrNull()
    }

    private fun mergeWithDefaults(saved: List<AiProviderSetting>): List<AiProviderSetting> {
        val defaults = AiDefaultProviders.all()
        val defaultsById = defaults.associateBy { it.id }
        val savedIds = saved.map { it.id }.toSet()
        val mergedSaved = saved.map { savedProvider ->
            val default = defaultsById[savedProvider.id]
            if (default == null) {
                savedProvider
            } else {
                mergeProvider(default, savedProvider)
            }
        }
        val missingDefaults = defaults.filter { it.id !in savedIds }
        return mergedSaved + missingDefaults
    }

    private fun mergeProvider(
        default: AiProviderSetting,
        savedProvider: AiProviderSetting
    ): AiProviderSetting {
        val baseUrl = savedProvider.baseUrl.ifBlank { default.baseUrl }
        return default.copy(
            enabled = savedProvider.enabled,
            name = savedProvider.name.ifBlank { default.name },
            apiKey = savedProvider.apiKey,
            baseUrl = baseUrl,
            model = savedProvider.model.ifBlank { default.model },
            models = normalizeModels(savedProvider.models.ifEmpty { default.models }),
            availableModelIds = normalizeModelIds(savedProvider.availableModelIds),
            availableModelSelectionInitialized =
                savedProvider.availableModelSelectionInitialized,
            timeoutSeconds = mergeTimeoutSeconds(default, savedProvider),
            chatCompletionsPath = savedProvider.chatCompletionsPath.ifBlank {
                default.chatCompletionsPath
            },
            modelsUrl = normalizeAiApiPath(
                baseUrl,
                savedProvider.modelsUrl.ifBlank { default.modelsUrl }
            ),
            useCustomModelsUrl = savedProvider.useCustomModelsUrl,
            balanceUrl = normalizeAiApiPath(
                baseUrl,
                savedProvider.balanceUrl.ifBlank { default.balanceUrl }
            ),
            balanceJsonPath = savedProvider.balanceJsonPath.ifBlank { default.balanceJsonPath },
            useCustomBalanceUrl = savedProvider.useCustomBalanceUrl,
            streamResponseEnabled = savedProvider.streamResponseEnabled
        )
    }

    private fun migrateLegacyConfig(defaults: List<AiProviderSetting>): List<AiProviderSetting> {
        val type = AiProviderType.from(appCtx.getPrefString(PreferKey.aiProviderType))
        val name = appCtx.getPrefString(PreferKey.aiProviderName).orEmpty()
        val targetId = when {
            name.equals("DeepSeek", ignoreCase = true) -> "deepseek"
            name.equals("Gemini", ignoreCase = true) || type == AiProviderType.GOOGLE -> "gemini"
            name.equals("Claude", ignoreCase = true) || type == AiProviderType.CLAUDE -> "claude"
            else -> "openai"
        }
        val apiKey = appCtx.getPrefString(PreferKey.aiApiKey).orEmpty()
        val baseUrl = appCtx.getPrefString(PreferKey.aiBaseUrl).orEmpty()
        val model = appCtx.getPrefString(PreferKey.aiModel).orEmpty()
        val timeoutSeconds = appCtx.getPrefInt(
            PreferKey.aiTimeoutSeconds,
            AI_PROVIDER_DEFAULT_TIMEOUT_SECONDS
        ).coerceIn(5, 600)
        val enabled = appCtx.getPrefBoolean(PreferKey.aiEnabled, true)
        return defaults.map { provider ->
            if (provider.id != targetId) {
                provider
            } else {
                provider.copy(
                    enabled = enabled,
                    apiKey = apiKey,
                    baseUrl = baseUrl.ifBlank { provider.baseUrl },
                    model = model.ifBlank { provider.model },
                    timeoutSeconds = timeoutSeconds,
                    chatCompletionsPath = appCtx.getPrefString(PreferKey.aiChatCompletionsPath)
                        .orEmpty()
                        .ifBlank { provider.chatCompletionsPath }
                )
            }
        }.also {
            appCtx.putPrefString(PreferKey.aiActiveProviderId, targetId)
        }
    }

    private fun normalize(provider: AiProviderSetting): AiProviderSetting {
        return provider.copy(
            timeoutSeconds = provider.timeoutSeconds.coerceIn(5, 600),
            models = normalizeModels(provider.models),
            availableModelIds = normalizeModelIds(provider.availableModelIds),
            modelsUrl = normalizeAiApiPath(provider.baseUrl, provider.modelsUrl),
            balanceUrl = normalizeAiApiPath(provider.baseUrl, provider.balanceUrl)
        )
    }

    private fun mergeTimeoutSeconds(
        default: AiProviderSetting,
        savedProvider: AiProviderSetting
    ): Int {
        val savedTimeout = savedProvider.timeoutSeconds.coerceIn(5, 600)
        return if (
            default.builtIn &&
            savedProvider.builtIn &&
            savedTimeout == AI_PROVIDER_LEGACY_DEFAULT_TIMEOUT_SECONDS
        ) {
            default.timeoutSeconds.coerceIn(5, 600)
        } else {
            savedTimeout
        }
    }

    private fun sanitize(provider: AiProviderSetting): AiProviderSetting? {
        return runCatching {
            val id = nullableString(provider.id)
            val type = nullableType(provider.type)
            val name = nullableString(provider.name)
            val baseUrl = nullableString(provider.baseUrl)
            provider.copy(
                id = id,
                type = type,
                name = name.ifBlank { id },
                apiKey = nullableString(provider.apiKey),
                baseUrl = baseUrl,
                model = nullableString(provider.model),
                models = normalizeModels(nullableModels(provider.models)),
                availableModelIds = normalizeModelIds(provider.availableModelIds),
                availableModelSelectionInitialized =
                    provider.availableModelSelectionInitialized,
                timeoutSeconds = provider.timeoutSeconds.coerceIn(5, 600),
                chatCompletionsPath = nullableString(provider.chatCompletionsPath)
                    .ifBlank { "/chat/completions" },
                modelsUrl = normalizeAiApiPath(baseUrl, nullableString(provider.modelsUrl)),
                thinkingParam = nullableString(provider.thinkingParam),
                effortParam = nullableString(provider.effortParam),
                disableEffortValue = nullableString(provider.disableEffortValue),
                reasoningOutputField = nullableString(provider.reasoningOutputField),
                balanceUrl = normalizeAiApiPath(baseUrl, nullableString(provider.balanceUrl)),
                balanceJsonPath = nullableString(provider.balanceJsonPath),
                streamResponseEnabled = provider.streamResponseEnabled,
                supportsStreamUsage = provider.supportsStreamUsage
            )
        }.getOrNull()
    }

    private fun defaultCustomProvider(type: AiProviderType): AiProviderSetting {
        return when (type) {
            AiProviderType.CLAUDE -> AiProviderSetting(
                id = "",
                type = AiProviderType.CLAUDE,
                builtIn = false,
                name = "Custom Anthropic",
                baseUrl = "https://api.anthropic.com/v1",
                modelsUrl = "/models",
                enabled = true
            )
            AiProviderType.OPENAI,
            AiProviderType.GOOGLE -> AiProviderSetting(
                id = "",
                type = AiProviderType.OPENAI,
                builtIn = false,
                name = "Custom OpenAI",
                baseUrl = "https://api.openai.com/v1",
                modelsUrl = "/models",
                enabled = true
            )
        }
    }

    private fun nextCustomProviderId(type: AiProviderType): String {
        val prefix = when (type) {
            AiProviderType.CLAUDE -> "custom_anthropic"
            AiProviderType.GOOGLE -> "custom_google"
            AiProviderType.OPENAI -> "custom_openai"
        }
        val ids = providers().map { it.id }.toSet()
        var index = 1
        while ("${prefix}_$index" in ids) {
            index++
        }
        return "${prefix}_$index"
    }

    private fun nullableString(value: String?): String {
        return value.orEmpty()
    }

    private fun nullableType(value: AiProviderType?): AiProviderType {
        return value ?: AiProviderType.OPENAI
    }

    private fun nullableModels(value: List<*>?): List<*> {
        return value ?: emptyList<Any>()
    }

    private fun normalizeModelIds(ids: List<String>?): List<String> {
        return ids.orEmpty().map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeModels(models: List<*>): List<AiModel> {
        return models.mapNotNull { model ->
            when (model) {
                is AiModel -> model.normalizedModel()
                is Map<*, *> -> model.toAiModel()
                is JsonObject -> model.toAiModel()
                is String -> AiModelRegistry.enrich(AiModel(id = model))
                else -> null
            }
        }.filter { it.id.isNotBlank() }
            .map { it.withClearedCapabilitiesIfNeeded() }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase() }
    }

    private fun AiModel.withClearedCapabilitiesIfNeeded(): AiModel {
        return if (AiModelRegistry.shouldClearDeclaredCapabilities(id)) {
            copy(
                inputModalities = emptyList(),
                outputModalities = emptyList(),
                abilities = emptyList()
            )
        } else {
            this
        }
    }

    private fun AiModel.normalizedModel(): AiModel? {
        val id = runCatching { id }.getOrNull().orEmpty().trim()
        if (id.isBlank()) {
            return null
        }
        return copy(
            id = id,
            name = runCatching { name }.getOrNull().orEmpty().ifBlank { id },
            displayName = runCatching { displayName }.getOrNull().orEmpty(),
            ownedBy = runCatching { ownedBy }.getOrNull().orEmpty(),
            type = runCatching { type }.getOrNull() ?: AiModelType.CHAT,
            inputModalities = runCatching { inputModalities }.getOrNull().orEmpty(),
            outputModalities = runCatching { outputModalities }.getOrNull().orEmpty(),
            abilities = runCatching { abilities }.getOrNull().orEmpty()
        )
    }

    private fun Map<*, *>.toAiModel(): AiModel {
        val id = stringValue("id")
        return AiModel(
            id = id,
            name = stringValue("name").ifBlank { id },
            displayName = stringValue("displayName"),
            ownedBy = stringValue("ownedBy").ifBlank { stringValue("owned_by") },
            type = aiModelTypeValue(),
            inputModalities = aiModelModalitiesValue("inputModalities", "d"),
            outputModalities = aiModelModalitiesValue("outputModalities", "e"),
            abilities = aiModelAbilitiesValue()
        )
    }

    private fun JsonObject.toAiModel(): AiModel {
        val id = stringValue("id")
        return AiModel(
            id = id,
            name = stringValue("name").ifBlank { id },
            displayName = stringValue("displayName"),
            ownedBy = stringValue("ownedBy").ifBlank { stringValue("owned_by") },
            type = aiModelTypeValue(),
            inputModalities = aiModelModalitiesValue("inputModalities", "d"),
            outputModalities = aiModelModalitiesValue("outputModalities", "e"),
            abilities = aiModelAbilitiesValue()
        )
    }

    private fun Map<*, *>.stringValue(key: String): String {
        return this[key]?.toString().orEmpty()
    }

    private fun JsonObject.stringValue(key: String): String {
        return get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
    }

    private fun Map<*, *>.aiModelTypeValue(): AiModelType {
        return parseAiModelType(stringValue("type").ifBlank { stringValue("modelType") })
    }

    private fun JsonObject.aiModelTypeValue(): AiModelType {
        return parseAiModelType(stringValue("type").ifBlank { stringValue("modelType") })
    }

    private fun Map<*, *>.aiModelModalitiesValue(
        stableKey: String,
        obfuscatedKey: String
    ): List<AiModelModality> {
        return parseAiModelModalities(this[stableKey] ?: this[obfuscatedKey])
    }

    private fun JsonObject.aiModelModalitiesValue(
        stableKey: String,
        obfuscatedKey: String
    ): List<AiModelModality> {
        return parseAiModelModalities(get(stableKey) ?: get(obfuscatedKey))
    }

    private fun Map<*, *>.aiModelAbilitiesValue(): List<AiModelAbility> {
        return parseAiModelAbilities(this["abilities"] ?: this["f"])
    }

    private fun JsonObject.aiModelAbilitiesValue(): List<AiModelAbility> {
        return parseAiModelAbilities(get("abilities") ?: get("f"))
    }

    private fun parseAiModelType(value: String): AiModelType {
        return when (value.lowercase()) {
            "image" -> AiModelType.IMAGE
            "embedding" -> AiModelType.EMBEDDING
            "video" -> AiModelType.VIDEO
            "asr", "speech_to_text", "stt" -> AiModelType.ASR
            "tts", "text_to_speech", "voice" -> AiModelType.TTS
            else -> AiModelType.CHAT
        }
    }

    private fun parseAiModelModalities(value: Any?): List<AiModelModality> {
        val values = modelValueList(value)
        return buildList {
            if (values.any { it.equals("text", ignoreCase = true) }) {
                add(AiModelModality.TEXT)
            }
            if (values.any { it.equals("image", ignoreCase = true) }) {
                add(AiModelModality.IMAGE)
            }
            if (values.any { it.equals("audio", ignoreCase = true) }) {
                add(AiModelModality.AUDIO)
            }
            if (values.any { it.equals("video", ignoreCase = true) }) {
                add(AiModelModality.VIDEO)
            }
        }
    }

    private fun parseAiModelAbilities(value: Any?): List<AiModelAbility> {
        val values = modelValueList(value)
        return buildList {
            if (values.any { it.equals("asr", ignoreCase = true) }) {
                add(AiModelAbility.ASR)
            }
            if (values.any { it.equals("tts", ignoreCase = true) }) {
                add(AiModelAbility.TTS)
            }
            if (values.any { it.equals("tool", ignoreCase = true) }) {
                add(AiModelAbility.TOOL)
            }
            if (values.any { it.equals("reasoning", ignoreCase = true) }) {
                add(AiModelAbility.REASONING)
            }
        }
    }

    private fun modelValueList(value: Any?): List<String> {
        return when (value) {
            is JsonArray -> value.mapNotNull { element ->
                element.takeIf { !it.isJsonNull }?.asString
            }
            is Iterable<*> -> value.mapNotNull { it?.toString() }
            is Array<*> -> value.mapNotNull { it?.toString() }
            is String -> value.split(',', '|').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

}
