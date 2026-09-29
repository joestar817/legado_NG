package io.legado.app.ui.book.read.epub

import java.nio.ByteBuffer

/** Private-display fence: a fixed signature and all 64 request bits, sampled inside large cells. */
internal class EpubCaptureMarker(surfaceWidth: Int, surfaceHeight: Int) {
    val cellSize = minOf(8, surfaceWidth / COLUMNS, surfaceHeight / ROWS)
    val width = COLUMNS * cellSize
    val height = ROWS * cellSize
    val left = (surfaceWidth - width) / 2
    val top = (surfaceHeight - height) / 2
    val byteCount = this.width * this.height * 4

    init { require(cellSize > 0) { "EPUB 捕获区域过小" } }

    fun white(token: Long, cell: Int): Boolean {
        require(cell in 0 until CELLS)
        return if (cell < 32) (SIGNATURE ushr (31 - cell)) and 1 != 0
        else (token ushr (63 - (cell - 32))) and 1L != 0L
    }

    fun matches(pixels: ByteBuffer, token: Long): Boolean =
        pixels.limit() >= byteCount && (0 until CELLS).all { bit(pixels, it) == if (white(token, it)) 1 else 0 }

    /** Do not accept another marked buffer as the clean frame, even if it has a different token. */
    fun containsMarker(pixels: ByteBuffer): Boolean =
        pixels.limit() >= byteCount && (0 until 32).all { bit(pixels, it) == if (white(0, it)) 1 else 0 }

    fun isCleanAfter(pixels: ByteBuffer, markedAt: Long, timestamp: Long): Boolean =
        pixels.limit() >= byteCount && timestamp > markedAt && !containsMarker(pixels)

    private fun bit(pixels: ByteBuffer, cell: Int): Int {
        val x = cell % COLUMNS * cellSize + cellSize / 2
        val y = cell / COLUMNS * cellSize + cellSize / 2
        val radius = if (cellSize >= 4) 1 else 0
        var black = 0
        var white = 0
        for (dy in -radius..radius) for (dx in -radius..radius) {
            // glReadPixels returns rows from the bottom; Canvas draws from the top.
            val at = ((height - 1 - (y + dy)) * width + x + dx) * 4
            val red = pixels.get(at).toInt() and 255
            val green = pixels.get(at + 1).toInt() and 255
            val blue = pixels.get(at + 2).toInt() and 255
            val alpha = pixels.get(at + 3).toInt() and 255
            if (alpha >= 192) {
                if (maxOf(red, green, blue) <= 64) black++
                if (minOf(red, green, blue) >= 192) white++
            }
        }
        val required = if (radius == 0) 1 else 7
        return when { white >= required -> 1; black >= required -> 0; else -> -1 }
    }

    companion object {
        const val COLUMNS = 16
        const val ROWS = 6
        const val CELLS = COLUMNS * ROWS
        private const val SIGNATURE = 0xD3A5C96E.toInt()
    }
}
