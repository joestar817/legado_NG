package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.BufferedReader

internal class AiOpenAiResponsesCodec(
    private val setting: AiProviderSetting,
    private val modelId: String,
    endpoint: String,
    private val deepSeekWeb: Boolean = false,
    private val nativeTools: List<JsonObject> = emptyList(),
    private val webEvents: AiNativeWebEvents? = null,
    private val chatGptAccessToken: String? = null,
) : AiChatCodec {
    private val codex = setting.isCodexSubscription()
    private val subscription = setting.isOpenAiAccountProvider()
    private val url = when {
        codex -> "$CODEX_API/responses"
        subscription -> "$CHATGPT_API/responses"
        else -> buildAiApiEndpoint(setting.baseUrl, endpoint)
    }.toHttpUrl()
    private val chatGptWeb = subscription && nativeTools.any { it.stringOrNull("type") == "web_search" }
    private val history = AiNativeChatHistory(setting, modelId, url.toString())
    private val officialDeepSeek = url.isHttps && url.port == 443 && url.host == "api.deepseek.com"
    private val officialOpenAi = url.isHttps && url.port == 443 && url.host == "api.openai.com"
    private val officialOpenRouter = url.isOpenRouterApi("responses")
    private val openAiReasoningModel = OPENAI_REASONING_MODEL.containsMatchIn(
        modelId.substringAfterLast('/').lowercase()
    )

    override fun request(
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        stream: Boolean,
        conversationId: String?
    ): Request {
        val effort = when {
            params.disableThinking -> "none"
            params.enableThinking -> params.reasoningEffort
            else -> null
        }?.takeIf { value -> !subscription || setting.models.firstOrNull { it.id == modelId }
            ?.modelInfo?.effortLevels?.value?.contains(value) == true }
        val body = JsonObject().apply {
            addProperty("model", modelId)
            addProperty("stream", stream || subscription)
            addProperty("store", false)
            if (codex) {
                val instructions = messages.filter { it.stringOrNull("role") == "system" }
                    .map { it.get("content").contentText() }.filter(String::isNotBlank)
                    .joinToString("\n\n")
                addProperty("instructions", instructions.ifBlank { "You are the Reading NG AI assistant." })
            }
            add("input", inputItems(messages, tools.isNotEmpty()))
            if (!subscription) {
                params.maxTokens?.let { addProperty("max_output_tokens", it) }
                if (officialDeepSeek || !openAiReasoningModel && !params.enableThinking) {
                    params.temperature?.let { addProperty("temperature", it) }
                }
            }
            if (officialOpenRouter && (params.enableThinking || params.disableThinking)) {
                add("reasoning", openRouterReasoningRequest(params))
            } else if (effort != null) {
                add("reasoning", JsonObject().apply { addProperty("effort", effort) })
            }
            val encryptedReasoning = officialOpenAi && openAiReasoningModel
            if (encryptedReasoning || chatGptWeb) {
                add("include", JsonArray().apply {
                    if (encryptedReasoning) add("reasoning.encrypted_content")
                    if (chatGptWeb) add("web_search_call.action.sources")
                })
            }
            if (tools.isNotEmpty() || nativeTools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    if (subscription && tools.isNotEmpty()) {
                        add(JsonObject().apply {
                            addProperty("type", "namespace")
                            addProperty("name", CHATGPT_TOOL_NAMESPACE)
                            addProperty("description", "Tools provided by Reading NG for this conversation")
                            add("tools", JsonArray().apply { tools.forEach { add(functionDefinition(it)) } })
                        })
                    } else tools.forEach { add(functionDefinition(it)) }
                    nativeTools.forEach { add(it.deepCopy()) }
                })
            } else if (params.jsonResponse) {
                add("text", JsonObject().apply {
                    add("format", JsonObject().apply { addProperty("type", "json_object") })
                })
            }
        }
        val token = if (subscription) requireNotNull(chatGptAccessToken) else setting.apiKeyForRequest()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .apply { if (codex) addCodexWorkspace(token) }
            .addOpenCodeProviderHeaders(setting, conversationId)
            .post(jsonBody(body))
            .build()
    }

    private fun functionDefinition(definition: JsonObject): JsonObject {
        require(definition.stringOrNull("type") == "function") {
            "Responses 只接受当前助理的函数工具"
        }
        val function = requireNotNull(definition.objectOrNull("function"))
        return JsonObject().apply {
            addProperty("type", "function")
            addProperty("name", function.stringOrNull("name"))
            function.stringOrNull("description")?.let { addProperty("description", it) }
            add("parameters", function.objectOrNull("parameters") ?: JsonObject())
            addProperty("strict", false)
        }
    }

    private fun inputItems(messages: List<JsonObject>, replayTools: Boolean): JsonArray = JsonArray().apply {
        var index = 0
        while (index < messages.size) {
            val message = messages[index++]
            val role = message.stringOrNull("role")
            val text = message.get("content").contentText()
            when (role) {
                "assistant" -> {
                    val start = index
                    while (index < messages.size && messages[index].stringOrNull("role") == "tool") index++
                    addAssistantHistory(message, text, messages.subList(start, index), replayTools)
                }
                "tool" -> add(textItem("assistant", toolTranscript(message)))
                "user", "system", "developer" -> {
                    val images = if (role == "user") message.chatVisionInputs() else emptyList()
                    if (images.isNotEmpty()) {
                        add(textItem(role, text).apply {
                            add("content", JsonArray().apply {
                                if (text.isNotBlank()) add(JsonObject().apply {
                                    addProperty("type", "input_text")
                                    addProperty("text", text)
                                })
                                images.forEach { image ->
                                    add(JsonObject().apply {
                                        addProperty("type", "input_image")
                                        addProperty("image_url", image.dataUrl)
                                    })
                                }
                            })
                        })
                    } else if (text.isNotBlank() && !(codex && role == "system")) {
                        add(textItem(role, text))
                    }
                }
            }
        }
    }

    private fun JsonArray.addAssistantHistory(
        message: JsonObject,
        text: String,
        results: List<JsonObject>,
        replayTools: Boolean
    ) {
        val calls = message.arrayOrNull("tool_calls").objectParts()
        val ids = calls.map { it.stringOrNull("id") }
        val resultIds = results.map { it.stringOrNull("tool_call_id") }
        val paired = replayTools && calls.isNotEmpty() && ids.none { it.isNullOrBlank() } &&
            ids.toSet().size == calls.size && resultIds.size == calls.size && resultIds.toSet() == ids.toSet()
        val native = history.parts(message).objectParts()
        val nativeCalls = native.filter { it.stringOrNull("type") == "function_call" }
        val nativeMatches = native.isNotEmpty() && native.messageText() == text &&
            nativeCalls.map { it.stringOrNull("call_id") } == ids &&
            (calls.isEmpty() || paired)
        if (nativeMatches) {
            native.forEach { add(it.deepCopy()) }
        } else {
            if (deepSeekWeb) native.filter { it.stringOrNull("type") == "web_search_call" }
                .forEach { add(it.deepCopy()) }
            if (text.isNotBlank()) add(textItem("assistant", text))
            if (paired) calls.forEach { call ->
                val function = requireNotNull(call.objectOrNull("function"))
                add(JsonObject().apply {
                    addProperty("type", "function_call")
                    addProperty("call_id", call.stringOrNull("id"))
                    addProperty("name", function.stringOrNull("name"))
                    if (subscription) addProperty("namespace", CHATGPT_TOOL_NAMESPACE)
                    addProperty("arguments", function.stringOrNull("arguments"))
                })
            }
        }
        results.forEach { result ->
            if (paired) {
                add(JsonObject().apply {
                    addProperty("type", "function_call_output")
                    addProperty("call_id", result.stringOrNull("tool_call_id"))
                    addProperty("output", result.get("content").contentText())
                })
            } else {
                add(textItem("assistant", toolTranscript(result)))
            }
        }
    }

    private fun textItem(role: String, text: String): JsonObject = JsonObject().apply {
        addProperty("type", "message")
        addProperty("role", if (subscription && role == "system") "developer" else role)
        addProperty("content", text)
    }

    private fun toolTranscript(message: JsonObject): String = buildString {
        append("此前工具返回")
        message.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(')') }
        appendLine()
        append(message.get("content").contentText())
    }

    override fun readJson(json: JsonObject): AiChatReply = readResponse(json)

    private fun readResponse(json: JsonObject, terminalStatus: String? = null): AiChatReply {
        if (subscription && (terminalStatus == "failed" || json.objectOrNull("error") != null)) {
            throw chatGptRequestError(null, json)
        }
        json.requireNoAiError()
        val status = when (terminalStatus) {
            "failed", "incomplete", "cancelled" -> terminalStatus
            else -> json.stringOrNull("status") ?: terminalStatus
        }
        val incompleteReason = json.objectOrNull("incomplete_details")?.stringOrNull("reason")
        val finish = when (status) {
            "completed", null -> null
            "incomplete" -> when (incompleteReason) {
                "max_output_tokens" -> "length"
                "content_filter" -> "content_filter"
                else -> "incomplete"
            }
            "failed" -> error("Responses 请求失败")
            "cancelled" -> "cancelled"
            else -> error("Responses 响应尚未完成")
        }
        val outputs = json.arrayOrNull("output")?.objectParts()
            ?: if (finish != null) emptyList() else error("Responses 未返回 output")
        webEvents?.responsesResult(outputs, inline = chatGptWeb)
        val calls = JsonArray()
        val replayItems = JsonArray()
        val callIds = hashSetOf<String>()
        val reasoning = StringBuilder()
        var refused = false
        for (item in outputs) {
            when (val type = item.stringOrNull("type")) {
                "message" -> {
                    val parts = item.arrayOrNull("content").objectParts()
                    refused = refused || parts.any { it.stringOrNull("type") == "refusal" }
                    replayItems.add(item.replayItem())
                }
                "reasoning" -> {
                    val detailed = item.arrayOrNull("content").contentText()
                    reasoning.append(detailed.ifBlank { item.arrayOrNull("summary").contentText() })
                    if (item.stringOrNull("encrypted_content")?.isNotBlank() == true ||
                        officialDeepSeek && detailed.isNotBlank()
                    ) {
                        replayItems.add(item.replayItem())
                    }
                }
                "function_call" -> if (finish == null) {
                    val callId = item.stringOrNull("call_id").orEmpty()
                    val name = item.stringOrNull("name").orEmpty()
                    val namespace = item.stringOrNull("namespace")
                    val arguments = item.stringOrNull("arguments").orEmpty()
                    val callStatus = item.stringOrNull("status")
                    check((callStatus == null || callStatus == "completed") &&
                        callId.isNotBlank() && callIds.add(callId) && name.isNotBlank() && arguments.isNotBlank() &&
                        (!subscription || namespace == null || namespace == CHATGPT_TOOL_NAMESPACE)
                    ) {
                        "Responses 返回了无效的函数调用"
                    }
                    item.get("arguments").aiJsonObjectArgument()
                    calls.add(aiFunctionCall(callId, name, arguments))
                    replayItems.add(item.replayItem())
                }
                "web_search_call" -> {
                    check(deepSeekWeb || chatGptWeb) { "Responses 返回了未支持的网页工具" }
                    if (deepSeekWeb) replayItems.add(item.replayItem())
                }
                else -> error("Responses 返回了不支持的输出项：$type")
            }
        }
        val usage = json.objectOrNull("usage")?.let {
            AiChatUsage(it.intOrNull("input_tokens"), it.intOrNull("output_tokens"), it.intOrNull("total_tokens"))
        }
        return history.reply(
            parts = replayItems,
            text = outputs.messageText(),
            reasoning = reasoning.toString().takeIf { it.isNotBlank() },
            calls = calls,
            model = json.stringOrNull("model"),
            finish = finish ?: when {
                refused -> "refusal"
                calls.size() > 0 -> "tool_calls"
                else -> "stop"
            },
            usage = usage
        )
    }

    private fun List<JsonObject>.messageText(): String = asSequence()
        .filter { it.stringOrNull("type") == "message" }
        .flatMap { it.arrayOrNull("content").objectParts().asSequence() }
        .filter { it.stringOrNull("type") == "output_text" }
        .joinToString("") { it.stringOrNull("text").orEmpty() }

    private fun JsonObject.replayItem(): JsonObject = deepCopy().apply {
        remove("created_by")
        remove("parsed_arguments")
        if (officialDeepSeek && stringOrNull("type") == "reasoning") {
            remove("encrypted_content")
            remove("summary")
        }
    }

    override fun readStream(reader: BufferedReader, onUpdate: (String, String?) -> Unit): AiChatReply {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val finishedItems = sortedMapOf<Int, JsonObject>()
        var reply: AiChatReply? = null
        reader.readAiSseFrames { event, data ->
            if (data == "[DONE]") return@readAiSseFrames true
            val frame = JsonParser.parseString(data).asJsonObject
            val type = frame.stringOrNull("type") ?: event
            if (type == "error" || type == "response.error") {
                if (subscription) throw chatGptRequestError(null, frame)
                val detail = frame.stringOrNull("message") ?: frame.stringOrNull("code")
                if (!detail.isNullOrBlank()) error(detail.take(500))
                frame.requireNoAiError()
                error("Responses 流式请求失败")
            }
            if (subscription && frame.objectOrNull("error") != null) throw chatGptRequestError(null, frame)
            frame.requireNoAiError(event)
            webEvents?.responsesProgress(type, frame)
            when (type) {
                "response.output_text.delta", "response.reasoning_text.delta", "response.reasoning_summary_text.delta" -> {
                    val delta = frame.stringOrNull("delta").orEmpty()
                    if (type == "response.output_text.delta") text.append(delta) else reasoning.append(delta)
                    onUpdate(text.toString(), reasoning.toString().takeIf { it.isNotBlank() })
                    false
                }
                "response.output_item.done" -> {
                    val index = frame.intOrNull("output_index")
                    val item = frame.objectOrNull("item")
                    if (index != null && index >= 0 && item != null) finishedItems[index] = item
                    false
                }
                "response.completed", "response.incomplete", "response.failed", "response.cancelled" -> {
                    val response = frame.objectOrNull("response") ?: error("Responses 结束事件缺少响应")
                    val succeeded = type == "response.completed" &&
                        (response.stringOrNull("status") ?: "completed") == "completed"
                    // A completed stream may report usage without repeating its output items.
                    if (succeeded && response.arrayOrNull("output")?.isEmpty != false &&
                        (finishedItems.isNotEmpty() || text.isNotBlank())
                    ) {
                        response.add("output", JsonArray().apply { finishedItems.values.forEach(::add) })
                    }
                    reply = readResponse(response, type.substringAfter('.')).also { completed ->
                        if (succeeded && completed.finishReason == "stop" && completed.content.isBlank() &&
                            text.isNotBlank()
                        ) {
                            completed.message.addProperty("content", text.toString())
                        }
                        if (completed.reasoning == null && reasoning.isNotEmpty()) {
                            completed.message.addProperty("reasoning_content", reasoning.toString())
                        }
                        if (completed.content.isNotBlank()) onUpdate(completed.content, completed.reasoning)
                    }
                    true
                }
                else -> false
            }
        }
        return reply ?: throw AiChatIncompleteStreamException("Responses 流在收到结束事件前中断")
    }

    private companion object {
        const val CHATGPT_TOOL_NAMESPACE = "reading_ng"
        val OPENAI_REASONING_MODEL = Regex("^(?:gpt-[56](?:[.-]|$)|o[134](?:-|$))")
    }
}
