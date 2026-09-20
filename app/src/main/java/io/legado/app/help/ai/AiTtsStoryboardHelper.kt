package io.legado.app.help.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineCapability
import io.legado.app.help.tts.TtsCapabilityRegistry
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.BookTtsAutomationConfig
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.readText
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSceneVoiceAssignment
import io.legado.app.ui.book.character.StoryboardIdentityLink
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

object AiTtsStoryboardHelper {

    private const val PROTOCOL_SKILL_ASSET = "skills/tts_storyboard/modules/protocol.md"
    private const val BASE_ROUTING_SKILL_ASSET = "skills/tts_storyboard/modules/base-routing.md"
    private const val SCENE_CONTEXT_SKILL_ASSET = "skills/tts_storyboard/modules/scene-context.md"
    private const val PERFORMANCE_INSTRUCTION_SKILL_ASSET =
        "skills/tts_storyboard/modules/performance-instruction.md"
    private const val STYLE_TAGS_SKILL_ASSET = "skills/tts_storyboard/modules/style-tags.md"
    private const val EMOTION_SKILL_ASSET = "skills/tts_storyboard/modules/emotion.md"
    private const val PERFORMANCE_SCENES_SKILL_ASSET = "skills/tts_storyboard/modes/performance-scenes.md"
    private const val COMBINED_FALLBACK_SKILL_ASSET = "skills/tts_storyboard/modes/fallback-combined.md"
    private const val CACHE_DIR = "ai_tts_storyboard"
    private const val CACHE_VERSION = 17
    private const val IDENTITY_CACHE_VERSION = 2
    private const val EXPRESSIVE_CACHE_VERSION = 2
    private const val MEMORY_CACHE_TTL = 5 * 60 * 1000L
    private object StoryboardMode {
        const val BASIC = "basic"
        const val PERFORMANCE = "performance"
    }
    private val quotePairs = mapOf(
        '“' to '”',
        '‘' to '’',
        '「' to '」',
        '『' to '』',
        '"' to '"'
    )
    private val quoteCloseCandidates = mapOf(
        '“' to listOf('”', '“'),
        '‘' to listOf('’', '‘'),
        '「' to listOf('」'),
        '『' to listOf('』'),
        '"' to listOf('"', '”')
    )
    private val sentencePunctuation = "。！？!?；;"
    private val thoughtCues = listOf("心想", "心道", "暗道", "想道", "心里想", "心中想", "心里暗道", "心中暗道")
    private val femaleAddresses = listOf(
        "小妹妹", "妹妹", "小姑娘", "姑娘", "小姐", "女士", "女侠", "夫人", "娘子"
    )
    private val maleAddresses = listOf(
        "小弟弟", "弟弟", "小公子", "公子", "少爷", "先生", "小哥", "大哥", "大叔", "老爷"
    )
    private val colonDialogueCues = listOf(
        "说", "说道", "问", "问道", "喊", "喊道", "叫", "叫道", "道", "开口",
        "吐槽", "坦言", "回答", "答道", "回道", "回复", "说了句", "喊上一句", "补了一句"
    )
    private val narratedQuoteStrongCues = listOf(
        "那句", "这句", "那句话", "这句话", "原话", "所谓", "口头禅",
        "字眼", "词语", "称呼", "标题", "名字", "写着", "显示"
    )
    private val narratedQuoteShortCues = listOf(
        "一句", "一声", "一串", "几个字", "两个字", "三个字", "四个字", "五个字"
    )
    private val textLeakKeys = setOf(
        "text", "input", "content", "sourceText", "source_text", "output", "ranges", "start", "end"
    )
    private val baseUnitKeys = setOf(
        "unitId", "roleType", "characterName", "characterId", "castRoleId", "speakerGender",
        "identityType", "nameType", "identityEvidence", "genderEvidence", "mergeCastRoleIds",
        "status", "confidence", "evidence", "performanceContext", "performanceInstruction",
        "styleConcepts", "emotion", "emotionIntensity", "expressiveConfidence"
    )
    private val sceneKeys = setOf("sceneId", "title", "startParagraphIndex", "endParagraphIndex")
    private val rootKeys = setOf("units", "newCharacters")
    private val roleTypes = setOf("narrator", "character", "thought", "other")
    private val statuses = setOf("assigned", "unknown")
    private val speakerGenders = setOf(
        StoryboardSegment.SpeakerGender.MALE,
        StoryboardSegment.SpeakerGender.FEMALE,
        StoryboardSegment.SpeakerGender.UNKNOWN
    )
    private val identityTypes = setOf(
        StoryboardSegment.IdentityType.NONE,
        StoryboardSegment.IdentityType.FORMAL_CHARACTER,
        StoryboardSegment.IdentityType.CAST_ROLE,
        StoryboardSegment.IdentityType.STABLE_CANDIDATE,
        StoryboardSegment.IdentityType.PENDING,
        StoryboardSegment.IdentityType.GUEST
    )
    private val nameTypes = setOf(
        StoryboardSegment.NameType.PROPER_NAME,
        StoryboardSegment.NameType.ALIAS,
        StoryboardSegment.NameType.UNIQUE_TITLE,
        StoryboardSegment.NameType.GENERIC_LABEL,
        StoryboardSegment.NameType.UNKNOWN
    )
    private val evidenceLevels = setOf(
        StoryboardSegment.Evidence.EXPLICIT,
        StoryboardSegment.Evidence.CONTEXTUAL,
        StoryboardSegment.Evidence.INFERRED,
        StoryboardSegment.Evidence.UNKNOWN
    )
    private val cacheMutex = Mutex()
    private val memoryCache = linkedMapOf<String, MemoryCacheEntry>()
    private val inFlightRequests = hashMapOf<String, CompletableDeferred<GenerateResult>>()
    private val cachedEnrichmentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cachedEnrichmentMutex = Mutex()
    private val cachedEnrichmentJobs = ConcurrentHashMap<String, Job>()

    suspend fun getOrGenerate(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): ChapterStoryboard = getOrGenerate(
        book = book,
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        content = content,
        characters = characters,
        forceRegenerate = false
    )

    suspend fun regenerate(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): ChapterStoryboard = getOrGenerate(
        book = book,
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        content = content,
        characters = characters,
        forceRegenerate = true
    )

    private suspend fun getOrGenerate(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>,
        forceRegenerate: Boolean
    ): ChapterStoryboard {
        val request = buildRequest(book, chapterIndex, chapterTitle, content, characters)
        val cacheFile = cacheFile(request)
        var owner = false
        var retryForceRegenerate = false
        var cachedCache: StoryboardCache? = null
        val pending = cacheMutex.withLock {
            val running = inFlightRequests[request.cacheKey]
            if (forceRegenerate && running != null) {
                retryForceRegenerate = true
                return@withLock running
            }
            if (forceRegenerate) {
                memoryCache.entries.removeAll { (key, entry) ->
                    key == request.cacheKey || entry.cache.identityCacheKey == request.identityCacheKey
                }
                deleteDerivedCombinedCaches(request)
                deleteLayeredCaches(request.identityCacheKey)
            } else {
                loadMemoryCache(request)?.let {
                    cachedCache = it
                }
                if (cachedCache == null) {
                    loadCache(cacheFile, request)?.let {
                        cachedCache = persistRequestIdentity(it, request, cacheFile)
                    }
                    if (cachedCache == null) {
                        loadCompatibleCache(request)?.let {
                            cachedCache = persistRequestIdentity(it, request, cacheFile)
                        }
                    }
                    if (cachedCache == null) {
                        loadLayeredCache(request)?.let { cache ->
                            cachedCache = persistRequestIdentity(cache, request, cacheFile)
                        }
                    }
                }
            }
            if (cachedCache == null) {
                running ?: CompletableDeferred<GenerateResult>().also {
                    inFlightRequests[request.cacheKey] = it
                    owner = true
                }
            } else null
        }
        if (retryForceRegenerate) {
            runCatching { pending?.await() }
                .exceptionOrNull()
                ?.takeIf { it is CancellationException }
                ?.let { throw it }
            return getOrGenerate(
                book = book,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                content = content,
                characters = characters,
                forceRegenerate = true
            )
        }
        cachedCache?.let { cache ->
            val storyboard = BookTtsCastingCoordinator.prepareCached(
                book,
                cache.toChapterStoryboard(),
                characters
            )
            val preparedCache = persistPreparedStoryboard(request, cacheFile, cache, storyboard)
            scheduleCachedEnrichment(
                request = request,
                cacheFile = cacheFile,
                cache = preparedCache,
                book = book,
                storyboard = storyboard,
                characters = characters
            )
            return storyboard
        }
        checkNotNull(pending)
        if (!owner) {
            val result = pending.await()
            val storyboard = BookTtsCastingCoordinator.prepareCached(
                book,
                result.cache.toChapterStoryboard(),
                characters
            )
            val preparedCache = persistPreparedStoryboard(request, cacheFile, result.cache, storyboard)
            scheduleCachedEnrichment(
                request = request,
                cacheFile = cacheFile,
                cache = preparedCache,
                book = book,
                storyboard = storyboard,
                characters = characters
            )
            return storyboard
        }
        try {
            val identity = loadIdentityCache(request)
            val generated = generate(request, identity?.storyboard?.scenes.orEmpty())
            val result = generated.copy(
                cache = identity?.let { mergeIdentityLayer(it, generated.cache) }
                    ?: generated.cache
            )
            cacheMutex.withLock {
                if (result.cacheable) {
                    memoryCache[request.cacheKey] = MemoryCacheEntry(
                        cache = result.cache,
                        expiresAt = System.currentTimeMillis() + MEMORY_CACHE_TTL
                    )
                    trimMemoryCache()
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(GSON.toJson(result.cache), Charsets.UTF_8)
                    persistLayeredCaches(request, result.cache)
                }
            }
            val storyboard = BookTtsCastingCoordinator.prepareGenerated(
                book,
                chapterIndex,
                result.cache.toChapterStoryboard(),
                characters
            )
            val enrichedCache = result.cache.withPreparedStoryboard(storyboard)
            val preparedResult = if (enrichedCache != result.cache) {
                result.copy(cache = enrichedCache).also { enriched ->
                    cacheMutex.withLock {
                        if (enriched.cacheable) {
                            memoryCache[request.cacheKey] = MemoryCacheEntry(
                                cache = enriched.cache,
                                expiresAt = System.currentTimeMillis() + MEMORY_CACHE_TTL
                            )
                            cacheFile.writeText(GSON.toJson(enriched.cache), Charsets.UTF_8)
                            persistLayeredCaches(request, enriched.cache)
                        }
                    }
                }
            } else {
                result
            }
            pending.complete(preparedResult)
            return storyboard
        } catch (e: Throwable) {
            pending.completeExceptionally(e)
            throw e
        } finally {
            cacheMutex.withLock {
                if (inFlightRequests[request.cacheKey] === pending) {
                    inFlightRequests.remove(request.cacheKey)
                }
            }
        }
    }

    suspend fun loadCachedOrMemory(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): ChapterStoryboard? {
        val request = buildRequest(book, chapterIndex, chapterTitle, content, characters)
        cacheMutex.withLock {
            loadMemoryCache(request)?.let {
                return it.toChapterStoryboard()
            }
            loadCache(cacheFile(request), request)?.let {
                return it.toChapterStoryboard()
            }
            loadLayeredCache(request)?.let {
                return it.toChapterStoryboard()
            }
        }
        return null
    }

    fun loadCached(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): ChapterStoryboard? {
        val request = buildRequest(book, chapterIndex, chapterTitle, content, characters)
        return (loadCache(cacheFile(request), request) ?: loadLayeredCache(request))
            ?.toChapterStoryboard()
    }

