package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBookScanMemoryTest {

    @Test
    fun readEvidenceOnlyAcceptsCompleteWindowChapters() {
        val arguments = JsonObject().apply {
            addProperty("work_key", "测试书\n作者")
            addProperty("start_chapter_index", 10)
        }
        val result = JsonObject().apply {
            add("structuredContent", JsonObject().apply {
                addProperty("ok", true)
                add("normalized_data", JsonObject().apply {
                    add("book", JsonObject().apply {
                        addProperty("work_key", "测试书\n作者")
                        addProperty("name", "测试书")
                        addProperty("author", "作者")
                    })
                    add("chapters", JsonArray().apply {
                        add(chapter(10, contentChars = 1200, includedChars = 1200, truncated = false))
                        add(chapter(11, contentChars = 1500, includedChars = 700, truncated = true))
                        add(chapter(12, contentChars = 0, includedChars = 0, truncated = false, hasContent = false))
                    })
                })
            })
        }

        val evidence = AiBookScanMemory.readEvidence(
            "bookshelf_text_window_get",
            arguments,
            result
        )

        assertEquals("测试书\n作者", evidence?.workKey)
        assertEquals(setOf(10), evidence?.fullyReadChapterIndexes)
    }

    @Test
    fun interactionParserHidesBookScanDeltaAndKeepsActions() {
        val content = """
            已完成初扫。

            ```book_scan_delta
            {"schema_version":1,"work_key":"测试书\\n作者","observed_chapters":[0]}
            ```

            ```legado-interaction
            {
              "version": 1,
              "id": "book_scan_next_action",
              "type": "actions",
              "title": "继续查看",
              "options": [{"label":"继续扫描","value":"continue_full_scan"}]
            }
            ```
        """.trimIndent()

        val parsed = AiChatInteractionParser.parse(content)

        assertTrue(parsed.content.contains("已完成初扫"))
        assertFalse(parsed.content.contains("schema_version"))
        assertEquals(1, parsed.interactions.size)
        assertEquals("book_scan_next_action", parsed.interactions.single().id)
    }

    @Test
    fun projectRulesMapForcedMarriageAndRejectFalseDeath() {
        val forcedMarriage = AiBookScanMemory.normalizeEvent(
            event = AiBookScanEvent(
                eventKey = "forced_marriage_1",
                eventType = "relationship_change",
                termIds = listOf("relationship.missed_love_interest"),
                status = "confirmed",
                severity = "critical",
                confidence = 0.95f,
                chapterIndexes = listOf(20),
                fact = "重要感情角色被迫与第三方拜堂成婚",
                attributes = JsonObject().apply {
                    addProperty("forced", true)
                    addProperty("consummated", false)
                }
            ),
            verified = setOf(20),
            bookStatus = "ongoing"
        )
        val permanentInjury = AiBookScanMemory.normalizeEvent(
            event = AiBookScanEvent(
                eventKey = "injury_1",
                eventType = "character_permanent_injury",
                termIds = listOf("character.important_death"),
                status = "confirmed",
                severity = "high",
                confidence = 0.9f,
                chapterIndexes = listOf(21),
                fact = "角色被阉割并囚禁"
            ),
            verified = setOf(21),
            bookStatus = "ongoing"
        )

        assertEquals("relationship_forced_marriage", forcedMarriage?.eventType)
        assertTrue(forcedMarriage?.termIds.orEmpty().contains("relationship.green_hat"))
        assertTrue(forcedMarriage?.termIds.orEmpty().contains("relationship.sent_love_interest"))
        assertFalse(forcedMarriage?.termIds.orEmpty().contains("relationship.missed_love_interest"))
        assertFalse(permanentInjury?.termIds.orEmpty().contains("character.important_death"))
        assertEquals("medium", permanentInjury?.severity)
    }

    private fun chapter(
        index: Int,
        contentChars: Int,
        includedChars: Int,
        truncated: Boolean,
        hasContent: Boolean = true
    ) = JsonObject().apply {
        addProperty("index", index)
        addProperty("has_content", hasContent)
        addProperty("content_chars", contentChars)
        addProperty("included_chars", includedChars)
        addProperty("truncated_by_mcp", truncated)
    }
}
