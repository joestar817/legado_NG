package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class AiWebActivityAction {
    @SerializedName("search") SEARCH,
    @SerializedName("read") READ,
    @SerializedName("image_search") IMAGE_SEARCH,
}

data class AiWebSource(
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String,
)

data class AiWebActivity(
    @SerializedName("sources") val sources: List<AiWebSource> = emptyList(),
    @SerializedName("failed_links") val failedLinks: List<String> = emptyList(),
    @SerializedName("search_failed") val searchFailed: Boolean = false,
    @SerializedName("active_action") val activeAction: AiWebActivityAction? = null,
    @SerializedName("provider_label") val providerLabel: String? = null,
    @SerializedName("external_error") val externalError: String? = null,
    @SerializedName("inline_citations") val inlineCitations: List<AiWebCitation>? = null,
    @SerializedName("cited_text_hash") val citedTextHash: Int? = null,
) {
    fun settledOrNull(): AiWebActivity? = copy(activeAction = null).takeIf {
        it.sources.isNotEmpty() || it.failedLinks.isNotEmpty() || it.searchFailed || it.externalError != null
    }
}

data class AiWebCitation(
    @SerializedName("end_offset") val endOffset: Int,
    @SerializedName("url") val url: String,
)

internal fun AiWebActivity.hasLegacyChatGptNativeLabel(usageProviderId: String?): Boolean {
    // Only ChatGPT Responses populate inline citations. The old native label was shared by both routes.
    val fromChatGpt = if (usageProviderId.isNullOrBlank()) !inlineCitations.isNullOrEmpty()
        else usageProviderId == AiDefaultProviders.CHATGPT_ID
    return fromChatGpt && (providerLabel == "DeepSeek 内置联网" || providerLabel == "DeepSeek built-in web access")
}

/** Normalizes provider events for the chat UI. Wire history is kept separately by each codec. */
internal class AiNativeWebEvents(private val onUpdate: (AiWebActivity) -> Unit) {
    private data class Call(val action: AiWebActivityAction, val url: String?)

    private val running = mutableMapOf<String, Call>()
    private val citations = linkedMapOf<String, AiWebSource>()
    private val readPages = linkedMapOf<String, AiWebSource>()
    private val searchResults = linkedMapOf<String, AiWebSource>()
    private val failedLinks = linkedSetOf<String>()
    private var searchFailed = false
    private var latest = AiWebActivity()
    private var inlineCitations: List<AiWebCitation>? = null
    private var citedTextHash: Int? = null

    val settled: AiWebActivity? get() = latest.settledOrNull()

    fun chatProgress(event: JsonObject) {
        val call = event.objectOrNull("call") ?: return
        when (event.stringOrNull("type")) {
            "call_started" -> started(call.stringOrNull("id"), call.webCall())
            "call_completed" -> completed(call)
        }
        publish()
    }

    fun chatResult(message: JsonObject) {
        message.arrayOrNull("server_tool_calls").webObjects().forEach { step ->
            step.arrayOrNull("calls").webObjects().forEach(::completed)
        }
        message.arrayOrNull("annotations").webObjects().forEach(::citation)
        publish()
    }

    fun responsesProgress(type: String?, frame: JsonObject) {
        val item = frame.objectOrNull("item")
        when {
            type == "response.output_item.added" && item?.stringOrNull("type") == "web_search_call" ->
                started(item.stringOrNull("id"), item.webCall())
            type == "response.output_item.done" && item?.stringOrNull("type") == "web_search_call" ->
                completed(item)
            type == "response.web_search_call.in_progress" || type == "response.web_search_call.searching" -> {
                val id = frame.stringOrNull("item_id")
                if (id != null && id !in running) started(id, frame.webCall())
            }
            type == "response.web_search_call.completed" -> frame.stringOrNull("item_id")?.let(running::remove)
            type == "response.output_text.annotation.added" -> frame.objectOrNull("annotation")?.let(::citation)
            else -> return
        }
        publish()
    }

