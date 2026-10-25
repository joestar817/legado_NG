package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicLoginState
import io.legado.app.help.bgm.MusicProviderException
import okhttp3.HttpUrl.Companion.toHttpUrl

/** QQ Music's web WeChat grant. QR identifiers and authorization codes stay in memory. */
internal object QqWechatLoginProtocol {
    const val APP_ID = "wx48db31d50e334801"

    fun authorizationUrl(state: String): String = "https://open.weixin.qq.com/connect/qrconnect"
        .toHttpUrl().newBuilder()
        .addQueryParameter("appid", APP_ID)
        .addQueryParameter("redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/")
        .addQueryParameter("response_type", "code")
        .addQueryParameter("scope", "snsapi_login")
        .addQueryParameter("state", state)
        .build().toString()

    fun identifier(html: String): String =
        Regex("(?:uuid=|/connect/qrcode/)([A-Za-z0-9_=-]{8,128})(?=[\"'&<>\\s])")
            .find(html)?.groupValues?.get(1)
            ?: throw MusicProviderException("获取微信登录二维码失败")

    fun result(text: String): Pair<MusicLoginState, String> {
        val status = Regex("window\\.wx_errcode\\s*=\\s*(\\d+)\\s*;")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        val state = when (status) {
            408 -> MusicLoginState.WAITING_SCAN
            404 -> MusicLoginState.WAITING_CONFIRMATION
            402 -> MusicLoginState.EXPIRED
            403 -> MusicLoginState.FAILED
            405 -> MusicLoginState.AUTHENTICATED
            else -> throw MusicProviderException("微信登录状态异常，请刷新二维码")
        }
        val code = if (state == MusicLoginState.AUTHENTICATED) {
            Regex("window\\.wx_code\\s*=\\s*['\"]([A-Za-z0-9_-]{1,512})['\"]")
                .find(text)?.groupValues?.get(1)
                ?: throw MusicProviderException("微信授权未完成，请刷新二维码")
        } else ""
        return state to code
    }
}
