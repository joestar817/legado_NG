package io.legado.app.help.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReadHighlightRuleStoreTest {

    @Test
    fun legacyNightColorsAreIgnoredByTheSinglePaletteModel() {
        val rule = GSON.fromJson(
            """{"textColor":17,"textColorNight":34,"bgColorNight":51,"underlineColorNight":68}""",
            ReadHighlightRule::class.java,
        )

        assertEquals(17, rule.textColor)
        val serialized = GSON.toJson(rule)
        assertFalse(serialized.contains("textColorNight"))
        assertFalse(serialized.contains("bgColorNight"))
        assertFalse(serialized.contains("underlineColorNight"))
    }

    @Test
    fun migrationUsesSelectedPresetFirstAndDeduplicatesRules() {
        val selected = ReadHighlightRule(id = "shared", name = "当前", pattern = "current")
        val sameContentWithAnotherId = selected.copy(id = "duplicate")
        val other = ReadHighlightRule(id = "other", name = "其它", pattern = "other")

        val migrated = migrateLegacyReadHighlightRules(
            ruleGroups = listOf(
                listOf(selected.copy(name = "旧版本", pattern = "old"), other),
                listOf(selected, sameContentWithAnotherId),
            ),
            preferredIndex = 1,
        )

        assertEquals(listOf("shared", "other"), migrated.map(ReadHighlightRule::id))
        assertEquals("current", migrated.first().pattern)
        assertEquals(listOf(0, 1), migrated.map(ReadHighlightRule::position))
    }

    @Test
    fun independentImportUpdatesMatchingIdsAndAppendsNewRules() {
        val existing = ReadHighlightRule(id = "same", name = "旧", pattern = "old")
        val updated = existing.copy(name = "新", pattern = "new")
        val added = ReadHighlightRule(id = "added", name = "新增", pattern = "added")

        val result = mergeReadHighlightRules(
            existing = listOf(existing),
            incoming = listOf(updated, added),
            replaceMatchingIds = true,
        )

        assertEquals(1, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(listOf("new", "added"), result.rules.map(ReadHighlightRule::pattern))
    }

    @Test
    fun presetImportDoesNotOverwriteExistingGlobalRule() {
        val existing = ReadHighlightRule(id = "same", name = "保留", pattern = "existing")
        val imported = existing.copy(name = "导入", pattern = "imported")

        val result = mergeReadHighlightRules(
            existing = listOf(existing),
            incoming = listOf(imported),
            replaceMatchingIds = false,
        )

        assertEquals(0, result.addedCount)
        assertEquals(0, result.updatedCount)
        assertEquals(1, result.skippedCount)
        assertEquals("existing", result.rules.single().pattern)
    }

    @Test
    fun restoreBuiltInRulesResetsAndAddsDefaultsWithoutRemovingCustomRules() {
        val editedBuiltIn = ReadHighlightRule(
            id = "built-in-one",
            name = "修改过的内置",
            pattern = "edited",
            position = 1,
        )
        val custom = ReadHighlightRule(
            id = "custom",
            name = "自定义",
            pattern = "custom",
            position = 0,
        )
        val defaults = listOf(
            editedBuiltIn.copy(name = "内置一", pattern = "default", position = 0),
            ReadHighlightRule(id = "built-in-two", name = "内置二", pattern = "second"),
        )

        val result = restoreBuiltInReadHighlightRules(
            existing = listOf(custom, editedBuiltIn),
            builtIn = defaults,
        )

        assertEquals(1, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(listOf("custom", "built-in-one", "built-in-two"), result.rules.map { it.id })
        assertEquals("default", result.rules[1].pattern)
        assertEquals(listOf(0, 1, 2), result.rules.map { it.position })
    }
}
