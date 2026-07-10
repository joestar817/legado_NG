package io.legado.app.help.ai

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatContextManagerTest {

    @Test
    fun estimateTextTokensWeightsCjkMoreThanAscii() {
        val cjk = AiChatContextManager.estimateTextTokens("这是十二个中文字符的测试文本")
        val ascii = AiChatContextManager.estimateTextTokens("abcdefghijkl")

        assertTrue(cjk > ascii)
        assertTrue(cjk >= 10)
    }

    @Test
    fun buildCompactedHistoryKeepsSystemSummaryAndRecentUsersOnly() {
        val messages = listOf(
            message("system", "system prompt"),
            message("user", "old request"),
            message("assistant", "old answer"),
            message("tool", "large tool payload"),
            message("user", "latest correction")
        )

        val compacted = AiChatContextManager.buildCompactedHistory(
            messages = messages,
            summary = "handoff",
            recentUserTokenBudget = 1000
        )

        assertEquals(listOf("system", "user", "user", "user"), compacted.map { it.role() })
        assertTrue(compacted[1].content().startsWith(AiChatContextManager.SUMMARY_PREFIX))
        assertTrue(compacted.any { it.content() == "latest correction" })
        assertFalse(compacted.any { it.role() == "assistant" || it.role() == "tool" })
    }

    @Test
    fun buildCompactedHistoryDoesNotRepeatOversizedUserMessage() {
        val oversized = "正文".repeat(200)
        val compacted = AiChatContextManager.buildCompactedHistory(
            messages = listOf(
                message("system", "system prompt"),
                message("user", oversized)
            ),
            summary = "oversized input summarized",
            recentUserTokenBudget = 20
        )

        assertEquals(2, compacted.size)
        assertFalse(compacted.any { it.content() == oversized })
    }

    @Test
    fun syncActiveSkillKeepsSingleInstructionAndCleansEmbeddedCopies() {
        val embedded = buildString {
            append(AiChatContextManager.ATTACHMENT_PREAMBLE)
            append(AiChatContextManager.APP_CONTEXT_HEADING)
            append("当前书籍：测试小说\n\n")
            append(AiChatContextManager.SKILL_HEADING)
            append("旧角色卡规则\n\n")
            append(AiChatContextManager.USER_MESSAGE_HEADING)
            append("继续扫描")
        }
        val messages = mutableListOf(
            message("system", "system prompt"),
            message("user", embedded)
        )

        AiChatContextManager.syncActiveSkill(messages, "角色卡生成", "新角色卡规则")
        AiChatContextManager.syncActiveSkill(messages, "书架管理", "书架管理规则")

        val skillMessages = messages.filter(AiChatContextManager::isActiveSkillMessage)
        assertEquals(1, skillMessages.size)
        assertTrue(skillMessages.single().content().contains("书架管理规则"))
        assertFalse(skillMessages.single().content().contains("新角色卡规则"))
        val userContent = messages.single { it.role() == "user" }.content()
        assertTrue(userContent.contains("当前书籍：测试小说"))
        assertTrue(userContent.contains("继续扫描"))
        assertFalse(userContent.contains(AiChatContextManager.SKILL_HEADING))
        assertFalse(userContent.contains("旧角色卡规则"))

        AiChatContextManager.syncActiveSkill(messages, null, null)
        assertFalse(messages.any(AiChatContextManager::isActiveSkillMessage))
    }

    @Test
    fun activeSkillIsCountedAsSkillAndPreservedByCompaction() {
        val messages = mutableListOf(
            message("system", "system prompt"),
            message("user", "old request"),
            message("assistant", "old answer")
        )
        AiChatContextManager.syncActiveSkill(messages, "角色卡生成", "只分析角色，不要续写")

        val breakdown = AiChatContextManager.breakdown(messages)
        val compacted = AiChatContextManager.buildCompactedHistory(
            messages = messages,
            summary = "handoff",
            recentUserTokenBudget = 100
        )

        assertTrue(breakdown.skillTokens > 0)
        assertEquals(1, compacted.count(AiChatContextManager::isActiveSkillMessage))
    }

    @Test
    fun compactionRevisionIncrementsAcrossReplacements() {
        val first = AiChatContextManager.buildCompactedHistory(
            messages = listOf(message("system", "system prompt"), message("user", "request")),
            summary = "first",
            recentUserTokenBudget = 100
        )
        val second = AiChatContextManager.buildCompactedHistory(
            messages = first + message("assistant", "continued"),
            summary = "second",
            recentUserTokenBudget = 100
        )

        assertEquals(1, AiChatContextManager.compactionRevision(first))
        assertEquals(2, AiChatContextManager.compactionRevision(second))
    }

    @Test
    fun breakdownSeparatesPromptSkillAppContextAndConversation() {
        val attachedUserContent = buildString {
            append(AiChatContextManager.ATTACHMENT_PREAMBLE)
            append(AiChatContextManager.APP_CONTEXT_HEADING)
            append("当前书籍：测试小说\n\n")
            append(AiChatContextManager.SKILL_HEADING)
            append("只分析角色关系，不要续写。\n\n")
            append(AiChatContextManager.USER_MESSAGE_HEADING)
            append("请扫描当前章节")
        }

        val breakdown = AiChatContextManager.breakdown(
            listOf(
                message("system", "你是阅读助手"),
                message("user", attachedUserContent),
                message("assistant", "开始分析")
            )
        )

        assertTrue(breakdown.systemPromptTokens > 0)
        assertTrue(breakdown.skillTokens > 0)
        assertTrue(breakdown.appContextTokens > 0)
        assertTrue(breakdown.conversationTokens > 0)
        assertTrue(breakdown.protocolTokens > 0)
        assertEquals(
            breakdown.systemPromptTokens + breakdown.toolTokens + breakdown.skillTokens +
                breakdown.appContextTokens + breakdown.conversationTokens +
                breakdown.protocolTokens,
            breakdown.totalTokens
        )
    }

    @Test
    fun breakdownIncludesToolDefinitionsCallsAndResults() {
        val toolDefinition = JsonObject().apply {
            addProperty("type", "function")
            add("function", JsonObject().apply {
                addProperty("name", "mcp__legado__get_book")
                addProperty("description", "读取书籍信息")
                add("parameters", JsonObject().apply {
                    addProperty("type", "object")
                })
            })
        }
        val assistantToolCall = message("assistant", "").apply {
            add("tool_calls", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "call-1") })
            })
        }

        val withoutMessages = AiChatContextManager.breakdown(
            messages = emptyList(),
            toolDefinitions = listOf(toolDefinition)
        )
        val withMessages = AiChatContextManager.breakdown(
            messages = listOf(assistantToolCall, message("tool", "书籍详情")),
            toolDefinitions = listOf(toolDefinition)
        )

        assertTrue(withoutMessages.toolTokens > 0)
        assertTrue(withMessages.toolTokens > withoutMessages.toolTokens)
        assertEquals(0, withMessages.conversationTokens)
    }

    @Test
    fun emptyChatAttributesOnlyPromptAndProtocolWithoutTools() {
        val breakdown = AiChatContextManager.breakdown(
            listOf(message("system", "你是阅读助手"))
        )

        assertTrue(breakdown.systemPromptTokens > 0)
        assertTrue(breakdown.protocolTokens > 0)
        assertEquals(0, breakdown.toolTokens)
        assertEquals(0, breakdown.skillTokens)
        assertEquals(0, breakdown.appContextTokens)
        assertEquals(0, breakdown.conversationTokens)
    }

    @Test
    fun tokenCalibrationUsesOfficialUsageAsAnchorAndOnlyEstimatesNewContent() {
        val calibration = AiChatTokenCalibration.create(
            contextTokens = 1_008_909,
            localContextTokens = 600_651,
            providerId = "deepseek",
            modelId = "deepseek-v4-flash"
        )!!

        assertEquals(1_017_302, calibration.estimate(609_044))
        assertTrue(calibration.matches("deepseek", "deepseek-v4-flash"))
        assertFalse(calibration.matches("sensenova", "deepseek-v4-flash"))
    }

    @Test
    fun calibrationFromHistoryUsesPrefixBeforeLastFinalAssistant() {
        val toolDefinition = JsonObject().apply {
            addProperty("type", "function")
        }
        val toolCall = message("assistant", "").apply {
            add("tool_calls", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "call-1") })
            })
        }
        val messages = listOf(
            message("system", "system prompt"),
            message("user", "scan"),
            toolCall,
            message("tool", "large result"),
            message("assistant", "scan result"),
            message("user", "continue")
        )
        val expectedLocalTokens = AiChatContextManager.breakdown(
            messages = messages.take(4),
            toolDefinitions = listOf(toolDefinition)
        ).totalTokens

        val calibration = AiChatContextManager.calibrationFromHistory(
            messages = messages,
            toolDefinitions = listOf(toolDefinition),
            promptTokens = 1234,
            providerId = "deepseek",
            modelId = "deepseek-v4-flash"
        )!!

        assertEquals(expectedLocalTokens, calibration.localContextTokens)
        assertEquals(1234, calibration.contextTokens)
    }

    @Test
    fun compactionOverflowTrimRemovesOldestCompleteTurn() {
        val messages = mutableListOf(
            message("user", "first"),
            message("assistant", "answer"),
            message("user", "latest")
        )

        assertTrue(AiChatContextManager.trimOldestCompactionHistoryUnit(messages))
        assertEquals(1, messages.size)
        assertEquals("latest", messages.single().get("content").asString)
    }

    @Test
    fun compactionOverflowShrinkKeepsBothEndsOfSingleLargeMessage() {
        val messages = mutableListOf(message("user", "a".repeat(5_000) + "TAIL"))

        assertTrue(AiChatContextManager.shrinkLargestCompactionMessage(messages))
        val content = messages.single().get("content").asString
        assertTrue(content.startsWith("a"))
        assertTrue(content.endsWith("TAIL"))
        assertTrue(content.length < 5_004)
    }

    @Test
    fun compactionOverflowTrimDoesNotLeaveToolResultWithoutCall() {
        val assistant = message("assistant", "").apply {
            add("tool_calls", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "call-1") })
            })
        }
        val tool = message("tool", "result").apply {
            addProperty("tool_call_id", "call-1")
        }
        val messages = mutableListOf(assistant, tool)

        assertTrue(AiChatContextManager.trimOldestCompactionHistoryUnit(messages))
        assertTrue(messages.isEmpty())
    }

    @Test
    fun calibrationFromHistoryRejectsMissingToolContext() {
        val messages = listOf(
            message("system", "system prompt"),
            message("user", "scan"),
            message("assistant", "scan result"),
            message("user", "continue")
        )

        val calibration = AiChatContextManager.calibrationFromHistory(
            messages = messages,
            toolDefinitions = emptyList(),
            promptTokens = 1_008_518,
            providerId = "deepseek",
            modelId = "deepseek-v4-flash",
            expectedToolCallCount = 1
        )

        assertEquals(null, calibration)
    }

    @Test
    fun historyForRegenerationKeepsHiddenToolChainBeforeTargetUser() {
        val toolCall = message("assistant", "").apply {
            add("tool_calls", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("id", "call-1") })
            })
        }
        val messages = listOf(
            message("system", "system prompt"),
            message("user", "scan"),
            toolCall,
            message("tool", "large result"),
            message("assistant", "scan result"),
            message("user", "continue"),
            message("assistant", "old retry result")
        )

        val regenerated = AiChatContextManager.historyForRegeneration(
            messages = messages,
            targetRole = "user",
            targetContent = "continue"
        )!!

        assertEquals(6, regenerated.size)
        assertTrue(regenerated.any { it.role() == "tool" })
        assertEquals("continue", regenerated.last().content())
    }

    private fun message(role: String, content: String): JsonObject {
        return JsonObject().apply {
            addProperty("role", role)
            addProperty("content", content)
        }
    }

    private fun JsonObject.role(): String = get("role").asString

    private fun JsonObject.content(): String = get("content").asString
}
