package io.legado.app.help.bgm

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MusicPlaylistMembershipTest {
    private fun track(id: String) = MusicTrack(id, MusicPlatform.QQ, id)

    @Test fun readsEveryPageAndDeduplicatesIds() = runBlocking {
        val offsets = mutableListOf<Int>()
        val ids = collectPlaylistTrackIds { offset ->
            offsets += offset
            if (offset == 0) MusicPage(listOf(track("a"), track("b")), 2, 4)
            else MusicPage(listOf(track("b"), track("c")), total = 4)
        }
        assertEquals(listOf(0, 2), offsets)
        assertEquals(setOf("a", "b", "c"), ids)
    }

    @Test fun doesNotTreatTruncatedReadAsComplete() = runBlocking {
        try {
            collectPlaylistTrackIds { MusicPage(listOf(track("a")), total = 3) }
            fail("Expected incomplete membership to fail")
        } catch (_: MusicProviderException) { }
    }

    @Test fun rejectsNonAdvancingPages() = runBlocking {
        try {
            collectPlaylistTrackIds { MusicPage(listOf(track("a")), nextOffset = 0) }
            fail("Expected repeated offset to fail")
        } catch (_: MusicProviderException) { }
    }

    @Test fun cancellationStopsBeforeTheNextPage() = runBlocking {
        var calls = 0
        val job = launch {
            collectPlaylistTrackIds {
                calls++
                currentCoroutineContext().cancel()
                MusicPage(listOf(track("a")), nextOffset = 1)
            }
        }
        job.join()
        assertEquals(1, calls)
    }
}
