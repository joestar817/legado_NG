package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.tts.ReadAloudPageText
import io.legado.app.help.tts.ReadAloudParagraph
import io.legado.app.help.tts.ReadAloudTextSource
import io.legado.app.help.tts.readAloudWholeChapterPageEndIndex
import io.legado.app.help.tts.readTextRange
import io.legado.app.ui.book.read.page.entities.TextChapter

/** 原生章节的只读适配，继续使用已有正文、段落及分页结果，不复制章节状态。 */
internal class NativeReadAloudTextSource(private val chapter: TextChapter,
                                       private val layoutPages: ReadAloudPageText? = null) : ReadAloudTextSource {
    override val isReady: Boolean get() = chapter.isCompleted
    override val hasContent: Boolean
        get() = readAloudWholeChapterPageEndIndex(chapter.pageSize) != null
    override val text: String get() = nativePages.readTextRange()
    override val paragraphs: List<ReadAloudParagraph> get() = chapter.getParagraphs(false)

    override val pageText: ReadAloudPageText get() = layoutPages ?: nativePages
    private val nativePages: ReadAloudPageText = object : ReadAloudPageText {
        override val size: Int get() = chapter.pageSize
        override val paragraphs: List<ReadAloudParagraph> get() = chapter.getParagraphs(true)

        override fun textAt(index: Int): String = chapter.pages[index].text
        override fun startAt(index: Int): Int = chapter.getReadLength(index)
    }
}
