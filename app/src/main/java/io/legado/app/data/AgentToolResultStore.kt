package io.legado.app.data

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import io.legado.app.data.dao.AgentToolResultRecordOutcome
import io.legado.app.data.entities.AgentToolResultArtifact
import io.legado.app.data.entities.AgentToolExecutionIntent
import io.legado.app.data.entities.AgentToolReceiptAcknowledgement
import io.legado.app.data.dao.AgentToolExecutionBeginOutcome
import java.security.MessageDigest

/**
 * Immutable store for generic tool execution artifacts.
 *
 * The payload is intentionally opaque: callers decide its JSON/text format while the store only
 * verifies identity and content hashes. A receipt id or a conversation/tool-call pair can never be
 * silently rebound to different execution data.
 */
object AgentToolResultStore {

    private const val MAX_ID_LENGTH = 160
    private const val MAX_TOOL_NAME_LENGTH = 256
    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

    fun deterministicReceiptId(
        conversationId: String,
        turnId: String,
        toolCallId: String,
        argumentsHash: String
    ): String {
        requireStableId("conversation_id", conversationId)
        requireStableId("turn_id", turnId)
        requireStableId("tool_call_id", toolCallId)
        require(SHA256_PATTERN.matches(argumentsHash)) {
            "arguments_hash must be a SHA-256 hex digest"
        }
        val identity = listOf(conversationId, turnId, toolCallId, argumentsHash.lowercase())
            .joinToString(separator = "") { "${it.length}:$it" }
        return "tr:" + sha256(identity)
    }

    fun payloadHash(payload: String): String = sha256(payload)

    /**
     * Hash tool arguments by their JSON value rather than Gson's object insertion order.
     * Object keys are sorted recursively; array order and primitive representation stay intact.
     */
    fun argumentsHash(arguments: JsonElement): String = sha256(canonicalJson(arguments))

    fun recordAndGet(artifact: AgentToolResultArtifact): AgentToolResultArtifact {
        val normalized = validateAndNormalize(artifact)
        return when (val outcome = appDb.agentToolResultDao.record(normalized)) {
            is AgentToolResultRecordOutcome.Stored -> outcome.artifact
            is AgentToolResultRecordOutcome.Conflict -> {
                throw AgentToolResultConflictException(
                    receiptId = normalized.receiptId,
                    existingReceiptId = outcome.existing?.receiptId
                )
            }
        }
    }

    fun beginExecution(intent: AgentToolExecutionIntent): AgentToolExecutionBeginOutcome {
        requireStableId("receipt_id", intent.receiptId)
        requireStableId("conversation_id", intent.conversationId)
        requireStableId("turn_id", intent.turnId)
        requireStableId("tool_call_id", intent.toolCallId)
        require(intent.toolName.isNotBlank() && intent.toolName.length <= MAX_TOOL_NAME_LENGTH) {
            "tool_name is required and must contain at most $MAX_TOOL_NAME_LENGTH characters"
        }
        require(SHA256_PATTERN.matches(intent.argumentsHash)) {
            "arguments_hash must be a SHA-256 hex digest"
        }
        if (intent.contentHash.isNotEmpty()) {
            require(SHA256_PATTERN.matches(intent.contentHash)) {
                "content_hash must be empty or a SHA-256 hex digest"
            }
        }
        return appDb.agentToolResultDao.beginExecution(intent)
    }

    fun completeExecution(
        intentReceiptId: String,
        artifact: AgentToolResultArtifact
    ): AgentToolResultArtifact {
        val normalized = validateAndNormalize(artifact)
        return when (
            val outcome = appDb.agentToolResultDao.completeExecution(
                intentReceiptId,
                normalized
            )
        ) {
            is io.legado.app.data.dao.AgentToolResultRecordOutcome.Stored -> outcome.artifact
            is io.legado.app.data.dao.AgentToolResultRecordOutcome.Conflict -> {
                throw IllegalStateException(
                    "Tool execution receipt conflict: ${normalized.receiptId}; " +
                        "existing=${outcome.existing?.receiptId ?: "missing"}"
                )
            }
        }
    }

