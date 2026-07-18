package io.legado.app.help.ai.runtime

import com.google.gson.JsonObject

object AgentHookRegistry {

    private val factories: Map<String, AgentRuntimeHookFactory> = listOf(
        MemoryFlushHookFactory
    ).associateBy(AgentRuntimeHookFactory::id)

    fun supportedHooks(): Map<String, Int> {
        return factories.mapValues { (_, factory) -> factory.version }
    }

    fun validateBindings(
        bindings: List<AgentHookBinding>,
        allowedToolNames: Set<String>? = null
    ): List<AgentHookBinding> {
        val seen = hashSetOf<String>()
        return bindings.map { binding ->
            require(seen.add(binding.id)) { "runtime_hooks 重复声明：${binding.id}" }
            val factory = factories[binding.id]
                ?: throw IllegalArgumentException("未知 runtime Hook：${binding.id}")
            require(binding.version == factory.version) {
                "runtime Hook ${binding.id} 不支持版本 ${binding.version}，当前版本为 ${factory.version}"
            }
            val validatedConfig = factory.validateConfig(binding.config.deepCopy())
            allowedToolNames?.let { allowed ->
                val unavailable = factory.referencedToolNames(validatedConfig) - allowed
                require(unavailable.isEmpty()) {
                    "runtime Hook ${binding.id} 引用了未授权工具：${unavailable.sorted().joinToString()}"
                }
            }
            binding.copy(config = validatedConfig)
        }
    }

    fun createRuntime(
        bindings: List<AgentHookBinding>,
        allowedToolNames: Set<String>? = null
    ): AgentHookRuntime {
        val validated = validateBindings(bindings, allowedToolNames)
        val pendingReceiptToolNames = validated.flatMapTo(linkedSetOf()) { binding ->
            val factory = requireNotNull(factories[binding.id])
            factory.pendingReceiptToolNames(binding.config.deepCopy())
        }
        return AgentHookRuntime(
            validated.map { binding ->
                val factory = requireNotNull(factories[binding.id])
                factory.create(binding.config.deepCopy())
            },
            pendingReceiptToolNames
        )
    }
}

class AgentHookRuntime internal constructor(
    private val hooks: List<AgentRuntimeHook>,
    private val recoverablePendingToolNames: Set<String>
) {
    private val processedEvents = hashMapOf<String, AgentHookResult>()

    @Synchronized
    fun dispatch(event: AgentHookEvent): List<AgentHookInvocationResult> {
        require(event.eventId.isNotBlank()) { "Agent Hook eventId 不能为空" }
        require(event.conversationId.isNotBlank()) { "Agent Hook conversationId 不能为空" }
        require(event.turnId.isNotBlank()) { "Agent Hook turnId 不能为空" }
        if (event.phase == AgentHookPhase.TOOL_RESULT_COMMITTED) {
            requireNotNull(event.receipt) { "TOOL_RESULT_COMMITTED 事件必须包含工具回执" }
        }
        if (event.phase == AgentHookPhase.TOOL_CALL_PROPOSED) {
            require(!event.proposedToolName.isNullOrBlank()) {
                "TOOL_CALL_PROPOSED 事件必须包含 proposedToolName"
            }
        }
        require(event.pendingReceiptIds.none(String::isBlank)) {
            "Agent Hook pendingReceiptIds 不能包含空值"
        }
        require(event.pendingReceiptIds.distinct().size == event.pendingReceiptIds.size) {
            "Agent Hook pendingReceiptIds 不能重复"
        }
        event.receipt?.let { receipt ->
            require(receipt.receiptId.isNotBlank()) { "工具回执 receiptId 不能为空" }
            require(receipt.toolCallId.isNotBlank()) { "工具回执 toolCallId 不能为空" }
            require(receipt.toolName.isNotBlank()) { "工具回执 toolName 不能为空" }
            require(receipt.conversationId == event.conversationId) {
                "工具回执与 Hook event 的 conversationId 不一致"
            }
            require(receipt.turnId == event.turnId) {
                "工具回执与 Hook event 的 turnId 不一致"
            }
            event.stepId?.let { stepId ->
                require(receipt.stepId == stepId) {
                    "工具回执与 Hook event 的 stepId 不一致"
                }
            }
        }
        return hooks.map { hook ->
            val key = "${event.eventId}\u0000${hook.id}\u0000${hook.version}"
            val result = processedEvents.getOrPut(key) { hook.onEvent(event) }
            AgentHookInvocationResult(
                eventId = event.eventId,
                hookId = hook.id,
                hookVersion = hook.version,
                result = result
            )
        }
    }

    @Synchronized
    fun snapshot(): List<AgentHookStateSnapshot> {
        return hooks.mapNotNull(AgentRuntimeHook::snapshot)
    }

    fun pendingReceiptToolNames(): Set<String> = recoverablePendingToolNames.toSet()

    @Synchronized
    fun evaluate(event: AgentHookEvent): AgentHookEffects {
        val results = dispatch(event).map { invocation -> invocation.result }
        return AgentHookEffects(
            notices = results.mapNotNull { result ->
                (result as? AgentHookResult.InjectNotice)?.notice
            }.filter(String::isNotBlank).distinct(),
            pinnedArtifactRefs = results.flatMap { result ->
                (result as? AgentHookResult.PinArtifacts)?.artifactRefs.orEmpty()
            }.filter(String::isNotBlank).distinct(),
            block = results.firstNotNullOfOrNull { result ->
                result as? AgentHookResult.BlockBoundary
            }
        )
    }
}
