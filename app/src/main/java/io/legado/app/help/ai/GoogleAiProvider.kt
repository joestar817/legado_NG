package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.Request

class GoogleAiProvider : AiProvider {

    override suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        val request = Request.Builder()
            .url("${setting.baseUrl.trimEndSlash()}/models?pageSize=100")
            .addHeader("x-goog-api-key", setting.apiKey)
            .get()
            .build()
        val json = aiHttpClient(setting.timeoutSeconds).executeJson(request)
        return json.arrayOrNull("models")?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = obj.stringOrNull("name")?.substringAfter("models/") ?: return@mapNotNull null
            val methods = obj.arrayOrNull("supportedGenerationMethods")
            if (methods != null && methods.none { it.asString == "generateContent" }) {
                return@mapNotNull null
            }
            AiModel(id = name, name = obj.stringOrNull("displayName") ?: name, ownedBy = "Google")
        } ?: emptyList()
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
            .url("${setting.baseUrl.trimEndSlash()}/models/${setting.model}:generateContent")
            .addHeader("x-goog-api-key", setting.apiKey)
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
