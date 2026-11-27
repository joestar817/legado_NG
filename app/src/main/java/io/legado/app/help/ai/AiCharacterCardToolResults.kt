package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** Compacts the reader's model messages while the original results remain available as receipts. */
internal object AiCharacterCardToolResults {
    private const val CACHE_STATUS = "bookshelf_cache_status_get"
    private const val TEXT_SEARCH = "bookshelf_text_search"
    private val cacheFields = setOf("index", "chapter_number", "title", "has_content", "is_volume")
    private val searchColumns = listOf("index", "title", "count", "hits")
    private val searchFields = searchColumns.toSet() + "chapter_number"
    private val searchFieldsWithSnippets = searchFields + "snippets"

    fun compact(toolName: String, result: JsonObject): JsonObject {
        if (toolName != CACHE_STATUS && toolName != TEXT_SEARCH) return result
        val structured = result.get("structuredContent") as? JsonObject ?: return result
        if (structured.get("ok") != JsonPrimitive(true)) return result
        val data = structured.get("normalized_data") as? JsonObject ?: return result
        val chapters = data.get("chapters") as? JsonArray ?: return result
        if (chapters.size() == 0) return result

        val compactChapters = when (toolName) {
            CACHE_STATUS -> compactCacheChapters(chapters)
            else -> compactSearchChapters(chapters)
        } ?: return result
        val modelData = JsonObject().apply {
            data.entrySet().forEach { (name, value) -> if (name != "chapters") add(name, value) }
            compactChapters.entrySet().forEach { (name, value) -> add(name, value) }
            addProperty("chapter_number_is_index_plus_one", true)
        }
        return result.replacing("structuredContent", structured.replacing("normalized_data", modelData))
    }

    private fun compactCacheChapters(chapters: JsonArray): JsonObject? {
        val indexes = mutableListOf<Int>()
        val cachedIndexes = mutableListOf<Int>()
        val volumeIndexes = mutableListOf<Int>()
        val titles = JsonArray(chapters.size())
        for (row in chapters) {
            val chapter = row as? JsonObject ?: return null
            if (chapter.keySet() != cacheFields) return null
            val index = chapter.consistentChapterIndex() ?: return null
            if (index <= (indexes.lastOrNull() ?: -1)) return null
            val hasContent = chapter.booleanField("has_content") ?: return null
            val isVolume = chapter.booleanField("is_volume") ?: return null
            indexes.add(index)
            if (hasContent) cachedIndexes.add(index)
            if (isVolume) volumeIndexes.add(index)
            titles.add(chapter.get("title"))
        }
        return JsonObject().apply {
            add("chapter_index_ranges", indexRanges(indexes))
            add("cached_chapter_index_ranges", indexRanges(cachedIndexes))
            add("volume_chapter_index_ranges", indexRanges(volumeIndexes))
            add("chapter_titles", titles)
        }
    }

    private fun compactSearchChapters(chapters: JsonArray): JsonObject? {
        val hasSnippets = chapters.any { it is JsonObject && it.has("snippets") }
        val columns = if (hasSnippets) searchColumns + "snippets" else searchColumns
        val rows = JsonArray(chapters.size())
        for (row in chapters) {
            val chapter = row as? JsonObject ?: return null
            val fields = if (chapter.has("snippets")) searchFieldsWithSnippets else searchFields
            if (chapter.keySet() != fields || chapter.consistentChapterIndex() == null) return null
            if (chapter.has("snippets") && chapter.get("snippets") !is JsonArray) return null
            rows.add(JsonArray(columns.size).apply {
                columns.forEach { add(chapter.get(it) ?: JsonNull.INSTANCE) }
            })
        }
        return JsonObject().apply {
            add("chapter_columns", JsonArray(columns.size).apply { columns.forEach { add(it) } })
            add("chapters", rows)
        }
    }

    private fun JsonObject.consistentChapterIndex(): Int? {
        val index = (get("index") as? JsonPrimitive)?.takeIf { it.isNumber }
            ?.asString?.toIntOrNull()?.takeIf { it in 0 until Int.MAX_VALUE } ?: return null
        val number = (get("chapter_number") as? JsonPrimitive)?.takeIf { it.isNumber }
            ?.asString?.toIntOrNull()
        return index.takeIf { number == index + 1 }
    }

    private fun JsonObject.booleanField(name: String): Boolean? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean

    private fun indexRanges(indexes: List<Int>): JsonArray {
        val ranges = JsonArray()
        var start = indexes.firstOrNull() ?: return ranges
        var end = start
        for (index in indexes.subList(1, indexes.size)) {
            if (index != end + 1) {
                ranges.add(indexRange(start, end))
                start = index
            }
            end = index
        }
        ranges.add(indexRange(start, end))
        return ranges
    }

    private fun indexRange(start: Int, end: Int): JsonArray = JsonArray(2).apply {
        add(start)
        add(end)
    }

    private fun JsonObject.replacing(name: String, value: JsonElement): JsonObject = JsonObject().also { copy ->
        entrySet().forEach { (key, field) -> copy.add(key, field) }
        copy.add(name, value)
    }
}
