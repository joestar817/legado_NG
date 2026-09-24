package io.legado.app.ui.book.read.epub

import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.ContentPositionMap
import io.legado.app.model.epub.EpubContentProjection
import io.legado.app.model.epub.EpubContentLine
import io.legado.app.model.epub.EpubMappedContent
import io.legado.app.model.epub.EpubPaths
import io.legado.app.model.epub.EpubSourceDocument
import io.legado.app.model.epub.EpubSourceMedia
import io.legado.app.model.epub.EpubSourceNode
import io.legado.app.ui.book.read.page.entities.TextChapter
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestOutputStream
import java.security.MessageDigest

/** Disposable derived data, explicitly encoded. No native page geometry, styles or live owners. */
internal object EpubOpeningMappingFile {
    // Bump when source capture, content projection or native coordinate interpretation changes.
    private const val VERSION = 1
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ITEMS = 10_000

    data class Record(@SerializedName("request") val request: String, @SerializedName("source") val source: String,
                      @SerializedName("signature") val signature: String, @SerializedName("mapped") val mapped: EpubMappedContent)

    fun request(book: Book, chapter: BookChapter, revision: String): String = digest {
        writeInt(VERSION); string(book.bookUrl); string(revision); string(chapter.url); writeInt(chapter.index)
        string(chapter.getVariable("nextUrl")); nullableString(chapter.startFragmentId); nullableString(chapter.endFragmentId)
        writeBoolean(book.getDelTag(Book.hTag)); writeBoolean(book.getDelTag(Book.rubyTag))
    }

    /** Ordered semantic inputs, including nested edits and exact native coordinates; no guessed offsets. */
    fun signature(chapter: TextChapter, cached: ContentPositionMap): String? = runCatching {
        check(chapter.isCompleted)
        signature(cached, checkNotNull(chapter.contentPositionMap), chapter.sourceParagraphs,
            chapter.pages.flatMap { page -> page.lines.map { line ->
                EpubContentLine(line.chapterPosition, line.sourceParagraphIndex, line.text,
                    line.isTitle, line.isImage, line.isParagraphEnd)
            } })
    }.getOrNull()

    fun signature(cached: ContentPositionMap, prepared: ContentPositionMap,
                  paragraphs: List<String>, lines: List<EpubContentLine>): String? = runCatching {
        digest {
            fun map(value: ContentPositionMap, depth: Int) {
                require(depth < 64)
                string(value.source); string(value.text)
                writeBoolean(value.removedTitle != null)
                value.removedTitle?.let { writeInt(it.start); writeInt(it.end) }
                writeInt(value.steps.size)
                value.steps.forEach { step ->
                    writeInt(step.inputLength); writeInt(step.outputLength); writeBoolean(step.display); writeInt(step.edits.size)
                    step.edits.forEach { edit ->
                        writeInt(edit.start); writeInt(edit.end); string(edit.replacement); writeBoolean(edit.nested != null)
                        edit.nested?.let { map(it, depth + 1) }
                    }
                }
            }
            map(cached, 0); map(prepared, 0)
            writeInt(paragraphs.size); paragraphs.forEach { string(it) }
            writeInt(lines.size)
            lines.forEach { line ->
                writeInt(line.position); writeInt(line.paragraph); string(line.text)
                writeBoolean(line.title); writeBoolean(line.image); writeBoolean(line.paragraphEnd)
            }
        }
    }.getOrNull()

    fun read(file: File, request: String): Record? = try {
        val size = file.length()
        require(size in 40..MAX_BYTES.toLong())
        val bytes = ByteArray(size.toInt())
        DataInputStream(file.inputStream()).use { it.readFully(bytes); require(it.read() == -1) }
        val end = bytes.size - 32
        val hash = MessageDigest.getInstance("SHA-256").apply { update(bytes, 0, end) }.digest()
        require(MessageDigest.isEqual(hash, bytes.copyOfRange(end, bytes.size)))
        DataInputStream(ByteArrayInputStream(bytes, 0, end)).use { input ->
            require(input.readInt() == VERSION && input.string() == request)
            val source = input.string()
            val signature = input.string()
            val text = input.string()
            val documents = input.list(8) {
                val href = EpubPaths.entryName(string())
                val html = string()
                val occurrence = readInt()
                val sourceNodes = list { EpubSourceNode(string(), readInt(), string()) }
                val sourceMedia = list(1024) { EpubSourceMedia(string(), string()) }
                val excluded = list { string() }.toSet()
                val document = EpubSourceDocument(href, html, sourceNodes, sourceMedia, excluded, occurrence)
                val nodes = list {
                    val element = string(); val ordinal = readInt(); val original = string(); val value = string()
                    val starts = positions(value.length); val ends = positions(value.length)
                    for (index in starts.indices) require(starts[index] in -1..text.length &&
                        ends[index] in starts[index]..text.length && (starts[index] >= 0 || ends[index] == -1))
                    EpubContentProjection.ProjectedNode(element, ordinal, original, value, starts, ends, nullableString(), readBoolean())
                }
                val media = list(1024) {
                    val element = string(); val visible = readBoolean(); val start = readInt(); val stop = readInt()
                    require(start in -1..text.length && stop in start..text.length && (start >= 0 || stop == -1))
                    EpubContentProjection.ProjectedMedia(element, visible, start, stop)
                }
                EpubContentProjection.ProjectedDocument(document, nodes, media)
            }
            require(documents.isNotEmpty() && input.available() == 0)
            Record(request, source, signature, EpubMappedContent(text, documents))
        }
    } catch (_: Exception) { null }

