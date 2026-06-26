package io.legado.app.help.http

import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLogTest {

    @Test
    fun formatHeadersRedactsAuthenticationCredentials() {
        val headers = Headers.headersOf(
            "Authorization", "Bearer sk-test-secret",
            "Cookie", "session=abc; uid=1",
            "Set-Cookie", "sid=response-secret; Path=/",
            "X-Api-Key", "api-key-secret",
            "User-Agent", "Legado"
        )

        val formatted = NetworkLog.formatHeaders(headers)

        assertTrue(formatted.contains("Authorization: [已脱敏]"))
        assertTrue(formatted.contains("Cookie: [已脱敏]"))
        assertTrue(formatted.contains("Set-Cookie: [已脱敏]"))
        assertTrue(formatted.contains("X-Api-Key: [已脱敏]"))
        assertTrue(formatted.contains("User-Agent: Legado"))
        assertFalse(formatted.contains("sk-test-secret"))
        assertFalse(formatted.contains("session=abc"))
        assertFalse(formatted.contains("response-secret"))
        assertFalse(formatted.contains("api-key-secret"))
    }

    @Test
    fun redactCredentialsForLogRedactsBodyCredentials() {
        val body = """
            {"api_key":"sk-json-secret","password":"pwd-secret","content":"正文 Bearer token 不应出现"}
            access_token=form-secret&name=reader
            Authorization: Bearer header-secret
        """.trimIndent()

        val redacted = NetworkLog.redactCredentialsForLog(body)

        assertFalse(redacted.contains("sk-json-secret"))
        assertFalse(redacted.contains("pwd-secret"))
        assertFalse(redacted.contains("form-secret"))
        assertFalse(redacted.contains("header-secret"))
        assertTrue(redacted.contains("\"api_key\":\"[已脱敏]\""))
        assertTrue(redacted.contains("\"password\":\"[已脱敏]\""))
        assertTrue(redacted.contains("access_token=[已脱敏]"))
        assertTrue(redacted.contains("Bearer [已脱敏]"))
    }

    @Test
    fun redactUrlForLogRedactsCredentialQueryParams() {
        val url = "https://example.com/api?access_token=url-secret&name=reader&api_key=key-secret#frag"

        val redacted = NetworkLog.redactUrlForLog(url)

        assertEquals(
            "https://example.com/api?access_token=[已脱敏]&name=reader&api_key=[已脱敏]#frag",
            redacted
        )
    }
}
