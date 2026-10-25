package io.legado.app.help.bgm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LocalAudioPreparationTest {
    @Test fun slowFilesAndFailuresDoNotReorderResultsOrExceedTheReadLimit() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val received = ArrayList<Int>()
        prepareLocalAudioInOrder((0 until 12).toList(), prepare = { index ->
            val running = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, running) }
            try {
                delay(if (index == 0) 80 else 5)
                if (index == 5) error("unreadable file")
                index
            } finally { active.decrementAndGet() }
        }) { index, result ->
            received += index
            if (index == 5) assertTrue(result.isFailure) else assertEquals(index, result.getOrThrow())
        }
        assertEquals((0 until 12).toList(), received)
        assertTrue(peak.get() in 2..4)
        assertEquals(0, active.get())
    }

    @Test fun stoppingKeepsOnlyConsumedResultsAndReleasesPrefetchedWork() = runBlocking {
        val active = AtomicInteger()
        val received = ArrayList<Int>()
        try {
            prepareLocalAudioInOrder((0 until 20).toList(), prepare = { index ->
                active.incrementAndGet()
                try { delay(if (index < 2) 5 else 5000); index }
                finally { active.decrementAndGet() }
            }) { index, result ->
                received += result.getOrThrow()
                if (index == 1) throw LocalAudioImportStopped()
            }
            fail("stop must reach the batch owner")
        } catch (_: LocalAudioImportStopped) { }
        assertEquals(listOf(0, 1), received)
        assertEquals(0, active.get())
    }
}
