package io.legado.app.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

internal enum class ListeningHistorySource(val storageValue: String) {
    READ_ALOUD("readAloud"),
    AUDIO("audio")
}

/** A resume bookmark; position is the source's existing character or millisecond coordinate. */
internal data class ListeningHistoryEntry(
    @SerializedName("source") val source: ListeningHistorySource,
    @SerializedName("bookUrl") val bookUrl: String,
    @SerializedName("chapterIndex") val chapterIndex: Int,
    @SerializedName("position") val position: Int
)

internal object ListeningHistoryCodec {
    fun encode(entry: ListeningHistoryEntry): String {
        require(entry.bookUrl.isNotBlank() && entry.chapterIndex >= 0 && entry.position >= 0)
        return JsonObject().apply {
            addProperty("version", 1)
            addProperty("source", entry.source.storageValue)
            addProperty("bookUrl", entry.bookUrl)
            addProperty("chapterIndex", entry.chapterIndex)
            addProperty("position", entry.position)
        }.toString()
    }

    fun decode(json: String): ListeningHistoryEntry {
        val element = JsonParser.parseString(json)
        require(element.isJsonObject) { "Invalid listening history object" }
        val root = element.asJsonObject
        require(readInt(root, "version") == 1) { "Unsupported listening history version" }
        val sourceValue = readString(root, "source")
        val source = ListeningHistorySource.entries.firstOrNull { it.storageValue == sourceValue }
        requireNotNull(source) { "Unknown listening history source" }
        val bookUrl = readString(root, "bookUrl")
        require(bookUrl.isNotBlank()) { "Empty listening history book URL" }
        return ListeningHistoryEntry(
            source, bookUrl, readInt(root, "chapterIndex"), readInt(root, "position")
        )
    }

    private fun readString(root: JsonObject, key: String): String {
        val value = root.get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Invalid listening history $key"
        }
        return value.asString
    }

    private fun readInt(root: JsonObject, key: String): Int {
        val value = root.get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "Invalid listening history $key"
        }
        val number = value.asString
        require(number.matches(Regex("0|[1-9][0-9]*"))) { "Invalid listening history $key" }
        return requireNotNull(number.toIntOrNull()) { "Listening history $key is out of range" }
    }
}

/** Used only under the store lock; no timer or playback state is owned here. */
internal class ListeningHistoryWritePolicy(initial: ListeningHistoryEntry? = null) {
    private var saved = initial
    private var savedAt = 0L

    fun shouldWrite(entry: ListeningHistoryEntry, now: Long, flush: Boolean = false): Boolean {
        if (entry == saved) return false
        val previous = saved
        return flush || previous == null || previous.source != entry.source ||
            previous.bookUrl != entry.bookUrl || previous.chapterIndex != entry.chapterIndex ||
            now - savedAt >= 1_000L
    }

    fun didWrite(entry: ListeningHistoryEntry, now: Long) {
        saved = entry
        savedAt = now
    }
}
