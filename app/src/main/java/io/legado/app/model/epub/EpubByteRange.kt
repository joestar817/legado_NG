package io.legado.app.model.epub

import java.io.IOException
import java.io.InputStream

/** Single byte ranges used by browser media playback; no multipart allocation. */
internal object EpubByteRange {
    fun parse(header: String, size: Long): LongRange? {
        if (size <= 0) return null
        val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header.trim()) ?: return null
        val (left, right) = match.destructured
        if (left.isEmpty()) {
            val suffix = right.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return (size - suffix.coerceAtMost(size))..(size - 1)
        }
        val first = left.toLongOrNull()?.takeIf { it < size } ?: return null
        val last = if (right.isEmpty()) size - 1 else right.toLongOrNull() ?: return null
        if (last < first) return null
        return first..last.coerceAtMost(size - 1)
    }
}

/** Streams only the requested span and closes the publication stream on cancellation. */
internal class EpubRangeInputStream(private val input: InputStream, start: Long, length: Long) : InputStream() {
    private var remaining = length
    init {
        require(start >= 0 && length >= 0)
        var skipped = 0L
        if (input is java.io.FileInputStream) {
            if (start > input.channel.size()) throw IOException("Truncated EPUB media range")
            input.channel.position(start)
            skipped = start
        }
        val buffer = ByteArray(64 * 1024)
        while (skipped < start) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), start - skipped).toInt())
            if (count <= 0) throw IOException("Truncated EPUB media range")
            skipped += count
        }
    }
    override fun read(): Int {
        if (remaining == 0L) return -1
        val value = input.read()
        if (value < 0) throw IOException("Truncated EPUB media range")
        remaining--
        return value
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        if (remaining == 0L) return -1
        val count = input.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) throw IOException("Truncated EPUB media range")
        remaining -= count
        return count
    }
    override fun close() = input.close()
}
