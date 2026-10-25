package io.legado.app.help.bgm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmPlaybackProgressTest {
    private fun progress(
        position: Long = 12_000,
        duration: Long = 60_000,
        seekable: Boolean = true,
        paused: Boolean = false,
    ) = formalBgmProgress("NETEASE:one", 7, position, duration, seekable, true, paused)

    @Test
    fun delayedSeekCannotTargetAnotherTrackOrAnotherSelectionOfTheSameTrack() {
        val current = progress()
        assertNull(current.seekPosition(20_000, "NETEASE:two", 7))
        assertNull(current.seekPosition(20_000, "NETEASE:one", 6))
        assertEquals(20_000L, current.seekPosition(20_000, "NETEASE:one", 7))
    }

    @Test
    fun unknownMediaDurationAndNonSeekableSourcesNeverUseCatalogueDuration() {
        val unknown = progress(duration = Long.MIN_VALUE + 1)
        assertEquals(0L, unknown.durationMs)
        assertEquals(0L, unknown.positionMs)
        assertFalse(unknown.canSeek)
        assertNull(unknown.seekPosition(20_000, "NETEASE:one", 7))
        assertFalse(progress(seekable = false).canSeek)
        assertNull(progress(seekable = false).seekPosition(20_000, "NETEASE:one", 7))
    }

    @Test
    fun pausedSeekClampsToActualMediaAndRemainsReversibleAtTheEnd() {
        val paused = progress(paused = true)
        assertEquals(0L, paused.seekPosition(-1, "NETEASE:one", 7))
        assertEquals(60_000L, paused.seekPosition(Long.MAX_VALUE, "NETEASE:one", 7))
        val atEnd = progress(position = 60_000, paused = true)
        assertEquals(20_000L, atEnd.seekPosition(20_000, "NETEASE:one", 7))
        assertTrue(atEnd.pausedByUser)
    }

    @Test
    fun invalidPlayerPositionsAreClampedAndMissingTrackHasNoControls() {
        assertEquals(0L, progress(position = -5).positionMs)
        assertEquals(60_000L, progress(position = 80_000).positionMs)
        val empty = formalBgmProgress(null, 7, 0, 60_000, true, true, false)
        assertFalse(empty.canSeek)
        assertFalse(empty.canTogglePause)
    }

    @Test
    fun endAfterPausedSeekOrPrimarySpeechWaitNeverAdvancesTheQueue() {
        assertFalse(canAdvanceBgmAtEnd(false, true, false))
        assertFalse(canAdvanceBgmAtEnd(true, false, false))
        assertFalse(canAdvanceBgmAtEnd(true, true, true))
        assertTrue(canAdvanceBgmAtEnd(true, true, false))
    }
}
