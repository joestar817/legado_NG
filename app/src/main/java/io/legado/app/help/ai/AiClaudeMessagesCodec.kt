package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import java.io.BufferedReader
import java.security.MessageDigest

internal class AiClaudeMessagesCodec(
    private val setting: AiProviderSetting,
    private val modelId: String,
    modelInfo: AiModelInfo? = null,
    private val deepSeekWeb: Boolean = false,
    private val nativeTools: List<JsonObject> = emptyList(),
    private val webEvents: AiNativeWebEvents? = null,
) : AiChatCodec {
    private val endpoint = buildAiApiEndpoint(setting.baseUrl, "/messages")
    private val history = AiNativeChatHistory(setting, modelId, endpoint)
    private val nativeReasoning = claudeNativeReasoning(modelId, modelInfo)
    private val fixedSampling = FIXED_SAMPLING.containsMatchIn(modelId)
    private val preservedThinking = PRESERVED_THINKING.containsMatchIn(modelId)
    private var requestPrefix: String? = null

    override fun request(
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        stream: Boolean,
        conversationId: String?,
    ): Request {
        val maxTokens = params.maxTokens ?: CLAUDE_DEFAULT_MAX_TOKENS
        val (system, turns) = messages.partition { it.stringOrNull("role") == "system" }
        val systemText = system.joinToString("\n") { it.get("content").contentText() }
        val definitions = JsonArray().apply {
            tools.forEach { definition ->
                val function = requireNotNull(definition.objectOrNull("function"))
                add(JsonObject().apply {
                    addProperty("name", function.stringOrNull("name"))
                    function.stringOrNull("description")?.let { addProperty("description", it) }
                    add("input_schema", function.get("parameters"))
                })
            }
            nativeTools.forEach { add(it.deepCopy()) }
        }
        val prefixHash = if (preservedThinking) PrefixHash(systemText, definitions) else null
        val wireMessages = buildMessages(turns, tools.isNotEmpty(), prefixHash)
        requestPrefix = prefixHash?.at(wireMessages)
        val body = JsonObject().apply {
            addProperty("model", modelId)
            addProperty("max_tokens", maxTokens)
            addProperty("stream", stream)
            addThinking(params, maxTokens)
            if (!fixedSampling && !params.enableThinking) {
                params.temperature?.let { addProperty("temperature", it.coerceIn(0f, 1f)) }
            }
            if (systemText.isNotBlank()) addProperty("system", systemText)
            add("messages", wireMessages)
            if (!definitions.isEmpty) add("tools", definitions)
        }
        return Request.Builder()
            .url(endpoint)
            .addHeader("x-api-key", setting.apiKeyForRequest())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .apply {
                if (endpoint.startsWith("https://opencode.ai/")) addOpenCodeProviderHeaders(setting, conversationId)
            }
            .post(jsonBody(body))
            .build()
    }

    private fun JsonObject.addThinking(params: AiTextParams, maxTokens: Int) {
        val reasoning = nativeReasoning ?: return
        when {
            params.disableThinking -> {
                if (reasoning.canDisable) {
                    add("thinking", JsonObject().apply { addProperty("type", "disabled") })
                }
                return
            }
            !params.enableThinking -> return
            reasoning.adaptive -> {
                add("thinking", JsonObject().apply {
                    addProperty("type", "adaptive")
                    addProperty("display", "summarized")
                })
            }
            reasoning.budgetRange != null && maxTokens > reasoning.budgetRange.first -> {
                val budget = (params.assistantReasoningMaxTokens ?: 4096)
                    .coerceIn(reasoning.budgetRange.first, maxTokens - 1)
                add("thinking", JsonObject().apply {
                    addProperty("type", "enabled")
                    addProperty("budget_tokens", budget)
                })
            }
            else -> return
        }
        params.reasoningEffort?.takeIf(reasoning.efforts::contains)?.let { effort ->
            add("output_config", JsonObject().apply { addProperty("effort", effort) })
        }
    }

    private fun buildMessages(
        turns: List<JsonObject>,
        replayNative: Boolean,
        prefix: PrefixHash?,
    ) = JsonArray().apply {
        val pendingToolIds = mutableSetOf<String>()
        turns.forEachIndexed { index, message ->
            val content = message.get("content").contentText()
            when (message.stringOrNull("role")) {
                "user" -> {
                    pendingToolIds.clear()
                    val parts = buildList {
                        if (content.isNotBlank()) add(textBlock(content))
                        message.chatVisionInputs().forEach { image ->
                            add(JsonObject().apply {
                                addProperty("type", "image")
                                add("source", JsonObject().apply {
                                    addProperty("type", "base64")
                                    addProperty("media_type", image.mime)
                                    addProperty("data", image.base64)
                                })
                            })
                        }
                    }
                    if (parts.isNotEmpty()) addNativeParts("user", "content", parts)
                }
                "assistant" -> {
                    pendingToolIds.clear()
                    val native = if (replayNative || deepSeekWeb) history.parts(message)?.objectParts() else null
                    val toolIds = native.orEmpty().filter { it.stringOrNull("type") == "tool_use" }
                        .map { it.stringOrNull("id") }
                    val canReplay = native != null &&
                        (toolIds.isEmpty() || replayNative && hasToolResults(turns, index, toolIds))
                    val blocks = if (canReplay) {
                        pendingToolIds.addAll(toolIds.filterNotNull())
                        val parts = requireNotNull(native)
                        val containsThinking = parts.any(::isThinkingBlock)
                        val canReplayThinking = prefix == null || !containsThinking ||
                            (content == parts.claudeContent("text", "text") &&
                                message.objectOrNull(AI_NATIVE_HISTORY)?.stringOrNull(PREFIX_KEY) == prefix.at(this))
                        val replayParts = if (canReplayThinking) parts else parts.filterNot(::isThinkingBlock)
                        replayParts.replayWithText(
                            content,
                            { it.stringOrNull("type") == "text" },
                            ::textBlock,
                        )
                    } else if (deepSeekWeb && native != null) {
                        native.filter { it.stringOrNull("type") in WEB_BLOCK_TYPES } +
                            listOfNotNull(content.takeIf(String::isNotBlank)?.let(::textBlock))
                    } else {
                        listOfNotNull(fallbackAssistantText(message, content).takeIf(String::isNotBlank)?.let(::textBlock))
                    }
                    addNativeParts("assistant", "content", blocks)
                }
                "tool" -> {
                    val id = message.stringOrNull("tool_call_id")
                    val block = if (id != null && pendingToolIds.remove(id)) {
                        JsonObject().apply {
                            addProperty("type", "tool_result")
                            addProperty("tool_use_id", id)
                            addProperty("content", content)
                        }
                    } else {
                        textBlock("工具 ${message.stringOrNull("name").orEmpty()} 的历史结果：\n$content")
                    }
                    addNativeParts("user", "content", listOf(block))
                }
            }
        }
    }

    private fun hasToolResults(turns: List<JsonObject>, assistantIndex: Int, toolIds: List<String?>): Boolean {
        val resultIds = turns.subList(assistantIndex + 1, turns.size).asSequence()
            .takeWhile { it.stringOrNull("role") == "tool" }
            .map { it.stringOrNull("tool_call_id") }
            .toList()
        return toolIds.none { it.isNullOrBlank() } && toolIds.distinct().size == toolIds.size &&
            resultIds.size == toolIds.size && resultIds.toSet() == toolIds.toSet()
    }

    private fun fallbackAssistantText(message: JsonObject, content: String): String = buildString {
        append(content)
        message.arrayOrNull("tool_calls")?.objectParts()?.forEach { call ->
            val function = call.objectOrNull("function") ?: return@forEach
            if (isNotEmpty()) append('\n')
            append("历史工具调用 ")
            append(function.stringOrNull("name").orEmpty())
            append("：")
            append(function.stringOrNull("arguments").orEmpty())
        }
    }

    override fun readJson(json: JsonObject): AiChatReply {
        val blocks = json.arrayOrNull("content") ?: error("Claude 响应缺少内容")
        val finish = json.stringOrNull("stop_reason") ?: error("Claude 响应缺少结束状态")
        if (webEvents != null) blocks.objectParts().forEach { webEvents.messagesBlock(it, streaming = false) }
        return reply(blocks, json.stringOrNull("model"), finish, json.objectOrNull("usage"))
    }

    override fun readStream(reader: BufferedReader, onUpdate: (String, String?) -> Unit): AiChatReply {
        val blocks = sortedMapOf<Int, JsonObject>()
        val openBlocks = mutableSetOf<Int>()
        val arguments = mutableMapOf<Int, StringBuilder>()
        val usage = JsonObject()
        var model: String? = null
        var finish: String? = null
        var stopped = false
        reader.readAiSseFrames { event, data ->
            val frame = JsonParser.parseString(data).asJsonObject
            frame.requireNoAiError(event)
            when (frame.stringOrNull("type") ?: event) {
                "message_start" -> frame.objectOrNull("message")?.let { message ->
                    model = message.stringOrNull("model")
                    message.objectOrNull("usage")?.entrySet()?.forEach { usage.add(it.key, it.value) }
                }
                "content_block_start" -> {
                    val index = requireNotNull(frame.intOrNull("index"))
                    val block = requireNotNull(frame.objectOrNull("content_block"))
                    check(index !in blocks) { "Claude 流式内容块重复" }
                    blocks[index] = block.deepCopy()
                    openBlocks.add(index)
                    webEvents?.messagesBlock(block, streaming = true)
                }
                "content_block_delta" -> {
                    val index = requireNotNull(frame.intOrNull("index"))
                    check(index in openBlocks) { "Claude 流式内容块缺少开始事件" }
                    val block = blocks.getValue(index)
                    val delta = requireNotNull(frame.objectOrNull("delta"))
                    when (delta.stringOrNull("type")) {
                        "text_delta" -> block.appendField("text", delta)
                        "thinking_delta" -> block.appendField("thinking", delta)
                        "signature_delta" -> block.appendField("signature", delta)
                        "citations_delta" -> if (deepSeekWeb) delta.objectOrNull("citation")?.let { citation ->
                            val citations = block.arrayOrNull("citations") ?: JsonArray().also { block.add("citations", it) }
                            citations.add(citation.deepCopy())
                            webEvents?.messagesCitation(citation)
                        }
                        "input_json_delta" -> delta.stringOrNull("partial_json")?.takeIf(String::isNotEmpty)?.let {
                            arguments.getOrPut(index, ::StringBuilder).append(it)
                        }
                    }
                    if (delta.has("text") || delta.has("thinking")) {
                        onUpdate(
                            blocks.values.claudeContent("text", "text"),
                            blocks.values.claudeContent("thinking", "thinking").takeIf(String::isNotBlank),
                        )
                    }
                }
                "content_block_stop" -> {
                    val index = requireNotNull(frame.intOrNull("index"))
                    check(openBlocks.remove(index)) { "Claude 流式内容块缺少开始事件" }
                    arguments.remove(index)?.let { fragments ->
                        val input = JsonParser.parseString(fragments.toString())
                        check(input.isJsonObject) { "Claude 工具参数不是 JSON 对象" }
                        blocks.getValue(index).add("input", input)
                    }
                }
                "message_delta" -> {
                    finish = frame.objectOrNull("delta")?.stringOrNull("stop_reason") ?: finish
                    frame.objectOrNull("usage")?.entrySet()?.forEach { usage.add(it.key, it.value) }
                }
                "message_stop" -> stopped = true
            }
            stopped
        }
        if (!stopped || openBlocks.isNotEmpty()) {
            throw AiChatIncompleteStreamException("Claude 流式响应提前结束")
        }
        return reply(
            JsonArray().apply { blocks.values.forEach { add(it) } },
            model,
            finish ?: throw AiChatIncompleteStreamException("Claude 流式响应缺少结束状态"),
            usage,
        )
    }

    private fun reply(parts: JsonArray, model: String?, finish: String, usage: JsonObject?): AiChatReply {
        val blocks = parts.objectParts()
        val calls = JsonArray().apply {
            blocks.filter { it.stringOrNull("type") == "tool_use" }.forEach { tool ->
                val id = tool.stringOrNull("id")?.takeIf(String::isNotBlank) ?: error("Claude 工具调用缺少 ID")
                val name = tool.stringOrNull("name")?.takeIf(String::isNotBlank) ?: error("Claude 工具调用缺少名称")
                add(aiFunctionCall(id, name, tool.get("input").aiJsonObjectArgument().toString()))
            }
        }
        val input = usage?.intOrNull("input_tokens")?.let { count ->
            count + (usage.intOrNull("cache_creation_input_tokens") ?: 0) +
                (usage.intOrNull("cache_read_input_tokens") ?: 0)
        }
        val output = usage?.intOrNull("output_tokens")
        val total = if (input != null && output != null) input + output else null
        val tokens = if (input == null && output == null) null else AiChatUsage(input, output, total)
        val reason = when (finish) {
            "max_tokens", "model_context_window_exceeded" -> "length"
            "tool_use" -> "tool_calls"
            "end_turn", "stop_sequence" -> "stop"
            "pause_turn" -> "incomplete"
            else -> finish
        }
        val reply = history.reply(
            parts,
            blocks.claudeContent("text", "text"),
            blocks.claudeContent("thinking", "thinking"),
            calls,
            model,
            reason,
            tokens,
        )
        requestPrefix?.takeIf { blocks.any(::isThinkingBlock) }?.let { prefix ->
            reply.message.objectOrNull(AI_NATIVE_HISTORY)?.addProperty(PREFIX_KEY, prefix)
        }
        return reply
    }

    private fun isThinkingBlock(part: JsonObject): Boolean = when (part.stringOrNull("type")) {
        "thinking", "redacted_thinking" -> true
        else -> false
    }

    private fun JsonObject.appendField(field: String, delta: JsonObject) {
        addProperty(field, stringOrNull(field).orEmpty() + delta.stringOrNull(field).orEmpty())
    }

    private fun Iterable<JsonObject>.claudeContent(type: String, key: String) =
        filter { it.stringOrNull("type") == type }.joinToString("") { it.stringOrNull(key).orEmpty() }

    private fun textBlock(text: String) = JsonObject().apply {
        addProperty("type", "text")
        addProperty("text", text)
    }

    private class PrefixHash(system: String, tools: JsonArray) {
        private var previous = digest(JsonArray().apply {
            add(system)
            add(tools)
        }.toString().toByteArray())
        private var length = 0

        fun at(messages: JsonArray): String {
            // Adjacent roles can still append to the last wire message. Commit it only once it stops being the tail.
            while (length < messages.size() - 1) {
                previous = digest(previous + messages[length++].toString().toByteArray())
            }
            val hash = if (messages.size() == 0) previous
                else digest(previous + messages[messages.size() - 1].toString().toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        }

        private fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
    }

    private companion object {
        const val PREFIX_KEY = "claude_prefix"
        val WEB_BLOCK_TYPES = setOf("server_tool_use", "web_search_tool_result", "web_fetch_tool_result")
        val FIXED_SAMPLING =
            Regex("claude-(?:opus-4-[7-9](?:-|$)|(?:opus|sonnet|haiku|fable|mythos)-[5-9](?:-|$)|mythos-preview)")
        val PRESERVED_THINKING = Regex("claude-(?:(?:fable|mythos)-5-[12](?:-|$)|(?:opus|sonnet|haiku)-5-5(?:-|$))")
    }
}
