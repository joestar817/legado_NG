package io.legado.app.ui.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.help.ai.AI_BOOK_SEARCH_DRAFT
import io.legado.app.help.ai.AI_BOOK_SEARCH_SCOPE
import io.legado.app.help.ai.AgentModeDefinition
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AgentModeIdentity
import io.legado.app.help.ai.AgentModeRegistry
import io.legado.app.help.ai.AiAssistantReplyEndException
import io.legado.app.help.ai.AiAssistantReplyEndReason
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSourceLookup
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.help.ai.AiChatClient
import io.legado.app.help.ai.AiChatContextEventType
import io.legado.app.help.ai.AiChatContextManager
import io.legado.app.help.ai.AiChatDurableContextUpdate
import io.legado.app.help.ai.AiChatGptException
import io.legado.app.help.ai.AiChatHistoryStore
import io.legado.app.help.ai.AiChatImageDrafts
import io.legado.app.help.ai.AiChatInteractionPolicyRegistry
import io.legado.app.help.ai.AiChatSessionSnapshot
import io.legado.app.help.ai.AiChatStreamUpdate
import io.legado.app.help.ai.AiImageAssets
import io.legado.app.help.ai.AiImageDraft
import io.legado.app.help.ai.isCodexImage
import io.legado.app.help.ai.AiMemoryTraceItem
import io.legado.app.help.ai.AiModelSelection
import io.legado.app.help.ai.AiPendingToolCall
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.ai.AiWebAccess
import io.legado.app.help.ai.AiWebActivity
import io.legado.app.help.ai.CHATGPT_USAGE_URL
import io.legado.app.help.ai.CODEX_IMAGE_TOOL
import io.legado.app.help.ai.CODEX_IMAGE_MODEL
import io.legado.app.help.ai.isCodexChatSelection
import io.legado.app.help.ai.aiBookSearchVisibleText
import io.legado.app.help.ai.bookSearchDraft
import io.legado.app.help.ai.bookSearchDraftForHistory
import io.legado.app.help.ai.hasChatHistoryContent
import io.legado.app.help.ai.isConfirmedBookSearchAudioScope
import io.legado.app.help.ai.parseAiBookSearchReply
import io.legado.app.web.mcp.McpInternalChannel
import io.legado.app.web.mcp.McpInternalToolCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.security.MessageDigest
import java.util.UUID

internal val aiChatSessions = AiChatSessions(
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    readImageDraft = AiChatImageDrafts::read,
    writeImageDraft = AiChatImageDrafts::write,
)

private fun chatSessionEntryKey(vararg parts: String): String {
    val encoded = parts.joinToString("") { "${it.length}:$it" }.encodeToByteArray()
    return MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") {
        it.toUByte().toString(16).padStart(2, '0')
    }
}

internal data class AiChatSessionEntry(
    val key: String,
    private val mode: AgentModeIdentity? = null,
    private val contextId: String? = null,
    private val workKey: String = "",
) {
    val dedicated get() = mode != null

    fun matches(identity: AgentModeIdentity, context: AgentModeEntryContext?): Boolean =
        mode == null || (mode == identity && contextId == context?.contextId && workKey == context.payloadString("work_key"))
}

internal fun chatSessionEntry(
    source: String?,
    mode: AgentModeDefinition,
    context: AgentModeEntryContext?,
    initialInput: String,
    skillIds: List<String>,
    contextAttachments: List<AiChatInputAttachment>,
): AiChatSessionEntry {
    val workKey = context.payloadString("work_key")
    val key = if (source == AiChatActivity.ENTRY_BOOK_SCAN && workKey.isNotBlank()) {
        chatSessionEntryKey(source, mode.id, mode.revision, context?.contextId.orEmpty(), workKey)
    } else {
        chatSessionEntryKey(
            source.orEmpty(), mode.id, mode.revision, context?.toJson().orEmpty(), initialInput,
            skillIds.joinToString("\u0000"), contextAttachments.joinToString("\u0000") { "${it.id}\u0001${it.prompt}" },
        )
    }
    return AiChatSessionEntry(key, mode.identity.takeIf { source != null }, context?.contextId, workKey)
}

