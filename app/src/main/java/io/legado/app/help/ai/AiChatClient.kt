package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.R
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
import io.legado.app.web.mcp.McpInternalChannel
import io.legado.app.web.mcp.McpInternalToolCatalog
import io.legado.app.web.mcp.McpToolSideEffect
import io.legado.app.web.mcp.McpToolExecutionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import splitties.init.appCtx
import java.io.IOException
import java.io.InterruptedIOException

private const val MODEL_GENERATION_TEST_TIMEOUT_SECONDS = 20
private const val CHAT_STREAM_TAIL_TIMEOUT_NANOS = 1_000_000_000L

internal class AiChatStreamResponseException(message: String) : IOException(message)

private class AiChatHttpResponseException(val status: Int) : IOException()

class AiChatClient internal constructor(
    private val externalSearchClient: () -> AiExternalSearchClient = { AiExternalSearchClient() },
    private val clientForTimeout: (Int) -> OkHttpClient,
) {

    constructor() : this(clientForTimeout = ::aiHttpClient)

    internal suspend fun testModelGeneration(
        setting: AiProviderSetting,
        model: AiModel,
        stream: Boolean,
        protocol: AiModelGenerationTestProtocol = setting.resolveModelGenerationRoute(model, stream = stream).protocol,
    ): AiModelGenerationTestResult {
        currentCoroutineContext().ensureActive()
        if (!setting.hasRequestCredentials()) {
            return AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.MISSING_API_KEY)
        }
        when (setting.resolveModelGenerationRoute(model, protocolOverride = protocol, stream = stream).issue) {
            AiModelGenerationIssue.UNSUPPORTED_PROTOCOL ->
                return AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.UNSUPPORTED_PROTOCOL)
            AiModelGenerationIssue.STREAMING_REQUIRED ->
                return AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.STREAMING_REQUIRED)
            null -> Unit
        }
        if (setting.type == AiProviderType.OPENAI && setting.generationApiPath(protocol) == null) {
            return AiModelGenerationTestResult.Failure(AiModelGenerationTestFailure.INVALID_ENDPOINT)
        }
        val messages = listOf(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", "请只回复 OK。")
        })
        var partialReply = ""
        return try {
            val timeoutSeconds = minOf(setting.timeoutSeconds, MODEL_GENERATION_TEST_TIMEOUT_SECONDS)
            val completion = aiHttpClient(timeoutSeconds).newBuilder()
                .retryOnConnectionFailure(false)
                .build()
                .completeChat(
                    setting = setting.copy(streamResponseEnabled = stream),
                    model = model,
                    messages = messages,
                    tools = emptyList(),
                    params = AiTextParams(),
                    conversationId = null,
                    onStreamUpdate = { partialReply = it.content },
                    generationTest = true,
                    protocol = protocol,
                )
            val result = evaluateModelGenerationTestReply(
                content = completion.content,
                finishReason = completion.finishReason?.trim()?.lowercase()?.ifBlank { null },
                streamEnded = completion.streamEnded,
                toolCallReceived = completion.toolCalls.size() > 0 ||
                    completion.message.objectOrNull("function_call") != null,
                refused = completion.message.hasChatGenerationRefusal(),
            )
            if (result is AiModelGenerationTestResult.Failure && result.reply.isEmpty()) {
                result.copy(reply = partialReply.trim())
            } else {
                result
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val partial = partialReply.trim()
            val reason = when {
                error is AiChatHttpResponseException -> AiModelGenerationTestFailure.HTTP
                error is AiEndpointException -> AiModelGenerationTestFailure.INVALID_ENDPOINT
                error is InterruptedIOException -> AiModelGenerationTestFailure.TIMEOUT
                error is AiChatIncompleteStreamException || partial.isNotEmpty() ->
                    AiModelGenerationTestFailure.INTERRUPTED
                error is AiChatStreamResponseException -> AiModelGenerationTestFailure.INVALID_RESPONSE
                error is IOException -> AiModelGenerationTestFailure.NETWORK
                else -> AiModelGenerationTestFailure.INVALID_RESPONSE
            }
            AiModelGenerationTestResult.Failure(
                reason = reason,
                reply = partial,
                httpStatus = (error as? AiChatHttpResponseException)?.status,
            )
        }
    }

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
        selection: AiModelSelection = AiModelSelection(AiConfig.assistantProviderId, AiConfig.assistantModelId),
        onContextEvent: (AiChatContextEvent) -> Unit = {}
    ): AiChatManualCompactionResult {
        check(selection.providerId.isNotBlank() && selection.modelId.isNotBlank()) {
            "请先在模型设置中选择聊天模型"
        }
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error(appCtx.getString(R.string.ai_assistant_model_reselect))
        val model = resolveModel(setting, selection.modelId)
        setting.requireModelGenerationProtocol(model)
        val contextWindow = assistantContextWindow(setting, model)
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
            setting = setting,
            model = model,
            messages = messages,
            toolDefinitions = tools,
            calibration = calibration,
            stage = AiChatCompactionStage.PRE_TURN,
            onContextEvent = onContextEvent,
            conversationId = conversationId,
            contextWindow = contextWindow,
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
            contextUsage = AiChatContextManager.usage(messages, tools, contextWindow = contextWindow),
            contextMessages = messages.map { it.deepCopy().asJsonObject }
        )
    }

    internal suspend fun send(
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
        onDurableContextUpdate: suspend (AiChatDurableContextUpdate) -> Unit = {},
        onContextEvent: (AiChatContextEvent) -> Unit = {},
        enabledMcpCapabilityIds: List<String> = emptyList(),
        allowWeb: Boolean = false,
        requireWebSearch: Boolean = false,
        webUserText: String? = null,
        onWebUpdate: (AiWebActivity, AiWebAccess) -> Unit = { _, _ -> },
        selection: AiModelSelection = AiModelSelection(AiConfig.assistantProviderId, AiConfig.assistantModelId),
        onToolActivity: (String?) -> Unit = {},
        previousImageId: String? = null,
        onImageStarted: (editing: Boolean) -> Unit = {},
        onToolConfirmationRequired: suspend (List<AiPendingToolCall>) -> Boolean = { false }
    ): AiChatTurnResult {
        val user = messages.lastOrNull { it.stringOrNull("role") == "user" }
        val attachedImages = user?.chatImageAssets().orEmpty()
        if (!requireWebSearch && user?.isDirectImageAction() == true) {
            val provider = selection.requireCodexImageProvider()
            onToolActivity(CODEX_IMAGE_TOOL)
            onImageStarted(attachedImages.isNotEmpty())
            val assetId = provider.generateCodexImage(
                user.get("content").contentText(), conversationId, turnId, turnId, attachedImages
            )
            val context = messages + newAssistantMessage("[Generated image]", null)
            onDurableContextUpdate(AiChatDurableContextUpdate(
                contextMessages = context,
                visibleContent = "",
                reasoning = null,
                imageAssetIds = listOf(assetId)
            ))
            return AiChatTurnResult(
                content = "",
                reasoning = null,
                model = CODEX_IMAGE_MODEL,
                finishReason = null,
                usage = null,
                toolTrace = emptyList(),
                warnings = emptyList(),
                contextUsage = AiChatContextManager.usage(context),
                contextMessages = context,
                imageAssetIds = listOf(assetId)
            )
        }
        check(selection.providerId.isNotBlank() && selection.modelId.isNotBlank()) {
            "请先在模型设置中选择聊天模型"
        }
        val setting = AiProviderStore.provider(selection.providerId)
            ?: error(appCtx.getString(R.string.ai_assistant_model_reselect))
        val model = resolveModel(setting, selection.modelId)
        val protocol = setting.requireModelGenerationProtocol(model)
        val modelSettings = model.modelSettingsFor(setting.officialDeepSeekModelConfigEndpoint())
        val contextWindow = assistantContextWindow(setting, model)
        val assistantParams = AiTextParams(temperature = AiConfig.temperature)
        val outputParams = modelSettings?.applyOutputLimit(
            assistantParams, model.catalogInfo(modelSettings), contextWindow,
        ) ?: assistantParams
        val params = AiConfig.assistantModelReasoning(setting, model).applyTo(outputParams)
        val webRoute = setting.nativeWebRoute(model, AiModelGenerationRoute(protocol, null))
        val webPlan = AiExternalSearchStore.current().resolveWebToolPlan(
            nativeRoute = webRoute,
            model = model,
            allowed = allowWeb,
            userText = webUserText ?: messages.lastOrNull { it.stringOrNull("role") == "user" }
                ?.get("content").contentText(),
            thinkingOff = params.disableThinking && protocol != AiModelGenerationTestProtocol.CLAUDE_MESSAGES,
            requireSearch = requireWebSearch,
        )
        check(!requireWebSearch || webPlan !is AiWebToolPlan.Off) {
            appCtx.getString(R.string.ai_book_search_web_required)
        }
        val nativeWebLabel = (webPlan as? AiWebToolPlan.Native)?.let { plan ->
            appCtx.getString(R.string.ai_search_source_native, plan.sourceName)
        }
        val webEvents = nativeWebLabel?.let { label ->
            AiNativeWebEvents { onWebUpdate(it.copy(providerLabel = label), AiWebAccess.Native) }
        }
        val externalWeb = (webPlan as? AiWebToolPlan.External)?.let { plan ->
            val access = AiWebAccess.External(plan.service)
            AiExternalWebTurn(plan.service, externalSearchClient()) { update ->
                onWebUpdate(update, access)
            }
        }
        fun settledWebActivity() = externalWeb?.settled ?: webEvents?.settled?.copy(providerLabel = nativeWebLabel)
        val activeSkill = AiChatContextManager.activeSkillSnapshot(messages)
        val localTools = if (model.abilities.contains(AiModelAbility.TOOL)) {
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
        val imageProvider = if (!requireWebSearch && AiModelAbility.TOOL in model.abilities) {
            setting.availableCodexImageProvider()
        } else null
        val imageToolEnabled = imageProvider != null
        if (attachedImages.isNotEmpty() && AiModelModality.IMAGE !in model.inputModalities &&
            !imageToolEnabled
        ) throw AiChatGptException(R.string.ai_image_chat_model_required)
        val tools = buildList {
            addAll(localTools)
            addAll(externalWeb?.definitions.orEmpty())
            if (imageToolEnabled) add(codexImageToolDefinition(
                hasAttachedImages = attachedImages.isNotEmpty(),
                hasPreviousImage = previousImageId != null
            ))
        }
        val warnings = buildList {
            if (AiConfig.internalMcpEnabled && !model.abilities.contains(AiModelAbility.TOOL)) {
                add("当前模型未声明工具能力，未附加 MCP tools")
            }
            if (!AiConfig.internalMcpEnabled) {
                add("内置 MCP 未开启")
            }
        }.toMutableList()
        val client = clientForTimeout(setting.timeoutSeconds)
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
        val usageAccumulator = AiChatUsageAccumulator()
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
                setting,
                model,
                requestMessages,
                tools,
                tokenCalibration,
                AiChatCompactionStage.PRE_TURN,
                onContextEvent,
                conversationId = conversationId,
                contextWindow = contextWindow,
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
            val requestParams = if (params.maxTokens != null) {
                val usage = AiChatContextManager.usage(requestMessages, tools, tokenCalibration, contextWindow)
                val available = contextWindow - usage.estimatedTokens
                check(available > 0) { "当前上下文已超过配置的窗口，无法分配输出 tokens" }
                val maxTokens = minOf(params.maxTokens, available)
                check(params.assistantReasoningMaxTokens?.let { it < maxTokens } != false) {
                    "上下文余量不足，请调低思考预算"
                }
                params.copy(maxTokens = maxTokens)
            } else {
                params
            }
            val completion = try {
                client.completeChat(
                    setting, model, requestMessages, tools, requestParams, conversationId, onStreamUpdate,
                    assistantTurn = true,
                    protocol = protocol,
                    webRoute = webRoute,
                    webPlan = webPlan,
                    webEvents = webEvents,
                )
            } catch (error: IllegalStateException) {
                if (!contextOverflowRecoveryAttempted &&
                    AiConfig.contextCompactionEnabled &&
                    error.isContextWindowExceeded()
                ) {
                    compactIfNeeded(
                        setting = setting,
                        model = model,
                        messages = requestMessages,
                        toolDefinitions = tools,
                        calibration = tokenCalibration,
                        stage = AiChatCompactionStage.PRE_TURN,
                        onContextEvent = onContextEvent,
                        conversationId = conversationId,
                        contextWindow = contextWindow,
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
            } finally {
                webEvents?.finishResponse()
            }
            lastModel = completion.model
            usageAccumulator.add(completion.usage)
            lastReasoning = completion.reasoning
            lastFinishReason = completion.finishReason
            stepNumber += 1
            val message = completion.message
            val content = completion.content
            val toolCalls = completion.toolCalls
            if (toolCalls.size() == 0) {
                val replyContent = AiChatToolStepContent.providerProjection(content)
                durableAssistantContent = AiChatToolStepContent.bestRecoveryCandidate(
                    recoverableToolStepContents
                )?.let { earlierContent ->
                    if (finalizeStateFlushRetryCount > 0) {
                        AiChatVisibleContentRecovery.recoverAfterInternalRetry(
                            finalContent = replyContent,
                            earlierContent = earlierContent
                        )
                    } else {
                        AiChatVisibleContentRecovery.recover(replyContent, earlierContent)
                    }
                } ?: replyContent.trim()
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
                    AiChatToolStepContent.recoveryCandidate(replyContent)?.let(recoverableToolStepContents::add)
                    requestMessages.add(uploadAssistantMessage(replyContent, lastReasoning, message))
                    finalizeStateFlushRetryCount += 1
                    onDurableContextUpdate(
                        durableContextUpdate(
                            requestMessages,
                            durableAssistantContent,
                            lastReasoning,
                            toolTrace,
                            hookRuntime
                        ).copy(webActivity = settledWebActivity())
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
                requestMessages.add(uploadAssistantMessage(replyContent, lastReasoning, message))
                onDurableContextUpdate(
                    durableContextUpdate(
                        requestMessages,
                        finalContent,
                        lastReasoning,
                        toolTrace,
                        hookRuntime,
                        toolReceipts
                    ).copy(webActivity = settledWebActivity())
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
                    usage = usageAccumulator.result(),
                    toolTrace = toolTrace,
                    toolReceipts = toolReceipts.toList(),
                    memoryTrace = memoryTrace.toList(),
                    warnings = if (usageAccumulator.hasPartialCoverage) {
                        warnings + "本轮用量统计不完整，仅包含已返回的部分"
                    } else {
                        warnings
                    },
                    contextUsage = AiChatContextManager.usage(
                        contextMessages,
                        tools,
                        tokenCalibration,
                        contextWindow,
                    ),
                    contextCalibration = tokenCalibration,
                    webActivity = settledWebActivity(),
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
            onStreamUpdate(AiChatStreamUpdate(
                content = durableAssistantContent,
                reasoning = lastReasoning,
                toolTrace = toolTrace.toList(),
                memoryTrace = memoryTrace.toList(),
            ))
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
                ).copy(webActivity = settledWebActivity())
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
                    arguments = arguments,
                    externalTool = externalWeb?.tool(functionName),
                )
            }
            require(parsedToolCalls.size == toolCalls.size()) {
                "模型返回了无法解析的 tool_calls，已在执行任何工具前中止"
            }
            val preparedToolCalls = parsedToolCalls.map { call ->
                require(call.callId.isNotBlank()) { "模型返回的 tool_call id 不能为空" }
                require(call.externalTool != null || (imageToolEnabled && call.functionName == CODEX_IMAGE_TOOL) ||
                    (call.functionName.startsWith(MCP_TOOL_PREFIX) && call.toolName.isNotBlank())) {
                    "模型返回了非法或未启用的工具名"
                }
                require(seenToolCallIds.add(call.callId)) {
                    "模型在同一轮重复使用 tool_call id：${call.callId}"
                }
                val executionArguments = call.externalTool?.let {
                    requireNotNull(externalWeb).receiptArguments(it, call.arguments)
                } ?: if (call.functionName == CODEX_IMAGE_TOOL) {
                    call.arguments.deepCopy().apply { addProperty("_account", setting.chatGptAccountId) }
                } else call.arguments
                val argumentsHash = AgentToolResultStore.argumentsHash(executionArguments)
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
                        call.externalTool == null && call.toolName.requiresPerCallConfirmation(
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
                            if (call.functionName == CODEX_IMAGE_TOOL &&
                                codexImageFile(conversationId, begin.intent.receiptId).isFile
                            ) {
                                startedIntent = begin.intent
                                codexImageSavedResult()
                            } else {
                                indeterminateIntent = begin.intent
                                indeterminateToolResult(call.toolName, begin.intent.receiptId)
                            }
                        }

                        is AgentToolExecutionBeginOutcome.Started -> {
                            startedIntent = begin.intent
                            onToolActivity(call.toolName)
                            try {
                                if (call.externalTool != null) {
                                    requireNotNull(externalWeb).execute(call.externalTool, call.arguments)
                                } else if (call.functionName == CODEX_IMAGE_TOOL) {
                                    try {
                                        val references = codexImageReferences(
                                            call.arguments, attachedImages, previousImageId
                                        )
                                        onImageStarted(references.isNotEmpty())
                                        requireNotNull(imageProvider).generateCodexImage(
                                            call.arguments, conversationId, turnId, prepared.receiptId, references
                                        )
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: AiChatGptException) {
                                        codexImageError(error.message)
                                    } catch (_: Exception) {
                                        codexImageError(appCtx.getString(R.string.ai_codex_image_failed))
                                    }
                                } else runCatching {
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
                            } finally {
                                onToolActivity(null)
                            }
                        }
                    }
                }
                if (call.externalTool != null) {
                    requireNotNull(externalWeb).acceptResult(result)
                } else {
                    toolTrace.add(buildString {
                        append(call.toolName)
                        append('(').append(call.arguments.toString().take(120)).append(')')
                        if (resolvedArtifact != null) append(" [replay]")
                        if (indeterminateIntent != null) append(" [indeterminate]")
                    })
                }
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
                    calibration = tokenCalibration,
                    contextWindow = contextWindow,
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
                ).copy(webActivity = settledWebActivity())
            )
            compactIfNeeded(
                    setting,
                    model,
                    requestMessages,
                    tools,
                    tokenCalibration,
                    AiChatCompactionStage.MID_TURN,
                    onContextEvent,
                    conversationId = conversationId,
                    contextWindow = contextWindow,
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
                    ).copy(webActivity = settledWebActivity())
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
                    ).copy(webActivity = settledWebActivity())
                )
            }
            throw error
        }
    }

    private suspend fun compactIfNeeded(
        setting: AiProviderSetting,
        model: AiModel,
        messages: MutableList<JsonObject>,
        toolDefinitions: List<JsonObject>,
        calibration: AiChatTokenCalibration?,
        stage: AiChatCompactionStage,
        onContextEvent: (AiChatContextEvent) -> Unit,
        conversationId: String,
        contextWindow: Int,
        force: Boolean = false,
        beforeCompaction: () -> AgentHookEffects = { AgentHookEffects() }
    ): AiChatCompactionRecord? {
        val usage = AiChatContextManager.usage(messages, toolDefinitions, calibration, contextWindow)
        if (!AiConfig.contextCompactionEnabled && !force) {
            check(usage.estimatedTokens < usage.contextWindowTokens) {
                "当前上下文约 ${usage.estimatedTokens} tokens，已超过配置的 ${usage.contextWindowTokens} tokens 上下文窗口；请开启自动压缩或增大上下文窗口"
            }
            return null
        }
        val officialUsageReachedThreshold =
            calibration?.contextTokens?.let { it >= usage.thresholdTokens } == true
        val hardWindowGuard = usage.contextWindowTokens.toLong() * HARD_WINDOW_GUARD_PERCENT / 100
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
        val summary = createCompactionSummary(setting, model, messages, conversationId, pinnedArtifactRefs)
        val replacement = AiChatContextManager.buildCompactedHistory(
            messages,
            summary.content,
            pinnedArtifactRefs,
            contextWindow = contextWindow,
        )
        val replacementUsage = AiChatContextManager.usage(replacement, toolDefinitions, contextWindow = contextWindow)
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
        setting: AiProviderSetting,
        model: AiModel,
        messages: List<JsonObject>,
        conversationId: String,
        pinnedArtifactRefs: Set<String> = emptySet()
    ): AiChatCompactionSummary {
        val prompt = appCtx.assets.open(CONTEXT_COMPACTION_PROMPT_ASSET)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val pinnedMessages = AiChatContextManager.pinnedArtifactMessages(
            messages,
            pinnedArtifactRefs
        ).toSet()
        val compactionHistory = messages
            .filterNot { message -> message.stringOrNull("role") == "system" || message in pinnedMessages }
            .mapTo(mutableListOf()) { it.deepCopy().apply { removeNativeChatHistory() } }
        val client = aiHttpClient(setting.timeoutSeconds)
        val params = setting.compactionTextParams(model)
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
            val completion = try {
                client.completeChat(setting, model, compactionMessages, emptyList(), params, conversationId)
            } catch (error: Exception) {
                if (error !is IllegalStateException && error !is AiChatStreamResponseException) throw error
                if (!error.isContextWindowExceeded()) throw error
                val reduced = AiChatContextManager
                    .trimOldestCompactionHistoryUnit(compactionHistory) ||
                    AiChatContextManager.shrinkLargestCompactionMessage(compactionHistory)
                if (!reduced) {
                    throw IllegalStateException("压缩模型的上下文窗口不足，无法生成接续摘要", error)
                }
                continue
            }
            check(
                completion.streamEnded &&
                    completion.toolCalls.size() == 0 &&
                    completion.finishReason?.lowercase() !in INCOMPLETE_SUMMARY_FINISH_REASONS
            ) {
                "上下文压缩未完成，已保留压缩前的上下文"
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
        val errors = generateSequence(this) { it.cause }.toList()
        if (errors.any { (it as? AiChatGptException)?.providerCode == "context_length_exceeded" }) return true
        val text = errors.asSequence()
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

    fun newUserMessage(
        content: String,
        imageAssetIds: List<String> = emptyList(),
        directImageAction: Boolean = false,
    ): JsonObject {
        return JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", content)
            setChatImageAssets(imageAssetIds, directImageAction)
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
            ?: error(appCtx.getString(R.string.ai_assistant_model_reselect))
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
        return AiChatContextManager.usage(messages, tools, calibration, assistantContextWindow(setting, model))
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
        setting.assistantModelChoice(modelId)?.model?.let { return it }
        val missingName = setting.missingCatalogModelName(modelId)
        error(
            if (missingName != null) appCtx.getString(R.string.ai_assistant_model_missing, missingName)
            else appCtx.getString(R.string.ai_assistant_model_reselect)
        )
    }

    private fun assistantContextWindow(setting: AiProviderSetting, model: AiModel): Int {
        return capAssistantContextWindow(
            AiConfig.assistantContextWindowTokens,
            setting.assistantContextWindowLimit(model),
        )
    }

    private fun AiProviderSetting.requireModelGenerationProtocol(model: AiModel): AiModelGenerationTestProtocol {
        val route = resolveModelGenerationRoute(model)
        when (route.issue) {
            AiModelGenerationIssue.UNSUPPORTED_PROTOCOL ->
                error(appCtx.getString(R.string.ai_model_interface_unsupported))
            AiModelGenerationIssue.STREAMING_REQUIRED ->
                error(appCtx.getString(R.string.ai_model_streaming_required))
            null -> return route.protocol
        }
    }

    private suspend fun OkHttpClient.completeChat(
        setting: AiProviderSetting,
        model: AiModel,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        conversationId: String?,
        onStreamUpdate: (AiChatStreamUpdate) -> Unit = {},
        generationTest: Boolean = false,
        assistantTurn: Boolean = false,
        protocol: AiModelGenerationTestProtocol = setting.requireModelGenerationProtocol(model),
        webRoute: AiNativeWebRoute? = null,
        webPlan: AiWebToolPlan = AiWebToolPlan.Off,
        webEvents: AiNativeWebEvents? = null,
    ): ChatCompletionSnapshot {
        val sessions = setting.accountSessions()
        val subscription = sessions != null
        val stream = setting.streamResponseEnabled || subscription
        val apiPath = setting.generationApiPath(protocol)
        val chatGptAccessToken = if (subscription && protocol == AiModelGenerationTestProtocol.RESPONSES && apiPath != null) {
            sessions?.accessToken(setting, forInference = true)
        } else null
        val nativeWeb = webPlan as? AiWebToolPlan.Native
        val nativeTools = nativeWeb?.definitions.orEmpty()
        val deepSeekRoute = webRoute as? AiDeepSeekWebRoute
        val wireMessages = messages.prepareVisionMessages(assistantTurn && AiModelModality.IMAGE in model.inputModalities)
        val codec = when (protocol) {
            AiModelGenerationTestProtocol.CLAUDE_MESSAGES ->
                AiClaudeMessagesCodec(setting, model.id, setting.reasoningCatalogInfo(model),
                    deepSeekRoute != null, nativeTools, webEvents)
            AiModelGenerationTestProtocol.GEMINI_GENERATE_CONTENT ->
                AiGeminiContentCodec(setting, model.id, setting.reasoningCatalogInfo(model))
            AiModelGenerationTestProtocol.RESPONSES -> {
                if (apiPath == null) throw AiEndpointException("Responses API endpoint is not configured")
                AiOpenAiResponsesCodec(setting, model.id, apiPath, deepSeekRoute != null,
                    nativeTools, webEvents, chatGptAccessToken = chatGptAccessToken)
            }
            AiModelGenerationTestProtocol.CHAT_COMPLETIONS -> null
        }
        if (codec != null) {
            val providerMessages = AiChatContextManager
                .providerMessages(wireMessages, webPlan.systemInstruction)
                .map { it.asJsonObject }
            val request = codec.request(providerMessages, tools, params, stream, conversationId)
            var partialContent = ""
            var partialReasoning: String? = null
            val client = if (subscription) chatGptHttpClient(setting.timeoutSeconds) else this
            val reply = client.newCall(request).readCancellableStream { response ->
                response.use {
                    if (chatGptAccessToken != null && !it.isSuccessful) {
                        val error = chatGptRequestError(it.code, it.chatGptJson())
                        val resolved = sessions?.resolveRequestError(setting.chatGptAccountId, chatGptAccessToken, error)
                            ?: error
                        if (!generationTest) throw resolved
                    }
                    it.requireChatHttpSuccess(generationTest)
                    if (stream) {
                        it.body.charStream().buffered().use { reader ->
                            try {
                                codec.readStream(reader) { content, reasoning ->
                                    val preview = if (assistantTurn) {
                                        AiChatToolStepContent.streamingProjection(content)
                                    } else content
                                    if (assistantTurn) {
                                        partialContent = content
                                        partialReasoning = reasoning
                                    }
                                    onStreamUpdate(AiChatStreamUpdate(
                                        content = preview,
                                        reasoning = reasoning,
                                    ))
                                }
                            } catch (error: Exception) {
                                val resolved = if (chatGptAccessToken != null && error is AiChatGptException) {
                                    sessions?.resolveRequestError(setting.chatGptAccountId, chatGptAccessToken, error)
                                        ?: error
                                } else error
                                if (!assistantTurn) throw resolved
                                throwInterruptedStream(resolved, partialContent, partialReasoning)
                            }
                        }
                    } else {
                        val json = JsonParser.parseString(it.body.string()).asJsonObject
                        json.requireNoAiError()
                        codec.readJson(json)
                    }
                }
            }
            val completion = ChatCompletionSnapshot(
                reply.message, reply.content, reply.reasoning, reply.model,
                reply.finishReason, reply.usage, reply.toolCalls,
            )
            if (assistantTurn) completion.requireAssistantEnd(partialContent, partialReasoning)
            if (!generationTest) reply.requireCompleted()
            return completion
        }
        if (apiPath == null) throw AiEndpointException("Chat Completions API endpoint is not configured")
        val deepSeekHistory = deepSeekRoute?.let { AiNativeChatHistory(setting, model.id, it.endpoint) }
        val request = Request.Builder()
            .url(buildAiApiEndpoint(setting.baseUrl, apiPath))
            .addHeader("Authorization", "Bearer ${setting.apiKeyForRequest()}")
            .addOpenCodeProviderHeaders(setting, conversationId)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody(
                buildRequestBody(setting, model, wireMessages, tools, params, stream, webPlan, deepSeekHistory),
            ))
            .build()
        val reasoningKey = setting.reasoningOptions(model.id).reasoningOutputField.ifBlank { "reasoning_content" }
        val openRouterHistory = setting.openRouterChatHistory(model.id)
        val completion = if (stream) {
            executeStreamChat(request, reasoningKey, openRouterHistory, onStreamUpdate, generationTest,
                assistantTurn, deepSeekHistory, webEvents)
        } else {
            executeJsonChat(request, reasoningKey, openRouterHistory, generationTest, deepSeekHistory, webEvents)
        }
        if (assistantTurn) completion.requireAssistantEnd()
        return completion
    }

    private fun Response.requireChatHttpSuccess(generationTest: Boolean) {
        if (isSuccessful) return
        if (generationTest) throw AiChatHttpResponseException(code)
        error("HTTP $code: ${body.string().take(500)}")
    }

    internal fun buildRequestBody(
        setting: AiProviderSetting,
        model: AiModel,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        params: AiTextParams,
        stream: Boolean,
        webPlan: AiWebToolPlan = AiWebToolPlan.Off,
        deepSeekHistory: AiNativeChatHistory? = null,
    ): JsonObject {
        val reasoningOptions = if (params.assistantReasoningProtocol == AiReasoningProtocol.CONFIGURED) {
            setting.assistantReasoningFields(model.id)
        } else {
            setting.reasoningOptions(model.id)
        }
        val openRouterHistory = setting.openRouterChatHistory(model.id)
        val nativeTools = (webPlan as? AiWebToolPlan.Native)?.definitions.orEmpty()
        return JsonObject().apply {
            addProperty("model", model.id)
            add(
                "messages",
                AiChatContextManager.providerMessages(messages, webPlan.systemInstruction).apply {
                    forEach { item ->
                        val message = item.asJsonObject
                        val parts = openRouterHistory?.parts(message)
                            ?.takeIf { message.stringOrNull("role") == "assistant" }
                        val webRecord = deepSeekHistory?.deepSeekChatRecord(message)
                            ?.takeIf { message.stringOrNull("role") == "assistant" }
                        message.removeNativeChatHistory()
                        message.remove("server_tool_calls")
                        message.remove("annotations")
                        if (parts != null) message.add(OPENROUTER_REASONING_DETAILS, parts)
                        webRecord?.arrayOrNull("steps")?.let { message.add("server_tool_calls", it) }
                        webRecord?.stringOrNull("final_reasoning")?.let { message.addProperty("reasoning_content", it) }
                    }
                }
            )
            params.temperature?.let { addProperty("temperature", it) }
            params.maxTokens?.let { addProperty("max_tokens", it) }
            addProperty("stream", stream)
            if (stream && setting.supportsStreamUsage) {
                add("stream_options", JsonObject().apply {
                    addProperty("include_usage", true)
                })
            }
            addReasoningParams(params, reasoningOptions)
            if (tools.isNotEmpty() || nativeTools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    tools.forEach { add(it.deepCopy()) }
                    nativeTools.forEach { add(it.deepCopy()) }
                })
            }
        }
    }

    private suspend fun OkHttpClient.executeJsonChat(
        request: Request,
        reasoningKey: String,
        openRouterHistory: AiNativeChatHistory?,
        generationTest: Boolean,
        deepSeekHistory: AiNativeChatHistory?,
        webEvents: AiNativeWebEvents?,
    ): ChatCompletionSnapshot {
        val responseBody = if (generationTest) {
            newCall(request).readCancellableStream { response ->
                response.use {
                    it.requireChatHttpSuccess(generationTest)
                    it.body.string()
                }
            }
        } else {
            val (response, body) = executeJsonOrThrow(request)
            if (!response.isSuccessful) error("HTTP ${response.code}: ${body.take(500)}")
            body
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        if (deepSeekHistory != null) json.requireNoAiError()
        val choice = json.arrayOrNull("choices")
            ?.firstOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: error("OpenAI response missing choices")
        val message = choice.objectOrNull("message")
            ?: error("OpenAI response missing message")
        if (generationTest) {
            check(message.stringOrNull("role") in setOf(null, "assistant")) {
                "OpenAI response did not contain an assistant message"
            }
        }
        val reasoning = message.stringOrNull(reasoningKey)
            ?: message.stringOrNull("reasoning_content")
            ?: message.stringOrNull("reasoning")
        val parts = message.remove(OPENROUTER_REASONING_DETAILS)
        if (openRouterHistory != null && parts?.isJsonArray == true) {
            message.remove("reasoning")
            openRouterHistory.attachParts(message, parts.asJsonArray)
        }
        webEvents?.chatResult(message)
        val serverSteps = message.arrayOrNull("server_tool_calls")
        message.remove("server_tool_calls")
        message.remove("annotations")
        deepSeekHistory?.attachDeepSeekChatSteps(message, serverSteps, reasoning)
        val usage = json.objectOrNull("usage")?.toChatUsage()
        return ChatCompletionSnapshot(
            message = message,
            content = if (generationTest) message.get("content").chatGenerationVisibleText()
            else message.get("content").contentText(),
            reasoning = reasoning,
            model = json.stringOrNull("model"),
            finishReason = choice.stringOrNull("finish_reason"),
            usage = usage,
            toolCalls = message.arrayOrNull("tool_calls") ?: JsonArray()
        )
    }

    private suspend fun OkHttpClient.executeStreamChat(
        request: Request,
        reasoningKey: String,
        openRouterHistory: AiNativeChatHistory?,
        onStreamUpdate: (AiChatStreamUpdate) -> Unit,
        generationTest: Boolean,
        assistantTurn: Boolean,
        deepSeekHistory: AiNativeChatHistory?,
        webEvents: AiNativeWebEvents?,
    ): ChatCompletionSnapshot {
        return newCall(request).readCancellableStream { response ->
            response.use {
                val responseBody = it.body
                it.requireChatHttpSuccess(generationTest)
                var model: String? = null
                var finishReason: String? = null
                var streamDone = false
                var refused = false
                var legacyFunctionCall = false
                var usage: AiChatUsage? = null
                var tailDeadline: Long? = null
                val contentBuilder = StringBuilder()
                val reasoningBuilder = StringBuilder()
                val reasoningDetails = openRouterHistory?.let { AiOpenRouterReasoningDetails() }
                var serverSteps: JsonArray? = null
                var finalReasoningStart = 0
                val toolCalls = linkedMapOf<Int, StreamToolCall>()
                responseBody.charStream().buffered().use { reader ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (tailDeadline?.let { System.nanoTime() - it >= 0 } == true) break
                        val line = try {
                            reader.readLine()
                        } catch (error: IOException) {
                            currentCoroutineContext().ensureActive()
                            if (tailDeadline != null) break
                            if (!assistantTurn) throw error
                            throwInterruptedStream(error, contentBuilder.toString(), reasoningBuilder.toString())
                        } ?: break
                        val data = line.trim()
                            .takeIf { line -> line.startsWith("data:") }
                            ?.removePrefix("data:")
                            ?.trim()
                            ?: continue
                        if (data == "[DONE]") {
                            streamDone = true
                            break
                        }
                        val json = runCatching {
                            JsonParser.parseString(data).asJsonObject
                        }.getOrNull() ?: continue
                        json.get("error")?.let { streamError ->
                            val detail = when {
                                streamError.isJsonObject -> streamError.asJsonObject.get("message")
                                streamError.isJsonPrimitive && streamError.asJsonPrimitive.isString -> streamError
                                else -> return@let
                            }
                            val message = detail?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                                ?.asString?.trim().orEmpty()
                            if (streamError.isJsonObject || message.isNotEmpty()) {
                                val error = AiChatStreamResponseException(
                                    message.take(500).ifEmpty { "提供商流式响应出错" }
                                )
                                if (!assistantTurn) throw error
                                throwInterruptedStream(error, contentBuilder.toString(), reasoningBuilder.toString())
                            }
                        }
                        model = json.stringOrNull("model") ?: model
                        usage = json.objectOrNull("usage")?.toChatUsage() ?: usage
                        val choice = json.arrayOrNull("choices")
                            ?.firstOrNull()
                            ?.takeIf { it.isJsonObject }
                            ?.asJsonObject
                            ?: continue
                        finishReason = choice.stringOrNull("finish_reason")?.takeIf(String::isNotBlank) ?: finishReason
                        val delta = choice.objectOrNull("delta")
                            ?: choice.objectOrNull("message")
                            ?: JsonObject()
                        if (deepSeekHistory != null) {
                            serverSteps = delta.arrayOrNull("server_tool_calls") ?: serverSteps
                            if (delta.has("server_tool_calls") || delta.has("annotations")) webEvents?.chatResult(delta)
                        }
                        refused = refused || delta.hasChatGenerationRefusal()
                        val contentDelta = if (generationTest) {
                            check(delta.stringOrNull("role") in setOf(null, "assistant")) {
                                "OpenAI response did not contain an assistant message"
                            }
                            legacyFunctionCall = legacyFunctionCall || delta.objectOrNull("function_call") != null
                            delta.get("content").chatGenerationVisibleText()
                        } else {
                            delta.get("content").contentText()
                        }
                        if (contentDelta.isNotEmpty()) {
                            contentBuilder.append(contentDelta)
                        }
                        val reasoningDelta = delta.stringOrNull(reasoningKey)
                            ?: delta.stringOrNull("reasoning_content")
                            ?: delta.stringOrNull("reasoning")
                        if (!reasoningDelta.isNullOrEmpty()) {
                            reasoningBuilder.append(reasoningDelta)
                        }
                        if (deepSeekHistory != null) choice.objectOrNull("server_tool_event")?.let { event ->
                            if (event.stringOrNull("type") == "step_reasoning_done") {
                                finalReasoningStart = reasoningBuilder.length
                            }
                            webEvents?.chatProgress(event)
                        }
                        delta.arrayOrNull(OPENROUTER_REASONING_DETAILS)?.let { details ->
                            reasoningDetails?.accept(details)
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
                        // A later read failure must not override an explicit refusal or truncation.
                        if (assistantTurn && (refused || !finishReason.isNullOrBlank())) {
                            assistantUnfinishedReply(
                                finishReason = finishReason,
                                streamEnded = true,
                                refused = refused,
                                content = contentBuilder.toString(),
                                reasoning = reasoningBuilder.toString(),
                            )?.let { throw it }
                        }
                        if (contentDelta.isNotEmpty() || !reasoningDelta.isNullOrEmpty()) {
                            val content = contentBuilder.toString()
                            onStreamUpdate(
                                AiChatStreamUpdate(
                                    content = if (assistantTurn) AiChatToolStepContent.streamingProjection(content)
                                    else content,
                                    reasoning = reasoningBuilder.toString()
                                        .takeIf { text -> text.isNotBlank() }
                                )
                            )
                        }
                        if (finishReason != null && tailDeadline == null) {
                            // Allow the usage tail without relying on the gateway to close SSE.
                            if (generationTest) break
                            val timeout = responseBody.source().timeout()
                            val deadline = System.nanoTime() + CHAT_STREAM_TAIL_TIMEOUT_NANOS
                            val boundedDeadline = if (timeout.hasDeadline()) minOf(deadline, timeout.deadlineNanoTime())
                            else deadline
                            timeout.deadlineNanoTime(boundedDeadline)
                            tailDeadline = boundedDeadline
                        }
                    }
                }
                val toolCallArray = JsonArray().apply {
                    toolCalls.toSortedMap().values.forEach { add(it.toJson()) }
                }
                val content = contentBuilder.toString()
                val reasoning = reasoningBuilder.toString().takeIf { text -> text.isNotBlank() }
                val message = JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", content)
                    reasoning?.let { value -> addProperty("reasoning_content", value) }
                    if (refused) addProperty("refusal", "refusal")
                    if (legacyFunctionCall) add("function_call", JsonObject())
                    if (toolCallArray.size() > 0) {
                        add("tool_calls", toolCallArray)
                    }
                    reasoningDetails?.result()?.let { openRouterHistory?.attachParts(this, it) }
                    // The separate step records carry all thinking before the last tool call.
                    deepSeekHistory?.attachDeepSeekChatSteps(this, serverSteps, reasoning?.substring(finalReasoningStart))
                }
                ChatCompletionSnapshot(
                    message = message,
                    content = content,
                    reasoning = reasoning,
                    model = model,
                    finishReason = finishReason,
                    usage = usage,
                    toolCalls = toolCallArray,
                    streamEnded = streamDone || !finishReason.isNullOrBlank(),
                )
            }
        }
    }

    private suspend fun throwInterruptedStream(error: Exception, content: String, reasoning: String?): Nothing {
        currentCoroutineContext().ensureActive()
        if (error is CancellationException) throw error
        val preview = AiChatToolStepContent.streamingProjection(content)
        if (preview.isBlank() && error !is AiChatIncompleteStreamException) throw error
        throw AiAssistantReplyEndException(AiAssistantReplyEndReason.INTERRUPTED, preview, reasoning, cause = error)
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

    private fun uploadAssistantMessage(
        content: String,
        reasoning: String?,
        providerMessage: JsonObject? = null,
    ): JsonObject {
        return JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content)
            if (!reasoning.isNullOrBlank()) {
                addProperty("reasoning_content", reasoning)
            }
            providerMessage?.objectOrNull(AI_NATIVE_HISTORY)?.let {
                add(AI_NATIVE_HISTORY, it.deepCopy())
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
        calibration: AiChatTokenCalibration?,
        contextWindow: Int,
    ): Int {
        val usage = AiChatContextManager.usage(messages, toolDefinitions, calibration, contextWindow)
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
        val arguments: JsonObject,
        val externalTool: AiExternalSearchTool?,
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
        val toolCalls: JsonArray,
        val streamEnded: Boolean = true,
    ) {
        fun requireAssistantEnd(partialContent: String = "", partialReasoning: String? = null) {
            assistantUnfinishedReply(
                finishReason, streamEnded, message.hasChatGenerationRefusal(),
                content.ifBlank { partialContent }, reasoning ?: partialReasoning,
            )?.let { throw it }
        }
    }

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
        private val INCOMPLETE_SUMMARY_FINISH_REASONS = setOf(
            "length", "content_filter", "refusal", "aborted", "cancelled", "error",
            "incomplete", "insufficient_system_resource", "tool_calls", "function_call",
        )
        private val CONTEXT_WINDOW_ERROR_MARKERS = listOf(
            "maximum context length",
            "context length",
            "context window",
            "context_length",
            "too many tokens",
            "prompt is too long",
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
    val contextMessages: List<JsonObject>,
    val webActivity: AiWebActivity? = null,
    val imageAssetIds: List<String> = emptyList(),
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
    val pendingReceiptIds: List<String> = emptyList(),
    val webActivity: AiWebActivity? = null,
    val imageAssetIds: List<String> = emptyList(),
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

/**
 * Accumulates provider usage across every model call in one turn.
 *
 * Usage fields are tracked independently because providers may report only some of
 * them. Fields never reported stay `null`; partially reported fields retain their
 * known subtotal and set [hasPartialCoverage] so the caller can label it.
 * `total_tokens` uses the provider-reported value when present; otherwise it is
 * derived per call from prompt + completion only when both are known.
 */
internal class AiChatUsageAccumulator {
    private var promptTokens = 0
    private var completionTokens = 0
    private var totalTokens = 0
    private var promptReports = 0
    private var completionReports = 0
    private var totalReports = 0

    var callsWithUsage = 0
        private set

    var callsWithoutUsage = 0
        private set

    /** True when any reported field omits one or more model calls. */
    val hasPartialCoverage: Boolean
        get() {
            val callCount = callsWithUsage + callsWithoutUsage
            return promptReports in 1 until callCount ||
                completionReports in 1 until callCount ||
                totalReports in 1 until callCount
        }

    fun add(usage: AiChatUsage?) {
        val prompt = usage?.promptTokens
        val completion = usage?.completionTokens
        val reportedTotal = usage?.totalTokens
        if (prompt == null && completion == null && reportedTotal == null) {
            callsWithoutUsage++
            return
        }
        callsWithUsage++
        if (prompt != null) {
            promptReports++
            promptTokens += prompt
        }
        if (completion != null) {
            completionReports++
            completionTokens += completion
        }
        val callTotal = reportedTotal ?: if (prompt != null && completion != null) {
            prompt + completion
        } else {
            null
        }
        if (callTotal != null) {
            totalReports++
            totalTokens += callTotal
        }
    }

    fun result(): AiChatUsage? {
        if (callsWithUsage == 0) return null
        return AiChatUsage(
            promptTokens = promptTokens.takeIf { promptReports > 0 },
            completionTokens = completionTokens.takeIf { completionReports > 0 },
            totalTokens = totalTokens.takeIf { totalReports > 0 }
        )
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
