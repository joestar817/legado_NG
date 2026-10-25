package io.legado.app.help.bgm.qq

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
class QqAccountProfileParserTest {
    private fun response(): JSONObject = JSONObject("""{
        "code":0,
        "account":{"code":0,"data":{}},
        "home":{"code":0,"data":{"Info":{"BaseInfo":{"EncryptedUin":"account-id","Name":"听书用户","Avatar":"https://y.gtimg.cn/avatar.jpg"}}}},
        "gene":{"code":0,"data":{"UserInfoCard":{"EncryptionAccount":"account-id","NickName":"听书用户","Signature":"听歌与阅读","HeadUrl":"https://q.qlogo.cn/avatar.jpg"}}},
        "following":{"code":0,"data":{"Total":3,"LockFlag":0}},
        "fans":{"code":0,"data":{"Total":0,"LockFlag":0}}
    }""")

    private fun extrasResponse(
        vip: JSONObject? = JSONObject("""{"identity":{"vip":0,"HugeVip":0,"level":0}}"""),
        soundPower: JSONObject? = JSONObject("""{"powerInfo":{"level":8}}"""),
    ): JSONObject = JSONObject().put("code", 0)
        .put("membership", JSONObject().put("code", 0).put("data", vip ?: JSONObject.NULL))
        .put("soundPower", JSONObject().put("code", 0).put("data", soundPower ?: JSONObject.NULL))

    @Test fun parsesCurrentAccountWithoutInventingMedalsOrListeningStatistics() {
        val profile = QqAccountProfileParser.profile("123", "account-id", response())
        assertEquals("听书用户", profile.nickname)
        assertEquals("听歌与阅读", profile.signature)
        assertEquals(3L, profile.followingCount)
        assertEquals(0L, profile.followerCount)
        assertNull(profile.level)
        assertNull(profile.listenSongs)
        assertTrue(profile.detailsLoaded)
    }

    @Test fun failedHomepageCanStillUseTheIdentityCheckedMusicCard() {
        val response = response().apply { getJSONObject("home").put("code", 10000) }
        response.getJSONObject("following").getJSONObject("data").put("LockFlag", 1)
        response.getJSONObject("fans").getJSONObject("data").remove("Total")
        val profile = QqAccountProfileParser.profile("123", "account-id", response)
        assertEquals("听书用户", profile.nickname)
        assertEquals("https://q.qlogo.cn/avatar.jpg", profile.avatarUrl)
        assertNull(profile.followingCount)
        assertNull(profile.followerCount)
        assertFalse(profile.detailsLoaded)
    }

    @Test fun anotherAccountsProfileAndExpiredAuthenticationAreRejected() {
        assertThrows(MusicProviderException::class.java) {
            QqAccountProfileParser.profile("123", "different-account", response())
        }
        val expired = response().apply { getJSONObject("fans").put("code", 104401) }
        assertThrows(MusicProviderException::class.java) {
            QqAccountProfileParser.profile("123", "account-id", expired)
        }
        val nestedExpiry = response().apply { getJSONObject("account").getJSONObject("data").put("code", 1000) }
        assertThrows(MusicProviderException::class.java) {
            QqAccountProfileParser.profile("123", "account-id", nestedExpiry)
        }
    }

    @Test fun expiredMembershipRetainsItsLevelAndOfficialBadgeWithInactiveState() {
        val membership = requireNotNull(QqAccountProfileParser.membership(JSONObject("""{
            "svip":0,"identity":{"vip":0,"HugeVip":0,"level":8,"icon":"https://y.gtimg.cn/vip.png"}
        }""")))
        assertEquals(8, membership.level)
        assertEquals("VIP Lv.8", membership.label)
        assertEquals("https://y.gtimg.cn/vip.png", membership.iconUrl)
        assertEquals(false, membership.isActive)
    }

    @Test fun neverSubscribedAccountsDoNotGetAnInventedExpiredVipBadge() {
        assertNull(QqAccountProfileParser.membership(JSONObject("""{
            "svip":0,"identity":{"vip":0,"HugeVip":0,"level":0,"icon":"https://y.gtimg.cn/vip.png"}
        }""")))
    }

