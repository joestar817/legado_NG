package io.legado.app.help.ai

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiSystemWorkflowPackageInstrumentedTest {

    @Test
    fun bookScanModePinsTwoStageSkillSet() {
        val mode = AgentModeRegistry.bookScan
        val workflow = requireNotNull(AiSkillRegistry.systemWorkflow(AiSkillRegistry.SKILL_BOOK_SCAN))
        val skillSet = requireNotNull(
            AiSkillPackageRegistry.systemPackageSet(mode.availableSystemSkillIds)
        )

        assertEquals("59", workflow.revision)
        assertTrue(workflow.prompt.contains("book_scan_facts"))
        assertTrue(workflow.prompt.contains("book_scan_report"))
        assertTrue(workflow.prompt.contains("bookshelf_book_get(work_key=上下文原样 work_key)"))
        assertTrue(workflow.prompt.contains("禁止调用 `bookshelf_current_book_get`"))
        assertTrue(workflow.prompt.contains("chapter_count=10,char_limit=0"))
        assertTrue(workflow.prompt.contains("只调用一次非空 `agent_memory_batch_upsert`"))
        assertTrue(workflow.prompt.contains("禁止创建或查询 `work_assessment`"))
        assertTrue(workflow.prompt.contains("references/reader-tags/index.md"))
        assertTrue(workflow.prompt.contains("interaction-policy.json"))
        assertTrue(workflow.prompt.contains("只能从目标交互的 `allowed_items` 逐字复制"))
        assertEquals(3, skillSet.skillIds.size)
        assertNotNull(
            AiAgentSkillTools.toolDefinition(
                activeSkillId = workflow.id,
                contentHash = skillSet.contentHash,
                availableSkillIds = mode.availableSystemSkillIds
            )
        )
        val interactionPolicy = requireNotNull(
            AiChatInteractionPolicyRegistry.resolve(mode)
        )
        assertEquals(2, interactionPolicy.schemaVersion)
        assertEquals(
            setOf(
                "actions",
                "single_choice",
                "multi_choice",
                "multi_tag_stance",
                "confirm"
            ),
            interactionPolicy.requiredPolicyTypes
        )
        val preferenceRule = requireNotNull(
            interactionPolicy.interactions["reader_preference_adaptive_tags"]
        )
        assertTrue("route.multi_partner" in preferenceRule.allowedItems)
        assertFalse("risk.committed_partner_betrayal" in preferenceRule.allowedItems)
        assertFalse("protagonist.passive_humiliation" in preferenceRule.allowedItems)
        val reactionRule = requireNotNull(interactionPolicy.interactions["book_scan_reaction"])
        assertEquals("single_choice", reactionRule.type)
        assertEquals(
            listOf("continue_scan", "reject"),
            reactionRule.options.map { it.value }
        )
        val likeReasonsRule = requireNotNull(
            interactionPolicy.interactions["book_scan_like_reasons"]
        )
        assertEquals("multi_choice", likeReasonsRule.type)
        assertTrue("feedback.protagonist" in likeReasonsRule.allowedItems)
        assertEquals("跳过，直接选范围", likeReasonsRule.skip?.label)
        val dislikeReasonsRule = requireNotNull(
            interactionPolicy.interactions["book_scan_dislike_reasons"]
        )
        assertFalse("feedback.confirmed_risk" in dislikeReasonsRule.allowedItems)
        assertEquals("保存并结束", dislikeReasonsRule.submit?.label)

        val modelInteractionPolicy = loadSkill(
            name = AiSkillRegistry.SKILL_BOOK_SCAN,
            path = "interaction-policy.json",
            activeSkillId = workflow.id,
            skillSetHash = skillSet.contentHash,
            availableSkillIds = mode.availableSystemSkillIds
        )
        assertTrue(modelInteractionPolicy.contains("feedback.pacing_problem"))
        assertFalse(modelInteractionPolicy.contains("feedback.opening_pacing"))

        val facts = loadSkill(
            name = AiSkillRegistry.SKILL_BOOK_SCAN_FACTS,
            activeSkillId = workflow.id,
            skillSetHash = skillSet.contentHash,
            availableSkillIds = mode.availableSystemSkillIds
        )
        assertTrue(facts.contains("window_bundle"))
        assertTrue(facts.contains("evidence_refs"))
        assertTrue(facts.contains("每个正文回执只保存一条"))
        assertTrue(facts.contains("首次快扫只调用一次"))
        assertTrue(facts.contains("work_components"))
        assertTrue(facts.contains("risks"))
        assertTrue(facts.contains("同一本书面对不同读者时，本层事实必须完全一致"))

        val tagIndex = loadSkill(
            name = AiSkillRegistry.SKILL_BOOK_SCAN,
            path = "references/reader-tags/index.md",
            activeSkillId = workflow.id,
            skillSetHash = skillSet.contentHash,
            availableSkillIds = mode.availableSystemSkillIds
        )
        assertTrue(tagIndex.contains("66 个稳定作品标签"))
        assertTrue(tagIndex.contains("每次默认选择 3 个"))
        assertTrue(tagIndex.contains("信息确有必要时最多 5 个"))
        assertTrue(tagIndex.contains("跳过、关闭卡片、停止扫描"))

        val report = loadSkill(
            name = AiSkillRegistry.SKILL_BOOK_SCAN_REPORT,
            activeSkillId = workflow.id,
            skillSetHash = skillSet.contentHash,
            availableSkillIds = mode.availableSystemSkillIds
        )
        assertTrue(report.contains("常年混中文网文书圈"))
        assertTrue(report.contains("high_match|match|tradeoff|mismatch|avoid|insufficient"))
        assertTrue(report.contains("报告只改变推荐和展示强度，不改写事实"))
        assertTrue(report.contains("不得写成已执行结果"))
        assertTrue(report.contains("禁止使用“整本书、整部作品、全篇、全书最"))
        assertTrue(report.contains("中段人物成长、关系发展、伏笔、节奏、剧情完成度、作者意图和隐藏风险一律保持未知"))
        assertTrue(report.contains("不为填字或填栏目重复观点"))
        assertTrue(report.contains("## 适读结论"))
        assertTrue(report.contains("## 作品速览"))
        assertTrue(report.contains("## 已确认雷点"))
        assertTrue(report.contains("## 阅读感受"))
        assertTrue(report.contains("以上只依据开头与结尾样本"))
        assertTrue(report.contains("仍需继续扫描确认"))
        assertTrue(report.contains("{\"id\":\"book_scan_reaction\"}"))
        assertTrue(report.contains("当前书反馈不自动写入长期偏好"))
        assertTrue(report.contains("不得直接输出 `reader_preference_adaptive_tags`"))
        assertTrue(report.contains("严禁只输出交互块而省略报告"))
        assertTrue(report.indexOf("## 适读结论") < report.indexOf("## 作品速览"))
        assertTrue(report.indexOf("## 作品速览") < report.indexOf("## 阅读感受"))
        assertFalse(report.contains("## 个性化可读性分析"))
        assertFalse(report.contains("## 扫描边界"))
        assertFalse(report.contains("## 主观锐评"))
        assertFalse(report.contains("## 关键人物"))
        assertFalse(report.contains("## 核心关系"))
        assertFalse(report.contains("## 重大雷点"))
        assertFalse(report.contains("无废话锐评模式"))
        assertFalse(report.contains("高端战力主动进入不可信势力"))

        try {
            loadSkill(
                name = "book_scan_review_work",
                activeSkillId = workflow.id,
                skillSetHash = skillSet.contentHash,
                availableSkillIds = mode.availableSystemSkillIds
            )
            fail("Expected removed review skill to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun loadSkill(
        name: String,
        path: String? = null,
        activeSkillId: String,
        skillSetHash: String,
        availableSkillIds: List<String>
    ): String {
        return requireNotNull(
            AiAgentSkillTools.call(
                name = AiAgentSkillTools.USE_SKILL,
                arguments = JsonObject().apply {
                    addProperty("name", name)
                    path?.let { addProperty("path", it) }
                },
                activeSkillId = activeSkillId,
                contentHash = skillSetHash,
                availableSkillIds = availableSkillIds
            )
        ).get("content").asString
    }
}
