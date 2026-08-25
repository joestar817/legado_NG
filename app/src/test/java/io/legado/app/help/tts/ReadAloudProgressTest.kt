package io.legado.app.help.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudProgressTest {

    @Test
    fun mediaItemIdentity_roundTripsPlaybackSourceRange() {
        val identity = ReadAloudMediaItemIdentity(
            generation = 7,
            chapterIndex = 109,
            itemIndex = 4,
            paragraphIndex = 8,
            start = 12,
            end = 37
        )

        assertEquals(identity, parseReadAloudMediaItemIdentity(identity.toMediaId()))
    }

    @Test
    fun mediaItemIdentity_rejectsMalformedOrInvalidIdentity() {
        assertNull(parseReadAloudMediaItemIdentity("read-aloud:7:109:4:8:37:12"))
        assertNull(parseReadAloudMediaItemIdentity("read-aloud:old:109:4:8:12:37"))
        assertNull(parseReadAloudMediaItemIdentity("https://example.com/audio.mp3"))
    }

    @Test
    fun mediaItemIdentity_exposesStalePlaylistGeneration() {
        val production = ReadAloudPlaylistProductionState()
        val staleGeneration = production.begin()
        val currentGeneration = production.begin()
        val staleIdentity = parseReadAloudMediaItemIdentity(
            ReadAloudMediaItemIdentity(
                generation = staleGeneration,
                chapterIndex = 109,
                itemIndex = 0,
                paragraphIndex = 0,
                start = 0,
                end = 12
            ).toMediaId()
        )

        assertFalse(production.isCurrent(requireNotNull(staleIdentity).generation))
        assertTrue(production.isCurrent(currentGeneration))
    }

    @Test
    fun mediaItemTransition_syncsPlaylistChangeWhenCurrentIdentityAdvanced() {
        assertTrue(
            shouldSyncReadAloudMediaItemTransition(
                playlistChanged = true,
                previousItemIndex = 3,
                currentItemIndex = 4
            )
        )
        assertFalse(
            shouldSyncReadAloudMediaItemTransition(
                playlistChanged = true,
                previousItemIndex = 4,
                currentItemIndex = 4
            )
        )
    }

    @Test
    fun mediaItemTransition_handoffsOnlyToStagedNextChapter() {
        assertTrue(
            shouldHandoffReadAloudChapter(
                currentChapterIndex = 5,
                mediaChapterIndex = 6,
                stagedChapterIndex = 6
            )
        )
        assertTrue(
            shouldHandoffReadAloudChapter(
                currentChapterIndex = 6,
                mediaChapterIndex = 7,
                stagedChapterIndex = 7
            )
        )
        assertFalse(
            shouldHandoffReadAloudChapter(
                currentChapterIndex = 5,
                mediaChapterIndex = 7,
                stagedChapterIndex = 7
            )
        )
        assertFalse(
            shouldHandoffReadAloudChapter(
                currentChapterIndex = 5,
                mediaChapterIndex = 6,
                stagedChapterIndex = null
            )
        )
    }

    @Test
    fun seamlessHandoff_removesOnlyItemsBeforeCurrentChapter() {
        assertEquals(12, previousReadAloudChapterMediaCount(currentMediaItemIndex = 12))
        assertEquals(0, previousReadAloudChapterMediaCount(currentMediaItemIndex = 0))
    }

    @Test
    fun seamlessHandoff_acceptsOnlyThePreparedContinuousPrefix() {
        assertTrue(isReadAloudSeamlessPrefixReady(itemIndex = 0, preparedItemCount = 1))
        assertFalse(isReadAloudSeamlessPrefixReady(itemIndex = 1, preparedItemCount = 1))
        assertFalse(isReadAloudSeamlessPrefixReady(itemIndex = 0, preparedItemCount = 0))
    }

    @Test
    fun seamlessQueue_countsSourceItemsOnlyBeforeHandoff() {
        assertEquals(
            13,
            expectedReadAloudSeamlessMediaItemCount(
                sourceMediaItemCount = 10,
                preparedItemCount = 3,
                handedOff = false
            )
        )
        assertEquals(
            3,
            expectedReadAloudSeamlessMediaItemCount(
                sourceMediaItemCount = 10,
                preparedItemCount = 3,
                handedOff = true
            )
        )
    }

    @Test
    fun progress_restoreHandlesDuplicatePositionAfterPreparation() {
        assertTrue(
            shouldHandleReadAloudProgress(
                paragraphSeeking = false,
                lastProgress = 120,
                progress = 120,
                restoreSubtitle = true
            )
        )
        assertFalse(
            shouldHandleReadAloudProgress(
                paragraphSeeking = false,
                lastProgress = 120,
                progress = 120,
                restoreSubtitle = false
            )
        )
    }

    @Test
    fun progress_restoreDoesNotOverrideSeekPreview() {
        assertFalse(
            shouldHandleReadAloudProgress(
                paragraphSeeking = true,
                lastProgress = 120,
                progress = 120,
                restoreSubtitle = true
            )
        )
    }

    @Test
    fun subtitleProjection_restoresTextInsideSameParagraph() {
        assertTrue(
            shouldProjectReadAloudSubtitle(
                lastParagraphIndex = 8,
                currentSubtitle = "正在准备朗读…",
                nextParagraphIndex = 8,
                nextSubtitle = "齐夏继续向前走。"
            )
        )
        assertFalse(
            shouldProjectReadAloudSubtitle(
                lastParagraphIndex = 8,
                currentSubtitle = "齐夏继续向前走。",
                nextParagraphIndex = 8,
                nextSubtitle = "齐夏继续向前走。"
            )
        )
    }

    @Test
    fun preparedPlaylist_reusesMatchingChapterForNormalPlayback() {
        assertEquals(
            true,
            canReusePreparedReadAloudPlaylist(
                forceRebuild = false,
                playlistChapterIndex = 3,
                currentChapterIndex = 3,
                hasSpeakItems = true
            )
        )
    }

    @Test
    fun preparedPlaylist_rejectsReuseWhenVoiceSwitchForcesRebuild() {
        assertEquals(
            false,
            canReusePreparedReadAloudPlaylist(
                forceRebuild = true,
                playlistChapterIndex = 3,
                currentChapterIndex = 3,
                hasSpeakItems = true
            )
        )
    }

    @Test
    fun preparedPosition_usesParagraphStartAndSegmentEnd() {
        assertEquals(
            145,
            preparedReadAloudChapterPosition(
                paragraphStarts = listOf(0, 100, 220),
                paragraphIndex = 1,
                preparedEnd = 45
            )
        )
    }

    @Test
    fun preparedPosition_ignoresUnknownParagraph() {
        assertNull(
            preparedReadAloudChapterPosition(
                paragraphStarts = listOf(0, 100),
                paragraphIndex = 3,
                preparedEnd = 20
            )
        )
    }

    @Test
    fun preparedTarget_selectsBufferedItemWithoutRebuildingPlaylist() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 12),
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 12, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 3, start = 0, end = 18)
        )

        assertEquals(
            ReadAloudPreparedPlaybackTarget(itemIndex = 1, itemOffset = 0),
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 3
            )
        )
    }

    @Test
    fun preparedTarget_rejectsItemNotYetInPlayerQueue() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 0, start = 0, end = 10),
            ReadAloudPreparedItemRange(paragraphIndex = 1, start = 0, end = 20)
        )

        assertNull(
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 1,
                targetParagraphOffset = 0,
                mediaItemCount = 1
            )
        )
    }

    @Test
    fun preparedTarget_keepsOffsetInsidePreparedItem() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 3, start = 0, end = 18)
        )

        assertEquals(
            ReadAloudPreparedPlaybackTarget(itemIndex = 0, itemOffset = 12),
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedTarget_rejectsTargetBeforePreparedQueue() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 55, start = 0, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 56, start = 0, end = 22)
        )

        assertNull(
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 21,
                targetParagraphOffset = 0,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedTarget_rejectsTargetBeforeFirstSegmentInSameParagraph() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 8, start = 15, end = 30),
            ReadAloudPreparedItemRange(paragraphIndex = 8, start = 30, end = 45)
        )

        assertNull(
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 8,
                targetParagraphOffset = 5,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun preparedTarget_skipsGapToNextPreparedItem() {
        val ranges = listOf(
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 0, end = 10),
            ReadAloudPreparedItemRange(paragraphIndex = 2, start = 15, end = 30)
        )

        assertEquals(
            ReadAloudPreparedPlaybackTarget(itemIndex = 1, itemOffset = 0),
            preparedReadAloudPlaybackTarget(
                ranges = ranges,
                targetParagraphIndex = 2,
                targetParagraphOffset = 12,
                mediaItemCount = 2
            )
        )
    }

    @Test
    fun seekPosition_usesSameLinearCharacterRatioAsPlaybackProgress() {
        assertEquals(
            4_000L,
            readAloudSeekPositionMs(
                durationMs = 10_000L,
                itemLength = 30,
                itemOffset = 12
            )
        )
    }
}
