package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfReadingProgressTest {
    @Test
    fun missingDirectoryAndInitialPositionHaveNoProgress() {
        assertEquals(0f, bookshelfReadingProgress(5, 20, 0), 0f)
        assertEquals(0f, bookshelfReadingProgress(0, 0, 100), 0f)
        assertEquals(0f, bookshelfReadingProgress(-1, 20, 100), 0f)
    }

    @Test
    fun progressUsesCurrentChapterAndClampsStalePositions() {
        assertEquals(0.01f, bookshelfReadingProgress(0, 20, 100), 0f)
        assertEquals(0.5f, bookshelfReadingProgress(49, 0, 100), 0f)
        assertEquals(1f, bookshelfReadingProgress(99, 0, 100), 0f)
        assertEquals(1f, bookshelfReadingProgress(Int.MAX_VALUE, 0, 100), 0f)
    }
}
