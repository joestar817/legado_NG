package io.legado.app.ui.book.info

import io.legado.app.data.entities.BookChapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BookInfoChapterOwnershipTest {
    @Test
    fun chaptersAreAvailableOnlyForTheirOwningBook() {
        val chapters = listOf(BookChapter(bookUrl = "book-a"), BookChapter(bookUrl = "book-a"))
        assertSame(chapters, chaptersForBook("book-a", chapters))
        assertNull(chaptersForBook("book-b", chapters))
        assertNull(chaptersForBook(null, chapters))
    }

    @Test
    fun mixedListsCannotBeUsedForEitherBook() {
        val chapters = listOf(BookChapter(bookUrl = "book-a"), BookChapter(bookUrl = "book-b"))
        assertNull(chaptersForBook("book-a", chapters))
        assertNull(chaptersForBook("book-b", chapters))
    }

    @Test
    fun emptyListRemainsAValidEmptyResultForTheCurrentBook() {
        val chapters = emptyList<BookChapter>()
        assertSame(chapters, chaptersForBook("book-a", chapters))
        assertNull(chaptersForBook("book-a", null))
    }

    @Test
    fun completedLoadIsAvailableBeforeTheUiReceivesItsUpdate() {
        val loaded = BookInfoLoadedChapters()
        val chapters = listOf(BookChapter(bookUrl = "book-a"))
        loaded.record("book-a", chapters)

        assertSame(chapters, loaded.forBook("book-a", displayed = null))
        assertNull(loaded.forBook("book-b", displayed = null))
    }

    @Test
    fun previousBooksLoadCannotReplaceTheCurrentBooksDisplayedChapters() {
        val loaded = BookInfoLoadedChapters()
        loaded.record("book-a", listOf(BookChapter(bookUrl = "book-a")))
        val current = listOf(BookChapter(bookUrl = "book-b"))

        assertSame(current, loaded.forBook("book-b", displayed = current))
        loaded.clear()
        assertSame(current, loaded.forBook("book-b", displayed = current))
    }

    @Test
    fun emptyResultFromPreviousBookDoesNotHideCurrentBookChapters() {
        val loaded = BookInfoLoadedChapters()
        loaded.record("book-a", emptyList())
        val current = listOf(BookChapter(bookUrl = "book-b"))

        assertSame(current, loaded.forBook("book-b", displayed = current))
    }

    @Test
    fun completingTheLoadBeforeAddingPersistsEvenWhenTheUiHasNotUpdated() = runBlocking {
        val loaded = BookInfoLoadedChapters()
        val chapters = listOf(BookChapter(bookUrl = "book-a"))
        val recorded = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<BookChapter>()
        var inBookshelf = false

        val loading = launch(Dispatchers.Default) {
            loaded.withPublication {
                loaded.record("book-a", chapters)
                recorded.complete(Unit)
                release.await()
                if (inBookshelf) saved.addAll(chapters)
            }
        }
        recorded.await()
        val adding = launch(Dispatchers.Default) {
            loaded.withPublication {
                loaded.forBook("book-a", displayed = null)?.let(saved::addAll)
                inBookshelf = true
            }
        }
        release.complete(Unit)
        loading.join()
        adding.join()

        assertEquals(chapters, saved)
    }

    @Test
    fun completingTheLoadAfterAddingPersistsTheNewChapters() = runBlocking {
        val loaded = BookInfoLoadedChapters()
        val chapters = listOf(BookChapter(bookUrl = "book-a"))
        val saved = mutableListOf<BookChapter>()
        var inBookshelf = false

        loaded.withPublication {
            loaded.forBook("book-a", displayed = null)?.let(saved::addAll)
            inBookshelf = true
        }
        loaded.withPublication {
            loaded.record("book-a", chapters)
            if (inBookshelf) saved.addAll(chapters)
        }

        assertEquals(chapters, saved)
    }
}
