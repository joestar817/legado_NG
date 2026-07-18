package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agentMemories",
    indices = [
        Index(value = ["scopeType", "scopeKey"]),
        Index(value = ["domain", "memoryType", "status"]),
        Index(value = ["updatedAt"])
    ]
)
data class AgentMemory(
    @PrimaryKey
    var id: String = "",
    @ColumnInfo(defaultValue = "")
    var scopeType: String = "",
    @ColumnInfo(defaultValue = "")
    var scopeKey: String = "",
    @ColumnInfo(defaultValue = "")
    var subject: String = "",
    @ColumnInfo(defaultValue = "")
    var domain: String = "",
    @ColumnInfo(defaultValue = "note")
    var memoryType: String = "note",
    @ColumnInfo(defaultValue = "")
    var title: String = "",
    @ColumnInfo(defaultValue = "")
    var content: String = "",
    @ColumnInfo(defaultValue = "{}")
    var dataJson: String = "{}",
    @ColumnInfo(defaultValue = "")
    var tags: String = "",
    @ColumnInfo(defaultValue = "1")
    var confidence: Float = 1f,
    @ColumnInfo(defaultValue = "ai")
    var source: String = "ai",
    @ColumnInfo(defaultValue = "active")
    var status: String = "active",
    @ColumnInfo(defaultValue = "0")
    var createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis()
)
