package io.legado.app.ui.book.info

import android.content.Context
import com.google.gson.JsonObject
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.book.menuCapabilities
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.utils.startActivity

object BookAiAssistantLauncher {

    fun openCharacterCardFromSelection(
        context: Context,
        book: Book,
        selectedName: String,
        chapterIndex: Int,
        chapterTitle: String?
    ) {
        val name = selectedName.trim()
        if (name.isBlank()) return
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val payload = JsonObject().apply {
            addProperty("work_key", workKey)
            addProperty("book_name", book.name)
            addProperty("book_author", book.author)
            addProperty("book_url", book.bookUrl)
            book.originName?.takeIf { it.isNotBlank() }?.let { addProperty("origin_name", it) }
            addProperty("character_name", name)
            addProperty("selected_text", name)
            addProperty("current_chapter_index", chapterIndex + 1)
            chapterTitle?.takeIf { it.isNotBlank() }
                ?.let { addProperty("current_chapter_title", it) }
            addProperty("total_chapters", book.totalChapterNum)
        }
        val entryContext = AgentModeEntryContext(
            contextId = "text_selection_character_card",
            title = "角色卡：$name",
            payload = payload,
        )
        val selectionAttachment = JsonObject().apply {
            addProperty("id", "text_selection_character")
            addProperty("title", "选中的角色：$name")
            addProperty(
                "prompt",
                buildString {
                    append("用户从《").append(book.name).append("》正文中选中了名字「")
                    append(name).append("」。")
                    append("当前章节：第").append(chapterIndex + 1).append("章")
                    chapterTitle?.takeIf { it.isNotBlank() }
                        ?.let { append(' ').append(it) }
                    append("。work_key=").append(workKey)
                    append("；book_url=").append(book.bookUrl)
                    append("。请只为这个角色生成待审核角色卡，不要扩展到其他角色。")
                }
            )
        }
        context.startActivity<AiChatActivity> {
            putExtra(AiChatActivity.EXTRA_ENTRY, AiChatActivity.ENTRY_TEXT_SELECTION)
            putStringArrayListExtra(
                AiChatActivity.EXTRA_LOADED_SKILL_IDS,
                arrayListOf(AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE)
            )
            putExtra(AiChatActivity.EXTRA_MODE_ENTRY_CONTEXT, entryContext.toJson())
            putStringArrayListExtra(
                AiChatActivity.EXTRA_CONTEXT_ATTACHMENTS,
                arrayListOf(selectionAttachment.toString())
            )
            putExtra(
                AiChatActivity.EXTRA_INITIAL_INPUT,
                "为《${book.name}》中的角色「$name」创建角色卡。" +
                    "这是指定角色模式：直接使用当前书已缓存章节，不要再询问扫描范围或强度；" +
                    "先查已有角色卡，再用正文搜索定位该角色的章节，只读命中章节。" +
                    "采样完成后先展示待审核表格，等我确认后再写入。"
            )
            putExtra(AiChatActivity.EXTRA_EXPAND_SUGGESTIONS, true)
        }
    }

    fun openBookScan(context: Context, book: Book) {
        if (!book.menuCapabilities.textContent) return
        val payload = JsonObject().apply {
            addProperty(
                "work_key",
                BookCharacterProfile.workKey(book.name, book.author),
            )
            addProperty("book_name", book.name)
            addProperty("book_author", book.getRealAuthor())
            addProperty("book_url", book.bookUrl)
            addProperty("origin_name", book.originName)
            book.kind?.takeIf { it.isNotBlank() }?.let { addProperty("category", it) }
            book.wordCount?.takeIf { it.isNotBlank() }?.let { addProperty("word_count", it) }
            addProperty("total_chapters", book.totalChapterNum)
            book.durChapterTitle?.takeIf { it.isNotBlank() }?.let {
                addProperty("current_chapter_index", book.durChapterIndex + 1)
                addProperty("current_chapter_title", it)
            }
            book.latestChapterTitle?.takeIf { it.isNotBlank() }?.let {
                addProperty("latest_chapter_title", it)
            }
        }
        val entryContext = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：${book.name}",
            payload = payload,
        )
        context.startActivity<AiChatActivity> {
            putExtra(AiChatActivity.EXTRA_ENTRY, AiChatActivity.ENTRY_BOOK_SCAN)
            putExtra(AiChatActivity.EXTRA_MODE_ENTRY_CONTEXT, entryContext.toJson())
        }
    }
}
