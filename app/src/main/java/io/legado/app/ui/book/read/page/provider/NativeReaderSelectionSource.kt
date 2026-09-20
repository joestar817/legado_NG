package io.legado.app.ui.book.read.page.provider

import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.api.ReaderContentEditTarget
import io.legado.app.ui.book.read.page.api.ReaderSelection
import io.legado.app.ui.book.read.page.api.ReaderSelectionSource

/** 只在原生适配端解释 TextPage/TextLine，不持有第二份选区或书签状态。 */
internal class NativeReaderSelectionSource(private val readView: ReadView) : ReaderSelectionSource {
    override val selectedText: String
        get() = readView.curPage.selectedText

    override fun bookmarkSelection(): ReaderSelection {
        return readView.curPage.bookmarkSelection()
    }

    override fun highlightSelection(): ReaderSelection? {
        return readView.curPage.highlightSelection()
    }

    override fun contentEditTarget(highlight: ReaderSelection?): ReaderContentEditTarget? {
        val page = if (highlight != null) {
            val chapter = listOfNotNull(
                ReadBook.curTextChapter, ReadBook.prevTextChapter, ReadBook.nextTextChapter,
            ).firstOrNull { it.chapter.index == highlight.chapterIndex } ?: return null
            chapter.getPageByReadPos(highlight.chapterPosition) ?: return null
        } else {
            val selected = readView.curPage
            selected.relativePage(selected.selectStartPos.relativePagePos)
        }
        val line = if (highlight != null) {
            page.lines.lastOrNull { it.chapterPosition <= highlight.chapterPosition }
        } else {
            page.lines.getOrNull(readView.curPage.selectStartPos.lineIndex)
        } ?: return null
        val selection = highlight ?: run {
            // 保留原入口在没有当前书籍时不能由临时选区打开编辑器的行为。
            if (ReadBook.book == null) return null
            highlightSelection() ?: return null
        }
        val paragraphStart = page.getTextChapter().pages.asSequence()
            .flatMap { it.lines.asSequence() }
            .firstOrNull { it.sourceParagraphIndex == line.sourceParagraphIndex }
            ?.chapterPosition ?: line.chapterPosition
        return ReaderContentEditTarget(
            chapterIndex = page.chapterIndex,
            chapterTitle = page.title,
            paragraphIndex = line.sourceParagraphIndex.coerceAtLeast(0),
            paragraphOffset = (selection.chapterPosition - paragraphStart).coerceAtLeast(0),
            selectedTextPrefix = selection.text.substringBefore('\n').take(128),
        )
    }
}
