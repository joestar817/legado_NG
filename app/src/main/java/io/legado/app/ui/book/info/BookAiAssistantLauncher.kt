package io.legado.app.ui.book.info

import android.content.Context
import com.google.gson.JsonObject
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.book.menuCapabilities
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.utils.startActivity

object BookAiAssistantLauncher {

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
