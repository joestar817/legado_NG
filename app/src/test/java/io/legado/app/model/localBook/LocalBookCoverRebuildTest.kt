package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class LocalBookCoverRebuildTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun publishesEachSuccessBeforeStartingTheNextBook() = runBlocking {
        val events = mutableListOf<String>()
        rebuildBookCovers(listOf(1, 2, 3), { true }, {
            events += "extract:$it"
            it != 2
        }, { book, _ -> events += "failed:$book" }, { events += "ready:$it" })
        assertEquals(listOf("extract:1", "ready:1", "extract:2", "failed:2", "extract:3", "ready:3"), events)
    }

    @Test fun ordinaryFailureDoesNotStopFollowingBooks() = runBlocking {
        val visited = mutableListOf<String>()
        val errors = mutableListOf<Pair<String, Exception?>>()
        val result = rebuildBookCovers(listOf("first", "broken", "last"), { true }, { book ->
            visited += book
            if (book == "broken") throw IOException("缺少 ZIP 目录")
            true
        }, { book, error -> errors += book to error })
        assertEquals(listOf("first", "broken", "last"), visited)
        assertEquals(CoverRebuildResult(2, 1, 0), result)
        assertEquals("broken", errors.single().first)
        assertEquals("缺少 ZIP 目录", errors.single().second!!.message)
    }

    @Test fun falseResultIsCountedOnceAndLaterEntriesContinue() = runBlocking {
        val errors = mutableListOf<Int>()
        val result = rebuildBookCovers(listOf(1, 2, 3), { it != 1 }, { it == 3 }, { book, error ->
            assertNull(error)
            errors += book
        })
        assertEquals(CoverRebuildResult(1, 1, 1), result)
        assertEquals(listOf(2), errors)
    }

    @Test fun eligibilityFailureIsIsolatedToo() = runBlocking {
        val result = rebuildBookCovers(listOf(1, 2), {
            if (it == 1) throw SecurityException("文件不可访问")
            true
        }, { true }, { _, error -> assertTrue(error is SecurityException) })
        assertEquals(CoverRebuildResult(1, 1, 0), result)
    }

    @Test fun emptyBatchDoesNotExtractAnything() = runBlocking {
        val result = rebuildBookCovers(emptyList<Int>(), { fail("Unexpected predicate"); true },
            { fail("Unexpected extraction"); true }, { _, _ -> fail("Unexpected failure") })
        assertEquals(CoverRebuildResult(0, 0, 0), result)
    }

    @Test fun cancellationFromExtractorEscapesWithoutFailureReport() {
        val visited = mutableListOf<Int>()
        val error = runCatching {
            runBlocking {
                rebuildBookCovers(listOf(1, 2), { true }, {
                    visited += it
                    throw CancellationException("stop")
                }, { _, _ -> fail("Cancellation is not a book failure") })
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(listOf(1), visited)
    }

    @Test fun cancelledJobStopsBeforeReportingExtractorFalse() = runBlocking {
        val visited = mutableListOf<Int>()
        val child = launch {
            val context = currentCoroutineContext()
            rebuildBookCovers(listOf(1, 2), { true }, {
                visited += it
                context.cancel()
                false
            }, { _, _ -> fail("Cancelled extraction must not become failure") })
        }
        child.join()
        assertTrue(child.isCancelled)
        assertEquals(listOf(1), visited)
    }

    @Test fun fatalErrorIsNotConvertedToOrdinaryBookFailure() {
        val fatal = AssertionError("fatal")
        val error = runCatching {
            runBlocking {
                rebuildBookCovers(listOf(1, 2), { true }, { throw fatal },
                    { _, _ -> fail("Fatal error must escape") })
            }
        }.exceptionOrNull()
        assertSame(fatal, error)
    }

    @Test fun failureCallbackExceptionIsNotCountedAgain() {
        val callbackError = IllegalStateException("callback")
        var callbacks = 0
        val error = runCatching {
            runBlocking {
                rebuildBookCovers(listOf(1), { true }, { false }, { _, _ ->
                    callbacks++
                    throw callbackError
                })
            }
        }.exceptionOrNull()
        assertSame(callbackError, error)
        assertEquals(1, callbacks)
    }

    @Test fun existingCoverSkipsOpeningTheBook() {
        val file = temporary.newFile("existing.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        assertTrue(extractBookCover(file, false) { fail("Existing cover should not parse a book"); false })
    }

    @Test fun cachedExtractorStillRunsAfterItsCoverDisappears() {
        val file = File(temporary.root, "cached.jpg")
        var extractions = 0
        val sameExtractor = { extractions++; file.writeBytes(byteArrayOf(1, 2)); true }
        assertTrue(extractBookCover(file, false, sameExtractor))
        assertTrue(file.delete())
        assertTrue(extractBookCover(file, false, sameExtractor))
        assertEquals(2, extractions)
        assertTrue(file.hasBookCover())
    }

    @Test fun emptyCoverIsRebuiltAndForcedCoverCanBeReplaced() {
        val file = temporary.newFile("empty.jpg")
        assertFalse(file.hasBookCover())
        assertTrue(extractBookCover(file, false) { file.writeText("first"); true })
        assertTrue(extractBookCover(file, true) { file.writeText("second"); true })
        assertEquals("second", file.readText())
    }

    @Test fun restoreSkipsCustomExistingAndUnsupportedCovers() {
        val missing = File(temporary.root, "missing.jpg")
        val existing = temporary.newFile("existing.jpg").apply { writeBytes(byteArrayOf(1)) }
        val epub = Book(originName = "sample.epub", type = 0)
        assertTrue(needsCoverRebuild(epub, missing))
        assertFalse(needsCoverRebuild(epub, existing))
        assertFalse(needsCoverRebuild(epub.copy(customCoverUrl = "https://example.test/custom.jpg"), missing))
        assertFalse(needsCoverRebuild(Book(originName = "sample.txt", type = 0), missing))
    }
}
