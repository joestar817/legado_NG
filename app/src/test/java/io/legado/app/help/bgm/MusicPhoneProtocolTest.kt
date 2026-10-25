package io.legado.app.help.bgm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MusicPhoneProtocolTest {
    private val session get() = MusicPhoneLoginSession("13000000000", "+86")

    @Test
    fun neteaseRequestsUsePhoneCaptchaWithoutPasswordOrRegistration() {
        val send = MusicPhoneProtocol.neteaseSend(session)
        assertEquals("86", send.getString("ctcode"))
        assertEquals("13000000000", send.getString("cellphone"))
        assertEquals("music_middleuser_pclogin", send.getString("secrete"))
        val login = MusicPhoneProtocol.neteaseLogin(session, "123456")
        assertEquals("13000000000", login.getString("phone"))
        assertEquals("123456", login.getString("captcha"))
        assertEquals("86", login.getString("countrycode"))
        assertFalse(login.has("password"))
    }

    @Test
    fun qqPlainPhoneIsNotSentAsEncryptedPhone() {
        val send = MusicPhoneProtocol.qqSend(session)
        assertEquals("qqmusic", send.getString("tmeAppid"))
        assertEquals("86", send.getString("areaCode"))
        assertEquals("13000000000", send.getString("phoneNo"))
        assertFalse(send.has("encryptedPhoneNo"))
        val login = MusicPhoneProtocol.qqLogin(session, "123456")
        assertEquals(1, login.getInt("loginMode"))
        assertEquals("123456", login.getString("code"))
        assertEquals("13000000000", login.getString("phoneNo"))
    }

    @Test
    fun qqChallengeAndThrottleNeverCountAsSent() {
        val challenge = MusicPhoneProtocol.qqSendResult(JSONObject()
            .put("code", 20276).put("data", JSONObject().put("securityURL", "https://ssl.captcha.qq.com/check")))
        assertEquals(MusicPhoneCodeState.SECURITY_REQUIRED, challenge.state)
        assertEquals("https://ssl.captcha.qq.com/check", challenge.securityUrl)
        assertEquals(MusicPhoneCodeState.RATE_LIMITED,
            MusicPhoneProtocol.qqSendResult(JSONObject().put("code", 100001)).state)
        assertEquals(MusicPhoneCodeState.SENT,
            MusicPhoneProtocol.qqSendResult(JSONObject().put("code", 0)).state)
    }

    @Test
    fun securityLinkRejectsOffPlatformOrUnsafeOrigins() {
        listOf("http://captcha.qq.com/check", "https://qq.com.evil.test/check",
            "https://user:password@qq.com/check", "https://captcha.qq.com:8443/check",
            "javascript:alert(1)").forEach {
            assertEquals("", MusicPhoneProtocol.safeSecurityUrl(it, MusicPlatform.QQ))
        }
    }

    @Test
    fun qqCredentialTypeFollowsServerOrUpstreamKeyInference() {
        assertEquals(0, MusicPhoneProtocol.qqLoginType(JSONObject().put("loginType", 0).put("musickey", "W_X_test")))
        assertEquals(1, MusicPhoneProtocol.qqLoginType(JSONObject().put("musickey", "W_X_test")))
        assertEquals(2, MusicPhoneProtocol.qqLoginType(JSONObject().put("musickey", "Q_H_test")))
    }

    @Test
    fun qqRefreshUsesCredentialTypeSpecificFields() {
        val wx = MusicPhoneProtocol.qqRefreshParameters(JSONObject().put("loginType", 1)
            .put("musicid", 123).put("unionid", "fixture"))
        assertEquals("123", wx.getString("str_musicid"))
        assertEquals("fixture", wx.getString("unionid"))
        assertFalse(wx.has("access_token"))
        val qq = MusicPhoneProtocol.qqRefreshParameters(JSONObject().put("loginType", 2).put("musicid", 123))
        assertEquals(123, qq.getInt("musicid"))
        assertFalse(qq.has("unionid"))
        val phone = MusicPhoneProtocol.qqRefreshParameters(JSONObject().put("loginType", 0).put("musicid", 123))
        assertTrue(phone.has("unionid"))
        assertTrue(phone.has("access_token"))
    }

    @Test
    fun neteaseAccountChallengeIsNotReportedAsCodeSent() {
        assertEquals(MusicPhoneCodeState.SECURITY_REQUIRED, MusicPhoneProtocol.neteaseSendResult(
            JSONObject().put("code", 400).put("message", "请完成安全验证")).state)
        assertEquals(MusicPhoneCodeState.RATE_LIMITED, MusicPhoneProtocol.neteaseSendResult(
            JSONObject().put("code", 400).put("message", "发送频繁")).state)
        assertEquals(MusicPhoneCodeState.SENT, MusicPhoneProtocol.neteaseSendResult(
            JSONObject().put("code", 200).put("data", true)).state)
    }

    @Test(expected = MusicProviderException::class)
    fun malformedCodeIsRejectedBeforeRequest() {
        MusicPhoneProtocol.qqLogin(session, "invalid-code")
    }
}
