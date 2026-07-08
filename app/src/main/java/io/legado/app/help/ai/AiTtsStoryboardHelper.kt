package io.legado.app.help.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

object AiTtsStoryboardHelper {

    private const val SKILL_ASSET = "skills/tts_storyboard.md"
    private const val CACHE_DIR = "ai_tts_storyboard"
    private const val CACHE_VERSION = 5
    private const val MEMORY_CACHE_TTL = 5 * 60 * 1000L
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
    private val colonDialogueCues = listOf(
        "说", "说道", "问", "问道", "喊", "喊道", "叫", "叫道", "道", "开口",
        "吐槽", "坦言", "回答", "答道", "回道", "回复", "说了句", "喊上一句", "补了一句"
    )
    private val textLeakKeys = setOf(
        "text", "input", "content", "sourceText", "source_text", "output", "ranges", "start", "end"
    )
    private val unitKeys = setOf(
        "unitId", "roleType", "characterName", "characterId", "speakerGender",
        "status", "confidence", "evidence"
    )
    private val rootKeys = setOf("units", "newCharacters")
    private val roleTypes = setOf("narrator", "character", "thought", "other")
    private val statuses = setOf("assigned", "unknown")
    private val speakerGenders = setOf(
        StoryboardSegment.SpeakerGender.MALE,
        StoryboardSegment.SpeakerGender.FEMALE,
        StoryboardSegment.SpeakerGender.UNKNOWN
    )
    private val cacheMutex = Mutex()
    private val memoryCache = linkedMapOf<String, MemoryCacheEntry>()
    private val inFlightRequests = hashMapOf<String, CompletableDeferred<GenerateResult>>()

