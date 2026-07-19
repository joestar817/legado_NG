package io.legado.app.ui.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSheetLaunchDebouncerTest {

    @Test
    fun `rapid card clicks share one launch window`() {
        val debouncer = TtsSheetLaunchDebouncer(intervalMillis = 700L)

        assertTrue(debouncer.tryAcquire(1_000L))
        assertFalse(debouncer.tryAcquire(1_200L))
        assertFalse(debouncer.tryAcquire(1_699L))
        assertTrue(debouncer.tryAcquire(1_700L))
    }

    @Test
    fun `rejected clicks do not extend launch window`() {
        val debouncer = TtsSheetLaunchDebouncer(intervalMillis = 700L)

        assertTrue(debouncer.tryAcquire(2_000L))
        assertFalse(debouncer.tryAcquire(2_600L))
        assertTrue(debouncer.tryAcquire(2_700L))
    }
}
