package io.legado.app.ui.book.read.page.entities

import io.legado.app.help.tts.ReadAloudParagraph

@Suppress("unused", "MemberVisibilityCanBePrivate")
data class TextParagraph(
    override var num: Int,
    val textLines: ArrayList<TextLine> = arrayListOf(),
) : ReadAloudParagraph {
    override val text: String get() = textLines.joinToString("") { it.text }
    override val length: Int get() = text.length
    val firstLine: TextLine get() = textLines.first()
    val lastLine: TextLine get() = textLines.last()
    override val chapterIndices: IntRange get() = firstLine.chapterPosition..lastLine.chapterPosition + lastLine.charSize
    override val chapterPosition: Int get() = firstLine.chapterPosition
    val realNum: Int get() = firstLine.paragraphNum
    override val isParagraphEnd: Boolean get() = lastLine.isParagraphEnd

}
