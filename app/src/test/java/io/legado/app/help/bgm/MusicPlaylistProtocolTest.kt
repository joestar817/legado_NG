package io.legado.app.help.bgm

import io.legado.app.help.bgm.qq.qqCgiData
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MusicPlaylistProtocolTest {
    private fun netease(special: Int = 0, owner: String = "12") = JSONObject()
        .put("id", 77).put("name", "听书").put("specialType", special)
        .put("creator", JSONObject().put("userId", owner))

    @Test fun ordinaryOwnedPlaylistIsEditable() {
        val playlist = requireNotNull(MusicPlaylistProtocol.netease(netease(), "12"))
        assertTrue(playlist.editable)
        MusicPlaylistProtocol.requireOwned(playlist, MusicPlatform.NETEASE, "12")
    }

    @Test fun favoritePlaylistCannotUseOrdinaryWriteOperations() {
        val playlist = requireNotNull(MusicPlaylistProtocol.netease(netease(5), "12"))
        assertEquals(MusicPlaylistKind.SYSTEM, playlist.kind)
        assertFalse(playlist.editable)
        rejected { MusicPlaylistProtocol.requireOwned(playlist, MusicPlatform.NETEASE, "12") }
    }

    @Test fun collectedPlaylistsNeverGainOwnershipFromTheirDisplayTitle() {
        val playlist = requireNotNull(MusicPlaylistProtocol.netease(netease(owner = "99"), "12"))
        assertEquals(MusicPlaylistKind.COLLECTED, playlist.kind)
        assertFalse(playlist.editable)
        rejected { MusicPlaylistProtocol.requireOwned(playlist, MusicPlatform.NETEASE, "12") }
    }

    @Test fun missingOwnerOrUnknownSpecialTypeDoesNotGrantWriteAccess() {
        val row = netease().apply { remove("creator") }
        assertFalse(requireNotNull(MusicPlaylistProtocol.netease(row, "12")).editable)
        assertFalse(requireNotNull(MusicPlaylistProtocol.netease(netease(999), "12")).editable)
        val missingType = netease().apply { remove("specialType") }
        assertFalse(requireNotNull(MusicPlaylistProtocol.netease(missingType, "12")).editable)
    }

    @Test fun accountSwitchCannotReusePreviousAccountsWriteTarget() {
        val playlist = requireNotNull(MusicPlaylistProtocol.netease(netease(), "12"))
        rejected { MusicPlaylistProtocol.requireOwned(playlist, MusicPlatform.NETEASE, "34") }
        rejected { MusicPlaylistProtocol.requireOwned(playlist, MusicPlatform.QQ, "12") }
    }

    @Test fun neteaseTrackIdsAreDistinctAndCrossPlatformTracksAreRejected() {
        val playlist = requireNotNull(MusicPlaylistProtocol.netease(netease(), "12"))
        val track = MusicTrack("100", MusicPlatform.NETEASE, "歌曲")
        val data = MusicPlaylistProtocol.neteaseTracks(playlist, listOf(track, track), true)
        assertEquals("[100]", data.getString("trackIds"))
        assertEquals("add", data.getString("op"))
        rejected { MusicPlaylistProtocol.neteaseTracks(playlist, listOf(track.copy(platform = MusicPlatform.QQ)), true) }
        rejected { MusicPlaylistProtocol.neteaseTracks(playlist, emptyList(), false) }
    }

    @Test fun qqUsesNumericSongIdAndPreservesSongTypeAndBooleanFlag() {
        val playlist = MusicPlaylist("80", MusicPlatform.QQ, "听书",
            extra = """{"tid":80,"dirid":10}""", ownerId = "12", kind = MusicPlaylistKind.OWNED)
        val track = MusicTrack("00mid", MusicPlatform.QQ, "歌", songType = 3, numericId = 12345)
        val request = MusicPlaylistProtocol.qqTracks(playlist, listOf(track))
        val song = request.getJSONArray("v_songInfo").getJSONObject(0)
        assertEquals(12345L, song.getLong("songId"))
        assertEquals(3, song.getInt("songType"))
        assertEquals(true, request.get("bFmtUtf8"))
        assertEquals(10, request.getInt("dirId"))
        rejected { MusicPlaylistProtocol.qqTracks(playlist, listOf(track.copy(numericId = 0))) }
    }

    @Test fun qqSystemDirectoryCannotBePassedAsAnOrdinaryPlaylist() {
        val playlist = MusicPlaylist("80", MusicPlatform.QQ, "系统",
            extra = """{"tid":80,"dirid":201}""", ownerId = "12", kind = MusicPlaylistKind.OWNED)
        rejected { MusicPlaylistProtocol.qqTracks(playlist, listOf(MusicTrack("mid", MusicPlatform.QQ, "歌", numericId = 12))) }
    }

    @Test fun allQqResponseLayersMustSucceed() {
        rejected { qqCgiData(JSONObject("""{"code":0,"req_0":{"code":80105,"data":{"retCode":0}}}""")) }
        rejected { qqCgiData(JSONObject("""{"req_0":{"code":0,"data":{"retCode":0}}}""")) }
        rejected { qqCgiData(JSONObject("""{"code":0,"req_0":{"data":{"retCode":0}}}""")) }
        rejected { MusicPlaylistProtocol.requireQqWrite(JSONObject("""{"retCode":12}""")) }
        rejected { MusicPlaylistProtocol.requireQqWrite(JSONObject()) }
        MusicPlaylistProtocol.requireQqWrite(qqCgiData(JSONObject("""{"code":0,"req_0":{"code":0,"data":{"retCode":0}}}""")))
    }

    @Test fun neteaseDuplicatesAndMalformedResponsesAreNotReportedAsSuccess() {
        rejected { MusicPlaylistProtocol.requireNeteaseWrite(JSONObject("""{"code":502}""")) }
        rejected { MusicPlaylistProtocol.requireNeteaseWrite(JSONObject()) }
        MusicPlaylistProtocol.requireNeteaseWrite(JSONObject("""{"code":200}"""))
    }

    @Test fun playlistNamesAreTrimmedAndBounded() {
        assertEquals("听书", MusicPlaylistProtocol.title(" 听书 "))
        rejected { MusicPlaylistProtocol.title(" ") }
        rejected { MusicPlaylistProtocol.title("字".repeat(41)) }
    }

    @Test fun membershipUsesAllIdsEvenWhenSongDetailsAreOnlyAPreview() {
        val row = JSONObject().put("trackCount", 3)
            .put("trackIds", JSONArray("""[{"id":100},{"id":101},{"id":102}]"""))
            .put("tracks", JSONArray("""[{"id":100}]"""))
        assertEquals(setOf("100", "101", "102"), MusicPlaylistProtocol.neteasePlaylistTrackIds(row))
    }

    @Test fun missingPartialOrInvalidMembershipIsNotAnEmptyPlaylist() {
        rejected { MusicPlaylistProtocol.neteasePlaylistTrackIds(JSONObject().put("trackCount", 3)) }
        rejected { MusicPlaylistProtocol.neteasePlaylistTrackIds(JSONObject()
            .put("trackCount", 3).put("trackIds", JSONArray("""[{"id":100}]"""))) }
        rejected { MusicPlaylistProtocol.neteasePlaylistTrackIds(JSONObject()
            .put("trackIds", JSONArray("""[{"id":null}]"""))) }
    }

    @Test fun explicitEmptyMembershipIsValid() {
        assertEquals(emptySet<String>(), MusicPlaylistProtocol.neteasePlaylistTrackIds(JSONObject()
            .put("trackCount", 0).put("trackIds", JSONArray())))
    }

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Expected a MusicProviderException")
        } catch (_: MusicProviderException) { }
    }
}
