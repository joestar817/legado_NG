package io.legado.app.help.ai

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatInteractionPolicyTest {

    private val policy = AiChatInteractionPolicyRegistry.parse(
        """
        {
          "schema_version": 2,
          "required_policy_types": ["single_choice", "multi_choice", "multi_tag_stance", "confirm"],
          "interactions": {
            "book_scan_target": {
              "type": "single_choice",
              "version": 1,
              "title": "选择扫描目标",
              "description": "选择范围。",
              "options": [
                {"label": "扫描约 100 章", "value": "scan_100", "description": "新增覆盖。"}
              ],
              "allowed_items": {},
              "submit": {"label": "开始扫描", "prompt_template": "扫描：{{value}}"}
            },
            "book_scan_rebuild_required": {
              "type": "confirm",
              "version": 1,
              "title": "已有档案需要重新核对",
              "description": "确认后重新读取首尾样本。",
              "options": [],
              "allowed_items": {},
              "submit": {"label": "重新快速定位", "prompt_template": "确认重扫"},
              "cancel": {"label": "暂不重扫", "prompt_template": "取消重扫"}
            },
            "book_scan_like_reasons": {
              "type": "multi_choice",
              "version": 1,
              "title": "哪些地方最对你的胃口？",
              "description": "可多选；这里只记录你对当前书的判断。",
              "options": [],
              "allowed_items": {
                "feedback.genre_setting": {
                  "label": "题材和设定对胃口",
                  "description": "类型、世界设定或核心创意正好是你想看的。"
                },
                "feedback.protagonist": {
                  "label": "主角有吸引力",
                  "description": "主角的目标、行动或处境让你愿意代入。"
                }
              },
              "submit": {"label": "确认这些点", "prompt_template": "原因：{{values}}"},
              "skip": {"label": "跳过，直接选范围", "prompt_template": "跳过原因并选择范围"}
            },
            "reader_preference_adaptive_tags": {
              "type": "multi_tag_stance",
              "version": 2,
              "title": "这些作品特征会怎样影响你的选择？",
              "description": "每项单选；只保存你明确选择的项目，未选择和跳过仍保持未知。",
              "options": [
                {"label": "更偏好这类作品", "value": "like", "description": ""},
                {"label": "可以接受", "value": "neutral", "description": ""},
                {"label": "会因此劝退", "value": "avoid", "description": ""}
              ],
              "allowed_items": {
                "route.multi_partner": {
                  "label": "后宫／多女主路线",
                  "description": "主角与多名角色建立明确感情或归宿关系"
                },
                "setting.system_present": {
                  "label": "有系统",
                  "description": "存在发任务、发奖励或提供交互规则的系统机制"
                }
              },
              "submit": {"label": "保存并更新推荐", "prompt_template": ""},
              "skip": {"label": "暂时跳过", "prompt_template": ""},
              "memory_patch": {
                "operation": "json_map_merge",
                "scope_type": "global",
                "scope_key": "default",
                "domain": "reader_preference",
                "memory_type": "global_profile",
                "title": "默认阅读偏好",
                "content": "用户在扫书报告中明确选择的标签态度。",
                "map_field": "tag_stances",
                "value_field": "stance",
                "identity_fields": ["schema_version", "profile_id"],
                "base_data": {
                  "schema_version": 2,
                  "profile_id": "default",
                  "dimension_answers": {},
                  "tag_stances": {},
                  "custom_rules": []
                },
                "on_base_mismatch": "replace"
              }
            }
          }
        }
        """.trimIndent()
    )

    @Test
    fun acceptsOnlyDeclaredNeutralItemsWithTrustedCopy() {
        assertEquals(
            emptyList<String>(),
            AiChatInteractionPolicyRegistry.violations(policy, preferenceInteraction())
        )
    }

    @Test
    fun rejectsUndeclaredNegativeOrRiskItemsAndUnknownInteractionIds() {
        val negative = AiChatInteractionPolicyRegistry.violations(
            policy,
            preferenceInteraction(
                item = AiChatInteractionItem(
                    "protagonist.passive_humiliation",
                    "长期窝囊受气",
                    "负面人物判断"
                )
            )
        )
        val risk = AiChatInteractionPolicyRegistry.violations(
            policy,
            preferenceInteraction(
                item = AiChatInteractionItem(
                    "risk.committed_partner_betrayal",
                    "关系背叛",
                    "确定风险"
                )
            )
        )
        val unknownInteraction = AiChatInteractionPolicyRegistry.violations(
            policy,
            preferenceInteraction().copy(id = "model_invented_question")
        )

        assertTrue(negative.any { it.contains("未获策略允许") })
        assertTrue(risk.any { it.contains("未获策略允许") })
        assertTrue(unknownInteraction.single().contains("必须使用已声明"))
    }

    @Test
    fun normalizesNeutralIdRelabeledAsRiskQuestionToTrustedCatalogCopy() {
        val result = AiChatInteractionPolicyRegistry.filter(
            policy,
            listOf(
                preferenceInteraction(
                    item = AiChatInteractionItem(
                        "route.multi_partner",
                        "绿帽／NTR",
                        "你是否喜欢这种情节？"
                    )
                )
            )
        )

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        assertEquals("后宫／多女主路线", result.accepted.single().items.single().label)
        assertEquals(
            "主角与多名角色建立明确感情或归宿关系",
            result.accepted.single().items.single().description
        )
    }

    @Test
    fun normalizesLegitimateChoiceIdToTrustedHostTemplate() {
        val malicious = scanTargetInteraction().copy(
            title = "你能接受绿帽吗？",
            description = "请选择态度",
            options = listOf(
                AiChatInteractionOption("喜欢", "like", ""),
                AiChatInteractionOption("不喜欢", "avoid", "")
            )
        )

        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(malicious))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        assertEquals("选择扫描目标", result.accepted.single().title)
        assertEquals("选择范围。", result.accepted.single().description)
        assertEquals(listOf("scan_100"), result.accepted.single().options.map { it.value })
    }

    @Test
    fun normalizesLegitimateConfirmIdToTrustedHostTemplate() {
        val malicious = rebuildInteraction().copy(
            title = "是否喜欢 NTR？",
            description = "这会写入长期偏好。"
        )

        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(malicious))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        assertEquals("已有档案需要重新核对", result.accepted.single().title)
        assertEquals("确认后重新读取首尾样本。", result.accepted.single().description)
    }

    @Test
    fun replacesChangedMemoryTargetBeforeRenderingAndRejectsRawWrite() {
        val original = preferenceInteraction()
        val malicious = original.copy(
            memoryPatch = original.memoryPatch?.copy(
                domain = "book_scan",
                memoryType = "manifest"
            )
        )

        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(malicious))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        assertEquals("reader_preference", result.accepted.single().memoryPatch?.domain)
        assertEquals("global_profile", result.accepted.single().memoryPatch?.memoryType)
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionPolicyRegistry.requireAllowed(policy, malicious)
        }
    }

    @Test
    fun normalizesHarmlessModelCopyDifferencesFromRealReport() {
        val modelInteraction = preferenceInteraction().copy(
            title = "阅读偏好确认",
            description = "以下两项是本书已确认的作品特征，回答后会改进个性化推荐：",
            options = listOf(
                AiChatInteractionOption("更偏好这类作品", "like", ""),
                AiChatInteractionOption("可以接受", "neutral", ""),
                AiChatInteractionOption("会因此劝退", "avoid", "")
            ),
            submit = AiChatInteractionSubmit("保存偏好", "保存以上阅读偏好到我的个人档案。"),
            skip = AiChatInteractionSubmit("跳过", "暂不保存偏好，继续下一步。"),
            memoryPatch = preferenceMemoryPatch().copy(
                title = "阅读偏好档案",
                content = "AI 扫书过程中建立的最小阅读偏好档案。"
            )
        )

        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(modelInteraction))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        val trusted = result.accepted.single()
        assertEquals("这些作品特征会怎样影响你的选择？", trusted.title)
        assertEquals("保存并更新推荐", trusted.submit?.label)
        assertEquals("暂时跳过", trusted.skip?.label)
        assertEquals("默认阅读偏好", trusted.memoryPatch?.title)
        AiChatInteractionPolicyRegistry.requireAllowed(policy, trusted)
    }

    @Test
    fun hydratesIdOnlyPreferenceIntentFromTrustedPolicy() {
        val intent = AiChatInteractionParser.parse(
            """
            ```legado-interaction
            {"id":"reader_preference_adaptive_tags","item_ids":["route.multi_partner","setting.system_present"]}
            ```
            """.trimIndent()
        ).interactions.single()

        assertEquals(AiChatInteractionType.POLICY_REF, intent.type)
        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(intent))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        val trusted = result.accepted.single()
        assertEquals(AiChatInteractionType.MULTI_TAG_STANCE, trusted.type)
        assertEquals("这些作品特征会怎样影响你的选择？", trusted.title)
        assertEquals(
            listOf("后宫／多女主路线", "有系统"),
            trusted.items.map { it.label }
        )
        assertEquals(listOf("like", "neutral", "avoid"), trusted.options.map { it.value })
        assertEquals("reader_preference", trusted.memoryPatch?.domain)
    }

    @Test
    fun hydratesIdOnlyStaticInteractionAndRejectsUnknownReference() {
        val staticIntent = AiChatInteractionParser.parse(
            """```legado-interaction
            {"id":"book_scan_target"}
            ```"""
        ).interactions.single()
        val unknownIntent = staticIntent.copy(id = "book_scan_target_similar")

        val accepted = AiChatInteractionPolicyRegistry.filter(policy, listOf(staticIntent))
        val rejected = AiChatInteractionPolicyRegistry.filter(policy, listOf(unknownIntent))

        assertEquals(AiChatInteractionType.SINGLE_CHOICE, accepted.accepted.single().type)
        assertEquals("选择扫描目标", accepted.accepted.single().title)
        assertEquals(listOf("scan_100"), accepted.accepted.single().options.map { it.value })
        assertEquals(listOf(unknownIntent), rejected.rejected)
    }

    @Test
    fun hydratesDynamicMultiChoiceOptionsFromTrustedItemIds() {
        val intent = AiChatInteractionParser.parse(
            """```legado-interaction
            {"id":"book_scan_like_reasons","item_ids":["feedback.genre_setting","feedback.protagonist"]}
            ```"""
        ).interactions.single()

        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(intent))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        val trusted = result.accepted.single()
        assertEquals(AiChatInteractionType.MULTI_CHOICE, trusted.type)
        assertEquals("哪些地方最对你的胃口？", trusted.title)
        assertEquals(
            listOf("feedback.genre_setting", "feedback.protagonist"),
            trusted.options.map { it.value }
        )
        assertEquals(
            listOf("题材和设定对胃口", "主角有吸引力"),
            trusted.options.map { it.label }
        )
        assertEquals(emptyList<AiChatInteractionItem>(), trusted.items)
        assertEquals("确认这些点", trusted.submit?.label)
        assertEquals("跳过，直接选范围", trusted.skip?.label)
    }

    @Test
    fun allowsDynamicPolicyItemsWithEmptyDescriptions() {
        val policy = AiChatInteractionPolicyRegistry.parse(
            """
            {
              "schema_version": 2,
              "required_policy_types": ["multi_choice"],
              "interactions": {
                "book_scan_like_reasons": {
                  "type": "multi_choice",
                  "version": 1,
                  "title": "目前哪些点让你愿意继续了解？",
                  "description": "可多选；没有明确喜欢的点就跳过。",
                  "options": [],
                  "allowed_items": {
                    "feedback.genre_setting": {"label": "题材和设定有兴趣", "description": ""}
                  },
                  "submit": {"label": "记下并选范围", "prompt_template": ""}
                }
              }
            }
            """.trimIndent()
        )
        val intent = AiChatInteractionParser.parse(
            """```legado-interaction
            {"id":"book_scan_like_reasons","item_ids":["feedback.genre_setting"]}
            ```"""
        ).interactions.single()

        val result = AiChatInteractionPolicyRegistry.filter(policy, listOf(intent))

        assertEquals(emptyList<AiChatInteraction>(), result.rejected)
        assertEquals("", result.accepted.single().options.single().description)
    }

    @Test
    fun requireAllowedFailsClosedBeforeSaving() {
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionPolicyRegistry.requireAllowed(
                policy,
                preferenceInteraction(
                    item = AiChatInteractionItem(
                        "risk.committed_partner_betrayal",
                        "关系背叛",
                        "确定风险"
                    )
                )
            )
        }
    }

    private fun preferenceInteraction(
        item: AiChatInteractionItem = AiChatInteractionItem(
            "route.multi_partner",
            "后宫／多女主路线",
            "主角与多名角色建立明确感情或归宿关系"
        )
    ): AiChatInteraction {
        return AiChatInteraction(
            version = 2,
            id = "reader_preference_adaptive_tags",
            type = AiChatInteractionType.MULTI_TAG_STANCE,
            title = "这些作品特征会怎样影响你的选择？",
            description = "每项单选；只保存你明确选择的项目，未选择和跳过仍保持未知。",
            options = listOf(
                AiChatInteractionOption("更偏好这类作品", "like", ""),
                AiChatInteractionOption("可以接受", "neutral", ""),
                AiChatInteractionOption("会因此劝退", "avoid", "")
            ),
            items = listOf(item),
            submit = AiChatInteractionSubmit("保存并更新推荐", ""),
            cancel = null,
            skip = AiChatInteractionSubmit("暂时跳过", ""),
            memoryPatch = preferenceMemoryPatch()
        )
    }

    private fun scanTargetInteraction(): AiChatInteraction {
        return AiChatInteraction(
            version = 1,
            id = "book_scan_target",
            type = AiChatInteractionType.SINGLE_CHOICE,
            title = "选择扫描目标",
            description = "选择范围。",
            options = listOf(AiChatInteractionOption("扫描约 100 章", "scan_100", "新增覆盖。")),
            submit = AiChatInteractionSubmit("开始扫描", "扫描：{{value}}"),
            cancel = null
        )
    }

    private fun rebuildInteraction(): AiChatInteraction {
        return AiChatInteraction(
            version = 1,
            id = "book_scan_rebuild_required",
            type = AiChatInteractionType.CONFIRM,
            title = "已有档案需要重新核对",
            description = "确认后重新读取首尾样本。",
            options = emptyList(),
            submit = AiChatInteractionSubmit("重新快速定位", "确认重扫"),
            cancel = AiChatInteractionSubmit("暂不重扫", "取消重扫")
        )
    }

    private fun preferenceMemoryPatch(): AiChatInteractionMemoryPatch {
        return AiChatInteractionMemoryPatch(
            operation = AiChatInteractionMemoryPatchOperation.JSON_MAP_MERGE,
            scopeType = "global",
            scopeKey = "default",
            domain = "reader_preference",
            memoryType = "global_profile",
            title = "默认阅读偏好",
            content = "用户在扫书报告中明确选择的标签态度。",
            mapField = "tag_stances",
            valueField = "stance",
            identityFields = listOf("schema_version", "profile_id"),
            baseData = JsonParser.parseString(
                """{"schema_version":2,"profile_id":"default","dimension_answers":{},"tag_stances":{},"custom_rules":[]}"""
            ).asJsonObject,
            onBaseMismatch = AiChatInteractionMemoryBaseMismatch.REPLACE
        )
    }
}
