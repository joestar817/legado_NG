package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfGridBackgroundTest {
    @Test
    fun tracksBarMovementRelativeToViewport() {
        assertEquals(208, bookshelfContainerBottomInset(100, 900, 800, 8))
        assertEquals(308, bookshelfContainerBottomInset(100, 900, 700, 8))
        assertEquals(108, bookshelfContainerBottomInset(100, 900, 900, 8))
    }

    @Test
    fun handlesFixedHiddenAndOutOfBoundsBars() {
        assertEquals(8, bookshelfContainerBottomInset(100, 900, 1000, 8))
        assertEquals(8, bookshelfContainerBottomInset(100, 900, null, 8))
        assertEquals(0, bookshelfContainerBottomInset(100, 900, 1200, 8))
        assertEquals(900, bookshelfContainerBottomInset(100, 900, 0, 8))
        assertEquals(0, bookshelfContainerBottomInset(0, 0, null, 8))
    }
}
