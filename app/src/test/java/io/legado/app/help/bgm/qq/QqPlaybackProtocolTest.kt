package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.help.bgm.MusicProviderException
import io.legado.app.help.bgm.MusicTrack
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class QqPlaybackProtocolTest {
    private val track = MusicTrack("song-mid", MusicPlatform.QQ, "试听曲目",
        mediaId = "stale-media", songType = 0, durationMs = 130_000)

    private fun details(): JSONObject = JSONObject("""{
        "tracks":[
            {"mid":"other-song","vs":["unrelated-trial"],"file":{"size_try":123}},
            {"mid":"song-mid","type":1,"interval":135,"vs":["trial-media"],
             "file":{"media_mid":"full-media","size_try":960887,"try_begin":51186,"try_end":76945}}
        ]
    }""")

    private fun vkey(filename: String = "RS02trial-media.mp3", result: Int = 0): JSONObject =
        JSONObject().put("midurlinfo", JSONArray().put(JSONObject()
            .put("songmid", "song-mid").put("filename", filename).put("result", result)
            .put("purl", "$filename?vkey=test")))

    @Test fun detailsAreMatchedByMidAndRefreshOnlyPlaybackMetadata() {
        val info = QqPlaybackProtocol.trackInfo(details(), track.id)
        val updated = QqPlaybackProtocol.playbackTrack(track, info)
        assertEquals(track.id, updated.id)
        assertEquals(track.title, updated.title)
        assertEquals("full-media", updated.mediaId)
        assertEquals(1, updated.songType)
        assertEquals(135_000L, updated.durationMs)
        assertNull(QqPlaybackProtocol.trackInfo(details(), "missing-song"))
        assertEquals(track, QqPlaybackProtocol.playbackTrack(track, null))
    }

    @Test fun trialRequiresFirstVsAndPositiveDeclaredSizeWithoutUsingTryWindow() {
        val info = QqPlaybackProtocol.trackInfo(details(), track.id)!!
        assertEquals("RS02trial-media.mp3", QqPlaybackProtocol.previewFileName(info))
        info.getJSONObject("file").put("try_begin", 0).put("try_end", 0)
        assertEquals("RS02trial-media.mp3", QqPlaybackProtocol.previewFileName(info))
        info.put("vs", JSONArray().put("").put("second-unrelated-media"))
        assertNull(QqPlaybackProtocol.previewFileName(info))
        info.put("vs", JSONArray().put("trial-media"))
        info.getJSONObject("file").put("size_try", 0)
        assertNull(QqPlaybackProtocol.previewFileName(info))
        info.getJSONObject("file").remove("size_try")
        assertNull(QqPlaybackProtocol.previewFileName(info))
        assertNull(QqPlaybackProtocol.previewFileName(null))
    }

    @Test fun invalidOrMissingTrialMidIsNeverReplacedWithTheOrdinaryMid() {
        val info = QqPlaybackProtocol.trackInfo(details(), track.id)!!
        for (value in listOf("../other", "https://example.com/media", "media?key=1")) {
            info.put("vs", JSONArray().put(value))
            assertNull(QqPlaybackProtocol.previewFileName(info))
        }
        info.put("vs", JSONArray().put(JSONObject.NULL))
        assertNull(QqPlaybackProtocol.previewFileName(info))
        info.remove("vs")
        assertNull(QqPlaybackProtocol.previewFileName(info))
    }

    @Test fun bothDeclaredFilenameAndActualPathMustMatchTheRequestedResource() {
        assertEquals("RS02trial-media.mp3?vkey=test",
            QqPlaybackProtocol.mediaPath(vkey(), track.id, "RS02trial-media.mp3"))
        assertThrows(MusicProviderException::class.java) {
            QqPlaybackProtocol.mediaPath(vkey(), track.id, "M500full-media.mp3")
        }
        val replaced = vkey().apply {
            getJSONArray("midurlinfo").getJSONObject(0).put("purl", "RS02other-media.mp3?vkey=test")
        }
        assertThrows(MusicProviderException::class.java) {
            QqPlaybackProtocol.mediaPath(replaced, track.id, "RS02trial-media.mp3")
        }
        val otherSong = vkey().apply {
            getJSONArray("midurlinfo").getJSONObject(0).put("songmid", "other-song")
        }
        assertThrows(MusicProviderException::class.java) {
            QqPlaybackProtocol.mediaPath(otherSong, track.id, "RS02trial-media.mp3")
        }
    }

    @Test fun authorizationCodesRemainDistinctFromAnEmptySuccessfulUrl() {
        for (code in listOf(104003, 104004, 104013)) {
            val error = assertThrows(MusicProviderException::class.java) {
                QqPlaybackProtocol.mediaPath(vkey(result = code), track.id, "RS02trial-media.mp3")
            }
            assertEquals(code, error.platformCode)
        }
        val empty = vkey().apply { getJSONArray("midurlinfo").getJSONObject(0).put("purl", "") }
        assertNull(QqPlaybackProtocol.mediaPath(empty, track.id, "RS02trial-media.mp3"))
        val malformed = vkey().apply { getJSONArray("midurlinfo").getJSONObject(0).remove("result") }
        assertThrows(MusicProviderException::class.java) {
            QqPlaybackProtocol.mediaPath(malformed, track.id, "RS02trial-media.mp3")
        }
    }

    @Test fun addressesOnlyComeFromReturnedPlatformCdnRootsOrAnAuthorizedAbsoluteUrl() {
        val path = "RS02trial-media.mp3?vkey=test"
        val roots = JSONArray().put("https://evil.example/").put("https://isure.stream.qqmusic.qq.com/")
        assertEquals("https://isure.stream.qqmusic.qq.com/$path", QqPlaybackProtocol.mediaAddress(path, roots))
        assertNull(QqPlaybackProtocol.mediaAddress(path, null))
        assertNull(QqPlaybackProtocol.mediaAddress("https://evil.example/$path", roots))
        assertNull(QqPlaybackProtocol.mediaAddress(path, JSONArray().put("https://user:pass@qq.com/")))
        assertNull(QqPlaybackProtocol.mediaAddress(path, JSONArray().put("https://qq.com:8443/")))
    }
}
