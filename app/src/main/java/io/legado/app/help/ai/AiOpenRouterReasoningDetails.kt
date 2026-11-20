package io.legado.app.help.ai

import com.google.gson.JsonArray
import okhttp3.HttpUrl

internal const val OPENROUTER_REASONING_DETAILS = "reasoning_details"

internal fun HttpUrl?.isOpenRouterApi(path: String): Boolean =
    this != null && isHttps && host == "openrouter.ai" && port == 443 &&
        username.isEmpty() && password.isEmpty() && encodedPath.trimEnd('/') == "/api/v1/$path"

internal fun AiProviderSetting.openRouterChatHistory(modelId: String): AiNativeChatHistory? =
    generationUrl(AiModelGenerationTestProtocol.CHAT_COMPLETIONS)
        ?.takeIf { it.isOpenRouterApi("chat/completions") }
        ?.let { AiNativeChatHistory(this, modelId, it.toString()) }

internal class AiOpenRouterReasoningDetails {
    private var details: JsonArray? = null
    private var pendingText: StringBuilder? = null
    private var pendingField: String? = null

    fun result(): JsonArray? {
        flushText()
        return details
    }

    fun accept(deltas: JsonArray) {
        val result = details ?: JsonArray().also { details = it }
        for (item in deltas) {
            if (!item.isJsonObject) continue
            val detail = item.asJsonObject
            val type = detail.stringOrNull("type")
            val textField = when (type) {
                "reasoning.text" -> "text"
                "reasoning.summary" -> "summary"
                else -> null
            }
            val previous = result.lastOrNull()?.asJsonObject
            // The gateway often repeats index 0. A signature closes its text block.
            if (textField == null || previous == null || previous.stringOrNull("type") != type ||
                !previous.stringOrNull("signature").isNullOrEmpty() ||
                listOf("id", "format").any { key ->
                    val old = previous.stringOrNull(key)
                    val new = detail.stringOrNull(key)
                    old != null && new != null && old != new
                }
            ) {
                flushText()
                result.add(detail.deepCopy())
                continue
            }
            detail.stringOrNull(textField)?.let { delta ->
                val text = pendingText ?: StringBuilder(previous.stringOrNull(textField).orEmpty()).also {
                    pendingText = it
                    pendingField = textField
                }
                text.append(delta)
            }
            detail.entrySet().forEach { (key, value) ->
                if (previous.get(key)?.isJsonNull != false) previous.add(key, value.deepCopy())
            }
        }
    }

    private fun flushText() {
        val text = pendingText ?: return
        details?.lastOrNull()?.asJsonObject?.addProperty(requireNotNull(pendingField), text.toString())
        pendingField = null
        pendingText = null
    }
}
