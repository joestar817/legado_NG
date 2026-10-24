package io.legado.app.help.bgm.netease

import io.legado.app.help.bgm.MusicAccountProfile
import io.legado.app.help.bgm.MusicMembershipInfo
import io.legado.app.help.bgm.MusicProviderException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Only the currently authenticated account may supply the dialog's profile and statistics. */
internal object NeteaseAccountProfileParser {
    fun account(response: JSONObject): MusicAccountProfile {
        val profile = response.optJSONObject("profile") ?: JSONObject()
        val accountId = response.optJSONObject("account")?.positiveId("id")
        val profileId = profile.positiveId("userId")
        val userId = accountId ?: profileId
            ?: throw MusicProviderException("网易云登录已失效，请重新登录")
        if (profileId != null && profileId != userId) {
            throw MusicProviderException("账号资料不匹配，请重试")
        }
        return MusicAccountProfile(
            userId = userId,
            nickname = profile.text("nickname"),
            avatarUrl = profile.text("avatarUrl"),
            signature = profile.text("signature"),
        )
    }

    fun detail(response: JSONObject, account: MusicAccountProfile): MusicAccountProfile {
        val profile = response.optJSONObject("profile")
            ?: throw MusicProviderException("账号资料暂不可用")
        if (profile.positiveId("userId") != account.userId) {
            throw MusicProviderException("账号资料不匹配，请重试")
        }
        return account.copy(
            nickname = profile.text("nickname").ifBlank { account.nickname },
            avatarUrl = profile.text("avatarUrl").ifBlank { account.avatarUrl },
            signature = if (profile.has("signature")) profile.text("signature") else account.signature,
            level = response.count("level")?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
            followingCount = profile.count("follows"),
            followerCount = profile.count("followeds"),
            playlistCount = profile.count("playlistCount"),
            listenSongs = response.count("listenSongs"),
            detailsLoaded = true,
        )
    }

    fun membership(response: JSONObject, userId: String): MusicMembershipInfo? {
        val data = response.optJSONObject("data")
            ?: throw MusicProviderException("会员信息暂不可用")
        // The public response can omit userId; a supplied identity must still match the requested account.
        if (data.has("userId") && data.positiveId("userId") != userId) {
            throw MusicProviderException("会员账号不匹配，请重试")
        }
        val level = data.count("redVipLevel")?.takeIf { it <= Int.MAX_VALUE }?.toInt()
            ?: throw MusicProviderException("会员等级暂不可用")
        if (level == 0) return null
        val icon = data.text("redVipLevelIcon").toHttpUrlOrNull()?.takeIf { url ->
            url.username.isEmpty() && url.password.isEmpty() &&
                ((url.scheme == "https" && url.port == 443) || (url.scheme == "http" && url.port == 80)) &&
                setOf("163.com", "126.net", "127.net", "163yun.com").any { url.host == it || url.host.endsWith(".$it") }
        }?.toString().orEmpty()
        return MusicMembershipInfo(level = level, iconUrl = icon)
    }

    private fun JSONObject.text(key: String): String = (opt(key) as? String)?.trim().orEmpty()

    private fun JSONObject.count(key: String): Long? =
        opt(key)?.toString()?.toLongOrNull()?.takeIf { it >= 0 }

    private fun JSONObject.positiveId(key: String): String? =
        count(key)?.takeIf { it > 0 }?.toString()
}
