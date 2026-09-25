package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], application = android.app.Application::class)
class CacheBookRequestSerializationTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(model: CacheBook.CacheBookModel, name: String): T =
        model.javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(model) as T
        }

    @Test
    fun sameGenerationKeepsSingleRequestAndOriginalCompletion() {
        val book = Book(bookUrl = "test")
        val model = CacheBook.CacheBookModel(BookSource(), book)
        field<MutableSet<Int>>(model, "onDownloadSet").add(2)
        field<MutableMap<Int, Long?>>(model, "downloadGenerations")[2] = 10L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            model.download(scope, BookChapter(bookUrl = book.bookUrl, index = 2), null, generation = 10L)
            assertEquals(1, model.onDownloadCount)
            assertTrue(field<Map<Int, *>>(model, "downloadCompletions").isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun newerGenerationWaitsForReleaseAndOldCancellationWakesIt() {
        val book = Book(bookUrl = "test")
        val model = CacheBook.CacheBookModel(BookSource(), book)
        field<MutableSet<Int>>(model, "onDownloadSet").add(2)
        field<MutableMap<Int, Long?>>(model, "downloadGenerations")[2] = 10L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            // No active reader: after release the waiter must stop before issuing IO.
            ReadBook.book = null
            model.download(scope, BookChapter(bookUrl = book.bookUrl, index = 2), null, generation = 11L)
            val signal = field<Map<Int, CompletableDeferred<Unit>>>(model, "downloadCompletions")[2]!!
            assertFalse(signal.isCompleted)
            assertEquals(1, model.onDownloadCount)
            model.javaClass.getDeclaredMethod("onCancel", Int::class.javaPrimitiveType).run {
                isAccessible = true
                invoke(model, 2)
            }
            assertTrue(signal.isCompleted)
            assertEquals(0, model.onDownloadCount)
            assertTrue(field<Map<Int, *>>(model, "downloadCompletions").isEmpty())
            assertTrue(field<Map<Int, *>>(model, "downloadGenerations").isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
