package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AgentToolResultArtifact
import io.legado.app.data.entities.AgentToolExecutionIntent
import io.legado.app.data.entities.AgentToolReceiptAcknowledgement

@Dao
abstract class AgentToolResultDao {

    @Query("select * from agentToolResultArtifacts where receiptId = :receiptId")
    abstract fun get(receiptId: String): AgentToolResultArtifact?

    @Query(
        "select * from agentToolResultArtifacts " +
            "where conversationId = :conversationId and turnId = :turnId and toolCallId = :toolCallId"
    )
    abstract fun getByToolCall(
        conversationId: String,
        turnId: String,
        toolCallId: String
    ): AgentToolResultArtifact?

    @Query(
        "select * from agentToolResultArtifacts " +
            "where conversationId = :conversationId and turnId = :turnId " +
            "order by createdAt asc, receiptId asc"
    )
    abstract fun listByTurn(
        conversationId: String,
        turnId: String
    ): List<AgentToolResultArtifact>

    @Query(
        "select * from agentToolResultArtifacts " +
            "where conversationId = :conversationId and turnId = :turnId " +
            "and toolName = :toolName and argumentsHash = :argumentsHash " +
            "and skillRevision = :skillRevision and contentHash = :contentHash " +
            "and success = 1 and complete = 1 " +
            "order by createdAt asc, receiptId asc limit 1"
    )
    abstract fun findReusableByTurnToolArguments(
        conversationId: String,
        turnId: String,
        toolName: String,
        argumentsHash: String,
        skillRevision: String,
        contentHash: String
    ): AgentToolResultArtifact?

    @Query("select * from agentToolExecutionIntents where receiptId = :receiptId")
    abstract fun getExecutionIntent(receiptId: String): AgentToolExecutionIntent?

    @Query(
        "select * from agentToolExecutionIntents " +
            "where conversationId = :conversationId and turnId = :turnId " +
            "and toolName = :toolName and argumentsHash = :argumentsHash " +
            "and skillRevision = :skillRevision and contentHash = :contentHash " +
            "order by createdAt asc, receiptId asc limit 1"
    )
    abstract fun findExecutionIntentByTurnToolArguments(
        conversationId: String,
        turnId: String,
        toolName: String,
        argumentsHash: String,
        skillRevision: String,
        contentHash: String
    ): AgentToolExecutionIntent?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertExecutionIntentIgnore(intent: AgentToolExecutionIntent): Long

    @Query("delete from agentToolExecutionIntents where receiptId = :receiptId")
    abstract fun deleteExecutionIntent(receiptId: String): Int

    @Query(
            "select a.* from agentToolResultArtifacts a " +
            "left join agentToolReceiptAcknowledgements m on m.receiptId = a.receiptId " +
            "where a.conversationId = :conversationId and m.receiptId is null " +
            "and a.success = 1 and a.complete = 1 " +
            "and (:skillRevision = '' or a.skillRevision = :skillRevision) " +
            "and (:contentHash = '' or a.contentHash = :contentHash) " +
            "order by a.createdAt asc, a.receiptId asc"
    )
    abstract fun listUnacknowledgedByConversation(
        conversationId: String,
        skillRevision: String,
        contentHash: String
    ): List<AgentToolResultArtifact>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertAcknowledgementsIgnore(
        acknowledgements: List<AgentToolReceiptAcknowledgement>
    ): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertIgnore(artifact: AgentToolResultArtifact): Long

    @Transaction
    open fun beginExecution(intent: AgentToolExecutionIntent): AgentToolExecutionBeginOutcome {
        get(intent.receiptId)?.let { return AgentToolExecutionBeginOutcome.Completed(it) }
        findExecutionIntentByTurnToolArguments(
            conversationId = intent.conversationId,
            turnId = intent.turnId,
            toolName = intent.toolName,
            argumentsHash = intent.argumentsHash,
            skillRevision = intent.skillRevision,
            contentHash = intent.contentHash
        )?.let { return AgentToolExecutionBeginOutcome.Indeterminate(it) }
        if (insertExecutionIntentIgnore(intent) != -1L) {
            return AgentToolExecutionBeginOutcome.Started(intent)
        }
        get(intent.receiptId)?.let { return AgentToolExecutionBeginOutcome.Completed(it) }
        return AgentToolExecutionBeginOutcome.Indeterminate(
            getExecutionIntent(intent.receiptId) ?: intent
        )
    }

    @Transaction
    open fun completeExecution(
        intentReceiptId: String,
        artifact: AgentToolResultArtifact
    ): AgentToolResultRecordOutcome {
        val outcome = record(artifact)
        if (outcome is AgentToolResultRecordOutcome.Stored) {
            deleteExecutionIntent(intentReceiptId)
        }
        return outcome
    }

    @Transaction
    open fun record(artifact: AgentToolResultArtifact): AgentToolResultRecordOutcome {
        val existingByReceipt = get(artifact.receiptId)
        if (existingByReceipt != null) {
            return if (existingByReceipt.sameExecutionAs(artifact)) {
                AgentToolResultRecordOutcome.Stored(existingByReceipt, replayed = true)
            } else {
                AgentToolResultRecordOutcome.Conflict(existingByReceipt)
            }
        }
        val existingByToolCall = getByToolCall(
            artifact.conversationId,
            artifact.turnId,
            artifact.toolCallId
        )
        if (existingByToolCall != null) {
            return if (existingByToolCall.sameExecutionAs(artifact)) {
                AgentToolResultRecordOutcome.Stored(existingByToolCall, replayed = true)
            } else {
                AgentToolResultRecordOutcome.Conflict(existingByToolCall)
            }
        }
        if (insertIgnore(artifact) != -1L) {
            return AgentToolResultRecordOutcome.Stored(artifact, replayed = false)
        }
        val raced = get(artifact.receiptId)
            ?: getByToolCall(artifact.conversationId, artifact.turnId, artifact.toolCallId)
            ?: return AgentToolResultRecordOutcome.Conflict(null)
        return if (raced.sameExecutionAs(artifact)) {
            AgentToolResultRecordOutcome.Stored(raced, replayed = true)
        } else {
            AgentToolResultRecordOutcome.Conflict(raced)
        }
    }

    private fun AgentToolResultArtifact.sameExecutionAs(other: AgentToolResultArtifact): Boolean {
        return receiptId == other.receiptId &&
            conversationId == other.conversationId &&
            turnId == other.turnId &&
            toolCallId == other.toolCallId &&
            toolName == other.toolName &&
            skillRevision == other.skillRevision &&
            contentHash == other.contentHash &&
            argumentsHash == other.argumentsHash &&
            resultHash == other.resultHash &&
            payload == other.payload &&
            success == other.success &&
            complete == other.complete
    }
}

sealed class AgentToolExecutionBeginOutcome {
    data class Started(val intent: AgentToolExecutionIntent) : AgentToolExecutionBeginOutcome()
    data class Completed(val artifact: AgentToolResultArtifact) : AgentToolExecutionBeginOutcome()
    data class Indeterminate(val intent: AgentToolExecutionIntent) : AgentToolExecutionBeginOutcome()
}

sealed class AgentToolResultRecordOutcome {
    data class Stored(
        val artifact: AgentToolResultArtifact,
        val replayed: Boolean
    ) : AgentToolResultRecordOutcome()

    data class Conflict(
        val existing: AgentToolResultArtifact?
    ) : AgentToolResultRecordOutcome()
}
