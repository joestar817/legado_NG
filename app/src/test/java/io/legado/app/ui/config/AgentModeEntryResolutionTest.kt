package io.legado.app.ui.config

import com.google.gson.JsonObject
import io.legado.app.data.entities.AgentMemory
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AgentModeEntryLaunchDecision
import io.legado.app.help.ai.AgentModeEntryMemoryState
import io.legado.app.help.ai.AgentModeEntryPolicy
import io.legado.app.help.ai.AgentModeEntryStartBehavior
import io.legado.app.help.ai.AgentModeRegistry
import io.legado.app.help.ai.AiChatInteractionParser
import io.legado.app.help.ai.AiChatInteractionType
import io.legado.app.help.ai.resolveAgentModeEntryLaunch
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentModeEntryResolutionTest {

    @Test
    fun bookScanEntryUsesBookScanSystemMode() {
        assertEquals(
            AgentModeRegistry.bookScan,
            resolveEntryAgentMode(AiChatActivity.ENTRY_BOOK_SCAN)
        )
    }

    @Test
    fun ordinaryEntryStaysInGeneralMode() {
        assertEquals(
            AgentModeRegistry.general,
            resolveEntryAgentMode(AiChatActivity.ENTRY_BOOK_DETAIL)
        )
    }

    @Test
    fun bookScanWaitsForHostConfirmationBeforeStarting() {
        assertEquals(
            AgentModeEntryLaunchDecision.SHOW_CONFIRMATION,
            resolveAgentModeEntryLaunch(
                policy = AgentModeRegistry.bookScan.entryPolicy,
                hasRequiredContext = true,
                alreadyStarted = false,
                hasVisibleMessages = false
            )
        )
    }

    @Test
    fun entryConfirmationRendersBookNameWithoutModelOrHistory() {
        val context = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：白蛇",
            payload = JsonObject().apply {
                addProperty("book_name", "白蛇")
            }
        )

        assertEquals(
            "当前准备扫描《白蛇》；AI 扫书：白蛇",
            renderModeEntryConfirmationTemplate(
                "当前准备扫描《{{context.payload.book_name}}》；{{context.title}}",
                context
            )
        )
    }

    @Test
    fun bookScanEntryDistinguishesAbsentAndPresentMemoryWithoutUsingChatHistory() {
        val context = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：白蛇",
            payload = JsonObject().apply {
                addProperty("book_name", "白蛇")
                addProperty("work_key", "白蛇::作者")
                addProperty("total_chapters", 38)
            }
        )

        val absent = requireNotNull(
            buildModeEntryConfirmation(
                mode = AgentModeRegistry.bookScan,
                context = context,
                memoryState = AgentModeEntryMemoryState.ABSENT
            )
        )
        val present = requireNotNull(
            buildModeEntryConfirmation(
                mode = AgentModeRegistry.bookScan,
                context = context,
                memoryState = AgentModeEntryMemoryState.PRESENT
            )
        )

        assertEquals("开始快速定位", absent.submit?.label)
        assertEquals(AiChatInteractionType.ACTIONS, present.type)
        assertEquals("选择操作", present.submit?.label)
        assertEquals(
            listOf("view_current_report", "continue_scan"),
            present.options.map { it.value }
        )
        assertEquals(false, absent.description.contains("当前会话暂无扫描结果"))
        assertEquals(true, present.description.contains("覆盖缺口"))
    }

    @Test
    fun bookScanEntryDoesNotOfferStartWhenMemoryStateIsUnknown() {
        val loading = requireNotNull(
            buildModeEntryConfirmation(
                mode = AgentModeRegistry.bookScan,
                context = AgentModeEntryContext(
                    contextId = "book_detail",
                    payload = JsonObject().apply {
                        addProperty("book_name", "白蛇")
                        addProperty("work_key", "白蛇::作者")
                    }
                ),
                memoryState = AgentModeEntryMemoryState.LOADING
            )
        )

        assertEquals(null, loading.submit)
        assertEquals(true, loading.description.contains("正在检查"))
    }

    @Test
    fun merelyShowingEntryCardDoesNotCreateAnEmptyHistorySession() {
        assertEquals(false, shouldPersistActiveSession(false, false))
        assertEquals(true, shouldPersistActiveSession(false, true))
        assertEquals(true, shouldPersistActiveSession(true, false))
    }

    @Test
    fun bookScanContinueEntryRendersCoverageGapAndTargetInteraction() {
        val context = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：白蛇",
            payload = JsonObject().apply {
                addProperty("book_name", "白蛇")
                addProperty("work_key", "白蛇::作者")
                addProperty("total_chapters", 38)
            }
        )
        val content = buildBookScanContinueEntryContent(
            context = context,
            memories = listOf(
                AgentMemory(
                    scopeType = "book",
                    scopeKey = "白蛇::作者",
                    domain = "book_scan",
                    memoryType = "manifest",
                    dataJson = """
                        {
                          "full_text_coverage": [[0, 5], [33, 38]],
                          "gaps": ["中段 6-33 章未完整排雷"]
                        }
                    """.trimIndent()
                )
            )
        )
        val render = AiChatInteractionParser.parse(content)

        assertEquals(true, render.content.contains("继续排雷"))
        assertEquals(true, render.content.contains("已完整核对"))
        assertEquals(true, render.content.contains("第 1–5 章、第 34–38 章"))
        assertEquals(render.content, true, render.content.contains("共 10 / 38 章"))
        assertEquals(true, render.content.contains("尚未完整核对"))
        assertEquals(true, render.content.contains("优先检查**：第 6–33 章"))
        assertEquals("book_scan_target", render.interactions.single().id)
    }

    @Test
    fun bookScanContinueEntrySplitsLegacyManifestTextWithoutDuplicatingWholeParagraph() {
        val content = buildBookScanContinueEntryContent(
            context = AgentModeEntryContext(
                contextId = "book_detail",
                payload = JsonObject().apply {
                    addProperty("book_name", "天之下")
                    addProperty("total_chapters", 500)
                }
            ),
            memories = listOf(
                AgentMemory(
                    scopeType = "book",
                    scopeKey = "天之下",
                    domain = "book_scan",
                    memoryType = "manifest",
                    content = "scan_100预算使用完毕。正文覆盖：406-410章（杨衍斩断李景风左腿完整过程）。" +
                        "snippet导航覆盖全书80+章。中段虽未全文读完，但已确认核心雷点。" +
                        "建议如需进一步可补读沈末辰在抚州被捕过程（433-437章）。当前预算已用完。"
                )
            )
        )

        assertEquals(true, content.contains("**已完整核对**：第 406–410 章，共 5 / 500 章"))
        assertEquals(true, content.contains("**已快速浏览**：约 80 章"))
        assertEquals(true, content.contains("**尚未完整核对**：按现有记录计算约 495 章"))
        assertEquals(true, content.contains("**优先检查**：第 433–437 章"))
        assertEquals(false, content.contains("manifest"))
        assertEquals(false, content.contains("scan_100"))
        assertEquals(false, content.contains("snippet"))

    }

    @Test
    fun entryDoesNotRestartAfterConfirmationOrVisibleHistory() {
        val policy = AgentModeRegistry.bookScan.entryPolicy

        assertEquals(
            AgentModeEntryLaunchDecision.NONE,
            resolveAgentModeEntryLaunch(
                policy = policy,
                hasRequiredContext = true,
                alreadyStarted = true,
                hasVisibleMessages = false
            )
        )
        assertEquals(
            AgentModeEntryLaunchDecision.NONE,
            resolveAgentModeEntryLaunch(
                policy = policy,
                hasRequiredContext = true,
                alreadyStarted = false,
                hasVisibleMessages = true
            )
        )
    }

    @Test
    fun automaticModesAndMissingContextHaveDistinctDecisions() {
        val automatic = AgentModeEntryPolicy(
            requiresContext = true,
            startBehavior = AgentModeEntryStartBehavior.AUTOMATIC
        )

        assertEquals(
            AgentModeEntryLaunchDecision.START_AUTOMATICALLY,
            resolveAgentModeEntryLaunch(
                policy = automatic,
                hasRequiredContext = true,
                alreadyStarted = false,
                hasVisibleMessages = false
            )
        )
        assertEquals(
            AgentModeEntryLaunchDecision.MISSING_REQUIRED_CONTEXT,
            resolveAgentModeEntryLaunch(
                policy = automatic,
                hasRequiredContext = false,
                alreadyStarted = false,
                hasVisibleMessages = false
            )
        )
    }
}
