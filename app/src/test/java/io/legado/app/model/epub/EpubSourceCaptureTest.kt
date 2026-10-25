package io.legado.app.model.epub

import io.legado.app.help.book.ContentPositionMap
import io.legado.app.help.book.EpubContentEntities
import org.jsoup.select.Elements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubSourceCaptureTest {
    private fun captureBody(html: String, removeHeadings: Boolean = false, removeRuby: Boolean = false,
                            start: String? = null, end: String? = null): EpubSourceChapter {
        val capture = EpubSourceCapture()
        val path = "Text/chapter.xhtml"
        val plain = EpubBodyReader.body(path, { html }, start, end, removeHeadings, null)
        val tracked = EpubBodyReader.body(path, { html }, start, end, removeHeadings, capture)
        val expected = EpubContentEntities.normalize(EpubBodyReader.format(Elements(plain), removeRuby, null))
        EpubBodyReader.format(Elements(tracked), removeRuby, capture)
        return checkNotNull(capture.result).also { assertEquals(expected, it.content) }
    }

    private fun assertExactCharacters(source: EpubSourceChapter, expected: String) {
        val seen = HashSet<Triple<Int, Int, Int>>()
        val actual = StringBuilder()
        source.tokens.forEach { token ->
            token.decoded.forEachIndexed { index, char ->
                if (!char.isWhitespace()) {
                    val location = checkNotNull(token.characters[index]) { "Missing source character: $char" }
                    assertEquals(char, source.documents[token.document].nodes[location.node].text[location.offset])
                    assertTrue("Duplicate source character", seen.add(Triple(token.document, location.node, location.offset)))
                    actual.append(char)
                }
            }
        }
        assertEquals(expected, actual.toString())
    }

    private fun assertTitleProjection(source: EpubSourceChapter, title: String, expected: String) {
        val prepared = ContentPositionMap(source.content)
        val end = checkNotNull(Regex("^[\\s\u3000]*${Regex.escape(title)}[\\s\u3000]*").find(prepared.text)).range.last + 1
        prepared.removedTitle = prepared.sourceRange(0, end)
        prepared.slice(prepared.text, end, display = false)
        val paragraphs = prepared.text.split('\n')
        val lines = arrayListOf(EpubContentLine(0, -1, title, true, false, true))
        var position = title.length + 1
        paragraphs.forEachIndexed { index, text ->
            lines.add(EpubContentLine(position, index, text, false, false, true))
            position += text.length + 1
        }
        // bindNativeContent invokes the actual bindTitle replacement before final projection.
        val mapped = EpubContentProjection(source, prepared).bindNativeContent(paragraphs, lines)
        val nodes = mapped.documents.flatMap { it.nodes }
        assertEquals(expected, nodes.joinToString("") { it.text }.filterNot { it.isWhitespace() })
        val represented = nodes.flatMap { node ->
            node.text.mapIndexedNotNull { index, char ->
                val start = node.starts[index]
                if (start < 0 || char.isWhitespace()) null else {
                    assertEquals(char, mapped.text[start])
                    assertEquals(start + 1, node.ends[index])
                    start to char
                }
            }
        }.sortedBy { it.first }
        assertEquals(expected, mapped.text.filterNot { it.isWhitespace() })
        assertEquals(expected, represented.joinToString("") { it.second.toString() })
        assertEquals("Every native character must have exactly one source glyph", represented.size, represented.map { it.first }.toSet().size)
    }

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

    @Test
    fun leadingSerializationIndentCannotClaimOriginalTrailingWhitespace() {
        val source = captureBody("<body><p>目录</p><p>活着</p>\n</body>")
        val title = source.tokens.first { it.decoded.contains("目录") }
        val leading = source.tokens.filter { it.end <= title.start }
        assertTrue(leading.isNotEmpty())
        assertTrue(leading.all { token -> token.characters.all { it == null } })
        assertExactCharacters(source, "目录活着")
        assertTitleProjection(source, "目录", "目录活着")
    }

    @Test
    fun titleBindingKeepsInlineAndRepeatedBodyTextExactlyOnce() {
        val source = captureBody("<body><p>目<b>录</b></p><p>活着<b>活着</b>活着</p>\n</body>")
        assertExactCharacters(source, "目录活着活着活着")
        assertTitleProjection(source, "目录", "目录活着活着活着")
    }

    @Test
    fun removedElementsCanMergeOriginalDirectTextNodes() {
        val source = captureBody("""<body><p>目录</p>甲<h1>移除标题</h1>乙<span style="display:none">隐藏</span>丙
            <p><ruby>活<rt>huo</rt>着<rp>(</rp>活<!-- split -->着</ruby></p></body>""",
            removeHeadings = true, removeRuby = true)
        assertExactCharacters(source, "目录甲乙丙活着活着")
        assertFalse(source.content.contains("huo"))
        assertFalse(source.content.contains("隐藏"))
        assertFalse(source.content.contains("移除标题"))
    }

    @Test
    fun commentsInOneElementGapShareTheSourceCursor() {
        val source = captureBody("<body><p>甲<!-- one -->乙<!-- two -->丙<b>界</b>丁<!-- three -->戊</p></body>")
        assertExactCharacters(source, "甲乙丙界丁戊")
    }

    @Test
    fun fragmentBodyWithoutSourceIdentityDoesNotBorrowOriginalBodyText() {
        val source = captureBody("""<body>原前文<p id="start">目录</p><p>活着<b>中文</b></p>
            <p id="end">结束</p>原尾文
</body>""", start = "start", end = "end")
        assertExactCharacters(source, "目录活着中文")
        val mappedNodes = source.tokens.flatMap { token -> token.characters.filterNotNull().map {
            source.documents[token.document].nodes[it.node].text
        } }
        assertTrue(source.documents.single().nodes.any { it.text.contains("原前文") })
        assertTrue(mappedNodes.none { it.contains("原前文") || it.contains("原尾文") })
    }

    @Test
    fun nonWhitespaceChangesStillFailStrictSourceMatching() {
        val capture = EpubSourceCapture()
        val body = EpubBodyReader.body("Text/chapter.xhtml", { "<body><p>甲</p></body>" }, null, null, false, capture)
        body.selectFirst("p")!!.text("乙")
        assertThrows(IllegalStateException::class.java) { EpubBodyReader.format(Elements(body), false, capture) }
    }
}
