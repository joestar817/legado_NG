package io.legado.app.help.tts

import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookTtsCastRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTtsCastingCoordinatorTest {

    @Test
    fun stableCastName_onlyAppliesStructuralSafetyNotSemanticBlacklist() {
        assertTrue(BookTtsCastingCoordinator.isStableCastName("服务员"))
        assertTrue(BookTtsCastingCoordinator.isStableCastName("一个男人"))
        assertFalse(BookTtsCastingCoordinator.isStableCastName("他"))
        assertFalse(BookTtsCastingCoordinator.isStableCastName("待确认说话人"))
        assertTrue(BookTtsCastingCoordinator.isStableCastName("赵文博"))
        assertTrue(BookTtsCastingCoordinator.isStableCastName("刘老师"))
    }

    @Test
    fun voiceGender_prefiltersKnownGenderButKeepsUnknownMetadata() {
        assertTrue(BookTtsCastingCoordinator.voiceMatchesGender("female", BookCharacter.Gender.FEMALE))
        assertFalse(BookTtsCastingCoordinator.voiceMatchesGender("female", BookCharacter.Gender.MALE))
        assertTrue(BookTtsCastingCoordinator.voiceMatchesGender("男声", BookCharacter.Gender.MALE))
        assertTrue(BookTtsCastingCoordinator.voiceMatchesGender(null, BookCharacter.Gender.FEMALE))
        assertTrue(BookTtsCastingCoordinator.voiceMatchesGender("neutral", BookCharacter.Gender.MALE))
    }

    @Test
    fun automaticAssignmentRequiresExplicitDecisionAndConfidence() {
        val allowed = setOf("voice_a")
        assertEquals(
            "voice_a",
            BookTtsCastingCoordinator.acceptedAutoAssignmentVoiceId(
                voiceId = "voice_a",
                decision = "assigned",
                confidence = 0.82f,
                allowedVoiceIds = allowed
            )
        )
        assertEquals(
            null,
            BookTtsCastingCoordinator.acceptedAutoAssignmentVoiceId(
                voiceId = "voice_a",
                decision = "assigned",
                confidence = 0.42f,
                allowedVoiceIds = allowed
            )
        )
        assertEquals(
            null,
            BookTtsCastingCoordinator.acceptedAutoAssignmentVoiceId(
                voiceId = null,
                decision = "unassigned",
                confidence = 0.1f,
                allowedVoiceIds = allowed
            )
        )
    }

    @Test
    fun sceneOverrideRequiresDifferentVoiceStrongConfidenceAndReason() {
        val allowed = setOf("voice_scene")
        assertEquals(
            "voice_scene",
            BookTtsCastingCoordinator.acceptedSceneOverrideVoiceId(
                voiceId = "voice_scene",
                decision = "assigned",
                confidence = 0.9f,
                reason = "样本显示年龄感与基础音色不符，候选更贴合",
                baseVoiceId = "voice_base",
                allowedVoiceIds = allowed
            )
        )
        assertEquals(
            null,
            BookTtsCastingCoordinator.acceptedSceneOverrideVoiceId(
                voiceId = "voice_scene",
                decision = "assigned",
                confidence = 0.82f,
                reason = "只体现当前情绪",
                baseVoiceId = "voice_base",
                allowedVoiceIds = allowed
            )
        )
        assertEquals(
            null,
            BookTtsCastingCoordinator.acceptedSceneOverrideVoiceId(
                voiceId = "voice_base",
                decision = "assigned",
                confidence = 0.95f,
                reason = "仍使用基础音色",
                baseVoiceId = "voice_base",
                allowedVoiceIds = allowed + "voice_base"
            )
        )
        assertEquals(
            null,
            BookTtsCastingCoordinator.acceptedSceneOverrideVoiceId(
                voiceId = "voice_scene",
                decision = "assigned",
                confidence = 0.95f,
                reason = " ",
                baseVoiceId = "voice_base",
                allowedVoiceIds = allowed
            )
        )
    }

    @Test
    fun identityName_normalizesOuterPunctuationAndWhitespace() {
        assertEquals(
            "赵文博",
            BookTtsCastingCoordinator.normalizeIdentityName("  “赵文博” ： ")
        )
        assertEquals(
            "john smith",
            BookTtsCastingCoordinator.normalizeIdentityName("John   Smith")
        )
    }

    @Test
    fun blockedIdentityNames_includeDisabledFormalAndIgnoredTemporaryAliases() {
        val blocked = BookTtsCastingCoordinator.blockedIdentityNames(
            characters = listOf(
                BookCharacter(name = "保留角色", enabled = true),
                BookCharacter(
                    name = "禁用正式角色",
                    aliasesJson = "[\"正式别名\"]",
                    enabled = false
                )
            ),
            roles = listOf(
                BookTtsCastRole(name = "保留临时角色", ignored = false),
                BookTtsCastRole(
                    name = "禁用临时角色",
                    aliasesJson = "[\"临时别名\"]",
                    ignored = true
                )
            )
        )

        assertEquals(
            setOf("禁用正式角色", "正式别名", "禁用临时角色", "临时别名"),
            blocked
        )
    }

    @Test
    fun explicitAliasMapping_canResolveCanonicalNameDiscoveredInSameStoryboard() {
        val text = "QQ上有一个添加信息，打开一看，青青子衿是谁？\n" +
            "来源是群添加。\n到同学群看了下，哦，是沈言卿。"

        assertEquals(
            mapOf("青青子衿" to "沈言卿"),
            BookTtsCastingCoordinator.findExplicitAliasMappings(
                text = text,
                canonicalNames = listOf("沈言卿", "青青子衿")
            )
        )
    }

    @Test
    fun confirmedAliasIsPermanentIdentityInsteadOfCacheScopedEvidence() {
        assertEquals(
            listOf("青青子衿"),
            BookTtsCastingCoordinator.persistentAliases(
                canonicalName = "沈言卿",
                names = listOf("沈言卿", "青青子衿", "青青子衿")
            )
        )
    }

    @Test
    fun explicitEvidenceAndProperNamesHaveHigherCorrectionPriority() {
        assertTrue(
            BookTtsCastingCoordinator.evidenceRank(BookTtsCastRole.Evidence.EXPLICIT) >
                BookTtsCastingCoordinator.evidenceRank(BookTtsCastRole.Evidence.INFERRED)
        )
        assertTrue(
            BookTtsCastingCoordinator.nameTypeRank(BookTtsCastRole.NameType.PROPER_NAME) >
                BookTtsCastingCoordinator.nameTypeRank(BookTtsCastRole.NameType.GENERIC_LABEL)
        )
    }

    @Test
    fun migratedUnknownRoleCanBeHiddenPendingButConfirmedRoleStaysStable() {
        assertTrue(
            BookTtsCastingCoordinator.canDowngradeLegacyStableRole(
                BookTtsCastRole(name = "小道童")
            )
        )
        assertFalse(
            BookTtsCastingCoordinator.canDowngradeLegacyStableRole(
                BookTtsCastRole(
                    name = "阿糯",
                    nameType = BookTtsCastRole.NameType.PROPER_NAME,
                    identityEvidence = BookTtsCastRole.Evidence.EXPLICIT
                )
            )
        )
    }

    @Test
    fun singleGuestClassificationCannotDowngradeStableOrNamedRole() {
        assertFalse(
            BookTtsCastingCoordinator.shouldDowngradeRoleToGuest(
                BookTtsCastRole(
                    name = "阿糯",
                    identityState = BookTtsCastRole.IdentityState.STABLE,
                    nameType = BookTtsCastRole.NameType.PROPER_NAME,
                    identityEvidence = BookTtsCastRole.Evidence.EXPLICIT
                ),
                hasExplicitGuestEvidence = true
            )
        )
        assertFalse(
            BookTtsCastingCoordinator.shouldDowngradeRoleToGuest(
                BookTtsCastRole(
                    name = "青青子衿",
                    identityState = BookTtsCastRole.IdentityState.PENDING,
                    nameType = BookTtsCastRole.NameType.ALIAS,
                    identityEvidence = BookTtsCastRole.Evidence.CONTEXTUAL
                ),
                hasExplicitGuestEvidence = true
            )
        )
    }

    @Test
    fun weakSingleChapterPendingRoleCanBeCorrectedToGuest() {
        assertTrue(
            BookTtsCastingCoordinator.shouldDowngradeRoleToGuest(
                BookTtsCastRole(
                    name = "镇魔司下属",
                    identityState = BookTtsCastRole.IdentityState.PENDING,
                    nameType = BookTtsCastRole.NameType.GENERIC_LABEL,
                    identityEvidence = BookTtsCastRole.Evidence.INFERRED,
                    firstChapterIndex = 2,
                    lastChapterIndex = 2
                ),
                hasExplicitGuestEvidence = true
            )
        )
    }

    @Test
    fun languageCandidates_excludeKnownNonChineseVoicesForChineseDialogue() {
        val voices = listOf(
            TtsVoice(id = "zh", name = "中文", language = "zh-CN"),
            TtsVoice(id = "en", name = "English", language = "en-US"),
            TtsVoice(id = "unknown", name = "未标注")
        )

        assertEquals(
            listOf("zh", "unknown"),
            BookTtsCastingCoordinator.preferLanguageCandidates(voices, listOf("这是一句中文对白")).map { it.id }
        )
        assertEquals(
            voices.map { it.id },
            BookTtsCastingCoordinator.preferLanguageCandidates(voices, listOf("Hello there")).map { it.id }
        )
    }
}
