package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubFrameWindowTest {
    @Test
    fun movingWindowAndInvalidationDisposeOnlyOwnedFrames() {
        val disposed = arrayListOf<String>()
        val window = EpubFrameWindow<String> { disposed.add(it) }
        window.put(0, "previous")
        window.put(1, "current")
        window.put(2, "next")
        window.retain(setOf(1, 2, 3))
        assertEquals(listOf("previous"), disposed)
        assertNull(window[0])
        assertEquals("current", window[1])
        window.put(2, "updated")
        assertEquals(listOf("previous", "next"), disposed)
        window.clear()
        window.clear()
        assertEquals(listOf("previous", "next", "current", "updated"), disposed)
    }
}