/** Owns live chat tasks in this process. All live state is accessed on the main thread. */
internal class AiChatSessions(
    val scope: CoroutineScope,
    val client: AiChatClient = AiChatClient(),
    val readImageDraft: (String) -> AiImageDraft? = { null },
    val writeImageDraft: (String, AiImageDraft) -> Unit = { _, _ -> },
    private val writeSnapshot: (AiChatSessionSnapshot) -> Unit = AiChatHistoryStore::saveSessionSnapshot,
) {
    val mainContext get() = scope.coroutineContext.minusKey(Job)
    val history = mutableStateListOf<AiChatSessionSnapshot>()
    val loadedDetailIds = mutableStateListOf<String>()

    private val liveSessions = mutableMapOf<String, AiChatSessionRuntime>()
    private val idleJobs = mutableMapOf<String, Job>()
    private val selectedByEntry = LinkedHashMap<String, String>()
    private val viewingSessions = mutableMapOf<String, Int>()
    private val taskState = MutableStateFlow<List<AiChatTask>>(emptyList())
    val tasks = taskState.asStateFlow()
    private var taskCompletionOrder = 0L
    private var petCompletionSeen = 0L
    private val previewMarkdown by lazy { aiChatSearchMarkdown(appCtx) }
    private val deletedIds = mutableSetOf<String>()
    private val historyLock = Mutex()
    private var attachedWindows = 0

    fun attachWindow() {
        attachedWindows++
    }

    fun detachWindow() {
        attachedWindows--
        if (attachedWindows == 0) {
            history.clear()
            loadedDetailIds.clear()
        }
    }

    fun get(id: String?) = liveSessions[id]

    fun selectedFor(entry: AiChatSessionEntry): String? = selectedByEntry[entry.key]?.takeIf { id ->
        liveSessions[id]?.let { entry.matches(it.mode.identity, it.activeModeEntryContext) } != false
    }

    fun bind(session: AiChatSessionRuntime) {
        idleJobs.remove(session.id)?.cancel()
        liveSessions[session.id] = session
        session.attachedWindows++
        session.savedSnapshot?.let { publish(it) }
        refreshTask(session)
    }

    fun unbind(session: AiChatSessionRuntime) {
        session.attachedWindows--
        releaseIfIdle(session)
    }

    fun releaseIfIdle(session: AiChatSessionRuntime) {
        if (session.attachedWindows != 0 || session.hasWork || session.hasDraft) return
        if (idleJobs[session.id]?.isActive == true) return
        idleJobs[session.id] = scope.launch {
            delay(5_000)
            idleJobs.remove(session.id)
            if (session.attachedWindows != 0 || session.hasWork || session.hasDraft) return@launch
            if (liveSessions.remove(session.id, session)) {
                session.release()
                if (session.savedSnapshot == null) selectedByEntry.values.removeAll { it == session.id }
            }
        }
    }

    fun select(entry: AiChatSessionEntry?, session: AiChatSessionRuntime, createdForEntry: Boolean = false) {
        if (session.deleted) return
        liveSessions[session.id] = session
        if (entry == null || entry.dedicated && !createdForEntry ||
            !entry.matches(session.mode.identity, session.activeModeEntryContext)) return
        selectedByEntry.remove(entry.key)
        selectedByEntry[entry.key] = session.id
        if (selectedByEntry.size > 100) selectedByEntry.remove(selectedByEntry.keys.first())
    }

    fun beginViewing(sessionId: String) {
        viewingSessions[sessionId] = (viewingSessions[sessionId] ?: 0) + 1
        if (taskState.value.any { it.sessionId == sessionId && it.state.isTerminal }) {
            forgetTask(sessionId)
        }
    }

    fun endViewing(sessionId: String) {
        val remaining = (viewingSessions[sessionId] ?: return) - 1
        if (remaining == 0) viewingSessions.remove(sessionId)
        else viewingSessions[sessionId] = remaining
    }

    fun refreshTask(session: AiChatSessionRuntime, outcome: AiChatTaskState? = null) {
        if (session.deleted) return
        val current = taskState.value
        val index = current.indexOfFirst { it.sessionId == session.id }
        val previous = current.getOrNull(index)
        val next = session.taskSummary() ?: previous?.takeUnless { session.id in viewingSessions }?.let { task ->
            when {
                task.state.isTerminal -> task
                outcome != null -> {
                    val completed = outcome == AiChatTaskState.COMPLETED
                    task.copy(
                        state = outcome,
                        actionTitle = null,
                        completionOrder = if (completed) ++taskCompletionOrder else 0,
                        replyPreview = if (completed) runCatching {
                            session.messages.lastOrNull()?.taskReplyPreview(previewMarkdown)
                        }.getOrNull() else null,
                    )
                }
                else -> null
            }
        }
        if (next == previous) return
        // Keep a row in place as its state changes, including completion while a picker is open.
        taskState.value = when {
            index < 0 && next != null -> listOf(next) + current
            next == null -> current.filterNot { it.sessionId == session.id }
            else -> current.toMutableList().apply { set(index, next) }
        }
    }

    /** Coalesce unseen completions into one reaction when the pet is visible again. */
    fun takePetCompletion(): Boolean {
        val latest = taskState.value.maxOfOrNull(AiChatTask::completionOrder) ?: return false
        if (latest <= petCompletionSeen) return false
        petCompletionSeen = latest
        return true
    }

    fun forgetTask(sessionId: String) {
        taskState.value = taskState.value.filterNot { it.sessionId == sessionId }
    }

    fun saveSelection(id: String): Job = write { AiChatHistoryStore.setActiveSessionId(id) }

    fun save(snapshot: AiChatSessionSnapshot): Deferred<Unit> {
        if (snapshot.id in deletedIds) return CompletableDeferred(Unit)
        publish(snapshot)
        return write { writeSnapshot(snapshot) }
    }

    fun mergeIndex(index: List<AiChatSessionSnapshot>) {
        val present = history.mapTo(mutableSetOf()) { it.id }
        history.addAll(index.filterNot { it.id in present || it.id in deletedIds })
        liveSessions.values.mapNotNull { it.savedSnapshot }.forEach { publish(it, sort = false) }
        history.sortChatSessions()
    }

    fun mergeDetails(details: List<AiChatSessionSnapshot>) {
        details.forEach { detail ->
            val latest = liveSessions[detail.id]?.savedSnapshot
                ?: history.firstOrNull { it.id == detail.id && it.updatedAt > detail.updatedAt }
                ?: detail
            publish(latest, sort = false)
        }
        history.sortChatSessions()
    }

    fun pin(ids: Set<String>, time: Long) {
        ids.forEach { id -> liveSessions[id]?.pin(time) }
        history.indices.forEach { index ->
            if (history[index].id in ids) history[index] = history[index].copy(isPinned = true, updatedAt = time)
        }
        history.sortChatSessions()
        write { AiChatHistoryStore.updatePinned(ids, isPinned = true, updatedAt = time) }
    }

    fun delete(ids: Set<String>) {
        deletedIds += ids
        ids.forEach {
            idleJobs.remove(it)?.cancel()
            liveSessions.remove(it)?.delete()
        }
        taskState.value = taskState.value.filterNot { it.sessionId in ids }
        selectedByEntry.values.removeAll { it in ids }
        history.removeAll { it.id in ids }
        loadedDetailIds.removeAll(ids)
        write { AiChatHistoryStore.deleteSessions(ids) }
    }

    fun wasDeleted(id: String) = id in deletedIds

    suspend fun <T> readHistory(load: () -> T): T = historyLock.withLock { withContext(Dispatchers.IO) { load() } }

    private fun write(block: () -> Unit): Deferred<Unit> = scope.async(start = CoroutineStart.UNDISPATCHED) {
        historyLock.withLock { withContext(Dispatchers.IO) { block() } }
    }

    private fun publish(snapshot: AiChatSessionSnapshot, sort: Boolean = true) {
        if (attachedWindows == 0 || snapshot.id in deletedIds) return
        val index = history.indexOfFirst { it.id == snapshot.id }
        if (!hasChatHistoryContent(
                hasMessages = snapshot.messages.isNotEmpty(),
                modeEntryStarted = snapshot.modeEntryStarted,
                bookSearchDraft = snapshot.modeEntryContext.bookSearchDraft(),
            )) {
            if (index >= 0) history.removeAt(index)
            loadedDetailIds.remove(snapshot.id)
            return
        }
        if (index >= 0) history[index] = snapshot else history.add(snapshot)
        if (snapshot.id !in loadedDetailIds) loadedDetailIds.add(snapshot.id)
        if (sort) history.sortChatSessions()
    }
}

