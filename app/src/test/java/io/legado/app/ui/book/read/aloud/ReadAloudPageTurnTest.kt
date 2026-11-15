package io.legado.app.ui.book.read.aloud

import io.legado.app.help.tts.LayoutReadAloudPageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudPageTurnTest {
    @Test
    fun adjacentPageFollowsLayoutBoundariesFromCurrentPosition() {
        val pages = LayoutReadAloudPageText("0123456789abcdefghijklmn", listOf(5, 12))

        assertEquals(5, pages.adjacentPagePosition(2, next = true))
        assertEquals(0, pages.adjacentPagePosition(5, next = false))
        assertEquals(12, pages.adjacentPagePosition(8, next = true))
        assertEquals(5, pages.adjacentPagePosition(17, next = false))
    }

    @Test
    fun firstAndLastPagesReportChapterBoundaries() {
        val pages = LayoutReadAloudPageText("0123456789", listOf(5))
        val empty = LayoutReadAloudPageText("", emptyList())

        assertNull(pages.adjacentPagePosition(0, next = false))
        assertNull(pages.adjacentPagePosition(9, next = true))
        assertNull(empty.adjacentPagePosition(0, next = true))
    }
}
