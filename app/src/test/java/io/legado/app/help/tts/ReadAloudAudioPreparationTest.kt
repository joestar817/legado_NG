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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

class ReadAloudAudioPreparationTest {

    @Test
    fun playablePrefix_startsAsSoonAsFirstItemIsReady() {
        assertFalse(hasReadAloudPlayablePrefix(preparedItemCount = 0, totalItemCount = 8))
        assertTrue(hasReadAloudPlayablePrefix(preparedItemCount = 1, totalItemCount = 8))
    }

    @Test
    fun playablePrefix_doesNotRequireSecondOrThirdItem() {
        assertTrue(hasReadAloudPlayablePrefix(preparedItemCount = 1, totalItemCount = 3))
    }

    @Test
    fun playablePrefix_rejectsEmptyChapter() {
        assertFalse(hasReadAloudPlayablePrefix(preparedItemCount = 0, totalItemCount = 0))
    }

    @Test
    fun productionGap_existsOnlyWhileARequiredItemIsMissing() {
        assertTrue(hasReadAloudProductionGap(enqueuedItemCount = 1, totalItemCount = 3))
        assertFalse(hasReadAloudProductionGap(enqueuedItemCount = 3, totalItemCount = 3))
    }

    @Test
    fun seamlessChapterQueue_usesTheActualLastPageInsteadOfAStartupWindow() {
        assertEquals(9, readAloudWholeChapterPageEndIndex(pageCount = 10))
        assertEquals(1, readAloudWholeChapterPageEndIndex(pageCount = 2))
        assertNull(readAloudWholeChapterPageEndIndex(pageCount = 0))
    }

    @Test
    fun playbackCompletion_skipsTrailingPunctuationParagraph() {
        val paragraphs = listOf("正文", "……")

        assertEquals(
            paragraphs.size,
            readAloudPlaybackCompletionTarget(
                currentParagraphIndex = 0,
                paragraphCount = paragraphs.size,
                isSilent = { index -> isReadAloudSynthesisTextSilent(paragraphs[index]) }
            )
        )
    }

    @Test
    fun playbackCompletion_skipsAllTrailingSilentParagraphs() {
        val paragraphs = listOf("正文", "……", "......", "　")

        assertEquals(
            paragraphs.size,
            readAloudPlaybackCompletionTarget(
                currentParagraphIndex = 0,
                paragraphCount = paragraphs.size,
                isSilent = { index -> isReadAloudSynthesisTextSilent(paragraphs[index]) }
            )
        )
    }

    @Test
    fun playbackCompletion_stopsBeforeAnAudibleParagraph() {
        val paragraphs = listOf("正文", "下一段", "……")

        assertEquals(
            1,
            readAloudPlaybackCompletionTarget(
                currentParagraphIndex = 0,
                paragraphCount = paragraphs.size,
                isSilent = { index -> isReadAloudSynthesisTextSilent(paragraphs[index]) }
            )
        )
    }

    @Test
    fun playlistAppend_hasSinglePrepareOwnerForInitialStart() {
        assertEquals(
            ReadAloudPlaylistAppendAction.START,
            readAloudPlaylistAppendAction(
                resumeProductionGap = false,
                wasPlaylistEmpty = true,
                playbackIdle = true
            )
        )
        assertEquals(
            ReadAloudPlaylistAppendAction.NONE,
            readAloudPlaylistAppendAction(
                resumeProductionGap = false,
                wasPlaylistEmpty = false,
                playbackIdle = false
            )
        )
    }

    @Test
    fun playlistAppend_prioritizesGapResumeOverInitialStart() {
        assertEquals(
            ReadAloudPlaylistAppendAction.RESUME,
            readAloudPlaylistAppendAction(
                resumeProductionGap = true,
                wasPlaylistEmpty = true,
                playbackIdle = true
            )
        )
        assertEquals(
            ReadAloudPlaylistAppendAction.RESUME,
            readAloudPlaylistAppendAction(
                resumeProductionGap = true,
                wasPlaylistEmpty = false,
                playbackIdle = false
            )
        )
    }

    @Test
    fun playlistProduction_defersEndUntilAllItemsAreProduced() {
        val state = ReadAloudPlaylistProductionState()
        val token = state.begin()

        assertFalse(state.onPlaybackEnded())
        assertTrue(state.onItemAppended(token))
        assertFalse(state.finish(token))
        assertTrue(state.onPlaybackEnded())
    }

    @Test
    fun playlistProduction_finishesPendingEndWhenProducerCompletes() {
        val state = ReadAloudPlaylistProductionState()
        val token = state.begin()

        assertFalse(state.onPlaybackEnded())
        assertTrue(state.finish(token))
    }

