package io.legado.app.ui.book.read.epub

/** Transient pointer/viewport coordination; the controller still owns the only logical range. */
internal class EpubSelectionDrag(var x: Float, var y: Float) {
    var pressed = true
        private set
    var pointerRevision = 0L
        private set
    var moved = false
        private set
    var inFlight = false
    var awaitingViewport = false
    var chapterTarget: Int? = null
    var edge = 0
        private set
    private var edgeSince = 0L
    var finalHit = false
    var initialHit = false

    fun update(x: Float, y: Float) {
        if (!pressed) return
        this.x = x
        this.y = y
        moved = true
        pointerRevision++
    }

    fun updateEdge(direction: Int, now: Long) {
        val next = direction.coerceIn(-1, 1)
        if (next != edge) edgeSince = now
        edge = next
    }

    fun canAdvance(now: Long) = pressed && !inFlight && !awaitingViewport && edge != 0 && now - edgeSince >= 350L

    fun didAdvance(now: Long, repeatDelay: Long = 350L) { edgeSince = now - (350L - repeatDelay) }

    fun finish() {
        pressed = false
        finalHit = moved
    }
}
