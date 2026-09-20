package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ReadAloudTextSourceTest {
    @Test
    fun ordinaryTextAndParagraphsDoNotRequestPagination() {
        val paragraphs = listOf(Paragraph(1, "　😀正文", 0, 5))
        val source = object : ReadAloudTextSource {
            override val isReady = true
            override val hasContent = true
            override val text = "　😀正文\n下一段"
            override val paragraphs: List<ReadAloudParagraph> = paragraphs
            override val pageText: ReadAloudPageText?
                get() = error("普通朗读不得请求分页")
        }

        assertEquals("　😀正文\n下一段", source.readText())
        assertSame(paragraphs, source.readParagraphs(false))
        assertEquals(1, source.paragraphNumberAt(4, false))
    }

    @Test
    fun pageSplittingAddsOnlyMissingNewlines() {
        val source = source(Pages(listOf("甲", "乙\n", "丙")))

        assertEquals("甲乙\n丙", source.readText(false))
        assertEquals("甲\n乙\n丙\n", source.readText(true))
    }

    @Test
    fun nativeMediaMarkersBecomeSpacesWithoutChangingOffsets() {
        val pages = Pages(listOf("甲袮😀", "꧁乙\n"))

        assertEquals("甲 😀 乙\n", pages.readTextRange())
        assertEquals("甲袮😀꧁乙\n".length, pages.readTextRange().length)
        assertEquals("甲 😀\n 乙\n", pages.readTextRange(pageSplit = true))
    }

    @Test
    fun aPageWindowUsesUtf16OffsetsRelativeToTheWindow() {
        val pages = Pages(listOf("前页", "😀乙", "丙"))

        assertEquals(
            "乙\n丙\n",
            pages.readTextRange(pageIndex = 1, pageSplit = true, startPos = 2, pageEndIndex = 99),
        )
        assertEquals("😀乙", pages.readTextRange(pageIndex = 1, pageEndIndex = 1))
    }

    @Test
    fun emptyPagesKeepExistingPageBreakBehavior() {
        val pages = Pages(listOf("", "甲", "", "\n", "乙"))

        assertEquals("\n甲\n\n乙\n", pages.readTextRange(pageSplit = true))
        assertEquals("", Pages(emptyList()).readTextRange())
        assertEquals("", pages.readTextRange(pageIndex = 2, pageEndIndex = 1))
    }

    @Test
    fun wholeChapterTextIsNotLimitedToTheFirstPage() {
        val pages = Pages((1..12).map { "第${it}段\n" })
        val source = source(pages)

        assertEquals((1..12).joinToString("") { "第${it}段\n" }, source.readText())
        assertEquals(source.readText(), source.readText(pageSplit = true))
    }

    @Test
    fun paragraphLookupKeepsInclusiveNativeBoundaryAndFirstMatch() {
        val whole = listOf(Paragraph(1, "第一段", 0, 3), Paragraph(2, "第二段", 3, 6))
        val perPage = listOf(Paragraph(1, "第", 0, 1, false), Paragraph(2, "一段", 1, 3))
        val source = source(Pages(listOf("第", "一段"), perPage), whole)

        assertEquals(1, source.paragraphNumberAt(3, false))
        assertEquals(2, source.paragraphNumberAt(2, true))
        assertEquals(-1, source.paragraphNumberAt(99, false))
        assertSame(whole, source.readParagraphs(false))
        assertSame(perPage, source.readParagraphs(true))
        assertFalse(source.readParagraphs(true).first().isParagraphEnd)
    }

    @Test(expected = IllegalStateException::class)
    fun explicitPageReadingDoesNotSilentlyFallBackWithoutPagination() {
        source(null).readText(pageSplit = true)
    }

    @Test(expected = IllegalStateException::class)
    fun explicitPageParagraphsRequirePagination() {
        source(null).readParagraphs(pageSplit = true)
    }

    private fun source(
        pages: ReadAloudPageText?,
        paragraphs: List<ReadAloudParagraph> = emptyList(),
    ): ReadAloudTextSource = object : ReadAloudTextSource {
        override val isReady = true
        override val hasContent = true
        override val text: String get() = pages?.readTextRange().orEmpty()
        override val paragraphs: List<ReadAloudParagraph> = paragraphs
        override val pageText = pages
    }

    private class Pages(
        private val texts: List<String>,
        override val paragraphs: List<ReadAloudParagraph> = emptyList(),
    ) : ReadAloudPageText {
        override val size: Int get() = texts.size
        override fun textAt(index: Int): String = texts[index]
    }

    private data class Paragraph(
        override val num: Int,
        override val text: String,
        override val chapterPosition: Int,
        val end: Int,
        override val isParagraphEnd: Boolean = true,
    ) : ReadAloudParagraph {
        override val length: Int get() = text.length
        override val chapterIndices: IntRange get() = chapterPosition..end
    }
}
