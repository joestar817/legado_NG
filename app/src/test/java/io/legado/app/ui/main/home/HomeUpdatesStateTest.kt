package io.legado.app.ui.main.home

import io.legado.app.data.entities.Book
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUpdatesStateTest {
    @Test fun `empty and exact six-book batches expose only real pages`() {
        assertEquals(0, homeUpdatesPageCount(0))
        assertEquals(1, homeUpdatesPageCount(6))
        assertEquals(2, homeUpdatesPageCount(12))
        assertTrue(homeUpdatesPageItems(emptyList<String>(), 0).isEmpty())
        assertEquals((1..6).toList(), homeUpdatesPageItems((1..6).toList(), 0))
        assertTrue(homeUpdatesPageItems((1..6).toList(), 1).isEmpty())
    }

    @Test fun `seven nine and thirteen books leave the last page partially empty`() {
        listOf(7 to listOf(6, 1), 9 to listOf(6, 3), 13 to listOf(6, 6, 1)).forEach { (count, sizes) ->
            val books = (1..count).map { "book-$it" }
            val pages = (0 until homeUpdatesPageCount(count)).map { homeUpdatesPageItems(books, it) }
            assertEquals(sizes, pages.map { it.size })
            assertEquals(books, pages.flatten())
            assertEquals(count, pages.flatten().toSet().size)
        }
    }

    @Test fun `a deleted last page clamps without jumping a still-valid page`() {
        assertEquals(2, homeUpdatesClampedPage(2, 13))
        assertEquals(1, homeUpdatesClampedPage(2, 12))
        assertEquals(1, homeUpdatesClampedPage(1, 7))
        assertEquals(0, homeUpdatesClampedPage(1, 6))
        assertEquals(0, homeUpdatesClampedPage(2, 0))
        assertEquals(0, homeUpdatesClampedPage(-1, 9))
    }

    @Test fun `invalid pages never repeat the first page or invent placeholder books`() {
        val books = (1..9).toList()
        assertTrue(homeUpdatesPageItems(books, -1).isEmpty())
        assertTrue(homeUpdatesPageItems(books, 2).isEmpty())
        assertTrue(homeUpdatesPageItems(books, Int.MAX_VALUE).isEmpty())
        assertEquals(listOf(7, 8, 9), homeUpdatesPageItems(books, 1))
    }

    @Test fun `page slices preserve object identity for read callbacks`() {
        val books = List(9) { Any() }
        val firstPage = homeUpdatesPageItems(books, 0)
        val secondPage = homeUpdatesPageItems(books, 1)
        assertTrue(firstPage.indices.all { firstPage[it] === books[it] })
        assertTrue(secondPage.indices.all { secondPage[it] === books[it + HOME_UPDATES_PAGE_SIZE] })
    }

    @Test fun `page count avoids overflow for large input counts`() {
        assertEquals(357_913_942, homeUpdatesPageCount(Int.MAX_VALUE))
        assertEquals(0, homeUpdatesPageCount(-1))
    }

    @Test fun `same URL display changes are emitted despite Book equality`() {
        val book = Book(bookUrl = "book", origin = "source", name = "原书名", author = "原作者", coverUrl = "cover")
        val original = HomeUpdateBook(book, 2, 100L)
        listOf(book.copy(name = "新书名"), book.copy(author = "新作者"),
            book.copy(coverUrl = "new-cover"), book.copy(customCoverUrl = "custom-cover"),
            book.copy(origin = "new-source")).forEach { changedBook ->
            assertEquals(book, changedBook)
            val changed = HomeUpdateBook(changedBook, original.updatedChapterCount, original.foundAt)
            assertFalse(original == changed)
            val flow = MutableStateFlow(HomeUpdatesState(books = listOf(original), loaded = true))
            flow.value = flow.value.copy(books = listOf(changed))
            assertSame(changed, flow.value.books.single())
        }
    }

    @Test fun `fresh Book objects with unchanged displayed metadata keep equal snapshots`() {
        val book = Book(bookUrl = "book", origin = "source", name = "书名", author = "作者",
            coverUrl = "source-cover", customCoverUrl = "chosen-cover")
        val first = HomeUpdateBook(book, 2, 100L)
        val second = HomeUpdateBook(book.copy(coverUrl = "unused-cover", durChapterIndex = 9), 2, 100L)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals("chosen-cover", second.displayCover)
    }

    @Test fun `display metadata remains immutable after the referenced Book changes`() {
        val book = Book(bookUrl = "book", origin = "source", name = "原书名", author = "原作者", coverUrl = "cover")
        val first = HomeUpdateBook(book, 2, 100L)
        book.name = "新书名"
        book.author = "新作者"
        book.customCoverUrl = "custom-cover"
        book.origin = "new-source"
        val second = HomeUpdateBook(book, 2, 100L)
        assertEquals("原书名", first.name)
        assertEquals("原作者", first.author)
        assertEquals("cover", first.displayCover)
        assertEquals("source", first.origin)
        assertFalse(first == second)
    }

    @Test fun `changed and newly known daily chapter counts are emitted for the same event`() {
        val book = Book(bookUrl = "book", origin = "source", name = "书名")
        val original = HomeUpdateBook(book, 2, 100L)
        listOf(3, null).forEach { count ->
            val changed = HomeUpdateBook(book, count, original.foundAt)
            assertFalse(original == changed)
            val flow = MutableStateFlow(HomeUpdatesState(books = listOf(original), loaded = true))
            flow.value = flow.value.copy(books = listOf(changed))
            assertSame(changed, flow.value.books.single())
            flow.value = flow.value.copy(books = listOf(original))
            assertSame(original, flow.value.books.single())
        }
    }
}
