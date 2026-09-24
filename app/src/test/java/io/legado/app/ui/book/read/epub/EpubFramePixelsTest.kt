package io.legado.app.ui.book.read.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.random.Random

class EpubFramePixelsTest {
    @Test
    fun bulkConversionMatchesRgbaChannelAndRowOrder() {
        val random = Random(7)
        for (width in 1..11) for (height in 1..12) {
            val bytes = random.nextBytes(width * height * 4)
            val expected = IntArray(width * height) { i ->
                val offset = ((height - 1 - i / width) * width + i % width) * 4
                ((bytes[offset + 3].toInt() and 255) shl 24) or
                    ((bytes[offset].toInt() and 255) shl 16) or
                    ((bytes[offset + 1].toInt() and 255) shl 8) or
                    (bytes[offset + 2].toInt() and 255)
            }
            val buffer = ByteBuffer.allocateDirect(bytes.size + 4).putInt(123).put(bytes)
            buffer.flip().position(4)
            val actual = IntArray(width * height)
            repeat(2) {
                readEpubFramePixels(buffer, width, height, actual)
                assertArrayEquals(expected, actual)
            }
        }
    }

    @Test
    fun rejectsIncompletePixelBuffers() {
        assertThrows(IllegalArgumentException::class.java) {
            readEpubFramePixels(ByteBuffer.allocate(7), 2, 1, IntArray(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readEpubFramePixels(ByteBuffer.allocate(8), 2, 1, IntArray(1))
        }
    }
}
