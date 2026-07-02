package io.legado.app.web.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.AgentMemory
import io.legado.app.help.ai.AiConfig
import io.legado.app.utils.GSON
import java.util.UUID

object AgentMemoryMcpTools {

    private const val DEFAULT_LIMIT = 10
    private const val MAX_LIMIT = 50

    fun tools(): List<Map<String, Any>> {
        return listOf(
            tool(
                name = "agent_memory_status_get",
                description = "Check whether the AI assistant memory system is enabled.",
                properties = emptyMap()
            ),
            tool(
                name = "agent_memory_search",
                description = "Search AI assistant memories by concrete object scope, domain, type, and keyword. Returns empty when memory is disabled.",
                properties = mapOf(
                    "scope_type" to stringSchema("Object type, for example book, book_source, conversation, global"),
                    "scope_key" to stringSchema("Stable object key. Prefer natural keys such as book title + author over source-specific URLs."),
                    "subject" to stringSchema("Optional human-readable object subject filter"),
                    "domain" to stringSchema("Optional business domain, for example character_card"),
                    "memory_type" to stringSchema("Optional memory type, for example checkpoint, fact, preference, decision"),
                    "keyword" to stringSchema("Optional keyword matched against title, content, subject, or tags"),
                    "status" to stringSchema("Default active. Pass empty string to include all statuses."),
                    "offset" to numberSchema("Default 0"),
                    "limit" to numberSchema("Default 10, max 50")
                )
            ),
            tool(
                name = "agent_memory_upsert",
                description = "Create or update one AI assistant memory. Only use after a final user-confirmed operation succeeds.",
                properties = mapOf(
                    "id" to stringSchema("Optional memory id. Omit to create a new id."),
                    "scope_type" to stringSchema("Required concrete object type, for example book or book_source"),
                    "scope_key" to stringSchema("Required stable object key"),
                    "subject" to stringSchema("Human-readable subject"),
                    "domain" to stringSchema("Required business domain"),
                    "memory_type" to stringSchema("Default checkpoint"),
                    "title" to stringSchema("Required short title"),
                    "content" to stringSchema("Required memory content"),
                    "data" to mapOf("type" to "object", "description" to "Optional structured payload"),
                    "data_json" to stringSchema("Optional JSON string payload"),
                    "tags" to mapOf("type" to "array", "description" to "Optional tag list"),
                    "confidence" to numberSchema("Default 1"),
                    "source" to stringSchema("Default ai"),
                    "status" to stringSchema("Default active")
                ),
                required = listOf("scope_type", "scope_key", "domain", "title", "content")
            ),
            tool(
                name = "agent_memory_archive",
                description = "Archive AI assistant memories by id.",
                properties = mapOf(
                    "ids" to mapOf("type" to "array", "description" to "Memory id list")
                ),
                required = listOf("ids")
            )
        )
    }

    fun call(name: String, arguments: JsonObject): Map<String, Any?>? {
        return when (name) {
            "agent_memory_status_get" -> status()
            "agent_memory_search" -> search(arguments)
            "agent_memory_upsert" -> upsert(arguments)
            "agent_memory_archive" -> archive(arguments)
            else -> null
        }
    }

