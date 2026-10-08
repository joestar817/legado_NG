package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadHighlightRuleSerializationTest {

    @Test
    fun rulesWithoutCrossParagraphFieldKeepParagraphMatching() {
        val rule = GSON.fromJson(
            """{"id":"old","pattern":"^正文$","textColor":17}""",
            ReadHighlightRule::class.java,
        )
        assertFalse(rule.matchAcrossParagraphs)
        assertFalse(rule.normalized().matchAcrossParagraphs)
        assertEquals("^正文$", rule.pattern)
        assertEquals(17, rule.textColor)
    }

    @Test
    fun crossParagraphSettingUsesStableSerializedFieldAndRoundTrips() {
        val rule = ReadHighlightRule(
            id = "cross",
            pattern = "“[^”]{1,1200}”",
            matchAcrossParagraphs = true,
            sampleText = "“第一段\n第二段”",
            textColor = 17,
        )
        val json = GSON.toJson(rule)
        assertTrue(json.contains("\"matchAcrossParagraphs\":true"))
        assertEquals(rule, GSON.fromJson(json, ReadHighlightRule::class.java))
        assertTrue(rule.normalized().matchAcrossParagraphs)
    }
}
