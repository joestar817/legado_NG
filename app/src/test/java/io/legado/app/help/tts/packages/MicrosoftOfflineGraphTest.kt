package io.legado.app.help.tts.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MicrosoftOfflineGraphTest {
    @Test fun graphRoundTripsAndHasStableOrder() {
        val graph = linkedMapOf(
            "io.legado.prototype.microsoft.voice.test" to "1".repeat(64),
            MicrosoftTtsPackageStore.ENGINE to "0".repeat(64),
        )
        val revision = encodeMicrosoftOfflineGraph(graph)
        assertEquals(graph, decodeMicrosoftOfflineGraph(revision))
        assertEquals(revision, encodeMicrosoftOfflineGraph(graph.toSortedMap()))
    }

    @Test fun graphRejectsMissingEngineDuplicateAndUnsafeIds() {
        val engine = MicrosoftTtsPackageStore.ENGINE + ":" + "0".repeat(64)
        assertThrows(IllegalArgumentException::class.java) { decodeMicrosoftOfflineGraph("voice:" + "1".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { decodeMicrosoftOfflineGraph("$engine|$engine") }
        assertThrows(IllegalArgumentException::class.java) { decodeMicrosoftOfflineGraph("$engine|../voice:" + "1".repeat(64)) }
    }
}
