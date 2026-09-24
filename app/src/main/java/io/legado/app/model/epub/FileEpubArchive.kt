package io.legado.app.model.epub

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Owns a read-only ZIP, without extracting files or using the shared legacy EPUB cache.
 * The caller must supply an immutable file for the whole session lifetime. SAF snapshot ownership
 * belongs to the future source adapter, not to this archive or BookHelp's filename-based cache.
 */
internal class FileEpubArchive private constructor(
    private val zip: ZipFile,
    override val entries: Map<String, EpubArchiveEntry>,
    private val zipEntries: Map<String, ZipEntry>,
) : EpubArchive {
    private val lock = Any()
    private val streams = mutableSetOf<EntryStream>()

    @Volatile
    private var closed = false

    override fun open(path: String): InputStream = synchronized(lock) {
        checkOpen()
        if (EpubPaths.entryName(path) != path) throw EpubFormatException("Invalid EPUB path")
        val entry = zipEntries[path] ?: throw EpubFormatException("Missing EPUB resource: $path")
        EntryStream(zip.getInputStream(entry), entry).also { streams.add(it) }
    }

    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            streams.toList().also { streams.clear() }
        }
        var failure: IOException? = null
        fun record(error: IOException) {
            val previous = failure
            if (previous == null) failure = error else previous.addSuppressed(error)
        }
        pending.forEach { stream ->
            try {
                stream.close()
            } catch (error: IOException) {
                record(error)
            }
        }
        try {
            zip.close()
        } catch (error: IOException) {
            record(error)
        }
        failure?.let { throw it }
    }

    private fun checkOpen() {
        if (closed) throw IOException("EPUB archive is closed")
    }

    /** Enforces declared size even when the central directory lies; checks CRC at full consumption. */
    private inner class EntryStream(
        private val input: InputStream,
        private val entry: ZipEntry,
    ) : InputStream() {
        private val crc = CRC32()
        private var count = 0L
        private var streamClosed = false
        private var ended = false
        private var failure: IOException? = null

        @Synchronized
        override fun read(): Int {
            val buffer = ByteArray(1)
            return if (read(buffer, 0, 1) == -1) -1 else buffer[0].toInt() and 0xff
        }

        @Synchronized
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkOpen()
            if (streamClosed) throw IOException("EPUB resource stream is closed")
            failure?.let { throw it }
            if (offset < 0 || length < 0 || offset > buffer.size - length) {
                throw IndexOutOfBoundsException()
            }
            if (length == 0) return 0
            if (ended) return -1
            try {
                // Read at most one byte beyond the declared size, never inflate an unbounded chunk.
                val allowed = minOf(length.toLong(), entry.size - count + 1).toInt()
                val read = input.read(buffer, offset, allowed)
                if (read == -1) {
                    if (count != entry.size || crc.value != entry.crc) {
                        throw EpubFormatException("Corrupt EPUB resource: ${entry.name}")
                    }
                    ended = true
                } else if (read > 0) {
                    count += read
                    if (count > entry.size) {
                        throw EpubFormatException("EPUB resource exceeds declared size: ${entry.name}")
                    }
                    crc.update(buffer, offset, read)
                }
                return read
            } catch (error: IOException) {
                failure = error
                throw error
            }
        }

        // InputStream.skip reads through this wrapper, so skipped data is also size/CRC checked.
        @Synchronized
        override fun close() {
            if (streamClosed) return
            streamClosed = true
            try {
                input.close()
            } finally {
                synchronized(lock) { streams.remove(this) }
            }
        }
    }

    companion object {
        fun open(file: File, limits: EpubArchiveLimits = EpubArchiveLimits()): FileEpubArchive {
            // Bound input before the platform parses the ZIP directory. The caller must prevent
            // mutation/replacement during this session; this is not a hardened ZIP parser sandbox.
            if (!file.isFile || file.length() > limits.maxArchiveBytes) {
                throw EpubFormatException("EPUB input is not a file or exceeds archive size limit")
            }
            val zip = ZipFile(file)
            try {
                val indexed = linkedMapOf<String, ZipEntry>()
                val descriptions = linkedMapOf<String, EpubArchiveEntry>()
                val names = hashSetOf<String>()
                val enumeration = zip.entries()
                var total = 0L
                var count = 0
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (++count > limits.maxEntries) throw EpubFormatException("Too many EPUB entries")
                    if (!names.add(entry.name)) {
                        throw EpubFormatException("Duplicate EPUB entry: ${entry.name}")
                    }
                    val path = EpubPaths.entryName(
                        if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
                    )
                    if (entry.size < 0 || entry.compressedSize < 0 || entry.crc < 0) {
                        throw EpubFormatException("Unknown EPUB resource size or CRC: $path")
                    }
                    if (entry.size > limits.maxEntryBytes || entry.size > limits.maxTotalBytes - total) {
                        throw EpubFormatException("EPUB archive exceeds resource size limit")
                    }
                    total += entry.size
                    if (entry.isDirectory) continue
                    if (entry.method != ZipEntry.STORED && entry.method != ZipEntry.DEFLATED) {
                        throw EpubFormatException("Unsupported EPUB compression: $path")
                    }
                    indexed[path] = entry
                    descriptions[path] = EpubArchiveEntry(path, entry.size, entry.compressedSize)
                }
                return FileEpubArchive(
                    zip,
                    Collections.unmodifiableMap(descriptions),
                    Collections.unmodifiableMap(indexed),
                )
            } catch (error: Exception) {
                try {
                    zip.close()
                } catch (closeError: IOException) {
                    error.addSuppressed(closeError)
                }
                if (error is IllegalArgumentException) {
                    throw EpubFormatException("Invalid EPUB archive path", error)
                }
                throw error
            }
        }
    }
}
