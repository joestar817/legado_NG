package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReadBookRequestIsolationTest {
    @Suppress("UNCHECKED_CAST")
    private val loading get() = ReadBook::class.java.getDeclaredField("loadingChapters").run {
        isAccessible = true
        get(ReadBook) as MutableList<Int>
    }

    private val epoch get() = ReadBook::class.java.getDeclaredField("loadEpoch").run {
        isAccessible = true
        get(ReadBook) as ReadBookLoadEpoch
    }

    @After
    fun clearReader() {
        loading.clear()
        ReadBook.downloadedChapters.clear()
        ReadBook.downloadFailChapters.clear()
        ReadBook.book = null
        ReadBook.durChapterIndex = 0
        ReadBook.durChapterPos = 0
    }

    @Test
    fun lateContentAndCancellationCannotRemoveNewBooksLoadingMarker() {
        val old = Book(bookUrl = "old")
        ReadBook.book = old
        val generation = ReadBook.captureLoadGeneration(old.bookUrl)!!
        ReadBook.book = Book(bookUrl = "new")
        loading.add(2)
        for (canceled in listOf(false, true)) {
            ReadBook.contentLoadFinish(old, BookChapter(bookUrl = "old", index = 2), "old text",
                resetPageOffset = false, canceled = canceled, generation = generation)
            assertEquals(listOf(2), loading)
        }
    }

    @Test
    fun reopeningSameUrlRejectsOldContentAndDownloadBookkeeping() {
        val book = Book(bookUrl = "same")
        ReadBook.book = book
        val oldGeneration = ReadBook.captureLoadGeneration(book.bookUrl)!!
        epoch.invalidate()
        ReadBook.book = book.copy()
        loading.add(2)
        ReadBook.contentLoadFinish(book, BookChapter(bookUrl = book.bookUrl, index = 2), "old",
            resetPageOffset = false, generation = oldGeneration)
        ReadBook.recordDownload(book.bookUrl, oldGeneration, 2, false)
        ReadBook.recordDownload(book.bookUrl, oldGeneration, 2, true)
        assertEquals(listOf(2), loading)
        assertTrue(ReadBook.downloadedChapters.isEmpty())
        assertTrue(ReadBook.downloadFailChapters.isEmpty())
        val current = ReadBook.captureLoadGeneration(book.bookUrl)!!
        ReadBook.recordDownload(book.bookUrl, current, 2, false)
        assertEquals(setOf(2), ReadBook.downloadedChapters)
    }

    @Test
    fun staleAwaitCompletionPropagatesCancellationWithoutTouchingCurrentLoading() = runBlocking {
        val old = Book(bookUrl = "old")
        ReadBook.book = old
        val generation = ReadBook.captureLoadGeneration(old.bookUrl)!!
        ReadBook.book = Book(bookUrl = "new")
        loading.add(2)
        try {
            ReadBook.contentLoadFinishAwait(old, BookChapter(bookUrl = old.bookUrl, index = 2), "old",
                resetPageOffset = false, generation = generation)
            fail("stale await must be canceled")
        } catch (_: CancellationException) {
            assertEquals(listOf(2), loading)
        }
    }

    @Test
    fun delayedFinalSaveKeepsOldBookPositionAndSource() {
        val executor = Executors.newSingleThreadExecutor()
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            executor.submit { blocked.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(blocked.await(5, TimeUnit.SECONDS))
            val old = Book(bookUrl = "old")
            val source = BookSource(bookSourceUrl = "old-source")
            val saved = ReadBookProgressSnapshot(old, source, 4, 87, 123L)
            val pending = executor.submit<Boolean> { saved.apply() }
            ReadBook.book = Book(bookUrl = "new", durChapterIndex = 20, durChapterPos = 500)
            ReadBook.durChapterIndex = 20
            ReadBook.durChapterPos = 500
            release.countDown()
            assertTrue(pending.get(5, TimeUnit.SECONDS))
            assertEquals(4, old.durChapterIndex)
            assertEquals(87, old.durChapterPos)
            assertEquals(123L, old.durChapterTime)
            assertSame(source, saved.source)
            assertEquals(20, ReadBook.book!!.durChapterIndex)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