    fun responsesResult(output: List<JsonObject>, inline: Boolean = false) {
        output.forEach { item ->
            when (item.stringOrNull("type")) {
                "web_search_call" -> completed(item)
                "message" -> item.arrayOrNull("content").webObjects().forEach { part ->
                    part.arrayOrNull("annotations").webObjects().forEach(::citation)
                }
            }
        }
        if (inline) collectInlineCitations(output)
        publish()
    }

    private fun collectInlineCitations(output: List<JsonObject>) {
        val text = StringBuilder()
        val offsets = mutableListOf<AiWebCitation>()
        output.filter { it.stringOrNull("type") == "message" }.forEach { item ->
            item.arrayOrNull("content").webObjects().filter { it.stringOrNull("type") == "output_text" }
                .forEach { part ->
                    val value = part.stringOrNull("text").orEmpty()
                    val characters = value.codePointCount(0, value.length)
                    part.arrayOrNull("annotations").webObjects().forEach annotation@{ annotation ->
                        if (offsets.size >= MAX_WEB_SOURCES || annotation.stringOrNull("type") != "url_citation") {
                            return@annotation
                        }
                        val url = annotation.stringOrNull("url").webUrl() ?: return@annotation
                        val end = runCatching { annotation.intOrNull("end_index") }.getOrNull()
                        val offset = end?.takeIf { it in 1..characters }?.let { value.offsetByCodePoints(0, it) }
                        offsets += AiWebCitation(if (offset == null) -1 else text.length + offset, url)
                    }
                    text.append(value)
                }
        }
        val raw = text.toString()
        val trimmed = raw.trim()
        val leading = raw.length - raw.trimStart().length
        inlineCitations = offsets.map { it.copy(endOffset = it.endOffset - leading) }.takeIf { it.isNotEmpty() }
        citedTextHash = trimmed.hashCode().takeIf { inlineCitations != null }
    }

    fun messagesBlock(block: JsonObject, streaming: Boolean) {
        val id = block.stringOrNull("tool_use_id")
        when (block.stringOrNull("type")) {
            "server_tool_use" -> started(block.stringOrNull("id"), block.webCall())
            "web_search_tool_result" -> {
                val results = block.arrayOrNull("content").webObjects()
                id?.let(running::remove)
                if (results.any { it.stringOrNull("type") == "web_search_tool_result_error" } ||
                    block.objectOrNull("content")?.stringOrNull("type") == "web_search_tool_result_error"
                ) searchFailed = true
                results.forEach { addSource(searchResults, it) }
            }
            "web_fetch_tool_result" -> {
                val call = id?.let(running::remove)
                val page = block.objectOrNull("content")
                if (page?.stringOrNull("type") == "web_fetch_tool_result_error") {
                    call?.url?.let(failedLinks::add)
                } else if (page != null) {
                    addSource(readPages, page)
                }
            }
            "text" -> block.arrayOrNull("citations").webObjects().forEach(::citation)
            else -> return
        }
        if (streaming) publish()
    }

    fun messagesCitation(citation: JsonObject) {
        citation(citation)
        publish()
    }

    fun finishResponse() {
        running.clear()
        publish()
    }

    private fun started(id: String?, call: Call) {
        if (!id.isNullOrBlank()) running[id] = call
    }

    private fun completed(item: JsonObject) {
        val previous = item.stringOrNull("id")?.let(running::remove)
        val call = item.webCall()
        if (item.stringOrNull("status") == "failed" || item.objectOrNull("error") != null) {
            val url = call.url ?: previous?.url
            if (url != null) failedLinks += url
            if (call.action != AiWebActivityAction.READ && previous?.action != AiWebActivityAction.READ) {
                searchFailed = true
            }
            return
        }
        item.objectOrNull("page")?.let { addSource(readPages, it) }
        val action = item.objectOrNull("action")
        val sources = item.arrayOrNull("sources") ?: action?.arrayOrNull("sources")
        sources.webObjects().forEach { addSource(searchResults, it) }
    }

