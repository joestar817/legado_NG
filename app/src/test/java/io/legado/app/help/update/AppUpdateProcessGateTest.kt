package io.legado.app.help.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateProcessGateTest {

    @Test
    fun `only first check starts in one process`() {
        val gate = AppUpdateProcessGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
    }

    @Test
    fun `new process gate allows a new check`() {
        val firstProcess = AppUpdateProcessGate()
        val nextProcess = AppUpdateProcessGate()

        assertTrue(firstProcess.tryStart())
        assertFalse(firstProcess.tryStart())
        assertTrue(nextProcess.tryStart())
    }
}
