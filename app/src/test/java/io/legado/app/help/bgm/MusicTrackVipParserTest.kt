package io.legado.app.help.bgm

import com.google.gson.Gson
import io.legado.app.help.bgm.netease.NeteaseMusicProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MusicTrackVipParserTest {
    @Test fun qqCatalogueSamplesMatchThreeBadgesInFiveTrackPlaylist() {
        // The third song has no VIP badge even though its full-song URL can be unavailable.
        val songs = JSONArray("""[
            {"name":"我好想你 (伴奏)","pay":{"pay_month":0,"pay_play":0,"pay_down":0}},
            {"name":"无仙","pay":{"pay_month":1,"pay_play":1,"pay_down":1}},
            {"name":"春娇与志明 (伴奏)","pay":{"pay_month":0,"pay_play":0,"pay_down":0}},
            {"name":"回忆观影券 (伴奏)","pay":{"pay_month":1,"pay_play":1,"pay_down":1}},
            {"name":"悬溺","pay":{"pay_month":1,"pay_play":1,"pay_down":1}}
        ]""")
        assertEquals(listOf(false, true, false, true, true),
            (0 until songs.length()).map { MusicTrackVipParser.qq(songs.getJSONObject(it)) })
    }

    @Test fun qqRequiresBothMonthlyMembershipAndPlaybackFlags() {
        for (monthly in 0..1) for (playback in 0..1) {
            val song = JSONObject().put("pay", JSONObject()
                .put("pay_month", monthly).put("pay_play", playback).put("pay_down", 1))
            assertEquals(monthly == 1 && playback == 1, MusicTrackVipParser.qq(song))
        }
        assertNull(MusicTrackVipParser.qq(JSONObject("""{"pay":{"pay_down":1},"code":104003,"try_end":60000}""")))
    }

    @Test fun qqMissingMalformedOrUnknownFlagsStayUnknown() {
        for (song in listOf("{}", """{"pay":null}""", """{"pay":1}""",
            """{"pay":{"pay_month":1}}""", """{"pay":{"pay_play":1}}""")) {
            assertNull(MusicTrackVipParser.qq(JSONObject(song)))
        }
        for (value in listOf(JSONObject.NULL, "bad", "", true, 1.5, 2, -1, JSONObject(), JSONArray())) {
            for (key in listOf("pay_month", "pay_play")) {
                val pay = JSONObject().put("pay_month", 1).put("pay_play", 1).put(key, value)
                assertNull("$key=$value", MusicTrackVipParser.qq(JSONObject().put("pay", pay)))
            }
        }
    }

    @Test fun neteaseOnlyFeeOneIsAVipBadge() {
        for ((fee, expected) in mapOf(0 to false, 1 to true, 4 to false, 8 to false)) {
            assertEquals(expected, MusicTrackVipParser.netease(JSONObject().put("fee", fee)))
        }
        for (value in listOf(JSONObject.NULL, "bad", "", true, 1.5, 2, -1, JSONObject(), JSONArray())) {
            assertNull(MusicTrackVipParser.netease(JSONObject().put("fee", value)))
        }
        assertNull(MusicTrackVipParser.netease(JSONObject("""{"paid":true,"copyright":1}""")))
    }

    @Test fun neteasePresentFeeAlwaysWinsOverConflictingPrivileges() {
        val top = JSONObject("""{"id":7,"fee":1}""")
        val song = JSONObject("""{"id":7,"fee":0,"privilege":{"id":7,"fee":1}}""")
        assertEquals(false, MusicTrackVipParser.netease(song, top))
        for (fee in listOf(4, 8)) {
            song.put("fee", fee)
            assertEquals(false, MusicTrackVipParser.netease(song, top))
        }
        for (fee in listOf(2, "bad", true)) {
            song.put("fee", fee)
            assertNull(MusicTrackVipParser.netease(song, top))
        }
    }

    @Test fun neteaseMissingOrNullFeeUsesOnlyMatchingPrivilegeIdentity() {
        val song = JSONObject("""{"id":7}""")
        val matching = JSONObject("""{"id":7,"fee":1}""")
        val different = JSONObject("""{"id":8,"fee":1}""")
        assertEquals(true, MusicTrackVipParser.netease(song, matching))
        song.put("fee", JSONObject.NULL)
        assertEquals(true, MusicTrackVipParser.netease(song, matching))
        assertNull(MusicTrackVipParser.netease(song, different))
        song.put("privilege", different)
        assertNull(MusicTrackVipParser.netease(song))
        song.put("privilege", matching)
        assertEquals(true, MusicTrackVipParser.netease(song, different))
        song.remove("id")
        assertNull(MusicTrackVipParser.netease(song, matching))
    }

    @Test fun neteaseNestedPrivilegePreservesExplicitFreeAndUnknownValues() {
        val top = JSONObject("""{"id":7,"fee":1}""")
        val nested = JSONObject("""{"id":7,"fee":0}""")
        val song = JSONObject().put("id", 7).put("privilege", nested)
        assertEquals(false, MusicTrackVipParser.netease(song, top))
        nested.put("fee", "bad")
        assertNull(MusicTrackVipParser.netease(song, top))
        nested.put("fee", JSONObject.NULL)
        assertEquals(true, MusicTrackVipParser.netease(song, top))
    }

    @Test fun neteaseTrackParsingMatchesPrivilegesByIdInsteadOfResponsePosition() {
        val songs = JSONArray("""[
            {"id":1,"name":"免费","fee":0,"ar":[{"name":"甲"}],"dt":1234},
            {"id":2,"name":"会员","artists":[{"name":"乙"}],"duration":2345},
            {"id":3,"name":"单独购买","fee":4},
            {"id":4,"name":"未知","fee":2},
            {"id":5,"name":"缺失"}
        ]""")
        val privileges = JSONArray("""[
            {"id":4,"fee":1},{"id":3,"fee":1},{"id":2,"fee":1},{"id":1,"fee":1},
            {"id":6,"fee":1},{"fee":1},null
        ]""")
        val tracks = NeteaseMusicProvider.parseTracks(songs, privileges)
        assertEquals(listOf("1", "2", "3", "4", "5"), tracks.map { it.id })
        assertEquals(listOf(false, true, false, null, null), tracks.map { it.isVip })
        assertEquals(listOf("甲", "乙"), tracks.take(2).map { it.artist })
        assertEquals(listOf(1234L, 2345L), tracks.take(2).map { it.durationMs })
        assertEquals(listOf(false, null, false, null, null),
            NeteaseMusicProvider.parseTracks(songs).map { it.isVip })
    }

    @Test fun vipFlagUsesStableSerializedNameAndMissingOldFieldsRemainUnknown() {
        val gson = Gson()
        val vip = MusicTrack("mid", MusicPlatform.QQ, "会员", isVip = true)
        val json = gson.toJsonTree(vip).asJsonObject
        assertTrue(json.get("isVip").asBoolean)
        assertEquals(vip, gson.fromJson(json, MusicTrack::class.java))
        val ordinary = gson.toJsonTree(vip.copy(isVip = false)).asJsonObject
        assertFalse(ordinary.get("isVip").asBoolean)
        assertNull(gson.fromJson("""{"id":"old","platform":"qq","title":"已有曲目"}""",
            MusicTrack::class.java).isVip)
        assertNull(gson.fromJson("""{"id":"old","platform":"netease","title":"已有曲目","isVip":null}""",
            MusicTrack::class.java).isVip)
    }
}
