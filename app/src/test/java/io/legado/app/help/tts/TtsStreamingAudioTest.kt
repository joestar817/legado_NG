package io.legado.app.help.tts

import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsStreamingAudioTest {

    private val audioRule = TtsWebSocketTextRule(
        matchPath = "choices[0].delta.audio.data",
        audioPath = "choices[0].delta.audio.data",
        audioEncoding = "base64"
    )

    @Test
    fun parseSynthesisRequest_readsSseAndPcmConfig() {
        val request = TtsScriptEngineClient.parseSynthesisRequest(
            """
            {
              "url": "https://example.com/v1/chat/completions",
              "transport": "sse",
              "audioContentType": "audio/pcm",
              "stream": {
                "doneData": "[DONE]",
                "maxAudioBytes": 4096,
                "pcm": {
                  "sampleRate": 24000,
                  "channels": 1,
                  "bitsPerSample": 16
                },
                "textRules": [
                  {
                    "matchPath": "choices[0].delta.audio.data",
                    "audioPath": "choices[0].delta.audio.data",
                    "audioEncoding": "base64"
                  }
                ]
              }
            }
            """.trimIndent(),
            TtsEngineSetting(
                id = "sse-test",
                name = "SSE Test",
                type = TtsEngineType.SCRIPT
            )
        )

        assertTrue(request.isSse)
        val config = requireNotNull(request.sseConfig)
        assertEquals(4096, config.maxAudioBytes)
        assertEquals(1, config.textRules.size)
        assertEquals(24000, config.pcm?.sampleRate)
        assertEquals(1, config.pcm?.channels)
        assertEquals(16, config.pcm?.bitsPerSample)
    }

    @Test
    fun sseStream_decodesChatCompletionAudioChunks() {
        val source = Buffer().writeUtf8(
            """
            data: {"choices":[{"delta":{"audio":{"data":"AQI="}}}]}

            data: {"choices":[{"delta":{"audio":{"data":"AwQ="}}}]}

            data: [DONE]

            """.trimIndent()
        )
        val input = TtsSseAudioInputStream(
            source,
            TtsSseConfig(textRules = listOf(audioRule))
        )

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), input.readBytes())
    }

    @Test
    fun ssePcmStream_prependsStreamingWavHeader() {
        val source = Buffer().writeUtf8(
            """
            data: {"choices":[{"delta":{"audio":{"data":"AQIDBA=="}}}]}

            data: [DONE]

            """.trimIndent()
        )
        val input = TtsSseAudioInputStream(
            source,
            TtsSseConfig(
                textRules = listOf(audioRule),
                pcm = TtsPcmConfig(sampleRate = 24000, channels = 1, bitsPerSample = 16)
            )
        )

        val bytes = input.readBytes()

        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals(24000, bytes.littleEndianInt(24))
        assertEquals(1, bytes.littleEndianShort(22))
        assertEquals(16, bytes.littleEndianShort(34))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes.copyOfRange(44, bytes.size))
    }

    @Test
    fun chunkedInputStream_exposesChunkBeforeFinish() {
        val input = TtsChunkedInputStream()
        input.offer(byteArrayOf(5, 6, 7))
        val first = ByteArray(3)

        assertEquals(3, input.read(first))
        assertArrayEquals(byteArrayOf(5, 6, 7), first)

        input.finish()
        assertEquals(-1, input.read())
    }

    @Test
    fun sseStream_propagatesDeclaredServiceError() {
        val source = Buffer().writeUtf8(
            """
            data: {"error":{"message":"invalid voice"}}

            """.trimIndent()
        )
        val input = TtsSseAudioInputStream(
            source,
            TtsSseConfig(
                textRules = listOf(
                    TtsWebSocketTextRule(
                        matchPath = "error.message",
                        errorPath = "error.message"
                    )
                )
            )
        )

        val error = assertThrows(java.io.IOException::class.java) {
            input.read()
        }

        assertEquals("invalid voice", error.message)
    }

    @Test
    fun chunkedInputStream_closeCancelsProducer() {
        var cancelled = false
        val input = TtsChunkedInputStream(onCloseStream = { cancelled = true })

        input.close()

        assertTrue(cancelled)
        assertEquals(-1, input.read())
    }

    private fun ByteArray.littleEndianShort(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
                ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.littleEndianInt(offset: Int): Int {
        return littleEndianShort(offset) or (littleEndianShort(offset + 2) shl 16)
    }
}
