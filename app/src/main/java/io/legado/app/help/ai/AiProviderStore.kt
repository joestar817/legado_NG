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
        saveProviders(providers().map { if (it.id == provider.id) provider else it })
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
            .filter { it.id.isNotBlank() && it.baseUrl.isNotBlank() }
            .map { normalize(it) }
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
        return default.copy(
            enabled = savedProvider.enabled,
            name = savedProvider.name.ifBlank { default.name },
            apiKey = savedProvider.apiKey,
            baseUrl = savedProvider.baseUrl.ifBlank { default.baseUrl },
            model = savedProvider.model.ifBlank { default.model },
            models = normalizeModels(savedProvider.models.ifEmpty { default.models }),
            timeoutSeconds = savedProvider.timeoutSeconds.coerceIn(5, 600),
            chatCompletionsPath = savedProvider.chatCompletionsPath.ifBlank {
                default.chatCompletionsPath
            },
            modelsUrl = savedProvider.modelsUrl.ifBlank { default.modelsUrl }
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
        val timeoutSeconds = appCtx.getPrefInt(PreferKey.aiTimeoutSeconds, 60).coerceIn(5, 600)
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
            models = normalizeModels(provider.models)
        )
    }

    private fun normalizeModels(models: List<*>): List<AiModel> {
        return models.mapNotNull { model ->
            when (model) {
                is AiModel -> model
                is Map<*, *> -> model.toAiModel()
                is JsonObject -> model.toAiModel()
                is String -> AiModel(id = model)
                else -> null
            }
        }.filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.id.lowercase() }
    }

    private fun Map<*, *>.toAiModel(): AiModel {
        val id = stringValue("id")
        return AiModel(
            id = id,
            name = stringValue("name").ifBlank { id },
            ownedBy = stringValue("ownedBy").ifBlank { stringValue("owned_by") }
        )
    }

    private fun JsonObject.toAiModel(): AiModel {
        val id = stringValue("id")
        return AiModel(
            id = id,
            name = stringValue("name").ifBlank { id },
            ownedBy = stringValue("ownedBy").ifBlank { stringValue("owned_by") }
        )
    }

    private fun Map<*, *>.stringValue(key: String): String {
        return this[key]?.toString().orEmpty()
    }

    private fun JsonObject.stringValue(key: String): String {
        return get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()
    }

}
