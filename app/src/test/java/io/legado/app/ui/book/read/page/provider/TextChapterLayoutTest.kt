package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChapterLayoutTest {

    @Test
    fun `empty pending page is not committed after terminal newpage`() {
        assertFalse(shouldCommitPendingTextPage(lineCount = 0))
    }

    @Test
    fun `page with layout lines is still committed`() {
        assertTrue(shouldCommitPendingTextPage(lineCount = 1))
    }
}
