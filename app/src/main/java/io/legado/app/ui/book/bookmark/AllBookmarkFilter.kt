package io.legado.app.ui.book.bookmark

import io.legado.app.data.entities.Bookmark

internal enum class BookmarkNoteFilter {
    ALL,
    WITH_NOTE,
    WITHOUT_NOTE,
}

internal data class AllBookmarkFilter(
    val bookKey: String? = null,
    val noteFilter: BookmarkNoteFilter = BookmarkNoteFilter.ALL,
    val color: Int? = null,
) {
    val isActive: Boolean
        get() = bookKey != null || noteFilter != BookmarkNoteFilter.ALL || color != null
}

internal data class BookmarkBookOption(
    val key: String,
    val bookName: String,
    val bookAuthor: String,
    val count: Int,
)

internal data class BookmarkTimelineGroup(
    val book: BookmarkBookOption,
    val matches: List<IndexedValue<Bookmark>>,
)

internal data class AllBookmarkCollection(
    val books: List<BookmarkBookOption>,
    val colors: List<Int>,
    val groups: List<BookmarkTimelineGroup>,
    val matchCount: Int,
)

internal fun bookmarkBookKey(bookName: String, bookAuthor: String): String =
    "group:${bookName.length}:$bookName\u0000$bookAuthor"

internal fun buildAllBookmarkCollection(
    bookmarks: List<Bookmark>,
    query: String,
    filter: AllBookmarkFilter,
): AllBookmarkCollection {
    val indexedGroups = bookmarks.withIndex().groupBy {
        bookmarkBookKey(it.value.bookName, it.value.bookAuthor)
    }
    val books = indexedGroups.map { (key, entries) ->
        val first = entries.first().value
        BookmarkBookOption(key, first.bookName, first.bookAuthor, entries.size)
    }
    val colors = bookmarks.asSequence()
        .filter { it.isTextHighlight }
        .map { it.highlightColor }
        .distinct()
        .toList()
    val normalizedQuery = query.trim()
    val groups = books.mapNotNull { book ->
        if (filter.bookKey != null && filter.bookKey != book.key) {
            return@mapNotNull null
        }
        val matches = indexedGroups.getValue(book.key).filter { (_, bookmark) ->
            bookmark.matchesNoteFilter(filter.noteFilter) &&
                (filter.color == null ||
                    bookmark.isTextHighlight && bookmark.highlightColor == filter.color) &&
                bookmark.matchesQuery(normalizedQuery)
        }
        if (matches.isEmpty()) null else BookmarkTimelineGroup(book, matches)
    }
    return AllBookmarkCollection(books, colors, groups, groups.sumOf { it.matches.size })
}

private fun Bookmark.matchesNoteFilter(filter: BookmarkNoteFilter): Boolean = when (filter) {
    BookmarkNoteFilter.ALL -> true
    BookmarkNoteFilter.WITH_NOTE -> content.isNotBlank()
    BookmarkNoteFilter.WITHOUT_NOTE -> content.isBlank()
}

private fun Bookmark.matchesQuery(query: String): Boolean = query.isEmpty() ||
    bookName.contains(query, ignoreCase = true) ||
    bookAuthor.contains(query, ignoreCase = true) ||
    chapterName.contains(query, ignoreCase = true) ||
    bookText.contains(query, ignoreCase = true) ||
    content.contains(query, ignoreCase = true)
