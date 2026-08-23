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
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

class ReadAloudTtsRouter private constructor(
    private val narratorBinding: RouteBinding?,
    private val characterBindings: Map<Long, RouteBinding>,
    private val castRoleBindings: Map<Long, RouteBinding>,
    private val dialogueMaleBinding: RouteBinding?,
    private val dialogueFemaleBinding: RouteBinding?,
    private val dialogueDefaultBinding: RouteBinding?,
    private val characterNameIndex: Map<String, Long>,
    private val characterGenderIndex: Map<Long, String>,
    private val castRoleNameIndex: Map<String, Long>,
    private val castRoleGenderIndex: Map<Long, String>,
    private val knownCharacterIds: Set<Long>,
    private val knownCastRoleIds: Set<Long>,
    private val unavailableCharacterBindings: Set<Long>,
    private val unavailableCastRoleBindings: Set<Long>,
    private val sceneVoiceEnabled: Boolean,
    private val protectedSceneCharacterIds: Set<Long>,
    private val protectedSceneCastRoleIds: Set<Long>
) {

    fun route(
        segment: StoryboardSegment?,
        fallbackEngine: TtsEngineSetting,
        scene: StoryboardScene? = null
    ): Route {
        val characterId = segment?.characterTargetId()
        val castRoleId = segment?.castRoleTargetId(characterId)
        val characterBinding = characterId?.let { characterBindings[it] }
        val castRoleBinding = castRoleId?.let { castRoleBindings[it] }
        val fallbackGender = segment?.dialogueFallbackGender(characterId, castRoleId)
        val dialogueFallbackBinding = fallbackGender?.let(::genderBinding)
        val isSpokenRole = segment?.type == StoryboardSegmentType.DIALOGUE ||
            segment?.type == StoryboardSegmentType.THOUGHT
        val defaultDialogueBinding = dialogueDefaultBinding.takeIf { isSpokenRole }
        val binding = characterBinding ?: castRoleBinding ?: dialogueFallbackBinding ?:
            defaultDialogueBinding ?:
            narratorBinding.takeUnless { isSpokenRole }
        val engine = binding?.engine?.takeIf { it.type == TtsEngineType.SCRIPT && it.enabled }
            ?: fallbackEngine
        val sceneVoiceId = scene?.voiceAssignments
            ?.firstOrNull { assignment ->
                sceneVoiceEnabled &&
                    assignment.engineId == engine.id &&
                    assignment.decision == "assigned" &&
                    when (assignment.targetType) {
                        BookCharacterTtsBinding.TargetType.CHARACTER ->
                            characterId == assignment.targetId && characterId !in protectedSceneCharacterIds
                        BookCharacterTtsBinding.TargetType.CAST_ROLE ->
                            castRoleId == assignment.targetId && castRoleId !in protectedSceneCastRoleIds
                        else -> false
                    }
            }
            ?.voiceId
            ?.takeIf { voiceId -> engine.enabledVoices().any { it.id == voiceId } }
        val voiceId = sceneVoiceId ?: binding?.voiceId
            ?.takeIf { binding.engine.id == engine.id }
            ?.takeIf { voiceId -> engine.enabledVoices().any { it.id == voiceId } }
            ?: engine.activeVoice()?.id
        return Route(
            engine = engine,
            voiceId = voiceId,
            styleId = null,
            kind = when {
                characterBinding != null -> RouteKind.CHARACTER
                castRoleBinding != null -> RouteKind.CAST_ROLE
                dialogueFallbackBinding != null -> RouteKind.DIALOGUE_FALLBACK
                isSpokenRole -> RouteKind.DIALOGUE_FALLBACK
                narratorBinding != null -> RouteKind.NARRATOR
                else -> RouteKind.ENGINE_DEFAULT
            },
            fallbackUsed = isSpokenRole && characterBinding == null && castRoleBinding == null,
            bindingUnavailable = characterId in unavailableCharacterBindings || castRoleId in unavailableCastRoleBindings,
            bindingMode = characterBinding?.bindingMode ?: castRoleBinding?.bindingMode,
            sceneOverrideUsed = sceneVoiceId != null,
            warnOnFailure = isSpokenRole && binding != null &&
                binding.engine.id == dialogueDefaultBinding?.engine?.id
        )
    }

    fun fallbackRoutes(
        segment: StoryboardSegment?,
        fallbackEngine: TtsEngineSetting,
        failedRoute: Route?
    ): List<Route> {
        val characterId = segment?.characterTargetId()
        val castRoleId = segment?.castRoleTargetId(characterId)
        val fallbackGender = segment?.dialogueFallbackGender(characterId, castRoleId)
        val isSpokenRole = segment?.type == StoryboardSegmentType.DIALOGUE ||
            segment?.type == StoryboardSegmentType.THOUGHT
        val candidates = buildList {
            if (failedRoute?.sceneOverrideUsed == true) {
                val baseBinding = characterId?.let { characterBindings[it] }
                    ?: castRoleId?.let { castRoleBindings[it] }
                baseBinding?.let { binding ->
                    add(
                        binding.toRoute(
                            kind = if (characterId != null) RouteKind.CHARACTER else RouteKind.CAST_ROLE,
                            fallbackUsed = true
                        )
                    )
                }
            }
            fallbackGender?.let(::genderBinding)?.let { binding ->
                add(binding.toRoute(RouteKind.DIALOGUE_FALLBACK, fallbackUsed = true))
            }
            narratorBinding?.let { binding ->
                add(binding.toRoute(RouteKind.NARRATOR, fallbackUsed = true))
            }
            add(
                Route(
                    engine = fallbackEngine,
                    voiceId = fallbackEngine.activeVoice()?.id,
                    styleId = null,
                    kind = if (isSpokenRole) RouteKind.DIALOGUE_FALLBACK else RouteKind.ENGINE_DEFAULT,
                    fallbackUsed = true
                )
            )
        }
        return candidates
            .distinctBy { Triple(it.engine.id, it.voiceId, it.styleId) }
            .filterNot { route ->
                failedRoute != null && route.engine.id == failedRoute.engine.id &&
                    route.voiceId == failedRoute.voiceId && route.styleId == failedRoute.styleId
            }
    }

    private fun RouteBinding.toRoute(kind: RouteKind, fallbackUsed: Boolean): Route {
        return Route(
            engine = engine,
            voiceId = voiceId ?: engine.activeVoice()?.id,
            styleId = null,
            kind = kind,
            fallbackUsed = fallbackUsed,
            bindingMode = bindingMode
        )
    }

    data class Route(
        val engine: TtsEngineSetting,
        val voiceId: String?,
        val styleId: String?,
        val kind: RouteKind = RouteKind.ENGINE_DEFAULT,
        val fallbackUsed: Boolean = false,
        val bindingUnavailable: Boolean = false,
        val bindingMode: String? = null,
        val sceneOverrideUsed: Boolean = false,
        val warnOnFailure: Boolean = false
    ) {
        val isAssignedRole: Boolean
            get() = !fallbackUsed && (kind == RouteKind.CHARACTER || kind == RouteKind.CAST_ROLE)
    }

    enum class RouteKind {
        CHARACTER,
        CAST_ROLE,
        DIALOGUE_FALLBACK,
        NARRATOR,
        ENGINE_DEFAULT
    }

    private fun StoryboardSegment.characterTargetId(): Long? {
        if (type != StoryboardSegmentType.DIALOGUE && type != StoryboardSegmentType.THOUGHT) {
            return null
        }
        speakerId?.takeIf { it > 0L && it in knownCharacterIds }?.let { return it }
        return speakerName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(BookTtsCastingCoordinator::normalizeIdentityName)
            ?.let { characterNameIndex[it] }
    }

    private fun StoryboardSegment.castRoleTargetId(characterId: Long?): Long? {
        if (characterId != null || (type != StoryboardSegmentType.DIALOGUE && type != StoryboardSegmentType.THOUGHT)) {
            return null
        }
        castRoleId?.takeIf { it > 0L && it in knownCastRoleIds }?.let { return it }
        return speakerName?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(BookTtsCastingCoordinator::normalizeIdentityName)
            ?.let { castRoleNameIndex[it] }
    }

    private fun StoryboardSegment.dialogueFallbackGender(characterId: Long?, castRoleId: Long?): String? {
        if (type != StoryboardSegmentType.DIALOGUE && type != StoryboardSegmentType.THOUGHT) {
            return null
        }
        return speakerGender.takeIf { it == StoryboardSegment.SpeakerGender.MALE || it == StoryboardSegment.SpeakerGender.FEMALE }
            ?: characterId?.let { characterGenderIndex[it] }
            ?: castRoleId?.let { castRoleGenderIndex[it] }
    }

    private fun genderBinding(gender: String): RouteBinding? {
        return when (gender) {
            StoryboardSegment.SpeakerGender.MALE -> dialogueMaleBinding
            StoryboardSegment.SpeakerGender.FEMALE -> dialogueFemaleBinding
            else -> null
        }
    }

    internal data class RouteBinding(
        val engine: TtsEngineSetting,
        val voiceId: String?,
        val bindingMode: String? = null
    )

    internal data class GlobalBindings(
        val narrator: RouteBinding?,
        val dialogueMale: RouteBinding?,
        val dialogueFemale: RouteBinding?,
        val dialogueDefault: RouteBinding? = null
    )

    companion object {
        fun createForCurrentBook(): ReadAloudTtsRouter? {
            if (!AppConfig.readAloudMultiRole) {
                return null
            }
            val book = ReadBook.book ?: return null
            return create(book)
        }

        internal fun globalScriptNarratorEngine(): TtsEngineSetting? {
            return resolveGlobalBindings(
                multiRoleEngineId = AppConfig.multiRoleTtsEngineId,
                narratorEngineId = AppConfig.defaultNarratorTtsEngineId,
                narratorVoiceId = AppConfig.defaultNarratorTtsVoiceId,
                dialogueMaleVoiceId = AppConfig.defaultDialogueMaleTtsVoiceId,
                dialogueFemaleVoiceId = AppConfig.defaultDialogueFemaleTtsVoiceId,
                engineResolver = TtsEngineStore::engine
            ).narrator?.engine
        }

        fun create(book: Book): ReadAloudTtsRouter? {
            val workKey = BookCharacterProfile.workKey(book.name, book.author)
            val characters = appDb.bookCharacterDao.getCharacters(workKey)
                .filter { it.enabled && it.name.isNotBlank() }
            val castRoles = appDb.bookCharacterDao.getTtsCastRoles(workKey)
                .filter { it.isRoutableRole() }
            val bindings = appDb.bookCharacterDao.getTtsBindings(workKey)
            val multiRoleEngineId = AppConfig.multiRoleTtsEngineId
            val engineIndex = TtsEngineStore.engines().associateBy { it.id }
            val engineResolver: (String?) -> TtsEngineSetting? = { engineId ->
                engineId?.let(engineIndex::get)
            }
            val currentEngineBindings = bindings
                .filter { it.targetType != BookCharacterTtsBinding.TargetType.NARRATOR }
                .filter { isBookBindingCompatible(it, multiRoleEngineId) }
            val bindingMap = currentEngineBindings
                .mapNotNull { binding ->
                binding.toRouteBinding(engineResolver)
                    ?.let { (binding.targetType to binding.targetId) to it }
                }.toMap()
            val unavailableBindingKeys = currentEngineBindings
                .filter { isBindingUnavailable(it, engineResolver) }
                .map { it.targetType to it.targetId }
                .toSet()
            val protectedSceneBindingKeys = currentEngineBindings
                .filter { it.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO }
                .map { it.targetType to it.targetId }
                .toSet()
            val narratorBinding = bindings.asSequence()
                .filter { it.targetType == BookCharacterTtsBinding.TargetType.NARRATOR }
                .sortedByDescending { it.updatedAt }
                .mapNotNull { it.toRouteBinding(engineResolver) }
                .firstOrNull()
            val globalBindings = resolveGlobalBindings(
                multiRoleEngineId = AppConfig.multiRoleTtsEngineId,
                narratorEngineId = AppConfig.defaultNarratorTtsEngineId,
                narratorVoiceId = AppConfig.defaultNarratorTtsVoiceId,
                dialogueMaleVoiceId = AppConfig.defaultDialogueMaleTtsVoiceId,
                dialogueFemaleVoiceId = AppConfig.defaultDialogueFemaleTtsVoiceId,
                engineResolver = engineResolver
            )
            val characterIds = characters.map { it.id }.toSet()
            return createResolved(
                narratorBinding = narratorBinding,
                characterBindings = bindingMap
                    .filterKeys { it.first == BookCharacterTtsBinding.TargetType.CHARACTER && it.second in characterIds }
                    .mapKeys { it.key.second },
                castRoleBindings = bindingMap
                    .filterKeys { key ->
                        key.first == BookCharacterTtsBinding.TargetType.CAST_ROLE &&
                            castRoles.any { it.id == key.second }
                    }
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
                        .map { BookTtsCastingCoordinator.normalizeIdentityName(it) to character.id }
                }.plus(
                    castRoles.flatMap { role ->
                        val characterId = role.linkedCharacterId ?: return@flatMap emptyList()
                        buildList {
                            add(role.name)
                            GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty().forEach(::add)
                        }.filter { it.isNotBlank() }.map {
                            BookTtsCastingCoordinator.normalizeIdentityName(it) to characterId
                        }
                    }
                ).toMap(),
                characterGenderIndex = characters.mapNotNull { character ->
                    character.gender
                        .takeIf { it == BookCharacter.Gender.MALE || it == BookCharacter.Gender.FEMALE }
                        ?.let { character.id to it }
                }.toMap(),
                castRoleNameIndex = castRoles
                    .filter { it.linkedCharacterId == null }
                    .flatMap { role ->
                        buildList {
                            add(role.name)
                            GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty().forEach(::add)
                        }.filter { it.isNotBlank() }.map {
                            BookTtsCastingCoordinator.normalizeIdentityName(it) to role.id
                        }
                    }.toMap(),
                castRoleGenderIndex = castRoles.mapNotNull { role ->
                    role.gender
                        .takeIf { it == BookCharacter.Gender.MALE || it == BookCharacter.Gender.FEMALE }
                        ?.let { role.id to it }
                }.toMap(),
                knownCharacterIds = characterIds,
                knownCastRoleIds = castRoles.mapTo(mutableSetOf()) { it.id },
                unavailableCharacterBindings = unavailableBindingKeys
                    .filter { it.first == BookCharacterTtsBinding.TargetType.CHARACTER }
                    .mapTo(mutableSetOf()) { it.second },
                unavailableCastRoleBindings = unavailableBindingKeys
                    .filter { it.first == BookCharacterTtsBinding.TargetType.CAST_ROLE }
                    .mapTo(mutableSetOf()) { it.second },
                sceneVoiceEnabled = BookTtsAutomationConfig.get(workKey).autoSwitchSceneVoices,
                protectedSceneCharacterIds = protectedSceneBindingKeys
                    .filter { it.first == BookCharacterTtsBinding.TargetType.CHARACTER }
                    .mapTo(mutableSetOf()) { it.second },
                protectedSceneCastRoleIds = protectedSceneBindingKeys
                    .filter { it.first == BookCharacterTtsBinding.TargetType.CAST_ROLE }
                    .mapTo(mutableSetOf()) { it.second },
                globalBindings = globalBindings
            )
        }

        internal fun resolveGlobalBindings(
            multiRoleEngineId: String?,
            narratorEngineId: String?,
            narratorVoiceId: String?,
            dialogueMaleVoiceId: String?,
            dialogueFemaleVoiceId: String?,
            engineResolver: (String?) -> TtsEngineSetting?
        ): GlobalBindings {
            val narrator = engineResolver(narratorEngineId)
                ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                ?.toGlobalRouteBinding(narratorVoiceId)
            val dialogueEngine = engineResolver(multiRoleEngineId)
                ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
            return GlobalBindings(
                narrator = narrator,
                dialogueMale = dialogueEngine?.toGlobalRouteBinding(dialogueMaleVoiceId),
                dialogueFemale = dialogueEngine?.toGlobalRouteBinding(dialogueFemaleVoiceId),
                dialogueDefault = dialogueEngine?.toDialogueDefaultRouteBinding()
            )
        }

        internal fun isBookBindingCompatible(
            binding: BookCharacterTtsBinding,
            multiRoleEngineId: String?
        ): Boolean {
            return binding.targetType == BookCharacterTtsBinding.TargetType.NARRATOR ||
                (!multiRoleEngineId.isNullOrBlank() && binding.engineId == multiRoleEngineId)
        }

        internal fun createResolved(
            narratorBinding: RouteBinding?,
            characterBindings: Map<Long, RouteBinding>,
            castRoleBindings: Map<Long, RouteBinding> = emptyMap(),
            dialogueMaleBinding: RouteBinding?,
            dialogueFemaleBinding: RouteBinding?,
            dialogueDefaultBinding: RouteBinding? = null,
            characterNameIndex: Map<String, Long>,
            characterGenderIndex: Map<Long, String>,
            castRoleNameIndex: Map<String, Long> = emptyMap(),
            castRoleGenderIndex: Map<Long, String> = emptyMap(),
            knownCharacterIds: Set<Long> = characterNameIndex.values.toSet() +
                characterGenderIndex.keys + characterBindings.keys,
            knownCastRoleIds: Set<Long> = castRoleNameIndex.values.toSet() +
                castRoleGenderIndex.keys + castRoleBindings.keys,
            unavailableCharacterBindings: Set<Long> = emptySet(),
            unavailableCastRoleBindings: Set<Long> = emptySet(),
            sceneVoiceEnabled: Boolean = false,
            protectedSceneCharacterIds: Set<Long> = emptySet(),
            protectedSceneCastRoleIds: Set<Long> = emptySet(),
            globalBindings: GlobalBindings = GlobalBindings(null, null, null)
        ): ReadAloudTtsRouter? {
            val effectiveNarratorBinding = narratorBinding ?: globalBindings.narrator
            val effectiveDialogueMaleBinding = dialogueMaleBinding ?: globalBindings.dialogueMale
            val effectiveDialogueFemaleBinding = dialogueFemaleBinding ?: globalBindings.dialogueFemale
            val effectiveDialogueDefaultBinding = dialogueDefaultBinding ?: globalBindings.dialogueDefault
            if (
                effectiveNarratorBinding == null &&
                characterBindings.isEmpty() &&
                castRoleBindings.isEmpty() &&
                effectiveDialogueMaleBinding == null &&
                effectiveDialogueFemaleBinding == null &&
                effectiveDialogueDefaultBinding == null &&
                characterNameIndex.isEmpty()
            ) {
                return null
            }
            return ReadAloudTtsRouter(
                narratorBinding = effectiveNarratorBinding,
                characterBindings = characterBindings,
                castRoleBindings = castRoleBindings,
                dialogueMaleBinding = effectiveDialogueMaleBinding,
                dialogueFemaleBinding = effectiveDialogueFemaleBinding,
                dialogueDefaultBinding = effectiveDialogueDefaultBinding,
                characterNameIndex = characterNameIndex,
                characterGenderIndex = characterGenderIndex,
                castRoleNameIndex = castRoleNameIndex,
                castRoleGenderIndex = castRoleGenderIndex,
                knownCharacterIds = knownCharacterIds,
                knownCastRoleIds = knownCastRoleIds,
                unavailableCharacterBindings = unavailableCharacterBindings,
                unavailableCastRoleBindings = unavailableCastRoleBindings,
                sceneVoiceEnabled = sceneVoiceEnabled,
                protectedSceneCharacterIds = protectedSceneCharacterIds,
                protectedSceneCastRoleIds = protectedSceneCastRoleIds
            )
        }

        internal fun isBindingUnavailable(binding: BookCharacterTtsBinding): Boolean {
            return isBindingUnavailable(binding, TtsEngineStore::engine)
        }

        private fun isBindingUnavailable(
            binding: BookCharacterTtsBinding,
            engineResolver: (String?) -> TtsEngineSetting?
        ): Boolean {
            if (binding.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) return false
            val engine = engineResolver(binding.engineId)
                ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                ?: return true
            val voiceId = binding.voiceId?.takeIf { it.isNotBlank() }
                ?: return binding.bindingMode == BookCharacterTtsBinding.BindingMode.AUTO
            return engine.enabledVoices().none { it.id == voiceId }
        }

        private fun BookCharacterTtsBinding.toRouteBinding(
            engineResolver: (String?) -> TtsEngineSetting?
        ): RouteBinding? {
            if (bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) return null
            val engine = engineResolver(engineId)?.takeIf { it.enabled } ?: return null
            val safeVoiceId = voiceId
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { id -> engine.enabledVoices().any { it.id == id } }
            if (bindingMode == BookCharacterTtsBinding.BindingMode.AUTO && safeVoiceId == null) {
                return null
            }
            return RouteBinding(engine, safeVoiceId, bindingMode)
        }

        private fun TtsEngineSetting.toGlobalRouteBinding(voiceId: String?): RouteBinding? {
            val safeVoiceId = voiceId
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { id -> enabledVoices().any { it.id == id } }
                ?: return null
            return RouteBinding(this, safeVoiceId)
        }

        private fun TtsEngineSetting.toDialogueDefaultRouteBinding(): RouteBinding {
            return RouteBinding(this, activeVoice()?.id)
        }

    }
}
