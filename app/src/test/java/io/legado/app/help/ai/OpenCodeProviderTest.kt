package io.legado.app.help.ai

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeProviderTest {
    private val provider = AiDefaultProviders.all().single { it.id == AiDefaultProviders.OPENCODE_ID }

    @Test
    fun `built in provider uses OpenCode Go chat and model endpoints`() {
        assertEquals("OpenCode", provider.name)
        assertEquals(AiProviderType.OPENAI, provider.type)
        assertTrue(provider.builtIn)
        assertEquals("https://opencode.ai/inference/go", provider.baseUrl)
        assertEquals(
            "https://opencode.ai/inference/go/openai/v1/chat/completions",
            provider.baseUrl.trimEndSlash() + provider.chatCompletionsPath.ensureStartSlash()
        )
        assertEquals(
            listOf("https://opencode.ai/inference/go/v1/models"),
            AiModelEndpointResolver.candidates(provider)
        )
    }

    @Test
    fun `built in chat and compaction share a stable per conversation session`() {
        val first = request(provider, "会话\n一")
        val second = request(provider, "会话\n一")
        val other = request(provider, "会话二")
        val session = requireNotNull(first.header("x-opencode-session"))
        assertTrue(session.matches(Regex("ses_[0-9a-f]{64}")))
        assertEquals(session, second.header("x-opencode-session"))
        assertNotEquals(session, other.header("x-opencode-session"))
        assertTrue(requireNotNull(first.header("User-Agent")).startsWith("Legado-NG/"))
        assertEquals("Bearer test", first.header("Authorization"))
    }

    @Test
    fun `standalone sessions differ and custom providers keep their request headers`() {
        val first = request(provider, null)
        val second = request(provider, null)
        assertNotEquals(first.header("x-opencode-session"), second.header("x-opencode-session"))

        val custom = provider.copy(id = "custom_openai_1", builtIn = false)
        val customRequest = request(custom, "same-conversation")
        assertNull(customRequest.header("x-opencode-session"))
        assertEquals("Existing-Agent/1.0", customRequest.header("User-Agent"))
        assertEquals("Bearer test", customRequest.header("Authorization"))
        assertNull(request(provider.copy(builtIn = false), "same-conversation").header("x-opencode-session"))
    }

    private fun request(setting: AiProviderSetting, conversationId: String?): Request = Request.Builder()
        .url(setting.baseUrl)
        .header("Authorization", "Bearer test")
        .header("User-Agent", "Existing-Agent/1.0")
        .addOpenCodeProviderHeaders(setting, conversationId)
        .build()
}
