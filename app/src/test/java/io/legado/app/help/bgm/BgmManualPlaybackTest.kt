package io.legado.app.help.bgm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmManualPlaybackTest {
    @Test
    fun openingOrPreparingListeningDoesNotStartMusicWithoutAnExplicitPlay() {
        val gate = BgmSpeechGate()
        gate.waiting(0)
        assertFalse(gate.canPlay(0))
        gate.playManually()
        assertTrue(gate.canPlay(0))
        assertFalse(gate.hasSpeech)
    }

    @Test
    fun manualMusicDoesNotExpireWithTheSpeechBufferingGrace() {
        val gate = BgmSpeechGate()
        gate.started()
        gate.waiting(100)
        assertFalse(gate.canPlay(8_100))
        gate.playManually()
        assertTrue(gate.canPlay(20_000))
    }

    @Test
    fun primaryPauseStillStopsManualMusicAndActualSpeechResumesNormalControl() {
        val gate = BgmSpeechGate()
        gate.playManually()
        gate.pause()
        assertFalse(gate.manualPlayback)
        assertFalse(gate.canPlay(1_000))
        gate.started()
        assertTrue(gate.canPlay(1_001))
        assertFalse(gate.manualPlayback)
    }

    @Test
    fun startingListeningHandsBackControlWithoutPretendingSpeechAlreadyStarted() {
        val gate = BgmSpeechGate()
        gate.playManually()
        gate.primaryStarting()
        assertFalse(gate.canPlay(10))
        gate.started()
        assertTrue(gate.canPlay(11))
    }
}
