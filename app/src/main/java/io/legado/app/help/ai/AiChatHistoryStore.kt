package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.dao.AiChatConversationMetadata
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessageNode
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.web.mcp.McpInternalToolCatalog
import splitties.init.appCtx
import java.io.File

object AiChatHistoryStore {

    private const val FILE_NAME = "ai_chat_history.json"
    private const val VERSION = 4
    private const val MAX_SESSIONS = 100
    private val lock = Any()
    private val messageListType = object : TypeToken<List<AiChatMessageSnapshot>>() {}.type
    private val uploadMessageListType = object : TypeToken<List<JsonObject>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type

    /**
     * Loads only the lightweight conversation index used by the drawer.
     * Message nodes and upload context are intentionally deferred until a
     * conversation is opened.
     */
    fun loadIndex(): AiChatHistoryState {
        return synchronized(lock) {
            migrateLegacyFileIfNeeded()
            val sessions = appDb.aiChatDao.getConversationMetadata(MAX_SESSIONS)
                .map { it.toSummarySnapshot() }
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

    fun setActiveSessionId(sessionId: String?) {
        synchronized(lock) {
            appCtx.putPrefString(PreferKey.aiChatActiveSessionId, sessionId)
        }
    }

    fun updatePinned(
        sessionIds: Collection<String>,
        isPinned: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        synchronized(lock) {
            sessionIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .forEach { sessionId ->
                    appDb.aiChatDao.updatePinned(sessionId, isPinned, updatedAt)
                }
        }
    }

    fun loadSession(sessionId: String): AiChatSessionSnapshot? {
        if (sessionId.isBlank()) return null
        return synchronized(lock) {
            appDb.aiChatDao.getConversation(sessionId)?.toSnapshot()
        }
    }

    fun loadAllSessionDetails(): List<AiChatSessionSnapshot> {
        return synchronized(lock) {
            appDb.aiChatDao.getConversations(MAX_SESSIONS)
                .map { it.toSnapshot() }
                .filter { it.messages.isNotEmpty() || it.modeEntryContext != null }
        }
    }

    fun saveSessionSnapshot(session: AiChatSessionSnapshot) {
        synchronized(lock) {
            saveSession(session.sanitize())
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

    fun saveDurableTurn(
        baseSession: AiChatSessionSnapshot,
        assistantMessageId: String,
        update: AiChatDurableContextUpdate
    ) {
        if (baseSession.id.isBlank() || assistantMessageId.isBlank()) return
        synchronized(lock) {
            saveSession(
                mergeDurableTurn(
                    baseSession = baseSession,
                    assistantMessageId = assistantMessageId,
                    update = update,
                    now = System.currentTimeMillis()
                ).sanitize()
            )
        }
    }

    internal fun mergeDurableTurn(
        baseSession: AiChatSessionSnapshot,
        assistantMessageId: String,
        update: AiChatDurableContextUpdate,
        now: Long
    ): AiChatSessionSnapshot {
        val messages = baseSession.messages
            .filterNot { message -> message.id == assistantMessageId }
            .toMutableList()
        if (update.visibleContent.isNotBlank()) {
            messages += AiChatMessageSnapshot(
                id = assistantMessageId,
                role = AiChatMessageSnapshot.ROLE_ASSISTANT,
                content = update.visibleContent,
                meta = if (update.pendingReceiptIds.isEmpty()) {
                    "执行中，可恢复"
                } else {
                    "执行中，已有未提交检查点"
                },
                reasoning = update.reasoning,
                toolTrace = update.toolTrace,
                toolReceipts = update.toolReceipts,
                deliveryState = AiChatMessageSnapshot.DELIVERY_IN_FLIGHT
            )
        }
        return baseSession.copy(
            updatedAt = now,
            messages = messages,
            uploadMessages = update.contextMessages.map { message ->
                message.deepCopy().asJsonObject
            }
        )
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
        val mode = normalizedAgentModeIdentity()
        return AiChatConversation(
            id = id,
            assistantId = mode.id,
            agentModeRevision = mode.revision,
            modeEntryContext = modeEntryContext?.validatedCopyOrNull()?.toJson().orEmpty(),
            modeEntryStarted = modeEntryStarted,
            title = title,
            createAt = createdAt,
            updateAt = updatedAt,
            isPinned = isPinned,
            loadedSkillIds = GSON.toJson(loadedSkillIds),
            enabledMcpCapabilityIds = GSON.toJson(enabledMcpCapabilityIds),
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
            modeId = assistantId,
            modeRevision = agentModeRevision,
            modeEntryContext = AgentModeEntryContext.fromJsonOrNull(modeEntryContext),
            modeEntryStarted = modeEntryStarted,
            title = title,
            createdAt = createAt,
            updatedAt = updateAt,
            isPinned = isPinned,
            messages = messages,
            loadedSkillIds = loadedSkillIds.toStringList(),
            enabledMcpCapabilityIds = enabledMcpCapabilityIds.toStringList(),
            uploadMessages = uploadMessages.toUploadMessages()
        ).sanitize()
    }

    private fun AiChatConversationMetadata.toSummarySnapshot(): AiChatSessionSnapshot {
        return AiChatSessionSnapshot(
            id = id,
            modeId = assistantId,
            modeRevision = agentModeRevision,
            modeEntryContext = AgentModeEntryContext.fromJsonOrNull(modeEntryContext),
            modeEntryStarted = modeEntryStarted,
            title = title,
            createdAt = createAt,
            updatedAt = updateAt,
            isPinned = isPinned,
            messages = emptyList(),
            loadedSkillIds = loadedSkillIds.toStringList(),
            enabledMcpCapabilityIds = enabledMcpCapabilityIds.toStringList(),
            uploadMessages = emptyList()
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
        val mode = normalizedAgentModeIdentity()
        val cleanEntryContext = modeEntryContext?.validatedCopyOrNull()
        val cleanMessages = recoverLegacyVisibleMessages(messages.filter {
            it.role.isNotBlank() && it.content.isNotBlank()
        }, uploadMessages).toMutableList()
        val cleanTitle = title.ifBlank {
            cleanEntryContext?.title?.takeIf { it.isNotBlank() }
                ?: cleanMessages.firstOrNull { it.role == AiChatMessageSnapshot.ROLE_USER }
                ?.content
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(24)
                ?: "新聊天"
        }
        return copy(
            modeId = mode.id,
            modeRevision = mode.revision,
            modeEntryContext = cleanEntryContext,
            title = cleanTitle,
            messages = cleanMessages,
            loadedSkillIds = loadedSkillIds.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            enabledMcpCapabilityIds = McpInternalToolCatalog.normalizeCapabilityIds(
                enabledMcpCapabilityIds
            ),
            uploadMessages = uploadMessages.filter { it.has("role") }
        )
    }

    internal fun recoverLegacyVisibleMessages(
        messages: List<AiChatMessageSnapshot>,
        uploadMessages: List<JsonObject>
    ): List<AiChatMessageSnapshot> {
        val uploadTurns = mutableListOf<MutableList<JsonObject>>()
        uploadMessages.forEach { message ->
            when (message.get("role")?.takeIf { it.isJsonPrimitive }?.asString) {
                AiChatMessageSnapshot.ROLE_USER -> {
                    uploadTurns += mutableListOf(message)
                }
                else -> uploadTurns.lastOrNull()?.add(message)
            }
        }
        var userTurnIndex = -1
        val recoveredMessages = messages.map { message ->
            val normalizedMessage = message.copy(
                resolvedInteractionIds = message.resolvedInteractionIds.orEmpty(),
                interactionResultLabels = message.interactionResultLabels.orEmpty(),
                interactionResultSelections = message.interactionResultSelections.orEmpty()
            )
            if (normalizedMessage.role == AiChatMessageSnapshot.ROLE_USER) {
                userTurnIndex += 1
                return@map normalizedMessage
            }
            if (normalizedMessage.role != AiChatMessageSnapshot.ROLE_ASSISTANT ||
                normalizedMessage.contextCompactionRevision != null
            ) return@map normalizedMessage
            val uploadTurn = uploadTurns.getOrNull(userTurnIndex) ?: return@map normalizedMessage
            AiChatVisibleContentRecovery.recoverFromUploadHistory(
                finalContent = normalizedMessage.content,
                uploadMessages = uploadTurn
            )?.let { recovered -> normalizedMessage.copy(content = recovered) } ?: normalizedMessage
        }
        return recoveredMessages.map { message ->
            if (message.toolTrace == null || message.toolReceipts == null ||
                message.deliveryState.isNullOrBlank()
            ) {
                message.copy(
                    toolTrace = message.toolTrace.orEmpty(),
                    toolReceipts = message.toolReceipts.orEmpty(),
                    resolvedInteractionIds = message.resolvedInteractionIds.orEmpty(),
                    interactionResultLabels = message.interactionResultLabels.orEmpty(),
                    interactionResultSelections = message.interactionResultSelections.orEmpty(),
                    deliveryState = message.deliveryState
                        ?.takeIf { it.isNotBlank() }
                        ?: AiChatMessageSnapshot.DELIVERY_SENT
                )
            } else {
                message
            }
        }
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
    @SerializedName("mode_id")
    val modeId: String? = null,
    @SerializedName("mode_revision")
    val modeRevision: String? = null,
    @SerializedName("mode_entry_context")
    val modeEntryContext: AgentModeEntryContext? = null,
    @SerializedName("mode_entry_started")
    val modeEntryStarted: Boolean = false,
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
    @SerializedName("enabled_mcp_capability_ids")
    val enabledMcpCapabilityIds: List<String> = emptyList(),
    @SerializedName("upload_messages")
    val uploadMessages: List<JsonObject> = emptyList()
) {
    fun normalizedAgentModeIdentity(): AgentModeIdentity {
        return AgentModeRegistry.normalizePersistedIdentity(modeId, modeRevision)
    }
}

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
    val toolTrace: List<String>? = emptyList(),
    @SerializedName("tool_receipts")
    val toolReceipts: List<ToolExecutionReceipt>? = emptyList(),
    @SerializedName("memory_trace")
    val memoryTrace: List<AiMemoryTraceItem>? = emptyList(),
    @SerializedName("elapsed_ms")
    val elapsedMs: Long? = null,
    @SerializedName("favorite")
    val favorite: Boolean = false,
    @SerializedName("delivery_state")
    val deliveryState: String? = DELIVERY_SENT,
    @SerializedName("upload_content")
    val uploadContent: String? = null,
    @SerializedName("resolved_interaction_ids")
    val resolvedInteractionIds: List<String> = emptyList(),
    @SerializedName("interaction_result_labels")
    val interactionResultLabels: Map<String, String> = emptyMap(),
    @SerializedName("interaction_result_selections")
    val interactionResultSelections: Map<String, Map<String, String>> = emptyMap(),
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
