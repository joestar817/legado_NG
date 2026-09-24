package io.legado.app.ui.book.import.local

import io.legado.app.model.localBook.BookImportPhase
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.ZipException

class BookImportStateTest {
    private fun batch() = BookImportBatch("samples", listOf(
        BookImportItem("a/book.epub", "book.epub"),
        BookImportItem("b/book.epub", "book.epub"),
        BookImportItem("c/book.epub", "book.epub"),
    ), 100)

    @Test
    fun sameNameFilesHaveIndependentStateAndWaitingIsNotTimed() {
        val state = batch().progress("b/book.epub", BookImportPhase.PARSING, 200)
        assertEquals(BookImportPhase.WAITING, state.items[0].phase)
        assertEquals(0L, state.items[0].elapsed(900))
        assertEquals(700L, state.items[1].elapsed(900))
    }

    @Test
    fun elapsedTimeCoversAllPhasesAndFreezesAtCompletion() {
        val state = batch().progress("a/book.epub", BookImportPhase.EXTRACTING, 200)
            .progress("a/book.epub", BookImportPhase.COPYING, 300, 50, 100)
            .progress("a/book.epub", BookImportPhase.PARSING, 400)
            .progress("a/book.epub", BookImportPhase.SUCCESS, 500)
        assertEquals(300L, state.items[0].elapsed(5000))
    }

    @Test
    fun stoppingPreservesSuccessfulBooksAndFailures() {
        val failure = ImportFailureInfo.from(ZipException("bad directory"))
        val state = batch().progress("a/book.epub", BookImportPhase.SUCCESS, 200)
            .progress("b/book.epub", BookImportPhase.FAILED, 300, error = failure)
            .finish(400, true, failure)
        assertEquals(1, state.successes)
        assertEquals(1, state.failures)
        assertEquals(1, state.stopped)
        assertFalse(state.running)
        assertNull(state.items[2].startedAt)
    }

    @Test
    fun retryOnlyResetsFailedAndStoppedEntriesAndKeepsCumulativeTime() {
        val state = batch().progress("a/book.epub", BookImportPhase.SUCCESS, 200)
            .finish(400, true, ImportFailureInfo("stopped", "stopped"))
        val retry = state.retry(1000)
        assertEquals(state.items[0], retry.items[0])
        assertEquals(BookImportPhase.WAITING, retry.items[1].phase)
        assertNull(retry.items[1].startedAt)
        assertEquals(500L, retry.elapsed(1200))
        assertTrue(retry.running)
    }

    @Test
    fun unexpectedArchiveFailureMarksOnlyUnfinishedItems() {
        val error = ImportFailureInfo("权限不足", "permission denied")
        val state = batch().progress("a/book.epub", BookImportPhase.SUCCESS, 200)
            .finish(400, false, error)
        assertEquals(1, state.successes)
        assertEquals(2, state.failures)
        assertEquals(error, state.items[2].error)
    }

    @Test
    fun lateProgressCannotOverwriteTerminalResult() {
        val state = batch().progress("a/book.epub", BookImportPhase.SUCCESS, 200)
        assertEquals(state, state.progress("a/book.epub", BookImportPhase.COPYING, 300))
    }

    @Test
    fun failuresHaveDirectSummaryAndFullCopyableReason() {
        val failure = ImportFailureInfo.from(ZipException("文件损坏或不完整：无法读取 EPUB 压缩目录"))
        assertEquals("文件损坏或不完整", failure.summary)
        assertTrue(failure.detail.contains("EPUB 压缩目录"))
        assertEquals("EPUB 缺少书籍信息", ImportFailureInfo.from(
            IllegalArgumentException("不是有效的EPUB文件(缺少OPF文档)")).summary)
        assertEquals("没有文件读写权限", ImportFailureInfo.from(SecurityException("denied")).summary)
    }
}
