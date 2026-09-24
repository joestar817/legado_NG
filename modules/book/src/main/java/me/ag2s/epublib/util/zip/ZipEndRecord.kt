package me.ag2s.epublib.util.zip

import java.util.zip.ZipException

/** 在内存中扫描有限的 ZIP 尾部，避免逐字节调用文件描述符。 */
internal object ZipEndRecord {
    private const val HEADER_SIZE = 22
    const val MAX_TAIL = HEADER_SIZE + 65535

    @JvmStatic
    @Throws(ZipException::class)
    fun find(tail: ByteArray): Int {
        for (offset in tail.size - HEADER_SIZE downTo 0) {
            if (tail[offset] == 0x50.toByte() && tail[offset + 1] == 0x4b.toByte()
                && tail[offset + 2] == 5.toByte() && tail[offset + 3] == 6.toByte()
            ) {
                val commentLength = (tail[offset + 20].toInt() and 255) or
                    ((tail[offset + 21].toInt() and 255) shl 8)
                // 注释内的同名签名不是目录结束记录。
                if (offset + HEADER_SIZE + commentLength == tail.size) return offset
            }
        }
        throw ZipException("文件损坏或不完整：无法读取 EPUB 压缩目录")
    }
}
