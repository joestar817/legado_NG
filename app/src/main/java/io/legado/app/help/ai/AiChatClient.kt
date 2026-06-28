package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.web.mcp.McpInternalChannel
import okhttp3.Request

class AiChatClient {

    suspend fun send(messages: MutableList<JsonObject>): AiChatTurnResult {
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
        repeat(MAX_TOOL_ROUNDS + 1) { round ->
            val body = buildRequestBody(model, requestMessages, tools, params)
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
            val (response, responseBody) = client.executeJsonOrThrow(request)
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${responseBody.take(500)}")
            }
            val json = JsonParser.parseString(responseBody).asJsonObject
            lastModel = json.stringOrNull("model")
            lastUsage = json.objectOrNull("usage")?.let {
                AiChatUsage(
                    promptTokens = it.intOrNull("prompt_tokens"),
                    completionTokens = it.intOrNull("completion_tokens"),
                    totalTokens = it.intOrNull("total_tokens")
                )
            }
            val choice = json.arrayOrNull("choices")
                ?.firstOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: error("OpenAI response missing choices")
            val message = choice.objectOrNull("message")
                ?: error("OpenAI response missing message")
            val content = message.get("content").contentText()
            lastReasoning = message.stringOrNull("reasoning_content")
                ?: message.stringOrNull("reasoning")
            lastFinishReason = choice.stringOrNull("finish_reason")
            val toolCalls = message.arrayOrNull("tool_calls") ?: JsonArray()
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
            if (round >= MAX_TOOL_ROUNDS) {
                return AiChatTurnResult(
                    content = content.ifBlank { "工具调用轮数达到上限，已停止继续调用。" },
                    reasoning = lastReasoning,
                    model = lastModel ?: model.id,
                    finishReason = lastFinishReason,
                    usage = lastUsage,
                    toolTrace = toolTrace,
                    warnings = warnings + "工具调用轮数达到上限"
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
                requestMessages.add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", callId)
                    addProperty("name", functionName)
                    addProperty("content", result.toString())
                })
            }
        }
        error("unreachable")
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
        params: AiTextParams
    ): JsonObject {
        val reasoningOptions = AiModelRegistry.capabilities(model.id).reasoning
        return JsonObject().apply {
            addProperty("model", model.id)
            add("messages", JsonArray().apply {
                messages.forEach { add(it) }
            })
            params.temperature?.let { addProperty("temperature", it) }
            params.maxTokens?.let { addProperty("max_tokens", it) }
            addProperty("stream", false)
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

    private data class McpChatTool(
        val name: String,
        val modelName: String,
        val description: String,
        val inputSchema: JsonObject
    )

    companion object {
        private const val MCP_TOOL_PREFIX = "mcp__legado__"
        private const val MAX_TOOL_ROUNDS = 4
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

data class AiChatUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)
