package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicLoginState
import io.legado.app.help.bgm.MusicProviderException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QqWechatLoginProtocolTest {
    @Test fun authorizationUsesQqMusicWebAppAndAnEncodedRedirect() {
        val url = QqWechatLoginProtocol.authorizationUrl("session-test").toHttpUrl()
        assertEquals("open.weixin.qq.com", url.host)
        assertEquals("wx48db31d50e334801", url.queryParameter("appid"))
        assertEquals("session-test", url.queryParameter("state"))
        assertEquals("https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/",
            url.queryParameter("redirect_uri"))
    }

    @Test fun identifiersCannotEscapeTheFixedQrEndpoint() {
        assertEquals("sample_uuid-123", QqWechatLoginProtocol.identifier("<img src=\"/connect/qrcode/sample_uuid-123\">"))
        assertEquals("sample_uuid-123", QqWechatLoginProtocol.identifier("\"?uuid=sample_uuid-123\""))
        assertThrows(MusicProviderException::class.java) {
            QqWechatLoginProtocol.identifier("uuid=../../external/path\"")
        }
    }

    @Test fun waitingConfirmationExpiryAndRefusalAreDistinct() {
        val states = mapOf(408 to MusicLoginState.WAITING_SCAN, 404 to MusicLoginState.WAITING_CONFIRMATION,
            402 to MusicLoginState.EXPIRED, 403 to MusicLoginState.FAILED)
        states.forEach { (code, state) ->
            assertEquals(state to "", QqWechatLoginProtocol.result("window.wx_errcode=$code;window.wx_code='';"))
        }
    }

    @Test fun completionRequiresAnAuthorizationCodeAndUnknownStatesFailClosed() {
        assertEquals(MusicLoginState.AUTHENTICATED to "test_code-123",
            QqWechatLoginProtocol.result("window.wx_errcode = 405; window.wx_code='test_code-123';"))
        listOf("window.wx_errcode=405;window.wx_code='';", "window.wx_errcode=500;", "garbage").forEach {
            assertThrows(MusicProviderException::class.java) { QqWechatLoginProtocol.result(it) }
        }
    }
}
