package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

internal const val AI_NATIVE_HISTORY = "_legado_native_history"

internal fun JsonObject.removeNativeChatHistory() {
    if (remove(AI_NATIVE_HISTORY) != null) remove("reasoning_content")
    remove(OPENROUTER_REASONING_DETAILS)
}

internal class AiNativeChatHistory(
    setting: AiProviderSetting,
    modelId: String,
    endpoint: String = setting.baseUrl.trimEndSlash()
) {
    private val route = MessageDigest.getInstance("SHA-256")
        .digest(listOf(setting.id, setting.type.prefValue, endpoint, modelId,
            if (setting.isOpenAiAccountProvider()) setting.chatGptAccountId.orEmpty() else setting.apiKey)
            .joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun parts(message: JsonObject): JsonArray? = message.objectOrNull(AI_NATIVE_HISTORY)
        ?.takeIf { it.stringOrNull("route") == route }?.arrayOrNull("parts")

    fun attachParts(message: JsonObject, parts: JsonArray) {
        message.add(AI_NATIVE_HISTORY, JsonObject().apply {
            addProperty("route", route)
            add("parts", parts.deepCopy())
        })
    }

    fun reply(
        parts: JsonArray,
        text: String,
        reasoning: String?,
        calls: JsonArray,
        model: String?,
        finish: String?,
        usage: AiChatUsage?
    ): AiChatReply = AiChatReply(JsonObject().apply {
        addProperty("role", "assistant")
        addProperty("content", text)
        if (!reasoning.isNullOrBlank()) addProperty("reasoning_content", reasoning)
        if (calls.size() > 0) add("tool_calls", calls)
        if (parts.objectParts().any { part ->
                part.entrySet().any { (key, value) -> key != "text" && (key != "type" || value.asString != "text") }
            }) {
            attachParts(this, parts)
        }
    }, model, finish, usage)
}

internal fun aiFunctionCall(id: String, name: String, arguments: String) = JsonObject().apply {
    addProperty("id", id)
    addProperty("type", "function")
    add("function", JsonObject().apply {
        addProperty("name", name)
        addProperty("arguments", arguments)
    })
}

internal fun JsonArray?.objectParts(): List<JsonObject> = this?.map { it.asJsonObject }.orEmpty()

internal fun JsonElement?.aiJsonObjectArgument(): JsonObject {
    if (this == null || isJsonNull) return JsonObject()
    if (isJsonObject) return asJsonObject
    check(isJsonPrimitive && asJsonPrimitive.isString) { "工具参数必须是 JSON 对象" }
    if (asString.isBlank()) return JsonObject()
    val decoded = runCatching { JsonParser.parseString(asString) }.getOrNull()
    check(decoded?.isJsonObject == true) { "工具参数不是完整的 JSON 对象" }
    return decoded.asJsonObject
}

internal fun JsonArray.addNativeParts(role: String, key: String, parts: List<JsonObject>) {
    if (parts.isEmpty()) return
    val previous = lastOrNull()?.asJsonObject
    if (previous?.stringOrNull("role") == role) {
        val previousParts = requireNotNull(previous.arrayOrNull(key))
        parts.forEach { previousParts.add(it) }
    } else {
        add(JsonObject().apply {
            addProperty("role", role)
            add(key, JsonArray().apply { parts.forEach { add(it) } })
        })
    }
}

internal fun List<JsonObject>.replayWithText(
    text: String,
    displayText: (JsonObject) -> Boolean,
    textPart: (String) -> JsonObject
): List<JsonObject> {
    if (filter(displayText).joinToString("") { it.stringOrNull("text").orEmpty() } == text) return this
    val result = mutableListOf<JsonObject>()
    var added = false
    forEach { part ->
        if (!displayText(part)) {
            result += part
        } else if (!added && text.isNotEmpty()) {
            result += textPart(text)
            added = true
        }
    }
    if (!added && text.isNotEmpty()) result += textPart(text)
    return result
}
