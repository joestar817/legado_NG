package io.legado.app.model.epub

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * A publication-scoped, offline resource owner. This is not a WebView security boundary: original
 * XHTML/SVG/scripts are returned unchanged, and must only reach a future isolated renderer after
 * its network, script, bridge and navigation policies have been implemented and verified.
 */
internal class EpubPublicationSession private constructor(
    private val archive: EpubArchive,
    val publication: EpubPublication,
    private val mediaDirectory: File? = null,
) : Closeable {
    @Volatile private var closed = false
    private val encryptionByPath = publication.encryption.associateBy { it.path }
    private val mediaFiles = HashMap<String, File>()
    private var mediaBytes = 0L
    private val mediaStreams = HashSet<java.io.FileInputStream>()

    private fun mediaInput(file: File): java.io.FileInputStream {
        val input = object : java.io.FileInputStream(file) {
            override fun close() {
                synchronized(this@EpubPublicationSession) { mediaStreams.remove(this) }
                super.close()
            }
        }
        mediaStreams.add(input)
        return input
    }

    /** Some legacy books omit illustrations from OPF. Admit only existing, passive raster assets. */
    fun resourceMediaType(path: String): String? = publication.resourcesByPath[path]?.mediaType
        ?: if (path in archive.entries) when (path.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "bmp" -> "image/bmp"
            else -> null
        } else null

    /** Only manifest resources can be opened, never filesystem paths or remote URLs. */
    @Synchronized
    fun openResource(path: String): EpubResource {
        if (closed) throw IOException("EPUB session is closed")
        EpubPaths.entryName(path)
        val mediaType = resourceMediaType(path)
            ?: throw EpubFormatException("Resource is not in the EPUB manifest: $path")
        val entry = archive.entries[path] ?: throw EpubFormatException("Missing EPUB resource: $path")
        val encryption = encryptionByPath[path]
        if (encryption == null && mediaDirectory != null && (mediaType.startsWith("audio/") || mediaType.startsWith("video/"))) {
            mediaFiles[path]?.let { return EpubResource(mediaType, entry.size, mediaInput(it)) }
            // One expansion per publication, shared by adjacent WebViews. Bound additional disk use.
            if (entry.size <= 512L * 1024 * 1024 - mediaBytes) {
                val file = File.createTempFile("media-", ".bin", mediaDirectory)
                try {
                    archive.open(path).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    if (file.length() != entry.size) throw EpubFormatException("Truncated EPUB media")
                    mediaFiles[path] = file; mediaBytes += entry.size
                    return EpubResource(mediaType, entry.size, mediaInput(file))
                } catch (error: Exception) {
                    file.delete()
                    throw error
                }
            }
        }
        // Resolve the key before opening a stream, so failures do not leak an archive handle.
        val key = encryption?.let {
            if (it.algorithm != IDPF_OBFUSCATION || mediaType !in FONT_MEDIA_TYPES) {
                throw EpubFormatException("Unsupported EPUB encryption: ${it.algorithm}")
            }
            val identifier = publication.uniqueIdentifier
                ?.filterNot { char -> char == ' ' || char == '\t' || char == '\r' || char == '\n' }
                ?.takeIf { value -> value.isNotEmpty() }
                ?: throw EpubFormatException("Missing EPUB identifier for obfuscated font")
            MessageDigest.getInstance("SHA-1").digest(identifier.toByteArray(Charsets.UTF_8))
        }
        val input = archive.open(path)
        val data = if (key == null) input else DeobfuscatingStream(input, key)
        val response = if (mediaType.startsWith("font/") || mediaType in PLAIN_FONT_MEDIA_TYPES) {
            FontInputStream(data)
        } else data
        return EpubResource(mediaType, entry.size, response)
    }

    /** Only buffered bytes are advertised; the session still invalidates every outstanding stream. */
    private inner class FontInputStream(input: InputStream) : InputStream() {
        private val buffered = BufferedInputStream(input, 64 * 1024)

        private fun checkSessionOpen() {
            if (closed) throw IOException("EPUB session is closed")
        }

        override fun read(): Int {
            checkSessionOpen()
            return buffered.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkSessionOpen()
            return buffered.read(buffer, offset, length)
        }

        override fun available(): Int {
            checkSessionOpen()
            return buffered.available()
        }

        override fun skip(count: Long): Long {
            checkSessionOpen()
            return buffered.skip(count)
        }

        override fun close() = buffered.close()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try { archive.close() } finally {
            mediaStreams.toList().forEach { runCatching { it.close() } }; mediaStreams.clear()
            mediaFiles.values.forEach { it.delete() }; mediaFiles.clear(); mediaBytes = 0
        }
    }

    companion object {
        private const val IDPF_OBFUSCATION = "http://www.idpf.org/2008/embedding"
        private val FONT_MEDIA_TYPES = setOf(
            "font/ttf", "font/otf", "font/woff", "font/woff2", "font/sfnt",
            "application/font-sfnt", "application/vnd.ms-opentype", "application/font-woff",
        )
        private val PLAIN_FONT_MEDIA_TYPES = setOf(
            "application/font-sfnt", "application/vnd.ms-opentype", "application/font-woff",
            "application/x-font-ttf", "application/x-font-otf", "application/x-font-woff",
        )

        fun open(file: File, limits: EpubArchiveLimits = EpubArchiveLimits()): EpubPublicationSession =
            open(FileEpubArchive.open(file, limits), limits, file.parentFile)

        /** Resource/layout metadata only. The existing reader remains the chapter-list owner. */
        @JvmOverloads
        fun openForLayout(file: File, limits: EpubArchiveLimits = EpubArchiveLimits(),
                          contentRevision: String? = null, onStage: ((String) -> Unit)? = null,
                          metadataDirectory: File? = file.parentFile): EpubPublicationSession =
            open(FileEpubArchive.open(file, limits), limits, file.parentFile, false, contentRevision, onStage,
                metadataDirectory?.let { File(it, "layout-metadata.bin") })

        /** Transfers ownership on entry, including parsing failures; useful for future SAF adapters. */
        fun open(
            archive: EpubArchive,
            limits: EpubArchiveLimits = EpubArchiveLimits(),
            mediaDirectory: File? = null,
        ): EpubPublicationSession = open(archive, limits, mediaDirectory, true)

        private fun open(archive: EpubArchive, limits: EpubArchiveLimits, mediaDirectory: File?,
                         includeNavigation: Boolean, contentRevision: String? = null,
                         onStage: ((String) -> Unit)? = null,
                         metadataFile: File? = mediaDirectory?.let { File(it, "layout-metadata.bin") }): EpubPublicationSession {
            try {
                onStage?.invoke("archive-index-ready")
                val parser = EpubPackageParser(limits)
                val publication = if (includeNavigation) parser.parse(archive)
                    else if (contentRevision != null) EpubLayoutMetadataCache.getOrParse(contentRevision, limits,
                        metadataFile) {
                        parser.parseLayout(archive)
                    } else parser.parseLayout(archive)
                onStage?.invoke("package-metadata-ready")
                return EpubPublicationSession(archive, publication, mediaDirectory)
            } catch (error: Exception) {
                try {
                    archive.close()
                } catch (closeError: Exception) {
                    error.addSuppressed(closeError)
                }
                throw error
            }
        }
    }

    /** EPUB 3.3 section 4.4: XOR only the first 1040 bytes with the identifier's SHA-1 digest. */
    private class DeobfuscatingStream(
        private val input: InputStream,
        private val key: ByteArray,
    ) : InputStream() {
        private var position = 0L

        override fun read(): Int {
            val value = input.read()
            if (value < 0) return value
            val decoded = if (position < 1040) {
                value xor (key[(position % key.size).toInt()].toInt() and 0xff)
            } else value
            position++
            return decoded
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = input.read(buffer, offset, length)
            if (read <= 0) return read
            val obfuscated = minOf(read.toLong(), (1040 - position).coerceAtLeast(0)).toInt()
            for (index in 0 until obfuscated) {
                buffer[offset + index] = (buffer[offset + index].toInt() xor
                    key[((position + index) % key.size).toInt()].toInt()).toByte()
            }
            position += read
            return read
        }

        // Keep InputStream's read-based skip so the key offset follows the original resource.
        override fun close() = input.close()
    }
}

/** No charset override: XML declarations, CSS @charset and BOMs remain in the original bytes. */
internal data class EpubResource(val mediaType: String, val size: Long, val data: InputStream) :
    Closeable {
    override fun close() = data.close()
}
