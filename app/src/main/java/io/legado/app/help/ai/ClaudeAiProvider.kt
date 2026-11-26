package io.legado.app.help.ai

import com.google.gson.JsonObject
import okhttp3.Request

class ClaudeAiProvider : AiProvider {

    override suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        val modelsUrl = AiModelEndpointResolver.candidates(setting).firstOrNull()
            ?: error("Models URL is empty")
        val apiKey = setting.apiKeyForRequest()
        val request = Request.Builder()
            .url(buildAiApiEndpoint(setting.baseUrl, modelsUrl))
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("x-api-key", apiKey)
                    if (setting.useCustomModelsUrl) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }
            }
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
        val modelKey = setting.modelConfigurationKey()
        return aiHttpClient(setting.timeoutSeconds).readModelCatalogPages(
            firstRequest = request,
            cursorParameter = "after_id",
            additionalCursorParameter = "before_id",
            nextCursor = { json ->
                val hasMore = json.get("has_more")?.takeUnless { it.isJsonNull }
                check(hasMore == null || (hasMore.isJsonPrimitive && hasMore.asJsonPrimitive.isBoolean)) {
                    "Invalid Claude model list pagination"
                }
                if (hasMore?.asBoolean == true) {
                    requireNotNull(json.modelCatalogCursor("last_id")) { "Missing Claude model list cursor" }
                } else null
            },
            readModels = { json, sourceId -> readModels(json, modelKey, sourceId) },
        )
    }

    private fun readModels(json: JsonObject, modelKey: String, sourceId: String): List<AiModel> {
        return json.modelCatalogEntries("data", "id").map { (obj, id) ->
            AiModel(
                id = id,
                name = obj.stringOrNull("name") ?: id,
                displayName = obj.stringOrNull("display_name").orEmpty(),
                ownedBy = obj.stringOrNull("owned_by").orEmpty().ifBlank { "Anthropic" },
                modelInfo = readModelInfo(obj, modelKey, AiModelCatalogFormat.CLAUDE).copy(catalogSourceId = sourceId),
            )
        }
    }
}
