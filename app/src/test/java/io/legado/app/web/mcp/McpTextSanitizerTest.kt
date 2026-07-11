package io.legado.app.web.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTextSanitizerTest {

    @Test
    fun removesEmbeddedImagePayloadAndKeepsText() {
        val content = "开头<img src=\"data:image/svg+xml;base64,${"A".repeat(20_000)}\">结尾"

        val result = McpTextSanitizer.forModel(content)

        assertEquals("开头结尾", result)
        assertFalse(result.contains("base64"))
        assertTrue(result.length < 100)
    }

    @Test
    fun keepsAltTextAndUsesPlaceholderForRemoteImage() {
        val content = "甲<img alt='表情' src='data:image/png;base64,AAAA'>乙<img src='https://a/b.jpg'>丙"

        assertEquals("甲表情乙[图片]丙", McpTextSanitizer.forModel(content))
    }

    @Test
    fun chapterWindowEndUsesInclusiveDaoBoundary() {
        assertEquals(11, mcpInclusiveChapterEnd(start = 0, chapterCount = 12))
        assertEquals(37, mcpInclusiveChapterEnd(start = 26, chapterCount = 12))
    }
}
