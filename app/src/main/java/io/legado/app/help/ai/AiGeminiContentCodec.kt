package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.BufferedReader
import java.util.UUID

internal class AiGeminiContentCodec(
    private val setting: AiProviderSetting,
    modelId: String,
    modelInfo: AiModelInfo? = null,
) : AiChatCodec {
    private val modelId = modelId.removePrefix("models/")
    private val history = AiNativeChatHistory(setting, this.modelId)
    private val nativeReasoning = geminiNativeReasoning(modelId, modelInfo)

    override fun request(
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        stream: Boolean,
        conversationId: String?,
    ): Request {
        val system = messages.asSequence().filter { it.stringOrNull("role") == "system" }
            .map { it.get("content").contentText() }.filter(String::isNotBlank).toList()
        val body = JsonObject().apply {
            if (system.isNotEmpty()) add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply { system.forEach { add(geminiText(it)) } })
            })
            add("contents", contents(messages, tools.isNotEmpty()))
            val config = JsonObject().apply {
                params.temperature?.let { addProperty("temperature", it) }
                params.maxTokens?.let { addProperty("maxOutputTokens", it) }
                if (params.jsonResponse && tools.isEmpty()) addProperty("responseMimeType", "application/json")
                thinkingConfig(params)?.let { add("thinkingConfig", it) }
            }
            if (config.size() > 0) add("generationConfig", config)
            if (tools.isNotEmpty()) add("tools", JsonArray().apply {
                add(JsonObject().apply {
                    add("functionDeclarations", JsonArray().apply {
                        tools.forEach { definition ->
                            val function = requireNotNull(definition.objectOrNull("function"))
                            add(JsonObject().apply {
                                addProperty("name", function.stringOrNull("name"))
                                addProperty("description", function.stringOrNull("description").orEmpty())
                                function.objectOrNull("parameters")?.let { add("parametersJsonSchema", it.deepCopy()) }
                            })
                        }
                    })
                })
            })
        }
        val method = if (stream) "streamGenerateContent" else "generateContent"
        val url = buildAiApiEndpoint(setting.baseUrl, "/models").toHttpUrl().newBuilder()
            .addPathSegment("$modelId:$method")
            .apply { if (stream) addQueryParameter("alt", "sse") }
            .build()
        return Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", setting.apiKeyForRequest())
            .addOpenCodeProviderHeaders(setting, conversationId)
            .post(jsonBody(body))
            .build()
    }

    private fun thinkingConfig(params: AiTextParams): JsonObject? {
        val reasoning = nativeReasoning ?: return null
        if (params.disableThinking) {
            if (!reasoning.canDisable) return null
            return JsonObject().apply {
                addProperty("thinkingBudget", 0)
                addProperty("includeThoughts", false)
            }
        }
        if (!params.enableThinking) return null
        return JsonObject().apply {
            params.reasoningEffort?.takeIf(reasoning.efforts::contains)?.let {
                addProperty("thinkingLevel", it.uppercase())
            }
            reasoning.budgetRange?.let { range ->
                val budget = params.assistantReasoningMaxTokens ?: -1
                if (budget == -1 || budget in range) addProperty("thinkingBudget", budget)
            }
            addProperty("includeThoughts", true)
        }
    }

    private fun contents(messages: List<JsonObject>, useTools: Boolean) = JsonArray().apply {
        var consumedThrough = -1
        messages.forEachIndexed { index, message ->
            if (index <= consumedThrough) return@forEachIndexed
            val content = message.get("content").contentText()
            when (message.stringOrNull("role")) {
                "user" -> {
                    val parts = buildList {
                        if (content.isNotBlank()) add(geminiText(content))
                        message.chatVisionInputs().forEach { image ->
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", image.mime)
                                    addProperty("data", image.base64)
                                })
                            })
                        }
                    }
                    if (parts.isNotEmpty()) addNativeParts("user", "parts", parts)
                }
                "assistant" -> {
                    val calls = message.arrayOrNull("tool_calls").objectParts()
                    val native = history.parts(message)?.objectParts()
                    val responses = native?.takeIf { useTools || calls.isEmpty() }?.let {
                        matchingToolResponses(messages, index, calls, it)
                    }
                    if (native != null && responses != null) {
                        addNativeParts("model", "parts", native.replayWithText(content, ::isDisplayText, ::geminiText))
                        addNativeParts("user", "parts", responses)
                        consumedThrough = index + responses.size
                    } else {
                        val parts = buildList {
                            if (content.isNotBlank()) add(geminiText(content))
                            calls.mapNotNull { it.objectOrNull("function") }
                                .forEach { call ->
                                    val name = call.stringOrNull("name").orEmpty()
                                    val arguments = call.stringOrNull("arguments").orEmpty()
                                    add(geminiText("工具调用 $name：$arguments"))
                                }
                        }
                        addNativeParts("model", "parts", parts)
                    }
                }
                "tool" -> {
                    val part = geminiText("工具 ${message.stringOrNull("name").orEmpty()} 的结果：\n$content")
                    addNativeParts("user", "parts", listOf(part))
                }
            }
        }
    }

    private fun matchingToolResponses(
        messages: List<JsonObject>,
        assistantIndex: Int,
        calls: List<JsonObject>,
        nativeParts: List<JsonObject>,
    ): List<JsonObject>? {
        val nativeCalls = nativeParts.mapNotNull { it.objectOrNull("functionCall") }
        if (nativeCalls.size != calls.size) return null
        if (calls.isEmpty()) return emptyList()
        // Replay the entire native batch or render it as text. Gemini requires
        // one adjacent result per call and the same order as the signed calls.
        val adjacentResults = messages.subList(assistantIndex + 1, messages.size)
            .takeWhile { it.stringOrNull("role") == "tool" }
        if (adjacentResults.size != calls.size) return null
        val byId = adjacentResults.associateByTo(mutableMapOf()) { it.stringOrNull("tool_call_id") }
        return calls.zip(nativeCalls).map { (call, nativeCall) ->
            val id = call.stringOrNull("id")?.takeIf(String::isNotBlank) ?: return null
            val nativeId = nativeCall.stringOrNull("id")?.takeIf(String::isNotBlank)
            if (nativeId != null && nativeId != id) return null
            val name = nativeCall.stringOrNull("name")?.takeIf(String::isNotBlank) ?: return null
            if (call.objectOrNull("function")?.stringOrNull("name") != name) return null
            val result = byId.remove(id) ?: return null
            if (result.stringOrNull("name")?.let { it != name } == true) return null
            geminiFunctionResponse(nativeCall, result.get("content").contentText())
        }
    }

    private fun geminiFunctionResponse(call: JsonObject, content: String) = JsonObject().apply {
        add("functionResponse", JsonObject().apply {
            addProperty("name", call.stringOrNull("name"))
            call.stringOrNull("id")?.takeIf(String::isNotBlank)?.let { addProperty("id", it) }
            val decoded = runCatching { JsonParser.parseString(content) }.getOrNull()
            add("response", decoded?.takeIf { it.isJsonObject } ?: JsonObject().apply {
                addProperty("result", content)
            })
        })
    }

    override fun readJson(json: JsonObject): AiChatReply =
        ResponseDecoder().apply { accept(json) }.reply()

    override fun readStream(reader: BufferedReader, onUpdate: (String, String?) -> Unit): AiChatReply {
        val decoder = ResponseDecoder()
        reader.readAiSseFrames { event, data ->
            if (data.trim() == "[DONE]" && event != "error") return@readAiSseFrames true
            val json = JsonParser.parseString(data).asJsonObject
            if (decoder.accept(json, event)) {
                onUpdate(decoder.content.toString(), decoder.thoughts.toString().takeIf(String::isNotBlank))
            }
            false
        }
        if (decoder.finish == null) throw AiChatIncompleteStreamException("Gemini 流式响应提前结束")
        return decoder.reply()
    }

    private inner class ResponseDecoder {
        val content = StringBuilder()
        val thoughts = StringBuilder()
        var finish: String? = null
            private set
        private val parts = JsonArray()
        private var candidateReceived = false
        private var model: String? = null
        private var promptTokens: Int? = null
        private var responseTokens: Int? = null
        private var thoughtTokens: Int? = null
        private var totalTokens: Int? = null

        fun accept(json: JsonObject, event: String? = null): Boolean {
            json.requireNoAiError(event)
            model = json.stringOrNull("modelVersion") ?: model
            json.objectOrNull("usageMetadata")?.let { tokens ->
                promptTokens = tokens.intOrNull("promptTokenCount") ?: promptTokens
                responseTokens = tokens.intOrNull("candidatesTokenCount") ?: responseTokens
                thoughtTokens = tokens.intOrNull("thoughtsTokenCount") ?: thoughtTokens
                totalTokens = tokens.intOrNull("totalTokenCount") ?: totalTokens
            }
            val blockReason = json.objectOrNull("promptFeedback")?.stringOrNull("blockReason")
            if (!blockReason.isNullOrBlank() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
                finish = "content_filter"
                return false
            }
            val candidate = json.arrayOrNull("candidates").objectParts().firstOrNull() ?: return false
            candidateReceived = true
            candidate.stringOrNull("finishReason")
                ?.takeUnless { it.isBlank() || it == "FINISH_REASON_UNSPECIFIED" }?.let { finish = it }
            var changed = false
            candidate.objectOrNull("content")?.arrayOrNull("parts").objectParts().forEach { part ->
                // A trailing empty-text part can carry a signature. Keep its
                // position, including empty or invisible parts, on the wire.
                parts.add(part.deepCopy())
                val text = part.stringOrNull("text").orEmpty()
                if (text.isNotEmpty()) {
                    (if (isDisplayText(part)) content else thoughts).append(text)
                    changed = true
                }
            }
            return changed
        }

        fun reply(): AiChatReply {
            check(candidateReceived || finish == "content_filter") { "Gemini response missing candidates" }
            val calls = JsonArray().apply {
                parts.objectParts().mapNotNull { it.objectOrNull("functionCall") }.forEach { function ->
                    val id = function.stringOrNull("id")?.takeIf(String::isNotBlank)
                        ?: "gemini_${UUID.randomUUID()}"
                    val name = requireNotNull(function.stringOrNull("name")?.takeIf(String::isNotBlank)) {
                        "Gemini function call missing name"
                    }
                    add(aiFunctionCall(id, name, function.get("args").aiJsonObjectArgument().toString()))
                }
            }
            val reason = when (finish) {
                "STOP" -> if (calls.size() > 0) "tool_calls" else "stop"
                "MAX_TOKENS" -> "length"
                "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "RECITATION", "LANGUAGE",
                "MODEL_ARMOR", "ESCALATION", "PUP_LIMITED_DISABLED", "content_filter" -> "content_filter"
                else -> "incomplete"
            }
            val completionTokens = if (responseTokens != null || thoughtTokens != null) {
                (responseTokens ?: 0) + (thoughtTokens ?: 0)
            } else {
                null
            }
            val usage = if (promptTokens != null || completionTokens != null || totalTokens != null) {
                AiChatUsage(promptTokens, completionTokens, totalTokens)
            } else {
                null
            }
            return history.reply(parts, content.toString(), thoughts.toString(), calls, model, reason, usage)
        }
    }

    private fun isDisplayText(part: JsonObject) = part.has("text") && part.get("thought")?.asBoolean != true

    private fun geminiText(text: String) = JsonObject().apply { addProperty("text", text) }
}
