package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicAccountProfile
import io.legado.app.help.bgm.MusicAccountExtras
import io.legado.app.help.bgm.MusicMembershipInfo
import io.legado.app.help.bgm.MusicProviderException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Only documented fields of the authenticated QQ Music account are presented. */
internal object QqAccountProfileParser {
    fun profile(userId: String, encryptedUin: String, response: JSONObject): MusicAccountProfile {
        checkEnvelope(response)
        if (data(response, "account") == null) throw MusicProviderException("登录状态暂不可用，请重试")
        val home = data(response, "home")?.optJSONObject("Info")?.optJSONObject("BaseInfo")
        val gene = data(response, "gene")?.optJSONObject("UserInfoCard")
        if (home == null && gene == null) throw MusicProviderException("账号资料暂不可用")
        if (encryptedUin.isBlank() || userId.toLongOrNull()?.let { it > 0 } != true ||
            (home != null && home.text("EncryptedUin") != encryptedUin) ||
            (gene != null && gene.text("EncryptionAccount") != encryptedUin)) {
            throw MusicProviderException("账号资料不匹配，请重试")
        }
        val following = relationCount(data(response, "following"))
        val fans = relationCount(data(response, "fans"))
        return MusicAccountProfile(
            userId = userId,
            nickname = home?.text("Name").orEmpty().ifBlank { gene?.text("NickName").orEmpty() },
            avatarUrl = imageUrl(home?.text("Avatar").orEmpty())
                .ifBlank { imageUrl(gene?.text("HeadUrl").orEmpty()) },
            signature = gene?.text("Signature").orEmpty(),
            followingCount = following,
            followerCount = fans,
            detailsLoaded = gene != null && following != null && fans != null,
        )
    }

    fun extras(response: JSONObject): MusicAccountExtras {
        checkEnvelope(response)
        val vip = data(response, "membership")
        val soundPower = data(response, "soundPower")?.optJSONObject("powerInfo")
        val musicLevel = soundPower?.count("level")?.takeIf { it <= Int.MAX_VALUE }?.toInt()
        var membershipLoaded = true
        val membership = try {
            membership(vip ?: throw MusicProviderException("会员信息暂不可用"))
        } catch (_: MusicProviderException) {
            membershipLoaded = false
            null
        }
        return MusicAccountExtras(
            membership = membership,
            musicLevel = musicLevel,
            musicLevelIconUrl = if (musicLevel != null) imageUrl(soundPower?.text("icon").orEmpty()) else "",
            membershipLoaded = membershipLoaded,
            musicLevelLoaded = musicLevel != null,
        )
    }

    fun membership(response: JSONObject): MusicMembershipInfo? {
        val identity = response.optJSONObject("identity")
            ?: throw MusicProviderException("会员信息暂不可用")
        val flags = listOf(response.count("svip"), response.count("star"),
            identity.count("HugeVip"), identity.count("vip"))
        if (flags.all { it == null }) {
            throw MusicProviderException("会员信息暂不可用")
        }
        val isActive = flags.any { it != null && it > 0 }
        val level = identity.count("level")?.takeIf { it <= Int.MAX_VALUE }?.toInt() ?: 0
        // Keep a reported historical level, but never present it as an active subscription.
        if (!isActive && level == 0) return null
        val label = when {
            response.count("svip")?.let { it > 0 } == true -> "超级会员"
            identity.count("HugeVip")?.let { it > 0 } == true -> "豪华绿钻"
            identity.count("vip")?.let { it > 0 } == true -> "绿钻"
            response.count("star")?.let { it > 0 } == true -> "星级会员"
            else -> "VIP"
        }
        return MusicMembershipInfo(
            level = level,
            iconUrl = imageUrl(identity.text("icon")),
            label = if (level > 0) "$label Lv.$level" else label,
            isActive = isActive,
        )
    }

    private fun checkEnvelope(response: JSONObject) {
        val code = response.optInt("code", -1)
        if (code != 0) throw MusicProviderException("QQ 音乐资料读取失败（$code）", code)
    }

    private fun data(response: JSONObject, name: String): JSONObject? {
        val item = response.optJSONObject(name) ?: return null
        val code = item.optInt("code", -1)
        val value = item.optJSONObject("data")
        val businessCode = value?.optInt("code", 0) ?: 0
        val authError = listOf(code, businessCode).firstOrNull { it in setOf(1000, 104400, 104401) }
        if (authError != null) {
            throw MusicProviderException("QQ 音乐登录已失效，请重新登录", authError)
        }
        return value?.takeIf { code == 0 && businessCode == 0 }
    }

    private fun relationCount(value: JSONObject?): Long? = value?.takeIf {
        it.optInt("LockFlag", 0) == 0
    }?.count("Total")

    private fun imageUrl(value: String): String = value.toHttpUrlOrNull()?.takeIf { url ->
        url.username.isEmpty() && url.password.isEmpty() &&
            ((url.isHttps && url.port == 443) || (url.scheme == "http" && url.port == 80)) &&
            setOf("qq.com", "gtimg.cn", "qpic.cn", "qlogo.cn").any {
                url.host == it || url.host.endsWith(".$it")
            }
    }?.toString().orEmpty()

    private fun JSONObject.text(key: String): String = (opt(key) as? String)?.trim().orEmpty()
    private fun JSONObject.count(key: String): Long? = opt(key)?.toString()?.toLongOrNull()?.takeIf { it >= 0 }
}
