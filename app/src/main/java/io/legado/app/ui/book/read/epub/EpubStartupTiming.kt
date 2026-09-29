package io.legado.app.ui.book.read.epub

import android.os.SystemClock
import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.constant.AppLog
import io.legado.app.help.config.AppConfig

/** Bounded milestones; slow completed operations also remain available in Release application logs. */
internal class EpubStartupTiming(private val stage: String, private val started: Long = SystemClock.elapsedRealtime()) {
    private var previous = started
    private val milestones = ArrayList<String>(16)
    private var reported = false

    fun mark(event: String) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - started
        if (BuildConfig.DEBUG) Log.d("EpubStartup", "$stage@$started $event total=${elapsed}ms step=${now - previous}ms")
        if (milestones.size < 16) milestones.add("$event:${elapsed}ms")
        val navigationSample = AppConfig.recordLog && event == "publish" &&
            (stage == "visible" || stage == "preparation")
        if (!reported && (elapsed >= 500 || navigationSample) && event in completedEvents) {
            reported = true
            AppLog.put("EPUB耗时 $stage@$started ${milestones.joinToString(" -> ")}")
        }
        previous = now
    }

    private companion object {
        val completedEvents = setOf("publish", "delivered", "frame-ready", "visible-ready",
            "chapter-visible", "cancelled", "ready", "payload-ready", "resources-ready",
            "host-ready", "hit-ready", "hit-cancelled")
    }
}
