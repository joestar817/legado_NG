package io.legado.app.help.tts

import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSceneVoiceAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTtsRouterTest {

    private val narratorEngine = engine(
        id = "narrator",
        voices = listOf(voice("narrator_voice"))
    )
    private val dialogueEngine = engine(
        id = "dialogue",
        voices = listOf(voice("male_voice"), voice("female_voice"))
    )

    @Test
    fun offlineEngineCanRouteItsOwnNarratorAndDialogueVoices() {
        val offline = dialogueEngine.copy(type = TtsEngineType.OFFLINE)
        val defaults = ReadAloudTtsRouter.resolveGlobalBindings(
            multiRoleEngineId = offline.id,
            narratorEngineId = offline.id,
            narratorVoiceId = "female_voice",
            dialogueMaleVoiceId = "male_voice",
            dialogueFemaleVoiceId = "female_voice",
            engineResolver = { id -> offline.takeIf { it.id == id } }
        )
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            globalBindings = defaults
        )!!
        assertRoute(router, narration(), offline, "female_voice")
        assertRoute(router, dialogue(StoryboardSegment.SpeakerGender.MALE), offline, "male_voice")
        assertRoute(router, dialogue(StoryboardSegment.SpeakerGender.FEMALE), offline, "female_voice")
    }

    @Test
    fun globalDefaults_routeEmptyBookByNarrationAndGender() {
        val defaults = ReadAloudTtsRouter.resolveGlobalBindings(
            multiRoleEngineId = dialogueEngine.id,
            narratorEngineId = narratorEngine.id,
            narratorVoiceId = "narrator_voice",
            dialogueMaleVoiceId = "male_voice",
            dialogueFemaleVoiceId = "female_voice",
            engineResolver = ::resolveEngine
        )
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            globalBindings = defaults
        )

        assertNotNull(router)
        assertRoute(router!!, narration(), narratorEngine, "narrator_voice")
        assertRoute(router, dialogue(StoryboardSegment.SpeakerGender.MALE), dialogueEngine, "male_voice")
        assertRoute(router, dialogue(StoryboardSegment.SpeakerGender.FEMALE), dialogueEngine, "female_voice")
    }

    @Test
    fun globalDefaults_withoutGenderVoices_usesEngineDefaultInsteadOfFirstVoice() {
        val defaults = ReadAloudTtsRouter.resolveGlobalBindings(
            multiRoleEngineId = dialogueEngine.id,
            narratorEngineId = narratorEngine.id,
            narratorVoiceId = "narrator_voice",
            dialogueMaleVoiceId = null,
            dialogueFemaleVoiceId = null,
            engineResolver = ::resolveEngine
        )
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            globalBindings = defaults
        )!!

        assertRoute(router, narration(), narratorEngine, "narrator_voice")
        val route = router.route(
            dialogue(StoryboardSegment.SpeakerGender.UNKNOWN),
            narratorEngine
        )
        assertEquals(dialogueEngine.id, route.engine.id)
        assertNull(route.voiceId)
        assertEquals(ReadAloudTtsRouter.RouteKind.DIALOGUE_FALLBACK, route.kind)
        assertTrue(route.warnOnFailure)

        val fallback = router.fallbackRoutes(
            dialogue(StoryboardSegment.SpeakerGender.UNKNOWN),
            narratorEngine,
            route
        ).first()
        assertEquals(narratorEngine.id, fallback.engine.id)
        assertEquals(ReadAloudTtsRouter.RouteKind.NARRATOR, fallback.kind)
        assertFalse(fallback.warnOnFailure)
    }

    @Test
    fun globalDefaults_withoutGenderVoices_respectsExplicitActiveVoice() {
        val selectedEngine = engine(
            id = "selected-dialogue",
            voices = listOf(voice("voice-a"), voice("voice-b")),
            activeVoiceId = "voice-b"
        )
        val defaults = ReadAloudTtsRouter.resolveGlobalBindings(
            multiRoleEngineId = selectedEngine.id,
            narratorEngineId = narratorEngine.id,
            narratorVoiceId = "narrator_voice",
            dialogueMaleVoiceId = null,
            dialogueFemaleVoiceId = null,
            engineResolver = { id ->
                when (id) {
                    selectedEngine.id -> selectedEngine
                    narratorEngine.id -> narratorEngine
                    else -> null
                }
            }
        )
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            globalBindings = defaults
        )!!

        val route = router.route(
            dialogue(StoryboardSegment.SpeakerGender.UNKNOWN),
            narratorEngine
        )

        assertEquals(selectedEngine.id, route.engine.id)
        assertEquals("voice-b", route.voiceId)
    }

    @Test
    fun unknownGenderDialogue_usesDialogueEngineInsteadOfNarrator() {
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = ReadAloudTtsRouter.RouteBinding(narratorEngine, "narrator_voice"),
            characterBindings = emptyMap(),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap()
        )!!

        val route = router.route(dialogue(StoryboardSegment.SpeakerGender.UNKNOWN), dialogueEngine)

        assertEquals(dialogueEngine.id, route.engine.id)
        assertEquals(ReadAloudTtsRouter.RouteKind.DIALOGUE_FALLBACK, route.kind)
        assertTrue(route.fallbackUsed)
    }

    @Test
    fun staleCharacterId_relinksByCurrentAlias() {
        val characterVoice = voice("character_voice")
        val characterEngine = engine(id = "character", voices = listOf(characterVoice))
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = mapOf(3L to ReadAloudTtsRouter.RouteBinding(characterEngine, characterVoice.id)),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = mapOf("老赵" to 3L),
            characterGenderIndex = mapOf(3L to StoryboardSegment.SpeakerGender.MALE),
            knownCharacterIds = setOf(3L)
        )!!

        assertRoute(
            router,
            dialogue(StoryboardSegment.SpeakerGender.MALE).copy(
                speakerId = 999L,
                speakerName = "老赵"
            ),
            characterEngine,
            characterVoice.id
        )
    }

    @Test
    fun bookBinding_overridesGlobalDialogueDefault() {
        val bookEngine = engine(
            id = "book",
            voices = listOf(voice("book_voice"))
        )
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            dialogueMaleBinding = ReadAloudTtsRouter.RouteBinding(bookEngine, "book_voice"),
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            globalBindings = ReadAloudTtsRouter.GlobalBindings(
                narrator = null,
                dialogueMale = ReadAloudTtsRouter.RouteBinding(dialogueEngine, "male_voice"),
                dialogueFemale = ReadAloudTtsRouter.RouteBinding(dialogueEngine, "female_voice")
            )
        )

        assertRoute(router!!, dialogue(StoryboardSegment.SpeakerGender.MALE), bookEngine, "book_voice")
        assertRoute(router, dialogue(StoryboardSegment.SpeakerGender.FEMALE), dialogueEngine, "female_voice")
    }

    @Test
    fun sceneVoiceOverride_onlyAppliesWhenEnabledAndBindingIsNotProtected() {
        val engine = engine(
            id = "scene",
            voices = listOf(voice("base_voice"), voice("scene_voice"))
        )
        val segment = dialogue(StoryboardSegment.SpeakerGender.FEMALE).copy(
            speakerId = 3L,
            speakerName = "沈言卿"
        )
        val scene = StoryboardScene(
            index = 1,
            title = "质询",
            summary = "",
            characters = listOf("沈言卿"),
            segments = listOf(segment),
            voiceAssignments = listOf(
                StoryboardSceneVoiceAssignment(
                    engineId = engine.id,
                    targetType = BookCharacterTtsBinding.TargetType.CHARACTER,
                    targetId = 3L,
                    voiceId = "scene_voice",
                    decision = "assigned",
                    confidence = 0.85f
                )
            )
        )
        fun router(enabled: Boolean, protected: Boolean) = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = mapOf(
                3L to ReadAloudTtsRouter.RouteBinding(
                    engine,
                    "base_voice",
                    BookCharacterTtsBinding.BindingMode.AUTO
                )
            ),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = mapOf("沈言卿" to 3L),
            characterGenderIndex = mapOf(3L to StoryboardSegment.SpeakerGender.FEMALE),
            sceneVoiceEnabled = enabled,
            protectedSceneCharacterIds = if (protected) setOf(3L) else emptySet()
        )!!

        val adaptive = router(enabled = true, protected = false).route(segment, engine, scene)
        val disabled = router(enabled = false, protected = false).route(segment, engine, scene)
        val protected = router(enabled = true, protected = true).route(segment, engine, scene)

        assertEquals("scene_voice", adaptive.voiceId)
        assertTrue(adaptive.sceneOverrideUsed)
        val fallbacks = router(enabled = true, protected = false)
            .fallbackRoutes(segment, engine, adaptive)
        assertEquals("base_voice", fallbacks.first().voiceId)
        assertEquals("base_voice", disabled.voiceId)
        assertFalse(disabled.sceneOverrideUsed)
        assertEquals("base_voice", protected.voiceId)
        assertFalse(protected.sceneOverrideUsed)
    }

    @Test
    fun castRoleBinding_routesUnknownNamedSpeakerBeforeGenderFallback() {
        val castVoice = voice("cast_voice")
        val castEngine = engine(id = "cast", voices = listOf(castVoice))
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            castRoleBindings = mapOf(9L to ReadAloudTtsRouter.RouteBinding(castEngine, castVoice.id)),
            dialogueMaleBinding = ReadAloudTtsRouter.RouteBinding(dialogueEngine, "male_voice"),
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            castRoleNameIndex = mapOf("赵文博" to 9L),
            castRoleGenderIndex = mapOf(9L to StoryboardSegment.SpeakerGender.MALE)
        )

        assertRoute(
            router!!,
            dialogue(StoryboardSegment.SpeakerGender.MALE).copy(speakerName = "赵文博"),
            castEngine,
            castVoice.id
        )
    }

    @Test
    fun explicitCastRoleId_survivesSpeakerRenameAndRoutesBeforeNarrator() {
        val castVoice = voice("cast_voice")
        val castEngine = engine(id = "cast", voices = listOf(castVoice))
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = ReadAloudTtsRouter.RouteBinding(narratorEngine, "narrator_voice"),
            characterBindings = emptyMap(),
            castRoleBindings = mapOf(9L to ReadAloudTtsRouter.RouteBinding(castEngine, castVoice.id)),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            knownCastRoleIds = setOf(9L)
        )

        assertRoute(
            router!!,
            dialogue(StoryboardSegment.SpeakerGender.UNKNOWN).copy(
                speakerName = "小道童",
                castRoleId = 9L
            ),
            castEngine,
            castVoice.id
        )
    }

    @Test
    fun canonicalCharacterBinding_winsWhenCastAliasLinksToCharacter() {
        val characterVoice = voice("character_voice")
        val characterEngine = engine(id = "character", voices = listOf(characterVoice))
        val castEngine = engine(id = "cast", voices = listOf(voice("cast_voice")))
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = mapOf(3L to ReadAloudTtsRouter.RouteBinding(characterEngine, characterVoice.id)),
            castRoleBindings = mapOf(9L to ReadAloudTtsRouter.RouteBinding(castEngine, "cast_voice")),
            dialogueMaleBinding = null,
            dialogueFemaleBinding = null,
            characterNameIndex = mapOf("老赵" to 3L),
            characterGenderIndex = mapOf(3L to StoryboardSegment.SpeakerGender.MALE),
            castRoleNameIndex = mapOf("老赵" to 9L),
            castRoleGenderIndex = mapOf(9L to StoryboardSegment.SpeakerGender.MALE)
        )

        assertRoute(
            router!!,
            dialogue(StoryboardSegment.SpeakerGender.MALE).copy(speakerName = "老赵"),
            characterEngine,
            characterVoice.id
        )
    }

    @Test
    fun unavailableCharacterBinding_isVisibleWhileRouteUsesDialogueFallback() {
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = null,
            characterBindings = emptyMap(),
            dialogueMaleBinding = ReadAloudTtsRouter.RouteBinding(dialogueEngine, "male_voice"),
            dialogueFemaleBinding = null,
            characterNameIndex = mapOf("赵文博" to 3L),
            characterGenderIndex = mapOf(3L to StoryboardSegment.SpeakerGender.MALE),
            unavailableCharacterBindings = setOf(3L)
        )

        val route = router!!.route(
            dialogue(StoryboardSegment.SpeakerGender.MALE).copy(speakerName = "赵文博"),
            narratorEngine
        )

        assertEquals(dialogueEngine.id, route.engine.id)
        assertEquals("male_voice", route.voiceId)
        assertEquals(ReadAloudTtsRouter.RouteKind.DIALOGUE_FALLBACK, route.kind)
        assertTrue(route.fallbackUsed)
        assertTrue(route.bindingUnavailable)
    }

    @Test
    fun failedCharacterRoute_usesGenderFallbackBeforeNarrator() {
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = ReadAloudTtsRouter.RouteBinding(narratorEngine, "narrator_voice"),
            characterBindings = mapOf(
                3L to ReadAloudTtsRouter.RouteBinding(dialogueEngine, "female_voice")
            ),
            dialogueMaleBinding = ReadAloudTtsRouter.RouteBinding(dialogueEngine, "male_voice"),
            dialogueFemaleBinding = null,
            characterNameIndex = mapOf("赵文博" to 3L),
            characterGenderIndex = mapOf(3L to StoryboardSegment.SpeakerGender.MALE)
        )!!
        val segment = dialogue(StoryboardSegment.SpeakerGender.MALE).copy(speakerName = "赵文博")
        val primary = router.route(segment, dialogueEngine)

        val fallbacks = router.fallbackRoutes(segment, dialogueEngine, primary)

        assertEquals(dialogueEngine.id, fallbacks.first().engine.id)
        assertEquals("male_voice", fallbacks.first().voiceId)
        assertEquals(ReadAloudTtsRouter.RouteKind.DIALOGUE_FALLBACK, fallbacks.first().kind)
        assertEquals(narratorEngine.id, fallbacks[1].engine.id)
        assertEquals("narrator_voice", fallbacks[1].voiceId)
        assertTrue(fallbacks.all { it.fallbackUsed })
        assertTrue(primary.isAssignedRole)
        assertFalse(fallbacks.first().isAssignedRole)
    }

    @Test
    fun invalidNarratorDefault_isIgnoredButValidMultiRoleDefaultRemains() {
        val systemEngine = engine(
            id = "system",
            type = TtsEngineType.SYSTEM,
            voices = listOf(voice("system_voice"))
        )
        val defaults = ReadAloudTtsRouter.resolveGlobalBindings(
            multiRoleEngineId = dialogueEngine.id,
            narratorEngineId = systemEngine.id,
            narratorVoiceId = "system_voice",
            dialogueMaleVoiceId = "missing_voice",
            dialogueFemaleVoiceId = null,
            engineResolver = { id ->
                when (id) {
                    dialogueEngine.id -> dialogueEngine
                    systemEngine.id -> systemEngine
                    else -> null
                }
            }
        )

        assertNull(defaults.narrator)
        assertNull(defaults.dialogueMale)
        assertNull(defaults.dialogueFemale)
        assertNotNull(defaults.dialogueDefault)
        val router = ReadAloudTtsRouter.createResolved(
            narratorBinding = defaults.narrator,
            characterBindings = emptyMap(),
            dialogueMaleBinding = defaults.dialogueMale,
            dialogueFemaleBinding = defaults.dialogueFemale,
            characterNameIndex = emptyMap(),
            characterGenderIndex = emptyMap(),
            globalBindings = defaults
        )
        assertNotNull(router)
        assertEquals(
            dialogueEngine.id,
            router!!.route(dialogue(StoryboardSegment.SpeakerGender.UNKNOWN), narratorEngine).engine.id
        )
    }

    @Test
    fun bookDialogueBinding_mustMatchCurrentMultiRoleEngine() {
        val narrator = BookCharacterTtsBinding.narrator("book").apply {
            engineId = narratorEngine.id
            voiceId = "narrator_voice"
        }
        val current = BookCharacterTtsBinding.character("book", 1L).apply {
            engineId = dialogueEngine.id
            voiceId = "male_voice"
        }
        val stale = BookCharacterTtsBinding.character("book", 2L).apply {
            engineId = "old_dialogue_engine"
            voiceId = "old_voice"
        }

        assertTrue(
            ReadAloudTtsRouter.isBookBindingCompatible(narrator, dialogueEngine.id)
        )
        assertTrue(
            ReadAloudTtsRouter.isBookBindingCompatible(current, dialogueEngine.id)
        )
        assertFalse(
            ReadAloudTtsRouter.isBookBindingCompatible(stale, dialogueEngine.id)
        )
        assertFalse(
            ReadAloudTtsRouter.isBookBindingCompatible(current, null)
        )
    }

    private fun resolveEngine(id: String?): TtsEngineSetting? {
        return when (id) {
            narratorEngine.id -> narratorEngine
            dialogueEngine.id -> dialogueEngine
            else -> null
        }
    }

    private fun assertRoute(
        router: ReadAloudTtsRouter,
        segment: StoryboardSegment,
        expectedEngine: TtsEngineSetting,
        expectedVoiceId: String
    ) {
        val route = router.route(segment, narratorEngine)
        assertEquals(expectedEngine.id, route.engine.id)
        assertEquals(expectedVoiceId, route.voiceId)
    }

    private fun narration() = StoryboardSegment(
        type = StoryboardSegmentType.NARRATION,
        paragraphIndex = 0,
        text = "旁白",
        speakerName = null,
        evidence = ""
    )

    private fun dialogue(gender: String) = StoryboardSegment(
        type = StoryboardSegmentType.DIALOGUE,
        paragraphIndex = 0,
        text = "对白",
        speakerName = null,
        evidence = "",
        speakerGender = gender
    )

    private fun engine(
        id: String,
        type: TtsEngineType = TtsEngineType.SCRIPT,
        voices: List<TtsVoice>,
        activeVoiceId: String? = null
    ) = TtsEngineSetting(
        id = id,
        name = id,
        type = type,
        voices = voices,
        activeVoiceId = activeVoiceId
    )

    private fun voice(id: String) = TtsVoice(id = id, name = id)
}
