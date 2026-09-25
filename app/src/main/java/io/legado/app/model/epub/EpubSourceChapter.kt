package io.legado.app.model.epub

import io.legado.app.help.book.ContentEdit
import io.legado.app.help.book.ContentPositionMap
import io.legado.app.help.book.EpubContentEntities
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/** Transient source coordinates produced alongside the existing EPUB-to-content conversion. */
internal data class EpubSourceChapter(
    val documents: List<EpubSourceDocument>,
    val html: String,
    val flattening: ContentPositionMap,
    val tokens: List<EpubSourceToken>,
) {
    val content: String get() = flattening.text
}

internal data class EpubSourceDocument(
    /** Resource.href from the existing epublib parser: decoded, archive-root-relative. */
    val href: String,
    val html: String,
    val nodes: List<EpubSourceNode>,
    val media: List<EpubSourceMedia>,
    val excludedElements: Set<String>,
    val spineOccurrence: Int = -1,
) {
    val location: EpubResourceLink get() = EpubResourceLink(EpubPaths.entryName(href))
}

internal data class EpubSourceNode(val element: String, val ordinal: Int, val text: String)
internal data class EpubSourceMedia(val element: String, val source: String)

/** Each decoded character identifies an exact original node/offset; whitespace-only additions have no node. */
internal data class EpubSourceCharacter(val node: Int, val offset: Int)
internal data class EpubSourceToken(
    val start: Int,
    val end: Int,
    val document: Int,
    val media: String?,
    val decoded: String,
    val decoding: ContentPositionMap?,
    val characters: List<EpubSourceCharacter?>,
)

internal class EpubSourceCapture {
    companion object { const val ATTRIBUTE = "data-ng-epub-source" }

    private data class Draft(
        val href: String, val document: Document, val nodes: List<EpubSourceNode>,
        val media: List<EpubSourceMedia>, val occurrence: Int, val excluded: MutableSet<String> = linkedSetOf(),
    )
    private val drafts = ArrayList<Draft>()
    var sourceOccurrence: Int = -1
    var result: EpubSourceChapter? = null
        private set

    /** Annotate a private parsed document, never the ZIP or the stored chapter content. */
    fun register(href: String, document: Document): String? {
        val index = drafts.size
        val nodes = ArrayList<EpubSourceNode>()
        val media = ArrayList<EpubSourceMedia>()
        document.getAllElements().forEachIndexed { elementIndex, element ->
            if (element === document) return@forEachIndexed
            val id = "$index-$elementIndex"
            element.attr(ATTRIBUTE, id)
            if (element.tagName().lowercase() in setOf("img", "image")) {
                media.add(EpubSourceMedia(id, element.attr("src").ifEmpty { element.attr("xlink:href") }))
            }
        }
        forEachEpubBodyNode(document.body()) { node ->
            if (node is TextNode) {
                val parent = node.parent() as? Element ?: return@forEachEpubBodyNode
                val ordinal = parent.textNodes().indexOf(node)
                nodes.add(EpubSourceNode(parent.attr(ATTRIBUTE), ordinal, node.wholeText))
            }
        }
        drafts.add(Draft(href, document.clone(), nodes, media, sourceOccurrence))
        return media.firstOrNull()?.element
    }

    fun exclude(elements: Iterable<Element>) {
        elements.forEach { element ->
            val id = element.attr(ATTRIBUTE)
            val document = id.substringBefore('-').toIntOrNull()
            if (document != null) drafts.getOrNull(document)?.excluded?.add(id)
        }
    }

