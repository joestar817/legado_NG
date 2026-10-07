package io.legado.app.web.mcp

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Shared wire validation for the discovery/subscription tools. Identifiers are never trimmed. */
internal object McpModuleSupport {
    fun field(type: String, description: String): Map<String, Any> =
        mapOf("type" to type, "description" to description)

    fun tool(name: String, description: String, properties: Map<String, Any>, vararg required: String) =
        mapOf<String, Any>(
            "name" to name, "description" to description,
            "inputSchema" to mapOf("type" to "object", "properties" to properties,
                "required" to required.toList(), "additionalProperties" to false)
        )

    val paging = mapOf("offset" to field("integer", "Local result offset, default 0"),
        "limit" to field("integer", "Local result limit, 1..200, default 50"))
    val timeout = mapOf("timeout_seconds" to field("integer", "Timeout, 1..120 seconds, default 30"))
    val urls = mapOf("urls" to mapOf("type" to "array", "minItems" to 1, "maxItems" to 200,
        "items" to field("string", "Exact source identifier")))

    fun validate(name: String, args: JsonObject, definitions: List<Map<String, Any>>) {
        val schema = definitions.single { it["name"] == name }["inputSchema"] as Map<*, *>
        val properties = schema["properties"] as Map<*, *>
        require(args.keySet().all { it in properties }) { "Unknown argument for $name" }
        (schema["required"] as List<*>).forEach { require(args.has(it as String)) { "$it is required" } }
        args.entrySet().forEach { (key, value) ->
            val type = (properties[key] as Map<*, *>)["type"]
            val valid = when (type) {
                "object" -> value.isJsonObject
                "array" -> value.isJsonArray
                "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                "boolean" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
                "integer" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
                    runCatching { value.asBigDecimal.intValueExact() }.isSuccess
                else -> false
            }
            require(valid) { "$key must be $type" }
        }
        if (args.has("offset")) args.int("offset", 0)
        if (args.has("limit")) args.int("limit", 50, 1, 200)
        if (args.has("page")) args.int("page", 1, 1)
        if (args.has("timeout_seconds")) args.int("timeout_seconds", 30, 1, 120)
    }

    fun JsonObject.string(key: String, required: Boolean = false): String? {
        val value = get(key) ?: return if (required) error("$key is required") else null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$key must be a string" }
        return value.asString.also { require(!required || it.isNotBlank()) { "$key must not be blank" } }
    }

    fun JsonObject.int(key: String, default: Int, min: Int = 0, max: Int = Int.MAX_VALUE): Int {
        val value = get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key must be an integer" }
        val number = runCatching { value.asBigDecimal.intValueExact() }
            .getOrElse { error("$key must be an integer") }
        require(number in min..max) { "$key must be in $min..$max" }
        return number
    }

    fun JsonObject.bool(key: String, default: Boolean? = null): Boolean? {
        val value = get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$key must be a boolean" }
        return value.asBoolean
    }

    fun JsonObject.obj(key: String): JsonObject {
        val value = get(key)
        require(value?.isJsonObject == true) { "$key must be an object" }
        return value.asJsonObject
    }

    fun JsonObject.identifiers(): List<String> {
        val value = get("urls")
        require(value?.isJsonArray == true && value.asJsonArray.size() in 1..200) { "urls must contain 1..200 identifiers" }
        val values = value.asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank()) { "Invalid source identifier" }
            it.asString
        }
        require(values.distinct().size == values.size) { "Duplicate source identifiers" }
        return values
    }

    fun result(name: String, data: Any?, error: String? = null): Map<String, Any?> = mapOf(
        "ok" to (error == null), "upstream_endpoint" to "native://$name", "normalized_data" to data,
        "raw_upstream" to null, "warnings" to listOfNotNull(error), "session_id" to null
    )

    fun <T> page(items: List<T>, args: JsonObject, key: String): Map<String, Any?> {
        val offset = args.int("offset", 0)
        val limit = args.int("limit", 50, 1, 200)
        val selected = items.drop(offset).take(limit)
        return mapOf(key to selected, "total" to items.size, "offset" to offset, "limit" to limit,
            "next_offset" to (offset + selected.size).takeIf { it < items.size })
    }

    fun <T> timed(args: JsonObject, block: suspend () -> T): T = runBlocking(Dispatchers.IO) {
        withTimeout(args.int("timeout_seconds", 30, 1, 120) * 1000L) { block() }
    }
}

/** Wire offsets use UTF-16 units; never send half a surrogate pair through UTF-8 JSON. */
internal fun mcpTextWindow(body: String, offset: Int, maxChars: Int): Map<String, Any?> {
    require(offset >= 0 && maxChars > 0)
    var start = offset.coerceAtMost(body.length)
    if (start in 1 until body.length && body[start].isLowSurrogate() && body[start - 1].isHighSurrogate()) start--
    var end = (start.toLong() + maxChars).coerceAtMost(body.length.toLong()).toInt()
    if (end in 1 until body.length && body[end - 1].isHighSurrogate() && body[end].isLowSurrogate()) {
        // A one-unit window at an emoji must still make progress.
        if (end - 1 == start) end++ else end--
    }
    return mapOf("content" to body.substring(start, end), "total_chars" to body.length,
        "offset" to start, "next_offset" to end.takeIf { it < body.length })
}
