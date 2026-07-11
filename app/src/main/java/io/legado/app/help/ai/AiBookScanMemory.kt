package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.data.appDb
import io.legado.app.data.entities.AgentMemory
import io.legado.app.utils.GSON
import java.security.MessageDigest

internal data class AiBookScanReadEvidence(
    val workKey: String,
    val subject: String,
    val fullyReadChapterIndexes: Set<Int>
)

internal data class AiBookScanSaveResult(
    val subject: String,
    val savedChapterCount: Int,
    val coveredChapterCount: Int,
    val totalChapterCount: Int,
    val eventCount: Int,
    val warnings: List<String>
)

internal data class AiBookScanDelta(
    @SerializedName("schema_version")
    val schemaVersion: Int = 0,
    @SerializedName("work_key")
    val workKey: String = "",
    @SerializedName("book_name")
    val bookName: String = "",
    @SerializedName("author")
    val author: String = "",
    @SerializedName("total_chapters")
    val totalChapters: Int = 0,
    @SerializedName("book_status")
    val bookStatus: String = "unknown",
    @SerializedName("scan_stage")
    val scanStage: String = "",
    @SerializedName("observed_chapters")
    val observedChapters: List<Int> = emptyList(),
    @SerializedName("batch_summary")
    val batchSummary: String = "",
    @SerializedName("dimension_signals")
    val dimensionSignals: List<AiBookScanDimensionSignal> = emptyList(),
    @SerializedName("events")
    val events: List<AiBookScanEvent> = emptyList(),
    @SerializedName("unresolved")
    val unresolved: List<String> = emptyList()
)

internal data class AiBookScanDimensionSignal(
    @SerializedName("dimension")
    val dimension: String = "",
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
    @SerializedName("finding")
    val finding: String = "",
    @SerializedName("confidence")
    val confidence: Float = 0f
)

internal data class AiBookScanEvent(
    @SerializedName("event_key")
    val eventKey: String = "",
    @SerializedName("event_type")
    val eventType: String = "",
    @SerializedName("term_ids")
    val termIds: List<String> = emptyList(),
    @SerializedName("status")
    val status: String = "",
    @SerializedName("severity")
    val severity: String = "",
    @SerializedName("confidence")
    val confidence: Float = 0f,
    @SerializedName("chapter_indexes")
    val chapterIndexes: List<Int> = emptyList(),
    @SerializedName("participants")
    val participants: List<String> = emptyList(),
    @SerializedName("fact")
    val fact: String = "",
    @SerializedName("spoiler_safe_summary")
    val spoilerSafeSummary: String = "",
    @SerializedName("attributes")
    val attributes: JsonObject = JsonObject()
)

