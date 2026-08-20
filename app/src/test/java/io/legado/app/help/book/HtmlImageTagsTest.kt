package io.legado.app.help.book

import org.junit.Assert.assertEquals
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
}
