package io.legado.app.help.ai

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiAgentSkillToolsTest {

    @Test
    fun pinnedSingleFileSkillIsLoadedThroughUseSkill() {
        val snapshot = AiActiveSkillSnapshot(
            skillId = "legacy_character_card",
            title = "角色卡生成",
            prompt = "只根据原文生成角色卡",
            contentHash = "pinned-hash"
        )

        assertNotNull(
            AiAgentSkillTools.toolDefinition(
                activeSkillId = snapshot.skillId,
                contentHash = snapshot.contentHash,
                activeSkill = snapshot
            )
        )
        val result = AiAgentSkillTools.call(
            name = AiAgentSkillTools.USE_SKILL,
            arguments = JsonObject().apply { addProperty("name", snapshot.skillId) },
            activeSkillId = snapshot.skillId,
            contentHash = snapshot.contentHash,
            activeSkill = snapshot
        )

        assertEquals("SKILL.md", result?.get("path")?.asString)
        assertEquals(snapshot.prompt, result?.get("content")?.asString)
    }
}
