package io.legado.app.help.book

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.model.AudioPlay
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val chapterOrderMutex = Mutex()
private val chapterPublicationLocks = ConcurrentHashMap<String, Mutex>()

/** Network requests may finish with an old index; resolve their URL while holding this lock. */
internal suspend fun <T> withChapterCachePublication(bookUrl: String, block: () -> T): T =
    chapterPublicationLocks.getOrPut(bookUrl) { Mutex() }.withLock { block() }

/** Main-thread snapshot, using the same reader routing as the book entry point. */
private fun currentChapterPosition(book: Book): Pair<Int, Int> {
    val usesMangaReader = !book.isLocal && book.isImage && AppConfig.showMangaUi
    return when {
        book.isAudio && AudioPlay.book?.bookUrl == book.bookUrl ->
            AudioPlay.durChapterIndex to AudioPlay.durChapterPos
        usesMangaReader && ReadManga.book?.bookUrl == book.bookUrl ->
            ReadManga.durChapterIndex to ReadManga.durChapterPos
        !book.isAudio && !usesMangaReader && ReadBook.book?.bookUrl == book.bookUrl ->
            ReadBook.durChapterIndex to ReadBook.durChapterPos
        else -> book.durChapterIndex to book.durChapterPos
    }
}

/** Keep the chapter identity, cache bytes and reading position together when reversing the TOC. */
internal suspend fun reverseBookChapterOrder(bookUrl: String): Book = chapterOrderMutex.withLock {
    withContext(IO) {
        var resumeBook = appDb.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException("书籍不存在")
        CacheBook.withIdleBook(bookUrl) { Unit }
        try {
            // Never hold the publication lock while joining a previous content writer.
            withContext(NonCancellable + Main) {
                val position = currentChapterPosition(resumeBook)
                resumeBook = resumeBook.copy(
                    durChapterIndex = position.first,
                    durChapterPos = position.second,
                )
                ReadBook.pauseForTocReorder(bookUrl)
                ReadManga.pauseForTocReorder(bookUrl)
                AudioPlay.pauseForTocReorder(bookUrl)
            }
            val latest = appDb.bookDao.getBook(bookUrl)
                ?: throw NoStackTraceException("书籍不存在")
            val livePosition = withContext(Main) { currentChapterPosition(latest) }
            resumeBook = latest.copy(
                durChapterIndex = livePosition.first,
                durChapterPos = livePosition.second,
            )
            withChapterCachePublication(bookUrl) {
                CacheBook.withIdleBook(bookUrl) {
                    val oldChapters = appDb.bookChapterDao.getChapterList(bookUrl)
                    if (oldChapters.isEmpty()) throw NoStackTraceException("目录为空")
                    val current = oldChapters.firstOrNull { it.index == resumeBook.durChapterIndex }
                        ?: throw NoStackTraceException("当前章节不存在，请重新选择章节")
                    val newChapters = oldChapters.asReversed().mapIndexed { index, chapter ->
                        chapter.copy(index = index)
                    }
                    val newIndex = newChapters.first { it.url == current.url }.index
                    var updated = resumeBook.copy(
                        readConfig = resumeBook.readConfig?.copy(),
                        durChapterIndex = newIndex,
                    ).apply { setReverseToc(!getReverseToc()) }
                    withReorderedChapterCache(updated, oldChapters, newChapters) {
                        appDb.runInTransaction {
                            val storedBook = appDb.bookDao.getBook(bookUrl)
                                ?: throw NoStackTraceException("书籍不存在")
                            val storedChapters = appDb.bookChapterDao.getChapterList(bookUrl)
                            if (storedBook.origin != resumeBook.origin ||
                                storedBook.tocUrl != resumeBook.tocUrl ||
                                storedBook.type != resumeBook.type ||
                                storedBook.getFolderName() != resumeBook.getFolderName() ||
                                storedBook.getReverseToc() != resumeBook.getReverseToc() ||
                                storedChapters.size != oldChapters.size ||
                                storedChapters.zip(oldChapters).any { (stored, old) ->
                                    stored.url != old.url || stored.index != old.index || stored.title != old.title
                                }
                            ) {
                                throw NoStackTraceException("目录已更新，请重新调整顺序")
                            }
                            // Keep metadata refreshed while files were staged; only order/progress change.
                            updated = storedBook.copy(
                                readConfig = storedBook.readConfig?.copy(),
                                durChapterIndex = newIndex,
                                durChapterPos = resumeBook.durChapterPos,
                                durVolumeIndex = resumeBook.durVolumeIndex,
                                chapterInVolumeIndex = resumeBook.chapterInVolumeIndex,
                            ).apply {
                                setReverseToc(!getReverseToc())
                                durChapterTitle = current.getDisplayTitle(
                                    ContentProcessor.get(this).getTitleReplaceRules(),
                                    getUseReplaceRule(),
                                    replaceBook = toReplaceBook(),
                                )
                            }
                            appDb.bookChapterDao.insert(*storedChapters.asReversed()
                                .mapIndexed { index, chapter -> chapter.copy(index = index) }.toTypedArray())
                            appDb.bookDao.update(updated)
                        }
                    }
                    CacheBook.cacheBookMap[bookUrl]?.book = updated
                    resumeBook = updated
                    updated
                }
            }
        } finally {
            withContext(NonCancellable + Main) {
                ReadBook.resumeAfterTocReorder(resumeBook)
                ReadManga.resumeAfterTocReorder(resumeBook)
                AudioPlay.resumeAfterTocReorder(resumeBook)
            }
        }
    }
}
