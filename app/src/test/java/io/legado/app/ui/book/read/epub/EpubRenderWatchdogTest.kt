package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test

class EpubRenderWatchdogTest {
    private val callbacks = mutableListOf<Runnable>()
    private val removed = mutableListOf<Runnable>()
    private val watchdog = EpubRenderWatchdog({ task, delay ->
        assertEquals(25_000L, delay)
        callbacks.add(task)
    }, { removed.add(it) })

    @Test
    fun missingRendererOrCaptureCallbackSettlesOnce() {
        var waiting = true
        var failures = 0
        watchdog.arm { waiting = false; failures++ }
        assertTrue(waiting)
        callbacks.single().run()
        callbacks.single().run()
        assertFalse(waiting)
        assertEquals(1, failures)
    }

    @Test
    fun completedOrClosedOperationCannotFailLater() {
        var failures = 0
        watchdog.arm { failures++ }
        watchdog.cancel()
        assertEquals(callbacks, removed)
        callbacks.single().run()
        assertEquals(0, failures)
    }

    @Test
    fun lateTimeoutCannotCancelReplacementOperation() {
        val failed = mutableListOf<String>()
        watchdog.arm { failed.add("old") }
        watchdog.arm { failed.add("new") }
        callbacks.first().run()
        assertTrue(failed.isEmpty())
        callbacks.last().run()
        assertEquals(listOf("new"), failed)
    }
}
