package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatUsageAccumulatorTest {

    @Test
    fun completeUsageIsSummedAcrossCalls() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 100, completionTokens = 20, totalTokens = 120))
        accumulator.add(AiChatUsage(promptTokens = 200, completionTokens = 30, totalTokens = 230))

        val result = accumulator.result()

        assertEquals(300, result?.promptTokens)
        assertEquals(50, result?.completionTokens)
        assertEquals(350, result?.totalTokens)
        assertEquals(2, accumulator.callsWithUsage)
        assertEquals(0, accumulator.callsWithoutUsage)
        assertFalse(accumulator.hasPartialCoverage)
    }

    @Test
    fun missingCompletionStaysUnknownAndKeepsProviderTotal() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 100, completionTokens = null, totalTokens = 120))

        val result = accumulator.result()

        assertEquals(100, result?.promptTokens)
        assertNull(result?.completionTokens)
        assertEquals(120, result?.totalTokens)
    }

    @Test
    fun providerTotalOnlyIsPreservedInsteadOfBecomingZero() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = null, completionTokens = null, totalTokens = 120))

        val result = accumulator.result()

        assertNull(result?.promptTokens)
        assertNull(result?.completionTokens)
        assertEquals(120, result?.totalTokens)
    }

    @Test
    fun totalIsDerivedPerCallOnlyWhenPromptAndCompletionAreKnown() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 100, completionTokens = 20, totalTokens = null))
        accumulator.add(AiChatUsage(promptTokens = 50, completionTokens = 10, totalTokens = null))

        val result = accumulator.result()

        assertEquals(150, result?.promptTokens)
        assertEquals(30, result?.completionTokens)
        assertEquals(180, result?.totalTokens)
    }

    @Test
    fun promptOnlyCallDoesNotInventCompletionOrTotal() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 100, completionTokens = null, totalTokens = null))

        val result = accumulator.result()

        assertEquals(100, result?.promptTokens)
        assertNull(result?.completionTokens)
        assertNull(result?.totalTokens)
    }

    @Test
    fun allMissingUsageReturnsNullInsteadOfZeros() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(null)
        accumulator.add(AiChatUsage(promptTokens = null, completionTokens = null, totalTokens = null))

        assertNull(accumulator.result())
        assertEquals(0, accumulator.callsWithUsage)
        assertEquals(2, accumulator.callsWithoutUsage)
        assertFalse(accumulator.hasPartialCoverage)
    }

    @Test
    fun mixedMissingCallsReportPartialCoverage() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 100, completionTokens = 20, totalTokens = 120))
        accumulator.add(null)

        val result = accumulator.result()

        assertEquals(100, result?.promptTokens)
        assertEquals(20, result?.completionTokens)
        assertEquals(120, result?.totalTokens)
        assertEquals(1, accumulator.callsWithUsage)
        assertEquals(1, accumulator.callsWithoutUsage)
        assertTrue(accumulator.hasPartialCoverage)
    }

    @Test
    fun streamingFinalChunkUsageIsCountedOncePerRequest() {
        // Streaming responses only carry usage on the final chunk. Each completed
        // request contributes its final usage exactly once.
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 100, completionTokens = 20, totalTokens = 120))
        accumulator.add(AiChatUsage(promptTokens = 200, completionTokens = 30, totalTokens = 230))

        val result = accumulator.result()

        assertEquals(300, result?.promptTokens)
        assertEquals(50, result?.completionTokens)
        assertEquals(350, result?.totalTokens)
    }

    @Test
    fun reportedZeroValuesAreNotTreatedAsUnknown() {
        val accumulator = AiChatUsageAccumulator()
        accumulator.add(AiChatUsage(promptTokens = 0, completionTokens = 0, totalTokens = 0))

        val result = accumulator.result()

        assertEquals(0, result?.promptTokens)
        assertEquals(0, result?.completionTokens)
        assertEquals(0, result?.totalTokens)
    }
}
