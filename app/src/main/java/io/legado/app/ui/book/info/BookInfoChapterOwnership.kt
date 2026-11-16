package io.legado.app.ui.book.info

import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.sync.Mutex

internal fun chaptersForBook(bookUrl: String?, chapters: List<BookChapter>?): List<BookChapter>? {
    if (bookUrl == null || chapters == null) return null
    return chapters.takeIf { list -> list.all { it.bookUrl == bookUrl } }
}

internal class BookInfoLoadedChapters {
    private data class Snapshot(val bookUrl: String, val chapters: List<BookChapter>)
    private val publicationMutex = Mutex()

    @Volatile
    private var latest: Snapshot? = null

    fun record(bookUrl: String, chapters: List<BookChapter>) {
        latest = Snapshot(bookUrl, chapters)
    }

    fun clear() {
        latest = null
    }

    fun forBook(bookUrl: String, displayed: List<BookChapter>?): List<BookChapter>? {
        val loaded = latest?.takeIf { it.bookUrl == bookUrl }?.chapters
        return chaptersForBook(bookUrl, loaded) ?: chaptersForBook(bookUrl, displayed)
    }

    suspend fun <T> withPublication(block: suspend () -> T): T {
        publicationMutex.lock()
        try {
            return block()
        } finally {
            publicationMutex.unlock()
        }
    }
}
