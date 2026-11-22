package io.legado.app.help.ai

import android.content.Context
import androidx.annotation.StringRes
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.legado.app.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.time.LocalDate
import kotlin.time.TimeSource

internal enum class AiExternalSearchTool(
    val functionName: String,
    val argument: String,
    val activity: AiWebActivityAction,
    private val description: String,
    private val argumentDescription: String,
) {
    SEARCH(
        "search_web", "query", AiWebActivityAction.SEARCH,
        "Find current public web information and return source titles, URLs and summaries.",
        "Focused search keywords.",
    ),
    READ_LINK(
        "read_web_url", "url", AiWebActivityAction.READ,
        "Read the contents of one public web page and return its title and text.",
        "An HTTP or HTTPS URL provided by the user or returned by web search.",
    );

    fun input(arguments: JsonObject): String = arguments.searchText(argument)

    fun definition(provider: AiExternalSearchProvider) = JsonObject().apply {
        addProperty("type", "function")
        add("function", JsonObject().apply {
            addProperty("name", functionName)
            addProperty("description", description)
            add("parameters", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add(argument, JsonObject().apply {
                        addProperty("type", "string")
                        val hint = if (this@AiExternalSearchTool == SEARCH) {
                            "$argumentDescription Use 1 to ${provider.maxQueryLength} Unicode characters."
                        } else argumentDescription
                        addProperty("description", hint)
                    })
                })
                add("required", JsonArray().apply { add(argument) })
            })
        })
    }
}

internal data class AiExternalWebPage(
    val source: AiWebSource,
    val text: String,
    val truncated: Boolean = false,
)

internal data class AiExternalSearchHit(
    val address: String,
    val url: String?,
    val title: String,
    val snippet: String,
    val summary: String,
    val siteName: String,
    val publishedDate: LocalDate?,
)

internal data class AiExternalSearchDebugResponse(
    val status: Int,
    val body: String?,
    val truncated: Boolean = false,
)

internal data class AiExternalSearchDebugResult<T : Any>(
    val elapsedMs: Long,
    val response: AiExternalSearchDebugResponse?,
    val data: T? = null,
    val error: AiExternalSearchException? = null,
)

internal enum class AiExternalSearchFailure(@param:StringRes val description: Int) {
    KEY_REQUIRED(R.string.ai_search_error_key_required),
    INVALID_KEY(R.string.ai_search_error_key_invalid),
    INVALID_QUERY(R.string.ai_search_error_query),
    INVALID_URL(R.string.ai_search_error_url),
    BALANCE(R.string.ai_search_error_balance),
    NO_PAGE(R.string.ai_search_error_no_page),
    RATE_LIMIT(R.string.ai_search_error_rate_limit),
    TIMEOUT(R.string.ai_search_error_timeout),
    NETWORK(R.string.ai_search_error_network),
    RESPONSE(R.string.ai_search_error_response),
    REMOTE(R.string.ai_search_error_remote),
}

internal class AiExternalSearchException(
    val failure: AiExternalSearchFailure,
    val httpStatus: Int? = null,
    val maxQueryLength: Int? = null,
) : IOException(failure.name) {
    fun reasonMessage(context: Context): String = maxQueryLength?.let {
        context.getString(R.string.ai_search_error_query_length, it)
    } ?: context.getString(failure.description)

    fun displayMessage(context: Context): String {
        val reason = reasonMessage(context)
        return httpStatus?.let { context.getString(R.string.ai_search_error_http, it, reason) } ?: reason
    }
}

private val externalSearchHttp by lazy {
    aiHttpClient(30).newBuilder()
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
}

/** The same Search/Fetch adapter is used by the setup test and normal chat turns. */
internal class AiExternalSearchClient(private val client: OkHttpClient = externalSearchHttp) {
    suspend fun execute(
        service: AiExternalSearchService,
        tool: AiExternalSearchTool,
        input: String,
    ): List<AiExternalWebPage> {
        val request = prepareRequest(service, tool, input, service.chatResultCount)
        return readPayload(request.http, service.provider, tool) { payload ->
            when (tool) {
                AiExternalSearchTool.SEARCH -> {
                    val hits = service.decodeSearchHits(payload)
                    val pages = hits.asSequence().mapNotNull { hit ->
                        val url = hit.url ?: return@mapNotNull null
                        val title = hit.title.take(240).ifEmpty { url }
                        AiExternalWebPage(AiWebSource(url, title), hit.summary.ifBlank { hit.snippet }.take(1600))
                    }.distinctBy { it.source.url }.take(request.count).toList()
                    if (pages.isEmpty() && hits.isNotEmpty()) fail(AiExternalSearchFailure.RESPONSE)
                    pages
                }
                AiExternalSearchTool.READ_LINK -> {
                    val page = service.provider.decodePage(payload, request.input)
                    val text = page.text.trim()
                    listOf(page.copy(
                        text = text.take(MAX_PAGE_CHARS),
                        truncated = text.length > MAX_PAGE_CHARS,
                    ))
                }
            }
        }
    }

