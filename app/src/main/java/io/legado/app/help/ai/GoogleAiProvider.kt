package io.legado.app.help.ai

import com.google.gson.JsonObject
import okhttp3.Request

class GoogleAiProvider : AiProvider {

    override suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        val modelsApi = if (setting.useCustomModelsUrl) {
            setting.modelsUrl
        } else {
            "/models?pageSize=100"
        }
        val modelsUrl = buildAiApiEndpoint(setting.baseUrl, modelsApi)
        val request = Request.Builder()
            .url(modelsUrl)
            .addHeader("x-goog-api-key", setting.apiKeyForRequest())
            .get()
            .build()
        val modelKey = setting.modelConfigurationKey()
        return aiHttpClient(setting.timeoutSeconds).readModelCatalogPages(
            firstRequest = request,
            cursorParameter = "pageToken",
            additionalCursorParameter = "page_token",
            nextCursor = { it.modelCatalogCursor("nextPageToken") },
            readModels = { json, sourceId -> readModels(json, modelKey, sourceId) },
        )
    }

    private fun readModels(json: JsonObject, modelKey: String, sourceId: String): List<AiModel> {
        return json.modelCatalogEntries("models", "name").mapNotNull { (obj, id) ->
            val name = id.substringAfter("models/").trim().takeIf { it.isNotEmpty() }
                ?: error("Invalid Gemini model identifier")
            val methods = if (obj.has("supportedGenerationMethods")) {
                obj.catalogStrings("supportedGenerationMethods") ?: error("Invalid Gemini generation methods")
            } else null
            if (methods != null && "generateContent" !in methods) {
                return@mapNotNull null
            }
            AiModel(
                id = name,
                name = name,
                displayName = obj.stringOrNull("displayName").orEmpty(),
                ownedBy = "Google",
                modelInfo = readModelInfo(obj, modelKey, AiModelCatalogFormat.GEMINI).copy(catalogSourceId = sourceId),
            )
        }
    }
}