    private fun status(): Map<String, Any?> {
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://agentMemory/status",
            normalizedData = mapOf(
                "enabled" to AiConfig.memoryEnabled,
                "policy" to "When disabled, agents must not read or write memories."
            )
        )
    }

    private fun search(arguments: JsonObject): Map<String, Any?> {
        if (!AiConfig.memoryEnabled) {
            return disabledResult(
                upstreamEndpoint = "native://agentMemory/search",
                ok = true,
                normalizedData = mapOf(
                    "enabled" to false,
                    "memories" to emptyList<Any>(),
                    "offset" to 0,
                    "limit" to 0,
                    "total" to 0,
                    "has_more" to false
                )
            )
        }
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val scopeType = arguments.get("scope_type").asStringOrNull()
            ?: arguments.get("scopeType").asStringOrNull()
            ?: ""
        val scopeKey = arguments.get("scope_key").asStringOrNull()
            ?: arguments.get("scopeKey").asStringOrNull()
            ?: ""
        val domain = arguments.get("domain").asStringOrNull().orEmpty()
        val memoryType = arguments.get("memory_type").asStringOrNull()
            ?: arguments.get("memoryType").asStringOrNull()
            ?: ""
        val subject = arguments.get("subject").asStringOrNull().orEmpty()
        val keyword = arguments.get("keyword").asStringOrNull().orEmpty()
        val status = arguments.get("status").asStringOrNull() ?: "active"
        val memories = appDb.agentMemoryDao.search(
            scopeType = scopeType.trim(),
            scopeKey = scopeKey.trim(),
            subject = subject.trim(),
            domain = domain.trim(),
            memoryType = memoryType.trim(),
            keyword = keyword.trim(),
            status = status.trim(),
            offset = offset,
            limit = limit
        )
        val total = appDb.agentMemoryDao.count(
            scopeType = scopeType.trim(),
            scopeKey = scopeKey.trim(),
            subject = subject.trim(),
            domain = domain.trim(),
            memoryType = memoryType.trim(),
            keyword = keyword.trim(),
            status = status.trim()
        )
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://agentMemory/search",
            normalizedData = mapOf(
                "enabled" to true,
                "memories" to memories.map { it.toMcpMap() },
                "offset" to offset,
                "limit" to limit,
                "total" to total,
                "has_more" to (offset + memories.size < total)
            )
        )
    }

    private fun upsert(arguments: JsonObject): Map<String, Any?> {
        if (!AiConfig.memoryEnabled) {
            return disabledResult(
                upstreamEndpoint = "native://agentMemory/upsert",
                ok = false,
                normalizedData = mapOf("enabled" to false)
            )
        }
        val id = arguments.get("id").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString()
        val existing = appDb.agentMemoryDao.get(id)
        val now = System.currentTimeMillis()
        val memory = AgentMemory(
            id = id,
            scopeType = arguments.get("scope_type").asRequiredString("scope_type"),
            scopeKey = arguments.get("scope_key").asRequiredString("scope_key"),
            subject = arguments.get("subject").asStringOrNull()?.trim().orEmpty(),
            domain = arguments.get("domain").asRequiredString("domain"),
            memoryType = arguments.get("memory_type").asStringOrNull()
                ?: arguments.get("memoryType").asStringOrNull()
                ?: existing?.memoryType
                ?: "checkpoint",
            title = arguments.get("title").asRequiredString("title"),
            content = arguments.get("content").asRequiredString("content"),
            dataJson = arguments.get("data_json").asStringOrNull()
                ?: arguments.get("dataJson").asStringOrNull()
                ?: arguments.get("data")?.let { GSON.toJson(it) }
                ?: existing?.dataJson
                ?: "{}",
            tags = arguments.get("tags").asStringListOrEmpty().joinToString(","),
            confidence = arguments.get("confidence").asFloatOrNull() ?: existing?.confidence ?: 1f,
            source = arguments.get("source").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: existing?.source
                ?: "ai",
            status = arguments.get("status").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: existing?.status
                ?: "active",
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        appDb.agentMemoryDao.upsert(memory)
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://agentMemory/upsert",
            normalizedData = mapOf(
                "created" to (existing == null),
                "memory" to appDb.agentMemoryDao.get(id)?.toMcpMap()
            )
        )
    }

    private fun archive(arguments: JsonObject): Map<String, Any?> {
        if (!AiConfig.memoryEnabled) {
            return disabledResult(
                upstreamEndpoint = "native://agentMemory/archive",
                ok = false,
                normalizedData = mapOf("enabled" to false)
            )
        }
        val ids = arguments.get("ids").asStringList("ids")
        val updated = appDb.agentMemoryDao.updateStatus(ids, "archived", System.currentTimeMillis())
        return toolResult(
            ok = true,
            upstreamEndpoint = "native://agentMemory/archive",
            normalizedData = mapOf(
                "requested" to ids.size,
                "updated" to updated,
                "ids" to ids
            )
        )
    }

    private fun disabledResult(
        upstreamEndpoint: String,
        ok: Boolean,
        normalizedData: Any?
    ): Map<String, Any?> {
        return toolResult(
            ok = ok,
            upstreamEndpoint = upstreamEndpoint,
            normalizedData = normalizedData,
            warnings = listOf("记忆系统未开启，未读取或写入记忆")
        )
    }

    private fun AgentMemory.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "scope_type" to scopeType,
            "scope_key" to scopeKey,
            "subject" to subject,
            "domain" to domain,
            "memory_type" to memoryType,
            "title" to title,
            "content" to content,
            "data_json" to dataJson,
            "tags" to splitTags(tags),
            "confidence" to confidence,
            "source" to source,
            "status" to status,
            "created_at" to createdAt,
            "updated_at" to updatedAt
        )
    }

    private fun splitTags(value: String): List<String> {
        return value.split(',', '，')
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
        return mapOf("type" to "string", "description" to description)
    }

    private fun numberSchema(description: String): Map<String, String> {
        return mapOf("type" to "number", "description" to description)
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return this?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
    }

    private fun JsonElement?.asFloatOrNull(): Float? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asFloat }.getOrNull()
    }

    private fun JsonElement?.asRequiredString(name: String): String {
        return asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$name is required")
    }

    private fun JsonElement?.asStringList(name: String): List<String> {
        val values = asStringListOrEmpty()
        if (values.isEmpty()) throw IllegalArgumentException("$name is required")
        return values
    }

    private fun JsonElement?.asStringListOrEmpty(): List<String> {
        val element = this ?: return emptyList()
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull()?.trim() }
            element.isJsonPrimitive -> element.asString.split(',', '，').map { it.trim() }
            else -> emptyList()
        }.filter { it.isNotEmpty() }
    }
}
