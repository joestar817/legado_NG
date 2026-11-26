package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.http.NetworkLog
import io.legado.app.help.http.NetworkLogInterceptor
import io.legado.app.help.http.await
import io.legado.app.help.http.networkLogSource
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.ProtocolException
import java.util.concurrent.TimeUnit

internal val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

internal class AiEndpointException(message: String) : ProtocolException(message)

internal fun HttpUrl.hasUserInfo(): Boolean = username.isNotEmpty() || password.isNotEmpty()

internal fun HttpUrl.sameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private val aiProviderHttpClient by lazy {
    // AI endpoints receive credentials and user content. Use platform trust and
    // check redirect destinations before OkHttp opens the next connection.
    OkHttpClient.Builder()
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.hasUserInfo()) {
                throw AiEndpointException("AI endpoint URL must not contain user information")
            }
            chain.proceed(request)
        }
        .addInterceptor(NetworkLogInterceptor.aiRequests)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val target = if (response.isRedirect) {
                response.header("Location")?.let(request.url::resolve)
            } else null
            if (target != null && (!request.url.sameOriginAs(target) || target.hasUserInfo())) {
                response.close()
                throw AiEndpointException("AI endpoint redirected outside its configured origin")
            }
            response
        }
        .build()
}

internal fun aiHttpClient(timeoutSeconds: Int): OkHttpClient {
    val timeout = timeoutSeconds.toLong().coerceIn(5L, 600L)
    return aiProviderHttpClient.newBuilder()
        .connectTimeout(timeout, TimeUnit.SECONDS)
        .writeTimeout(timeout, TimeUnit.SECONDS)
        .readTimeout(timeout, TimeUnit.SECONDS)
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()
}

internal fun Request.withAiTextLog(params: AiTextParams): Request {
    return newBuilder()
        .tag(NetworkLog.GenerationTrace::class.java, NetworkLog.GenerationTrace())
        .apply { params.networkLogSource?.let { networkLogSource(it) } }
        .build()
}

internal suspend fun <T> Call.readCancellableStream(read: suspend (Response) -> T): T =
    coroutineScope {
        suspendCancellableCoroutine { continuation ->
            // Keep cancellation connected after await() hands off the response body.
            continuation.invokeOnCancellation { cancel() }
            launch(IO) {
                continuation.resumeWith(runCatching { read(await()) })
            }
        }
    }

internal fun String.trimEndSlash(): String = trim().trimEnd('/')

internal fun String.ensureStartSlash(): String {
    return if (startsWith("/")) this else "/$this"
}

internal fun aiProviderHost(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    if (trimmed.isEmpty()) {
        return ""
    }
    val host = runCatching { java.net.URI(trimmed).host }.getOrNull()
    if (!host.isNullOrBlank()) {
        return host
    }
    return trimmed
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
}

internal fun buildAiApiEndpoint(baseUrl: String, apiPath: String): String {
    val base = baseUrl.trim().toHttpUrlOrNull()
        ?.takeUnless { it.hasUserInfo() || it.query != null || it.fragment != null }
        ?: throw AiEndpointException("Invalid AI API base URL")
    val api = apiPath.trim()
    check(api.isNotBlank()) { "API path is empty" }
    val endpoint = if (api.startsWith("http://", ignoreCase = true) ||
        api.startsWith("https://", ignoreCase = true)
    ) {
        api
    } else {
        "${base.toString().trimEnd('/')}${api.ensureStartSlash()}"
    }
    val target = endpoint.toHttpUrlOrNull()
        ?: throw AiEndpointException("Invalid AI API endpoint")
    if (!base.sameOriginAs(target) || target.hasUserInfo()) {
        throw AiEndpointException("AI API endpoint must use the configured Base URL origin")
    }
    return target.toString()
}

internal fun normalizeAiApiPath(baseUrl: String, apiPath: String): String {
    val api = apiPath.trim()
    if (api.isBlank()) {
        return ""
    }
    if (!api.startsWith("http://", ignoreCase = true) &&
        !api.startsWith("https://", ignoreCase = true)
    ) {
        return api.ensureStartSlash()
    }
    val base = baseUrl.trim().toHttpUrlOrNull() ?: return api
    val target = api.toHttpUrlOrNull() ?: return api
    if (!base.sameOriginAs(target) || target.hasUserInfo()) return api
    val basePath = base.encodedPath.trimEnd('/')
    val targetPath = target.encodedPath
    val relative = when {
        targetPath == basePath -> return api
        targetPath.startsWith("$basePath/") -> targetPath.substring(basePath.length)
        else -> return api
    }
    return buildString {
        append(relative)
        target.encodedQuery?.let { append('?').append(it) }
        target.encodedFragment?.let { append('#').append(it) }
    }
}

