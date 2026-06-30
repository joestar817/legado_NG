package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StringUtilsTest {

    @Test
    fun contentWordCountIgnoresInlineSvgImageTags() {
        val svg = "data:image/svg+xml;base64," + "A".repeat(60_000)
        val content = "第3章 他真的好过分啊\n　　沈言卿的内心很不平静。" +
                "<img src=\"$svg\">" +
                "\n　　陈升继续往前走。"

        assertEquals(33, StringUtils.contentWordCount(content))
        assertEquals("33字", StringUtils.contentWordCountFormat(content))
    }
}
