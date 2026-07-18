package io.legado.app.help.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

class ReadAloudAudioPreparationTest {

    @Test
    fun scheduler_skipsSaturatedEngineAndKeepsPlaybackOrder() = runBlocking {
        val firstAStarted = CompletableDeferred<Unit>()
        val firstARelease = CompletableDeferred<Unit>()
        val bStarted = CompletableDeferred<Unit>()
        val started = Collections.synchronizedList(mutableListOf<String>())
        val delivered = mutableListOf<String>()
        val tasks = listOf(
            task("a1", "A", 1) {
                started += "a1"
                firstAStarted.complete(Unit)
                firstARelease.await()
                "a1"
            },
            task("a2", "A", 1) {
                started += "a2"
                "a2"
            },
            task("b1", "B", 1) {
                started += "b1"
                bStarted.complete(Unit)
                "b1"
            }
        )

        val job = launch {
            prepareReadAloudAudioTasks(tasks, globalConcurrency = 2) { delivered += it }
        }
        firstAStarted.await()
        withTimeout(1_000) { bStarted.await() }
        assertEquals(listOf("a1", "b1"), started.toList())

        firstARelease.complete(Unit)
        job.join()

        assertEquals(listOf("a1", "a2", "b1"), delivered)
    }

    @Test
    fun scheduler_appliesGlobalAndPerEngineLimits() = runBlocking {
        val globalActive = AtomicInteger()
        val globalPeak = AtomicInteger()
        val activeA = AtomicInteger()
        val peakA = AtomicInteger()
        val activeB = AtomicInteger()
        val peakB = AtomicInteger()

        suspend fun prepare(engine: String): String {
            val engineActive = if (engine == "A") activeA else activeB
            val enginePeak = if (engine == "A") peakA else peakB
            updatePeak(globalPeak, globalActive.incrementAndGet())
            updatePeak(enginePeak, engineActive.incrementAndGet())
            try {
                delay(40)
                return engine
            } finally {
                engineActive.decrementAndGet()
                globalActive.decrementAndGet()
            }
        }

        val tasks = listOf(
            task("a1", "A", 1) { prepare("A") },
            task("a2", "A", 1) { prepare("A") },
            task("b1", "B", 2) { prepare("B") },
            task("b2", "B", 2) { prepare("B") },
            task("b3", "B", 2) { prepare("B") }
        )

        prepareReadAloudAudioTasks(tasks, globalConcurrency = 3)

        assertEquals(3, globalPeak.get())
        assertEquals(1, peakA.get())
        assertEquals(2, peakB.get())
    }

    @Test
    fun scheduler_deduplicatesCacheButDeliversEveryItem() = runBlocking {
        val executions = AtomicInteger()
        val delivered = mutableListOf<String>()
        val duplicate = task("same", "A", 2) {
            executions.incrementAndGet()
            "audio"
        }

        prepareReadAloudAudioTasks(listOf(duplicate, duplicate), 2) { delivered += it }

        assertEquals(1, executions.get())
        assertEquals(listOf("audio", "audio"), delivered)
    }

    @Test
    fun scheduler_cancelsActiveEngineTask() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean()
        val task = task("a1", "A", 1) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.set(true)
            }
        }

        val job = launch { prepareReadAloudAudioTasks(listOf(task), 1) }
        started.await()
        job.cancelAndJoin()

        assertTrue(cancelled.get())
    }

    @Test
    fun atomicWriter_publishesOnlyCompleteAudio() = runBlocking {
        val folder = createTempDirectory("read-aloud-audio-").toFile()
        try {
            val target = File(folder, "audio.mp3")
            val bytes = byteArrayOf(1, 2, 3, 4)

            writeReadAloudAudioAtomically(target, ByteArrayInputStream(bytes))

            assertArrayEquals(bytes, target.readBytes())
            assertTrue(folder.listFiles().orEmpty().none { it.extension == "part" })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun atomicWriter_removesPartialFileOnFailure() = runBlocking {
        val folder = createTempDirectory("read-aloud-audio-").toFile()
        try {
            val target = File(folder, "audio.mp3")
            val brokenInput = object : InputStream() {
                var emitted = false
                override fun read(): Int {
                    if (!emitted) {
                        emitted = true
                        return 1
                    }
                    throw IOException("broken")
                }
            }

            assertThrows(IOException::class.java) {
                runBlocking { writeReadAloudAudioAtomically(target, brokenInput) }
            }

            assertFalse(target.exists())
            assertTrue(folder.listFiles().orEmpty().none { it.extension == "part" })
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun <T> task(
        cacheKey: String,
        engineKey: String,
        maxConcurrency: Int,
        prepare: suspend () -> T
    ) = ReadAloudAudioTask(cacheKey, engineKey, maxConcurrency, prepare)

    private fun updatePeak(peak: AtomicInteger, value: Int) {
        while (true) {
            val current = peak.get()
            if (value <= current || peak.compareAndSet(current, value)) return
        }
    }
}
