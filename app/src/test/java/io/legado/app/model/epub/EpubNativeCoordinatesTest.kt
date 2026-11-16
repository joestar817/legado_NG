package io.legado.app.model.epub

import io.legado.app.constant.AppPattern
import io.legado.app.help.book.ContentEdit
import io.legado.app.help.book.ContentPositionMap
import org.jsoup.select.Elements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubNativeCoordinatesTest {
    private fun emptyProjection(paragraph: String): EpubContentProjection {
        val flattening = ContentPositionMap("")
        flattening.record("", paragraph, listOf(ContentEdit(0, 0, paragraph)), display = false)
        val source = EpubSourceChapter(emptyList(), "", flattening, emptyList())
        return EpubContentProjection(source, ContentPositionMap(paragraph))
    }

    @Test
    fun chapterHeadingImageAndDelayedIndentKeepTheirCoordinates() {
        val capture = EpubSourceCapture()
        val body = EpubBodyReader.body("OEBPS/Text/chapter.xhtml", {
            """<body><h2 class="chapter-title"><span class="title-pic"><img class="title-pic"
                src="../Images/title-pic.png"/></span><span class="chapter-title"><small>第1章：</small>明道宫</span></h2></body>"""
        }, null, null, false, capture)
        EpubBodyReader.format(Elements(body), false, capture)
        val source = checkNotNull(capture.result)
        val paragraph = source.content
        val image = AppPattern.imgPattern.matcher(paragraph)
        assertTrue(image.find())
        val prefix = paragraph.substring(0, image.start())
        val title = paragraph.substring(image.end())
        assertTrue(prefix.length >= 2 && prefix.all { it == '\u3000' })
        assertEquals("第1章：明道宫", title)
        val lines = listOf(
            EpubContentLine(0, 0, " ", false, true, false),
            EpubContentLine(1, 0, prefix + title, false, false, true),
        )
        val mapped = EpubContentProjection(source, ContentPositionMap(paragraph))
            .bindNativeContent(listOf(paragraph), lines)
        val document = mapped.documents.single()
        val media = document.media.single()
        assertTrue(media.visible)
        assertEquals(0, media.start)
        assertEquals(1, media.end)
        val characters = document.nodes.flatMap { node ->
            node.text.mapIndexedNotNull { index, char ->
                if (char.isWhitespace()) null else {
                    val start = node.starts[index]
                    assertTrue("Every title character must have a native position", start >= 0)
                    assertEquals(char, mapped.text[start])
                    assertEquals(start + 1, node.ends[index])
                    char
                }
            }
        }.joinToString("")
        assertEquals(title, characters)
    }

    @Test
    fun consecutiveBlockImagesKeepMixedDeferredWhitespaceInOrder() {
        val paragraph = "  <img src=\"a.png\">\u3000<img src=\"b.png\">正文"
        val mapped = emptyProjection(paragraph).bindNativeContent(listOf(paragraph), listOf(
            EpubContentLine(0, 0, " ", false, true, false),
            EpubContentLine(1, 0, " ", false, true, false),
            EpubContentLine(2, 0, "  \u3000正文", false, false, true),
        ))
        assertEquals("    \u3000正文\n", mapped.text)
    }

    @Test
    fun delayedIndentCanSpanMultipleNativeTextLines() {
        val paragraph = "\u3000\u3000<img src=\"a.png\">正文"
        val mapped = emptyProjection(paragraph).bindNativeContent(listOf(paragraph), listOf(
            EpubContentLine(0, 0, " ", false, true, false),
            EpubContentLine(1, 0, "\u3000", false, false, false),
            EpubContentLine(2, 0, "\u3000正文", false, false, true),
        ))
        assertEquals(" \u3000\u3000正文\n", mapped.text)
    }

    @Test
    fun inlineImagesAndImageOnlyParagraphsStillMatch() {
        val inline = "  <img src=\"a.png\">正文"
        val inlineMapped = emptyProjection(inline).bindNativeContent(listOf(inline), listOf(
            EpubContentLine(0, 0, "  袮正文", false, false, true),
        ))
        assertEquals("  袮正文\n", inlineMapped.text)

        val imageOnly = "\u3000\u3000<img src=\"a.png\">"
        val imageMapped = emptyProjection(imageOnly).bindNativeContent(listOf(imageOnly), listOf(
            EpubContentLine(0, 0, " ", false, true, true),
        ))
        assertEquals(" \n", imageMapped.text)
    }

    @Test
    fun blockImagesMatchWithNoIndentOrPrecedingText() {
        val noIndent = "<img src=\"a.png\">乙"
        val first = emptyProjection(noIndent).bindNativeContent(listOf(noIndent), listOf(
            EpubContentLine(0, 0, " ", false, true, false),
            EpubContentLine(1, 0, "乙", false, false, true),
        ))
        assertEquals(" 乙\n", first.text)

        val preceding = "甲 <img src=\"a.png\">乙"
        val second = emptyProjection(preceding).bindNativeContent(listOf(preceding), listOf(
            EpubContentLine(0, 0, "甲 ", false, false, false),
            EpubContentLine(2, 0, " ", false, true, false),
            EpubContentLine(3, 0, "乙", false, false, true),
        ))
        assertEquals("甲  乙\n", second.text)
    }

    @Test
    fun delayedIndentDoesNotHideDifferentText() {
        val paragraph = "\u3000\u3000<img src=\"a.png\">甲"
        assertThrows(IllegalArgumentException::class.java) {
            emptyProjection(paragraph).bindNativeContent(listOf(paragraph), listOf(
                EpubContentLine(0, 0, " ", false, true, false),
                EpubContentLine(1, 0, "\u3000\u3000乙", false, false, true),
            ))
        }
    }
}
