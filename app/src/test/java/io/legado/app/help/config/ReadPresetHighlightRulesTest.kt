package io.legado.app.help.config

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadPresetHighlightRulesTest {
    private val global = ReadHighlightRule(id = "global", name = "全局", pattern = "global", enabled = true)
    private val linked = ReadHighlightRule(id = "linked", name = "关联", pattern = "linked", enabled = false, position = 1)

    @Test
    fun linkedPresetsUseTheirRulesAndUnlinkedPresetUsesGlobalRules() {
        val rules = listOf(global, linked)
        assertEquals(listOf("linked"), resolvePresetHighlightRules(rules, listOf("linked"), true).map { it.id })
        assertTrue(resolvePresetHighlightRules(rules, listOf("linked"), true).single().enabled)
        assertEquals(listOf("global"), resolvePresetHighlightRules(rules, null, false).map { it.id })
        assertEquals(listOf("global"), resolvePresetHighlightRules(rules, listOf("missing"), true).map { it.id })
        assertFalse(rules[1].enabled)
    }

    @Test
    fun presetImportDoesNotReplaceOrGloballyEnableRules() {
        val incoming = linked.copy(id = global.id)
        val result = mergePresetHighlightRules(listOf(global), listOf(incoming)) { "replacement" }
        assertEquals("replacement", result.idMapping[global.id])
        assertEquals(global, result.merge.rules.first())
        assertEquals("linked", result.merge.rules.last().pattern)
        assertFalse(result.merge.rules.last().enabled)
    }

    @Test
    fun sameRuleIsReusedRegardlessOfGlobalEnablement() {
        val result = mergePresetHighlightRules(listOf(linked), listOf(linked.copy(id = "other", enabled = true)))
        assertEquals("linked", result.idMapping["other"])
        assertEquals(1, result.merge.rules.size)
        assertFalse(result.merge.rules.single().enabled)
    }

    @Test
    fun linkedRuleIdsSurvivePresetSerializationAndBookCopy() {
        val preset = ReadBookConfig.Config(linkedHighlightRuleIds = listOf("a", "b"), useLinkedHighlightRules = true)
        val restored = Gson().fromJson(Gson().toJson(preset), ReadBookConfig.Config::class.java)
        assertEquals(listOf("a", "b"), restored.linkedHighlightRuleIds)
        assertTrue(restored.useLinkedHighlightRules)
        assertEquals(restored.linkedHighlightRuleIds, restored.copyForBook().linkedHighlightRuleIds)
    }
}
