package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 通用工具结果消费关系，只记录执行回执被哪个 Kernel 能力消费。
 *
 * 它不保存扫书/角色卡语义；语义内容仍只存在 AgentMemory。
 */
@Entity(
    tableName = "agentToolReceiptAcknowledgements",
    primaryKeys = ["receiptId", "consumerType", "consumerKey"],
    foreignKeys = [
        ForeignKey(
            entity = AgentToolResultArtifact::class,
            parentColumns = ["receiptId"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["receiptId"]),
        Index(value = ["conversationId"]),
        Index(value = ["consumerType", "consumerKey"])
    ]
)
data class AgentToolReceiptAcknowledgement(
    @ColumnInfo(defaultValue = "")
    val receiptId: String,
    @ColumnInfo(defaultValue = "")
    val consumerType: String,
    @ColumnInfo(defaultValue = "")
    val consumerKey: String,
    @ColumnInfo(defaultValue = "")
    val conversationId: String,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long
)
