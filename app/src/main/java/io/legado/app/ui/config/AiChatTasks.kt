package io.legado.app.ui.config

import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AiBookSourceLookup
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.help.ai.AiChatInteractionParser
import io.legado.app.help.ai.AiChatInteractionPolicyContract
import io.legado.app.help.ai.AiChatInteractionPolicyRegistry
import io.legado.app.help.ai.AiWebActivityAction
import io.legado.app.help.ai.CODEX_IMAGE_TOOL
import io.legado.app.help.ai.isCodexImage
import io.legado.pet.api.PetActivities
import io.noties.markwon.Markwon
import splitties.init.appCtx

internal const val CHARACTER_CARD_ENTRY_CONTEXT = "book_detail_character_card"

internal fun AgentModeEntryContext?.characterCardWorkKey(): String? =
    this?.takeIf { it.contextId == CHARACTER_CARD_ENTRY_CONTEXT }
        .payloadString("work_key")
        .takeIf { it.isNotBlank() }

internal enum class AiChatTaskState(@StringRes val label: Int, val needsAction: Boolean = false) {
    THINKING(R.string.ai_chat_task_thinking),
    GENERATING(R.string.ai_chat_task_generating),
    GENERATING_IMAGE(R.string.ai_codex_generating_image),
    WEB_SEARCH(R.string.ai_chat_web_searching),
    WEB_READ(R.string.ai_chat_web_reading),
    WEB_IMAGE_SEARCH(R.string.ai_chat_web_searching_images),
    BOOK_SOURCES(R.string.ai_book_search_searching),
    CHAPTERS(R.string.ai_chat_task_chapters),
    READ_CHAPTERS(R.string.ai_chat_task_read_chapters),
    CHECK_CACHE(R.string.ai_chat_task_check_cache),
    SAVE_CHARACTERS(R.string.ai_chat_task_save_characters),
    TOOL(R.string.ai_chat_task_tool),
    COMPACTING(R.string.ai_chat_task_compacting),
    WORKING(R.string.ai_chat_task_working),
    CONFIRM_TOOL(R.string.ai_chat_task_confirm_tool, needsAction = true),
    CONFIRM_BOOK(R.string.ai_chat_task_confirm_book, needsAction = true),
    CHOOSE_BOOK_SCOPE(R.string.ai_chat_task_choose_book_scope, needsAction = true),
    WAITING(R.string.ai_chat_task_waiting, needsAction = true),
    COMPLETED(R.string.ai_chat_task_completed),
    FAILED(R.string.ai_chat_task_failed);

    val isTerminal get() = this == COMPLETED || this == FAILED
    val isRunning get() = !needsAction && !isTerminal
    val showsBadge get() = isTerminal || needsAction && this != WAITING
}

internal data class AiChatTask(
    val sessionId: String,
    val title: String,
    val state: AiChatTaskState,
    val workKey: String? = null,
    val actionTitle: String? = null,
    val createdAt: Long? = null,
    val replyPreview: String? = null,
    val workActivity: String? = null,
    val completionOrder: Long = 0,
)

internal fun List<AiChatTask>.petWorkActivity(): String? =
    firstNotNullOfOrNull { task -> task.workActivity.takeIf { task.state.isRunning } }
        ?: PetActivities.WORK.takeIf { any { it.state.isRunning } }

internal fun AiBookSourceLookup.chatTaskOutcome(): AiChatTaskState? = when (status) {
    AiBookSourceStatus.COMPLETE -> AiChatTaskState.COMPLETED
    AiBookSourceStatus.INCOMPLETE -> if (hits.isEmpty()) AiChatTaskState.FAILED else AiChatTaskState.COMPLETED
    AiBookSourceStatus.FAILED, AiBookSourceStatus.DISABLED, AiBookSourceStatus.NO_SOURCES -> AiChatTaskState.FAILED
    else -> null
}

internal fun AiWebActivityAction.chatTaskState(): AiChatTaskState = when (this) {
    AiWebActivityAction.SEARCH -> AiChatTaskState.WEB_SEARCH
    AiWebActivityAction.READ -> AiChatTaskState.WEB_READ
    AiWebActivityAction.IMAGE_SEARCH -> AiChatTaskState.WEB_IMAGE_SEARCH
}

internal fun toolChatTaskState(toolName: String): AiChatTaskState = when (toolName) {
    CODEX_IMAGE_TOOL -> AiChatTaskState.GENERATING_IMAGE
    "search_web" -> AiChatTaskState.WEB_SEARCH
    "read_web_url" -> AiChatTaskState.WEB_READ
    "bookshelf_chapter_list", "bookshelf_text_search" -> AiChatTaskState.CHAPTERS
    "bookshelf_chapter_content_get", "bookshelf_chapter_snippets_get", "bookshelf_text_window_get" ->
        AiChatTaskState.READ_CHAPTERS
    "bookshelf_cache_status_get" -> AiChatTaskState.CHECK_CACHE
    "bookshelf_character_upsert", "bookshelf_character_delete", "bookshelf_character_set_enabled" ->
        AiChatTaskState.SAVE_CHARACTERS
    else -> AiChatTaskState.TOOL
}

internal fun ChatUiMessage.taskReplyPreview(markdown: Markwon): String? {
    if (role != ChatRole.ASSISTANT || loading) return null
    if (content.isBlank()) {
        return if (imageAssetIds.isNotEmpty() || toolReceipts.any { it.isCodexImage() }) {
            appCtx.getString(R.string.ai_codex_generated_image)
        } else null
    }
    val visible = AiChatInteractionParser.parse(content).content.take(2_048)
    return markdown.toMarkdown(normalizeAiChatMarkdown(visible)).toString()
        .replace(Regex("\\s+"), " ").trim().take(100).takeIf { it.isNotBlank() }
}

internal fun ChatUiMessage.pendingChatTaskAction(
    policy: AiChatInteractionPolicyContract?,
): Pair<AiChatTaskState, String?>? {
    if (loading || role != ChatRole.ASSISTANT || meta == "error") return null
    bookSearch?.let { reply ->
        val candidateAvailable = reply.candidates.any { it.key !in reply.rejectedKeys }
        if (candidateAvailable) {
            val state = when (reply.lookup?.status) {
                null -> AiChatTaskState.CONFIRM_BOOK
                AiBookSourceStatus.AUDIO_SCOPE_REQUIRED, AiBookSourceStatus.INVALID_SCOPE ->
                    AiChatTaskState.CHOOSE_BOOK_SCOPE
                else -> null
            }
            if (state != null) return state to null
        }
    }
    val render = AiChatInteractionParser.parse(content)
    val interaction = AiChatInteractionPolicyRegistry.filter(policy, render.interactions).accepted.firstOrNull {
        it.id !in resolvedInteractionIds && it.id !in interactionResultLabels
    } ?: return null
    return AiChatTaskState.WAITING to interaction.title.takeIf { it.isNotBlank() }
}
