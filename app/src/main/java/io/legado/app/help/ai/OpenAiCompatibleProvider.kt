package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request

class OpenAiCompatibleProvider : AiProvider {

    override suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        val client = aiHttpClient(setting.timeoutSeconds)
        var lastError: Throwable? = null
        AiModelEndpointResolver.candidates(setting).forEach { url ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (setting.apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer ${setting.apiKey}")
                        }
                    }
                    .get()
                    .build()
                val (response, body) = client.executeJsonOrThrow(request)
                if (response.code == 404 || response.code == 405) {
                    return@forEach
                }
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${body.take(500)}")
                }
                val json = JsonParser.parseString(body).asJsonObject
                return parseModels(json)
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No model endpoint candidates")
    }

    override suspend fun generateText(
        setting: AiProviderSetting,
        messages: List<AiMessage>,
        params: AiTextParams
    ): AiTextResult {
        val reasoningOptions = setting.reasoningOptions(setting.model)
        val requestBody = JsonObject().apply {
            addProperty("model", setting.model)
            add("messages", JsonArray().apply {
                messages.forEach { message ->
                    add(JsonObject().apply {
                        addProperty("role", message.role.apiValue)
                        addProperty("content", message.content)
                    })
                }
            })
            params.temperature?.let { addProperty("temperature", it) }
            params.maxTokens?.let { addProperty("max_tokens", it) }
            if (params.jsonResponse) {
                add("response_format", JsonObject().apply {
                    addProperty("type", "json_object")
                })
            }
            addReasoningParams(params, reasoningOptions)
            addProperty("stream", false)
        }

        val client = aiHttpClient(setting.timeoutSeconds)
        val request = Request.Builder()
            .url("${setting.baseUrl.trimEndSlash()}${setting.chatCompletionsPath.ensureStartSlash()}")
            .apply {
                if (setting.apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer ${setting.apiKey}")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(jsonBody(requestBody))
            .build()

        val (response, body) = client.executeJsonOrThrow(request)
        if (!response.isSuccessful) {
            error("HTTP ${response.code}: ${body.take(500)}")
        }
        val json = JsonParser.parseString(body).asJsonObject
        val choice = json.arrayOrNull("choices")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: error("OpenAI response missing choices")
        val message = choice.objectOrNull("message")
        val content = message?.get("content").contentText()
            .ifBlank { choice.objectOrNull("delta")?.get("content").contentText() }
        val reasoningKey = reasoningOptions.reasoningOutputField.ifBlank { "reasoning_content" }
        val reasoning = message?.stringOrNull(reasoningKey)
            ?: message?.stringOrNull("reasoning")
        val usage = json.objectOrNull("usage")
        return AiTextResult(
            content = content,
            reasoning = reasoning,
            model = json.stringOrNull("model"),
            finishReason = choice.stringOrNull("finish_reason"),
            promptTokens = usage?.intOrNull("prompt_tokens"),
            completionTokens = usage?.intOrNull("completion_tokens"),
            totalTokens = usage?.intOrNull("total_tokens"),
            rawPreview = body.take(1000)
        )
    }

    private fun parseModels(json: JsonObject): List<AiModel> {
        return json.arrayOrNull("data")
            ?.mapNotNull { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = obj.stringOrNull("id") ?: return@mapNotNull null
                val clearCapabilities = AiModelRegistry.shouldClearDeclaredCapabilities(id)
                AiModel(
                    id = id,
                    name = obj.stringOrNull("name") ?: id,
                    ownedBy = obj.stringOrNull("owned_by").orEmpty(),
                    inputModalities = if (clearCapabilities) {
                        emptyList()
                    } else {
                        obj.stringArrayOrEmpty("input_modalities").toModelModalities()
                    },
                    outputModalities = if (clearCapabilities) {
                        emptyList()
                    } else {
                        obj.stringArrayOrEmpty("output_modalities").toModelModalities()
                    },
                    abilities = if (clearCapabilities) {
                        emptyList()
                    } else {
                        obj.stringArrayOrEmpty("supported_features").toModelAbilities()
                    }
                )
            }
            ?: emptyList()
    }

    private fun JsonObject.stringArrayOrEmpty(key: String): List<String> {
        return arrayOrNull(key)
            ?.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive }?.asString?.trim()
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun List<String>.toModelAbilities(): List<AiModelAbility> {
        val values = map { it.lowercase() }.toSet()
        return buildList {
            if ("tools" in values || "tool" in values || "function_calling" in values) {
                add(AiModelAbility.TOOL)
            }
            if ("reasoning" in values) {
                add(AiModelAbility.REASONING)
            }
        }
    }

    private fun List<String>.toModelModalities(): List<AiModelModality> {
        val values = map { it.lowercase() }.toSet()
        return buildList {
            if ("text" in values) add(AiModelModality.TEXT)
            if ("image" in values) add(AiModelModality.IMAGE)
            if ("audio" in values) add(AiModelModality.AUDIO)
            if ("video" in values) add(AiModelModality.VIDEO)
        }
    }
}
