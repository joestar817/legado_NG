package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.Request

class ClaudeAiProvider : AiProvider {

    override suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        val request = Request.Builder()
            .url("${setting.baseUrl.trimEndSlash()}/models")
            .addHeader("x-api-key", setting.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()
        val json = aiHttpClient(setting.timeoutSeconds).executeJson(request)
        return json.arrayOrNull("data")?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.stringOrNull("id") ?: return@mapNotNull null
            AiModel(id = id, name = obj.stringOrNull("display_name") ?: id, ownedBy = "Anthropic")
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
        val requestBody = JsonObject().apply {
            addProperty("model", setting.model)
            addProperty("max_tokens", params.maxTokens ?: 2048)
            params.temperature?.let { addProperty("temperature", it) }
            if (systemText.isNotBlank()) {
                addProperty("system", systemText)
            }
            add("messages", JsonArray().apply {
                messages.filter { it.role != AiMessage.Role.SYSTEM }.forEach { message ->
                    add(JsonObject().apply {
                        addProperty(
                            "role",
                            if (message.role == AiMessage.Role.ASSISTANT) "assistant" else "user"
                        )
                        addProperty("content", message.content)
                    })
                }
            })
        }
        val request = Request.Builder()
            .url("${setting.baseUrl.trimEndSlash()}/messages")
            .addHeader("x-api-key", setting.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody(requestBody))
            .build()
        val json = aiHttpClient(setting.timeoutSeconds).executeJson(request)
        val content = json.arrayOrNull("content")
            ?.mapNotNull { it.textPart() }
            ?.joinToString("")
            .orEmpty()
        val usage = json.objectOrNull("usage")
        return AiTextResult(
            content = content,
            model = json.stringOrNull("model"),
            finishReason = json.stringOrNull("stop_reason"),
            promptTokens = usage?.intOrNull("input_tokens"),
            completionTokens = usage?.intOrNull("output_tokens"),
            totalTokens = usage?.let {
                (it.intOrNull("input_tokens") ?: 0) + (it.intOrNull("output_tokens") ?: 0)
            }
        )
    }
}
