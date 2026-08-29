package io.legado.app.help.http

import android.text.TextUtils
import androidx.annotation.Keep
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Cookie as CookieEntity
import io.legado.app.data.dao.deleteByUrlsChunked
import io.legado.app.help.CacheManager
import io.legado.app.help.http.api.CookieManagerInterface
import io.legado.app.help.source.BookSourceStorageScope
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.removeCookie
import io.legado.app.utils.splitNotBlank
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.Connection

@Keep
@Suppress("unused")
class BookSourceCookieStore(
    sourceUrl: String
) : CookieManagerInterface {

    private val namespace = BookSourceStorageScope.namespace(sourceUrl)
    private val storagePrefix = "book_source_cookie_$namespace:"

    private fun domain(url: String): String = NetworkUtils.getSubDomain(url)

    private fun storageKey(url: String): String = storagePrefix + domain(url)

    private fun persistentMemoryKey(url: String): String = storageKey(url) + ":persistent"

    private fun sessionMemoryKey(url: String): String = storageKey(url) + ":session"

    override fun setCookie(url: String, cookie: String?) {
        try {
            val value = cookie.orEmpty()
            val storageKey = storageKey(url)
            CacheManager.putMemory(persistentMemoryKey(url), value)
            appDb.cookieDao.insert(CookieEntity(storageKey, value))
        } catch (e: Exception) {
            AppLog.put("保存书源Cookie失败\n$e", e)
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(cookie)) return
        val oldCookie = getCookieNoSession(url)
        if (oldCookie.isEmpty()) {
            setCookie(url, cookie)
        } else {
            val cookieMap = cookieToMap(oldCookie)
            cookieMap.putAll(cookieToMap(cookie))
            setCookie(url, mapToCookie(cookieMap))
        }
    }

    override fun getCookie(url: String): String {
        val cookieMap = CookieManager.mergeCookiesToMap(
            getCookieNoSession(url),
            CacheManager.getFromMemory(sessionMemoryKey(url)) as? String
        )
        var cookie = mapToCookie(cookieMap).orEmpty()
        while (cookie.length > 4096 && cookieMap.isNotEmpty()) {
            cookieMap.remove(cookieMap.keys.random())
            cookie = mapToCookie(cookieMap).orEmpty()
        }
        return cookie
    }

    override fun removeCookie(url: String) {
        appDb.cookieDao.delete(storageKey(url))
        CacheManager.deleteMemory(persistentMemoryKey(url))
        CacheManager.deleteMemory(sessionMemoryKey(url))
    }

    override fun setWebCookie(url: String, cookie: String) {
        replaceCookie(url, cookie)
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> {
        return CookieStore.cookieToMap(cookie)
    }

    override fun mapToCookie(cookieMap: Map<String, String>?): String? {
        return CookieStore.mapToCookie(cookieMap)
    }

    fun saveResponse(response: Response) {
        var current: Response? = response
        while (current != null) {
            saveCookiesFromHeaders(current.request.url, current.headers)
            current = current.priorResponse
        }
    }

    fun saveResponse(response: Connection.Response) {
        val url = response.url().toString().toHttpUrlOrNull() ?: return
        val headers = Headers.Builder().apply {
            response.multiHeaders().forEach { (name, values) ->
                values.forEach { value -> add(name, value) }
            }
        }.build()
        saveCookiesFromHeaders(url, headers)
    }

    fun applyToWebView(cookieUrl: String, targetUrl: String) {
        val baseUrl = NetworkUtils.getBaseUrl(targetUrl) ?: return
        val manager = android.webkit.CookieManager.getInstance()
        manager.removeCookie(targetUrl)
        getCookie(cookieUrl).splitNotBlank(";").forEach { cookie ->
            manager.setCookie(baseUrl, cookie)
        }
        manager.flush()
    }

    fun captureFromWebView(pageUrl: String, storageUrl: String = pageUrl) {
        val cookie = android.webkit.CookieManager.getInstance().getCookie(pageUrl)
        setCookie(storageUrl, cookie.orEmpty())
    }

    private fun saveCookiesFromHeaders(url: HttpUrl, headers: Headers) {
        val cookies = Cookie.parseAll(url, headers)
        if (cookies.isEmpty()) return
        val persistent = cookies.filter { it.persistent }.toCookieString()
        if (persistent.isNotEmpty()) {
            replaceCookie(url.toString(), persistent)
        }
        val session = cookies.filterNot { it.persistent }.toCookieString()
        if (session.isNotEmpty()) {
            val oldSession = CacheManager.getFromMemory(sessionMemoryKey(url.toString())) as? String
            CookieManager.mergeCookies(oldSession, session)?.let { merged ->
                CacheManager.putMemory(sessionMemoryKey(url.toString()), merged)
            }
        }
    }

    private fun getCookieNoSession(url: String): String {
        val memoryKey = persistentMemoryKey(url)
        val memoryCookie = CacheManager.getFromMemory(memoryKey) as? String
        if (memoryCookie != null) return memoryCookie
        return appDb.cookieDao.get(storageKey(url))?.cookie.orEmpty().also { cookie ->
            CacheManager.putMemory(memoryKey, cookie)
        }
    }

    private fun List<Cookie>.toCookieString(): String {
        return joinToString("; ") { "${it.name}=${it.value}" }
    }

    internal companion object {
        fun forSource(source: BaseSource?): CookieManagerInterface {
            return (source as? BookSource)?.let {
                BookSourceCookieStore(it.bookSourceUrl)
            } ?: CookieStore
        }

        fun forBookSource(source: BaseSource?): BookSourceCookieStore? {
            return (source as? BookSource)?.let {
                BookSourceCookieStore(it.bookSourceUrl)
            }
        }

        fun clear(sourceUrl: String) {
            clear(listOf(sourceUrl))
        }

        fun clear(sourceUrls: Collection<String>) {
            if (sourceUrls.isEmpty()) return
            val prefixes = sourceUrls.distinct().map { sourceUrl ->
                val namespace = BookSourceStorageScope.namespace(sourceUrl)
                "book_source_cookie_$namespace:"
            }
            val scopedUrls = appDb.cookieDao.allUrls().filter { url ->
                url.startsWith("book_source_cookie_") && prefixes.any(url::startsWith)
            }
            appDb.cookieDao.deleteByUrlsChunked(scopedUrls)
            CacheManager.deleteMemoryByPrefixes(prefixes)
        }
    }
}
