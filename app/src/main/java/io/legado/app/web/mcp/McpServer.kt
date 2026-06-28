package io.legado.app.web.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookSourceController
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.http.NetworkLog
import io.legado.app.model.Debug
import io.legado.app.model.webBook.SearchModel
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object McpServer {

    private const val PROTOCOL_VERSION = "2025-06-18"
    private const val SERVER_NAME = "Legado Native MCP"
    private const val MIME_JSON = "application/json; charset=utf-8"
    private const val MIME_TEXT = "text/plain; charset=utf-8"
    private const val DEFAULT_NETWORK_LOG_LIMIT = 20
    private const val MAX_NETWORK_LOG_LIMIT = 50
    private const val DEFAULT_NETWORK_LOG_BODY_CHARS = 16 * 1024
    private const val MAX_NETWORK_LOG_BODY_CHARS = 64 * 1024
    private const val DEFAULT_DEBUG_LOG_LIMIT = 20
    private const val MAX_DEBUG_LOG_LIMIT = 100
    private const val DEFAULT_DEBUG_LOG_STACK_CHARS = 16 * 1024
    private const val MAX_DEBUG_LOG_STACK_CHARS = 64 * 1024
    private val debugRunLock = Any()

    fun isEnabled(): Boolean = appCtx.getPrefBoolean(PreferKey.mcpService, false)

    fun isInternalEnabled(): Boolean = appCtx.getPrefBoolean(PreferKey.aiInternalMcp, false)

    fun serve(method: NanoHTTPD.Method, uri: String, postData: String?): NanoHTTPD.Response {
        if (!isEnabled()) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                MIME_TEXT,
                "MCP service is disabled"
            )
        }
        if (uri != "/" && uri != "/mcp") {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                MIME_TEXT,
                "Not found"
            )
        }
        return when (method) {
            NanoHTTPD.Method.POST -> {
                val response = handleJsonRpc(postData)
                if (response == null) {
                    NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.ACCEPTED,
                        MIME_TEXT,
                        ""
                    )
                } else {
                    jsonResponse(response)
                }
            }
            NanoHTTPD.Method.GET -> NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                MIME_TEXT,
                "MCP stream is not supported in this version"
            )
            else -> NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                MIME_TEXT,
                "Method not allowed"
            )
        }
    }

    fun handleJsonRpc(postData: String?): JsonElement? {
        if (postData.isNullOrBlank()) {
            return errorResponse(null, -32700, "Parse error")
        }
        val json = runCatching { JsonParser.parseString(postData) }.getOrElse {
            return errorResponse(null, -32700, "Parse error")
        }
        return handleJsonRpc(json)
    }

    fun handleJsonRpc(json: JsonElement): JsonElement? {
        return when {
            json.isJsonArray -> handleBatch(json.asJsonArray)
            json.isJsonObject -> handleRequest(json.asJsonObject)
            else -> errorResponse(null, -32600, "Invalid Request")
        }
    }

    private fun handleBatch(batch: JsonArray): JsonElement {
        if (batch.size() == 0) {
            return errorResponse(null, -32600, "Invalid Request")
        }
        val responses = JsonArray()
        batch.forEach { item ->
            if (item.isJsonObject) {
                handleRequest(item.asJsonObject)?.let { responses.add(it) }
            } else {
                responses.add(errorResponse(null, -32600, "Invalid Request"))
            }
        }
        return responses
    }

    private fun handleRequest(request: JsonObject): JsonElement? {
        val id = request.get("id")
        val method = request.get("method")?.takeIf { it.isJsonPrimitive }?.asString
        val params = request.get("params")?.takeIf { it.isJsonObject }?.asJsonObject
        if (method.isNullOrBlank()) {
            return errorResponse(id, -32600, "Invalid Request")
        }
        if (id == null && method.startsWith("notifications/")) {
            return null
        }
        return runCatching {
            when (method) {
                "initialize" -> successResponse(id, initializeResult())
                "ping" -> successResponse(id, emptyMap<String, Any>())
                "tools/list" -> successResponse(id, mapOf("tools" to tools()))
                "tools/call" -> successResponse(id, callTool(params))
                "resources/list" -> successResponse(id, mapOf("resources" to resources()))
                "resources/templates/list" -> successResponse(id, mapOf("resourceTemplates" to emptyList<Any>()))
                "resources/read" -> successResponse(id, readResource(params))
                "prompts/list" -> successResponse(id, mapOf("prompts" to emptyList<Any>()))
                else -> errorResponse(id, -32601, "Method not found")
            }
        }.getOrElse {
            errorResponse(id, -32603, it.localizedMessage ?: "Internal error")
        }
    }

    private fun initializeResult(): Map<String, Any> {
        return mapOf(
            "protocolVersion" to PROTOCOL_VERSION,
            "capabilities" to mapOf(
                "tools" to mapOf("listChanged" to false),
                "resources" to mapOf<String, Any>()
            ),
            "serverInfo" to mapOf(
                "name" to SERVER_NAME,
                "version" to "0.1.0"
            )
        )
    }

    private fun tools(): List<Map<String, Any>> {
        return listOf(
            tool(
                name = "legado_ping",
                description = "Check whether the native Legado MCP service is enabled.",
                properties = emptyMap()
            ),
            tool(
                name = "legado_get_api_summary",
                description = "Return the native MCP tool and resource summary.",
                properties = emptyMap()
            ),
            tool(
                name = "book_source_list",
                description = "List Legado book sources with basic identity fields only.",
                properties = emptyMap()
            ),
            tool(
                name = "book_source_get",
                description = "Get one Legado book source by bookSourceUrl.",
                properties = mapOf(
                    "url" to stringSchema("BookSource.bookSourceUrl")
                ),
                required = listOf("url")
            ),
            tool(
                name = "book_source_save",
                description = "Save or overwrite one Legado book source.",
                properties = mapOf(
                    "source" to mapOf(
                        "type" to "object",
                        "description" to "BookSource JSON object"
                    )
                ),
                required = listOf("source")
            ),
            tool(
                name = "book_source_debug",
                description = "Debug a Legado book source and return collected debug logs.",
                properties = mapOf(
                    "tag" to stringSchema("BookSource.bookSourceUrl"),
                    "key" to stringSchema("Keyword or URL used by Legado debug"),
                    "mode" to mapOf(
                        "type" to "string",
                        "enum" to listOf("auto", "search", "detail", "explore", "toc", "content"),
                        "default" to "auto"
                    ),
                    "timeout_seconds" to mapOf(
                        "type" to "number",
                        "default" to 30
                    )
                ),
                required = listOf("tag", "key")
            ),
            tool(
                name = "book_search",
                description = "Search books across enabled Legado book sources.",
                properties = mapOf(
                    "key" to stringSchema("Book name, author, or keyword"),
                    "scope" to stringSchema("Optional Legado SearchScope string. Empty means all enabled sources."),
                    "wait_for_finish" to mapOf(
                        "type" to "boolean",
                        "default" to false
                    ),
                    "min_results" to mapOf(
                        "type" to "number",
                        "default" to 1
                    ),
                    "timeout_seconds" to mapOf(
                        "type" to "number",
                        "default" to 30
                    )
                ),
                required = listOf("key")
            ),
            tool(
                name = "network_log_list",
                description = "List recent runtime network logs as compact summaries. Bodies and headers are not returned.",
                properties = mapOf(
                    "offset" to mapOf(
                        "type" to "number",
                        "default" to 0
                    ),
                    "limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_NETWORK_LOG_LIMIT,
                        "maximum" to MAX_NETWORK_LOG_LIMIT
                    ),
                    "type" to mapOf(
                        "type" to "string",
                        "enum" to listOf("OkHttp", "JS", "WebView"),
                        "description" to "Optional log type filter"
                    ),
                    "keyword" to stringSchema("Optional keyword matched against URL, source, method, status, or error preview."),
                    "only_errors" to mapOf(
                        "type" to "boolean",
                        "default" to false
                    )
                )
            ),
            tool(
                name = "network_log_get",
                description = "Get one runtime network log detail by id. Body output is capped by body_char_limit.",
                properties = mapOf(
                    "id" to mapOf(
                        "type" to "number",
                        "description" to "Network log id from network_log_list"
                    ),
                    "include_headers" to mapOf(
                        "type" to "boolean",
                        "default" to true
                    ),
                    "include_body" to mapOf(
                        "type" to "boolean",
                        "default" to true
                    ),
                    "body_char_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_NETWORK_LOG_BODY_CHARS,
                        "maximum" to MAX_NETWORK_LOG_BODY_CHARS
                    )
                ),
                required = listOf("id")
            ),
            tool(
                name = "network_log_clear",
                description = "Clear the in-memory runtime network log window before reproducing an issue.",
                properties = emptyMap()
            ),
            tool(
                name = "debug_log_list",
                description = "List recent in-memory app debug logs as compact summaries.",
                properties = mapOf(
                    "offset" to mapOf(
                        "type" to "number",
                        "default" to 0
                    ),
                    "limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_DEBUG_LOG_LIMIT,
                        "maximum" to MAX_DEBUG_LOG_LIMIT
                    ),
                    "keyword" to stringSchema("Optional keyword matched against message or throwable stack."),
                    "only_errors" to mapOf(
                        "type" to "boolean",
                        "default" to false
                    )
                )
            ),
            tool(
                name = "debug_log_get",
                description = "Get one in-memory app debug log detail by id.",
                properties = mapOf(
                    "id" to mapOf(
                        "type" to "number",
                        "description" to "Debug log id from debug_log_list"
                    ),
                    "include_stack" to mapOf(
                        "type" to "boolean",
                        "default" to true
                    ),
                    "stack_char_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_DEBUG_LOG_STACK_CHARS,
                        "maximum" to MAX_DEBUG_LOG_STACK_CHARS
                    )
                ),
                required = listOf("id")
            ),
            tool(
                name = "debug_log_clear",
                description = "Clear the in-memory app debug log window.",
                properties = emptyMap()
            )
        ) + BookshelfMcpTools.tools() + SettingsMcpTools.tools()
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Any>,
        required: List<String> = emptyList()
    ): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required
            )
        )
    }

    private fun stringSchema(description: String): Map<String, String> {
        return mapOf(
            "type" to "string",
            "description" to description
        )
    }

    private fun resources(): List<Map<String, String>> {
        return listOf(
            mapOf(
                "uri" to "legado://api/mcp",
                "name" to "mcp-api",
                "description" to "Native Legado MCP tool summary",
                "mimeType" to "application/json"
            ),
            mapOf(
                "uri" to "legado://schema/book-source",
                "name" to "book-source-schema",
                "description" to "Minimal BookSource JSON schema for MCP clients",
                "mimeType" to "application/schema+json"
            )
        ) + BookshelfMcpTools.resources() + SettingsMcpTools.resources()
    }

    private fun readResource(params: JsonObject?): Map<String, Any> {
        val uri = params?.get("uri")?.asStringOrNull()
            ?: throw IllegalArgumentException("uri is required")
        val text = when (uri) {
            "legado://api/mcp" -> GSON.toJson(apiSummary())
            "legado://schema/book-source" -> GSON.toJson(bookSourceSchema())
            else -> BookshelfMcpTools.readResource(uri)
                ?: SettingsMcpTools.readResource(uri)
                ?: throw IllegalArgumentException("Unknown resource: $uri")
        }
        return mapOf(
            "contents" to listOf(
                mapOf(
                    "uri" to uri,
                    "mimeType" to "application/json",
                    "text" to text
                )
            )
        )
    }

    private fun callTool(params: JsonObject?): Map<String, Any?> {
        val name = params?.get("name")?.asStringOrNull()
            ?: throw IllegalArgumentException("tool name is required")
        val arguments = params.get("arguments")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: JsonObject()
        val result = when (name) {
            "legado_ping" -> toolResult(
                ok = true,
                upstreamEndpoint = "native://mcp",
                normalizedData = mapOf(
                    "mcp_service_enabled" to isEnabled(),
                    "internal_mcp_enabled" to isInternalEnabled(),
                    "endpoint" to "/mcp"
                )
            )
            "legado_get_api_summary" -> toolResult(
                ok = true,
                upstreamEndpoint = "native://mcp",
                normalizedData = apiSummary()
            )
            "book_source_list" -> listBookSources()
            "book_source_get" -> {
                val url = arguments.get("url").asRequiredString("url")
                normalizeReturnData(
                    "/getBookSource",
                    BookSourceController.getSource(mapOf("url" to listOf(url)))
                )
            }
            "book_source_save" -> {
                val source = arguments.get("source") ?: throw IllegalArgumentException("source is required")
                normalizeReturnData(
                    "/saveBookSource",
                    BookSourceController.saveSource(GSON.toJson(source))
                )
            }
            "book_source_debug" -> runBookSourceDebug(arguments)
            "book_search" -> runBookSearch(arguments)
            "bookshelf_search" -> runBookSearch(arguments)
            "network_log_list" -> listNetworkLogs(arguments)
            "network_log_get" -> getNetworkLog(arguments)
            "network_log_clear" -> clearNetworkLogs()
            "debug_log_list" -> listDebugLogs(arguments)
            "debug_log_get" -> getDebugLog(arguments)
            "debug_log_clear" -> clearDebugLogs()
            else -> BookshelfMcpTools.call(name, arguments)
                ?: SettingsMcpTools.call(name, arguments)
                ?: throw IllegalArgumentException("Unknown tool: $name")
        }
        val text = GSON.toJson(result)
        return mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to text
                )
            ),
            "structuredContent" to result,
            "isError" to (result["ok"] != true)
        )
    }

    private fun runBookSourceDebug(arguments: JsonObject): Map<String, Any?> {
        val tag = arguments.get("tag").asRequiredString("tag")
        val key = arguments.get("key").asRequiredString("key")
        val mode = arguments.get("mode").asStringOrNull() ?: "auto"
        val timeoutSeconds = arguments.get("timeout_seconds").asDoubleOrNull() ?: 30.0
        val transformedKey = when (mode) {
            "auto", "search", "detail" -> key
            "explore" -> "explore::$key"
            "toc" -> "++$key"
            "content" -> "--$key"
            else -> key
        }
        val source = appDb.bookSourceDao.getBookSource(tag)
            ?: return toolResult(
                ok = false,
                upstreamEndpoint = "native://bookSourceDebug",
                normalizedData = null,
                warnings = listOf("未找到源，请检查书源地址")
            )
        val logs = Collections.synchronizedList(mutableListOf<String>())
        val latch = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val callback = object : Debug.Callback {
            override fun printLog(state: Int, msg: String) {
                if (state !in arrayOf(10, 20, 30, 40)) {
                    logs.add(msg)
                }
                if (state == -1 || state == 1000) {
                    latch.countDown()
                }
            }
        }
        synchronized(debugRunLock) {
            Debug.callback = callback
            Debug.startDebug(scope, source, transformedKey)
            latch.await((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
            Debug.cancelDebug(true)
        }
        scope.cancel()
        val done = latch.count == 0L
        return toolResult(
            ok = done,
            upstreamEndpoint = "native://bookSourceDebug",
            normalizedData = mapOf(
                "logs" to logs,
                "done" to done,
                "error_message" to if (done) null else "Debug timed out before completion"
            ),
            warnings = if (done) emptyList() else listOf("Debug timed out before completion")
        )
    }

    private fun runBookSearch(arguments: JsonObject): Map<String, Any?> {
        val key = arguments.get("key").asRequiredString("key")
        val scopeText = arguments.get("scope").asStringOrNull() ?: ""
        val waitForFinish = arguments.get("wait_for_finish").asBooleanOrNull() ?: false
        val minResults = (arguments.get("min_results").asIntOrNull() ?: 1).coerceAtLeast(1)
        val timeoutSeconds = arguments.get("timeout_seconds").asDoubleOrNull() ?: 30.0
        val books = Collections.synchronizedList(mutableListOf<SearchBook>())
        val searchScope = SearchScope(scopeText)
        val sourceCount = searchScope.getBookSourceParts().size
        val errorMessage = AtomicReference<String?>(null)
        val done = AtomicBoolean(false)
        val batchCount = AtomicInteger(0)
        val returnLatch = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val callback = object : SearchModel.CallBack {
            override fun getSearchScope(): SearchScope = searchScope
            override fun onSearchStart() = Unit
            override fun onSearchSuccess(searchBooks: List<SearchBook>) {
                books.clear()
                books.addAll(searchBooks)
                batchCount.incrementAndGet()
                if (!waitForFinish && searchBooks.size >= minResults) {
                    returnLatch.countDown()
                }
            }
            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) {
                done.set(true)
                returnLatch.countDown()
            }
            override fun onSearchCancel(exception: Throwable?) {
                errorMessage.set(exception?.localizedMessage)
                returnLatch.countDown()
            }
        }
        if (sourceCount == 0) {
            scope.cancel()
            return toolResult(
                ok = false,
                upstreamEndpoint = "native://searchBook",
                normalizedData = mapOf(
                    "books" to emptyList<SearchBook>(),
                    "done" to true,
                    "source_count" to 0,
                    "batch_count" to 0,
                    "search_scope" to searchScope.toString(),
                    "error_message" to "启用书源为空"
                ),
                warnings = listOf("启用书源为空")
            )
        }
        val searchModel = SearchModel(scope, callback)
        searchModel.search(System.currentTimeMillis(), key)
        val returned = returnLatch.await((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
        searchModel.close()
        scope.cancel()
        val bookSnapshot = synchronized(books) {
            ArrayList(books)
        }
        val error = errorMessage.get()
        val isDone = done.get()
        return toolResult(
            ok = error == null,
            upstreamEndpoint = "native://searchBook",
            normalizedData = mapOf(
                "books" to bookSnapshot,
                "done" to isDone,
                "source_count" to sourceCount,
                "batch_count" to batchCount.get(),
                "search_scope" to searchScope.toString(),
                "wait_for_finish" to waitForFinish,
                "min_results" to minResults,
                "error_message" to (error ?: if (returned) null else "Search timed out before completion")
            ),
            warnings = when {
                error != null -> listOf(error)
                !returned -> listOf("Search timed out before completion")
                !isDone -> listOf("Search returned partial results before all sources finished")
                else -> emptyList()
            }
        )
    }

    private fun normalizeReturnData(upstreamEndpoint: String, returnData: ReturnData): Map<String, Any?> {
        return toolResult(
            ok = returnData.isSuccess,
            upstreamEndpoint = upstreamEndpoint,
            normalizedData = returnData.data,
            rawUpstream = returnData,
            warnings = if (returnData.isSuccess || returnData.errorMsg.isBlank()) {
                emptyList()
            } else {
                listOf(returnData.errorMsg)
            }
        )
    }

    private fun listBookSources(): Map<String, Any?> {
        val sources = appDb.bookSourceDao.all.map {
            mapOf(
                "bookSourceComment" to it.bookSourceComment,
                "bookSourceGroup" to it.bookSourceGroup,
                "bookSourceName" to it.bookSourceName,
                "bookSourceType" to it.bookSourceType,
                "bookSourceUrl" to it.bookSourceUrl
            )
        }
        return toolResult(
            ok = sources.isNotEmpty(),
            upstreamEndpoint = "native://bookSourceList",
            normalizedData = sources,
            warnings = if (sources.isEmpty()) listOf("设备源列表为空") else emptyList()
        )
    }

    private fun listNetworkLogs(arguments: JsonObject): Map<String, Any?> {
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_NETWORK_LOG_LIMIT)
            .coerceIn(1, MAX_NETWORK_LOG_LIMIT)
        val type = arguments.get("type").asStringOrNull()?.takeIf { it.isNotBlank() }
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val onlyErrors = arguments.get("only_errors").asBooleanOrNull() ?: false
        val allLogs = NetworkLog.logs
        val filtered = allLogs.filter { entry ->
            (type == null || entry.type.equals(type, ignoreCase = true)) &&
                    (!onlyErrors || entry.isNetworkLogError()) &&
                    (keyword == null || entry.matchesNetworkLogKeyword(keyword))
        }
        val page = filtered.drop(offset).take(limit).map { it.toNetworkLogSummary() }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://networkLog/list",
            normalizedData = mapOf(
                "logs" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to allLogs.size,
                "filtered_total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size),
                "recording_enabled" to NetworkLog.isEnabled,
                "max_log_size" to NetworkLog.MAX_LOG_SIZE,
                "body_preview_size" to NetworkLog.BODY_PREVIEW_SIZE
            ),
            warnings = if (NetworkLog.isEnabled) {
                emptyList()
            } else {
                listOf("记录网络请求开关未开启；当前只返回内存中已有日志")
            }
        )
    }

    private fun getNetworkLog(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asLongOrNull()
            ?: throw IllegalArgumentException("id is required")
        val includeHeaders = arguments.get("include_headers").asBooleanOrNull() ?: true
        val includeBody = arguments.get("include_body").asBooleanOrNull() ?: true
        val bodyCharLimit = (arguments.get("body_char_limit").asIntOrNull()
            ?: DEFAULT_NETWORK_LOG_BODY_CHARS).coerceIn(0, MAX_NETWORK_LOG_BODY_CHARS)
        val entry = NetworkLog.find(id)
            ?: return toolResult(
                ok = false,
                upstreamEndpoint = "native://networkLog/get",
                normalizedData = null,
                warnings = listOf("未找到网络日志，可能已被新日志挤出内存窗口")
            )
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://networkLog/get",
            normalizedData = entry.toNetworkLogDetail(includeHeaders, includeBody, bodyCharLimit)
        )
    }

    private fun clearNetworkLogs(): Map<String, Any?> {
        val before = NetworkLog.logs.size
        NetworkLog.clear()
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://networkLog/clear",
            normalizedData = mapOf(
                "cleared" to before,
                "remaining" to NetworkLog.logs.size
            )
        )
    }

    private fun listDebugLogs(arguments: JsonObject): Map<String, Any?> {
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_DEBUG_LOG_LIMIT)
            .coerceIn(1, MAX_DEBUG_LOG_LIMIT)
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val onlyErrors = arguments.get("only_errors").asBooleanOrNull() ?: false
        val allLogs = AppLog.logs
        val filtered = allLogs.filter { entry ->
            (!onlyErrors || entry.third != null) &&
                    (keyword == null || entry.matchesDebugLogKeyword(keyword))
        }
        val page = filtered.drop(offset).take(limit).map { it.toDebugLogSummary() }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://debugLog/list",
            normalizedData = mapOf(
                "logs" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to allLogs.size,
                "filtered_total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size),
                "max_log_size" to 100
            )
        )
    }

    private fun getDebugLog(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asLongOrNull()
            ?: throw IllegalArgumentException("id is required")
        val includeStack = arguments.get("include_stack").asBooleanOrNull() ?: true
        val stackCharLimit = (arguments.get("stack_char_limit").asIntOrNull()
            ?: DEFAULT_DEBUG_LOG_STACK_CHARS).coerceIn(0, MAX_DEBUG_LOG_STACK_CHARS)
        val entry = AppLog.logs.firstOrNull { it.first == id }
            ?: return toolResult(
                ok = false,
                upstreamEndpoint = "native://debugLog/get",
                normalizedData = null,
                warnings = listOf("未找到调试日志，可能已被清空或挤出内存窗口")
            )
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://debugLog/get",
            normalizedData = entry.toDebugLogDetail(includeStack, stackCharLimit)
        )
    }

    private fun clearDebugLogs(): Map<String, Any?> {
        val before = AppLog.logs.size
        AppLog.clear()
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://debugLog/clear",
            normalizedData = mapOf(
                "cleared" to before,
                "remaining" to AppLog.logs.size
            )
        )
    }

    private fun NetworkLog.Entry.toNetworkLogSummary(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "time" to time,
            "time_text" to formatNetworkLogTime(time),
            "type" to type,
            "method" to method,
            "url" to url,
            "status_code" to statusCode,
            "took_ms" to tookMs,
            "source" to source,
            "ok_status" to !isNetworkLogError(),
            "has_request_headers" to !requestHeaders.isNullOrBlank(),
            "has_request_body" to !requestBody.isNullOrBlank(),
            "has_response_headers" to !responseHeaders.isNullOrBlank(),
            "has_response_body" to !responseBody.isNullOrBlank(),
            "has_error" to !error.isNullOrBlank(),
            "error_preview" to error?.lineSequence()?.firstOrNull()
        )
    }

    private fun NetworkLog.Entry.toNetworkLogDetail(
        includeHeaders: Boolean,
        includeBody: Boolean,
        bodyCharLimit: Int
    ): Map<String, Any?> {
        val detail = linkedMapOf<String, Any?>(
            "id" to id,
            "time" to time,
            "time_text" to formatNetworkLogTime(time),
            "type" to type,
            "method" to method,
            "url" to url,
            "status_code" to statusCode,
            "took_ms" to tookMs,
            "source" to source,
            "ok_status" to !isNetworkLogError()
        )
        if (includeHeaders) {
            detail["request_headers"] = requestHeaders
            detail["response_headers"] = responseHeaders
        }
        if (includeBody) {
            detail["request_body"] = requestBody.limitNetworkLogText(bodyCharLimit)
            detail["request_body_chars"] = requestBody?.length ?: 0
            detail["request_body_truncated_by_mcp"] = (requestBody?.length ?: 0) > bodyCharLimit
            detail["response_body"] = responseBody.limitNetworkLogText(bodyCharLimit)
            detail["response_body_chars"] = responseBody?.length ?: 0
            detail["response_body_truncated_by_mcp"] = (responseBody?.length ?: 0) > bodyCharLimit
        }
        detail["error"] = error
        return detail
    }

    private fun NetworkLog.Entry.isNetworkLogError(): Boolean {
        return error != null || statusCode?.let { it !in 200..399 } == true
    }

    private fun NetworkLog.Entry.matchesNetworkLogKeyword(keyword: String): Boolean {
        return sequenceOf(
            type,
            method,
            url,
            statusCode?.toString(),
            source,
            error?.lineSequence()?.firstOrNull()
        ).filterNotNull().any { it.contains(keyword, ignoreCase = true) }
    }

    private fun String?.limitNetworkLogText(maxChars: Int): String? {
        val value = this ?: return null
        return if (value.length > maxChars) {
            value.take(maxChars) + "\n[truncated by MCP at $maxChars chars]"
        } else {
            value
        }
    }

    private fun formatNetworkLogTime(time: Long): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
    }

    private fun Triple<Long, String, Throwable?>.toDebugLogSummary(): Map<String, Any?> {
        val throwable = third
        return mapOf(
            "id" to first,
            "time" to first,
            "time_text" to formatNetworkLogTime(first),
            "message_preview" to second.lineSequence().firstOrNull().orEmpty().limitMcpText(500),
            "has_throwable" to (throwable != null),
            "throwable_class" to throwable?.javaClass?.name,
            "throwable_message" to throwable?.localizedMessage
        )
    }

    private fun Triple<Long, String, Throwable?>.toDebugLogDetail(
        includeStack: Boolean,
        stackCharLimit: Int
    ): Map<String, Any?> {
        val throwable = third
        val detail = linkedMapOf<String, Any?>(
            "id" to first,
            "time" to first,
            "time_text" to formatNetworkLogTime(first),
            "message" to second,
            "has_throwable" to (throwable != null),
            "throwable_class" to throwable?.javaClass?.name,
            "throwable_message" to throwable?.localizedMessage
        )
        if (includeStack) {
            val stack = throwable?.stackTraceToString()
            detail["stack"] = stack.limitNetworkLogText(stackCharLimit)
            detail["stack_chars"] = stack?.length ?: 0
            detail["stack_truncated_by_mcp"] = (stack?.length ?: 0) > stackCharLimit
        }
        return detail
    }

    private fun Triple<Long, String, Throwable?>.matchesDebugLogKeyword(keyword: String): Boolean {
        return sequenceOf(
            second,
            third?.javaClass?.name,
            third?.localizedMessage,
            third?.stackTraceToString()
        ).filterNotNull().any { it.contains(keyword, ignoreCase = true) }
    }

    private fun String.limitMcpText(maxChars: Int): String {
        return if (length > maxChars) {
            take(maxChars) + "\n[truncated by MCP at $maxChars chars]"
        } else {
            this
        }
    }

    private fun toolResult(
        ok: Boolean,
        upstreamEndpoint: String,
        normalizedData: Any?,
        rawUpstream: Any? = null,
        warnings: List<String> = emptyList(),
        sessionId: String? = null
    ): Map<String, Any?> {
        return mapOf(
            "ok" to ok,
            "upstream_endpoint" to upstreamEndpoint,
            "normalized_data" to normalizedData,
            "raw_upstream" to rawUpstream,
            "warnings" to warnings,
            "session_id" to sessionId
        )
    }

    private fun apiSummary(): Map<String, Any> {
        return mapOf(
            "endpoint" to "/mcp",
            "protocol_version" to PROTOCOL_VERSION,
            "tools" to tools().mapNotNull { it["name"] },
            "resources" to resources().mapNotNull { it["uri"] },
            "notes" to listOf(
                "External HTTP MCP runs behind the service management switch.",
                "Built-in AI can use the same MCP handlers through an internal channel controlled by AI settings.",
                "Authentication and read/write permission tiers are intentionally deferred.",
                "This version returns JSON responses and does not implement SSE streaming."
            )
        )
    }

    private fun bookSourceSchema(): Map<String, Any> {
        return mapOf(
            "type" to "object",
            "required" to listOf("bookSourceUrl", "bookSourceName"),
            "properties" to mapOf(
                "bookSourceUrl" to stringSchema("BookSource primary URL"),
                "bookSourceName" to stringSchema("BookSource display name"),
                "bookSourceGroup" to stringSchema("Optional source group"),
                "bookSourceType" to mapOf("type" to "integer"),
                "enabled" to mapOf("type" to "boolean"),
                "enabledExplore" to mapOf("type" to "boolean"),
                "searchUrl" to stringSchema("Search URL rule"),
                "exploreUrl" to stringSchema("Explore URL rule"),
                "ruleSearch" to mapOf("type" to "object"),
                "ruleBookInfo" to mapOf("type" to "object"),
                "ruleToc" to mapOf("type" to "object"),
                "ruleContent" to mapOf("type" to "object")
            ),
            "additionalProperties" to true
        )
    }

    private fun successResponse(id: JsonElement?, result: Any): JsonObject {
        val obj = JsonObject()
        obj.addProperty("jsonrpc", "2.0")
        obj.add("id", id ?: com.google.gson.JsonNull.INSTANCE)
        obj.add("result", GSON.toJsonTree(result))
        return obj
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject {
        val error = JsonObject()
        error.addProperty("code", code)
        error.addProperty("message", message)
        val obj = JsonObject()
        obj.addProperty("jsonrpc", "2.0")
        obj.add("id", id ?: com.google.gson.JsonNull.INSTANCE)
        obj.add("error", error)
        return obj
    }

    private fun jsonResponse(element: JsonElement): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            MIME_JSON,
            GSON.toJson(element)
        )
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return this?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonElement?.asDoubleOrNull(): Double? {
        return this?.takeIf { it.isJsonPrimitive }?.asDouble
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return this?.takeIf { it.isJsonPrimitive }?.asInt
    }

    private fun JsonElement?.asLongOrNull(): Long? {
        return this?.takeIf { it.isJsonPrimitive }?.asLong
    }

    private fun JsonElement?.asBooleanOrNull(): Boolean? {
        return this?.takeIf { it.isJsonPrimitive }?.asBoolean
    }

    private fun JsonElement?.asRequiredString(name: String): String {
        return asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("$name is required")
    }
}
