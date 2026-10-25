package io.legado.app.help.bgm.netease

import io.legado.app.help.bgm.MusicAccountProfile
import io.legado.app.help.bgm.MusicProviderException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NeteaseAccountProfileParserTest {
    @Test fun accountResponseProvidesIdentityWithoutInventingStatistics() {
        val account = NeteaseAccountProfileParser.account(JSONObject("""{
            "account":{"id":123},
            "profile":{"userId":123,"nickname":"听书用户","avatarUrl":"https://p1.music.126.net/avatar.jpg","signature":"听歌与阅读"}
        }"""))
        assertEquals("123", account.userId)
        assertEquals("听书用户", account.nickname)
        assertEquals("https://p1.music.126.net/avatar.jpg", account.avatarUrl)
        assertEquals("听歌与阅读", account.signature)
        assertNull(account.level)
        assertNull(account.followerCount)
        assertNull(account.listenSongs)
        assertFalse(account.detailsLoaded)
    }

    @Test fun absentProfileIsNotRenderedAsLiteralNull() {
        val account = NeteaseAccountProfileParser.account(JSONObject("""{
            "account":{"id":"123"},"profile":{"userId":123,"nickname":null,"avatarUrl":null,"signature":null}
        }"""))
        assertEquals("", account.nickname)
        assertEquals("", account.avatarUrl)
        assertEquals("", account.signature)
    }

    @Test fun missingAndConflictingAccountIdsAreRejected() {
        assertThrows(MusicProviderException::class.java) {
            NeteaseAccountProfileParser.account(JSONObject("""{"account":null,"profile":null}"""))
        }
        assertThrows(MusicProviderException::class.java) {
            NeteaseAccountProfileParser.account(JSONObject("""{"account":{"id":123},"profile":{"userId":456}}"""))
        }
    }

    @Test fun detailKeepsZeroCountersAndSongCountWithoutInventingListeningTime() {
        val profile = NeteaseAccountProfileParser.detail(JSONObject("""{
            "level":10,"listenSongs":12845,
            "profile":{"userId":123,"nickname":"新昵称","follows":0,"followeds":1,"playlistCount":48,"signature":""}
        }"""), MusicAccountProfile("123", nickname = "旧昵称", avatarUrl = "avatar", signature = "旧签名"))
        assertEquals("新昵称", profile.nickname)
        assertEquals("avatar", profile.avatarUrl)
        assertEquals("", profile.signature)
        assertEquals(10, profile.level)
        assertEquals(0L, profile.followingCount)
        assertEquals(1L, profile.followerCount)
        assertEquals(48L, profile.playlistCount)
        assertEquals(12845L, profile.listenSongs)
        assertTrue(profile.detailsLoaded)
    }

    @Test fun missingOrInvalidCountersRemainUnknownInsteadOfZero() {
        val profile = NeteaseAccountProfileParser.detail(JSONObject("""{
            "level":2147483648,"listenSongs":null,
            "profile":{"userId":123,"follows":-1,"followeds":"unknown","playlistCount":2.5}
        }"""), MusicAccountProfile("123"))
        assertNull(profile.level)
        assertNull(profile.followingCount)
        assertNull(profile.followerCount)
        assertNull(profile.playlistCount)
        assertNull(profile.listenSongs)
    }

    @Test fun detailCannotMixAnotherAccountIntoTheDialog() {
        val account = MusicAccountProfile("123")
        assertThrows(MusicProviderException::class.java) {
            NeteaseAccountProfileParser.detail(JSONObject("""{"profile":{"userId":456,"nickname":"别人的资料"}}"""), account)
        }
        assertThrows(MusicProviderException::class.java) {
            NeteaseAccountProfileParser.detail(JSONObject("""{"profile":{"nickname":"没有账号标识"}}"""), account)
        }
    }

    @Test fun membershipKeepsThePlatformLevelAndOriginalBadgeUrl() {
        val membership = NeteaseAccountProfileParser.membership(JSONObject("""{
            "data":{"redVipLevel":7,"redVipLevelIcon":"https://p5.music.126.net/vip.png"}
        }"""), "123")!!
        assertEquals(7, membership.level)
        assertEquals("https://p5.music.126.net/vip.png", membership.iconUrl)
    }

    @Test fun zeroMembershipLevelDoesNotInventABadge() {
        assertNull(NeteaseAccountProfileParser.membership(JSONObject("""{
            "data":{"userId":123,"redVipLevel":0,"redVipLevelIcon":"https://p5.music.126.net/vip.png"}
        }"""), "123"))
    }

    @Test fun unavailableMembershipLevelIsAnErrorInsteadOfNonMember() {
        listOf("""{}""", """{"data":{}}""", """{"data":{"redVipLevel":null}}""",
            """{"data":{"redVipLevel":-1}}""", """{"data":{"redVipLevel":2147483648}}""").forEach {
            assertThrows(MusicProviderException::class.java) {
                NeteaseAccountProfileParser.membership(JSONObject(it), "123")
            }
        }
    }

    @Test fun missingStaticIconKeepsLevelWithoutSubstitutingAnUnverifiedDynamicAsset() {
        val membership = NeteaseAccountProfileParser.membership(JSONObject("""{
            "data":{"redVipLevel":7,"redVipLevelIcon":null,"redVipDynamicIconUrl":"https://p5.music.126.net/vip.gif"}
        }"""), "123")!!
        assertEquals(7, membership.level)
        assertEquals("", membership.iconUrl)
    }

    @Test fun membershipCannotAttachAnotherUsersBadge() {
        assertThrows(MusicProviderException::class.java) {
            NeteaseAccountProfileParser.membership(JSONObject("""{
                "data":{"userId":456,"redVipLevel":7}
            }"""), "123")
        }
    }

    @Test fun membershipImagesOnlyUsePlatformHttpOriginsWithoutCredentials() {
        listOf("file:///data/local/tmp/avatar.png", "https://music.126.net.example.org/icon.png",
            "https://user:secret@p5.music.126.net/icon.png", "https://p5.music.126.net:999/icon.png"
        ).forEach { url ->
            val membership = NeteaseAccountProfileParser.membership(JSONObject().put("data",
                JSONObject().put("redVipLevel", 7).put("redVipLevelIcon", url)), "123")!!
            assertEquals(7, membership.level)
            assertEquals("", membership.iconUrl)
        }
    }
}
