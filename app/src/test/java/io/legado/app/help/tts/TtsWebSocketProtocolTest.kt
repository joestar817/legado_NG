package io.legado.app.help.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient

class TtsWebSocketProtocolTest {

    private val engine = TtsEngineSetting(
        id = "websocket-test",
        name = "WebSocket Test",
        type = TtsEngineType.SCRIPT
    )

    @Test
    fun parseSynthesisRequest_readsWebSocketProtocol() {
        val request = TtsScriptEngineClient.parseSynthesisRequest(
            """
            {
              "transport": "websocket",
              "url": "wss://example.com/tts",
              "headers": {"Origin": "https://example.com"},
              "audioContentType": "audio/mpeg",
              "timeout": 60,
              "websocket": {
                "openMessages": ["start"],
                "binaryAudio": true,
                "finishOnClose": true,
                "connectTimeout": 8,
                "firstAudioTimeout": 12,
                "idleTimeout": 5,
                "finishGrace": 300,
                "maxAudioBytes": 1024,
                "textRules": [
                  {
                    "matchPath": "event",
                    "equals": "TaskFinished",
                    "finish": true
                  }
                ]
              }
            }
            """.trimIndent(),
            engine
        )

        assertTrue(request.isWebSocket)
        assertEquals("https://example.com", request.headers["Origin"])
        assertEquals("audio/mpeg", request.audioContentType)
        val config = requireNotNull(request.webSocketConfig)
        assertEquals(listOf("start"), config.openMessages)
        assertTrue(config.binaryAudio)
        assertTrue(config.finishOnClose)
        assertEquals(8, config.connectTimeoutSeconds)
        assertEquals(12, config.firstAudioTimeoutSeconds)
        assertEquals(5, config.idleTimeoutSeconds)
        assertEquals(300, config.finishGraceMillis)
        assertEquals(1024, config.maxAudioBytes)
        assertEquals(1, config.textRules.size)
    }

    @Test
    fun parseSynthesisRequest_infersWebSocketFromUrl() {
        val request = TtsScriptEngineClient.parseSynthesisRequest(
            """{"url":"wss://example.com/tts","websocket":{}}""",
            engine
        )

        assertTrue(request.isWebSocket)
    }

    @Test
    fun protocol_handlesTaskSequenceAndAudioFrames() {
        val protocol = TtsWebSocketProtocol(
            TtsWebSocketConfig(
                openMessages = listOf("start-task"),
                textRules = listOf(
                    TtsWebSocketTextRule(
                        matchPath = "status_code",
                        notEqualsValue = "20000000",
                        errorPath = "status_text"
                    ),
                    TtsWebSocketTextRule(
                        matchPath = "event",
                        equalsValue = "TaskStarted",
                        sendMessages = listOf("text", "finish-task")
                    ),
                    TtsWebSocketTextRule(
                        matchPath = "type",
                        equalsValue = "3",
                        audioPath = "buffer",
                        audioEncoding = "base64"
                    ),
                    TtsWebSocketTextRule(
                        matchPath = "event",
                        equalsValue = "TaskFinished",
                        finish = true
                    )
                )
            )
        )

        assertEquals(listOf("start-task"), protocol.openMessages())
        val started = protocol.onText("""{"event":"TaskStarted","status_code":20000000}""")
        assertEquals(listOf("text", "finish-task"), started.sendMessages)
        assertFalse(started.finished)
        assertNull(started.error)

        val audio = protocol.onText("""{"type":3,"buffer":"AQID"}""")
        assertEquals(1, audio.audioChunks.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), audio.audioChunks.single())

        val binary = protocol.binaryAudio(byteArrayOf(4, 5))
        assertArrayEquals(byteArrayOf(4, 5), binary)

        val finished = protocol.onText("""{"event":"TaskFinished"}""")
        assertTrue(finished.finished)
        assertNull(finished.error)
    }

    @Test
    fun protocol_extractsServiceError() {
        val protocol = TtsWebSocketProtocol(
            TtsWebSocketConfig(
                textRules = listOf(
                    TtsWebSocketTextRule(
                        matchPath = "status_code",
                        notEqualsValue = "20000000",
                        errorPath = "status_text"
                    )
                )
            )
        )

        val result = protocol.onText("""{"status_code":50000000,"status_text":"invalid voice"}""")

        assertEquals("invalid voice", result.error)
    }

    @Test
    fun scriptMetadata_readsConcurrentRate() {
        val parsed = TtsEngineStore.scriptEngineFromScript(
            """
            // @name WebSocket Test
            // @uuid websocket_test
            // @concurrentRate 200
            function synthesize() { return {}; }
            """.trimIndent()
        )

        assertEquals("200", parsed?.concurrentRate)
    }

    @Test
    fun webSocketClient_removesHttpInterceptors() {
        val baseClient = OkHttpClient.Builder()
            .addInterceptor { chain -> chain.proceed(chain.request()) }
            .addNetworkInterceptor { chain -> chain.proceed(chain.request()) }
            .build()

        val client = TtsWebSocketEngineClient.createWebSocketClient(baseClient, 8)

        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
        assertEquals(8_000, client.connectTimeoutMillis)
        assertEquals(0, client.readTimeoutMillis)
    }
}
