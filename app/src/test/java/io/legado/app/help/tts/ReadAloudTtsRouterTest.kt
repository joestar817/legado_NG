package io.legado.app.help.tts

import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun invalidOrSystemGlobalDefaults_areIgnored() {
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
        assertNull(
            ReadAloudTtsRouter.createResolved(
                narratorBinding = defaults.narrator,
                characterBindings = emptyMap(),
                dialogueMaleBinding = defaults.dialogueMale,
                dialogueFemaleBinding = defaults.dialogueFemale,
                characterNameIndex = emptyMap(),
                characterGenderIndex = emptyMap(),
                globalBindings = defaults
            )
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
        voices: List<TtsVoice>
    ) = TtsEngineSetting(
        id = id,
        name = id,
        type = type,
        voices = voices
    )

    private fun voice(id: String) = TtsVoice(id = id, name = id)
}
