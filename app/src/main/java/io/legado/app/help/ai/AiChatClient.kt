package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.data.AgentToolResultStore
import io.legado.app.data.entities.AgentToolResultArtifact
import io.legado.app.data.entities.AgentToolExecutionIntent
import io.legado.app.data.dao.AgentToolExecutionBeginOutcome
import io.legado.app.help.ai.runtime.AgentHookBinding
import io.legado.app.help.ai.runtime.AgentHookEvent
import io.legado.app.help.ai.runtime.AgentHookEffects
import io.legado.app.help.ai.runtime.AgentHookPhase
import io.legado.app.help.ai.runtime.AgentHookRegistry
import io.legado.app.help.ai.runtime.AgentHookResult
import io.legado.app.help.ai.runtime.AgentHookRuntime
import io.legado.app.help.ai.runtime.ToolApprovalStatus
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import io.legado.app.help.http.await
import io.legado.app.web.mcp.McpInternalChannel
import io.legado.app.web.mcp.McpInternalToolCatalog
import io.legado.app.web.mcp.McpToolSideEffect
import io.legado.app.web.mcp.McpToolExecutionContext
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import splitties.init.appCtx

class AiChatClient {

    suspend fun compactContext(
        messages: MutableList<JsonObject>,
        conversationId: String,
        skillId: String = "",
        skillRevision: String = "",
        skillContentHash: String = "",
        availableSkillIds: List<String> = emptyList(),
        runtimeHooks: List<AgentHookBinding> = emptyList(),
        promptUsageAnchor: AiChatPromptUsageAnchor? = null,
        enabledMcpCapabilityIds: List<String> = emptyList(),
        onContextEvent: (AiChatContextEvent) -> Unit = {}
    ): AiChatManualCompactionResult {
        val selection = runCatching { AiConfig.requireAssistantModel() }.getOrElse {
            throw IllegalStateException("请先在模型设置中选择聊天模型")
        }
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error("AI provider not found: ${selection.providerId}")
        check(setting.enabled) { "AI provider is disabled" }
        check(setting.type == AiProviderType.OPENAI) { "AI 聊天暂只支持 OpenAI 兼容提供商" }
        val model = resolveModel(setting, selection.modelId)
        val activeSkill = AiChatContextManager.activeSkillSnapshot(messages)
        val tools = if (model.abilities.contains(AiModelAbility.TOOL)) {
            loadModelTools(
                skillId,
                skillContentHash,
                availableSkillIds,
                enabledMcpCapabilityIds,
                activeSkill
            )
        } else {
            emptyList()
        }
        val hookRuntime = AgentHookRegistry.createRuntime(
            runtimeHooks,
            McpInternalToolCatalog.resolveToolNames(enabledMcpCapabilityIds)
        )
        val pendingReceiptIds = AgentToolResultStore.listUnacknowledgedByConversation(
            conversationId = conversationId,
            skillRevision = skillRevision,
            contentHash = skillContentHash,
            toolNames = hookRuntime.pendingReceiptToolNames()
        ).map { artifact -> artifact.receiptId }
        messages.applyHookEffects(hookRuntime.evaluate(
            AgentHookEvent(
                eventId = "$conversationId:manual-compaction:resume",
                phase = AgentHookPhase.TURN_RESUMED,
                conversationId = conversationId,
                turnId = "manual-compaction",
                skillRevision = skillRevision,
                skillContentHash = skillContentHash,
                authorizedCapabilityIds = enabledMcpCapabilityIds,
                pendingReceiptIds = pendingReceiptIds
            )
        ))
        val calibration = resolveTokenCalibration(
            messages = messages,
            toolDefinitions = tools,
            anchor = promptUsageAnchor,
            providerId = selection.providerId,
            modelId = model.id
        )
        var blocked: AgentHookResult.BlockBoundary? = null
        val record = compactIfNeeded(
            messages = messages,
            toolDefinitions = tools,
            calibration = calibration,
            stage = AiChatCompactionStage.PRE_TURN,
            onContextEvent = onContextEvent,
            force = true,
            beforeCompaction = {
                hookRuntime.evaluate(
                    AgentHookEvent(
                        eventId = "$conversationId:manual-compaction:boundary",
                        phase = AgentHookPhase.BEFORE_COMPACTION,
                        conversationId = conversationId,
                        turnId = "manual-compaction",
                        skillRevision = skillRevision,
                        skillContentHash = skillContentHash,
                        authorizedCapabilityIds = enabledMcpCapabilityIds
                    )
                ).also { effects -> blocked = effects.block }
            }
        ) ?: error(blocked?.message ?: "上下文压缩未执行")
        return AiChatManualCompactionResult(
            record = record,
            contextUsage = AiChatContextManager.usage(messages, tools),
            contextMessages = messages.map { it.deepCopy().asJsonObject }
        )
    }

