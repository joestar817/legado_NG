package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChatTitleResolutionTest {

    @Test
    fun modeEntryTitleStaysStableAfterAssistantReportArrives() {
        assertEquals(
            "AI 扫书：白蛇",
            resolveConversationTitle(
                modeEntryTitle = "AI 扫书：白蛇",
                derivedTitle = "## 适读结论",
                previousTitle = "事实包有效。基于当前样本"
            )
        )
    }

    @Test
    fun normalChatStillUsesDerivedThenPreviousTitle() {
        assertEquals(
            "帮我整理本章",
            resolveConversationTitle(
                modeEntryTitle = null,
                derivedTitle = "帮我整理本章",
                previousTitle = "旧标题"
            )
        )
        assertEquals(
            "旧标题",
            resolveConversationTitle(
                modeEntryTitle = "",
                derivedTitle = null,
                previousTitle = "旧标题"
            )
        )
    }
}
