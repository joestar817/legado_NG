package io.legado.app.ui.book.read.aloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningCapsulePolicyTest {

    @Test
    fun readerIgnoresMainPagePreferenceAndOtherPagesNeverAttach() {
        assertTrue(
            ListeningCapsulePolicy.shouldAttach(
                ListeningCapsuleHost.READER,
                showOnMain = false,
            )
        )
        assertFalse(
            ListeningCapsulePolicy.shouldAttach(
                ListeningCapsuleHost.MAIN,
                showOnMain = false,
            )
        )
        assertTrue(
            ListeningCapsulePolicy.shouldAttach(
                ListeningCapsuleHost.MAIN,
                showOnMain = true,
            )
        )
        assertFalse(
            ListeningCapsulePolicy.shouldAttach(
                ListeningCapsuleHost.OTHER,
                showOnMain = true,
            )
        )
    }

    @Test
    fun readerOnlyRepresentsTextReadAloud() {
        assertNull(
            resolve(
                host = ListeningCapsuleHost.READER,
                readAloudRunning = false,
                audioRunning = true,
                audioPlaying = true,
            )
        )
        assertEquals(
            ListeningPlayback.READ_ALOUD,
            resolve(
                host = ListeningCapsuleHost.READER,
                readAloudRunning = true,
                audioRunning = true,
                audioPlaying = true,
            )
        )
    }

    @Test
    fun mainPageUsesTheActuallyPlayingSessionBeforeAStalePreference() {
        assertEquals(
            ListeningPlayback.AUDIO,
            resolve(
                readAloudRunning = true,
                audioRunning = true,
                audioPlaying = true,
                preferred = ListeningPlayback.READ_ALOUD,
            )
        )
        assertEquals(
            ListeningPlayback.READ_ALOUD,
            resolve(
                readAloudRunning = true,
                readAloudPlaying = true,
                audioRunning = true,
                preferred = ListeningPlayback.AUDIO,
            )
        )
        assertEquals(
            ListeningPlayback.READ_ALOUD,
            resolve(
                readAloudRunning = true,
                audioPlaying = true,
            )
        )
    }

    @Test
    fun mainPageFallsBackToTheRemainingOrPreferredPausedSession() {
        assertEquals(
            ListeningPlayback.AUDIO,
            resolve(
                readAloudRunning = true,
                audioRunning = true,
                preferred = ListeningPlayback.AUDIO,
            )
        )
        assertEquals(
            ListeningPlayback.AUDIO,
            resolve(audioRunning = true)
        )
        assertEquals(
            ListeningPlayback.READ_ALOUD,
            resolve(readAloudRunning = true)
        )
        assertNull(resolve())
    }

    private fun resolve(
        host: ListeningCapsuleHost = ListeningCapsuleHost.MAIN,
        readAloudRunning: Boolean = false,
        readAloudPlaying: Boolean = false,
        audioRunning: Boolean = false,
        audioPlaying: Boolean = false,
        preferred: ListeningPlayback? = null,
    ): ListeningPlayback? {
        return ListeningCapsulePolicy.resolvePlayback(
            host = host,
            readAloudRunning = readAloudRunning,
            readAloudPlaying = readAloudPlaying,
            audioRunning = audioRunning,
            audioPlaying = audioPlaying,
            preferred = preferred,
        )
    }
}