    suspend fun send(
        messages: MutableList<JsonObject>,
        conversationId: String,
        turnId: String,
        skillId: String = "",
        skillRevision: String = "",
        skillContentHash: String = "",
        availableSkillIds: List<String> = emptyList(),
        memoryPolicy: AgentModeMemoryPolicy? = null,
        runtimeHooks: List<AgentHookBinding> = emptyList(),
        promptUsageAnchor: AiChatPromptUsageAnchor? = null,
        onStreamUpdate: (AiChatStreamUpdate) -> Unit = {},
        onDurableContextUpdate: (AiChatDurableContextUpdate) -> Unit = {},
        onContextEvent: (AiChatContextEvent) -> Unit = {},
        enabledMcpCapabilityIds: List<String> = emptyList(),
        onToolConfirmationRequired: suspend (List<AiPendingToolCall>) -> Boolean = { false }
    ): AiChatTurnResult {
        val selection = runCatching { AiConfig.requireAssistantModel() }.getOrElse {
            throw IllegalStateException("请先在模型设置中选择聊天模型")
        }
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error("AI provider not found: ${selection.providerId}")
        check(setting.enabled) { "AI provider is disabled" }
        check(setting.type == AiProviderType.OPENAI) { "AI 聊天暂只支持 OpenAI 兼容提供商" }
        val model = resolveModel(setting, selection.modelId)
        val params = AiConfig.assistantChatParams(model.abilities.contains(AiModelAbility.REASONING))
        val activeSkill = AiChatContextManager.activeSkillSnapshot(messages)
        val tools = if (model.abilities.contains(AiModelAbility.TOOL)) {
            loadModelTools(
                skillId,
                skillContentHash,
                availableSkillIds,
                enabledMcpCapabilityIds,
                activeSkill
            )
        } else {
            emptyList()
        }
        val warnings = buildList {
            if (AiConfig.internalMcpEnabled && !model.abilities.contains(AiModelAbility.TOOL)) {
                add("当前模型未声明工具能力，未附加 MCP tools")
            }
            if (!AiConfig.internalMcpEnabled) {
                add("内置 MCP 未开启")
            }
        }.toMutableList()
        val client = aiHttpClient(setting.timeoutSeconds)
        val requestMessages = messages
        val recoveringInterruptedBatch =
            AiChatToolBatchRecovery.discardLatestIncompleteBatch(requestMessages)
        var allowLogicalRecoveryReplay = recoveringInterruptedBatch
        val hookRuntime = AgentHookRegistry.createRuntime(
            runtimeHooks,
            McpInternalToolCatalog.resolveToolNames(enabledMcpCapabilityIds)
        )
        val resumedReceipts = AgentToolResultStore.listUnacknowledgedByConversation(
            conversationId = conversationId,
            skillRevision = skillRevision,
            contentHash = skillContentHash,
            toolNames = hookRuntime.pendingReceiptToolNames()
        ).filter { artifact -> artifact.success && artifact.complete }
        requestMessages.applyHookEffects(hookRuntime.evaluate(
            AgentHookEvent(
                eventId = "$turnId:open",
                phase = if (resumedReceipts.isEmpty()) {
                    AgentHookPhase.TURN_OPENED
                } else {
                    AgentHookPhase.TURN_RESUMED
                },
                conversationId = conversationId,
                turnId = turnId,
                skillRevision = skillRevision,
                skillContentHash = skillContentHash,
                authorizedCapabilityIds = enabledMcpCapabilityIds,
                pendingReceiptIds = resumedReceipts.map { artifact -> artifact.receiptId }
            )
        ))
        val toolTrace = mutableListOf<String>()
        var lastUsage: AiChatUsage? = null
        var lastModel: String? = null
        var lastReasoning: String? = null
        var lastFinishReason: String? = null
        val contextCompactions = mutableListOf<AiChatCompactionRecord>()
        val memoryTrace = mutableListOf<AiMemoryTraceItem>()
        val toolReceipts = mutableListOf<ToolExecutionReceipt>()
        val recoverableToolStepContents = mutableListOf<String>()
        var durableAssistantContent = ""
        var stepNumber = 0
        var finalizeStateFlushRetryCount = 0
        var contextOverflowRecoveryAttempted = false
        val seenToolCallIds = hashSetOf<String>()
        var tokenCalibration = resolveTokenCalibration(
            messages = requestMessages,
            toolDefinitions = tools,
            anchor = promptUsageAnchor,
            providerId = selection.providerId,
            modelId = model.id
        )
        try {
            compactIfNeeded(
                requestMessages,
                tools,
                tokenCalibration,
                AiChatCompactionStage.PRE_TURN,
                onContextEvent,
                beforeCompaction = {
                    hookRuntime.evaluate(
                        AgentHookEvent(
                            eventId = "$turnId:pre-turn-compaction",
                            phase = AgentHookPhase.BEFORE_COMPACTION,
                            conversationId = conversationId,
                            turnId = turnId,
                            skillRevision = skillRevision,
                            skillContentHash = skillContentHash,
                            authorizedCapabilityIds = enabledMcpCapabilityIds
                        )
                    )
                }
            )?.let { record ->
            contextCompactions += record
            tokenCalibration = null
            onDurableContextUpdate(
                durableContextUpdate(
                    requestMessages,
                    durableAssistantContent,
                    lastReasoning,
                    toolTrace,
                    hookRuntime
                )
            )
        }
            while (true) {
            currentCoroutineContext().ensureActive()
            val stream = setting.streamResponseEnabled
            val body = buildRequestBody(setting, model, requestMessages, tools, params, stream)
            val request = Request.Builder()
                .url("${setting.baseUrl.trimEndSlash()}${setting.chatCompletionsPath.ensureStartSlash()}")
                .apply {
                    if (setting.apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${setting.apiKey}")
                    }
                }
                .addHeader("Content-Type", "application/json")
                .post(jsonBody(body))
                .build()
            val reasoningKey = setting.reasoningOptions(model.id)
                .reasoningOutputField
                .ifBlank { "reasoning_content" }
            val completion = try {
                if (stream) {
                    client.executeStreamChat(
                        request = request,
                        reasoningKey = reasoningKey,
                        deferVisibleContent = tools.isNotEmpty(),
                        onStreamUpdate = onStreamUpdate
                    )
                } else {
                    client.executeJsonChat(request, reasoningKey)
                }
            } catch (error: IllegalStateException) {
                if (!contextOverflowRecoveryAttempted &&
                    AiConfig.contextCompactionEnabled &&
                    error.isContextWindowExceeded()
                ) {
                    compactIfNeeded(
                        messages = requestMessages,
                        toolDefinitions = tools,
                        calibration = tokenCalibration,
                        stage = AiChatCompactionStage.PRE_TURN,
                        onContextEvent = onContextEvent,
                        force = true,
                        beforeCompaction = {
                            hookRuntime.evaluate(
                                AgentHookEvent(
                                    eventId = "$turnId:overflow-compaction",
                                    phase = AgentHookPhase.BEFORE_COMPACTION,
                                    conversationId = conversationId,
                                    turnId = turnId,
                                    skillRevision = skillRevision,
                                    skillContentHash = skillContentHash,
                                    authorizedCapabilityIds = enabledMcpCapabilityIds
                                )
                            )
                        }
                    )?.let { record ->
                        contextCompactions += record
                        tokenCalibration = null
                        contextOverflowRecoveryAttempted = true
                    } ?: throw error
                    continue
                }
                throw error
            }
            lastModel = completion.model
            lastUsage = completion.usage
            lastReasoning = completion.reasoning
            lastFinishReason = completion.finishReason
            stepNumber += 1
            val message = completion.message
            val content = completion.content
            val toolCalls = completion.toolCalls
            if (toolCalls.size() == 0) {
                durableAssistantContent = AiChatToolStepContent.bestRecoveryCandidate(
                    recoverableToolStepContents
                )?.let { earlierContent ->
                    if (finalizeStateFlushRetryCount > 0) {
                        AiChatVisibleContentRecovery.recoverAfterInternalRetry(
                            finalContent = content,
                            earlierContent = earlierContent
                        )
                    } else {
                        AiChatVisibleContentRecovery.recover(content, earlierContent)
                    }
                } ?: content.trim()
                val finalizeEffects = hookRuntime.evaluate(
                    AgentHookEvent(
                        eventId = "$turnId:step-$stepNumber:finalize",
                        phase = AgentHookPhase.BEFORE_TURN_FINALIZE,
                        conversationId = conversationId,
                        turnId = turnId,
                        stepId = "step-$stepNumber",
                        skillRevision = skillRevision,
                        skillContentHash = skillContentHash,
                        authorizedCapabilityIds = enabledMcpCapabilityIds
                    )
                )
                requestMessages.applyHookEffects(finalizeEffects)
                val finalizeBlock = finalizeEffects.block
                if (finalizeBlock != null && finalizeStateFlushRetryCount == 0) {
                    AiChatToolStepContent.recoveryCandidate(content)?.let(recoverableToolStepContents::add)
                    requestMessages.add(uploadAssistantMessage(content, lastReasoning))
                    finalizeStateFlushRetryCount += 1
                    onDurableContextUpdate(
                        durableContextUpdate(
                            requestMessages,
                            durableAssistantContent,
                            lastReasoning,
                            toolTrace,
                            hookRuntime
                        )
                    )
                    continue
                }
                val finalContent = if (finalizeBlock == null) {
                    requestMessages.removeRuntimeNotices()
                    durableAssistantContent
                } else {
                    warnings += "本轮存在尚未确认的工具结果，已保留为可恢复状态"
                    buildString {
                        append(durableAssistantContent.trim())
                        if (isNotEmpty()) append("\n\n")
                        append("> [!WARNING]\n> 本轮有 ")
                        append(finalizeBlock.pendingReceiptIds.size)
                        append(" 个工具结果尚未写入 AgentMemory，结果已保留，可在当前会话继续恢复。")
                    }
                }
                onStreamUpdate(
                    AiChatStreamUpdate(
                        content = finalContent,
                        reasoning = lastReasoning,
                        toolTrace = toolTrace.toList(),
                        memoryTrace = memoryTrace.toList()
                    )
                )
                requestMessages.add(uploadAssistantMessage(content, lastReasoning))
                onDurableContextUpdate(
                    durableContextUpdate(
                        requestMessages,
                        finalContent,
                        lastReasoning,
                        toolTrace,
                        hookRuntime,
                        toolReceipts
                    )
                )
                tokenCalibration = calibrationAfterModelResponse(
                    usage = completion.usage,
                    messages = requestMessages,
                    toolDefinitions = tools,
                    providerId = selection.providerId,
                    modelId = model.id
                ) ?: tokenCalibration
                val contextMessages = requestMessages.map { it.deepCopy().asJsonObject }
                return AiChatTurnResult(
                    content = finalContent,
                    reasoning = lastReasoning,
                    model = lastModel ?: model.id,
                    finishReason = lastFinishReason,
                    usage = lastUsage,
                    toolTrace = toolTrace,
                    toolReceipts = toolReceipts.toList(),
                    memoryTrace = memoryTrace.toList(),
                    warnings = warnings,
                    contextUsage = AiChatContextManager.usage(
                        contextMessages,
                        tools,
                        tokenCalibration
                    ),
                    contextCalibration = tokenCalibration,
                    compactionCount = contextCompactions.size,
                    contextCompactions = contextCompactions.toList(),
                    contextMessages = contextMessages
                )
            }
            AiChatToolStepContent.recoveryCandidate(content)?.let(recoverableToolStepContents::add)
            val providerContent = AiChatToolStepContent.providerProjection(content)
            if (providerContent != content) {
                warnings += "Provider 工具协议文本已从可见消息和后续请求中隔离"
            }
            requestMessages.add(message.deepCopy().asJsonObject.apply {
                addProperty("role", "assistant")
                addProperty("content", providerContent)
            })
            // 先持久化完整 tool-call 计划。批次中途退出时不保存半组 tool result，
            // 恢复阶段会清理未闭合的 Provider 投影，并通过不可变 receipt replay。
            onDurableContextUpdate(
                durableContextUpdate(
                    requestMessages,
                    durableAssistantContent,
                    lastReasoning,
                    toolTrace,
                    hookRuntime,
                    toolReceipts
                )
            )
            tokenCalibration = calibrationAfterModelResponse(
                usage = completion.usage,
                messages = requestMessages,
                toolDefinitions = tools,
                providerId = selection.providerId,
                modelId = model.id
            ) ?: tokenCalibration
            val parsedToolCalls = toolCalls.mapNotNull { call ->
                val callObject = call.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val callId = callObject.stringOrNull("id").orEmpty()
                val function = callObject.objectOrNull("function") ?: return@mapNotNull null
                val functionName = function.stringOrNull("name").orEmpty()
                val toolName = functionName.removePrefix(MCP_TOOL_PREFIX)
                val arguments = function.stringOrNull("arguments").orEmpty().parseObjectOrEmpty()
                ParsedToolCall(
                    callId = callId,
                    functionName = functionName,
                    toolName = toolName,
                    arguments = arguments
                )
            }
            require(parsedToolCalls.size == toolCalls.size()) {
                "模型返回了无法解析的 tool_calls，已在执行任何工具前中止"
            }
            val preparedToolCalls = parsedToolCalls.map { call ->
                require(call.callId.isNotBlank()) { "模型返回的 tool_call id 不能为空" }
                require(call.functionName.startsWith(MCP_TOOL_PREFIX) && call.toolName.isNotBlank()) {
                    "模型返回了非法的 MCP tool name"
                }
                require(seenToolCallIds.add(call.callId)) {
                    "模型在同一轮重复使用 tool_call id：${call.callId}"
                }
                val argumentsHash = AgentToolResultStore.argumentsHash(call.arguments)
                val receiptId = AgentToolResultStore.deterministicReceiptId(
                    conversationId = conversationId,
                    turnId = turnId,
                    toolCallId = call.callId,
                    argumentsHash = argumentsHash
                )
                val exactArtifact = AgentToolResultStore.get(receiptId)?.also { artifact ->
                    require(
                        artifact.conversationId == conversationId &&
                            artifact.turnId == turnId &&
                            artifact.toolName == call.toolName &&
                            artifact.skillRevision == skillRevision &&
                            artifact.contentHash == skillContentHash &&
                            artifact.argumentsHash == argumentsHash
                    ) { "已存在的工具回执与本次调用身份冲突，已拒绝重复执行" }
                }
                PreparedToolCall(
                    call = call,
                    argumentsHash = argumentsHash,
                    receiptId = receiptId,
                    reusableArtifact = exactArtifact ?: if (allowLogicalRecoveryReplay) {
                        AgentToolResultStore.findReusableByTurnToolArguments(
                            conversationId = conversationId,
                            turnId = turnId,
                            toolName = call.toolName,
                            argumentsHash = argumentsHash,
                            skillRevision = skillRevision,
                            contentHash = skillContentHash
                        )
                    } else {
                        null
                    }
                )
            }
            // Logical identity replay is only for the first regenerated batch after an
            // interrupted provider projection. Later batches in the same resumed turn are live.
            allowLogicalRecoveryReplay = false
            onStreamUpdate(
                AiChatStreamUpdate(
                    content = durableAssistantContent,
                    reasoning = lastReasoning,
                    toolTrace = toolTrace.toList(),
                    memoryTrace = memoryTrace.toList()
                )
            )
            val writeToolCalls = if (AiConfig.operationPermissionMode.requiresWriteConfirmation) {
                preparedToolCalls
                    .filter { prepared -> prepared.reusableArtifact == null }
                    .map { prepared -> prepared.call }
                    .filter { call ->
                        call.toolName.requiresPerCallConfirmation(
                            arguments = call.arguments
                        )
                    }
            } else {
                emptyList()
            }
            val writeToolCallIds = writeToolCalls.mapTo(mutableSetOf()) { it.callId }
            val writeApproved = if (writeToolCalls.isNotEmpty()) {
                onToolConfirmationRequired(writeToolCalls.map { call ->
                    call.toPendingToolCall(
                        McpInternalToolCatalog.sideEffectOf(
                            toolName = call.toolName,
                            argumentsDeclareWrite = call.arguments.booleanOrNull("write") == true ||
                                call.arguments.booleanOrNull("overwrite") == true
                        )
                    )
                })
            } else {
                true
            }
            preparedToolCalls.forEach { prepared ->
                val call = prepared.call
                val proposedEffects = if (prepared.reusableArtifact == null) {
                    hookRuntime.evaluate(
                        AgentHookEvent(
                            eventId = "$turnId:step-$stepNumber:${call.callId}:proposed",
                            phase = AgentHookPhase.TOOL_CALL_PROPOSED,
                            conversationId = conversationId,
                            turnId = turnId,
                            stepId = "step-$stepNumber",
                            skillRevision = skillRevision,
                            skillContentHash = skillContentHash,
                            authorizedCapabilityIds = enabledMcpCapabilityIds,
                            proposedToolName = call.toolName
                        )
                    )
                } else {
                    AgentHookEffects()
                }
                requestMessages.applyHookEffects(proposedEffects)
                val proposedBlock = proposedEffects.block
                val requiresWriteApproval = call.callId in writeToolCallIds
                var startedIntent: AgentToolExecutionIntent? = null
                var resolvedArtifact = prepared.reusableArtifact
                var indeterminateIntent: AgentToolExecutionIntent? = null
                val result = when {
                    resolvedArtifact != null -> resolvedArtifact.toReplayedMcpResult()
                    proposedBlock != null -> blockedToolResult(call.toolName, proposedBlock)
                    !writeApproved && requiresWriteApproval -> writeOperationCanceledResult(call.toolName)
                    else -> when (
                        val begin = AgentToolResultStore.beginExecution(
                            AgentToolExecutionIntent(
                                receiptId = prepared.receiptId,
                                conversationId = conversationId,
                                turnId = turnId,
                                toolCallId = call.callId,
                                toolName = call.toolName,
                                skillRevision = skillRevision,
                                contentHash = skillContentHash,
                                argumentsHash = prepared.argumentsHash,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    ) {
                        is AgentToolExecutionBeginOutcome.Completed -> {
                            resolvedArtifact = begin.artifact
                            begin.artifact.toReplayedMcpResult()
                        }

                        is AgentToolExecutionBeginOutcome.Indeterminate -> {
                            indeterminateIntent = begin.intent
                            indeterminateToolResult(call.toolName, begin.intent.receiptId)
                        }

                        is AgentToolExecutionBeginOutcome.Started -> {
                            startedIntent = begin.intent
                            runCatching {
                                AiAgentSkillTools.call(
                                    name = call.toolName,
                                    arguments = call.arguments,
                                    activeSkillId = skillId,
                                    contentHash = skillContentHash,
                                    availableSkillIds = normalizedAvailableSkillIds(
                                        skillId,
                                        availableSkillIds
                                    ),
                                    activeSkill = activeSkill
                                ) ?: McpInternalChannel.callTool(
                                    call.toolName,
                                    call.arguments,
                                    enabledMcpCapabilityIds,
                                    McpToolExecutionContext(
                                        conversationId = conversationId,
                                        turnId = turnId,
                                        skillRevision = skillRevision,
                                        skillContentHash = skillContentHash,
                                        allowedMemoryRanges = memoryPolicy?.allowedRanges.orEmpty()
                                    )
                                )
                            }.getOrElse {
                                JsonObject().apply {
                                    addProperty("ok", false)
                                    addProperty("error", it.localizedMessage ?: "MCP tool failed")
                                }
                            }
                        }
                    }
                }
                toolTrace.add(
                    buildString {
                        append(call.toolName)
                        append('(').append(call.arguments.toString().take(120)).append(')')
                        if (resolvedArtifact != null) append(" [replay]")
                        if (indeterminateIntent != null) append(" [indeterminate]")
                    }
                )
                onStreamUpdate(
                    AiChatStreamUpdate(
                        content = durableAssistantContent,
                        reasoning = lastReasoning,
                        toolTrace = toolTrace.toList(),
                        memoryTrace = memoryTrace.toList()
                    )
                )
                val fullModelToolContent = resolvedArtifact?.payload
                    ?: AiModelToolContent.format(result, call.toolName, Int.MAX_VALUE)
                val modelResultCharBudget = resolveToolResultCharBudget(
                    messages = requestMessages,
                    toolDefinitions = tools,
                    calibration = tokenCalibration
                )
                val boundedModelToolContent = AiModelToolContent.format(
                    result,
                    call.toolName,
                    (modelResultCharBudget - AiToolResultBudget.ENVELOPE_RESERVE_CHARS)
                        .coerceAtLeast(512)
                )
                val resultSucceeded = resolvedArtifact?.success
                    ?: result.isSuccessfulToolResult()
                val resultComplete = resolvedArtifact?.complete
                    ?: (!result.containsTruncationMarker() &&
                        indeterminateIntent == null &&
                        !AiModelToolContent.isTruncatedByApp(boundedModelToolContent))
                val newArtifact = AgentToolResultArtifact(
                        receiptId = prepared.receiptId,
                        conversationId = conversationId,
                        turnId = turnId,
                        toolCallId = call.callId,
                        toolName = call.toolName,
                        skillRevision = skillRevision,
                        contentHash = skillContentHash,
                        argumentsHash = prepared.argumentsHash,
                        resultHash = AgentToolResultStore.payloadHash(fullModelToolContent),
                        payload = fullModelToolContent,
                        success = resultSucceeded,
                        complete = resultComplete,
                        createdAt = System.currentTimeMillis()
                    )
                val storedArtifact = resolvedArtifact ?: if (indeterminateIntent != null) {
                    null
                } else {
                    startedIntent?.let { intent ->
                        AgentToolResultStore.completeExecution(intent.receiptId, newArtifact)
                    } ?: AgentToolResultStore.recordAndGet(newArtifact)
                }
                val receipt = ToolExecutionReceipt(
                    receiptId = storedArtifact?.receiptId ?: indeterminateIntent!!.receiptId,
                    conversationId = conversationId,
                    turnId = turnId,
                    stepId = "step-$stepNumber",
                    toolCallId = call.callId,
                    toolName = call.toolName,
                    argumentsHash = storedArtifact?.argumentsHash ?: prepared.argumentsHash,
                    resultHash = storedArtifact?.resultHash
                        ?: AgentToolResultStore.payloadHash(fullModelToolContent),
                    status = when {
                        result.booleanOrNull("canceled") == true -> ToolExecutionStatus.CANCELED
                        resultSucceeded -> ToolExecutionStatus.SUCCEEDED
                        else -> ToolExecutionStatus.FAILED
                    },
                    approvalStatus = when {
                        !requiresWriteApproval -> ToolApprovalStatus.NOT_REQUIRED
                        writeApproved -> ToolApprovalStatus.APPROVED
                        else -> ToolApprovalStatus.REJECTED
                    },
                    resultTruncated = storedArtifact?.complete != true,
                    artifactRefs = storedArtifact?.let { listOf(it.receiptId) }.orEmpty(),
                    acknowledgedReceiptIds = if (resultSucceeded && storedArtifact != null) {
                        result.acknowledgedReceiptIds()
                    } else {
                        emptyList()
                    },
                    error = result.toolErrorOrNull(),
                    createdAt = storedArtifact?.createdAt ?: indeterminateIntent!!.createdAt
                )
                toolReceipts += receipt
                requestMessages.applyHookEffects(hookRuntime.evaluate(
                    AgentHookEvent(
                        eventId = "$turnId:step-$stepNumber:${call.callId}:committed",
                        phase = AgentHookPhase.TOOL_RESULT_COMMITTED,
                        conversationId = conversationId,
                        turnId = turnId,
                        stepId = "step-$stepNumber",
                        skillRevision = skillRevision,
                        skillContentHash = skillContentHash,
                        authorizedCapabilityIds = enabledMcpCapabilityIds,
                        receipt = receipt
                    )
                ))
                if (receipt.acknowledgedReceiptIds.isNotEmpty()) {
                    requestMessages.removeRuntimeNotices()
                }
                val modelToolContent = if (storedArtifact == null) {
                    AiModelToolContent.format(result, call.toolName, modelResultCharBudget)
                } else if (AiModelToolContent.isTruncatedByApp(boundedModelToolContent)) {
                    JsonParser.parseString(boundedModelToolContent).asJsonObject.apply {
                        add("_agent_result", storedArtifact.toAgentResultEnvelope())
                    }.toString()
                } else {
                    AiModelToolContent.format(
                        result.withAgentResultEnvelope(storedArtifact),
                        call.toolName,
                        modelResultCharBudget
                    )
                }
                requestMessages.add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", call.callId)
                    addProperty("name", call.functionName)
                    addProperty("content", modelToolContent)
                })
            }
            // 一次发布完整的 tool-call batch，避免进程恢复时留下半组 Provider 消息。
            onDurableContextUpdate(
                durableContextUpdate(
                    requestMessages,
                    durableAssistantContent,
                    lastReasoning,
                    toolTrace,
                    hookRuntime,
                    toolReceipts
                )
            )
            compactIfNeeded(
                    requestMessages,
                    tools,
                    tokenCalibration,
                    AiChatCompactionStage.MID_TURN,
                    onContextEvent,
                    beforeCompaction = {
                        hookRuntime.evaluate(
                            AgentHookEvent(
                                eventId = "$turnId:step-$stepNumber:compaction",
                                phase = AgentHookPhase.BEFORE_COMPACTION,
                                conversationId = conversationId,
                                turnId = turnId,
                                stepId = "step-$stepNumber",
                                skillRevision = skillRevision,
                                skillContentHash = skillContentHash,
                                authorizedCapabilityIds = enabledMcpCapabilityIds
                            )
                        )
                    }
                )?.let { record ->
                contextCompactions += record
                tokenCalibration = null
                onDurableContextUpdate(
                    durableContextUpdate(
                        requestMessages,
                        durableAssistantContent,
                        lastReasoning,
                        toolTrace,
                        hookRuntime,
                        toolReceipts
                    )
                )
            }
            }
        } catch (error: Throwable) {
            requestMessages.applyHookEffects(
                hookRuntime.evaluate(
                    AgentHookEvent(
                        eventId = "$turnId:aborted:$stepNumber",
                        phase = AgentHookPhase.TURN_ABORTED,
                        conversationId = conversationId,
                        turnId = turnId,
                        stepId = stepNumber.takeIf { it > 0 }?.let { "step-$it" },
                        skillRevision = skillRevision,
                        skillContentHash = skillContentHash,
                        authorizedCapabilityIds = enabledMcpCapabilityIds
                    )
                )
            )
            runCatching {
                onDurableContextUpdate(
                    durableContextUpdate(
                        requestMessages,
                        durableAssistantContent,
                        lastReasoning,
                        toolTrace,
                        hookRuntime,
                        toolReceipts
                    )
                )
            }
            throw error
        }
    }

