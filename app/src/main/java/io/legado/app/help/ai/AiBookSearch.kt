package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.BookSourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val RESULT_FENCE = "```legado-book-search"
private val explicitRequest = Regex(
    """^(?:请|帮我)?(?:找(?:一下)?|搜索|查找)?\s*(小说|网文|漫画|有声书|音乐|歌曲|动画|视频|下载)\s*《([^》]{1,150})》[\s，,、]+作者[\s：:]*([^\s，,。？?！!]{1,80})[。！!]?$"""
)

internal enum class AiBookSearchAvailability {
    READY, SKILL, MODEL, ACCOUNT, PROVIDER, GENERATION, OFF, UNAVAILABLE, EXTERNAL_SETUP, EXTERNAL_TOOLS,
}

internal fun AiExternalSearchSettings.bookSearchAvailability(
    hasSkill: Boolean,
    provider: AiProviderSetting?,
    model: AiModel?,
    hasCredentials: Boolean = provider?.hasRequestCredentials() == true,
): AiBookSearchAvailability {
    if (!hasSkill) return AiBookSearchAvailability.SKILL
    if (provider == null || !provider.enabled) return AiBookSearchAvailability.MODEL
    if (!hasCredentials) {
        return if (provider.isOpenAiAccountProvider()) AiBookSearchAvailability.ACCOUNT else AiBookSearchAvailability.PROVIDER
    }
    if (model?.isTextChatModel() != true) return AiBookSearchAvailability.MODEL
    val generation = provider.resolveModelGenerationRoute(model)
    if (generation.issue != null) return AiBookSearchAvailability.GENERATION
    if (!webEnabled) return AiBookSearchAvailability.OFF
    val plan = resolveWebToolPlan(
        provider.nativeWebRoute(model, generation), model, true, "", false, requireSearch = true,
    )
    return when {
        plan != AiWebToolPlan.Off -> AiBookSearchAvailability.READY
        !usesExternalService -> AiBookSearchAvailability.UNAVAILABLE
        activeService() == null -> AiBookSearchAvailability.EXTERNAL_SETUP
        else -> AiBookSearchAvailability.EXTERNAL_TOOLS
    }
}

enum class AiBookSearchMedia(val sourceType: Int) {
    @SerializedName("novel") NOVEL(BookSourceType.default),
    @SerializedName("manga") MANGA(BookSourceType.image),
    @SerializedName("audiobook") AUDIOBOOK(BookSourceType.audio),
    @SerializedName("music") MUSIC(BookSourceType.audio),
    @SerializedName("video") VIDEO(BookSourceType.video),
    @SerializedName("download") DOWNLOAD(BookSourceType.file);

    internal fun matchesAudioLabel(label: String, allowUnclassified: Boolean = false): Boolean {
        if (sourceType != BookSourceType.audio) return true
        val audiobook = label.contains("有声") || label.contains("听书") ||
            label.contains("audiobook", ignoreCase = true)
        val music = label.contains("音乐") || label.contains("歌曲") ||
            label.contains("music", ignoreCase = true)
        if (audiobook == music) return allowUnclassified
        return if (this == AUDIOBOOK) audiobook else music
    }

    companion object {
        internal fun fromWire(value: String): AiBookSearchMedia? = when (value) {
            "novel", "小说", "网文" -> NOVEL
            "manga", "漫画" -> MANGA
            "audiobook", "有声书" -> AUDIOBOOK
            "music", "音乐", "歌曲" -> MUSIC
            "video", "动画", "视频" -> VIDEO
            "download", "下载" -> DOWNLOAD
            else -> null
        }
    }
}

data class AiBookSearchCandidate(
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String,
    @SerializedName("media") val media: AiBookSearchMedia?,
    @SerializedName("evidence") val evidence: String = "",
    @SerializedName("alias") val alias: String? = null,
) {
    val key: String get() = listOf(title, author, media?.name.orEmpty()).joinToString("\u0000")
    val searchable: Boolean get() = title.isNotBlank() && author.isNotBlank() && media != null
}

data class AiBookSearchReply(
    @SerializedName("candidates") val candidates: List<AiBookSearchCandidate>,
    @SerializedName("verified") val verified: Boolean = false,
    @SerializedName("find_in_sources") val findInSources: Boolean = false,
    @SerializedName("question") val question: String = "",
    @SerializedName("rejected_keys") val rejectedKeys: List<String> = emptyList(),
    @SerializedName("attempted_keys") val attemptedKeys: List<String> = emptyList(),
    @SerializedName("lookup") val lookup: AiBookSourceLookup? = null,
) {
    internal val automaticCandidate: AiBookSearchCandidate?
        get() = candidates.singleOrNull()?.takeIf {
            verified && findInSources && it.searchable && it.key !in rejectedKeys
        }
}

