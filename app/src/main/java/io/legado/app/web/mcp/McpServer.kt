package io.legado.app.web.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD
import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookSourceController
import io.legado.app.BuildConfig
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessageNode
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.ai.AiChatMessageSnapshot
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.http.NetworkLog
import io.legado.app.model.Debug
import io.legado.app.model.ReadBook
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
    private const val DEFAULT_AI_CHAT_CONVERSATION_LIMIT = 20
    private const val MAX_AI_CHAT_CONVERSATION_LIMIT = 50
    private const val DEFAULT_AI_CHAT_MESSAGE_LIMIT = 100
    private const val MAX_AI_CHAT_MESSAGE_LIMIT = 300
    private const val DEFAULT_AI_CHAT_TEXT_CHARS = 8 * 1024
    private const val MAX_AI_CHAT_TEXT_CHARS = 64 * 1024
    private const val DEFAULT_AI_CHAT_UPLOAD_CHARS = 16 * 1024
    private const val MAX_AI_CHAT_UPLOAD_CHARS = 128 * 1024
    private const val DEFAULT_STORYBOARD_PARAGRAPH_LIMIT = 40
    private const val MAX_STORYBOARD_PARAGRAPH_LIMIT = 300
    private const val DEFAULT_STORYBOARD_UNIT_LIMIT = 120
    private const val MAX_STORYBOARD_UNIT_LIMIT = 500
    private const val DEFAULT_STORYBOARD_SEGMENT_LIMIT = 120
    private const val MAX_STORYBOARD_SEGMENT_LIMIT = 500
    private const val DEFAULT_STORYBOARD_TEXT_CHARS = 160
    private const val MAX_STORYBOARD_TEXT_CHARS = 2000
    private const val DEFAULT_BOOK_SOURCE_LIMIT = 100
    private const val MAX_BOOK_SOURCE_LIMIT = 300
    private const val DEFAULT_SEARCH_RESULT_LIMIT = 50
    private const val MAX_SEARCH_RESULT_LIMIT = 200
    private val debugRunLock = Any()
    private val aiChatMessageListType = object : TypeToken<List<AiChatMessageSnapshot>>() {}.type

    fun isEnabled(): Boolean = BuildConfig.DEBUG || appCtx.getPrefBoolean(PreferKey.mcpService, false)

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

    internal fun listInternalTools(capabilityIds: Collection<String>): List<Map<String, Any>> {
        val allowedToolNames = McpInternalToolCatalog.resolveToolNames(capabilityIds)
        return tools().filter { it["name"] in allowedToolNames }
    }

    internal fun callInternalTool(
        name: String,
        arguments: JsonObject,
        capabilityIds: Collection<String>
    ): Map<String, Any?> {
        require(name in McpInternalToolCatalog.resolveToolNames(capabilityIds)) {
            "MCP tool is not enabled for this AI conversation: $name"
        }
        return callTool(JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        })
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
                description = "List Legado book sources with basic identity fields only. Default is paged to avoid large tool results.",
                properties = mapOf(
                    "offset" to mapOf(
                        "type" to "number",
                        "default" to 0
                    ),
                    "limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_BOOK_SOURCE_LIMIT,
                        "maximum" to MAX_BOOK_SOURCE_LIMIT
                    ),
                    "keyword" to stringSchema("Optional keyword matched against source name, URL, group, or comment."),
                    "enabled" to mapOf(
                        "type" to "boolean",
                        "description" to "Optional enabled-state filter"
                    )
                )
            ),
            tool(
                name = "book_source_stats_get",
                description = "Return lightweight book source counts grouped by enabled state, source type, group, and capabilities.",
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
                name = "book_source_delete",
                description = "Delete Legado book sources by bookSourceUrl.",
                properties = mapOf(
                    "urls" to mapOf(
                        "type" to "array",
                        "description" to "BookSource.bookSourceUrl list"
                    )
                ),
                required = listOf("urls")
            ),
            tool(
                name = "book_source_set_enabled",
                description = "Enable or disable Legado book sources by bookSourceUrl.",
                properties = mapOf(
                    "urls" to mapOf(
                        "type" to "array",
                        "description" to "BookSource.bookSourceUrl list"
                    ),
                    "enabled" to mapOf(
                        "type" to "boolean",
                        "default" to true
                    )
                ),
                required = listOf("urls")
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
                description = "Search books across enabled Legado book sources. Returns compact result fields by default; pass include_detail=true only when intro/toc/source detail is needed for returned rows.",
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
                    ),
                    "offset" to mapOf(
                        "type" to "number",
                        "default" to 0
                    ),
                    "limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_SEARCH_RESULT_LIMIT,
                        "maximum" to MAX_SEARCH_RESULT_LIMIT
                    ),
                    "include_detail" to mapOf(
                        "type" to "boolean",
                        "default" to false,
                        "description" to "Include intro/toc/source detail for returned rows"
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
                name = "read_aloud_storyboard_debug_get",
                description = "Return the current read-aloud chapter AI storyboard debug snapshot. This is read-only and does not call the model.",
                properties = mapOf(
                    "include_storyboard" to mapOf(
                        "type" to "boolean",
                        "default" to true
                    ),
                    "include_payload" to mapOf(
                        "type" to "boolean",
                        "default" to false,
                        "description" to "Include the exact model request payload used by the App."
                    ),
                    "paragraph_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_STORYBOARD_PARAGRAPH_LIMIT,
                        "maximum" to MAX_STORYBOARD_PARAGRAPH_LIMIT
                    ),
                    "unit_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_STORYBOARD_UNIT_LIMIT,
                        "maximum" to MAX_STORYBOARD_UNIT_LIMIT
                    ),
                    "segment_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_STORYBOARD_SEGMENT_LIMIT,
                        "maximum" to MAX_STORYBOARD_SEGMENT_LIMIT
                    ),
                    "text_char_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_STORYBOARD_TEXT_CHARS,
                        "maximum" to MAX_STORYBOARD_TEXT_CHARS
                    )
                )
            ),
            tool(
                name = "ai_chat_conversation_list",
                description = "List persisted AI assistant chat conversations as compact summaries.",
                properties = mapOf(
                    "offset" to mapOf(
                        "type" to "number",
                        "default" to 0
                    ),
                    "limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_AI_CHAT_CONVERSATION_LIMIT,
                        "maximum" to MAX_AI_CHAT_CONVERSATION_LIMIT
                    ),
                    "keyword" to stringSchema("Optional keyword matched against title, skill id, message content, reasoning, or tool trace."),
                    "include_empty" to mapOf(
                        "type" to "boolean",
                        "default" to false
                    )
                )
            ),
            tool(
                name = "ai_chat_conversation_get",
                description = "Get one persisted AI assistant chat conversation with message history. Long text is capped by text_char_limit.",
                properties = mapOf(
                    "id" to stringSchema("Conversation id from ai_chat_conversation_list"),
                    "message_offset" to mapOf(
                        "type" to "number",
                        "default" to 0
                    ),
                    "message_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_AI_CHAT_MESSAGE_LIMIT,
                        "maximum" to MAX_AI_CHAT_MESSAGE_LIMIT
                    ),
                    "text_char_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_AI_CHAT_TEXT_CHARS,
                        "maximum" to MAX_AI_CHAT_TEXT_CHARS
                    ),
                    "include_upload_messages" to mapOf(
                        "type" to "boolean",
                        "default" to false
                    ),
                    "upload_char_limit" to mapOf(
                        "type" to "number",
                        "default" to DEFAULT_AI_CHAT_UPLOAD_CHARS,
                        "maximum" to MAX_AI_CHAT_UPLOAD_CHARS
                    )
                ),
                required = listOf("id")
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
        ) + BookshelfMcpTools.tools() + SettingsMcpTools.tools() + AgentMemoryMcpTools.tools()
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
            "book_source_list" -> listBookSources(arguments)
            "book_source_stats_get" -> getBookSourceStats()
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
            "book_source_delete" -> deleteBookSources(arguments)
            "book_source_set_enabled" -> setBookSourcesEnabled(arguments)
            "book_source_debug" -> runBookSourceDebug(arguments)
            "book_search" -> runBookSearch(arguments)
            "bookshelf_search" -> runBookSearch(arguments)
            "network_log_list" -> listNetworkLogs(arguments)
            "network_log_get" -> getNetworkLog(arguments)
            "network_log_clear" -> clearNetworkLogs()
            "read_aloud_storyboard_debug_get" -> getReadAloudStoryboardDebug(arguments)
            "ai_chat_conversation_list" -> listAiChatConversations(arguments)
            "ai_chat_conversation_get" -> getAiChatConversation(arguments)
            "debug_log_list" -> listDebugLogs(arguments)
            "debug_log_get" -> getDebugLog(arguments)
            "debug_log_clear" -> clearDebugLogs()
            else -> BookshelfMcpTools.call(name, arguments)
                ?: SettingsMcpTools.call(name, arguments)
                ?: AgentMemoryMcpTools.call(name, arguments)
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

    private fun deleteBookSources(arguments: JsonObject): Map<String, Any?> {
        val urls = arguments.get("urls").asStringList()
        val sources = urls.mapNotNull { appDb.bookSourceDao.getBookSource(it) }
        sources.forEach { appDb.bookSourceDao.delete(it) }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookSourceDelete",
            normalizedData = mapOf(
                "requested" to urls.size,
                "deleted_count" to sources.size,
                "deleted" to sources.map {
                    mapOf(
                        "url" to it.bookSourceUrl,
                        "name" to it.bookSourceName,
                        "group" to it.bookSourceGroup
                    )
                }
            ),
            warnings = if (sources.size == urls.size) emptyList() else listOf("部分书源 url 未找到")
        )
    }

    private fun setBookSourcesEnabled(arguments: JsonObject): Map<String, Any?> {
        val urls = arguments.get("urls").asStringList()
        val enabled = arguments.get("enabled").asBooleanOrNull() ?: true
        val sources = urls.mapNotNull { appDb.bookSourceDao.getBookSource(it) }
        sources.forEach { appDb.bookSourceDao.enable(it.bookSourceUrl, enabled) }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookSourceSetEnabled",
            normalizedData = mapOf(
                "requested" to urls.size,
                "enabled" to enabled,
                "updated_count" to sources.size,
                "updated" to sources.map {
                    mapOf(
                        "url" to it.bookSourceUrl,
                        "name" to it.bookSourceName,
                        "old_enabled" to it.enabled,
                        "enabled" to enabled
                    )
                }
            ),
            warnings = if (sources.size == urls.size) emptyList() else listOf("部分书源 url 未找到")
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
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_SEARCH_RESULT_LIMIT)
            .coerceIn(1, MAX_SEARCH_RESULT_LIMIT)
        val includeDetail = arguments.get("include_detail").asBooleanOrNull() ?: false
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
                    "books" to emptyList<Map<String, Any?>>(),
                    "offset" to offset,
                    "limit" to limit,
                    "total" to 0,
                    "has_more" to false,
                    "compact" to !includeDetail,
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
        val page = bookSnapshot.drop(offset).take(limit).map { book ->
            if (includeDetail) book.toMcpSearchDetail() else book.toMcpSearchSummary()
        }
        return toolResult(
            ok = error == null,
            upstreamEndpoint = "native://searchBook",
            normalizedData = mapOf(
                "books" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to bookSnapshot.size,
                "has_more" to (offset + page.size < bookSnapshot.size),
                "compact" to !includeDetail,
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

    private fun listBookSources(arguments: JsonObject): Map<String, Any?> {
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_BOOK_SOURCE_LIMIT)
            .coerceIn(1, MAX_BOOK_SOURCE_LIMIT)
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val enabled = arguments.get("enabled").asBooleanOrNull()
        val filtered = appDb.bookSourceDao.all
            .asSequence()
            .filter { enabled == null || it.enabled == enabled }
            .filter {
                keyword == null ||
                    it.bookSourceName.contains(keyword, ignoreCase = true) ||
                    it.bookSourceUrl.contains(keyword, ignoreCase = true) ||
                    it.bookSourceGroup.orEmpty().contains(keyword, ignoreCase = true) ||
                    it.bookSourceComment.orEmpty().contains(keyword, ignoreCase = true)
            }
            .toList()
        val sources = filtered.drop(offset).take(limit).map {
            mapOf(
                "bookSourceComment" to it.bookSourceComment,
                "bookSourceGroup" to it.bookSourceGroup,
                "bookSourceName" to it.bookSourceName,
                "bookSourceType" to it.bookSourceType,
                "bookSourceUrl" to it.bookSourceUrl,
                "enabled" to it.enabled
            )
        }
        return toolResult(
            ok = filtered.isNotEmpty(),
            upstreamEndpoint = "native://bookSourceList",
            normalizedData = mapOf(
                "sources" to sources,
                "offset" to offset,
                "limit" to limit,
                "total" to filtered.size,
                "has_more" to (offset + sources.size < filtered.size)
            ),
            warnings = if (filtered.isEmpty()) listOf("设备源列表为空或没有匹配结果") else emptyList()
        )
    }

    private fun getBookSourceStats(): Map<String, Any?> {
        val sources = appDb.bookSourceDao.all
        val parts = appDb.bookSourceDao.allPart
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://bookSourceStats",
            normalizedData = mapOf(
                "total" to sources.size,
                "enabled" to sources.count { it.enabled },
                "disabled" to sources.count { !it.enabled },
                "enabled_explore" to sources.count { it.enabledExplore },
                "disabled_explore" to sources.count { !it.enabledExplore },
                "login_required" to sources.count { !it.loginUrl.isNullOrBlank() },
                "type_counts" to sourceTypeCounts(sources.map { it.bookSourceType }),
                "group_counts" to sourceGroupCounts(sources.map { it.bookSourceGroup }),
                "capability_counts" to mapOf(
                    "search" to parts.count { it.hasSearchUrl },
                    "explore" to parts.count { it.hasExploreUrl },
                    "login" to parts.count { it.hasLoginUrl },
                    "event_listener" to parts.count { it.eventListener },
                    "enabled_text" to appDb.bookSourceDao.allTextEnabledPart.size
                )
            )
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

    private fun getReadAloudStoryboardDebug(arguments: JsonObject): Map<String, Any?> {
        val includeStoryboard = arguments.get("include_storyboard").asBooleanOrNull() ?: true
        val includePayload = arguments.get("include_payload").asBooleanOrNull() ?: false
        val paragraphLimit = (arguments.get("paragraph_limit").asIntOrNull()
            ?: DEFAULT_STORYBOARD_PARAGRAPH_LIMIT).coerceIn(1, MAX_STORYBOARD_PARAGRAPH_LIMIT)
        val unitLimit = (arguments.get("unit_limit").asIntOrNull()
            ?: DEFAULT_STORYBOARD_UNIT_LIMIT).coerceIn(1, MAX_STORYBOARD_UNIT_LIMIT)
        val segmentLimit = (arguments.get("segment_limit").asIntOrNull()
            ?: DEFAULT_STORYBOARD_SEGMENT_LIMIT).coerceIn(1, MAX_STORYBOARD_SEGMENT_LIMIT)
        val textCharLimit = (arguments.get("text_char_limit").asIntOrNull()
            ?: DEFAULT_STORYBOARD_TEXT_CHARS).coerceIn(0, MAX_STORYBOARD_TEXT_CHARS)
        val book = ReadBook.book
            ?: return toolResult(
                ok = false,
                upstreamEndpoint = "native://readAloud/storyboardDebug",
                normalizedData = null,
                warnings = listOf("当前没有打开的书籍")
            )
        val chapter = ReadBook.curTextChapter
            ?: return toolResult(
                ok = false,
                upstreamEndpoint = "native://readAloud/storyboardDebug",
                normalizedData = mapOf(
                    "book" to mapOf(
                        "name" to book.name,
                        "author" to book.author,
                        "book_url" to book.bookUrl
                    ),
                    "chapter_index" to ReadBook.durChapterIndex
                ),
                warnings = listOf("当前章节内容尚未加载")
            )
        val readAloudContent = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter)
        val rawContent = chapter.getContent()
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = if (workKey.isBlank()) {
            emptyList()
        } else {
            appDb.bookCharacterDao.getCharacters(workKey)
        }
        val data = linkedMapOf<String, Any?>(
            "read_aloud_source" to mapOf(
                "content_chars" to readAloudContent.length,
                "paragraph_count" to AiTtsStoryboardHelper.paragraphsFromContent(readAloudContent).size,
                "preview" to readAloudContent.limitMcpText(textCharLimit)
            ),
            "raw_chapter_source" to mapOf(
                "content_chars" to rawContent.length,
                "preview" to rawContent.limitMcpText(textCharLimit)
            ),
            "storyboard_snapshot" to AiTtsStoryboardHelper.debugSnapshot(
                book = book,
                chapterIndex = ReadBook.durChapterIndex,
                chapterTitle = chapter.title,
                content = readAloudContent,
                characters = characters,
                includeStoryboard = includeStoryboard,
                includePayload = includePayload,
                paragraphLimit = paragraphLimit,
                unitLimit = unitLimit,
                segmentLimit = segmentLimit,
                textCharLimit = textCharLimit
            )
        )
        val warnings = buildList {
            if (readAloudContent.isBlank()) {
                add("当前朗读源为空")
            }
            if (readAloudContent != rawContent) {
                add("朗读源与原章节正文不完全一致，可能包含朗读过滤或分页处理")
            }
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://readAloud/storyboardDebug",
            normalizedData = data,
            warnings = warnings
        )
    }

    private fun listAiChatConversations(arguments: JsonObject): Map<String, Any?> {
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_AI_CHAT_CONVERSATION_LIMIT)
            .coerceIn(1, MAX_AI_CHAT_CONVERSATION_LIMIT)
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val includeEmpty = arguments.get("include_empty").asBooleanOrNull() ?: false
        val total = appDb.aiChatDao.countConversations()
        val records = appDb.aiChatDao.getConversations((offset + limit + 100).coerceAtLeast(MAX_AI_CHAT_CONVERSATION_LIMIT))
            .map { conversation ->
                val messages = appDb.aiChatDao.getMessageNodes(conversation.id)
                    .flatMap { it.toAiChatMessages() }
                conversation to messages
            }
        val filtered = records.filter { (conversation, messages) ->
            (includeEmpty || messages.isNotEmpty()) &&
                    (keyword == null || conversation.matchesAiChatKeyword(keyword, messages))
        }
        val page = filtered.drop(offset).take(limit).map { (conversation, messages) ->
            conversation.toAiChatConversationSummary(messages)
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://aiChat/conversationList",
            normalizedData = mapOf(
                "conversations" to page,
                "offset" to offset,
                "limit" to limit,
                "total" to total,
                "filtered_total" to filtered.size,
                "has_more" to (offset + page.size < filtered.size)
            )
        )
    }

    private fun getAiChatConversation(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asRequiredString("id")
        val messageOffset = (arguments.get("message_offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val messageLimit = (arguments.get("message_limit").asIntOrNull() ?: DEFAULT_AI_CHAT_MESSAGE_LIMIT)
            .coerceIn(1, MAX_AI_CHAT_MESSAGE_LIMIT)
        val textCharLimit = (arguments.get("text_char_limit").asIntOrNull() ?: DEFAULT_AI_CHAT_TEXT_CHARS)
            .coerceIn(0, MAX_AI_CHAT_TEXT_CHARS)
        val includeUploadMessages = arguments.get("include_upload_messages").asBooleanOrNull() ?: false
        val uploadCharLimit = (arguments.get("upload_char_limit").asIntOrNull() ?: DEFAULT_AI_CHAT_UPLOAD_CHARS)
            .coerceIn(0, MAX_AI_CHAT_UPLOAD_CHARS)
        val conversation = appDb.aiChatDao.getConversation(id)
            ?: return toolResult(
                ok = false,
                upstreamEndpoint = "native://aiChat/conversationGet",
                normalizedData = null,
                warnings = listOf("未找到 AI 聊天会话")
            )
        val messages = appDb.aiChatDao.getMessageNodes(id)
            .flatMap { node ->
                node.toAiChatMessages().mapIndexed { messageIndex, message ->
                    node to (messageIndex to message)
                }
            }
        val page = messages.drop(messageOffset).take(messageLimit).map { (node, indexedMessage) ->
            val (messageIndex, message) = indexedMessage
            message.toAiChatMessageDetail(node, messageIndex, textCharLimit)
        }
        val data = linkedMapOf<String, Any?>(
            "conversation" to conversation.toAiChatConversationSummary(messages.map { it.second.second }),
            "messages" to page,
            "message_offset" to messageOffset,
            "message_limit" to messageLimit,
            "message_total" to messages.size,
            "has_more_messages" to (messageOffset + page.size < messages.size),
            "text_char_limit" to textCharLimit
        )
        if (includeUploadMessages) {
            val uploadMessages = conversation.uploadMessages.limitMcpText(uploadCharLimit)
            data["upload_messages_json"] = uploadMessages
            data["upload_messages_chars"] = conversation.uploadMessages.length
            data["upload_messages_truncated_by_mcp"] = conversation.uploadMessages.length > uploadCharLimit
        }
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://aiChat/conversationGet",
            normalizedData = data
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

    private fun AiChatConversation.toAiChatConversationSummary(
        messages: List<AiChatMessageSnapshot>
    ): Map<String, Any?> {
        val lastMessage = messages.lastOrNull()
        val toolTraceCount = messages.sumOf { it.toolTrace.size }
        return mapOf(
            "id" to id,
            "title" to title,
            "assistant_id" to assistantId,
            "create_at" to createAt,
            "create_time_text" to formatMcpDateTime(createAt),
            "update_at" to updateAt,
            "update_time_text" to formatMcpDateTime(updateAt),
            "is_pinned" to isPinned,
            "loaded_skill_ids" to loadedSkillIds.toAiChatStringList(),
            "message_count" to messages.size,
            "user_message_count" to messages.count { it.role == AiChatMessageSnapshot.ROLE_USER },
            "assistant_message_count" to messages.count { it.role == AiChatMessageSnapshot.ROLE_ASSISTANT },
            "last_message_role" to lastMessage?.role,
            "last_message_preview" to lastMessage?.content?.lineSequence()?.firstOrNull().orEmpty().limitMcpText(500),
            "has_reasoning" to messages.any { !it.reasoning.isNullOrBlank() },
            "tool_trace_count" to toolTraceCount,
            "has_upload_messages" to (uploadMessages.isNotBlank() && uploadMessages != "[]")
        )
    }

    private fun AiChatMessageSnapshot.toAiChatMessageDetail(
        node: AiChatMessageNode,
        messageIndex: Int,
        textCharLimit: Int
    ): Map<String, Any?> {
        val reasoningText = reasoning
        return mapOf(
            "node_id" to node.id,
            "node_index" to node.nodeIndex,
            "select_index" to node.selectIndex,
            "message_index" to messageIndex,
            "id" to id,
            "role" to role,
            "content" to content.limitMcpText(textCharLimit),
            "content_chars" to content.length,
            "content_truncated_by_mcp" to (content.length > textCharLimit),
            "meta" to meta,
            "reasoning" to reasoningText?.limitMcpText(textCharLimit),
            "reasoning_chars" to (reasoningText?.length ?: 0),
            "reasoning_truncated_by_mcp" to ((reasoningText?.length ?: 0) > textCharLimit),
            "tool_trace" to toolTrace.map { it.limitMcpText(textCharLimit) },
            "tool_trace_count" to toolTrace.size,
            "elapsed_ms" to elapsedMs,
            "favorite" to favorite
        )
    }

    private fun AiChatConversation.matchesAiChatKeyword(
        keyword: String,
        messages: List<AiChatMessageSnapshot>
    ): Boolean {
        return sequenceOf(
            id,
            title,
            assistantId,
            loadedSkillIds,
            customSystemPrompt
        ).any { it.contains(keyword, ignoreCase = true) } ||
                messages.any { message ->
                    sequenceOf(
                        message.id,
                        message.role,
                        message.content,
                        message.meta,
                        message.reasoning,
                        message.toolTrace.joinToString("\n")
                    ).filterNotNull().any { it.contains(keyword, ignoreCase = true) }
                }
    }

    private fun AiChatMessageNode.toAiChatMessages(): List<AiChatMessageSnapshot> {
        return runCatching {
            GSON.fromJson<List<AiChatMessageSnapshot>>(messages, aiChatMessageListType)
        }.getOrNull().orEmpty()
    }

    private fun String.toAiChatStringList(): List<String> {
        return runCatching {
            JsonParser.parseString(this).asJsonArray
        }.getOrNull()
            ?.mapNotNull { it.asStringOrNull()?.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
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

    private fun SearchBook.toMcpSearchSummary(): Map<String, Any?> {
        return mapOf(
            "book_url" to bookUrl,
            "name" to name,
            "author" to author,
            "origin" to origin,
            "origin_name" to originName,
            "type" to type,
            "kind" to kind,
            "word_count" to wordCount,
            "latest_chapter_title" to latestChapterTitle,
            "respond_time" to respondTime
        )
    }

    private fun SearchBook.toMcpSearchDetail(): Map<String, Any?> {
        return toMcpSearchSummary() + mapOf(
            "cover_url" to coverUrl,
            "intro" to intro,
            "toc_url" to tocUrl,
            "origin_order" to originOrder,
            "chapter_word_count" to chapterWordCount,
            "chapter_word_count_text" to chapterWordCountText
        )
    }

    private fun formatNetworkLogTime(time: Long): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
    }

    private fun formatMcpDateTime(time: Long): String {
        return if (time > 0L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
        } else {
            ""
        }
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

    private fun sourceTypeCounts(types: List<Int>): List<Map<String, Any>> {
        return types
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
            .map { (type, count) ->
                mapOf(
                    "type" to type,
                    "label" to sourceTypeLabel(type),
                    "count" to count
                )
            }
    }

    private fun sourceTypeLabel(type: Int): String {
        return when (type) {
            0 -> "text"
            1 -> "audio"
            2 -> "image"
            3 -> "file"
            4 -> "video"
            else -> "unknown"
        }
    }

    private fun sourceGroupCounts(groups: List<String?>): List<Map<String, Any>> {
        val counts = linkedMapOf<String, Int>()
        groups.forEach { value ->
            val names = splitGroupNames(value).ifEmpty { listOf("未分组") }
            names.forEach { name ->
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return counts
            .map { (group, count) -> mapOf("group" to group, "count" to count) }
            .sortedWith(compareByDescending<Map<String, Any>> { it["count"] as Int }.thenBy { it["group"] as String })
    }

    private fun splitGroupNames(value: String?): List<String> {
        return value.orEmpty()
            .split(',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
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

    private fun JsonElement?.asStringList(): List<String> {
        val element = this ?: throw IllegalArgumentException("urls is required")
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull()?.trim() }
                .filter { it.isNotEmpty() }
            element.isJsonPrimitive -> element.asString.split(',', '，')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }.also {
            if (it.isEmpty()) throw IllegalArgumentException("urls is required")
        }
    }
}
