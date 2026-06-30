package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.http.await
import io.legado.app.web.mcp.McpInternalChannel
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class AiChatClient {

    suspend fun send(
        messages: MutableList<JsonObject>,
        onStreamUpdate: (AiChatStreamUpdate) -> Unit = {}
    ): AiChatTurnResult {
        val selection = runCatching { AiConfig.requireAssistantModel() }.getOrElse {
            throw IllegalStateException("请先在模型设置中选择聊天模型")
        }
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error("AI provider not found: ${selection.providerId}")
        check(setting.enabled) { "AI provider is disabled" }
        check(setting.type == AiProviderType.OPENAI) { "AI 聊天暂只支持 OpenAI 兼容提供商" }
        val model = resolveModel(setting, selection.modelId)
        val params = AiConfig.assistantChatParams(model.abilities.contains(AiModelAbility.REASONING))
        val tools = if (AiConfig.internalMcpEnabled && model.abilities.contains(AiModelAbility.TOOL)) {
            loadMcpTools()
        } else {
            emptyList()
        }
        val warnings = buildList {
            if (AiConfig.internalMcpEnabled && !model.abilities.contains(AiModelAbility.TOOL)) {
                add("当前模型未声明工具能力，未附加 MCP tools")
            }
            if (!AiConfig.internalMcpEnabled) {
                add("内置 MCP 未开启")
            }
        }
        val client = aiHttpClient(setting.timeoutSeconds)
        val requestMessages = messages
        val toolTrace = mutableListOf<String>()
        var lastUsage: AiChatUsage? = null
        var lastModel: String? = null
        var lastReasoning: String? = null
        var lastFinishReason: String? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val stream = setting.streamResponseEnabled
            val body = buildRequestBody(model, requestMessages, tools, params, stream)
            val request = Request.Builder()
                .url("${setting.baseUrl.trimEndSlash()}${setting.chatCompletionsPath.ensureStartSlash()}")
                .apply {
                    if (setting.apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${setting.apiKey}")
                    }
                }
                .addHeader("Content-Type", "application/json")
                .post(jsonBody(body))
                .build()
            val reasoningKey = AiModelRegistry.capabilities(model.id)
                .reasoning
                .reasoningOutputField
                .ifBlank { "reasoning_content" }
            val completion = if (stream) {
                client.executeStreamChat(request, reasoningKey, onStreamUpdate)
            } else {
                client.executeJsonChat(request, reasoningKey)
            }
            lastModel = completion.model
            lastUsage = completion.usage
            lastReasoning = completion.reasoning
            lastFinishReason = completion.finishReason
            val message = completion.message
            val content = completion.content
            val toolCalls = completion.toolCalls
            if (toolCalls.size() == 0) {
                requestMessages.add(uploadAssistantMessage(content, lastReasoning))
                return AiChatTurnResult(
                    content = content,
                    reasoning = lastReasoning,
                    model = lastModel ?: model.id,
                    finishReason = lastFinishReason,
                    usage = lastUsage,
                    toolTrace = toolTrace,
                    warnings = warnings
                )
            }
            requestMessages.add(message.deepCopy().asJsonObject.apply {
                addProperty("role", "assistant")
                if (!has("content") || get("content").isJsonNull) {
                    addProperty("content", "")
                }
            })
            toolCalls.forEach { call ->
                val callObject = call.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val callId = callObject.stringOrNull("id").orEmpty()
                val function = callObject.objectOrNull("function") ?: return@forEach
                val functionName = function.stringOrNull("name").orEmpty()
                val toolName = functionName.removePrefix(MCP_TOOL_PREFIX)
                val arguments = function.stringOrNull("arguments").orEmpty().parseObjectOrEmpty()
                val result = runCatching {
                    McpInternalChannel.callTool(toolName, arguments)
                }.getOrElse {
                    JsonObject().apply {
                        addProperty("ok", false)
                        addProperty("error", it.localizedMessage ?: "MCP tool failed")
                    }
                }
                toolTrace.add("$toolName(${arguments.toString().take(120)})")
                onStreamUpdate(
                    AiChatStreamUpdate(
                        content = content,
                        reasoning = lastReasoning,
                        toolTrace = toolTrace.toList()
                    )
                )
                requestMessages.add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", callId)
                    addProperty("name", functionName)
                    addProperty("content", result.toString())
                })
            }
        }
    }

    fun newSystemMessage(): JsonObject {
        return JsonObject().apply {
            addProperty("role", "system")
            addProperty(
                "content",
                "你是 Legado 内置 AI 助手。回答要简洁直接。若用户的问题需要读取或管理 Legado 数据，并且可用 MCP 工具能完成，应优先调用 MCP 工具。"
            )
        }
    }

    fun newUserMessage(content: String): JsonObject {
        return JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", content)
        }
    }

    fun newAssistantMessage(content: String, reasoning: String?): JsonObject {
        return uploadAssistantMessage(content, reasoning)
    }

    private fun resolveModel(setting: AiProviderSetting, modelId: String): AiModel {
        val model = setting.models.firstOrNull { it.id == modelId } ?: AiModel(id = modelId, name = modelId)
        return AiModelRegistry.enrich(model)
    }

    private fun buildRequestBody(
        model: AiModel,
        messages: List<JsonObject>,
        tools: List<McpChatTool>,
        params: AiTextParams,
        stream: Boolean
    ): JsonObject {
        val reasoningOptions = AiModelRegistry.capabilities(model.id).reasoning
        return JsonObject().apply {
            addProperty("model", model.id)
            add("messages", JsonArray().apply {
                messages.forEach { add(it) }
            })
            params.temperature?.let { addProperty("temperature", it) }
            params.maxTokens?.let { addProperty("max_tokens", it) }
            addProperty("stream", stream)
            if (params.enableThinking && reasoningOptions.thinkingParam.isNotBlank()) {
                add(reasoningOptions.thinkingParam, JsonObject().apply {
                    addProperty("type", "enabled")
                })
            } else if (params.disableThinking && reasoningOptions.thinkingParam.isNotBlank()) {
                add(reasoningOptions.thinkingParam, JsonObject().apply {
                    addProperty("type", "disabled")
                })
            }
            if (params.enableThinking && reasoningOptions.effortParam.isNotBlank()) {
                addProperty(reasoningOptions.effortParam, params.reasoningEffort ?: "high")
            }
            if (tools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    tools.forEach { tool ->
                        add(JsonObject().apply {
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", tool.modelName)
                                addProperty("description", tool.description)
                                add("parameters", tool.inputSchema)
                            })
                        })
                    }
                })
            }
        }
    }

    private suspend fun OkHttpClient.executeJsonChat(
        request: Request,
        reasoningKey: String
    ): ChatCompletionSnapshot {
        val (response, responseBody) = executeJsonOrThrow(request)
        if (!response.isSuccessful) {
            error("HTTP ${response.code}: ${responseBody.take(500)}")
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val choice = json.arrayOrNull("choices")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: error("OpenAI response missing choices")
        val message = choice.objectOrNull("message")
            ?: error("OpenAI response missing message")
        val usage = json.objectOrNull("usage")?.toChatUsage()
        return ChatCompletionSnapshot(
            message = message,
            content = message.get("content").contentText(),
            reasoning = message.stringOrNull(reasoningKey)
                ?: message.stringOrNull("reasoning_content")
                ?: message.stringOrNull("reasoning"),
            model = json.stringOrNull("model"),
            finishReason = choice.stringOrNull("finish_reason"),
            usage = usage,
            toolCalls = message.arrayOrNull("tool_calls") ?: JsonArray()
        )
    }

    private suspend fun OkHttpClient.executeStreamChat(
        request: Request,
        reasoningKey: String,
        onStreamUpdate: (AiChatStreamUpdate) -> Unit
    ): ChatCompletionSnapshot {
        return withContext(IO) {
            val response = newCall(request).await()
            response.use {
                val responseBody = it.body
                if (!it.isSuccessful) {
                    error("HTTP ${it.code}: ${responseBody.string().take(500)}")
                }
                var model: String? = null
                var finishReason: String? = null
                var usage: AiChatUsage? = null
                val contentBuilder = StringBuilder()
                val reasoningBuilder = StringBuilder()
                val toolCalls = linkedMapOf<Int, StreamToolCall>()
                responseBody.charStream().buffered().useLines { lines ->
                    lines.forEach { line ->
                        currentCoroutineContext().ensureActive()
                        val data = line.trim()
                            .takeIf { line -> line.startsWith("data:") }
                            ?.removePrefix("data:")
                            ?.trim()
                            ?: return@forEach
                        if (data == "[DONE]") {
                            return@forEach
                        }
                        val json = runCatching {
                            JsonParser.parseString(data).asJsonObject
                        }.getOrNull() ?: return@forEach
                        model = json.stringOrNull("model") ?: model
                        usage = json.objectOrNull("usage")?.toChatUsage() ?: usage
                        val choice = json.arrayOrNull("choices")
                            ?.firstOrNull()
                            ?.takeIf { it.isJsonObject }
                            ?.asJsonObject
                            ?: return@forEach
                        finishReason = choice.stringOrNull("finish_reason") ?: finishReason
                        val delta = choice.objectOrNull("delta")
                            ?: choice.objectOrNull("message")
                            ?: JsonObject()
                        val contentDelta = delta.get("content").contentText()
                        if (contentDelta.isNotEmpty()) {
                            contentBuilder.append(contentDelta)
                        }
                        val reasoningDelta = delta.stringOrNull(reasoningKey)
                            ?: delta.stringOrNull("reasoning_content")
                            ?: delta.stringOrNull("reasoning")
                        if (!reasoningDelta.isNullOrEmpty()) {
                            reasoningBuilder.append(reasoningDelta)
                        }
                        delta.arrayOrNull("tool_calls")?.forEach { item ->
                            val obj = item.takeIf { call -> call.isJsonObject }
                                ?.asJsonObject
                                ?: return@forEach
                            val index = obj.intOrNull("index") ?: toolCalls.size
                            val toolCall = toolCalls.getOrPut(index) { StreamToolCall() }
                            obj.stringOrNull("id")?.let { id -> toolCall.id = id }
                            obj.stringOrNull("type")?.let { type -> toolCall.type = type }
                            obj.objectOrNull("function")?.let { function ->
                                function.stringOrNull("name")?.let { name ->
                                    toolCall.name = name
                                }
                                function.stringOrNull("arguments")?.let { arguments ->
                                    toolCall.arguments.append(arguments)
                                }
                            }
                        }
                        if (contentDelta.isNotEmpty() || !reasoningDelta.isNullOrEmpty()) {
                            onStreamUpdate(
                                AiChatStreamUpdate(
                                    content = contentBuilder.toString(),
                                    reasoning = reasoningBuilder.toString()
                                        .takeIf { text -> text.isNotBlank() }
                                )
                            )
                        }
                    }
                }
                val toolCallArray = JsonArray().apply {
                    toolCalls.toSortedMap().values.forEach { add(it.toJson()) }
                }
                val content = contentBuilder.toString()
                val reasoning = reasoningBuilder.toString().takeIf { text -> text.isNotBlank() }
                val message = JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", content)
                    reasoning?.let { value -> addProperty("reasoning_content", value) }
                    if (toolCallArray.size() > 0) {
                        add("tool_calls", toolCallArray)
                    }
                }
                ChatCompletionSnapshot(
                    message = message,
                    content = content,
                    reasoning = reasoning,
                    model = model,
                    finishReason = finishReason,
                    usage = usage,
                    toolCalls = toolCallArray
                )
            }
        }
    }

    private fun loadMcpTools(): List<McpChatTool> {
        val response = McpInternalChannel.request(JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", "tools")
            addProperty("method", "tools/list")
        })?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val tools = response.objectOrNull("result")?.arrayOrNull("tools") ?: return emptyList()
        return tools.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = obj.stringOrNull("name") ?: return@mapNotNull null
            val inputSchema = obj.objectOrNull("inputSchema") ?: JsonObject().apply {
                addProperty("type", "object")
            }
            McpChatTool(
                name = name,
                modelName = "$MCP_TOOL_PREFIX$name",
                description = obj.stringOrNull("description").orEmpty(),
                inputSchema = inputSchema
            )
        }
    }

    private fun uploadAssistantMessage(content: String, reasoning: String?): JsonObject {
        return JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content)
            if (!reasoning.isNullOrBlank()) {
                addProperty("reasoning_content", reasoning)
            }
        }
    }

    private fun String.parseObjectOrEmpty(): JsonObject {
        return runCatching {
            JsonParser.parseString(this).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull() ?: JsonObject()
    }

    private fun JsonObject.toChatUsage(): AiChatUsage {
        return AiChatUsage(
            promptTokens = intOrNull("prompt_tokens"),
            completionTokens = intOrNull("completion_tokens"),
            totalTokens = intOrNull("total_tokens")
        )
    }

    private data class McpChatTool(
        val name: String,
        val modelName: String,
        val description: String,
        val inputSchema: JsonObject
    )

    private data class ChatCompletionSnapshot(
        val message: JsonObject,
        val content: String,
        val reasoning: String?,
        val model: String?,
        val finishReason: String?,
        val usage: AiChatUsage?,
        val toolCalls: JsonArray
    )

    private class StreamToolCall {
        var id: String = ""
        var type: String = "function"
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()

        fun toJson(): JsonObject {
            return JsonObject().apply {
                addProperty("id", id)
                addProperty("type", type.ifBlank { "function" })
                add("function", JsonObject().apply {
                    addProperty("name", name)
                    addProperty("arguments", arguments.toString())
                })
            }
        }
    }

    companion object {
        private const val MCP_TOOL_PREFIX = "mcp__legado__"
    }
}

data class AiChatTurnResult(
    val content: String,
    val reasoning: String?,
    val model: String,
    val finishReason: String?,
    val usage: AiChatUsage?,
    val toolTrace: List<String>,
    val warnings: List<String>
)

data class AiChatStreamUpdate(
    val content: String,
    val reasoning: String?,
    val toolTrace: List<String> = emptyList()
)

data class AiChatUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)
