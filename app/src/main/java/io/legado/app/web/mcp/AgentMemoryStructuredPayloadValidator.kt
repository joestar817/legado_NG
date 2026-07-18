package io.legado.app.web.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Validates generic chapter-scoped structured memory payloads before persistence.
 *
 * This is intentionally shape based: no workflow, book, tag, or risk identifier is known here.
 * A payload opts into the invariant by declaring a root-level chapter_range.
 */
internal object AgentMemoryStructuredPayloadValidator {

    fun validateExplicitPayload(arguments: JsonObject, argumentPath: String = "") {
        val (fieldName, payload) = explicitPayload(arguments) ?: return
        val root = payload.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val rootPath = listOf(argumentPath.trimEnd('.'), fieldName)
            .filter { it.isNotBlank() }
            .joinToString(".")
        validateChapterScopedRoot(root, rootPath)
    }

    private fun explicitPayload(arguments: JsonObject): Pair<String, JsonElement>? {
        for (fieldName in listOf("data_json", "dataJson")) {
            val value = arguments.get(fieldName) ?: continue
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
            val parsed = runCatching { JsonParser.parseString(value.asString) }.getOrNull()
                ?: return null
            return fieldName to parsed
        }
        return arguments.get("data")?.let { "data" to it }
    }

    private fun validateChapterScopedRoot(root: JsonObject, rootPath: String) {
        val chapterRange = root.get("chapter_range") ?: return
        require(chapterRange.isJsonArray && chapterRange.asJsonArray.size() == 2) {
            "$rootPath.chapter_range must be a [start,end) integer array"
        }
        val start = strictInt(chapterRange.asJsonArray[0])
        val end = strictInt(chapterRange.asJsonArray[1])
        require(start != null && end != null && start >= 0 && end >= start) {
            "$rootPath.chapter_range must be a valid [start,end) integer range"
        }
        walk(
            value = root,
            path = rootPath,
            rangeStart = start,
            rangeEnd = end
        )
    }

    private fun walk(
        value: JsonElement,
        path: String,
        rangeStart: Int,
        rangeEnd: Int
    ) {
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) ->
                val childPath = "$path.$key"
                if (key == "evidence_refs") {
                    validateEvidenceRefs(child, childPath, rangeStart, rangeEnd)
                } else {
                    walk(child, childPath, rangeStart, rangeEnd)
                }
            }

            value.isJsonArray -> value.asJsonArray.forEachIndexed { index, child ->
                walk(child, "$path[$index]", rangeStart, rangeEnd)
            }
        }
    }

    private fun validateEvidenceRefs(
        value: JsonElement,
        path: String,
        rangeStart: Int,
        rangeEnd: Int
    ) {
        require(value.isJsonArray) { "$path must be an array" }
        value.asJsonArray.forEachIndexed { index, element ->
            val evidencePath = "$path[$index]"
            require(element.isJsonObject) { "$evidencePath must be an object" }
            val chapterIndex = strictInt(element.asJsonObject.get("chapter_index"))
            require(chapterIndex != null) {
                "$evidencePath.chapter_index must be an integer"
            }
            require(chapterIndex >= rangeStart && chapterIndex < rangeEnd) {
                "$evidencePath.chapter_index=$chapterIndex is outside " +
                    "chapter_range=[$rangeStart,$rangeEnd)"
            }
        }
    }

    private fun strictInt(value: JsonElement?): Int? {
        val primitive = value?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        if (!primitive.isNumber || !INTEGER_REGEX.matches(primitive.asString)) return null
        return primitive.asString.toIntOrNull()
    }

    private val INTEGER_REGEX = Regex("-?(?:0|[1-9][0-9]*)")
}
