package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StringUtilsTest {

    @Test
    fun contentWordCountIgnoresEveryImageStyleBeforeLayout() {
        for (style in listOf("FULL", "TEXT", "text")) {
            val content = "正文<img src=\"data:image/svg+xml;base64,AAAA,{\"style\":\"$style\"}\">结束"
            assertEquals(4, StringUtils.contentWordCount(content))
        }
    }

    @Test
    fun imageOnlyParagraphsAndRepeatedReviewImagesDoNotAddWords() {
        val review = "<img src=\"review.svg,{\"style\":\"TEXT\"}\">"
        assertEquals(0, StringUtils.contentWordCount(review.repeat(66)))
        assertEquals(4, StringUtils.contentWordCount("正文" + review.repeat(66) + "结束"))
    }

    @Test
    fun realTextThatResemblesAnInternalPlaceholderIsStillCounted() {
        assertEquals(4, StringUtils.contentWordCount("文字袮꧁"))
    }

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
