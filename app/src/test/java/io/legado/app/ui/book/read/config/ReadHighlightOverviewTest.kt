package io.legado.app.ui.book.read.config

import io.legado.app.help.config.ReadHighlightRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadHighlightOverviewTest {
    private val global = ReadHighlightRule(id = "global", name = "Global", pattern = "global", enabled = true)
    private val linked = ReadHighlightRule(id = "linked", name = "Linked", pattern = "linked", enabled = false)
    private val rules = listOf(global, linked)

    @Test
    fun linkedPresetUsesItsRulesIndependentlyOfGlobalSwitches() {
        val state = readHighlightOverview(rules, listOf("linked"), useLinkedRules = true)
        assertTrue(state.usesLinkedRules)
        assertEquals(setOf("linked"), state.linkedRuleIds)
        assertEquals(setOf("linked"), state.usedRuleIds)
    }

    @Test
    fun followGlobalKeepsLinksButShowsOnlyEnabledGlobalRules() {
        val state = readHighlightOverview(rules, listOf("linked"), useLinkedRules = false)
        assertFalse(state.usesLinkedRules)
        assertEquals(setOf("linked"), state.linkedRuleIds)
        assertEquals(setOf("global"), state.usedRuleIds)
    }

    @Test
    fun missingLinksFallBackAndDoNotCountAsLinked() {
        val state = readHighlightOverview(rules, listOf("missing", "", "missing"), useLinkedRules = true)
        assertFalse(state.usesLinkedRules)
        assertTrue(state.linkedRuleIds.isEmpty())
        assertEquals(setOf("global"), state.usedRuleIds)
    }

    @Test
    fun missingOrRepeatedIdsDoNotChangeValidAssociation() {
        val state = readHighlightOverview(rules, listOf("missing", "linked", "linked"), useLinkedRules = true)
        assertTrue(state.usesLinkedRules)
        assertEquals(setOf("linked"), state.linkedRuleIds)
        assertEquals(setOf("linked"), state.usedRuleIds)
    }

    @Test
    fun emptyLibraryDoesNotPretendAnyRuleIsActive() {
        val state = readHighlightOverview(emptyList(), listOf("linked"), useLinkedRules = true)
        assertFalse(state.usesLinkedRules)
        assertTrue(state.linkedRuleIds.isEmpty())
        assertTrue(state.usedRuleIds.isEmpty())
    }
}
