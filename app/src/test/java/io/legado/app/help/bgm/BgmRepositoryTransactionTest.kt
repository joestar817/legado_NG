package io.legado.app.help.bgm

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(
    application = BgmTransactionTestApplication::class,
    manifest = Config.NONE,
    sdk = [28],
    shadows = [BgmMetadataFixtureShadow::class],
)
class BgmRepositoryTransactionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val index get() = File(app.filesDir, "bgm/settings.json")
    private val audioDirectory get() = File(app.noBackupFilesDir, "bgm/audio")
    private val mutex get() = field<Mutex>("mutex")

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T = BgmRepository.javaClass.getDeclaredField(name).run {
        isAccessible = true
        get(BgmRepository) as T
    }

    @Before
    fun resetIsolatedSandbox() = runBlocking {
        withTimeout(5_000) { field<Deferred<*>>("ready").await() }
        drain()
        File(app.filesDir, "bgm").deleteRecursively()
        File(app.noBackupFilesDir, "bgm").deleteRecursively()
        field<MutableStateFlow<BgmSettings>>("mutableSettings").value = BgmSettings()
        BgmRepository.claimPlaybackOwner()
        Unit
    }

    @After
    fun finishPendingMutations() = runBlocking { drain() }

    @Test
    fun playbackProgressBelongsOnlyToTheLatestHostAndNeverTouchesSavedSettings() = runBlocking {
        assertTrue(BgmRepository.replaceQueue(listOf(track("one"))).await())
        val settingsBefore = BgmRepository.settings.value
        val bytesBefore = index.readBytes()
        val first = BgmRepository.claimPlaybackOwner()
        val progress = BgmPlaybackProgress(settingsBefore.currentTrack?.key,
            settingsBefore.selectionRevision, 1_000, 60_000, true, true)
        BgmRepository.publishProgress(first, progress)
        assertEquals(progress, BgmRepository.progress.value)

        val replacement = BgmRepository.claimPlaybackOwner()
        assertEquals(BgmPlaybackProgress(), BgmRepository.progress.value)
        BgmRepository.publishProgress(first, progress)
        BgmRepository.releasePlaybackOwner(first)
        assertTrue(BgmRepository.isPlaybackOwner(replacement))
        assertEquals(BgmPlaybackProgress(), BgmRepository.progress.value)
        BgmRepository.publishProgress(replacement, progress)
        BgmRepository.releasePlaybackOwner(replacement)
        assertEquals(BgmPlaybackProgress(), BgmRepository.progress.value)
        assertEquals(settingsBefore, BgmRepository.settings.value)
        assertArrayEquals(bytesBefore, index.readBytes())
    }

    @Test
    fun queuedAdvanceCannotChangeReplacementOwnersSelection() = runBlocking {
        BgmRepository.replaceQueue(listOf(track("one"), track("two")))
        drain()
        val owner = BgmRepository.claimPlaybackOwner()
        val before = BgmRepository.settings.value
        val oldIndex = index.readBytes()
        mutex.lock()
        val pending = try {
            BgmRepository.advanceIfCurrent(owner, before.selectionRevision).also {
                BgmRepository.claimPlaybackOwner()
            }
        } finally {
            mutex.unlock()
        }
        assertFalse(withTimeout(5_000) { pending.await() })
        assertEquals(before, BgmRepository.settings.value)
        assertArrayEquals(oldIndex, index.readBytes())
    }

    @Test
    fun queuedReplacementWinsOverOldRevisionsAdvance() = runBlocking {
        BgmRepository.replaceQueue(listOf(track("old-one"), track("old-two")))
        drain()
        val owner = BgmRepository.claimPlaybackOwner()
        val oldRevision = BgmRepository.settings.value.selectionRevision
        val replacement = listOf(track("new-one"), track("new-two"))
        mutex.lock()
        val pending = try {
            BgmRepository.replaceQueue(replacement)
            BgmRepository.advanceIfCurrent(owner, oldRevision)
        } finally {
            mutex.unlock()
        }
        assertFalse(withTimeout(5_000) { pending.await() })
        assertEquals(replacement, BgmRepository.settings.value.queue)
        assertEquals(0, BgmRepository.settings.value.index)
        assertEquals(oldRevision + 1, BgmRepository.settings.value.selectionRevision)
    }

    @Test
    fun failedIndexWriteDoesNotRemoveIndexedAudioOrOldIndex() = runBlocking {
        importFixture("existing")
        val before = BgmRepository.settings.value
        val existing = before.localTracks.single()
        val audio = File(audioDirectory, existing.localPath)
        val audioBytes = audio.readBytes()
        val indexBytes = index.readBytes()
        blockTemporaryIndex()

        BgmRepository.removeLocal(existing)
        drain()

        assertEquals(before, BgmRepository.settings.value)
        assertArrayEquals(indexBytes, index.readBytes())
        assertArrayEquals(audioBytes, audio.readBytes())
    }

    @Test
    fun copiedImportWhoseIndexWriteFailsIsRejectedAndRolledBack() = runBlocking {
        importFixture("existing") // Positive control: copy, fixture metadata and indexing all work.
        val before = BgmRepository.settings.value
        val indexBytes = index.readBytes()
        val audioNames = audioDirectory.list().orEmpty().toSet()
        blockTemporaryIndex()

        try {
            importFixture("rejected")
            fail("An unindexed copy must not count as a successful import")
        } catch (_: MusicProviderException) {
            // Expected public failure rather than an imported count of one.
        }

        assertEquals(before, BgmRepository.settings.value)
        assertArrayEquals(indexBytes, index.readBytes())
        assertEquals(audioNames, audioDirectory.list().orEmpty().toSet())
    }

    @Test
    fun batchAppendAndReorderPreserveCurrentSelection() = runBlocking {
        val one = track("one")
        val two = track("two")
        val three = track("three")
        assertTrue(BgmRepository.replaceQueue(listOf(one, two), 1).await())
        val revision = BgmRepository.settings.value.selectionRevision
        assertTrue(BgmRepository.addToQueue(listOf(one, three, three)).await())
        assertTrue(BgmRepository.moveInQueue(two.key, 2).await())
        val state = BgmRepository.settings.value
        assertEquals(listOf(one, three, two), state.queue)
        assertEquals(two, state.currentTrack)
        assertEquals(revision, state.selectionRevision)
        assertTrue(state.enabled)
    }

    @Test
    fun cancelledQueuedReplacementDoesNotChangeDiskOrSelection() = runBlocking {
        assertTrue(BgmRepository.replaceQueue(listOf(track("existing"))).await())
        val before = BgmRepository.settings.value
        val bytes = index.readBytes()
        val operation = BgmQueueOperation()
        mutex.lock()
        val pending = try {
            BgmRepository.replaceQueue(listOf(track("cancelled")), operation = operation).also {
                operation.cancel()
            }
        } finally {
            mutex.unlock()
        }
        assertFalse(withTimeout(5_000) { pending.await() })
        assertEquals(before, BgmRepository.settings.value)
        assertArrayEquals(bytes, index.readBytes())
    }

    @Test
    fun cancelledAppendCannotModifyExistingQueue() = runBlocking {
        assertTrue(BgmRepository.replaceQueue(listOf(track("existing"))).await())
        val before = BgmRepository.settings.value
        val operation = BgmQueueOperation().apply { cancel() }
        assertFalse(BgmRepository.addToQueue(listOf(track("cancelled")), operation).await())
        assertEquals(before, BgmRepository.settings.value)
    }

    @Test
    fun emptySelectionDoesNotReplaceExistingQueueAndEmptyQueueCannotBeEnabled() = runBlocking {
        assertTrue(BgmRepository.setEnabled(true).await())
        assertFalse(BgmRepository.settings.value.enabled)
        assertTrue(BgmRepository.replaceQueue(listOf(track("existing"))).await())
        val before = BgmRepository.settings.value
        assertFalse(BgmRepository.replaceQueue(emptyList()).await())
        assertEquals(before, BgmRepository.settings.value)
    }

    @Test
    fun importBatchDeduplicatesContentAndReportsIndividualFailure() = runBlocking {
        val original = File(app.cacheDir, "original.mp3").apply { writeText("same audio") }
        val renamed = File(app.cacheDir, "renamed.mp3").apply { writeText("same audio") }
        val empty = File(app.cacheDir, "empty.mp3").apply { writeBytes(byteArrayOf()) }
        val result = BgmRepository.importLocalBatch(listOf(original, renamed, empty).map(Uri::fromFile))
        assertEquals(1, result.imported)
        assertEquals(1, result.duplicates)
        assertEquals(1, result.failed)
        assertEquals("音频文件为空", result.failures.single().reason)
        assertFalse(result.failures.single().name.contains(app.cacheDir.absolutePath))
        assertEquals(1, BgmRepository.settings.value.localTracks.size)
        assertEquals(original.length(), BgmRepository.localStorageBytes())
        assertEquals(1, audioDirectory.listFiles().orEmpty().size)
        val repeated = BgmRepository.importLocalBatch(listOf(Uri.fromFile(renamed)))
        assertEquals(0, repeated.imported)
        assertEquals(1, repeated.duplicates)
        assertEquals(1, audioDirectory.listFiles().orEmpty().size)
    }

    @Test
    fun batchLocalRemovalKeepsOriginalsAndAdvancesToSurvivingSuccessor() = runBlocking {
        importFixture("first")
        importFixture("second")
        importFixture("third")
        val local = BgmRepository.settings.value.localTracks
        assertTrue(BgmRepository.replaceQueue(local, 1).await())
        assertTrue(BgmRepository.removeLocal(local.take(2)).await())
        val remaining = BgmRepository.settings.value
        assertEquals(listOf(local[2]), remaining.localTracks)
        assertEquals(listOf(local[2]), remaining.queue)
        assertEquals(local[2], remaining.currentTrack)
        assertTrue(remaining.enabled)
        assertEquals(1, audioDirectory.listFiles().orEmpty().size)
        assertTrue(File(app.cacheDir, "first.mp3").isFile)
        assertTrue(File(app.cacheDir, "second.mp3").isFile)
    }

    @Test
    fun duplicateDetectionReadsOlderCopiesWithoutRewritingTheirIdentity() = runBlocking {
        importFixture("existing")
        val current = BgmRepository.settings.value
        val hashed = current.localTracks.single()
        val older = hashed.copy(localPath = "${hashed.id}.audio")
        assertTrue(File(audioDirectory, hashed.localPath).renameTo(File(audioDirectory, older.localPath)))
        field<MutableStateFlow<BgmSettings>>("mutableSettings").value = current.copy(localTracks = listOf(older))
        val result = BgmRepository.importLocalBatch(listOf(Uri.fromFile(File(app.cacheDir, "existing.mp3"))))
        assertEquals(1, result.duplicates)
        assertEquals(0, result.imported)
        assertEquals(listOf(older), BgmRepository.settings.value.localTracks)
        assertEquals(1, audioDirectory.listFiles().orEmpty().size)
    }

    @Test
    fun removalUsesIndexedPathRatherThanCallerSuppliedFile() = runBlocking {
        importFixture("first")
        importFixture("second")
        val tracks = BgmRepository.settings.value.localTracks
        val forgedSelection = tracks[0].copy(localPath = tracks[1].localPath)
        assertTrue(BgmRepository.removeLocal(listOf(forgedSelection)).await())
        assertEquals(listOf(tracks[1]), BgmRepository.settings.value.localTracks)
        assertTrue(File(audioDirectory, tracks[1].localPath).isFile)
        assertFalse(File(audioDirectory, tracks[0].localPath).exists())
    }

    private suspend fun importFixture(name: String) {
        val file = File(app.cacheDir, "$name.mp3").apply { writeText(name) }
        assertEquals(1, BgmRepository.importLocal(listOf(Uri.fromFile(file))))
    }

    private fun blockTemporaryIndex() {
        // Portable write failure, without relying on OS-specific chmod or open-file rename rules.
        val temporary = File(index.parentFile, "settings.json.tmp")
        assertTrue(temporary.mkdir())
        File(temporary, "keep").writeText("occupy the staging path")
    }

    private suspend fun drain() {
        // An always-rejected queued mutation provides a completion fence without changing settings.
        assertFalse(withTimeout(5_000) { BgmRepository.advanceIfCurrent(-1, -1).await() })
    }

    private fun track(id: String) = MusicTrack(id, MusicPlatform.NETEASE, id)
}

class BgmTransactionTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}

/** Only metadata is faked; the actual ContentResolver copy and repository disk transaction run. */
@Implements(MediaMetadataRetriever::class)
class BgmMetadataFixtureShadow {
    @Implementation fun __constructor__() = Unit
    @Implementation fun setDataSource(path: String) = Unit
    @Implementation fun extractMetadata(key: Int): String? =
        if (key == MediaMetadataRetriever.METADATA_KEY_DURATION) "1000" else null
    @Implementation fun release() = Unit
}