    suspend fun searchForDebug(service: AiExternalSearchService, query: String) =
        debug(service, AiExternalSearchTool.SEARCH, query) { payload, request ->
            val key = service.apiKey.trim()
            val hits = service.decodeSearchHits(payload)
            // TinySearch returns a page of results and has no result count parameter.
            val visibleHits = if (service.provider == AiExternalSearchProvider.TINYFISH) hits.take(request.count) else hits
            visibleHits.map { hit ->
                val displayAddress = hit.address.redact(key)
                hit.copy(
                    address = displayAddress,
                    url = hit.url.takeIf { displayAddress == hit.address },
                    title = hit.title.redact(key),
                    snippet = hit.snippet.redact(key),
                    summary = hit.summary.redact(key),
                    siteName = hit.siteName.redact(key),
                )
            }
        }

    suspend fun fetchForDebug(service: AiExternalSearchService, url: String) =
        debug(service, AiExternalSearchTool.READ_LINK, url) { payload, request ->
            val key = service.apiKey.trim()
            val page = service.provider.decodePage(payload, request.input)
            page.copy(
                source = AiWebSource(page.source.url.redact(key), page.source.title.redact(key)),
                text = page.text.redact(key),
            )
        }

    private suspend fun <T : Any> debug(
        service: AiExternalSearchService,
        tool: AiExternalSearchTool,
        input: String,
        decode: (JsonObject, SearchRequest) -> T,
    ): AiExternalSearchDebugResult<T> {
        val started = TimeSource.Monotonic.markNow()
        val key = service.apiKey.trim()
        var response: AiExternalSearchDebugResponse? = null
        return try {
            val request = prepareRequest(service, tool, input, service.resultCount.coerceIn(service.resultCountRange))
            val data = readPayload(request.http, service.provider, tool, onResponse = { status, body, truncated ->
                response = AiExternalSearchDebugResponse(status, body?.redactResponse(key), truncated)
            }) { payload -> decode(payload, request) }
            AiExternalSearchDebugResult(started.elapsedNow().inWholeMilliseconds, response, data = data)
        } catch (error: AiExternalSearchException) {
            AiExternalSearchDebugResult(started.elapsedNow().inWholeMilliseconds, response, error = error)
        }
    }

    private data class SearchRequest(val http: Request, val input: String, val count: Int)

    private fun prepareRequest(
        service: AiExternalSearchService,
        tool: AiExternalSearchTool,
        input: String,
        count: Int,
    ): SearchRequest {
        val key = service.apiKey.trim()
        if (key.isEmpty()) fail(AiExternalSearchFailure.KEY_REQUIRED)
        if (key.any { it !in '!'..'~' }) fail(AiExternalSearchFailure.INVALID_KEY)
        val text = input.trim()
        val value = when (tool) {
            AiExternalSearchTool.SEARCH -> {
                val limit = service.provider.maxQueryLength
                if (text.codePointCount(0, text.length) !in 1..limit) {
                    throw AiExternalSearchException(AiExternalSearchFailure.INVALID_QUERY, maxQueryLength = limit)
                }
                text
            }
            AiExternalSearchTool.READ_LINK -> text.webUrl() ?: fail(AiExternalSearchFailure.INVALID_URL)
        }
        val request = service.buildSearchRequest(key, tool, value, count)
        return SearchRequest(request, value, count)
    }

    private suspend fun <T> readPayload(
        request: Request,
        provider: AiExternalSearchProvider,
        tool: AiExternalSearchTool,
        onResponse: ((status: Int, body: String?, truncated: Boolean) -> Unit)? = null,
        decode: (JsonObject) -> T,
    ): T {
        return try {
            client.newCall(request).readCancellableStream { response ->
                response.use {
                    val error = if (it.code == 200) null else provider.httpFailure(it.code, tool)
                    // Doubao can report a more specific API error in the body, including for HTTP 4xx.
                    if (error != null && onResponse == null && provider != AiExternalSearchProvider.DOUBAO) throw error
                    onResponse?.invoke(it.code, null, false)
                    val source = it.body.source()
                    val truncated = source.request(MAX_RESPONSE_BYTES + 1)
                    val body = when {
                        !truncated -> source.readUtf8()
                        onResponse != null -> source.readUtf8(DEBUG_RAW_PREVIEW_BYTES)
                        else -> null
                    }
                    onResponse?.invoke(it.code, body, truncated)
                    val json = if (truncated || body == null) null else runCatching { JsonParser.parseString(body) }.getOrNull()
                    val payload = json?.takeIf { it.isJsonObject }?.asJsonObject
                    if (provider == AiExternalSearchProvider.DOUBAO && payload != null) {
                        throwOnDoubaoSearchError(payload, tool, error?.httpStatus)
                    }
                    if (error != null) throw error
                    decode(payload ?: fail(AiExternalSearchFailure.RESPONSE))
                }
            }
        } catch (error: AiExternalSearchException) {
            throw error
        } catch (_: InterruptedIOException) {
            currentCoroutineContext().ensureActive()
            fail(AiExternalSearchFailure.TIMEOUT)
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            fail(AiExternalSearchFailure.NETWORK)
        }
    }

    private fun fail(reason: AiExternalSearchFailure): Nothing = throw AiExternalSearchException(reason)

    private companion object {
        const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
        const val MAX_PAGE_CHARS = 100_000
        const val DEBUG_RAW_PREVIEW_BYTES = 16L * 1024
    }
}

private fun String.redact(key: String): String = if (key.isEmpty()) this else replace(key, "[redacted]")

private fun String.redactResponse(key: String): String {
    if (key.isEmpty()) return this
    val escapedKey = JsonPrimitive(key).toString().removeSurrounding("\"")
    return redact(escapedKey).redact(key)
}

internal fun JsonObject.searchText(name: String): String = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