    fun finish(bodies: List<Element>, html: String, flattening: ContentPositionMap): EpubSourceChapter {
        val bodyHtml = bodies.map { it.outerHtml() }
        check(bodyHtml.joinToString("\n") == html)
        val tokens = ArrayList<EpubSourceToken>()
        val markerEdits = ArrayList<ContentEdit>()
        val retainedMarkers = flattening.text.contains(" $ATTRIBUTE=\"")
        var base = 0
        for (body in bodyHtml) {
            val parsed = Jsoup.parse(body, "", Parser.htmlParser().setTrackPosition(true))
            val cursors = HashMap<String, Int>()
            forEachEpubBodyNode(parsed.body()) { node ->
                val element = if (node is Element) node else node.parent() as? Element
                val id = element?.attr(ATTRIBUTE).orEmpty()
                val doc = id.substringBefore('-').toIntOrNull()
                // The legacy formatter retains some tags (for example ops:switch). Remove only
                // our attribute from a tracked opening tag, never matching attribute-like prose.
                if (retainedMarkers && node is Element && doc != null) {
                    val range = node.sourceRange()
                    if (range.isTracked) {
                        val marker = " $ATTRIBUTE=\"$id\""
                        val start = body.indexOf(marker, range.start().pos())
                        if (start >= range.start().pos() && start + marker.length <= range.end().pos()) {
                            val from = flattening.outputPosition(base + start, ContentPositionMap.Affinity.BEFORE)
                            val to = flattening.outputPosition(base + start + marker.length, ContentPositionMap.Affinity.AFTER)
                            if (flattening.text.substring(from, to) == marker) {
                                markerEdits.add(ContentEdit(from, to, ""))
                            }
                        }
                    }
                }
                if (node is TextNode && doc != null) {
                    val range = node.sourceRange()
                    if (range.isTracked) {
                        val start = range.start().pos()
                        val end = range.end().pos()
                        val lexical = body.substring(start, end)
                        val decoding = ContentPositionMap(lexical)
                        val decoded = EpubContentEntities.decode(lexical, decoding)
                        val original = drafts[doc].nodes.withIndex().filter { it.value.element == id }
                        val source = original.joinToString("") { it.value.text }
                        var cursor = cursors[id] ?: 0
                        val locations = ArrayList<EpubSourceCharacter?>()
                        for (char in decoded) {
                            if (char.isWhitespace()) {
                                if (cursor < source.length && source[cursor].isWhitespace()) {
                                    locations.add(characterAt(original, cursor))
                                    while (cursor < source.length && source[cursor].isWhitespace()) cursor++
                                } else locations.add(null) // Jsoup's pretty-print indentation.
                            } else {
                                while (cursor < source.length && source[cursor].isWhitespace()) cursor++
                                check(cursor < source.length && source[cursor] == char) {
                                    "EPUB element $id changed text during serialization"
                                }
                                locations.add(characterAt(original, cursor++))
                            }
                        }
                        cursors[id] = cursor
                        tokens.add(EpubSourceToken(base + start, base + end, doc, null, decoded, decoding, locations))
                    }
                } else if (node is Element && node.normalName() == "img" && doc != null) {
                    val range = node.sourceRange()
                    if (range.isTracked) tokens.add(EpubSourceToken(base + range.start().pos(), base + range.end().pos(),
                        doc, id, "", null, emptyList()))
                }
            }
            base += body.length + 1
        }
        if (markerEdits.isNotEmpty()) {
            val edits = markerEdits.sortedBy { it.start }
            val input = flattening.text
            val output = StringBuilder(input).also { text ->
                edits.asReversed().forEach { edit -> text.delete(edit.start, edit.end) }
            }.toString()
            flattening.record(input, output, edits, display = false)
        }
        val documents = drafts.map {
            it.document.outputSettings().prettyPrint(false)
            // Whitespace outside <body> is not source content. HTML's after-body parser mode
            // would otherwise append it to the last body text node during the browser load.
            it.document.textNodes().filter { node -> node.wholeText.isBlank() }.forEach { node -> node.remove() }
            it.document.selectFirst("html")?.textNodes()?.filter { node -> node.wholeText.isBlank() }
                ?.forEach { node -> node.remove() }
            EpubSourceDocument(it.href, it.document.outerHtml(), it.nodes, it.media, it.excluded.toSet(), it.occurrence)
        }
        return EpubSourceChapter(documents, html, flattening, tokens.sortedBy { it.start }).also { result = it }
    }

    private fun characterAt(nodes: List<IndexedValue<EpubSourceNode>>, offset: Int): EpubSourceCharacter {
        var remaining = offset
        for (entry in nodes) {
            if (remaining < entry.value.text.length) return EpubSourceCharacter(entry.index, remaining)
            remaining -= entry.value.text.length
        }
        error("EPUB source character is outside its owning element")
    }
}
