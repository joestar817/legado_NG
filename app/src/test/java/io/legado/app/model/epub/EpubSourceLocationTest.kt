package io.legado.app.model.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubSourceLocationTest {
    private fun document(href: String) = EpubSourceDocument(href, "", emptyList(), emptyList(), emptySet())

    @Test
    fun sourceHrefIsAnArchiveNameNotAnOpfRelativeUrl() {
        listOf("cover.xhtml", "OEBPS/Text/cover.xhtml", "EPUB/content/text/chapter.xhtml").forEach { path ->
            assertEquals(EpubResourceLink(path), document(path).location)
        }
    }

    @Test
    fun decodedSourceNamesKeepLiteralUrlCharacters() {
        listOf("OPS/第一 章.xhtml", "OPS/a+b.xhtml", "OPS/100%.xhtml", "OPS/literal%20.xhtml",
            "OPS/part#1.xhtml", "OPS/part?1.xhtml").forEach { path ->
            val location = document(path).location
            assertEquals(path, location.path)
            assertEquals(location, EpubPaths.resolve("mimetype", "/" + EpubPaths.encodePath(path)))
        }
    }

    @Test
    fun sourceNamesCannotEscapeTheArchive() {
        listOf("../cover.xhtml", "/cover.xhtml", "https://example.com/cover.xhtml").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) { document(path).location }
        }
    }
}
