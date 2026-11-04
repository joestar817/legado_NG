package io.legado.app.ui.book.read.epub

/** Joins rendering with the original animation completion before publishing the live page. */
internal class EpubTurnHandoff {
    private var sequence = 0L
    private var active: Long? = null
    private var animationFinished = false
    private var ready: (() -> Unit)? = null
    var waitingForMove = false
        private set

    fun begin(): Long {
        cancel()
        return (++sequence).also { active = it; waitingForMove = true }
    }

    fun isCurrent(ticket: Long) = active == ticket

    fun startMove(ticket: Long): Boolean {
        if (!isCurrent(ticket)) return false
        waitingForMove = false
        return true
    }

    /** False leaves the ordinary, non-overlapped ready path with its existing owner. */
    fun deferReady(publish: () -> Unit): Boolean {
        if (active == null) return false
        ready = publish
        publishIfComplete()
        return true
    }

    fun finishAnimation(ticket: Long) {
        if (!isCurrent(ticket)) return
        animationFinished = true
        publishIfComplete()
    }

    private fun publishIfComplete() {
        if (!animationFinished) return
        val publish = ready ?: return
        // Clear before invoking the original callback; it can synchronously start another turn.
        cancel()
        publish()
    }

    fun cancel() {
        active = null
        waitingForMove = false
        animationFinished = false
        ready = null
    }
}
