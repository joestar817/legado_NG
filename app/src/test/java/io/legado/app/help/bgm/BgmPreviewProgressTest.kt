package io.legado.app.help.bgm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmPreviewProgressTest {
    @Test
    fun unknownDurationNeverEnablesSeekingOrInventsProgress() {
        val progress = auditionProgress(18_000, -9223372036854775807L, 135_000, true, true)
        assertEquals(0, progress.positionMs)
        assertEquals(0, progress.durationMs)
        assertEquals(135_000, progress.fullDurationMs)
        assertFalse(progress.canSeek)
        assertNull(progress.seekPosition(50_000))
    }

    @Test
    fun officialExcerptUsesActualAssetLengthAndClampsToThatLength() {
        val progress = auditionProgress(18_000, 60_000, 135_000, true, true)
        assertEquals(18_000, progress.positionMs)
        assertEquals(60_000, progress.durationMs)
        assertEquals(135_000, progress.fullDurationMs)
        assertTrue(progress.isPreview)
        assertEquals(60_000L, progress.seekPosition(120_000))
        assertEquals(0L, progress.seekPosition(-50))
    }

    @Test
    fun completeAudioUsesDecodedDurationInsteadOfStaleCatalogueDuration() {
        val progress = auditionProgress(90_000, 90_000, 120_000, false, true)
        assertEquals(90_000, progress.fullDurationMs)
        assertEquals(90_000L, progress.seekPosition(Long.MAX_VALUE))
        assertFalse(progress.isPreview)
    }

    @Test
    fun unavailableSeekAndShortCatalogueNeverCreateAnInvalidRange() {
        val progress = auditionProgress(80_000, 60_000, 45_000, true, false)
        assertEquals(60_000, progress.positionMs)
        assertEquals(60_000, progress.fullDurationMs)
        assertFalse(progress.canSeek)
        assertNull(progress.seekPosition(10_000))
    }
}
