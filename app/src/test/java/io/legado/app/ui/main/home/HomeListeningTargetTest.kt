package io.legado.app.ui.main.home

import io.legado.app.data.entities.Book
import io.legado.app.model.ListeningHistoryEntry
import io.legado.app.model.ListeningHistorySource
import io.legado.app.ui.book.read.aloud.ListeningPlayback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeListeningTargetTest {
    private val entry = ListeningHistoryEntry(ListeningHistorySource.READ_ALOUD, "book", 1, 135)
    private val history = HomeListeningState(
        book = Book(bookUrl = "book"), bookUrl = "book",
        playback = ListeningPlayback.READ_ALOUD, history = entry,
    )

    @Test
    fun theSameHistoricalTargetIsAccepted() {
        assertTrue(matchesHomeListeningTarget(history, history.copy()))
    }

    @Test
    fun startingALiveSessionInTheSameBookInvalidatesHistoricalControls() {
        val live = history.copy(history = null)
        assertFalse(matchesHomeListeningTarget(history, live))
        assertFalse(matchesHomeListeningTarget(live, history))
        assertTrue(matchesHomeListeningTarget(live, live.copy(playing = true)))
    }

    @Test
    fun changingTheBookmarkSourceBookOrRemovingMetadataInvalidatesControls() {
        assertFalse(matchesHomeListeningTarget(history, history.copy(history = entry.copy(position = 150))))
        assertFalse(matchesHomeListeningTarget(history, history.copy(playback = ListeningPlayback.AUDIO)))
        assertFalse(matchesHomeListeningTarget(history, history.copy(bookUrl = "other")))
        assertFalse(matchesHomeListeningTarget(history, history.copy(book = null)))
    }
}
