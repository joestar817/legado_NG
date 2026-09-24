package io.legado.app.ui.book.read.epub

import android.os.SystemClock
import android.util.Log
import io.legado.app.BuildConfig

/** Sparse debug milestones; never hooks methods, suspends threads or records book content. */
internal class EpubStartupTiming(private val stage: String) {
    private val started = SystemClock.elapsedRealtime()
    private var previous = started

    fun mark(event: String) {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        Log.d("EpubStartup", "$stage@$started $event total=${now - started}ms step=${now - previous}ms")
        previous = now
    }
}
