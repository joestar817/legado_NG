package io.legado.app.help.bgm.qq

import io.legado.app.help.bgm.MusicProviderException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** A single visible captcha, bound to the original phone session and never persisted. */
internal class QqPhoneVerification(val sessionId: String, val sourceUrl: String) {
    val id: String = UUID.randomUUID().toString()
    private val completed = AtomicBoolean(false)
    private val callbackUrl = "https://bgm-phone-verification.invalid/complete/$id"
    private val source = sourceUrl.toHttpUrlOrNull()?.takeIf {
        it.isSafeHttps() && it.host in MUSIC_HOSTS && !isDownload(it)
    } ?: throw MusicProviderException("验证地址暂不支持，请使用扫码登录")
    val startUrl: String = pageUrl(source.toString())
        ?: throw MusicProviderException("验证地址暂不支持，请使用扫码登录")

    fun isCallback(url: String): Boolean = url == callbackUrl

    fun acceptCompletion(url: String, currentPage: String?, isMainFrame: Boolean): Boolean {
        val page = currentPage?.toHttpUrlOrNull() ?: return false
        return isMainFrame && isCallback(url) && page.isCaptchaPage() &&
            page.queryParameter("redirectUrl") == callbackUrl && completed.compareAndSet(false, true)
    }

    fun cancel() { completed.set(true) }

    /** The official page calls redirectUrl only after its verification callback succeeds. */
    fun pageUrl(raw: String): String? {
        val url = raw.toHttpUrlOrNull()?.takeIf { it.isSafeHttps() && !isDownload(it) } ?: return null
        if (url.isCaptchaPage()) {
            return url.newBuilder().setQueryParameter("redirectUrl", callbackUrl).build().toString()
        }
        // Some securityURL responses start with a c.y.qq.com redirect. No other top-level pages.
        return url.toString().takeIf { url == source && url.host == "c.y.qq.com" }
    }

    fun allowsResource(raw: String, isMainFrame: Boolean): Boolean {
        if (isMainFrame) return pageUrl(raw) != null
        if (raw.startsWith("data:") || raw.startsWith("blob:") || raw == "about:blank") return true
        val url = raw.toHttpUrlOrNull()?.takeIf { it.isSafeHttps() && !isDownload(it) } ?: return false
        return RESOURCE_DOMAINS.any { url.host == it || url.host.endsWith(".$it") }
    }

    private fun HttpUrl.isCaptchaPage() = isSafeHttps() && host in MUSIC_HOSTS &&
        encodedPath == "/m/client/safety_captcha/index.html"

    private fun HttpUrl.isSafeHttps() = isHttps && port == 443 && username.isEmpty() && password.isEmpty()

    private fun isDownload(url: HttpUrl): Boolean {
        val path = url.pathSegments.joinToString("/").lowercase()
        return path.endsWith(".apk") || path.endsWith(".xapk") || path.endsWith(".apks") ||
            path.trimEnd('/') == "m/client" || url.host.startsWith("dldir") ||
            url.host in setOf("a.app.qq.com", "sj.qq.com", "dl.stream.qqmusic.qq.com")
    }

    private companion object {
        val MUSIC_HOSTS = setOf("y.qq.com", "c.y.qq.com")
        val RESOURCE_DOMAINS = setOf("qq.com", "gtimg.cn", "gtimg.com", "qcloud.com", "myqcloud.com", "tencentmusic.com")
    }
}
