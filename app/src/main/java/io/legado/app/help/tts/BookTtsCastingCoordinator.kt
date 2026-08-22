package io.legado.app.help.tts

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.data.entities.BookTtsCastRoleContribution
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiMessage
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.StoryboardIdentityLink
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSceneVoiceAssignment
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/**
 * 把分镜里稳定但尚未进入正式角色卡的说话人沉淀为“演播角色”，并为当前多人引擎补空绑定。
 * 正式角色卡和手动／继承绑定都只读，绝不在这里静默创建或覆盖。
 */
object BookTtsCastingCoordinator {

    private const val PROMPT_ASSET = "skills/tts_casting/SKILL.md"
    private const val SCENE_VOICE_TARGET_TYPE = "scene_voice"
    private const val AUTO_CAST_POLICY_VERSION = "auto_cast_v2_evidence"
    private const val MIN_SCENE_OVERRIDE_CONFIDENCE = 0.85f
    private const val SCENE_VOICE_POLICY_VERSION = "scene_voice_v2_anchored"
    private val reservedNames = setOf("旁白", "心声", "对白男", "对白女", "待确认说话人")
    private val pronouns = setOf("我", "你", "他", "她", "它", "他们", "她们", "对方", "某人", "那人", "这人")
    private val prepareMutexes = ConcurrentHashMap<String, Mutex>()
    private val assignmentMutexes = ConcurrentHashMap<String, Mutex>()

    private fun prepareMutex(workKey: String): Mutex =
        prepareMutexes.getOrPut(workKey) { Mutex() }

    private fun assignmentMutex(workKey: String, engineId: String): Mutex =
        assignmentMutexes.getOrPut("$workKey|$engineId") { Mutex() }

    fun storyboardContextRoles(book: Book, chapterIndex: Int): List<BookTtsCastRole> {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        return appDb.bookCharacterDao.getTtsCastRoles(workKey)
            .filter { it.linkedCharacterId == null && it.isRoutableRole() }
            .filter { role ->
                role.identityState == BookTtsCastRole.IdentityState.STABLE ||
                    kotlin.math.abs(role.lastChapterIndex - chapterIndex) <= 12
            }
    }

    suspend fun prepareGenerated(
        book: Book,
        chapterIndex: Int,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ): ChapterStoryboard {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val automation = BookTtsAutomationConfig.get(workKey)
        val prepared = prepareMutex(workKey).withLock {
            val roles = if (automation.autoCreateTemporaryRoles) {
                runCatching {
                    appDb.runInTransaction(Callable {
                        syncCastRoles(book, chapterIndex, storyboard, characters)
                    })
                }.onFailure { error ->
                    AppLog.put("演播角色发现失败，已继续使用现有绑定\n${error.localizedMessage}", error)
                }.getOrElse {
                    SyncedCastRoles(storyboard.identityLinks, appDb.bookCharacterDao.getTtsCastRoles(workKey))
                }
            } else {
                SyncedCastRoles(storyboard.identityLinks, appDb.bookCharacterDao.getTtsCastRoles(workKey))
            }
            val enrichedStoryboard = storyboard.copy(identityLinks = roles.identityLinks)
            PreparedStoryboard(relinkStoryboard(enrichedStoryboard, roles.roles), roles.roles)
        }
        if (automation.autoAssignVoices) {
            runCatching { autoBindCurrentEngine(book, prepared.storyboard, characters, prepared.roles) }
                .onFailure { error ->
                    AppLog.put("演播角色自动选音失败，已保留对白兜底\n${error.localizedMessage}", error)
                }
        }
        return if (automation.autoSwitchSceneVoices) {
            runCatching {
                applySceneVoiceAssignments(book, prepared.storyboard, characters, prepared.roles)
            }.onFailure { error ->
                AppLog.put("按场景自动选音失败，已继续使用角色绑定\n${error.localizedMessage}", error)
            }.getOrDefault(prepared.storyboard)
        } else {
            prepared.storyboard
        }
    }

    suspend fun prepareCached(
        book: Book,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ): ChapterStoryboard {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val automation = BookTtsAutomationConfig.get(workKey)
        val prepared = prepareMutex(workKey).withLock {
            val roles = if (automation.autoCreateTemporaryRoles) {
                runCatching {
                    appDb.runInTransaction(Callable {
                        syncCastRoles(book, storyboard.sourceChapterIndex.coerceAtLeast(0), storyboard, characters)
                    })
                }.onFailure { error ->
                    AppLog.put("缓存角色纠错应用失败，已继续使用现有绑定\n${error.localizedMessage}", error)
                }.getOrElse {
                    SyncedCastRoles(storyboard.identityLinks, appDb.bookCharacterDao.getTtsCastRoles(workKey))
                }
            } else {
                SyncedCastRoles(storyboard.identityLinks, appDb.bookCharacterDao.getTtsCastRoles(workKey))
            }
            val enrichedStoryboard = storyboard.copy(identityLinks = roles.identityLinks)
            PreparedStoryboard(relinkStoryboard(enrichedStoryboard, roles.roles), roles.roles)
        }
        return prepared.storyboard
    }

    /**
     * 缓存命中后的模型富化只能在后台执行。播放前台只使用 [prepareCached] 的本地结果，
     * 避免基础选角复评或场景选音重新把缓存章节变成网络请求。
     */
    suspend fun enrichCached(
        book: Book,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ): ChapterStoryboard {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val automation = BookTtsAutomationConfig.get(workKey)
        val prepared = prepareMutex(workKey).withLock {
            val roles = appDb.bookCharacterDao.getTtsCastRoles(workKey)
            PreparedStoryboard(relinkStoryboard(storyboard, roles), roles)
        }
        if (automation.autoAssignVoices) {
            runCatching { autoBindCurrentEngine(book, prepared.storyboard, characters, prepared.roles) }
                .onFailure { error ->
                    AppLog.put("缓存演播角色后台自动选音失败，已保留现有绑定\n${error.localizedMessage}", error)
                }
        }
        return if (automation.autoSwitchSceneVoices) {
            runCatching {
                applySceneVoiceAssignments(book, prepared.storyboard, characters, prepared.roles)
            }.onFailure { error ->
                AppLog.put("缓存场景后台自动选音失败，已继续使用角色绑定\n${error.localizedMessage}", error)
            }.getOrDefault(prepared.storyboard)
        } else {
            prepared.storyboard
        }
    }

    suspend fun assignUnboundRoles(workKey: String): Int {
        val snapshot = prepareMutex(workKey).withLock { currentCastingSnapshot(workKey) }
            ?: return 0
        return assignTargets(snapshot.engine, workKey, snapshot.targets, replaceAuto = false)
    }

