package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderUtilsTest {

    @Test
    fun `provider host is extracted from full url`() {
        assertEquals(
            "api.openai.com",
            aiProviderHost("https://api.openai.com/v1")
        )
        assertEquals(
            "openrouter.ai",
            aiProviderHost("https://openrouter.ai/api/v1/")
        )
    }

    @Test
    fun `provider host tolerates urls without scheme`() {
        assertEquals("api.deepseek.com", aiProviderHost("api.deepseek.com"))
        assertEquals("example.com", aiProviderHost("example.com/v1?debug=1"))
    }

    @Test
    fun `provider host is empty for blank input`() {
        assertEquals("", aiProviderHost("   "))
    }
}