private fun Result<*>.chatTaskOutcome(): AiChatTaskState? = when {
    isSuccess -> AiChatTaskState.COMPLETED
    exceptionOrNull() is CancellationException -> null
    else -> AiChatTaskState.FAILED
}

internal class AiChatToolApproval(val calls: List<AiPendingToolCall>) {
    val response = CompletableDeferred<Boolean>()
    var presenter by mutableStateOf<Any?>(null)
}

internal class AiChatSessionRuntime(
    private val owner: AiChatSessions,
    val mode: AgentModeDefinition,
    initialContext: AgentModeEntryContext?,
    initialAttachments: List<AiChatInputAttachment>,
    initialInput: String = "",
    val id: String = UUID.randomUUID().toString(),
    saved: AiChatSessionSnapshot? = null,
) {
    private val scope = CoroutineScope(owner.scope.coroutineContext + SupervisorJob(owner.scope.coroutineContext[Job]))
    private val chatClient get() = owner.client
    private val taskInteractionPolicy by lazy { AiChatInteractionPolicyRegistry.resolve(mode) }
    internal var attachedWindows = 0
    internal var deleted = false
        private set
    var savedSnapshot = saved
        private set

    val messages = mutableStateListOf<ChatUiMessage>()
    val uploadMessages = mutableStateListOf<JsonObject>()
    private var imageDraft by mutableStateOf(
        saved?.let { owner.readImageDraft(id) } ?: AiImageDraft(prompt = initialInput)
    )
    val imageDraftAssets get() = imageDraft.assets
    val explicitImageDraft get() = imageDraft.direct
    val directImageDraft get() = explicitImageDraft || imageDraft.editAssets.orEmpty().any(imageDraftAssets::contains)
    var imageWait by mutableStateOf<AiChatImageWait?>(null)
        private set
    val inputAttachments = mutableStateListOf<AiChatInputAttachment>().apply { addAll(initialAttachments) }
    val skillManagedCapabilityIds = mutableStateListOf<String>().apply {
        addAll(resolveModeManagedCapabilities(mode, initialAttachments))
    }
    val manualMcpCapabilityIds = mutableStateListOf<String>().apply {
        if (saved != null && mode.allowsManualMcpCapabilities) {
            addAll(McpInternalToolCatalog.normalizeCapabilityIds(saved.enabledMcpCapabilityIds) - skillManagedCapabilityIds.toSet())
        }
    }
    val enabledMcpCapabilityIds = mutableStateListOf<String>().apply {
        addAll(McpInternalToolCatalog.normalizeCapabilityIds(manualMcpCapabilityIds + skillManagedCapabilityIds))
    }
    val entryContextState = mutableStateOf(initialContext)
    var activeModeEntryContext by entryContextState
    val modeStartedState = mutableStateOf(saved?.modeEntryStarted == true)
    var modeEntryStarted by modeStartedState
    val inputState = mutableStateOf(imageDraft.prompt)
    var input by inputState
    val draftEditedState = mutableStateOf(saved?.modeEntryContext.bookSearchDraft() != null)
    var bookSearchDraftEdited by draftEditedState
    val greetingPendingState = mutableStateOf(saved == null)
    val contextStatusState = mutableStateOf<String?>(null)
    var contextStatusText by contextStatusState
    var contextUsage by mutableStateOf(AiChatContextManager.usage(emptyList()))
        private set
    var contextCompactionCount by mutableIntStateOf(0)
    var sending by mutableStateOf(false)
        private set
    var manualContextCompacting by mutableStateOf(false)
        private set
    var activeBookLookupMessageId by mutableStateOf<String?>(null)
        private set
    var activeLoadingMessageId by mutableStateOf<String?>(null)
        private set
    var activeLoadingStartedAt by mutableStateOf<Long?>(null)
        private set
    var activeStreamContentTarget by mutableStateOf("")
        private set
    var activeStreamReasoningTarget by mutableStateOf("")
        private set
    var activeStreamToolTraceTarget by mutableStateOf<List<String>>(emptyList())
        private set
    var activeStreamMemoryTraceTarget by mutableStateOf<List<AiMemoryTraceItem>>(emptyList())
        private set
    var activeWebActivity by mutableStateOf<AiWebActivity?>(null)
        private set
    var activeWebAccess by mutableStateOf<AiWebAccess?>(null)
        private set
    var pendingApproval by mutableStateOf<AiChatToolApproval?>(null)
        private set
    var providerStateRevision by mutableIntStateOf(0)
        private set

    private var sendingJob: Job? = null
    private var bookLookupJob: Job? = null
    private var bookLookupRevision = 0
    private var contextUsageRevision = 0
    private var latestCheckpoint: AiChatDurableContextUpdate? = null
    private var turnModel: AiModelSelection? = null
    private var pendingHostActions by mutableIntStateOf(0)
    private var activeToolName: String? = null
    private var automaticContextCompacting = false
    private var writingReply = false

    val hasWork get() = sending || activeBookLookupMessageId != null || pendingHostActions > 0
    val hasDraft get() = imageDraftAssets.isNotEmpty() || directImageDraft ||
        input.isNotBlank() && (mode.id != AgentModeRegistry.BOOK_SEARCH_ID || bookSearchDraftEdited)
    val runningHostAction get() = pendingHostActions > 0

    fun taskSummary(): AiChatTask? {
        val state = when {
            pendingApproval != null -> AiChatTaskState.CONFIRM_TOOL
            manualContextCompacting || automaticContextCompacting -> AiChatTaskState.COMPACTING
            activeBookLookupMessageId != null -> AiChatTaskState.BOOK_SOURCES
            sending -> activeWebActivity?.activeAction?.chatTaskState()
                ?: activeToolName?.let(::toolChatTaskState)
                ?: if (writingReply) AiChatTaskState.GENERATING else AiChatTaskState.THINKING
            runningHostAction -> AiChatTaskState.WORKING
            else -> null
        }
        val action = if (state == null) {
            messages.lastOrNull()?.pendingChatTaskAction(taskInteractionPolicy)
        } else null
        val taskState = state ?: action?.first ?: return null
        val entry = activeModeEntryContext
        val workKey = entry.characterCardWorkKey()?.takeIf {
            AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE in inputAttachments.loadedSkillIds()
        }
        val title = entry.payloadString("book_name").takeIf { workKey != null && it.isNotBlank() }
            ?: savedSnapshot?.title
            ?: resolveConversationTitle(entry?.title, messages.deriveChatTitle(), null)
        return AiChatTask(id, title, taskState, workKey, action?.second, savedSnapshot?.createdAt)
    }

    init {
        if (saved == null) {
            uploadMessages.add(chatClient.newSystemMessage())
        } else {
            messages.addAll(saved.messages.map { it.toUiMessage() })
            uploadMessages.replaceUploadMessages(saved, chatClient)
            contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
            if (messages.any { it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.RECOVERABLE }) {
                contextStatusText = "上次请求未完成，发送新消息或点击重新生成可恢复"
            }
        }
    }

    fun updateMessage(id: String, transform: (ChatUiMessage) -> ChatUiMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) messages[index] = transform(messages[index])
    }

    fun updateDeliveryState(ids: Collection<String>, state: ChatDeliveryState) {
        ids.forEach { id -> updateMessage(id) { it.copy(deliveryState = state) } }
    }

    fun markInteractionResolved(
        messageId: String,
        interactionId: String,
        resultLabel: String,
        resultSelections: Map<String, String> = emptyMap(),
    ) {
        updateMessage(messageId) { message ->
            message.copy(
                resolvedInteractionIds = (message.resolvedInteractionIds + interactionId).distinct(),
                interactionResultLabels = message.interactionResultLabels + (interactionId to resultLabel.trim()),
                interactionResultSelections = if (resultSelections.isEmpty()) message.interactionResultSelections
                    else message.interactionResultSelections + (interactionId to resultSelections),
            )
        }
        persist()
    }

    private fun persistedEntryContext(): AgentModeEntryContext? {
        val entry = activeModeEntryContext ?: return null
        if (mode.id != AgentModeRegistry.BOOK_SEARCH_ID) return entry
        val draft = bookSearchDraftForHistory(input, messages.any { !it.loading }, bookSearchDraftEdited)
        return entry.copy(payload = entry.payload.deepCopy().apply {
            if (draft == null) remove(AI_BOOK_SEARCH_DRAFT) else addProperty(AI_BOOK_SEARCH_DRAFT, draft)
        })
    }

    fun persist(outcome: AiChatTaskState? = null): Deferred<Unit>? {
        if (deleted) return null
        saveImageDraft()
        val old = savedSnapshot
        val entry = persistedEntryContext()
        val visible = messages.filterNot { it.loading }
        val hasContent = hasChatHistoryContent(visible.isNotEmpty(), modeEntryStarted, entry.bookSearchDraft())
        if (!hasContent && old == null && imageDraftAssets.isEmpty() && !directImageDraft) return null
        val now = System.currentTimeMillis()
        val base = AiChatSessionSnapshot(
            id = id,
            title = resolveConversationTitle(entry?.title, visible.deriveChatTitle(), old?.title),
            modeId = mode.id,
            modeRevision = mode.revision,
            modeEntryContext = entry,
            modeEntryStarted = modeEntryStarted,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            isPinned = old?.isPinned == true,
            messages = visible.map { it.toSnapshot() },
            loadedSkillIds = inputAttachments.loadedSkillIds(),
            enabledMcpCapabilityIds = enabledMcpCapabilityIds.toList(),
            uploadMessages = uploadMessages.map { it.deepCopy().asJsonObject },
        )
        val checkpoint = latestCheckpoint
        val loadingId = activeLoadingMessageId
        val snapshot = if (checkpoint != null && loadingId != null) {
            AiChatHistoryStore.mergeDurableTurn(
                baseSession = base,
                assistantMessageId = loadingId,
                update = checkpoint,
                now = now,
                modelSelection = turnModel,
                assistantMessageIndex = messages.indexOfFirst { it.id == loadingId }.takeIf { it >= 0 },
            )
        } else base
        savedSnapshot = snapshot
        owner.refreshTask(this, outcome)
        return owner.save(snapshot)
    }

    fun replaceImageDraftAssets(assets: List<String>, editAsset: String? = null) {
        val editAssets = (imageDraft.editAssets.orEmpty() + listOfNotNull(editAsset))
            .distinct()
            .filter(assets::contains)
        imageDraft = imageDraft.copy(
            assets = assets,
            direct = imageDraft.direct && editAsset == null,
            editAssets = editAssets.ifEmpty { null },
        )
    }

    fun setExplicitImageDraft(selected: Boolean) {
        imageDraft = imageDraft.copy(direct = selected, editAssets = null)
    }

    fun clearImageDraft() {
        imageDraft = AiImageDraft()
    }

    fun saveImageDraft() {
        if (deleted) return
        owner.writeImageDraft(id, imageDraft.copy(prompt = input))
    }

    fun pin(time: Long) {
        savedSnapshot = savedSnapshot?.copy(isPinned = true, updatedAt = time)
    }

    fun delete() {
        deleted = true
        scope.cancel()
    }

    fun release() = scope.cancel()

    fun stopSending() {
        cancelBookSourceLookup()
        sendingJob?.cancel()
    }

    fun <T> runHostAction(action: suspend AiChatSessionRuntime.() -> T): Deferred<T> {
        pendingHostActions++
        owner.refreshTask(this)
        return scope.async(start = CoroutineStart.UNDISPATCHED) {
            var completed = false
            try {
                action().also { completed = true }
            } finally {
                pendingHostActions--
                owner.refreshTask(this@AiChatSessionRuntime, AiChatTaskState.COMPLETED.takeIf { completed })
                owner.releaseIfIdle(this@AiChatSessionRuntime)
            }
        }
    }

    private suspend fun confirmToolCalls(calls: List<AiPendingToolCall>): Boolean {
        if (calls.isEmpty()) return true
        return withContext(owner.mainContext) {
            val approval = AiChatToolApproval(calls)
            pendingApproval = approval
            owner.refreshTask(this@AiChatSessionRuntime)
            try {
                approval.response.await()
            } finally {
                if (pendingApproval === approval) {
                    pendingApproval = null
                    owner.refreshTask(this@AiChatSessionRuntime)
                }
            }
        }
    }

    fun syncActiveSkillContext() {
        val skill = inputAttachments.lastOrNull {
            it.type == AiChatInputAttachmentType.SKILL
        }
        AiChatContextManager.syncActiveSkill(
            messages = uploadMessages,
            snapshot = skill?.activeSkillSnapshot()
        )
    }

    fun syncModeEntryContext() {
        AiChatContextManager.syncModeEntryContext(
            messages = uploadMessages,
            context = activeModeEntryContext.takeUnless { mode.id == AgentModeRegistry.BOOK_SEARCH_ID }
        )
    }

    fun refreshContextUsage() {
        syncModeEntryContext()
        syncActiveSkillContext()
        val revision = ++contextUsageRevision
        val pendingContexts = inputAttachments.filter {
            it.type == AiChatInputAttachmentType.CONTEXT
        }
        val snapshot = buildList {
            addAll(uploadMessages.map { it.deepCopy().asJsonObject })
            if (pendingContexts.isNotEmpty()) {
                add(chatClient.newUserMessage(buildUserUploadContent("", pendingContexts)))
            }
        }
        val promptUsageAnchor = messages.latestPromptUsageAnchor()
        val mcpCapabilityIds = enabledMcpCapabilityIds.toList()
        val activeSkill = inputAttachments.lastOrNull {
            it.type == AiChatInputAttachmentType.SKILL
        }
        contextUsage = AiChatContextManager.usage(snapshot)
        scope.launch {
            val measured = withContext(Dispatchers.IO) {
                runCatching {
                    chatClient.estimateContextUsage(
                        snapshot,
                        promptUsageAnchor,
                        skillId = activeSkill?.id?.removePrefix("skill.").orEmpty(),
                        skillContentHash = activeSkill?.skillContentHash.orEmpty(),
                        availableSkillIds = activeSkill?.availableSkillIds.orEmpty(),
                        enabledMcpCapabilityIds = mcpCapabilityIds
                    )
                }
                    .getOrElse { AiChatContextManager.usage(snapshot) }
            }
            if (!deleted && revision == contextUsageRevision) {
                contextUsage = measured
            }
        }
    }

    fun cancelBookSourceLookup() {
        val messageId = activeBookLookupMessageId ?: return
        bookLookupRevision++
        bookLookupJob?.cancel()
        bookLookupJob = null
        activeBookLookupMessageId = null
        updateMessage(messageId) { message ->
            val reply = message.bookSearch ?: return@updateMessage message
            val lookup = reply.lookup ?: return@updateMessage message
            message.copy(bookSearch = reply.copy(lookup = lookup.copy(status = AiBookSourceStatus.INTERRUPTED)))
        }
        persist()
    }

    fun bookSearchScope(): String = activeModeEntryContext?.payload?.get(AI_BOOK_SEARCH_SCOPE)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()

    fun launchBookSourceLookup(
        messageId: String,
        candidate: AiBookSearchCandidate,
        keyword: String = candidate.title,
        automatically: Boolean = false,
    ) {
        if (deleted || mode.id != AgentModeRegistry.BOOK_SEARCH_ID || sending || !candidate.searchable) return
        val reply = messages.firstOrNull { it.id == messageId }?.bookSearch ?: return
        if (candidate.key in reply.rejectedKeys) return
        val lookupScope = bookSearchScope()
        val attemptKey = "${candidate.key}\u0000$lookupScope"
        if (automatically && messages.any { attemptKey in it.bookSearch?.attemptedKeys.orEmpty() }) return
        val audioConfirmed = activeModeEntryContext?.payload.isConfirmedBookSearchAudioScope(candidate.media)
        cancelBookSourceLookup()
        val revision = ++bookLookupRevision
        val capabilities = enabledMcpCapabilityIds.toList()
        activeBookLookupMessageId = messageId
        fun isCurrent() = !deleted && bookLookupRevision == revision
        fun updateLookup(lookup: AiBookSourceLookup) {
            updateMessage(messageId) { message ->
                val state = message.bookSearch ?: return@updateMessage message
                message.copy(bookSearch = state.copy(
                    lookup = lookup,
                    attemptedKeys = (state.attemptedKeys + attemptKey).distinct(),
                ))
            }
        }
        val running = AiBookSourceLookup(candidate.key, keyword, AiBookSourceStatus.RUNNING)
        updateLookup(running)
        persist()
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    McpInternalChannel.searchPreciseBook(
                        candidate, keyword, lookupScope, audioConfirmed, capabilities,
                    ) { hits ->
                        scope.launch {
                            if (isCurrent() && activeBookLookupMessageId == messageId) {
                                val hadHit = messages.firstOrNull { it.id == messageId }
                                    ?.bookSearch?.lookup?.hits.orEmpty().isNotEmpty()
                                updateLookup(running.copy(hits = hits))
                                // Preserve verified cards if the app is interrupted during the remaining requests.
                                if (!hadHit && hits.isNotEmpty()) persist()
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val partial = messages.firstOrNull { it.id == messageId }?.bookSearch?.lookup?.hits.orEmpty()
                running.copy(status = AiBookSourceStatus.FAILED, hits = partial)
            }
            if (isCurrent()) {
                updateLookup(result)
                activeBookLookupMessageId = null
                bookLookupJob = null
                persist(outcome = result.chatTaskOutcome())
                owner.releaseIfIdle(this@AiChatSessionRuntime)
            }
        }.also { bookLookupJob = it }
    }

    fun sendCurrentMessages(activeUserMessageIds: List<String>, modelSelection: AiModelSelection) {
        if (deleted || sending) return
        val displayModel = if (modelSelection.isCodexChatSelection() &&
            messages.lastOrNull { it.role == ChatRole.USER }?.directImageAction == true
        ) {
            modelSelection.copy(modelId = CODEX_IMAGE_MODEL)
        } else modelSelection
        turnModel = displayModel
        latestCheckpoint = null
        syncModeEntryContext()
        syncActiveSkillContext()
        sending = true
        val turnId = activeUserMessageIds.firstOrNull()
            ?: messages.asReversed().firstOrNull { message -> message.role == ChatRole.USER }?.id
            ?: UUID.randomUUID().toString()
        val activeSkill = inputAttachments.lastOrNull { attachment ->
            attachment.type == AiChatInputAttachmentType.SKILL
        }
        val promptUsageAnchor = messages.latestPromptUsageAnchor()
        val loadingId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        activeLoadingMessageId = loadingId
        activeLoadingStartedAt = startedAt
        activeStreamContentTarget = ""
        activeStreamReasoningTarget = ""
        activeStreamToolTraceTarget = emptyList()
        activeStreamMemoryTraceTarget = emptyList()
        activeWebActivity = null
        activeWebAccess = null
        activeToolName = null
        automaticContextCompacting = false
        writingReply = false
        val uploadMessagesBeforeSend = uploadMessages.map { it.deepCopy().asJsonObject }
        val previousImageId = if (mode.id == AgentModeRegistry.GENERAL_ID) {
            messages.asReversed().firstNotNullOfOrNull { message ->
                if (message.role != ChatRole.ASSISTANT) null else {
                    message.imageAssetIds.singleOrNull() ?: message.toolReceipts.filter { it.isCodexImage() }
                        .singleOrNull()?.let { receipt ->
                            AiImageAssets.generatedId(receipt.conversationId, receipt.receiptId)
                                .takeIf { AiImageAssets.file(it)?.isFile == true }
                        }
                }
            }
        } else null
        val mcpCapabilityIds = enabledMcpCapabilityIds.toList()
        val requireWebSearch = mode.id == AgentModeRegistry.BOOK_SEARCH_ID
        val allowWebForTurn = requireWebSearch || mode.id == AgentModeRegistry.GENERAL_ID
        val webUserText = messages.lastOrNull { it.role == ChatRole.USER }?.content.orEmpty()
        messages += ChatUiMessage(
            id = loadingId,
            role = ChatRole.ASSISTANT,
            content = "正在思考...",
            elapsedMs = 0L,
            loading = true,
            usageProviderId = displayModel.providerId,
            usageModelId = displayModel.modelId,
        )
        sendingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val requestMessages = uploadMessagesBeforeSend
                .map { it.deepCopy().asJsonObject }
                .toMutableList()
            val result = runCatching {
                persist()?.await()
                withContext(Dispatchers.IO) {
                    chatClient.send(
                        messages = requestMessages,
                        conversationId = id,
                        turnId = turnId,
                        skillId = activeSkill?.id?.removePrefix("skill.").orEmpty(),
                        skillRevision = activeSkill?.skillRuntimeRevision.orEmpty(),
                        skillContentHash = activeSkill?.skillContentHash.orEmpty(),
                        availableSkillIds = activeSkill?.availableSkillIds.orEmpty(),
                        memoryPolicy = mode.memoryPolicy,
                        runtimeHooks = mode.runtimeHooks +
                            if (mode.allowsUserSkills) {
                                activeSkill?.skillRuntime?.runtimeHooks.orEmpty()
                            } else {
                                emptyList()
                            },
                        promptUsageAnchor = promptUsageAnchor,
                        allowWeb = allowWebForTurn,
                        requireWebSearch = requireWebSearch,
                        webUserText = webUserText,
                        onWebUpdate = { update, access ->
                            scope.launch {
                                if (!deleted && activeLoadingMessageId == loadingId) {
                                    val changed = activeWebActivity?.activeAction != update.activeAction
                                    activeWebActivity = update
                                    activeWebAccess = access
                                    if (changed) owner.refreshTask(this@AiChatSessionRuntime)
                                }
                            }
                        },
                        onStreamUpdate = { update: AiChatStreamUpdate ->
                            scope.launch {
                                if (!deleted && activeLoadingMessageId == loadingId) {
                                    val visibleText = if (requireWebSearch) {
                                        aiBookSearchVisibleText(update.content)
                                    } else update.content
                                    val startedReply = !writingReply && activeToolName == null &&
                                        visibleText.isNotBlank() && visibleText.length > activeStreamContentTarget.length
                                    activeStreamContentTarget = visibleText
                                    activeStreamReasoningTarget = update.reasoning.orEmpty()
                                    activeStreamToolTraceTarget = update.toolTrace
                                    activeStreamMemoryTraceTarget = update.memoryTrace
                                    if (startedReply) {
                                        writingReply = true
                                        owner.refreshTask(this@AiChatSessionRuntime)
                                    }
                                }
                            }
                        },
                        onDurableContextUpdate = { update ->
                            withContext(owner.mainContext + NonCancellable) {
                                if (!deleted && activeLoadingMessageId == loadingId) {
                                    latestCheckpoint = update
                                    if (update.imageAssetIds.isNotEmpty() || update.toolReceipts.any { it.isCodexImage() }) {
                                        imageWait = null
                                        updateMessage(loadingId) {
                                            it.copy(toolReceipts = update.toolReceipts, imageAssetIds = update.imageAssetIds)
                                        }
                                    }
                                    persist()?.await()
                                }
                            }
                        },
                        onContextEvent = { event ->
                            scope.launch contextUpdate@{
                                if (deleted || activeLoadingMessageId != loadingId) return@contextUpdate
                                when (event.type) {
                                    AiChatContextEventType.STARTED -> {
                                        automaticContextCompacting = true
                                        event.beforeUsage?.let { contextUsage = it }
                                        contextStatusText = "正在自动压缩上下文..."
                                    }
                                    AiChatContextEventType.COMPLETED -> {
                                        automaticContextCompacting = false
                                        val after = event.afterTokens ?: event.beforeTokens
                                        event.afterUsage?.let { contextUsage = it }
                                        event.compaction?.let {
                                            contextCompactionCount = it.revision
                                        }
                                        contextStatusText =
                                            "上下文已自动压缩 · ${formatContextTokens(event.beforeTokens)} → ${formatContextTokens(after)}"
                                    }
                                }
                                owner.refreshTask(this@AiChatSessionRuntime)
                            }
                        },
                        // Only the host can start a book-source search after validating the reply or user selection.
                        enabledMcpCapabilityIds = if (requireWebSearch) emptyList() else mcpCapabilityIds,
                        selection = modelSelection,
                        previousImageId = previousImageId,
                        onImageStarted = { editing ->
                            scope.launch {
                                if (!deleted && activeLoadingMessageId == loadingId) {
                                    imageWait = AiChatImageWait(System.currentTimeMillis(), editing)
                                }
                            }
                        },
                        onToolActivity = { toolName ->
                            scope.launch {
                                if (!deleted && activeLoadingMessageId == loadingId) {
                                    activeToolName = toolName
                                    if (toolName != CODEX_IMAGE_TOOL) imageWait = null
                                    if (toolName != null) writingReply = false
                                    owner.refreshTask(this@AiChatSessionRuntime)
                                }
                            }
                        },
                        onToolConfirmationRequired = { pendingCalls ->
                            confirmToolCalls(pendingCalls)
                        }
                    )
                }
            }
            if (deleted || activeLoadingMessageId != loadingId) {
                return@launch
            }
            val elapsedMs = System.currentTimeMillis() - startedAt
            val canceled = result.exceptionOrNull() is CancellationException
            var bookToLookup: Pair<String, AiBookSearchCandidate>? = null
            result.onSuccess {
                updateDeliveryState(activeUserMessageIds, ChatDeliveryState.SENT)
                uploadMessages.clear()
                uploadMessages.addAll(
                    it.contextMessages.map { message -> message.deepCopy().asJsonObject }
                )
                val loadingIndex = messages.indexOfFirst { message -> message.id == loadingId }
                val baseMessage = it.toUiMessage(elapsedMs, displayModel)
                val completedMessage = if (requireWebSearch) {
                    val rejected = messages.flatMap { message -> message.bookSearch?.rejectedKeys.orEmpty() }.toSet()
                    val reply = parseAiBookSearchReply(it.content, it.webActivity)?.let { parsed ->
                        parsed.copy(rejectedKeys = parsed.candidates.map { candidate -> candidate.key }.filter { key -> key in rejected })
                    }
                    val unverified = reply == null || it.webActivity?.searchFailed == true ||
                        it.webActivity?.externalError != null || it.webActivity?.sources.isNullOrEmpty()
                    baseMessage.copy(
                        content = aiBookSearchVisibleText(baseMessage.content).ifBlank {
                            reply?.question?.takeIf { question -> question.isNotBlank() }
                                ?: appCtx.getString(R.string.ai_book_search_unverified)
                        },
                        bookSearch = reply,
                        meta = if (unverified) listOfNotNull(
                            baseMessage.meta, appCtx.getString(R.string.ai_book_search_unverified)
                        ).joinToString(" · ") else baseMessage.meta,
                    ).also { message ->
                        reply?.automaticCandidate?.let { candidate -> bookToLookup = message.id to candidate }
                    }
                } else baseMessage
                val compactionMessages = it.contextCompactions.map { record ->
                    ChatUiMessage(
                        role = ChatRole.ASSISTANT,
                        content = "上下文已自动压缩",
                        contextCompaction = record
                    )
                }
                if (loadingIndex >= 0) {
                    messages.removeAt(loadingIndex)
                    messages.addAll(loadingIndex, compactionMessages + completedMessage)
                } else {
                    messages += compactionMessages
                    messages += completedMessage
                }
                contextUsage = it.contextUsage
                contextCompactionCount = AiChatContextManager.compactionRevision(uploadMessages)
            }.onFailure {
                val replyEnd = it as? AiAssistantReplyEndException
                val chatGptError = (replyEnd?.cause ?: it) as? AiChatGptException
                if (requireWebSearch) when (chatGptError?.messageRes) {
                    R.string.ai_chatgpt_sign_in_required,
                    R.string.ai_chatgpt_reauthorize_required,
                    R.string.ai_chatgpt_plan_permission_required -> providerStateRevision++
                    else -> Unit
                }
                val replyNotice = replyEnd?.let { end ->
                    appCtx.getString(when (end.reason) {
                        AiAssistantReplyEndReason.TRUNCATED -> R.string.ai_assistant_reply_truncated
                        AiAssistantReplyEndReason.INTERRUPTED -> R.string.ai_assistant_reply_interrupted
                        AiAssistantReplyEndReason.REFUSED -> R.string.ai_assistant_reply_refused
                    })
                }
                val durable = latestCheckpoint
                uploadMessages.clear()
                uploadMessages.addAll(
                    (durable?.contextMessages ?: uploadMessagesBeforeSend).map { message ->
                        message.deepCopy().asJsonObject
                    }
                )
                val savedContent = durable?.visibleContent
                    ?.takeIf { content -> content.isNotBlank() }
                val interruptedContent = replyEnd?.partialContent
                    ?: activeStreamContentTarget.takeIf { savedContent == null }.orEmpty()
                val draft = AiChatContextManager.interruptedReplyContent(interruptedContent, it)
                val visibleInterruptedContent = interruptedContent.takeIf { content ->
                    content.isNotBlank() && (replyEnd == null || draft != null)
                }
                val partialContent = listOfNotNull(savedContent, visibleInterruptedContent)
                    .distinct().joinToString("\n\n").takeIf { content -> content.isNotBlank() }
                val hasImage = durable?.imageAssetIds?.isNotEmpty() == true ||
                    durable?.toolReceipts?.any { receipt -> receipt.isCodexImage() } == true
                if (partialContent != null || replyNotice != null || hasImage) {
                    updateMessage(loadingId) { loading ->
                        if (draft != null) {
                            uploadMessages += AiChatContextManager.interruptedReplyMessage(loading.id, draft)
                        }
                        loading.copy(
                            content = (partialContent ?: replyNotice.orEmpty()).let { content ->
                                if (requireWebSearch) aiBookSearchVisibleText(content).ifBlank {
                                    appCtx.getString(R.string.ai_book_search_unverified)
                                } else content
                            },
                            uploadContent = draft,
                            reasoning = if (replyEnd != null) {
                                replyEnd.partialReasoning ?: durable?.reasoning.takeIf { savedContent != null }
                            } else {
                                durable?.reasoning ?: activeStreamReasoningTarget.takeIf { it.isNotBlank() }
                            },
                            toolTrace = durable?.toolTrace ?: activeStreamToolTraceTarget,
                            toolReceipts = durable?.toolReceipts.orEmpty(),
                            imageAssetIds = durable?.imageAssetIds.orEmpty(),
                            webActivity = durable?.webActivity ?: activeWebActivity?.settledOrNull(),
                            loading = false,
                            meta = when {
                                partialContent == null && !hasImage -> "error"
                                replyNotice != null -> replyNotice
                                canceled -> "已中断，可恢复"
                                else -> "执行中断，结果已保留"
                            }
                        )
                    }
                } else {
                    messages.removeAll { message -> message.id == loadingId }
                }
                contextStatusText = null
                updateDeliveryState(
                    activeUserMessageIds,
                    when {
                        canceled -> ChatDeliveryState.RECOVERABLE
                        replyEnd != null -> ChatDeliveryState.SENT
                        else -> ChatDeliveryState.FAILED
                    }
                )
                if (!canceled && (replyEnd == null || chatGptError != null)) {
                    val message = if (chatGptError?.messageRes == R.string.ai_chatgpt_usage_limit) {
                        val label = appCtx.getString(R.string.ai_chatgpt_manage_usage)
                        "${chatGptError.message}\n\n[$label]($CHATGPT_USAGE_URL)"
                    } else chatGptError?.message ?: it.localizedMessage ?: it.toString()
                    messages += ChatUiMessage(
                        role = ChatRole.ASSISTANT,
                        content = message,
                        meta = "error"
                    )
                }
            }
            latestCheckpoint = null
            turnModel = null
            persist()
            sending = false
            sendingJob = null
            activeLoadingMessageId = null
            activeLoadingStartedAt = null
            activeStreamContentTarget = ""
            activeStreamReasoningTarget = ""
            activeStreamToolTraceTarget = emptyList()
            activeStreamMemoryTraceTarget = emptyList()
            activeWebActivity = null
            activeWebAccess = null
            activeToolName = null
            imageWait = null
            automaticContextCompacting = false
            writingReply = false
            refreshContextUsage()
            if (canceled) {
                contextStatusText = null
            } else if (!sendQueuedMessages(modelSelection)) {
                bookToLookup?.let { (messageId, candidate) ->
                    launchBookSourceLookup(messageId, candidate, automatically = true)
                }
            }
            owner.refreshTask(this@AiChatSessionRuntime, result.chatTaskOutcome())
            owner.releaseIfIdle(this@AiChatSessionRuntime)
        }
    }

    private fun sendQueuedMessages(modelSelection: AiModelSelection): Boolean {
        val queued = messages.filter { it.role == ChatRole.USER && it.deliveryState == ChatDeliveryState.QUEUED }
        if (queued.isEmpty()) return false
        val queuedIds = queued.map { it.id }
        queued.forEach { message ->
            uploadMessages += chatClient.newUserMessage(
                AiChatContextManager.removeEmbeddedSkill(message.uploadContent?.takeIf { it.isNotBlank() } ?: message.content),
                message.imageAssetIds,
                message.directImageAction,
            )
        }
        updateDeliveryState(queuedIds, ChatDeliveryState.IN_FLIGHT)
        sendCurrentMessages(queuedIds, modelSelection)
        return true
    }

    fun manuallyCompressContext(modelSelection: AiModelSelection) {
        if (deleted || sending || manualContextCompacting) return
        syncModeEntryContext()
        syncActiveSkillContext()
        manualContextCompacting = true
        sending = true
        owner.refreshTask(this)
        val promptUsageAnchor = messages.latestPromptUsageAnchor()
        val snapshot = uploadMessages.map { it.deepCopy().asJsonObject }.toMutableList()
        val mcpCapabilityIds = enabledMcpCapabilityIds.toList()
        val activeSkill = inputAttachments.lastOrNull { attachment ->
            attachment.type == AiChatInputAttachmentType.SKILL
        }
        sendingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    chatClient.compactContext(
                        messages = snapshot,
                        conversationId = id,
                        skillId = activeSkill?.id?.removePrefix("skill.").orEmpty(),
                        skillRevision = activeSkill?.skillRuntimeRevision.orEmpty(),
                        skillContentHash = activeSkill?.skillContentHash.orEmpty(),
                        availableSkillIds = activeSkill?.availableSkillIds.orEmpty(),
                        runtimeHooks = mode.runtimeHooks +
                            if (mode.allowsUserSkills) {
                                activeSkill?.skillRuntime?.runtimeHooks.orEmpty()
                            } else {
                                emptyList()
                            },
                        promptUsageAnchor = promptUsageAnchor,
                        enabledMcpCapabilityIds = mcpCapabilityIds,
                        selection = modelSelection,
                        onContextEvent = { event ->
                            scope.launch contextUpdate@{
                                if (deleted || !manualContextCompacting) return@contextUpdate
                                when (event.type) {
                                    AiChatContextEventType.STARTED -> {
                                        event.beforeUsage?.let { contextUsage = it }
                                        contextStatusText = "正在手动压缩上下文..."
                                    }

                                    AiChatContextEventType.COMPLETED -> {
                                        val after = event.afterTokens ?: event.beforeTokens
                                        event.afterUsage?.let { contextUsage = it }
                                        event.compaction?.let {
                                            contextCompactionCount = it.revision
                                        }
                                        contextStatusText =
                                            "上下文已手动压缩 · ${formatContextTokens(event.beforeTokens)} → ${formatContextTokens(after)}"
                                    }
                                }
                            }
                        }
                    )
                }
            }
            if (deleted) return@launch
            val canceled = result.exceptionOrNull() is CancellationException
            result.onSuccess { compacted ->
                uploadMessages.clear()
                uploadMessages.addAll(
                    compacted.contextMessages.map { message -> message.deepCopy().asJsonObject }
                )
                contextUsage = compacted.contextUsage
                contextCompactionCount = compacted.record.revision
                messages += ChatUiMessage(
                    role = ChatRole.ASSISTANT,
                    content = "上下文已手动压缩",
                    contextCompaction = compacted.record
                )
                persist()
            }.onFailure { error ->
                contextStatusText = null
                if (!canceled) {
                    messages += ChatUiMessage(
                        role = ChatRole.ASSISTANT,
                        content = error.localizedMessage ?: "上下文压缩失败",
                        meta = "error",
                    )
                    persist()
                }
            }
            manualContextCompacting = false
            sending = false
            sendingJob = null
            refreshContextUsage()
            if (!canceled) sendQueuedMessages(modelSelection)
            owner.refreshTask(this@AiChatSessionRuntime, result.chatTaskOutcome())
            owner.releaseIfIdle(this@AiChatSessionRuntime)
        }
    }
}
