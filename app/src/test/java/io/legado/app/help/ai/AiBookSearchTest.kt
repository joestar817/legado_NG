package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.legado.app.constant.BookSourceType
import io.legado.app.model.webBook.matchesPreciseBook
import io.legado.app.web.mcp.matchesBookSearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBookSearchTest {
    private val identity = "https://example.org/book/42"
    private val chapter = "https://example.net/book/42/intro"
    private val evidence = AiWebActivity(sources = listOf(AiWebSource(identity, "Book"), AiWebSource(chapter, "Chapter")))

    private fun reply(
        intent: String = "find",
        media: String = "novel",
        evidenceUrl: String = "$chapter#summary",
    ) = """
        《问道红尘》作者姬叉。序章里流苏寄宿在狼牙棒里。
        ```legado-book-search
        {"status":"verified","intent":"$intent","candidates":[{
          "title":"问道红尘","author":"姬叉","media":"$media","evidence":"序章里流苏寄宿在狼牙棒里",
          "identity_url":"$identity","evidence_url":"$evidenceUrl"
        }]}
        ```
    """.trimIndent()

    @Test
    fun onlyVerifiedIdentityAndClueAuthorizeAutomaticLookup() {
        val parsed = requireNotNull(parseAiBookSearchReply(reply(), evidence))
        assertEquals("问道红尘", parsed.automaticCandidate?.title)
        assertNull(parseAiBookSearchReply(reply(), evidence.copy(sources = evidence.sources.take(1)))?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(), evidence.copy(searchFailed = true))?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(), null)?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(intent = "title_only"), evidence)?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(media = ""), evidence)?.automaticCandidate)
        assertNull(parsed.copy(rejectedKeys = parsed.candidates.map { it.key }).automaticCandidate)
    }

    @Test
    fun oneCitedPageCanVerifyIdentityAndClueDespiteSourceTracking() {
        val web = AiWebActivity(sources = listOf(AiWebSource("$identity?utm_source=chatgpt.com", "Book")))

        assertEquals("问道红尘", parseAiBookSearchReply(reply(evidenceUrl = identity), web)?.automaticCandidate?.title)
        assertNull(parseAiBookSearchReply(reply(), web)?.automaticCandidate)
    }

    @Test
    fun evidenceMustStillMatchTheQueryThatIdentifiesTheChapter() {
        val web = evidence.copy(sources = listOf(
            AiWebSource(identity, "Book"),
            AiWebSource("$chapter?chapter_id=1&utm_source=chatgpt.com", "Chapter"),
        ))

        assertNotNull(parseAiBookSearchReply(reply(evidenceUrl = "$chapter?chapter_id=1"), web)?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(evidenceUrl = "$chapter?chapter_id=2"), web)?.automaticCandidate)
    }

    @Test
    fun externalErrorsAndMissingEvidenceRequireConfirmation() {
        assertNull(parseAiBookSearchReply(reply(), evidence.copy(externalError = "timeout"))?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(), evidence.copy(sources = emptyList()))?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply(), evidence.copy(sources = evidence.sources.drop(1)))?.automaticCandidate)
        val noClue = reply().replace("序章里流苏寄宿在狼牙棒里\",", "\",")
        assertNull(parseAiBookSearchReply(noClue, evidence)?.automaticCandidate)
    }

    @Test
    fun ambiguityAndVersionQuestionsAreNeverSentToBookSourcesAutomatically() {
        val candidate = """{"title":"另一部作品","author":"其他作者","media":"novel"}"""
        val ambiguous = reply().replace("}]}", "},$candidate]}")
        val clarify = """```legado-book-search
            {"status":"clarify","intent":"find","question":"想找小说还是漫画？","candidates":[]}
            ```""".trimIndent()
        assertEquals(2, parseAiBookSearchReply(ambiguous, evidence)?.candidates?.size)
        assertNull(parseAiBookSearchReply(ambiguous, evidence)?.automaticCandidate)
        assertNull(parseAiBookSearchReply(reply().replace("姬叉\",\"media", "\",\"media"), evidence)?.automaticCandidate)
        val question = requireNotNull(parseAiBookSearchReply(clarify, evidence))
        assertEquals("想找小说还是漫画？", question.question)
        assertNull(question.automaticCandidate)
    }

    @Test
    fun protocolIsSeparateFromVisibleReplyAndExplicitIntentMustBeUnambiguous() {
        assertEquals("《问道红尘》作者姬叉。序章里流苏寄宿在狼牙棒里。", aiBookSearchVisibleText(reply()))
        assertEquals("答复", aiBookSearchVisibleText("答复\n```legado-book-search\n{broken"))
        assertNull(parseAiBookSearchReply("答复\n```legado-book-search\n{broken", evidence))
        assertNotNull(explicitAiBookSearchCandidate("找小说《问道红尘》，作者：姬叉"))
        assertNull(explicitAiBookSearchCandidate("是不是小说《问道红尘》，作者姬叉？"))
    }

    @Test
    fun preciseMatchAndScopeRejectNamesakesAndOutOfScopeSources() {
        assertTrue(matchesPreciseBook("问道红尘", "姬叉", "问道红尘", "姬叉"))
        assertFalse(matchesPreciseBook("问道红尘", "见秋", "问道红尘", "姬叉"))
        assertFalse(matchesPreciseBook("问道红尘", "姬叉二世", "问道红尘", "姬叉"))
        assertFalse(matchesPreciseBook("灵气复苏", "问道红尘", "问道红尘", "姬叉"))
        assertTrue(matchesBookSearchScope("漫画,有声", "https://source.example", "NG；漫画"))
        assertFalse(matchesBookSearchScope("漫画", "https://source.example", "漫画外文"))
        assertFalse(matchesBookSearchScope("书源::https://specific.example", "https://source.example", "书源"))
        assertEquals(BookSourceType.image, AiBookSearchMedia.MANGA.sourceType)
        assertTrue(AiBookSearchMedia.AUDIOBOOK.matchesAudioLabel("精品听书"))
        assertFalse(AiBookSearchMedia.AUDIOBOOK.matchesAudioLabel("歌曲音乐"))
        assertFalse(AiBookSearchMedia.AUDIOBOOK.matchesAudioLabel("听书和音乐"))
    }

    @Test
    fun audioScopeConfirmationOnlyAppliesToTheSelectedMedia() {
        val scope = JsonObject().apply { addProperty(AI_BOOK_SEARCH_AUDIO_SCOPE_MEDIA, "AUDIOBOOK") }
        assertTrue(scope.isConfirmedBookSearchAudioScope(AiBookSearchMedia.AUDIOBOOK))
        assertFalse(scope.isConfirmedBookSearchAudioScope(AiBookSearchMedia.MUSIC))
        assertFalse(scope.isConfirmedBookSearchAudioScope(AiBookSearchMedia.NOVEL))
        assertFalse(JsonObject().isConfirmedBookSearchAudioScope(AiBookSearchMedia.AUDIOBOOK))
        assertTrue(AiBookSearchMedia.AUDIOBOOK.matchesAudioLabel("混合内容", allowUnclassified = true))
        assertFalse(AiBookSearchMedia.AUDIOBOOK.matchesAudioLabel("音乐专源", allowUnclassified = true))
    }

    @Test
    fun searchRequiredTurnsDoNotUseAnOnlyReadLinkPlan() {
        val settings = AiExternalSearchSettings()
        val model = AiModel("deepseek-flash")
        val both = setOf(AiWebTool.SEARCH, AiWebTool.READ_LINK)
        fun route(enabled: Set<AiWebTool>) = AiDeepSeekWebRoute(
            "https://api.deepseek.com/chat/completions", AiModelGenerationTestProtocol.CHAT_COMPLETIONS,
            flash = true, supported = both, enabled = enabled,
        )
        val link = "https://example.org/book/42"
        val searchable = settings.resolveWebToolPlan(route(both), model, true, link, false, requireSearch = true)
        assertEquals("deepseek_web_search", (searchable as AiWebToolPlan.Native).definitions.single().get("type").asString)
        val readOnly = settings.resolveWebToolPlan(route(setOf(AiWebTool.READ_LINK)), model, true, link, false, requireSearch = true)
        assertEquals(AiWebToolPlan.Off, readOnly)
    }

    @Test
    fun bookSearchMetadataRoundTripsAndExistingMessagesCanOmitIt() {
        val gson = Gson()
        val parsed = requireNotNull(parseAiBookSearchReply(reply(), evidence))
        val snapshot = AiChatMessageSnapshot("message", "assistant", "识别结果", bookSearch = parsed)
        val json = gson.toJson(snapshot)
        assertTrue(json.contains("\"book_search\""))
        assertEquals(parsed, gson.fromJson(json, AiChatMessageSnapshot::class.java).bookSearch)
        assertNull(gson.fromJson("""{"id":"old","role":"assistant","content":"Hello"}""", AiChatMessageSnapshot::class.java).bookSearch)
    }
}
