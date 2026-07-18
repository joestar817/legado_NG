package io.legado.app.help.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON

/**
 * A Mode-owned, conversation-scoped entry context.
 *
 * The Agent kernel treats [payload] as opaque data. Business fields and their
 * meaning belong to the System Workflow that owns the Mode.
 */
data class AgentModeEntryContext(
    @SerializedName("schema_version")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerializedName("context_id")
    val contextId: String,
    @SerializedName("title")
    val title: String = "",
    @SerializedName("payload")
    val payload: JsonObject = JsonObject()
) {

    init {
        validate(schemaVersion, contextId, title, payload)
    }

    fun validatedCopyOrNull(): AgentModeEntryContext? {
        return runCatching {
            AgentModeEntryContext(
                schemaVersion = schemaVersion,
                contextId = contextId,
                title = title,
                payload = payload.deepCopy()
            )
        }.getOrNull()
    }

    fun toJson(): String {
        val validated = validatedCopyOrNull()
            ?: throw IllegalArgumentException("Agent Mode entry context 不合法")
        return GSON.toJsonTree(validated).toString()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_PAYLOAD_CHARS = 32 * 1024
        const val MAX_SERIALIZED_CHARS = 40 * 1024
        private const val MAX_TITLE_CHARS = 160
        private const val MAX_KEY_CHARS = 128
        private const val MAX_DEPTH = 8
        private val CONTEXT_ID_REGEX = Regex("^[a-z][a-z0-9_.-]{0,63}$")

        fun fromJsonOrNull(raw: String?): AgentModeEntryContext? {
            val text = raw?.trim().orEmpty()
            if (text.isBlank() || text.length > MAX_SERIALIZED_CHARS) return null
            val root = runCatching {
                JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull() ?: return null
            val schemaVersionElement = root.get("schema_version")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?: return null
            val schemaVersion = runCatching { schemaVersionElement.asInt }.getOrNull()
                ?: return null
            val contextId = root.stringValue("context_id") ?: return null
            val title = root.stringValue("title").orEmpty()
            val payload = root.get("payload")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.deepCopy()
                ?: return null
            return runCatching {
                AgentModeEntryContext(
                    schemaVersion = schemaVersion,
                    contextId = contextId,
                    title = title,
                    payload = payload
                )
            }.getOrNull()
        }

        private fun validate(
            schemaVersion: Int,
            contextId: String,
            title: String,
            payload: JsonObject
        ) {
            require(schemaVersion == CURRENT_SCHEMA_VERSION) {
                "不支持的 Agent Mode entry context schema：$schemaVersion"
            }
            require(contextId.matches(CONTEXT_ID_REGEX)) {
                "Agent Mode entry context id 不合法：$contextId"
            }
            require(title.length <= MAX_TITLE_CHARS && !title.hasForbiddenControlChars()) {
                "Agent Mode entry context title 不合法"
            }
            require(payload.toString().length <= MAX_PAYLOAD_CHARS) {
                "Agent Mode entry context payload 过大"
            }
            require(payload.isSafeJson(depth = 0)) {
                "Agent Mode entry context payload 不合法"
            }
        }

        private fun JsonObject.stringValue(name: String): String? {
            return get(name)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
        }

        private fun JsonElement.isSafeJson(depth: Int): Boolean {
            if (depth > MAX_DEPTH) return false
            return when {
                isJsonNull -> true
                isJsonPrimitive -> {
                    val primitive = asJsonPrimitive
                    !primitive.isString || !primitive.asString.hasForbiddenControlChars()
                }
                isJsonArray -> asJsonArray.all { it.isSafeJson(depth + 1) }
                isJsonObject -> asJsonObject.entrySet().all { (key, value) ->
                    key.isNotBlank() &&
                        key.length <= MAX_KEY_CHARS &&
                        !key.hasForbiddenControlChars() &&
                        value.isSafeJson(depth + 1)
                }
                else -> false
            }
        }

        private fun String.hasForbiddenControlChars(): Boolean {
            return any { char ->
                char.code < 0x20 && char != '\n' && char != '\r' && char != '\t'
            }
        }
    }
}
