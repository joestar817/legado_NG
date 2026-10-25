package io.legado.app.help.bgm

import android.net.Uri
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LocalAudioPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun discoveryAcceptsCaseAndMimeHintsWithoutPromisingEncryptedOrUnsupportedContainers() {
        for (name in listOf("Song.MP3", "Song.FLAC", "雨声.opus", "rain.oga", "voice.amr", "clip.3gp")) {
            assertTrue(name, LocalAudioFormat.isCandidate(name))
        }
        assertTrue(LocalAudioFormat.isCandidate("无后缀", "audio/mpeg"))
        assertTrue(LocalAudioFormat.isCandidate("song.mp3", "application/octet-stream"))
        for (name in listOf("song.ncm", "song.qmcflac", "song.kgm", "archive.zip", "album.cue", "video.mp4", "song.ape")) {
            assertFalse(name, LocalAudioFormat.isCandidate(name, "audio/mpeg"))
        }
        assertFalse(LocalAudioFormat.isCandidate("cover.jpg", "image/jpeg"))
    }

    @Test fun codecsAreCheckedSeparatelyFromExtensions() {
        assertTrue("audio/flac" in LocalAudioFormat.audioMimes)
        assertTrue("audio/mp4a-latm" in LocalAudioFormat.audioMimes)
        assertFalse("audio/alac" in LocalAudioFormat.audioMimes)
        assertFalse("video/avc" in LocalAudioFormat.audioMimes)
        assertTrue(LocalAudioFormat.isRecording("录音.AMR"))
        assertFalse(LocalAudioFormat.isRecording("short-loop.wav"))
    }

    @Test fun mp3FastProbeRequiresRealFramesAndCompleteTags() {
        fun tag(id: String, value: String): ByteArray {
            val bytes = byteArrayOf(3) + value.toByteArray(Charsets.UTF_8)
            return id.toByteArray(Charsets.US_ASCII) +
                java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array() + byteArrayOf(0, 0) + bytes
        }
        fun audio(tags: ByteArray): ByteArray {
            val size = tags.size
            val id3 = byteArrayOf(73, 68, 51, 3, 0, 0,
                ((size shr 21) and 127).toByte(), ((size shr 14) and 127).toByte(),
                ((size shr 7) and 127).toByte(), (size and 127).toByte())
            val frame = ByteArray(417).apply { this[0] = 0xff.toByte(); this[1] = 0xfb.toByte(); this[2] = 0x90.toByte() }
            return id3 + tags + (0 until 5).fold(byteArrayOf()) { bytes, _ -> bytes + frame }
        }
        val tags = tag("TIT2", "雨声") + tag("TPE1", "测试歌手") + tag("TALB", "测试专辑") + tag("TLEN", "130")
        val valid = audio(tags)
        val result = LocalMp3Probe.read(valid.inputStream(), valid.size.toLong()) { }
        assertNotNull(result)
        assertEquals("雨声", result!!.tags.title.toString())
        assertEquals("audio/mpeg", result.format.sampleMimeType)
        assertTrue(result.durationMs > 0)
        val incomplete = audio(tag("TIT2", "ID3v1 fallback needed"))
        assertNull(LocalMp3Probe.read(incomplete.inputStream(), incomplete.size.toLong()) { })
        val renamedVideo = "\u0000\u0000\u0000\u0018ftyp3gp4rest".toByteArray()
        assertNull(LocalMp3Probe.read(renamedVideo.inputStream(), renamedVideo.size.toLong()) { })
        val corrupt = valid.copyOf(10 + tags.size + 4)
        assertNull(runCatching { LocalMp3Probe.read(corrupt.inputStream(), corrupt.size.toLong()) { } }.getOrNull())
    }

    @Test fun fileReferencesCannotEscapeThroughParentTraversalOrSiblingPrefixes() {
        val root = temporary.newFolder("music")
        val rootUri = Uri.fromFile(root)
        assertTrue(LocalAudioAccess.belongsToRoot(Uri.fromFile(File(root, "album/track.mp3")), rootUri))
        assertFalse(LocalAudioAccess.belongsToRoot(Uri.fromFile(File(root, "../private.mp3")), rootUri))
        assertFalse(LocalAudioAccess.belongsToRoot(Uri.fromFile(File(root.parentFile, "music-other/track.mp3")), rootUri))
        assertFalse(LocalAudioAccess.belongsToRoot(Uri.parse("https://example.test/track.mp3"), rootUri))
        assertFalse(LocalAudioAccess.belongsToRoot(rootUri, rootUri))
    }

    @Test fun documentReferencesStayInTheSelectedAuthorityAndTreeGrant() {
        val root = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        assertTrue(LocalAudioAccess.belongsToRoot(Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fa.mp3"), root))
        assertFalse(LocalAudioAccess.belongsToRoot(Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3APrivate/document/primary%3APrivate%2Fa.mp3"), root))
        assertFalse(LocalAudioAccess.belongsToRoot(Uri.parse(
            "content://different.provider/tree/primary%3AMusic/document/primary%3AMusic%2Fa.mp3"), root))
    }

    @Test fun overlappingTreeGrantsIdentifyTheSameDocumentWithoutConfusingOtherProviders() {
        val parent = Uri.parse("content://files.provider/tree/music/document/music%2Falbum%2Fa.mp3")
        val child = Uri.parse("content://files.provider/tree/music%2Falbum/document/music%2Falbum%2Fa.mp3")
        assertEquals(LocalAudioAccess.identity(parent), LocalAudioAccess.identity(child))
        assertNotEquals(LocalAudioAccess.identity(parent), LocalAudioAccess.identity(Uri.parse(
            "content://other.provider/tree/music/document/music%2Falbum%2Fa.mp3")))
    }

    @Test fun removingAnExternalReferenceNeverDeletesItsSourceEvenWithAnOwnedPathPresent() {
        val root = temporary.newFolder("music")
        val file = File(root, "rain.wav").apply { writeText("keep the original") }
        val reference = LocalMusicReference(Uri.fromFile(file).toString(), Uri.fromFile(root).toString(), file.name, file.length(), 1)
        val track = MusicTrack("chosen", MusicPlatform.LOCAL, "雨声", localPath = "chosen.wav", localReference = reference)
        LocalMusicLibrary.remove(track)
        assertEquals("keep the original", file.readText())
    }

    @Test fun referenceAndOwnedCopyHaveExplicitIndependentSerializedSources() {
        val gson = Gson()
        val reference = LocalMusicReference("file:///music/rain.wav", "file:///music", "rain.wav", 1024, 1234, "ab")
        val track = MusicTrack("chosen", MusicPlatform.LOCAL, "雨声", localReference = reference)
        val json = gson.toJson(track)
        assertTrue(json.contains("\"localReference\""))
        assertTrue(json.contains("\"rootUri\""))
        assertEquals(reference, gson.fromJson(json, MusicTrack::class.java).localReference)
        val copy = MusicTrack("copy", MusicPlatform.LOCAL, "雨声", localPath = "copy.wav")
        assertNull(gson.fromJson(gson.toJson(copy), MusicTrack::class.java).localReference)
        assertEquals("copy.wav", gson.fromJson(gson.toJson(copy), MusicTrack::class.java).localPath)
    }

    @Test fun metadataReuseRequiresTheSameIdentityNameSizeAndModificationTime() {
        val cache = LocalAudioMetadataCache()
        val key = LocalAudioMetadataCache.Key("provider:track", "rain.mp3", 1024, 1234)
        val metadata = LocalAudioMetadata("雨声", mime = "audio/mpeg")
        cache.put(key, metadata, artworkChecked = true)
        assertEquals(metadata, cache.get(key, needsArtwork = true))
        assertNull(cache.get(key.copy(identity = "provider:other"), false))
        assertNull(cache.get(key.copy(name = "renamed.mp3"), false))
        assertNull(cache.get(key.copy(size = 2048), false))
        assertNull(cache.get(key.copy(modified = 5678), false))
    }

    @Test fun unknownVersionsAndUncheckedArtworkCannotMasqueradeAsCompleteMetadata() {
        val cache = LocalAudioMetadataCache()
        val metadata = LocalAudioMetadata("雨声", mime = "audio/mpeg")
        val key = LocalAudioMetadataCache.Key("provider:track", "rain.mp3", 1024, 1234)
        cache.put(key, metadata, artworkChecked = false)
        assertEquals(metadata, cache.get(key, false))
        assertNull(cache.get(key, true))
        cache.put(key, metadata, artworkChecked = true)
        cache.put(key, metadata.copy(title = "incomplete"), artworkChecked = false)
        assertEquals(metadata, cache.get(key, true))
        for (unknown in listOf(key.copy(size = 0), key.copy(modified = 0))) {
            cache.put(unknown, metadata, artworkChecked = true)
            assertNull(cache.get(unknown, false))
        }
    }

    @Test fun metadataCacheEvictsLeastRecentlyUsedFilesAtItsBound() {
        val cache = LocalAudioMetadataCache(capacity = 2)
        val first = LocalAudioMetadataCache.Key("first", "a.mp3", 1, 1)
        val second = first.copy(identity = "second")
        val third = first.copy(identity = "third")
        val metadata = LocalAudioMetadata("测试", mime = "audio/mpeg")
        cache.put(first, metadata, true)
        cache.put(second, metadata, true)
        assertNotNull(cache.get(first, true))
        cache.put(third, metadata, true)
        assertNull(cache.get(second, true))
        assertNotNull(cache.get(first, true))
        assertNotNull(cache.get(third, true))
    }

    @Test fun externalSizeIncludesReferencedFilesOnceAndUpdatesWithTheirIndexedSize() {
        val root = "content://files.provider/tree/music"
        val reference = LocalMusicReference("$root/document/music%2Falbum%2Fa.mp3", root, "a.mp3", 1024, 1)
        val first = MusicTrack("a", MusicPlatform.LOCAL, "A", localReference = reference)
        val alias = first.copy(id = "alias", localReference = reference.copy(
            uri = "content://files.provider/tree/music%2Falbum/document/music%2Falbum%2Fa.mp3"))
        val second = first.copy(id = "b", localReference = reference.copy(uri = "$root/document/music%2Fb.mp3", size = 2048))
        val unknown = first.copy(id = "unknown", localReference = reference.copy(uri = "$root/document/music%2Funknown.mp3", size = -1))
        assertEquals(3072L, LocalMusicLibrary.externalStorageBytes(listOf(first, alias, second, unknown)))
        assertEquals(4096L, LocalMusicLibrary.externalStorageBytes(listOf(first.copy(localReference = reference.copy(size = 4096)))))
        assertEquals(0L, LocalMusicLibrary.externalStorageBytes(listOf(first.copy(localReference = null, localPath = "a.mp3"))))
    }
}
