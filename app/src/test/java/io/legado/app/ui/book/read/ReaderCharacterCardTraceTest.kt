package io.legado.app.ui.book.read

import io.legado.app.help.ai.AiCharacterCardTool
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderCharacterCardTraceTest {
    @Test
    fun groupsAllCallsInDisplayOrderAndKeepsFailuresVisible() {
        val tools = listOf(
            call("bookshelf_text_search"),
            call("use_skill"),
            call("bookshelf_cache_status_get"),
            call("agent_memory_search"),
            call("bookshelf_character_list"),
            call("bookshelf_chapter_content_get", ToolExecutionStatus.FAILED),
            call("bookshelf_chapter_content_get"),
            call("unknown_tool"),
        )

        assertEquals(
            listOf(
                ReaderCharacterToolSummary(ReaderCharacterTraceGroup.PREPARATION, 2, ReaderCharacterTraceStatus.DONE),
                ReaderCharacterToolSummary(ReaderCharacterTraceGroup.CHARACTERS, 2, ReaderCharacterTraceStatus.DONE),
                ReaderCharacterToolSummary(ReaderCharacterTraceGroup.SEARCH, 1, ReaderCharacterTraceStatus.DONE),
                ReaderCharacterToolSummary(ReaderCharacterTraceGroup.CHAPTERS, 2, ReaderCharacterTraceStatus.FAILED),
                ReaderCharacterToolSummary(ReaderCharacterTraceGroup.OTHER, 1, ReaderCharacterTraceStatus.DONE),
            ),
            summarizeReaderCharacterCardTools(tools, finished = true),
        )
    }

    @Test
    fun callsWithoutAReceiptStopShowingAsRunningWhenGenerationEnds() {
        val tools = listOf(call("bookshelf_book_get", null))

        assertEquals(
            ReaderCharacterTraceStatus.RUNNING,
            summarizeReaderCharacterCardTools(tools, finished = false).single().status,
        )
        assertEquals(
            ReaderCharacterTraceStatus.INTERRUPTED,
            summarizeReaderCharacterCardTools(tools, finished = true).single().status,
        )
    }

    private fun call(
        name: String,
        status: ToolExecutionStatus? = ToolExecutionStatus.SUCCEEDED,
    ) = AiCharacterCardTool(name, status, emptySet())
}
