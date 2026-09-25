package io.legado.app.model.epub

import org.junit.Assert.*
import org.junit.Test

class EpubMissingResourceTest {
    private fun archive(extra: String = "", chapterPresent: Boolean = true): EpubArchive {
        val files = linkedMapOf(
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""",
            "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata/>
                <manifest><item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/>$extra</manifest>
                <spine><itemref idref="c"/></spine></package>""",
        )
        if (chapterPresent) files["chapter.xhtml"] = "<html><body><p>正文<img src='missing.png'/></p></body></html>"
        return object : EpubArchive {
            override val entries = files.mapValues { (path, content) ->
                val size = content.toByteArray().size.toLong()
                EpubArchiveEntry(path, size, size)
            }
            override fun open(path: String) = files.getValue(path).byteInputStream()
            override fun close() = Unit
        }
    }

    @Test
    fun missingImagesAndFontsRetainUsableChapterAndManifest() {
        val extras = """<item id="image" href="missing.png" media-type="image/png"/>
            <item id="font" href="missing.woff" media-type="font/woff"/>"""
        EpubPublicationSession.open(archive(extras)).use { session ->
            assertEquals(3, session.publication.manifest.size)
            assertEquals(2, session.publication.warnings.count { it.startsWith("Missing manifest resource:") })
            session.openResource("chapter.xhtml").data.use { assertTrue(it.readBytes().toString(Charsets.UTF_8).contains("正文")) }
            for (path in listOf("missing.png", "missing.woff")) {
                val error = assertThrows(EpubFormatException::class.java) { session.openResource(path) }
                assertTrue(error.message!!.contains(path))
            }
        }
    }

    @Test
    fun missingRequestedChapterStillFailsWithItsPath() {
        EpubPublicationSession.open(archive(chapterPresent = false)).use { session ->
            val error = assertThrows(EpubFormatException::class.java) { session.openResource("chapter.xhtml") }
            assertTrue(error.message!!.contains("chapter.xhtml"))
        }
    }

    @Test
    fun unusedResourceCannotEscapeArchive() {
        assertThrows(EpubFormatException::class.java) {
            EpubPackageParser().parseLayout(archive("""<item id="bad" href="../outside.png" media-type="image/png"/>"""))
        }
    }

    @Test
    fun duplicateIdsAndFallbackCyclesRemainRejected() {
        for (extra in listOf(
            """<item id="c" href="missing.png" media-type="image/png"/>""",
            """<item id="a" href="a.png" media-type="image/png" fallback="b"/>
                <item id="b" href="b.png" media-type="image/png" fallback="a"/>""",
        )) assertThrows(EpubFormatException::class.java) { EpubPackageParser().parseLayout(archive(extra)) }
    }
}
