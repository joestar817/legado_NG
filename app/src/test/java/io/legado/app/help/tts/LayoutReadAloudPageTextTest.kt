package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutReadAloudPageTextTest {
    @Test fun layoutBoundariesPreserveContentAndParagraphOffsets() {
        val content = "abcdef\nghi😀jkl\n"
        val pages = LayoutReadAloudPageText(content, listOf(0, 3, 10, 13))
        assertEquals(content, (0 until pages.size).joinToString("") { pages.textAt(it) })
        assertEquals(listOf(0, 3, 7, 10, 13), pages.paragraphs.map { it.chapterPosition })
        assertEquals(listOf(1, 2, 3, 4, 5), pages.paragraphs.map { it.num })
        assertFalse(pages.paragraphs[0].isParagraphEnd)
        assertTrue(pages.paragraphs[1].isParagraphEnd)
        assertEquals("abc\ndef\nghi\n😀j\nkl\n", pages.readTextRange(pageSplit = true))
        assertEquals(2, pages.indexAt(12))
        assertEquals(10, pages.startAt(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBoundaryInsideSurrogatePair() {
        LayoutReadAloudPageText("a😀b", listOf(2))
    }
}
