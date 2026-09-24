package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.nio.ByteBuffer

class EpubFontDataTest {
    @Test
    fun removesOnlyTruncatedOptionalMetricsWithoutChangingGlyphData() {
        val source = font(6)
        val original = source.copyOf()
        val result = EpubFontData.forWebView(source)
        assertNotSame(source, result)
        assertArrayEquals(original, source)
        val tables = tables(result)
        assertEquals(setOf("glyf", "head", "maxp"), tables.keys)
        assertArrayEquals(tables(source).getValue("glyf"), tables.getValue("glyf"))
        assertEquals(0xb1b0afba.toInt(), ByteBuffer.wrap(result).let { data ->
            var sum = 0
            while (data.remaining() >= 4) sum += data.int
            sum
        })
        assertSame(result, EpubFontData.forWebView(result))
    }

    @Test
    fun completeVerticalMetricsAndUnsupportedFormatsStayUnchanged() {
        for (size in listOf(10, 12)) {
            val valid = font(size)
            assertSame(valid, EpubFontData.forWebView(valid))
        }
        for (format in listOf(0x74746366, 0x774f4632, 0x4f54544f)) {
            val data = font(6).also { ByteBuffer.wrap(it).putInt(0, format) }
            assertSame(data, EpubFontData.forWebView(data))
        }
    }

    @Test
    fun invalidDirectoriesAreNeverRebuilt() {
        val raw = font(6)
        for (size in 0 until 92) {
            val truncated = raw.copyOf(size)
            assertSame(truncated, EpubFontData.forWebView(truncated))
        }
        for (field in listOf(20, 24)) {
            val overflow = raw.copyOf().also { ByteBuffer.wrap(it).putInt(field, -1) }
            assertSame(overflow, EpubFontData.forWebView(overflow))
        }
    }

    private fun font(verticalBytes: Int): ByteArray {
        val records = linkedMapOf(
            "glyf" to byteArrayOf(1, 2, 3, 4, 5),
            "head" to ByteArray(54).also { ByteBuffer.wrap(it).putInt(12, 0x5f0f3cf5) },
            "maxp" to ByteArray(6).also { ByteBuffer.wrap(it).putInt(0, 0x00010000).putShort(4, 4) },
            "vhea" to ByteArray(36).also { ByteBuffer.wrap(it).putInt(0, 0x00010000).putShort(34, 1) },
            "vmtx" to ByteArray(verticalBytes),
        )
        val bytes = ByteArray(12 + records.size * 16 + records.values.sumOf { (it.size + 3) and -4 })
        val buffer = ByteBuffer.wrap(bytes).putInt(0, 0x00010000).putShort(4, records.size.toShort())
        var offset = 12 + records.size * 16
        records.entries.forEachIndexed { index, (name, data) ->
            name.toByteArray(Charsets.US_ASCII).copyInto(bytes, 12 + index * 16)
            buffer.putInt(20 + index * 16, offset).putInt(24 + index * 16, data.size)
            data.copyInto(bytes, offset)
            offset += (data.size + 3) and -4
        }
        return bytes
    }

    private fun tables(bytes: ByteArray): Map<String, ByteArray> {
        val buffer = ByteBuffer.wrap(bytes)
        return (0 until buffer.getShort(4).toInt()).associate { index ->
            val at = 12 + index * 16
            val offset = buffer.getInt(at + 8)
            String(bytes, at, 4, Charsets.US_ASCII) to bytes.copyOfRange(offset, offset + buffer.getInt(at + 12))
        }
    }
}
