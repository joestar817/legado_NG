package io.legado.app.model.jsSource

import cn.hutool.core.codec.Base64
import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

class JsSourceEngineBinaryCodecTest {

    private val engine = JsSourceEngine(
        BookSource(
            bookSourceUrl = "https://example.com/js-source-codec",
            bookSourceName = "JS Source Codec Test",
            mainJs = "function search(){return [];} function getChapters(){return [];} " +
                "function getContent(){return '';}",
        )
    )

    @Test
    fun memoryArchiveRejectsInvalidInputBeforeNativeAccess() {
        assertThrows(IllegalArgumentException::class.java) { engine.getArchiveByteArrayContent("", "data") }
        assertThrows(IllegalArgumentException::class.java) {
            engine.getArchiveByteArrayContent("A".repeat(2_000_001), "data")
        }
        assertThrows(IllegalArgumentException::class.java) { engine.getArchiveByteArrayContent("AA==", "bad\u0000name") }
    }

    @Test
    fun gzipRoundTripsUtf8Text() {
        val value = "番茄正文\n第二段"

        val encoded = engine.gzipUtf8ToBase64(value)

        assertEquals(value, engine.decompressBase64ToUtf8(encoded))
    }

    @Test
    fun zlibAndPlainBytesDecodeToUtf8() {
        val value = "压缩正文"
        val compressed = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(value.toByteArray()) }
        }.toByteArray()

        assertEquals(
            value,
            engine.decompressBase64ToUtf8(
                Base64.encode(compressed)
            )
        )
        assertEquals(
            value,
            engine.decompressBase64ToUtf8(
                Base64.encode(value.toByteArray())
            )
        )
    }

    @Test
    fun binaryPostRejectsCleartextTransportBeforeNetworkAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.postBase64Body(
                "http://example.com/device-register",
                Base64.encode(byteArrayOf(1)),
                null,
            )
        }
    }
}
