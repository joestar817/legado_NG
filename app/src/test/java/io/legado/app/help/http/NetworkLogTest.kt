package io.legado.app.help.http

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLogTest {

    @Test
    fun recordsGenerationAfterHttpSuccessAndRetainsBothElapsedTimes() {
        val request = Request.Builder().url("https://example.invalid/responses")
            .tag(NetworkLog.GenerationTrace::class.java, NetworkLog.GenerationTrace())
            .build()
        val response = responseWithBody("fixture".toResponseBody())
        val entry = NetworkLog.okHttpEntry(request, response, 15L, error = null, aiRequest = true)
        try {
            NetworkLog.addAiEntry(request, entry)
            NetworkLog.finishGeneration(request, NetworkLog.GenerationState.TRUNCATED, "length")
            val finished = requireNotNull(NetworkLog.find(entry.id))
            assertEquals(200, finished.statusCode)
            assertEquals(15L, finished.tookMs)
            assertEquals(NetworkLog.GenerationState.TRUNCATED, finished.generation?.state)
            assertEquals("length", finished.generation?.finishReason)
            assertTrue(requireNotNull(finished.generation).elapsedMs >= 0L)
        } finally {
            NetworkLog.clear()
        }
    }

    @Test
    fun preservesAnEarlyCancellationWhenTheTransportEntryArrivesLater() {
        val request = Request.Builder().url("https://example.invalid/responses")
            .tag(NetworkLog.GenerationTrace::class.java, NetworkLog.GenerationTrace())
            .build()
        try {
            NetworkLog.finishGeneration(request, NetworkLog.GenerationState.CANCELLED)
            val entry = NetworkLog.okHttpEntry(request, null, 15L, error = null, aiRequest = true)
            NetworkLog.addAiEntry(request, entry)
            assertEquals(NetworkLog.GenerationState.CANCELLED, NetworkLog.find(entry.id)?.generation?.state)
        } finally {
            NetworkLog.clear()
        }
    }

    @Test
    fun boundsAndRedactsRequestPreviewsEvenWhenACredentialIsCutMidValue() {
        val secret = "fixture-secret"
        val body = """{"refresh_token":"$secret""" +
            "x".repeat(NetworkLog.BODY_PREVIEW_SIZE.toInt()) + """"}"""
        val request = Request.Builder()
            .url("https://example.invalid/responses")
            .header("X-Refresh-Token", "fixture-header")
            .apply { networkLogSource("Fixture") }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val entry = NetworkLog.okHttpEntry(request, null, 15L, error = null, aiRequest = true)
        assertFalse(entry.detail.contains(secret))
        assertFalse(entry.detail.contains("fixture-header"))
        assertTrue(entry.requestBody.orEmpty().contains("[已脱敏]"))
        assertTrue(entry.requestBody.orEmpty().contains("truncated at"))
    }

    @Test
    fun evictsOldLargeEntriesBeforeReachingTheEntryCountLimit() {
        val request = Request.Builder().url("https://example.invalid/responses").build()
        val preview = "a".repeat(NetworkLog.BODY_PREVIEW_SIZE.toInt())
        try {
            repeat(64) {
                val entry = NetworkLog.okHttpEntry(request, null, 15L, error = null, aiRequest = true)
                    .copy(requestBody = preview, responseBody = preview)
                NetworkLog.addAiEntry(request, entry)
            }
            assertTrue(NetworkLog.logs.size in 1..16)
        } finally {
            NetworkLog.clear()
        }
    }

    @Test
    fun aiMetadataLogDoesNotReadTheStreamOrExposeRequestData() {
        var streamReads = 0
        val request = Request.Builder()
            .url("https://reader:private@copilot.tencent.com/v2/chat/completions?state=private#private")
            .header("Authorization", "Bearer fixture-token")
            .post("private prompt".toRequestBody())
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .header("X-User-Id", "private-user-id")
            .body(object : ResponseBody() {
                override fun contentType() = "text/event-stream".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource {
                    streamReads++
                    error("Log should not read from this stream")
                }
            })
            .build()

        val entry = NetworkLog.okHttpEntry(request, response, 25L, error = null, aiRequest = true)

        assertEquals("AI", entry.source)
        assertEquals("POST", entry.method)
        assertEquals("https://copilot.tencent.com/v2/chat/completions", entry.url)
        assertEquals(200, entry.statusCode)
        assertEquals(25L, entry.tookMs)
        assertEquals(0, streamReads)
        assertNull(entry.requestHeaders)
        assertNull(entry.requestBody)
        assertNull(entry.responseHeaders)
        assertNull(entry.responseBody)
    }

    @Test
    fun aiTextRequestLogKeepsPayloadAndRedactsCredentials() {
        val request = Request.Builder()
            .url("https://copilot.tencent.com/v2/chat/completions?credential=fixture")
            .header("Authorization", "Bearer fixture-token")
            .header("X-User-Id", "fixture-account")
            .apply { networkLogSource("AI听书 · 分镜归因") }
            .post("""{"model":"hy4flash-free","max_tokens":64000,"messages":[]}""".toRequestBody())
            .build()

        val entry = NetworkLog.okHttpEntry(request, null, 30L, error = null, aiRequest = true)

        assertEquals("AI听书 · 分镜归因", entry.source)
        assertEquals("https://copilot.tencent.com/v2/chat/completions", entry.url)
        assertTrue(entry.requestBody.orEmpty().contains("hy4flash-free"))
        assertTrue(entry.requestBody.orEmpty().contains("\"max_tokens\":64000"))
        assertTrue(entry.requestHeaders.orEmpty().contains("Authorization: [已脱敏]"))
        assertFalse(entry.detail.contains("fixture-token"))
        assertFalse(entry.detail.contains("fixture-account"))
    }

    @Test
    fun aiStreamLogDoesNotReadAheadOrDrainOnClose() {
        val firstFrame = "data: {\"choices\":[]}\n\n"
        var reads = 0
        var closed = false
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++
                check(reads == 1) { "The logger should not read ahead" }
                sink.writeUtf8(firstFrame)
                return firstFrame.toByteArray().size.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {
                closed = true
            }
        }
        val body = source.buffer().asResponseBody("text/event-stream".toMediaType())
        val previews = mutableListOf<String>()
        val response = responseWithBody(body).captureNetworkLogBody { previews += it }

        assertEquals(0, reads)
        response.use {
            assertEquals(firstFrame.trim(), it.body.source().readUtf8Line())
            assertTrue(previews.isEmpty())
        }
        assertEquals(1, reads)
        assertTrue(closed)
        assertEquals(listOf(firstFrame), previews)
    }

    @Test
    fun largeAiStreamLogKeepsTheFinishReasonAndUsage() {
        val start = "data: {\"model\":\"hy4flash-free\"}\n\n"
        val end = "data: {\"choices\":[{\"finish_reason\":\"length\"}],\"usage\":{\"completion_tokens\":64000}}\n\ndata: [DONE]\n\n"
        val body = start + "x".repeat(NetworkLog.BODY_PREVIEW_SIZE.toInt()) + "\n\n" + end
        val previews = mutableListOf<String>()
        val response = responseWithBody(body.toResponseBody("text/event-stream".toMediaType()))
            .captureNetworkLogBody { previews += it }

        assertEquals(body, response.use { it.body.string() })
        val preview = previews.single()
        assertTrue(preview.startsWith(start))
        assertTrue(preview.endsWith(end))
        assertTrue(preview.contains("bytes omitted"))
    }

    private fun responseWithBody(body: ResponseBody): Response = Response.Builder()
        .request(Request.Builder().url("https://copilot.tencent.com/v2/chat/completions").build())
        .protocol(Protocol.HTTP_2)
        .code(200)
        .message("OK")
        .body(body)
        .build()

    @Test
    fun formatHeadersRedactsAuthenticationCredentials() {
        val headers = Headers.headersOf(
            "Authorization", "Bearer sk-test-secret",
            "Cookie", "session=abc; uid=1",
            "Set-Cookie", "sid=response-secret; Path=/",
            "X-Api-Key", "api-key-secret",
            "X-Goog-Api-Key", "gemini-key-secret",
            "X-CSRF-Token", "csrf-header-secret",
            "X-XSRF-Token", "xsrf-header-secret",
            "User-Agent", "Legado"
        )

        val formatted = NetworkLog.formatHeaders(headers)

        assertTrue(formatted.contains("Authorization: [已脱敏]"))
        assertTrue(formatted.contains("Cookie: [已脱敏]"))
        assertTrue(formatted.contains("Set-Cookie: [已脱敏]"))
        assertTrue(formatted.contains("X-Api-Key: [已脱敏]"))
        assertTrue(formatted.contains("X-Goog-Api-Key: [已脱敏]"))
        assertTrue(formatted.contains("X-CSRF-Token: [已脱敏]"))
        assertTrue(formatted.contains("X-XSRF-Token: [已脱敏]"))
        assertTrue(formatted.contains("User-Agent: Legado"))
        assertFalse(formatted.contains("sk-test-secret"))
        assertFalse(formatted.contains("session=abc"))
        assertFalse(formatted.contains("response-secret"))
        assertFalse(formatted.contains("api-key-secret"))
        assertFalse(formatted.contains("gemini-key-secret"))
        assertFalse(formatted.contains("csrf-header-secret"))
        assertFalse(formatted.contains("xsrf-header-secret"))
    }

    @Test
    fun formatHeaderMapRedactsGeminiApiKey() {
        val formatted = NetworkLog.formatHeaders(
            mapOf(
                "x-goog-api-key" to "gemini-map-secret",
                "Content-Type" to "application/json"
            )
        )

        assertTrue(formatted.contains("x-goog-api-key: [已脱敏]"))
        assertTrue(formatted.contains("Content-Type: application/json"))
        assertFalse(formatted.contains("gemini-map-secret"))
    }

    @Test
    fun redactCredentialsForLogRedactsBodyCredentials() {
        val body = """
            {"api_key":"sk-json-secret","password":"pwd-secret","_csrfToken":"csrf-json-secret","content":"正文 Bearer token 不应出现"}
            access_token=form-secret&xsrf-token=xsrf-form-secret&name=reader
            Authorization: Bearer header-secret
        """.trimIndent()

        val redacted = NetworkLog.redactCredentialsForLog(body)

        assertFalse(redacted.contains("sk-json-secret"))
        assertFalse(redacted.contains("pwd-secret"))
        assertFalse(redacted.contains("form-secret"))
        assertFalse(redacted.contains("csrf-json-secret"))
        assertFalse(redacted.contains("xsrf-form-secret"))
        assertFalse(redacted.contains("header-secret"))
        assertTrue(redacted.contains("\"api_key\":\"[已脱敏]\""))
        assertTrue(redacted.contains("\"password\":\"[已脱敏]\""))
        assertTrue(redacted.contains("\"_csrfToken\":\"[已脱敏]\""))
        assertTrue(redacted.contains("access_token=[已脱敏]"))
        assertTrue(redacted.contains("xsrf-token=[已脱敏]"))
        assertTrue(redacted.contains("Bearer [已脱敏]"))
    }

    @Test
    fun redactUrlForLogRedactsCredentialQueryParams() {
        val url = "https://example.com/api?access_token=url-secret&name=reader" +
            "&api_key=key-secret&_csrfToken=csrf-secret&csrf_token=csrf-snake-secret" +
            "&csrf-token=csrf-kebab-secret&xsrfToken=xsrf-secret#frag"

        val redacted = NetworkLog.redactUrlForLog(url)

        assertEquals(
            "https://example.com/api?access_token=[已脱敏]&name=reader" +
                "&api_key=[已脱敏]&_csrfToken=[已脱敏]&csrf_token=[已脱敏]" +
                "&csrf-token=[已脱敏]&xsrfToken=[已脱敏]#frag",
            redacted
        )
    }

    @Test
    fun redactUrlForLogRedactsOpaqueKeysAndKeepsSearchKeyword() {
        val opaqueKey = "k".repeat(64)
        val protectedUrl = "https://example.com/api?key=$opaqueKey&lang=zh"
        val searchUrl = "https://example.com/search?key=三体&page=1"

        assertEquals(
            "https://example.com/api?key=[已脱敏]&lang=zh",
            NetworkLog.redactUrlForLog(protectedUrl),
        )
        assertEquals(searchUrl, NetworkLog.redactUrlForLog(searchUrl))
    }

    @Test
    fun redactThrowableForLogDropsUrlKeyCookieAndOriginalCause() {
        val opaqueKey = "k".repeat(64)
        val cookieValue = "fixture-cookie-secret"
        val error = IllegalStateException(
            "request failed https://example.com/api?key=$opaqueKey " +
                "headers={\"Cookie\":\"session=$cookieValue; lang=zh\"}"
        ).apply {
            stackTrace = arrayOf(StackTraceElement("Fixture", "call", "Fixture.kt", 12))
        }

        val redacted = NetworkLog.redactThrowableForLog(error)
        val text = redacted.stackTraceToString()

        assertFalse(text.contains(opaqueKey))
        assertFalse(text.contains(cookieValue))
        assertTrue(text.contains("key=[已脱敏]"))
        assertTrue(text.contains("\"Cookie\":\"[已脱敏]\""))
        assertTrue(text.contains("Fixture.call(Fixture.kt:12)"))
        assertNull(redacted.cause)
    }

    @Test
    fun displaySourceUsesGlobalWhenSourceIsMissing() {
        assertEquals("全局", NetworkLog.displaySource(null))
        assertEquals("全局", NetworkLog.displaySource(""))
        assertEquals("书源 / 书名 / 章节", NetworkLog.displaySource("书源 / 书名 / 章节"))
    }
}
