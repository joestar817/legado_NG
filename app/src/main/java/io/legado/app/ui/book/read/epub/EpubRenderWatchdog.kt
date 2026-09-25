package io.legado.app.ui.book.read.epub

/** A native deadline also fires when Chromium/visual-state/capture callbacks never return. */
internal class EpubRenderWatchdog(
    private val schedule: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
) {
    private var pending: Runnable? = null

    fun arm(timeoutMillis: Long = 25_000, onTimeout: () -> Unit) {
        cancel()
        val task = object : Runnable {
            override fun run() {
                if (pending !== this) return
                pending = null
                onTimeout()
            }
        }
        pending = task
        schedule(task, timeoutMillis)
    }

    fun cancel() {
        val task = pending
        pending = null
        if (task != null) remove(task)
    }
}
