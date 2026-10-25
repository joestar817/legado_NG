package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.utils.NetworkUtils
import java.io.File

/** 仅统计实际缓存；本地原文件和虚拟分卷不算可清理的章节缓存。调用方在 IO 线程加载。 */
internal data class BookCacheStatus(
    val chapterUrls: Set<String>,
    val total: Int,
    val hasCache: Boolean,
) {
    val cached: Int get() = chapterUrls.size
}

internal fun loadBookCacheStatus(
    book: Book,
    chapters: List<BookChapter>,
    checkActive: () -> Unit = {},
): BookCacheStatus {
    checkActive()
    val files = File(BookHelp.cachePath, book.getFolderName()).listFiles().orEmpty()
    val names = if (book.isAudio) {
        appDb.bookSourceDao.getBookSource(book.origin)?.let {
            AudioDownloadCache.getCachedChapterFileNames(it, book)
        }.orEmpty()
    } else {
        files.asSequence().filter(File::isFile).map(File::getName).toSet()
    }
    val contentChapters = chapters.filterNot(BookChapter::isVolume)
    return BookCacheStatus(
        chapterUrls = contentChapters.asSequence()
            .filter { chapter ->
                checkActive()
                chapter.getFileName() in names &&
                    (book.isLocal || book.isAudio || isCompleteCachedChapter(book, chapter))
            }
            .map(BookChapter::url)
            .toSet(),
        total = if (chapters.isEmpty()) book.totalChapterNum.coerceAtLeast(0) else contentChapters.size,
        // 包括图片、音频清单及残留缓存；不能因为正文计数为零就禁止清理。
        hasCache = names.isNotEmpty() || files.any { it.isFile || !it.list().isNullOrEmpty() },
    )
}

private fun isCompleteCachedChapter(book: Book, chapter: BookChapter): Boolean {
    val file = File(File(BookHelp.cachePath, book.getFolderName()), chapter.getFileName())
    if (!file.isFile || file.length() == 0L) return false
    if (!book.isImage) return true
    // hasImageContent 会删除损坏图片；管理统计只读核对图片，沿用缓存页的完整性口径。
    val matcher = AppPattern.imgPattern.matcher(file.readText())
    var imageCount = 0
    while (matcher.find()) {
        imageCount++
        val src = NetworkUtils.getAbsoluteURL(chapter.url, matcher.group(1)!!)
        if (getCachedBookImage(book, src) == null) return false
    }
    return imageCount > 0
}
