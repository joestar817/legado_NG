package io.legado.app.help.bgm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Request fields from the same upstream versions as the existing music adapters. */
internal object MusicPhoneProtocol {
    fun neteaseSend(session: MusicPhoneLoginSession): JSONObject = JSONObject()
        .put("ctcode", session.countryCode).put("cellphone", session.phone)
        .put("secrete", "music_middleuser_pclogin")

    fun neteaseLogin(session: MusicPhoneLoginSession, code: String): JSONObject = JSONObject()
        .put("type", "1").put("https", "true").put("phone", session.phone)
        .put("countrycode", session.countryCode).put("captcha", checkedCode(code))
        .put("remember", "true").put("secureCaptcha", "")

    fun qqSend(session: MusicPhoneLoginSession): JSONObject = JSONObject()
        .put("tmeAppid", "qqmusic").put("areaCode", session.countryCode).put("phoneNo", session.phone)

    fun qqLogin(session: MusicPhoneLoginSession, code: String): JSONObject = JSONObject()
        .put("code", checkedCode(code)).put("loginMode", 1).put("phoneNo", session.phone)

    fun neteaseSendResult(response: JSONObject): MusicPhoneCodeResult {
        if (response.optInt("code", -1) == 200 && response.opt("data") != false) {
            return MusicPhoneCodeResult(MusicPhoneCodeState.SENT, "验证码已发送")
        }
        val message = response.optString("message").ifBlank { response.optString("msg") }
        if (message.contains("频繁")) {
            return MusicPhoneCodeResult(MusicPhoneCodeState.RATE_LIMITED, "操作过于频繁，请稍后重试")
        }
        if (listOf("安全", "实名", "验证", "绑定").any(message::contains)) {
            return MusicPhoneCodeResult(MusicPhoneCodeState.SECURITY_REQUIRED, "需要验证账号，请在网易云音乐完成后重试")
        }
        val code = response.optInt("code", -1)
        throw MusicProviderException("网易云验证码发送失败（$code），请在官方客户端检查账号", code)
    }

    fun neteaseLoginError(response: JSONObject): MusicProviderException {
        val code = response.optInt("code", -1)
        val message = response.optString("message").ifBlank { response.optString("msg") }
        val reason = when {
            message.contains("频繁") -> "操作过于频繁，请稍后重试"
            message.contains("验证码") -> "验证码错误或已失效，请重新获取"
            else -> "网易云登录失败（$code），请在官方客户端完成账号或安全验证"
        }
        return MusicProviderException(reason, code)
    }

    fun qqSendResult(response: JSONObject): MusicPhoneCodeResult {
        val code = response.optInt("code", -1)
        return when (code) {
            0 -> MusicPhoneCodeResult(MusicPhoneCodeState.SENT, "验证码已发送")
            20276 -> MusicPhoneCodeResult(MusicPhoneCodeState.SECURITY_REQUIRED,
                "请先完成安全验证",
                safeSecurityUrl(response.optJSONObject("data")?.optString("securityURL").orEmpty(), MusicPlatform.QQ))
            100001 -> MusicPhoneCodeResult(MusicPhoneCodeState.RATE_LIMITED, "操作过于频繁，请稍后重试")
            else -> throw MusicProviderException("QQ 音乐验证码发送失败（$code），请在官方客户端检查账号", code)
        }
    }

    fun qqLoginError(code: Int): MusicProviderException = MusicProviderException(when (code) {
        20271 -> "验证码错误或已失效，请重新获取"
        20272, 20274 -> "账号绑定未完成，请在 QQ 音乐处理后重试"
        20276 -> "需要安全验证，请在 QQ 音乐完成后重试"
        20277, 20278, 20450 -> "账号受限，请在 QQ 音乐完成身份或安全验证"
        20279 -> "登录设备数量已达限制，请在 QQ 音乐管理设备"
        100001, 104604 -> "操作过于频繁，请稍后重试"
        else -> "QQ 音乐登录失败（$code），请在官方客户端检查账号"
    }, code)

    fun qqLoginType(credential: JSONObject): Int = if (credential.has("loginType")) {
        credential.optInt("loginType")
    } else if (credential.optString("musickey").startsWith("W_X")) 1 else 2

    fun qqRefreshParameters(credential: JSONObject): JSONObject {
        val type = qqLoginType(credential)
        return JSONObject().put("openid", credential.optString("openid"))
            .put("refresh_token", credential.optString("refresh_token"))
            .put("musickey", credential.optString("musickey"))
            .put("refresh_key", credential.optString("refresh_key")).put("loginMode", 2).apply {
                if (type != 1) {
                    put("access_token", credential.optString("access_token"))
                    put("expired_in", credential.optLong("expired_at"))
                    put("musicid", credential.optLong("musicid"))
                }
                if (type != 2) {
                    put("str_musicid", credential.optString("str_musicid").ifBlank { credential.optString("musicid") })
                    put("unionid", credential.optString("unionid"))
                }
            }
    }

    fun safeSecurityUrl(raw: String, platform: MusicPlatform): String {
        val url = raw.toHttpUrlOrNull() ?: return ""
        val roots = if (platform == MusicPlatform.QQ) setOf("qq.com", "tencentmusic.com")
            else setOf("163.com", "126.net")
        return raw.takeIf { url.isHttps && url.port == 443 && url.username.isEmpty() &&
            url.password.isEmpty() && roots.any { url.host == it || url.host.endsWith(".$it") } }.orEmpty()
    }

    private fun checkedCode(code: String): String = code.trim().also {
        if (!it.matches(Regex("[0-9]{4,8}"))) throw MusicProviderException("请输入正确的验证码")
    }
}