    fun write(file: File, record: Record) {
        var partial: File? = null
        try {
            val buffer = BoundedOutput()
            DataOutputStream(buffer).use { output ->
                output.writeInt(VERSION); output.string(record.request); output.string(record.source); output.string(record.signature)
                output.string(record.mapped.text)
                output.list(record.mapped.documents, 8) { document ->
                    with(document.source) {
                        string(href); string(html); writeInt(spineOccurrence)
                        list(nodes) { string(it.element); writeInt(it.ordinal); string(it.text) }
                        list(media, 1024) { string(it.element); string(it.source) }
                        list(excludedElements.toList()) { string(it) }
                    }
                    list(document.nodes) {
                        string(it.element); writeInt(it.ordinal); string(it.original); string(it.text)
                        positions(it.starts); positions(it.ends); nullableString(it.beforeMedia); writeBoolean(it.preserveBreaks)
                    }
                    list(document.media, 1024) { string(it.element); writeBoolean(it.visible); writeInt(it.start); writeInt(it.end) }
                }
            }
            val bytes = buffer.toByteArray()
            partial = File.createTempFile("opening-mapping-", ".part", file.parentFile)
            partial.outputStream().use { it.write(bytes); it.write(MessageDigest.getInstance("SHA-256").digest(bytes)) }
            Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // A lost cache hit never prevents ordinary preparation and never changes book data.
        } finally { partial?.delete() }
    }

    private fun digest(block: DataOutputStream.() -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DataOutputStream(BufferedOutputStream(DigestOutputStream(object : OutputStream() { override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit }, digest), 64 * 1024)).use(block)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private class BoundedOutput : ByteArrayOutputStream() {
        override fun write(value: Int) { require(count < MAX_BYTES - 32); super.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(length <= MAX_BYTES - 32 - count); super.write(bytes, offset, length)
        }
    }
    private fun DataOutputStream.nullableString(value: String?) {
        if (value == null) writeInt(-1) else {
            // Preserve UTF-16 code units exactly, including editor-supplied unpaired surrogates.
            require(value.length <= MAX_BYTES / 2)
            val bytes = ByteArray(value.length * 2)
            value.forEachIndexed { index, char -> bytes[index * 2] = (char.code ushr 8).toByte(); bytes[index * 2 + 1] = char.code.toByte() }
            writeInt(value.length); write(bytes)
        }
    }
    private fun DataOutputStream.string(value: String) = nullableString(value)
    private fun DataInputStream.nullableString(): String? {
        val size = readInt(); if (size == -1) return null
        require(size in 0..available() / 2)
        val bytes = ByteArray(size * 2).also(::readFully)
        return String(CharArray(size) { ((bytes[it * 2].toInt() and 255) shl 8 or (bytes[it * 2 + 1].toInt() and 255)).toChar() })
    }
    private fun DataInputStream.string(): String = requireNotNull(nullableString())
    private fun DataOutputStream.positions(values: IntArray) { writeInt(values.size); values.forEach(::writeInt) }
    private fun DataInputStream.positions(expected: Int): IntArray {
        require(readInt() == expected && expected <= available() / 4)
        return IntArray(expected) { readInt() }
    }
    private fun <T> DataInputStream.list(max: Int = MAX_ITEMS, read: DataInputStream.() -> T): List<T> {
        val count = readInt(); require(count in 0..max && count <= available()); return List(count) { read(this) }
    }
    private fun <T> DataOutputStream.list(values: List<T>, max: Int = MAX_ITEMS, write: DataOutputStream.(T) -> Unit) {
        require(values.size <= max); writeInt(values.size); values.forEach { write(this, it) }
    }
}
