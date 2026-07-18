package io.legado.app.help.ai

import com.google.gson.JsonObject

/**
 * 清理上次进程中断留下的未闭合 tool-call 批次，使下一次 Provider 请求保持合法。
 * 已执行结果由不可变 receipt 账本负责 replay，清理这里只影响 Provider 消息投影。
 */
internal object AiChatToolBatchRecovery {

    fun discardLatestIncompleteBatch(messages: MutableList<JsonObject>): Boolean {
        for (assistantIndex in messages.indices.reversed()) {
            val assistant = messages[assistantIndex]
            if (assistant.role() != "assistant") continue
            val calls = assistant.get("tool_calls")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?: continue
            if (calls.size() == 0) continue
            val callIds = calls.mapNotNull { call ->
                call.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.get("id")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
            }.toSet()
            var endExclusive = assistantIndex + 1
            val resultIds = linkedSetOf<String>()
            while (endExclusive < messages.size && messages[endExclusive].role() == "tool") {
                messages[endExclusive].get("tool_call_id")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
                    ?.let(resultIds::add)
                endExclusive += 1
            }
            if (callIds.isNotEmpty() && resultIds.containsAll(callIds)) continue
            messages.subList(assistantIndex, endExclusive).clear()
            return true
        }
        return false
    }

    private fun JsonObject.role(): String {
        return get("role")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
