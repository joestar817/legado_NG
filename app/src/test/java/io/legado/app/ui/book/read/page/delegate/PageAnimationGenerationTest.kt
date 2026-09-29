package io.legado.app.ui.book.read.page.delegate

import org.junit.Assert.*
import org.junit.Test

class PageAnimationGenerationTest {
    @Test fun queuedOldCleanupCannotStopTheNextAnimationAcrossFiveHundredTurns() {
        val owner = PageAnimationGeneration()
        var running = false
        var previousCleanup: (() -> Unit)? = null
        repeat(500) {
            owner.advance()
            running = true
            previousCleanup?.invoke()
            assertTrue("Old completion stopped turn $it", running)
            previousCleanup = owner.guard { running = false }
        }
        previousCleanup!!.invoke()
        assertFalse(running)
    }
    @Test fun synchronousCompletionCallbackCanStartAnotherAnimation() {
        val owner = PageAnimationGeneration()
        owner.advance()
        var newAnimationStarted = true
        val stopOldScroll = owner.guard { newAnimationStarted = false }
        owner.advance() // onAnimStop re-enters a consumer which starts a new animation.
        stopOldScroll()
        assertTrue(newAnimationStarted)
    }
    @Test fun abortAndDestroyInvalidatePostedCompletions() {
        val owner = PageAnimationGeneration()
        owner.advance()
        val old = owner.guard { fail("Aborted animation reset live state") }
        owner.advance()
        old()
    }
}
