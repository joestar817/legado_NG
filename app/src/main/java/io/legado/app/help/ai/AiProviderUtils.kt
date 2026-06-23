package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.http.await
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

internal fun aiHttpClient(timeoutSeconds: Int): OkHttpClient {
    val timeout = timeoutSeconds.toLong().coerceIn(5L, 600L)
    return okHttpClient.newBuilder()
        .connectTimeout(timeout, TimeUnit.SECONDS)
        .writeTimeout(timeout, TimeUnit.SECONDS)
        .readTimeout(timeout, TimeUnit.SECONDS)
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()
}

internal fun String.trimEndSlash(): String = trim().trimEnd('/')

internal fun String.ensureStartSlash(): String {
    return if (startsWith("/")) this else "/$this"
}

internal suspend fun OkHttpClient.executeJson(request: Request): JsonObject {
    return withContext(IO) {
        val response = newCall(request).await()
        val body = response.body.string()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${body.take(500)}")
        }
        JsonParser.parseString(body).asJsonObject
    }
}

internal suspend fun OkHttpClient.executeJsonOrThrow(request: Request): Pair<Response, String> {
    return withContext(IO) {
        val response = newCall(request).await()
        val body = response.body.string()
        response to body
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
