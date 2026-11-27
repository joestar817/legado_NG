package io.legado.app.help.ai

import com.google.gson.JsonParser
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import io.legado.app.ui.book.info.BookAiAssistantLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCharacterCardHelperTest {
    @Test
    fun selectedChapterPassesTheToolIndexAndDisplayNumberSeparately() {
        val book = Book(bookUrl = "test-book", name = "样书", author = "作者")
        val entry = BookAiAssistantLauncher.characterCardSelection(book, "测试角色", 0, "第1章")

        assertEquals("text_selection_character_card", entry.context.contextId)
        assertEquals(0, entry.context.payload.get("current_chapter_index").asInt)
        assertEquals(1, entry.context.payload.get("current_chapter_number").asInt)
        assertEquals("第1章", entry.context.payload.get("current_chapter_title").asString)
    }

    @Test
    fun chosenAliasResolvesToTheCanonicalNameAndKeepsTheSixFormFields() {
        val form = requireNotNull(characterCardForm(
            listOf("**陈升**", "男", "男主", "升子、陈升", "一段人物介绍", "未提及"),
            "「升子」",
        ))

        assertEquals("陈升", form.name)
        assertEquals(listOf("升子"), form.aliases)
        assertEquals(BookCharacter.Gender.MALE, form.gender)
        assertEquals(BookCharacter.RoleTag.MALE_LEAD, form.roleTag)
        assertEquals("一段人物介绍", form.intro)
        assertNull(form.imagePrompt)
    }

    @Test
    fun unrelatedAndIneligibleRowsDoNotBecomeDrafts() {
        assertNull(characterCardForm(listOf("赵文博", "男", "男配", "无", "简介", "无"), "陈升"))
        assertNull(characterCardForm(listOf("陈升", "男", "路人", "无", "简介", "无"), "陈升"))
    }

    @Test
    fun rewrittenIntroMustFitTheLimitAndBeCompletePlainText() {
        val atLimit = "人".repeat(CHARACTER_CARD_INTRO_MAX_CHARS - 1) + "。"
        assertEquals(atLimit, validatedShortCharacterIntro(AiTextResult(atLimit, finishReason = "stop")))
        assertNull(validatedShortCharacterIntro(AiTextResult(atLimit + "。")))
        assertNull(validatedShortCharacterIntro(AiTextResult("带着成年记忆重生的高中生。", finishReason = "length")))
        assertNull(validatedShortCharacterIntro(AiTextResult("人物介绍：带着成年记忆重生的高中生。")))
        assertNull(validatedShortCharacterIntro(AiTextResult("带着成年记忆重生的高中生。\n重视家人。")))
    }

    @Test
    fun repeatedUpdatesKeepOneCallAndOnlyIncludeChaptersWithReturnedText() {
        val history = AiCharacterCardProgressTracker()
        val name = "bookshelf_text_window_get"
        val started = AiToolExecutionUpdate("first", name)
        val result = JsonParser.parseString("""
            {"structuredContent":{"normalized_data":{"chapters":[
              {"chapter_number":1,"included_chars":200},
              {"chapter_number":2,"included_chars":0},
              {"chapter_number":3,"included_chars":100}
            ]}}}
        """).asJsonObject
        val finished = started.copy(
            receipt = ToolExecutionReceipt(
                receiptId = "receipt",
                conversationId = "conversation",
                turnId = "turn",
                stepId = "step",
                toolCallId = "first",
                toolName = name,
                argumentsHash = "hash",
                status = ToolExecutionStatus.SUCCEEDED,
            ),
            result = result,
        )

        assertEquals(name, history.record(started)?.runningTool)
        assertNull(history.record(started))
        val completed = requireNotNull(history.record(finished))
        assertEquals(1, completed.tools.size)
        assertEquals(setOf(1, 3), completed.tools.single().chapters)
        assertNull(completed.runningTool)
        assertNull(history.record(started))
        assertNull(history.record(finished))
        assertEquals(2, history.record(AiToolExecutionUpdate("second", name))?.tools?.size)
    }

    @Test
    fun usageAccumulatesAcrossRequestsAndSurvivesToolUpdates() {
        val tracker = AiCharacterCardProgressTracker()
        tracker.recordUsage(AiChatUsage(promptTokens = 1_200, completionTokens = 18, totalTokens = 1_218))
        val withTool = requireNotNull(tracker.record(AiToolExecutionUpdate("first", "bookshelf_text_window_get")))

        assertEquals(1_200, withTool.usage?.promptTokens)
        assertEquals(18, withTool.usage?.completionTokens)

        val afterRewrite = tracker.recordUsage(AiChatUsage(promptTokens = 450, completionTokens = 31, totalTokens = 481))
        assertEquals(1_650, afterRewrite.usage?.promptTokens)
        assertEquals(49, afterRewrite.usage?.completionTokens)
        assertEquals(withTool.tools, afterRewrite.tools)
        assertFalse(afterRewrite.missingPromptTokens)
        assertFalse(afterRewrite.missingCompletionTokens)
    }

    @Test
    fun partialUsageMarksOnlyTheDirectionThatMissedARequest() {
        val tracker = AiCharacterCardProgressTracker()
        tracker.recordUsage(AiChatUsage(promptTokens = 100, completionTokens = 0, totalTokens = 100))
        val partial = tracker.recordUsage(AiChatUsage(promptTokens = 12, completionTokens = null, totalTokens = null))

        assertEquals(112, partial.usage?.promptTokens)
        assertEquals(0, partial.usage?.completionTokens)
        assertFalse(partial.missingPromptTokens)
        assertTrue(partial.missingCompletionTokens)

        val afterMissing = tracker.recordUsage(null)
        assertEquals(partial.usage, afterMissing.usage)
        assertTrue(afterMissing.missingPromptTokens)
        assertTrue(afterMissing.missingCompletionTokens)
        assertNull(AiCharacterCardProgressTracker().recordUsage(null).usage)
    }
}
