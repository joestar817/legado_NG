package io.legado.app.model.epub

import io.legado.app.help.book.ContentPositionMap
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubExportedTitleProjectionTest {
    @Test
    fun matchingExportedTitleKeepsBothOriginalSpansAndTheirUtf16Coordinates() {
        val fixture = project("第100章", " 韩一墨的担忧", listOf("第100章", "韩一墨的担忧"))
        assertEquals(listOf("第100章", " 韩一墨的担忧"), fixture.segments.map { it.text })
        assertFalse(fixture.segments.any { it.preserveBreaks })
        assertCoordinates(fixture)
        assertEquals("第100章\n韩一墨的担忧", fixture.mapped.text.substringBefore("\n　　正文"))
        assertEquals(6, fixture.segments[1].starts[1])
        assertTrue(fixture.mapped.documents.single().source.html.contains("font-size:125%"))
    }

    @Test
    fun punctuationSurrogatePairsAndExtraWhitespaceKeepExactNativeCharacterPositions() {
        val fixture = project("第🌙章！", "\t　 （🙂）“破灾”：A&B？！", listOf("第🌙章！", "（🙂）“破灾”：A&B？！"))
        assertEquals(listOf("第🌙章！", "\t　 （🙂）“破灾”：A&B？！"), fixture.segments.map { it.text })
        assertCoordinates(fixture)
        val surrogateIndexes = fixture.segments.flatMap { node -> node.text.indices.filter { node.text[it].isSurrogate() }.map { node.starts[it] } }
        assertEquals(4, surrogateIndexes.size)
        assertEquals(4, surrogateIndexes.toSet().size)
    }

    @Test
    fun nativeSegmentationMayChangeWithoutMovingOriginalAuthoredSpanOwners() {
        val fixture = project("第100章", " 韩一墨的担忧", listOf("第100章 韩一墨的担忧"))
        assertEquals(listOf("第100章", " 韩一墨的担忧"), fixture.segments.map { it.text })
        assertCoordinates(fixture)
        assertEquals(6, fixture.segments[1].starts[1])
    }

    @Test
    fun ordinaryEpubOrMissingOwnedHeadingUsesTheOriginalReplacementPath() {
        listOf(project("第100章", " 韩一墨的担忧", listOf("第100章", "韩一墨的担忧"), admitted = false),
            project("第100章", " 韩一墨的担忧", listOf("第100章", "韩一墨的担忧"), ownedHeading = false)).forEach { fixture ->
            assertEquals("第100章\n韩一墨的担忧", fixture.segments.first().text)
            assertEquals("", fixture.segments.last().text)
            assertCoordinates(fixture)
        }
    }

    @Test
    fun changedNativeTitleNeverBypassesExistingReplacementSemantics() {
        val fixture = project("第100章", " 韩一墨的担忧", listOf("第100章", "已修改标题！"))
        assertEquals("第100章\n已修改标题！", fixture.segments.first().text)
        assertEquals("", fixture.segments.last().text)
        assertCoordinates(fixture)
        assertTrue(fixture.mapped.text.contains("正文&标点。"))
    }

    private data class Fixture(val mapped: EpubMappedContent, val segments: List<EpubContentProjection.ProjectedNode>)

    private fun project(first: String, second: String, nativeTitle: List<String>, admitted: Boolean = true,
                        ownedHeading: Boolean = true): Fixture {
        fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val marker = if (ownedHeading) "class=\"ng-read-title head\" data-ng-read-title=\"1\"" else "class=\"head\""
        val html = "<html><body><h2 $marker><span class=\"ng-title-segment\" style=\"font-size:100%\">${xml(first)}</span>" +
            "<span class=\"ng-title-segment\" style=\"font-size:125%\">${xml(second)}</span></h2><p>正文&amp;标点。</p></body></html>"
        val capture = EpubSourceCapture()
        val body = EpubBodyReader.body("OEBPS/Text/chapter_0.html", { html }, null, null, false, capture)
        EpubBodyReader.format(Elements(body), false, capture)
        val source = requireNotNull(capture.result)
        val prepared = ContentPositionMap(source.content)
        val bodyStart = source.content.indexOf("正文").let { at ->
            require(at >= 0)
            source.content.lastIndexOf('\n', at).takeIf { it >= 0 }?.plus(1) ?: at
        }
        prepared.removedTitle = prepared.sourceRange(0, bodyStart)
        prepared.slice(prepared.text, bodyStart, display = false)
        val paragraphs = prepared.text.split('\n')
        var position = 0
        val lines = nativeTitle.map { title ->
            EpubContentLine(position, -1, title, true, false, true).also { position += title.length + 1 }
        } + paragraphs.mapIndexed { index, paragraph ->
            EpubContentLine(position, index, paragraph, false, false, true).also { position += paragraph.length + 1 }
        }
        val mapped = EpubContentProjection(source, prepared, preserveExportedTitleOwners = admitted).bindNativeContent(paragraphs, lines)
        val original = Jsoup.parse(source.documents.single().html)
        val ids = original.select(".ng-title-segment").map { it.attr(EpubSourceCapture.ATTRIBUTE) }
        return Fixture(mapped, ids.map { id -> mapped.documents.single().nodes.single { it.element == id } })
    }

    private fun assertCoordinates(fixture: Fixture) {
        val positions = HashSet<Int>()
        val represented = StringBuilder()
        fixture.mapped.documents.flatMap { it.nodes }.forEach { node ->
            node.text.forEachIndexed { index, char ->
                if (!char.isWhitespace()) {
                    val position = node.starts[index]
                    assertTrue("Unbound native title/body character: $char", position >= 0)
                    assertEquals(char, fixture.mapped.text[position])
                    assertEquals(position + 1, node.ends[index])
                    assertTrue("Duplicate native character at $position", positions.add(position))
                    represented.append(char)
                }
            }
        }
        assertEquals(fixture.mapped.text.filterNot(Char::isWhitespace), represented.toString())
    }
}
