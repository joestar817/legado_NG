package io.legado.app.help.ai.runtime

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

enum class AgentHookPhase {
    @SerializedName("turn_opened")
    TURN_OPENED,

    @SerializedName("turn_resumed")
    TURN_RESUMED,

    @SerializedName("tool_call_proposed")
    TOOL_CALL_PROPOSED,

    @SerializedName("tool_result_committed")
    TOOL_RESULT_COMMITTED,

    @SerializedName("before_compaction")
    BEFORE_COMPACTION,

    @SerializedName("before_turn_finalize")
    BEFORE_TURN_FINALIZE,

    @SerializedName("turn_aborted")
    TURN_ABORTED
}

enum class ToolExecutionStatus {
    @SerializedName("succeeded")
    SUCCEEDED,

    @SerializedName("failed")
    FAILED,

    @SerializedName("canceled")
    CANCELED
}

enum class ToolApprovalStatus {
    @SerializedName("not_required")
    NOT_REQUIRED,

    @SerializedName("approved")
    APPROVED,

    @SerializedName("rejected")
    REJECTED
}

data class ToolExecutionReceipt(
    @SerializedName("receipt_id")
    val receiptId: String,
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("turn_id")
    val turnId: String,
    @SerializedName("step_id")
    val stepId: String,
    @SerializedName("tool_call_id")
    val toolCallId: String,
    @SerializedName("tool_name")
    val toolName: String,
    @SerializedName("arguments_hash")
    val argumentsHash: String,
    @SerializedName("result_hash")
    val resultHash: String? = null,
    @SerializedName("status")
    val status: ToolExecutionStatus,
    @SerializedName("approval_status")
    val approvalStatus: ToolApprovalStatus = ToolApprovalStatus.NOT_REQUIRED,
    @SerializedName("result_truncated")
    val resultTruncated: Boolean = false,
    @SerializedName("artifact_refs")
    val artifactRefs: List<String> = emptyList(),
    @SerializedName("acknowledged_receipt_ids")
    val acknowledgedReceiptIds: List<String> = emptyList(),
    @SerializedName("error")
    val error: String? = null,
    @SerializedName("created_at")
    val createdAt: Long = 0L
) {
    val isSuccessfulAndComplete: Boolean
        get() = status == ToolExecutionStatus.SUCCEEDED && !resultTruncated
}

data class AgentHookEvent(
    @SerializedName("event_id")
    val eventId: String,
    @SerializedName("phase")
    val phase: AgentHookPhase,
    @SerializedName("conversation_id")
    val conversationId: String,
    @SerializedName("turn_id")
    val turnId: String,
    @SerializedName("step_id")
    val stepId: String? = null,
    @SerializedName("skill_revision")
    val skillRevision: String? = null,
    @SerializedName("skill_content_hash")
    val skillContentHash: String? = null,
    @SerializedName("authorized_capability_ids")
    val authorizedCapabilityIds: List<String> = emptyList(),
    @SerializedName("proposed_tool_name")
    val proposedToolName: String? = null,
    @SerializedName("receipt")
    val receipt: ToolExecutionReceipt? = null,
    @SerializedName("pending_receipt_ids")
    val pendingReceiptIds: List<String> = emptyList(),
    @SerializedName("pending_artifact_refs")
    val pendingArtifactRefs: List<String> = emptyList()
)

sealed interface AgentHookResult {

    data object Continue : AgentHookResult

    data class InjectNotice(
        @SerializedName("notice")
        val notice: String
    ) : AgentHookResult

    data class PinArtifacts(
        @SerializedName("artifact_refs")
        val artifactRefs: List<String>
    ) : AgentHookResult

    data class BlockBoundary(
        @SerializedName("reason_code")
        val reasonCode: String,
        @SerializedName("message")
        val message: String,
        @SerializedName("recovery_instruction")
        val recoveryInstruction: String? = null,
        @SerializedName("recoverable")
        val recoverable: Boolean = true,
        @SerializedName("pending_receipt_ids")
        val pendingReceiptIds: List<String> = emptyList()
    ) : AgentHookResult
}

data class AgentHookEffects(
    val notices: List<String> = emptyList(),
    val pinnedArtifactRefs: List<String> = emptyList(),
    val block: AgentHookResult.BlockBoundary? = null
)

data class AgentHookBinding(
    @SerializedName("id")
    val id: String,
    @SerializedName("version")
    val version: Int,
    @SerializedName("config")
    val config: JsonObject = JsonObject()
)

data class AgentSkillRuntimeDeclaration(
    @SerializedName("mcp_capabilities")
    val mcpCapabilities: List<String> = emptyList(),
    @SerializedName("runtime_hooks")
    val runtimeHooks: List<AgentHookBinding> = emptyList()
)

data class AgentHookInvocationResult(
    @SerializedName("event_id")
    val eventId: String,
    @SerializedName("hook_id")
    val hookId: String,
    @SerializedName("hook_version")
    val hookVersion: Int,
    @SerializedName("result")
    val result: AgentHookResult
)

data class AgentHookStateSnapshot(
    @SerializedName("hook_id")
    val hookId: String,
    @SerializedName("hook_version")
    val hookVersion: Int,
    @SerializedName("pending_receipt_ids")
    val pendingReceiptIds: List<String> = emptyList()
)

interface AgentRuntimeHook {
    val id: String
    val version: Int

    fun onEvent(event: AgentHookEvent): AgentHookResult

    fun snapshot(): AgentHookStateSnapshot? = null
}

interface AgentRuntimeHookFactory {
    val id: String
    val version: Int

    fun validateConfig(config: JsonObject): JsonObject

    fun referencedToolNames(config: JsonObject): Set<String> = emptySet()

    /**
     * 返回该 Hook 需要跨轮次恢复为 pending 的工具结果类型。
     * 持久层保存所有工具回执，只有这里声明的结果才进入 Hook 恢复快照。
     */
    fun pendingReceiptToolNames(config: JsonObject): Set<String> = emptySet()

    fun create(config: JsonObject): AgentRuntimeHook
}
