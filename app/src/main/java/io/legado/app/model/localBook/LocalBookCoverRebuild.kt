package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

internal fun File.hasBookCover(): Boolean = isFile && length() > 0

internal fun canExtractOriginalCover(book: Book): Boolean =
    LocalBook.canExtractCover(book) && book.customCoverUrl.isNullOrBlank()

internal fun needsCoverRebuild(book: Book, cover: File): Boolean =
    canExtractOriginalCover(book) && !cover.hasBookCover()

/** 已有缓存无需打开书籍；缺图时始终显式提取，不依赖解析器构造副作用。 */
internal inline fun extractBookCover(cover: File, force: Boolean, extract: () -> Boolean): Boolean =
    if (!force && cover.hasBookCover()) true else extract()

internal data class CoverRebuildResult(val success: Int, val failed: Int, val skipped: Int)

internal suspend fun <T> rebuildBookCovers(
    books: Iterable<T>,
    shouldExtract: (T) -> Boolean,
    extract: (T) -> Boolean,
    onFailure: (T, Exception?) -> Unit,
    onSuccess: (T) -> Unit = {},
): CoverRebuildResult {
    var success = 0
    var failed = 0
    var skipped = 0
    for (book in books) {
        currentCoroutineContext().ensureActive()
        val written = try {
            if (!shouldExtract(book)) {
                skipped++
                continue
            }
            extract(book)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            currentCoroutineContext().ensureActive()
            failed++
            onFailure(book, error)
            continue
        }
        currentCoroutineContext().ensureActive()
        if (written) {
            success++
            onSuccess(book)
        } else {
            failed++
            onFailure(book, null)
        }
    }
    return CoverRebuildResult(success, failed, skipped)
}
