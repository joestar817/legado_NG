package io.legado.app.ui.book.read.epub

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Completion signals must remain runnable while IO workers wait for their GPU results. */
internal object EpubRenderCallbacks {
    val executor: Executor = Executors.newSingleThreadExecutor { callback ->
        Thread(callback, "epub-render-callback").apply { isDaemon = true }
    }
}
