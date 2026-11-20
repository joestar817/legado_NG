package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.Request
import java.io.BufferedReader

internal class AiChatIncompleteStreamException(message: String) : IllegalStateException(message)

internal interface AiChatCodec {
    fun request(
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        stream: Boolean,
        conversationId: String?
    ): Request

    fun readJson(json: JsonObject): AiChatReply

    fun readStream(reader: BufferedReader, onUpdate: (String, String?) -> Unit): AiChatReply
}

internal data class AiChatReply(
    val message: JsonObject,
    val model: String?,
    val finishReason: String?,
    val usage: AiChatUsage?
) {
    val content: String get() = message.get("content").contentText()
    val reasoning: String? get() = message.stringOrNull("reasoning_content")
    val toolCalls: JsonArray get() = message.arrayOrNull("tool_calls") ?: JsonArray()

    fun requireCompleted() {
        when (finishReason?.lowercase()) {
            "length" -> error("模型输出达到上限，回复未完成")
            "content_filter", "refusal" -> error("模型拒绝生成此回复")
            "aborted", "cancelled", "error", "incomplete" -> error("提供商中断生成，回复未完成")
        }
        check(content.isNotBlank() || toolCalls.size() > 0) { "模型返回了空回复" }
    }
}

internal fun JsonObject.requireNoAiError(event: String? = null) {
    val detail = get("error")?.takeUnless { it.isJsonNull }?.let { error ->
        if (error.isJsonObject) error.asJsonObject.stringOrNull("message") ?: error.toString()
        else if (error.isJsonPrimitive) error.asString
        else error.toString()
    }
    if (detail != null || event == "error" || stringOrNull("type") == "error") {
        error(detail?.take(500) ?: "提供商返回错误")
    }
}

internal fun BufferedReader.readAiSseFrames(accept: (event: String?, data: String) -> Boolean) {
    var event: String? = null
    val data = mutableListOf<String>()
    while (true) {
        val line = readLine()
        if (line == null || line.isEmpty()) {
            if (data.isNotEmpty() && accept(event, data.joinToString("\n"))) return
            if (line == null) return
            event = null
            data.clear()
        } else if (line.startsWith("event:")) {
            event = line.substringAfter(':').removePrefix(" ")
        } else if (line.startsWith("data:")) {
            data.add(line.substringAfter(':').removePrefix(" "))
        }
    }
}
