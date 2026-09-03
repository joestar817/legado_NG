package io.legado.app.quickjs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickJsSandboxProcessTest {

    @Test
    fun recognizesConfiguredAndSystemExpandedIsolatedProcessNames() {
        assertTrue(
            QuickJsSandboxProcess.isSandboxProcessName(
                "io.legado.app.ng.debug:quickjs_sandbox"
            )
        )
        assertTrue(
            QuickJsSandboxProcess.isSandboxProcessName(
                "io.legado.app.ng.debug:quickjs_sandbox:" +
                    "io.legado.app.quickjs.QuickJsSandboxService"
            )
        )
    }

    @Test
    fun rejectsMainAndSimilarlyNamedProcesses() {
        assertFalse(QuickJsSandboxProcess.isSandboxProcessName(null))
        assertFalse(QuickJsSandboxProcess.isSandboxProcessName("io.legado.app.ng.debug"))
        assertFalse(
            QuickJsSandboxProcess.isSandboxProcessName(
                "io.legado.app.ng.debug:quickjs_sandbox_worker"
            )
        )
    }
}
