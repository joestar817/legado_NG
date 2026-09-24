package io.legado.app.ui.book.read.epub

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.model.epub.EpubContentLine
import io.legado.app.model.epub.EpubContentProjection
import io.legado.app.model.epub.EpubMappedContent
import io.legado.app.model.epub.EpubPublicationSession
import io.legado.app.model.epub.EpubSingleDocumentSource
import io.legado.app.model.epub.EpubSourceChapter
import io.legado.app.model.epub.EpubResourceLink
import io.legado.app.model.epub.EpubPageSize
import io.legado.app.model.epub.bindNativeContent
import io.legado.app.model.localBook.EpubFile
import io.legado.app.ui.book.read.page.entities.TextChapter
import org.json.JSONObject
import java.util.UUID
import java.io.File
import org.jsoup.Jsoup
import splitties.init.appCtx
import io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher
import io.legado.app.help.config.ReadBookConfig

/** One existing prepared chapter, projected onto its original documents without running rules again. */
internal class EpubPreparedContent private constructor(
    val chapter: TextChapter,
    val mapped: EpubMappedContent,
    val documents: List<Document>,
) {
    data class Document(val location: EpubResourceLink, val html: String, val payload: JSONObject,
                        val first: Int, val last: Int, val endFragment: String?, val spineIndex: Int,
                        val chapter: TextChapter, val text: String, val svgSize: EpubPageSize?,
                        val styleFonts: Map<String, ByteArray> = emptyMap(),
                        val nineSlices: Map<String, io.legado.app.model.epub.EpubNineSliceImage> = emptyMap(),
                        val backgrounds: Map<String, io.legado.app.model.epub.EpubBackgroundImage> = emptyMap(),
                        val titles: List<EpubTitleProjection> = emptyList())

    fun documentAt(position: Int): Int = documents.indexOfLast { it.first <= position }.coerceAtLeast(0)

    fun withHighlightStyles(matcher: ReadHighlightMatcher): EpubPreparedContent {
        val styles = EpubCharStyles { path -> runCatching { readEpubReaderFont(appCtx, path) }.getOrNull() }
        styles.rematch(chapter.highlightInputs, matcher)
        return EpubPreparedContent(chapter, mapped, documents.map { document ->
            document.copy(payload = JSONObject(document.payload.toString())
                .put("charStyles", styles.styles).put("styleRanges", styles.ranges),
                styleFonts = styles.fonts, nineSlices = styles.nineSlices, backgrounds = styles.backgrounds)
        })
    }

    companion object {
        fun create(book: Book, chapter: TextChapter, session: EpubPublicationSession,
                   openingSource: EpubSourceChapter? = null, sourceRevision: String? = null,
                   openingRecord: EpubOpeningMappingFile.Record? = null, mappingFile: File? = null): EpubPreparedContent {
            val startup = EpubStartupTiming("projection")
            check(chapter.isCompleted) { "EPUB 正文尚未准备完成" }
            val publication = session.publication
            val prepared = checkNotNull(chapter.contentPositionMap) { "EPUB 正文缺少位置记录" }
            val memory = EpubOpeningMappingCache.find(book, chapter, sourceRevision) { source ->
                BookHelp.epubContentPositions(book, chapter.chapter, source, prepared.source)
            }
            val stored = openingRecord?.takeIf { memory == null && sourceRevision != null &&
                it.request == EpubOpeningMappingFile.request(book, chapter.chapter, sourceRevision) }
            val storedPositions = stored?.let { BookHelp.epubContentPositions(book, chapter.chapter, it.source, prepared.source) }
            val disk = stored?.takeIf { storedPositions != null &&
                EpubOpeningMappingFile.signature(chapter, storedPositions) == it.signature }?.mapped
            if (disk != null) EpubOpeningMappingCache.put(book, chapter, sourceRevision, checkNotNull(storedPositions), disk)
            val reused = memory ?: disk
            val mapped = reused ?: run {
                val path = chapter.chapter.url.substringBeforeLast('#')
                val direct = openingSource ?: runCatching {
                    EpubSingleDocumentSource.read(publication, path,
                        chapter.chapter.getVariable("nextUrl").substringBeforeLast('#'),
                        chapter.chapter.startFragmentId, chapter.chapter.endFragmentId,
                        book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag)) {
                        session.openResource(path).use { it.data.readBytes().toString(java.nio.charset.Charset.defaultCharset()) }
                    }
                }.getOrNull()
                val directPositions = direct?.let {
                    BookHelp.epubContentPositions(book, chapter.chapter, it.content, prepared.source)
                }
                val source = if (directPositions != null) checkNotNull(direct)
                    else EpubFile.getSourceChapter(book, chapter.chapter) ?: error("EPUB 原文不存在")
                startup.mark(if (directPositions != null) "snapshot-source-ready" else "source-ready")
                val cached = directPositions ?: BookHelp.epubContentPositions(book, chapter.chapter, source.content, prepared.source)
                    ?: error("本章缺少可验证的正文位置记录，原编辑内容已保留，请在正文编辑器中检查")
                val projection = EpubContentProjection(source, cached.followedBy(prepared))
                val lines = chapter.pages.flatMap { page -> page.lines.map { line ->
                    EpubContentLine(line.chapterPosition, line.sourceParagraphIndex, line.text,
                        line.isTitle, line.isImage, line.isParagraphEnd)
                } }
                projection.bindNativeContent(chapter.sourceParagraphs, lines).also {
                    EpubOpeningMappingCache.put(book, chapter, sourceRevision, cached, it)
                    if (mappingFile != null && sourceRevision != null) EpubOpeningMappingFile.signature(chapter, cached)?.let { signature ->
                        EpubOpeningMappingFile.write(mappingFile, EpubOpeningMappingFile.Record(
                            EpubOpeningMappingFile.request(book, chapter.chapter, sourceRevision), source.content, signature, it))
                    }
                }
            }
            startup.mark(if (disk != null) "mapping-disk-reused" else if (memory != null) "mapping-reused" else "mapping-ready")
            val charStyles = EpubCharStyles { path ->
                runCatching { readEpubReaderFont(appCtx, path) }.getOrNull()
            }.apply { rematch(chapter.highlightInputs, ReadHighlightMatcher(ReadBookConfig.highlightRules)) }
            startup.mark("styles-ready")
            val key = UUID.randomUUID().toString()
            var last = 0
            var nextOccurrence = 0
            val titleLines = chapter.pages.flatMap { it.lines }.filter { it.isTitle }
            val documents = mapped.documents.mapIndexed { index, document ->
                val (first, end) = document.contentBounds(last)
                last = end
                val location = document.source.location.copy(
                    fragment = if (index == 0) chapter.chapter.startFragmentId else null)
                val spineIndex = document.source.spineOccurrence.takeIf { it >= 0 } ?: (nextOccurrence until publication.spine.size).firstOrNull {
                    publication.resourcesById[publication.spine[it].idref]?.location?.path == location.path
                } ?: -1
                if (spineIndex >= 0) nextOccurrence = spineIndex + 1
                val svgSize = if (publication.resourcesByPath[location.path]?.mediaType == "image/svg+xml") {
                    val svg = Jsoup.parse(document.source.html).selectFirst("svg") ?: error("SVG 正文不存在")
                    val box = svg.attr("viewBox").split(Regex("[ ,\\s]+")).mapNotNull(String::toFloatOrNull)
                    val width = if (box.size == 4) box[2] else svg.attr("width").removeSuffix("px").toFloatOrNull()
                    val height = if (box.size == 4) box[3] else svg.attr("height").removeSuffix("px").toFloatOrNull()
                    EpubPageSize(requireNotNull(width) { "固定 SVG 缺少宽度" }, requireNotNull(height) { "固定 SVG 缺少高度" })
                } else null
                Document(location, document.source.html, document.toReaderJson("$key-$index").put("text", mapped.text)
                    .put("charStyles", charStyles.styles).put("styleRanges", charStyles.ranges),
                    first, end, if (index == mapped.documents.lastIndex) chapter.chapter.endFragmentId else null,
                    spineIndex, chapter, mapped.text, svgSize, charStyles.fonts, charStyles.nineSlices, charStyles.backgrounds,
                    EpubTitleProjection.from(document, titleLines))
            }
            check(documents.isNotEmpty()) { "EPUB 章节没有正文文档" }
            startup.mark("payload-ready")
            return EpubPreparedContent(chapter, mapped, documents)
        }
    }
}

internal fun EpubContentProjection.ProjectedDocument.contentBounds(fallback: Int): Pair<Int, Int> {
    var first = -1
    var end = -1
    for (node in nodes) {
        for (value in node.starts) if (value >= 0 && (first < 0 || value < first)) first = value
        for (value in node.ends) if (value >= 0 && value > end) end = value
    }
    for (item in media) {
        if (item.start >= 0 && (first < 0 || item.start < first)) first = item.start
        if (item.end >= 0 && item.end > end) end = item.end
    }
    if (first < 0) first = fallback
    if (end < 0) end = first
    return first to end
}
