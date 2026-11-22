package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val hanScript = Regex("\\p{sc=Han}")
private val japaneseOrKoreanScript = Regex("[\\p{sc=Hiragana}\\p{sc=Katakana}\\p{sc=Hangul}]")

internal fun AiExternalSearchService.buildSearchRequest(
    key: String,
    tool: AiExternalSearchTool,
    input: String,
    count: Int,
): Request {
    val body = JsonObject()
    val path = when (tool) {
        AiExternalSearchTool.SEARCH -> when (provider) {
            AiExternalSearchProvider.DEEPSEEK -> {
                body.addProperty("query", input)
                body.addProperty("count", count)
                "/v1/tools/search"
            }
            AiExternalSearchProvider.DOUBAO -> {
                body.addProperty("Query", input)
                body.addProperty("SearchType", "web")
                when (doubaoMode) {
                    AiDoubaoSearchMode.GLOBAL -> {
                        body.addProperty("DocCount", count)
                        body.addProperty("MaxSnippetLength", 300)
                        body.addProperty("MaxImageCountPerDoc", 1)
                        "/search_api/global_search"
                    }
                    AiDoubaoSearchMode.CUSTOM -> {
                        body.addProperty("Count", count)
                        body.add("Filter", JsonObject().apply { addProperty("NeedUrl", true) })
                        "/search_api/web_search"
                    }
                }
            }
            AiExternalSearchProvider.TAVILY -> {
                body.addProperty("query", input)
                body.addProperty("max_results", count)
                body.addProperty("search_depth", tavilySearchDepth.apiValue)
                body.addProperty("include_published_date", true)
                "/search"
            }
            AiExternalSearchProvider.TINYFISH -> {
                val url = provider.baseUrl.toHttpUrl().newBuilder().addQueryParameter("query", input)
                // TinySearch defaults to US/en without a locale. Han also occurs in Japanese and Korean.
                if (hanScript.containsMatchIn(input) && !japaneseOrKoreanScript.containsMatchIn(input)) {
                    url.addQueryParameter("location", "CN")
                    url.addQueryParameter("language", "zh")
                }
                return Request.Builder().url(url.build()).header("X-API-Key", key).get().build()
            }
            AiExternalSearchProvider.EXA -> {
                body.addProperty("query", input)
                body.addProperty("type", "auto")
                body.addProperty("numResults", count)
                body.add("contents", JsonObject().apply {
                    add("highlights", JsonObject().apply { addProperty("maxCharacters", 1200) })
                    add("text", JsonObject().apply { addProperty("maxCharacters", 1200) })
                })
                "/search"
            }
        }
        AiExternalSearchTool.READ_LINK -> when (provider) {
            AiExternalSearchProvider.DEEPSEEK -> {
                body.addProperty("url", input)
                "/v1/tools/fetch"
            }
            AiExternalSearchProvider.DOUBAO -> {
                body.addProperty("Url", input)
                "/search_api/fetch"
            }
            AiExternalSearchProvider.TAVILY -> {
                body.add("urls", JsonArray().apply { add(input) })
                body.addProperty("extract_depth", "basic")
                body.addProperty("format", "markdown")
                "/extract"
            }
            AiExternalSearchProvider.TINYFISH -> {
                body.add("urls", JsonArray().apply { add(input) })
                body.addProperty("format", "markdown")
                return Request.Builder().url("https://api.fetch.tinyfish.ai")
                    .header("X-API-Key", key).post(jsonBody(body)).build()
            }
            AiExternalSearchProvider.EXA -> {
                body.add("urls", JsonArray().apply { add(input) })
                body.addProperty("text", true)
                "/contents"
            }
        }
    }
    return Request.Builder().url(provider.baseUrl + path)
        .header("Authorization", "Bearer $key")
        .post(jsonBody(body))
        .build()
}

