package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "aiChatMessageNodes",
    indices = [
        Index(value = ["conversationId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AiChatConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AiChatMessageNode(
    @PrimaryKey
    val id: String,
    @ColumnInfo(defaultValue = "")
    val conversationId: String = "",
    @ColumnInfo(defaultValue = "0")
    val nodeIndex: Int = 0,
    @ColumnInfo(defaultValue = "[]")
    val messages: String = "[]",
    @ColumnInfo(defaultValue = "0")
    val selectIndex: Int = 0
)
