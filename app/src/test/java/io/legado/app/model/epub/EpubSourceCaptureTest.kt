package io.legado.app.model.epub

import io.legado.app.help.book.ContentPositionMap
import io.legado.app.help.book.EpubContentEntities
import org.jsoup.select.Elements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSourceCaptureTest {
    private fun capture(vararg documents: String): EpubSourceChapter {
        val capture = EpubSourceCapture()
        val plainBodies = Elements()
        val trackedBodies = Elements()
        documents.forEachIndexed { index, html ->
            val path = "Text/chapter$index.xhtml"
            plainBodies.add(EpubBodyReader.body(path, { html }, null, null, false, null))
            capture.sourceOccurrence = index
            trackedBodies.add(EpubBodyReader.body(path, { html }, null, null, false, capture))
        }
        val plain = EpubContentEntities.normalize(EpubBodyReader.format(plainBodies, false, null))
        EpubBodyReader.format(trackedBodies, false, capture)
        return checkNotNull(capture.result).also { source ->
            assertEquals("Observation must preserve the existing chapter text", plain, source.content)
            val prepared = ContentPositionMap(source.content)
            val paragraphs = prepared.text.split('\n')
            var offset = 0
            val lines = paragraphs.mapIndexed { index, paragraph ->
                val text = paragraph.replace(Regex("<img[^>]*>"), "袮")
                EpubContentLine(offset, index, text, false, false, true).also {
                    offset += text.length + 1
                }
            }
            val mapped = EpubContentProjection(source, prepared).bindNativeContent(paragraphs, lines)
            mapped.documents.forEach { document ->
                document.nodes.forEach { node ->
                    assertEquals(node.original, node.text)
                    node.text.forEachIndexed { index, char ->
                        val position = node.starts[index]
                        if (position >= 0 && !char.isWhitespace() && !char.isSurrogate()) {
                            assertEquals("Mapped character at $position", char, mapped.text[position])
                        }
                    }
                }
            }
        }
    }

    @Test
    fun namespaceTagsPreserveTextImagesAndCoordinates() {
        val source = capture("""<body><ops:switch xmlns:ops="http://www.idpf.org/2007/ops">
            <ops:case required-namespace="http://www.w3.org/2000/svg"><p>开头<b>中文</b>尾部</p>
            <img src="../Images/a.jpg"></ops:case><ops:default>备用文字</ops:default>
            </ops:switch></body>""")
        assertFalse(source.content.contains("data-ng-epub-source"))
        assertTrue(source.content.contains("<ops:switch"))
        assertTrue(source.content.contains("<img src=\"Images/a.jpg\">"))
        assertTrue(source.documents.single().html.contains("data-ng-epub-source"))
    }

    @Test
    fun attributeLikeProseAndCommentsAreNotTreatedAsMarkers() {
        val source = capture("""<body><!-- data-ng-epub-source="0-4" -->
            <ops:case title='literal data-ng-epub-source="0-4"'>
            <p>文字 data-ng-epub-source="0-4" &amp; 结尾</p></ops:case></body>""")
        assertTrue(source.content.contains("文字 data-ng-epub-source=\"0-4\" & 结尾"))
    }

    @Test
    fun multipleDocumentsKeepTheirIndependentOffsets() {
        val source = capture(
            "<body><ops:case>第一份<b>重复</b></ops:case></body>",
            "<body><ops:case><p>第二份重复</p><img src=\"b.png\"></ops:case></body>",
        )
        assertEquals(2, source.documents.size)
        assertFalse(source.content.contains("data-ng-epub-source"))
    }

    @Test
    fun deeplyNestedHtmlKeepsContentAndCoordinatesWithoutRecursion() {
        val source = capture("<body>" + "<div>".repeat(5000) +
            "Visible text" + "</div>".repeat(5000) + "</body>")
        assertTrue(source.content.contains("Visible text"))
    }

    @Test
    fun ordinaryHtmlKeepsExistingProjection() {
        capture("<body><p>甲<b>重复</b>乙 &amp; 丙</p><p>下一段<img src=\"a.jpg\"></p></body>")
    }
}