    @Test fun missingMembershipStateIsNotMistakenForANonMember() {
        assertThrows(MusicProviderException::class.java) {
            QqAccountProfileParser.membership(JSONObject("""{"identity":{"level":8}}"""))
        }

    @Test fun membershipKeepsOfficialTypeAndRejectsForeignImageOrigins() {
        val membership = QqAccountProfileParser.membership(JSONObject("""{
            "identity":{"HugeVip":1,"level":8,"icon":"https://qq.com.attacker.test/vip.png"}
        }"""))!!
        assertEquals("豪华绿钻 Lv.8", membership.label)
        assertEquals("", membership.iconUrl)
        val superVip = QqAccountProfileParser.membership(JSONObject("""{
            "svip":1,"identity":{"HugeVip":1,"level":8,"icon":"https://y.gtimg.cn/vip.png"}
        }"""))!!
        assertEquals("超级会员 Lv.8", superVip.label)
        assertEquals("https://y.gtimg.cn/vip.png", superVip.iconUrl)
        assertEquals(true, superVip.isActive)
    }

    @Test fun musicLevelDoesNotRequireAnActiveSubscription() {
        val extras = QqAccountProfileParser.extras(extrasResponse())
        assertNull(extras.membership)
        assertEquals(8, extras.musicLevel)
        assertEquals("", extras.musicLevelIconUrl)
        assertTrue(extras.membershipLoaded)
        assertTrue(extras.musicLevelLoaded)
    }

    @Test fun currentAccountLevelKeepsItsOfficialImageRatherThanTheNextLevelImage() {
        val extras = QqAccountProfileParser.extras(extrasResponse(vip = null,
            soundPower = JSONObject("""{"powerInfo":{"level":8,
                "icon":"https://y.gtimg.cn/current-level.png",
                "nextLevelIcon":"https://y.gtimg.cn/next-level.png"}}""")))
        assertEquals(8, extras.musicLevel)
        assertEquals("https://y.gtimg.cn/current-level.png", extras.musicLevelIconUrl)
        assertFalse(extras.membershipLoaded)
        assertTrue(extras.musicLevelLoaded)
    }

    @Test fun missingOrUntrustedBadgeImagesDoNotEraseTheKnownLevel() {
        for (url in listOf("", "https://qq.com.attacker.test/badge.png",
            "https://user:password@y.gtimg.cn/badge.png", "https://y.gtimg.cn:8443/badge.png",
            "file:///badge.png")) {
            val powerInfo = JSONObject().put("level", 8).put("icon", url)
                .put("nextLevelIcon", "https://y.gtimg.cn/next-level.png")
            val extras = QqAccountProfileParser.extras(extrasResponse(
                soundPower = JSONObject().put("powerInfo", powerInfo)))
            assertEquals(8, extras.musicLevel)
            assertEquals("", extras.musicLevelIconUrl)
            assertTrue(extras.musicLevelLoaded)
        }
    }

    @Test fun aMissingMembershipBlockDoesNotEraseKnownMusicLevelOrClaimNonMembership() {
        val extras = QqAccountProfileParser.extras(extrasResponse(vip = null))
        assertNull(extras.membership)
        assertEquals(8, extras.musicLevel)
        assertFalse(extras.membershipLoaded)
        assertTrue(extras.musicLevelLoaded)
    }

    @Test fun invalidMusicLevelsAreNotCoercedToZero() {
        for (raw in listOf("null", "-1", "2147483648", "1.5", "\"unknown\"")) {
            val extras = QqAccountProfileParser.extras(extrasResponse(
                soundPower = JSONObject("""{"powerInfo":{"level":$raw,"icon":"https://y.gtimg.cn/badge.png"}}""")))
            assertNull(extras.musicLevel)
            assertEquals("", extras.musicLevelIconUrl)
            assertFalse(extras.musicLevelLoaded)
        }
        val zero = QqAccountProfileParser.extras(extrasResponse(
            soundPower = JSONObject("""{"powerInfo":{"level":0}}""")))
        assertEquals(0, zero.musicLevel)
        assertTrue(zero.musicLevelLoaded)
    }

    @Test fun accountLevelUsesSoundPowerInsteadOfTheLegacyVipMusicLevel() {
        val extras = QqAccountProfileParser.extras(extrasResponse(vip = JSONObject("""{
            "identity":{"HugeVip":1,"level":6},"userinfo":{"music_level":7}
        }""")))
        val membership = requireNotNull(extras.membership)
        assertEquals(6, membership.level)
        assertEquals(8, extras.musicLevel)
        assertEquals(true, membership.isActive)
    }

    @Test fun unavailableSoundPowerDoesNotReuseLegacyLevelOrEraseMembership() {
        val response = extrasResponse(vip = JSONObject("""{
            "identity":{"HugeVip":1,"level":6},"userinfo":{"music_level":7}
        }"""))
        response.getJSONObject("soundPower").put("code", 10000)
        val extras = QqAccountProfileParser.extras(response)
        assertEquals(6, requireNotNull(extras.membership).level)
        assertNull(extras.musicLevel)
        assertEquals("", extras.musicLevelIconUrl)
        assertTrue(extras.membershipLoaded)
        assertFalse(extras.musicLevelLoaded)
    }

    @Test fun authenticationFailureInEitherExtraBlockRejectsTheWholeAccountResult() {
        for (block in listOf("membership", "soundPower")) {
            val response = extrasResponse().apply { getJSONObject(block).put("code", 104401) }
            assertThrows(MusicProviderException::class.java) { QqAccountProfileParser.extras(response) }
        }
        val nested = extrasResponse().apply {
            getJSONObject("soundPower").getJSONObject("data").put("code", 1000)
        }
        assertThrows(MusicProviderException::class.java) { QqAccountProfileParser.extras(nested) }
    }
}
