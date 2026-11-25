package io.legado.app.help.ai

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTtsStoryboardHelperTest {

    @Test
    fun truncatedHalfIsSplitAgainWithoutRepeatingSuccessfulBatches() = runBlocking {
        val units = List(7) { AiTtsStoryboardHelper.CandidateUnit(unitId = "u$it") }
        val batchSizes = mutableListOf<Int>()
        val failedBatchSizes = mutableListOf<Int>()

        val assignments = AiTtsStoryboardHelper.requestStoryboardUnitBatches(
            units,
            onSplit = { count, _ -> failedBatchSizes += count }
        ) { batch ->
            batchSizes += batch.size
            if (batch.size > 3) throw AiTtsStoryboardHelper.StoryboardOutputTruncatedException()
            batch.map { AiTtsStoryboardHelper.ModelUnitResult(unitId = it.unitId) }
        }

        assertEquals(listOf(7, 4, 2, 2, 3), batchSizes)
        assertEquals(listOf(7, 4), failedBatchSizes)
        assertEquals(units.map { it.unitId }, assignments.map { it.unitId })
    }

    @Test
    fun persistentlyTruncatedOutputStopsAtTheRetryLimit() {
        val units = List(8) { AiTtsStoryboardHelper.CandidateUnit(unitId = "u$it") }
        val batchSizes = mutableListOf<Int>()

        assertThrows(AiTtsStoryboardHelper.StoryboardOutputTruncatedException::class.java) {
            runBlocking {
                AiTtsStoryboardHelper.requestStoryboardUnitBatches(units, onSplit = { _, _ -> }) { batch ->
                    batchSizes += batch.size
                    throw AiTtsStoryboardHelper.StoryboardOutputTruncatedException()
                }
            }
        }

        assertEquals(listOf(8, 4, 2), batchSizes)
    }

    @Test
    fun invalidJsonKeepsTheSingleSplitRetry() {
        val units = List(8) { AiTtsStoryboardHelper.CandidateUnit(unitId = "u$it") }
        val batchSizes = mutableListOf<Int>()
        val invalidJson = com.google.gson.JsonSyntaxException("fixture invalid JSON")

        val error = assertThrows(com.google.gson.JsonSyntaxException::class.java) {
            runBlocking {
                AiTtsStoryboardHelper.requestStoryboardUnitBatches(units, onSplit = { _, _ -> }) { batch ->
                    batchSizes += batch.size
                    throw invalidJson
                }
            }
        }

        assertEquals(invalidJson, error)
        assertEquals(listOf(8, 4), batchSizes)
    }

    @Test
    fun fallbackRequestCombinesOnlyWhenAutomationAndActorGuidanceAreOff() {
        assertTrue(
            AiTtsStoryboardHelper.shouldCombineFallbackRequest(
                autoCreateTemporaryRoles = false,
                autoAssignVoices = false,
                capabilities = listOf("style_tags", "emotion")
            )
        )
        assertFalse(
            AiTtsStoryboardHelper.shouldCombineFallbackRequest(
                autoCreateTemporaryRoles = true,
                autoAssignVoices = false,
                capabilities = emptyList()
            )
        )
        assertFalse(
            AiTtsStoryboardHelper.shouldCombineFallbackRequest(
                autoCreateTemporaryRoles = false,
                autoAssignVoices = false,
                capabilities = listOf("performance_instruction")
            )
        )
    }

    @Test
    fun combinedFallbackResponseValidatesScenesAndUnitsTogether() {
        val paragraphs = listOf(
            AiTtsStoryboardHelper.ContextParagraph(0, "陈升走进车棚。"),
            AiTtsStoryboardHelper.ContextParagraph(1, "“你来了？”")
        )
        val units = listOf(
            AiTtsStoryboardHelper.CandidateUnit(
                unitId = "u1",
                ranges = listOf(AiTtsStoryboardHelper.TextRange(paragraphIndex = 1)),
                textPreview = "“你来了？”"
            )
        )
        val raw = """
            {
              "scenes": [{
                "sceneId": "scene_1",
                "title": "陈升走进车棚",
                "startParagraphIndex": 0,
                "endParagraphIndex": 1
              }],
              "units": [{
                "unitId": "u1",
                "roleType": "character",
                "characterName": "",
                "characterId": 0,
                "castRoleId": 0,
                "speakerGender": "unknown",
                "identityType": "guest",
                "nameType": "generic_label",
                "identityEvidence": "contextual",
                "genderEvidence": "unknown",
                "mergeCastRoleIds": [],
                "status": "unknown",
                "confidence": 0.8,
                "evidence": "场景人物开口",
                "performanceContext": [],
                "performanceInstruction": "",
                "styleConcepts": [],
                "emotion": null,
                "emotionIntensity": null,
                "expressiveConfidence": null
              }],
              "newCharacters": []
            }
        """.trimIndent()

        val (scenes, assignments) = AiTtsStoryboardHelper.parseAndValidateCombinedForTest(
            raw = raw,
            paragraphs = paragraphs,
            targetUnits = units
        )

        assertEquals("陈升走进车棚", scenes.single().title)
        assertEquals("u1", assignments.single().unitId)
    }

    @Test
    fun cacheIdentityDoesNotDependOnMutableCharacterProfiles() {
        val key = AiTtsStoryboardHelper.storyboardCacheKeyForTest(
            book = Book(bookUrl = "book-url", name = "Book", author = "Author"),
            chapterIndex = 7,
            chapterTitle = "Chapter",
            contentHash = "content-hash",
            mode = "performance",
            capabilities = listOf("scene_context", "performance_instruction"),
            providerId = "provider",
            modelId = "model"
        )

        assertEquals("14bce68d36a03f7f6f3ff5609ee1a42e", key)
    }

    @Test
    fun identityAndExpressiveCachesHaveIndependentInvalidationKeys() {
        val book = Book(bookUrl = "book-url", name = "Book", author = "Author")
        val identity = AiTtsStoryboardHelper.storyboardIdentityCacheKeyForTest(
            book = book,
            chapterIndex = 7,
            chapterTitle = "Chapter",
            contentHash = "content-hash",
            providerId = "provider",
            modelId = "model"
        )
        val scene = AiTtsStoryboardHelper.storyboardExpressiveCacheKeyForTest(
            identity,
            listOf("scene_context")
        )
        val actor = AiTtsStoryboardHelper.storyboardExpressiveCacheKeyForTest(
            identity,
            listOf("performance_instruction")
        )

        assertEquals(identity, AiTtsStoryboardHelper.storyboardIdentityCacheKeyForTest(
            book, 7, "Chapter", "content-hash", "provider", "model"
        ))
        assertTrue(scene != actor)
    }

    @Test
    fun semanticSceneTitlesAreValidatedAndKept() {
        val paragraphs = listOf(
            AiTtsStoryboardHelper.ContextParagraph(0, "陈升推车走进车棚。"),
            AiTtsStoryboardHelper.ContextParagraph(1, "沈言卿站在车棚门口。"),
            AiTtsStoryboardHelper.ContextParagraph(2, "回家后，陈升向妈妈撒娇。")
        )

        val scenes = AiTtsStoryboardHelper.parseAndValidateScenesForTest(
            raw = """
                {"scenes":[
                  {"sceneId":"scene_1","title":"陈升与沈言卿在车棚","startParagraphIndex":0,"endParagraphIndex":1},
                  {"sceneId":"scene_2","title":"陈升向妈妈撒娇","startParagraphIndex":2,"endParagraphIndex":2}
                ]}
            """.trimIndent(),
            paragraphs = paragraphs
        )

        assertEquals(listOf("陈升与沈言卿在车棚", "陈升向妈妈撒娇"), scenes.map { it.title })
    }

    @Test
    fun narratedQuoteReferencesAreNotHintedAsDialogue() {
        val recalled = "那句“靠你了”总往她心窝子钻，钻得她暖暖热热。"
        val summarized = "电话里，沈言卿娇憨甜脆地说了好长一串“很想”。"
        val direct = "“很想很想很想很想……”"
        val colonDialogue = "陈升问：“有多想？”"
        val thought = "她心想：“不能再这样了。”"

        assertEquals("narrator", quoteHint(recalled))
        assertEquals("narrator", quoteHint(summarized))
        assertEquals("character", quoteHint(direct))
        assertEquals("character", quoteHint(colonDialogue))
        assertEquals("thought", quoteHint(thought))
        assertEquals("narrator", AiTtsStoryboardHelper.routedRoleType("narrator", "character"))
        assertEquals("character", AiTtsStoryboardHelper.routedRoleType("character", "character"))
    }

    @Test
    fun duplicateTargetTextIsRemovedWithoutDroppingUsefulContext() {
        val result = AiTtsStoryboardHelper.sanitizePerformanceContext(
            context = listOf(
                "安秋月回答：“QQ1314……，我……我没有手机。”",
                "陈升刚问了她的 QQ 和手机号。",
                "她低声回答，仍有些局促。"
            ),
            targetText = "“QQ1314……，我……我没有手机。”",
            enabled = true
        )

        assertEquals(
            listOf("陈升刚问了她的 QQ 和手机号。", "她低声回答，仍有些局促。"),
            result
        )
    }

    @Test
    fun contextIsBoundedAndDisabledOutsidePerformanceDialogue() {
        val longItem = "情".repeat(100)
        val result = AiTtsStoryboardHelper.sanitizePerformanceContext(
            context = listOf("  ", longItem, longItem, "第二条", "第三条", "第四条"),
            targetText = "当前对白",
            enabled = true
        )

        assertEquals(3, result.size)
        assertEquals(80, result.first().length)
        assertEquals(emptyList<String>(), AiTtsStoryboardHelper.sanitizePerformanceContext(
            context = listOf("不应透传"),
            targetText = "当前对白",
            enabled = false
        ))
    }

    @Test
    fun actorCapabilityIncludesSceneAndLoadsModulesInStableOrder() {
        val capabilities = AiTtsStoryboardHelper.resolveStoryboardSkillCapabilities(
            declaredCapabilities = setOf("performance_instruction")
        )

        assertEquals(listOf("scene_context", "performance_instruction"), capabilities)
        assertEquals(
            listOf(
                "skills/tts_storyboard/modules/protocol.md",
                "skills/tts_storyboard/modules/base-routing.md",
                "skills/tts_storyboard/modules/scene-context.md",
                "skills/tts_storyboard/modules/performance-instruction.md"
            ),
            AiTtsStoryboardHelper.storyboardSkillAssets(capabilities)
        )
        assertEquals(
            listOf("scene_context"),
            AiTtsStoryboardHelper.resolveStoryboardSkillCapabilities(
                declaredCapabilities = setOf("scene_context")
            )
        )
        assertEquals(
            emptyList<String>(),
            AiTtsStoryboardHelper.resolveStoryboardSkillCapabilities(
                declaredCapabilities = emptySet()
            )
        )
    }

    @Test
    fun performanceInstructionIsShortAndProviderNeutral() {
        assertEquals(
            "刚哭过，开口迟疑，后半句逐渐变轻",
            AiTtsStoryboardHelper.sanitizePerformanceInstruction(
                instruction = "  刚哭过，开口迟疑，后半句逐渐变轻  ",
                targetText = "我生活费丢了。",
                enabled = true
            )
        )
        assertEquals(
            "",
            AiTtsStoryboardHelper.sanitizePerformanceInstruction(
                instruction = "我生活费丢了。",
                targetText = "我生活费丢了。",
                enabled = true
            )
        )
        assertEquals(
            "",
            AiTtsStoryboardHelper.sanitizePerformanceInstruction(
                instruction = "开口迟疑",
                targetText = "我生活费丢了。",
                enabled = false
            )
        )
    }

    @Test
    fun basicModeAcceptsStableExpressiveShapeAndClearsUnsupportedFields() {
        val raw = """
            {
              "units": [
                {
                  "unitId": "u1",
                  "roleType": "character",
                  "characterName": "",
                  "characterId": 0,
                  "castRoleId": 0,
                  "speakerGender": "unknown",
                  "identityType": "guest",
                  "nameType": "generic_label",
                  "identityEvidence": "contextual",
                  "genderEvidence": "unknown",
                  "mergeCastRoleIds": [],
                  "status": "unknown",
                  "confidence": 0.8,
                  "evidence": "场景路人开口",
                  "performanceContext": ["不应透传"],
                  "performanceInstruction": "不应透传",
                  "styleConcepts": ["不应透传"],
                  "emotion": "愤怒",
                  "emotionIntensity": 0.9,
                  "expressiveConfidence": 0.9
                }
              ],
              "newCharacters": []
            }
        """.trimIndent()

        val unit = AiTtsStoryboardHelper.parseAndValidateForTest(
            raw = raw,
            targetUnits = listOf(
                AiTtsStoryboardHelper.CandidateUnit(
                    unitId = "u1",
                    roleHint = "character",
                    textPreview = "你好。"
                )
            ),
            capabilities = emptyList(),
            allowNewCharacters = false
        ).single()

        assertEquals(emptyList<String>(), unit.performanceContext)
        assertEquals("", unit.performanceInstruction)
        assertEquals(emptyList<String>(), unit.styleConcepts)
        assertEquals(null, unit.emotion)
        assertEquals(null, unit.emotionIntensity)
        assertEquals(null, unit.expressiveConfidence)
    }

    @Test
    fun classifiedSpeakerIsKeptEvenWhenGenderNeedsLaterCorrection() {
        assertTrue(
            AiTtsStoryboardHelper.shouldKeepUnboundSpeaker(
                displayName = "阿糯",
                identityType = StoryboardSegment.IdentityType.STABLE_CANDIDATE,
                nameType = StoryboardSegment.NameType.PROPER_NAME
            )
        )
        assertTrue(
            AiTtsStoryboardHelper.shouldKeepUnboundSpeaker(
                displayName = "小道童",
                identityType = StoryboardSegment.IdentityType.PENDING,
                nameType = StoryboardSegment.NameType.GENERIC_LABEL
            )
        )
        assertFalse(
            AiTtsStoryboardHelper.shouldKeepUnboundSpeaker(
                displayName = "",
                identityType = StoryboardSegment.IdentityType.NONE,
                nameType = StoryboardSegment.NameType.UNKNOWN
            )
        )
    }

    @Test
    fun pendingAndGuestSpeakersRemainDialogueWithoutKnownGender() {
        val pending = AiTtsStoryboardHelper.ModelUnitResult(
            unitId = "pending",
            roleType = "character",
            characterName = "小道童",
            speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
            identityType = StoryboardSegment.IdentityType.PENDING,
            nameType = StoryboardSegment.NameType.GENERIC_LABEL,
            status = "unknown"
        )
        val guest = pending.copy(
            unitId = "guest",
            characterName = "镇魔司下属",
            speakerGender = StoryboardSegment.SpeakerGender.MALE,
            identityType = StoryboardSegment.IdentityType.GUEST
        )

        assertEquals(StoryboardSegmentType.DIALOGUE, AiTtsStoryboardHelper.resolvedSegmentType(pending))
        assertEquals(StoryboardSegmentType.DIALOGUE, AiTtsStoryboardHelper.resolvedSegmentType(guest))
    }

    @Test
    fun explicitAdjacentAddressCorrectsUnknownSpeakerGender() {
        val address = AiTtsStoryboardHelper.ModelUnitResult(
            unitId = "u1",
            roleType = "character",
            characterName = "沈棠",
            castRoleId = 7,
            speakerGender = StoryboardSegment.SpeakerGender.FEMALE,
            genderEvidence = StoryboardSegment.Evidence.EXPLICIT
        )
        val reply = AiTtsStoryboardHelper.ModelUnitResult(
            unitId = "u2",
            roleType = "character",
            characterName = "小道童",
            castRoleId = 3,
            speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
            genderEvidence = StoryboardSegment.Evidence.UNKNOWN,
            evidence = "提示语: 小道童高兴起来"
        )
        val units = listOf(
            AiTtsStoryboardHelper.CandidateUnit(
                unitId = "u1",
                ranges = listOf(AiTtsStoryboardHelper.TextRange(paragraphIndex = 10)),
                textPreview = "“小妹妹你以后会比任何人都漂亮。”"
            ),
            AiTtsStoryboardHelper.CandidateUnit(
                unitId = "u2",
                ranges = listOf(AiTtsStoryboardHelper.TextRange(paragraphIndex = 11)),
                textPreview = "“真的吗？师父嫌我胖。”"
            )
        )

        val result = AiTtsStoryboardHelper.applyAdjacentGenderEvidence(
            assignments = listOf(address, reply),
            targetUnits = units
        ).last()

        assertEquals(StoryboardSegment.SpeakerGender.FEMALE, result.speakerGender)
        assertEquals(StoryboardSegment.Evidence.EXPLICIT, result.genderEvidence)
        assertTrue(result.evidence.contains("小妹妹"))
    }

    @Test
    fun properNameCanNotRemainGuestIdentity() {
        assertEquals(
            StoryboardSegment.IdentityType.STABLE_CANDIDATE,
            AiTtsStoryboardHelper.normalizedUnboundIdentityType(
                identityType = StoryboardSegment.IdentityType.GUEST,
                nameType = StoryboardSegment.NameType.PROPER_NAME
            )
        )
        assertEquals(
            StoryboardSegment.IdentityType.GUEST,
            AiTtsStoryboardHelper.normalizedUnboundIdentityType(
                identityType = StoryboardSegment.IdentityType.GUEST,
                nameType = StoryboardSegment.NameType.GENERIC_LABEL
            )
        )
    }

    @Test
    fun explicitNicknameOwnerIsRecoveredFromNarrationWithoutSpokenUnit() {
        val mappings = AiTtsStoryboardHelper.findExplicitAliasMappings(
            paragraphs = listOf(
                AiTtsStoryboardHelper.ContextParagraph(0, "QQ上有一个添加信息，打开一看，青青子衿是谁？"),
                AiTtsStoryboardHelper.ContextParagraph(1, "来源是群添加。"),
                AiTtsStoryboardHelper.ContextParagraph(2, "到同学群看了下，哦，是沈言卿。")
            ),
            canonicalNames = listOf("陈升", "沈言卿", "赵文博")
        )

        assertEquals("沈言卿", mappings["青青子衿"])
    }

    @Test
    fun explicitNicknameAssignmentKeepsAliasAndCreatesFormalIdentityLink() {
        val character = BookCharacter(id = 8L, name = "沈言卿")
        val assignment = AiTtsStoryboardHelper.parseAndValidateForTest(
            raw = explicitAliasResponse(characterId = 8L),
            targetUnits = listOf(
                AiTtsStoryboardHelper.CandidateUnit(unitId = "u1", textPreview = "你好。")
            ),
            capabilities = emptyList(),
            allowNewCharacters = false,
            knownCharacters = listOf(character)
        ).single()

        assertEquals("青青子衿", assignment.characterName)
        val link = AiTtsStoryboardHelper.identityLinksFromAssignments(
            assignments = listOf(assignment),
            characters = listOf(character),
            castRoles = emptyList()
        ).single()
        assertEquals("青青子衿", link.aliasName)
        assertEquals(8L, link.characterId)
        assertEquals(null, link.castRoleId)
    }

    @Test
    fun explicitNicknameAssignmentCreatesTemporaryRoleIdentityLink() {
        val role = BookTtsCastRole(id = 6L, name = "沈言卿")
        val link = AiTtsStoryboardHelper.identityLinksFromAssignments(
            assignments = listOf(
                AiTtsStoryboardHelper.ModelUnitResult(
                    unitId = "u1",
                    roleType = "character",
                    characterName = "青青子衿",
                    castRoleId = 6L,
                    identityType = StoryboardSegment.IdentityType.CAST_ROLE,
                    nameType = StoryboardSegment.NameType.ALIAS,
                    identityEvidence = StoryboardSegment.Evidence.EXPLICIT,
                    confidence = 0.95f,
                    evidence = "正文明确说明青青子衿是沈言卿"
                )
            ),
            characters = emptyList(),
            castRoles = listOf(role)
        ).single()

        assertEquals("青青子衿", link.aliasName)
        assertEquals(null, link.characterId)
        assertEquals(6L, link.castRoleId)
    }

    @Test
    fun contextualNicknameAssignmentDoesNotCreateIdentityLink() {
        val links = AiTtsStoryboardHelper.identityLinksFromAssignments(
            assignments = listOf(
                AiTtsStoryboardHelper.ModelUnitResult(
                    characterName = "青青子衿",
                    castRoleId = 6L,
                    nameType = StoryboardSegment.NameType.ALIAS,
                    identityEvidence = StoryboardSegment.Evidence.CONTEXTUAL,
                    confidence = 0.95f
                )
            ),
            characters = emptyList(),
            castRoles = listOf(BookTtsCastRole(id = 6L, name = "沈言卿"))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun nicknameQuestionWithoutExplicitOwnerDoesNotGuessIdentity() {
        val mappings = AiTtsStoryboardHelper.findExplicitAliasMappings(
            paragraphs = listOf(
                AiTtsStoryboardHelper.ContextParagraph(0, "青青子衿是谁？群里没人回答。"),
                AiTtsStoryboardHelper.ContextParagraph(1, "沈言卿稍后也上线了。")
            ),
            canonicalNames = listOf("沈言卿")
        )

        assertTrue(mappings.isEmpty())
    }

    private fun explicitAliasResponse(characterId: Long): String = """
        {
          "units": [{
            "unitId": "u1",
            "roleType": "character",
            "characterName": "青青子衿",
            "characterId": $characterId,
            "castRoleId": 0,
            "speakerGender": "female",
            "identityType": "formal_character",
            "nameType": "alias",
            "identityEvidence": "explicit",
            "genderEvidence": "unknown",
            "mergeCastRoleIds": [],
            "status": "assigned",
            "confidence": 0.95,
            "evidence": "正文明确说明青青子衿是沈言卿",
            "performanceContext": [],
            "performanceInstruction": "",
            "styleConcepts": [],
            "emotion": null,
            "emotionIntensity": null,
            "expressiveConfidence": null
          }],
          "newCharacters": []
        }
    """.trimIndent()

    private fun quoteHint(text: String): String {
        val start = text.indexOf('“')
        val end = text.indexOf('”', start + 1) + 1
        return AiTtsStoryboardHelper.quoteRoleHint(text, start, end)
    }
}
