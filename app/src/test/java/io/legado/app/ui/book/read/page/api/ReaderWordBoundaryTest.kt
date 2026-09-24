package io.legado.app.ui.book.read.page.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderWordBoundaryTest {
    @Test fun authorOrStyleFragmentsDoNotChangeWordSelection() {
        val paragraph = "前 helloworld 后"
        assertEquals(2..11, readerWordBoundary(paragraph, 4))
        assertEquals(2..11, readerWordBoundary(paragraph, 9))
    }

    @Test fun emptyAndOutOfBoundsHitsDoNotCreateSelections() {
        assertNull(readerWordBoundary("", 0))
        assertNull(readerWordBoundary("abc", -1))
        assertNull(readerWordBoundary("abc", 3))
    }
}
