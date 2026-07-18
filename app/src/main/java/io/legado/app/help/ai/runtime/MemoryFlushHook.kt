package io.legado.app.help.ai.runtime

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal data class MemoryFlushConfig(
    val watchTools: Set<String>,
    val flushTools: Set<String>,
    val maxPending: Int,
    val blockBefore: Set<AgentHookPhase>
)

/**
 * 通用“工具结果必须先落入 AgentMemory”约束。
 *
 * Hook 只跟踪执行回执，不理解章节、人物或雷点；具体 Mode 通过配置决定监控哪些读取工具。
 */
internal object MemoryFlushHookFactory : AgentRuntimeHookFactory {

    override val id: String = "core.memory_flush"
    override val version: Int = 1

    override fun validateConfig(config: JsonObject): JsonObject {
        val allowedKeys = setOf("watch_tools", "flush_tools", "max_pending", "block_before")
        val unknownKeys = config.keySet() - allowedKeys
        require(unknownKeys.isEmpty()) {
            "$id 配置包含未知字段：${unknownKeys.sorted().joinToString()}"
        }
        val parsed = parseConfig(config)
        require(parsed.watchTools.isNotEmpty()) { "$id.watch_tools 不能为空" }
        require(parsed.flushTools.isNotEmpty()) { "$id.flush_tools 不能为空" }
        require(parsed.watchTools.intersect(parsed.flushTools).isEmpty()) {
            "$id 的 watch_tools 与 flush_tools 不能重叠"
        }
        require(parsed.maxPending in 1..25) { "$id.max_pending 必须在 1..25 之间" }
        require(parsed.blockBefore.isNotEmpty()) { "$id.block_before 不能为空" }
        return JsonObject().apply {
            add("watch_tools", parsed.watchTools.sorted().toJsonArray())
            add("flush_tools", parsed.flushTools.sorted().toJsonArray())
            addProperty("max_pending", parsed.maxPending)
            add("block_before", parsed.blockBefore.map { it.toBoundaryName() }.sorted().toJsonArray())
        }
    }

    override fun referencedToolNames(config: JsonObject): Set<String> {
        val parsed = parseConfig(validateConfig(config))
        return parsed.watchTools + parsed.flushTools
    }

    override fun pendingReceiptToolNames(config: JsonObject): Set<String> {
        return parseConfig(validateConfig(config)).watchTools
    }

    override fun create(config: JsonObject): AgentRuntimeHook {
        return MemoryFlushHook(parseConfig(validateConfig(config)))
    }

    private fun parseConfig(config: JsonObject): MemoryFlushConfig {
        val blockBefore = config.get("block_before")?.let { value ->
            require(value.isJsonArray) { "$id.block_before 必须是字符串数组" }
            value.asJsonArray.mapIndexed { index, element ->
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    "$id.block_before[$index] 必须是字符串"
                }
                when (element.asString.trim()) {
                    "compaction" -> AgentHookPhase.BEFORE_COMPACTION
                    "turn_finalize" -> AgentHookPhase.BEFORE_TURN_FINALIZE
                    else -> throw IllegalArgumentException(
                        "$id.block_before[$index] 只支持 compaction 或 turn_finalize"
                    )
                }
            }.toSet()
        } ?: setOf(AgentHookPhase.BEFORE_COMPACTION, AgentHookPhase.BEFORE_TURN_FINALIZE)
        return MemoryFlushConfig(
            watchTools = config.requiredStringSet("watch_tools"),
            flushTools = config.requiredStringSet("flush_tools"),
            maxPending = config.get("max_pending")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asInt
                ?: 1,
            blockBefore = blockBefore
        )
    }

    private fun JsonObject.requiredStringSet(key: String): Set<String> {
        val value = get(key) ?: throw IllegalArgumentException("$id.$key 缺失")
        require(value.isJsonArray) { "$id.$key 必须是字符串数组" }
        val values = value.asJsonArray.mapIndexed { index, element ->
            require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                "$id.$key[$index] 必须是字符串"
            }
            element.asString.trim().also { toolName ->
                require(TOOL_NAME_REGEX.matches(toolName)) {
                    "$id.$key[$index] 不是合法工具名"
                }
            }
        }
        require(values.distinct().size == values.size) { "$id.$key 不能包含重复工具名" }
        return values.toSet()
    }

    private fun AgentHookPhase.toBoundaryName(): String = when (this) {
        AgentHookPhase.BEFORE_COMPACTION -> "compaction"
        AgentHookPhase.BEFORE_TURN_FINALIZE -> "turn_finalize"
        else -> error("Unsupported memory flush boundary: $this")
    }

    private fun List<String>.toJsonArray(): JsonArray = JsonArray().also { array ->
        forEach(array::add)
    }

    private val TOOL_NAME_REGEX = Regex("^[A-Za-z0-9_.:-]+$")
}

