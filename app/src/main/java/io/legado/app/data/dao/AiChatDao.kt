package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessageNode
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {

    @Query("select * from aiChatConversations order by isPinned desc, updateAt desc limit :limit")
    fun flowConversations(limit: Int = 100): Flow<List<AiChatConversation>>

    @Query("select * from aiChatConversations order by isPinned desc, updateAt desc limit :limit")
    fun getConversations(limit: Int = 100): List<AiChatConversation>

    @Query("select * from aiChatConversations where id = :id")
    fun getConversation(id: String): AiChatConversation?

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
}
