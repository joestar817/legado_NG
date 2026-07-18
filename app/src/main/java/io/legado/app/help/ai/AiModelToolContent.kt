package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object AiModelToolContent {

    fun format(result: JsonObject, toolName: String, maxChars: Int): String {
        // MCP mirrors structured results into content[].text for protocol clients.
        // The built-in channel already has structuredContent, so sending the whole
        // envelope would duplicate large chapter bodies in every following request.
        val content = result.get("structuredContent")
            ?.takeUnless { it.isJsonNull }
            ?.toString()
            ?: result.toString()
        if (content.length <= maxChars) return content
        return JsonObject().apply {
            addProperty("ok", false)
            addProperty("tool", toolName)
            addProperty("truncated_by_app", true)
            addProperty("original_chars", content.length)
            addProperty("preview_chars", maxChars)
            addProperty(
                "message",
                "工具结果过大，App 已截断以避免超过模型上下文。请用 offset/limit/start/end/keyword/include_detail=false 等参数分页或缩小范围后重试。"
            )
            addProperty("preview", content.take(maxChars))
        }.toString()
    }

    fun isTruncatedByApp(content: String): Boolean {
        return runCatching {
            JsonParser.parseString(content)
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("truncated_by_app")
                ?.takeIf { it.isJsonPrimitive }
                ?.asBoolean == true
        }.getOrDefault(false)
    }
}

internal object AiToolResultBudget {

    const val ENVELOPE_RESERVE_CHARS = 1_024
    private const val RESPONSE_RESERVE_TOKENS = 8_192
    private const val CHARS_PER_TOKEN = 1.2
    private const val MIN_RESULT_CHARS = 1_024
    private const val MAX_RESULT_CHARS = 120_000

    fun resolveChars(
        contextWindowTokens: Int,
        thresholdTokens: Int,
        estimatedTokens: Int
    ): Int {
        val safeWindowTokens = minOf(contextWindowTokens, thresholdTokens)
        val availableTokens = (
            safeWindowTokens - estimatedTokens - RESPONSE_RESERVE_TOKENS
            ).coerceAtLeast(512)
        return (availableTokens * CHARS_PER_TOKEN)
            .toInt()
            .coerceIn(MIN_RESULT_CHARS, MAX_RESULT_CHARS)
    }
}
