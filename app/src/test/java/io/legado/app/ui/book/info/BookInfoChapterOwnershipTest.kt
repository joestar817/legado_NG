package io.legado.app.ui.book.info

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.updateTypeFromSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BookInfoChapterOwnershipTest {
    @Test
    fun sourceTypeResetDoesNotChangeTheDisplayedMangaWhileTheTocIsLoading() {
        val manga = Book(bookUrl = "book-a", type = BookType.image)
        val oldChapters = listOf(BookChapter(bookUrl = manga.bookUrl, url = "first"))
        val displayed = BookInfoSnapshot(manga, oldChapters)

        val draft = displayed.book.copyForBookInfo()
        draft.updateTypeFromSource(BookType.text)
        assertEquals(BookType.text, draft.type)
        assertEquals(BookType.image, displayed.book.type)
        assertSame(oldChapters, displayed.chapters)

        draft.type = BookType.image
        val newChapters = oldChapters + BookChapter(bookUrl = manga.bookUrl, url = "second")
        val refreshed = BookInfoSnapshot(draft, newChapters)
        assertEquals(BookType.image, refreshed.book.type)
        assertSame(newChapters, refreshed.chapters)
        assertSame(oldChapters, displayed.chapters)
    }

    @Test
    fun ruleInputKeepsTransientDataAndDoesNotShareReadingSettings() {
        val book = Book(bookUrl = "book-a").apply {
            config.customBookType = BookType.image
            infoHtml = "detail"
            tocHtml = "catalog"
            downloadUrls = mutableListOf("first", "second")
        }

        val draft = book.copyForBookInfo()
        assertNotSame(book.readConfig, draft.readConfig)
        assertEquals(BookType.image, draft.readConfig?.customBookType)
        draft.config.customBookType = BookType.text
        assertEquals(BookType.image, book.readConfig?.customBookType)
        assertEquals("detail", draft.infoHtml)
        assertEquals("catalog", draft.tocHtml)
        assertEquals(book.downloadUrls, draft.downloadUrls)
        assertNotSame(book.downloadUrls, draft.downloadUrls)
    }

    @Test
    fun refreshKeepsEditsMadeAfterTheBookWasPublished() {
        val displayed = Book(bookUrl = "book-a", origin = "source", type = BookType.image)
        val snapshot = BookInfoSnapshot(displayed)
        val refreshed = displayed.copyForBookInfo().apply {
            name = "updated title"
            durChapterTitle = "title from the new TOC"
        }
        displayed.customCoverUrl = "new cover"
        displayed.canUpdate = false
        displayed.group = 2L
        displayed.durChapterPos = 30

        refreshed.keepBookInfoEdits(snapshot.initialBook, displayed)

        assertEquals("new cover", refreshed.customCoverUrl)
        assertEquals(false, refreshed.canUpdate)
        assertEquals(2L, refreshed.group)
        assertEquals(30, refreshed.durChapterPos)
        assertEquals("updated title", refreshed.name)
        assertEquals("title from the new TOC", refreshed.durChapterTitle)
        assertEquals(BookType.image, refreshed.type)
    }

    @Test
    fun userVariableEditKeepsOtherValuesProducedByTheSource() {
        val before = Book(bookUrl = "book-a", variable = """{"custom":"old value"}""")
        val edited = before.copy(variable = """{"custom":"new value"}""")
        val refreshed = before.copy(variable = """{"custom":"old value","source":"result"}""")

        refreshed.keepBookInfoEdits(before, edited)

        assertEquals("new value", refreshed.variableMap["custom"])
        assertEquals("result", refreshed.variableMap["source"])
    }

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
