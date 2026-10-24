package io.legado.app.help.bgm

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** Ephemeral phone-login state, owned by a visible login dialog; never persisted. */
class MusicPhoneLoginSession(phone: String, countryCode: String = "86") {
    val phone = phone.trim()
    val countryCode = countryCode.trim().removePrefix("+")
    internal val id: String = UUID.randomUUID().toString()
    private var platform: MusicPlatform? = null
    private var cancelled = false
    private var committed = false
    private var codeSent = false

    @Synchronized
    internal fun bind(platform: MusicPlatform) {
        requireActive()
        if (!phone.matches(Regex("[0-9]{5,15}")) || !countryCode.matches(Regex("[0-9]{1,4}"))) {
            throw MusicProviderException("请输入正确的手机号和区号")
        }
        if (this.platform != null && this.platform != platform) {
            throw MusicProviderException("登录方式已变化，请重新获取验证码")
        }
        this.platform = platform
    }

    @Synchronized
    internal fun requireActive(requireCode: Boolean = false) {
        if (cancelled || committed) throw CancellationException("音乐登录已结束")
        if (requireCode && !codeSent) throw MusicProviderException("请先获取验证码")
    }

    @Synchronized
    internal fun markCodeSent(sent: Boolean) {
        requireActive()
        codeSent = sent
    }

    @Synchronized
    internal fun commitIfActive(commit: () -> Unit): Boolean {
        if (cancelled || committed) return false
        commit()
        committed = true
        return true
    }

    @Synchronized
    fun cancel() {
        if (!committed) cancelled = true
    }
}

enum class MusicPhoneCodeState {
    @SerializedName("sent") SENT,
    @SerializedName("security_required") SECURITY_REQUIRED,
    @SerializedName("rate_limited") RATE_LIMITED
}

data class MusicPhoneCodeResult(
    @SerializedName("state") val state: MusicPhoneCodeState,
    @SerializedName("message") val message: String,
    @SerializedName("securityUrl") val securityUrl: String = ""
)
