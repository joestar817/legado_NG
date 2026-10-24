package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicProviderException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QqPhoneVerificationTest {
    private val source = "https://y.qq.com/m/client/safety_captcha/index.html?validatetype=10&captype=8&tokenValid=fixture"
    private fun callback(flow: QqPhoneVerification) = flow.startUrl.toHttpUrl().queryParameter("redirectUrl")!!

    @Test fun keepsServerChallengeParametersAndReplacesOnlyTheSuccessDestination() {
        val flow = QqPhoneVerification("phone-session", "$source&redirectUrl=https%3A%2F%2Fy.qq.com%2Fm%2Fclient%2F")
        val url = flow.startUrl.toHttpUrl()
        assertEquals("10", url.queryParameter("validatetype"))
        assertEquals("fixture", url.queryParameter("tokenValid"))
        assertEquals("8", url.queryParameter("captype"))
        assertEquals(1, url.queryParameterValues("redirectUrl").size)
        assertEquals("bgm-phone-verification.invalid", callback(flow).toHttpUrl().host)
        assertEquals(flow.startUrl, flow.pageUrl(flow.startUrl))
    }

    @Test fun successIsBoundToTheCurrentMainFrameAndConsumedOnce() {
        val flow = QqPhoneVerification("session", source)
        assertFalse(flow.acceptCompletion(callback(flow), source, true))
        assertFalse(flow.acceptCompletion(callback(flow), flow.startUrl, false))
        assertFalse(flow.acceptCompletion(callback(flow), "https://untrusted.test/", true))
        assertTrue(flow.acceptCompletion(callback(flow), flow.startUrl, true))
        assertFalse(flow.acceptCompletion(callback(flow), flow.startUrl, true))
    }

    @Test fun cancelledAndReplacedChallengesCannotComplete() {
        val old = QqPhoneVerification("session", source)
        val current = QqPhoneVerification("session", source)
        assertNotEquals(callback(old), callback(current))
        assertFalse(current.acceptCompletion(callback(old), current.startUrl, true))
        old.cancel()
        assertFalse(old.acceptCompletion(callback(old), old.startUrl, true))
    }

    @Test fun supportsOnlyTheOriginalServerRedirectAndTheCaptchaPage() {
        val initial = "https://c.y.qq.com/safety/redirect?fixture=1"
        val flow = QqPhoneVerification("session", initial)
        assertEquals(initial, flow.startUrl)
        assertTrue(flow.pageUrl(source)!!.contains("redirectUrl="))
        assertNull(flow.pageUrl("https://c.y.qq.com/other"))
        assertNull(flow.pageUrl("https://y.qq.com/portal/"))
    }

    @Test fun refusesDownloadAndAppLaunchTargetsInEveryFrame() {
        val flow = QqPhoneVerification("session", source)
        listOf("https://y.qq.com/m/client/", "https://y.qq.com/m/client", "https://y.qq.com/download/music.APK",
            "https://dldir1.qq.com/music", "https://a.app.qq.com/o/simple.jsp",
            "qqmusic://qq.com/ui/openUrl", "intent://client/#Intent;scheme=qqmusic;end",
            "market://details?id=com.tencent.qqmusic", "file:///sdcard/music.apk", "content://downloads/music").forEach {
            assertNull(flow.pageUrl(it))
            assertFalse(flow.allowsResource(it, true))
            assertFalse(flow.allowsResource(it, false))
        }
    }

    @Test fun allowsOfficialCaptchaAssetsButNotForeignOriginsOrCleartext() {
        val flow = QqPhoneVerification("session", source)
        listOf("https://y.qq.com/music/safety-web/safety-captcha/index.js",
            "https://y.gtimg.cn/music/safety-web/safety-captcha/index.css",
            "https://turing.captcha.qcloud.com/TJCaptcha.js",
            "https://safety.music.qq.com/cgi/fcgi-bin/fcg_music_validate").forEach {
            assertTrue(flow.allowsResource(it, false))
            assertFalse(flow.allowsResource(it, true))
        }
        listOf("http://y.qq.com/captcha", "https://qq.com.attacker.test/a.js", "https://user:pass@y.qq.com/a.js",
            "https://y.qq.com:8443/a.js").forEach { assertFalse(flow.allowsResource(it, false)) }
    }

    @Test fun rejectsUnsafeInitialAddresses() {
        listOf("https://y.qq.com/m/client/", "https://qq.com.attacker.test/captcha",
            "http://y.qq.com/m/client/safety_captcha/index.html", "https://y.qq.com/app.apk",
            "https://user:pass@y.qq.com/m/client/safety_captcha/index.html").forEach {
            assertThrows(MusicProviderException::class.java) { QqPhoneVerification("session", it) }
        }
    }
}
