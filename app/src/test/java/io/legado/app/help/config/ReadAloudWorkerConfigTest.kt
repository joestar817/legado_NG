package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudWorkerConfigTest {

    @Test
    fun normalizeReadAloudWorkerCount_defaultsToThree() {
        assertEquals(3, normalizeReadAloudWorkerCount(null))
        assertEquals(3, normalizeReadAloudWorkerCount(""))
        assertEquals(3, normalizeReadAloudWorkerCount("invalid"))
    }

    @Test
    fun normalizeReadAloudWorkerCount_limitsConfiguredRange() {
        assertEquals(1, normalizeReadAloudWorkerCount("0"))
        assertEquals(3, normalizeReadAloudWorkerCount("3"))
        assertEquals(5, normalizeReadAloudWorkerCount("9"))
    }
}
