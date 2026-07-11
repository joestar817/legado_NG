package io.legado.app.help.ai

import com.google.gson.JsonObject

/**
 * Book scan tool results can contain many chapters. They are persisted into the
 * book_scan memory domain and must not be resent on every interactive branch.
 */
internal object AiBookScanContext {

    fun pruneAfterTurn(messages: List<JsonObject>): List<JsonObject> {
        return messages.mapNotNull { message ->
            val role = message.get("role")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            when {
                role == "tool" -> null
                role == "assistant" && message.get("tool_calls")
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.isEmpty == false -> null

                else -> message.deepCopy().asJsonObject.apply {
                    if (role == "assistant") {
                        remove("reasoning")
                        remove("reasoning_content")
                        get("content")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.let { raw ->
                                addProperty("content", AiChatInteractionParser.parse(raw).content)
                            }
                    }
                }
            }
        }
    }
}