    private suspend fun compactIfNeeded(
        messages: MutableList<JsonObject>,
        toolDefinitions: List<JsonObject>,
        calibration: AiChatTokenCalibration?,
        stage: AiChatCompactionStage,
        onContextEvent: (AiChatContextEvent) -> Unit,
        force: Boolean = false,
        beforeCompaction: () -> AgentHookEffects = { AgentHookEffects() }
    ): AiChatCompactionRecord? {
        val usage = AiChatContextManager.usage(messages, toolDefinitions, calibration)
        if (!AiConfig.contextCompactionEnabled && !force) {
            check(usage.estimatedTokens < usage.contextWindowTokens) {
                "当前上下文约 ${usage.estimatedTokens} tokens，已超过配置的 ${usage.contextWindowTokens} tokens 上下文窗口；请开启自动压缩或增大上下文窗口"
            }
            return null
        }
        val officialUsageReachedThreshold =
            calibration?.contextTokens?.let { it >= usage.thresholdTokens } == true
        val hardWindowGuard = usage.contextWindowTokens * HARD_WINDOW_GUARD_PERCENT / 100
        val predictedNearWindowLimit = usage.estimatedTokens >= hardWindowGuard
        if (!force && !officialUsageReachedThreshold && !predictedNearWindowLimit) return null

        val hookEffects = beforeCompaction()
        messages.applyHookEffects(hookEffects)
        hookEffects.block?.let {
            return null
        }
        val pinnedArtifactRefs = hookEffects.pinnedArtifactRefs.toSet()

        onContextEvent(
            AiChatContextEvent(
                type = AiChatContextEventType.STARTED,
                stage = stage,
                beforeTokens = usage.estimatedTokens,
                beforeUsage = usage
            )
        )
        val summary = createCompactionSummary(messages, pinnedArtifactRefs)
        val replacement = AiChatContextManager.buildCompactedHistory(
            messages,
            summary.content,
            pinnedArtifactRefs
        )
        val replacementUsage = AiChatContextManager.usage(replacement, toolDefinitions)
        check(replacementUsage.estimatedTokens < usage.thresholdTokens) {
            "上下文压缩后仍有约 ${replacementUsage.estimatedTokens} tokens，" +
                "未降到 ${usage.thresholdTokens} tokens 阈值以下"
        }
        messages.clear()
        messages.addAll(replacement)
        val record = AiChatCompactionRecord(
            beforeTokens = usage.estimatedTokens,
            afterTokens = replacementUsage.estimatedTokens,
            revision = AiChatContextManager.compactionRevision(replacement),
            summaryPromptTokens = summary.usage?.promptTokens ?: 0,
            summaryCompletionTokens = summary.usage?.completionTokens ?: 0
        )
        onContextEvent(
            AiChatContextEvent(
                type = AiChatContextEventType.COMPLETED,
                stage = stage,
                beforeTokens = usage.estimatedTokens,
                afterTokens = replacementUsage.estimatedTokens,
                afterUsage = replacementUsage,
                compaction = record
            )
        )
        return record
    }

