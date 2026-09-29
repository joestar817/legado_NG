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
    fun mappingRevisionChangesOnlyWithPayloadAndDocumentQueryStillRoutes() {
        EpubPublicationSession.open(archive()).use { session ->
            EpubResourceGateway(session, readerRuntime = true).use { gateway ->
                gateway.setContent("chapter.xhtml", "<p>text</p>", "first")
                val first = gateway.contentUrl()
                gateway.setContent("chapter.xhtml", "<p>text</p>", "first")
                assertEquals(first, gateway.contentUrl())
                gateway.setContent("chapter.xhtml", "<p>text</p>", "second")
                assertNotEquals(first, gateway.contentUrl())
                gateway.setDocumentContents(listOf(Triple("chapter-key", "chapter.xhtml", "<p>text</p>" to "child")))
                val child = gateway.contentUrl("chapter-key")
                gateway.serve(child, "GET", false).data.use { assertEquals("child", it.reader().readText()) }
                gateway.setDocumentContents(listOf(Triple("chapter-key", "chapter.xhtml", "<p>text</p>" to "child")))
                assertEquals(child, gateway.contentUrl("chapter-key"))
            }
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

    @Test
    fun offlineRendererRejectsForeignOriginsAndNonHttpsWithoutNetworkFallback() {
        EpubPublicationSession.open(archive()).use { session ->
            EpubResourceGateway(session, readerRuntime = true).use { gateway ->
                EpubResourceGateway(session, readerRuntime = true).use { other ->
                    val local = gateway.prepareDocument(EpubResourceLink("chapter.xhtml"))
                    assertEquals(200, gateway.serve(local, "GET", true).also { it.data.close() }.status)
                    val host = java.net.URI(gateway.origin).host
                    for (url in listOf(
                        "https://example.com/chapter.xhtml", "${other.origin}/chapter.xhtml",
                        "http://$host/chapter.xhtml", "https://user@$host/chapter.xhtml",
                        "https://$host:443/chapter.xhtml", "file:///chapter.xhtml",
                        "content://book/chapter.xhtml", "${gateway.origin}/%2e%2e/outside.xhtml",
                    )) for (mainFrame in listOf(false, true)) {
                        val response = gateway.serve(url, "GET", mainFrame)
                        response.data.use { assertEquals("$url / main=$mainFrame", 403, response.status) }
                        assertTrue(response.headers.getValue("Content-Security-Policy").contains("script-src 'none'"))
                    }
                }
            }
        }
    }

    @Test
    fun offlineRendererErrorsRetainCspAndNeverBecomeNetworkFallback() {
        val extra = """<item id="font" href="missing.woff" media-type="font/woff"/>"""
        EpubPublicationSession.open(archive(extra)).use { session ->
            EpubResourceGateway(session, readerRuntime = true).use { gateway ->
                val local = gateway.prepareDocument(EpubResourceLink("chapter.xhtml"))
                val responses = listOf(
                    gateway.serve("${gateway.origin}/unknown.png", "GET", false) to 404,
                    gateway.serve("${gateway.origin}/missing.woff", "GET", false) to 422,
                    gateway.serve(local, "POST", true) to 405,
                )
                gateway.close()
                for ((response, status) in responses + (gateway.serve(local, "GET", true) to 410)) {
                    response.data.use { assertEquals(status, response.status) }
                    val csp = response.headers.getValue("Content-Security-Policy")
                    assertTrue(csp.contains("default-src 'none'"))
                    assertTrue(csp.contains("script-src 'none'"))
                    assertTrue(csp.contains("base-uri 'none'"))
                    assertTrue(csp.contains("form-action 'none'"))
                }
            }
        }
    }
}
