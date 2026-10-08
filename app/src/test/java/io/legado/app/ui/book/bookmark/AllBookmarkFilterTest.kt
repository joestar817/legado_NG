package io.legado.app.ui.book.bookmark

import io.legado.app.data.entities.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AllBookmarkFilterTest {
    @Test
    fun readerSearchOnlyMatchesChapterExcerptAndNotes() {
        val bookmarks = listOf(
            bookmark(1).copy(bookName = "needle"),
            bookmark(2).copy(bookAuthor = "needle"),
            bookmark(3).copy(chapterName = "NEEDLE"),
            bookmark(4).copy(bookText = "needle passage"),
            bookmark(5).copy(content = "needle note"),
        )
        val reader = buildAllBookmarkCollection(bookmarks, " needle ", AllBookmarkFilter(), searchBookMetadata = false)
        assertEquals(listOf(3L, 4L, 5L), reader.times())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), collect(bookmarks, query = "needle").times())
    }

    @Test
    fun readerCombinesNoteColorAndSearchAndPreservesBookmarkIdentity() {
        val selected = highlight(1).copy(bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT_CANONICAL,
            bookText = "目标段落", content = "备注")
        val others = listOf(highlight(2).copy(bookText = "目标段落"),
            highlight(3, 0xFF3366CC.toInt()).copy(bookText = "目标段落", content = "备注"),
            bookmark(4).copy(bookText = "目标段落", content = "备注"))
        val input = listOf(selected) + others
        val collection = buildAllBookmarkCollection(input, "目标", AllBookmarkFilter(
            noteFilter = BookmarkNoteFilter.WITH_NOTE, color = selected.highlightColor), searchBookMetadata = false)
        assertEquals(listOf(1L), collection.times())
        assertSame(selected, collection.groups.single().matches.single().value)
        assertEquals(2, collection.colors.size)
        assertEquals(input.map { it.time }, buildAllBookmarkCollection(input, "", AllBookmarkFilter(),
            searchBookMetadata = false).times())
    }

    @Test
    fun readerNoMatchesRetainsColorOptionsAndRecoversWhenFilterIsCleared() {
        val input = listOf(highlight(1), bookmark(2))
        val collection = buildAllBookmarkCollection(input, "", AllBookmarkFilter(
            noteFilter = BookmarkNoteFilter.WITH_NOTE), searchBookMetadata = false)
        assertEquals(0, collection.matchCount)
        assertEquals(listOf(Bookmark.DEFAULT_HIGHLIGHT_COLOR), collection.colors)
        assertEquals(2, buildAllBookmarkCollection(input, "", AllBookmarkFilter(),
            searchBookMetadata = false).matchCount)
    }

    @Test
    fun defaultFilterIsInactiveAndEachConditionActivatesIt() {
        assertFalse(AllBookmarkFilter().isActive)
        assertTrue(AllBookmarkFilter(bookKey = "book").isActive)
        assertTrue(AllBookmarkFilter(noteFilter = BookmarkNoteFilter.WITH_NOTE).isActive)
        assertTrue(AllBookmarkFilter(noteFilter = BookmarkNoteFilter.WITHOUT_NOTE).isActive)
        assertTrue(AllBookmarkFilter(color = 0).isActive)
    }

    @Test
    fun sameBookNameWithDifferentAuthorsRemainsSeparate() {
        val bookmarks = listOf(
            bookmark(1, bookName = "同名", bookAuthor = "甲"),
            bookmark(2, bookName = "同名", bookAuthor = "乙"),
            bookmark(3, bookName = "同名", bookAuthor = "甲"),
        )
        val collection = collect(bookmarks)

        assertEquals(listOf("甲", "乙"), collection.books.map { it.bookAuthor })
        assertEquals(listOf(2, 1), collection.books.map { it.count })
        assertNotEquals(collection.books[0].key, collection.books[1].key)
        assertEquals(3, collection.matchCount)
        assertEquals(listOf(0, 2), collection.groups[0].matches.map { it.index })
    }

    @Test
    fun lengthPrefixedKeysPreserveExistingEncodingWithoutDelimiterCollisions() {
        assertEquals("group:2:书名\u0000作者", bookmarkBookKey("书名", "作者"))
        assertNotEquals(bookmarkBookKey("a\u0000b", "c"), bookmarkBookKey("a", "b\u0000c"))
        assertNotEquals(bookmarkBookKey("ab", "c"), bookmarkBookKey("a", "bc"))
        assertNotEquals(bookmarkBookKey("", "书"), bookmarkBookKey("书", ""))
        assertEquals("group:2:📚\u0000", bookmarkBookKey("📚", ""))
    }

    @Test
    fun bookNoteColorAndQueryAreIntersected() {
        val red = 0xFFCC2233.toInt()
        val blue = 0xFF3366CC.toInt()
        val target = highlight(1, red).copy(bookText = "The TARGET passage", content = "批注")
        val bookmarks = listOf(
            target,
            highlight(2, blue).copy(bookText = "target", content = "批注"),
            highlight(3, red).copy(bookText = "other", content = "批注"),
            highlight(4, red).copy(bookName = "另一本", bookText = "target", content = "批注"),
            bookmark(5).copy(bookText = "target", highlightColor = red, content = "批注"),
            highlight(6, red).copy(bookText = "target"),
        )
        val collection = collect(
            bookmarks,
            query = "  target  ",
            filter = AllBookmarkFilter(
                bookKey = bookmarkBookKey(target.bookName, target.bookAuthor),
                noteFilter = BookmarkNoteFilter.WITH_NOTE,
                color = red,
            ),
        )

        assertEquals(listOf(1L), collection.times())
        assertEquals(1, collection.matchCount)
        assertEquals(5, collection.groups.single().book.count)
        assertEquals(2, collection.books.size)
        assertEquals(listOf(red, blue), collection.colors)
    }

    @Test
    fun ordinaryAndMarkedBookmarksRemainTogetherWithOrWithoutNotes() {
        val positionNote = bookmark(1).copy(content = "位置备注")
        val highlightNote = highlight(2).copy(content = "划线备注")
        val bookmarks = listOf(positionNote, highlightNote, bookmark(3), highlight(4))

        assertEquals(listOf(1L, 2L, 3L, 4L), collect(bookmarks).times())
        assertEquals(listOf(1L, 2L), collect(bookmarks, noteFilter = BookmarkNoteFilter.WITH_NOTE).times())
        assertEquals(listOf(3L, 4L), collect(bookmarks, noteFilter = BookmarkNoteFilter.WITHOUT_NOTE).times())
    }

    @Test
    fun allFormsOfBlankContentBelongOnlyToWithoutNotes() {
        val bookmarks = listOf(
            bookmark(1),
            bookmark(2).copy(content = " "),
            bookmark(3).copy(content = "\t"),
            highlight(4).copy(content = "\n"),
            highlight(5).copy(content = "　"),
            highlight(6).copy(content = " \n\t　"),
            bookmark(7).copy(content = "　备注　"),
            highlight(8).copy(content = "\n备注\n"),
        )

        val withNotes = collect(bookmarks, noteFilter = BookmarkNoteFilter.WITH_NOTE).times()
        val withoutNotes = collect(bookmarks, noteFilter = BookmarkNoteFilter.WITHOUT_NOTE).times()
        assertEquals(listOf(7L, 8L), withNotes)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), withoutNotes)
        assertTrue(withNotes.toSet().intersect(withoutNotes.toSet()).isEmpty())
        assertEquals(collect(bookmarks).times().toSet(), (withNotes + withoutNotes).toSet())
    }

    @Test
    fun defaultColorsOnPositionsAndInvalidHighlightsAreNotColorCandidates() {
        val invalidHighlight = bookmark(2).copy(bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT)
        val customColor = 0xFF123456.toInt()
        val bookmarks = listOf(bookmark(1), invalidHighlight, highlight(3, customColor))

        assertEquals(listOf(customColor), collect(bookmarks).colors)
        assertTrue(collect(bookmarks, filter = AllBookmarkFilter(color = Bookmark.DEFAULT_HIGHLIGHT_COLOR)).groups.isEmpty())
        assertEquals(listOf(1L, 2L, 3L), collect(bookmarks).times())
    }

    @Test
    fun datasetContainingOnlyMarksReturnsAllBookmarksByDefault() {
        val bookmarks = listOf(
            highlight(1).copy(highlightStyle = Bookmark.STYLE_BACKGROUND),
            highlight(2).copy(highlightStyle = Bookmark.STYLE_UNDERLINE),
            highlight(3).copy(highlightStyle = Bookmark.STYLE_WAVY_UNDERLINE),
        )

        assertEquals(listOf(1L, 2L, 3L), collect(bookmarks).times())
        assertEquals(3, collect(bookmarks).matchCount)
        assertEquals(3, collect(bookmarks).books.single().count)
        assertEquals(
            listOf(1L, 2L, 3L),
            collect(bookmarks, filter = AllBookmarkFilter(color = Bookmark.DEFAULT_HIGHLIGHT_COLOR)).times(),
        )
        assertEquals(listOf(Bookmark.DEFAULT_HIGHLIGHT_COLOR), collect(bookmarks).colors)
    }

    @Test
    fun colorCandidatesRetainExactArgbAndFirstOccurrenceOrder() {
        val colors = listOf(0, 0x00123456, 0x7F123456, 0xFF123456.toInt())
        val bookmarks = colors.mapIndexed { index, color -> highlight(index.toLong(), color) } +
            highlight(9, colors[1])

        assertEquals(colors, collect(bookmarks).colors)
        assertEquals(listOf(1L, 9L), collect(bookmarks, filter = AllBookmarkFilter(color = colors[1])).times())
        assertEquals(listOf(0L), collect(bookmarks, filter = AllBookmarkFilter(color = 0)).times())
    }

    @Test
    fun noMatchesKeepsAllBookAndColorCandidates() {
        val bookmarks = listOf(highlight(1), highlight(2).copy(bookName = "另一本"))
        val unfiltered = collect(bookmarks)
        val filtered = collect(bookmarks, query = "不存在的文本")
        val missingBook = collect(bookmarks, filter = AllBookmarkFilter(bookKey = "missing"))

        for (collection in listOf(filtered, missingBook)) {
            assertTrue(collection.groups.isEmpty())
            assertEquals(0, collection.matchCount)
            assertEquals(unfiltered.books, collection.books)
            assertEquals(unfiltered.colors, collection.colors)
        }
    }

    @Test
    fun querySearchesAllExistingFieldsIgnoringCaseAndOuterWhitespace() {
        val bookmarks = listOf(
            bookmark(1, bookName = "NeEdLe"),
            bookmark(2, bookAuthor = "NEEDLE"),
            bookmark(3).copy(chapterName = "needle"),
            bookmark(4).copy(bookText = "a needle passage"),
            bookmark(5).copy(content = "a NeEdLe note"),
            bookmark(6),
        )

        val collection = collect(bookmarks, query = "\t needle \n")
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), collection.times())
        assertEquals(bookmarks.size, collect(bookmarks, query = "\n \t").matchCount)
    }

    @Test
    fun filteringRetainsOriginalIndicesInstancesAndFirstSeenGroupOrder() {
        val bookmarks = listOf(
            bookmark(10, bookName = "甲").copy(content = "无匹配"),
            bookmark(11, bookName = "乙").copy(content = "match"),
            bookmark(12, bookName = "甲").copy(content = "match"),
            bookmark(13, bookName = "乙").copy(content = "match"),
            bookmark(14, bookName = "丙"),
        )
        val original = bookmarks.map { it.copy() }
        val collection = collect(bookmarks, query = "match")

        assertEquals(listOf("甲", "乙", "丙"), collection.books.map { it.bookName })
        assertEquals(listOf("甲", "乙"), collection.groups.map { it.book.bookName })
        assertEquals(listOf(listOf(2), listOf(1, 3)), collection.groups.map { group -> group.matches.map { it.index } })
        for (entry in collection.groups.flatMap { it.matches }) {
            assertSame(bookmarks[entry.index], entry.value)
        }
        assertEquals(original, bookmarks)
    }

    @Test
    fun notesAndColorAreIndependentConditionsOnUnifiedBookmarks() {
        val blue = 0xFF3366CC.toInt()
        val bookmarks = listOf(
            bookmark(1).copy(content = "普通备注"),
            highlight(2).copy(content = "着色备注"),
            highlight(3),
            highlight(4, blue).copy(content = "蓝色备注"),
        )
        val withNotes = collect(
            bookmarks,
            filter = AllBookmarkFilter(
                noteFilter = BookmarkNoteFilter.WITH_NOTE,
                color = Bookmark.DEFAULT_HIGHLIGHT_COLOR,
            ),
        )
        val withoutNotes = collect(
            bookmarks,
            filter = AllBookmarkFilter(
                noteFilter = BookmarkNoteFilter.WITHOUT_NOTE,
                color = Bookmark.DEFAULT_HIGHLIGHT_COLOR,
            ),
        )

        assertEquals(listOf(2L), withNotes.times())
        assertEquals(listOf(3L), withoutNotes.times())
        assertEquals(4, withNotes.books.single().count)
        assertEquals(withNotes.books, withoutNotes.books)
        assertEquals(withNotes.colors, withoutNotes.colors)
    }

    @Test
    fun addingNoteDoesNotRemoveMarkedBookmarkFromTheCollection() {
        val marked = highlight(1).copy(highlightStyle = Bookmark.STYLE_UNDERLINE)
        val withoutNote = collect(listOf(marked))
        val withNote = collect(listOf(marked.copy(content = "新增备注")))

        assertEquals(withoutNote.times(), withNote.times())
        assertEquals(withoutNote.books, withNote.books)
        assertEquals(withoutNote.colors, withNote.colors)
        assertEquals(1, withNote.matchCount)
    }

    @Test
    fun emptyInputHasNoCandidatesOrGroups() {
        val collection = collect(emptyList(), query = "text", noteFilter = BookmarkNoteFilter.WITH_NOTE)

        assertTrue(collection.books.isEmpty())
        assertTrue(collection.colors.isEmpty())
        assertTrue(collection.groups.isEmpty())
        assertEquals(0, collection.matchCount)
    }

    private fun bookmark(time: Long, bookName: String = "测试书", bookAuthor: String = "作者") =
        Bookmark(time = time, bookName = bookName, bookAuthor = bookAuthor)

    private fun highlight(time: Long, color: Int = Bookmark.DEFAULT_HIGHLIGHT_COLOR) =
        bookmark(time).copy(
            bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT,
            endChapterPos = 1,
            highlightColor = color,
        )

    private fun collect(
        bookmarks: List<Bookmark>,
        query: String = "",
        noteFilter: BookmarkNoteFilter = BookmarkNoteFilter.ALL,
        filter: AllBookmarkFilter = AllBookmarkFilter(noteFilter = noteFilter),
    ) = buildAllBookmarkCollection(bookmarks, query, filter)

    private fun AllBookmarkCollection.times() = groups.flatMap { group ->
        group.matches.map { it.value.time }
    }
}