    private suspend fun createCompactionSummary(
        messages: List<JsonObject>,
        pinnedArtifactRefs: Set<String> = emptySet()
    ): AiChatCompactionSummary {
        val selection = AiConfig.contextCompactionModel()
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error("AI provider not found: ${selection.providerId}")
        check(setting.enabled) { "上下文压缩模型所属的 AI provider 已禁用" }
        check(setting.type == AiProviderType.OPENAI) { "上下文压缩暂只支持 OpenAI 兼容提供商" }
        val model = resolveModel(setting, selection.modelId)
        val prompt = appCtx.assets.open(CONTEXT_COMPACTION_PROMPT_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val pinnedMessages = AiChatContextManager.pinnedArtifactMessages(
            messages,
            pinnedArtifactRefs
        ).toSet()
        val compactionHistory = messages.filterNot { message ->
                message.stringOrNull("role") == "system" || message in pinnedMessages
            }
            .mapTo(mutableListOf()) { it.deepCopy().asJsonObject }
        while (true) {
            currentCoroutineContext().ensureActive()
            val compactionMessages = buildList {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", prompt)
                })
                addAll(compactionHistory)
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "请将以上上下文压缩为可继续当前任务的接续摘要。")
                })
            }
            val body = buildRequestBody(
                setting = setting,
                model = model,
                messages = compactionMessages,
                tools = emptyList(),
                params = AiTextParams(temperature = 0f, disableThinking = true),
                stream = false
            )
            val request = Request.Builder()
                .url("${setting.baseUrl.trimEndSlash()}${setting.chatCompletionsPath.ensureStartSlash()}")
                .apply {
                    if (setting.apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer ${setting.apiKey}")
                    }
                }
                .addHeader("Content-Type", "application/json")
                .post(jsonBody(body))
                .build()
            val completion = try {
                aiHttpClient(setting.timeoutSeconds)
                    .executeJsonChat(request, setting.reasoningOptions(model.id).reasoningOutputField)
            } catch (error: IllegalStateException) {
                if (!error.isContextWindowExceeded()) throw error
                val reduced = AiChatContextManager
                    .trimOldestCompactionHistoryUnit(compactionHistory) ||
                    AiChatContextManager.shrinkLargestCompactionMessage(compactionHistory)
                if (!reduced) {
                    throw IllegalStateException("压缩模型的上下文窗口不足，无法生成接续摘要", error)
                }
                continue
            }
            return AiChatCompactionSummary(
                content = completion.content
                    .trim()
                    .ifBlank { error("上下文压缩模型返回了空摘要") },
                usage = completion.usage
            )
        }
    }

    private fun Throwable.isContextWindowExceeded(): Boolean {
        val text = generateSequence(this) { it.cause }
            .mapNotNull { it.message }
            .joinToString("\n")
            .lowercase()
        return CONTEXT_WINDOW_ERROR_MARKERS.any(text::contains)
    }

    fun newSystemMessage(): JsonObject {
        return JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", AGENT_SYSTEM_PROMPT)
        }
    }

    fun newUserMessage(content: String): JsonObject {
        return JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", content)
        }
    }

    fun newAssistantMessage(content: String, reasoning: String?): JsonObject {
        return uploadAssistantMessage(content, reasoning)
    }

    fun estimateContextUsage(
        messages: List<JsonObject>,
        promptUsageAnchor: AiChatPromptUsageAnchor? = null,
        skillId: String = "",
        skillContentHash: String = "",
        availableSkillIds: List<String> = emptyList(),
        enabledMcpCapabilityIds: List<String> = emptyList()
    ): AiChatContextUsage {
        val selection = AiConfig.requireAssistantModel()
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error("AI provider not found: ${selection.providerId}")
        val model = resolveModel(setting, selection.modelId)
        val activeSkill = AiChatContextManager.activeSkillSnapshot(messages)
        val tools = if (model.abilities.contains(AiModelAbility.TOOL)) {
            loadModelTools(
                skillId,
                skillContentHash,
                availableSkillIds,
                enabledMcpCapabilityIds,
                activeSkill
            )
        } else {
            emptyList()
        }
        val calibration = resolveTokenCalibration(
            messages = messages,
            toolDefinitions = tools,
            anchor = promptUsageAnchor,
            providerId = selection.providerId,
            modelId = model.id
        )
        return AiChatContextManager.usage(messages, tools, calibration)
    }

    private fun resolveTokenCalibration(
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject>,
        anchor: AiChatPromptUsageAnchor?,
        providerId: String,
        modelId: String
    ): AiChatTokenCalibration? {
        anchor ?: return null
        if (anchor.providerId != null && anchor.providerId != providerId) return null
        if (anchor.modelId != null && anchor.modelId != modelId) return null
        val contextTokens = anchor.contextTokens
        val localContextTokens = anchor.localContextTokens
        if (contextTokens != null && localContextTokens != null) {
            return AiChatTokenCalibration.create(
                contextTokens = contextTokens,
                localContextTokens = localContextTokens,
                providerId = providerId,
                modelId = modelId
            )
        }
        return anchor.localPromptTokens?.let { localPromptTokens ->
            AiChatTokenCalibration.create(
                contextTokens = anchor.promptTokens,
                localContextTokens = localPromptTokens,
                providerId = providerId,
                modelId = modelId
            )
        } ?: AiChatContextManager.calibrationFromHistory(
            messages = messages,
            toolDefinitions = toolDefinitions,
            promptTokens = anchor.promptTokens,
            providerId = providerId,
            modelId = modelId,
            expectedToolCallCount = anchor.expectedToolCallCount
        )
    }

    private fun calibrationAfterModelResponse(
        usage: AiChatUsage?,
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject>,
        providerId: String,
        modelId: String
    ): AiChatTokenCalibration? {
        usage ?: return null
        val contextTokens = usage.totalTokens?.takeIf { it > 0 }
            ?: usage.promptTokens
                ?.takeIf { it > 0 }
                ?.let { promptTokens -> promptTokens + (usage.completionTokens ?: 0) }
            ?: return null
        val localContextTokens = AiChatContextManager
            .usage(messages, toolDefinitions)
            .localEstimatedTokens
        return AiChatTokenCalibration.create(
            contextTokens = contextTokens,
            localContextTokens = localContextTokens,
            providerId = providerId,
            modelId = modelId
        )
    }

    private fun resolveModel(setting: AiProviderSetting, modelId: String): AiModel {
        val model = setting.models.firstOrNull { it.id == modelId } ?: AiModel(id = modelId, name = modelId)
        return AiModelRegistry.enrich(model)
    }

    private fun buildRequestBody(
        setting: AiProviderSetting,
        model: AiModel,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        stream: Boolean
    ): JsonObject {
        val reasoningOptions = setting.reasoningOptions(model.id)
        return JsonObject().apply {
            addProperty("model", model.id)
            add("messages", JsonArray().apply {
                messages.forEach { message ->
                    add(AiChatContextManager.providerMessage(message))
                }
            })
            params.temperature?.let { addProperty("temperature", it) }
            params.maxTokens?.let { addProperty("max_tokens", it) }
            addProperty("stream", stream)
            if (stream && setting.supportsStreamUsage) {
                add("stream_options", JsonObject().apply {
                    addProperty("include_usage", true)
                })
            }
            addReasoningParams(params, reasoningOptions)
            if (tools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    tools.forEach { tool ->
                        add(tool.deepCopy())
                    }
                })
            }
        }
    }

    private suspend fun OkHttpClient.executeJsonChat(
        request: Request,
        reasoningKey: String
    ): ChatCompletionSnapshot {
        val (response, responseBody) = executeJsonOrThrow(request)
        if (!response.isSuccessful) {
            error("HTTP ${response.code}: ${responseBody.take(500)}")
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val choice = json.arrayOrNull("choices")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: error("OpenAI response missing choices")
        val message = choice.objectOrNull("message")
            ?: error("OpenAI response missing message")
        val usage = json.objectOrNull("usage")?.toChatUsage()
        return ChatCompletionSnapshot(
            message = message,
            content = message.get("content").contentText(),
            reasoning = message.stringOrNull(reasoningKey)
                ?: message.stringOrNull("reasoning_content")
                ?: message.stringOrNull("reasoning"),
            model = json.stringOrNull("model"),
            finishReason = choice.stringOrNull("finish_reason"),
            usage = usage,
            toolCalls = message.arrayOrNull("tool_calls") ?: JsonArray()
        )
    }

    private suspend fun OkHttpClient.executeStreamChat(
        request: Request,
        reasoningKey: String,
        deferVisibleContent: Boolean,
        onStreamUpdate: (AiChatStreamUpdate) -> Unit
    ): ChatCompletionSnapshot {
        return withContext(IO) {
            val response = newCall(request).await()
            response.use {
                val responseBody = it.body
                if (!it.isSuccessful) {
                    error("HTTP ${it.code}: ${responseBody.string().take(500)}")
                }
                var model: String? = null
                var finishReason: String? = null
                var usage: AiChatUsage? = null
                val contentBuilder = StringBuilder()
                val reasoningBuilder = StringBuilder()
                val toolCalls = linkedMapOf<Int, StreamToolCall>()
                responseBody.charStream().buffered().useLines { lines ->
                    lines.forEach { line ->
                        currentCoroutineContext().ensureActive()
                        val data = line.trim()
                            .takeIf { line -> line.startsWith("data:") }
                            ?.removePrefix("data:")
                            ?.trim()
                            ?: return@forEach
                        if (data == "[DONE]") {
                            return@forEach
                        }
                        val json = runCatching {
                            JsonParser.parseString(data).asJsonObject
                        }.getOrNull() ?: return@forEach
                        model = json.stringOrNull("model") ?: model
                        usage = json.objectOrNull("usage")?.toChatUsage() ?: usage
                        val choice = json.arrayOrNull("choices")
                            ?.firstOrNull()
                            ?.takeIf { it.isJsonObject }
                            ?.asJsonObject
                            ?: return@forEach
                        finishReason = choice.stringOrNull("finish_reason") ?: finishReason
                        val delta = choice.objectOrNull("delta")
                            ?: choice.objectOrNull("message")
                            ?: JsonObject()
                        val contentDelta = delta.get("content").contentText()
                        if (contentDelta.isNotEmpty()) {
                            contentBuilder.append(contentDelta)
                        }
                        val reasoningDelta = delta.stringOrNull(reasoningKey)
                            ?: delta.stringOrNull("reasoning_content")
                            ?: delta.stringOrNull("reasoning")
                        if (!reasoningDelta.isNullOrEmpty()) {
                            reasoningBuilder.append(reasoningDelta)
                        }
                        delta.arrayOrNull("tool_calls")?.forEach { item ->
                            val obj = item.takeIf { call -> call.isJsonObject }
                                ?.asJsonObject
                                ?: return@forEach
                            val index = obj.intOrNull("index") ?: toolCalls.size
                            val toolCall = toolCalls.getOrPut(index) { StreamToolCall() }
                            obj.stringOrNull("id")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { id -> toolCall.id = id }
                            obj.stringOrNull("type")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { type -> toolCall.type = type }
                            obj.objectOrNull("function")?.let { function ->
                                function.stringOrNull("name")
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { name -> toolCall.name = name }
                                function.stringOrNull("arguments")?.let { arguments ->
                                    toolCall.arguments.append(arguments)
                                }
                            }
                        }
                        if ((!deferVisibleContent && contentDelta.isNotEmpty()) || !reasoningDelta.isNullOrEmpty()) {
                            onStreamUpdate(
                                AiChatStreamUpdate(
                                    content = if (deferVisibleContent) "" else contentBuilder.toString(),
                                    reasoning = reasoningBuilder.toString()
                                        .takeIf { text -> text.isNotBlank() }
                                )
                            )
                        }
                    }
                }
                val toolCallArray = JsonArray().apply {
                    toolCalls.toSortedMap().values.forEach { add(it.toJson()) }
                }
                val content = contentBuilder.toString()
                val reasoning = reasoningBuilder.toString().takeIf { text -> text.isNotBlank() }
                if (deferVisibleContent && toolCallArray.size() == 0 && content.isNotEmpty()) {
                    onStreamUpdate(
                        AiChatStreamUpdate(
                            content = content,
                            reasoning = reasoning
                        )
                    )
                }
                val message = JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", content)
                    reasoning?.let { value -> addProperty("reasoning_content", value) }
                    if (toolCallArray.size() > 0) {
                        add("tool_calls", toolCallArray)
                    }
                }
                ChatCompletionSnapshot(
                    message = message,
                    content = content,
                    reasoning = reasoning,
                    model = model,
                    finishReason = finishReason,
                    usage = usage,
                    toolCalls = toolCallArray
                )
            }
        }
    }

    private fun loadModelTools(
        skillId: String,
        skillContentHash: String,
        availableSkillIds: Collection<String>,
        enabledMcpCapabilityIds: Collection<String>,
        activeSkill: AiActiveSkillSnapshot?
    ): List<JsonObject> {
        val normalizedSkillIds = normalizedAvailableSkillIds(skillId, availableSkillIds)
        val definitions = buildList {
            AiAgentSkillTools.toolDefinition(
                activeSkillId = skillId,
                contentHash = skillContentHash,
                availableSkillIds = normalizedSkillIds,
                activeSkill = activeSkill
            )?.let(::add)
            if (AiConfig.internalMcpEnabled) {
                addAll(McpInternalChannel.listTools(enabledMcpCapabilityIds))
            }
        }
        return definitions.mapNotNull { obj ->
            val name = obj.stringOrNull("name") ?: return@mapNotNull null
            val inputSchema = obj.objectOrNull("inputSchema") ?: JsonObject().apply {
                addProperty("type", "object")
            }
            JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", "$MCP_TOOL_PREFIX$name")
                    addProperty("description", obj.stringOrNull("description").orEmpty())
                    add("parameters", inputSchema.deepCopy())
                })
            }
        }
    }

    private fun uploadAssistantMessage(content: String, reasoning: String?): JsonObject {
        return JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content)
            if (!reasoning.isNullOrBlank()) {
                addProperty("reasoning_content", reasoning)
            }
        }
    }

    private fun String.parseObjectOrEmpty(): JsonObject {
        return runCatching {
            JsonParser.parseString(this).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull() ?: JsonObject()
    }

    private fun durableContextUpdate(
        messages: List<JsonObject>,
        visibleContent: String,
        reasoning: String?,
        toolTrace: List<String>,
        hookRuntime: AgentHookRuntime,
        toolReceipts: List<ToolExecutionReceipt> = emptyList()
    ): AiChatDurableContextUpdate {
        return AiChatDurableContextUpdate(
            contextMessages = messages.map { message -> message.deepCopy().asJsonObject },
            visibleContent = visibleContent,
            reasoning = reasoning,
            toolTrace = toolTrace.toList(),
            toolReceipts = toolReceipts.toList(),
            pendingReceiptIds = hookRuntime.snapshot()
                .flatMap { snapshot -> snapshot.pendingReceiptIds }
                .distinct()
        )
    }

    private fun MutableList<JsonObject>.applyHookEffects(effects: AgentHookEffects) {
        val noticeLines = buildList {
            addAll(effects.notices)
            effects.block?.let { blocked ->
                add(blocked.message)
                if (blocked.pendingReceiptIds.isNotEmpty()) {
                    add("待处理 artifact/receipt：${blocked.pendingReceiptIds.joinToString(",")}")
                }
                blocked.recoveryInstruction?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.filter(String::isNotBlank).distinct()
        removeRuntimeNotices()
        if (noticeLines.isEmpty()) return
        val notice = JsonObject().apply {
            addProperty("role", "system")
            addProperty(
                "content",
                buildString {
                    append(AGENT_RUNTIME_NOTICE_PREFIX).append('\n')
                    append(noticeLines.joinToString("\n"))
                }
            )
        }
        val index = indexOfLast { message ->
            message.stringOrNull("role") == "system"
        }.let { lastSystem -> if (lastSystem >= 0) lastSystem + 1 else 0 }
        add(index, notice)
    }

    private fun MutableList<JsonObject>.removeRuntimeNotices() {
        removeAll { message ->
            message.stringOrNull("role") == "system" &&
                message.stringOrNull("content").orEmpty().startsWith(AGENT_RUNTIME_NOTICE_PREFIX)
        }
    }

    private fun normalizedAvailableSkillIds(
        activeSkillId: String,
        availableSkillIds: Collection<String>
    ): List<String> {
        return availableSkillIds
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { listOfNotNull(activeSkillId.trim().takeIf(String::isNotBlank)) }
    }

    private fun blockedToolResult(
        toolName: String,
        blocked: AgentHookResult.BlockBoundary
    ): JsonObject {
        return JsonObject().apply {
            add("structuredContent", JsonObject().apply {
                addProperty("ok", false)
                addProperty("tool", toolName)
                addProperty("blocked", true)
                addProperty("reason_code", blocked.reasonCode)
                addProperty("error", blocked.message)
                add("pending_receipt_ids", JsonArray().apply {
                    blocked.pendingReceiptIds.forEach { receiptId -> add(receiptId) }
                })
            })
        }
    }

    private fun indeterminateToolResult(toolName: String, receiptId: String): JsonObject {
        return JsonObject().apply {
            add("structuredContent", JsonObject().apply {
                addProperty("ok", false)
                addProperty("tool", toolName)
                addProperty("execution_indeterminate", true)
                addProperty("intent_receipt_id", receiptId)
                addProperty(
                    "error",
                    "检测到上次执行在结果提交前中断。为避免重复写入，本次未自动重放；请先核对目标状态，再使用新的参数或幂等键重试。"
                )
            })
        }
    }

    private fun JsonObject.isSuccessfulToolResult(): Boolean {
        val payload = objectOrNull("structuredContent") ?: this
        return payload.booleanOrNull("ok") == true
    }

    private fun JsonObject.toolErrorOrNull(): String? {
        val payload = objectOrNull("structuredContent") ?: this
        return payload.stringOrNull("error")
            ?: payload.stringOrNull("message")?.takeIf { payload.booleanOrNull("ok") == false }
    }

    private fun JsonObject.acknowledgedReceiptIds(): List<String> {
        val payload = objectOrNull("structuredContent") ?: this
        val normalized = payload.objectOrNull("normalized_data")
            ?: payload.objectOrNull("normalizedData")
            ?: return emptyList()
        return normalized.arrayOrNull("acknowledged_receipt_ids")
            ?.mapNotNull { element ->
                element.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            ?.distinct()
            .orEmpty()
    }

    private fun JsonObject.containsTruncationMarker(): Boolean {
        fun contains(element: JsonElement): Boolean {
            return when {
                element.isJsonObject -> element.asJsonObject.entrySet().any { (key, value) ->
                    ((key == "truncated_by_app" || key == "truncated_by_mcp") &&
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean) ||
                        contains(value)
                }
                element.isJsonArray -> element.asJsonArray.any(::contains)
                else -> false
            }
        }
        return contains(this)
    }

    private fun JsonObject.withAgentResultEnvelope(
        artifact: AgentToolResultArtifact
    ): JsonObject {
        val copy = deepCopy().asJsonObject
        val target = copy.objectOrNull("structuredContent") ?: copy
        target.add("_agent_result", artifact.toAgentResultEnvelope())
        return copy
    }

    private fun AgentToolResultArtifact.toAgentResultEnvelope(): JsonObject {
        return JsonObject().apply {
            addProperty("receipt_id", receiptId)
            addProperty("result_hash", resultHash)
            addProperty("success", success)
            addProperty("complete", complete)
        }
    }

    private fun resolveToolResultCharBudget(
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject>,
        calibration: AiChatTokenCalibration?
    ): Int {
        val usage = AiChatContextManager.usage(messages, toolDefinitions, calibration)
        return AiToolResultBudget.resolveChars(
            contextWindowTokens = usage.contextWindowTokens,
            thresholdTokens = usage.thresholdTokens,
            estimatedTokens = usage.estimatedTokens
        )
    }

    private fun String.requiresPerCallConfirmation(
        arguments: JsonObject
    ): Boolean {
        return McpInternalToolCatalog.requiresUserConfirmation(
            toolName = this,
            argumentsDeclareWrite = arguments.booleanOrNull("write") == true ||
                arguments.booleanOrNull("overwrite") == true
        )
    }

    private fun writeOperationCanceledResult(toolName: String): JsonObject {
        return JsonObject().apply {
            addProperty("ok", false)
            addProperty("canceled", true)
            addProperty("error", "用户取消写操作")
            addProperty("tool", toolName)
            addProperty(
                "message",
                "当前写操作未执行。请停止写入流程，并向用户说明操作已取消。"
            )
        }
    }

    private fun JsonObject.toChatUsage(): AiChatUsage {
        return AiChatUsage(
            promptTokens = intOrNull("prompt_tokens"),
            completionTokens = intOrNull("completion_tokens"),
            totalTokens = intOrNull("total_tokens")
        )
    }

    private fun JsonObject.booleanOrNull(name: String): Boolean? {
        return runCatching {
            get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
        }.getOrNull()
    }

    private fun AgentToolResultArtifact.toReplayedMcpResult(): JsonObject {
        val storedPayload = runCatching { JsonParser.parseString(payload) }
            .getOrElse { throw IllegalStateException("已保存的工具结果无法解析，拒绝重复执行", it) }
        return JsonObject().apply {
            add("structuredContent", storedPayload)
        }
    }

    private data class PreparedToolCall(
        val call: ParsedToolCall,
        val argumentsHash: String,
        val receiptId: String,
        val reusableArtifact: AgentToolResultArtifact?
    )

    private data class ParsedToolCall(
        val callId: String,
        val functionName: String,
        val toolName: String,
        val arguments: JsonObject
    ) {
        fun toPendingToolCall(sideEffect: McpToolSideEffect): AiPendingToolCall {
            return AiPendingToolCall(
                toolName = toolName,
                functionName = functionName,
                callId = callId,
                arguments = arguments.deepCopy(),
                sideEffect = sideEffect
            )
        }
    }

    private data class ChatCompletionSnapshot(
        val message: JsonObject,
        val content: String,
        val reasoning: String?,
        val model: String?,
        val finishReason: String?,
        val usage: AiChatUsage?,
        val toolCalls: JsonArray
    )

    private class StreamToolCall {
        var id: String = ""
        var type: String = "function"
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()

        fun toJson(): JsonObject {
            return JsonObject().apply {
                addProperty("id", id)
                addProperty("type", type.ifBlank { "function" })
                add("function", JsonObject().apply {
                    addProperty("name", name)
                    addProperty("arguments", arguments.toString())
                })
            }
        }
    }

    companion object {

        private const val CONTEXT_COMPACTION_PROMPT_ASSET = "ai/context_compaction.md"
        private const val AGENT_RUNTIME_NOTICE_PREFIX = "[AI_RUNTIME_NOTICE]"
        private const val HARD_WINDOW_GUARD_PERCENT = 95
        private const val MCP_TOOL_PREFIX = "mcp__legado__"
        private val CONTEXT_WINDOW_ERROR_MARKERS = listOf(
            "maximum context length",
            "context length",
            "context window",
            "context_length",
            "too many tokens"
        )
        private val AGENT_SYSTEM_PROMPT = """
            你是 Legado / 阅读NG 内置 AI 助手。回答要简洁直接。

            这是 Agent 聊天专用系统提示词，只约束普通 AI 助手聊天；不要把这些规则用于 App 内部的段落净化、章节净化或其它结构化模型调用。

            MCP 使用：
            - 若用户的问题需要读取或管理 Legado 数据，并且可用 MCP 工具能完成，应优先调用 MCP 工具。
            - 不要编造工具名、参数或工具结果；工具不可用时说明限制。
            - 写入、删除、覆盖、启用、禁用、应用、回滚都属于写操作。
            - 书籍作品身份优先使用 work_key 或 name+author；book_url 是当前书源实例地址，换源后可能变化，不要把它当作跨源稳定身份。

            写操作权限：
            - App 会按当前权限模式控制写工具执行：写操作确认模式会在真实执行前弹出本地确认窗；完全信任模式会直接执行。
            - 不要根据“ok、确认、继续”等自然语言自行判断本地权限；本地权限只由 App 控制。
            - 大批量、破坏性、覆盖性、AI 生成内容应用等操作，仍应先在聊天中展示可审核内容，再等待用户明确要求执行。
            - 当你决定执行写操作时，直接调用对应写工具；如果 App 弹窗被用户取消，工具结果会返回 canceled=true，此时停止写入流程并说明已取消。
            - 写工具成功后说明写入结果；不要声称未执行的操作已经完成。

            交互协议：
            - 需要用户选择或确认时，可在正文后输出一个 ```legado-interaction 代码块。
            - 支持 type：actions、single_choice、multi_choice、multi_tag_stance、confirm。
            - 代码块必须完整闭合，必须是合法 JSON，id 稳定简短，按钮 label 要短。
            - 所有 options 项统一使用 {"label":"...","value":"..."}；不要用 id 代替 value。
            - 交互块不能替代正文说明；正文先说明依据、结果和风险。
            - 如果正文说“请选择”“请确认”“点击按钮”，必须紧跟一个完整的 legado-interaction 代码块；不要只写提示语。
            - single_choice 示例：
            ```legado-interaction
            {
              "version": 1,
              "id": "sampling_mode",
              "type": "single_choice",
              "title": "选择采样强度",
              "description": "请选择本次处理强度。",
              "options": [
                {"label": "平衡", "value": "balanced"},
                {"label": "快速", "value": "fast"},
                {"label": "深入", "value": "deep"}
              ],
              "submit": {
                "label": "开始",
                "prompt_template": "使用{{label}}模式继续"
              }
            }
            ```
            - confirm 示例：
            ```legado-interaction
            {
              "version": 1,
              "id": "apply_confirm",
              "type": "confirm",
              "title": "确认应用",
              "description": "确认后将执行上面说明的写入操作。",
              "submit": {
                "label": "应用",
                "prompt_template": "确认应用上面的内容"
              },
              "cancel": {
                "label": "取消",
                "prompt_template": "暂不应用"
              }
            }
            ```

            记忆系统：
            - 记忆属于 AI 助手功能，不属于某个 Skill。
            - 普通临时推理不强制保存记忆；用户请求或当前活动 Skill 明确要求持久语义档案时，使用通用记忆接口。
            - 查询或写入前先调用 agent_memory_status_get 检查开关。
            - 如果 enabled=false，不要调用任何记忆检索或写入工具。
            - 如果 enabled=true，并且任务有明确对象，先用 agent_memory_search 检索该对象相关记忆。scope_key 优先使用稳定自然键，例如书名+作者、书源名称+关键地址；不要只依赖易变化的 bookUrl。
            - agent_memory_upsert 保存单条持久语义；agent_memory_batch_upsert 在一个事务中保存一组相关语义。已有记忆必须复用搜索结果中的 id，避免重复创建。
            - 记忆写入仍受本地 capability 和确认策略约束；不得通过其它存储或伪装参数绕过权限。
            - Skill 的人物、关系、事实、分析与进度等业务语义若需跨轮次复用，应保存在 AgentMemory；不要同时维护另一份 Skill 专用档案。
            - 不要把角色卡、书籍、书源、规则等业务写入工具当作记忆工具。
            - 保存内容要短，记录对象、业务域、已应用结果、采样范围或后续建议，避免存入整段原文。
        """.trimIndent()

    }
}

data class AiPendingToolCall(
    val toolName: String,
    val functionName: String,
    val callId: String,
    val arguments: JsonObject,
    val sideEffect: McpToolSideEffect
)

data class AiChatTurnResult(
    val content: String,
    val reasoning: String?,
    val model: String,
    val finishReason: String?,
    val usage: AiChatUsage?,
    val toolTrace: List<String>,
    val toolReceipts: List<ToolExecutionReceipt> = emptyList(),
    val memoryTrace: List<AiMemoryTraceItem> = emptyList(),
    val warnings: List<String>,
    val contextUsage: AiChatContextUsage,
    val contextCalibration: AiChatTokenCalibration? = null,
    val compactionCount: Int = 0,
    val contextCompactions: List<AiChatCompactionRecord> = emptyList(),
    val contextMessages: List<JsonObject>
)

data class AiChatManualCompactionResult(
    val record: AiChatCompactionRecord,
    val contextUsage: AiChatContextUsage,
    val contextMessages: List<JsonObject>
)

data class AiChatStreamUpdate(
    val content: String,
    val reasoning: String?,
    val toolTrace: List<String> = emptyList(),
    val memoryTrace: List<AiMemoryTraceItem> = emptyList()
)

data class AiChatDurableContextUpdate(
    val contextMessages: List<JsonObject>,
    val visibleContent: String,
    val reasoning: String?,
    val toolTrace: List<String> = emptyList(),
    val toolReceipts: List<ToolExecutionReceipt> = emptyList(),
    val pendingReceiptIds: List<String> = emptyList()
)

data class AiMemoryTraceItem(
    @SerializedName("status")
    val status: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("detail")
    val detail: String? = null
) {
    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_SAVED = "saved"
        const val STATUS_FAILED = "failed"
    }
}

data class AiChatUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

private data class AiChatCompactionSummary(
    val content: String,
    val usage: AiChatUsage?
)
