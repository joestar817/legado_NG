package io.legado.app.ui.book.read

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.book.read.page.api.ReaderSelection

/** 保留现有书签字段和默认值，页面后端只交付选区，不各自实现书签规则。 */
internal fun ReaderSelection.createBookmark(book: Book): Bookmark {
    return book.createBookMark().apply {
        chapterIndex = this@createBookmark.chapterIndex
        chapterPos = chapterPosition
        chapterName = chapterTitle
        bookText = text
    }
}

internal fun ReaderSelection.createTextHighlight(book: Book): Bookmark? {
    if (
        endChapterIndex < chapterIndex ||
        endChapterIndex == chapterIndex && endChapterPosition <= chapterPosition
    ) {
        return null
    }
    return createBookmark(book).apply {
        bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT
        endChapterIndex = this@createTextHighlight.endChapterIndex
        endChapterPos = endChapterPosition
        highlightStyle = Bookmark.STYLE_BACKGROUND
        highlightColor = Bookmark.DEFAULT_HIGHLIGHT_COLOR
    }
}
