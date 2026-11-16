package io.legado.app.help.tts.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftOfflineSupportTest {
    @Test
    fun onlySupportedAndroidArm64ProcessesCanUseTheRuntime() {
        assertNull(MicrosoftOfflineSupport.unavailableReason(34, "aarch64"))
        assertNull(MicrosoftOfflineSupport.unavailableReason(34, "arm64"))
        assertEquals(MicrosoftOfflineSupport.UNSUPPORTED_ARCH,
            MicrosoftOfflineSupport.unavailableReason(34, "x86_64"))
        assertEquals(MicrosoftOfflineSupport.UNSUPPORTED_ARCH,
            MicrosoftOfflineSupport.unavailableReason(34, null))
        assertEquals(MicrosoftOfflineSupport.UNSUPPORTED_ANDROID,
            MicrosoftOfflineSupport.unavailableReason(25, "aarch64"))
    }

    @Test
    fun packageAbiAndMissingRevisionCannotBeMistakenForReady() {
        assertTrue(MicrosoftOfflineSupport.supportsPackageAbi("arm64-v8a"))
        assertFalse(MicrosoftOfflineSupport.supportsPackageAbi("x86_64"))
        assertNull(MicrosoftOfflineSupport.engineUnavailableReason(34, "aarch64", "graph"))
        assertEquals("离线语音包尚未准备完成",
            MicrosoftOfflineSupport.engineUnavailableReason(34, "aarch64", null))
        assertEquals(MicrosoftOfflineSupport.UNSUPPORTED_ARCH,
            MicrosoftOfflineSupport.engineUnavailableReason(34, "x86_64", "graph"))
    }

    @Test
    fun aStoredX86EngineIsUnavailableEvenWhenItsAbiMatchesTheProcess() {
        assertFalse(MicrosoftOfflineSupport.runtimeCompatible("x86_64", "x86_64", 34, 26, 4, listOf(4)))
        assertFalse(MicrosoftOfflineSupport.runtimeCompatible("x86_64", "arm64-v8a", 34, 26, 4, listOf(4)))
        assertFalse(MicrosoftOfflineSupport.runtimeCompatible("arm64-v8a", "x86_64", 34, 26, 4, listOf(4)))
        assertFalse(MicrosoftOfflineSupport.runtimeCompatible("arm64-v8a", "arm64-v8a", 34, 26, 16, listOf(4)))
        assertTrue(MicrosoftOfflineSupport.runtimeCompatible("arm64-v8a", "arm64-v8a", 34, 26, 4, listOf(4)))
    }
}
