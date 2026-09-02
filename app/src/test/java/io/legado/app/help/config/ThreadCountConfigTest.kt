package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadCountConfigTest {

    @Test
    fun normalizeThreadCount_clampsToSupportedRange() {
        assertEquals(THREAD_COUNT_MIN, normalizeThreadCount(Int.MIN_VALUE))
        assertEquals(THREAD_COUNT_MIN, normalizeThreadCount(THREAD_COUNT_MIN))
        assertEquals(THREAD_COUNT_DEFAULT, normalizeThreadCount(THREAD_COUNT_DEFAULT))
        assertEquals(THREAD_COUNT_MAX, normalizeThreadCount(THREAD_COUNT_MAX))
        assertEquals(THREAD_COUNT_MAX, normalizeThreadCount(Int.MAX_VALUE))
    }
}
