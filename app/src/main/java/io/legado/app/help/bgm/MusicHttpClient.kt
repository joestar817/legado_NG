package io.legado.app.help.bgm

import io.legado.app.help.http.await
import kotlinx.coroutines.CancellationException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Account requests never enter the book-source cookie jar or network log. */
internal class MusicHttpClient(
    private val platform: MusicPlatform,
    private val allowedDomains: Set<String>,
) {
    private val cookies = mutableListOf<Cookie>()
    private val jar: CookieJar = object : CookieJar {
        // BridgeInterceptor would replace an explicit Cookie header. Merge it below instead.
        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

        override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) {
            synchronized(cookies) {
                if (cleanup.isClosed) return
                received.forEach { next ->
                    cookies.removeAll {
                        it.name == next.name && it.domain == next.domain && it.path == next.path
                    }
                    if (next.expiresAt > System.currentTimeMillis()) cookies.add(next)
                }
            }
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .cookieJar(jar)
        .retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val original = chain.request()
            checkUrl(original.url)
            val merged = linkedMapOf<String, String>()
            synchronized(cookies) {
                cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
                cookies.filter { it.matches(original.url) }.forEach { merged[it.name] = it.value }
            }
            val origin = original.tag(AccountOrigin::class.java)
            origin?.cookieHeader?.takeIf { origin.host == original.url.host }
                ?.split(';')?.forEach { item ->
                val split = item.indexOf('=')
                if (split > 0) merged[item.substring(0, split).trim()] = item.substring(split + 1).trim()
            }
            val request = original.newBuilder().apply {
                removeHeader("Cookie")
                if (merged.isNotEmpty()) header("Cookie", merged.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }.build()
            chain.proceed(request)
        }.build()
    private val noRedirectClient: OkHttpClient = client.newBuilder().followRedirects(false)
        .followSslRedirects(false).build()
    private val cleanup: MusicHttpCleanup = MusicHttpCleanup(
        clearCookies = ::clearCookies,
        cancelCalls = client.dispatcher::cancelAll,
        evictConnections = client.connectionPool::evictAll,
        shutdownExecutor = client.dispatcher.executorService::shutdown,
    )

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): MusicHttpResponse = request(url, null, headers, followRedirects)

    suspend fun postForm(
        url: String,
        fields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): MusicHttpResponse {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return request(url, form, headers, followRedirects)
    }

    suspend fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): MusicHttpResponse = request(
        url, body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()),
        headers, followRedirects,
    )

    private suspend fun request(
        url: String, body: RequestBody?, headers: Map<String, String>, followRedirects: Boolean,
    ): MusicHttpResponse {
        try {
            ensureOpen()
            val request = Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> header(key, value) }
                if (body == null) get() else post(body)
            }.build().let { request -> request.newBuilder()
                .tag(AccountOrigin::class.java, AccountOrigin(request.url.host, request.header("Cookie"))).build() }
            checkUrl(request.url)
            val selected = if (followRedirects) client else noRedirectClient
            return selected.newCall(request).await().use { response ->
                ensureOpen()
                val content = response.body
                if (content.contentLength() > MAX_BODY) throw IOException("body size")
                val source = content.source()
                source.request(MAX_BODY + 1)
                if (source.buffer.size > MAX_BODY) throw IOException("body size")
                val result = MusicHttpResponse(response.code, response.headers, source.readByteArray())
                ensureOpen()
                if (response.code !in 200..399) {
                    throw MusicProviderException("${platform.label}请求失败（${response.code}）", response.code)
                }
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MusicProviderException) {
            throw e
        } catch (_: Exception) {
            throw MusicProviderException("${platform.label}网络请求失败，请稍后重试")
        }
    }

    private fun checkUrl(url: HttpUrl) {
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.port != 443 ||
            allowedDomains.none { url.host == it || url.host.endsWith(".$it") }
        ) throw IOException("platform origin")
    }

    fun cookie(name: String, host: String? = null): String = synchronized(cookies) {
        cookies.lastOrNull {
            it.name == name && it.expiresAt > System.currentTimeMillis() &&
                (host == null || host == it.domain || host.endsWith(".${it.domain}"))
        }?.value.orEmpty()
    }

    fun snapshotCookies(): JSONArray = synchronized(cookies) {
        JSONArray().apply {
            cookies.filter { it.expiresAt > System.currentTimeMillis() }.forEach { cookie ->
                put(JSONObject().put("url", "https://${cookie.domain}${cookie.path}")
                    .put("cookie", cookie.toString()))
            }
        }
    }

    fun restoreCookies(saved: JSONArray?) {
        synchronized(cookies) {
            if (cleanup.isClosed) return
            cookies.clear()
            if (saved == null) return
            for (index in 0 until saved.length()) {
                val item = saved.optJSONObject(index) ?: continue
                val origin = runCatching { Request.Builder().url(item.getString("url")).build().url }.getOrNull()
                    ?: continue
                if (runCatching { checkUrl(origin) }.isFailure) continue
                Cookie.parse(origin, item.optString("cookie"))?.let { cookie ->
                    if (cookie.expiresAt > System.currentTimeMillis()) cookies.add(cookie)
                }
            }
        }
    }

    fun clearCookies() = synchronized(cookies) { cookies.clear() }

    /** Only for disposable authentication contexts, never the shared provider client. */
    fun close() {
        cleanup.close()
    }

    private fun ensureOpen() {
        if (cleanup.isClosed) throw CancellationException("Music authentication context closed")
    }

    private data class AccountOrigin(val host: String, val cookieHeader: String?)
    private companion object { const val MAX_BODY = 8L * 1024 * 1024 }
}

internal data class MusicHttpResponse(val status: Int, val headers: Headers, val body: ByteArray) {
    fun text(): String = body.toString(Charsets.UTF_8)
    fun json(): JSONObject = JSONObject(text())
}
