package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.IOException

/** 漫画的旧正文没有图片时，需要重新获取正文，而不是直接跳过或只补图。 */
internal fun hasUsableChapterCacheContent(book: Book, content: String?): Boolean {
    if (content.isNullOrEmpty()) return false
    return !book.isImage || AppPattern.imgPattern.matcher(content).find()
}

/** 只读检查，与下载和统计使用相同的绝对图片地址及有效性判定。 */
internal fun hasCompleteChapterImages(
    book: Book,
    chapter: BookChapter,
    content: String?,
): Boolean {
    if (!hasUsableChapterCacheContent(book, content)) return false
    val matcher = AppPattern.imgPattern.matcher(content!!)
    while (matcher.find()) {
        val src = NetworkUtils.getAbsoluteURL(chapter.url, matcher.group(1)!!)
        if (getCachedBookImage(book, src) == null) return false
    }
    return true
}

/** 仅供后台缓存队列使用；普通阅读保存正文的时序及图片容错保持不变。 */
internal suspend fun completeChapterCache(
    bookSource: BookSource,
    book: Book,
    chapter: BookChapter,
    content: String,
) {
    currentCoroutineContext().ensureActive()
    if (!hasUsableChapterCacheContent(book, content)) {
        throw IOException(if (book.isImage) "漫画正文没有可缓存的图片" else "章节正文为空")
    }
    // 一章内顺序补图，沿用缓存队列原有的图片并发度。
    BookHelp.flowImages(chapter, content).collect { src ->
        currentCoroutineContext().ensureActive()
        BookHelp.saveImageForCache(bookSource, book, src, chapter)
    }
    currentCoroutineContext().ensureActive()
    val file = File(File(BookHelp.cachePath, book.getFolderName()), chapter.getFileName())
    if (!file.isFile || file.readText() != content) {
        throw IOException("章节正文未完整保存")
    }
    if (!hasCompleteChapterImages(book, chapter, content)) {
        throw IOException("章节图片缓存不完整")
    }
    currentCoroutineContext().ensureActive()
}