internal data class AiBookScanManifest(
    @SerializedName("schema_version")
    val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("work_key")
    val workKey: String = "",
    @SerializedName("book_name")
    val bookName: String = "",
    @SerializedName("author")
    val author: String = "",
    @SerializedName("total_chapters")
    val totalChapters: Int = 0,
    @SerializedName("book_status")
    val bookStatus: String = "unknown",
    @SerializedName("coverage")
    val coverage: AiBookScanCoverage = AiBookScanCoverage(),
    @SerializedName("dimension_signals")
    val dimensionSignals: Map<String, List<AiBookScanDimensionSignal>> = emptyMap(),
    @SerializedName("event_index")
    val eventIndex: List<AiBookScanEvent> = emptyList(),
    @SerializedName("unresolved")
    val unresolved: List<String> = emptyList(),
    @SerializedName("last_scan_stage")
    val lastScanStage: String = "",
    @SerializedName("updated_at")
    val updatedAt: Long = 0L
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

internal data class AiBookScanCoverage(
    @SerializedName("fully_read_chapters")
    val fullyReadChapters: List<Int> = emptyList(),
    @SerializedName("fully_read_ranges")
    val fullyReadRanges: List<AiBookScanRange> = emptyList(),
    @SerializedName("missing_ranges")
    val missingRanges: List<AiBookScanRange> = emptyList(),
    @SerializedName("covered_count")
    val coveredCount: Int = 0,
    @SerializedName("total_count")
    val totalCount: Int = 0,
    @SerializedName("coverage_rate")
    val coverageRate: Float = 0f,
    @SerializedName("completed")
    val completed: Boolean = false
)

internal data class AiBookScanRange(
    @SerializedName("start")
    val start: Int = 0,
    @SerializedName("end")
    val end: Int = 0
)

internal data class AiBookScanShard(
    @SerializedName("schema_version")
    val schemaVersion: Int = AiBookScanManifest.SCHEMA_VERSION,
    @SerializedName("work_key")
    val workKey: String,
    @SerializedName("scan_stage")
    val scanStage: String,
    @SerializedName("fully_read_chapters")
    val fullyReadChapters: List<Int>,
    @SerializedName("batch_summary")
    val batchSummary: String,
    @SerializedName("dimension_signals")
    val dimensionSignals: List<AiBookScanDimensionSignal>,
    @SerializedName("event_keys")
    val eventKeys: List<String>,
    @SerializedName("unresolved")
    val unresolved: List<String>,
    @SerializedName("created_at")
    val createdAt: Long
)

internal object AiBookScanMemory {

    private const val DOMAIN = "book_scan"
    private const val SOURCE = "book_scan_hook"
    private const val MAX_SIGNALS_PER_DIMENSION = 100
    private const val MAX_MANIFEST_EVENTS = 300
    private const val MAX_UNRESOLVED = 100

    private val deltaBlockRegex = Regex(
        pattern = """(?s)```[ \t]*(?:legado-book-scan|book_scan_delta)[^\r\n]*\r?\n(.*?)\r?\n```"""
    )
    private val allowedStages = setOf("orientation", "full_scan", "targeted_review")
    private val allowedBookStatuses = setOf("ongoing", "completed", "hiatus", "unknown")
    private val allowedDimensions = setOf(
        "work_positioning",
        "protagonist_experience",
        "character_ecology",
        "relationship",
        "plot_structure",
        "plot_logic",
        "worldbuilding",
        "power_progression",
        "pacing",
        "writing_style",
        "tone_and_content",
        "ending_safety"
    )
    private val allowedStatuses = setOf("suspected", "confirmed", "resolved", "reversed", "not_found")
    private val allowedSeverities = setOf("critical", "high", "medium", "low", "info")
    private val majorRiskTerms = setOf(
        "relationship.green_hat",
        "relationship.sent_love_interest",
        "relationship.major_heroine_death",
        "relationship.betrayal",
        "protagonist.abuse",
        "protagonist.prolonged_frustration",
        "character.important_death",
        "ending.rushed",
        "ending.hiatus"
    )

    fun readEvidence(
        toolName: String,
        arguments: JsonObject,
        result: JsonObject
    ): AiBookScanReadEvidence? {
        if (toolName !in setOf("bookshelf_text_window_get", "bookshelf_chapter_content_get")) {
            return null
        }
        val normalized = result.objectValue("structuredContent")
            ?.objectValue("normalized_data")
            ?: return null
        if (result.objectValue("structuredContent")?.booleanValue("ok") != true) return null
        val book = normalized.objectValue("book") ?: return null
        val workKey = book.stringValue("work_key")?.trim()?.takeIf { it.isNotEmpty() }
            ?: arguments.stringValue("work_key")?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val indexes = when (toolName) {
            "bookshelf_text_window_get" -> normalized.arrayValue("chapters")
                ?.mapNotNull { element ->
                    val chapter = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val hasContent = chapter.booleanValue("has_content") == true
                    val contentChars = chapter.intValue("content_chars") ?: 0
                    val includedChars = chapter.intValue("included_chars") ?: 0
                    val truncated = chapter.booleanValue("truncated_by_mcp") == true
                    chapter.intValue("index")
                        ?.takeIf { hasContent && !truncated && includedChars >= contentChars }
                }
                .orEmpty()

            else -> {
                val hasContent = normalized.booleanValue("has_content") == true
                val truncated = normalized.booleanValue("truncated_by_mcp") == true
                val index = normalized.objectValue("chapter")?.intValue("index")
                listOfNotNull(index?.takeIf { hasContent && !truncated })
            }
        }
        if (indexes.isEmpty()) return null
        val subject = listOfNotNull(
            book.stringValue("name")?.trim()?.takeIf { it.isNotEmpty() },
            book.stringValue("author")?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" / ").ifBlank { workKey.replace('\n', ' ') }
        return AiBookScanReadEvidence(workKey, subject, indexes.toSet())
    }

    fun saveDeltas(
        content: String,
        evidence: Collection<AiBookScanReadEvidence>
    ): List<AiBookScanSaveResult> {
        if (!AiConfig.memoryEnabled) return emptyList()
        val deltas = deltaBlockRegex.findAll(content).mapNotNull { match ->
            runCatching {
                val root = JsonParser.parseString(match.groupValues[1].trim()).asJsonObject
                val payload = root.objectValue("book_scan_delta") ?: root
                GSON.fromJson(payload, AiBookScanDelta::class.java)
            }.getOrNull()
        }.toList()
        if (deltas.isEmpty()) return emptyList()
        val evidenceByWork = evidence.groupBy { it.workKey }.mapValues { (_, items) ->
            AiBookScanReadEvidence(
                workKey = items.first().workKey,
                subject = items.first().subject,
                fullyReadChapterIndexes = items.flatMapTo(linkedSetOf()) { it.fullyReadChapterIndexes }
            )
        }
        return deltas.mapNotNull { delta ->
            saveDelta(delta, evidenceByWork[delta.workKey])
        }
    }

    private fun saveDelta(
        delta: AiBookScanDelta,
        evidence: AiBookScanReadEvidence?
    ): AiBookScanSaveResult? {
        if (delta.schemaVersion != AiBookScanManifest.SCHEMA_VERSION) return null
        if (delta.workKey.isBlank() || delta.scanStage !in allowedStages) return null
        if (evidence == null) return null
        val claimed = delta.observedChapters.filter { it >= 0 }.toSet()
        val verified = claimed.intersect(evidence.fullyReadChapterIndexes).toSortedSet()
        if (verified.isEmpty()) return null
        val warnings = buildList {
            val rejected = claimed - verified
            if (rejected.isNotEmpty()) {
                add("模型声明的 ${rejected.size} 章未通过正文完整读取校验，未计入覆盖")
            }
        }
        val now = System.currentTimeMillis()
        val scopeHash = delta.workKey.sha256(20)
        val manifestId = "book_scan:manifest:$scopeHash"
        val oldMemory = appDb.agentMemoryDao.get(manifestId)
        val oldManifest = oldMemory?.dataJson?.let { json ->
            runCatching { GSON.fromJson(json, AiBookScanManifest::class.java) }.getOrNull()
        }
        val totalChapters = maxOf(delta.totalChapters, oldManifest?.totalChapters ?: 0)
        val bookStatus = delta.bookStatus.takeIf { it in allowedBookStatuses }
            ?: oldManifest?.bookStatus?.takeIf { it in allowedBookStatuses }
            ?: "unknown"
        val allCovered = buildSet {
            addAll(oldManifest?.coverage?.fullyReadChapters.orEmpty())
            addAll(verified)
        }.filter { totalChapters <= 0 || it < totalChapters }.sorted()
        val mergedSignals = mergeDimensionSignals(
            oldManifest?.dimensionSignals.orEmpty(),
            delta.dimensionSignals,
            bookStatus
        )
        val validEvents = delta.events.mapNotNull { event -> normalizeEvent(event, verified, bookStatus) }
        validEvents.forEach { event -> saveEvent(delta, evidence.subject, scopeHash, event, now) }
        val mergedEvents = mergeEvents(oldManifest?.eventIndex.orEmpty(), validEvents)
        val unresolved = (oldManifest?.unresolved.orEmpty() + delta.unresolved)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .takeLast(MAX_UNRESOLVED)
        val coverage = buildCoverage(allCovered, totalChapters)
        val manifest = AiBookScanManifest(
            workKey = delta.workKey,
            bookName = delta.bookName.ifBlank { oldManifest?.bookName.orEmpty() },
            author = delta.author.ifBlank { oldManifest?.author.orEmpty() },
            totalChapters = totalChapters,
            bookStatus = bookStatus,
            coverage = coverage,
            dimensionSignals = mergedSignals,
            eventIndex = mergedEvents,
            unresolved = unresolved,
            lastScanStage = delta.scanStage,
            updatedAt = now
        )
        val criticalCount = mergedEvents.count {
            it.severity in setOf("critical", "high") && it.status in setOf("suspected", "confirmed")
        }
        appDb.agentMemoryDao.upsert(
            AgentMemory(
                id = manifestId,
                scopeType = "book",
                scopeKey = delta.workKey,
                subject = evidence.subject,
                domain = DOMAIN,
                memoryType = "manifest",
                title = "AI 扫书档案",
                content = buildString {
                    append("已完整读取 ${coverage.coveredCount}/${coverage.totalCount} 章")
                    if (coverage.completed) append("，全书覆盖完成")
                    if (criticalCount > 0) append("；重大风险候选 $criticalCount 项")
                },
                dataJson = GSON.toJson(manifest),
                tags = buildList {
                    add("AI扫书")
                    add(if (coverage.completed) "完整扫描" else "扫描中")
                    if (criticalCount > 0) add("重大风险")
                }.joinToString(","),
                confidence = if (coverage.completed) 1f else coverage.coverageRate.coerceIn(0.1f, 0.99f),
                source = SOURCE,
                status = "active",
                createdAt = oldMemory?.createdAt ?: now,
                updatedAt = now
            )
        )
        saveShard(delta, evidence.subject, scopeHash, verified.toList(), validEvents, now)
        return AiBookScanSaveResult(
            subject = evidence.subject,
            savedChapterCount = verified.size,
            coveredChapterCount = coverage.coveredCount,
            totalChapterCount = coverage.totalCount,
            eventCount = validEvents.size,
            warnings = warnings
        )
    }

    internal fun normalizeEvent(
        event: AiBookScanEvent,
        verified: Set<Int>,
        bookStatus: String
    ): AiBookScanEvent? {
        if (event.eventKey.isBlank() || event.status !in allowedStatuses || event.severity !in allowedSeverities) {
            return null
        }
        val chapters = event.chapterIndexes.filter { it in verified }.distinct().sorted()
        if (event.status != "not_found" && chapters.isEmpty()) return null
        val forcedMarriage = event.eventType == "relationship_forced_marriage" || (
            event.attributes.booleanValue("forced") == true &&
                listOf("成婚", "拜堂", "嫁给", "婚姻").any { marker -> event.fact.contains(marker) }
            )
        val normalizedEventType = if (forcedMarriage) "relationship_forced_marriage" else event.eventType
        val normalizedTerms = buildList {
            event.termIds.forEach { term ->
                val value = term.trim()
                if (value.isEmpty()) return@forEach
                if (forcedMarriage && value == "relationship.missed_love_interest") return@forEach
                if (normalizedEventType != "character_death" && value == "character.important_death") return@forEach
                if (bookStatus != "completed" && value == "ending.rushed") return@forEach
                add(value)
            }
            if (forcedMarriage) {
                add("relationship.green_hat")
                add("relationship.sent_love_interest")
            }
        }.distinct().take(20)
        val normalizedAttributes = event.attributes.deepCopy().apply {
            if (forcedMarriage) {
                if (!has("forced")) addProperty("forced", true)
                if (!has("marriage")) addProperty("marriage", true)
            }
        }
        val normalizedSeverity = if (
            event.severity in setOf("critical", "high") && normalizedTerms.none { it in majorRiskTerms }
        ) {
            "medium"
        } else {
            event.severity
        }
        return event.copy(
            eventKey = event.eventKey.trim().take(120),
            eventType = normalizedEventType.trim().take(80),
            termIds = normalizedTerms,
            severity = normalizedSeverity,
            confidence = event.confidence.coerceIn(0f, 1f),
            chapterIndexes = chapters,
            participants = event.participants.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20),
            fact = event.fact.trim().take(800),
            spoilerSafeSummary = event.spoilerSafeSummary.trim().take(300),
            attributes = normalizedAttributes
        )
    }

    private fun mergeDimensionSignals(
        old: Map<String, List<AiBookScanDimensionSignal>>,
        incoming: List<AiBookScanDimensionSignal>,
        bookStatus: String
    ): Map<String, List<AiBookScanDimensionSignal>> {
        val result = old.mapValuesTo(linkedMapOf()) { (_, signals) -> signals.toMutableList() }
        incoming.forEach { signal ->
            if (signal.dimension !in allowedDimensions) return@forEach
            val rawTags = signal.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
            val containsPrematureRushedEnding = signal.dimension == "ending_safety" &&
                bookStatus != "completed" &&
                (rawTags.any { it.contains("烂尾") } || signal.finding.contains("烂尾"))
            val normalized = signal.copy(
                tags = if (containsPrematureRushedEnding) {
                    (rawTags.filterNot { it.contains("烂尾") } + "当前未收束").distinct()
                } else {
                    rawTags
                },
                finding = if (containsPrematureRushedEnding) {
                    "当前文本末尾仍有未收束情节；作品未完结，不能据此判断烂尾。"
                } else {
                    signal.finding.trim().take(600)
                },
                confidence = signal.confidence.coerceIn(0f, 1f)
            )
            val bucket = result.getOrPut(signal.dimension) { mutableListOf() }
            val duplicateIndex = bucket.indexOfFirst {
                it.tags == normalized.tags && it.finding == normalized.finding
            }
            if (duplicateIndex >= 0) {
                if (normalized.confidence >= bucket[duplicateIndex].confidence) {
                    bucket[duplicateIndex] = normalized
                }
            } else {
                bucket += normalized
            }
            while (bucket.size > MAX_SIGNALS_PER_DIMENSION) bucket.removeAt(0)
        }
        return result
    }

    private fun mergeEvents(
        old: List<AiBookScanEvent>,
        incoming: List<AiBookScanEvent>
    ): List<AiBookScanEvent> {
        val result = linkedMapOf<String, AiBookScanEvent>()
        old.forEach { result[it.eventKey] = it }
        incoming.forEach { event ->
            val current = result[event.eventKey]
            if (current == null || event.confidence >= current.confidence) {
                result[event.eventKey] = event
            }
        }
        return result.values.toList().takeLast(MAX_MANIFEST_EVENTS)
    }

    private fun saveEvent(
        delta: AiBookScanDelta,
        subject: String,
        scopeHash: String,
        event: AiBookScanEvent,
        now: Long
    ) {
        val id = "book_scan:event:$scopeHash:${event.eventKey.sha256(20)}"
        val existing = appDb.agentMemoryDao.get(id)
        appDb.agentMemoryDao.upsert(
            AgentMemory(
                id = id,
                scopeType = "book",
                scopeKey = delta.workKey,
                subject = subject,
                domain = DOMAIN,
                memoryType = "event",
                title = event.spoilerSafeSummary.ifBlank { event.eventType.ifBlank { "扫书事件" } }.take(80),
                content = event.fact.take(800),
                dataJson = GSON.toJson(event),
                tags = (event.termIds + event.status + event.severity).distinct().joinToString(","),
                confidence = event.confidence,
                source = SOURCE,
                status = "active",
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    private fun saveShard(
        delta: AiBookScanDelta,
        subject: String,
        scopeHash: String,
        verified: List<Int>,
        events: List<AiBookScanEvent>,
        now: Long
    ) {
        val rangeKey = verified.joinToString(",")
        val id = "book_scan:shard:$scopeHash:${rangeKey.sha256(20)}"
        val existing = appDb.agentMemoryDao.get(id)
        val shard = AiBookScanShard(
            workKey = delta.workKey,
            scanStage = delta.scanStage,
            fullyReadChapters = verified,
            batchSummary = delta.batchSummary.trim().take(1200),
            dimensionSignals = delta.dimensionSignals.filter { it.dimension in allowedDimensions },
            eventKeys = events.map { it.eventKey },
            unresolved = delta.unresolved.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(50),
            createdAt = now
        )
        appDb.agentMemoryDao.upsert(
            AgentMemory(
                id = id,
                scopeType = "book",
                scopeKey = delta.workKey,
                subject = subject,
                domain = DOMAIN,
                memoryType = "shard",
                title = "扫书章节 ${rangesFromIndexes(verified).joinToString { "${it.start}-${it.end - 1}" }}",
                content = shard.batchSummary.ifBlank { "已完整读取 ${verified.size} 章，未记录额外摘要。" },
                dataJson = GSON.toJson(shard),
                tags = listOf("AI扫书", delta.scanStage).joinToString(","),
                confidence = 1f,
                source = SOURCE,
                status = "active",
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    private fun buildCoverage(indexes: List<Int>, total: Int): AiBookScanCoverage {
        val safeTotal = total.coerceAtLeast(0)
        val normalized = indexes.filter { it >= 0 && (safeTotal == 0 || it < safeTotal) }.distinct().sorted()
        val covered = normalized.size
        val ranges = rangesFromIndexes(normalized)
        val missing = if (safeTotal > 0) {
            rangesFromIndexes((0 until safeTotal).filterNot { it in normalized.toHashSet() })
        } else {
            emptyList()
        }
        return AiBookScanCoverage(
            fullyReadChapters = normalized,
            fullyReadRanges = ranges,
            missingRanges = missing,
            coveredCount = covered,
            totalCount = safeTotal,
            coverageRate = if (safeTotal > 0) covered.toFloat() / safeTotal else 0f,
            completed = safeTotal > 0 && covered == safeTotal
        )
    }

    private fun rangesFromIndexes(indexes: List<Int>): List<AiBookScanRange> {
        val values = indexes.distinct().sorted()
        if (values.isEmpty()) return emptyList()
        val result = mutableListOf<AiBookScanRange>()
        var start = values.first()
        var previous = start
        values.drop(1).forEach { value ->
            if (value == previous + 1) {
                previous = value
            } else {
                result += AiBookScanRange(start, previous + 1)
                start = value
                previous = value
            }
        }
        result += AiBookScanRange(start, previous + 1)
        return result
    }

    private fun JsonObject.objectValue(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrayValue(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.stringValue(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.intValue(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull()

    private fun JsonObject.booleanValue(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrNull()

    private fun String.sha256(length: Int): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(length)
    }
}
