package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubFrameWindowTest {
    private data class Frame(val id: Int, val bytes: Long = 12)

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

    @Test
    fun continuousTurnsStayBoundedWithoutAnIdleRetain() {
        val disposed = arrayListOf<Int>()
        val window = EpubFrameWindow<Frame>(maxCount = 4, maxBytes = 48, sizeOf = { it.bytes }) { disposed.add(it.id) }
        repeat(100) { page ->
            window.put(page, Frame(page))
            assertTrue(window.ownedCount <= 4)
            assertTrue(window.ownedBytes <= 48)
            assertEquals(page, window[page]?.id)
        }
        assertEquals((0..95).toList(), disposed)
        window.clear()
        assertEquals((0..99).toList(), disposed)
        assertEquals(0, window.ownedCount)
        assertEquals(0L, window.ownedBytes)
    }

    @Test
    fun borrowedFrameSurvivesPaintInvalidationAndIsDisposedExactlyOnce() {
        val disposed = arrayListOf<Int>()
        val window = EpubFrameWindow<Frame>(maxBytes = 48, sizeOf = { it.bytes }) { disposed.add(it.id) }
        window.put(0, Frame(0))
        val source = requireNotNull(window.acquire(0))
        window.clear()
        assertNull(window[0])
        assertTrue(disposed.isEmpty())
        assertEquals(12L, window.ownedBytes)
        window.put(0, Frame(1))
        assertEquals(0, source.value.id)
        source.close()
        source.close()
        assertEquals(listOf(0), disposed)
        assertEquals(12L, window.ownedBytes)
        window.clear()
        assertEquals(listOf(0, 1), disposed)
    }

    @Test
    fun protectedFramesForceNewCacheEntryToBeDeclinedInsteadOfRecycledSources() {
        val disposed = arrayListOf<Int>()
        val window = EpubFrameWindow<Frame>(maxBytes = 24, sizeOf = { it.bytes }) { disposed.add(it.id) }
        window.put(0, Frame(0))
        window.put(1, Frame(1))
        val a = requireNotNull(window.acquire(0))
        val b = requireNotNull(window.acquire(1))
        window.clear()
        window.put(2, Frame(2))
        assertNull(window[2])
        assertEquals(listOf(2), disposed)
        assertEquals(24L, window.ownedBytes)
        a.close(); b.close()
        assertEquals(listOf(2, 0, 1), disposed)
        assertEquals(0L, window.ownedBytes)
    }

    @Test
    fun byteBudgetAccountsForUnequalFramesAndOversizedFrames() {
        val disposed = arrayListOf<Int>()
        val window = EpubFrameWindow<Frame>(maxBytes = 48, sizeOf = { it.bytes }) { disposed.add(it.id) }
        window.put(0, Frame(0, 20))
        window.put(1, Frame(1, 30))
        assertNull(window[0])
        assertEquals(30L, window.ownedBytes)
        window.put(2, Frame(2, 60))
        assertNull(window[2])
        assertEquals(30L, window.ownedBytes)
        window.clear()
        assertEquals(0L, window.ownedBytes)
        assertEquals(listOf(0, 2, 1), disposed)
    }

    @Test
    fun replacementDoesNotDisposeBorrowedOldValueUntilReleased() {
        val disposed = arrayListOf<Int>()
        val window = EpubFrameWindow<Frame> { disposed.add(it.id) }
        window.put(0, Frame(0))
        val old = requireNotNull(window.acquire(0))
        window.put(0, Frame(1))
        assertTrue(disposed.isEmpty())
        assertEquals(1, window[0]?.id)
        old.close()
        window.clear()
        assertEquals(listOf(0, 1), disposed)
    }
}
