package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
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
        onStreamUpdate: (AiChatStreamUpdate) -> Unit = {},
        onToolConfirmationRequired: suspend (List<AiPendingToolCall>) -> Boolean = { false }
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
        val memoryTrace = mutableListOf<AiMemoryTraceItem>()
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
                    memoryTrace = memoryTrace.toList(),
                    warnings = warnings
                )
            }
            requestMessages.add(message.deepCopy().asJsonObject.apply {
                addProperty("role", "assistant")
                if (!has("content") || get("content").isJsonNull) {
                    addProperty("content", "")
                }
            })
            val parsedToolCalls = toolCalls.mapNotNull { call ->
                val callObject = call.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val callId = callObject.stringOrNull("id").orEmpty()
                val function = callObject.objectOrNull("function") ?: return@mapNotNull null
                val functionName = function.stringOrNull("name").orEmpty()
                val toolName = functionName.removePrefix(MCP_TOOL_PREFIX)
                val arguments = function.stringOrNull("arguments").orEmpty().parseObjectOrEmpty()
                ParsedToolCall(
                    callId = callId,
                    functionName = functionName,
                    toolName = toolName,
                    arguments = arguments
                )
            }
            val writeToolCalls = if (AiConfig.operationPermissionMode.requiresWriteConfirmation) {
                parsedToolCalls.filter { it.toolName.isWriteTool(it.arguments) }
            } else {
                emptyList()
            }
            val writeToolCallIds = writeToolCalls.mapTo(mutableSetOf()) { it.callId }
            val writeApproved = if (writeToolCalls.isNotEmpty()) {
                onToolConfirmationRequired(writeToolCalls.map { it.toPendingToolCall() })
            } else {
                true
            }
            val characterMemoryContexts = mutableListOf<CharacterApplyMemoryContext>()
            parsedToolCalls.forEach { call ->
                val result = if (!writeApproved && call.callId in writeToolCallIds) {
                    writeOperationCanceledResult(call.toolName)
                } else {
                    runCatching {
                        McpInternalChannel.callTool(call.toolName, call.arguments)
                    }.getOrElse {
                        JsonObject().apply {
                            addProperty("ok", false)
                            addProperty("error", it.localizedMessage ?: "MCP tool failed")
                        }
                    }
                }
                toolTrace.add("${call.toolName}(${call.arguments.toString().take(120)})")
                onStreamUpdate(
                    AiChatStreamUpdate(
                        content = content,
                        reasoning = lastReasoning,
                        toolTrace = toolTrace.toList(),
                        memoryTrace = memoryTrace.toList()
                    )
                )
                result.toCharacterApplyMemoryContext(call.toolName)
                    ?.let { characterMemoryContexts += it }
                requestMessages.add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", call.callId)
                    addProperty("name", call.functionName)
                    addProperty("content", result.toModelToolContent(call.toolName))
                })
            }
            maybeRunCharacterMemoryHook(
                contexts = characterMemoryContexts,
                messages = requestMessages,
                setting = setting,
                model = model,
                client = client,
                memoryTrace = memoryTrace,
                onStreamUpdate = {
                    onStreamUpdate(
                        AiChatStreamUpdate(
                            content = content,
                            reasoning = lastReasoning,
                            toolTrace = toolTrace.toList(),
                            memoryTrace = memoryTrace.toList()
                        )
                    )
                }
            )
        }
    }

    fun newSystemMessage(): JsonObject {
        return JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", AGENT_SYSTEM_PROMPT)
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

    private fun String.isWriteTool(arguments: JsonObject): Boolean {
        if (this in WRITE_CONFIRMATION_REQUIRED_TOOLS) {
            return true
        }
        if (WRITE_CONFIRMATION_REQUIRED_SUFFIXES.any { suffix -> endsWith(suffix) }) {
            return true
        }
        return arguments.booleanOrNull("write") == true ||
            arguments.booleanOrNull("overwrite") == true
    }

    private fun writeOperationCanceledResult(toolName: String): JsonObject {
        return JsonObject().apply {
            addProperty("ok", false)
            addProperty("canceled", true)
            addProperty("error", "用户取消写操作")
            addProperty("tool", toolName)
            addProperty(
                "message",
                "当前写操作未执行。请停止写入流程，并向用户说明操作已取消。"
            )
        }
    }

    private fun JsonObject.toModelToolContent(toolName: String): String {
        val content = toString()
        if (content.length <= MAX_MODEL_TOOL_RESULT_CHARS) {
            return content
        }
        return JsonObject().apply {
            addProperty("ok", false)
            addProperty("tool", toolName)
            addProperty("truncated_by_app", true)
            addProperty("original_chars", content.length)
            addProperty("preview_chars", MAX_MODEL_TOOL_RESULT_CHARS)
            addProperty(
                "message",
                "工具结果过大，App 已截断以避免超过模型上下文。请用 offset/limit/start/end/keyword/include_detail=false 等参数分页或缩小范围后重试。"
            )
            addProperty("preview", content.take(MAX_MODEL_TOOL_RESULT_CHARS))
        }.toString()
    }

    private suspend fun maybeRunCharacterMemoryHook(
        contexts: List<CharacterApplyMemoryContext>,
        messages: List<JsonObject>,
        setting: AiProviderSetting,
        model: AiModel,
        client: OkHttpClient,
        memoryTrace: MutableList<AiMemoryTraceItem>,
        onStreamUpdate: () -> Unit
    ) {
        if (!AiConfig.memoryEnabled) return
        val mergedContext = contexts.mergeCharacterMemoryContext() ?: return
        val context = mergedContext.copy(
            scanData = messages.latestCharacterScanData(mergedContext.workKey)
        )
        memoryTrace += AiMemoryTraceItem(
            status = AiMemoryTraceItem.STATUS_RUNNING,
            title = "正在生成记忆...",
            detail = "角色卡已写入，正在总结本次操作"
        )
        onStreamUpdate()
        val traceIndex = memoryTrace.lastIndex
        val finalTrace = runCatching {
            val generated = generateCharacterApplyMemory(
                context = context,
                setting = setting,
                model = model,
                client = client
            )
            val arguments = generated.toMemoryUpsertArguments(context)
            val writeResult = McpInternalChannel.callTool("agent_memory_upsert", arguments)
            check(writeResult.structuredContentOk()) { "记忆写入失败" }
            AiMemoryTraceItem(
                status = AiMemoryTraceItem.STATUS_SAVED,
                title = "已保存记忆",
                detail = generated.title
            )
        }.getOrElse {
            AiMemoryTraceItem(
                status = AiMemoryTraceItem.STATUS_FAILED,
                title = "记忆保存失败",
                detail = it.localizedMessage ?: it.toString()
            )
        }
        if (traceIndex in memoryTrace.indices) {
            memoryTrace[traceIndex] = finalTrace
        } else {
            memoryTrace += finalTrace
        }
        onStreamUpdate()
    }

    private suspend fun generateCharacterApplyMemory(
        context: CharacterApplyMemoryContext,
        setting: AiProviderSetting,
        model: AiModel,
        client: OkHttpClient
    ): GeneratedMemory {
        val memoryMessages = listOf(
            JsonObject().apply {
                addProperty("role", "system")
                addProperty(
                    "content",
                    """
                    你是阅读 App 的 AI 记忆总结器。
                    只根据用户已经确认并已成功执行的操作生成一条短记忆。
                    只返回 JSON，不要 Markdown，不要解释。
                    JSON 字段：
                    - title：20 字以内短标题
                    - content：120 字以内，说明对象、已完成结果、后续有用线索
                    - tags：字符串数组，最多 5 个
                    - confidence：0 到 1，已确认成功操作一般为 1
                    不要保存章节原文、长日志、隐私、API Key、Cookie 或完整业务数据。
                    """.trimIndent()
                )
            },
            JsonObject().apply {
                addProperty("role", "user")
                addProperty(
                    "content",
                    """
                    角色卡写入已成功，请为后续 AI 助理生成一条记忆。

                    书籍对象：${context.subject}
                    work_key：${context.workKey}
                    已写入角色数量：${context.updatedCount}
                    已写入角色：${context.characterNames.joinToString("、").ifBlank { "未提供名称" }}
                    扫描元数据：${context.scanData?.toString() ?: "未提供"}

                    返回 JSON：
                    {"title":"","content":"","tags":[],"confidence":1}
                    """.trimIndent()
                )
            }
        )
        val body = buildRequestBody(
            model = model,
            messages = memoryMessages,
            tools = emptyList(),
            params = AiTextParams(temperature = 0f, disableThinking = true),
            stream = false
        )
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
        val completion = client.executeJsonChat(request, reasoningKey)
        val json = completion.content.extractJsonObject()
        return GeneratedMemory(
            title = json.stringOrNull("title")
                ?.trim()
                ?.take(40)
                ?.takeIf { it.isNotBlank() }
                ?: "角色卡已写入",
            content = json.stringOrNull("content")
                ?.trim()
                ?.take(240)
                ?.takeIf { it.isNotBlank() }
                ?: "已为${context.subject}写入 ${context.updatedCount} 张角色卡。",
            tags = json.arrayOrNull("tags")
                ?.mapNotNull { it.takeIf { element -> element.isJsonPrimitive }?.asString?.trim() }
                ?.filter { it.isNotBlank() }
                ?.take(5)
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("角色卡", "已写入"),
            confidence = json.get("confidence")
                ?.takeIf { it.isJsonPrimitive }
                ?.asFloat
                ?.coerceIn(0f, 1f)
                ?: 1f
        )
    }

    private fun GeneratedMemory.toMemoryUpsertArguments(context: CharacterApplyMemoryContext): JsonObject {
        return JsonObject().apply {
            addProperty("scope_type", "book")
            addProperty("scope_key", context.workKey)
            addProperty("subject", context.subject)
            addProperty("domain", "character_card")
            addProperty("memory_type", "checkpoint")
            addProperty("title", title)
            addProperty("content", content)
            add("tags", JsonArray().apply {
                tags.forEach { add(it) }
            })
            addProperty("confidence", confidence)
            addProperty("source", "post_action_hook")
            add("data", JsonObject().apply {
                context.scanData?.entrySet()?.forEach { (key, value) ->
                    add(key, value.deepCopy())
                }
                addProperty("source_tool", "character_write_hook")
                addProperty("updated_count", context.updatedCount)
                add("character_names", JsonArray().apply {
                    context.characterNames.forEach { add(it) }
                })
            })
        }
    }

    private fun JsonObject.toCharacterApplyMemoryContext(toolName: String): CharacterApplyMemoryContext? {
        if (toolName !in CHARACTER_MEMORY_HOOK_TOOLS) return null
        if (!structuredContentOk()) return null
        val data = objectOrNull("structuredContent")
            ?.objectOrNull("normalized_data")
            ?: return null
        val characters = buildList {
            data.arrayOrNull("updated")?.forEach { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let { add(it) }
            }
            data.objectOrNull("character")?.let { add(it) }
        }
        if (characters.isEmpty()) return null
        val workKey = data.stringOrNull("work_key")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: characters.firstNotNullOfOrNull { it.stringOrNull("work_key")?.trim() }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val names = characters.mapNotNull { it.stringOrNull("name")?.trim()?.takeIf { name -> name.isNotBlank() } }
        val updatedCount = data.get("updated_count")
            ?.takeIf { it.isJsonPrimitive }
            ?.asInt
            ?: characters.size
        return CharacterApplyMemoryContext(
            workKey = workKey,
            subject = workKey.toMemorySubject(),
            updatedCount = updatedCount,
            characterNames = names.distinct().take(20)
        )
    }

    private fun List<JsonObject>.latestCharacterScanData(workKey: String): JsonObject? {
        asReversed().forEach { message ->
            if (message.stringOrNull("role") != "assistant") return@forEach
            val content = message.stringOrNull("content").orEmpty()
            val matches = CHARACTER_SCAN_META_BLOCK_REGEX.findAll(content).toList()
            matches.asReversed().forEach { match ->
                val meta = runCatching {
                    JsonParser.parseString(match.groupValues[1].trim())
                        .takeIf { it.isJsonObject }
                        ?.asJsonObject
                }.getOrNull() ?: return@forEach
                val metaWorkKey = meta.stringOrNull("work_key")
                    ?: meta.stringOrNull("workKey")
                    ?: meta.objectOrNull("book")?.stringOrNull("work_key")
                    ?: meta.objectOrNull("book")?.stringOrNull("workKey")
                if (metaWorkKey.isNullOrBlank() || metaWorkKey == workKey) {
                    return meta
                }
            }
        }
        return null
    }

    private fun List<CharacterApplyMemoryContext>.mergeCharacterMemoryContext(): CharacterApplyMemoryContext? {
        if (isEmpty()) return null
        val grouped = groupBy { it.workKey }
        val (_, contexts) = grouped.maxByOrNull { (_, items) -> items.sumOf { it.updatedCount } } ?: return null
        val first = contexts.first()
        val names = contexts.flatMap { it.characterNames }
            .distinct()
            .take(20)
        return CharacterApplyMemoryContext(
            workKey = first.workKey,
            subject = first.subject,
            updatedCount = names.size.takeIf { it > 0 } ?: contexts.sumOf { it.updatedCount },
            characterNames = names
        )
    }

    private fun JsonObject.structuredContentOk(): Boolean {
        return objectOrNull("structuredContent")
            ?.get("ok")
            ?.takeIf { it.isJsonPrimitive }
            ?.asBoolean == true
    }

    private fun String.extractJsonObject(): JsonObject {
        val trimmed = trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching {
            JsonParser.parseString(trimmed).asJsonObject
        }.getOrElse {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JsonParser.parseString(trimmed.substring(start, end + 1)).asJsonObject
            } else {
                throw it
            }
        }
    }

    private fun String.toMemorySubject(): String {
        val parts = replace("\\n", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "《${parts[0]}》${parts[1]}"
            parts.size == 1 -> parts[0]
            else -> this
        }
    }

    private fun JsonObject.toChatUsage(): AiChatUsage {
        return AiChatUsage(
            promptTokens = intOrNull("prompt_tokens"),
            completionTokens = intOrNull("completion_tokens"),
            totalTokens = intOrNull("total_tokens")
        )
    }

    private fun JsonObject.booleanOrNull(name: String): Boolean? {
        return runCatching {
            get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
        }.getOrNull()
    }

    private data class McpChatTool(
        val name: String,
        val modelName: String,
        val description: String,
        val inputSchema: JsonObject
    )

    private data class ParsedToolCall(
        val callId: String,
        val functionName: String,
        val toolName: String,
        val arguments: JsonObject
    ) {
        fun toPendingToolCall(): AiPendingToolCall {
            return AiPendingToolCall(
                toolName = toolName,
                functionName = functionName,
                callId = callId,
                arguments = arguments.deepCopy()
            )
        }
    }

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
        private const val MAX_MODEL_TOOL_RESULT_CHARS = 120_000
        private val CHARACTER_SCAN_META_BLOCK_REGEX = Regex(
            pattern = """(?s)```[ \t]*(?:legado-character-scan|character_scan_meta)[^\r\n]*\r?\n(.*?)\r?\n```"""
        )
        private val CHARACTER_MEMORY_HOOK_TOOLS = setOf(
            "bookshelf_character_upsert"
        )
        private val AGENT_SYSTEM_PROMPT = """
            你是 Legado / 阅读NG 内置 AI 助手。回答要简洁直接。

            这是 Agent 聊天专用系统提示词，只约束普通 AI 助手聊天；不要把这些规则用于 App 内部的段落净化、章节净化或其它结构化模型调用。

            MCP 使用：
            - 若用户的问题需要读取或管理 Legado 数据，并且可用 MCP 工具能完成，应优先调用 MCP 工具。
            - 不要编造工具名、参数或工具结果；工具不可用时说明限制。
            - 写入、删除、覆盖、启用、禁用、应用、回滚都属于写操作。
            - 书籍作品身份优先使用 work_key 或 name+author；book_url 是当前书源实例地址，换源后可能变化，不要把它当作跨源稳定身份。

            写操作权限：
            - App 会按当前权限模式控制写工具执行：写操作确认模式会在真实执行前弹出本地确认窗；完全信任模式会直接执行。
            - 不要根据“ok、确认、继续”等自然语言自行判断本地权限；本地权限只由 App 控制。
            - 大批量、破坏性、覆盖性、AI 生成内容应用等操作，仍应先在聊天中展示可审核内容，再等待用户明确要求执行。
            - 当你决定执行写操作时，直接调用对应写工具；如果 App 弹窗被用户取消，工具结果会返回 canceled=true，此时停止写入流程并说明已取消。
            - 写工具成功后说明写入结果；不要声称未执行的操作已经完成。

            交互协议：
            - 需要用户选择或确认时，可在正文后输出一个 ```legado-interaction 代码块。
            - 支持 type：actions、single_choice、multi_choice、confirm。
            - 代码块必须完整闭合，必须是合法 JSON，id 稳定简短，按钮 label 要短。
            - 交互块不能替代正文说明；正文先说明依据、结果和风险。
            - 如果正文说“请选择”“请确认”“点击按钮”，必须紧跟一个完整的 legado-interaction 代码块；不要只写提示语。
            - 领域 Skill 可能要求输出其它 App 元数据代码块，例如 ```character_scan_meta。按 Skill 要求输出即可；这类元数据会被 App 隐藏，不要在正文里解释其内部字段。
            - single_choice 示例：
            ```legado-interaction
            {
              "version": 1,
              "id": "sampling_mode",
              "type": "single_choice",
              "title": "选择采样强度",
              "description": "请选择本次处理强度。",
              "options": [
                {"label": "平衡", "value": "balanced"},
                {"label": "快速", "value": "fast"},
                {"label": "深入", "value": "deep"}
              ],
              "submit": {
                "label": "开始",
                "prompt_template": "使用{{label}}模式继续"
              }
            }
            ```
            - confirm 示例：
            ```legado-interaction
            {
              "version": 1,
              "id": "apply_confirm",
              "type": "confirm",
              "title": "确认应用",
              "description": "确认后将执行上面说明的写入操作。",
              "submit": {
                "label": "应用",
                "prompt_template": "确认应用上面的内容"
              },
              "cancel": {
                "label": "取消",
                "prompt_template": "暂不应用"
              }
            }
            ```

            记忆系统：
            - 记忆属于 AI 助手功能，不属于某个 Skill。
            - 当前不要为了普通业务流程强制保存记忆。
            - 用户明确要求查询历史上下文，或后续 App 通过 hook 要求处理记忆时，先调用 agent_memory_status_get 检查开关。
            - 如果 enabled=false，不要调用任何记忆检索或写入工具。
            - 如果 enabled=true，并且任务有明确对象，先用 agent_memory_search 检索该对象相关记忆。scope_key 优先使用稳定自然键，例如书名+作者、书源名称+关键地址；不要只依赖易变化的 bookUrl。
            - 记忆写入主要由 App 在已确认业务操作成功后的 hook 触发；普通分析、预览、失败操作或用户未确认前不要保存记忆。
            - 不要把角色卡、书籍、书源、规则等业务写入工具当作记忆工具；只有 agent_memory_upsert 才代表记忆已保存。
            - 保存内容要短，记录对象、业务域、已应用结果、采样范围或后续建议，避免存入整段原文。
        """.trimIndent()

        private val WRITE_CONFIRMATION_REQUIRED_TOOLS = setOf(
            "bookshelf_character_upsert",
            "bookshelf_character_delete",
            "bookshelf_character_set_enabled",
            "book_source_save",
            "book_source_delete",
            "book_source_set_enabled",
            "bookshelf_book_upsert",
            "bookshelf_book_delete",
            "bookshelf_book_group_update",
            "bookshelf_cache_download",
            "bookshelf_cache_clear",
            "bookshelf_group_upsert",
            "bookshelf_group_delete",
            "replace_rule_upsert",
            "replace_rule_delete",
            "replace_rule_set_enabled",
            "agent_memory_upsert",
            "agent_memory_archive",
            "network_log_clear",
            "debug_log_clear"
        )

        private val WRITE_CONFIRMATION_REQUIRED_SUFFIXES = listOf(
            "_upsert",
            "_delete",
            "_set_enabled",
            "_apply",
            "_rollback",
            "_save",
            "_archive",
            "_clear",
            "_group_update"
        )

    }
}

data class AiPendingToolCall(
    val toolName: String,
    val functionName: String,
    val callId: String,
    val arguments: JsonObject
)

data class AiChatTurnResult(
    val content: String,
    val reasoning: String?,
    val model: String,
    val finishReason: String?,
    val usage: AiChatUsage?,
    val toolTrace: List<String>,
    val memoryTrace: List<AiMemoryTraceItem> = emptyList(),
    val warnings: List<String>
)

data class AiChatStreamUpdate(
    val content: String,
    val reasoning: String?,
    val toolTrace: List<String> = emptyList(),
    val memoryTrace: List<AiMemoryTraceItem> = emptyList()
)

data class AiMemoryTraceItem(
    @SerializedName("status")
    val status: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("detail")
    val detail: String? = null
) {
    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_SAVED = "saved"
        const val STATUS_FAILED = "failed"
    }
}

data class AiChatUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

private data class CharacterApplyMemoryContext(
    val workKey: String,
    val subject: String,
    val updatedCount: Int,
    val characterNames: List<String>,
    val scanData: JsonObject? = null
)

private data class GeneratedMemory(
    val title: String,
    val content: String,
    val tags: List<String>,
    val confidence: Float
)
