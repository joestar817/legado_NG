package io.legado.app.ui.book.read.epub

import io.legado.app.model.epub.EpubContentProjection
import io.legado.app.ui.book.read.page.provider.ReadTitleStyleParser
import io.legado.app.ui.book.read.page.entities.TextLine
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/** Transient source coordinates for authored headings. The existing parser owns segmentation. */
internal class EpubTitleProjection private constructor(
    private val text: String,
    private val starts: IntArray,
    private val ends: IntArray,
    private val nativeParts: List<NativePart>,
) {
    private data class NativePart(val from: Int, val to: Int, val main: Boolean)
    private var cachedOptions: List<Any>? = null
    private var cachedSegments = JSONArray()

    fun segments(type: Int, distance: Int, flags: String, scale: Float): JSONArray {
        val options = listOf(type, distance, flags, scale)
        if (cachedOptions == options) return cachedSegments
        if (nativeParts.isNotEmpty()) {
            cachedSegments = JSONArray().apply { nativeParts.forEachIndexed { index, part ->
                put(JSONObject().put("from", part.from).put("to", part.to).put("main", part.main)
                    .put("scale", if (part.main) 1f else scale).put("breakBefore", index > 0))
            } }
            cachedOptions = options
            return cachedSegments
        }
        var cursor = 0
        val result = JSONArray()
        ReadTitleStyleParser.parse(text, type, distance, flags, scale).forEachIndexed { index, segment ->
            val at = text.indexOf(segment.text, cursor)
            if (at < 0) return@forEachIndexed
            cursor = at + segment.text.length
            var from = -1
            var to = -1
            var first = true
            fun flush() {
                if (from >= 0 && to > from) {
                    result.put(JSONObject().put("from", from).put("to", to).put("scale", segment.scale).put("main", segment.main)
                        .put("breakBefore", index > 0 && first && result.length() > 0))
                    first = false
                }
            }
            for (i in at until cursor) {
                val a = starts[i]
                val b = ends[i]
                if (a < 0 || b <= a) continue
                if (from < 0) { from = a; to = b }
                else if (a <= to) to = maxOf(to, b)
                else { flush(); from = a; to = b }
            }
            flush()
        }
        cachedOptions = options
        cachedSegments = result
        return result
    }

    companion object {
        fun from(document: EpubContentProjection.ProjectedDocument, nativeLines: List<TextLine>): List<EpubTitleProjection> {
            val html = Jsoup.parse(document.source.html)
            val native = ArrayList<NativePart>()
            var start = -1
            var end = -1
            var main = true
            nativeLines.forEach { line ->
                if (start < 0) { start = line.chapterPosition; main = line.titleTextSize == null }
                end = line.chapterPosition + line.charSize
                if (line.isParagraphEnd) { native.add(NativePart(start, end, main)); start = -1 }
            }
            if (start >= 0) native.add(NativePart(start, end, main))
            return html.select("h1,h2,h3,h4,h5,h6,[role=heading]").mapNotNull { heading ->
                if (heading.parents().any { it.normalName() in setOf("h1", "h2", "h3", "h4", "h5", "h6") || it.attr("role") == "heading" }) return@mapNotNull null
                val elements = heading.allElements.mapTo(HashSet()) { it.attr("data-ng-epub-source") }
                val nodes = document.nodes.filter { it.element in elements }
                val text = nodes.joinToString("") { it.text }
                val starts = nodes.flatMap { it.starts.asIterable() }.toIntArray()
                val ends = nodes.flatMap { it.ends.asIterable() }.toIntArray()
                val prepared = native.mapNotNull { part ->
                    val indexes = starts.indices.filter { starts[it] >= part.from && ends[it] <= part.to && ends[it] > starts[it] }
                    if (indexes.isEmpty()) null else NativePart(indexes.minOf { starts[it] }, indexes.maxOf { ends[it] }, part.main)
                }
                if (text.isBlank()) null else EpubTitleProjection(text,
                    starts, ends, prepared)
            }
        }
    }
}
