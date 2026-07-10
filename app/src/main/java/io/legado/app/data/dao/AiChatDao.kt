package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessageNode

@Dao
interface AiChatDao {

    @Query(
        "select id, assistantId, title, createAt, updateAt, isPinned, " +
            "customSystemPrompt, loadedSkillIds from aiChatConversations " +
            "order by isPinned desc, updateAt desc limit :limit"
    )
    fun getConversationMetadata(limit: Int = 100): List<AiChatConversationMetadata>

    @Query(
        "select id, assistantId, title, createAt, updateAt, isPinned, " +
            "customSystemPrompt, loadedSkillIds from aiChatConversations where id = :id"
    )
    fun getConversationMetadata(id: String): AiChatConversationMetadata?

    @Query("select length(uploadMessages) from aiChatConversations where id = :id")
    fun getUploadMessagesLength(id: String): Int?

    @Query(
        "select substr(uploadMessages, :start, :length) " +
            "from aiChatConversations where id = :id"
    )
    fun getUploadMessagesChunk(id: String, start: Int, length: Int): String?

    @Transaction
    fun getConversations(limit: Int = 100): List<AiChatConversation> {
        return getConversationMetadata(limit).map { metadata ->
            metadata.toConversation(readUploadMessages(metadata.id))
        }
    }

    @Transaction
    fun getConversation(id: String): AiChatConversation? {
        val metadata = getConversationMetadata(id) ?: return null
        return metadata.toConversation(readUploadMessages(id))
    }

    @Query("select count(*) from aiChatConversations")
    fun countConversations(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConversation(conversation: AiChatConversation)

    @Query("delete from aiChatConversations where id = :id")
    fun deleteConversation(id: String)

    @Query("update aiChatConversations set isPinned = :isPinned, updateAt = :updateAt where id = :id")
    fun updatePinned(id: String, isPinned: Boolean, updateAt: Long = System.currentTimeMillis())

    @Query("select * from aiChatMessageNodes where conversationId = :conversationId order by nodeIndex asc")
    fun getMessageNodes(conversationId: String): List<AiChatMessageNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessageNodes(nodes: List<AiChatMessageNode>)

    @Query("delete from aiChatMessageNodes where conversationId = :conversationId")
    fun deleteMessageNodes(conversationId: String)

    @Transaction
    fun replaceConversation(conversation: AiChatConversation, nodes: List<AiChatMessageNode>) {
        insertConversation(conversation)
        deleteMessageNodes(conversation.id)
        if (nodes.isNotEmpty()) {
            insertMessageNodes(nodes)
        }
    }

    private fun readUploadMessages(id: String): String {
        val totalLength = getUploadMessagesLength(id) ?: return "[]"
        if (totalLength <= 0) return "[]"
        return buildString(totalLength) {
            var start = 1
            while (start <= totalLength) {
                append(getUploadMessagesChunk(id, start, UPLOAD_MESSAGE_CHUNK_CHARS).orEmpty())
                start += UPLOAD_MESSAGE_CHUNK_CHARS
            }
        }
    }

    companion object {
        private const val UPLOAD_MESSAGE_CHUNK_CHARS = 256 * 1024
    }
}

data class AiChatConversationMetadata(
    val id: String,
    val assistantId: String,
    val title: String,
    val createAt: Long,
    val updateAt: Long,
    val isPinned: Boolean,
    val customSystemPrompt: String,
    val loadedSkillIds: String
) {
    fun toConversation(uploadMessages: String): AiChatConversation {
        return AiChatConversation(
            id = id,
            assistantId = assistantId,
            title = title,
            createAt = createAt,
            updateAt = updateAt,
            isPinned = isPinned,
            customSystemPrompt = customSystemPrompt,
            loadedSkillIds = loadedSkillIds,
            uploadMessages = uploadMessages
        )
    }
}
