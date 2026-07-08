package io.legado.app.help.tts

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

class ReadAloudTtsRouter private constructor(
    private val narratorBinding: RouteBinding?,
    private val characterBindings: Map<Long, RouteBinding>,
    private val dialogueMaleBinding: RouteBinding?,
    private val dialogueFemaleBinding: RouteBinding?,
    private val characterNameIndex: Map<String, Long>,
    private val characterGenderIndex: Map<Long, String>
) {

    fun route(segment: StoryboardSegment?, fallbackEngine: TtsEngineSetting): Route {
        val characterId = segment?.characterTargetId()
        val binding = characterId?.let { characterBindings[it] }
            ?: segment?.dialogueFallbackGender(characterId)?.let { genderBinding(it) }
            ?: narratorBinding
        val engine = binding?.engine?.takeIf { it.type == TtsEngineType.SCRIPT && it.enabled }
            ?: fallbackEngine
        val voiceId = binding?.voiceId
            ?.takeIf { binding.engine.id == engine.id }
            ?.takeIf { voiceId -> engine.enabledVoices().any { it.id == voiceId } }
            ?: engine.activeVoiceId
        return Route(engine, voiceId, styleId = null)
    }

    data class Route(
        val engine: TtsEngineSetting,
        val voiceId: String?,
        val styleId: String?
    )

    private fun StoryboardSegment.characterTargetId(): Long? {
        if (type != StoryboardSegmentType.DIALOGUE && type != StoryboardSegmentType.THOUGHT) {
            return null
        }
        speakerId?.takeIf { it > 0L }?.let { return it }
        return speakerName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { characterNameIndex[it] }
    }

    private fun StoryboardSegment.dialogueFallbackGender(characterId: Long?): String? {
        if (type != StoryboardSegmentType.DIALOGUE && type != StoryboardSegmentType.THOUGHT) {
            return null
        }
        return speakerGender.takeIf { it == StoryboardSegment.SpeakerGender.MALE || it == StoryboardSegment.SpeakerGender.FEMALE }
            ?: characterId?.let { characterGenderIndex[it] }
    }

    private fun genderBinding(gender: String): RouteBinding? {
        return when (gender) {
            StoryboardSegment.SpeakerGender.MALE -> dialogueMaleBinding
            StoryboardSegment.SpeakerGender.FEMALE -> dialogueFemaleBinding
            else -> null
        }
    }

    private data class RouteBinding(
        val engine: TtsEngineSetting,
        val voiceId: String?
    )

    companion object {
        fun createForCurrentBook(): ReadAloudTtsRouter? {
            if (!AppConfig.readAloudMultiRole) {
                return null
            }
            val book = ReadBook.book ?: return null
            return create(book)
        }

        fun create(book: Book): ReadAloudTtsRouter? {
            val workKey = BookCharacterProfile.workKey(book.name, book.author)
            val characters = appDb.bookCharacterDao.getCharacters(workKey)
                .filter { it.enabled && it.name.isNotBlank() }
            val bindings = appDb.bookCharacterDao.getTtsBindings(workKey)
            if (characters.isEmpty() && bindings.isEmpty()) {
                return null
            }
            val bindingMap = bindings.mapNotNull { binding ->
                binding.toRouteBinding()?.let { (binding.targetType to binding.targetId) to it }
            }.toMap()
            val characterIds = characters.map { it.id }.toSet()
            return ReadAloudTtsRouter(
                narratorBinding = bindingMap[BookCharacterTtsBinding.TargetType.NARRATOR to 0L],
                characterBindings = bindingMap
                    .filterKeys { it.first == BookCharacterTtsBinding.TargetType.CHARACTER && it.second in characterIds }
                    .mapKeys { it.key.second },
                dialogueMaleBinding = bindingMap[BookCharacterTtsBinding.TargetType.DIALOGUE_MALE to 0L],
                dialogueFemaleBinding = bindingMap[BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE to 0L],
                characterNameIndex = characters.flatMap { character ->
                    buildList {
                        add(character.name)
                        character.aliasesJson
                            ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
                            .orEmpty()
                            .forEach { add(it) }
                    }
                        .filter { it.isNotBlank() }
                        .map { it.trim() to character.id }
                }.toMap(),
                characterGenderIndex = characters.mapNotNull { character ->
                    character.gender
                        .takeIf { it == BookCharacter.Gender.MALE || it == BookCharacter.Gender.FEMALE }
                        ?.let { character.id to it }
                }.toMap()
            )
        }

        private fun BookCharacterTtsBinding.toRouteBinding(): RouteBinding? {
            val engine = TtsEngineStore.engine(engineId)?.takeIf { it.enabled } ?: return null
            val safeVoiceId = voiceId
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { id -> engine.enabledVoices().any { it.id == id } }
            return RouteBinding(engine, safeVoiceId)
        }

    }
}
