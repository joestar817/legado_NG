package io.legado.app.ui.book.read.epub

import io.legado.app.data.entities.BookChapter
import io.legado.app.model.epub.EpubResourceLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubChapterLinksTest {
    @Test
    fun catalogLinksOpenTheirOwnChapters() {
        val chapters = listOf(
            BookChapter(url = "OPS/chapter1.html", index = 1),
            BookChapter(url = "OPS/chapter2.html", index = 2),
            BookChapter(url = "OEBPS/Text/v1ch01.xhtml", index = 6),
        )
        assertEquals(2, EpubChapterLinks.find(chapters, EpubResourceLink("OPS/chapter2.html"))?.index)
        assertEquals(6, EpubChapterLinks.find(chapters, EpubResourceLink("OEBPS/Text/v1ch01.xhtml"))?.index)
    }

    @Test
    fun sharedDocumentRequiresAnUnambiguousFragment() {
        val chapters = listOf(
            BookChapter(url = "OPS/shared.xhtml#one", startFragmentId = "one", index = 3),
            BookChapter(url = "OPS/shared.xhtml#two", startFragmentId = "two", index = 4),
        )
        assertEquals(4, EpubChapterLinks.find(chapters, EpubResourceLink("OPS/shared.xhtml", fragment = "two"))?.index)
        assertNull(EpubChapterLinks.find(chapters, EpubResourceLink("OPS/shared.xhtml", fragment = "unknown")))
    }
}
