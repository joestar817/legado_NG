package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.AgentMemory
import io.legado.app.help.ai.runtime.AgentSkillRuntimeDeclaration
import io.legado.app.web.mcp.McpMemoryAccessRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatInteractionMemoryTest {

    @Test
    fun createsSparseProfileFromExplicitSelectionsOnly() {
        val interaction = interaction()

        val merged = AiChatInteractionMemoryMerge.merge(
            interaction = interaction,
            existingDataJson = null,
            selectedValues = linkedMapOf(
                "setting.system_present" to "neutral",
                "format.live_audience" to "avoid"
            )
        )

        assertEquals(2, merged.get("schema_version").asInt)
        assertTrue(merged.getAsJsonObject("dimension_answers").entrySet().isEmpty())
        assertEquals(0, merged.getAsJsonArray("custom_rules").size())
        val stances = merged.getAsJsonObject("tag_stances")
        assertEquals(2, stances.size())
        assertEquals("neutral", stances.getAsJsonObject("setting.system_present").get("stance").asString)
        assertEquals("ui_choice", stances.getAsJsonObject("setting.system_present").get("source_kind").asString)
        assertEquals("有系统：可以接受", stances.getAsJsonObject("setting.system_present").get("source_text").asString)
        assertTrue(stances.getAsJsonObject("setting.system_present").get("confirmed_by_user").asBoolean)
        assertFalse(stances.has("style.slow_burn"))
    }

    @Test
    fun preservesUnselectedProfileDataAndOverwritesOnlySelectedKey() {
        val existing = """
            {
              "schema_version":2,
              "profile_id":"default",
              "dimension_answers":{"narrative_view":["single_male"]},
              "tag_stances":{
                "setting.system_present":{"stance":"avoid","source_kind":"ui_choice"},
                "style.slow_burn":{"stance":"like","source_kind":"natural_language"}
              },
              "custom_rules":[{"text":"不喜欢主角舔原著角色"}]
            }
        """.trimIndent()

        val merged = AiChatInteractionMemoryMerge.merge(
            interaction = interaction(),
            existingDataJson = existing,
            selectedValues = mapOf("setting.system_present" to "like")
        )

        assertEquals(
            "single_male",
            merged.getAsJsonObject("dimension_answers")
                .getAsJsonArray("narrative_view")[0].asString
        )
        assertEquals(1, merged.getAsJsonArray("custom_rules").size())
        val stances = merged.getAsJsonObject("tag_stances")
        assertEquals("like", stances.getAsJsonObject("setting.system_present").get("stance").asString)
        assertEquals("like", stances.getAsJsonObject("style.slow_burn").get("stance").asString)
    }

    @Test
    fun explicitReplaceDoesNotInferFieldsFromVersionOneProfile() {
        val legacy = """
            {
              "schema_version":1,
              "relationship_route":"harem",
              "defense_level":"low",
              "note":"legacy"
            }
        """.trimIndent()

        val merged = AiChatInteractionMemoryMerge.merge(
            interaction = interaction(),
            existingDataJson = legacy,
            selectedValues = mapOf("format.live_audience" to "avoid")
        )

        assertEquals(2, merged.get("schema_version").asInt)
        assertFalse(merged.has("relationship_route"))
        assertFalse(merged.has("defense_level"))
        assertFalse(merged.has("note"))
        assertEquals(1, merged.getAsJsonObject("tag_stances").size())
    }

    @Test
    fun rejectBaseMismatchAndMalformedExistingJsonFailClosed() {
        val rejectInteraction = interaction().let { current ->
            current.copy(
                memoryPatch = current.memoryPatch?.copy(
                    onBaseMismatch = AiChatInteractionMemoryBaseMismatch.REJECT
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryMerge.merge(
                interaction = rejectInteraction,
                existingDataJson = "{\"schema_version\":1}",
                selectedValues = mapOf("setting.system_present" to "avoid")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryMerge.merge(
                interaction = interaction(),
                existingDataJson = "not json",
                selectedValues = mapOf("setting.system_present" to "avoid")
            )
        }
    }

    @Test
    fun writerPersistsThroughTrustedSystemWorkflowAndBuildsHiddenContinuation() {
        val store = FakeStore()
        val fixture = writerFixture(store)

        val result = AiChatInteractionMemoryWriter.write(
            interaction = interaction(),
            selectedValues = linkedMapOf("setting.system_present" to "neutral"),
            agentMode = fixture.mode,
            activeSkill = fixture.snapshot,
            conversationId = "conversation-1",
            turnId = "turn-1",
            dependencies = fixture.dependencies
        )

        assertTrue(result.created)
        assertEquals("memory-1", result.memoryId)
        assertEquals("已保存 1 项阅读偏好", result.displayText)
        val continuation = JsonParser.parseString(result.continuationPrompt).asJsonObject
        assertEquals("interaction_result", continuation.get("type").asString)
        assertEquals("saved", continuation.get("status").asString)
        assertEquals("setting.system_present", continuation.getAsJsonArray("selected")[0].asJsonObject.get("id").asString)
        assertNotNull(store.memory)
        val stored = requireNotNull(store.memory)
        assertEquals("user_confirmed", stored.source)
        assertEquals("global_profile", stored.memoryType)
        assertEquals(
            "neutral",
            JsonParser.parseString(stored.dataJson).asJsonObject
                .getAsJsonObject("tag_stances")
                .getAsJsonObject("setting.system_present")
                .get("stance").asString
        )
    }

    @Test
    fun writerRejectsGeneralModeUntrustedSkillAndOutOfPolicyTarget() {
        val store = FakeStore()
        val fixture = writerFixture(store)

        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryWriter.write(
                interaction = interaction(),
                selectedValues = mapOf("setting.system_present" to "avoid"),
                agentMode = AgentModeRegistry.general,
                activeSkill = fixture.snapshot,
                conversationId = "conversation-1",
                turnId = "turn-1",
                dependencies = fixture.dependencies
            )
        }

        val untrustedDependencies = fixture.dependencies.copy(
            resolveSystemSkill = { _, _ -> fixture.skill.copy(userModified = true) }
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryWriter.write(
                interaction = interaction(),
                selectedValues = mapOf("setting.system_present" to "avoid"),
                agentMode = fixture.mode,
                activeSkill = fixture.snapshot,
                conversationId = "conversation-1",
                turnId = "turn-1",
                dependencies = untrustedDependencies
            )
        }

        val outOfPolicy = interaction().let { current ->
            current.copy(
                memoryPatch = current.memoryPatch?.copy(
                    scopeType = "conversation",
                    scopeKey = "conversation-1"
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryWriter.write(
                interaction = outOfPolicy,
                selectedValues = mapOf("setting.system_present" to "avoid"),
                agentMode = fixture.mode,
                activeSkill = fixture.snapshot,
                conversationId = "conversation-1",
                turnId = "turn-1",
                dependencies = fixture.dependencies
            )
        }
        assertEquals(null, store.memory)
    }

    @Test
    fun writerDoesNotWriteWhenMemoryDisabledOrInteractionSkipped() {
        val store = FakeStore()
        val fixture = writerFixture(store)
        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryWriter.write(
                interaction = interaction(),
                selectedValues = mapOf("setting.system_present" to "avoid"),
                agentMode = fixture.mode,
                activeSkill = fixture.snapshot,
                conversationId = "conversation-1",
                turnId = "turn-1",
                dependencies = fixture.dependencies.copy(memoryEnabled = { false })
            )
        }

        val skipped = AiChatInteractionMemoryWriter.skipped(interaction())

        assertEquals(null, store.memory)
        assertEquals(null, skipped.memoryId)
        assertEquals("skipped", JsonParser.parseString(skipped.continuationPrompt).asJsonObject.get("status").asString)
    }

    @Test
    fun writerRejectsInteractionPolicyViolationBeforeTouchingMemory() {
        val store = FakeStore()
        val fixture = writerFixture(store)

        assertThrows(IllegalArgumentException::class.java) {
            AiChatInteractionMemoryWriter.write(
                interaction = interaction(),
                selectedValues = mapOf("setting.system_present" to "avoid"),
                agentMode = fixture.mode,
                activeSkill = fixture.snapshot,
                conversationId = "conversation-1",
                turnId = "turn-1",
                dependencies = fixture.dependencies.copy(
                    validateInteraction = { _, _ ->
                        throw IllegalArgumentException("policy rejected")
                    }
                )
            )
        }

        assertEquals(null, store.memory)
    }

    private fun interaction(): AiChatInteraction {
        val baseData = JsonParser.parseString(
            """
                {
                  "schema_version":2,
                  "profile_id":"default",
                  "dimension_answers":{},
                  "tag_stances":{},
                  "custom_rules":[]
                }
            """.trimIndent()
        ).asJsonObject
        return AiChatInteraction(
            version = 2,
            id = "reader_preference_detected_tags",
            type = AiChatInteractionType.MULTI_TAG_STANCE,
            title = "这些元素你怎么看？",
            description = "只保存明确选择，未选保持未知。",
            options = listOf(
                AiChatInteractionOption("更偏好这类作品", "like", ""),
                AiChatInteractionOption("可以接受", "neutral", ""),
                AiChatInteractionOption("会因此劝退", "avoid", "")
            ),
            items = listOf(
                AiChatInteractionItem(
                    id = "setting.system_present",
                    label = "有系统",
                    description = "本书以系统任务推动若干关键行动。"
                ),
                AiChatInteractionItem(
                    id = "format.live_audience",
                    label = "直播围观",
                    description = "存在大量观众反应。"
                )
            ),
            submit = AiChatInteractionSubmit("保存并重新评估", ""),
            cancel = null,
            skip = AiChatInteractionSubmit("暂不设置", ""),
            memoryPatch = AiChatInteractionMemoryPatch(
                operation = AiChatInteractionMemoryPatchOperation.JSON_MAP_MERGE,
                scopeType = "global",
                scopeKey = "default",
                domain = "reader_preference",
                memoryType = "global_profile",
                title = "默认阅读偏好",
                content = "用户明确确认的稀疏阅读偏好",
                mapField = "tag_stances",
                valueField = "stance",
                identityFields = listOf("schema_version", "profile_id"),
                baseData = baseData,
                onBaseMismatch = AiChatInteractionMemoryBaseMismatch.REPLACE
            )
        )
    }

    private fun writerFixture(store: FakeStore): WriterFixture {
        val skill = AiSkillDefinition(
            id = "book_scan",
            name = "AI 扫书",
            summary = "test",
            scope = AiSkillScope.AGENT,
            prompt = "test",
            builtIn = true,
            userModified = false,
            runtime = AgentSkillRuntimeDeclaration(),
            revision = "52",
            contentHash = "trusted-hash"
        )
        val snapshot = AiActiveSkillSnapshot(
            skillId = skill.id,
            title = skill.name,
            prompt = skill.prompt,
            revision = skill.revision,
            runtimeRevision = "book_scan@52@trusted-hash",
            contentHash = skill.contentHash,
            runtime = skill.runtime
        )
        val mode = AgentModeDefinition(
            id = "test_book_scan",
            revision = "1",
            kind = AgentModeKind.SYSTEM,
            allowsUserSkills = false,
            allowsManualMcpCapabilities = false,
            systemWorkflowId = skill.id,
            systemSkillIds = listOf(skill.id),
            memoryPolicy = AgentModeMemoryPolicy(
                listOf(
                    McpMemoryAccessRange(
                        scopeType = "global",
                        scopeKey = "default",
                        domain = "reader_preference"
                    )
                )
            )
        )
        val dependencies = AiChatInteractionMemoryWriterDependencies(
            memoryEnabled = { true },
            store = store,
            resolveSystemSkill = { _, _ -> skill },
            now = { 1234L },
            newId = { "memory-1" }
        )
        return WriterFixture(skill, snapshot, mode, dependencies)
    }

    private data class WriterFixture(
        val skill: AiSkillDefinition,
        val snapshot: AiActiveSkillSnapshot,
        val mode: AgentModeDefinition,
        val dependencies: AiChatInteractionMemoryWriterDependencies
    )

    private class FakeStore : AiChatInteractionMemoryStore {
        var memory: AgentMemory? = null

        override fun writeTransaction(
            block: () -> AiChatInteractionMemoryWriteResult
        ): AiChatInteractionMemoryWriteResult = block()

        override fun findLatest(patch: AiChatInteractionMemoryPatch): AgentMemory? = memory

        override fun upsert(memory: AgentMemory) {
            this.memory = memory
        }
    }
}
