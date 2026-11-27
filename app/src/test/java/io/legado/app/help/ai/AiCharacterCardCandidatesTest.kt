package io.legado.app.help.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCharacterCardCandidatesTest {
    @Test
    fun chapterSourceIncludesCharactersFromLaterParagraphsAndOmitsImageUrls() {
        val source = characterCardCandidateText(listOf(
            "　周宁和林川正在聊天。",
            """<img src="https://example.invalid/illustration.webp">""",
            "　沈晴推开门，将信递给两人。",
        ))
        val result = AiTextResult("""{"names":["周宁","林川","沈晴"]}""")

        assertEquals("周宁和林川正在聊天。\n[图片]\n沈晴推开门，将信递给两人。", source)
        assertEquals(listOf("周宁", "林川", "沈晴"), parseCharacterCardCandidates(result, source))
        assertEquals(listOf("周宁", "林川"), parseCharacterCardCandidates(result, "周宁和林川正在聊天。"))
    }

    @Test
    fun onlyDistinctNamesFromTheChapterAreKeptInListOrder() {
        val source = "　　安秋月和刘倩调配。\n孙雨欣招呼她们坐下。"
        val result = AiTextResult(
            """
            ```json
            {"names":["安秋月","刘倩",null,33,"沈晴","孙雨欣","「安秋月」"]}
            ```
            """.trimIndent(),
        )
        assertEquals(
            listOf("安秋月", "刘倩", "孙雨欣"),
            parseCharacterCardCandidates(result, source),
        )
    }

    @Test
    fun emptyListIsDifferentFromInvalidOrTruncatedResult() {
        assertEquals(
            emptyList<String>(),
            parseCharacterCardCandidates(AiTextResult("""{"names":[]}"""), "正文"),
        )
        assertNull(parseCharacterCardCandidates(AiTextResult("""{"names":[1,false]}"""), "正文"))
        assertNull(parseCharacterCardCandidates(
            AiTextResult("""{"names":[]}""", finishReason = "max_output_tokens"),
            "正文",
        ))
    }

    @Test
    fun outputLimitStopRetriesTheSameRequestWithALargerBudget() = runBlocking {
        val requestedBudgets = mutableListOf<Int>()
        val complete = AiTextResult("""{"names":[]}""")

        val result = requestCandidateText(4096, 8192) { budget ->
            requestedBudgets += budget
            if (budget == 4096) throw AiAssistantReplyEndException(AiAssistantReplyEndReason.TRUNCATED)
            complete
        }

        assertEquals(listOf(4096, 8192), requestedBudgets)
        assertEquals(complete, result)
    }

    @Test
    fun retryStopsWhenBudgetCannotGrowOrTheStreamWasInterrupted() = runBlocking {
        val cases = listOf(
            Triple(AiAssistantReplyEndReason.TRUNCATED, 4096, listOf(4096)),
            Triple(AiAssistantReplyEndReason.TRUNCATED, 8192, listOf(4096, 8192)),
            Triple(AiAssistantReplyEndReason.INTERRUPTED, 8192, listOf(4096)),
        )
        cases.forEach { (reason, retryBudget, expectedBudgets) ->
            val requestedBudgets = mutableListOf<Int>()
            val failure = runCatching {
                requestCandidateText(4096, retryBudget) { budget ->
                    requestedBudgets += budget
                    throw AiAssistantReplyEndException(reason)
                }
            }.exceptionOrNull() as? AiAssistantReplyEndException

            assertEquals(expectedBudgets, requestedBudgets)
            assertEquals(reason, failure?.reason)
        }
    }

    @Test
    fun longParagraphBlocksPreserveTheEndAndDoNotSplitSurrogatePairs() {
        val source = "甲".repeat(100) + "𠮷" + "乙".repeat(160) + "结尾"
        val blocks = characterCardCandidateBlocks(source, blockSize = 101).toList()
        assertTrue(blocks.size > 1)
        assertTrue(blocks.all { it.length <= 101 && !it.first().isLowSurrogate() && !it.last().isHighSurrogate() })
        assertTrue(blocks.any { "𠮷" in it })
        assertTrue(blocks.last().endsWith("结尾"))
    }

    @Test
    fun completedAndLiveStageUsageAddsWithoutDroppingMissingUsageFlags() {
        val discovery = AiCharacterCardProgress(usage = AiChatUsage(405, 120, 525))
        val generation = AiCharacterCardProgress(
            usage = AiChatUsage(1_200, null, null),
            missingCompletionTokens = true,
        )
        val combined = discovery.followedBy(generation)
        assertEquals(AiChatUsage(1_605, 120, 525), combined.usage)
        assertTrue(combined.missingCompletionTokens)
    }
}
