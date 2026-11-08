package io.legado.app.ui.config

import org.junit.Assert.*
import org.junit.Test

class InterfaceFontSelectionGateTest {
    @Test
    fun onlyLatestRequestCanPublishItsSelection() {
        val gate = InterfaceFontSelectionGate()
        val old = gate.nextRequest()
        val latest = gate.nextRequest()
        var installed = "initial"

        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(latest))
        assertFalse(gate.commitIfCurrent(old) { installed = "stale" })
        assertEquals("initial", installed)
        assertTrue(gate.commitIfCurrent(latest) { installed = "latest" })
        assertEquals("latest", installed)
        assertFalse(gate.commitIfCurrent(old) { installed = "late stale result" })
        assertEquals("latest", installed)
    }

    @Test
    fun dismissalInvalidatesAnOutstandingLoadWithoutChangingInstalledSelection() {
        val gate = InterfaceFontSelectionGate()
        val loading = gate.nextRequest()
        var commits = 0

        gate.invalidate()

        assertFalse(gate.isCurrent(loading))
        assertFalse(gate.commitIfCurrent(loading) { commits++ })
        assertEquals(0, commits)
    }

    @Test
    fun aNewRequestAfterInvalidationCanApplyNormally() {
        val gate = InterfaceFontSelectionGate()
        gate.nextRequest()
        gate.invalidate()
        val request = gate.nextRequest()
        var installed = false

        assertTrue(gate.commitIfCurrent(request) { installed = true })
        assertTrue(installed)
    }

    @Test
    fun failedPublicationPropagatesInsteadOfReportingSuccess() {
        val gate = InterfaceFontSelectionGate()
        val request = gate.nextRequest()
        val failure = IllegalStateException("storage failed")

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            gate.commitIfCurrent(request) { throw failure }
        })
        assertTrue(gate.isCurrent(request))
        var retried = false
        assertTrue(gate.commitIfCurrent(request) { retried = true })
        assertTrue(retried)
    }
}
