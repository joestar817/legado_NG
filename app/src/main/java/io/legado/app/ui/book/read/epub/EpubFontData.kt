package io.legado.app.ui.book.read.epub

import java.nio.ByteBuffer

/** A transient WebView resource copy. Never rewrites the user's font or native Typeface input. */
internal object EpubFontData {
    private data class Table(val tag: Int, val offset: Int, val length: Int)

    /**
     * Some static TrueType fonts have complete outlines/horizontal metrics but a truncated
     * optional vmtx table. Android accepts the font; WebView's OTS rejects the whole face.
     * Omit only that unusable vertical pair, leaving glyphs and shaping tables byte-identical.
     * Valid fonts and unsupported formats are passed unchanged to the normal font loader.
     */
    fun forWebView(bytes: ByteArray): ByteArray {
        if (bytes.size < 12 || bytes.size > 64 * 1024 * 1024) return bytes
        val input = ByteBuffer.wrap(bytes)
        if (input.getInt(0) != 0x00010000) return bytes
        fun ushort(at: Int) = input.getShort(at).toInt() and 0xffff
        fun uint(at: Int) = input.getInt(at).toLong() and 0xffffffffL
        val count = ushort(4)
        val directoryEnd = 12 + count * 16
        if (count !in 1..4095 || directoryEnd > bytes.size) return bytes
        val tables = ArrayList<Table>(count)
        val tags = HashSet<Int>()
        repeat(count) { index ->
            val at = 12 + index * 16
            val tag = input.getInt(at)
            val offset = uint(at + 8)
            val length = uint(at + 12)
            if (!tags.add(tag) || offset < directoryEnd || offset + length > bytes.size) return bytes
            if ((0..3).any { (bytes[at + it].toInt() and 0xff) !in 0x20..0x7e }) return bytes
            tables.add(Table(tag, offset.toInt(), length.toInt()))
        }
        val byOffset = tables.filter { it.length > 0 }.sortedBy { it.offset }
        if (byOffset.zipWithNext().any { (a, b) -> a.offset.toLong() + a.length > b.offset }) return bytes
        // Variation/vertical-origin data can depend on the vertical metrics. Do not alter it.
        if (tags.any { it == 0x66766172 || it == 0x56564152 || it == 0x564f5247 }) return bytes
        val maxp = tables.find { it.tag == 0x6d617870 && it.length >= 6 } ?: return bytes
        val head = tables.find { it.tag == 0x68656164 && it.length >= 54 } ?: return bytes
        val vhea = tables.find { it.tag == 0x76686561 && it.length >= 36 } ?: return bytes
        val vmtx = tables.find { it.tag == 0x766d7478 } ?: return bytes
        if (input.getInt(head.offset + 12) != 0x5f0f3cf5 || input.getInt(maxp.offset) != 0x00010000) return bytes
        val version = input.getInt(vhea.offset)
        if (version != 0x00010000 && version != 0x00011000) return bytes
        val glyphs = ushort(maxp.offset + 4)
        val metrics = ushort(vhea.offset + 34)
        if (metrics !in 1..glyphs) return bytes
        val required = metrics * 4 + (glyphs - metrics) * 2
        if (vmtx.length < metrics * 4 || vmtx.length >= required) return bytes

        // Any old digital signature no longer describes the resource copy.
        val kept = tables.filter { it !== vhea && it !== vmtx && it.tag != 0x44534947 }.sortedBy { it.tag }
        val size = 12 + kept.size * 16 + kept.sumOf { (it.length + 3) and -4 }
        val output = ByteArray(size)
        val buffer = ByteBuffer.wrap(output)
        val power = Integer.highestOneBit(kept.size)
        buffer.putInt(0, 0x00010000)
        buffer.putShort(4, kept.size.toShort())
        buffer.putShort(6, (power * 16).toShort())
        buffer.putShort(8, Integer.numberOfTrailingZeros(power).toShort())
        buffer.putShort(10, (kept.size * 16 - power * 16).toShort())
        var offset = 12 + kept.size * 16
        var headOffset = 0
        kept.forEachIndexed { index, table ->
            bytes.copyInto(output, offset, table.offset, table.offset + table.length)
            if (table === head) {
                headOffset = offset
                buffer.putInt(offset + 8, 0)
            }
            val at = 12 + index * 16
            buffer.putInt(at, table.tag)
            buffer.putInt(at + 4, checksum(output, offset, table.length))
            buffer.putInt(at + 8, offset)
            buffer.putInt(at + 12, table.length)
            offset += (table.length + 3) and -4
        }
        buffer.putInt(headOffset + 8, 0xb1b0afba.toInt() - checksum(output, 0, output.size))
        return output
    }

    private fun checksum(bytes: ByteArray, start: Int, length: Int): Int {
        var sum = 0
        var at = start
        val end = start + length
        while (at < end) {
            var word = 0
            repeat(4) { byte ->
                word = word shl 8
                if (at + byte < end) word = word or (bytes[at + byte].toInt() and 0xff)
            }
            sum += word
            at += 4
        }
        return sum
    }
}
