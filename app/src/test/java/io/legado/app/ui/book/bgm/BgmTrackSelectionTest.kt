package io.legado.app.ui.book.bgm

import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.help.bgm.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BgmTrackSelectionTest {
    private fun track(id: String) = MusicTrack(id, MusicPlatform.QQ, id)
    private fun selected(vararg ids: String) = ids.map(::track).associateBy(MusicTrack::key)
    private fun ids(result: BgmTrackSelectionResult) = result.tracks.values.map(MusicTrack::id)

    @Test fun selectingAllCompletesVisibleResultsAndKeepsHiddenSelection() {
        val result = selectBgmTracks(selected("hidden", "b"), listOf(track("a"), track("b"), track("c")),
            BgmTrackSelectionAction.TOGGLE_ALL)
        assertEquals(listOf("hidden", "b", "a", "c"), ids(result))
        assertFalse(result.limitReached)
    }

    @Test fun selectingAllAgainClearsOnlyVisibleResults() {
        val result = selectBgmTracks(selected("hidden", "a", "b"), listOf(track("a"), track("b")),
            BgmTrackSelectionAction.TOGGLE_ALL)
        assertEquals(listOf("hidden"), ids(result))
        assertFalse(result.limitReached)
    }

    @Test fun inversionKeepsHiddenSelectionAndAddsInResultOrder() {
        val result = selectBgmTracks(selected("hidden", "b"), listOf(track("c"), track("b"), track("a")),
            BgmTrackSelectionAction.INVERT)
        assertEquals(listOf("hidden", "c", "a"), ids(result))
        assertFalse(result.limitReached)
    }

    @Test fun neitherActionSelectsSongsAlreadyInDestination() {
        BgmTrackSelectionAction.entries.forEach { action ->
            val result = selectBgmTracks(selected("hidden", "existing"),
                listOf(track("existing"), track("new")), action, excludedIds = setOf("existing"))
            assertEquals(listOf("hidden", "new"), ids(result))
            assertFalse(result.limitReached)
        }
    }

    @Test fun selectAllCancelsWhenOnlyEligibleCandidatesAreSelected() {
        val result = selectBgmTracks(selected("hidden", "a"), listOf(track("existing"), track("a")),
            BgmTrackSelectionAction.TOGGLE_ALL, excludedIds = setOf("existing"))
        assertEquals(listOf("hidden"), ids(result))
    }

    @Test fun additionLimitIncludesSongsSelectedInOtherSearches() {
        val hidden = (0 until 99).map { track("hidden-$it") }.associateBy(MusicTrack::key)
        BgmTrackSelectionAction.entries.forEach { action ->
            val result = selectBgmTracks(hidden, listOf(track("first"), track("second")), action, limit = 100)
            assertEquals(100, result.tracks.size)
            assertEquals(hidden.keys.toList(), result.tracks.keys.take(99))
            assertEquals("first", ids(result).last())
            assertTrue(result.limitReached)
        }
    }

    @Test fun inversionReleasesVisibleSlotsBeforeFillingThem() {
        val current = (0 until 98).map { track("hidden-$it") }.associateBy(MusicTrack::key) + selected("a", "b")
        val result = selectBgmTracks(current,
            listOf(track("c"), track("a"), track("d"), track("b"), track("e")),
            BgmTrackSelectionAction.INVERT, limit = 100)
        assertEquals(100, result.tracks.size)
        assertEquals(listOf("c", "d"), ids(result).takeLast(2))
        assertFalse(track("a").key in result.tracks)
        assertFalse(track("b").key in result.tracks)
        assertTrue(result.limitReached)
    }

    @Test fun fillingExactlyToLimitDoesNotReportOverflow() {
        val current = (0 until 99).map { track("hidden-$it") }.associateBy(MusicTrack::key)
        val result = selectBgmTracks(current, listOf(track("a")), BgmTrackSelectionAction.TOGGLE_ALL, limit = 100)
        assertEquals(100, result.tracks.size)
        assertFalse(result.limitReached)
    }

    @Test fun emptyAndExcludedCandidatesDoNotAffectHiddenSelection() {
        BgmTrackSelectionAction.entries.forEach { action ->
            val empty = selectBgmTracks(selected("hidden"), emptyList(), action, limit = 100)
            assertEquals(listOf("hidden"), ids(empty))
            val excluded = selectBgmTracks(selected("hidden"), listOf(track("existing")), action,
                excludedIds = setOf("existing"), limit = 100)
            assertEquals(listOf("hidden"), ids(excluded))
            assertFalse(empty.limitReached)
            assertFalse(excluded.limitReached)
        }
    }

    @Test fun duplicateResultsAreHandledOnceAndKeepFirstOccurrenceOrder() {
        BgmTrackSelectionAction.entries.forEach { action ->
            val result = selectBgmTracks(selected("hidden"), listOf(track("b"), track("a"), track("b")),
                action, limit = 3)
            assertEquals(listOf("hidden", "b", "a"), ids(result))
            assertFalse(result.limitReached)
        }
    }

    @Test fun ordinarySelectionHasNoPlaylistAdditionLimit() {
        val tracks = (0 until 138).map { track(it.toString()) }
        val result = selectBgmTracks(emptyMap(), tracks, BgmTrackSelectionAction.TOGGLE_ALL)
        assertEquals(tracks, result.tracks.values.toList())
        assertFalse(result.limitReached)
    }
}
