package io.legado.app.ui.book.read.epub

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** OpenGL RGBA rows start at the bottom; Bitmap's ARGB colour array starts at the top. */
internal fun readEpubFramePixels(buffer: ByteBuffer, width: Int, height: Int, colors: IntArray) {
    require(width > 0 && height > 0 && width.toLong() * height == colors.size.toLong())
    require(buffer.remaining().toLong() >= colors.size.toLong() * 4)
    buffer.order(ByteOrder.BIG_ENDIAN).asIntBuffer().get(colors)
    for (y in 0 until (height + 1) / 2) {
        val top = y * width
        val bottom = (height - 1 - y) * width
        for (x in 0 until width) {
            val a = colors[top + x]
            val b = colors[bottom + x]
            colors[top + x] = Integer.rotateRight(b, 8)
            colors[bottom + x] = Integer.rotateRight(a, 8)
        }
    }
}
