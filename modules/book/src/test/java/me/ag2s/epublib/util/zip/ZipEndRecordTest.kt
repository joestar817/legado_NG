package me.ag2s.epublib.util.zip

import org.junit.Assert.*
import org.junit.Test
import java.util.zip.ZipException

class ZipEndRecordTest {
    private fun record(comment: Int, prefix: Int = 0): ByteArray = ByteArray(prefix + 22 + comment).apply {
        this[prefix] = 0x50
        this[prefix + 1] = 0x4b
        this[prefix + 2] = 5
        this[prefix + 3] = 6
        this[prefix + 20] = comment.toByte()
        this[prefix + 21] = (comment shr 8).toByte()
    }

    @Test fun emptyZipHasValidEndRecord() = assertEquals(0, ZipEndRecord.find(record(0)))
    @Test fun ordinaryCommentIsAccepted() = assertEquals(6, ZipEndRecord.find(record(19, 6)))
    @Test fun maximumCommentAtWindowBoundaryIsAccepted() {
        val tail = record(65535)
        assertEquals(ZipEndRecord.MAX_TAIL, tail.size)
        assertEquals(0, ZipEndRecord.find(tail))
    }
    @Test fun falseSignatureInsideCommentIsIgnored() {
        val tail = record(50)
        tail[30] = 0x50; tail[31] = 0x4b; tail[32] = 5; tail[33] = 6
        assertEquals(0, ZipEndRecord.find(tail))
    }
    @Test fun truncatedAndShortFilesAreRejected() {
        for (size in listOf(0, 3, 21)) {
            assertThrows(ZipException::class.java) { ZipEndRecord.find(ByteArray(size)) }
        }
        assertThrows(ZipException::class.java) { ZipEndRecord.find(record(15).copyOf(30)) }
    }
    @Test fun invalidTailRejectsWithoutFileAccess() {
        assertThrows(ZipException::class.java) { ZipEndRecord.find(ByteArray(ZipEndRecord.MAX_TAIL)) }
    }
}