    @Test
    fun playlistProduction_continuesSameGenerationForNextChapter() {
        val state = ReadAloudPlaylistProductionState()
        val token = state.begin()

        assertFalse(state.finish(token))
        assertTrue(state.continueProduction(token))
        assertFalse(state.onPlaybackEnded())
        assertTrue(state.onItemAppended(token))
        assertFalse(state.finish(token))
    }

    @Test
    fun playlistProduction_ignoresCallbacksFromCancelledGeneration() {
        val state = ReadAloudPlaylistProductionState()
        val stale = state.begin()
        val current = state.begin()

        assertFalse(state.isCurrent(stale))
        assertTrue(state.isCurrent(current))
        assertFalse(state.onItemAppended(stale))
        assertFalse(state.finish(stale))
        assertFalse(state.onItemAppended(current))
    }

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

    @Test
    fun ttsCacheClear_onlyDeletesTargetBookAndPreservesInProgressAudio() {
        val root = createTempDirectory("read-aloud-cache-").toFile()
        try {
            val currentBook = File(root, "current").apply { mkdirs() }
            val otherBook = File(root, "other").apply { mkdirs() }
            val completed = File(currentBook, "completed.mp3").apply { writeBytes(byteArrayOf(1)) }
            val inProgress = File(currentBook, "writing.mp3.123.part").apply {
                writeBytes(byteArrayOf(2))
            }
            val other = File(otherBook, "completed.mp3").apply { writeBytes(byteArrayOf(3)) }

            val removed = ReadAloudCacheManager.clearTtsAudioCache(
                directory = currentBook,
                preserveInProgress = true,
            )

            assertEquals(1, removed)
            assertFalse(completed.exists())
            assertTrue(inProgress.exists())
            assertTrue(other.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ttsCacheClear_removesPartialAudioWhenServiceIsStopped() {
        val root = createTempDirectory("read-aloud-cache-").toFile()
        try {
            val cache = File(root, "current").apply { mkdirs() }
            val inProgress = File(cache, "stale.mp3.123.part").apply {
                writeBytes(byteArrayOf(1))
            }

            val removed = ReadAloudCacheManager.clearTtsAudioCache(
                directory = cache,
                preserveInProgress = false,
            )

            assertEquals(1, removed)
            assertFalse(inProgress.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun wavValidation_detectsShortAudioThatEndsMidSpeech() {
        val folder = createTempDirectory("read-aloud-wav-").toFile()
        try {
            val target = File(folder, "truncated.wav").apply {
                writeBytes(pcmWav(durationMillis = 1_500, quietTailMillis = 0))
            }

            val issue = detectAbruptWavTruncation(
                target,
                "小欣，我不是故意瞒着你，我性格就是这样的，这次来也是真的和你吃个饭。"
            )

            assertNotNull(issue)
            assertTrue(requireNotNull(issue).tailRms > 300)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun wavValidation_keepsShortAudioWithAQuietSentenceTail() {
        val folder = createTempDirectory("read-aloud-wav-").toFile()
        try {
            val target = File(folder, "complete.wav").apply {
                writeBytes(pcmWav(durationMillis = 1_500, quietTailMillis = 150))
            }

            assertNull(
                detectAbruptWavTruncation(
                    target,
                    "小欣，我不是故意瞒着你，我性格就是这样的，这次来也是真的和你吃个饭。"
                )
            )
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun wavRetry_replacesAbruptResponseWithCompleteResponse() = runBlocking {
        val folder = createTempDirectory("read-aloud-wav-").toFile()
        try {
            val target = File(folder, "audio.wav")
            val attempts = AtomicInteger()
            val shortWav = pcmWav(durationMillis = 1_500, quietTailMillis = 0)
            val completeWav = pcmWav(durationMillis = 4_000, quietTailMillis = 150)

            writeReadAloudAudioWithWavRetry(
                target,
                "小欣，我不是故意瞒着你，我性格就是这样的，这次来也是真的和你吃个饭。"
            ) {
                ByteArrayInputStream(
                    if (attempts.incrementAndGet() == 1) shortWav else completeWav
                )
            }

            assertEquals(2, attempts.get())
            assertArrayEquals(completeWav, target.readBytes())
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

    private fun pcmWav(
        durationMillis: Int,
        quietTailMillis: Int,
        sampleRate: Int = 48_000,
        amplitude: Short = 4_000
    ): ByteArray {
        val sampleCount = sampleRate * durationMillis / 1_000
        val quietSamples = sampleRate * quietTailMillis / 1_000
        val dataSize = sampleCount * 2
        return ByteBuffer.allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray())
                putInt(36 + dataSize)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2.toShort())
                putShort(16.toShort())
                put("data".toByteArray())
                putInt(dataSize)
                repeat(sampleCount) { index ->
                    putShort(if (index >= sampleCount - quietSamples) 0.toShort() else amplitude)
                }
            }
            .array()
    }
}
