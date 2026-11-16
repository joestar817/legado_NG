package io.legado.app.ui.book.changecover

import io.legado.app.data.entities.BookSourcePart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeCoverSearchProgressTest {
    @Test
    fun successfulFailedAndTimedOutSourcesAllComplete() = runBlocking {
        val sources = listOf("success", "failure", "timeout").map { BookSourcePart(bookSourceUrl = it) }
        val completed = mutableListOf<String>()

        searchCoverSources(
            sources = sources,
            concurrency = 3,
            search = { source ->
                when (source.bookSourceUrl) {
                    "failure" -> error("source failed")
                    "timeout" -> withTimeout(1) { delay(50) }
                }
            },
            onCompleted = { completed += it.bookSourceUrl },
        )

        assertEquals(listOf("failure", "success", "timeout"), completed.sorted())
    }

    @Test
    fun cancellationDoesNotReportAnUnfinishedSource() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val completed = mutableListOf<String>()
        val task = launch {
            searchCoverSources(
                sources = listOf(BookSourcePart(bookSourceUrl = "slow")),
                concurrency = 1,
                search = {
                    started.complete(Unit)
                    awaitCancellation()
                },
                onCompleted = { completed += it.bookSourceUrl },
            )
        }

        started.await()
        task.cancelAndJoin()
        assertEquals(emptyList<String>(), completed)
    }
}
