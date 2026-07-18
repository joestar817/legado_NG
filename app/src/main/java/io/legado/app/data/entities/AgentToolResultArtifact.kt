package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agentToolResultArtifacts",
    indices = [
        Index(value = ["conversationId", "turnId", "toolCallId"], unique = true),
        Index(value = ["turnId"]),
        Index(value = ["createdAt"])
    ]
)
data class AgentToolResultArtifact(
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
    val skillRevision: String = "",
    @ColumnInfo(defaultValue = "")
    val contentHash: String = "",
    @ColumnInfo(defaultValue = "")
    val argumentsHash: String,
    @ColumnInfo(defaultValue = "")
    val resultHash: String,
    @ColumnInfo(defaultValue = "")
    val payload: String,
    @ColumnInfo(defaultValue = "0")
    val success: Boolean,
    @ColumnInfo(defaultValue = "0")
    val complete: Boolean,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long
)