internal fun AiExternalSearchService.decodeSearchHits(payload: JsonObject): List<AiExternalSearchHit> {
    val global = doubaoMode == AiDoubaoSearchMode.GLOBAL
    val results = when (provider) {
        AiExternalSearchProvider.DOUBAO -> {
            val result = payload.objectOrNull("Result") ?: invalidResponse()
            if (global && (result.integer("ErrorCode") ?: 0) != 0) {
                throw AiExternalSearchException(AiExternalSearchFailure.REMOTE)
            }
            val itemsKey = if (global) "Documents" else "WebResults"
            val totalKey = if (global) "TotalDocCount" else "ResultCount"
            // The result array can be omitted when a query succeeds with no hits.
            if (result.get(itemsKey).let { it == null || it.isJsonNull } && result.integer(totalKey) == 0) {
                return emptyList()
            }
            result.arrayOrNull(itemsKey)
        }
        else -> payload.arrayOrNull("results")
    } ?: invalidResponse()

    val hits = results.filterIsInstance<JsonObject>().map { entry ->
        when (provider) {
            AiExternalSearchProvider.DEEPSEEK -> searchHit(
                entry.searchText("url"), entry.searchText("title"), entry.searchText("snippet"),
                entry.searchText("summary"), entry.searchText("site_name"), isoDate(entry.searchText("date")),
            )
            AiExternalSearchProvider.DOUBAO -> if (global) {
                decodeDoubaoGlobalHit(entry)
            } else {
                searchHit(
                    entry.searchText("Url"), entry.searchText("Title"), entry.searchText("Snippet"),
                    entry.searchText("Summary"), entry.searchText("SiteName"), isoDate(entry.searchText("PublishTime")),
                )
            }
            AiExternalSearchProvider.TAVILY -> {
                val text = entry.searchText("content").trim()
                val date = entry.searchText("published_date")
                val publishedDate = date.takeIf(String::isNotBlank)?.let {
                    runCatching { LocalDate.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull() ?: isoDate(it)
                }
                searchHit(entry.searchText("url"), entry.searchText("title"), searchSnippet(text), text, "", publishedDate)
            }
            AiExternalSearchProvider.TINYFISH -> {
                val text = entry.searchText("snippet").trim()
                searchHit(
                    entry.searchText("url"), entry.searchText("title"), searchSnippet(text), text,
                    entry.searchText("site_name"), isoDate(entry.searchText("date")),
                )
            }
            AiExternalSearchProvider.EXA -> {
                val highlights = entry.arrayOrNull("highlights")
                    ?.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.orEmpty()
                    .map { it.asString.trim() }.filter(String::isNotEmpty)
                val text = highlights.joinToString("\n").ifEmpty { entry.searchText("text").trim() }
                searchHit(
                    entry.searchText("url"), entry.searchText("title"), searchSnippet(text), text,
                    "", isoDate(entry.searchText("publishedDate")),
                )
            }
        }
    }
    if (hits.isEmpty() && !results.isEmpty) invalidResponse()
    return hits
}

private fun decodeDoubaoGlobalHit(entry: JsonObject): AiExternalSearchHit {
    val summary = entry.arrayOrNull("Snippet")?.filterIsInstance<JsonObject>().orEmpty()
        .mapNotNull { it.searchText("Text").trim().takeIf(String::isNotEmpty) }
        .joinToString("\n")
    val host = entry.objectOrNull("HostInfo")?.searchText("Hostname").orEmpty()
    val date = entry.objectOrNull("DocumentInfo")?.searchText("PublishTime").orEmpty()
    return searchHit(entry.searchText("Url"), entry.searchText("Title"), searchSnippet(summary), summary, host, isoDate(date))
}

private fun searchSnippet(text: String): String = if (text.codePointCount(0, text.length) > 200) {
    text.substring(0, text.offsetByCodePoints(0, 200)).trimEnd() + "…"
} else text

private fun searchHit(
    url: String,
    title: String,
    snippet: String,
    summary: String,
    site: String,
    date: LocalDate?,
) = AiExternalSearchHit(
    address = url.trim(),
    url = url.trim().webUrl(),
    title = title.trim(),
    snippet = snippet.trim().ifEmpty { summary.trim() },
    summary = summary.trim(),
    siteName = site.trim(),
    publishedDate = date,
)

private fun isoDate(value: String): LocalDate? {
    val date = value.trim()
    if (date.isEmpty()) return null
    val format = if ('T' in date) DateTimeFormatter.ISO_DATE_TIME else DateTimeFormatter.ISO_DATE
    return runCatching { LocalDate.parse(date, format) }.getOrNull()
}

internal fun AiExternalSearchProvider.decodePage(payload: JsonObject, address: String): AiExternalWebPage = when (this) {
    AiExternalSearchProvider.DEEPSEEK -> page(address, payload.searchText("title"), payload.searchText("content"))
    AiExternalSearchProvider.DOUBAO -> {
        val data = payload.objectOrNull("Result")?.objectOrNull("Data") ?: invalidResponse()
        val status = data.integer("StatusCode") ?: invalidResponse()
        if (status !in 200..299) noPage()
        val url = data.searchText("RedirectedUrl").webUrl() ?: data.searchText("Url").webUrl() ?: address
        page(url, data.searchText("Title"), data.searchText("ContentText"))
    }
    AiExternalSearchProvider.TAVILY, AiExternalSearchProvider.TINYFISH, AiExternalSearchProvider.EXA -> {
        val results = payload.arrayOrNull("results") ?: invalidResponse()
        if (results.isEmpty) {
            if (this == AiExternalSearchProvider.TINYFISH &&
                payload.arrayOrNull("errors")?.filterIsInstance<JsonObject>()?.any { it.searchText("error") == "timeout" } == true
            ) {
                throw AiExternalSearchException(AiExternalSearchFailure.TIMEOUT)
            }
            noPage()
        }
        val result = results.first().takeIf { it.isJsonObject }?.asJsonObject ?: invalidResponse()
        val finalUrl = if (this == AiExternalSearchProvider.TINYFISH) result.searchText("final_url").webUrl() else null
        val url = finalUrl ?: result.searchText("url").webUrl() ?: address
        val textField = if (this == AiExternalSearchProvider.TAVILY) "raw_content" else "text"
        page(url, result.searchText("title"), result.searchText(textField))
    }
}

private fun page(url: String, title: String, text: String): AiExternalWebPage {
    if (text.isBlank()) noPage()
    return AiExternalWebPage(AiWebSource(url, title.trim().take(240).ifEmpty { url }), text)
}

private fun invalidResponse(): Nothing = throw AiExternalSearchException(AiExternalSearchFailure.RESPONSE)
private fun noPage(): Nothing = throw AiExternalSearchException(AiExternalSearchFailure.NO_PAGE)

private fun JsonObject.integer(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()

internal fun throwOnDoubaoSearchError(payload: JsonObject, tool: AiExternalSearchTool, httpStatus: Int?) {
    val error = payload.objectOrNull("ResponseMetadata")?.objectOrNull("Error") ?: return
    val failure = when (error.integer("CodeN")?.toString() ?: error.searchText("Code")) {
        "10400" -> if (tool == AiExternalSearchTool.READ_LINK) AiExternalSearchFailure.INVALID_URL else AiExternalSearchFailure.INVALID_QUERY
        "10403", "700901", "invalid_api_key" -> AiExternalSearchFailure.INVALID_KEY
        "10406", "10407", "10412" -> AiExternalSearchFailure.BALANCE
        "10413", "10502" -> AiExternalSearchFailure.NO_PAGE
        "700429", "rate_limit_exceeded" -> AiExternalSearchFailure.RATE_LIMIT
        else -> httpStatus?.let { AiExternalSearchProvider.DOUBAO.httpFailure(it, tool).failure } ?: AiExternalSearchFailure.REMOTE
    }
    throw AiExternalSearchException(failure, httpStatus)
}

internal fun AiExternalSearchProvider.httpFailure(status: Int, tool: AiExternalSearchTool): AiExternalSearchException {
    val failure = when (status) {
        401 -> AiExternalSearchFailure.INVALID_KEY
        402 -> AiExternalSearchFailure.BALANCE
        404 -> if (tool == AiExternalSearchTool.READ_LINK) AiExternalSearchFailure.NO_PAGE else AiExternalSearchFailure.REMOTE
        408, 504 -> AiExternalSearchFailure.TIMEOUT
        429 -> AiExternalSearchFailure.RATE_LIMIT
        432, 433 -> if (this == AiExternalSearchProvider.TAVILY) AiExternalSearchFailure.BALANCE else AiExternalSearchFailure.REMOTE
        else -> AiExternalSearchFailure.REMOTE
    }
    return AiExternalSearchException(failure, status)
}
