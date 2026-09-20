package io.legado.app.ui.book.read

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.book.read.page.api.ReaderSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionBookmarkTest {
    private val book = Book(name = "测试书籍", author = "测试作者")

    @Test
    fun positionBookmarkKeepsExistingDefaultsAndSelectedText() {
        val selected = ReaderSelection(2, 128, "第三章", "　😀重复文字\n下一段", 3, 12)

        val bookmark = selected.createBookmark(book)

        assertEquals(book.name, bookmark.bookName)
        assertEquals(book.author, bookmark.bookAuthor)
        assertEquals(2, bookmark.chapterIndex)
        assertEquals(128, bookmark.chapterPos)
        assertEquals("第三章", bookmark.chapterName)
        assertEquals("　😀重复文字\n下一段", bookmark.bookText)
        assertEquals("", bookmark.content)
        assertEquals(Bookmark.TYPE_POSITION, bookmark.bookmarkType)
        assertEquals(0, bookmark.endChapterIndex)
        assertEquals(0, bookmark.endChapterPos)
        assertFalse(bookmark.isTextHighlight)
    }

    @Test
    fun highlightPreservesUtf16RangeAndExclusiveEnd() {
        val text = "😀文字"
        val bookmark = ReaderSelection(1, 10, "第二章", text, 1, 10 + text.length)
            .createTextHighlight(book)!!

        assertEquals(10, bookmark.chapterPos)
        assertEquals(14, bookmark.endChapterPos)
        assertEquals(text, bookmark.bookText)
        assertEquals(Bookmark.TYPE_TEXT_HIGHLIGHT, bookmark.bookmarkType)
        assertEquals(Bookmark.STYLE_BACKGROUND, bookmark.highlightStyle)
        assertEquals(Bookmark.DEFAULT_HIGHLIGHT_COLOR, bookmark.highlightColor)
        assertTrue(bookmark.containsChapterPosition(1, 10))
        assertTrue(bookmark.containsChapterPosition(1, 13))
        assertFalse(bookmark.containsChapterPosition(1, 14))
    }

    @Test
    fun scrollingSelectionMayEndAtASmallerOffsetInTheNextChapter() {
        val bookmark = ReaderSelection(2, 512, "第三章", "章尾\n下一章", 3, 8)
            .createTextHighlight(book)!!

        assertEquals(512, bookmark.chapterPos)
        assertEquals(3, bookmark.endChapterIndex)
        assertEquals(8, bookmark.endChapterPos)
        assertTrue(bookmark.coversChapter(2))
        assertTrue(bookmark.containsChapterPosition(3, 0))
        assertFalse(bookmark.containsChapterPosition(3, 8))
    }

    @Test
    fun rangeEndingAtNextChapterStartIsStillValid() {
        val bookmark = ReaderSelection(2, 512, "第三章", "章尾", 3, 0)
            .createTextHighlight(book)!!

        assertTrue(bookmark.isTextHighlight)
        assertFalse(bookmark.containsChapterPosition(3, 0))
    }

    @Test
    fun collapsedAndReversedHighlightsAreRejected() {
        assertNull(ReaderSelection(2, 12, "第三章", "").createTextHighlight(book))
        assertNull(ReaderSelection(2, 12, "第三章", "文字", 2, 10).createTextHighlight(book))
        assertNull(ReaderSelection(2, 12, "第三章", "文字", 1, 500).createTextHighlight(book))
    }

    @Test
    fun identicalTextDoesNotReplaceTheSelectedOccurrencePosition() {
        val first = ReaderSelection(1, 4, "第二章", "重复文字", 1, 8)
            .createTextHighlight(book)!!
        val second = ReaderSelection(1, 104, "第二章", "重复文字", 1, 108)
            .createTextHighlight(book)!!

        assertEquals(first.bookText, second.bookText)
        assertEquals(4, first.chapterPos)
        assertEquals(104, second.chapterPos)
    }
}
