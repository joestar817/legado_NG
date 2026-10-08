package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadHighlightRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadHighlightMatcherTest {

    private fun dialogueRule(color: Int = 1) = ReadHighlightRule(
        pattern = "“[^”]{1,1200}”",
        matchAcrossParagraphs = true,
        textColor = color,
    )

    @Test
    fun threeParagraphDialogueIsProjectedBackToEachParagraph() {
        val paragraphs = listOf("旁白：“第一段。", "  第二段。", "第三段。”旁白。")
        val text = paragraphs.joinToString("\n")
        val context = ReadHighlightContext(text)
        val matcher = ReadHighlightMatcher(listOf(dialogueRule()))
        val firstQuote = text.indexOf('“')
        val lastQuote = text.indexOf('”')
        var offset = 0
        paragraphs.forEach { paragraph ->
            val styles = matcher.match(paragraph, false, context, offset)
            paragraph.indices.forEach { index ->
                assertEquals(
                    "chapter offset ${offset + index}",
                    if (offset + index in firstQuote..lastQuote) 1 else null,
                    styles?.get(index)?.textColor,
                )
            }
            offset += paragraph.length + 1
        }
    }

    @Test
    fun ordinaryRulesKeepParagraphAnchors() {
        val rule = ReadHighlightRule(pattern = "^目标$", textColor = 2)
        val matcher = ReadHighlightMatcher(listOf(rule))
        val text = "目标\n旁白\n目标"
        val context = ReadHighlightContext(text)
        assertFalse(matcher.hasCrossParagraphRules)
        assertEquals(listOf(2, 2), matcher.match("目标", false, context, 6)?.map { it?.textColor })
        assertEquals(
            listOf(2, 2, null, null, null, null, 2, 2),
            matcher.matchSample(text, false)?.map { it?.textColor },
        )
    }

    @Test
    fun localAndCrossParagraphRulesKeepOriginalPrecedence() {
        val text = "“开头\n目标\n结尾”"
        val local = ReadHighlightRule(pattern = "目标", textColor = 2)
        val cross = dialogueRule()
        val context = ReadHighlightContext(text)
        val offset = text.indexOf("目标")
        val localLast = ReadHighlightMatcher(listOf(cross, local))
        val crossLast = ReadHighlightMatcher(listOf(local, cross))
        assertEquals(listOf(2, 2), localLast.match("目标", false, context, offset)?.map { it?.textColor })
        assertEquals(listOf(1, 1), crossLast.match("目标", false, context, offset)?.map { it?.textColor })
        assertEquals(2, localLast.matchSample(text, false)?.get(offset)?.textColor)
        assertEquals(1, crossLast.matchSample(text, false)?.get(offset)?.textColor)
    }

    @Test
    fun emptyMatchesAndInvalidPatternsDoNotCreateHighlights() {
        val matcher = ReadHighlightMatcher(
            listOf(
                dialogueRule().copy(pattern = "["),
                dialogueRule().copy(pattern = "(?=目)"),
                ReadHighlightRule(pattern = "^", textColor = 2),
            ),
        )
        assertNull(matcher.matchSample("目标\n目标", false))
        assertNull(matcher.match("目标", false))
        assertNull(matcher.matchSample("", false))
        val valid = ReadHighlightMatcher(listOf(dialogueRule().copy(pattern = "["), dialogueRule()))
        assertEquals(listOf(1, 1, 1), valid.match("“好”", false)?.map { it?.textColor })
    }

    @Test
    fun unclosedDialogueDoesNotHighlightFollowingParagraphs() {
        val matcher = ReadHighlightMatcher(listOf(dialogueRule()))
        assertNull(matcher.matchSample("旁白\n“没有结束的对白\n后续正文", false))
        assertNull(matcher.match("后续正文", false, ReadHighlightContext("后续正文")))
    }

    @Test
    fun acrossParagraphModeDoesNotImplicitlyEnableDotAllOrMultiline() {
        val text = "“第一段\n第二段”"
        val dot = dialogueRule().copy(pattern = "“.*?”")
        assertNull(ReadHighlightMatcher(listOf(dot)).matchSample(text, false))
        assertTrue(
            ReadHighlightMatcher(listOf(dot.copy(pattern = "(?s)“.*?”")))
                .matchSample(text, false)!!.all { it?.textColor == 1 },
        )
        assertTrue(
            ReadHighlightMatcher(listOf(dot)).matchSample("“第一段\\n第二段”", false)!!
                .all { it?.textColor == 1 },
        )
        val anchor = dialogueRule().copy(pattern = "^目标$")
        assertNull(ReadHighlightMatcher(listOf(anchor)).matchSample("旁白\n目标\n旁白", false))
        assertEquals(
            1,
            ReadHighlightMatcher(listOf(anchor.copy(pattern = "(?m)^目标$")))
                .matchSample("旁白\n目标\n旁白", false)?.get(3)?.textColor,
        )
    }

    @Test
    fun mediaBoundaryPreventsDialogueFromSpanningOpaqueContent() {
        val matcher = ReadHighlightMatcher(listOf(dialogueRule()))
        assertNull(matcher.matchSample("“前段\n\uFFFC\n后段”", false))
        val text = "“前段”\n\uFFFC\n“后段”"
        val styles = matcher.matchSample(text, false)
        assertEquals(1, styles?.first()?.textColor)
        assertEquals(1, styles?.last()?.textColor)
        assertNull(styles?.get(text.indexOf('\uFFFC')))
        assertNull(matcher.match("“前段\uFFFC后段”", false))
    }

    @Test
    fun titleAndBodyScopeAreRespectedWithIndependentContexts() {
        val titleOnly = dialogueRule().copy(targetScope = ReadHighlightRule.TARGET_TITLE)
        val bodyOnly = dialogueRule(2).copy(targetScope = ReadHighlightRule.TARGET_BODY)
        val matcher = ReadHighlightMatcher(listOf(titleOnly, bodyOnly))
        assertEquals(1, matcher.match("“标题”", true, ReadHighlightContext("“标题”"))?.first()?.textColor)
        assertEquals(2, matcher.match("“正文”", false, ReadHighlightContext("“正文”"))?.first()?.textColor)
        assertNull(matcher.match("“标题", true, ReadHighlightContext("“标题")))
        assertNull(matcher.match("正文”", false, ReadHighlightContext("正文”")))
    }

    @Test
    fun previewPreservesCrLfBlankLinesIndentationAndUtf16Offsets() {
        val text = "旁白\r\n　“甲😀\r\n\r\n　乙”\r结尾"
        val matcher = ReadHighlightMatcher(listOf(dialogueRule()))
        val styles = matcher.matchSample(text, false)!!
        assertEquals(text.length, styles.size)
        text.indices.forEach { index ->
            assertEquals(
                if (index in text.indexOf('“')..text.indexOf('”')) 1 else null,
                styles[index]?.textColor,
            )
        }
        val local = ReadHighlightMatcher(listOf(ReadHighlightRule(pattern = "^　乙”$", textColor = 2)))
        assertEquals(2, local.matchSample(text, false)?.get(text.indexOf('乙'))?.textColor)
    }

    @Test
    fun arbitrarySlicesRetainFullContextMatchesAcrossPageBoundaries() {
        val text = "旁白“甲\n乙\n丙”旁白"
        val matcher = ReadHighlightMatcher(listOf(dialogueRule()))
        val context = ReadHighlightContext(text)
        val expected = matcher.matchSample(text, false)
        for (start in text.indices) {
            for (end in start + 1..text.length) {
                val actual = matcher.match(text.substring(start, end), false, context, start)
                for (index in start until end) {
                    assertEquals(expected?.get(index), actual?.get(index - start))
                }
            }
        }
    }

    @Test
    fun absentContextStillMatchesAcrossNewlinesInTheProvidedInput() {
        val text = "“甲\n乙”"
        val matcher = ReadHighlightMatcher(listOf(dialogueRule()))
        assertTrue(matcher.hasCrossParagraphRules)
        assertTrue(matcher.match(text, false)!!.all { it?.textColor == 1 })
        assertNull(ReadHighlightMatcher(listOf(dialogueRule().copy(matchAcrossParagraphs = false)))
            .matchSample(text, false))
    }
}
