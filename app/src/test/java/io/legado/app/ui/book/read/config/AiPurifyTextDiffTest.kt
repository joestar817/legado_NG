package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiPurifyTextDiffTest {
    @Test
    fun replacementAndRemovalAreHighlightedAtTheirOwnLocations() {
        val original = "雨刚停，他像前走。【广告】门开了。"
        val cleaned = "雨刚停，他向前走。门开了。"
        val spans = aiPurifyTextDiff(original, cleaned)

        assertEquals(original, spans.joinToString("") { it.original })
        assertEquals(cleaned, spans.joinToString("") { it.cleaned })
        assertEquals(
            listOf(AiPurifyDiffSpan("像", "向"), AiPurifyDiffSpan("【广告】", "")),
            spans.filter { it.changed },
        )
    }

    @Test
    fun comparisonDoesNotSplitSupplementaryCharacters() {
        val diff = aiPurifyTextDiff("甲😀乙", "甲😃乙")

        assertEquals(listOf(AiPurifyDiffSpan("😀", "😃")), diff.filter { it.changed })
    }

    @Test
    fun comparisonDisplaysInsertionsAndUnchangedText() {
        val insertion = aiPurifyTextDiff("甲乙", "甲新乙")
        assertEquals(listOf(AiPurifyDiffSpan("", "新")), insertion.filter { it.changed })
        assertFalse(aiPurifyTextDiff("甲乙", "甲乙").single().changed)
    }

    @Test
    fun largeSelectionsKeepUnchangedContextAroundTheChangedWindow() {
        val prefix = "甲".repeat(3000)
        val suffix = "乙".repeat(3000)
        val old = "像".repeat(2500)
        val new = "向".repeat(2500)

        assertEquals(
            listOf(AiPurifyDiffSpan(prefix, prefix), AiPurifyDiffSpan(old, new), AiPurifyDiffSpan(suffix, suffix)),
            aiPurifyTextDiff(prefix + old + suffix, prefix + new + suffix),
        )
    }
}
