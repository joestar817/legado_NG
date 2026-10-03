package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2 shareLayout 退役迁移契约：shareConfig 排版字段写回每个预设，外观保留。
 */
class ShareLayoutMigrationTest {

    @Test
    fun `merge writes shared typography into every preset keeping appearance`() {
        val presets = listOf(
            ReadBookConfig.Config(
                name = "A", textSize = 16, textFont = "a.ttf", bgStr = "#111111",
                highlightRules = arrayListOf(ReadHighlightRule(id = "legacy-rule-1")),
            ),
            ReadBookConfig.Config(name = "B", textSize = 17, textFont = "b.ttf", bgStr = "#222222"),
        )
        val shared = ReadBookConfig.Config(textSize = 26, textFont = "shared.ttf", lineSpacingExtra = 19)

        val merged = mergeSharedLayoutIntoPresets(presets, shared)

        assertEquals(26, merged[0].textSize)
        assertEquals("shared.ttf", merged[0].textFont)
        assertEquals(19, merged[0].lineSpacingExtra)
        assertEquals("A", merged[0].name)
        assertEquals("#111111", merged[0].bgStr)
        // copyForBook 会清空 highlightRules，迁移必须保留（legacy 规则随后迁入 ReadHighlightRuleStore）
        assertEquals(listOf("legacy-rule-1"), merged[0].highlightRules.map { it.id })

        assertEquals(26, merged[1].textSize)
        assertEquals("shared.ttf", merged[1].textFont)
        assertEquals(19, merged[1].lineSpacingExtra)
        assertEquals("B", merged[1].name)
        assertEquals("#222222", merged[1].bgStr)
    }

    @Test
    fun `merge does not mutate source presets`() {
        val preset = ReadBookConfig.Config(name = "A", textSize = 16, bgStr = "#111111")
        val shared = ReadBookConfig.Config(textSize = 26, textFont = "shared.ttf")

        mergeSharedLayoutIntoPresets(listOf(preset), shared)

        assertEquals(16, preset.textSize)
        assertEquals("A", preset.name)
        assertEquals("#111111", preset.bgStr)
    }
}