    fun get(receiptId: String): AgentToolResultArtifact? = appDb.agentToolResultDao.get(receiptId)

    fun listByTurn(conversationId: String, turnId: String): List<AgentToolResultArtifact> {
        return appDb.agentToolResultDao.listByTurn(conversationId, turnId)
    }

    fun findReusableByTurnToolArguments(
        conversationId: String,
        turnId: String,
        toolName: String,
        argumentsHash: String,
        skillRevision: String,
        contentHash: String
    ): AgentToolResultArtifact? {
        return appDb.agentToolResultDao.findReusableByTurnToolArguments(
            conversationId = conversationId,
            turnId = turnId,
            toolName = toolName,
            argumentsHash = argumentsHash,
            skillRevision = skillRevision,
            contentHash = contentHash
        )
    }

    fun listUnacknowledgedByConversation(
        conversationId: String,
        skillRevision: String = "",
        contentHash: String = "",
        toolNames: Set<String>? = null
    ): List<AgentToolResultArtifact> {
        val artifacts = appDb.agentToolResultDao.listUnacknowledgedByConversation(
            conversationId = conversationId,
            skillRevision = skillRevision,
            contentHash = contentHash
        )
        return toolNames?.let { allowed ->
            if (allowed.isEmpty()) emptyList() else artifacts.filter { it.toolName in allowed }
        } ?: artifacts
    }

    /**
     * 将真实、成功且完整的工具结果关联到消费它们的通用 Kernel 能力。
     * 调用方负责把业务语义写入自己的唯一存储；这里仅保存可恢复的执行回执关系。
     */
    fun acknowledgeSuccessfulComplete(
        receiptIds: Collection<String>,
        consumerType: String,
        consumerKeys: Collection<String>,
        conversationId: String,
        skillRevision: String = "",
        contentHash: String = "",
        createdAt: Long = System.currentTimeMillis()
    ): List<String> {
        val normalizedReceiptIds = receiptIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (normalizedReceiptIds.isEmpty()) return emptyList()
        val normalizedConsumerType = consumerType.trim()
        val normalizedConsumerKeys = consumerKeys.map(String::trim).filter(String::isNotBlank).distinct()
        requireStableId("conversation_id", conversationId)
        requireStableId("consumer_type", normalizedConsumerType)
        require(normalizedConsumerKeys.isNotEmpty()) { "consumer_keys must not be empty" }
        normalizedConsumerKeys.forEach { requireStableId("consumer_key", it) }
        val normalizedSkillRevision = skillRevision.trim()
        val normalizedContentHash = contentHash.trim().lowercase()
        if (normalizedContentHash.isNotEmpty()) {
            require(SHA256_PATTERN.matches(normalizedContentHash)) {
                "content_hash must be empty or a SHA-256 hex digest"
            }
        }
        val artifacts = normalizedReceiptIds.map { receiptId ->
            requireStableId("receipt_id", receiptId)
            val artifact = get(receiptId)
                ?: throw IllegalArgumentException("source receipt does not exist: $receiptId")
            require(artifact.success && artifact.complete) {
                "source receipt must be successful and complete: $receiptId"
            }
            require(artifact.conversationId == conversationId.trim()) {
                "source receipt belongs to another conversation: $receiptId"
            }
            if (normalizedSkillRevision.isNotEmpty()) {
                require(artifact.skillRevision == normalizedSkillRevision) {
                    "source receipt belongs to another workflow revision: $receiptId"
                }
            }
            if (normalizedContentHash.isNotEmpty()) {
                require(artifact.contentHash == normalizedContentHash) {
                    "source receipt belongs to another workflow content hash: $receiptId"
                }
            }
            artifact
        }
        appDb.agentToolResultDao.insertAcknowledgementsIgnore(
            artifacts.flatMap { artifact ->
                normalizedConsumerKeys.map { consumerKey ->
                    AgentToolReceiptAcknowledgement(
                        receiptId = artifact.receiptId,
                        consumerType = normalizedConsumerType,
                        consumerKey = consumerKey,
                        conversationId = artifact.conversationId,
                        createdAt = createdAt
                    )
                }
            }
        )
        return artifacts.map { it.receiptId }
    }

