package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlImageTagsTest {

    @Test
    fun removesEmptyFormattedImageWithoutRemovingSurroundingText() {
        assertEquals(
            "前文后文",
            HtmlImageTags.removeEmptySources("前文<img src=\"\">后文"),
        )
    }

    @Test
    fun removesEmptyImageEvenWhenItCarriesInteractionOptions() {
        assertEquals(
            "正文",
            HtmlImageTags.removeEmptySources(
                "正文<img src=\",{\"click\":\"showComments()\",\"style\":\"TEXT\"}\">"
            ),
        )
    }

    @Test
    fun keepsNormalAndInteractiveDataImages() {
        val normal = "<img src=\"https://example.com/cover.jpg\">"
        val bubble =
            "<img src=\"data:image/svg+xml;base64,PHN2Zz4=,{\"click\":\"showComments()\",\"style\":\"TEXT\"}\">"

        assertEquals(
            "前${normal}${bubble}后",
            HtmlImageTags.removeEmptySources("前${normal}${bubble}后"),
        )
    }

    @Test
    fun preservesImageTagLineBreaksWhileNormalizingSurroundingText() {
        val bubble =
            "<img src=\"https://example.com/comment,\r{'click':'showComments()','style':'TEXT'}\">"

        val result = HtmlImageTags.preserveDuringTextTransform(
            "  前文${bubble}  \r  后文  "
        ) {
            it.lines().joinToString("\n") { line -> line.trim() }
        }

        assertEquals("前文${bubble}\n后文", result)
    }

    @Test
    fun preservesInteractiveImageDuringResegment() {
        val bubble =
            """<img src="https://example.com/comment?para=1,{'click':'showComments("1")','style':'TEXT'}">"""

        val result = HtmlImageTags.preserveDuringTextTransform(
            "他说：“正文。”${bubble}\n下一段。"
        ) {
            ContentHelp.reSegment(it, "第一章")
        }

        assertTrue(result.contains(bubble))
        assertEquals(1, HtmlImageTags.anyImageTag.findAll(result).count())
    }

    @Test
    fun restoredImageRemainsAvailableToExplicitReplacement() {
        val image = "<img src=\"https://example.com/ad.jpg\">"
        val normalized = HtmlImageTags.preserveDuringTextTransform("正文${image}") { it }

        assertEquals("正文", normalized.replace(image, ""))
    }
}
