package io.legado.app.help.ai

import com.google.gson.JsonArray
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

    override suspend fun generateText(
        setting: AiProviderSetting,
        messages: List<AiMessage>,
        params: AiTextParams
    ): AiTextResult {
        val systemText = messages
            .filter { it.role == AiMessage.Role.SYSTEM }
            .joinToString("\n") { it.content }
        val contents = JsonArray().apply {
            messages.filter { it.role != AiMessage.Role.SYSTEM }.forEach { message ->
                add(JsonObject().apply {
                    addProperty(
                        "role",
                        if (message.role == AiMessage.Role.ASSISTANT) "model" else "user"
                    )
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", message.content)
                        })
                    })
                })
            }
        }
        val requestBody = JsonObject().apply {
            if (systemText.isNotBlank()) {
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemText) })
                    })
                })
            }
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                params.temperature?.let { addProperty("temperature", it) }
                params.maxTokens?.let { addProperty("maxOutputTokens", it) }
            })
        }

        val request = Request.Builder()
            .url(buildAiApiEndpoint(setting.baseUrl, "/models/${setting.model}:generateContent"))
            .addHeader("x-goog-api-key", setting.apiKeyForRequest())
            .addHeader("Content-Type", "application/json")
            .post(jsonBody(requestBody))
            .build()
        val json = aiHttpClient(setting.timeoutSeconds).executeJson(request)
        val candidate = json.arrayOrNull("candidates")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: error("Gemini response missing candidates")
        val parts = candidate.objectOrNull("content")?.arrayOrNull("parts") ?: JsonArray()
        val content = parts.mapNotNull { it.textPart() }.joinToString("")
        val usage = json.objectOrNull("usageMetadata")
        return AiTextResult(
            content = content,
            model = setting.model,
            finishReason = candidate.stringOrNull("finishReason"),
            promptTokens = usage?.intOrNull("promptTokenCount"),
            completionTokens = usage?.intOrNull("candidatesTokenCount"),
            totalTokens = usage?.intOrNull("totalTokenCount")
        )
    }
}