    private fun JsonObject.webCall(): Call {
        val action = objectOrNull("action") ?: objectOrNull("input")
        val url = (action?.stringOrNull("url") ?: objectOrNull("page")?.stringOrNull("url")).webUrl()
        val category = when {
            stringOrNull("kind") in IMAGE_SEARCH_KINDS -> AiWebActivityAction.IMAGE_SEARCH
            stringOrNull("kind") == "read_link" || url != null -> AiWebActivityAction.READ
            else -> AiWebActivityAction.SEARCH
        }
        return Call(category, url)
    }

    private fun citation(item: JsonObject) {
        addSource(citations, item.objectOrNull("url_citation") ?: item)
    }

    private fun addSource(target: MutableMap<String, AiWebSource>, item: JsonObject) {
        val url = item.stringOrNull("url").webUrl() ?: return
        val title = (item.stringOrNull("title") ?: item.objectOrNull("content")?.stringOrNull("title"))
            ?.trim()?.take(240)?.takeIf(String::isNotEmpty) ?: url
        if (target[url]?.title == url || (url !in target && target.size < MAX_WEB_SOURCES)) {
            target[url] = AiWebSource(url, title)
        }
        if (target === readPages) failedLinks.remove(url)
    }

    private fun publish() {
        val sources = (citations.ifEmpty { searchResults }.values + readPages.values)
            .distinctBy(AiWebSource::url).take(MAX_WEB_SOURCES)
        val update = AiWebActivity(sources, failedLinks.take(MAX_WEB_SOURCES), searchFailed,
            running.values.lastOrNull()?.action, inlineCitations = inlineCitations, citedTextHash = citedTextHash)
        if (update != latest) {
            latest = update
            onUpdate(update)
        }
    }

    private companion object {
        const val MAX_WEB_SOURCES = 30
        val IMAGE_SEARCH_KINDS = setOf("image_search", "deep_image_search")
    }
}

private fun JsonArray?.webObjects() = this?.filterIsInstance<JsonObject>().orEmpty()

internal fun String?.webUrl(): String? = this?.takeIf { it.length <= 8192 }?.toHttpUrlOrNull()
    ?.takeUnless { it.username.isNotEmpty() || it.password.isNotEmpty() }
    ?.toString()

/** Add citation links to the display projection without changing chat history sent to the model. */
internal fun AiWebActivity.withInlineLinks(markdown: String): String {
    val citations = inlineCitations.orEmpty()
    if (citations.isEmpty() || markdown.isBlank()) return markdown
    val labels = citations.map { it.url }.distinct().withIndex().associate { it.value to it.index + 1 }
    val raw = markdown.trim()
    val match = raw.hashCode() == citedTextHash
    val links = linkedMapOf<Int, MutableSet<String>>()
    val trailing = linkedSetOf<String>()
    citations.forEach { citation ->
        val url = citation.url.webUrl() ?: return@forEach
        if (raw.contains("]($url") || raw.contains("](<$url")) return@forEach
        val link = "[${labels.getValue(citation.url)}](<$url>)"
        val offset = citation.endOffset
        if (match && offset in 1..raw.length) {
            val end = raw.indexOf('\n', offset - 1).takeIf { it >= 0 } ?: raw.length
            val start = raw.lastIndexOf('\n', end - 1) + 1
            val line = raw.substring(start, end).trimEnd()
            // Keep link syntax out of table delimiters, indented code and fence markers.
            if (line.isNotBlank() && !line.startsWith("    ") && !line.trimStart().startsWith("```") &&
                !line.trimStart().startsWith("~~~") && '|' !in line && '`' !in line) {
                links.getOrPut(start + line.length) { linkedSetOf() }.add(link)
            } else trailing.add(link)
        } else trailing.add(link)
    }
    if (links.isEmpty() && trailing.isEmpty()) return markdown
    return StringBuilder(raw).apply {
        links.toSortedMap(reverseOrder()).forEach { (offset, urls) -> insert(offset, urls.joinToString(" ", " ")) }
        if (trailing.isNotEmpty()) append(trailing.joinToString(" ", "\n\n"))
    }.toString()
}
