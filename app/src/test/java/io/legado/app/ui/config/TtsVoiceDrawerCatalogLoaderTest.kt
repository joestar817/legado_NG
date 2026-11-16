package io.legado.app.ui.config

import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceDrawerCatalogLoaderTest {
    @Test
    fun onlyTheOpenedEmptyEngineIsFetchedAndRepeatedOpensAreDeduplicated() = runBlocking {
        var state = TtsVoiceDrawerState(loading = false, groups = listOf(
            group("first", needsFetch = true),
            group("second", needsFetch = true),
            group("cached", needsFetch = false),
        ))
        val requested = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val loader = TtsVoiceDrawerCatalogLoader(
            scope = this,
            readState = { state },
            writeState = { state = it },
            fetch = { id -> requested += id; gate.await() },
            loadGroup = { id -> group(id, needsFetch = false) },
        )

        assertTrue(requested.isEmpty())
        loader.open("cached")
        loader.open("second")
        loader.open("second")
        yield()
        assertEquals(listOf("second"), requested)
        assertFalse(state.groups[0].fetching)
        assertTrue(state.groups[1].fetching)
        gate.complete(Unit)
        loader.awaitPending()
        assertFalse(state.groups[1].needsFetch)
        assertFalse(state.groups[1].fetching)
        assertTrue(state.groups[0].needsFetch)
    }

    @Test
    fun failureWaitsForAnExplicitRetryAndDoesNotRefetchOnAnotherOpen() = runBlocking {
        var state = TtsVoiceDrawerState(loading = false, groups = listOf(group("empty", true)))
        var attempts = 0
        val loader = TtsVoiceDrawerCatalogLoader(
            scope = this,
            readState = { state },
            writeState = { state = it },
            fetch = { if (++attempts == 1) error("failed") },
            loadGroup = { group(it, needsFetch = false) },
        )

        loader.open("empty")
        loader.awaitPending()
        assertEquals(1, attempts)
        assertTrue(state.groups.single().fetchFailed)
        loader.open("empty")
        yield()
        assertEquals(1, attempts)
        loader.retry("empty")
        loader.awaitPending()
        assertEquals(2, attempts)
        assertFalse(state.groups.single().fetchFailed)
        assertFalse(state.groups.single().needsFetch)
    }

    @Test
    fun emptyCatalogCanFetchButDisabledVoicesAreNotRefetched() {
        val empty = TtsEngineSetting(
            id = "empty", name = "Empty", type = TtsEngineType.SCRIPT,
            script = "function voices() {}",
        )
        val disabled = empty.copy(
            id = "disabled",
            voices = listOf(TtsVoice(id = "voice", name = "Voice")),
            disabledVoiceIds = listOf("voice"),
        )

        val emptyGroup = empty.toVoiceDrawerGroup("System") { false }
        val disabledGroup = disabled.toVoiceDrawerGroup("System") { false }
        assertTrue(emptyGroup.needsFetch)
        assertFalse(disabledGroup.needsFetch)
        assertEquals("没有已启用的发音人", disabledGroup.emptyMessage)
    }

    private fun group(id: String, needsFetch: Boolean) = TtsVoiceDrawerGroup(
        engineId = id,
        engineName = id,
        cards = emptyList(),
        needsFetch = needsFetch,
    )
}
