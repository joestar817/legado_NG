package io.legado.app.help.ai

import io.legado.app.utils.GSON
import io.legado.app.web.mcp.McpMemoryAccessRange
import io.legado.app.web.mcp.McpMemoryAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModeRegistryTest {

    @Test
    fun p3RegistersGeneralAndReadOnlyBookScanMode() {
        val modes = AgentModeRegistry.all()

        assertEquals(listOf(AgentModeRegistry.general, AgentModeRegistry.bookScan), modes)
        assertTrue(AgentModeRegistry.general.allowsUserSkills)
        assertEquals(AgentModeKind.GENERAL, AgentModeRegistry.general.kind)
        assertEquals(AgentModeKind.SYSTEM, AgentModeRegistry.bookScan.kind)
        assertEquals(AiSkillRegistry.SKILL_BOOK_SCAN, AgentModeRegistry.bookScan.systemWorkflowId)
        assertEquals(
            listOf(
                AiSkillRegistry.SKILL_BOOK_SCAN,
                AiSkillRegistry.SKILL_BOOK_SCAN_FACTS,
                AiSkillRegistry.SKILL_BOOK_SCAN_REPORT
            ),
            AgentModeRegistry.bookScan.availableSystemSkillIds
        )
        assertEquals(false, AgentModeRegistry.bookScan.allowsUserSkills)
        assertEquals(false, AgentModeRegistry.bookScan.allowsManualMcpCapabilities)
        assertEquals(
            listOf("bookshelf.query", "bookshelf.read_content", "bookshelf.cache_status", "ai.memory"),
            AgentModeRegistry.bookScan.fixedMcpCapabilityIds
        )
        assertEquals(
            listOf(
                McpMemoryAccessRange(scopeType = "book", domain = "book_scan"),
                McpMemoryAccessRange(
                    scopeType = "global",
                    scopeKey = "default",
                    domain = "reader_preference",
                    access = McpMemoryAccess.READ_ONLY
                )
            ),
            AgentModeRegistry.bookScan.memoryPolicy?.allowedRanges
        )
        assertEquals("core.memory_flush", AgentModeRegistry.bookScan.runtimeHooks.single().id)
        assertEquals(
            AgentModeEntryPolicy(
                requiresContext = true,
                startBehavior = AgentModeEntryStartBehavior.HOST_CONFIRMATION,
                memoryProbe = AgentModeEntryMemoryProbe(
                    scopeType = "book",
                    scopeKeyContextPath = "payload.work_key",
                    domain = "book_scan",
                    memoryType = "",
                    loading = AgentModeEntryNotice(
                        title = "AI 扫书：{{context.payload.book_name}}",
                        description = "正在检查这本书已有的本地扫书档案……"
                    ),
                    absent = AgentModeEntryConfirmation(
                        title = "AI 扫书：{{context.payload.book_name}}",
                        description = "尚未找到这本书的扫书档案。点击后将读取开头与结尾样本进行快速定位。\n\n" +
                            "开始前可先在下方设置模型和思考深度。",
                        confirmLabel = "开始快速定位"
                    ),
                    present = AgentModeEntryConfirmation(
                        title = "AI 扫书：{{context.payload.book_name}}",
                        description = "已找到这本书的本地扫书档案。可以直接查看当前结论，也可以先看覆盖缺口再继续排雷；已有有效事实不会重复读取正文。\n\n" +
                            "开始前可先在下方设置模型和思考深度。",
                        confirmLabel = "选择操作",
                        actions = listOf(
                            AgentModeEntryAction(
                                label = "查看当前结论",
                                value = "view_current_report",
                                description = "核对已有档案并生成当前结论。"
                            ),
                            AgentModeEntryAction(
                                label = "继续排雷",
                                value = "continue_scan",
                                description = "先查看覆盖缺口，再选择扫描范围。"
                            )
                        )
                    ),
                    disabled = AgentModeEntryNotice(
                        title = "暂时无法检查扫书档案",
                        description = "AI 记忆系统未开启。请先在 AI 设置中开启记忆，再重新进入扫书模式。"
                    ),
                    error = AgentModeEntryNotice(
                        title = "扫书档案检查失败",
                        description = "读取本地扫书档案时发生错误；当前不会把它当成首次扫描。请重新进入后再试。"
                    )
                )
            ),
            AgentModeRegistry.bookScan.entryPolicy
        )
        assertEquals(AgentModeEntryPolicy(), AgentModeRegistry.general.entryPolicy)
    }

    @Test
    fun bookScanEntryProbeUsesExactWorkKeyInsideModePolicy() {
        val context = AgentModeEntryContext(
            contextId = "book_detail",
            payload = com.google.gson.JsonObject().apply {
                addProperty("work_key", "白蛇::作者")
            }
        )

        assertEquals(
            AgentModeEntryMemoryProbeTarget(
                scopeType = "book",
                scopeKey = "白蛇::作者",
                domain = "book_scan",
                memoryType = "",
                status = "active"
            ),
            resolveAgentModeEntryMemoryProbeTarget(AgentModeRegistry.bookScan, context)
        )
    }

    @Test
    fun legacyDefaultAndBlankIdentityNormalizeToGeneral() {
        assertEquals(
            AgentModeRegistry.general.identity,
            AgentModeRegistry.normalizePersistedIdentity("default", "")
        )
        assertEquals(
            AgentModeRegistry.general.identity,
            AgentModeRegistry.normalizePersistedIdentity(null, null)
        )
    }

    @Test
    fun legacyJsonWithoutModeFieldsNormalizesToGeneral() {
        val session = GSON.fromJson(
            """
            {
              "id": "legacy-session",
              "title": "旧会话",
              "created_at": 1,
              "updated_at": 2,
              "messages": []
            }
            """.trimIndent(),
            AiChatSessionSnapshot::class.java
        )

        assertEquals(
            AgentModeRegistry.general.identity,
            session.normalizedAgentModeIdentity()
        )
    }

    @Test
    fun unknownModeIsPreservedButCannotResolve() {
        val identity = AgentModeRegistry.normalizePersistedIdentity("future_mode", "7")

        assertEquals(AgentModeIdentity("future_mode", "7"), identity)
        assertNull(AgentModeRegistry.resolve(identity))
    }
}
