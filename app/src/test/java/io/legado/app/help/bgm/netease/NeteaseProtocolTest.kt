package io.legado.app.help.bgm.netease

import io.legado.app.help.bgm.MusicHttpClient
import io.legado.app.help.bgm.MusicPlatform
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NeteaseProtocolTest {
    /** Expected bytes were generated offline by the pinned upstream 4.41.1 codec. */
    @Test fun weapiMatchesUpstreamUtf8AndRsaVector() {
        val encrypted = NeteaseCrypto.weapi(JSONObject().put("text", "听书 BGM"), "pppppppppppppppp")
        assertEquals("PR7Ms7zqJtYwReXI3xljUFI42zBFGzBk1yPMTrMPkmN8HXkNiWrTp1dl8viTJepC", encrypted["params"])
        assertEquals("d571259ee779c40016dcd8456ee76ec0668228ab9d7873275215adb6872bacc5ca4b6062b399303863af64e3999bf981fe2638edeb2a8fc791ac20edce7598b8fac0571dda6aea083a4ff651996a0deed7ce46756acb8716e86aab55a039746baee9b9ffe26081c588d450053a37b98ab01ee92677e139ac9370187593a64bc9", encrypted["encSecKey"])
    }

    @Test fun normalUrlEapiMatchesUpstreamVector() {
        assertEquals("FA90B329E9614F79E79598F37DC2EDB430F8378D2A2796338F0BFDEAEF824A22975CDA9D96D79E6DC4A59218CDB8199F8C39C82BF6270DF1CF5422064F351230CF1EB5AE383C8994A3F0207D89DB41BA47E836F21DF86C7D07FF2D63ACAA63FBA35CC48935283844D6A2A0D8ACD76E14",
            NeteaseCrypto.eapi("/api/song/enhance/player/url", JSONObject().put("ids", "[\"123\"]"))["params"])
    }

    @Test fun bothCatalogueSongShapesKeepStableIdsAndDurations() {
        val values = JSONArray("""[{"id":1,"name":"一","ar":[{"name":"甲"}],"al":{"name":"专辑","picUrl":"https://p1.music.126.net/x"},"dt":1234},{"id":2,"name":"二","artists":[{"name":"乙"}],"album":{"name":"旧字段"},"duration":2345}]""")
        val songs = NeteaseMusicProvider.parseTracks(values)
        assertEquals(listOf("1", "2"), songs.map { it.id })
        assertEquals(listOf("甲", "乙"), songs.map { it.artist })
        assertEquals(listOf(1234L, 2345L), songs.map { it.durationMs })
    }

    @Test fun mediaOriginRequiresExactPlatformSuffixAndNoCredentials() {
        assertTrue(NeteaseMusicProvider.isMediaUrl("https://m801.music.126.net/a.mp3"))
        assertTrue(NeteaseMusicProvider.isMediaUrl("http://m8.music.126.net/a.mp3"))
        assertFalse(NeteaseMusicProvider.isMediaUrl("https://music.126.net.example.org/a"))
        assertFalse(NeteaseMusicProvider.isMediaUrl("https://user:secret@music.126.net/a"))
        assertFalse(NeteaseMusicProvider.isMediaUrl("https://music.126.net:999/a"))
    }

    @Test fun restoredCookieSnapshotKeepsEarlierCookiesAndRejectsForeignOrigins() {
        val client = MusicHttpClient(MusicPlatform.NETEASE, setOf("163.com"))
        client.restoreCookies(JSONArray().put(JSONObject().put("url", "https://music.163.com/")
            .put("cookie", "test_cookie=first; path=/; secure"))
            .put(JSONObject().put("url", "https://outside.example/").put("cookie", "foreign=bad; path=/")))
        assertEquals("first", client.cookie("test_cookie", "music.163.com"))
        assertEquals("", client.cookie("foreign"))
        val restored = MusicHttpClient(MusicPlatform.NETEASE, setOf("163.com"))
        restored.restoreCookies(client.snapshotCookies())
        assertEquals("first", restored.cookie("test_cookie"))
        restored.clearCookies()
        assertEquals(0, restored.snapshotCookies().length())
    }
}
