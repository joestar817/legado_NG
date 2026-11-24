package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessageNode

private const val CONVERSATION_METADATA_SQL =
    "select id, assistantId, agentModeRevision, modeEntryContext, modeEntryStarted, " +
        "title, createAt, updateAt, isPinned, customSystemPrompt, loadedSkillIds, " +
        "enabledMcpCapabilityIds, webEnabled, " +
        "exists (select 1 from aiChatMessageNodes where conversationId = aiChatConversations.id) " +
        "as hasMessageNodes from aiChatConversations"

@Dao
interface AiChatDao {

    @Query(
        "$CONVERSATION_METADATA_SQL order by isPinned desc, updateAt desc, id limit :limit offset :offset"
    )
    fun getConversationMetadata(limit: Int = 100, offset: Int = 0): List<AiChatConversationMetadata>

    @Query("$CONVERSATION_METADATA_SQL where id = :id")
    fun getConversationMetadata(id: String): AiChatConversationMetadata?

    @Query("select length(uploadMessages) from aiChatConversations where id = :id")
    fun getUploadMessagesLength(id: String): Int?

    @Query(
        "select substr(uploadMessages, :start, :length) " +
            "from aiChatConversations where id = :id"
    )
    fun getUploadMessagesChunk(id: String, start: Int, length: Int): String?

    @Transaction
    fun getConversations(limit: Int = 100): List<AiChatConversation> =
        readConversations(getConversationMetadata(limit))

    @Transaction
    fun readConversations(metadata: List<AiChatConversationMetadata>): List<AiChatConversation> =
        metadata.map { it.toConversation(readUploadMessages(it.id)) }

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

    @Query("select exists(select 1 from aiChatMessageNodes where instr(messages, :encodedOwner) > 0)")
    fun hasToolReceiptOwner(encodedOwner: String): Boolean

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
    val agentModeRevision: String,
    val modeEntryContext: String,
    val modeEntryStarted: Boolean,
    val title: String,
    val createAt: Long,
    val updateAt: Long,
    val isPinned: Boolean,
    val customSystemPrompt: String,
    val loadedSkillIds: String,
    val enabledMcpCapabilityIds: String,
    val webEnabled: Boolean,
    val hasMessageNodes: Boolean,
) {
    fun toConversation(uploadMessages: String): AiChatConversation {
        return AiChatConversation(
            id = id,
            assistantId = assistantId,
            agentModeRevision = agentModeRevision,
            modeEntryContext = modeEntryContext,
            modeEntryStarted = modeEntryStarted,
            title = title,
            createAt = createAt,
            updateAt = updateAt,
            isPinned = isPinned,
            customSystemPrompt = customSystemPrompt,
            loadedSkillIds = loadedSkillIds,
            enabledMcpCapabilityIds = enabledMcpCapabilityIds,
            uploadMessages = uploadMessages,
            webEnabled = webEnabled,
        )
    }
}
