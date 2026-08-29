package io.legado.app.model

import io.legado.app.help.source.SourceInteractionKind
import io.legado.app.help.source.SourceInteractionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckSourceTaskStateTest {

    @Test
    fun countsProcessedResultsAndKeepsBlockedSeparateFromFailure() {
        val state = CheckSourceTaskState(
            status = CheckSourceTaskStatus.RUNNING,
            items = listOf(
                item("passed", CheckSourceItemStatus.PASSED),
                item("failed", CheckSourceItemStatus.FAILED),
                item("blocked", CheckSourceItemStatus.BLOCKED),
                item("running", CheckSourceItemStatus.RUNNING),
                item("waiting", CheckSourceItemStatus.WAITING),
            ),
        )

        assertEquals(5, state.totalCount)
        assertEquals(3, state.processedCount)
        assertEquals(1, state.passedCount)
        assertEquals(1, state.failedCount)
        assertEquals(1, state.blockedCount)
        assertEquals(2, state.remainingCount)
    }

    @Test
    fun validationPolicyCanRestoreOldInteractionFlowWhenBlockingIsOff() {
        val allowedPolicy = SourceInteractionPolicy(
            blockDialogs = false,
            blockMediaLaunches = false,
        )

        assertFalse(allowedPolicy.shouldBlock(SourceInteractionKind.BROWSER_VERIFICATION))
        assertFalse(allowedPolicy.shouldBlock(SourceInteractionKind.VIDEO_PLAYER))
        assertFalse(allowedPolicy.shouldBlock(SourceInteractionKind.BROWSER))

        val blockedPolicy = SourceInteractionPolicy(
            blockDialogs = true,
            blockMediaLaunches = true,
        )
        assertTrue(blockedPolicy.shouldBlock(SourceInteractionKind.BROWSER_VERIFICATION))
        assertTrue(blockedPolicy.shouldBlock(SourceInteractionKind.VIDEO_PLAYER))
        assertTrue(blockedPolicy.shouldBlock(SourceInteractionKind.BROWSER))
    }

    private fun item(origin: String, status: CheckSourceItemStatus) = CheckSourceItemState(
        origin = origin,
        sourceName = origin,
        sourceType = 0,
        status = status,
    )
}