    internal fun validateAndNormalize(artifact: AgentToolResultArtifact): AgentToolResultArtifact {
        requireStableId("receipt_id", artifact.receiptId)
        requireStableId("conversation_id", artifact.conversationId)
        requireStableId("turn_id", artifact.turnId)
        requireStableId("tool_call_id", artifact.toolCallId)
        require(artifact.toolName.isNotBlank()) { "tool_name is required" }
        require(artifact.toolName.length <= MAX_TOOL_NAME_LENGTH) {
            "tool_name must contain at most $MAX_TOOL_NAME_LENGTH characters"
        }
        require(!artifact.toolName.hasControlCharacters()) {
            "tool_name must not contain control characters"
        }
        require(artifact.skillRevision.length <= MAX_ID_LENGTH) {
            "skill_revision must contain at most $MAX_ID_LENGTH characters"
        }
        require(!artifact.skillRevision.hasControlCharacters()) {
            "skill_revision must not contain control characters"
        }
        if (artifact.contentHash.isNotEmpty()) {
            require(SHA256_PATTERN.matches(artifact.contentHash)) {
                "content_hash must be empty or a SHA-256 hex digest"
            }
        }
        require(SHA256_PATTERN.matches(artifact.argumentsHash)) {
            "arguments_hash must be a SHA-256 hex digest"
        }
        val expectedReceiptId = deterministicReceiptId(
            artifact.conversationId.trim(),
            artifact.turnId.trim(),
            artifact.toolCallId.trim(),
            artifact.argumentsHash.lowercase()
        )
        require(artifact.receiptId.trim() == expectedReceiptId) {
            "receipt_id does not match the deterministic execution identity"
        }
        require(SHA256_PATTERN.matches(artifact.resultHash)) {
            "result_hash must be a SHA-256 hex digest"
        }
        val actualResultHash = payloadHash(artifact.payload)
        require(artifact.resultHash.equals(actualResultHash, ignoreCase = true)) {
            "result_hash does not match payload"
        }
        require(artifact.createdAt >= 0L) { "created_at must not be negative" }
        return artifact.copy(
            receiptId = artifact.receiptId.trim(),
            conversationId = artifact.conversationId.trim(),
            turnId = artifact.turnId.trim(),
            toolCallId = artifact.toolCallId.trim(),
            toolName = artifact.toolName.trim(),
            skillRevision = artifact.skillRevision.trim(),
            contentHash = artifact.contentHash.lowercase(),
            argumentsHash = artifact.argumentsHash.lowercase(),
            resultHash = artifact.resultHash.lowercase()
        )
    }

    internal fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun canonicalJson(value: JsonElement): String = when {
        value.isJsonNull -> JsonNull.INSTANCE.toString()
        value.isJsonPrimitive -> value.asJsonPrimitive.toString()
        value.isJsonArray -> value.asJsonArray.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { item -> canonicalJson(item) }
        value.isJsonObject -> value.asJsonObject.entrySet()
            .sortedBy { (key, _) -> key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, item) ->
                "${JsonPrimitive(key)}:${canonicalJson(item)}"
            }
        else -> error("Unsupported JSON element")
    }

    private fun requireStableId(name: String, value: String) {
        require(value.isNotBlank()) { "$name is required" }
        require(value.length <= MAX_ID_LENGTH) {
            "$name must contain at most $MAX_ID_LENGTH characters"
        }
        require(!value.hasControlCharacters()) { "$name must not contain control characters" }
    }

    private fun String.hasControlCharacters(): Boolean = any { it.code < 0x20 || it.code == 0x7f }
}

class AgentToolResultConflictException(
    val receiptId: String,
    val existingReceiptId: String?
) : IllegalStateException(
    "tool result receipt conflicts with an existing execution: " +
        "receipt_id=$receiptId, existing_receipt_id=${existingReceiptId ?: "unknown"}"
)
