package io.legado.app.model.epub

import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/** The session owns this archive. Streams must not escape the session lifetime. */
internal interface EpubArchive : Closeable {
    val entries: Map<String, EpubArchiveEntry>
    fun open(path: String): InputStream
}

internal data class EpubArchiveEntry(val path: String, val size: Long, val compressedSize: Long)

/** Explicit admission limits, not automatic truncation or a text-rendering fallback. */
internal data class EpubArchiveLimits(
    val maxArchiveBytes: Long = 512L * 1024 * 1024,
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Long = 256L * 1024 * 1024,
    val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxXmlBytes: Int = 4 * 1024 * 1024,
) {
    init {
        require(maxArchiveBytes > 0 && maxEntries > 0 && maxEntryBytes > 0 &&
            maxTotalBytes > 0 && maxXmlBytes > 0)
    }
}

internal class EpubFormatException(message: String, cause: Throwable? = null) :
    IOException(message, cause)