    suspend fun getOrGenerate(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        content: String,
        characters: List<BookCharacter>
    ): ChapterStoryboard {
        val request = buildRequest(book, chapterIndex, chapterTitle, content, characters)
        val cacheFile = cacheFile(request)
        var owner = false
        val pending = cacheMutex.withLock {
            loadMemoryCache(request)?.let {
                return it.toChapterStoryboard()
            }
            loadCache(cacheFile, request)?.let {
                return it.toChapterStoryboard()
            }
            inFlightRequests[request.cacheKey] ?: CompletableDeferred<GenerateResult>().also {
                inFlightRequests[request.cacheKey] = it
                owner = true
            }
        }
        if (!owner) {
            return pending.await().cache.toChapterStoryboard()
        }
        try {
            val result = generate(request)
            cacheMutex.withLock {
                if (result.cacheable) {
                    memoryCache[request.cacheKey] = MemoryCacheEntry(
                        cache = result.cache,
                        expiresAt = System.currentTimeMillis() + MEMORY_CACHE_TTL
                    )
                    trimMemoryCache()
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(GSON.toJson(result.cache), Charsets.UTF_8)
                }
            }
            pending.complete(result)
            return result.cache.toChapterStoryboard()
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
        return loadCache(cacheFile(request), request)?.toChapterStoryboard()
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
        return chapter.getNeedReadAloud(0, pageSplit, 0)
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
                "generated_at" to cache?.generatedAt,
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
                    "evidence" to it.evidence.limitDebugText(textCharLimit)
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
                            "evidence" to segment.evidence.limitDebugText(textCharLimit)
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

    private suspend fun generate(request: StoryboardRequest): GenerateResult {
        val selection = AiConfig.requireReadAloudStoryboardModel()
        val supportsReasoning = selection.supportsReasoning()
        val systemPrompt = readSkillPrompt()
        val result = runCatching {
            requestModelUnits(
                request = request,
                selection = selection,
                systemPrompt = systemPrompt,
                supportsReasoning = supportsReasoning,
                targetUnits = request.units
            )
        }
        val assignments = result.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            io.legado.app.constant.AppLog.put(
                "AI听书分镜整章请求失败，已临时回退旁白\n${error.localizedMessage}",
                error
            )
            request.units.map { it.fallbackAssignment(error, "整章请求") }
        }
        return GenerateResult(
            cache = StoryboardCache(
                cacheVersion = CACHE_VERSION,
                key = request.cacheKey,
                providerId = selection.providerId,
                modelId = selection.modelId,
                contentHash = request.contentHash,
                generatedAt = System.currentTimeMillis(),
                chapterTitle = request.chapterTitle,
                paragraphs = request.paragraphs,
                units = request.units,
                assignments = assignments
            ),
            cacheable = result.isSuccess
        )
    }

    private suspend fun requestModelUnits(
        request: StoryboardRequest,
        selection: AiModelSelection,
        systemPrompt: String,
        supportsReasoning: Boolean,
        targetUnits: List<CandidateUnit>
    ): List<ModelUnitResult> {
        if (targetUnits.isEmpty()) return emptyList()
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
        return parseAndValidate(
            raw = result.content,
            targetUnits = targetUnits,
            allowNewCharacters = false,
            knownCharacters = request.characters.map { it.toKnownCharacter() }
        )
    }

    private fun CandidateUnit.fallbackAssignment(error: Throwable, chunkLabel: String): ModelUnitResult {
        val message = error.localizedMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { "：${it.take(120)}" }
            .orEmpty()
        return ModelUnitResult(
            unitId = unitId,
            roleType = "narrator",
            characterName = "",
            characterId = 0L,
            speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
            status = "unknown",
            confidence = 0f,
            evidence = "$chunkLabel 请求失败，回退旁白$message"
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
        val enabledCharacters = characters.filter { it.enabled && it.name.isNotBlank() }
        val charactersHash = MD5Utils.md5Encode(
            enabledCharacters
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
        val selection = runCatching { AiConfig.requireReadAloudStoryboardModel() }.getOrNull()
        val cacheKey = MD5Utils.md5Encode(
            listOf(
                CACHE_VERSION,
                book.bookUrl,
                book.name,
                book.author,
                chapterIndex,
                chapterTitle,
                contentHash,
                charactersHash,
                selection?.providerId.orEmpty(),
                selection?.modelId.orEmpty()
            ).joinToString("\u0000")
        )
        return StoryboardRequest(
            book = book,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            contentHash = contentHash,
            cacheKey = cacheKey,
            paragraphs = paragraphs,
            characters = enabledCharacters,
            units = buildCandidateUnits(paragraphs)
        )
    }

    private fun buildCandidateUnits(paragraphs: List<ContextParagraph>): List<CandidateUnit> {
        val texts = paragraphs.associate { it.paragraphIndex to it.text }
        val units = arrayListOf<CandidateUnit>()
        paragraphs.forEach { paragraph ->
            val text = paragraph.text
            val quoteSpans = findQuoteSpans(text)
            quoteSpans.forEach { span ->
                val preview = text.substring(span.start, span.end)
                units += makeUnit(
                    kind = span.kind,
                    roleHint = if (looksLikeThought(text, span.start, span.end)) "thought" else "character",
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
            allowNewCharacters = false,
            knownCharacters = characters.map { it.toKnownCharacter() },
            contextParagraphs = paragraphs,
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

    private fun parseAndValidate(
        raw: String,
        targetUnits: List<CandidateUnit>,
        allowNewCharacters: Boolean,
        knownCharacters: List<KnownCharacter> = emptyList()
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
        val knownIndex = knownCharacterIndex(knownCharacters)
        return output.units.mapIndexed { index, unit ->
            val item = unitsElement.asJsonArray[index].asJsonObject
            val extraKeys = item.keySet() - unitKeys
            check(extraKeys.isEmpty()) { "AI 返回 unit 额外字段：${extraKeys.joinToString()}" }
            check(unit.roleType in roleTypes) { "AI 返回非法 roleType：${unit.roleType}" }
            check(unit.status in statuses) { "AI 返回非法 status：${unit.status}" }
            check(unit.speakerGender in speakerGenders) { "AI 返回非法 speakerGender：${unit.speakerGender}" }
            check(unit.confidence in 0f..1f) { "AI 返回非法 confidence：${unit.confidence}" }
            normalizeModelUnit(unit, knownIndex)
        }
    }

    private fun knownCharacterIndex(knownCharacters: List<KnownCharacter>): KnownCharacterIndex {
        val byId = knownCharacters
            .filter { it.characterId > 0L }
            .associateBy { it.characterId }
        val byName = buildMap {
            knownCharacters.forEach { character ->
                val names = buildList {
                    add(character.name)
                    addAll(character.aliases)
                }
                names.forEach { name ->
                    val key = name.trim()
                    if (key.isNotBlank()) {
                        put(key, character)
                    }
                }
            }
        }
        return KnownCharacterIndex(byId, byName)
    }

    private fun normalizeModelUnit(
        unit: ModelUnitResult,
        knownIndex: KnownCharacterIndex
    ): ModelUnitResult {
        if (unit.roleType == "narrator" || unit.roleType == "other") {
            return unit.copy(
                characterName = "",
                characterId = 0L,
                speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
                status = "unknown"
            )
        }
        val modelDisplayName = unit.characterName.trim()
        val known = knownIndex.byId[unit.characterId]
            ?: modelDisplayName.takeIf { it.isNotBlank() }?.let { knownIndex.byName[it] }
        if (known != null) {
            return unit.copy(
                characterName = known.name,
                characterId = known.characterId,
                speakerGender = known.gender.takeIf { it in speakerGenders && it != StoryboardSegment.SpeakerGender.UNKNOWN }
                    ?: unit.speakerGender,
                status = "assigned"
            )
        }
        val genderFallback = unit.speakerGender == StoryboardSegment.SpeakerGender.MALE ||
            unit.speakerGender == StoryboardSegment.SpeakerGender.FEMALE
        if (genderFallback) {
            return unit.copy(
                characterName = modelDisplayName,
                characterId = 0L,
                status = "unknown"
            )
        }
        return unit.copy(
            roleType = "narrator",
            characterName = "",
            characterId = 0L,
            speakerGender = StoryboardSegment.SpeakerGender.UNKNOWN,
            status = "unknown"
        )
    }

    private fun StoryboardCache.toChapterStoryboard(): ChapterStoryboard {
        val assignmentMap = assignments.associateBy { it.unitId }
        val unitMap = units.associateBy { it.unitId }
        val paragraphSegments = paragraphs.associate { paragraph ->
            paragraph.paragraphIndex to buildSegmentsForParagraph(paragraph, unitMap, assignmentMap)
        }
        val scenes = buildScenes(paragraphs, paragraphSegments)
        return ChapterStoryboard(chapterTitle = chapterTitle, scenes = scenes)
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
            val roleType = assignment?.roleType
            val assignedOrGenderFallback = assignment?.status == "assigned" ||
                assignment?.speakerGender == StoryboardSegment.SpeakerGender.MALE ||
                assignment?.speakerGender == StoryboardSegment.SpeakerGender.FEMALE
            val type = when {
                assignedOrGenderFallback && roleType == "character" ->
                    StoryboardSegmentType.DIALOGUE
                assignedOrGenderFallback && roleType == "thought" ->
                    StoryboardSegmentType.THOUGHT
                else -> StoryboardSegmentType.NARRATION
            }
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
                speakerGender = if (type == StoryboardSegmentType.NARRATION) {
                    StoryboardSegment.SpeakerGender.UNKNOWN
                } else {
                    assignment?.speakerGender ?: StoryboardSegment.SpeakerGender.UNKNOWN
                },
                start = range.start,
                end = range.end
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
                last.speakerName == segment.speakerName &&
                last.speakerGender == segment.speakerGender &&
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
        paragraphSegments: Map<Int, List<StoryboardSegment>>
    ): List<StoryboardScene> {
        val groups = arrayListOf<MutableList<ContextParagraph>>()
        var current = arrayListOf<ContextParagraph>()
        var currentLength = 0
        paragraphs.forEach { paragraph ->
            if (current.isNotEmpty() && (current.size >= 8 || currentLength + paragraph.text.length > 900)) {
                groups += current
                current = arrayListOf()
                currentLength = 0
            }
            current += paragraph
            currentLength += paragraph.text.length
        }
        if (current.isNotEmpty()) groups += current
        return groups.mapIndexed { index, group ->
            val segments = group.flatMap { paragraphSegments[it.paragraphIndex].orEmpty() }
            val names = segments
                .mapNotNull { it.speakerName ?: it.virtualSpeakerName() }
                .distinct()
            val summary = segments.firstOrNull { it.type == StoryboardSegmentType.NARRATION }?.text
                ?: group.firstOrNull()?.text.orEmpty()
            StoryboardScene(
                index = index + 1,
                title = if (names.isNotEmpty()) {
                    "分镜${index + 1} · ${names.take(3).joinToString("、")}"
                } else {
                    "分镜${index + 1} · ${group.firstOrNull()?.text.orEmpty().take(28)}"
                },
                summary = summary.replace(Regex("\\s+"), " ").trim().take(64),
                characters = names,
                segments = segments
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

    private fun loadCache(file: File, request: StoryboardRequest): StoryboardCache? {
        if (!file.exists()) return null
        return runCatching {
            val cache = GSON.fromJson(file.readText(Charsets.UTF_8), StoryboardCache::class.java)
            cache.takeIf {
                it.cacheVersion == CACHE_VERSION &&
                    it.key == request.cacheKey &&
                    it.contentHash == request.contentHash
            }
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
                    it.contentHash == request.contentHash
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
        return File(File(appCtx.cacheDir, CACHE_DIR), "${request.cacheKey}.json")
    }

    private fun readSkillPrompt(): String {
        val raw = appCtx.assets.open(SKILL_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
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
        val paragraphs: List<ContextParagraph>,
        val characters: List<BookCharacter>,
        val units: List<CandidateUnit>
    )

    private data class GenerateResult(
        val cache: StoryboardCache,
        val cacheable: Boolean
    )

    private data class MemoryCacheEntry(
        val cache: StoryboardCache,
        val expiresAt: Long
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
        @SerializedName("generatedAt")
        val generatedAt: Long = 0L,
        @SerializedName("chapterTitle")
        val chapterTitle: String = "",
        @SerializedName("paragraphs")
        val paragraphs: List<ContextParagraph> = emptyList(),
        @SerializedName("units")
        val units: List<CandidateUnit> = emptyList(),
        @SerializedName("assignments")
        val assignments: List<ModelUnitResult> = emptyList()
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
        @SerializedName("allowNewCharacters")
        val allowNewCharacters: Boolean,
        @SerializedName("knownCharacters")
        val knownCharacters: List<KnownCharacter>,
        @SerializedName("contextParagraphs")
        val contextParagraphs: List<ContextParagraph>,
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

    private data class KnownCharacterIndex(
        val byId: Map<Long, KnownCharacter>,
        val byName: Map<String, KnownCharacter>
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
        @SerializedName("speakerGender")
        val speakerGender: String = StoryboardSegment.SpeakerGender.UNKNOWN,
        @SerializedName("status")
        val status: String = "",
        @SerializedName("confidence")
        val confidence: Float = 0f,
        @SerializedName("evidence")
        val evidence: String = ""
    )
}
