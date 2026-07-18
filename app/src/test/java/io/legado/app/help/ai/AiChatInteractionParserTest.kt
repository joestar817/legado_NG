package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatInteractionParserTest {

    @Test
    fun acceptsBareInteractionJsonWithoutHidingOrdinaryJson() {
        val interactionText = """
            操作已准备。

            {"version":1,"id":"continue_action","type":"actions","title":"继续","options":[{"label":"继续","value":"continue"}]}
        """.trimIndent()
        val parsedInteraction = AiChatInteractionParser.parse(interactionText)
        assertEquals("操作已准备。", parsedInteraction.content)
        assertEquals("continue_action", parsedInteraction.interactions.single().id)

        val ordinaryJson = """
            普通数据：
            {"type":"book","title":"示例"}
        """.trimIndent()
        val parsedJson = AiChatInteractionParser.parse(ordinaryJson)
        assertEquals(ordinaryJson, parsedJson.content)
        assertTrue(parsedJson.interactions.isEmpty())
        assertFalse(parsedJson.hasHiddenArtifacts)
    }

    @Test
    fun acceptsXmlInteractionInsideTextFence() {
        val content = """
            已完成。

            ```text
            <legado-interaction>
            {"version":1,"id":"next_action","type":"actions","title":"下一步","options":[{"label":"分析","value":"analyze"}]}
            </legado-interaction>
            ```
        """.trimIndent()

        val parsed = AiChatInteractionParser.parse(content)

        assertEquals("已完成。", parsed.content)
        assertEquals("next_action", parsed.interactions.single().id)
    }

    @Test
    fun acceptsMinimalXmlPolicyReferenceInInteractionFence() {
        val content = """
            ```legado-interaction
            <interaction id="book_scan_reaction" />
            ```
        """.trimIndent()

        val parsed = AiChatInteractionParser.parse(content)

        assertEquals("", parsed.content)
        assertEquals("book_scan_reaction", parsed.interactions.single().id)
        assertTrue(parsed.interactions.single().items.isEmpty())
    }

    @Test
    fun acceptsXmlPolicyReferenceWithItemIds() {
        val content = """
            ```legado-interaction
            <interaction id="book_scan_dislike_reasons" item-ids="risk.forced_third_party_marriage,feedback.relationship_problem" />
            ```
        """.trimIndent()

        val parsed = AiChatInteractionParser.parse(content)

        assertEquals("book_scan_dislike_reasons", parsed.interactions.single().id)
        assertEquals(
            listOf("risk.forced_third_party_marriage", "feedback.relationship_problem"),
            parsed.interactions.single().items.map { it.id }
        )
    }

    @Test
    fun keepsSingleChoiceDescriptionAndBuildsPrompt() {
        val content = """
            ```legado-interaction
            {
              "version": 1,
              "id": "target",
              "type": "single_choice",
              "title": "选择目标",
              "options": [
                {"label":"完整处理","value":"full","description":"覆盖全部可用内容。"}
              ],
              "submit": {"label":"开始","prompt_template":"目标：{{label}}（{{value}}）"}
            }
            ```
        """.trimIndent()

        val interaction = AiChatInteractionParser.parse(content).interactions.single()
        val option = interaction.options.single()

        assertEquals(AiChatInteractionType.SINGLE_CHOICE, interaction.type)
        assertEquals("覆盖全部可用内容。", option.description)
        assertEquals("目标：完整处理（full）", AiChatInteractionParser.buildPrompt(interaction, option))
    }

    @Test
    fun hidesLegacyMetadataBlocksButOnlyAsCompatibilityArtifacts() {
        val content = """
            历史报告。

            ```book_scan_delta
            {"schema_version":1,"work_key":"示例"}
            ```
            <character_scan_meta>{"version":1}</character_scan_meta>
        """.trimIndent()

        val parsed = AiChatInteractionParser.parse(content)

        assertEquals("历史报告。", parsed.content)
        assertTrue(parsed.hasHiddenArtifacts)
        assertTrue(parsed.interactions.isEmpty())
    }

    @Test
    fun parsesVersion2MultiTagStanceContract() {
        val parsed = AiChatInteractionParser.parse(validMultiTagStance())
        val interaction = parsed.interactions.single()

        assertEquals("", parsed.content)
        assertEquals(2, interaction.version)
        assertEquals(AiChatInteractionType.MULTI_TAG_STANCE, interaction.type)
        assertEquals(listOf("setting.system_present", "format.live_audience"), interaction.items.map { it.id })
        assertEquals(listOf("like", "neutral", "avoid"), interaction.options.map { it.value })
        assertEquals(
            listOf("更偏好这类作品", "可以接受", "会因此劝退"),
            interaction.options.map { it.label }
        )
        assertEquals("暂不设置", interaction.skip?.label)
        assertEquals("tag_stances", interaction.memoryPatch?.mapField)
        assertEquals("stance", interaction.memoryPatch?.valueField)
        assertEquals(listOf("schema_version", "profile_id"), interaction.memoryPatch?.identityFields)
        assertEquals(
            AiChatInteractionMemoryBaseMismatch.REPLACE,
            interaction.memoryPatch?.onBaseMismatch
        )
    }

    @Test
    fun acceptsIdAsLegacyAliasWhenMultiTagOptionValueIsMissing() {
        val idOnlyOptions = validMultiTagStance().replace(
            "\"value\":\"like\"",
            "\"id\":\"like\""
        ).replace(
            "\"value\":\"neutral\"",
            "\"id\":\"neutral\""
        ).replace(
            "\"value\":\"avoid\"",
            "\"id\":\"avoid\""
        )

        val parsed = AiChatInteractionParser.parse(idOnlyOptions)

        assertEquals(listOf("like", "neutral", "avoid"), parsed.interactions.single().options.map { it.value })
        assertEquals("", parsed.content)
    }

    @Test
    fun rejectsConflictingOptionIdAndValueWithoutHidingTheBlock() {
        val conflictingOption = validMultiTagStance().replace(
            "{\"label\":\"更偏好这类作品\",\"value\":\"like\"}",
            "{\"label\":\"更偏好这类作品\",\"value\":\"like\",\"id\":\"avoid\"}"
        )

        val parsed = AiChatInteractionParser.parse(conflictingOption)

        assertTrue(parsed.interactions.isEmpty())
        assertTrue(parsed.content.contains("multi_tag_stance"))
        assertTrue(parsed.content.contains("legado-interaction"))
    }

    @Test
    fun rejectsInvalidMultiTagStanceContractsWithoutHidingThem() {
        val duplicateItems = validMultiTagStance().replace(
            "format.live_audience",
            "setting.system_present"
        )
        val invalidStance = validMultiTagStance().replace(
            "{\"label\":\"会因此劝退\",\"value\":\"avoid\"}",
            "{\"label\":\"会因此劝退\",\"value\":\"dislike\"}"
        )
        val missingSummary = validMultiTagStance().replace(
            "本书以系统任务推动若干关键行动。",
            ""
        )

        listOf(duplicateItems, invalidStance, missingSummary).forEach { content ->
            val parsed = AiChatInteractionParser.parse(content)
            assertTrue(parsed.interactions.isEmpty())
            assertTrue(parsed.content.contains("multi_tag_stance"))
            assertTrue(parsed.content.contains("legado-interaction"))
        }
    }

    @Test
    fun normalizesLegacyStanceLabelsToCurrentDecisionImpactCopy() {
        val legacyLabels = validMultiTagStance()
            .replace("更偏好这类作品", "喜欢")
            .replace("可以接受", "无所谓")
            .replace("会因此劝退", "不喜欢")

        val interaction = AiChatInteractionParser.parse(legacyLabels).interactions.single()

        assertEquals(
            listOf("更偏好这类作品", "可以接受", "会因此劝退"),
            interaction.options.map { it.label }
        )
    }

    @Test
    fun rejectsMoreThanFiveMultiTagItems() {
        val itemTemplate =
            "{\"id\":\"tag.%d\",\"label\":\"标签%d\",\"description\":\"已在当前作品中发现。\"}"
        val sixItems = (1..6).joinToString(",") { index ->
            itemTemplate.format(index, index)
        }
        val content = validMultiTagStance(itemsJson = sixItems)

        val parsed = AiChatInteractionParser.parse(content)

        assertTrue(parsed.interactions.isEmpty())
        assertTrue(parsed.content.contains("tag.6"))
    }

    @Test
    fun keepsUnknownFencedInteractionVisible() {
        val content = """
            前置说明。

            ```legado-interaction
            {"version":2,"id":"future","type":"future_interaction"}
            ```
        """.trimIndent()

        val parsed = AiChatInteractionParser.parse(content)

        assertTrue(parsed.interactions.isEmpty())
        assertTrue(parsed.content.contains("前置说明。"))
        assertTrue(parsed.content.contains("future_interaction"))
    }

    private fun validMultiTagStance(
        itemsJson: String = """
            {"id":"setting.system_present","label":"有系统","description":"本书以系统任务推动若干关键行动。"},
            {"id":"format.live_audience","label":"直播围观","description":"存在大量观众反应。"}
        """.trimIndent()
    ): String {
        return """
            ```legado-interaction
            {
              "version": 2,
              "id": "reader_preference_detected_tags",
              "type": "multi_tag_stance",
              "title": "这些元素你怎么看？",
              "description": "只保存明确选择，未选保持未知。",
              "items": [
            ${itemsJson.prependIndent("    ")}
              ],
              "options": [
                {"label":"更偏好这类作品","value":"like"},
                {"label":"可以接受","value":"neutral"},
                {"label":"会因此劝退","value":"avoid"}
              ],
              "memory_patch": {
                "operation": "json_map_merge",
                "scope_type": "global",
                "scope_key": "default",
                "domain": "reader_preference",
                "memory_type": "global_profile",
                "title": "默认阅读偏好",
                "content": "用户明确确认的稀疏阅读偏好",
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
              },
              "submit": {"label":"保存并重新评估"},
              "skip": {"label":"暂不设置"}
            }
            ```
        """.trimIndent()
    }
}
