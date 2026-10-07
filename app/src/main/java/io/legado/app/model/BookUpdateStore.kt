package io.legado.app.model

import androidx.room.withTransaction
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookUpdate
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Values copied before a request can mutate its Book or suspend for network IO. */
internal data class BookUpdateSnapshot(
    val bookUrl: String,
    val origin: String,
    val totalChapterNum: Int,
    val isLocal: Boolean = false,
    val isNotShelf: Boolean = false,
)

private fun BookUpdateSnapshot.isExistingOnlineShelfBook(): Boolean =
    bookUrl.isNotBlank() && origin.isNotBlank() && !isLocal && !isNotShelf && totalChapterNum > 0

internal fun bookUpdateBaseline(
    input: BookUpdateSnapshot,
    persisted: BookUpdateSnapshot?,
    sourceUrl: String,
): BookUpdateSnapshot? = persisted?.takeIf {
    input.isExistingOnlineShelfBook() && it.isExistingOnlineShelfBook() &&
        input.bookUrl == it.bookUrl && input.origin == it.origin && input.origin == sourceUrl &&
        input.totalChapterNum == it.totalChapterNum
}

internal fun isBookUpdateDiscovery(
    baseline: BookUpdateSnapshot,
    current: BookUpdateSnapshot?,
    result: BookUpdateSnapshot,
): Boolean = current != null && baseline.isExistingOnlineShelfBook() &&
    current.isExistingOnlineShelfBook() && result.isExistingOnlineShelfBook() &&
    current.bookUrl == baseline.bookUrl && result.bookUrl == baseline.bookUrl &&
    current.origin == baseline.origin && result.origin == baseline.origin &&
    current.totalChapterNum >= baseline.totalChapterNum &&
    result.totalChapterNum > current.totalChapterNum

internal fun bookUpdateEpochDay(foundAt: Long, zoneId: ZoneId): Long =
    Instant.ofEpochMilli(foundAt).atZone(zoneId).toLocalDate().toEpochDay()

/** A caller may not have saved yesterday's discovered directory to Book yet. */
internal fun bookUpdateDiscoveryFloor(currentTotal: Int, recordedTotal: Int?): Int =
    maxOf(currentTotal, recordedTotal ?: 0)

private fun snapshotOf(book: Book, totalChapterNum: Int = book.totalChapterNum) = BookUpdateSnapshot(
    book.bookUrl, book.origin, totalChapterNum, book.isLocal, book.isNotShelf,
)

/**
 * Records successful directory discovery, not a claim that the caller has committed its cache.
 * Storage failures never change the existing fetch result, and no detached work outlives cancellation.
 */
internal object BookUpdateStore {
    suspend fun capture(book: Book, sourceUrl: String): BookUpdateSnapshot? {
        val input = snapshotOf(book)
        if (!input.isExistingOnlineShelfBook() || input.origin != sourceUrl) return null
        return try {
            withContext(IO) {
                currentCoroutineContext().ensureActive()
                val persisted = appDb.bookDao.getBook(input.bookUrl)?.let { snapshotOf(it) }
                bookUpdateBaseline(input, persisted, sourceUrl)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.put("读取今日书讯更新基线失败", error)
            null
        }
    }

    suspend fun record(baseline: BookUpdateSnapshot?, book: Book, chapterCount: Int) {
        baseline ?: return
        try {
            val result = snapshotOf(book, chapterCount)
            val latestChapterTitle = book.latestChapterTitle
            withContext(IO) {
                appDb.withTransaction {
                    currentCoroutineContext().ensureActive()
                    val current = appDb.bookDao.getBook(baseline.bookUrl)?.let { snapshotOf(it) }
                    if (current != null && isBookUpdateDiscovery(baseline, current, result)) {
                        // Assign the local day under the same transaction that orders discoveries.
                        val foundAt = System.currentTimeMillis()
                        val epochDay = bookUpdateEpochDay(foundAt, ZoneId.systemDefault())
                        val dao = appDb.bookUpdateDao
                        val previous = dao.get(epochDay, result.bookUrl, result.origin)
                        val recorded = previous ?: dao.getLatestBeforeDay(epochDay, result.bookUrl, result.origin)
                        val floor = bookUpdateDiscoveryFloor(current.totalChapterNum, recorded?.totalChapterNum)
                        if (chapterCount > floor) {
                            dao.recordIfNewer(BookUpdate(
                                epochDay = epochDay,
                                bookUrl = result.bookUrl,
                                origin = result.origin,
                                latestChapterTitle = latestChapterTitle,
                                foundAt = foundAt,
                                totalChapterNum = chapterCount,
                                baselineChapterNum = if (previous == null) floor else previous.baselineChapterNum,
                            ))
                        }
                    }
                    currentCoroutineContext().ensureActive()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.put("保存今日书讯失败", error)
        }
    }
}
