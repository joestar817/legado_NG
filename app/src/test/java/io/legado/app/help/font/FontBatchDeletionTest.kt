package io.legado.app.help.font

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FontBatchDeletionTest {
    private fun entry(id: String) = FontFileEntry(
        id = id, name = "same.ttf", uri = id, size = 100,
        managed = null, canDelete = true, directoryUri = "fonts", sha256 = "a".repeat(64),
    )

    @Test fun partialFailureDoesNotStopLaterFilesOrReportFailureAsDeleted() = runBlocking {
        val entries = listOf(entry("one"), entry("two"), entry("three"))
        val calls = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val result = FontBatchDeletion.delete(entries, onDeleted = { deleted += it.id }) {
            calls += it.id
            if (it.id == "two") error("权限已撤销")
        }
        assertEquals(listOf("one", "two", "three"), calls)
        assertEquals(listOf("one", "three"), deleted)
        assertEquals(deleted, result.deleted.map { it.id })
        assertEquals(listOf("two"), result.failures.map { it.entry.id })
        assertEquals("权限已撤销", result.failures.single().reason)
    }

    @Test fun cancellationFinishesCurrentFileAndReportsItButDoesNotStartNextFile() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            FontBatchDeletion.delete(listOf(entry("one"), entry("two")), onDeleted = { deleted += it.id }) {
                calls += it.id
                entered.complete(Unit)
                finish.await()
            }
        }
        entered.await()
        job.cancel()
        finish.complete(Unit)
        job.join()
        assertEquals(listOf("one"), calls)
        assertEquals(listOf("one"), deleted)
        assertTrue(job.isCancelled)
    }

    @Test fun alreadyCancelledBatchDoesNotDeleteAnything() = runBlocking {
        val calls = mutableListOf<String>()
        val job = launch {
            currentCoroutineContext().cancel()
            FontBatchDeletion.delete(listOf(entry("one"))) { calls += it.id }
        }
        job.join()
        assertTrue(calls.isEmpty())
    }

    @Test fun explicitCancellationIsNotRecordedAsAnOrdinaryFailure() {
        val calls = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                FontBatchDeletion.delete(listOf(entry("one"), entry("two"))) {
                    calls += it.id
                    throw CancellationException("stop")
                }
            }
        }
        assertEquals(listOf("one"), calls)
    }

    @Test fun sameContentAtDifferentPathsIsDeletedSeparatelyAndDuplicateIdentityOnlyOnce() = runBlocking {
        val first = entry("fonts/one.ttf")
        val second = entry("other/one.ttf")
        val calls = mutableListOf<String>()
        val result = FontBatchDeletion.delete(listOf(first, second, first)) { calls += it.id }
        assertEquals(listOf(first.id, second.id), calls)
        assertEquals(listOf(first, second), result.deleted)
        assertTrue(result.failures.isEmpty())
    }

    @Test fun callbackFailureIsNotMisreportedAsAFileDeletionFailure() {
        val calls = mutableListOf<String>()
        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                FontBatchDeletion.delete(
                    listOf(entry("one"), entry("two")),
                    onDeleted = { throw IllegalArgumentException("callback failed") },
                    deleteFile = { calls += it.id },
                )
            }
        }
        assertEquals("callback failed", failure.message)
        assertEquals(listOf("one"), calls)
    }
}
