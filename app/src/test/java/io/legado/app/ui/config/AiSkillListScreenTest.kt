package io.legado.app.ui.config

import io.legado.app.help.ai.AiSkillDefinition
import io.legado.app.help.ai.AiSkillScope
import org.junit.Assert.assertSame
import org.junit.Test

class AiSkillListScreenTest {

    private val skill = AiSkillDefinition(
        id = "book_scan",
        name = "AI 扫书",
        summary = "多文件 System Workflow",
        scope = AiSkillScope.AGENT,
        prompt = "prompt",
        builtIn = true,
    )

    @Test
    fun `list actions retain the visible skill instance`() {
        val item = AiSkillListItemUiModel(
            skill = skill,
            name = skill.name,
            summary = skill.summary,
            iconText = "A",
            headerTags = emptyList(),
        )

        assertSame(skill, item.skill)
        assertSame(skill, AiSkillListAction.OpenSkill(item.skill).skill)
        assertSame(skill, AiSkillListAction.DeleteSkill(item.skill).skill)
    }
}
