package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aiChatConversations")
data class AiChatConversation(
    @PrimaryKey
    val id: String,
    @ColumnInfo(defaultValue = "default")
    val assistantId: String = "default",
    @ColumnInfo(defaultValue = "")
    val title: String = "",
    @ColumnInfo(defaultValue = "0")
    val createAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val updateAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val customSystemPrompt: String = "",
    @ColumnInfo(defaultValue = "[]")
    val uploadMessages: String = "[]"
)
