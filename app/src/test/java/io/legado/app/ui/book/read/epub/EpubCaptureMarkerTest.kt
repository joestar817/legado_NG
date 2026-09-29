package io.legado.app.ui.book.read.epub

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.floor
import kotlin.math.round

class EpubCaptureMarkerTest {
    /** Model glReadPixels after a SurfaceTexture crop and limited texture-coordinate precision. */
    private fun pixels(marker: EpubCaptureMarker, token: Long?, width: Int = 1116, height: Int = 2480,
        inset: Int = 0, quantized: Boolean = false, black: Int = 0, white: Int = 255): ByteBuffer {
        val result = ByteBuffer.allocate(marker.byteCount)
        for (y in 0 until marker.height) for (x in 0 until marker.width) {
            fun sample(pixel: Int, extent: Int, flipped: Boolean): Int {
                var coordinate = (inset + (pixel + .5) / extent * (extent - 2 * inset)) / extent
                if (flipped) coordinate = 1 - coordinate
                if (quantized) coordinate = round(coordinate * 1024) / 1024
                if (flipped) coordinate = 1 - coordinate
                return floor(coordinate * extent).toInt().coerceIn(0, extent - 1)
            }
            val sourceX = sample(marker.left + x, width, false) - marker.left
            val sourceY = sample(marker.top + y, height, true) - marker.top
            val bright = token != null && sourceX in 0 until marker.width && sourceY in 0 until marker.height &&
                marker.white(token, sourceY / marker.cellSize * EpubCaptureMarker.COLUMNS + sourceX / marker.cellSize)
            val color = if (bright) white else black
            val at = ((marker.height - 1 - y) * marker.width + x) * 4
            repeat(3) { result.put(at + it, color.toByte()) }
            result.put(at + 3, 255.toByte())
        }
        return result
    }

    @Test fun onePixelCornerFenceIsLostByDocumentedOneTexelCrop() {
        // Old marker was only at Canvas (0,0). After the crop its sampled source is (1,1).
        val width = 1116
        val height = 2480
        val x = floor(1 + .5 / width * (width - 2)).toInt()
        val y = floor(1 + .5 / height * (height - 2)).toInt()
        assertEquals(1, x)
        assertEquals(1, y)
    }

    @Test fun centeredMarkerSurvivesCropQuantizationAndColorRounding() {
        val random = java.util.Random(28)
        for ((width, height) in listOf(1116 to 2480, 1080 to 2400, 1440 to 3200, 320 to 480)) {
            val marker = EpubCaptureMarker(width, height)
            repeat(200) {
                val token = random.nextLong()
                val readback = pixels(marker, token, width, height, inset = it % 3, quantized = true, black = 16, white = 235)
                assertTrue("Failed $width x $height sample $it", marker.matches(readback, token))
                assertFalse(marker.matches(readback, token xor 1L))
            }
        }
    }

    @Test fun fullRequestIdentityDoesNotAliasAfterOldColorCounterWrap() {
        val marker = EpubCaptureMarker(1116, 2480)
        for (token in listOf(1L, 65537L, Long.MIN_VALUE, Long.MAX_VALUE, -1L)) {
            val readback = pixels(marker, token)
            assertTrue(marker.matches(readback, token))
            assertFalse(marker.matches(readback, token xor (1L shl 32)))
        }
    }

    @Test fun cleanFrameMustBeNewerAndMustNotContainAnyRequestMarker() {
        val marker = EpubCaptureMarker(1116, 2480)
        assertFalse(marker.isCleanAfter(pixels(marker, 1), 100, 101))
        assertFalse(marker.isCleanAfter(pixels(marker, 2), 100, 102))
        val clean = pixels(marker, null)
        assertFalse(marker.isCleanAfter(clean, 100, 99))
        assertFalse(marker.isCleanAfter(clean, 100, 100))
        assertTrue(marker.isCleanAfter(clean, 100, 101))
    }

    @Test fun transparentAndSolidBuffersCannotAcknowledgeAMarker() {
        val marker = EpubCaptureMarker(1116, 2480)
        assertFalse(marker.matches(ByteBuffer.allocate(marker.byteCount), 1))
        assertFalse(marker.matches(pixels(marker, null, black = 255), 1))
        assertFalse(marker.matches(pixels(marker, null, black = 0), 1))
        assertFalse(marker.isCleanAfter(ByteBuffer.allocate(4), 100, 101))
    }
}