data class AiBookSourceLookup(
    @SerializedName("candidate_key") val candidateKey: String,
    @SerializedName("keyword") val keyword: String,
    @SerializedName("status") val status: AiBookSourceStatus,
    @SerializedName("hits") val hits: List<AiBookSourceHit> = emptyList(),
    @SerializedName("failed_sources") val failedSources: Int = 0,
)

enum class AiBookSourceStatus {
    @SerializedName("running") RUNNING,
    @SerializedName("complete") COMPLETE,
    @SerializedName("incomplete") INCOMPLETE,
    @SerializedName("failed") FAILED,
    @SerializedName("disabled") DISABLED,
    @SerializedName("invalid_scope") INVALID_SCOPE,
    @SerializedName("no_sources") NO_SOURCES,
    @SerializedName("audio_scope_required") AUDIO_SCOPE_REQUIRED,
    @SerializedName("namesake") NAMESAKE,
    @SerializedName("unverified") UNVERIFIED,
    @SerializedName("interrupted") INTERRUPTED,
    @SerializedName("open_failed") OPEN_FAILED,
}

data class AiBookSourceHit(
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String,
    @SerializedName("url") val url: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("origin_name") val originName: String,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("source_count") val sourceCount: Int,
)

internal fun explicitAiBookSearchCandidate(text: String): AiBookSearchCandidate? {
    val (media, title, author) = explicitRequest.matchEntire(text.trim())?.destructured ?: return null
    return AiBookSearchCandidate(title.trim(), author.trim(), AiBookSearchMedia.fromWire(media))
        .takeIf { it.searchable && it.title.none(Char::isISOControl) }
}

internal fun aiBookSearchVisibleText(text: String): String =
    text.substringBefore(RESULT_FENCE, text).trimEnd()

internal fun parseAiBookSearchReply(text: String, web: AiWebActivity?): AiBookSearchReply? {
    val start = text.indexOf(RESULT_FENCE)
    if (start < 0) return null
    val jsonStart = start + RESULT_FENCE.length
    val end = text.indexOf("```", jsonStart)
    if (end < 0) return null
    val raw = text.substring(jsonStart, end).trim()
    if (raw.length > 12_000) return null
    val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return null
    val sourceUrls = web?.sources.orEmpty().mapNotNullTo(hashSetOf()) { it.url.citationAddress() }
    fun supported(url: String) = url.citationAddress()?.let { it in sourceUrls } == true
    val entries = root.get("candidates")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
        .take(3).mapNotNull { entry ->
            val obj = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val title = obj.field("title", 150)
            if (title.isBlank()) return@mapNotNull null
            val evidence = obj.field("evidence", 350)
            val identityChecked = supported(obj.field("identity_url", 2048))
            val evidenceChecked = evidence.isNotBlank() && supported(obj.field("evidence_url", 2048))
            val alias = obj.field("alias", 150).takeIf {
                it.isNotBlank() && it != title && supported(obj.field("alias_url", 2048))
            }
            AiBookSearchCandidate(
                title = title,
                author = obj.field("author", 100),
                media = AiBookSearchMedia.fromWire(obj.field("media", 16)),
                evidence = evidence,
                alias = alias,
            ) to (identityChecked && evidenceChecked)
        }
    return AiBookSearchReply(
        candidates = entries.map { it.first }.distinctBy { it.key },
        verified = root.field("status", 16) == "verified" && entries.size == 1 &&
            entries.single().first.searchable && entries.single().second &&
            web?.searchFailed != true && web?.externalError == null,
        findInSources = root.field("intent", 16) == "find",
        question = root.field("question", 250),
    )
}

private fun String.citationAddress(): String? =
    toHttpUrlOrNull()?.newBuilder()
        ?.fragment(null)
        ?.removeAllQueryParameters("utm_source")
        ?.build()
        ?.toString()

private fun JsonObject.field(key: String, maxLength: Int): String =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.trim()?.takeIf { it.length <= maxLength && it.none(Char::isISOControl) }.orEmpty()

internal const val AI_BOOK_SEARCH_SCOPE = "search_scope"
internal const val AI_BOOK_SEARCH_AUDIO_SCOPE_MEDIA = "audio_scope_media"
internal const val AI_BOOK_SEARCH_DRAFT = "draft"

internal fun AgentModeEntryContext?.bookSearchDraft(): String? = this?.payload?.get(AI_BOOK_SEARCH_DRAFT)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString?.takeIf { it.isNotBlank() }

internal fun bookSearchDraftForHistory(input: String, hasMessages: Boolean, userEdited: Boolean): String? {
    if (!hasMessages && !userEdited) return null
    return input.take(4000).filter { !it.isISOControl() || it in "\n\r\t" }.takeIf { it.isNotBlank() }
}

internal fun JsonObject?.isConfirmedBookSearchAudioScope(media: AiBookSearchMedia?): Boolean =
    media?.sourceType == BookSourceType.audio && this?.field(AI_BOOK_SEARCH_AUDIO_SCOPE_MEDIA, 16) == media.name
