package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agentToolExecutionIntents",
    indices = [
        Index(value = ["conversationId", "turnId", "toolName", "argumentsHash"]),
        Index(value = ["createdAt"])
    ]
)
data class AgentToolExecutionIntent(
    @PrimaryKey
    @ColumnInfo(defaultValue = "")
    val receiptId: String,
    @ColumnInfo(defaultValue = "")
    val conversationId: String,
    @ColumnInfo(defaultValue = "")
    val turnId: String,
    @ColumnInfo(defaultValue = "")
    val toolCallId: String,
    @ColumnInfo(defaultValue = "")
    val toolName: String,
    @ColumnInfo(defaultValue = "")
    val skillRevision: String,
    @ColumnInfo(defaultValue = "")
    val contentHash: String,
    @ColumnInfo(defaultValue = "")
    val argumentsHash: String,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long
)
