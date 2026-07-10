package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessageNode
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import java.io.File

object AiChatHistoryStore {

    private const val FILE_NAME = "ai_chat_history.json"
    private const val VERSION = 2
    private const val MAX_SESSIONS = 100
    private const val DEFAULT_ASSISTANT_ID = "default"
    private val lock = Any()
    private val messageListType = object : TypeToken<List<AiChatMessageSnapshot>>() {}.type
    private val uploadMessageListType = object : TypeToken<List<JsonObject>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type

    fun load(): AiChatHistoryState {
        return synchronized(lock) {
            migrateLegacyFileIfNeeded()
            val sessions = appDb.aiChatDao.getConversations(MAX_SESSIONS)
                .map { it.toSnapshot() }
                .filter { it.messages.isNotEmpty() }
            val activeId = appCtx.getPrefString(PreferKey.aiChatActiveSessionId)
                ?.takeIf { id -> sessions.any { it.id == id } }
                ?: sessions.firstOrNull()?.id
            AiChatHistoryState(
                version = VERSION,
                activeSessionId = activeId,
                sessions = sessions
            ).sanitize()
        }
    }

    fun save(state: AiChatHistoryState) {
        synchronized(lock) {
            val cleanState = state.sanitize()
            appCtx.putPrefString(PreferKey.aiChatActiveSessionId, cleanState.activeSessionId)
            cleanState.sessions.forEach { session ->
                saveSession(session)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        synchronized(lock) {
            appDb.aiChatDao.deleteMessageNodes(sessionId)
            appDb.aiChatDao.deleteConversation(sessionId)
        }
    }

    fun deleteSessions(sessionIds: Collection<String>) {
        synchronized(lock) {
            sessionIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { sessionId ->
                    appDb.aiChatDao.deleteMessageNodes(sessionId)
                    appDb.aiChatDao.deleteConversation(sessionId)
                }
        }
    }

    private fun historyFile(): File {
        return File(appCtx.filesDir, FILE_NAME)
    }

    private fun migrateLegacyFileIfNeeded() {
        val file = historyFile()
        if (!file.exists() || appDb.aiChatDao.countConversations() > 0) {
            return
        }
        val legacyState = runCatching {
            GSON.fromJson(file.readText(), AiChatHistoryState::class.java)
        }.getOrNull()?.sanitize() ?: return
        legacyState.sessions.forEach { saveSession(it) }
        appCtx.putPrefString(PreferKey.aiChatActiveSessionId, legacyState.activeSessionId)
        runCatching { file.delete() }
    }

    private fun saveSession(session: AiChatSessionSnapshot) {
        appDb.aiChatDao.replaceConversation(
            conversation = session.toEntity(),
            nodes = session.toMessageNodes()
        )
    }

    private fun AiChatSessionSnapshot.toEntity(): AiChatConversation {
        return AiChatConversation(
            id = id,
            assistantId = DEFAULT_ASSISTANT_ID,
            title = title,
            createAt = createdAt,
            updateAt = updatedAt,
            isPinned = isPinned,
            loadedSkillIds = GSON.toJson(loadedSkillIds),
            uploadMessages = GSON.toJson(uploadMessages)
        )
    }

    private fun AiChatSessionSnapshot.toMessageNodes(): List<AiChatMessageNode> {
        return messages.mapIndexed { index, message ->
            AiChatMessageNode(
                id = "$id:$index",
                conversationId = id,
                nodeIndex = index,
                messages = GSON.toJson(listOf(message)),
                selectIndex = 0
            )
        }
    }

    private fun AiChatConversation.toSnapshot(): AiChatSessionSnapshot {
        val messages = appDb.aiChatDao.getMessageNodes(id)
            .flatMap { node -> node.toMessages() }
        return AiChatSessionSnapshot(
            id = id,
            title = title,
            createdAt = createAt,
            updatedAt = updateAt,
            isPinned = isPinned,
            messages = messages,
            loadedSkillIds = loadedSkillIds.toStringList(),
            uploadMessages = uploadMessages.toUploadMessages()
        ).sanitize()
    }

    private fun AiChatMessageNode.toMessages(): List<AiChatMessageSnapshot> {
        return runCatching {
            GSON.fromJson<List<AiChatMessageSnapshot>>(messages, messageListType)
        }.getOrNull().orEmpty()
    }

    private fun String.toUploadMessages(): List<JsonObject> {
        return runCatching {
            GSON.fromJson<List<JsonObject>>(this, uploadMessageListType)
        }.getOrNull().orEmpty()
            .filter { it.has("role") }
            .map { it.deepCopy().asJsonObject }
    }

    private fun String.toStringList(): List<String> {
        return runCatching {
            GSON.fromJson<List<String>>(this, stringListType)
        }.getOrNull().orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun AiChatHistoryState.sanitize(): AiChatHistoryState {
        val cleanSessions = sessions
            .filter { it.id.isNotBlank() }
            .map { it.sanitize() }
            .sortedWith(
                compareByDescending<AiChatSessionSnapshot> { it.isPinned }
                    .thenByDescending { it.updatedAt }
            )
            .take(MAX_SESSIONS)
        val cleanActiveId = activeSessionId?.takeIf { activeId ->
            cleanSessions.any { it.id == activeId }
        } ?: cleanSessions.firstOrNull()?.id
        return copy(
            version = VERSION,
            activeSessionId = cleanActiveId,
            sessions = cleanSessions
        )
    }

    private fun AiChatSessionSnapshot.sanitize(): AiChatSessionSnapshot {
        val cleanMessages = messages.filter {
            it.role.isNotBlank() && it.content.isNotBlank()
        }
        val cleanTitle = title.ifBlank {
            cleanMessages.firstOrNull { it.role == AiChatMessageSnapshot.ROLE_USER }
                ?.content
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(24)
                ?: "新聊天"
        }
        return copy(
            title = cleanTitle,
            messages = cleanMessages,
            loadedSkillIds = loadedSkillIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            uploadMessages = uploadMessages.filter { it.has("role") }
        )
    }
}

data class AiChatHistoryState(
    @SerializedName("version")
    val version: Int = 1,
    @SerializedName("active_session_id")
    val activeSessionId: String? = null,
    @SerializedName("sessions")
    val sessions: List<AiChatSessionSnapshot> = emptyList()
)

data class AiChatSessionSnapshot(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("created_at")
    val createdAt: Long,
    @SerializedName("updated_at")
    val updatedAt: Long,
    @SerializedName("is_pinned")
    val isPinned: Boolean = false,
    @SerializedName("messages")
    val messages: List<AiChatMessageSnapshot>,
    @SerializedName("loaded_skill_ids")
    val loadedSkillIds: List<String> = emptyList(),
    @SerializedName("upload_messages")
    val uploadMessages: List<JsonObject> = emptyList()
)

data class AiChatMessageSnapshot(
    @SerializedName("id")
    val id: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("meta")
    val meta: String? = null,
    @SerializedName("reasoning")
    val reasoning: String? = null,
    @SerializedName("tool_trace")
    val toolTrace: List<String> = emptyList(),
    @SerializedName("memory_trace")
    val memoryTrace: List<AiMemoryTraceItem>? = emptyList(),
    @SerializedName("elapsed_ms")
    val elapsedMs: Long? = null,
    @SerializedName("favorite")
    val favorite: Boolean = false,
    @SerializedName("delivery_state")
    val deliveryState: String = DELIVERY_SENT,
    @SerializedName("upload_content")
    val uploadContent: String? = null,
    @SerializedName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerializedName("local_prompt_tokens")
    val localPromptTokens: Int? = null,
    @SerializedName("context_anchor_tokens")
    val contextAnchorTokens: Int? = null,
    @SerializedName("local_context_anchor_tokens")
    val localContextAnchorTokens: Int? = null,
    @SerializedName("usage_provider_id")
    val usageProviderId: String? = null,
    @SerializedName("usage_model_id")
    val usageModelId: String? = null,
    @SerializedName("context_compaction_before_tokens")
    val contextCompactionBeforeTokens: Int? = null,
    @SerializedName("context_compaction_after_tokens")
    val contextCompactionAfterTokens: Int? = null,
    @SerializedName("context_compaction_revision")
    val contextCompactionRevision: Int? = null,
    @SerializedName("context_compaction_summary_prompt_tokens")
    val contextCompactionSummaryPromptTokens: Int? = null,
    @SerializedName("context_compaction_summary_completion_tokens")
    val contextCompactionSummaryCompletionTokens: Int? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val DELIVERY_SENT = "sent"
        const val DELIVERY_QUEUED = "queued"
        const val DELIVERY_IN_FLIGHT = "in_flight"
        const val DELIVERY_FAILED = "failed"
    }
}
