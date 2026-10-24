package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ReadHighlightRuleSerializationTest {

    @Test
    fun turningOffFollowInitializesMissingNightColorsAndFreezesDayValues() {
        val day = ReadHighlightRule(textColor = 17, bgColor = null, underlineColor = 0)
        val night = day.withNightFollowDay(false)
        assertFalse(night.followsDayAtNight())
        assertEquals(ReadHighlightColors(17, null, 0), night.colors(true))
        assertEquals(ReadHighlightRule.NIGHT_DEFAULT, night.nightColorMode(ReadHighlightRule.COLOR_BACKGROUND))
        assertEquals(night.colors(true), night.copy(textColor = 34, bgColor = 51, underlineColor = 68).colors(true))
        assertEquals(day.colors(false), night.colors(false))
        assertEquals(night, GSON.fromJson(GSON.toJson(night), ReadHighlightRule::class.java))
    }

    @Test
    fun turningOffFollowKeepsPartialLegacyColorsAndOnlyInitializesMissingValues() {
        val legacy = GSON.fromJson(
            """{"textColor":17,"textColorNight":0,"bgColor":51,"underlineColor":68}""",
            ReadHighlightRule::class.java,
        )
        val night = legacy.withNightFollowDay(false)
        assertEquals(ReadHighlightColors(0, 51, 68), night.colors(true))
        assertEquals(night.colors(true), night.copy(bgColor = 85, underlineColor = 102).colors(true))
        assertEquals(legacy.colors(false), night.colors(false))
    }

    @Test
    fun followRoundTripKeepsExistingColorsAndExplicitlyDisabledSlots() {
        val night = ReadHighlightRule(
            textColor = 17, bgColor = 34, underlineColor = 51,
            textColorNight = 68, bgColorNight = 85, underlineColorNight = 102,
            nightFollowDay = false,
            bgColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            underlineColorNightMode = ReadHighlightRule.NIGHT_FOLLOW_DAY,
        )
        val following = night.withNightFollowDay(true)
        assertEquals(night.colors(false), following.colors(true))
        val restored = following.withNightFollowDay(false)
        assertEquals(ReadHighlightColors(68, null, 102), restored.colors(true))
        assertEquals(85, restored.bgColorNight)
        assertEquals(night.colors(false), restored.colors(false))
    }

    @Test
    fun disabledNightColorStaysOffAfterGlobalFollowRoundTrip() {
        val night = ReadHighlightRule(textColor = 17).withNightFollowDay(false)
            .withColor(ReadHighlightRule.COLOR_TEXT, null, true)
        val restored = night.withNightFollowDay(true).withNightFollowDay(false)
        assertNull(restored.colors(true).textColor)
        assertEquals(ReadHighlightRule.NIGHT_DEFAULT, restored.nightColorMode(ReadHighlightRule.COLOR_TEXT))
        assertEquals(17, restored.textColorNight)
        assertEquals(17, restored.colors(false).textColor)
    }

    @Test
    fun singlePaletteRulesFollowDayWithoutFreezingColors() {
        val rule = GSON.fromJson("""{"textColor":17,"bgColor":51}""", ReadHighlightRule::class.java)
        assertTrue(rule.followsDayAtNight())
        assertEquals(rule.colors(false), rule.colors(true))
        val edited = rule.copy(textColor = 34, bgColor = null)
        assertEquals(34, edited.colors(true).textColor)
        assertNull(edited.colors(true).bgColor)
        assertTrue(ReadHighlightRule().followsDayAtNight())
    }

    @Test
    fun partialLegacyNightPaletteKeepsPerColorInheritance() {
        val rule = GSON.fromJson(
            """{"textColor":17,"textColorNight":0,"bgColor":51,"bgColorNight":null,"underlineColor":68}""",
            ReadHighlightRule::class.java,
        )
        assertFalse(rule.followsDayAtNight())
        assertEquals(ReadHighlightColors(0, 51, 68), rule.colors(true))
        assertEquals(85, rule.copy(bgColor = 85).colors(true).bgColor)
        assertEquals(rule, GSON.fromJson(GSON.toJson(rule), ReadHighlightRule::class.java))
    }

    @Test
    fun nightDefaultIsDifferentFromFollowingDayAndKeepsRememberedColors() {
        val rule = ReadHighlightRule(
            textColor = 17, bgColor = 34, underlineColor = 51,
            textColorNight = 68, bgColorNight = 85, underlineColorNight = 102,
            nightFollowDay = false,
            textColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            bgColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            underlineColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
        )
        assertEquals(ReadHighlightColors(null, null, null), rule.colors(true))
        val following = rule.copy(nightFollowDay = true)
        assertEquals(rule.colors(false), following.colors(true))
        assertEquals(85, following.bgColorNight)
        val restored = following.copy(nightFollowDay = false)
            .withNightColorMode(ReadHighlightRule.COLOR_BACKGROUND, ReadHighlightRule.NIGHT_CUSTOM)
        assertEquals(85, restored.colors(true).bgColor)
        assertEquals(restored, GSON.fromJson(GSON.toJson(restored), ReadHighlightRule::class.java))
    }

    @Test
    fun allNightFieldsUseStableNamesAndSurvivePresetTransport() {
        val rule = ReadHighlightRule(
            textColor = 17, textColorNight = 0x33000000, bgColorNight = 0, underlineColorNight = 34,
            nightFollowDay = true, textColorNightMode = ReadHighlightRule.NIGHT_CUSTOM,
            bgColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            underlineColorNightMode = ReadHighlightRule.NIGHT_FOLLOW_DAY,
        )
        val fields = listOf("textColorNight", "bgColorNight", "underlineColorNight", "nightFollowDay",
            "textColorNightMode", "bgColorNightMode", "underlineColorNightMode")
        val json = GSON.toJson(rule)
        fields.forEach { assertTrue(json.contains("\"$it\":")) }
        val config = ReadBookConfig.Config(highlightRules = arrayListOf(rule))
        assertEquals(rule, GSON.fromJson(GSON.toJson(config), ReadBookConfig.Config::class.java).highlightRules.single())
    }

    @Test
    fun invalidModesConservativelyFollowDayWithoutDiscardingSavedColor() {
        val rule = ReadHighlightRule(textColor = 17, textColorNight = 34, nightFollowDay = false,
            textColorNightMode = 99, bgColorNightMode = ReadHighlightRule.NIGHT_CUSTOM)
        assertTrue(rule.hasInvalidNightColorModes())
        assertEquals(17, rule.normalized().colors(true).textColor)
        assertEquals(34, rule.normalized().textColorNight)
        assertNull(rule.normalized().colors(true).bgColor)
        assertFalse(rule.normalized().hasInvalidNightColorModes())
    }

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
