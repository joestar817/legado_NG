package io.legado.app.ui.main.home

import io.legado.app.data.entities.Book
import java.time.LocalDate

/** A real update discovered today; read progress does not remove it from this snapshot. */
internal data class HomeUpdateBook(
    val book: Book,
    val updatedChapterCount: Int?,
    val foundAt: Long,
    // Book equality only compares URL; freeze the actual NgBookCover display keys for StateFlow.
    val bookUrl: String = book.bookUrl,
    val name: String = book.name,
    val author: String = book.author,
    val displayCover: String? = book.getDisplayCover(),
    val origin: String = book.origin,
)

internal data class HomeUpdatesState(
    val today: LocalDate = LocalDate.now(),
    val books: List<HomeUpdateBook> = emptyList(),
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val checking: Boolean = false,
    val lastCheckedAt: Long? = null,
)

internal const val HOME_UPDATES_PAGE_SIZE = 6

internal fun homeUpdatesPageCount(bookCount: Int): Int =
    if (bookCount <= 0) 0 else (bookCount - 1) / HOME_UPDATES_PAGE_SIZE + 1

internal fun homeUpdatesClampedPage(page: Int, bookCount: Int): Int =
    page.coerceIn(0, (homeUpdatesPageCount(bookCount) - 1).coerceAtLeast(0))

/** The last page contains only real books; empty grid slots belong to layout, not data. */
internal fun <T> homeUpdatesPageItems(books: List<T>, page: Int): List<T> {
    if (page < 0 || page >= homeUpdatesPageCount(books.size)) return emptyList()
    val start = page * HOME_UPDATES_PAGE_SIZE
    return books.subList(start, minOf(start + HOME_UPDATES_PAGE_SIZE, books.size))
}