internal suspend fun OkHttpClient.executeJson(request: Request): JsonObject =
    executeJsonWithUrl(request).first

internal suspend fun OkHttpClient.readModelCatalogPages(
    firstRequest: Request,
    cursorParameter: String,
    additionalCursorParameter: String? = null,
    nextCursor: (JsonObject) -> String?,
    readModels: (JsonObject, String) -> List<AiModel>,
): List<AiModel> {
    fun withoutCursors(url: HttpUrl) = url.newBuilder().apply {
        removeAllQueryParameters(cursorParameter)
        additionalCursorParameter?.let { removeAllQueryParameters(it) }
    }.build()

    val request = firstRequest.newBuilder().url(withoutCursors(firstRequest.url)).build()
    val (firstPage, catalogUrl) = executeJsonWithUrl(request)
    val endpoint = withoutCursors(catalogUrl)
    check(catalogUrl == endpoint) { "AI model list did not start on the first page" }
    val sourceId = endpoint.catalogSourceId()
    val models = readModels(firstPage, sourceId).toMutableList()
    val seenCursors = mutableSetOf<String>()
    var cursor = nextCursor(firstPage)
    while (cursor != null) {
        check(seenCursors.add(cursor)) { "Repeated AI model list cursor" }
        val pageRequest = request.newBuilder()
            .url(endpoint.newBuilder().setQueryParameter(cursorParameter, cursor).build())
            .build()
        val (page, pageUrl) = executeJsonWithUrl(pageRequest)
        check(withoutCursors(pageUrl) == endpoint) {
            "AI model list source changed during pagination"
        }
        models += readModels(page, sourceId)
        cursor = nextCursor(page)
    }
    return models
}

internal suspend fun OkHttpClient.executeJsonWithUrl(request: Request): Pair<JsonObject, HttpUrl> {
    return newCall(request).readCancellableStream { response ->
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) {
                throw IllegalStateException("HTTP ${it.code}: ${body.take(500)}")
            }
            JsonParser.parseString(body).asJsonObject to it.request.url
        }
    }
}

internal suspend fun OkHttpClient.executeJsonOrThrow(request: Request): Pair<Response, String> {
    return newCall(request).readCancellableStream { response ->
        response.use { it to it.body.string() }
    }
}

internal fun JsonObject.stringOrNull(key: String): String? {
    return get(key)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.intOrNull(key: String): Int? {
    return get(key)?.takeIf { !it.isJsonNull }?.asInt
}

internal fun JsonObject.arrayOrNull(key: String): JsonArray? {
    return get(key)?.takeIf { it.isJsonArray }?.asJsonArray
}

internal fun JsonObject.modelCatalogEntries(name: String, idField: String): List<Pair<JsonObject, String>> {
    val entries = arrayOrNull(name) ?: error("Invalid AI model list response")
    return entries.map { item ->
        val model = item.takeIf { it.isJsonObject }?.asJsonObject ?: error("Invalid AI model list entry")
        val id = model.get(idField)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.trim()?.takeIf { it.isNotEmpty() } ?: error("AI model identifier is missing")
        model to id
    }
}

internal fun JsonObject.modelCatalogCursor(name: String): String? {
    val value = get(name)?.takeUnless { it.isJsonNull } ?: return null
    check(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Invalid AI model list cursor" }
    return value.asString.takeIf { it.isNotBlank() }
}

internal fun JsonObject.objectOrNull(key: String): JsonObject? {
    return get(key)?.takeIf { it.isJsonObject }?.asJsonObject
}

internal fun JsonElement?.textPart(): String? {
    return this?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.stringOrNull("text")
}

internal fun JsonElement?.contentText(): String {
    val element = this?.takeIf { !it.isJsonNull } ?: return ""
    return when {
        element.isJsonPrimitive -> element.asString
        element.isJsonArray -> element.asJsonArray.mapNotNull { it.textPart() }.joinToString("")
        else -> ""
    }
}

internal fun jsonBody(json: JsonObject) = json.toString().toRequestBody(jsonMediaType)