    /**
     * 缓存分镜开始合成前的唯一选角关口。这里只补真正缺失或已经失效的绑定，
     * 不把临时音色的证据复评从后台重新搬回播放前台。
     */
    suspend fun assignMissingRolesForPlayback(
        workKey: String,
        onAssignmentRequired: () -> Unit = {}
    ): Int {
        var preparationShown = false
        fun showPreparation() {
            if (!preparationShown) {
                preparationShown = true
                onAssignmentRequired()
            }
        }
        val initialEngine = prepareMutex(workKey).withLock {
            currentMultiRoleEngine()
        } ?: return 0
        if (initialEngine.enabledVoices().isEmpty() && initialEngine.supportsVoiceFetch()) {
            showPreparation()
            try {
                TtsEngineStore.ensureVoiceCatalog(
                    engineId = initialEngine.id,
                    restartReadAloud = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.put(
                    "多人朗读发音人目录初始化失败，已继续使用对白兜底\n${error.localizedMessage}",
                    error
                )
                return 0
            }
        }
        val snapshot = prepareMutex(workKey).withLock {
            val current = currentCastingSnapshot(workKey) ?: return@withLock null
            val usableVoiceIds = current.engine.enabledVoices()
                .mapTo(mutableSetOf()) { it.id }
            if (usableVoiceIds.isEmpty()) return@withLock null
            val bindings = appDb.bookCharacterDao.getTtsBindings(workKey)
                .filter { it.engineId == current.engine.id }
                .associateBy { it.targetType to it.targetId }
            val missingTargets = current.targets.filter { target ->
                BookTtsBindingPolicy.needsPlaybackAssignment(
                    binding = bindings[target.targetType to target.targetId],
                    usableVoiceIds = usableVoiceIds
                )
            }
            current.copy(targets = missingTargets).takeIf { missingTargets.isNotEmpty() }
        } ?: return 0
        showPreparation()
        return assignTargets(snapshot.engine, workKey, snapshot.targets, replaceAuto = false)
    }

    suspend fun reassignTemporaryRoles(workKey: String): Int {
        val snapshot = prepareMutex(workKey).withLock {
            val engine = currentMultiRoleEngine() ?: return@withLock null
            val targets = appDb.bookCharacterDao.getTtsCastRoles(workKey)
                .filter { it.linkedCharacterId == null && !it.ignored && it.isVisibleTemporaryRole() }
                .mapNotNull { it.toCastingTarget() }
            CastingSnapshot(engine, targets)
        } ?: return 0
        return assignTargets(snapshot.engine, workKey, snapshot.targets, replaceAuto = true)
    }

    fun linkPromotedRoles(workKey: String, characters: List<BookCharacter>) {
        val dao = appDb.bookCharacterDao
        val characterById = characters.associateBy { it.id }
        dao.getTtsCastRoles(workKey)
            .filter { it.linkedCharacterId != null }
            .forEach { role ->
                val character = role.linkedCharacterId?.let(characterById::get)
                if (character != null) {
                    mergeRoleIntoCharacter(character, role)
                } else {
                    dao.deleteTtsBindings(
                        role.workKey,
                        BookCharacterTtsBinding.TargetType.CAST_ROLE,
                        role.id
                    )
                    dao.deleteTtsCastRole(role)
                }
            }
        val canonicalNameIndex = buildMap {
            characters.forEach { character ->
                buildList {
                    add(character.name.trim())
                    character.aliasesJson
                        ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
                        .orEmpty()
                        .mapTo(this) { it.trim() }
                }.filter { it.isNotBlank() }.forEach { name ->
                    putIfAbsent(normalizeIdentityName(name), character.id)
                }
            }
        }
        dao.getTtsCastRoles(workKey)
            .filter { it.linkedCharacterId == null && it.isRoutableRole() }
            .forEach { role ->
                val characterId = buildList {
                    add(role.name)
                    GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty().forEach(::add)
                }.asSequence()
                    .map(::normalizeIdentityName)
                    .mapNotNull(canonicalNameIndex::get)
                    .firstOrNull()
                    ?: return@forEach
                characterById[characterId]?.let { mergeRoleIntoCharacter(it, role) }
            }
    }

    private fun mergeRoleIntoCharacter(character: BookCharacter, role: BookTtsCastRole) {
        val aliases = buildList {
            character.aliasesJson
                ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
                .orEmpty()
                .forEach(::add)
            add(role.name)
            roleAliases(role).forEach(::add)
        }.map(String::trim)
            .filter {
                it.isNotBlank() &&
                    normalizeIdentityName(it) != normalizeIdentityName(character.name)
            }
            .distinctBy(::normalizeIdentityName)
        character.aliasesJson = aliases.takeIf { it.isNotEmpty() }?.let(GSON::toJson)
        appDb.bookCharacterDao.updateCharacter(character)
        appDb.bookCharacterDao.mergeTtsCastRoleIntoCharacter(role, character.id)
    }

    internal fun normalizeIdentityName(name: String): String {
        val boundaryPunctuation = setOf(
            '“', '”', '‘', '’', '「', '」', '『', '』', ':', '：', '，', ',', '。', '.', '！', '!', '？', '?'
        )
        return name.trim { it.isWhitespace() || it in boundaryPunctuation }
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    internal fun isStableCastName(name: String): Boolean {
        val value = name.trim().trim('“', '”', '‘', '’', '「', '」', '『', '』', ':', '：')
        if (value.length !in 2..16 || value in reservedNames || value in pronouns) return false
        if (value.endsWith("等人") || value.contains("未知") || value.contains("待确认")) return false
        return value.any { it.isLetter() }
    }

    fun discardCachedChapter(workKey: String, chapterIndex: Int) {
        appDb.runInTransaction {
            val dao = appDb.bookCharacterDao
            val affectedRoleIds = dao.getTtsCastRoleContributions(workKey, chapterIndex)
                .mapTo(mutableSetOf()) { it.roleId }
            dao.deleteTtsCastRoleContributions(workKey, chapterIndex)
            affectedRoleIds.forEach(::rebuildRoleFromContributions)
        }
    }

    private fun explicitIdentityLinksFromStoryboard(
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>
    ): List<StoryboardIdentityLink> {
        val targets = buildList {
            characters.forEach { add(IdentityTarget(it.name, it.id, null)) }
            castRoles.filter { it.linkedCharacterId == null && !it.ignored }
                .forEach { add(IdentityTarget(it.name, null, it.id)) }
        }.filter { it.name.isNotBlank() }
            .distinctBy { normalizeIdentityName(it.name) }
        if (targets.isEmpty()) return emptyList()
        val text = storyboard.scenes.asSequence()
            .flatMap { it.segments.asSequence() }
            .sortedWith(compareBy<StoryboardSegment> { it.paragraphIndex }.thenBy { it.start })
            .joinToString("\n") { it.text }
        if (text.isBlank()) return emptyList()
        val mappings = findExplicitAliasMappings(text, targets.map { it.name })
        val targetIndex = targets.associateBy { normalizeIdentityName(it.name) }
        return mappings.mapNotNull { (alias, canonicalName) ->
            val target = targetIndex[normalizeIdentityName(canonicalName)] ?: return@mapNotNull null
            StoryboardIdentityLink(
                aliasName = alias,
                characterId = target.characterId,
                castRoleId = target.castRoleId,
                evidence = "正文明确说明“$alias”属于“${target.name}”"
            )
        }
    }

    internal fun findExplicitAliasMappings(
        text: String,
        canonicalNames: List<String>
    ): Map<String, String> {
        if (text.isBlank() || canonicalNames.isEmpty()) return emptyMap()
        val results = linkedMapOf<String, String>()
        val questionPattern = Regex(
            "(?:^|[\\s，。！？!?；;：:、【\\[])" +
                "([\\p{L}\\p{N}_·]{2,16})是谁[？?]?"
        )
        questionPattern.findAll(text).forEach { match ->
            val alias = match.groupValues[1].trim()
            val tail = text.substring(match.range.last + 1).take(100)
            val owner = canonicalNames.firstOrNull { canonicalName ->
                Regex(
                    "(?:哦|原来|查到|发现)?[，,：:\\s]*是\\s*" +
                        Regex.escape(canonicalName) +
                        "(?=[，。！？!?；;：:\\s]|$)"
                ).containsMatchIn(tail)
            }
            if (owner != null && normalizeIdentityName(alias) != normalizeIdentityName(owner)) {
                results.putIfAbsent(alias, owner)
            }
        }
        canonicalNames.forEach { canonicalName ->
            val escapedName = Regex.escape(canonicalName)
            Regex(
                "$escapedName\\s*的(?:QQ|微信|群)?(?:网名|昵称|账号|群名片|代号|乳名|外号)" +
                    "\\s*(?:是|叫|为)\\s*[【\\[]?([\\p{L}\\p{N}_·]{2,16})[】\\]]?"
            ).findAll(text).forEach { match ->
                results.putIfAbsent(match.groupValues[1].trim(), canonicalName)
            }
            Regex(
                "(?:^|[\\s，。！？!?；;：:、【\\[])" +
                    "([\\p{L}\\p{N}_·]{2,16})\\s*是\\s*$escapedName\\s*的" +
                    "(?:QQ|微信|群)?(?:网名|昵称|账号|群名片|代号|乳名|外号)"
            ).findAll(text).forEach { match ->
                results.putIfAbsent(match.groupValues[1].trim(), canonicalName)
            }
        }
        return results
    }

    private fun mergeIdentityLinks(
        original: List<StoryboardIdentityLink>,
        derived: List<StoryboardIdentityLink>
    ): List<StoryboardIdentityLink> = (original + derived).distinctBy {
        Triple(normalizeIdentityName(it.aliasName), it.characterId, it.castRoleId)
    }

    private fun syncCastRoles(
        book: Book,
        chapterIndex: Int,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ): SyncedCastRoles {
        val dao = appDb.bookCharacterDao
        val profile = dao.getOrCreateProfile(book.name, book.author, book.bookUrl)
        linkPromotedRoles(profile.workKey, characters)
        applyExplicitIdentityLinks(profile.workKey, storyboard.identityLinks, characters)
        mergeExplicitFormalRoles(profile.workKey, storyboard, characters)
        val canonicalNames = characters.flatMap { character ->
            buildList {
                add(character.name.trim())
                character.aliasesJson
                    ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
                    .orEmpty()
                    .mapTo(this) { it.trim() }
            }
        }.filter { it.isNotBlank() }.map(::normalizeIdentityName).toSet()
        val discovered = storyboard.scenes
            .flatMap { it.segments }
            .filter { it.type == StoryboardSegmentType.DIALOGUE || it.type == StoryboardSegmentType.THOUGHT }
            .filter { it.speakerId == null || it.speakerId <= 0L }
            .mapNotNull { segment ->
                val name = segment.speakerName?.trim().orEmpty()
                val gender = segment.speakerGender.takeIf {
                    it == BookCharacter.Gender.MALE || it == BookCharacter.Gender.FEMALE
                } ?: BookCharacter.Gender.UNKNOWN
                val normalizedName = normalizeIdentityName(name)
                if (normalizedName in canonicalNames) return@mapNotNull null
                DiscoveredOccurrence(
                    name = name,
                    gender = gender,
                    text = segment.text.trim(),
                    castRoleId = segment.castRoleId,
                    identityType = segment.identityType,
                    nameType = segment.nameType,
                    identityEvidence = segment.identityEvidence,
                    genderEvidence = segment.genderEvidence,
                    mergeCastRoleIds = segment.mergeCastRoleIds,
                    evidence = segment.evidence.removePrefix("AI归因：").trim()
                )
            }
        val bindings = dao.getTtsBindings(profile.workKey)
        discovered.filter { it.identityType == StoryboardSegment.IdentityType.GUEST }
            .groupBy { normalizeIdentityName(it.name) }
            .forEach { (normalizedName, occurrences) ->
                val old = roleNameIndex(dao.getTtsCastRoles(profile.workKey))[normalizedName]
                    ?: return@forEach
                if (old.linkedCharacterId != null || old.ignored || hasProtectedBinding(old.id, bindings)) {
                    return@forEach
                }
                if (!shouldDowngradeRoleToGuest(
                        old,
                        occurrences.any { it.identityEvidence == StoryboardSegment.Evidence.EXPLICIT }
                    )
                ) return@forEach
                old.identityState = BookTtsCastRole.IdentityState.GUEST
                old.updatedAt = System.currentTimeMillis()
                dao.updateTtsCastRole(old)
                dao.deleteTtsBindings(
                    old.workKey,
                    BookCharacterTtsBinding.TargetType.CAST_ROLE,
                    old.id
                )
            }

        val activeRoleIds = discovered.filter {
            it.identityType == StoryboardSegment.IdentityType.CAST_ROLE ||
                it.identityType == StoryboardSegment.IdentityType.STABLE_CANDIDATE ||
                it.identityType == StoryboardSegment.IdentityType.PENDING
        }.filter { it.name.isNotBlank() && isStableCastName(it.name) }
            .groupBy { occurrence ->
                occurrence.castRoleId?.takeIf { it > 0L }?.let { "id:$it" }
                    ?: "name:${normalizeIdentityName(occurrence.name)}"
            }
            .values
            .mapNotNull { occurrences ->
                upsertDiscoveredRole(
                    workKey = profile.workKey,
                    chapterIndex = chapterIndex,
                    cacheKey = storyboard.sourceCacheKey.ifBlank { "legacy:$chapterIndex" },
                    cacheRevision = storyboard.sourceCacheRevision.takeIf { it > 0L } ?: 1L,
                    occurrences = occurrences
                )
            }.toSet()
        val cacheKey = storyboard.sourceCacheKey.ifBlank { "legacy:$chapterIndex" }
        val cacheRevision = storyboard.sourceCacheRevision.takeIf { it > 0L } ?: 1L
        val previousRoleIds = dao.getTtsCastRoleContributions(profile.workKey, chapterIndex)
            .mapTo(mutableSetOf()) { it.roleId }
        dao.deleteStaleTtsCastRoleContributions(
            profile.workKey,
            chapterIndex,
            cacheKey,
            cacheRevision
        )
        (previousRoleIds + activeRoleIds).forEach(::rebuildRoleFromContributions)

        val refreshedRoles = dao.getTtsCastRoles(profile.workKey)
        val derivedLinks = explicitIdentityLinksFromStoryboard(storyboard, characters, refreshedRoles)
        val allLinks = mergeIdentityLinks(storyboard.identityLinks, derivedLinks)
        val linkedRoleIds = applyExplicitIdentityLinks(profile.workKey, allLinks, characters)
        linkedRoleIds.forEach(::rebuildRoleFromContributions)
        return SyncedCastRoles(allLinks, dao.getTtsCastRoles(profile.workKey))
    }

    private fun applyExplicitIdentityLinks(
        workKey: String,
        identityLinks: List<StoryboardIdentityLink>,
        characters: List<BookCharacter>
    ): Set<Long> {
        if (identityLinks.isEmpty()) return emptySet()
        val dao = appDb.bookCharacterDao
        val characterIndex = characters.associateBy { it.id }
        val affectedRoleIds = mutableSetOf<Long>()
        identityLinks.distinctBy { link ->
            Triple(
                normalizeIdentityName(link.aliasName),
                link.characterId,
                link.castRoleId
            )
        }.forEach { link ->
            val alias = link.aliasName.trim()
            val normalizedAlias = normalizeIdentityName(alias)
            if (!isStableCastName(alias) || normalizedAlias.isBlank()) return@forEach
            val character = link.characterId?.let(characterIndex::get)
            if (character != null) {
                if (normalizedAlias == normalizeIdentityName(character.name)) return@forEach
                val aliases = character.aliasesJson
                    ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
                    .orEmpty()
                    .plus(alias)
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinctBy(::normalizeIdentityName)
                character.aliasesJson = GSON.toJson(aliases)
                dao.updateCharacter(character)
                roleNameIndex(dao.getTtsCastRoles(workKey))[normalizedAlias]
                    ?.takeIf { it.linkedCharacterId == null && !it.ignored }
                    ?.let {
                        affectedRoleIds += it.id
                        mergeRoleIntoCharacter(character, it)
                    }
                return@forEach
            }
            var target = link.castRoleId
                ?.let(dao::getTtsCastRole)
                ?.takeIf { it.workKey == workKey && it.linkedCharacterId == null && !it.ignored }
                ?: return@forEach
            if (normalizedAlias == normalizeIdentityName(target.name)) return@forEach
            val source = roleNameIndex(dao.getTtsCastRoles(workKey))[normalizedAlias]
            if (source != null && source.id != target.id &&
                source.linkedCharacterId == null && !source.ignored
            ) {
                target = mergeRoles(target, source)
            }
            target.aliasesJson = GSON.toJson(
                (roleAliases(target) + alias)
                    .map(String::trim)
                    .filter { it.isNotBlank() && normalizeIdentityName(it) != normalizeIdentityName(target.name) }
                    .distinctBy(::normalizeIdentityName)
            )
            target.identityEvidence = BookTtsCastRole.Evidence.EXPLICIT
            target.identityEvidenceJson = GSON.toJson(
                (roleEvidence(target) + link.evidence.trim())
                    .filter(String::isNotBlank)
                    .distinct()
                    .takeLast(6)
            )
            target.updatedAt = System.currentTimeMillis()
            dao.updateTtsCastRole(target)
            affectedRoleIds += target.id
        }
        return affectedRoleIds
    }

    private fun mergeExplicitFormalRoles(
        workKey: String,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ) {
        val dao = appDb.bookCharacterDao
        val characterIndex = characters.associateBy { it.id }
        storyboard.scenes.asSequence()
            .flatMap { it.segments.asSequence() }
            .filter { it.identityType == StoryboardSegment.IdentityType.FORMAL_CHARACTER }
            .mapNotNull { segment ->
                val character = segment.speakerId?.let(characterIndex::get) ?: return@mapNotNull null
                segment.mergeCastRoleIds.map { character to it }
            }
            .flatten()
            .distinctBy { (character, roleId) -> character.id to roleId }
            .forEach { (character, roleId) ->
                val role = dao.getTtsCastRole(roleId) ?: return@forEach
                if (role.workKey != workKey || role.ignored || role.linkedCharacterId != null) return@forEach
                mergeRoleIntoCharacter(character, role)
            }
    }

    private fun upsertDiscoveredRole(
        workKey: String,
        chapterIndex: Int,
        cacheKey: String,
        cacheRevision: Long,
        occurrences: List<DiscoveredOccurrence>
    ): Long? {
        val dao = appDb.bookCharacterDao
        val currentRoles = dao.getTtsCastRoles(workKey)
        val byName = roleNameIndex(currentRoles)
        val explicitProperName = occurrences.firstOrNull {
            it.nameType == StoryboardSegment.NameType.PROPER_NAME &&
                it.identityEvidence == StoryboardSegment.Evidence.EXPLICIT
        }
        val preferred = explicitProperName ?: occurrences.maxByOrNull {
            evidenceRank(it.identityEvidence) * 10 + nameTypeRank(it.nameType)
        } ?: return null
        val preferredName = preferred.name.trim()
        val preferredNormalizedName = normalizeIdentityName(preferredName)
        val referenced = occurrences.asSequence()
            .mapNotNull { it.castRoleId?.takeIf { id -> id > 0L } }
            .mapNotNull(dao::getTtsCastRole)
            .firstOrNull { it.workKey == workKey && !it.ignored && it.linkedCharacterId == null }
        var target = byName[preferredNormalizedName] ?: referenced
        val mergeIds = occurrences.flatMap { it.mergeCastRoleIds }.toMutableSet()
        if (target != null && referenced != null && target.id != referenced.id) mergeIds += referenced.id
        if (target == null) {
            val now = System.currentTimeMillis()
            val initialCounts = mapOf(chapterIndex.toString() to occurrences.size)
            val identityState = resolvedIdentityState(null, occurrences, preferred)
            val role = BookTtsCastRole(
                workKey = workKey,
                name = preferredName,
                gender = preferred.gender,
                aliasesJson = "[]",
                firstChapterIndex = chapterIndex,
                lastChapterIndex = chapterIndex,
                occurrenceCount = occurrences.size,
                representativeTextsJson = GSON.toJson(representativeTexts(emptyList(), occurrences)),
                identityState = identityState,
                nameType = preferred.nameType,
                identityEvidence = preferred.identityEvidence,
                genderEvidence = preferred.genderEvidence,
                chapterOccurrencesJson = GSON.toJson(initialCounts),
                identityEvidenceJson = GSON.toJson(identityEvidenceItems(emptyList(), occurrences)),
                createdAt = now,
                updatedAt = now
            )
            val newId = dao.upsertTtsCastRole(role)
            target = dao.getTtsCastRole(newId) ?: role.copy(id = newId)
        }
        var resolvedTarget: BookTtsCastRole = checkNotNull(target)

        mergeIds.filter { it > 0L && it != resolvedTarget.id }.distinct().forEach { sourceId ->
            val source = dao.getTtsCastRole(sourceId) ?: return@forEach
            if (source.workKey != workKey || source.ignored || source.linkedCharacterId != null) return@forEach
            resolvedTarget = mergeRoles(resolvedTarget, source)
        }

        val aliases = roleAliases(resolvedTarget).toMutableList()
        if (normalizeIdentityName(resolvedTarget.name) != preferredNormalizedName &&
            explicitProperName != null &&
            resolvedTarget.nameType != BookTtsCastRole.NameType.PROPER_NAME
        ) {
            aliases += resolvedTarget.name
            resolvedTarget.name = preferredName
        }
        occurrences.map { it.name.trim() }
            .filter { it.isNotBlank() && normalizeIdentityName(it) != normalizeIdentityName(resolvedTarget.name) }
            .forEach(aliases::add)
        resolvedTarget.aliasesJson = GSON.toJson(aliases.distinctBy(::normalizeIdentityName))

        val strongestGender = occurrences.maxByOrNull { evidenceRank(it.genderEvidence) }
        if (strongestGender != null &&
            evidenceRank(strongestGender.genderEvidence) > evidenceRank(resolvedTarget.genderEvidence)
        ) {
            resolvedTarget.gender = strongestGender.gender
            resolvedTarget.genderEvidence = strongestGender.genderEvidence
        }
        if (evidenceRank(preferred.identityEvidence) > evidenceRank(resolvedTarget.identityEvidence)) {
            resolvedTarget.identityEvidence = preferred.identityEvidence
        }
        if (nameTypeRank(preferred.nameType) > nameTypeRank(resolvedTarget.nameType)) {
            resolvedTarget.nameType = preferred.nameType
        }
        val nextIdentityState = resolvedIdentityState(resolvedTarget, occurrences, preferred)
        resolvedTarget.identityState = if (
            nextIdentityState == BookTtsCastRole.IdentityState.PENDING &&
            hasProtectedBinding(resolvedTarget.id, dao.getTtsBindings(workKey))
        ) {
            BookTtsCastRole.IdentityState.STABLE
        } else {
            nextIdentityState
        }

        val chapterCounts = chapterCounts(resolvedTarget).toMutableMap()
        chapterCounts[chapterIndex.toString()] = occurrences.size
        resolvedTarget.chapterOccurrencesJson = GSON.toJson(chapterCounts)
        resolvedTarget.occurrenceCount = chapterCounts.values.sum()
        val chapterIndexes = chapterCounts.keys.mapNotNull(String::toIntOrNull)
        if (chapterIndexes.isNotEmpty()) {
            resolvedTarget.firstChapterIndex = chapterIndexes.min()
            resolvedTarget.lastChapterIndex = chapterIndexes.max()
        }
        resolvedTarget.representativeTextsJson = GSON.toJson(
            representativeTexts(roleSamples(resolvedTarget), occurrences)
        )
        resolvedTarget.identityEvidenceJson = GSON.toJson(
            identityEvidenceItems(roleEvidence(resolvedTarget), occurrences)
        )
        resolvedTarget.updatedAt = System.currentTimeMillis()
        dao.updateTtsCastRole(resolvedTarget)
        val contributionGender = occurrences.maxByOrNull { evidenceRank(it.genderEvidence) }
        val now = System.currentTimeMillis()
        dao.upsertTtsCastRoleContribution(
            BookTtsCastRoleContribution(
                workKey = workKey,
                chapterIndex = chapterIndex,
                roleId = resolvedTarget.id,
                cacheKey = cacheKey,
                cacheRevision = cacheRevision,
                namesJson = GSON.toJson(
                    (listOf(preferred.name.trim()) + occurrences.map { it.name.trim() })
                        .filter(String::isNotBlank)
                        .distinctBy(::normalizeIdentityName)
                ),
                gender = contributionGender?.gender ?: BookCharacter.Gender.UNKNOWN,
                identityState = resolvedIdentityState(null, occurrences, preferred),
                nameType = preferred.nameType,
                identityEvidence = preferred.identityEvidence,
                genderEvidence = contributionGender?.genderEvidence ?: BookTtsCastRole.Evidence.UNKNOWN,
                occurrenceCount = occurrences.size,
                representativeTextsJson = GSON.toJson(representativeTexts(emptyList(), occurrences)),
                identityEvidenceJson = GSON.toJson(identityEvidenceItems(emptyList(), occurrences)),
                createdAt = now,
                updatedAt = now
            )
        )
        return resolvedTarget.id
    }

    private fun mergeRoles(target: BookTtsCastRole, source: BookTtsCastRole): BookTtsCastRole {
        val dao = appDb.bookCharacterDao
        mergeRoleContributions(target.id, source.id)
        val aliases = (roleAliases(target) + source.name + roleAliases(source))
            .filter { normalizeIdentityName(it) != normalizeIdentityName(target.name) }
            .distinctBy(::normalizeIdentityName)
        target.aliasesJson = GSON.toJson(aliases)
        target.representativeTextsJson = GSON.toJson(
            (roleSamples(target) + roleSamples(source)).filter { it.isNotBlank() }.distinct().take(4)
        )
        target.identityEvidenceJson = GSON.toJson(
            (roleEvidence(target) + roleEvidence(source)).filter { it.isNotBlank() }.distinct().take(6)
        )
        val counts = chapterCounts(target).toMutableMap()
        chapterCounts(source).forEach { (chapter, count) ->
            counts[chapter] = (counts[chapter] ?: 0) + count
        }
        target.chapterOccurrencesJson = GSON.toJson(counts)
        target.occurrenceCount = counts.values.sum()
        if (evidenceRank(source.genderEvidence) > evidenceRank(target.genderEvidence)) {
            target.gender = source.gender
            target.genderEvidence = source.genderEvidence
        }
        if (evidenceRank(source.identityEvidence) > evidenceRank(target.identityEvidence)) {
            target.identityEvidence = source.identityEvidence
        }
        if (nameTypeRank(source.nameType) > nameTypeRank(target.nameType)) target.nameType = source.nameType
        if (identityStateRank(source.identityState) > identityStateRank(target.identityState)) {
            target.identityState = source.identityState
        }
        target.firstChapterIndex = minOf(target.firstChapterIndex, source.firstChapterIndex)
        target.lastChapterIndex = maxOf(target.lastChapterIndex, source.lastChapterIndex)

        val bindings = dao.getTtsBindings(target.workKey)
        bindings.filter {
            it.targetType == BookCharacterTtsBinding.TargetType.CAST_ROLE && it.targetId == source.id
        }.forEach { sourceBinding ->
            val targetBinding = bindings.firstOrNull {
                it.targetType == BookCharacterTtsBinding.TargetType.CAST_ROLE &&
                    it.targetId == target.id &&
                    it.engineId == sourceBinding.engineId
            }
            if (targetBinding == null || bindingRank(sourceBinding.bindingMode) > bindingRank(targetBinding.bindingMode)) {
                dao.upsertTtsBinding(
                    sourceBinding.copy(targetId = target.id, updatedAt = System.currentTimeMillis())
                )
            }
            dao.deleteTtsBinding(
                sourceBinding.workKey,
                sourceBinding.targetType,
                sourceBinding.targetId,
                sourceBinding.engineId
            )
        }
        dao.deleteTtsCastRole(source)
        return target
    }

    private fun mergeRoleContributions(targetRoleId: Long, sourceRoleId: Long) {
        val dao = appDb.bookCharacterDao
        val targetByChapter = dao.getTtsCastRoleContributions(targetRoleId)
            .associateBy { it.chapterIndex }
        dao.getTtsCastRoleContributions(sourceRoleId).forEach { source ->
            val target = targetByChapter[source.chapterIndex]
            val merged = if (target == null) {
                source.copy(roleId = targetRoleId, updatedAt = System.currentTimeMillis())
            } else {
                val primary = listOf(target, source).maxWithOrNull(
                    compareBy<BookTtsCastRoleContribution> { nameTypeRank(it.nameType) }
                        .thenBy { evidenceRank(it.identityEvidence) }
                        .thenBy { it.cacheRevision }
                ) ?: target
                target.copy(
                    cacheKey = primary.cacheKey,
                    cacheRevision = maxOf(target.cacheRevision, source.cacheRevision),
                    namesJson = GSON.toJson(
                        (contributionNames(primary) + contributionNames(target) + contributionNames(source))
                            .filter(String::isNotBlank)
                            .distinctBy(::normalizeIdentityName)
                    ),
                    gender = if (evidenceRank(source.genderEvidence) > evidenceRank(target.genderEvidence)) {
                        source.gender
                    } else {
                        target.gender
                    },
                    identityState = if (identityStateRank(source.identityState) > identityStateRank(target.identityState)) {
                        source.identityState
                    } else {
                        target.identityState
                    },
                    nameType = primary.nameType,
                    identityEvidence = primary.identityEvidence,
                    genderEvidence = listOf(target, source).maxByOrNull {
                        evidenceRank(it.genderEvidence)
                    }?.genderEvidence ?: target.genderEvidence,
                    occurrenceCount = target.occurrenceCount + source.occurrenceCount,
                    representativeTextsJson = GSON.toJson(
                        (contributionSamples(target) + contributionSamples(source))
                            .filter(String::isNotBlank).distinct().take(4)
                    ),
                    identityEvidenceJson = GSON.toJson(
                        (contributionEvidence(target) + contributionEvidence(source))
                            .filter(String::isNotBlank).distinct().take(6)
                    ),
                    updatedAt = System.currentTimeMillis()
                )
            }
            dao.upsertTtsCastRoleContribution(merged)
            dao.deleteTtsCastRoleContribution(source)
        }
    }

    private fun rebuildRoleFromContributions(roleId: Long) {
        val dao = appDb.bookCharacterDao
        val role = dao.getTtsCastRole(roleId) ?: return
        val contributions = dao.getTtsCastRoleContributions(roleId)
        if (contributions.isEmpty()) {
            val bindings = dao.getTtsBindings(role.workKey)
            if (role.linkedCharacterId != null || hasProtectedBinding(role.id, bindings)) return
            bindings.filter {
                it.targetType == BookCharacterTtsBinding.TargetType.CAST_ROLE && it.targetId == role.id
            }.forEach {
                dao.deleteTtsBinding(it.workKey, it.targetType, it.targetId, it.engineId)
            }
            dao.deleteTtsCastRole(role)
            return
        }
        val primary = contributions.maxWithOrNull(
            compareBy<BookTtsCastRoleContribution> { nameTypeRank(it.nameType) }
                .thenBy { evidenceRank(it.identityEvidence) }
                .thenBy { identityStateRank(it.identityState) }
                .thenBy { it.cacheRevision }
        ) ?: return
        val persistedNames = listOf(role.name) + roleAliases(role)
        val persistedIdentityEvidence = role.identityEvidence
        val persistedIdentityEvidenceItems = roleEvidence(role)
        val preferredName = contributionNames(primary).firstOrNull().orEmpty()
        val existingWithPreferredName = preferredName.takeIf(String::isNotBlank)
            ?.let { dao.getTtsCastRoleByName(role.workKey, it) }
        if (preferredName.isNotBlank() && (existingWithPreferredName == null || existingWithPreferredName.id == role.id)) {
            role.name = preferredName
        }
        val names = contributions.flatMap(::contributionNames)
        role.aliasesJson = GSON.toJson(
            persistentAliases(role.name, persistedNames + names)
        )
        val strongestGender = contributions.maxByOrNull { evidenceRank(it.genderEvidence) }
        if (strongestGender != null) {
            role.gender = strongestGender.gender
            role.genderEvidence = strongestGender.genderEvidence
        }
        role.identityState = (listOf(role.identityState) + contributions.map { it.identityState })
            .maxByOrNull(::identityStateRank) ?: role.identityState
        role.nameType = primary.nameType
        role.identityEvidence = listOf(persistedIdentityEvidence, primary.identityEvidence)
            .maxByOrNull(::evidenceRank) ?: primary.identityEvidence
        val chapterCounts = contributions.associate { it.chapterIndex.toString() to it.occurrenceCount }
        role.chapterOccurrencesJson = GSON.toJson(chapterCounts)
        role.occurrenceCount = chapterCounts.values.sum()
        role.firstChapterIndex = contributions.minOf { it.chapterIndex }
        role.lastChapterIndex = contributions.maxOf { it.chapterIndex }
        role.representativeTextsJson = GSON.toJson(
            contributions.sortedByDescending { it.cacheRevision }
                .flatMap(::contributionSamples).filter(String::isNotBlank).distinct().take(4)
        )
        role.identityEvidenceJson = GSON.toJson(
            (persistedIdentityEvidenceItems + contributions.sortedByDescending { it.cacheRevision }
                .flatMap(::contributionEvidence))
                .filter(String::isNotBlank).distinct().takeLast(6)
        )
        role.updatedAt = System.currentTimeMillis()
        dao.updateTtsCastRole(role)
    }

    internal fun persistentAliases(canonicalName: String, names: List<String>): List<String> =
        names.map(String::trim)
            .filter { it.isNotBlank() && normalizeIdentityName(it) != normalizeIdentityName(canonicalName) }
            .distinctBy(::normalizeIdentityName)

    private fun resolvedIdentityState(
        old: BookTtsCastRole?,
        occurrences: List<DiscoveredOccurrence>,
        preferred: DiscoveredOccurrence
    ): String {
        if (old != null &&
            occurrences.all { it.identityType == StoryboardSegment.IdentityType.PENDING } &&
            canDowngradeLegacyStableRole(old)
        ) return BookTtsCastRole.IdentityState.PENDING
        if (old?.identityState == BookTtsCastRole.IdentityState.STABLE) return old.identityState
        if (occurrences.any { it.identityType == StoryboardSegment.IdentityType.STABLE_CANDIDATE }) {
            return BookTtsCastRole.IdentityState.STABLE
        }
        if (preferred.nameType == StoryboardSegment.NameType.PROPER_NAME &&
            preferred.identityEvidence == StoryboardSegment.Evidence.EXPLICIT
        ) return BookTtsCastRole.IdentityState.STABLE
        return BookTtsCastRole.IdentityState.PENDING
    }

    internal fun canDowngradeLegacyStableRole(role: BookTtsCastRole): Boolean =
        role.identityState == BookTtsCastRole.IdentityState.STABLE &&
            evidenceRank(role.identityEvidence) <= evidenceRank(BookTtsCastRole.Evidence.INFERRED) &&
            nameTypeRank(role.nameType) <= nameTypeRank(BookTtsCastRole.NameType.GENERIC_LABEL)

    internal fun shouldDowngradeRoleToGuest(
        role: BookTtsCastRole,
        hasExplicitGuestEvidence: Boolean
    ): Boolean = hasExplicitGuestEvidence &&
        role.identityState == BookTtsCastRole.IdentityState.PENDING &&
        role.firstChapterIndex == role.lastChapterIndex &&
        evidenceRank(role.identityEvidence) <= evidenceRank(BookTtsCastRole.Evidence.INFERRED) &&
        nameTypeRank(role.nameType) <= nameTypeRank(BookTtsCastRole.NameType.GENERIC_LABEL)

    private fun roleNameIndex(roles: List<BookTtsCastRole>): Map<String, BookTtsCastRole> = buildMap {
        roles.filter { !it.ignored && it.linkedCharacterId == null }.forEach { role ->
            (listOf(role.name) + roleAliases(role)).filter { it.isNotBlank() }.forEach { name ->
                putIfAbsent(normalizeIdentityName(name), role)
            }
        }
    }

    private fun roleAliases(role: BookTtsCastRole): List<String> =
        GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty()

    private fun roleSamples(role: BookTtsCastRole): List<String> =
        GSON.fromJsonObject<List<String>>(role.representativeTextsJson).getOrNull().orEmpty()

    private fun roleEvidence(role: BookTtsCastRole): List<String> =
        GSON.fromJsonObject<List<String>>(role.identityEvidenceJson).getOrNull().orEmpty()

    private fun contributionNames(contribution: BookTtsCastRoleContribution): List<String> =
        GSON.fromJsonObject<List<String>>(contribution.namesJson).getOrNull().orEmpty()

    private fun contributionSamples(contribution: BookTtsCastRoleContribution): List<String> =
        GSON.fromJsonObject<List<String>>(contribution.representativeTextsJson).getOrNull().orEmpty()

    private fun contributionEvidence(contribution: BookTtsCastRoleContribution): List<String> =
        GSON.fromJsonObject<List<String>>(contribution.identityEvidenceJson).getOrNull().orEmpty()

    private fun chapterCounts(role: BookTtsCastRole): Map<String, Int> =
        GSON.fromJsonObject<Map<String, Int>>(role.chapterOccurrencesJson).getOrNull().orEmpty()
            .filterValues { it >= 0 }

    private fun representativeTexts(
        existing: List<String>,
        occurrences: List<DiscoveredOccurrence>
    ): List<String> = (existing + occurrences.map { it.text })
        .map { it.trim().take(120) }
        .filter { it.isNotBlank() }
        .distinct()
        .take(4)

    private fun identityEvidenceItems(
        existing: List<String>,
        occurrences: List<DiscoveredOccurrence>
    ): List<String> = (existing + occurrences.map { it.evidence })
        .map { it.trim().take(120) }
        .filter { it.isNotBlank() }
        .distinct()
        .take(6)

    private fun hasProtectedBinding(roleId: Long, bindings: List<BookCharacterTtsBinding>): Boolean =
        bindings.any {
            it.targetType == BookCharacterTtsBinding.TargetType.CAST_ROLE &&
                it.targetId == roleId &&
                it.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO
        }

    internal fun evidenceRank(value: String): Int = when (value) {
        BookTtsCastRole.Evidence.EXPLICIT -> 3
        BookTtsCastRole.Evidence.CONTEXTUAL -> 2
        BookTtsCastRole.Evidence.INFERRED -> 1
        else -> 0
    }

    internal fun nameTypeRank(value: String): Int = when (value) {
        BookTtsCastRole.NameType.PROPER_NAME -> 4
        BookTtsCastRole.NameType.ALIAS -> 3
        BookTtsCastRole.NameType.UNIQUE_TITLE -> 2
        BookTtsCastRole.NameType.GENERIC_LABEL -> 1
        else -> 0
    }

    private fun identityStateRank(value: String): Int = when (value) {
        BookTtsCastRole.IdentityState.STABLE -> 3
        BookTtsCastRole.IdentityState.PENDING -> 2
        else -> 1
    }

    private fun bindingRank(value: String?): Int = when (value) {
        BookCharacterTtsBinding.BindingMode.MANUAL -> 3
        BookCharacterTtsBinding.BindingMode.INHERIT -> 2
        BookCharacterTtsBinding.BindingMode.AUTO -> 1
        else -> 0
    }

    private fun relinkStoryboard(
        storyboard: ChapterStoryboard,
        castRoles: List<BookTtsCastRole>
    ): ChapterStoryboard {
        val activeRoles = castRoles.filter { it.linkedCharacterId == null && it.isRoutableRole() }
        val roleIds = activeRoles.mapTo(mutableSetOf()) { it.id }
        val names = roleNameIndex(activeRoles)
        return storyboard.copy(
            scenes = storyboard.scenes.map { scene ->
                scene.copy(
                    segments = scene.segments.map { segment ->
                        if (segment.type != StoryboardSegmentType.DIALOGUE &&
                            segment.type != StoryboardSegmentType.THOUGHT
                        ) return@map segment
                        if (segment.identityType == StoryboardSegment.IdentityType.GUEST) {
                            return@map segment.copy(castRoleId = null)
                        }
                        val roleId = segment.castRoleId?.takeIf { it in roleIds }
                            ?: segment.speakerName
                                ?.let(::normalizeIdentityName)
                                ?.let { names[it]?.id }
                        segment.copy(castRoleId = roleId)
                    }
                )
            }
        )
    }

    private suspend fun autoBindCurrentEngine(
        book: Book,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>
    ): Int {
        val engine = currentMultiRoleEngine() ?: return 0
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val segments = storyboard.scenes.flatMap { it.segments }
            .filter { it.type == StoryboardSegmentType.DIALOGUE || it.type == StoryboardSegmentType.THOUGHT }
        val targets = buildList {
            characters.filter { it.enabled }.forEach { character ->
                val characterSegments = segments.filter { segment ->
                    segment.speakerId == character.id || segment.speakerName == character.name
                }
                val representativeTexts = characterSegments.map { it.text.trim().take(120) }
                    .filter { it.isNotBlank() }.distinct().take(3)
                if (representativeTexts.isNotEmpty()) {
                    add(
                        CastingTarget(
                            targetType = BookCharacterTtsBinding.TargetType.CHARACTER,
                            targetId = character.id,
                            name = character.name,
                            gender = character.gender,
                            summary = character.castingSummary(),
                            occurrenceCount = characterSegments.size,
                            representativeTexts = representativeTexts,
                            samples = characterSegments.toCastingSamples()
                        )
                    )
                }
            }
            castRoles.filter { it.linkedCharacterId == null && it.isRoutableRole() }.forEach { role ->
                val roleNames = buildList {
                    add(role.name)
                    GSON.fromJsonObject<List<String>>(role.aliasesJson).getOrNull().orEmpty().forEach(::add)
                }.map(::normalizeIdentityName).toSet()
                val roleSegments = segments.filter { segment ->
                    segment.castRoleId == role.id ||
                        segment.speakerName?.let { normalizeIdentityName(it) in roleNames } == true
                }
                val representativeTexts = (roleSamples(role) + roleSegments.map { it.text })
                    .map { it.trim().take(120) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(4)
                if (representativeTexts.isNotEmpty()) {
                    add(
                        CastingTarget(
                            targetType = BookCharacterTtsBinding.TargetType.CAST_ROLE,
                            targetId = role.id,
                            name = role.name,
                            gender = role.gender,
                            summary = role.castSummary(),
                            occurrenceCount = role.occurrenceCount,
                            representativeTexts = representativeTexts,
                            samples = roleSegments.toCastingSamples()
                        )
                    )
                }
            }
        }
        return assignTargets(engine, workKey, targets, replaceAuto = false)
    }

    private suspend fun applySceneVoiceAssignments(
        book: Book,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>
    ): ChapterStoryboard {
        val engine = currentMultiRoleEngine()
            ?.takeIf { it.supportsCapability(TtsEngineCapability.CASTING_METADATA) }
            ?: return storyboard
        val voices = engine.enabledVoices().filter { it.id.isNotBlank() }
        if (voices.isEmpty()) return storyboard
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val enabledVoiceIds = voices.mapTo(mutableSetOf()) { it.id }
        val engineBindings = appDb.bookCharacterDao.getTtsBindings(workKey)
            .filter { it.engineId == engine.id }
        val protectedBindings = engineBindings
            .filter { it.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO }
            .mapTo(mutableSetOf()) { it.targetType to it.targetId }
        val baseVoiceIds = engineBindings.mapNotNull { binding ->
            binding.voiceId
                ?.takeIf { it in enabledVoiceIds }
                ?.let { (binding.targetType to binding.targetId) to it }
        }.toMap()
        val targets = sceneCastingTargets(
            storyboard = storyboard,
            characters = characters,
            castRoles = castRoles,
            protectedBindings = protectedBindings,
            baseVoiceIds = baseVoiceIds
        )
        if (targets.isEmpty()) {
            return storyboard.withSceneVoiceAssignments(engine.id, emptyList())
        }
        val catalogSignature = sceneVoiceAssignmentSignature(voices, baseVoiceIds)
        val existing = storyboard.scenes.flatMap { scene ->
            scene.voiceAssignments.filter { it.engineId == engine.id }.map { assignment ->
                SceneTargetKey(scene.index, assignment.targetType, assignment.targetId) to assignment
            }
        }.toMap()
        val reusable = targets.mapNotNull { target ->
            existing[target.key]
                ?.takeIf { it.engineId == engine.id && it.catalogSignature == catalogSignature }
                ?.takeIf {
                    it.decision == CastingAssignment.Decision.UNASSIGNED ||
                        it.voiceId?.let(enabledVoiceIds::contains) == true
                }
                ?.let { target.key to it }
        }.toMap()
        if (reusable.size == targets.size) {
            return storyboard.withSceneVoiceAssignments(
                engine.id,
                reusable.map { (key, assignment) -> SceneAssignmentResult(key.sceneIndex, assignment) }
            )
        }

        val candidateMap = targets.associateWith { target ->
            val languageCandidates = preferLanguageCandidates(voices, target.representativeTexts)
            val genderCandidates = if (target.gender == BookCharacter.Gender.UNKNOWN) {
                languageCandidates
            } else {
                languageCandidates.filter { voice -> voiceMatchesGender(voice.gender, target.gender) }
            }
            genderCandidates.filter { it.id != target.baseVoiceId }
        }
        val requestIndex = linkedMapOf<Long, SceneCastingTarget>()
        val requestTargets = targets.mapIndexedNotNull { index, target ->
            val candidates = candidateMap[target].orEmpty()
            if (candidates.isEmpty()) return@mapIndexedNotNull null
            val requestId = index.toLong() + 1L
            requestIndex[requestId] = target
            CastingTarget(
                targetType = SCENE_VOICE_TARGET_TYPE,
                targetId = requestId,
                name = target.name,
                gender = target.gender,
                summary = target.summary,
                occurrenceCount = target.segments.size,
                representativeTexts = target.representativeTexts,
                candidateVoiceIds = candidates.map { it.id },
                sceneIndex = target.key.sceneIndex,
                baseTargetType = target.key.targetType,
                baseTargetId = target.key.targetId,
                baseVoiceId = target.baseVoiceId,
                samples = target.segments.map { segment ->
                    CastingSample(
                        textPreview = segment.text,
                        performanceContext = segment.performanceContext
                    )
                }
            )
        }
        val requestedVoiceIds = requestTargets.flatMap { target ->
            target.candidateVoiceIds + listOfNotNull(target.baseVoiceId)
        }.toSet()
        val aiAssignments = if (requestTargets.isEmpty()) {
            emptyList()
        } else {
            requestAssignments(
                engine = engine,
                voices = voices.filter { it.id in requestedVoiceIds },
                targets = requestTargets
            )
        }.associateBy { it.targetId }
        val requestIdByTarget = requestIndex.entries.associate { it.value to it.key }
        val assignments = targets.map { target ->
            val candidates = candidateMap[target].orEmpty()
            val modelAssignment = requestIdByTarget[target]?.let(aiAssignments::get)
            val acceptedVoiceId = modelAssignment?.let { assignment ->
                acceptedSceneOverrideVoiceId(
                    voiceId = assignment.voiceId,
                    decision = assignment.decision,
                    confidence = assignment.confidence,
                    reason = assignment.reason,
                    baseVoiceId = target.baseVoiceId,
                    allowedVoiceIds = candidates.mapTo(mutableSetOf()) { it.id }
                )
            }
            SceneAssignmentResult(
                sceneIndex = target.key.sceneIndex,
                assignment = StoryboardSceneVoiceAssignment(
                    engineId = engine.id,
                    catalogSignature = catalogSignature,
                    targetType = target.key.targetType,
                    targetId = target.key.targetId,
                    voiceId = acceptedVoiceId,
                    decision = if (acceptedVoiceId != null) {
                        CastingAssignment.Decision.ASSIGNED
                    } else {
                        CastingAssignment.Decision.UNASSIGNED
                    },
                    confidence = modelAssignment?.confidence ?: 0f,
                    reason = modelAssignment?.reason
                )
            )
        }
        return storyboard.withSceneVoiceAssignments(engine.id, assignments)
    }

    private fun sceneCastingTargets(
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>,
        protectedBindings: Set<Pair<String, Long>>,
        baseVoiceIds: Map<Pair<String, Long>, String>
    ): List<SceneCastingTarget> {
        val characterById = characters.filter { it.enabled }.associateBy { it.id }
        val characterByName = buildMap {
            characterById.values.forEach { character ->
                buildList {
                    add(character.name)
                    character.aliasesJson
                        ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
                        .orEmpty()
                        .forEach(::add)
                }.filter { it.isNotBlank() }.forEach { name ->
                    putIfAbsent(normalizeIdentityName(name), character)
                }
            }
        }
        val activeCastRoles = castRoles.filter {
            it.linkedCharacterId == null && it.isRoutableRole()
        }
        val castRoleById = activeCastRoles.associateBy { it.id }
        val castRoleByName = buildMap {
            activeCastRoles.forEach { role ->
                (listOf(role.name) + roleAliases(role))
                    .filter { it.isNotBlank() }
                    .forEach { name -> putIfAbsent(normalizeIdentityName(name), role) }
            }
        }
        return storyboard.scenes.flatMap { scene ->
            val grouped = linkedMapOf<Pair<String, Long>, MutableList<StoryboardSegment>>()
            scene.segments.filter { segment ->
                segment.type == StoryboardSegmentType.DIALOGUE ||
                    segment.type == StoryboardSegmentType.THOUGHT
            }.forEach { segment ->
                val normalizedName = segment.speakerName?.let(::normalizeIdentityName)
                val character = segment.speakerId?.let(characterById::get)
                    ?: normalizedName?.let(characterByName::get)
                if (character != null) {
                    val key = BookCharacterTtsBinding.TargetType.CHARACTER to character.id
                    if (key !in protectedBindings) grouped.getOrPut(key, ::arrayListOf).add(segment)
                    return@forEach
                }
                val role = segment.castRoleId?.let(castRoleById::get)
                    ?: normalizedName?.let(castRoleByName::get)
                if (role != null) {
                    val key = BookCharacterTtsBinding.TargetType.CAST_ROLE to role.id
                    if (key !in protectedBindings) grouped.getOrPut(key, ::arrayListOf).add(segment)
                }
            }
            grouped.mapNotNull { (baseTarget, segments) ->
                val baseVoiceId = baseVoiceIds[baseTarget] ?: return@mapNotNull null
                val character = if (baseTarget.first == BookCharacterTtsBinding.TargetType.CHARACTER) {
                    characterById[baseTarget.second]
                } else null
                val role = if (baseTarget.first == BookCharacterTtsBinding.TargetType.CAST_ROLE) {
                    castRoleById[baseTarget.second]
                } else null
                val name = character?.name ?: role?.name ?: return@mapNotNull null
                val representativeTexts = segments.map { it.text.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(3)
                if (representativeTexts.isEmpty()) return@mapNotNull null
                SceneCastingTarget(
                    key = SceneTargetKey(scene.index, baseTarget.first, baseTarget.second),
                    name = name,
                    gender = character?.gender ?: role?.gender ?: BookCharacter.Gender.UNKNOWN,
                    summary = character?.castingSummary() ?: role?.castSummary(),
                    baseVoiceId = baseVoiceId,
                    representativeTexts = representativeTexts,
                    segments = segments.take(3)
                )
            }
        }
    }

    private fun ChapterStoryboard.withSceneVoiceAssignments(
        engineId: String,
        assignments: List<SceneAssignmentResult>
    ): ChapterStoryboard {
        val byScene = assignments.groupBy { it.sceneIndex }
        return copy(
            scenes = scenes.map { scene ->
                scene.copy(
                    voiceAssignments = scene.voiceAssignments.filter { it.engineId != engineId } +
                        byScene[scene.index].orEmpty().map { it.assignment }
                )
            }
        )
    }

    private fun sceneVoiceAssignmentSignature(
        voices: List<TtsVoice>,
        baseVoiceIds: Map<Pair<String, Long>, String>
    ): String = MD5Utils.md5Encode(
        listOf(
            SCENE_VOICE_POLICY_VERSION,
            voices.sortedBy { it.id }.joinToString("\n") { voice ->
                listOf(
                    voice.id,
                    voice.name,
                    voice.language.orEmpty(),
                    voice.gender.orEmpty(),
                    voice.style.orEmpty(),
                    voice.tags.joinToString(","),
                    voice.extra?.toString().orEmpty()
                ).joinToString("|")
            },
            baseVoiceIds.toSortedMap(
                compareBy<Pair<String, Long>> { it.first }.thenBy { it.second }
            ).entries.joinToString("\n") { (target, voiceId) ->
                "${target.first}|${target.second}|$voiceId"
            }
        ).joinToString("\n")
    )

    private fun currentMultiRoleEngine(): TtsEngineSetting? {
        return TtsEngineStore.engine(io.legado.app.help.config.AppConfig.multiRoleTtsEngineId)
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
    }

    private fun currentCastingSnapshot(workKey: String): CastingSnapshot? {
        val engine = currentMultiRoleEngine() ?: return null
        val dao = appDb.bookCharacterDao
        val characters = dao.getCharacters(workKey).filter { it.enabled && it.name.isNotBlank() }
        val castRoles = dao.getTtsCastRoles(workKey)
            .filter { it.linkedCharacterId == null && it.isRoutableRole() }
        val targets = buildList {
            characters.forEach { character -> add(character.toCastingTarget()) }
            castRoles.forEach { role -> role.toCastingTarget()?.let(::add) }
        }
        return CastingSnapshot(engine, targets)
    }

    private suspend fun assignTargets(
        engine: TtsEngineSetting,
        workKey: String,
        targets: List<CastingTarget>,
        replaceAuto: Boolean
    ): Int = assignmentMutex(workKey, engine.id).withLock {
        val effectiveEngine = if (
            engine.enabledVoices().isEmpty() && engine.supportsVoiceFetch()
        ) {
            TtsEngineStore.ensureVoiceCatalog(
                engineId = engine.id,
                restartReadAloud = false
            )
        } else {
            engine
        }
        val voices = effectiveEngine.enabledVoices().filter { it.id.isNotBlank() }
        if (voices.isEmpty() || targets.isEmpty()) return@withLock 0
        val dao = appDb.bookCharacterDao
        val existingBindings = dao.getTtsBindings(workKey)
            .filter { it.engineId == effectiveEngine.id }
            .associateBy { it.targetType to it.targetId }
        val candidateMap = targets.associateWith { target ->
            val languageCandidates = preferLanguageCandidates(voices, target.representativeTexts)
            if (target.gender == BookCharacter.Gender.UNKNOWN) {
                languageCandidates
            } else {
                languageCandidates.filter { voice -> voiceMatchesGender(voice.gender, target.gender) }
            }
        }
        val evidenceSignatures = targets.associateWith { target ->
            autoCastingEvidenceSignature(target, candidateMap[target].orEmpty())
        }
        val eligibleTargets = targets.filter { target ->
            val existing = existingBindings[target.targetType to target.targetId]
            BookTtsBindingPolicy.shouldEvaluate(
                binding = existing,
                usableVoiceIds = candidateMap[target].orEmpty().mapTo(mutableSetOf()) { it.id },
                evidenceSignature = evidenceSignatures.getValue(target),
                replaceAuto = replaceAuto
            )
        }
        if (eligibleTargets.isEmpty()) return@withLock 0

        val aiTargets = candidateMap.filterValues { it.isNotEmpty() }.keys.toList()
            .filter { it in eligibleTargets }
        val aiCandidateIds = aiTargets.flatMap { target -> candidateMap[target].orEmpty().map { it.id } }.toSet()
        val aiAssignments = if (aiTargets.isEmpty()) {
            emptyList()
        } else {
            requestAssignments(
                engine = effectiveEngine,
                voices = voices.filter { it.id in aiCandidateIds },
                targets = aiTargets.map { target ->
                    target.copy(candidateVoiceIds = candidateMap[target].orEmpty().map { it.id })
                }
            )
        }
        val assignmentIndex = aiAssignments
            .distinctBy { it.targetType to it.targetId }
            .associateBy { it.targetType to it.targetId }
        var savedCount = 0
        appDb.runInTransaction {
            val currentBindings = dao.getTtsBindings(workKey)
                .filter { it.engineId == effectiveEngine.id }
                .associateBy { it.targetType to it.targetId }
            eligibleTargets.forEach { target ->
                val key = target.targetType to target.targetId
                val current = currentBindings[key]
                if (current != null && current.bindingMode != BookCharacterTtsBinding.BindingMode.AUTO) {
                    return@forEach
                }
                val allowed = candidateMap[target].orEmpty().mapTo(mutableSetOf()) { it.id }
                val evidenceSignature = evidenceSignatures.getValue(target)
                if (!BookTtsBindingPolicy.shouldEvaluate(
                        binding = current,
                        usableVoiceIds = allowed,
                        evidenceSignature = evidenceSignature,
                        replaceAuto = replaceAuto
                    )
                ) {
                    return@forEach
                }
                val assignment = assignmentIndex[key]
                val acceptedVoiceId = acceptedAutoAssignmentVoiceId(
                    voiceId = assignment?.voiceId,
                    decision = assignment?.decision,
                    confidence = assignment?.confidence ?: 0f,
                    allowedVoiceIds = allowed
                )
                val now = System.currentTimeMillis()
                val resolution = BookTtsBindingPolicy.resolve(
                    current = current,
                    newBinding = {
                        BookCharacterTtsBinding(
                            workKey = workKey,
                            targetType = target.targetType,
                            targetId = target.targetId,
                            engineId = effectiveEngine.id,
                            createdAt = now
                        )
                    },
                    usableVoiceIds = allowed,
                    evidenceSignature = evidenceSignature,
                    acceptedVoiceId = acceptedVoiceId,
                    confidence = assignment?.confidence ?: 0f,
                    replaceAuto = replaceAuto,
                    now = now
                )
                dao.upsertTtsBinding(resolution.binding)
                if (acceptedVoiceId != null && resolution.binding.voiceId == acceptedVoiceId) {
                    savedCount++
                }
            }
        }
        savedCount
    }

    internal fun acceptedAutoAssignmentVoiceId(
        voiceId: String?,
        decision: String?,
        confidence: Float,
        allowedVoiceIds: Set<String>
    ): String? = voiceId
        ?.takeIf { decision == CastingAssignment.Decision.ASSIGNED }
        ?.takeIf { confidence >= BookTtsBindingPolicy.MIN_AUTO_CONFIDENCE }
        ?.takeIf { it in allowedVoiceIds }

    private fun autoCastingEvidenceSignature(
        target: CastingTarget,
        candidates: List<TtsVoice>
    ): String = MD5Utils.md5Encode(
        listOf(
            AUTO_CAST_POLICY_VERSION,
            GSON.toJson(target.copy(candidateVoiceIds = candidates.map { it.id })),
            candidates.sortedBy { it.id }.joinToString("\n") { voice ->
                listOf(
                    voice.id,
                    voice.name,
                    voice.language.orEmpty(),
                    voice.gender.orEmpty(),
                    voice.style.orEmpty(),
                    voice.tags.joinToString(","),
                    voice.extra?.toString().orEmpty()
                ).joinToString("|")
            }
        ).joinToString("\n")
    )

    internal fun acceptedSceneOverrideVoiceId(
        voiceId: String?,
        decision: String?,
        confidence: Float,
        reason: String?,
        baseVoiceId: String,
        allowedVoiceIds: Set<String>
    ): String? = voiceId
        ?.takeIf { decision == CastingAssignment.Decision.ASSIGNED }
        ?.takeIf { confidence >= MIN_SCENE_OVERRIDE_CONFIDENCE }
        ?.takeIf { !reason.isNullOrBlank() }
        ?.takeIf { it != baseVoiceId }
        ?.takeIf { it in allowedVoiceIds }

    private suspend fun requestAssignments(
        engine: TtsEngineSetting,
        voices: List<TtsVoice>,
        targets: List<CastingTarget>
    ): List<CastingAssignment> {
        val selection = AiConfig.requireReadAloudStoryboardModel()
        val payload = CastingPayload(
            engineId = engine.id,
            voices = voices.map {
                CastingVoice(it.id, it.name, it.language, it.gender, it.style, it.tags, it.extra)
            },
            targets = targets
        )
        val result = AiManager.generateText(
            providerId = selection.providerId,
            modelId = selection.modelId,
            messages = listOf(
                AiMessage(AiMessage.Role.SYSTEM, appCtx.assets.open(PROMPT_ASSET).bufferedReader().use { it.readText() }),
                AiMessage(AiMessage.Role.USER, GSON.toJson(payload))
            ),
            params = AiConfig.readAloudStoryboardParams(targets.size, supportsReasoning = false)
        )
        check(result.content.isNotBlank()) { "AI 自动选音返回为空" }
        val normalized = result.content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = normalized.indexOf('{')
        val end = normalized.lastIndexOf('}')
        check(start >= 0 && end >= start) { "AI 自动选音未返回 JSON 对象" }
        val raw = normalized.substring(start, end + 1)
        val root = JsonParser.parseString(raw).asJsonObject
        return GSON.fromJson(root.get("assignments"), Array<CastingAssignment>::class.java)?.toList().orEmpty()
    }

    internal fun voiceMatchesGender(voiceGender: String?, targetGender: String): Boolean {
        if (targetGender == BookCharacter.Gender.UNKNOWN || voiceGender.isNullOrBlank()) return true
        val value = voiceGender.trim().lowercase()
        val normalized = when {
            value.contains("female") || value.contains('女') -> BookCharacter.Gender.FEMALE
            value.contains("male") || value.contains('男') -> BookCharacter.Gender.MALE
            else -> return true
        }
        return normalized == targetGender
    }

    internal fun preferLanguageCandidates(voices: List<TtsVoice>, representativeTexts: List<String>): List<TtsVoice> {
        if (representativeTexts.none { text -> text.any { it.isCjkUnifiedIdeograph() } }) return voices
        return voices.filter { voice ->
            voice.language.isNullOrBlank() || voice.language.isChineseLanguageTag()
        }.ifEmpty { voices }
    }

    private fun Char.isCjkUnifiedIdeograph(): Boolean {
        return code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF
    }

    private fun String.isChineseLanguageTag(): Boolean {
        val value = trim().lowercase()
        return value.startsWith("zh") || value.startsWith("cmn") || value.contains("chinese") ||
            value.contains("mandarin") || value.contains("中文") || value.contains("普通话")
    }

    private fun BookCharacter.castingSummary(): String? {
        return buildList {
            roleTag.takeIf { it != BookCharacter.RoleTag.UNKNOWN }?.let(::add)
            displayIntro()?.trim()?.take(160)?.let(::add)
        }.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    private fun BookCharacter.toCastingTarget(): CastingTarget = CastingTarget(
        targetType = BookCharacterTtsBinding.TargetType.CHARACTER,
        targetId = id,
        name = name,
        gender = gender,
        summary = castingSummary(),
        occurrenceCount = 0,
        representativeTexts = listOfNotNull(
            displayIntro()?.trim()?.takeIf { it.isNotBlank() }
                ?: name.takeIf { it.isNotBlank() }
        )
    )

    private fun BookTtsCastRole.toCastingTarget(): CastingTarget? {
        val samples = roleSamples(this)
            .map { it.trim().take(120) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        if (samples.isEmpty()) return null
        return CastingTarget(
            targetType = BookCharacterTtsBinding.TargetType.CAST_ROLE,
            targetId = id,
            name = name,
            gender = gender,
            summary = castSummary(),
            occurrenceCount = occurrenceCount,
            representativeTexts = samples
        )
    }

    private fun BookTtsCastRole.castSummary(): String {
        val chapterSummary = if (firstChapterIndex == lastChapterIndex) {
            "第 ${firstChapterIndex + 1} 章出现"
        } else {
            "第 ${firstChapterIndex + 1}-${lastChapterIndex + 1} 章出现"
        }
        return "$chapterSummary · 共 $occurrenceCount 次"
    }

    private fun List<StoryboardSegment>.toCastingSamples(): List<CastingSample> =
        asSequence()
            .map { segment ->
                CastingSample(
                    textPreview = segment.text.trim().take(120),
                    performanceContext = segment.performanceContext
                )
            }
            .filter { it.textPreview.isNotBlank() }
            .distinctBy { it.textPreview to it.performanceContext }
            .take(3)
            .toList()

    private data class CastingPayload(
        @SerializedName("engineId") val engineId: String,
        @SerializedName("voices") val voices: List<CastingVoice>,
        @SerializedName("targets") val targets: List<CastingTarget>
    )

    private data class PreparedStoryboard(
        val storyboard: ChapterStoryboard,
        val roles: List<BookTtsCastRole>
    )

    private data class SyncedCastRoles(
        val identityLinks: List<StoryboardIdentityLink>,
        val roles: List<BookTtsCastRole>
    )

    private data class IdentityTarget(
        val name: String,
        val characterId: Long?,
        val castRoleId: Long?
    )

    private data class CastingSnapshot(
        val engine: TtsEngineSetting,
        val targets: List<CastingTarget>
    )

    private data class CastingVoice(
        @SerializedName("voiceId") val voiceId: String,
        @SerializedName("name") val name: String,
        @SerializedName("language") val language: String?,
        @SerializedName("gender") val gender: String?,
        @SerializedName("style") val style: String?,
        @SerializedName("tags") val tags: List<String>,
        @SerializedName("extra") val extra: JsonObject? = null
    )

    private data class CastingTarget(
        @SerializedName("targetType") val targetType: String,
        @SerializedName("targetId") val targetId: Long,
        @SerializedName("name") val name: String,
        @SerializedName("gender") val gender: String,
        @SerializedName("summary") val summary: String? = null,
        @SerializedName("occurrenceCount") val occurrenceCount: Int = 0,
        @SerializedName("representativeTexts") val representativeTexts: List<String>,
        @SerializedName("candidateVoiceIds") val candidateVoiceIds: List<String> = emptyList(),
        @SerializedName("sceneIndex") val sceneIndex: Int? = null,
        @SerializedName("baseTargetType") val baseTargetType: String? = null,
        @SerializedName("baseTargetId") val baseTargetId: Long? = null,
        @SerializedName("baseVoiceId") val baseVoiceId: String? = null,
        @SerializedName("samples") val samples: List<CastingSample> = emptyList()
    )

    private data class CastingSample(
        @SerializedName("textPreview") val textPreview: String,
        @SerializedName("performanceContext") val performanceContext: List<String>
    )

    private data class SceneTargetKey(
        val sceneIndex: Int,
        val targetType: String,
        val targetId: Long
    )

    private data class SceneCastingTarget(
        val key: SceneTargetKey,
        val name: String,
        val gender: String,
        val summary: String?,
        val baseVoiceId: String,
        val representativeTexts: List<String>,
        val segments: List<StoryboardSegment>
    )

    private data class SceneAssignmentResult(
        val sceneIndex: Int,
        val assignment: StoryboardSceneVoiceAssignment
    )

    private data class DiscoveredOccurrence(
        val name: String,
        val gender: String,
        val text: String,
        val castRoleId: Long?,
        val identityType: String,
        val nameType: String,
        val identityEvidence: String,
        val genderEvidence: String,
        val mergeCastRoleIds: List<Long>,
        val evidence: String
    )

    private data class CastingAssignment(
        @SerializedName("targetType") val targetType: String,
        @SerializedName("targetId") val targetId: Long,
        @SerializedName("voiceId") val voiceId: String? = null,
        @SerializedName("decision") val decision: String? = null,
        @SerializedName("confidence") val confidence: Float = 0f,
        @SerializedName("reason") val reason: String? = null
    ) {
        object Decision {
            const val ASSIGNED = "assigned"
            const val UNASSIGNED = "unassigned"
        }
    }
}