private class MemoryFlushHook(
    private val config: MemoryFlushConfig
) : AgentRuntimeHook {

    override val id: String = MemoryFlushHookFactory.id
    override val version: Int = MemoryFlushHookFactory.version

    private val pendingReceiptIds = linkedSetOf<String>()

    override fun onEvent(event: AgentHookEvent): AgentHookResult {
        if (event.phase == AgentHookPhase.TURN_RESUMED) {
            pendingReceiptIds.clear()
            pendingReceiptIds.addAll(event.pendingReceiptIds)
            return if (pendingReceiptIds.isEmpty()) {
                AgentHookResult.Continue
            } else {
                pendingNotice()
            }
        }
        if (event.phase == AgentHookPhase.TOOL_RESULT_COMMITTED) {
            val receipt = event.receipt?.takeIf { it.isSuccessfulAndComplete }
                ?: return AgentHookResult.Continue
            if (receipt.toolName in config.flushTools) {
                pendingReceiptIds.removeAll(receipt.acknowledgedReceiptIds.toSet())
                return if (pendingReceiptIds.isEmpty()) AgentHookResult.Continue else pendingNotice()
            }
            if (receipt.toolName in config.watchTools) {
                pendingReceiptIds += receipt.receiptId
                return pendingNotice()
            }
            return AgentHookResult.Continue
        }
        if (event.phase == AgentHookPhase.TOOL_CALL_PROPOSED &&
            event.proposedToolName in config.watchTools &&
            pendingReceiptIds.size >= config.maxPending
        ) {
            return blocked(REASON_PENDING_LIMIT, "上一批工具结果尚未写入 AgentMemory，新的读取已暂停。")
        }
        if (event.phase in config.blockBefore && pendingReceiptIds.isNotEmpty()) {
            return blocked(REASON_PENDING_MEMORY, "存在尚未写入 AgentMemory 的工具结果，当前边界已暂停。")
        }
        return AgentHookResult.Continue
    }

    override fun snapshot(): AgentHookStateSnapshot {
        return AgentHookStateSnapshot(
            hookId = id,
            hookVersion = version,
            pendingReceiptIds = pendingReceiptIds.toList()
        )
    }

    private fun pendingNotice(): AgentHookResult.InjectNotice {
        val jsonIds = pendingReceiptIds.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")
        return AgentHookResult.InjectNotice(
            "以下工具结果尚未写入 AgentMemory：$jsonIds。达到本 Mode 的 pending 上限前必须调用 " +
                "agent_memory_upsert 或 agent_memory_batch_upsert，并把它们放入 source_receipt_ids；" +
                "压缩或结束前必须全部落盘。"
        )
    }

    private fun blocked(reasonCode: String, message: String): AgentHookResult.BlockBoundary {
        return AgentHookResult.BlockBoundary(
            reasonCode = reasonCode,
            message = message,
            recoveryInstruction = "请先调用 AgentMemory 写入工具，并传入全部 pending source_receipt_ids。",
            pendingReceiptIds = pendingReceiptIds.toList()
        )
    }

    private companion object {
        const val REASON_PENDING_MEMORY = "memory_flush_pending"
        const val REASON_PENDING_LIMIT = "memory_flush_pending_limit"
    }
}
