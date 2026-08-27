package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningPlaybackCoordinatorTest {

    @Test
    fun startingReadAloudStopsOnlyARunningAudioSession() {
        assertEquals(
            ListeningPlaybackStop.AUDIO,
            requiredListeningPlaybackStop(
                target = ListeningPlaybackTarget.READ_ALOUD,
                readAloudRunning = true,
                audioRunning = true,
            )
        )
        assertNull(
            requiredListeningPlaybackStop(
                target = ListeningPlaybackTarget.READ_ALOUD,
                readAloudRunning = true,
                audioRunning = false,
            )
        )
    }

    @Test
    fun startingAudioStopsOnlyARunningReadAloudSession() {
        assertEquals(
            ListeningPlaybackStop.READ_ALOUD,
            requiredListeningPlaybackStop(
                target = ListeningPlaybackTarget.AUDIO,
                readAloudRunning = true,
                audioRunning = true,
            )
        )
        assertNull(
            requiredListeningPlaybackStop(
                target = ListeningPlaybackTarget.AUDIO,
                readAloudRunning = false,
                audioRunning = true,
            )
        )
    }
}
