package io.legado.app.model.epub

import io.legado.app.help.book.ContentEdit
import io.legado.app.help.book.ContentPositionMap
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Applies already-executed content edits to original text nodes. It contains no replacement rules,
 * storage or playback policy. Stable character references survive insertions across inline tags.
 */
internal class EpubContentProjection(
    val source: EpubSourceChapter,
    val prepared: ContentPositionMap,
) {
    internal class Glyph(
        val document: Int,
        val node: Int,
        val media: String?,
        val char: Char,
        val generated: Boolean = false,
    ) {
        var previous: Glyph? = null
        var next: Glyph? = null
        var live = true
        var canonicalStart = Int.MAX_VALUE
        var canonicalEnd = -1
    }
    internal data class Mark(val start: Glyph?, val last: Glyph?, val boundary: Glyph? = null)
    internal data class ProjectedNode(
        val element: String, val ordinal: Int, val original: String,
        val text: String, val starts: IntArray, val ends: IntArray,
        val beforeMedia: String? = null,
        val preserveBreaks: Boolean = false,
    )
    internal data class ProjectedMedia(val element: String, val visible: Boolean, val start: Int, val end: Int)
    internal data class ProjectedDocument(val source: EpubSourceDocument, val nodes: List<ProjectedNode>, val media: List<ProjectedMedia>)

    private val head = Glyph(-1, -1, null, '\u0000')
    private val tail = Glyph(-1, -1, null, '\u0000')
    private val originalNodes = source.documents.map { document -> document.nodes.map { arrayOfNulls<Glyph>(it.text.length) } }
    private val mediaGlyphs = HashMap<Pair<Int, String>, Glyph>()
    private val extraNodes = ArrayList<Pair<Int, String>>()
    private val lexical = arrayOfNulls<Mark>(source.html.length)
    val rawMarks: List<Mark?>
    val bodyMarks: List<Mark?>

    init {
        head.next = tail
        tail.previous = head
        source.documents.forEachIndexed { documentIndex, document ->
            val parsed = Jsoup.parse(document.html)
            val indices = document.nodes.withIndex().associate { (index, node) -> (node.element to node.ordinal) to index }
            val mediaIds = document.media.mapTo(HashSet()) { it.element }
            fun visit(node: Node) {
                if (node is Element && node.normalName() in setOf("head", "script", "style")) return
                if (node is TextNode) {
                    val parent = node.parent() as? Element ?: return
                    val id = parent.attr(EpubSourceCapture.ATTRIBUTE)
                    val index = indices[id to parent.textNodes().indexOf(node)] ?: error("EPUB source text identity missing")
                    check(node.wholeText == document.nodes[index].text) { "EPUB source text changed while serializing" }
                    node.wholeText.forEachIndexed { offset, char ->
                        val glyph = Glyph(documentIndex, index, null, char)
                        insertBefore(tail, glyph)
                        originalNodes[documentIndex][index][offset] = glyph
                    }
                } else if (node is Element) {
                    val id = node.attr(EpubSourceCapture.ATTRIBUTE)
                    if (id in mediaIds) Glyph(documentIndex, -1, id, '\uFFFC').also {
                        insertBefore(tail, it)
                        mediaGlyphs[documentIndex to id] = it
                    }
                }
                node.childNodes().forEach(::visit)
            }
            visit(parsed.body())
        }
        source.tokens.forEach { token ->
            if (token.media != null) {
                val glyph = mediaGlyphs[token.document to token.media]
                if (glyph != null) for (index in token.start until token.end) lexical[index] = Mark(glyph, glyph)
            } else {
                val decoding = token.decoding ?: return@forEach
                for (index in token.start until token.end) {
                    val from = decoding.outputPosition(index - token.start, ContentPositionMap.Affinity.BEFORE)
                    val to = decoding.outputPosition(index - token.start + 1, ContentPositionMap.Affinity.AFTER)
                    val characters = token.characters.subList(from, to).filterNotNull()
                    val first = characters.firstOrNull()?.let { originalNodes[token.document][it.node][it.offset] }
                    val last = characters.lastOrNull()?.let { originalNodes[token.document][it.node][it.offset] }
                    if (first != null && last != null) lexical[index] = Mark(first, last)
                }
            }
        }
        rawMarks = apply(source.flattening, lexical.toList(), draw = false)
        require(source.content == prepared.source) { "EPUB 缓存正文与原书不同，缺少该次编辑的位置记录" }
        bodyMarks = apply(prepared, rawMarks, draw = true)
        check(bodyMarks.size == prepared.text.length)
    }

    private fun apply(map: ContentPositionMap, input: List<Mark?>, draw: Boolean): List<Mark?> {
        require(input.size == map.source.length)
        var current = input
        map.steps.forEach { step ->
            val result = ArrayList<Mark?>(step.outputLength)
            var cursor = 0
            step.edits.forEach { edit ->
                result.addAll(current.subList(cursor, edit.start))
                if (edit.nested != null) {
                    result.addAll(apply(edit.nested, current.subList(edit.start, edit.end), draw && step.display))
                } else {
                    val span = span(current, edit.start, edit.end)
                    if (draw && step.display) result.addAll(replace(span, edit))
                    else repeat(edit.replacement.length) { result.add(span) }
                }
                cursor = edit.end
            }
            result.addAll(current.subList(cursor, current.size))
            check(result.size == step.outputLength)
            current = result
        }
        return current
    }

    private fun span(marks: List<Mark?>, start: Int, end: Int): Mark {
        val selected = marks.subList(start, end)
        val first = selected.firstOrNull { it?.start != null }?.start
        val last = selected.lastOrNull { it?.last != null }?.last
        if (first != null && last != null) return Mark(first, last)
        val explicit = selected.firstOrNull { it?.boundary != null }?.boundary
        if (explicit != null) return Mark(null, null, explicit)
        // Tag-only removals need the nearest text anchor. Do not walk the entire chapter
        // prefix for every removal: long, heavily marked-up chapters otherwise become quadratic.
        for (index in end until marks.size) {
            marks[index]?.start?.let { return Mark(null, null, it) }
        }
        for (index in start - 1 downTo 0) {
            marks[index]?.last?.let { return Mark(null, null, it.next ?: tail) }
        }
        return Mark(null, null, tail)
    }

    private fun active(glyph: Glyph?): Glyph {
        var current = glyph ?: tail
        while (!current.live) current = current.next ?: tail
        return current
    }

    private fun replace(mark: Mark, edit: ContentEdit): List<Mark?> {
        val start = active(mark.start ?: mark.boundary)
        val end = active(mark.last?.next ?: mark.boundary)
        var owner = start.takeUnless { it === tail } ?: end.previous ?: head
        var candidate = start
        while (candidate !== end && candidate !== tail) {
            if (candidate.node >= 0 && !candidate.char.isWhitespace()) { owner = candidate; break }
            candidate = candidate.next ?: tail
        }
        if (owner === head || owner.document < 0) owner = head.next?.takeUnless { it === tail } ?: tail
        require(owner.document >= 0 || edit.replacement.isEmpty()) { "EPUB has no content anchor for generated text" }
        var cursor = start
        while (cursor !== end && cursor !== tail) {
            val next = cursor.next ?: tail
            remove(cursor)
            cursor = next
        }
        require(cursor === end) { "EPUB content edit reversed source order" }
        if (edit.replacement.isEmpty()) return emptyList()
        val document = owner.document
        val node = if (owner.node >= 0) owner.node else {
            extraNodes.add(document to requireNotNull(owner.media))
            -extraNodes.size - 1
        }
        val inserted = edit.replacement.map { char -> Glyph(document, node, null, char, generated = true).also { insertBefore(end, it) } }
        return inserted.map { Mark(it, it) }
    }

    private fun insertBefore(anchor: Glyph, glyph: Glyph) {
        val previous = anchor.previous ?: head
        glyph.previous = previous
        glyph.next = anchor
        previous.next = glyph
        anchor.previous = glyph
    }

    private fun remove(glyph: Glyph) {
        glyph.previous?.next = glyph.next
        glyph.next?.previous = glyph.previous
        glyph.live = false
    }

    /** Bind the existing native content coordinate, after its image/title normalization. */
    fun bind(position: Int, canonicalPosition: Int) {
        val mark = bodyMarks.getOrNull(position) ?: return
        var glyph = active(mark.start ?: mark.boundary)
        val end = active(mark.last?.next ?: mark.boundary)
        while (glyph !== end && glyph !== tail) {
            glyph.canonicalStart = minOf(glyph.canonicalStart, canonicalPosition)
            glyph.canonicalEnd = maxOf(glyph.canonicalEnd, canonicalPosition + 1)
            glyph = glyph.next ?: tail
        }
    }

    fun bindTitle(text: String, positions: IntArray) {
        require(text.length == positions.size)
        val removed = prepared.removedTitle ?: return
        if (text.isEmpty()) return
        val mark = span(rawMarks, removed.start, removed.end)
        val inserted = replace(mark, ContentEdit(removed.start, removed.end, text))
        inserted.forEachIndexed { index, value ->
            value?.start?.let { it.canonicalStart = positions[index]; it.canonicalEnd = positions[index] + 1 }
        }
    }

    fun documents(): List<ProjectedDocument> {
        val byNode = HashMap<Pair<Int, Int>, MutableList<Glyph>>()
        var glyph = head.next ?: tail
        while (glyph !== tail) {
            if (glyph.media == null) byNode.getOrPut(glyph.document to glyph.node) { ArrayList() }.add(glyph)
            glyph = glyph.next ?: tail
        }
        return source.documents.mapIndexed { documentIndex, document ->
            val nodes = document.nodes.mapIndexed { index, node ->
                projectedNode(node.element, node.ordinal, node.text, byNode[documentIndex to index].orEmpty())
            }.toMutableList()
            extraNodes.forEachIndexed { index, (owner, media) ->
                if (owner == documentIndex) nodes.add(projectedNode("", 0, "", byNode[owner to -index - 2].orEmpty(), media))
            }
            val media = document.media.map { item ->
                val value = mediaGlyphs[documentIndex to item.element]
                ProjectedMedia(item.element, value?.live == true, value?.canonicalStart?.takeUnless { it == Int.MAX_VALUE } ?: -1,
                    value?.canonicalEnd ?: -1)
            }
            ProjectedDocument(document, nodes, media)
        }
    }

    private fun projectedNode(element: String, ordinal: Int, original: String, glyphs: List<Glyph>, beforeMedia: String? = null) =
        ProjectedNode(element, ordinal, original, glyphs.joinToString("") { it.char.toString() },
            glyphs.map { if (it.canonicalStart == Int.MAX_VALUE) -1 else it.canonicalStart }.toIntArray(),
            glyphs.map { it.canonicalEnd }.toIntArray(), beforeMedia,
            glyphs.any { it.generated && (it.char == '\n' || it.char == '\r') })
}
