package io.legado.app.model

import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.utils.postEvent

/** 统一收口正文、图片和有声书持久音频缓存的清理时序。 */
object BookCacheManager {

    fun clear(book: Book) {
        CacheBook.cancel(book.bookUrl)
        AudioPlay.onBookCacheCleared(book.bookUrl)
        BookHelp.clearCache(book)
        postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
    }

    fun clearChapter(book: Book, chapter: BookChapter) {
        CacheBook.cancel(book.bookUrl)
        AudioPlay.onBookCacheCleared(book.bookUrl)
        AudioDownloadCache.clearChapter(book, chapter)
        BookHelp.delContent(book, chapter)
        postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
    }

    fun clearAll() {
        CacheBook.close()
        AudioPlay.book?.bookUrl?.let(AudioPlay::onBookCacheCleared)
        BookHelp.clearCache()
        postEvent(EventBus.UP_DOWNLOAD, "")
    }
}