    fun listCachedStoryboards(
        book: Book,
        chapters: List<BookChapter>,
        characters: List<BookCharacter>
    ): List<CachedStoryboardEntry> {
        val enabledCharacters = characters.filter { it.enabled && it.name.isNotBlank() }
        val currentCharactersHash = charactersHash(enabledCharacters)
        val emptyCharactersHash = charactersHash(emptyList())
        val chaptersByTitle = chapters.groupBy { it.title }
        val entries = cacheDirectory().listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                val cache = readCache(file) ?: return@mapNotNull null
                if (cache.cacheVersion != CACHE_VERSION) return@mapNotNull null
                val chapterIndex = when {
                    cache.bookUrl == book.bookUrl && cache.chapterIndex >= 0 -> cache.chapterIndex
                    cache.bookUrl.isNotBlank() -> return@mapNotNull null
                    else -> chaptersByTitle[cache.chapterTitle]
                        .orEmpty()
                        .firstOrNull { chapter ->
                            cache.key in cacheIdentityCandidates(
                                book = book,
                                chapterIndex = chapter.index,
                                chapterTitle = cache.chapterTitle,
                                contentHash = cache.contentHash,
                                mode = cache.mode,
                                capabilities = cache.storyboardCapabilities,
                                providerId = cache.providerId,
                                modelId = cache.modelId,
                                charactersHashes = listOf(currentCharactersHash, emptyCharactersHash)
                            )
                        }
                        ?.index
                        ?: return@mapNotNull null
                }
                CachedStoryboardEntry(
                    chapterIndex = chapterIndex,
                    chapterTitle = cache.chapterTitle,
                    generatedAt = cache.generatedAt,
                    storyboard = cache.toChapterStoryboard(),
                    cacheKeys = setOf(file.nameWithoutExtension, cache.key)
                        .filter { it.isNotBlank() }
                        .toSet()
                )
            }
        return entries
            .groupBy { it.chapterIndex }
            .mapNotNull { (_, versions) ->
                versions.maxByOrNull { it.generatedAt }?.copy(
                    cacheKeys = versions.flatMapTo(linkedSetOf()) { it.cacheKeys }
                )
            }
            .sortedByDescending { it.chapterIndex }
    }

    suspend fun deleteCachedStoryboard(entry: CachedStoryboardEntry): Boolean {
        val cacheKeys = entry.cacheKeys
        if (cacheKeys.isEmpty()) return false
        val pending = cacheMutex.withLock {
            cacheKeys.mapNotNull(inFlightRequests::get).distinct()
        }
        pending.forEach { runCatching { it.await() } }
        val deletion = cacheMutex.withLock {
            cacheKeys.forEach { memoryCache.remove(it) }
            val files = cacheDirectory()
                .listFiles { file ->
                    file.extension == "json" && file.nameWithoutExtension in cacheKeys
                }
                .orEmpty()
            val removedCaches = files.mapNotNull(::readCache)
            val identityKeys = removedCaches
                .map { it.identityCacheKey }
                .filter { it.isNotBlank() }
                .distinct()
            val failed = files.filterNot { !it.exists() || it.delete() }
            check(failed.isEmpty()) { "无法删除 ${failed.size} 个分镜缓存文件" }
            identityKeys.forEach(::deleteLayeredCaches)
            files.isNotEmpty() to removedCaches.map {
                BookCharacterProfile.workKey(it.bookName, it.bookAuthor) to it.chapterIndex
            }.distinct()
        }
        deletion.second.forEach { (workKey, chapterIndex) ->
            BookTtsCastingCoordinator.discardCachedChapter(workKey, chapterIndex)
        }
        return deletion.first
    }

    fun paragraphsFromContent(content: String): List<String> {
        val lines = content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .filter { it.isNotEmpty() }
            .toList()
        return lines.ifEmpty { listOf(content).filter { it.isNotEmpty() } }
    }

    fun readAloudContentFromChapter(
        chapter: TextChapter,
        pageSplit: Boolean = appCtx.getPrefBoolean(PreferKey.readAloudByPage)
    ): String {
        return chapter.readAloudText.readText(pageSplit)
    }

    fun debugSnapshot(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>,
        includeStoryboard: Boolean,
        includePayload: Boolean = false,
        paragraphLimit: Int,
        unitLimit: Int,
        segmentLimit: Int,
        textCharLimit: Int
    ): Map<String, Any?> {
        val request = buildRequest(book, chapterIndex, chapterTitle, content, characters)
        val file = cacheFile(request)
        val now = System.currentTimeMillis()
        val memoryEntry = memoryCache[request.cacheKey]?.takeIf { it.expiresAt > now }
        val diskCache = loadCache(file, request)
        val cache = memoryEntry?.cache ?: diskCache
        val cacheSource = when {
            memoryEntry != null -> "memory"
            diskCache != null -> "disk"
            else -> "none"
        }
        val data = linkedMapOf<String, Any?>(
            "book" to mapOf(
                "name" to book.name,
                "author" to book.author,
                "book_url" to book.bookUrl
            ),
            "chapter" to mapOf(
                "index" to chapterIndex,
                "title" to chapterTitle
            ),
            "request" to mapOf(
                "cache_key" to request.cacheKey,
                "mode" to request.mode,
                "multi_role_engine_id" to request.multiRoleEngineId,
                "storyboard_capabilities" to request.storyboardCapabilities,
                "content_hash" to request.contentHash,
                "content_chars" to content.length,
                "paragraph_count" to request.paragraphs.size,
                "unit_count" to request.units.size,
                "enabled_character_count" to request.characters.size
            ),
            "cache" to mapOf(
                "source" to cacheSource,
                "exists" to (cache != null),
                "path" to file.absolutePath,
                "version" to cache?.cacheVersion,
                "mode" to cache?.mode,
                "generated_at" to cache?.generatedAt,
                "scene_count" to cache?.scenes?.size,
                "assignment_count" to cache?.assignments?.size
            ),
            "characters" to request.characters.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "role_tag" to it.roleTag,
                    "enabled" to it.enabled
                )
            },
            "paragraphs" to request.paragraphs.take(paragraphLimit).map {
                mapOf(
                    "paragraph_index" to it.paragraphIndex,
                    "length" to it.text.length,
                    "leading_space_count" to it.text.takeWhile { ch -> ch.isWhitespace() }.length,
                    "preview" to it.text.limitDebugText(textCharLimit)
                )
            },
            "paragraph_total" to request.paragraphs.size,
            "units" to request.units.take(unitLimit).map { unit ->
                mapOf(
                    "unit_id" to unit.unitId,
                    "kind" to unit.kind,
                    "role_hint" to unit.roleHint,
                    "ranges" to unit.ranges.map { range ->
                        mapOf(
                            "paragraph_index" to range.paragraphIndex,
                            "start" to range.start,
                            "end" to range.end
                        )
                    },
                    "text_preview" to unit.textPreview.limitDebugText(textCharLimit),
                    "cue_before" to unit.cueBefore.limitDebugText(textCharLimit),
                    "cue_after" to unit.cueAfter.limitDebugText(textCharLimit)
                )
            },
            "unit_total" to request.units.size,
            "assignments" to cache?.assignments?.take(unitLimit)?.map {
                mapOf(
                    "unit_id" to it.unitId,
                    "role_type" to it.roleType,
                    "character_name" to it.characterName,
                    "character_id" to it.characterId,
                    "speaker_gender" to it.speakerGender,
                    "status" to it.status,
                    "confidence" to it.confidence,
                    "evidence" to it.evidence.limitDebugText(textCharLimit),
                    "performance_context" to it.performanceContext.map { context ->
                        context.limitDebugText(textCharLimit)
                    },
                    "performance_instruction" to it.performanceInstruction.limitDebugText(textCharLimit)
                )
            }.orEmpty()
        )
        if (includeStoryboard) {
            val storyboard = cache?.toChapterStoryboard()
            data["storyboard"] = mapOf(
                "scene_count" to (storyboard?.scenes?.size ?: 0),
                "segment_count" to (storyboard?.segmentCount ?: 0),
                "dialogue_count" to (storyboard?.dialogueCount ?: 0),
                "thought_count" to (storyboard?.thoughtCount ?: 0),
                "segments" to storyboard
                    ?.scenes
                    ?.asSequence()
                    ?.flatMap { scene ->
                        scene.segments.asSequence().map { scene.index to it }
                    }
                    ?.take(segmentLimit)
                    ?.map { (sceneIndex, segment) ->
                        mapOf(
                            "scene_index" to sceneIndex,
                            "type" to segment.type.name.lowercase(),
                            "speaker_id" to segment.speakerId,
                            "speaker_name" to segment.speakerName,
                            "speaker_gender" to segment.speakerGender,
                            "paragraph_index" to segment.paragraphIndex,
                            "start" to segment.start,
                            "end" to segment.end,
                            "text" to segment.text.limitDebugText(textCharLimit),
                            "evidence" to segment.evidence.limitDebugText(textCharLimit),
                            "performance_context" to segment.performanceContext.map { context ->
                                context.limitDebugText(textCharLimit)
                            },
                            "performance_instruction" to
                                segment.performanceInstruction.limitDebugText(textCharLimit)
                        )
                    }
                    ?.toList()
                    .orEmpty()
            )
        }
        if (includePayload) {
            data["payload"] = request.toPayload(request.units)
        }
        return data
    }

    fun segmentsForParagraph(
        storyboard: ChapterStoryboard?,
        paragraphIndex: Int,
        fallbackText: String
    ): List<StoryboardSegment> {
        val segments = storyboard
            ?.scenes
            ?.asSequence()
            ?.flatMap { it.segments.asSequence() }
            ?.filter { it.paragraphIndex == paragraphIndex }
            ?.sortedBy { it.start }
            ?.toList()
            .orEmpty()
        return segments.ifEmpty {
            listOf(
                StoryboardSegment(
                    type = StoryboardSegmentType.NARRATION,
                    paragraphIndex = paragraphIndex,
                    text = fallbackText,
                    speakerName = null,
                    evidence = "旁白",
                    start = 0,
                    end = fallbackText.length
                )
            )
        }
    }

    private suspend fun generate(
        request: StoryboardRequest,
        cachedScenes: List<SceneRange>
    ): GenerateResult {
        val selection = AiConfig.requireReadAloudStoryboardModel()
        val supportsReasoning = selection.supportsReasoning()
        val prepared = if (cachedScenes.isNotEmpty()) {
            val preparedRequest = request.withNaturalScenes(cachedScenes)
            PreparedModelResult(
                request = preparedRequest,
                assignments = requestModelUnits(
                    request = preparedRequest,
                    selection = selection,
                    systemPrompt = readSkillPrompt(preparedRequest.storyboardCapabilities),
                    supportsReasoning = supportsReasoning,
                    targetUnits = preparedRequest.units
                )
            )
        } else if (request.combineSceneAndRouting) {
            runCatching {
                requestCombinedScenesAndUnits(request, selection, supportsReasoning)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                io.legado.app.constant.AppLog.put(
                    "基础分镜合并请求无效，已回退到分步生成\n${error.localizedMessage}",
                    error
                )
                val preparedRequest = request.withNaturalScenes(selection, supportsReasoning)
                PreparedModelResult(
                    request = preparedRequest,
                    assignments = requestModelUnits(
                        request = preparedRequest,
                        selection = selection,
                        systemPrompt = readSkillPrompt(preparedRequest.storyboardCapabilities),
                        supportsReasoning = supportsReasoning,
                        targetUnits = preparedRequest.units
                    )
                )
            }
        } else {
            val preparedRequest = request.withNaturalScenes(selection, supportsReasoning)
            PreparedModelResult(
                request = preparedRequest,
                assignments = requestModelUnits(
                    request = preparedRequest,
                    selection = selection,
                    systemPrompt = readSkillPrompt(preparedRequest.storyboardCapabilities),
                    supportsReasoning = supportsReasoning,
                    targetUnits = preparedRequest.units
                )
            )
        }
        val preparedRequest = prepared.request
        val identityLinks = mergeIdentityLinks(
            preparedRequest.identityLinks,
            identityLinksFromAssignments(
                assignments = prepared.assignments,
                characters = preparedRequest.characters,
                castRoles = preparedRequest.castRoles
            )
        )
        return GenerateResult(
            cache = StoryboardCache(
                cacheVersion = CACHE_VERSION,
                key = preparedRequest.cacheKey,
                providerId = selection.providerId,
                modelId = selection.modelId,
                contentHash = preparedRequest.contentHash,
                identityCacheKey = preparedRequest.identityCacheKey,
                expressiveCacheKey = preparedRequest.expressiveCacheKey,
                generatedAt = System.currentTimeMillis(),
                bookUrl = preparedRequest.book.bookUrl,
                bookName = preparedRequest.book.name,
                bookAuthor = preparedRequest.book.author,
                chapterIndex = preparedRequest.chapterIndex,
                chapterTitle = preparedRequest.chapterTitle,
                mode = preparedRequest.mode,
                multiRoleEngineId = preparedRequest.multiRoleEngineId,
                storyboardCapabilities = preparedRequest.storyboardCapabilities,
                paragraphs = preparedRequest.paragraphs,
                scenes = preparedRequest.scenes,
                units = preparedRequest.units,
                assignments = prepared.assignments,
                identityLinks = identityLinks
            ),
            cacheable = true
        )
    }

    private suspend fun requestCombinedScenesAndUnits(
        request: StoryboardRequest,
        selection: AiModelSelection,
        supportsReasoning: Boolean
    ): PreparedModelResult {
        val result = AiManager.generateText(
            providerId = selection.providerId,
            modelId = selection.modelId,
            messages = listOf(
                AiMessage(
                    AiMessage.Role.SYSTEM,
                    readSkillPrompt(request.storyboardCapabilities) +
                        "\n\n" + readAssetPrompt(COMBINED_FALLBACK_SKILL_ASSET)
                ),
                AiMessage(AiMessage.Role.USER, GSON.toJson(request.toPayload(request.units)))
            ),
            params = AiConfig.readAloudStoryboardParams(
                targetUnitCount = request.units.size,
                supportsReasoning = supportsReasoning
            )
        )
        check(result.content.isNotBlank()) { result.emptyContentMessage() }
        check(result.finishReason != "length") { "AI 合并分镜输出被截断" }
        return parseAndValidateCombined(
            raw = result.content,
            request = request
        )
    }

    private suspend fun StoryboardRequest.withNaturalScenes(
        selection: AiModelSelection,
        supportsReasoning: Boolean
    ): StoryboardRequest {
        if (paragraphs.isEmpty()) return this
        val generated = runCatching {
            requestSceneRanges(selection, supportsReasoning)
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            io.legado.app.constant.AppLog.put(
                "自然场景划分失败，已使用连续段落边界\n${error.localizedMessage}",
                error
            )
            fallbackSceneRanges(paragraphs)
        }
        return withNaturalScenes(generated)
    }

    private fun StoryboardRequest.withNaturalScenes(
        generated: List<SceneRange>
    ): StoryboardRequest {
        if (paragraphs.isEmpty()) return this
        return copy(
            scenes = generated,
            units = units.map { unit ->
                val paragraphIndex = unit.ranges.firstOrNull()?.paragraphIndex
                val sceneId = generated.firstOrNull { scene ->
                    paragraphIndex != null && paragraphIndex in scene.startParagraphIndex..scene.endParagraphIndex
                }?.sceneId
                check(sceneId != null) { "候选片段未命中自然场景：${unit.unitId}" }
                unit.copy(sceneId = sceneId)
            }
        )
    }

    private suspend fun StoryboardRequest.requestSceneRanges(
        selection: AiModelSelection,
        supportsReasoning: Boolean
    ): List<SceneRange> {
        val payload = ScenePayload(
            chapter = PayloadChapter(chapterIndex, chapterTitle),
            paragraphCount = paragraphs.size,
            firstParagraphIndex = paragraphs.first().paragraphIndex,
            lastParagraphIndex = paragraphs.last().paragraphIndex,
            contextParagraphs = paragraphs
        )
        val result = AiManager.generateText(
            providerId = selection.providerId,
            modelId = selection.modelId,
            messages = listOf(
                AiMessage(AiMessage.Role.SYSTEM, readAssetPrompt(PERFORMANCE_SCENES_SKILL_ASSET)),
                AiMessage(AiMessage.Role.USER, GSON.toJson(payload))
            ),
            params = AiConfig.readAloudStoryboardParams(
                targetUnitCount = paragraphs.size,
                supportsReasoning = supportsReasoning,
                largeUnitOutput = false
            )
        )
        check(result.content.isNotBlank()) { result.emptyContentMessage() }
        check(result.finishReason != "length") { "AI 场景边界输出被截断" }
        return parseAndValidateScenes(result.content, paragraphs)
    }

    private suspend fun requestModelUnits(
        request: StoryboardRequest,
        selection: AiModelSelection,
        systemPrompt: String,
        supportsReasoning: Boolean,
        targetUnits: List<CandidateUnit>
    ): List<ModelUnitResult> {
        if (targetUnits.isEmpty()) return emptyList()
        return runCatching {
            requestModelUnitsOnce(
                request = request,
                selection = selection,
                systemPrompt = systemPrompt,
                supportsReasoning = supportsReasoning,
                targetUnits = targetUnits
            )
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            if (targetUnits.size < 2) throw error
            io.legado.app.constant.AppLog.put(
                "AI听书分镜整章结果无效，改为两段重试\n${error.localizedMessage}",
                error
            )
            val midpoint = (targetUnits.size + 1) / 2
            val assignments = listOf(targetUnits.take(midpoint), targetUnits.drop(midpoint)).flatMap { chunk ->
                requestModelUnitsOnce(
                    request = request,
                    selection = selection,
                    systemPrompt = systemPrompt,
                    supportsReasoning = supportsReasoning,
                    targetUnits = chunk
                )
            }
            applyAdjacentGenderEvidence(assignments, targetUnits)
        }
    }

    private fun scheduleCachedEnrichment(
        request: StoryboardRequest,
        cacheFile: File,
        cache: StoryboardCache,
        book: Book,
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ) {
        val job = cachedEnrichmentJobs.compute(request.cacheKey) { _, current ->
            if (current?.isActive == true) {
                current
            } else {
                cachedEnrichmentScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        cachedEnrichmentMutex.withLock {
                            val enriched = BookTtsCastingCoordinator.enrichCached(
                                book = book,
                                storyboard = storyboard,
                                characters = characters
                            )
                            persistPreparedStoryboard(request, cacheFile, cache, enriched)
                        }
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        io.legado.app.constant.AppLog.put(
                            "缓存分镜后台选角失败，已继续使用现有结果\n${error.localizedMessage}",
                            error
                        )
                    } finally {
                        cachedEnrichmentJobs.remove(
                            request.cacheKey,
                            currentCoroutineContext()[Job]
                        )
                    }
                }
            }
        }
        job?.start()
    }

    private suspend fun requestModelUnitsOnce(
        request: StoryboardRequest,
        selection: AiModelSelection,
        systemPrompt: String,
        supportsReasoning: Boolean,
        targetUnits: List<CandidateUnit>
    ): List<ModelUnitResult> {
        val payload = request.toPayload(targetUnits)
        val result = AiManager.generateText(
            providerId = selection.providerId,
            modelId = selection.modelId,
            messages = listOf(
                AiMessage(AiMessage.Role.SYSTEM, systemPrompt),
                AiMessage(AiMessage.Role.USER, GSON.toJson(payload))
            ),
            params = AiConfig.readAloudStoryboardParams(
                targetUnitCount = targetUnits.size,
                supportsReasoning = supportsReasoning
            )
        )
        check(result.content.isNotBlank()) {
            result.emptyContentMessage()
        }
        check(result.finishReason != "length") {
            "AI 输出被截断，请切换更大输出窗口的模型或减少章节候选 unit"
        }
        return applyAdjacentGenderEvidence(
            assignments = parseAndValidate(
                raw = result.content,
                targetUnits = targetUnits,
                capabilities = request.storyboardCapabilities,
                allowNewCharacters = false,
                knownCharacters = request.characters.map { it.toKnownCharacter() },
                knownCastRoles = request.castRoles.map { it.toKnownCastRole() }
            ),
            targetUnits = targetUnits
        )
    }

    private fun buildRequest(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): StoryboardRequest {
        val paragraphs = paragraphsFromContent(content).mapIndexed { index, text ->
            ContextParagraph(index, text)
        }
        val contentHash = MD5Utils.md5Encode(content)
        val multiRoleEngine = TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
            ?.takeIf { it.enabled && it.isScriptEngine }
        val multiRoleEngineId = multiRoleEngine?.id.orEmpty()
        val storyboardCapabilities = resolveStoryboardSkillCapabilities(
            declaredCapabilities = multiRoleEngine?.capabilities.orEmpty()
        )
        val mode = if (storyboardCapabilities.isEmpty()) {
            StoryboardMode.BASIC
        } else {
            StoryboardMode.PERFORMANCE
        }
        val enabledCharacters = characters.filter { it.enabled && it.name.isNotBlank() }
        val knownCastRoles = BookTtsCastingCoordinator.storyboardContextRoles(book, chapterIndex)
        val automation = BookTtsAutomationConfig.get(
            BookCharacterProfile.workKey(book.name, book.author)
        )
        val combineSceneAndRouting = shouldCombineFallbackRequest(
            autoCreateTemporaryRoles = automation.autoCreateTemporaryRoles,
            autoAssignVoices = automation.autoAssignVoices,
            capabilities = storyboardCapabilities
        )
        val selection = runCatching { AiConfig.requireReadAloudStoryboardModel() }.getOrNull()
        val identityCacheKey = storyboardIdentityCacheKey(
            book = book,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            contentHash = contentHash,
            providerId = selection?.providerId.orEmpty(),
            modelId = selection?.modelId.orEmpty()
        )
        val expressiveCacheKey = storyboardExpressiveCacheKey(
            identityCacheKey = identityCacheKey,
            capabilities = storyboardCapabilities
        )
        val cacheKey = storyboardCacheKey(
            book = book,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            contentHash = contentHash,
            mode = mode,
            capabilities = storyboardCapabilities,
            providerId = selection?.providerId.orEmpty(),
            modelId = selection?.modelId.orEmpty()
        )
        return StoryboardRequest(
            book = book,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            contentHash = contentHash,
            cacheKey = cacheKey,
            identityCacheKey = identityCacheKey,
            expressiveCacheKey = expressiveCacheKey,
            mode = mode,
            providerId = selection?.providerId.orEmpty(),
            modelId = selection?.modelId.orEmpty(),
            charactersHash = charactersHash(enabledCharacters),
            multiRoleEngineId = multiRoleEngineId,
            storyboardCapabilities = storyboardCapabilities,
            paragraphs = paragraphs,
            characters = enabledCharacters,
            castRoles = knownCastRoles,
            units = buildCandidateUnits(paragraphs),
            identityLinks = extractExplicitIdentityLinks(
                paragraphs = paragraphs,
                characters = enabledCharacters,
                castRoles = knownCastRoles
            ),
            combineSceneAndRouting = combineSceneAndRouting
        )
    }

    private fun extractExplicitIdentityLinks(
        paragraphs: List<ContextParagraph>,
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>
    ): List<StoryboardIdentityLink> {
        val targets = buildList {
            characters.forEach { character ->
                add(IdentityTarget(character.name, character.id, null))
            }
            castRoles.filter { it.linkedCharacterId == null }.forEach { role ->
                add(IdentityTarget(role.name, null, role.id))
            }
        }.filter { it.name.isNotBlank() }
            .distinctBy { BookTtsCastingCoordinator.normalizeIdentityName(it.name) }
        if (targets.isEmpty()) return emptyList()
        val mappings = findExplicitAliasMappings(paragraphs, targets.map { it.name })
        val targetIndex = targets.associateBy {
            BookTtsCastingCoordinator.normalizeIdentityName(it.name)
        }
        return mappings.mapNotNull { (alias, canonicalName) ->
            val target = targetIndex[BookTtsCastingCoordinator.normalizeIdentityName(canonicalName)]
                ?: return@mapNotNull null
            StoryboardIdentityLink(
                aliasName = alias,
                characterId = target.characterId,
                castRoleId = target.castRoleId,
                evidence = "正文明确说明“$alias”属于“${target.name}”"
            )
        }
    }

    internal fun findExplicitAliasMappings(
        paragraphs: List<ContextParagraph>,
        canonicalNames: List<String>
    ): Map<String, String> {
        if (paragraphs.isEmpty() || canonicalNames.isEmpty()) return emptyMap()
        val text = paragraphs.joinToString("\n") { it.text }
        val results = linkedMapOf<String, String>()
        val questionPattern = Regex(
            "(?:^|[\\s，。！？!?；;：:、【\\[])" +
                "([\\p{L}\\p{N}_·]{2,16})是谁[？?]?"
        )
        questionPattern.findAll(text).forEach { match ->
            val alias = match.groupValues[1].trim()
            val tail = text.substring(match.range.last + 1)
                .take(100)
            val owner = canonicalNames.firstOrNull { canonicalName ->
                Regex(
                    "(?:哦|原来|查到|发现)?[，,：:\\s]*是\\s*" +
                        Regex.escape(canonicalName) +
                        "(?=[，。！？!?；;：:\\s]|$)"
                ).containsMatchIn(tail)
            }
            if (owner != null &&
                BookTtsCastingCoordinator.normalizeIdentityName(alias) !=
                BookTtsCastingCoordinator.normalizeIdentityName(owner)
            ) {
                results.putIfAbsent(alias, owner)
            }
        }
        canonicalNames.forEach { canonicalName ->
            val escapedName = Regex.escape(canonicalName)
            val ownerFirst = Regex(
                "$escapedName\\s*的(?:QQ|微信|群)?(?:网名|昵称|账号|群名片|代号|乳名|外号)" +
                    "\\s*(?:是|叫|为)\\s*[【\\[]?([\\p{L}\\p{N}_·]{2,16})[】\\]]?"
            )
            ownerFirst.findAll(text).forEach { match ->
                results.putIfAbsent(match.groupValues[1].trim(), canonicalName)
            }
            val aliasFirst = Regex(
                "(?:^|[\\s，。！？!?；;：:、【\\[])" +
                    "([\\p{L}\\p{N}_·]{2,16})\\s*是\\s*$escapedName\\s*的" +
                    "(?:QQ|微信|群)?(?:网名|昵称|账号|群名片|代号|乳名|外号)"
            )
            aliasFirst.findAll(text).forEach { match ->
                results.putIfAbsent(match.groupValues[1].trim(), canonicalName)
            }
        }
        return results
    }

    private fun buildCandidateUnits(paragraphs: List<ContextParagraph>): List<CandidateUnit> {
        val texts = paragraphs.associate { it.paragraphIndex to it.text }
        val units = arrayListOf<CandidateUnit>()
        paragraphs.forEach { paragraph ->
            val text = paragraph.text
            val quoteSpans = findQuoteSpans(text)
            quoteSpans.forEach { span ->
                val preview = text.substring(span.start, span.end)
                val roleHint = quoteRoleHint(text, span.start, span.end)
                units += makeUnit(
                    kind = if (roleHint == "narrator") "quote_reference" else span.kind,
                    roleHint = roleHint,
                    ranges = listOf(TextRange(paragraph.paragraphIndex, span.start, span.end)),
                    textPreview = preview,
                    cueBefore = contextBefore(texts, paragraph.paragraphIndex, span.start),
                    cueAfter = contextAfter(texts, paragraph.paragraphIndex, span.end)
                )
            }
            findColonUnits(text, quoteSpans).forEach { span ->
                val preview = text.substring(span.start, span.end)
                units += makeUnit(
                    kind = span.kind,
                    roleHint = span.roleHint,
                    ranges = listOf(TextRange(paragraph.paragraphIndex, span.start, span.end)),
                    textPreview = preview,
                    cueBefore = contextBefore(texts, paragraph.paragraphIndex, span.start),
                    cueAfter = contextAfter(texts, paragraph.paragraphIndex, span.end)
                )
            }
        }
        return units.sortedWith(compareBy<CandidateUnit> {
            it.ranges.firstOrNull()?.paragraphIndex ?: 0
        }.thenBy {
            it.ranges.firstOrNull()?.start ?: 0
        }.thenBy {
            it.ranges.firstOrNull()?.end ?: 0
        })
    }

    private fun findQuoteSpans(text: String): List<UnitSpan> {
        val spans = arrayListOf<UnitSpan>()
        var index = 0
        while (index < text.length) {
            val open = text[index]
            if (open !in quotePairs) {
                index++
                continue
            }
            val close = findNextQuoteClose(text, index + 1, open)
            if (close < 0) {
                spans += UnitSpan(index, text.length, "quote_unclosed", "character")
                break
            }
            spans += UnitSpan(index, close + 1, "quote", "character")
            index = close + 1
        }
        return spans
    }

    private fun findNextQuoteClose(text: String, start: Int, open: Char): Int {
        return quoteCloseCandidates[open]
            .orEmpty()
            .map { text.indexOf(it, start) }
            .filter { it >= 0 }
            .minOrNull()
            ?: -1
    }

    private fun findColonUnits(text: String, quoteSpans: List<UnitSpan>): List<UnitSpan> {
        val results = arrayListOf<UnitSpan>()
        val quoteMask = BooleanArray(text.length)
        quoteSpans.forEach { span ->
            for (index in span.start until span.end.coerceAtMost(text.length)) {
                if (index >= 0) quoteMask[index] = true
            }
        }
        var index = 0
        while (index < text.length) {
            if (quoteMask[index] || text[index] !in "：:") {
                index++
                continue
            }
            if (isRatioOrTimeColon(text, index)) {
                index++
                continue
            }
            val prefixStart = previousBoundary(text, index)
            val roleHint = colonRoleHint(text.substring(prefixStart, index))
            var speechStart = index + 1
            while (speechStart < text.length && text[speechStart].isWhitespace()) {
                speechStart++
            }
            if (roleHint == null || speechStart >= text.length || text[speechStart] in quotePairs) {
                index++
                continue
            }
            val speechEnd = if (roleHint == "thought") text.length else nextSentenceEnd(text, speechStart)
            if (speechEnd <= speechStart) {
                index++
                continue
            }
            results += UnitSpan(
                speechStart,
                speechEnd,
                if (roleHint == "thought") "thought_colon" else "dialogue_colon",
                roleHint
            )
            index = speechEnd
        }
        return results
    }

    private fun isRatioOrTimeColon(text: String, index: Int): Boolean {
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + 1)
        return before?.isDigit() == true && after?.isDigit() == true
    }

    private fun previousBoundary(text: String, index: Int): Int {
        var start = 0
        "。！？!?；;\n".forEach { char ->
            start = maxOf(start, text.lastIndexOf(char, startIndex = index - 1) + 1)
        }
        return start
    }

    private fun nextSentenceEnd(text: String, index: Int): Int {
        var cursor = index
        while (cursor < text.length) {
            if (text[cursor] in sentencePunctuation) {
                var end = cursor + 1
                while (end < text.length && text[end] in "。！？!?…") {
                    end++
                }
                return end
            }
            cursor++
        }
        return text.length
    }

    private fun colonRoleHint(prefix: String): String? {
        val value = prefix.trim().trim('“', '”', '‘', '’', '"', '\'', '，', ',', '。', ':', '：')
        if (value.isBlank() || value.length > 40) return null
        if (thoughtCues.any { value.takeLast(16).contains(it) }) return "thought"
        if ((value.takeLast(16).contains("心里") || value.takeLast(16).contains("心中")) &&
            value.endsWith("想")
        ) return "thought"
        if (colonDialogueCues.any { value.takeLast(16).contains(it) }) return "character"
        return null
    }

    private fun looksLikeThought(text: String, start: Int, end: Int): Boolean {
        val before = text.substring(maxOf(0, start - 40), start)
        val after = text.substring(end, minOf(text.length, end + 40))
        return thoughtCues.any { before.contains(it) || after.contains(it) }
    }

    internal fun quoteRoleHint(text: String, start: Int, end: Int): String {
        if (looksLikeThought(text, start, end)) return "thought"
        if (looksLikeNarratedQuote(text, start, end)) return "narrator"
        return "character"
    }

    private fun looksLikeNarratedQuote(text: String, start: Int, end: Int): Boolean {
        if (start !in 0..text.length || end !in start..text.length) return false
        val prefix = text.substring(previousBoundary(text, start), start)
            .trim()
            .trimEnd('，', ',', '、')
        if (prefix.isBlank() || prefix.endsWith('：') || prefix.endsWith(':')) return false
        val nearbyPrefix = prefix.takeLast(28)
        if (narratedQuoteStrongCues.any(nearbyPrefix::contains)) return true
        val quotedLength = (end - start - 2).coerceAtLeast(0)
        return quotedLength <= 16 && narratedQuoteShortCues.any(nearbyPrefix::contains)
    }

    internal fun routedRoleType(roleHint: String, modelRoleType: String): String {
        return if (roleHint == "narrator") "narrator" else modelRoleType
    }

    private fun contextBefore(
        paragraphs: Map<Int, String>,
        paragraphIndex: Int,
        start: Int,
        limit: Int = 120
    ): String {
        val current = paragraphs[paragraphIndex].orEmpty().take(start)
        val previous = paragraphs[paragraphIndex - 1].orEmpty()
        return (previous.takeLast(40) + "\n" + current).trim().takeLast(limit)
    }

    private fun contextAfter(
        paragraphs: Map<Int, String>,
        paragraphIndex: Int,
        end: Int,
        limit: Int = 120
    ): String {
        val current = paragraphs[paragraphIndex].orEmpty().drop(end)
        val next = paragraphs[paragraphIndex + 1].orEmpty()
        return (current + "\n" + next.take(40)).trim().take(limit)
    }

    private fun makeUnit(
        kind: String,
        roleHint: String,
        ranges: List<TextRange>,
        textPreview: String,
        cueBefore: String,
        cueAfter: String
    ): CandidateUnit {
        val first = ranges.first()
        val last = ranges.last()
        val digest = MD5Utils.md5Encode(textPreview).take(8)
        return CandidateUnit(
            unitId = "u_${first.paragraphIndex}_${first.start}_${last.paragraphIndex}_${last.end}_${kind}_$digest",
            kind = kind,
            roleHint = roleHint,
            ranges = ranges,
            textPreview = textPreview,
            cueBefore = cueBefore,
            cueAfter = cueAfter
        )
    }

    private fun StoryboardRequest.toPayload(targetUnits: List<CandidateUnit>): StoryboardPayload {
        return StoryboardPayload(
            book = PayloadBook(book.name, book.author),
            chapter = PayloadChapter(chapterIndex, chapterTitle),
            mode = mode,
            storyboardCapabilities = storyboardCapabilities,
            allowNewCharacters = false,
            knownCharacters = characters.map { it.toKnownCharacter() },
            knownCastRoles = castRoles.map { it.toKnownCastRole() },
            contextParagraphs = paragraphs,
            scenes = scenes,
            units = targetUnits,
            targetUnitIds = targetUnits.map { it.unitId }
        )
    }

    private fun BookCharacter.toKnownCharacter(): KnownCharacter {
        val aliases = aliasesJson
            ?.let { GSON.fromJsonObject<List<String>>(it).getOrNull() }
            .orEmpty()
            .filter { it.isNotBlank() }
        return KnownCharacter(
            characterId = id,
            name = name,
            aliases = aliases,
            gender = gender,
            role = roleTag
        )
    }

    private fun BookTtsCastRole.toKnownCastRole(): KnownCastRole {
        val aliases = GSON.fromJsonObject<List<String>>(aliasesJson).getOrNull().orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val representativeTexts = GSON.fromJsonObject<List<String>>(representativeTextsJson).getOrNull().orEmpty()
            .map { it.trim().take(120) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        val evidenceItems = GSON.fromJsonObject<List<String>>(identityEvidenceJson).getOrNull().orEmpty()
            .map { it.trim().take(120) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
        return KnownCastRole(
            castRoleId = id,
            name = name,
            aliases = aliases,
            gender = gender,
            identityState = identityState,
            nameType = nameType,
            identityEvidence = identityEvidence,
            genderEvidence = genderEvidence,
            chapterRange = if (firstChapterIndex == lastChapterIndex) {
                "${firstChapterIndex + 1}"
            } else {
                "${firstChapterIndex + 1}-${lastChapterIndex + 1}"
            },
            occurrenceCount = occurrenceCount,
            representativeTexts = representativeTexts,
            evidence = evidenceItems
        )
    }

    private fun parseAndValidate(
        raw: String,
        targetUnits: List<CandidateUnit>,
        capabilities: List<String>,
        allowNewCharacters: Boolean,
        knownCharacters: List<KnownCharacter> = emptyList(),
        knownCastRoles: List<KnownCastRole> = emptyList()
    ): List<ModelUnitResult> {
        val json = normalizeModelOutput(raw).extractJsonObjectCandidate()
        check(json.isNotBlank()) { "AI 未返回 JSON 对象" }
        val element = JsonParser.parseString(json)
        check(element is JsonObject) { "AI 返回根节点不是 JSON 对象" }
        val rootExtraKeys = element.keySet() - rootKeys
        check(rootExtraKeys.isEmpty()) { "AI 返回额外根字段：${rootExtraKeys.joinToString()}" }
        check(findTextLeaks(element).isEmpty()) { "AI 返回中包含正文字段" }
        val unitsElement = element.get("units")
        check(unitsElement?.isJsonArray == true) { "AI 返回 units 不是数组" }
        if (!allowNewCharacters) {
            val newCharacters = element.get("newCharacters")
            check(newCharacters == null || (newCharacters.isJsonArray && newCharacters.asJsonArray.size() == 0)) {
                "AI 返回了未允许的新角色"
            }
        }
        val output = GSON.fromJson(json, StoryboardModelOutput::class.java)
        val targetUnitIds = targetUnits.map { it.unitId }
        val targetSet = targetUnitIds.toSet()
        val seen = output.units.map { it.unitId }
        val missing = targetUnitIds.filter { it !in seen }
        val duplicated = seen.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val unknown = seen.filter { it !in targetSet }
        check(missing.isEmpty()) { "AI 漏掉目标 unit：${missing.take(3).joinToString()}" }
        check(duplicated.isEmpty()) { "AI 重复返回 unit：${duplicated.take(3).joinToString()}" }
        check(unknown.isEmpty()) { "AI 返回未知 unit：${unknown.take(3).joinToString()}" }
        val knownIndex = knownSpeakerIndex(knownCharacters, knownCastRoles)
        val supportsScene = TtsEngineCapability.SCENE_CONTEXT in capabilities
        val supportsInstruction = TtsEngineCapability.PERFORMANCE_INSTRUCTION in capabilities
        val supportsStyle = TtsEngineCapability.STYLE_TAGS in capabilities
        val supportsEmotion = TtsEngineCapability.EMOTION in capabilities
        val supportsIntensity = TtsEngineCapability.EMOTION_INTENSITY in capabilities
        return output.units.mapIndexed { index, unit ->
            val item = unitsElement.asJsonArray[index].asJsonObject
            val extraKeys = item.keySet() - baseUnitKeys
            check(extraKeys.isEmpty()) { "AI 返回 unit 额外字段：${extraKeys.joinToString()}" }
            check(unit.roleType in roleTypes) { "AI 返回非法 roleType：${unit.roleType}" }
            check(unit.status in statuses) { "AI 返回非法 status：${unit.status}" }
            check(unit.speakerGender in speakerGenders) { "AI 返回非法 speakerGender：${unit.speakerGender}" }
            check(unit.identityType in identityTypes) { "AI 返回非法 identityType：${unit.identityType}" }
            check(unit.nameType in nameTypes) { "AI 返回非法 nameType：${unit.nameType}" }
            check(unit.identityEvidence in evidenceLevels) { "AI 返回非法 identityEvidence：${unit.identityEvidence}" }
            check(unit.genderEvidence in evidenceLevels) { "AI 返回非法 genderEvidence：${unit.genderEvidence}" }
            check(unit.confidence in 0f..1f) { "AI 返回非法 confidence：${unit.confidence}" }
            val source = targetUnits.first { it.unitId == unit.unitId }
            val roleType = routedRoleType(source.roleHint, unit.roleType)
            val isSpokenUnit = roleType == "character" || roleType == "thought"
            val performanceContext = sanitizePerformanceContext(
                context = unit.performanceContext,
                targetText = source.textPreview,
                enabled = supportsScene && isSpokenUnit
            )
            val performanceInstruction = sanitizePerformanceInstruction(
                instruction = unit.performanceInstruction,
                targetText = source.textPreview,
                enabled = supportsInstruction && isSpokenUnit
            )
            check(!supportsScene || !isSpokenUnit || performanceContext.isNotEmpty()) {
                "AI 未给对白或心声返回有效场景：${unit.unitId}"
            }
            check(!supportsInstruction || !isSpokenUnit || performanceInstruction.isNotEmpty()) {
                "AI 未给对白或心声返回有效演员指导：${unit.unitId}"
            }
            val styleConcepts = unit.styleConcepts
                .takeIf { supportsStyle && isSpokenUnit }
                .orEmpty()
                .map { it.trim().replace(Regex("\\s+"), " ").take(16) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(4)
            val emotion = unit.emotion
                ?.trim()
                ?.lowercase()
                ?.take(24)
                ?.takeIf { supportsEmotion && isSpokenUnit && it.isNotBlank() }
            val emotionIntensity = unit.emotionIntensity
                ?.takeIf { supportsIntensity && isSpokenUnit && it in 0f..1f }
            val expressiveConfidence = unit.expressiveConfidence
                ?.takeIf { supportsEmotion && isSpokenUnit && it in 0f..1f }
            val normalizedUnit = unit.copy(
                roleType = roleType,
                performanceContext = performanceContext,
                performanceInstruction = performanceInstruction,
                styleConcepts = styleConcepts,
                emotion = emotion,
                emotionIntensity = emotionIntensity,
                expressiveConfidence = expressiveConfidence
            )
            normalizeModelUnit(normalizedUnit, knownIndex)
        }
    }

    internal fun parseAndValidateForTest(
        raw: String,
        targetUnits: List<CandidateUnit>,
        capabilities: List<String>,
        allowNewCharacters: Boolean,
        knownCharacters: List<BookCharacter> = emptyList(),
        knownCastRoles: List<BookTtsCastRole> = emptyList()
    ): List<ModelUnitResult> = parseAndValidate(
        raw = raw,
        targetUnits = targetUnits,
        capabilities = capabilities,
        allowNewCharacters = allowNewCharacters,
        knownCharacters = knownCharacters.map { it.toKnownCharacter() },
        knownCastRoles = knownCastRoles.map { it.toKnownCastRole() }
    )

    private fun parseAndValidateScenes(
        raw: String,
        paragraphs: List<ContextParagraph>
    ): List<SceneRange> {
        val json = normalizeModelOutput(raw).extractJsonObjectCandidate()
        check(json.isNotBlank()) { "AI 未返回场景 JSON" }
        val element = JsonParser.parseString(json)
        check(element is JsonObject && element.keySet() == setOf("scenes")) {
            "AI 场景返回包含非法字段"
        }
        val scenesElement = element.get("scenes")
        check(scenesElement?.isJsonArray == true) { "AI 场景返回不是数组" }
        scenesElement.asJsonArray.forEach { item ->
            check(item.isJsonObject && item.asJsonObject.keySet() == sceneKeys) {
                "AI 场景项包含非法字段"
            }
        }
        val output = GSON.fromJson(json, SceneOutput::class.java)
        val scenes = output.scenes
            .map { it.copy(title = it.title.replace(Regex("\\s+"), " ").trim()) }
            .sortedBy { it.startParagraphIndex }
        check(scenes.isNotEmpty()) { "AI 未返回自然场景" }
        check(scenes.map { it.sceneId }.all { it.isNotBlank() }) { "AI 返回空 sceneId" }
        check(scenes.map { it.sceneId }.distinct().size == scenes.size) { "AI 返回重复 sceneId" }
        scenes.forEach { scene ->
            check(scene.title.length in 2..30) { "AI 返回非法场景标题" }
            check(scene.startParagraphIndex <= scene.endParagraphIndex) { "AI 返回非法场景范围" }
        }
        val indexes = paragraphs.map { it.paragraphIndex }
        indexes.forEach { paragraphIndex ->
            check(scenes.count { paragraphIndex in it.startParagraphIndex..it.endParagraphIndex } == 1) {
                "自然段 $paragraphIndex 未被场景唯一覆盖"
            }
        }
        check(scenes.first().startParagraphIndex == indexes.first()) { "场景未从首段开始" }
        check(scenes.last().endParagraphIndex == indexes.last()) { "场景未覆盖末段" }
        return scenes
    }

    internal fun parseAndValidateScenesForTest(
        raw: String,
        paragraphs: List<ContextParagraph>
    ): List<SceneRange> = parseAndValidateScenes(raw, paragraphs)

    internal fun shouldCombineFallbackRequest(
        autoCreateTemporaryRoles: Boolean,
        autoAssignVoices: Boolean,
        capabilities: List<String>
    ): Boolean = !autoCreateTemporaryRoles &&
        !autoAssignVoices &&
        TtsEngineCapability.SCENE_CONTEXT !in capabilities &&
        TtsEngineCapability.PERFORMANCE_INSTRUCTION !in capabilities

    private fun parseAndValidateCombined(
        raw: String,
        request: StoryboardRequest
    ): PreparedModelResult {
        val (scenes, assignments) = parseCombinedPayload(
            raw = raw,
            paragraphs = request.paragraphs,
            targetUnits = request.units,
            capabilities = request.storyboardCapabilities,
            knownCharacters = request.characters.map { it.toKnownCharacter() },
            knownCastRoles = request.castRoles.map { it.toKnownCastRole() }
        )
        val preparedRequest = request.withNaturalScenes(scenes)
        return PreparedModelResult(preparedRequest, assignments)
    }

    private fun parseCombinedPayload(
        raw: String,
        paragraphs: List<ContextParagraph>,
        targetUnits: List<CandidateUnit>,
        capabilities: List<String>,
        knownCharacters: List<KnownCharacter> = emptyList(),
        knownCastRoles: List<KnownCastRole> = emptyList()
    ): Pair<List<SceneRange>, List<ModelUnitResult>> {
        val json = normalizeModelOutput(raw).extractJsonObjectCandidate()
        check(json.isNotBlank()) { "AI 未返回合并分镜 JSON" }
        val element = JsonParser.parseString(json)
        check(element is JsonObject && element.keySet() == setOf("scenes", "units", "newCharacters")) {
            "AI 合并分镜返回包含非法字段"
        }
        val sceneRoot = JsonObject().apply {
            add("scenes", element.get("scenes"))
        }
        val unitRoot = JsonObject().apply {
            add("units", element.get("units"))
            add("newCharacters", element.get("newCharacters"))
        }
        val scenes = parseAndValidateScenes(GSON.toJson(sceneRoot), paragraphs)
        val assignments = parseAndValidate(
            raw = GSON.toJson(unitRoot),
            targetUnits = targetUnits,
            capabilities = capabilities,
            allowNewCharacters = false,
            knownCharacters = knownCharacters,
            knownCastRoles = knownCastRoles
        )
        return scenes to assignments
    }

    internal fun parseAndValidateCombinedForTest(
        raw: String,
        paragraphs: List<ContextParagraph>,
        targetUnits: List<CandidateUnit>,
        capabilities: List<String> = emptyList()
    ): Pair<List<SceneRange>, List<ModelUnitResult>> = parseCombinedPayload(
        raw = raw,
        paragraphs = paragraphs,
        targetUnits = targetUnits,
        capabilities = capabilities
    )

    private fun fallbackSceneRanges(paragraphs: List<ContextParagraph>): List<SceneRange> {
        if (paragraphs.isEmpty()) return emptyList()
        val scenes = arrayListOf<SceneRange>()
        var start = paragraphs.first().paragraphIndex
        var end = start
        var count = 0
        var length = 0
        paragraphs.forEach { paragraph ->
            if (count > 0 && (count >= 8 || length + paragraph.text.length > 900)) {
                scenes += SceneRange("scene_${scenes.size + 1}", "", start, end)
                start = paragraph.paragraphIndex
                count = 0
                length = 0
            }
            end = paragraph.paragraphIndex
            count++
            length += paragraph.text.length
        }
        scenes += SceneRange("scene_${scenes.size + 1}", "", start, end)
        return scenes
    }

    internal fun sanitizePerformanceContext(
        context: List<String>,
        targetText: String,
        enabled: Boolean
    ): List<String> {
        if (!enabled) return emptyList()
        val target = targetText.normalizeComparisonText()
        var remainingChars = 220
        return context.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                target.length >= 12 && target in it.normalizeComparisonText()
            }
            .distinct()
            .take(3)
            .mapNotNull { item ->
                if (remainingChars <= 0) return@mapNotNull null
                val sanitized = item.take(minOf(80, remainingChars)).trim()
                remainingChars -= sanitized.length
                sanitized.takeIf { it.isNotBlank() }
            }
            .toList()
    }

    internal fun sanitizePerformanceInstruction(
        instruction: String,
        targetText: String,
        enabled: Boolean
    ): String {
        if (!enabled) return ""
        val sanitized = instruction.trim().replace(Regex("\\s+"), " ")
        if (sanitized.length !in 4..40) return ""
        val target = targetText.normalizeComparisonText()
        val normalizedInstruction = sanitized.normalizeComparisonText()
        if (target.isNotBlank() &&
            (target == normalizedInstruction || target.length >= 8 && target in normalizedInstruction)
        ) return ""
        return sanitized
    }

    private fun String.normalizeComparisonText(): String {
        return replace(Regex("[\\s“”‘’\\\"'，。！？!?；;：:、…—-]+"), "")
    }

    private fun knownSpeakerIndex(
        knownCharacters: List<KnownCharacter>,
        knownCastRoles: List<KnownCastRole>
    ): KnownSpeakerIndex {
        val charactersById = knownCharacters
            .filter { it.characterId > 0L }
            .associateBy { it.characterId }
        val charactersByName = buildMap {
            knownCharacters.forEach { character ->
                (listOf(character.name) + character.aliases).forEach { name ->
                    val key = BookTtsCastingCoordinator.normalizeIdentityName(name)
                    if (key.isNotBlank()) put(key, character)
                }
            }
        }
        val castRolesById = knownCastRoles.filter { it.castRoleId > 0L }.associateBy { it.castRoleId }
        val castRolesByName = buildMap {
            knownCastRoles.forEach { role ->
                (listOf(role.name) + role.aliases).forEach { name ->
                    val key = BookTtsCastingCoordinator.normalizeIdentityName(name)
                    if (key.isNotBlank()) put(key, role)
                }
            }
        }
        return KnownSpeakerIndex(charactersById, charactersByName, castRolesById, castRolesByName)
    }

    internal fun applyAdjacentGenderEvidence(
        assignments: List<ModelUnitResult>,
        targetUnits: List<CandidateUnit>
    ): List<ModelUnitResult> {
        if (assignments.isEmpty() || targetUnits.size < 2) return assignments
        val byId = assignments.associateBy { it.unitId }.toMutableMap()
        targetUnits.zipWithNext().forEach { (previousUnit, currentUnit) ->
            val previous = byId[previousUnit.unitId] ?: return@forEach
            val current = byId[currentUnit.unitId] ?: return@forEach
            if (previous.roleType !in setOf("character", "thought") ||
                current.roleType !in setOf("character", "thought") ||
                current.speakerGender != StoryboardSegment.SpeakerGender.UNKNOWN ||
                sameSpeakerIdentity(previous, current)
            ) return@forEach
            val previousParagraph = previousUnit.ranges.firstOrNull()?.paragraphIndex ?: return@forEach
            val currentParagraph = currentUnit.ranges.firstOrNull()?.paragraphIndex ?: return@forEach
            if (currentParagraph - previousParagraph !in 0..1) return@forEach
            val text = previousUnit.textPreview.trimStart { character ->
                character.isWhitespace() || character in "“”‘’\"'"
            }
            val address = femaleAddresses.firstOrNull(text::startsWith)
                ?: maleAddresses.firstOrNull(text::startsWith)
                ?: return@forEach
            val gender = if (address in femaleAddresses) {
                StoryboardSegment.SpeakerGender.FEMALE
            } else {
                StoryboardSegment.SpeakerGender.MALE
            }
            val evidence = listOf(current.evidence.trim().trimEnd('；', ';'), "紧邻称呼“$address”")
                .filter { it.isNotBlank() }
                .joinToString("；")
            byId[current.unitId] = current.copy(
                speakerGender = gender,
                genderEvidence = StoryboardSegment.Evidence.EXPLICIT,
                evidence = evidence
            )
        }
        return assignments.map { byId[it.unitId] ?: it }
    }

    private fun sameSpeakerIdentity(first: ModelUnitResult, second: ModelUnitResult): Boolean {
        if (first.characterId > 0L && first.characterId == second.characterId) return true
        if (first.castRoleId > 0L && first.castRoleId == second.castRoleId) return true
        val firstName = BookTtsCastingCoordinator.normalizeIdentityName(first.characterName)
        val secondName = BookTtsCastingCoordinator.normalizeIdentityName(second.characterName)
        return firstName.isNotBlank() && firstName == secondName
    }

    private fun normalizeModelUnit(
        unit: ModelUnitResult,
        knownIndex: KnownSpeakerIndex
    ): ModelUnitResult {
        if (unit.roleType == "narrator" || unit.roleType == "other") {
            return unit.copy(
                characterName = "",
                characterId = 0L,
                castRoleId = 0L,
                speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
                identityType = StoryboardSegment.IdentityType.NONE,
                nameType = StoryboardSegment.NameType.UNKNOWN,
                identityEvidence = StoryboardSegment.Evidence.UNKNOWN,
                genderEvidence = StoryboardSegment.Evidence.UNKNOWN,
                mergeCastRoleIds = emptyList(),
                status = "unknown",
                performanceContext = emptyList(),
                performanceInstruction = "",
                styleConcepts = emptyList(),
                emotion = null,
                emotionIntensity = null,
                expressiveConfidence = null
            )
        }
        val modelDisplayName = unit.characterName.trim()
        val normalizedDisplayName = BookTtsCastingCoordinator.normalizeIdentityName(modelDisplayName)
        val knownCharacter = knownIndex.charactersById[unit.characterId]
            ?: normalizedDisplayName.takeIf { it.isNotBlank() }?.let { knownIndex.charactersByName[it] }
        if (knownCharacter != null) {
            val mergeIds = unit.mergeCastRoleIds
                .filter { it > 0L && it in knownIndex.castRolesById }
                .distinct()
                .takeIf {
                    unit.identityEvidence == StoryboardSegment.Evidence.EXPLICIT && unit.confidence >= 0.85f
                }
                .orEmpty()
            val explicitAlias = unit.nameType == StoryboardSegment.NameType.ALIAS &&
                unit.identityEvidence == StoryboardSegment.Evidence.EXPLICIT &&
                unit.confidence >= 0.85f &&
                normalizedDisplayName.isNotBlank() &&
                normalizedDisplayName != BookTtsCastingCoordinator.normalizeIdentityName(knownCharacter.name)
            return unit.copy(
                characterName = modelDisplayName.takeIf { explicitAlias } ?: knownCharacter.name,
                characterId = knownCharacter.characterId,
                castRoleId = 0L,
                speakerGender = knownCharacter.gender.takeIf {
                    it in speakerGenders && it != StoryboardSegment.SpeakerGender.UNKNOWN
                }
                    ?: unit.speakerGender,
                identityType = StoryboardSegment.IdentityType.FORMAL_CHARACTER,
                mergeCastRoleIds = mergeIds,
                status = "assigned"
            )
        }
        val hasStableNameType = unit.nameType == StoryboardSegment.NameType.PROPER_NAME ||
            unit.nameType == StoryboardSegment.NameType.ALIAS ||
            unit.nameType == StoryboardSegment.NameType.UNIQUE_TITLE
        val knownCastRole = if (
            unit.identityType == StoryboardSegment.IdentityType.GUEST && !hasStableNameType
        ) {
            null
        } else {
            knownIndex.castRolesById[unit.castRoleId]
                ?: if (unit.identityType == StoryboardSegment.IdentityType.CAST_ROLE || hasStableNameType) {
                    normalizedDisplayName.takeIf { it.isNotBlank() }?.let { knownIndex.castRolesByName[it] }
                } else null
        }
        if (knownCastRole != null) {
            val incomingGenderIsStronger = evidenceRank(unit.genderEvidence) > evidenceRank(knownCastRole.genderEvidence)
            val resolvedGender = unit.speakerGender.takeIf {
                incomingGenderIsStronger && it in speakerGenders && it != StoryboardSegment.SpeakerGender.UNKNOWN
            } ?: knownCastRole.gender.takeIf {
                it in speakerGenders && it != StoryboardSegment.SpeakerGender.UNKNOWN
            } ?: unit.speakerGender
            val mergeIds = unit.mergeCastRoleIds
                .filter { it > 0L && it != knownCastRole.castRoleId && it in knownIndex.castRolesById }
                .distinct()
                .takeIf {
                    unit.identityEvidence == StoryboardSegment.Evidence.EXPLICIT && unit.confidence >= 0.85f
                }
                .orEmpty()
            return unit.copy(
                characterName = modelDisplayName.ifBlank { knownCastRole.name },
                characterId = 0L,
                castRoleId = knownCastRole.castRoleId,
                speakerGender = resolvedGender,
                identityType = if (unit.identityType == StoryboardSegment.IdentityType.PENDING) {
                    StoryboardSegment.IdentityType.PENDING
                } else {
                    StoryboardSegment.IdentityType.CAST_ROLE
                },
                nameType = unit.nameType.takeUnless { it == StoryboardSegment.NameType.UNKNOWN }
                    ?: knownCastRole.nameType,
                mergeCastRoleIds = mergeIds.takeUnless {
                    unit.identityType == StoryboardSegment.IdentityType.PENDING
                }.orEmpty(),
                status = "assigned"
            )
        }
        val hasClassifiedIdentity = shouldKeepUnboundSpeaker(
            modelDisplayName,
            unit.identityType,
            unit.nameType
        )
        if (hasClassifiedIdentity) {
            val normalizedIdentityType = normalizedUnboundIdentityType(
                identityType = unit.identityType,
                nameType = unit.nameType
            )
            return unit.copy(
                characterName = modelDisplayName,
                characterId = 0L,
                castRoleId = 0L,
                identityType = normalizedIdentityType,
                mergeCastRoleIds = emptyList(),
                status = "unknown"
            )
        }
        return unit.copy(
            roleType = "narrator",
            characterName = "",
            characterId = 0L,
            castRoleId = 0L,
            speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
            identityType = StoryboardSegment.IdentityType.NONE,
            nameType = StoryboardSegment.NameType.UNKNOWN,
            identityEvidence = StoryboardSegment.Evidence.UNKNOWN,
            genderEvidence = StoryboardSegment.Evidence.UNKNOWN,
            mergeCastRoleIds = emptyList(),
            status = "unknown",
            performanceContext = emptyList(),
            performanceInstruction = "",
            styleConcepts = emptyList(),
            emotion = null,
            emotionIntensity = null,
            expressiveConfidence = null
        )
    }

    internal fun identityLinksFromAssignments(
        assignments: List<ModelUnitResult>,
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>
    ): List<StoryboardIdentityLink> {
        if (assignments.isEmpty()) return emptyList()
        val characterIndex = characters.filter { it.id > 0L }.associateBy { it.id }
        val castRoleIndex = castRoles.filter { it.id > 0L }.associateBy { it.id }
        return assignments.mapNotNull { assignment ->
            if (assignment.nameType != StoryboardSegment.NameType.ALIAS ||
                assignment.identityEvidence != StoryboardSegment.Evidence.EXPLICIT ||
                assignment.confidence < 0.85f
            ) return@mapNotNull null
            val alias = assignment.characterName.trim()
            val targetName = when {
                assignment.characterId > 0L -> characterIndex[assignment.characterId]?.name
                assignment.castRoleId > 0L -> castRoleIndex[assignment.castRoleId]?.name
                else -> null
            }?.trim().orEmpty()
            val normalizedAlias = BookTtsCastingCoordinator.normalizeIdentityName(alias)
            if (targetName.isBlank() ||
                normalizedAlias.isBlank() ||
                normalizedAlias == BookTtsCastingCoordinator.normalizeIdentityName(targetName)
            ) return@mapNotNull null
            StoryboardIdentityLink(
                aliasName = alias,
                characterId = assignment.characterId.takeIf { it in characterIndex },
                castRoleId = assignment.castRoleId.takeIf { it in castRoleIndex },
                evidence = assignment.evidence.trim()
            )
        }.distinctBy { link ->
            Triple(
                BookTtsCastingCoordinator.normalizeIdentityName(link.aliasName),
                link.characterId,
                link.castRoleId
            )
        }
    }

    private fun mergeIdentityLinks(
        deterministic: List<StoryboardIdentityLink>,
        modelDerived: List<StoryboardIdentityLink>
    ): List<StoryboardIdentityLink> = (deterministic + modelDerived).distinctBy { link ->
        Triple(
            BookTtsCastingCoordinator.normalizeIdentityName(link.aliasName),
            link.characterId,
            link.castRoleId
        )
    }

    private fun evidenceRank(value: String): Int = when (value) {
        StoryboardSegment.Evidence.EXPLICIT -> 3
        StoryboardSegment.Evidence.CONTEXTUAL -> 2
        StoryboardSegment.Evidence.INFERRED -> 1
        else -> 0
    }

    internal fun shouldKeepUnboundSpeaker(
        displayName: String,
        identityType: String,
        nameType: String
    ): Boolean = displayName.isNotBlank() && (
        identityType == StoryboardSegment.IdentityType.STABLE_CANDIDATE ||
            identityType == StoryboardSegment.IdentityType.PENDING ||
            identityType == StoryboardSegment.IdentityType.GUEST ||
            nameType == StoryboardSegment.NameType.PROPER_NAME ||
            nameType == StoryboardSegment.NameType.UNIQUE_TITLE ||
        nameType == StoryboardSegment.NameType.GENERIC_LABEL
        )

    internal fun normalizedUnboundIdentityType(identityType: String, nameType: String): String {
        val stableNameType = nameType == StoryboardSegment.NameType.PROPER_NAME ||
            nameType == StoryboardSegment.NameType.ALIAS ||
            nameType == StoryboardSegment.NameType.UNIQUE_TITLE
        if (identityType == StoryboardSegment.IdentityType.GUEST && stableNameType) {
            return StoryboardSegment.IdentityType.STABLE_CANDIDATE
        }
        return when (identityType) {
            StoryboardSegment.IdentityType.STABLE_CANDIDATE,
            StoryboardSegment.IdentityType.PENDING,
            StoryboardSegment.IdentityType.GUEST -> identityType
            else -> if (stableNameType) {
                StoryboardSegment.IdentityType.STABLE_CANDIDATE
            } else {
                StoryboardSegment.IdentityType.GUEST
            }
        }
    }

    internal fun resolvedSegmentType(assignment: ModelUnitResult?): StoryboardSegmentType {
        val roleType = assignment?.roleType
        val assignedOrClassifiedSpeaker = assignment?.status == "assigned" || assignment?.let {
            shouldKeepUnboundSpeaker(
                displayName = it.characterName,
                identityType = it.identityType,
                nameType = it.nameType
            )
        } == true
        return when {
            assignedOrClassifiedSpeaker && roleType == "character" ->
                StoryboardSegmentType.DIALOGUE
            assignedOrClassifiedSpeaker && roleType == "thought" ->
                StoryboardSegmentType.THOUGHT
            else -> StoryboardSegmentType.NARRATION
        }
    }

    private fun StoryboardCache.toChapterStoryboard(): ChapterStoryboard {
        val assignmentMap = assignments.associateBy { it.unitId }
        val unitMap = units.associateBy { it.unitId }
        val paragraphSegments = paragraphs.associate { paragraph ->
            paragraph.paragraphIndex to buildSegmentsForParagraph(paragraph, unitMap, assignmentMap)
        }
        val storyboardScenes = buildScenes(
            paragraphs,
            paragraphSegments,
            scenes,
            sceneVoiceAssignments
        )
        return ChapterStoryboard(
            chapterTitle = chapterTitle,
            scenes = storyboardScenes,
            identityLinks = identityLinks,
            sourceCacheKey = key,
            sourceCacheRevision = generatedAt,
            sourceChapterIndex = chapterIndex
        )
    }

    private fun buildSegmentsForParagraph(
        paragraph: ContextParagraph,
        unitMap: Map<String, CandidateUnit>,
        assignmentMap: Map<String, ModelUnitResult>
    ): List<StoryboardSegment> {
        val paragraphUnits = unitsForParagraph(paragraph.paragraphIndex, unitMap.values)
        val segments = arrayListOf<StoryboardSegment>()
        var cursor = 0
        paragraphUnits.forEach { unit ->
            val range = unit.ranges.firstOrNull { it.paragraphIndex == paragraph.paragraphIndex }
                ?: return@forEach
            if (range.start > cursor) {
                addNarrationSegment(paragraph, cursor, range.start, segments)
            }
            val assignment = assignmentMap[unit.unitId]
            val type = resolvedSegmentType(assignment)
            segments += StoryboardSegment(
                type = type,
                paragraphIndex = paragraph.paragraphIndex,
                text = paragraph.text.substring(range.start, range.end.coerceAtMost(paragraph.text.length)),
                speakerName = assignment?.characterName
                    ?.trim()
                    ?.takeIf { type != StoryboardSegmentType.NARRATION && it.isNotBlank() },
                evidence = when {
                    type == StoryboardSegmentType.NARRATION &&
                        assignment?.evidence?.isNotBlank() == true -> assignment.evidence
                    type == StoryboardSegmentType.NARRATION -> "AI归因：旁白"
                    assignment?.evidence?.isNotBlank() == true -> "AI归因：${assignment.evidence}"
                    else -> "AI归因"
                },
                speakerId = assignment?.characterId?.takeIf { it > 0L && type != StoryboardSegmentType.NARRATION },
                castRoleId = assignment?.castRoleId?.takeIf { it > 0L && type != StoryboardSegmentType.NARRATION },
                speakerGender = if (type == StoryboardSegmentType.NARRATION) {
                    StoryboardSegment.SpeakerGender.UNKNOWN
                } else {
                    assignment?.speakerGender ?: StoryboardSegment.SpeakerGender.UNKNOWN
                },
                identityType = assignment?.identityType
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
                    ?: StoryboardSegment.IdentityType.NONE,
                nameType = assignment?.nameType
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
                    ?: StoryboardSegment.NameType.UNKNOWN,
                identityEvidence = assignment?.identityEvidence
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
                    ?: StoryboardSegment.Evidence.UNKNOWN,
                genderEvidence = assignment?.genderEvidence
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
                    ?: StoryboardSegment.Evidence.UNKNOWN,
                mergeCastRoleIds = assignment?.mergeCastRoleIds
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
                    .orEmpty(),
                start = range.start,
                end = range.end,
                performanceContext = if (type == StoryboardSegmentType.NARRATION) {
                    emptyList()
                } else {
                    assignment?.performanceContext.orEmpty()
                },
                performanceInstruction = if (type == StoryboardSegmentType.NARRATION) {
                    ""
                } else {
                    assignment?.performanceInstruction.orEmpty()
                },
                styleConcepts = assignment?.styleConcepts
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
                    .orEmpty(),
                emotion = assignment?.emotion?.takeIf { type != StoryboardSegmentType.NARRATION },
                emotionIntensity = assignment?.emotionIntensity
                    ?.takeIf { type != StoryboardSegmentType.NARRATION },
                expressiveConfidence = assignment?.expressiveConfidence
                    ?.takeIf { type != StoryboardSegmentType.NARRATION }
            )
            cursor = range.end
        }
        if (cursor < paragraph.text.length) {
            addNarrationSegment(paragraph, cursor, paragraph.text.length, segments)
        }
        return segments.filter { it.text.isNotBlank() }.mergeAdjacent()
    }

    private fun unitsForParagraph(
        paragraphIndex: Int,
        units: Collection<CandidateUnit>
    ): List<CandidateUnit> {
        return units
            .filter { unit -> unit.ranges.any { it.paragraphIndex == paragraphIndex } }
            .sortedBy { it.ranges.first { range -> range.paragraphIndex == paragraphIndex }.start }
    }

    private fun addNarrationSegment(
        paragraph: ContextParagraph,
        start: Int,
        end: Int,
        segments: MutableList<StoryboardSegment>
    ) {
        val text = paragraph.text.substring(start, end.coerceAtMost(paragraph.text.length))
        if (text.isBlank()) return
        segments += StoryboardSegment(
            type = StoryboardSegmentType.NARRATION,
            paragraphIndex = paragraph.paragraphIndex,
            text = text,
            speakerName = null,
            evidence = "旁白",
            start = start,
            end = end
        )
    }

    private fun List<StoryboardSegment>.mergeAdjacent(): List<StoryboardSegment> {
        val result = arrayListOf<StoryboardSegment>()
        forEach { segment ->
            val last = result.lastOrNull()
            if (last != null &&
                last.type == segment.type &&
                last.speakerId == segment.speakerId &&
                last.castRoleId == segment.castRoleId &&
                last.speakerName == segment.speakerName &&
                last.speakerGender == segment.speakerGender &&
                last.identityType == segment.identityType &&
                last.nameType == segment.nameType &&
                last.identityEvidence == segment.identityEvidence &&
                last.genderEvidence == segment.genderEvidence &&
                last.mergeCastRoleIds == segment.mergeCastRoleIds &&
                last.performanceContext == segment.performanceContext &&
                last.performanceInstruction == segment.performanceInstruction &&
                last.styleConcepts == segment.styleConcepts &&
                last.emotion == segment.emotion &&
                last.emotionIntensity == segment.emotionIntensity &&
                last.expressiveConfidence == segment.expressiveConfidence &&
                last.end == segment.start
            ) {
                result[result.lastIndex] = last.copy(
                    text = last.text + segment.text,
                    end = segment.end
                )
            } else {
                result += segment
            }
        }
        return result
    }

    private fun String.limitDebugText(limit: Int): String {
        val safeLimit = limit.coerceAtLeast(0)
        return if (safeLimit == 0 || length <= safeLimit) {
            this
        } else {
            take(safeLimit) + "…"
        }
    }

    private fun buildScenes(
        paragraphs: List<ContextParagraph>,
        paragraphSegments: Map<Int, List<StoryboardSegment>>,
        sceneRanges: List<SceneRange>,
        sceneVoiceAssignments: List<CachedSceneVoiceAssignment>
    ): List<StoryboardScene> {
        val groups: List<Pair<SceneRange?, List<ContextParagraph>>> = if (sceneRanges.isNotEmpty()) {
            sceneRanges.map { scene ->
                scene to paragraphs.filter {
                    it.paragraphIndex in scene.startParagraphIndex..scene.endParagraphIndex
                }
            }.filter { (_, group) -> group.isNotEmpty() }
        } else {
            val fallbackGroups = arrayListOf<MutableList<ContextParagraph>>()
            var current = arrayListOf<ContextParagraph>()
            var currentLength = 0
            paragraphs.forEach { paragraph ->
                if (current.isNotEmpty() &&
                    (current.size >= 8 || currentLength + paragraph.text.length > 900)
                ) {
                    fallbackGroups += current
                    current = arrayListOf()
                    currentLength = 0
                }
                current += paragraph
                currentLength += paragraph.text.length
            }
            if (current.isNotEmpty()) fallbackGroups += current
            fallbackGroups.map { null to it }
        }
        return groups.mapIndexed { index, (sceneRange, group) ->
            val segments = group.flatMap { paragraphSegments[it.paragraphIndex].orEmpty() }
            val names = segments
                .mapNotNull { it.speakerName ?: it.virtualSpeakerName() }
                .distinct()
            val summary = segments.firstOrNull { it.type == StoryboardSegmentType.NARRATION }?.text
                ?: group.firstOrNull()?.text.orEmpty()
            StoryboardScene(
                index = index + 1,
                title = sceneRange?.title?.takeIf { it.isNotBlank() }
                    ?: names.takeIf { it.isNotEmpty() }?.take(3)?.joinToString("、")
                    ?: group.firstOrNull()?.text.orEmpty().replace(Regex("\\s+"), " ").trim().take(28),
                summary = summary.replace(Regex("\\s+"), " ").trim().take(64),
                characters = names,
                segments = segments,
                contextText = group.joinToString("\n") { it.text },
                voiceAssignments = sceneVoiceAssignments
                    .filter { it.sceneIndex == index + 1 }
                    .map { it.assignment }
            )
        }
    }

    private fun StoryboardSegment.virtualSpeakerName(): String? {
        if (type != StoryboardSegmentType.DIALOGUE && type != StoryboardSegmentType.THOUGHT) {
            return null
        }
        return when (speakerGender) {
            StoryboardSegment.SpeakerGender.MALE -> "对白男"
            StoryboardSegment.SpeakerGender.FEMALE -> "对白女"
            else -> null
        }
    }

    private fun persistLayeredCaches(request: StoryboardRequest, cache: StoryboardCache) {
        val identity = StoryboardIdentityCache(
            key = request.identityCacheKey,
            storyboard = cache.copy(
                key = request.identityCacheKey,
                mode = StoryboardMode.BASIC,
                multiRoleEngineId = "",
                storyboardCapabilities = emptyList(),
                expressiveCacheKey = "",
                scenes = cache.scenes,
                units = cache.units,
                assignments = cache.assignments.map { it.withoutExpressiveLayer() },
                sceneVoiceAssignments = emptyList()
            )
        )
        writeJson(identityCacheFile(request.identityCacheKey), identity)
        if (request.storyboardCapabilities.isNotEmpty()) {
            val expressive = StoryboardExpressiveCache(
                key = request.expressiveCacheKey,
                identityKey = request.identityCacheKey,
                capabilities = request.storyboardCapabilities,
                generatedAt = cache.generatedAt,
                scenes = emptyList(),
                units = emptyList(),
                assignments = cache.assignments.map { it.expressiveLayerOnly() },
                sceneVoiceAssignments = cache.sceneVoiceAssignments
            )
            writeJson(expressiveCacheFile(request.expressiveCacheKey), expressive)
        }
    }

    private fun loadLayeredCache(request: StoryboardRequest): StoryboardCache? {
        val identity = loadIdentityCache(request) ?: return null
        if (request.storyboardCapabilities.isEmpty()) {
            return composeLayeredCache(request, identity, null)
        }
        val expressive = readExpressiveCache(expressiveCacheFile(request.expressiveCacheKey))
            ?.takeIf {
                it.version == EXPRESSIVE_CACHE_VERSION &&
                    it.key == request.expressiveCacheKey &&
                    it.identityKey == request.identityCacheKey &&
                    it.capabilities == request.storyboardCapabilities
            }
            ?: return composeLayeredCache(request, identity, null)
        return composeLayeredCache(request, identity, expressive)
    }

    private fun loadIdentityCache(request: StoryboardRequest): StoryboardIdentityCache? {
        return readIdentityCache(identityCacheFile(request.identityCacheKey))
            ?.takeIf {
                it.version == IDENTITY_CACHE_VERSION &&
                    it.key == request.identityCacheKey &&
                    it.storyboard.contentHash == request.contentHash &&
                    it.storyboard.providerId == request.providerId &&
                    it.storyboard.modelId == request.modelId
            }
    }

    private fun composeLayeredCache(
        request: StoryboardRequest,
        identity: StoryboardIdentityCache,
        expressive: StoryboardExpressiveCache?
    ): StoryboardCache {
        val expressiveByUnit = expressive?.assignments.orEmpty().associateBy { it.unitId }
        val expressiveUnits = expressive?.units.orEmpty().associateBy { it.unitId }
        return identity.storyboard.copy(
            cacheVersion = CACHE_VERSION,
            key = request.cacheKey,
            identityCacheKey = request.identityCacheKey,
            expressiveCacheKey = request.expressiveCacheKey,
            bookUrl = request.book.bookUrl,
            bookName = request.book.name,
            bookAuthor = request.book.author,
            chapterIndex = request.chapterIndex,
            chapterTitle = request.chapterTitle,
            mode = request.mode,
            multiRoleEngineId = request.multiRoleEngineId,
            storyboardCapabilities = request.storyboardCapabilities,
            generatedAt = expressive?.generatedAt ?: identity.storyboard.generatedAt,
            scenes = expressive?.scenes?.takeIf { it.isNotEmpty() } ?: identity.storyboard.scenes,
            units = identity.storyboard.units.map { unit ->
                unit.copy(
                    sceneId = expressiveUnits[unit.unitId]?.sceneId?.takeIf { it.isNotBlank() }
                        ?: unit.sceneId
                )
            },
            assignments = identity.storyboard.assignments.map { base ->
                base.withExpressiveLayer(expressiveByUnit[base.unitId])
            },
            sceneVoiceAssignments = expressive?.sceneVoiceAssignments.orEmpty()
        )
    }

    private suspend fun persistPreparedStoryboard(
        request: StoryboardRequest,
        cacheFile: File,
        cache: StoryboardCache,
        storyboard: ChapterStoryboard
    ): StoryboardCache {
        val enriched = cache.withPreparedStoryboard(storyboard)
        if (enriched == cache) return cache
        cacheMutex.withLock {
            memoryCache[request.cacheKey] = MemoryCacheEntry(
                cache = enriched,
                expiresAt = System.currentTimeMillis() + MEMORY_CACHE_TTL
            )
            trimMemoryCache()
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(GSON.toJson(enriched), Charsets.UTF_8)
            persistLayeredCaches(request, enriched)
        }
        return enriched
    }

    private fun StoryboardCache.withPreparedStoryboard(
        storyboard: ChapterStoryboard
    ): StoryboardCache = copy(
        identityLinks = storyboard.identityLinks,
        sceneVoiceAssignments = storyboard.scenes.flatMap { scene ->
            scene.voiceAssignments.map { assignment ->
                CachedSceneVoiceAssignment(scene.index, assignment)
            }
        }
    )

    private fun mergeIdentityLayer(
        identity: StoryboardIdentityCache,
        generated: StoryboardCache
    ): StoryboardCache {
        val generatedByUnit = generated.assignments.associateBy { it.unitId }
        val identityIds = identity.storyboard.assignments.map { it.unitId }.toSet()
        val generatedIds = generated.assignments.map { it.unitId }.toSet()
        if (identityIds != generatedIds) return generated
        return generated.copy(
            identityCacheKey = identity.key,
            assignments = identity.storyboard.assignments.map { base ->
                base.withExpressiveLayer(generatedByUnit[base.unitId])
            }
        )
    }

    private fun ModelUnitResult.withoutExpressiveLayer() = copy(
        performanceContext = emptyList(),
        performanceInstruction = "",
        styleConcepts = emptyList(),
        emotion = null,
        emotionIntensity = null,
        expressiveConfidence = null
    )

    private fun ModelUnitResult.expressiveLayerOnly() = ModelUnitResult(
        unitId = unitId,
        performanceContext = performanceContext,
        performanceInstruction = performanceInstruction,
        styleConcepts = styleConcepts,
        emotion = emotion,
        emotionIntensity = emotionIntensity,
        expressiveConfidence = expressiveConfidence
    )

    private fun ModelUnitResult.withExpressiveLayer(expressive: ModelUnitResult?): ModelUnitResult = copy(
        performanceContext = expressive?.performanceContext.orEmpty(),
        performanceInstruction = expressive?.performanceInstruction.orEmpty(),
        styleConcepts = expressive?.styleConcepts.orEmpty(),
        emotion = expressive?.emotion,
        emotionIntensity = expressive?.emotionIntensity,
        expressiveConfidence = expressive?.expressiveConfidence
    )

    private fun deleteLayeredCaches(identityKey: String) {
        identityCacheFile(identityKey).delete()
        expressiveCacheDirectory().listFiles { file -> file.extension == "json" }
            .orEmpty()
            .filter { readExpressiveCache(it)?.identityKey == identityKey }
            .forEach(File::delete)
    }

    private fun deleteDerivedCombinedCaches(request: StoryboardRequest) {
        cacheDirectory().listFiles { file -> file.extension == "json" }
            .orEmpty()
            .filter { file ->
                val cache = readCache(file) ?: return@filter false
                cache.identityCacheKey == request.identityCacheKey ||
                    (cache.contentHash == request.contentHash &&
                        cache.bookUrl == request.book.bookUrl &&
                        cache.chapterIndex == request.chapterIndex &&
                        cache.providerId == request.providerId &&
                        cache.modelId == request.modelId)
            }
            .forEach(File::delete)
    }

    private fun readIdentityCache(file: File): StoryboardIdentityCache? = readJson(file)

    private fun readExpressiveCache(file: File): StoryboardExpressiveCache? = readJson(file)

    private inline fun <reified T> readJson(file: File): T? {
        if (!file.exists()) return null
        return runCatching { GSON.fromJson(file.readText(Charsets.UTF_8), T::class.java) }.getOrNull()
    }

    private fun writeJson(file: File, value: Any) {
        file.parentFile?.mkdirs()
        file.writeText(GSON.toJson(value), Charsets.UTF_8)
    }

    private fun loadCache(file: File, request: StoryboardRequest): StoryboardCache? {
        return readCache(file)?.takeIf {
            it.cacheVersion == CACHE_VERSION &&
                it.key == request.cacheKey &&
                it.contentHash == request.contentHash &&
                it.mode == request.mode
        }
    }

    private fun loadCompatibleCache(request: StoryboardRequest): StoryboardCache? {
        val legacyKeys = cacheIdentityCandidates(
            book = request.book,
            chapterIndex = request.chapterIndex,
            chapterTitle = request.chapterTitle,
            contentHash = request.contentHash,
            mode = request.mode,
            capabilities = request.storyboardCapabilities,
            providerId = request.providerId,
            modelId = request.modelId,
            charactersHashes = listOf(request.charactersHash, charactersHash(emptyList()))
        )
        return cacheDirectory().listFiles { file -> file.extension == "json" }
            .orEmpty()
            .asSequence()
            .mapNotNull(::readCache)
            .filter { cache ->
                cache.cacheVersion == CACHE_VERSION &&
                    cache.contentHash == request.contentHash &&
                    cache.chapterTitle == request.chapterTitle &&
                    cache.mode == request.mode &&
                    cache.storyboardCapabilities == request.storyboardCapabilities &&
                    cache.providerId == request.providerId &&
                    cache.modelId == request.modelId &&
                    (cache.bookUrl.isBlank() || cache.bookUrl == request.book.bookUrl) &&
                    (cache.chapterIndex < 0 || cache.chapterIndex == request.chapterIndex) &&
                    (cache.bookUrl == request.book.bookUrl || cache.key in legacyKeys)
            }
            .maxByOrNull { it.generatedAt }
    }

    private fun persistRequestIdentity(
        cache: StoryboardCache,
        request: StoryboardRequest,
        targetFile: File
    ): StoryboardCache {
        val normalized = cache.copy(
            key = request.cacheKey,
            identityCacheKey = request.identityCacheKey,
            expressiveCacheKey = request.expressiveCacheKey,
            bookUrl = request.book.bookUrl,
            bookName = request.book.name,
            bookAuthor = request.book.author,
            chapterIndex = request.chapterIndex,
            identityLinks = mergeIdentityLinks(cache.identityLinks, request.identityLinks)
        )
        if (normalized != cache || !targetFile.exists()) {
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(GSON.toJson(normalized), Charsets.UTF_8)
        }
        return normalized
    }

    private fun readCache(file: File): StoryboardCache? {
        if (!file.exists()) return null
        return runCatching {
            GSON.fromJson(file.readText(Charsets.UTF_8), StoryboardCache::class.java)
        }.getOrNull()
    }

    private fun loadMemoryCache(request: StoryboardRequest): StoryboardCache? {
        val now = System.currentTimeMillis()
        return memoryCache[request.cacheKey]
            ?.takeIf { it.expiresAt > now }
            ?.cache
            ?.takeIf {
                it.cacheVersion == CACHE_VERSION &&
                    it.key == request.cacheKey &&
                    it.contentHash == request.contentHash &&
                    it.mode == request.mode
            }
    }

    private fun trimMemoryCache() {
        val now = System.currentTimeMillis()
        val iterator = memoryCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expiresAt <= now || memoryCache.size > 8) {
                iterator.remove()
            }
        }
    }

    private fun cacheFile(request: StoryboardRequest): File {
        return File(cacheDirectory(), "${request.cacheKey}.json")
    }

    private fun identityCacheFile(key: String): File = File(identityCacheDirectory(), "$key.json")

    private fun expressiveCacheFile(key: String): File = File(expressiveCacheDirectory(), "$key.json")

    private fun identityCacheDirectory(): File = File(cacheDirectory(), "identity")

    private fun expressiveCacheDirectory(): File = File(cacheDirectory(), "expressive")

    private fun cacheDirectory(): File {
        return File(appCtx.cacheDir, CACHE_DIR)
    }

    private fun charactersHash(characters: List<BookCharacter>): String {
        return MD5Utils.md5Encode(
            characters
                .sortedBy { it.id }
                .joinToString("\n") { character ->
                    listOf(
                        character.id,
                        character.name,
                        character.aliasesJson.orEmpty(),
                        character.gender,
                        character.roleTag
                    ).joinToString("|")
                }
        )
    }

    private fun storyboardCacheKey(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        contentHash: String,
        mode: String,
        capabilities: List<String>,
        providerId: String,
        modelId: String
    ): String {
        val capabilityIdentity = TtsCapabilityRegistry.versioned(capabilities)
        return MD5Utils.md5Encode(
            listOf(
                CACHE_VERSION,
                book.bookUrl,
                book.name,
                book.author,
                chapterIndex,
                chapterTitle,
                contentHash,
                mode,
                capabilityIdentity.joinToString(","),
                providerId,
                modelId
            ).joinToString("\u0000")
        )
    }

    private fun storyboardIdentityCacheKey(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        contentHash: String,
        providerId: String,
        modelId: String
    ): String = MD5Utils.md5Encode(
        listOf(
            IDENTITY_CACHE_VERSION,
            book.bookUrl,
            book.name,
            book.author,
            chapterIndex,
            chapterTitle,
            contentHash,
            providerId,
            modelId
        ).joinToString("\u0000")
    )

    private fun storyboardExpressiveCacheKey(
        identityCacheKey: String,
        capabilities: List<String>
    ): String = MD5Utils.md5Encode(
        listOf(
            EXPRESSIVE_CACHE_VERSION,
            identityCacheKey,
            TtsCapabilityRegistry.versioned(capabilities).joinToString(",")
        ).joinToString("\u0000")
    )

    private fun cacheIdentityCandidates(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        contentHash: String,
        mode: String,
        capabilities: List<String>,
        providerId: String,
        modelId: String,
        charactersHashes: List<String>
    ): Set<String> {
        val stableKey = storyboardCacheKey(
            book,
            chapterIndex,
            chapterTitle,
            contentHash,
            mode,
            capabilities,
            providerId,
            modelId
        )
        val legacyKeys = charactersHashes.distinct().map { charactersHash ->
            val capabilityIdentity = TtsCapabilityRegistry.versioned(capabilities)
            MD5Utils.md5Encode(
                listOf(
                    CACHE_VERSION,
                    book.bookUrl,
                    book.name,
                    book.author,
                    chapterIndex,
                    chapterTitle,
                    contentHash,
                    charactersHash,
                    mode,
                    capabilityIdentity.joinToString(","),
                    providerId,
                    modelId
                ).joinToString("\u0000")
            )
        }
        return (legacyKeys + stableKey).toSet()
    }

    internal fun storyboardCacheKeyForTest(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        contentHash: String,
        mode: String,
        capabilities: List<String>,
        providerId: String,
        modelId: String
    ): String = storyboardCacheKey(
        book,
        chapterIndex,
        chapterTitle,
        contentHash,
        mode,
        capabilities,
        providerId,
        modelId
    )

    internal fun storyboardIdentityCacheKeyForTest(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        contentHash: String,
        providerId: String,
        modelId: String
    ): String = storyboardIdentityCacheKey(
        book,
        chapterIndex,
        chapterTitle,
        contentHash,
        providerId,
        modelId
    )

    internal fun storyboardExpressiveCacheKeyForTest(
        identityCacheKey: String,
        capabilities: List<String>
    ): String = storyboardExpressiveCacheKey(identityCacheKey, capabilities)

    internal fun resolveStoryboardSkillCapabilities(
        declaredCapabilities: Set<String>
    ): List<String> {
        val normalized = TtsCapabilityRegistry.normalize(declaredCapabilities)
        return listOf(
            TtsEngineCapability.SCENE_CONTEXT,
            TtsEngineCapability.PERFORMANCE_INSTRUCTION,
            TtsEngineCapability.STYLE_TAGS,
            TtsEngineCapability.EMOTION,
            TtsEngineCapability.EMOTION_INTENSITY
        ).filter { it in normalized }
    }

    internal fun storyboardSkillAssets(
        capabilities: List<String>
    ): List<String> {
        return buildList {
            add(PROTOCOL_SKILL_ASSET)
            add(BASE_ROUTING_SKILL_ASSET)
            if (TtsEngineCapability.SCENE_CONTEXT in capabilities) {
                add(SCENE_CONTEXT_SKILL_ASSET)
            }
            if (TtsEngineCapability.PERFORMANCE_INSTRUCTION in capabilities) {
                add(PERFORMANCE_INSTRUCTION_SKILL_ASSET)
            }
            if (TtsEngineCapability.STYLE_TAGS in capabilities) add(STYLE_TAGS_SKILL_ASSET)
            if (TtsEngineCapability.EMOTION in capabilities) add(EMOTION_SKILL_ASSET)
        }
    }

    private fun readSkillPrompt(capabilities: List<String>): String {
        return storyboardSkillAssets(capabilities)
            .joinToString("\n\n---\n\n") { readAssetPrompt(it) }
    }

    private fun readAssetPrompt(asset: String): String {
        val raw = appCtx.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!raw.startsWith("---")) return raw.trim()
        val end = Regex("\\r?\\n---(?:\\r?\\n|$)").find(raw, startIndex = 3) ?: return raw.trim()
        return raw.substring(end.range.last + 1).trim()
    }

    private fun normalizeModelOutput(text: String): String {
        var output = text.trim()
        if (output.startsWith("```")) {
            output = output.lines()
                .drop(1)
                .dropLastWhile { it.trim() == "```" }
                .joinToString("\n")
                .trim()
        }
        return output
    }

    private fun String.extractJsonObjectCandidate(): String {
        val start = indexOf('{')
        if (start < 0) return ""
        val source = substring(start).trim()
        var inString = false
        var escaped = false
        var depth = 0
        source.forEachIndexed { index, char ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            when {
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> depth++
                !inString && char == '}' -> {
                    depth--
                    if (depth == 0) return source.substring(0, index + 1)
                }
            }
        }
        return source
    }

    private fun findTextLeaks(value: JsonElement, path: String = "root"): List<String> {
        val leaks = arrayListOf<String>()
        if (value.isJsonObject) {
            value.asJsonObject.entrySet().forEach { (key, child) ->
                val childPath = "$path.$key"
                if (key in textLeakKeys) leaks += childPath
                leaks += findTextLeaks(child, childPath)
            }
        } else if (value.isJsonArray) {
            value.asJsonArray.forEachIndexed { index, child ->
                leaks += findTextLeaks(child, "$path[$index]")
            }
        }
        return leaks
    }

    private fun AiTextResult.emptyContentMessage(): String {
        val reasoning = reasoning.orEmpty().trim()
        return if (reasoning.isNotBlank()) {
            if (finishReason == "length") {
                "AI 仅返回思考过程，未返回分镜。请调低或关闭思考深度后重试"
            } else {
                "AI 仅返回思考过程，未返回分镜"
            }
        } else {
            "AI 返回空内容"
        }
    }

    private fun AiModelSelection.supportsReasoning(): Boolean {
        return AiProviderStore.provider(providerId)
            ?.models
            ?.firstOrNull { it.id == modelId }
            ?.abilities
            ?.contains(AiModelAbility.REASONING) == true
    }

    private data class UnitSpan(
        val start: Int,
        val end: Int,
        val kind: String,
        val roleHint: String
    )

    private data class StoryboardRequest(
        val book: Book,
        val chapterIndex: Int,
        val chapterTitle: String,
        val contentHash: String,
        val cacheKey: String,
        val identityCacheKey: String,
        val expressiveCacheKey: String,
        val mode: String,
        val providerId: String,
        val modelId: String,
        val charactersHash: String,
        val multiRoleEngineId: String,
        val storyboardCapabilities: List<String>,
        val paragraphs: List<ContextParagraph>,
        val characters: List<BookCharacter>,
        val castRoles: List<BookTtsCastRole>,
        val units: List<CandidateUnit>,
        val identityLinks: List<StoryboardIdentityLink> = emptyList(),
        val scenes: List<SceneRange> = emptyList(),
        val combineSceneAndRouting: Boolean = false
    )

    private data class PreparedModelResult(
        val request: StoryboardRequest,
        val assignments: List<ModelUnitResult>
    )

    private data class GenerateResult(
        val cache: StoryboardCache,
        val cacheable: Boolean
    )

    private data class MemoryCacheEntry(
        val cache: StoryboardCache,
        val expiresAt: Long
    )

    data class CachedStoryboardEntry(
        val chapterIndex: Int,
        val chapterTitle: String,
        val generatedAt: Long,
        val storyboard: ChapterStoryboard,
        val cacheKeys: Set<String> = emptySet()
    )

    data class StoryboardCache(
        @SerializedName("cacheVersion")
        val cacheVersion: Int = CACHE_VERSION,
        @SerializedName("key")
        val key: String = "",
        @SerializedName("providerId")
        val providerId: String = "",
        @SerializedName("modelId")
        val modelId: String = "",
        @SerializedName("contentHash")
        val contentHash: String = "",
        @SerializedName("identityCacheKey")
        val identityCacheKey: String = "",
        @SerializedName("expressiveCacheKey")
        val expressiveCacheKey: String = "",
        @SerializedName("generatedAt")
        val generatedAt: Long = 0L,
        @SerializedName("bookUrl")
        val bookUrl: String = "",
        @SerializedName("bookName")
        val bookName: String = "",
        @SerializedName("bookAuthor")
        val bookAuthor: String = "",
        @SerializedName("chapterIndex")
        val chapterIndex: Int = -1,
        @SerializedName("chapterTitle")
        val chapterTitle: String = "",
        @SerializedName("mode")
        val mode: String = StoryboardMode.BASIC,
        @SerializedName("multiRoleEngineId")
        val multiRoleEngineId: String = "",
        @SerializedName("storyboardCapabilities")
        val storyboardCapabilities: List<String> = emptyList(),
        @SerializedName("paragraphs")
        val paragraphs: List<ContextParagraph> = emptyList(),
        @SerializedName("scenes")
        val scenes: List<SceneRange> = emptyList(),
        @SerializedName("units")
        val units: List<CandidateUnit> = emptyList(),
        @SerializedName("assignments")
        val assignments: List<ModelUnitResult> = emptyList(),
        @SerializedName("identityLinks")
        val identityLinks: List<StoryboardIdentityLink> = emptyList(),
        @SerializedName("sceneVoiceAssignments")
        val sceneVoiceAssignments: List<CachedSceneVoiceAssignment> = emptyList()
    )

    private data class StoryboardIdentityCache(
        @SerializedName("version")
        val version: Int = IDENTITY_CACHE_VERSION,
        @SerializedName("key")
        val key: String = "",
        @SerializedName("storyboard")
        val storyboard: StoryboardCache = StoryboardCache()
    )

    private data class StoryboardExpressiveCache(
        @SerializedName("version")
        val version: Int = EXPRESSIVE_CACHE_VERSION,
        @SerializedName("key")
        val key: String = "",
        @SerializedName("identityKey")
        val identityKey: String = "",
        @SerializedName("capabilities")
        val capabilities: List<String> = emptyList(),
        @SerializedName("generatedAt")
        val generatedAt: Long = 0L,
        @SerializedName("scenes")
        val scenes: List<SceneRange> = emptyList(),
        @SerializedName("units")
        val units: List<CandidateUnit> = emptyList(),
        @SerializedName("assignments")
        val assignments: List<ModelUnitResult> = emptyList(),
        @SerializedName("sceneVoiceAssignments")
        val sceneVoiceAssignments: List<CachedSceneVoiceAssignment> = emptyList()
    )

    data class CachedSceneVoiceAssignment(
        @SerializedName("sceneIndex")
        val sceneIndex: Int = 0,
        @SerializedName("assignment")
        val assignment: StoryboardSceneVoiceAssignment = StoryboardSceneVoiceAssignment(
            engineId = "",
            targetType = "",
            targetId = 0L,
            decision = "unassigned"
        )
    )

    data class ContextParagraph(
        @SerializedName("paragraphIndex")
        val paragraphIndex: Int = 0,
        @SerializedName("text")
        val text: String = ""
    )

    data class CandidateUnit(
        @SerializedName("unitId")
        val unitId: String = "",
        @SerializedName("sceneId")
        val sceneId: String = "",
        @SerializedName("kind")
        val kind: String = "",
        @SerializedName("roleHint")
        val roleHint: String = "",
        @SerializedName("ranges")
        val ranges: List<TextRange> = emptyList(),
        @SerializedName("textPreview")
        val textPreview: String = "",
        @SerializedName("cueBefore")
        val cueBefore: String = "",
        @SerializedName("cueAfter")
        val cueAfter: String = ""
    )

    data class TextRange(
        @SerializedName("paragraphIndex")
        val paragraphIndex: Int = 0,
        @SerializedName("start")
        val start: Int = 0,
        @SerializedName("end")
        val end: Int = 0
    )

    private data class StoryboardPayload(
        @SerializedName("book")
        val book: PayloadBook,
        @SerializedName("chapter")
        val chapter: PayloadChapter,
        @SerializedName("mode")
        val mode: String,
        @SerializedName("storyboardCapabilities")
        val storyboardCapabilities: List<String>,
        @SerializedName("allowNewCharacters")
        val allowNewCharacters: Boolean,
        @SerializedName("knownCharacters")
        val knownCharacters: List<KnownCharacter>,
        @SerializedName("knownCastRoles")
        val knownCastRoles: List<KnownCastRole>,
        @SerializedName("contextParagraphs")
        val contextParagraphs: List<ContextParagraph>,
        @SerializedName("scenes")
        val scenes: List<SceneRange>,
        @SerializedName("units")
        val units: List<CandidateUnit>,
        @SerializedName("targetUnitIds")
        val targetUnitIds: List<String>
    )

    private data class PayloadBook(
        @SerializedName("name")
        val name: String,
        @SerializedName("author")
        val author: String
    )

    private data class PayloadChapter(
        @SerializedName("index")
        val index: Int,
        @SerializedName("title")
        val title: String
    )

    private data class ScenePayload(
        @SerializedName("chapter")
        val chapter: PayloadChapter,
        @SerializedName("paragraphCount")
        val paragraphCount: Int,
        @SerializedName("firstParagraphIndex")
        val firstParagraphIndex: Int,
        @SerializedName("lastParagraphIndex")
        val lastParagraphIndex: Int,
        @SerializedName("contextParagraphs")
        val contextParagraphs: List<ContextParagraph>
    )

    private data class SceneOutput(
        @SerializedName("scenes")
        val scenes: List<SceneRange> = emptyList()
    )

    data class SceneRange(
        @SerializedName("sceneId")
        val sceneId: String = "",
        @SerializedName("title")
        val title: String = "",
        @SerializedName("startParagraphIndex")
        val startParagraphIndex: Int = 0,
        @SerializedName("endParagraphIndex")
        val endParagraphIndex: Int = 0
    )

    private data class KnownCharacter(
        @SerializedName("characterId")
        val characterId: Long,
        @SerializedName("name")
        val name: String,
        @SerializedName("aliases")
        val aliases: List<String>,
        @SerializedName("gender")
        val gender: String,
        @SerializedName("role")
        val role: String
    )

    private data class KnownCastRole(
        @SerializedName("castRoleId")
        val castRoleId: Long,
        @SerializedName("name")
        val name: String,
        @SerializedName("aliases")
        val aliases: List<String>,
        @SerializedName("gender")
        val gender: String,
        @SerializedName("identityState")
        val identityState: String,
        @SerializedName("nameType")
        val nameType: String,
        @SerializedName("identityEvidence")
        val identityEvidence: String,
        @SerializedName("genderEvidence")
        val genderEvidence: String,
        @SerializedName("chapterRange")
        val chapterRange: String,
        @SerializedName("occurrenceCount")
        val occurrenceCount: Int,
        @SerializedName("representativeTexts")
        val representativeTexts: List<String>,
        @SerializedName("evidence")
        val evidence: List<String>
    )

    private data class KnownSpeakerIndex(
        val charactersById: Map<Long, KnownCharacter>,
        val charactersByName: Map<String, KnownCharacter>,
        val castRolesById: Map<Long, KnownCastRole>,
        val castRolesByName: Map<String, KnownCastRole>
    )

    private data class IdentityTarget(
        val name: String,
        val characterId: Long?,
        val castRoleId: Long?
    )

    private data class StoryboardModelOutput(
        @SerializedName("units")
        val units: List<ModelUnitResult> = emptyList(),
        @SerializedName("newCharacters")
        val newCharacters: List<JsonObject> = emptyList()
    )

    data class ModelUnitResult(
        @SerializedName("unitId")
        val unitId: String = "",
        @SerializedName("roleType")
        val roleType: String = "",
        @SerializedName("characterName")
        val characterName: String = "",
        @SerializedName("characterId")
        val characterId: Long = 0L,
        @SerializedName("castRoleId")
        val castRoleId: Long = 0L,
        @SerializedName("speakerGender")
        val speakerGender: String = StoryboardSegment.SpeakerGender.UNKNOWN,
        @SerializedName("identityType")
        val identityType: String = StoryboardSegment.IdentityType.NONE,
        @SerializedName("nameType")
        val nameType: String = StoryboardSegment.NameType.UNKNOWN,
        @SerializedName("identityEvidence")
        val identityEvidence: String = StoryboardSegment.Evidence.UNKNOWN,
        @SerializedName("genderEvidence")
        val genderEvidence: String = StoryboardSegment.Evidence.UNKNOWN,
        @SerializedName("mergeCastRoleIds")
        val mergeCastRoleIds: List<Long> = emptyList(),
        @SerializedName("status")
        val status: String = "",
        @SerializedName("confidence")
        val confidence: Float = 0f,
        @SerializedName("evidence")
        val evidence: String = "",
        @SerializedName("performanceContext")
        val performanceContext: List<String> = emptyList(),
        @SerializedName("performanceInstruction")
        val performanceInstruction: String = "",
        @SerializedName("styleConcepts")
        val styleConcepts: List<String> = emptyList(),
        @SerializedName("emotion")
        val emotion: String? = null,
        @SerializedName("emotionIntensity")
        val emotionIntensity: Float? = null,
        @SerializedName("expressiveConfidence")
        val expressiveConfidence: Float? = null
    )
}
