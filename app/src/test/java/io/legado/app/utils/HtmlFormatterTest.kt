package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URL

class HtmlFormatterTest {

    @Test
    fun emptyImageDoesNotBecomeTheRedirectPageUrl() {
        assertEquals(
            "前文后文",
            HtmlFormatter.formatKeepImg(
                "前文<img src=\"\">后文",
                URL("https://example.com/chapter/1"),
            ),
        )
    }

    @Test
    fun relativeAndInteractiveImagesRemainFormatted() {
        assertEquals(
            "前文<img src=\"https://example.com/chapter/cover.jpg\">后文",
            HtmlFormatter.formatKeepImg(
                "前文<img src=\"cover.jpg\">后文",
                URL("https://example.com/chapter/1"),
            ),
        )
        val bubble =
            "<img src=\"data:image/svg+xml;base64,PHN2Zz4=,{\"click\":\"showComments()\",\"style\":\"TEXT\"}\">"
        assertEquals(bubble, HtmlFormatter.formatKeepImg(bubble))
    }
}
