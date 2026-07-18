package io.legado.app.ui.config

import io.legado.app.help.ai.AiChatInteraction
import io.legado.app.help.ai.AiChatInteractionItem
import io.legado.app.help.ai.AiChatInteractionOption
import io.legado.app.help.ai.AiChatInteractionType
import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatResolvedInteractionTest {

    @Test
    fun resolvedSelectionsKeepItemOrderAndHumanReadableLabels() {
        val interaction = AiChatInteraction(
            version = 2,
            id = "reader_preference_adaptive_tags",
            type = AiChatInteractionType.MULTI_TAG_STANCE,
            title = "了解你的偏好",
            description = "只保存明确选择。",
            options = listOf(
                AiChatInteractionOption("更偏好这类作品", "like", ""),
                AiChatInteractionOption("可以接受", "neutral", ""),
                AiChatInteractionOption("会因此劝退", "avoid", "")
            ),
            items = listOf(
                AiChatInteractionItem("tone.dark_cruel", "黑暗残酷", "持续高压且缓解较少。"),
                AiChatInteractionItem("setting.system_present", "有系统", "存在任务或奖励系统。")
            ),
            submit = null,
            cancel = null
        )

        val result = resolveInteractionSelections(
            interaction,
            mapOf(
                "setting.system_present" to "like",
                "tone.dark_cruel" to "avoid",
                "unknown" to "neutral"
            )
        )

        assertEquals(listOf("黑暗残酷", "有系统"), result.map { it.itemLabel })
        assertEquals(listOf("会因此劝退", "更偏好这类作品"), result.map { it.optionLabel })
        assertEquals("持续高压且缓解较少。", result.first().description)
    }
}
