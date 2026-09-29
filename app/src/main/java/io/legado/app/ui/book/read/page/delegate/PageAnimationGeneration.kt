package io.legado.app.ui.book.read.page.delegate

/** Main-thread animation ownership, including completion work posted after the last frame. */
internal class PageAnimationGeneration {
    private var value = 0L
    fun advance() { value++ }
    fun guard(action: () -> Unit): () -> Unit {
        val owner = value
        return { if (owner == value) action() }
    }
}
