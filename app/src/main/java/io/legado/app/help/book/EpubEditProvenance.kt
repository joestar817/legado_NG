package io.legado.app.help.book

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Provenance owned by the existing chapter cache. Edited text may require it for original-layout display;
 * it is not a disposable layout cache. The chapter text remains the sole editable content. */
internal object EpubEditProvenance {
    private const val LIMIT = 16 * 1024 * 1024
    private fun metadata(file: File) = AtomicFile(File(file.path + ".epub-positions.gz"))
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun encode(map: ContentPositionMap): JSONObject = JSONObject()
        .put("steps", JSONArray().apply {
            map.steps.forEach { step -> put(JSONObject().put("display", step.display).put("edits", JSONArray().apply {
                step.edits.forEach { edit -> put(JSONObject().put("start", edit.start).put("end", edit.end)
                    .put("text", edit.replacement).apply { edit.nested?.let { put("nested", encode(it)) } }) }
            })) }
        }).apply { map.removedTitle?.let { put("title", JSONArray(listOf(it.start, it.end))) } }

    private fun decode(source: String, value: JSONObject, depth: Int = 0): ContentPositionMap {
        require(depth < 32)
        val map = ContentPositionMap(source)
        val steps = value.getJSONArray("steps")
        for (index in 0 until steps.length()) {
            val step = steps.getJSONObject(index)
            val edits = step.getJSONArray("edits")
            val input = map.text
            val changes = (0 until edits.length()).map { position ->
                val edit = edits.getJSONObject(position)
                val start = edit.getInt("start")
                val end = edit.getInt("end")
                require(start in 0..end && end <= input.length)
                ContentEdit(start, end, edit.getString("text"), edit.optJSONObject("nested")?.let {
                    decode(input.substring(start, end), it, depth + 1)
                })
            }
            var cursor = 0
            val output = buildString {
                changes.forEach { edit ->
                    require(edit.start >= cursor)
                    append(input, cursor, edit.start).append(edit.replacement)
                    cursor = edit.end
                    require(length <= LIMIT)
                }
                append(input, cursor, input.length)
            }
            map.record(input, output, changes, step.getBoolean("display"))
        }
        value.optJSONArray("title")?.let {
            val start = it.getInt(0); val end = it.getInt(1)
            require(start in 0..end && end <= source.length)
            map.removedTitle = ContentPositionMap.Range(start, end)
        }
        return map
    }

    private fun read(file: File): JSONArray = runCatching {
        metadata(file).openRead().use { raw -> GZIPInputStream(raw).use { gzip ->
            val bytes = gzip.readBytesBounded()
            val value = JSONObject(bytes.toString(Charsets.UTF_8))
            require(value.getInt("version") == 1)
            value.getJSONArray("revisions")
        } }
    }.getOrElse { JSONArray() }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = read(buffer)
            if (size < 0) break
            require(output.size() + size <= LIMIT)
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }

    fun restore(file: File, source: String, cached: String, chapterTitle: String? = null): ContentPositionMap? {
        if (source == cached) return ContentPositionMap(source)
        // Older automatically generated EPUB caches could trim only the chapter's outer
        // whitespace. This exact match has a reversible position map and needs no edit record.
        val first = source.indexOfFirst { !it.isWhitespace() }
        if (first >= 0) {
            val end = source.indexOfLast { !it.isWhitespace() } + 1
            if (source.substring(first, end) == cached) {
                return ContentPositionMap(source).also { it.slice(source, first, end, display = false) }
            }
        }
        // A previously generated cache may have omitted the EPUB heading. Match the entire
        // remaining body and the chapter title exactly; never infer positions from similar text.
        if (!chapterTitle.isNullOrBlank() && cached.isNotEmpty()) {
            val start = source.indexOf(cached)
            if (start > 0 && source.lastIndexOf(cached) == start &&
                source.substring(0, start).trim() == chapterTitle.trim() &&
                source.substring(start + cached.length).isBlank()) {
                return ContentPositionMap(source).also {
                    it.slice(source, start, start + cached.length, display = false)
                }
            }
        }
        val sourceHash = hash(source); val resultHash = hash(cached)
        val revisions = read(file)
        for (index in 0 until revisions.length()) {
            val value = revisions.getJSONObject(index)
            if (value.optString("source") == sourceHash && value.optString("result") == resultHash) {
                return runCatching { decode(source, value.getJSONObject("map")).also { require(it.text == cached) } }.getOrNull()
            }
        }
        return null
    }

    fun save(file: File, content: String, map: ContentPositionMap) {
        require(map.text == content)
        val revisions = JSONArray()
        // Retain the old revision until the chapter write commits. Both interruption points are readable.
        val oldHash = if (file.exists()) hash(file.readText()) else null
        val previous = read(file)
        for (index in 0 until previous.length()) {
            val value = previous.getJSONObject(index)
            if (value.optString("result") == oldHash) { revisions.put(value); break }
        }
        revisions.put(JSONObject().put("source", hash(map.source)).put("result", hash(content)).put("map", encode(map)))
        val bytes = JSONObject().put("version", 1).put("revisions", revisions).toString().toByteArray()
        require(bytes.size <= LIMIT) { "EPUB 编辑位置记录过大" }
        val compressed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        write(metadata(file), compressed)
        write(AtomicFile(file), content.toByteArray())
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }

    fun delete(file: File) = metadata(file).delete()
}
