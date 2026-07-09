package io.legado.app.ui.main.bookshelf

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig

fun Book.bookshelfAuthorText(context: Context): String {
    val unread = getUnreadChapterNum()
    if (!AppConfig.showUnread || unread <= 0) {
        return author
    }
    if (author.isBlank()) {
        return context.getString(R.string.bookshelf_unread_chapters, unread)
    }
    return context.getString(R.string.bookshelf_author_unread, author, unread)
}
