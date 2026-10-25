package io.legado.app.help.bgm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmPreviewPolicyTest {
    @Test
    fun switchingTrackRejectsPreviousResolutionAndPlaybackCallbacks() {
        val gate = BgmPreviewRequestGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun stoppingOrReleasingCannotBeUndoneByLateCallbacks() {
        val gate = BgmPreviewRequestGate()
        val first = gate.begin()
        gate.stop()
        gate.stop()
        assertFalse(gate.isCurrent(first))
        val second = gate.begin()
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun auditionUsesConfiguredGainAndRejectsInvalidLoudness() {
        assertEquals(0.15f, previewGain(Float.NaN), 0f)
        assertEquals(0.15f, previewGain(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, previewGain(-1f), 0f)
        assertEquals(1f, previewGain(2f), 0f)
        assertEquals(0.08f, previewGain(0.08f), 0f)
    }

    @Test
    fun pausedServiceDoesNotSupplyFocusButResumingPreparationDoes() {
        val gate = BgmPreviewHostGate()
        val speech = BgmSpeechGate()
        assertFalse(gate.hostActive)
        gate.started()
        assertTrue(gate.hostActive)
        assertFalse(speech.canPlay(0)) // Reserving focus must not start saved BGM prematurely.
        gate.paused()
        assertFalse(gate.hostActive)
        gate.started()
        assertTrue(gate.hostActive)
    }

    @Test
    fun speechGapsAndRepeatedSegmentsKeepHostFocusUntilExplicitPause() {
        val gate = BgmPreviewHostGate()
        val speech = BgmSpeechGate()
        gate.started()
        speech.started()
        speech.waiting(0)
        assertFalse(speech.canPlay(8_000))
        assertTrue(gate.hostActive) // Long buffering pauses BGM, not the host's focus session.
        gate.started()
        speech.started()
        assertTrue(gate.hostActive)
        gate.paused()
        speech.pause()
        assertFalse(gate.hostActive)
    }
}
