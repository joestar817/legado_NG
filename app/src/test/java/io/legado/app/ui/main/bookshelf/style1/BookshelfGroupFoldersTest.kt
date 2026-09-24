package io.legado.app.ui.main.bookshelf.style1

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfGroupFoldersTest {
    @Test
    fun ungroupedIncludesLocalAndOnlineButExcludesHiddenCustomGroups() {
        val ungrouped = BookGroup(groupId = BookGroup.IdNoGroup, bookSort = 3)
        val hidden = BookGroup(groupId = 1, show = false)
        val lastBit = BookGroup(groupId = Long.MIN_VALUE, show = false)
        val books = listOf(
            Book(bookUrl = "online", type = BookType.text),
            Book(bookUrl = "local", type = BookType.text or BookType.local),
            Book(bookUrl = "audio", type = BookType.audio),
            Book(bookUrl = "manga", type = BookType.image),
            Book(bookUrl = "hidden-group", group = 1),
            Book(bookUrl = "last-bit", group = Long.MIN_VALUE),
        )
        val folders = buildBookshelfGroupFolders(
            groups = listOf(ungrouped),
            books = books,
            allCustomGroupMask = listOf(ungrouped, hidden, lastBit).customGroupMask(),
        )

        assertEquals(
            listOf("online", "local", "audio", "manga"),
            folders.single().books.map { it.bookUrl },
        )
    }

    @Test
    fun novelAndMangaFiltersAlsoIncludeBooksInManualGroups() {
        val novel = BookGroup(groupId = BookGroup.IdNovel, bookSort = 3)
        val manga = BookGroup(groupId = BookGroup.IdManga, bookSort = 3)
        val books = listOf(
            Book(bookUrl = "online-novel", type = BookType.text),
            Book(bookUrl = "local-novel", type = BookType.text or BookType.local),
            Book(bookUrl = "grouped-novel", type = BookType.text, group = 1),
            Book(bookUrl = "manga", type = BookType.image),
            Book(bookUrl = "grouped-manga", type = BookType.image, group = 1),
            Book(bookUrl = "audio", type = BookType.audio),
        )

        val folders = buildBookshelfGroupFolders(listOf(novel, manga), books)
        assertEquals(
            listOf("online-novel", "local-novel", "grouped-novel"),
            folders.single { it.group.groupId == BookGroup.IdNovel }.books.map { it.bookUrl },
        )
        assertEquals(
            listOf("manga", "grouped-manga"),
            folders.single { it.group.groupId == BookGroup.IdManga }.books.map { it.bookUrl },
        )
    }

    @Test
    fun ungroupedUsesManagedOrderAndIsNotInjectedWhenHidden() {
        val custom = BookGroup(groupId = 1, order = 1, bookSort = 3)
        val ungrouped = BookGroup(groupId = BookGroup.IdNoGroup, order = 2, bookSort = 3)
        val books = listOf(Book(bookUrl = "ungrouped"), Book(bookUrl = "custom", group = 1))

        assertEquals(
            listOf(1L, BookGroup.IdNoGroup),
            buildBookshelfGroupFolders(listOf(ungrouped, custom), books).map { it.group.groupId },
        )
        assertEquals(
            listOf(1L),
            buildBookshelfGroupFolders(listOf(custom), books).map { it.group.groupId },
        )
    }
}
